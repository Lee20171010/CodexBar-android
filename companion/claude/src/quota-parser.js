const ANSI_PATTERN = /[\u001B\u009B][[\]()#;?]*(?:(?:(?:[a-zA-Z\d]*(?:;[-a-zA-Z\d\/#&.:=?%@~_]+)*)?\u0007)|(?:(?:\d{1,4}(?:[;:]\d{0,4})*)?[\dA-PR-TZcf-nq-uy=><~]))/g;
const MAX_CAPTURE_LENGTH = 256 * 1024;
const MAX_WINDOWS = 8;

const WINDOW_LABELS = [
  [/^Current session\b/i, '5-Hour'],
  [/^Current week\s*\(all(?: models)?\)/i, '7-Day'],
  [/^Current week\s*\(Sonnet(?: only)?\)/i, 'Sonnet'],
  [/^Current week\s*\(Opus(?: only)?\)/i, 'Opus'],
  [/^Current week\b/i, '7-Day']
];

export function sanitizeTerminalOutput(value) {
  return value
    .slice(-MAX_CAPTURE_LENGTH)
    .replace(ANSI_PATTERN, '')
    .replace(/\u0008/g, '')
    .replace(/\r(?!\n)/g, '\n');
}

export function parseClaudeUsageOutput(output, now = new Date()) {
  const text = sanitizeTerminalOutput(output);
  const usageBlock = extractUsageBlock(text);
  if (usageBlock == null || isLoadingBlock(usageBlock)) return null;
  const lines = usageBlock.split(/\r?\n/).slice(0, 80);
  const windows = [];
  const seenLabels = new Set();

  for (let index = 0; index < lines.length && windows.length < MAX_WINDOWS; index += 1) {
    const line = compactLine(lines[index]);
    const label = labelForLine(line);
    if (label == null || seenLabels.has(label.toLowerCase())) continue;
    const nearby = lines.slice(index, index + 7).map(compactLine).join(' ');
    const usedFraction = parseUsedFraction(nearby);
    if (usedFraction == null) continue;
    const resetsAtEpochSeconds = parseReset(nearby, now);
    windows.push({
      label,
      usedFraction,
      ...(resetsAtEpochSeconds == null ? {} : { resetsAtEpochSeconds })
    });
    seenLabels.add(label.toLowerCase());
  }

  if (windows.length === 0) return null;
  const tier = parseTier(usageBlock);
  return { windows, ...(tier == null ? {} : { tier }) };
}

export function isClaudeUsageLoading(output) {
  const usageBlock = extractUsageBlock(sanitizeTerminalOutput(output));
  return usageBlock != null && isLoadingBlock(usageBlock);
}

function extractUsageBlock(text) {
  const sessionIndex = text.lastIndexOf('Current session');
  if (sessionIndex < 0) return null;
  const accountIndex = text.lastIndexOf('Account & Usage', sessionIndex);
  const start = accountIndex >= 0 && sessionIndex - accountIndex <= 8 * 1024
    ? accountIndex
    : sessionIndex;
  return text.slice(start).split(/\r?\n/).slice(0, 80).join('\n');
}

function isLoadingBlock(value) {
  const loadingMatches = [...value.matchAll(/\bLoading(?:\.{3}|…)?\b/gi)];
  const usageMatches = [...value.matchAll(/\d{1,3}(?:\.\d+)?\s*%\s*(?:used|spent|consumed|left|remaining|available)?/gi)];
  const lastLoading = loadingMatches.at(-1)?.index ?? -1;
  const lastUsage = usageMatches.at(-1)?.index ?? -1;
  return lastLoading >= 0 && lastLoading > lastUsage;
}

function compactLine(value) {
  return value.replace(/[│┃┆┊╎╏▏▎▍▌▋▊▉█]/g, ' ').replace(/\s+/g, ' ').trim();
}

function labelForLine(line) {
  for (const [pattern, label] of WINDOW_LABELS) {
    if (pattern.test(line)) return label;
  }
  return null;
}

function parseUsedFraction(value) {
  const matches = [...value.matchAll(/(\d{1,3}(?:\.\d+)?)\s*%\s*(used|spent|consumed|left|remaining|available)?/gi)];
  for (const match of matches) {
    const percent = Number(match[1]);
    if (!Number.isFinite(percent) || percent < 0 || percent > 100) continue;
    const qualifier = match[2]?.toLowerCase();
    const used = qualifier === 'left' || qualifier === 'remaining' || qualifier === 'available'
      ? 100 - percent
      : percent;
    return Math.round((used / 100) * 10_000) / 10_000;
  }
  return null;
}

function parseReset(value, now) {
  const reset = value.match(/resets?\s+(?:in\s+)?([^|•]+?)(?=\s+(?:Current|Session|Usage by|Total|Plan|Tier|$))/i)?.[1]
    ?? value.match(/resets?\s+(?:in\s+)?(.+)$/i)?.[1];
  if (reset == null) return null;
  const normalized = reset.replace(/\([^)]*\)/g, '').trim();
  const relative = normalized.match(/(?:(\d+)\s*d(?:ays?)?)?\s*(?:(\d+)\s*h(?:ours?|rs?)?)?\s*(?:(\d+)\s*m(?:in(?:utes?)?)?)?/i);
  if (relative && relative[0].trim().length > 0) {
    const minutes = Number(relative[1] ?? 0) * 24 * 60 +
      Number(relative[2] ?? 0) * 60 +
      Number(relative[3] ?? 0);
    if (Number.isSafeInteger(minutes) && minutes > 0 && minutes <= 31 * 24 * 60) {
      return Math.floor(now.getTime() / 1000) + minutes * 60;
    }
  }

  const clock = normalized.match(/^(\d{1,2}):(\d{2})\s*(am|pm)$/i);
  if (clock) {
    let hour = Number(clock[1]) % 12;
    if (clock[3].toLowerCase() === 'pm') hour += 12;
    const candidate = new Date(now);
    candidate.setHours(hour, Number(clock[2]), 0, 0);
    if (candidate <= now) candidate.setDate(candidate.getDate() + 1);
    return Math.floor(candidate.getTime() / 1000);
  }

  const parsed = Date.parse(normalized.replace(/\bat\b/i, ''));
  return Number.isFinite(parsed) && parsed > now.getTime()
    ? Math.floor(parsed / 1000)
    : null;
}

function parseTier(text) {
  const matches = [...text.matchAll(/^\s*(?:Plan|Tier|Subscription):\s*(.+?)\s*$/gim)];
  const tier = matches.at(-1)?.[1]?.trim();
  return isSafeText(tier, 64) && KNOWN_TIER_PATTERN.test(tier) ? tier : null;
}

function isSafeText(value, maxLength) {
  return typeof value === 'string' && value.length > 0 && value.length <= maxLength &&
    !/[\u0000-\u001f\u007f]/.test(value);
}

const KNOWN_TIER_PATTERN = /^(?:Free|Pro|Max(?:\s+(?:5x|20x)|\s*\((?:5x|20x)\))?|Team|Enterprise)$/i;

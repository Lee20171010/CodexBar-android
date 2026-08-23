import assert from 'node:assert/strict';
import test from 'node:test';
import { parseClaudeUsageOutput, sanitizeTerminalOutput } from '../src/quota-parser.js';

const NOW = new Date('2026-08-23T00:00:00Z');

test('parses current Claude Code session and weekly used percentages', () => {
  const parsed = parseClaudeUsageOutput(`
    Account & Usage
    Current session
    37% used
    Resets in 2h 30m
    Current week (all models)
    54% used
    Resets in 2 days
    Current week (Sonnet only)
    10% used
    Plan: Max 5x
  `, NOW);

  assert.deepEqual(parsed, {
    tier: 'Max 5x',
    windows: [
      { label: '5-Hour', usedFraction: 0.37, resetsAtEpochSeconds: 1787452200 },
      { label: '7-Day', usedFraction: 0.54, resetsAtEpochSeconds: 1787616000 },
      { label: 'Sonnet', usedFraction: 0.1 }
    ]
  });
});

test('converts remaining percentage to used fraction', () => {
  const parsed = parseClaudeUsageOutput(`
    Current session
    63% remaining
    Resets 9:40pm
  `, NOW);

  assert.equal(parsed.windows[0].label, '5-Hour');
  assert.equal(parsed.windows[0].usedFraction, 0.37);
  assert.ok(parsed.windows[0].resetsAtEpochSeconds > Math.floor(NOW.getTime() / 1000));
});

test('accepts enterprise output with only a current session limit', () => {
  const parsed = parseClaudeUsageOutput('Current session\n12% used\n', NOW);

  assert.deepEqual(parsed, {
    windows: [{ label: '5-Hour', usedFraction: 0.12 }]
  });
});

test('ignores unrelated or invalid percentages and strips terminal controls', () => {
  const sanitized = sanitizeTerminalOutput('\u001b[31mCurrent session\u001b[0m\r150% used');
  assert.equal(sanitized.includes('\u001b'), false);
  assert.equal(parseClaudeUsageOutput(sanitized, NOW), null);
  assert.equal(parseClaudeUsageOutput('Total cost: 37%\n', NOW), null);
});

test('waits for loading to finish and never forwards arbitrary plan text', () => {
  assert.equal(
    parseClaudeUsageOutput('Account & Usage\nCurrent session\n37% used\nLoading...', NOW),
    null
  );
  assert.deepEqual(
    parseClaudeUsageOutput('Account & Usage\nCurrent session\n37% used\nPlan: private-hook-output', NOW),
    { windows: [{ label: '5-Hour', usedFraction: 0.37 }] }
  );
});

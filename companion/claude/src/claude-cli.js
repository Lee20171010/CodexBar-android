import os from 'node:os';
import pty from 'node-pty';
import { isClaudeUsageLoading, parseClaudeUsageOutput } from './quota-parser.js';

const MAX_CAPTURE_LENGTH = 256 * 1024;

export class ClaudeUsageSession {
  constructor({
    command = 'claude',
    startupDelayMillis = 6_000,
    commandDelayMillis = 350,
    timeoutMillis = 45_000,
    parseSettleMillis = 2_500,
    minimumObservationMillis = 3_000,
    spawn = pty.spawn
  } = {}) {
    this.command = command;
    this.startupDelayMillis = startupDelayMillis;
    this.commandDelayMillis = commandDelayMillis;
    this.timeoutMillis = timeoutMillis;
    this.parseSettleMillis = parseSettleMillis;
    this.minimumObservationMillis = minimumObservationMillis;
    this.spawn = spawn;
    this.terminal = null;
    this.pending = null;
  }

  collect({ now = new Date() } = {}) {
    if (this.pending != null) {
      return Promise.reject(new Error('A Claude usage refresh is already running'));
    }
    const startedNow = this.ensureTerminal();
    return new Promise((resolve, reject) => {
      const pending = {
        resolve,
        reject,
        now,
        capture: '',
        active: false,
        requestSentAt: 0,
        candidateFingerprint: null,
        sendTimer: null,
        settleTimer: null,
        timeoutTimer: null
      };
      this.pending = pending;

      if (!startedNow) this.terminal.write('\u001b');
      pending.sendTimer = setTimeout(() => {
        if (this.pending !== pending || this.terminal == null) return;
        pending.active = true;
        pending.capture = '';
        pending.requestSentAt = Date.now();
        this.terminal.write('/usage\r');
      }, startedNow ? this.startupDelayMillis : this.commandDelayMillis);
      pending.timeoutTimer = setTimeout(() => {
        this.finishPending(
          new Error(
            'Claude Code did not return complete plan usage. Run `claude` once, finish sign-in and trust prompts, then confirm `/usage` works.'
          )
        );
      }, this.timeoutMillis);
    });
  }

  close() {
    if (this.pending != null) {
      this.finishPending(new Error('Claude usage session closed'));
    }
    const terminal = this.terminal;
    this.terminal = null;
    try {
      terminal?.kill();
    } catch {
      // Process already exited.
    }
  }

  ensureTerminal() {
    if (this.terminal != null) return false;
    const terminal = this.spawn(this.command, ['--allowed-tools', ''], {
      name: 'xterm-color',
      cols: 120,
      rows: 60,
      cwd: os.homedir(),
      env: {
        ...process.env,
        NO_COLOR: '1'
      }
    });
    this.terminal = terminal;
    terminal.onData((data) => this.handleData(data));
    terminal.onExit(({ exitCode }) => {
      if (this.terminal === terminal) this.terminal = null;
      if (this.pending != null) {
        this.finishPending(
          new Error(`Claude Code exited before plan usage was available (${exitCode})`)
        );
      }
    });
    return true;
  }

  handleData(data) {
    const pending = this.pending;
    if (pending == null || !pending.active) return;
    pending.capture = (pending.capture + data).slice(-MAX_CAPTURE_LENGTH);
    clearTimeout(pending.settleTimer);

    if (isClaudeUsageLoading(pending.capture)) {
      pending.candidateFingerprint = null;
      return;
    }
    const parsed = parseClaudeUsageOutput(pending.capture, pending.now);
    if (parsed == null) {
      pending.candidateFingerprint = null;
      return;
    }
    pending.candidateFingerprint = JSON.stringify(parsed);
    pending.settleTimer = setTimeout(
      () => this.finishStableCandidate(pending),
      this.parseSettleMillis
    );
  }

  finishStableCandidate(pending) {
    if (this.pending !== pending || pending.candidateFingerprint == null) return;
    const elapsed = Date.now() - pending.requestSentAt;
    if (elapsed < this.minimumObservationMillis) {
      pending.settleTimer = setTimeout(
        () => this.finishStableCandidate(pending),
        this.minimumObservationMillis - elapsed
      );
      return;
    }
    if (isClaudeUsageLoading(pending.capture)) return;
    const parsed = parseClaudeUsageOutput(pending.capture, pending.now);
    if (parsed == null || JSON.stringify(parsed) !== pending.candidateFingerprint) return;
    this.finishPending(null, parsed);
  }

  finishPending(error, value) {
    const pending = this.pending;
    if (pending == null) return;
    this.pending = null;
    clearTimeout(pending.sendTimer);
    clearTimeout(pending.settleTimer);
    clearTimeout(pending.timeoutTimer);
    try {
      this.terminal?.write('\u001b');
    } catch {
      // Process exited while the result was being finalized.
    }
    if (error) pending.reject(error);
    else pending.resolve(value);
  }
}

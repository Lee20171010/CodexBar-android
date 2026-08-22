import process from 'node:process';
import pty from 'node-pty';

const marker = 'CODEXBAR_PTY_SMOKE';
const isWindows = process.platform === 'win32';
const command = isWindows ? (process.env.ComSpec ?? 'cmd.exe') : '/bin/sh';
const args = isWindows
  ? ['/d', '/s', '/c', `echo ${marker}`]
  : ['-c', `printf ${marker}`];

await new Promise((resolve, reject) => {
  let output = '';
  let sawMarker = false;
  let finished = false;
  const terminal = pty.spawn(command, args, {
    name: 'xterm-color',
    cols: 80,
    rows: 24,
    env: process.env
  });
  const finish = (error) => {
    if (finished) return;
    finished = true;
    clearTimeout(timeout);
    if (error) {
      try {
        terminal.kill();
      } catch {
        // The short-lived command may already have exited.
      }
    }
    if (error) reject(error);
    else resolve();
  };
  const timeout = setTimeout(
    () => finish(new Error('node-pty smoke command timed out')),
    5_000
  );
  terminal.onData((chunk) => {
    output = (output + chunk).slice(-4_096);
    sawMarker = output.includes(marker);
  });
  terminal.onExit(({ exitCode }) => {
    if (sawMarker && exitCode === 0) {
      finish();
    } else if (!finished) {
      finish(new Error(`node-pty smoke command exited without output (${exitCode})`));
    }
  });
});

process.stdout.write('node-pty-spawn-ok\n');
process.exit(0);

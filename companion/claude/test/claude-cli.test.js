import assert from 'node:assert/strict';
import test from 'node:test';
import { ClaudeUsageSession } from '../src/claude-cli.js';

test('reuses one Claude PTY and waits until loading is replaced by stable usage', async () => {
  let spawnCount = 0;
  let killed = false;
  const writes = [];
  let dataHandler = () => {};
  let exitHandler = () => {};
  const terminal = {
    onData(handler) {
      dataHandler = handler;
    },
    onExit(handler) {
      exitHandler = handler;
    },
    write(value) {
      writes.push(value);
      if (value !== '/usage\r') return;
      setTimeout(() => dataHandler('Account & Usage\nCurrent session\nLoading...\n'), 1);
      setTimeout(() => dataHandler(
        'Account & Usage\nCurrent session\n25% used\nCurrent week (all models)\n40% used\nPlan: Pro\n'
      ), 8);
    },
    kill() {
      killed = true;
      exitHandler({ exitCode: 0 });
    }
  };
  const session = new ClaudeUsageSession({
    spawn(command, args) {
      spawnCount += 1;
      assert.equal(command, 'claude-test');
      assert.deepEqual(args, ['--allowed-tools', '']);
      return terminal;
    },
    command: 'claude-test',
    startupDelayMillis: 0,
    commandDelayMillis: 0,
    timeoutMillis: 500,
    parseSettleMillis: 15,
    minimumObservationMillis: 20
  });

  const first = await session.collect({ now: new Date('2026-08-23T00:00:00Z') });
  const second = await session.collect({ now: new Date('2026-08-23T00:05:00Z') });

  assert.equal(spawnCount, 1);
  assert.equal(writes.filter((value) => value === '/usage\r').length, 2);
  assert.deepEqual(first, second);
  assert.deepEqual(first, {
    tier: 'Pro',
    windows: [
      { label: '5-Hour', usedFraction: 0.25 },
      { label: '7-Day', usedFraction: 0.4 }
    ]
  });
  assert.equal(killed, false);

  session.close();
  assert.equal(killed, true);
});

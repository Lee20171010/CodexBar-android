import assert from 'node:assert/strict';
import test from 'node:test';
import { ensureNodePtyHelperExecutable } from '../src/node-pty-runtime.js';

test('repairs the selected macOS node-pty helper before spawning', () => {
  const chmodCalls = [];
  const accessCalls = [];
  const helpers = ensureNodePtyHelperExecutable({
    platform: 'darwin',
    architecture: 'arm64',
    moduleDirectory: '/private/node-pty',
    exists: (candidate) => candidate.includes('darwin-arm64'),
    stat: () => ({ isFile: () => true, isSymbolicLink: () => false }),
    chmod: (candidate, mode) => chmodCalls.push({ candidate, mode }),
    access: (candidate, mode) => accessCalls.push({ candidate, mode })
  });

  assert.equal(helpers.length, 1);
  assert.equal(chmodCalls.length, 1);
  assert.equal(chmodCalls[0].mode, 0o755);
  assert.equal(accessCalls.length, 1);
  assert.equal(accessCalls[0].candidate, helpers[0]);
});

test('does not touch helpers on Linux or Windows', () => {
  let touched = false;
  const helpers = ensureNodePtyHelperExecutable({
    platform: 'linux',
    exists: () => {
      touched = true;
      return true;
    }
  });

  assert.deepEqual(helpers, []);
  assert.equal(touched, false);
});

import { accessSync, chmodSync, constants, existsSync, lstatSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const DEFAULT_NODE_PTY_DIRECTORY = fileURLToPath(
  new URL('../node_modules/node-pty/', import.meta.url)
);

export function ensureNodePtyHelperExecutable({
  platform = process.platform,
  architecture = process.arch,
  moduleDirectory = DEFAULT_NODE_PTY_DIRECTORY,
  exists = existsSync,
  stat = lstatSync,
  chmod = chmodSync,
  access = accessSync
} = {}) {
  if (platform !== 'darwin') return [];
  if (architecture !== 'arm64' && architecture !== 'x64') {
    throw new Error(`Unsupported macOS architecture for node-pty: ${architecture}`);
  }

  const candidates = [
    path.join(moduleDirectory, 'prebuilds', `darwin-${architecture}`, 'spawn-helper'),
    path.join(moduleDirectory, 'build', 'Release', 'spawn-helper')
  ];
  const helpers = candidates.filter(exists);
  if (helpers.length === 0) {
    throw new Error('The node-pty macOS spawn-helper is missing; reinstall the companion dependencies.');
  }

  for (const helper of helpers) {
    const metadata = stat(helper);
    if (!metadata.isFile() || metadata.isSymbolicLink()) {
      throw new Error('The node-pty macOS spawn-helper is not a regular file.');
    }
    chmod(helper, 0o755);
    access(helper, constants.X_OK);
  }
  return helpers;
}

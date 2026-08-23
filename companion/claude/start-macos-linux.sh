#!/usr/bin/env sh
set -eu

cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
  echo "CodexBar Claude companion requires Node.js 20 or newer." >&2
  exit 1
fi

node -e "if (Number(process.versions.node.split('.')[0]) < 20) process.exit(1)" || {
  echo "CodexBar Claude companion requires Node.js 20 or newer." >&2
  exit 1
}

if [ ! -f node_modules/node-pty/package.json ]; then
  echo "Installing the pinned companion dependencies..."
  npm ci --omit=dev
fi

exec npm start -- "$@"

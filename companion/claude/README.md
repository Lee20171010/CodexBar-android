# CodexBar Claude companion

This companion keeps Anthropic authentication inside the official Claude Code CLI and serves only a sanitized plan-usage snapshot to CodexBar for Android over the local network.

## Requirements

- Node.js 20 or newer
- The current official Claude Code CLI, already signed in
- The computer and Android phone on the same trusted Wi-Fi network

Install Claude Code using Anthropic's current instructions, then run `claude` once. Complete browser sign-in, the first-run prompts, and any workspace trust prompt. Confirm that `/usage` shows **Current session** before starting the companion.

Do not use `claude setup-token` for this app. Anthropic documents that token for inference automation; it does not include the `user:profile` permission required to read plan usage.

## Start and pair

Extract the release archive. On Windows, double-click `start-windows.cmd`. On macOS or Linux, run:

```shell
chmod +x start-macos-linux.sh
./start-macos-linux.sh
```

The launchers install the exact dependencies from `package-lock.json` on first use. The equivalent manual commands are:

```shell
npm ci --omit=dev
npm start
```

The companion opens one dedicated official Claude Code terminal with tools disabled and reuses it for every refresh. It runs `/usage`, waits for loading to finish and the complete view to stabilize, parses only quota labels, percentages, reset text, an allowlisted plan name, and freshness, then discards the raw terminal output. It never serves Anthropic tokens, prompts, responses, files, email addresses, or session text.

On Android, open CodexBar → **Connections** → **Claude**, tap **Scan QR securely in CodexBar**, and scan the displayed QR. Do not use the system camera or another scanner: the QR contains the local pairing password and is intentionally not a web/app link. If Google Play services cannot open the in-app scanner, paste the displayed `CBCLAUDE1...` code into the hidden pairing field. Tap **Pair & verify Claude companion** and keep the companion window running while current usage is needed.

## Troubleshooting

- If no snapshot is available, run `claude` directly in a terminal, finish sign-in/trust prompts, enter `/usage`, then restart the companion.
- If Windows installed the npm launcher instead of the native CLI, run `start-windows.cmd --claude-command claude.cmd`.
- The companion prefers the default-route Wi-Fi/Ethernet address and prints the selected interface plus alternatives. If the phone cannot connect, restart with `--address 192.168.x.x` using the listed Wi-Fi address, allow Node.js only on private networks in the computer firewall, and confirm the Wi-Fi does not use client isolation.
- Keep date and time automatic on both devices; requests outside the two-minute clock-skew window are rejected.

Options:

```text
--address 192.168.1.20
--port 43823
--interval-minutes 5
--claude-command /path/to/claude
--cli-version 2.1.123
```

The server binds to one numeric private address, authenticates bounded requests with HMAC-SHA256, rejects replayed nonces, rate-limits clients, and returns an AES-256-GCM encrypted snapshot. The pairing identity is stored in `~/.codexbar/claude-companion.json`. Do not expose the port through router forwarding, a public IP, or a public tunnel.

# Repository working agreement

Working agreement for the Android app and its native Swift Core/CLI integration.

## Local environment

- Read `AGENTS.local.md` if present before signing or device work. It contains private operator configuration, is ignored by Git, and must never be committed.
- Keep machine names, network addresses, personal paths and credential values in local configuration. Public documentation should use environment variables and portable examples.

## Development style

- Preserve the existing architecture, naming, formatting, dependencies, and release workflow.
- Prefer the smallest change that completely solves the requested behavior; reuse existing helpers and verified tooling.
- Preserve Compose UI, widgets, domain models, and encrypted credential storage while integrating upstream Swift Core/CLI through the data layer.
- Keep unrelated user changes out of the current task and its commits.
- Do not commit credentials, signing material, local configuration, caches, downloaded toolchains, native binaries, or generated build outputs.

## Commit policy

- Use the agreed target branch; do not change the branch workflow or publication target without authorization.
- Create a commit whenever one meaningful, independently reviewable unit is complete and its focused checks pass.
- Prefer small atomic commits when work naturally separates into domain logic, presentation mapping, UI, widgets, tests, documentation, build configuration, or release metadata.
- Keep each commit understandable, buildable where practical, and safe to revert independently.
- Stage only the files or hunks belonging to that concern, then review the staged diff before committing.
- Use concise imperative Conventional Commit messages unless an established repository convention requires otherwise.
- Do not create empty commits, artificial file splits, formatting churn, duplicate changes, or history noise solely to increase contribution counts.
- Do not amend, squash, rebase, force-push, or otherwise rewrite history without an explicit request.
- Push only after relevant checks pass and publication is part of the requested task. Do not push directly to a protected/default branch unless explicitly requested.
- Associate work with the relevant project issues or pull requests, including references in the review or completion report.

## Verification

- Before each commit, run the narrowest relevant unit test, lint, compile, build, or documentation check for that logical unit.
- After the final implementation commit, run the broadest relevant test, lint, and build checks whose cost is reasonable. Avoid repeating expensive checks without a new change or unresolved concern.
- Review the final diff and commit sequence for mixed concerns, regressions, unnecessary changes, and missing tests.
- Report checks that could not run and remaining unverified scope honestly.
- Distinguish compilation, runtime-probe success, full CLI App-UID smoke checks, and real quota/login/refresh acceptance. An error JSON response is not proof of successful quota retrieval.
- Distinguish the emulator's ARM64 native-bridge path from physical ARM64 hardware; use the advertised supported ABI paths rather than rejecting them solely because the kernel is x86_64.
- Use synthetic credentials and isolated homes for automated CLI checks. Set provider-specific homes such as `CODEX_HOME`, `CLAUDE_CONFIG_DIR`, and XDG paths; `HOME` alone is insufficient. Real-account quota requests and token refresh require explicit authorization.

## Long-running work

- Run lengthy Gradle/Swift builds and APK transfers in the background with a bounded timeout and retained logs; avoid unlimited foreground waits.
- Reuse build caches and completed native payloads. Use `--max-workers=2` for Gradle and `--jobs 2` for Swift unless measured needs justify changing them.
- State the current stage and report meaningful outcomes. Do not repeatedly poll background jobs; use completion notifications.
- After interruption, check for residual processes before restarting the same work.
- Prefer optimized, stripped, single-target-ABI Release APKs for remote installation and acceptance: off-LAN transfer size matters. Use Debug only when a required debugger, `run-as`, or debug-only diagnostic needs it; state that reason before transferring. Where possible, test the exact Release APK intended for delivery rather than installing a separate Debug build.

## Completion report

- Report each task commit SHA, subject, and purpose.
- Report checks executed and their outcomes.
- Report remaining risks, deferred work, and whether the branch was pushed or released.
- Identify whether normal dashboard/widget refresh actually uses the new engine or only an acceptance variant exercises it.

## 共用 Android Emulator

- 共用遠端 runtime 一次只供一個測試 session 使用；裝置及連線資料由本機設定提供。
- ADB wrapper 與獨占 session wrapper 分別由 `ANDROID_TEST_ADB_WRAPPER`、`ANDROID_TEST_SESSION_WRAPPER` 指定。
- 未經授權，不得安裝、建立或啟動替代 Emulator/AVD。

### Runtime lock

以一次 session wrapper 包住完整的 emulator-affecting session，不得逐條 ADB 命令分別上鎖：

```bash
"$ANDROID_TEST_SESSION_WRAPPER" bash -c '
  "$ANDROID_TEST_ADB_WRAPPER" devices -l
  # Install an isolated test package; arrange trap-based cleanup before installation.
  "$ANDROID_TEST_ADB_WRAPPER" install /path/to/test.apk
  "$ANDROID_TEST_ADB_WRAPPER" shell am start -n test.package/.TestActivity
  # setup → assertions → cleanup, all within this lock
'
```

鎖的範圍必須涵蓋：確認裝置 → 安裝 → 啟動 → setup → intents → UI 操作 → screenshots/logcat → assertions → cleanup。鎖檔及等待時間由本機 wrapper 設定，所有使用同一 runtime 的 session 必須使用同一把鎖。不得換 lock file 規避鎖。逾時回報 busy；裝置不可用回報 unavailable，不得自行建立替代 Emulator。

### 不需鎖的工作

源碼編輯、Gradle/Swift 編譯、APK build、不存取 Emulator 的 unit tests、lint、靜態分析及文件生成不需鎖，並應優先在取得鎖前完成，以縮短共用資源占用時間。

### 強制規則與限制

- 共用 Emulator runtime 測試必須同時使用設定的 session-lock 與 ADB wrapper。
- 禁止另開 ADB server 取代共用設定、繞過任一 wrapper，或未經授權修改遠端網路及 AVD 設定。
- 若 session 啟動 `adb logcat`、錄影、監控或其他背景程序，必須在離開鎖定 scope 前明確停止並 wait；測試腳本使用 trap/finally 清理。禁止背景程序在鎖釋放後繼續接觸 Emulator。
- 本機鎖只能協調遵守規範的同機 session，無法阻止其他主機或直接 ADB 操作。
- 自動測試期間可以觀察畫面；未經協調，不應手動點擊、輸入、切換 App 或改變 Emulator 狀態。
- 使用獨立測試 application ID；目前 `nativeDebug` 為 `com.codexbar.android.native`。保留既有 App 及其資料，只清理本次測試擁有的安裝與檔案。
- `native/test.py` 自行取得整段鎖，正常呼叫不需再包外層鎖，也不得直接使用其內部 `--locked` 參數繞過鎖。

## Release Signing

- Use the operator-provided existing release keystore; do not generate, replace or modify it without explicit authorization.
- Pass it to Gradle through `ANDROID_KEYSTORE_PATH`; keep passwords and aliases in environment variables or secrets, never in the repository or logs.
- Load signing variables from the operator's private environment without printing their contents; do not enable shell tracing around secrets.
- Release-key signing of a debuggable acceptance APK does not make it a production release.

## Native Core/CLI integration

- Keep upstream revision and toolchain versions pinned. Record Android changes in `native/android.patch`; edits in ignored `upstream-codexbar/` alone are not deliverables.
- Build/package with `native/build.py`; use `native/README.md` for commands, current limitations, and acceptance scope.
- Package executables at build time as `lib*.so` and run them from `nativeLibraryDir`; copy non-executable resources into app-private storage. Do not download executable code at runtime.
- Preserve normal TLS verification and Android security policies during testing.
- Preserve credential ownership and refreshed tokens when bridging to the CLI. Do not introduce concurrent refresh writers or leave plaintext credentials in logs, shared storage, or test artifacts.
- Keep temporary platform restrictions explicit, including the native engine's current API 28 minimum versus the existing app's API 26 minimum; do not silently change product compatibility.

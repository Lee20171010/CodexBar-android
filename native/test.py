#!/usr/bin/env python3
"""Run fixed, credential-free native CLI probes while holding the shared ADB lock."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.codexbar.android.native"
ADB = os.environ.get("ANDROID_TEST_ADB_WRAPPER", "")
SESSION_WRAPPER = os.environ.get("ANDROID_TEST_SESSION_WRAPPER", "")


def adb(*args, timeout=30, check=True):
    return subprocess.run([ADB, *args], capture_output=True, text=True, timeout=timeout, check=check)


def verify_api_settings(run_id, provider, display_name):
    """Exercise the real settings/repository path with a synthetic key on a fresh test install."""
    remote = f"/data/local/tmp/codexbar-native-{run_id}.xml"

    def snapshot():
        adb("shell", "uiautomator", "dump", remote)
        text = adb("shell", "cat", remote).stdout
        (ROOT / f"build/native/{provider}-settings-ui.xml").write_text(text)
        return ET.fromstring(text)

    def bounds(node):
        return list(map(int, re.findall(r"\d+", node.attrib["bounds"])))

    def tap(node):
        x1, y1, x2, y2 = bounds(node)
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))

    try:
        adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS", check=False)
        adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/com.codexbar.android.MainActivity")
        tree = snapshot()
        if any(n.get("text") == "Background Token Refresh" for n in tree.iter("node")):
            adb("shell", "input", "keyevent", "4")
            tree = snapshot()
        settings = next(n for n in tree.iter("node") if n.get("content-desc") == "Settings")
        tap(settings)
        size = list(map(int, re.findall(r"\d+", adb("shell", "wm", "size").stdout)))[-2:]
        width, height = size
        for _ in range(8):
            tree = snapshot()
            add_buttons = [n for n in tree.iter("node") if n.get("text") == f"Add {display_name} account"]
            if add_buttons:
                tap(add_buttons[0])
                continue
            fields = [n for n in tree.iter("node") if n.get("class") == "android.widget.EditText" and
                      any(c.get("text") == ("GitHub OAuth token" if provider == "copilot" else f"{display_name} API Key") for c in n.iter("node"))]
            if fields:
                field = fields[0]
                assert field.get("password") == "true", "API key field must be masked"
                tap(field)
                adb("shell", "input", "text", "codexbar-invalid-synthetic-key")
                adb("shell", "input", "keyevent", "4")
                # The field may only just have scrolled into view; reveal its button below it.
                adb("shell", "input", "swipe", str(width // 2), str(height * 3 // 4),
                    str(width // 2), str(height // 2), "350")
                tree = snapshot()
                buttons = [n for n in tree.iter("node") if n.get("text") == "Validate & save"]
                tap(max(buttons, key=lambda n: bounds(n)[1]))
                for _ in range(20):
                    tree = snapshot()
                    if any(n.get("text") == "Credentials rejected. Check this account's credentials." for n in tree.iter("node")):
                        tap(next(n for n in tree.iter("node") if n.get("text") == "Cancel"))
                        # Failed validation remains an unpublished draft, including after reopening Settings.
                        adb("shell", "input", "keyevent", "4")
                        tree = snapshot()
                        tap(next(n for n in tree.iter("node") if n.get("content-desc") == "Settings"))
                        tree = snapshot()
                        assert not any(n.get("text") in ("Reconnect", "Rename") for n in tree.iter("node")), "Rejected draft was saved"
                        adb("shell", "input", "keyevent", "4")
                        print(f"{display_name} settings: masked draft → native validation → expected rejection → no saved account", flush=True)
                        return True
                    time.sleep(1)
                raise RuntimeError(f"{display_name} settings did not display the expected API-key rejection")
            adb("shell", "input", "swipe", str(width // 2), str(height * 3 // 4),
                str(width // 2), str(height // 3), "350")
        raise RuntimeError(f"{display_name} API key field was not found")
    finally:
        adb("shell", "rm", "-f", remote, check=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--abi", choices=("x86_64", "arm64-v8a"), default="x86_64")
    parser.add_argument("--configuration", choices=("debug", "release"), default="debug")
    parser.add_argument("--opencode-go", action="store_true", help="Also test Go API with a fixed invalid key")
    parser.add_argument("--openrouter", action="store_true", help="Also test OpenRouter API with a fixed invalid key")
    parser.add_argument("--copilot", action="store_true", help="Also test Copilot API with a fixed invalid token")
    parser.add_argument("--locked", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args()
    for name, value in (("ANDROID_TEST_ADB_WRAPPER", ADB), ("ANDROID_TEST_SESSION_WRAPPER", SESSION_WRAPPER)):
        if not value:
            parser.error(f"Set {name} to the configured wrapper before running device checks")
    if args.configuration == "release" and args.abi != "arm64-v8a":
        parser.error("nativeRelease currently packages ARM64 only")
    if not args.locked:
        return subprocess.call([SESSION_WRAPPER, sys.executable,
                                str(Path(__file__).resolve()), "--abi", args.abi,
                                "--configuration", args.configuration, "--locked",
                                *(["--opencode-go"] if args.opencode_go else []),
                                *(["--openrouter"] if args.openrouter else []),
                                *(["--copilot"] if args.copilot else [])])
    variant = "nativeRelease" if args.configuration == "release" else "nativeDebug"
    apk = ROOT / f"app/build/outputs/apk/{variant}/app-{variant}.apk"
    if not apk.is_file():
        raise SystemExit(f"Build :app:assemble{variant[0].upper() + variant[1:]} first")
    apk_hash = hashlib.sha256(apk.read_bytes()).hexdigest()
    print(adb("devices", "-l").stdout, flush=True)
    existing = adb("shell", "pm", "path", PACKAGE, check=False)
    if "package:" in existing.stdout:
        raise SystemExit("Test package already installed; refusing to replace existing data")
    suffix = "-release" if args.configuration == "release" else ""
    report_path = ROOT / f"build/native/device-report-{args.abi}{suffix}.json"
    run_id = str(uuid.uuid4())
    try:
        print(f"Installing {apk.stat().st_size // 1024 // 1024} MiB {args.abi} APK…", flush=True)
        print(adb("install", "--abi", args.abi, str(apk), timeout=900).stdout, flush=True)
        adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/com.codexbar.android.NativeCliSmokeActivity",
            "--es", "run_id", run_id, "--ez", "test_go", str(args.opencode_go).lower(),
            "--ez", "test_openrouter", str(args.openrouter).lower(),
            "--ez", "test_copilot", str(args.copilot).lower())
        deadline = time.monotonic() + 210
        while time.monotonic() < deadline:
            # Release must stay non-debuggable. Read only this run's synthetic report from logcat.
            result = (adb("logcat", "-d", "-v", "raw", "-s", "CodexBarNative:I", "*:S", check=False)
                      if args.configuration == "release" else
                      adb("shell", "run-as", PACKAGE, "cat", "files/native-report.json", check=False))
            if result.returncode == 0:
                candidates = result.stdout.splitlines() if args.configuration == "release" else [result.stdout]
                for candidate in candidates:
                    try:
                        report = json.loads(candidate)
                    except json.JSONDecodeError:
                        continue
                    if not isinstance(report, dict) or report.get("runId") != run_id:
                        continue
                    report.update(apkSha256=apk_hash, apkBytes=apk.stat().st_size,
                                  abi=args.abi, configuration=args.configuration)
                    for enabled, provider, name in ((args.opencode_go, "go", "OpenCode Go"),
                                                    (args.openrouter, "openrouter", "OpenRouter"),
                                                    (args.copilot, "copilot", "GitHub Copilot")):
                        if enabled and report.get("passed"):
                            try:
                                report[f"{provider}SettingsPassed"] = verify_api_settings(run_id, provider, name)
                            except Exception as error:
                                report[f"{provider}SettingsPassed"] = False
                                report["uiError"] = str(error)
                                report["passed"] = False
                    report_path.parent.mkdir(parents=True, exist_ok=True)
                    report_path.write_text(json.dumps(report, indent=2) + "\n")
                    for probe in report.get("results", []):
                        print(f"{probe['name']}: passed={probe['passed']}, exit={probe.get('exitCode', 'n/a')}", flush=True)
                    print(f"Report: {report_path}", flush=True)
                    return 0 if report.get("passed") else 1
            time.sleep(2)
        raise SystemExit("No report within 210 seconds; runtime acceptance failed")
    finally:
        # Even a timed-out install may have installed the package; clean it under the same lock.
        adb("shell", "am", "force-stop", PACKAGE, check=False)
        print(adb("uninstall", PACKAGE, timeout=60, check=False).stdout, flush=True)


if __name__ == "__main__":
    sys.exit(main())

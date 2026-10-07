#!/usr/bin/env python3
"""Refresh checked-in license assets from the pinned build inputs, or verify offline."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/licenses"
REVISION = "cb5f0cbe88615a441272c6f9e44675bf594a3fa3"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tools", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    manifest = ASSETS / "sources.json"
    if args.check:
        records = json.loads(manifest.read_text())
        assert records and {p.name for p in ASSETS.glob('*.txt')} == set(records)
        for name, record in records.items():
            assert hashlib.sha256((ASSETS / name).read_bytes()).hexdigest() == record["sha256"], name
        assert (ASSETS / "Android-origin.txt").read_bytes() == (ROOT / "LICENSE").read_bytes()
        assert (ASSETS / "About.txt").read_bytes() == (ROOT / "NOTICE.md").read_bytes()
        print(f"Verified {len(records)} packaged notice files")
        return
    if not args.tools:
        parser.error("--tools is required to regenerate native notices")
    source = ROOT / "upstream-codexbar"
    assert subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip() == REVISION
    entries = {}

    def local(name, path, origin):
        entries[name] = (path.read_bytes(), origin)

    def remote(name, repo, revision, path):
        url = f"https://raw.githubusercontent.com/{repo}/{revision}/{path}"
        with urllib.request.urlopen(url, timeout=30) as response:
            entries[name] = (response.read(), url)

    local("About.txt", ROOT / "NOTICE.md", "NOTICE.md")
    local("Android-origin.txt", ROOT / "LICENSE", "LICENSE")
    local("CodexBar-Core-CLI.txt", source / "LICENSE", f"https://github.com/steipete/CodexBar/blob/{REVISION}/LICENSE")
    local("QuickJS.txt", source / "Sources/CQuickJS/LICENSE", f"https://github.com/steipete/CodexBar/blob/{REVISION}/Sources/CQuickJS/LICENSE")
    pins = {p["identity"]: p for p in json.loads((source / "Package.resolved").read_text())["pins"]}
    for package in ("Commander", "SweetCookieKit", "swift-crypto", "swift-asn1", "swift-log"):
        checkout = ROOT / "build/native/arm64-v8a/checkouts" / package
        revision = subprocess.check_output(["git", "-C", str(checkout), "rev-parse", "HEAD"], text=True).strip()
        pin = pins[package.lower()]
        assert revision == pin["state"]["revision"], package
        origin = pin["location"]
        assert origin.startswith("https://github.com/") and "@" not in origin
        for path in sorted(checkout.iterdir()):
            if path.is_file() and path.name.lower().startswith(("license", "notice")):
                local(f"{package}-{path.stem}.txt", path, f"{origin.removesuffix('.git')}/blob/{revision}/{path.name}")
    sdk = args.tools / "sdk-bundles/swift-6.4.0-RELEASE_android.artifactbundle"
    local("Swift-runtime.txt", sdk / "swift-android/swift-resources/usr/share/swift/LICENSE.txt", "Swift Android SDK 6.4.0: usr/share/swift/LICENSE.txt")
    local("NDK-runtime.txt", args.tools / "ndk/android-ndk-r30/toolchains/llvm/prebuilt/linux-x86_64/NOTICE", "Android NDK r30: LLVM NOTICE (includes additional toolchain notices)")
    for repo, path in (("swift-corelibs-foundation", "LICENSE"), ("swift-corelibs-libdispatch", "LICENSE"), ("swift-foundation", "LICENSE.md"), ("swift-foundation", "NOTICE.txt"), ("swift-foundation-icu", "LICENSE.md")):
        remote(f"{repo}-{Path(path).stem}.txt", f"swiftlang/{repo}", "swift-6.4.0-RELEASE", path)
    for name, repo, revision, path in (
        ("ICU", "unicode-org/icu", "release-76-1", "LICENSE"),
        ("curl", "curl/curl", "curl-8_9_1", "COPYING"),
        ("libxml2", "GNOME/libxml2", "v2.11.5", "Copyright"),
        ("BoringSSL", "google/boringssl", "fips-20220613", "LICENSE"),
    ):
        remote(f"{name}.txt", repo, revision, path)
    # SQLite is public domain; retain the original source's dedication verbatim.
    sqlite = ROOT / "build/native/sqlite/arm64-v8a/sqlite3.h"
    local("SQLite.txt", sqlite, "https://www.sqlite.org/2025/sqlite-amalgamation-3500400.zip")
    body, origin = entries["SQLite.txt"]
    entries["SQLite.txt"] = (body[:body.index(b"*/") + 2] + b"\n", origin)
    ASSETS.mkdir(parents=True, exist_ok=True)
    records = {}
    for name, (body, origin) in sorted(entries.items()):
        assert body and b"<html" not in body.lower(), name
        (ASSETS / name).write_bytes(body)
        records[name] = {"source": origin, "sha256": hashlib.sha256(body).hexdigest()}
    manifest.write_text(json.dumps(records, indent=2) + "\n")
    print(f"Generated {len(records)} notice files; review sources and hashes before committing")


if __name__ == "__main__":
    main()

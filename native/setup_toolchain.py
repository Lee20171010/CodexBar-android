#!/usr/bin/env python3
"""Download, verify and extract the pinned official tools into a local build directory."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def verify(archive, expected):
    with archive.open("rb") as stream:
        actual = hashlib.file_digest(stream, "sha256").hexdigest()
    if actual != expected:
        raise ValueError(f"Checksum mismatch: {archive.name}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tools", type=Path, default=ROOT / "build/swift-android")
    parser.add_argument("--verify-only", action="store_true", help="Verify cached archives without downloading or extracting")
    args = parser.parse_args()
    tools = args.tools.resolve()
    downloads = tools / "downloads"
    downloads.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((Path(__file__).parent / "toolchains.json").read_text())
    for name in ("hostToolchain", "androidSdk", "androidNdk"):
        spec = manifest[name]
        archive = downloads / spec["url"].rsplit("/", 1)[1]
        if not archive.exists() and not args.verify_only:
            temporary = archive.with_suffix(archive.suffix + ".partial")
            print(f"Downloading {archive.name}", flush=True)
            try:
                with urllib.request.urlopen(spec["url"], timeout=90) as response, temporary.open("wb") as output:
                    shutil.copyfileobj(response, output)
                verify(temporary, spec["sha256"])
                temporary.replace(archive)
            finally:
                temporary.unlink(missing_ok=True)
        verify(archive, spec["sha256"])
        print(f"Verified {archive.name}", flush=True)
        if args.verify_only:
            continue
        destination = tools / spec["directory"]
        destination.mkdir(parents=True, exist_ok=True)
        if archive.suffix == ".zip":
            subprocess.run(["unzip", "-q", "-o", str(archive), "-d", str(destination)], check=True)
        else:
            subprocess.run(["tar", "-xzf", str(archive), "-C", str(destination)], check=True)
    print(f"Tool root: {tools}")


if __name__ == "__main__":
    main()

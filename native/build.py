#!/usr/bin/env python3
"""Build the pinned upstream Core/CLI with the official Swift Android SDK."""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import shutil
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REVISION = "cb5f0cbe88615a441272c6f9e44675bf594a3fa3"  # v0.71.0
SQLITE_VERSION = "3500400"
SQLITE_SHA256 = "1d3049dd0f830a025a53105fc79fd2ab9431aea99e137809d064d8ee8356b032"


def build_sqlite(ndk, arch, abi):
    """Bundle upstream's existing SQLite dependency; Android exposes no public NDK SQLite API."""
    directory = ROOT / "build/native/sqlite" / abi
    directory.mkdir(parents=True, exist_ok=True)
    archive = directory.parent / f"sqlite-amalgamation-{SQLITE_VERSION}.zip"
    if not archive.exists():
        with urllib.request.urlopen(f"https://www.sqlite.org/2025/{archive.name}") as response:
            archive.write_bytes(response.read())
    if hashlib.sha256(archive.read_bytes()).hexdigest() != SQLITE_SHA256:
        raise SystemExit("SQLite archive checksum mismatch")
    with zipfile.ZipFile(archive) as zipped:
        for name in ("sqlite3.c", "sqlite3.h"):
            destination = directory / name
            content = zipped.read(f"sqlite-amalgamation-{SQLITE_VERSION}/{name}")
            if not destination.exists() or destination.read_bytes() != content:
                destination.write_bytes(content)
    library = directory / "libsqlite3.a"
    inputs = [directory / "sqlite3.c", directory / "sqlite3.h", Path(__file__), ndk / "source.properties"]
    if library.exists() and library.stat().st_mtime_ns > max(path.stat().st_mtime_ns for path in inputs):
        return directory
    bin_dir = ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin"
    subprocess.run([str(bin_dir / f"{arch}-linux-android28-clang"), "-Os", "-fPIC",
                    "-ffunction-sections", "-fdata-sections",
                    "-DSQLITE_THREADSAFE=1", "-c", str(directory / "sqlite3.c"),
                    "-o", str(directory / "sqlite3.o")], check=True)
    subprocess.run([str(bin_dir / "llvm-ar"), "rcs", str(directory / "libsqlite3.a"),
                    str(directory / "sqlite3.o")], check=True)
    return directory


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tools", type=Path, required=True,
                        help="Extracted Swift 6.4.0 toolchain, Android SDK and NDK r30 root")
    parser.add_argument("--abi", choices=("x86_64", "arm64-v8a"), default="x86_64")
    parser.add_argument("--configuration", choices=("debug", "release"), default="debug")
    args = parser.parse_args()
    tools = args.tools.resolve()
    swift = tools / "toolchain/swift-6.4.0-RELEASE-ubuntu24.04/usr/bin/swift"
    ndk = tools / "ndk/android-ndk-r30"
    sdks = tools / "sdk-bundles"
    for required in (swift, ndk / "source.properties", sdks):
        if not required.exists():
            parser.error(f"Missing toolchain component: {required}")
    source = ROOT / "upstream-codexbar"
    if not source.exists():
        subprocess.run(["git", "clone", "--depth", "1", "--branch", "v0.71.0",
                        "https://github.com/steipete/CodexBar.git", str(source)], check=True)
    revision = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if revision != REVISION:
        parser.error(f"Upstream revision mismatch: {revision}; expected {REVISION}")
    patch = ROOT / "native/android.patch"
    git = ["git", "-C", str(source), "apply"]
    if subprocess.run(git + ["--check", "--reverse", str(patch)], capture_output=True).returncode != 0:
        subprocess.run(git + ["--check", str(patch)], check=True)
        subprocess.run(git + [str(patch)], check=True)
    environment = os.environ.copy()
    environment["ANDROID_NDK_HOME"] = str(ndk)
    compatibility = tools / "compat/usr/lib/x86_64-linux-gnu"
    if compatibility.is_dir():
        environment["LD_LIBRARY_PATH"] = str(compatibility)
    arch = "aarch64" if args.abi == "arm64-v8a" else "x86_64"
    sqlite = build_sqlite(ndk, arch, args.abi)
    environment["CODEXBAR_SQLITE3_LIB_DIR"] = str(sqlite)
    optimization = ["-Xswiftc", "-Osize", "-Xlinker", "--gc-sections"] if args.configuration == "release" else []
    subprocess.run([
        str(swift), "build", "--package-path", str(source),
        "--configuration", args.configuration,
        "--scratch-path", str(ROOT / "build/native" / args.abi),
        "--swift-sdks-path", str(sdks), "--swift-sdk", "swift-6.4.0-RELEASE_android",
        "--triple", f"{arch}-unknown-linux-android28", "--static-swift-stdlib",
        "--product", "CodexBarCLI", "--jobs", "2",
        "-Xcc", f"-I{sqlite}", "-Xlinker", f"-L{sqlite}",
        *optimization,
    ], env=environment, check=True)
    product = ROOT / "build/native" / args.abi / f"out/Products/{args.configuration.title()}-android-{arch}"
    package = ROOT / "build/native/package"
    if args.configuration == "release":
        package /= "release"
    package /= args.abi
    libraries = package / "jniLibs" / args.abi
    libraries.mkdir(parents=True, exist_ok=True)
    llvm = ndk / "toolchains/llvm/prebuilt/linux-x86_64"
    strip_mode = "--strip-all" if args.configuration == "release" else "--strip-debug"
    subprocess.run([str(llvm / "bin/llvm-strip"), strip_mode, "-o",
                    str(libraries / "libcodexbar.so"), str(product / "CodexBarCLI")], check=True)
    subprocess.run([str(llvm / "bin/llvm-strip"), strip_mode, "-o", str(libraries / "libc++_shared.so"),
                    str(llvm / f"sysroot/usr/lib/{arch}-linux-android/libc++_shared.so")], check=True)
    assets = package / "assets/codexbar"
    assets.mkdir(parents=True, exist_ok=True)
    bundle = assets / "CodexBar_CodexBarCore.bundle"
    if bundle.exists():
        shutil.rmtree(bundle)
    shutil.copytree(product / "CodexBar_CodexBarCore.bundle", bundle)
    (assets / "VERSION").write_text("0.71.0\n")
    print(f"Native payload: {package}")


if __name__ == "__main__":
    main()

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TEST_OUT="$(mktemp -d)"
trap 'rm -rf "$TEST_OUT"' EXIT
javac --release 11 -encoding UTF-8 -d "$TEST_OUT" \
    src/io/github/liuran001/mmliquidglass/NavigationProfile.java \
    src/io/github/liuran001/mmliquidglass/HostApp.java \
    tests/io/github/liuran001/mmliquidglass/HostProfilesTest.java
java -cp "$TEST_OUT" io.github.liuran001.mmliquidglass.HostProfilesTest

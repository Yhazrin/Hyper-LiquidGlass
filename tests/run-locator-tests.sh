#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TEST_OUT="$(mktemp -d)"
trap 'rm -rf "$TEST_OUT"' EXIT
SOURCE_ROOT="${BENCH_SOURCE_ROOT:-$PWD}"
mapfile -t FIXTURES < <(find tests/jvm -name '*.java' ! -name '*Test.java' -print)
if [ "${1:-LocatorBenchmark}" = LocatorRegressionTest ]; then
    FIXTURES+=(tests/jvm/io/github/liuran001/mmliquidglass/LocatorRegressionTest.java)
fi
javac --release 11 -encoding UTF-8 -d "$TEST_OUT" \
    "$SOURCE_ROOT/src/io/github/liuran001/mmliquidglass/NavigationProfile.java" \
    "$SOURCE_ROOT/src/io/github/liuran001/mmliquidglass/HostApp.java" \
    "$SOURCE_ROOT/src/io/github/liuran001/mmliquidglass/GenericTabLocator.java" \
    "${FIXTURES[@]}"
java -Xms128m -Xmx128m -cp "$TEST_OUT" io.github.liuran001.mmliquidglass."${1:-LocatorBenchmark}"

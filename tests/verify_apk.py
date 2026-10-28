"""Verify packaging of the current build, including DEX definitions and LSPosed scopes."""
import hashlib
from pathlib import Path
import struct
import sys
import zipfile


def dex_classes(data):
    if not data.startswith(b"dex\n"):
        raise ValueError("not a DEX file")
    strings_offset = struct.unpack_from("<I", data, 60)[0]
    types_offset = struct.unpack_from("<I", data, 68)[0]
    count, definitions_offset = struct.unpack_from("<II", data, 96)
    names = set()
    for index in range(count):
        type_index = struct.unpack_from("<I", data, definitions_offset + index * 32)[0]
        string_index = struct.unpack_from("<I", data, types_offset + type_index * 4)[0]
        offset = struct.unpack_from("<I", data, strings_offset + string_index * 4)[0]
        while data[offset] & 0x80:
            offset += 1
        offset += 1  # skip ULEB128 UTF-16 length
        names.add(data[offset:data.index(b"\0", offset)].decode("utf-8"))
    return names


def main():
    root = Path(__file__).resolve().parent.parent
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else root / "LiquidGlass-v0.2.3.apk"
    prefix = "Lio/github/liuran001/mmliquidglass/"
    required = {prefix + name + ";" for name in (
        "LiquidGlassModule", "LiquidGlassInstaller", "TabBarBridge", "LiquidGlassPanel",
        "DropletPanel", "GenericTabLocator", "GenericTabLocator$RowCache", "NavigationProfile")}
    with zipfile.ZipFile(path) as apk:
        assert apk.testzip() is None, "APK CRC mismatch"
        dex = apk.read("classes.dex")
        assert dex == (root / "build/dex/classes.dex").read_bytes(), "APK contains a stale DEX"
        classes = dex_classes(dex)
        assert required <= classes, "missing required production classes"
        assert all(name.startswith(prefix) for name in classes), "test doubles or foreign classes packaged"
        assert not any("Test;" in name or "LocatorFixtures" in name for name in classes), "test fixture packaged"
        packages = {"com.tencent.mm", "com.tencent.mobileqq", "tv.danmaku.bili",
                    "com.ss.android.ugc.aweme", "com.sankuai.meituan", "com.sankuai.meituan.takeoutnew"}
        for name in ("scope.list", "module.scope"):
            target = "META-INF/xposed/" + name
            assert set(apk.read(target).decode().splitlines()) == packages, target
            assert apk.read(target) == (root / target).read_bytes(), "stale scope"
        assert apk.read("META-INF/xposed/java_init.list").decode().strip() == \
            "io.github.liuran001.mmliquidglass.LiquidGlassModule"
    print(f"APK packaging PASS: {len(classes)} production DEX classes, fresh DEX, six-app scopes, no test doubles")
    print("SHA-256:", hashlib.sha256(path.read_bytes()).hexdigest())


if __name__ == "__main__":
    main()

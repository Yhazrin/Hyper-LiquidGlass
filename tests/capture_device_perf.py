"""Capture local Android frame stats and a screenshot. Does not claim a screenshot passes visual review."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import shutil
import subprocess
import time
from frame_stats import NoUsableFrames, acceptance, parse_frame_stats


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package", choices=["com.tencent.mm", "com.tencent.mobileqq", "tv.danmaku.bili",
        "com.ss.android.ugc.aweme", "com.sankuai.meituan", "com.sankuai.meituan.takeoutnew"])
    parser.add_argument("--label", choices=["enabled", "disabled"], required=True,
                        help="Manually set LSPosed first; this is only a run label")
    parser.add_argument("--seconds", type=int, default=20)
    parser.add_argument("--budget-ms", type=float)
    parser.add_argument("--output", type=Path, default=Path("build/device-perf"))
    args = parser.parse_args()
    if not 5 <= args.seconds <= 60:
        parser.error("seconds must be between 5 and 60")
    adb = shutil.which("adb")
    if not adb:
        parser.exit(2, "adb unavailable: device performance and visual validation have not run.\n")

    def run(*command, binary=False):
        return subprocess.check_output([adb, *command], timeout=20, text=not binary)

    try:
        if run("get-state").strip() != "device":
            parser.exit(2, "No authorized Android device; capture not run.\n")
        # Require the intended app to be on screen; never generate blind tap events.
        focus = run("shell", "dumpsys", "window", "windows")
        focus_lines = "\n".join(line for line in focus.splitlines()
                                if "mCurrentFocus" in line or "mFocusedApp" in line)
        if args.package + "/" not in focus_lines:
            parser.exit(2, "Bring the requested app to the foreground before capture.\n")
        version_info = run("shell", "dumpsys", "package", args.package)
        version = re.search(r"versionName=([^\s]+)", version_info)
        run("shell", "dumpsys", "gfxinfo", args.package, "reset")
        print(f"Capturing {args.seconds}s: scroll and switch tabs in {args.package}; leave the bottom bar visible at the end.", flush=True)
        time.sleep(args.seconds)
        focus = run("shell", "dumpsys", "window", "windows")
        if not any(args.package + "/" in line for line in focus.splitlines()
                   if "mCurrentFocus" in line or "mFocusedApp" in line):
            parser.exit(2, "The app left foreground; repeat the capture.\n")
        raw = run("shell", "dumpsys", "gfxinfo", args.package, "framestats")
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
        output = args.output / f"{args.package}-{args.label}-{stamp}"
        output.mkdir(parents=True)
        (output / "framestats.txt").write_text(raw)
        (output / "screen.png").write_bytes(run("exec-out", "screencap", "-p", binary=True))
        metadata = {"package": args.package, "version": version.group(1) if version else None,
                    "label_user_supplied": args.label, "seconds": args.seconds,
                    "android": run("shell", "getprop", "ro.build.version.release").strip(),
                    "visual_review": "pending", "lsposed_state": "not verified by script"}
        try:
            result = parse_frame_stats(raw, args.budget_ms)
            result["timing_passed"] = acceptance(result)
        except NoUsableFrames as error:
            result = {"timing_passed": False, "error": str(error)}
        result["metadata"] = metadata
        (output / "summary.json").write_text(json.dumps(result, ensure_ascii=False, indent=2))
        print(output)
        return 0 if result["timing_passed"] else 1
    except (subprocess.SubprocessError, OSError) as error:
        parser.exit(2, f"Device capture failed: {error}\n")


if __name__ == "__main__":
    raise SystemExit(main())

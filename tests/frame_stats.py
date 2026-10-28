"""Parse Android gfxinfo framestats; these are window frame timings, not GPU-only timings."""
import argparse
import csv
import json
import math
from pathlib import Path


class NoUsableFrames(ValueError):
    pass


def percentile(values, quantile):
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * quantile) - 1)]


def parse_frame_stats(text, fallback_budget_ms=None):
    if fallback_budget_ms is not None and fallback_budget_ms <= 0:
        raise ValueError("fallback budget must be positive")
    windows = {}
    discarded = {"flags": 0, "invalid_timestamps": 0, "missing_deadline": 0}
    header = None
    active = False
    window = "unknown"
    for raw in text.splitlines():
        line = raw.strip()
        if line.startswith("Window:"):
            window = line.partition(":")[2].strip()
        if line == "---PROFILEDATA---":
            active = not active
            header = None
            continue
        if not active:
            continue
        fields = next(csv.reader([line]))
        if "IntendedVsync" in fields and "FrameCompleted" in fields:
            header = fields
            continue
        if header is None or len(fields) < len(header):
            continue
        try:
            frame = {key: int(value) for key, value in zip(header, fields) if key}
            if frame.get("Flags", 0) != 0:
                discarded["flags"] += 1
                continue
            start, end = frame["IntendedVsync"], frame["FrameCompleted"]
            if start <= 0 or end <= start or end >= 2**63 - 1:
                discarded["invalid_timestamps"] += 1
                continue
            deadline = frame.get("FrameDeadline", 0)
            if deadline <= start or deadline >= 2**63 - 1:
                if fallback_budget_ms is None:
                    discarded["missing_deadline"] += 1
                    continue
                deadline = start + fallback_budget_ms * 1_000_000
            samples = windows.setdefault(window, [])
            samples.append(((end - start) / 1_000_000, end > deadline))
        except (ValueError, KeyError):
            discarded["invalid_timestamps"] += 1
    if not windows:
        raise NoUsableFrames("No usable frames; do not treat this capture as a pass. Older Android needs --budget-ms.")
    summaries = []
    for name, samples in windows.items():
        times = [duration for duration, _ in samples]
        misses = sum(missed for _, missed in samples)
        summaries.append({
            "window": name,
            "sample_count": len(samples),
            "p50_ms": percentile(times, .50),
            "p95_ms": percentile(times, .95),
            "p99_ms": percentile(times, .99),
            "max_ms": max(times),
            "missed_deadlines": misses,
            "missed_deadline_pct": misses * 100 / len(samples),
        })
    return {"windows": summaries, "discarded": discarded,
            "sample_count": sum(item["sample_count"] for item in summaries)}


def acceptance(result, minimum_frames=120, max_missed_deadline_pct=5.0):
    # A brief popup must not fail the main window's minimum-sample check, but
    # all windows are retained in the report and checked for deadline misses.
    enough = any(window["sample_count"] >= minimum_frames for window in result["windows"])
    within_limit = all(window["missed_deadline_pct"] <= max_missed_deadline_pct
                       for window in result["windows"])
    return enough and within_limit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path)
    parser.add_argument("--budget-ms", type=float, help="Only for devices without valid FrameDeadline; specify the real refresh budget")
    parser.add_argument("--minimum-frames", type=int, default=120)
    parser.add_argument("--max-missed-deadline-pct", type=float, default=5.0)
    args = parser.parse_args()
    if args.minimum_frames <= 0 or not 0 <= args.max_missed_deadline_pct <= 100:
        parser.error("invalid acceptance thresholds")
    try:
        result = parse_frame_stats(args.capture.read_text(), args.budget_ms)
    except (NoUsableFrames, ValueError) as error:
        parser.exit(2, str(error) + "\n")
    result["passed"] = acceptance(result, args.minimum_frames, args.max_missed_deadline_pct)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())

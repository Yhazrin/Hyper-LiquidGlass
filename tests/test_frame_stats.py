import unittest
from frame_stats import NoUsableFrames, acceptance, parse_frame_stats


def capture(rows, header="Flags,IntendedVsync,FrameCompleted,FrameDeadline"):
    return "Window: app/Main\n---PROFILEDATA---\n" + header + "\n" + "\n".join(rows) + "\n---PROFILEDATA---\n"


class FrameStatsTests(unittest.TestCase):
    def test_timing_and_deadline(self):
        result = parse_frame_stats(capture([
            "0,100000000,105000000,108333333",
            "0,200000000,210000000,208333333",
            "0,300000000,306000000,308333333",
        ]))
        stats = result["windows"][0]
        self.assertEqual(stats["sample_count"], 3)
        self.assertEqual(stats["p50_ms"], 6)
        self.assertEqual(stats["p95_ms"], 10)
        self.assertEqual(stats["missed_deadlines"], 1)
        self.assertFalse(acceptance(result, 3, 5))
        self.assertTrue(acceptance(result, 3, 34))
        self.assertFalse(acceptance(result, 120, 34))

    def test_invalid_and_flagged_frames_not_passes(self):
        with self.assertRaises(NoUsableFrames):
            parse_frame_stats(capture(["1,100,105,108", "0,100,0,108",
                                       "0,100,9223372036854775807,108"]))
        with self.assertRaises(NoUsableFrames):
            parse_frame_stats("")

    def test_no_assumed_60hz(self):
        raw = capture(["0,100000000,110000000"], "Flags,IntendedVsync,FrameCompleted")
        with self.assertRaises(NoUsableFrames):
            parse_frame_stats(raw)
        result = parse_frame_stats(raw, 8.333)
        self.assertEqual(result["windows"][0]["missed_deadlines"], 1)
        result = parse_frame_stats(raw, 16.667)
        self.assertEqual(result["windows"][0]["missed_deadlines"], 0)

    def test_multiple_windows(self):
        raw = capture(["0,100,105,108"]) + capture(["0,200,209,208"]).replace("app/Main", "app/Popup")
        result = parse_frame_stats(raw)
        self.assertEqual(len(result["windows"]), 2)
        self.assertEqual(result["sample_count"], 2)
        self.assertFalse(acceptance(result, 1, 5))

    def test_discard_counts(self):
        raw = capture(["0,100,105,108", "1,200,205,208", "0,300,0,308", "0,400,405,0"])
        result = parse_frame_stats(raw)
        self.assertEqual(result["sample_count"], 1)
        self.assertEqual(result["discarded"], {"flags": 1, "invalid_timestamps": 1, "missing_deadline": 1})


if __name__ == "__main__":
    unittest.main()

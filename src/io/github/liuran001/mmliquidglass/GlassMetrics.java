package io.github.liuran001.mmliquidglass;

/** UI-thread counters: one log on pause, never per frame. Not a power meter.
 * Timings cover synchronous Java recording only, not asynchronous GPU work.
 */
final class GlassMetrics {
    private static long startedMs, panels, panelNs, reads, readNs, frames;
    private GlassMetrics() { }
    static void begin() {
        if (startedMs != 0L) return;
        panels = panelNs = reads = readNs = frames = 0L;
        startedMs = android.os.SystemClock.uptimeMillis();
    }
    static void panel(long ns) { panels++; panelNs += ns; }
    static void semantics(long ns) { reads++; readNs += ns; }
    static void animationFrame() { frames++; }
    static void end() {
        if (startedMs == 0L) return;
        LiquidGlassModule.log(android.util.Log.INFO, "glass_metrics app=" + LiquidGlassModule.app()
                + " active_ms=" + (android.os.SystemClock.uptimeMillis() - startedMs)
                + " panel_draws=" + panels + " panel_record_ns=" + panelNs
                + " semantics_reads=" + reads + " semantics_ns=" + readNs
                + " animation_frames=" + frames + " paused=true");
        startedMs = 0L;
    }
}

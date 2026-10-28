package android.os;
public final class SystemClock {
    private static long fixed = -1;
    public static long uptimeMillis() { return fixed >= 0 ? fixed : System.nanoTime()/1_000_000; }
    public static void setTime(long value) { fixed=value; }
}

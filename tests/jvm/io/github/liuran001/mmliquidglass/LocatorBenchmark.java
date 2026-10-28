package io.github.liuran001.mmliquidglass;
import android.view.ViewGroup;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
/** Measures real production discovery code against deterministic JVM view doubles, not GPU/ART. */
public final class LocatorBenchmark {
    private static volatile Object sink;
    private static final com.sun.management.ThreadMXBean MEMORY =
        (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static void measure(String name, int count, java.util.function.Supplier<Object> work) {
        for(int i=0;i<3000;i++) sink=work.get();
        long[] ns=new long[31]; long allocated=0;
        long thread=Thread.currentThread().getId();
        for(int batch=0;batch<ns.length;batch++) {
            long bytes=MEMORY.getThreadAllocatedBytes(thread);
            long start=System.nanoTime();
            for(int i=0;i<count;i++) sink=work.get();
            ns[batch]=(System.nanoTime()-start)/count;
            allocated+=MEMORY.getThreadAllocatedBytes(thread)-bytes;
        }
        Arrays.sort(ns);
        System.out.printf(Locale.ROOT,"%s,%d,%d,%.1f%n",name,ns[15],ns[29],allocated/(double)(count*ns.length));
    }
    public static void main(String[] args) {
        ViewGroup root=LocatorFixtures.screen(100);
        ViewGroup row=(ViewGroup)root.getChildAt(1);
        String[][] labels={{"首页"},{"朋友"},{""},{"消息"},{"我"}};
        System.out.println("scenario,median_batch_ns_per_op,p95_batch_ns_per_op,allocated_bytes_per_op");
        measure("profile_valid",3000,()->LocatorFixtures.PROFILE.matches(labels));
        measure("locate_613_nodes",200,()->GenericTabLocator.locate(root,LocatorFixtures.PROFILE));
        // Freeze time so this measures steady-state lookups within one validation interval.
        android.os.SystemClock.setTime(1000);
        measure("findRow_stable",3000,()->GenericTabLocator.findRow(row,LocatorFixtures.PROFILE));
        long[] frames={0L};
        measure("findRow_120fps_with_revalidation",3000,()->{
            android.os.SystemClock.setTime(2000L + frames[0]++ * 1000L / 120L);
            return GenericTabLocator.findRow(row,LocatorFixtures.PROFILE);
        });
    }
}

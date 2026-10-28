package io.github.liuran001.mmliquidglass;

import android.os.SystemClock;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public final class LocatorRegressionTest {
    private static int checks;
    private static void check(boolean ok, String name) {
        checks++;
        if (!ok) throw new AssertionError(name);
    }
    private static void reset() {
        GenericTabLocator.clearCache();
        SystemClock.setTime(1000);
    }
    private static ViewGroup row(FrameLayout screen) { return (ViewGroup) screen.getChildAt(1); }

    // Pre-optimization set-based semantics serve as a differential oracle.
    private static boolean reference(NavigationProfile profile, String[][] tabs) {
        if (tabs.length < profile.minTabs || tabs.length > profile.maxTabs) return false;
        Set<String> found = new HashSet<>();
        int count = 0;
        for (String[] tab : tabs) {
            Set<String> slot = new HashSet<>();
            for (String value : tab) {
                if (value == null) continue;
                for (String label : profile.labels) if (label.equals(value.trim())) slot.add(label);
            }
            if (slot.size() > 1) return false;
            if (!slot.isEmpty()) {
                if (!found.add(slot.iterator().next())) return false;
                count++;
            }
        }
        if (count < Math.max(3, tabs.length - 1)) return false;
        for (String anchor : profile.anchors) if (!found.contains(anchor)) return false;
        return true;
    }

    public static void main(String[] args) {
        NavigationProfile profile = LocatorFixtures.PROFILE;
        reset();
        FrameLayout screen = LocatorFixtures.screen(100);
        ViewGroup bar = row(screen);
        check(GenericTabLocator.locate(screen, profile) == bar, "locate valid 613-node screen");
        check(GenericTabLocator.findRow(bar, profile) == bar, "find visible row");
        for (int i = 0; i < 1000; i++) {
            check(GenericTabLocator.findRow(bar, profile) == bar, "stable cached lookup");
        }
        reset();
        FrameLayout wrapper = LocatorFixtures.frame(0,2220,1080,2400);
        screen.removeView(bar); bar.layout(0,0,1080,180); wrapper.addView(bar); screen.addView(wrapper);
        check(GenericTabLocator.locate(screen, profile) == wrapper, "promote through small FrameLayout wrapper");
        check(GenericTabLocator.findRow(wrapper, profile) == bar, "cache nested row");
        ViewGroup replacement = LocatorFixtures.row(); replacement.layout(0,0,1080,180);
        wrapper.removeView(bar); wrapper.addView(replacement);
        check(GenericTabLocator.findRow(wrapper, profile) == replacement, "detached cached row invalidated immediately");
        LocatorFixtures.label(replacement,0).setText("不是导航");
        SystemClock.setTime(1250);
        check(GenericTabLocator.findRow(wrapper, profile) == null, "label change invalidates at TTL");
        LocatorFixtures.label(replacement,0).setText("首页");
        SystemClock.setTime(1500);
        check(GenericTabLocator.findRow(wrapper, profile) == replacement, "negative cache expires");
        SystemClock.setTime(10);
        check(GenericTabLocator.findRow(wrapper, profile) == replacement, "clock rollback revalidates");
        replacement.setVisibility(android.view.View.INVISIBLE);
        check(GenericTabLocator.findRow(wrapper, profile) == null, "hidden row never returned");

        reset(); screen=LocatorFixtures.screen(0); bar=row(screen);
        screen.addView(LocatorFixtures.row());
        check(GenericTabLocator.locate(screen, profile) == null, "ambiguous navigation rejected");
        reset(); screen=LocatorFixtures.screen(0); bar=row(screen);
        bar.layout(0,400,1080,580);
        check(GenericTabLocator.locate(screen, profile) == null, "top toolbar rejected");
        reset(); screen=LocatorFixtures.screen(0); bar=row(screen);
        screen.getChildAt(0).layout(0,0,1080,2200);
        check(GenericTabLocator.locate(screen, profile) == null, "nonoverlapping content rejected");
        reset(); screen=LocatorFixtures.screen(0); bar=row(screen);
        bar.getChildAt(1).layout(0,0,216,180);
        check(GenericTabLocator.locate(screen, profile) == null, "overlapping items rejected");
        reset(); screen=LocatorFixtures.screen(0); bar=row(screen);
        LocatorFixtures.label(bar,1).setText("首页");
        check(GenericTabLocator.locate(screen, profile) == null, "duplicate tab labels rejected");
        reset(); screen=LocatorFixtures.screen(500);
        check(GenericTabLocator.locate(screen, profile) == null, "exhausted traversal cannot claim a match");
        reset(); screen=LocatorFixtures.screen(0); bar=row(screen);
        FrameLayout descendant=LocatorFixtures.frame(0,0,100,100);
        ((ViewGroup)screen.getChildAt(0)).addView(descendant);
        for (int i=0;i<35;i++) {
            FrameLayout next=LocatorFixtures.frame(0,0,100,100);
            descendant.addView(next);descendant=next;
        }
        check(GenericTabLocator.locate(screen, profile) == null, "excessive depth fails closed");
        reset();
        LiquidGlassHostLayout installed=new LiquidGlassHostLayout();
        installed.layout(0,0,1080,2400);installed.addView(LocatorFixtures.row());
        check(GenericTabLocator.locate(installed, profile) == null, "do not rediscover installed glass");

        Random random=new Random(20261028);
        String[] pool={null,"","首页","朋友","消息","我","商城","动态","我的","发布", " 首页 ", "首页推荐", "\t消息\n"};
        for(HostApp host:new HostApp[]{HostApp.DOUYIN,HostApp.MEITUAN,HostApp.WAIMAI,HostApp.BILIBILI}) {
            for(int i=0;i<25000;i++) {
                String[][] tabs=new String[random.nextInt(8)][];
                for(int j=0;j<tabs.length;j++) {
                    tabs[j]=new String[random.nextInt(4)];
                    for(int k=0;k<tabs[j].length;k++) tabs[j][k]=pool[random.nextInt(pool.length)];
                }
                check(host.navigationProfile.matches(tabs)==reference(host.navigationProfile,tabs), "differential seed "+host.pkg+":"+i);
            }
        }
        check(profile.labelMask(new StringBuilder(" 首页 "))==profile.labelMask("首页"), "CharSequence normalization");
        System.out.println("Passed "+checks+" locator/cache/differential checks (JVM fixtures, no GPU)");
    }
}

package io.github.liuran001.mmliquidglass;

/** Runs with a plain JDK; no Android device or mock Android methods required. */
public final class HostProfilesTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static String[][] tabs(String... names) {
        String[][] result = new String[names.length][];
        for (int i = 0; i < names.length; i++) result[i] = new String[]{names[i]};
        return result;
    }
    public static void main(String[] args) {
        check(HostApp.DOUYIN.navigationProfile.matches(tabs("首页", "朋友", "", "消息", "我")), "publish slot");
        check(HostApp.MEITUAN.navigationProfile.matches(tabs("首页", "视频", "消息", "我的")), "Meituan");
        check(HostApp.WAIMAI.navigationProfile.matches(tabs("首页", "消息", "订单", "我的")), "Waimai");
        check(HostApp.WAIMAI.navigationProfile.matches(tabs("外卖", "订单", "我的")), "three-tab Waimai");
        check(HostApp.BILIBILI.navigationProfile.matches(tabs("首页", "动态", "会员购", "我的")), "Bilibili");
        check(!HostApp.DOUYIN.navigationProfile.matches(tabs("首页", "动态", "会员购", "我的")), "wrong app");
        check(!HostApp.MEITUAN.navigationProfile.matches(tabs("首页", "我的")), "too few tabs");
        check(!HostApp.MEITUAN.navigationProfile.matches(tabs("首页", "消息", "我的", "订单", "发现", "视频")), "too many tabs");
        check(!HostApp.DOUYIN.navigationProfile.matches(tabs("首页", "", "", "消息", "我")), "too many unknown slots");
        check(!HostApp.DOUYIN.navigationProfile.matches(tabs("首页", "首页", "消息", "我")), "duplicate labels");
        check(!HostApp.DOUYIN.navigationProfile.matches(new String[][]{{"首页", "朋友"}, {"消息"}, {"我"}, {"发布"}}), "wrapper masquerading as tab");
        check(HostApp.DOUYIN.navigationProfile.matches(new String[][]{{" 首页 ", "首页"}, {"朋友"}, {"消息"}, {"我"}}), "text and accessibility duplicate");
        check(!HostApp.DOUYIN.navigationProfile.matches(tabs("首页推荐视频", "朋友", "消息", "我")), "substring is not a navigation label");
        check(!HostApp.DOUYIN.navigationProfile.matches(tabs("首页", "朋友", "消息", "未知")), "required anchor missing");
        for (HostApp host : new HostApp[]{HostApp.WECHAT, HostApp.QQ, HostApp.BILIBILI,
                HostApp.DOUYIN, HostApp.MEITUAN, HostApp.WAIMAI}) {
            check(HostApp.forPackage(host.pkg) == host, "package mapping " + host.pkg);
            check(HostApp.forProcess(host.pkg) == host, "main process " + host.pkg);
            check(HostApp.forProcess(host.pkg + ":push") == null, "secondary process " + host.pkg);
            check(!host.acceptsActivity("other.app", host.launcherActivity), "foreign activity");
        }
        check(HostApp.WECHAT.acceptsActivity(HostApp.WECHAT.pkg, HostApp.WECHAT.launcherActivity), "WeChat home");
        check(!HostApp.WECHAT.acceptsActivity(HostApp.WECHAT.pkg, "other.Activity"), "WeChat detail");
        check(HostApp.forPackage("com.unknown.app") == null, "unknown package");
        check(!HostApp.WECHAT.isExperimental() && !HostApp.QQ.isExperimental(), "original hosts");
        check(HostApp.DOUYIN.isExperimental(), "new hosts marked experimental");
        for (HostApp host : new HostApp[]{HostApp.DOUYIN, HostApp.BILIBILI}) {
            check(host.acceptsActivity(host.pkg, host.launcherActivity), "documented home activity");
            check(!host.acceptsActivity(host.pkg, "some.DetailActivity"), "reject detail activity");
        }
        System.out.println("Passed " + checks + " host/profile checks");
    }
}

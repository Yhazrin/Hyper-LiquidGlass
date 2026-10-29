package io.github.liuran001.mmliquidglass;

import android.content.Context;
import android.content.pm.PackageInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Bilibili 9.16.1 adapter, bound to the shipped container, not every composable.
 * e.invoke owns the background; f.invoke is a navigation click callback.
 * Native labels, badges, click handlers and Compose groups are never replaced.
 */
final class BiliGlass {
    private static final String PACKAGE = "tv.danmaku.bili.khome.widget.bottomtab.";
    private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();
    private static boolean sInstalled;
    private static boolean sReady;
    private static boolean sVersionChecked;
    private static boolean sSupported;

    private static final class Scope {
        final String backgroundUrl;
        int contentDepth;
        Scope(String backgroundUrl) { this.backgroundUrl = backgroundUrl; }
    }

    private BiliGlass() { }

    /** Unknown versions use a fully resolved ABI family, otherwise retain the native bar. */
    static boolean prepare(Context context) {
        if (!sVersionChecked) {
            try {
                PackageInfo info = context.getPackageManager().getPackageInfo("tv.danmaku.bili", 0);
                // The resolved, scoped ABI profile is the gate, not an arbitrary
                // version string. Unknown ABIs retain the app's original bar.
                sSupported = sReady;
                sVersionChecked = true;
                LiquidGlassModule.log(android.util.Log.INFO, "bili adapter: version="
                        + info.versionName + " code=" + info.versionCode
                        + " supported=" + sSupported + " hooksReady=" + sReady);
            } catch (Throwable error) {
                LiquidGlassModule.logErr("bili version unavailable; retaining native bar", error);
            }
        }
        return sSupported && sReady;
    }

    private static boolean isChrome() {
        Scope scope = SCOPE.get();
        return scope != null && scope.contentDepth == 0;
    }

    static void install(ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;
        try {
            // Resolve every required boundary before enabling any substitution.
            // These two ABI families are anchored in the 9.16.1 and 9.17.0
            // APKs respectively. Select by complete structural signature, not
            // by version number. Never partially hook an unrecognised family.
            Profile profile = resolveProfile(cl);
            Method invoke = profile.invoke;
            Field garbField = profile.garb;
            Field backgroundField = profile.background;
            Method normalTab = profile.normal;
            Method publishTab = profile.publish;
            Object companion = cl.loadClass("androidx.compose.ui.graphics.Color").getField("Companion").get(null);
            Method transparentGetter = null;
            for (Method method : companion.getClass().getMethods()) {
                if (method.getName().startsWith("getTransparent-")
                        && method.getParameterCount() == 0 && method.getReturnType() == long.class) {
                    transparentGetter = method;
                    break;
                }
            }
            if (transparentGetter == null) throw new NoSuchMethodException("Color.Transparent");
            final long transparent = ((Number) transparentGetter.invoke(companion)).longValue();
            Method bg = colorMethod(cl, "getBg1-");
            Method line = colorMethod(cl, "getGa2-");
            protectContent(normalTab);
            protectContent(publishTab);
            for (Method method : new Method[]{bg, line}) {
                LiquidGlassModule.hookIntercept(method, chain ->
                        isChrome() ? transparent : chain.proceed());
            }
            // Require skin handling too: never install a pill over unsupported native chrome.
            installSkinAlpha(cl);
            LiquidGlassModule.hookIntercept(invoke, chain -> {
                if (!sSupported || !sReady) return chain.proceed();
                Object garb = garbField.get(chain.getThisObject());
                String url = garb == null ? "" : (String) backgroundField.get(garb);
                Scope previous = SCOPE.get();
                SCOPE.set(new Scope(url));
                try { return chain.proceed(); }
                finally {
                    if (previous == null) SCOPE.remove(); else SCOPE.set(previous);
                }
            });
            sReady = true;
            LiquidGlassModule.log(android.util.Log.INFO,
                    "bili chrome: scoped profile=" + profile.name + "; native tab content protected");
        } catch (Throwable error) {
            sReady = false;
            LiquidGlassModule.logErr("bili adapter unavailable; retaining native bar", error);
        }
    }

    private static final class Profile {
        final String name;
        final Method invoke, normal, publish;
        final Field garb, background;
        Profile(String name, Method invoke, Field garb, Field background,
                Method normal, Method publish) {
            this.name = name; this.invoke = invoke; this.garb = garb;
            this.background = background; this.normal = normal; this.publish = publish;
        }
    }

    private static Profile resolveProfile(ClassLoader cl) throws Exception {
        String[][] families = {{"e", "e", "o", "q"}, {"f", "d", "q", "s"}};
        Profile selected = null;
        for (String[] family : families) {
            try {
                Class<?> body = cl.loadClass(PACKAGE + family[0]);
                Method invoke = body.getDeclaredMethod("invoke", Object.class, Object.class, Object.class);
                if (invoke.getReturnType() != Object.class) continue;
                Field garb = body.getDeclaredField(family[1]);
                if (!garb.getType().getName().equals(PACKAGE + "a")) continue;
                garb.setAccessible(true);
                Field background = garb.getType().getDeclaredField("f");
                if (background.getType() != String.class) continue;
                background.setAccessible(true);
                Method normal = tabMethod(cl.loadClass(PACKAGE + family[2]), 8);
                Method publish = tabMethod(cl.loadClass(PACKAGE + family[3]), 6);
                if (selected != null) throw new IllegalStateException("ambiguous Bili ABI profiles");
                selected = new Profile(family[0] + "/" + family[2] + "/" + family[3],
                        invoke, garb, background, normal, publish);
            } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException ignored) {
                // A family that does not match contributes no hooks.
            }
        }
        if (selected == null) throw new NoSuchMethodException("no supported Bili ABI profile");
        return selected;
    }

    private static Method tabMethod(Class<?> type, int count) throws NoSuchMethodException {
        for (Method method : type.getDeclaredMethods()) {
            Class<?>[] args = method.getParameterTypes();
            if (method.getName().equals("a") && args.length == count
                    && args[0] == int.class && args[count - 1] == int.class
                    && args[count - 2].getName().equals("androidx.compose.runtime.Composer")) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + ".a");
    }

    private static Method colorMethod(ClassLoader cl, String prefix) throws Exception {
        for (Method method : cl.loadClass("com.bilibili.compose.theme.BiliColors").getDeclaredMethods()) {
            if (method.getName().startsWith(prefix) && method.getParameterCount() == 0
                    && method.getReturnType() == long.class) return method;
        }
        throw new NoSuchMethodException(prefix);
    }

    private static void protectContent(Method method) {
        LiquidGlassModule.hookIntercept(method, chain -> {
            Scope scope = SCOPE.get();
            if (scope == null) return chain.proceed();
            scope.contentDepth++;
            try { return chain.proceed(); }
            finally { scope.contentDepth--; }
        });
    }

    private static void installSkinAlpha(ClassLoader cl) throws Exception {
        int installed = 0;
        {
            Class<?> modifier = cl.loadClass("androidx.compose.ui.Modifier");
            Method alpha = cl.loadClass("androidx.compose.ui.draw.AlphaKt")
                    .getMethod("alpha", modifier, float.class);
            for (Method method : cl.loadClass("kntr.base.imageloader.BiliImageKt").getDeclaredMethods()) {
                Class<?>[] args = method.getParameterTypes();
                if (!method.getName().equals("BiliImage") || args.length < 2 || args[1] != modifier) continue;
                Method urlGetter = args[0].getMethod("takeImageUrl");
                installed++;
                LiquidGlassModule.hookIntercept(method, chain -> {
                    Scope scope = SCOPE.get();
                    if (isChrome() && scope.backgroundUrl != null && !scope.backgroundUrl.isEmpty()
                            && scope.backgroundUrl.equals(urlGetter.invoke(chain.getArg(0)))) {
                        Object[] rewritten = chain.getArgs().toArray();
                        rewritten[1] = alpha.invoke(null, rewritten[1], 0f);
                        return chain.proceed(rewritten);
                    }
                    return chain.proceed();
                });
            }
        }
        if (installed == 0) throw new NoSuchMethodException("BiliImage skin boundary");
    }
}

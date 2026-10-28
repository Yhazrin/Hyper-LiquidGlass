package io.github.liuran001.mmliquidglass;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import android.os.SystemClock;

/** Fail-closed discovery for unverified app versions. Never changes a view while searching. */
final class GenericTabLocator {
    private static final int MAX_VISITS = 2500;
    private static final int MAX_DEPTH = 30;

    // UI-thread only. Weak keys AND values keep destroyed Activity trees collectable.
    private static final WeakHashMap<ViewGroup, RowCache> ROW_CACHE = new WeakHashMap<>();
    private static final long ROW_RECHECK_MS = 250L;

    private static final class RowCache {
        final NavigationProfile profile;
        final WeakReference<ViewGroup> row;
        final long checkedAt;
        RowCache(NavigationProfile profile, ViewGroup row, long now) {
            this.profile = profile;
            this.row = new WeakReference<>(row);
            this.checkedAt = now;
        }
    }

    private GenericTabLocator() {}

    static void clearCache() {
        ROW_CACHE.clear();
    }

    private static boolean under(View row, ViewGroup bar) {
        for (int depth = 0; row != null && depth <= MAX_DEPTH; depth++) {
            if (row == bar) return true;
            row = row.getParent() instanceof View ? (View) row.getParent() : null;
        }
        return false;
    }

    static ViewGroup locate(View root, NavigationProfile profile) {
        Search search = new Search(profile, true);
        search.walk(root, 0);
        // Do not choose arbitrarily between an overlay and an underlying home screen.
        if (search.exhausted || search.rows.size() != 1) return null;
        ViewGroup bar = search.rows.get(0);
        final int rowHeight = bar.getHeight();
        while (true) {
            if (supportsLayout(bar)) return bar;
            if (!(bar.getParent() instanceof ViewGroup)) return null;
            ViewGroup wrapper = (ViewGroup) bar.getParent();
            if (wrapper instanceof LiquidGlassHostLayout
                    || wrapper.getHeight() > rowHeight * 1.6f) return null;
            bar = wrapper;
        }
    }

    static ViewGroup findRow(ViewGroup bar, NavigationProfile profile) {
        long now = SystemClock.uptimeMillis();
        RowCache cached = ROW_CACHE.get(bar);
        if (cached != null && cached.profile == profile && now >= cached.checkedAt
                && now - cached.checkedAt < ROW_RECHECK_MS) {
            ViewGroup row = cached.row.get();
            if (row != null && row.isShown() && row.getAlpha() > 0f && under(row, bar)) {
                return row;
            }
            // A negative result is cached too. Detached or hidden positive rows
            // are re-searched immediately rather than used for another frame.
            if (row == null) return null;
        }
        Search search = new Search(profile, false);
        search.walk(bar, 0);
        ViewGroup row = !search.exhausted && search.rows.size() == 1 ? search.rows.get(0) : null;
        ROW_CACHE.put(bar, new RowCache(profile, row, now));
        return row;
    }

    /** The existing renderer requires a separate content sibling under a FrameLayout bar. */
    static boolean supportsLayout(ViewGroup bar) {
        if (!(bar.getParent() instanceof FrameLayout)) return false;
        ViewGroup parent = (ViewGroup) bar.getParent();
        if (parent instanceof LiquidGlassHostLayout) return false;
        for (int i = 0; i < parent.indexOfChild(bar); i++) {
            View c = parent.getChildAt(i);
            if (c instanceof ViewGroup && c.isShown() && c.getAlpha() > 0f
                    && c.getWidth() >= parent.getWidth() * 0.8f
                    && c.getHeight() >= parent.getHeight() * 0.75f
                    && c.getTop() + c.getHeight() >= bar.getTop() + bar.getHeight()) {
                return true;
            }
        }
        return false;
    }

    private static final class Search {
        final NavigationProfile profile;
        final boolean atScreenBottom;
        final List<ViewGroup> rows = new ArrayList<>();
        int visited;
        boolean exhausted;

        Search(NavigationProfile profile, boolean atScreenBottom) {
            this.profile = profile;
            this.atScreenBottom = atScreenBottom;
        }

        void walk(View view, int depth) {
            if (++visited > MAX_VISITS || depth > MAX_DEPTH) {
                exhausted = true;
                return;
            }
            if (!(view instanceof ViewGroup) || !view.isShown()
                    || view.getAlpha() <= 0f || view instanceof LiquidGlassHostLayout) return;
            ViewGroup group = (ViewGroup) view;
            if (matches(group)) rows.add(group);
            if (exhausted) return;
            for (int i = 0; i < group.getChildCount(); i++) {
                walk(group.getChildAt(i), depth + 1);
                if (exhausted) return;
            }
        }

        boolean matches(ViewGroup row) {
            if (row.getWidth() <= 0 || row.getHeight() <= 0) return false;
            float density = row.getResources().getDisplayMetrics().density;
            // Cheap screen geometry and tab counts come before label-tree traversal.
            if (atScreenBottom) {
                View root = row.getRootView();
                if (row.getWidth() < root.getWidth() * 0.7f
                        || row.getHeight() > 112 * density) return false;
                row.getLocationOnScreen(position);
                root.getLocationOnScreen(origin);
                int gap = origin[1] + root.getHeight() - position[1] - row.getHeight();
                if (gap < -2 * density || gap > 96 * density) return false;
            }
            int count = 0;
            int previousRight = Integer.MIN_VALUE;
            int firstWidth = 0;
            int top = -1;
            for (int i = 0; i < row.getChildCount(); i++) {
                View tab = row.getChildAt(i);
                if (tab.getVisibility() == View.GONE) continue;
                if (++count > profile.maxTabs || tab.getVisibility() != View.VISIBLE
                        || tab.getAlpha() <= 0f || tab.getWidth() <= 0
                        || tab.getHeight() < 32 * density || tab.getHeight() > 96 * density
                        || tab.getLeft() < previousRight) return false;
                if (top >= 0 && Math.abs(top - tab.getTop()) > 12 * density) return false;
                if (firstWidth > 0 && (tab.getWidth() < firstWidth * 0.5f
                        || tab.getWidth() > firstWidth * 1.8f)) return false;
                if (firstWidth == 0) firstWidth = tab.getWidth();
                top = tab.getTop();
                previousRight = tab.getRight();
            }
            if (count < profile.minTabs) return false;
            long found = 0L;
            int matched = 0;
            for (int i = 0; i < row.getChildCount(); i++) {
                View tab = row.getChildAt(i);
                if (tab.getVisibility() == View.GONE) continue;
                long slot = collectLabels(tab, 0);
                if (exhausted || (slot & (slot - 1)) != 0 || (found & slot) != 0) return false;
                if (slot != 0L) { found |= slot; matched++; }
            }
            return profile.accepts(found, matched, count);
        }

        // Scratch is per search, never per candidate row or label.
        final int[] position = new int[2];
        final int[] origin = new int[2];

        long collectLabels(View view, int depth) {
            if (++visited > MAX_VISITS || depth > MAX_DEPTH) {
                exhausted = true;
                return 0L;
            }
            if (view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) return 0L;
            long mask = view instanceof TextView
                    ? profile.labelMask(((TextView) view).getText()) : 0L;
            mask |= profile.labelMask(view.getContentDescription());
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    mask |= collectLabels(group.getChildAt(i), depth + 1);
                    if (exhausted || (mask & (mask - 1)) != 0) return mask;
                }
            }
            return mask;
        }
    }
}

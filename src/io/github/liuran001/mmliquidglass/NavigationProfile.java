package io.github.liuran001.mmliquidglass;

/** Semantic signatures for experimental hosts, independent of obfuscated resource IDs. */
final class NavigationProfile {
    final String[] labels;
    final String[] anchors;
    final int minTabs;
    final int maxTabs;
    private final long requiredLabels;

    NavigationProfile(int minTabs, int maxTabs, String[] labels, String... anchors) {
        if (minTabs < 3 || maxTabs < minTabs || labels.length > 63) {
            throw new IllegalArgumentException("invalid navigation profile limits");
        }
        this.minTabs = minTabs;
        this.maxTabs = maxTabs;
        this.labels = labels.clone();
        this.anchors = anchors.clone();
        long required = 0L;
        for (String anchor : anchors) {
            long bit = labelMask(anchor);
            if (bit == 0L) throw new IllegalArgumentException("unknown anchor: " + anchor);
            required |= bit;
        }
        requiredLabels = required;
    }

    /** Exact String.trim-compatible comparison without allocating a String or collection. */
    long labelMask(CharSequence text) {
        if (text == null) return 0L;
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) <= ' ') start++;
        while (end > start && text.charAt(end - 1) <= ' ') end--;
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            if (label.length() != end - start) continue;
            int at = 0;
            while (at < label.length() && label.charAt(at) == text.charAt(start + at)) at++;
            if (at == label.length()) return 1L << i;
        }
        return 0L;
    }

    boolean accepts(long found, int matchedTabs, int tabCount) {
        // Allow one unlabelled central publish button, but require three named tabs.
        return tabCount >= minTabs && tabCount <= maxTabs
                && matchedTabs >= Math.max(3, tabCount - 1)
                && (found & requiredLabels) == requiredLabels;
    }

    boolean matches(String[][] tabs) {
        if (tabs.length < minTabs || tabs.length > maxTabs) return false;
        long found = 0L;
        int matchedTabs = 0;
        for (String[] tab : tabs) {
            long slot = 0L;
            for (String text : tab) slot |= labelMask(text);
            // Multiple labels within one child means a wrapper, not a tab.
            if ((slot & (slot - 1)) != 0 || (found & slot) != 0) return false;
            if (slot != 0L) {
                found |= slot;
                matchedTabs++;
            }
        }
        return accepts(found, matchedTabs, tabs.length);
    }
}

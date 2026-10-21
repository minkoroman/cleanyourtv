package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Joins the continue-watching sources into one row and splits it by tab. */
final class ContinueMerge {

    static final int TAB_ALL = 0;
    static final int TAB_FILMS = 1;
    static final int TAB_SERIES = 2;

    private ContinueMerge() {}

    /** Newest first; an item in both lists keeps its newer copy. */
    static List<ContinueItem> merge(List<ContinueItem> a, List<ContinueItem> b, int limit) {
        Map<String, ContinueItem> byKey = new LinkedHashMap<>();
        List<ContinueItem> all = new ArrayList<>(a);
        all.addAll(b);
        for (ContinueItem item : all) {
            ContinueItem prev = byKey.get(item.key);
            if (prev == null || item.ts >= prev.ts) byKey.put(item.key, item);
        }
        List<ContinueItem> out = new ArrayList<>(byKey.values());
        out.sort((x, y) -> Long.compare(y.ts, x.ts));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    static List<ContinueItem> filter(List<ContinueItem> items, int tab) {
        if (tab == TAB_ALL) return items;
        List<ContinueItem> out = new ArrayList<>();
        for (ContinueItem item : items) {
            if (item.series == (tab == TAB_SERIES)) out.add(item);
        }
        return out;
    }
}

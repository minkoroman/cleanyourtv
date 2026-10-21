package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class ContinueMergeTest {

    private static ContinueItem item(String key, long ts, boolean series) {
        ContinueItem i = new ContinueItem();
        i.key = key;
        i.title = key;
        i.ts = ts;
        i.series = series;
        return i;
    }

    @Test public void merge_newestFirst() {
        List<ContinueItem> l = ContinueMerge.merge(Arrays.asList(item("a", 5, false), item("b", 9, false)),
                Collections.singletonList(item("c", 7, false)), 12);
        assertEquals("b", l.get(0).key);
        assertEquals("c", l.get(1).key);
        assertEquals("a", l.get(2).key);
    }

    @Test public void merge_sameKeyKeepsNewer() {
        List<ContinueItem> l = ContinueMerge.merge(Collections.singletonList(item("a", 3, false)),
                Collections.singletonList(item("a", 8, false)), 12);
        assertEquals(1, l.size());
        assertEquals(8, l.get(0).ts);
    }

    @Test public void merge_limit() {
        List<ContinueItem> many = new ArrayList<>();
        for (int i = 0; i < 15; i++) many.add(item("k" + i, i, false));
        assertEquals(12, ContinueMerge.merge(many, Collections.emptyList(), 12).size());
    }

    @Test public void filter_tabs() {
        List<ContinueItem> l = Arrays.asList(item("film", 2, false), item("show", 1, true));
        assertEquals(2, ContinueMerge.filter(l, ContinueMerge.TAB_ALL).size());
        List<ContinueItem> films = ContinueMerge.filter(l, ContinueMerge.TAB_FILMS);
        assertEquals(1, films.size());
        assertFalse(films.get(0).series);
        List<ContinueItem> shows = ContinueMerge.filter(l, ContinueMerge.TAB_SERIES);
        assertEquals(1, shows.size());
        assertTrue(shows.get(0).series);
    }
}

package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TmdbArtTest {

    @Test public void query_usesOriginalTitle() {
        String url = TmdbArt.searchUrl("Deadpool 2", "Дедпул 2");
        assertTrue(url.startsWith("https://api.themoviedb.org/3/search/multi?"));
        assertTrue(url.contains("query=Deadpool+2"));
    }

    @Test public void query_fallsBackToTitle() {
        assertTrue(TmdbArt.searchUrl("", "Керрі").contains("query=%D0%9A"));
        assertTrue(TmdbArt.searchUrl(null, "Керрі").contains("query=%D0%9A"));
    }

    @Test public void pick_firstResultWithBackdrop() {
        assertEquals("https://image.tmdb.org/t/p/w780/abc.jpg",
                TmdbArt.pickBackdrop("{\"results\":[{\"backdrop_path\":null},{\"backdrop_path\":\"/abc.jpg\"}]}"));
    }

    @Test public void pick_noneFound() {
        assertNull(TmdbArt.pickBackdrop("{\"results\":[]}"));
        assertNull(TmdbArt.pickBackdrop("not json"));
    }
}

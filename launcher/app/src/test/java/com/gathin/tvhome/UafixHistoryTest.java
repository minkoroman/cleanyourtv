package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class UafixHistoryTest {

    private static final String FIXTURE = "{\"history\":["
            + "{\"url\":\"https://uafix.net/films/deadpool-2/\",\"title\":\"Дедпул 2\",\"orig\":\"Deadpool 2\","
            + "\"poster\":\"https://uafix.net/p/1.jpg\",\"season\":\"\",\"label\":\"Фільм\",\"ei\":0,"
            + "\"pos\":600,\"dur\":5000,\"ts\":300},"
            + "{\"url\":\"https://uafix.net/serials/kitpes/\",\"title\":\"КітПес\",\"orig\":\"CatDog\","
            + "\"poster\":\"\",\"season\":\"1 сезон\",\"label\":\"Серія 2\",\"si\":1,\"ei\":2,"
            + "\"pos\":100,\"dur\":1300,\"ts\":200},"
            + "{\"url\":\"https://uafix.net/films/gone/\",\"title\":\"Прибраний\",\"removed\":true,"
            + "\"pos\":10,\"dur\":100,\"ts\":500},"
            + "{\"url\":\"https://uafix.net/films/logan/\",\"title\":\"Лоґан\",\"label\":\"Фільм\","
            + "\"pos\":970,\"dur\":1000,\"ts\":400}"
            + "]}";

    @Test public void parse_keepsUnfinishedAndDropsRemovedAndWatched() {
        List<ContinueItem> l = UafixHistory.parse(FIXTURE);
        assertEquals(2, l.size());
        assertEquals("Дедпул 2", l.get(0).title);
        assertEquals("КітПес", l.get(1).title);
    }

    @Test public void parse_filmFields() {
        ContinueItem film = UafixHistory.parse(FIXTURE).get(0);
        assertFalse(film.series);
        assertNull(film.subtitle);
        assertEquals("uafix", film.source);
        assertEquals("uafix:https://uafix.net/films/deadpool-2/", film.key);
        assertEquals("Deadpool 2", film.orig);
        assertEquals("https://uafix.net/p/1.jpg", film.imageUrl);
        assertEquals(0.12f, film.progress, 0.01f);
        assertEquals(300, film.ts);
    }

    @Test public void parse_seriesFields() {
        ContinueItem ep = UafixHistory.parse(FIXTURE).get(1);
        assertTrue(ep.series);
        assertEquals("Серія 2", ep.subtitle);
    }

    @Test public void parse_seriesWithoutSeasonFields() {
        String json = "{\"history\":[{\"url\":\"https://uafix.net/serials/kerri-serials/\",\"title\":\"Керрі\","
                + "\"season\":\"\",\"label\":\"Серія 1 — Перший урок\",\"si\":0,\"ei\":0,\"pos\":10,\"dur\":100}]}";
        assertTrue(UafixHistory.parse(json).get(0).series);
    }

    @Test public void parse_garbage_returnsEmpty() {
        assertTrue(UafixHistory.parse("<html>").isEmpty());
        assertTrue(UafixHistory.parse(null).isEmpty());
    }
}

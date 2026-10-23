package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Scanner;

import org.junit.Test;

public class OutageTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 23);

    private static Map<LocalDate, PoeSchedule.Day> fixture() {
        InputStream in = OutageTest.class.getResourceAsStream("/poe-2026-10-23.html");
        String html = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
        return PoeSchedule.parse(html);
    }

    @Test public void parse_realPage_queue22() {
        PoeSchedule.Day d = fixture().get(DAY);
        assertEquals(12, d.rows.size());
        assertEquals(48, d.rows.get("2.2").size());
        assertEquals(PoeSchedule.Slot.ON, d.rows.get("2.2").get(0));
        assertEquals(PoeSchedule.Slot.OFF, d.rows.get("2.2").get(12));
        assertEquals(PoeSchedule.Slot.MAYBE, d.rows.get("2.2").get(13));
        assertFalse(d.cancelled);
    }

    @Test public void status_lightOn_untilNextOutage() {
        OutageStatus s = OutageStatus.at(fixture(), "2.2", DAY, LocalTime.of(14, 5));
        assertFalse(s.off);
        assertEquals(LocalTime.of(17, 30), s.until);
    }

    @Test public void status_duringOutage_untilItsEnd() {
        OutageStatus s = OutageStatus.at(fixture(), "2.2", DAY, LocalTime.of(18, 0));
        assertTrue(s.off);
        assertEquals(LocalTime.of(19, 30), s.until);
    }

    @Test public void status_lastOutageRunsToMidnight() {
        OutageStatus s = OutageStatus.at(fixture(), "2.2", DAY, LocalTime.of(23, 45));
        assertTrue(s.off);
        assertEquals(LocalTime.MIDNIGHT, s.until);
    }

    @Test public void status_afterLastOutage_noTimeAndTomorrowUnknown() {
        OutageStatus s = OutageStatus.at(fixture(), "2.2", DAY, LocalTime.of(20, 0));
        assertFalse(s.off);
        assertEquals(LocalTime.of(23, 30), s.until);
        assertNull(s.tomorrowFirst);
        assertFalse(s.tomorrowKnown);
    }

    @Test public void status_unknownQueueOrDay_isNull() {
        assertNull(OutageStatus.at(fixture(), "9.9", DAY, LocalTime.NOON));
        assertNull(OutageStatus.at(fixture(), "2.2", DAY.plusDays(3), LocalTime.NOON));
    }

    @Test public void outages_countsBlocks() {
        assertEquals(4, OutageStatus.outages(fixture().get(DAY).rows.get("2.2")));
        assertEquals(LocalTime.of(6, 0), OutageStatus.firstOff(fixture().get(DAY).rows.get("2.2")));
    }

    @Test public void cancelled_meansNoOutages() {
        String html = "<p>24 жовтня 2026 року застосування графіка погодинного відключення електроенергії "
                + "не прогнозується.</p><p>Оновлено 24 жовтня 2026 07:10</p>";
        PoeSchedule.Day d = PoeSchedule.parse(html).get(DAY.plusDays(1));
        assertTrue(d.cancelled);
        OutageStatus s = OutageStatus.at(PoeSchedule.parse(html), "2.2", DAY.plusDays(1), LocalTime.NOON);
        assertTrue(s.noOutages);
    }

    @Test(expected = IllegalArgumentException.class)
    public void garbage_throws() {
        PoeSchedule.parse("<html>сайт на обслуговуванні</html>");
    }
}

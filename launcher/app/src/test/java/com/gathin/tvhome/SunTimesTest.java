package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.Test;

public class SunTimesTest {

    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    @Test public void kyiv_2026_10_21() {
        int[] s = SunTimes.compute(LocalDate.of(2026, 10, 21), 50.45, 30.52, KYIV);
        assertEquals(7 * 60 + 28, s[0], 2);
        assertEquals(17 * 60 + 56, s[1], 2);
    }

    @Test public void kyiv_afterDstEnds() {
        int[] s = SunTimes.compute(LocalDate.of(2026, 10, 26), 50.45, 30.52, KYIV);
        assertEquals(6 * 60 + 37, s[0], 2);
        assertEquals(16 * 60 + 47, s[1], 2);
    }
}

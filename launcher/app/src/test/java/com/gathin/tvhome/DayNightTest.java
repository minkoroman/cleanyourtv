package com.gathin.tvhome;

import static com.gathin.tvhome.DayNight.Mode.AUTO;
import static com.gathin.tvhome.DayNight.Mode.DARK;
import static com.gathin.tvhome.DayNight.Mode.LIGHT;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DayNightTest {

    @Test public void auto_eveningIsNight() { assertTrue(DayNight.isNight(AUTO, 18 * 60, 435, 1068)); }

    @Test public void auto_noonIsDay() { assertFalse(DayNight.isNight(AUTO, 12 * 60, 435, 1068)); }

    @Test public void auto_beforeSunriseIsNight() { assertTrue(DayNight.isNight(AUTO, 3 * 60, 435, 1068)); }

    @Test public void light_ignoresClock() { assertFalse(DayNight.isNight(LIGHT, 23 * 60, 435, 1068)); }

    @Test public void dark_ignoresClock() { assertTrue(DayNight.isNight(DARK, 12 * 60, 435, 1068)); }
}

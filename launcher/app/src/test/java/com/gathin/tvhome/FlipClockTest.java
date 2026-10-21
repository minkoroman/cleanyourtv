package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FlipClockTest {

    @Test public void digits_padsHourAndMinute() {
        assertEquals("0905", FlipClockView.digits(9, 5));
        assertEquals("2314", FlipClockView.digits(23, 14));
    }

    @Test public void changed_marksOnlyTheDigitsThatFlip() {
        assertArrayEquals(new boolean[]{false, false, false, true}, FlipClockView.changed("2314", "2315"));
        assertArrayEquals(new boolean[]{true, true, true, true}, FlipClockView.changed("2359", "0000"));
    }

    @Test public void nextSlide_wrapsAndHandlesEmpty() {
        assertEquals(1, ClockScene.nextSlide(0, 3));
        assertEquals(0, ClockScene.nextSlide(2, 3));
        assertEquals(-1, ClockScene.nextSlide(0, 0));
    }
}

package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlassMovedTest {

    @Test public void sameSpot_withinTolerance_isNotMoved() {
        assertFalse(GridNavigation.glassMoved(100f, 200f, 100.6f, 199.5f, 1f));
    }

    @Test public void tileShiftedByLayout_isMoved() {
        assertTrue(GridNavigation.glassMoved(100f, 1020f, 100f, 520f, 1f));
        assertTrue(GridNavigation.glassMoved(100f, 200f, 140f, 200f, 1f));
    }
}

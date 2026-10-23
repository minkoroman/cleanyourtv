package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RefreshDueTest {

    @Test public void neverFetched_isDue() {
        assertTrue(ContinueRepository.refreshDue(1_000_000L, 0L));
    }

    @Test public void backToHomeAfterAMinute_isDue() {
        assertTrue(ContinueRepository.refreshDue(1_060_000L, 1_000_000L));
    }

    @Test public void focusFlickerWithinSeconds_isNotDue() {
        assertFalse(ContinueRepository.refreshDue(1_010_000L, 1_000_000L));
    }
}

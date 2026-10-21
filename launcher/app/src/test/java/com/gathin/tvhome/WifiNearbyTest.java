package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class WifiNearbyTest {

    @Test public void list_strongestFirst_withoutCurrentDuplicatesOrHidden() {
        List<WifiNearby.Network> l = WifiNearby.list(Arrays.asList(
                new WifiNearby.Network("Home", -22, true),
                new WifiNearby.Network("Home_5G", -37, true),
                new WifiNearby.Network("Sosid", -80, true),
                new WifiNearby.Network("Sosid", -60, true),
                new WifiNearby.Network("", -30, false),
                new WifiNearby.Network("Kafe", -70, false)), "Home", 5);
        assertEquals(3, l.size());
        assertEquals("Home_5G", l.get(0).ssid);
        assertEquals("Sosid", l.get(1).ssid);
        assertEquals(-60, l.get(1).rssi);
        assertEquals("Kafe", l.get(2).ssid);
    }

    @Test public void list_limit() {
        assertEquals(1, WifiNearby.list(Arrays.asList(new WifiNearby.Network("a", -40, true),
                new WifiNearby.Network("b", -50, true)), null, 1).size());
    }

    @Test public void signal_wordsByStrength() {
        assertEquals(WifiNearby.EXCELLENT, WifiNearby.strength(-45));
        assertEquals(WifiNearby.GOOD, WifiNearby.strength(-62));
        assertEquals(WifiNearby.FAIR, WifiNearby.strength(-72));
        assertEquals(WifiNearby.WEAK, WifiNearby.strength(-85));
    }
}

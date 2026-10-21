package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The Wi-Fi menu's list of networks around the TV, and signal strength in words. Pure, for JVM tests. */
final class WifiNearby {

    static final int EXCELLENT = 3, GOOD = 2, FAIR = 1, WEAK = 0;

    static final class Network {
        final String ssid;
        final int rssi;
        final boolean secured;

        Network(String ssid, int rssi, boolean secured) {
            this.ssid = ssid;
            this.rssi = rssi;
            this.secured = secured;
        }
    }

    private WifiNearby() {}

    /** One entry per name (its strongest access point), hidden names and the current network left out. */
    static List<Network> list(List<Network> scan, String current, int limit) {
        Map<String, Network> best = new LinkedHashMap<>();
        for (Network n : scan) {
            if (n.ssid == null || n.ssid.isEmpty() || n.ssid.equals(current)) continue;
            Network prev = best.get(n.ssid);
            if (prev == null || n.rssi > prev.rssi) best.put(n.ssid, n);
        }
        List<Network> out = new ArrayList<>(best.values());
        out.sort((a, b) -> Integer.compare(b.rssi, a.rssi));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    static int strength(int rssi) {
        if (rssi >= -55) return EXCELLENT;
        if (rssi >= -67) return GOOD;
        if (rssi >= -77) return FAIR;
        return WEAK;
    }
}

package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Sunrise and sunset for one place and day (NOAA's low-precision formulas, within a minute or two at Ukrainian
 * latitudes), so the theme can follow the sun without any network.
 *
 * Pure maths, no Android types: the caller passes the date, the coordinates and the zone, which also makes the
 * clock change at the end of October a matter of passing the new day.
 */
final class SunTimes {

    private SunTimes() {}

    /** Returns {sunrise, sunset} in minutes after local midnight. */
    static int[] compute(LocalDate date, double lat, double lon, ZoneId zone) {
        double g = 2 * Math.PI / 365 * (date.getDayOfYear() - 1);
        double eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g)
                - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
        double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g)
                + 0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
        double latR = Math.toRadians(lat);
        double cosHa = Math.cos(Math.toRadians(90.833)) / (Math.cos(latR) * Math.cos(decl))
                - Math.tan(latR) * Math.tan(decl);
        double ha = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cosHa))));
        int offset = zone.getRules().getOffset(date.atStartOfDay(zone).plusHours(12).toInstant())
                .getTotalSeconds() / 60;
        return new int[] {
                wrap(720 - 4 * (lon + ha) - eqTime + offset),
                wrap(720 - 4 * (lon - ha) - eqTime + offset),
        };
    }

    private static int wrap(double minutes) {
        return Math.floorMod((int) Math.round(minutes), 1440);
    }
}

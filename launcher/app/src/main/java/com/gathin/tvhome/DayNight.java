package com.gathin.tvhome;

/** Whether Home shows its night look: by the sun (AUTO) or forced by the theme button. */
final class DayNight {

    enum Mode { AUTO, LIGHT, DARK }

    private DayNight() {}

    static boolean isNight(Mode mode, int nowMinute, int sunrise, int sunset) {
        if (mode == Mode.LIGHT) return false;
        if (mode == Mode.DARK) return true;
        return nowMinute < sunrise || nowMinute >= sunset;
    }
}

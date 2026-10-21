package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class SunCoordsTest {

    private static final double[] KYIV = {50.45, 30.52};

    @Test public void configJson_overridesDefault() {
        assertArrayEquals(new double[]{49.59, 34.55},
                Config.sunCoords("{\"sun_lat\":49.59,\"sun_lon\":34.55}", KYIV), 1e-9);
    }

    @Test public void missingBrokenOrOutOfRange_keepsDefault() {
        assertArrayEquals(KYIV, Config.sunCoords(null, KYIV), 1e-9);
        assertArrayEquals(KYIV, Config.sunCoords("not json", KYIV), 1e-9);
        assertArrayEquals(KYIV, Config.sunCoords("{\"columns\":5}", KYIV), 1e-9);
        assertArrayEquals(KYIV, Config.sunCoords("{\"sun_lat\":120,\"sun_lon\":33}", KYIV), 1e-9);
        assertArrayEquals(KYIV, Config.sunCoords("{\"sun_lat\":\"x\",\"sun_lon\":33}", KYIV), 1e-9);
    }
}

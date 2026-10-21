package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PaletteTest {

    @Test public void lerp_atZero_isNight() {
        assertEquals(Palette.NIGHT.text, Palette.lerp(Palette.NIGHT, Palette.DAY, 0f).text);
    }

    @Test public void lerp_atOne_isDay() {
        assertEquals(Palette.DAY.bgTop, Palette.lerp(Palette.NIGHT, Palette.DAY, 1f).bgTop);
    }

    @Test public void lerp_halfway_averagesEachChannel() {
        Palette a = new Palette(0, 0, 0, 0xFF000000, 0, 0, 0);
        Palette b = new Palette(0, 0, 0, 0x80FF6420, 0, 0, 0);
        assertEquals(0xC0803210, Palette.lerp(a, b, .5f).text);
    }

    @Test public void colours_matchSpec() {
        assertEquals(0xFF6B0D14, Palette.NIGHT.bgTop);
        assertEquals(0xFF0C0507, Palette.NIGHT.bgBottom);
        assertEquals(0xFFF3EDED, Palette.NIGHT.text);
        assertEquals(0xFFECEBEF, Palette.DAY.bgTop);
        assertEquals(0xFF17171A, Palette.DAY.text);
    }
}

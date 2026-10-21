package com.gathin.tvhome;

/**
 * The two looks of Sun Home and the blend between them. Night is a dark burgundy glow, day a light grey with dark
 * text; Home animates from one to the other through lerp when the sun crosses the horizon.
 */
final class Palette {

    static final Palette NIGHT = new Palette(0xFF6B0D14, 0xFF2A080C, 0xFF0C0507,
            0xFFF3EDED, 0x99F3EDED, 0x24FFFFFF, 0x12FFFFFF);
    static final Palette DAY = new Palette(0xFFECEBEF, 0xFFECEBEF, 0xFFECEBEF,
            0xFF17171A, 0xFF85858C, 0xFFFFFFFF, 0x8CFFFFFF);

    /** Background glow centre, middle and edge. */
    final int bgTop, bgMid, bgBottom;
    final int text, textMuted;
    /** Fill of the round top buttons and of the dock band. */
    final int buttonFill, dockBand;

    Palette(int bgTop, int bgMid, int bgBottom, int text, int textMuted, int buttonFill, int dockBand) {
        this.bgTop = bgTop;
        this.bgMid = bgMid;
        this.bgBottom = bgBottom;
        this.text = text;
        this.textMuted = textMuted;
        this.buttonFill = buttonFill;
        this.dockBand = dockBand;
    }

    boolean isDark() {
        return this == NIGHT || luminance(bgMid) < 0.5f;
    }

    static Palette lerp(Palette a, Palette b, float t) {
        return new Palette(mix(a.bgTop, b.bgTop, t), mix(a.bgMid, b.bgMid, t), mix(a.bgBottom, b.bgBottom, t),
                mix(a.text, b.text, t), mix(a.textMuted, b.textMuted, t),
                mix(a.buttonFill, b.buttonFill, t), mix(a.dockBand, b.dockBand, t));
    }

    static int mix(int from, int to, float t) {
        int c = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int x = (from >>> shift) & 0xFF;
            int y = (to >>> shift) & 0xFF;
            c |= (Math.round(x + (y - x) * t) & 0xFF) << shift;
        }
        return c;
    }

    private static float luminance(int c) {
        return (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
    }
}

package com.gathin.tvhome;

import android.content.Intent;

/** One card of the continue-watching row, from uafix's history or the system's Watch Next. */
final class ContinueItem {
    /** Shown in the card's second line: "uafix", "Netflix". */
    String source;
    String title;
    /** Original title, for the TMDB search; may be empty. */
    String orig;
    /** Episode line ("Серія 2"), or null for the default "continue watching". */
    String subtitle;
    boolean series;
    /** Artwork to show until (or instead of) a TMDB backdrop. */
    String imageUrl;
    float progress;
    long ts;
    /** Stable identity across refreshes and sources: "uafix:<page url>", "wn:<package>:<title>". */
    String key;
    /** What OK opens; filled in by the source, null in tests. */
    Intent launch;
}

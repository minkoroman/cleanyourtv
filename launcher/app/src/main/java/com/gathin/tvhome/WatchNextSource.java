package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.util.Log;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The system's Watch Next row (what Netflix and other apps publish as "continue watching").
 *
 * Android may hand a non-system launcher only its own rows (ACCESS_ALL_EPG_DATA is signature|privileged), and an
 * app may publish nothing: both simply give an empty list, logged once as TvHome/WatchNext rows=N.
 */
final class WatchNextSource {

    private static final String TAG = "TvHome/WatchNext";
    private static final String[] COLUMNS = {
            TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
            TvContract.WatchNextPrograms.COLUMN_TITLE,
            TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
            TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
            TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
            TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
            TvContract.WatchNextPrograms.COLUMN_TYPE,
    };

    private WatchNextSource() {}

    static List<ContinueItem> read(Context context) {
        List<ContinueItem> out = new ArrayList<>();
        try (Cursor c = context.getContentResolver().query(
                TvContract.WatchNextPrograms.CONTENT_URI, COLUMNS, null, null, null)) {
            if (c == null) {
                Log.i(TAG, "rows=0 (no cursor)");
                return Collections.emptyList();
            }
            PackageManager pm = context.getPackageManager();
            while (c.moveToNext()) {
                String pkg = c.getString(0);
                if (pkg == null || pkg.equals(context.getPackageName())) continue;
                ContinueItem item = new ContinueItem();
                item.source = label(pm, pkg);
                item.title = c.getString(1);
                String season = c.getString(3);
                String episode = c.getString(4);
                item.series = c.getInt(10) == TvContract.WatchNextPrograms.TYPE_TV_EPISODE || episode != null;
                if (episode != null) {
                    item.subtitle = (season != null ? "С" + season + " " : "") + "Е" + episode
                            + (c.getString(2) != null ? " · " + c.getString(2) : "");
                }
                item.imageUrl = c.getString(5);
                long pos = c.getLong(6);
                long dur = c.getLong(7);
                item.progress = dur > 0 ? Math.min(1f, (float) pos / dur) : 0f;
                item.ts = c.getLong(8) / 1000;
                item.key = "wn:" + pkg + ":" + item.title;
                item.launch = launchIntent(pm, pkg, c.getString(9));
                out.add(item);
            }
        } catch (SecurityException | IllegalArgumentException e) {
            Log.i(TAG, "rows=0 (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            return Collections.emptyList();
        }
        Log.i(TAG, "rows=" + out.size());
        return out;
    }

    private static String label(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static Intent launchIntent(PackageManager pm, String pkg, String uri) {
        if (uri != null) {
            try {
                return Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            } catch (URISyntaxException ignored) {
                // fall back to opening the app
            }
        }
        return pm.getLaunchIntentForPackage(pkg);
    }
}

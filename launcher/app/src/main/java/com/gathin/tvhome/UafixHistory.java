package com.gathin.tvhome;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Reads the history document of uafix's sync server into cards: unfinished titles only. */
final class UafixHistory {

    /** Watched this much or more counts as finished. */
    private static final double FINISHED = 0.95;

    private UafixHistory() {}

    /** uafix 1.6.2+: opens the title and resumes it where it stopped. */
    static String openLink(String pageUrl) {
        try {
            return "uafix://open?url=" + URLEncoder.encode(pageUrl, "UTF-8") + "&play=1";
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    static List<ContinueItem> parse(String json) {
        if (json == null) return Collections.emptyList();
        try {
            JSONObject doc = new JSONObject(json);
            JSONArray history = doc.optJSONArray("history");
            // "Clear history" in uafix keeps old entries on other devices until they merge; like uafix's own
            // merge, anything not newer than the clear is gone.
            long cleared = doc.optLong("history_cleared", 0);
            if (history == null) return Collections.emptyList();
            List<ContinueItem> out = new ArrayList<>();
            for (int i = 0; i < history.length(); i++) {
                JSONObject h = history.optJSONObject(i);
                if (h == null || h.optBoolean("removed") || h.optString("url").isEmpty()) continue;
                if (cleared > 0 && h.optLong("ts") <= cleared) continue;
                double pos = h.optDouble("pos", 0);
                double dur = h.optDouble("dur", 0);
                if (dur > 0 && pos / dur >= FINISHED) continue;
                ContinueItem item = new ContinueItem();
                item.source = "uafix";
                item.title = h.optString("title");
                item.orig = h.optString("orig");
                String label = h.optString("label");
                item.subtitle = label.isEmpty() || label.equals("Фільм") ? null : label;
                // A series can come without season fields; its page and episode label still say so.
                item.series = !h.optString("season").isEmpty() || h.optInt("ei") > 0
                        || label.startsWith("Серія") || h.optString("url").contains("/serials/");
                item.imageUrl = h.optString("poster");
                item.progress = dur > 0 ? (float) (pos / dur) : 0f;
                item.ts = h.optLong("ts");
                item.key = "uafix:" + h.optString("url");
                item.url = h.optString("url");
                out.add(item);
            }
            return out;
        } catch (JSONException e) {
            return Collections.emptyList();
        }
    }
}

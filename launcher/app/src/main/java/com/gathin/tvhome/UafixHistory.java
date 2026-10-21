package com.gathin.tvhome;

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

    static List<ContinueItem> parse(String json) {
        if (json == null) return Collections.emptyList();
        try {
            JSONArray history = new JSONObject(json).optJSONArray("history");
            if (history == null) return Collections.emptyList();
            List<ContinueItem> out = new ArrayList<>();
            for (int i = 0; i < history.length(); i++) {
                JSONObject h = history.optJSONObject(i);
                if (h == null || h.optBoolean("removed") || h.optString("url").isEmpty()) continue;
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
                out.add(item);
            }
            return out;
        } catch (JSONException e) {
            return Collections.emptyList();
        }
    }
}

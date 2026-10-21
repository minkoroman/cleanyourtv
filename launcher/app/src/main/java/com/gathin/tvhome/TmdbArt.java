package com.gathin.tvhome;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Wide 16:9 stills from TMDB for the cards, found the way uafix finds them: by the original title first. */
final class TmdbArt {

    private TmdbArt() {}

    static String searchUrl(String orig, String title) {
        String query = orig == null || orig.trim().isEmpty() ? title : orig;
        try {
            return "https://api.themoviedb.org/3/search/multi?language=uk-UA&query="
                    + URLEncoder.encode(query == null ? "" : query.trim(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    /** The first result's backdrop at w780, or null when nothing has one. */
    static String pickBackdrop(String searchJson) {
        try {
            JSONArray results = new JSONObject(searchJson).optJSONArray("results");
            if (results == null) return null;
            for (int i = 0; i < results.length(); i++) {
                JSONObject r = results.optJSONObject(i);
                String path = r == null || r.isNull("backdrop_path") ? "" : r.optString("backdrop_path");
                if (!path.isEmpty()) return "https://image.tmdb.org/t/p/w780" + path;
            }
            return null;
        } catch (JSONException e) {
            return null;
        }
    }
}

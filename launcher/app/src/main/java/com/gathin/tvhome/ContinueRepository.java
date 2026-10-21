package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The continue-watching row's data: uafix's history from its sync server, cached on disk so the row shows at once
 * and without a network, merged with the system's Watch Next rows, and TMDB backdrops for the cards, each fetched once and kept on disk.
 *
 * Network only runs from load(), which Home calls in onResume, and uafix at most every 15 minutes. One background
 * thread does all of it; results come back on the main thread.
 */
final class ContinueRepository {

    static final String UAFIX_PACKAGE = "net.uafix.uafix_tv";
    private static final String SYNC_URL = "https://uafix-sync.uafix-35038b.workers.dev/v1/sync/";
    private static final long REFRESH_MS = 15 * 60 * 1000L;
    private static final int TIMEOUT_MS = 8000;
    private static final int ROW_LIMIT = 12;
    private static final String TAG = "TvHome/Continue";

    private final Context context;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final File historyFile;
    private final File tmdbFile;
    private final File artDir;
    /** Decoded artwork by card and size, capped in bytes: the screensaver's full-screen stills are ~4 MB each. */
    private final LruCache<String, Bitmap> memory = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    /** TMDB lookups: card key → backdrop URL, or "" for none found. Only touched on the worker. */
    private JSONObject tmdb;

    ContinueRepository(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences("sun_home", Context.MODE_PRIVATE);
        File dir = new File(this.context.getFilesDir(), "continue");
        dir.mkdirs();
        historyFile = new File(dir, "uafix.json");
        tmdbFile = new File(dir, "tmdb.json");
        artDir = new File(this.context.getCacheDir(), "art");
        artDir.mkdirs();
    }

    /** Delivers the cached row at once, then again after a refresh when one is due. */
    void load(Consumer<List<ContinueItem>> onMain) {
        worker.execute(() -> {
            deliver(onMain, buildRow());
            String code = BuildConfig.UAFIX_SYNC_CODE;
            long last = prefs.getLong("uafix_fetched", 0);
            if (code.isEmpty() || System.currentTimeMillis() - last < REFRESH_MS) return;
            try {
                String body = get(SYNC_URL + code, false);
                if (UafixHistory.parse(body).isEmpty() && !body.contains("\"history\"")) return;
                Files.write(historyFile.toPath(), body.getBytes(StandardCharsets.UTF_8));
                prefs.edit().putLong("uafix_fetched", System.currentTimeMillis()).apply();
                deliver(onMain, buildRow());
            } catch (IOException e) {
                Log.i(TAG, "uafix refresh failed, keeping the cache: " + e);
            }
        });
    }

    /** The card's artwork decoded for widthPx: the TMDB backdrop if one exists, else the source's own image. */
    void art(ContinueItem item, int widthPx, Consumer<Bitmap> onMain) {
        String memoryKey = item.key + "@" + widthPx;
        Bitmap hit = memory.get(memoryKey);
        if (hit != null) {
            onMain.accept(hit);
            return;
        }
        worker.execute(() -> {
            Bitmap bmp = null;
            String backdrop = backdropFor(item);
            // Full-screen use (the screensaver) takes TMDB's 1280px still; the row's cards the 780px one.
            if (backdrop != null) bmp = image(widthPx > 780 ? backdrop.replace("/w780/", "/w1280/") : backdrop, widthPx);
            if (bmp == null && item.imageUrl != null && !item.imageUrl.isEmpty()) bmp = image(item.imageUrl, widthPx);
            if (bmp == null) return;
            memory.put(memoryKey, bmp);
            Bitmap result = bmp;
            main.post(() -> onMain.accept(result));
        });
    }

    private List<ContinueItem> buildRow() {
        List<ContinueItem> uafix = Collections.emptyList();
        // Without a sync code this build has no uafix row at all, cached history included.
        if (!BuildConfig.UAFIX_SYNC_CODE.isEmpty() && historyFile.exists()) {
            try {
                uafix = UafixHistory.parse(new String(Files.readAllBytes(historyFile.toPath()), StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.i(TAG, "cannot read the cached history: " + e);
            }
        }
        Intent open = context.getPackageManager().getLaunchIntentForPackage(UAFIX_PACKAGE);
        for (ContinueItem item : uafix) item.launch = open;
        return ContinueMerge.merge(uafix, WatchNextSource.read(context), ROW_LIMIT);
    }

    private void deliver(Consumer<List<ContinueItem>> onMain, List<ContinueItem> row) {
        main.post(() -> onMain.accept(row));
    }

    /** Runs on the worker. */
    private String backdropFor(ContinueItem item) {
        if (!"uafix".equals(item.source) || BuildConfig.TMDB_TOKEN.isEmpty()) return null;
        if (tmdb == null) {
            try {
                tmdb = tmdbFile.exists()
                        ? new JSONObject(new String(Files.readAllBytes(tmdbFile.toPath()), StandardCharsets.UTF_8))
                        : new JSONObject();
            } catch (IOException | JSONException e) {
                tmdb = new JSONObject();
            }
        }
        if (tmdb.has(item.key)) {
            String known = tmdb.optString(item.key);
            return known.isEmpty() ? null : known;
        }
        try {
            String found = TmdbArt.pickBackdrop(get(TmdbArt.searchUrl(item.orig, item.title), true));
            tmdb.put(item.key, found == null ? "" : found);
            Files.write(tmdbFile.toPath(), tmdb.toString().getBytes(StandardCharsets.UTF_8));
            return found;
        } catch (IOException | JSONException e) {
            return null;  // not remembered: tried again next time
        }
    }

    /** Runs on the worker: from the disk cache, or downloaded once into it. */
    private Bitmap image(String url, int widthPx) {
        File file = new File(artDir, sha1(url) + ".img");
        if (!file.exists()) {
            try {
                HttpURLConnection c = open(url, false);
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(file)) {
                    byte[] buf = new byte[16384];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                } finally {
                    c.disconnect();
                }
            } catch (IOException e) {
                file.delete();
                return null;
            }
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0) {
            file.delete();
            return null;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 1;
        while (bounds.outWidth / (opts.inSampleSize * 2) >= widthPx) opts.inSampleSize *= 2;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getPath(), opts);
    }

    private String get(String url, boolean tmdbAuth) throws IOException {
        HttpURLConnection c = open(url, tmdbAuth);
        try (InputStream in = c.getInputStream()) {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url, boolean tmdbAuth) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", "UAFix-Player");
        if (tmdbAuth) c.setRequestProperty("Authorization", "Bearer " + BuildConfig.TMDB_TOKEN);
        return c;
    }

    private static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : d) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}

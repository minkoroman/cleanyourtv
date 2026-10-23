package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The outage schedule page from poe.pl.ua, kept on disk so the strip shows at once and without a network. Asked for
 * on returns to Home, at most every 10 minutes: the site changes a few times a day at most.
 */
final class OutageRepository {

    private static final long MIN_GAP_MS = 10 * 60 * 1000L;
    private static final String TAG = "TvHome/Outage";

    /** What the strip needs: the parsed days and when the copy on disk was fetched (0 = never). */
    static final class Snapshot {
        final Map<LocalDate, PoeSchedule.Day> days;
        final long fetchedAt;

        Snapshot(Map<LocalDate, PoeSchedule.Day> days, long fetchedAt) {
            this.days = days;
            this.fetchedAt = fetchedAt;
        }
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final File page;

    OutageRepository(Context context) {
        prefs = context.getSharedPreferences("sun_home", Context.MODE_PRIVATE);
        File dir = new File(context.getFilesDir(), "outage");
        dir.mkdirs();
        page = new File(dir, "poe.html");
    }

    void load(Consumer<Snapshot> onMain) {
        worker.execute(() -> {
            Snapshot cached = read();
            if (cached != null) main.post(() -> onMain.accept(cached));
            if (System.currentTimeMillis() - prefs.getLong("poe_fetched", 0) < MIN_GAP_MS) return;
            try {
                String html = get();
                PoeSchedule.parse(html);   // a page we cannot read never replaces a good copy
                Files.write(page.toPath(), html.getBytes(StandardCharsets.UTF_8));
                prefs.edit().putLong("poe_fetched", System.currentTimeMillis()).apply();
                Snapshot fresh = read();
                if (fresh != null) main.post(() -> onMain.accept(fresh));
            } catch (IOException | IllegalArgumentException e) {
                Log.i(TAG, "schedule refresh failed, keeping the copy: " + e);
            }
        });
    }

    private Snapshot read() {
        if (!page.exists()) return null;
        try {
            String html = new String(Files.readAllBytes(page.toPath()), StandardCharsets.UTF_8);
            return new Snapshot(PoeSchedule.parse(html), prefs.getLong("poe_fetched", 0));
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    /** The headers the site's own page sends with this request (and the Svitlo app copies). */
    private static String get() throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(PoeSchedule.URL).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (sun-home-tv)");
        c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        c.setRequestProperty("Referer", "https://www.poe.pl.ua/");
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
}

package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.TextView;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The clock screensaver's scene (layout dream_clock): stills from the continue-watching row (uafix history, TMDB
 * backdrops already cached by Home) fill the screen one after another, each slowly zooming, with its title at the
 * bottom left and a split-flap clock at the bottom right. Every minute the text drifts a little so nothing burns
 * in. Without stills it is the clock on black. Shared by ClockDream (the system screensaver) and ClockActivity
 * (Home left idle).
 */
final class ClockScene {

    private static final long SLIDE_MS = 20_000L;
    private static final long FADE_MS = 1500L;
    private static final float ZOOM = 1.08f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private ContinueRepository repository;
    private List<ContinueItem> slides = Collections.emptyList();
    private int index = -1;
    private ImageView front, back;
    private View info, clockBlock, track, progress;
    private TextView kicker, title, subtitle;
    private FlipClockView flip;
    private boolean started;

    private final Runnable slideTick = new Runnable() {
        @Override
        public void run() {
            showNext();
            handler.postDelayed(this, SLIDE_MS);
        }
    };

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            LocalTime now = LocalTime.now();
            flip.setTime(now.getHour(), now.getMinute(), true);
            drift();
            handler.postDelayed(this, 60_000L - (now.getSecond() * 1000L + now.getNano() / 1_000_000L));
        }
    };

    static int nextSlide(int current, int count) {
        return count == 0 ? -1 : (current + 1) % count;
    }

    private final Context context;
    private final View root;

    /** root is the inflated dream_clock layout. */
    ClockScene(View root) {
        this.root = root;
        context = root.getContext();
        front = root.findViewById(R.id.dreamArtA);
        back = root.findViewById(R.id.dreamArtB);
        info = root.findViewById(R.id.dreamInfo);
        clockBlock = root.findViewById(R.id.dreamClockBlock);
        kicker = root.findViewById(R.id.dreamKicker);
        title = root.findViewById(R.id.dreamTitle);
        subtitle = root.findViewById(R.id.dreamSubtitle);
        track = root.findViewById(R.id.dreamTrack);
        progress = root.findViewById(R.id.dreamProgress);
        progress.setPivotX(0f);
        flip = root.findViewById(R.id.dreamFlip);
        LocalTime now = LocalTime.now();
        flip.setTime(now.getHour(), now.getMinute(), false);
        repository = new ContinueRepository(context);
        placeClock(false);   // centred until the first still arrives
    }

    /** In the corner beside a still's title, or in the middle of a black screen when there are no stills. */
    private void placeClock(boolean corner) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) clockBlock.getLayoutParams();
        int m = Math.round(context.getResources().getDisplayMetrics().density * (corner ? 56 : 0));
        lp.gravity = corner ? Gravity.BOTTOM | Gravity.END : Gravity.CENTER;
        lp.setMarginEnd(m);
        lp.bottomMargin = corner ? Math.round(context.getResources().getDisplayMetrics().density * 40) : 0;
        clockBlock.setLayoutParams(lp);
        ((android.widget.LinearLayout) clockBlock).setGravity(corner ? Gravity.END : Gravity.CENTER_HORIZONTAL);
    }

    void start() {
        started = true;
        clockBlock.animate().alpha(1f).setDuration(1200).start();
        handler.post(minuteTick);
        repository.load(items -> {
            boolean first = slides.isEmpty();
            slides = items;
            if (first && started && !items.isEmpty()) {
                handler.removeCallbacks(slideTick);
                handler.post(slideTick);
            }
        });
    }

    void stop() {
        started = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void showNext() {
        index = nextSlide(index, slides.size());
        if (index < 0) return;
        ContinueItem item = slides.get(index);
        int width = context.getResources().getDisplayMetrics().widthPixels;
        repository.art(item, width, bmp -> {
            if (started) present(item, bmp);
        });
    }

    /** Fades the new still in over the old one and starts its slow zoom; the title changes with it. */
    private void present(ContinueItem item, Bitmap bmp) {
        placeClock(true);
        ImageView incoming = back;
        back = front;
        front = incoming;
        // The new still goes over the old one, and the scrim and text back over both.
        incoming.bringToFront();
        root.findViewById(R.id.dreamScrim).bringToFront();
        info.bringToFront();
        clockBlock.bringToFront();
        incoming.setImageBitmap(bmp);
        incoming.animate().cancel();
        incoming.setAlpha(0f);
        incoming.setScaleX(1f);
        incoming.setScaleY(1f);
        incoming.setTranslationX(0f);
        incoming.setPivotX(incoming.getWidth() * (0.3f + 0.4f * random.nextFloat()));
        incoming.setPivotY(incoming.getHeight() * (0.3f + 0.4f * random.nextFloat()));
        incoming.animate().alpha(1f).setDuration(FADE_MS).start();
        incoming.animate().scaleX(ZOOM).scaleY(ZOOM).setDuration(SLIDE_MS + FADE_MS).start();
        info.animate().alpha(0f).setDuration(400).withEndAction(() -> {
            kicker.setText(context.getString(R.string.continue_watching) + " · " + item.source);
            title.setText(item.title);
            subtitle.setText(item.subtitle != null ? item.subtitle
                    : context.getString(item.series ? R.string.kind_series : R.string.kind_film));
            track.setVisibility(item.progress > 0.01f ? View.VISIBLE : View.INVISIBLE);
            progress.setScaleX(item.progress);
            info.animate().alpha(1f).setDuration(900).start();
        }).start();
    }

    /** A few dp in a random direction each minute, so the text never sits on the same pixels for long. */
    private void drift() {
        float d = 10 * context.getResources().getDisplayMetrics().density;
        float dx = (random.nextFloat() * 2 - 1) * d;
        float dy = (random.nextFloat() * 2 - 1) * d;
        info.animate().translationX(dx).translationY(dy).setDuration(3000).start();
        clockBlock.animate().translationX(-dx).translationY(dy).setDuration(3000).start();
    }
}

package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;

import java.util.Locale;

/**
 * A split-flap clock (HH MM) for the screensaver: four dark cards, and a digit that changes flips over its middle
 * line, the old top half folding down and the new bottom half unfolding. Drawn on a canvas, no bitmaps.
 */
public class FlipClockView extends View {

    private static final long FLIP_MS = 600;

    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint seam = new Paint();
    private final RectF rect = new RectF();
    private final float density;
    private String shown = "";
    private String previous = "";
    private boolean[] flipping = new boolean[4];
    private float progress = 1f;
    private ValueAnimator flip;

    public FlipClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        card.setColor(0xD91C1C20);
        text.setColor(0xFFF3F3F3);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(ResourcesCompat.getFont(context, R.font.outfit_light));
        seam.setColor(0xFF0B0B0D);
    }

    static String digits(int hour, int minute) {
        return String.format(Locale.ROOT, "%02d%02d", hour, minute);
    }

    static boolean[] changed(String from, String to) {
        boolean[] out = new boolean[4];
        for (int i = 0; i < 4; i++) out[i] = from.length() != 4 || from.charAt(i) != to.charAt(i);
        return out;
    }

    void setTime(int hour, int minute, boolean animate) {
        String next = digits(hour, minute);
        if (next.equals(shown)) return;
        previous = shown.isEmpty() ? next : shown;
        flipping = changed(previous, next);
        shown = next;
        if (flip != null) flip.cancel();
        if (!animate || previous.equals(next)) {
            progress = 1f;
            invalidate();
            return;
        }
        flip = ValueAnimator.ofFloat(0f, 1f);
        flip.setDuration(FLIP_MS);
        flip.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        flip.start();
    }

    private float cardWidth(int h) {
        return h * 0.74f;
    }

    private float gap() {
        return 6 * density;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int w = (int) (4 * cardWidth(h) + 3 * gap() + 3 * gap());   // a wider gap between hours and minutes
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (shown.length() != 4) return;
        int h = getHeight();
        float w = cardWidth(h);
        text.setTextSize(h * 0.78f);
        float x = 0;
        for (int i = 0; i < 4; i++) {
            if (i == 2) x += 3 * gap();
            rect.set(x, 0, x + w, h);
            char now = shown.charAt(i);
            if (!flipping[i] || progress >= 1f) {
                half(canvas, now, true, 1f);
                half(canvas, now, false, 1f);
            } else {
                char old = previous.charAt(i);
                if (progress < 0.5f) {
                    half(canvas, now, true, 1f);
                    half(canvas, old, false, 1f);
                    half(canvas, old, true, 1f - 2 * progress);
                } else {
                    half(canvas, now, true, 1f);
                    half(canvas, old, false, 1f);
                    half(canvas, now, false, 2 * progress - 1f);
                }
            }
            float cy = rect.centerY();
            canvas.drawRect(rect.left, cy - density, rect.right, cy + density, seam);
            x += w + gap();
        }
    }

    /** One half of a card with its digit, squashed towards the middle line by scale. */
    private void half(Canvas canvas, char digit, boolean top, float scale) {
        if (scale <= 0f) return;
        float cy = rect.centerY();
        float radius = 8 * density;
        canvas.save();
        canvas.clipRect(rect.left, top ? rect.top : cy, rect.right, top ? cy : rect.bottom);
        canvas.scale(1f, scale, rect.centerX(), cy);
        canvas.drawRoundRect(rect, radius, radius, card);
        canvas.drawText(String.valueOf(digit), rect.centerX(), cy - (text.descent() + text.ascent()) / 2, text);
        canvas.restore();
    }
}

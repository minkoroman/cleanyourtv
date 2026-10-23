package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Collections;
import java.util.List;

/** Today in 48 half-hour cells: outages red, switching time amber, the past dimmed, a tick at now. */
public class OutageBar extends View {

    private static final int OFF = 0xFFE5484D, MAYBE = 0xFFF5A524;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private List<PoeSchedule.Slot> slots = Collections.emptyList();
    private int nowSlot = -1;
    private boolean dark = true;

    public OutageBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
    }

    void set(List<PoeSchedule.Slot> slots, int nowSlot) {
        this.slots = slots;
        this.nowSlot = nowSlot;
        invalidate();
    }

    void setDark(boolean dark) {
        this.dark = dark;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = PoeSchedule.SLOTS;
        float gap = density;
        float cell = (getWidth() - gap * (n - 1)) / n;
        float top = 2 * density, bottom = getHeight() - 2 * density;
        int on = dark ? 0x33FFFFFF : 0x2217171A;
        for (int i = 0; i < n; i++) {
            PoeSchedule.Slot s = i < slots.size() ? slots.get(i) : PoeSchedule.Slot.ON;
            int color = s == PoeSchedule.Slot.OFF ? OFF : s == PoeSchedule.Slot.MAYBE ? MAYBE : on;
            if (i < nowSlot) color = (color & 0x00FFFFFF) | (((color >>> 24) / 2) << 24);   // the past, faded
            paint.setColor(color);
            float x = i * (cell + gap);
            rect.set(x, top, x + cell, bottom);
            canvas.drawRoundRect(rect, density, density, paint);
        }
        if (nowSlot >= 0 && nowSlot < n) {
            paint.setColor(dark ? 0xFFFFFFFF : 0xFF17171A);
            float x = nowSlot * (cell + gap) + cell / 2;
            rect.set(x - density, 0, x + density, getHeight());
            canvas.drawRoundRect(rect, density, density, paint);
        }
    }
}

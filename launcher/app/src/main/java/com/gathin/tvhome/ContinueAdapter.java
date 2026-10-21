package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * The continue-watching row. A focused card widens (170 → 260dp) inside the row's fixed height, its still growing
 * upwards from the caption line; the caption block keeps one fixed height, one line per text, so nothing below the
 * row is ever overlapped.
 */
final class ContinueAdapter extends RecyclerView.Adapter<ContinueAdapter.Holder> {

    interface Listener {
        void onCardClicked(ContinueItem item);
        /** Up, Down, or Left/Right at the row's ends; true when handled. */
        boolean onCardKey(int position, int keyCode);
        void onCardFocused(int position);
    }

    private final ContinueRepository repository;
    private final Listener listener;
    private final int normalW, focusedW;
    private final float density;
    private List<ContinueItem> items = new ArrayList<>();
    private Palette palette = Palette.NIGHT;

    ContinueAdapter(Context context, ContinueRepository repository, Listener listener) {
        this.repository = repository;
        this.listener = listener;
        normalW = context.getResources().getDimensionPixelSize(R.dimen.continue_card_width);
        focusedW = context.getResources().getDimensionPixelSize(R.dimen.continue_card_focused_width);
        density = context.getResources().getDisplayMetrics().density;
    }

    void submit(List<ContinueItem> list) {
        items = new ArrayList<>(list);
        notifyDataSetChanged();
    }

    void setPalette(Palette p) {
        palette = p;
        notifyItemRangeChanged(0, items.size(), "palette");
    }

    @Override public int getItemCount() {
        return items.size();
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_continue, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position, @NonNull List<Object> payloads) {
        if (payloads.contains("palette")) {
            h.colour(palette, h.itemView.hasFocus());
            return;
        }
        super.onBindViewHolder(h, position, payloads);
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        ContinueItem item = items.get(position);
        h.item = item;
        h.title.setText(item.title);
        Context c = h.itemView.getContext();
        String kind = c.getString(item.series ? R.string.kind_series : R.string.kind_film);
        String what = item.subtitle != null ? item.subtitle : c.getString(R.string.continue_watching);
        h.meta.setText(kind + " · " + item.source + " · " + what);
        h.placeholder.setText(item.title);
        h.placeholder.setVisibility(View.VISIBLE);
        h.art.setImageDrawable(null);
        h.setWidth(h.itemView.hasFocus() ? focusedW : normalW);
        h.colour(palette, h.itemView.hasFocus());
        // A share of the track whatever its width, so it stays right while a focused card widens.
        h.progress.setPivotX(0f);
        h.progress.setScaleX(item.progress);
        h.track.setVisibility(item.progress > 0.01f ? View.VISIBLE : View.INVISIBLE);
        repository.art(item, focusedW, bmp -> {
            if (h.item != item) return;   // recycled meanwhile
            h.art.setImageBitmap(bmp);
            h.placeholder.setVisibility(View.GONE);
        });
    }

    final class Holder extends RecyclerView.ViewHolder {
        final View card, caption, track, progress;
        final ImageView art;
        final TextView title, meta, placeholder;
        ContinueItem item;
        private ValueAnimator widthAnim;

        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.continueCard);
            caption = v.findViewById(R.id.continueCaption);
            art = v.findViewById(R.id.continueArt);
            placeholder = v.findViewById(R.id.continuePlaceholder);
            title = v.findViewById(R.id.continueTitle);
            meta = v.findViewById(R.id.continueMeta);
            track = v.findViewById(R.id.continueTrack);
            progress = v.findViewById(R.id.continueProgress);
            card.setClipToOutline(true);
            v.setOnClickListener(x -> {
                if (item != null) listener.onCardClicked(item);
            });
            v.setOnKeyListener((x, keyCode, e) -> e.getAction() == KeyEvent.ACTION_DOWN
                    && listener.onCardKey(getBindingAdapterPosition(), keyCode));
            v.setOnFocusChangeListener((x, focused) -> {
                animateWidth(focused ? focusedW : normalW);
                colour(palette, focused);
                if (focused) listener.onCardFocused(getBindingAdapterPosition());
            });
        }

        void setWidth(int w) {
            if (widthAnim != null) widthAnim.cancel();
            applyWidth(w);
        }

        private void animateWidth(int target) {
            if (widthAnim != null) widthAnim.cancel();
            widthAnim = ValueAnimator.ofInt(card.getLayoutParams().width, target);
            widthAnim.setDuration(180);
            widthAnim.addUpdateListener(a -> applyWidth((int) a.getAnimatedValue()));
            widthAnim.start();
        }

        private void applyWidth(int w) {
            ViewGroup.LayoutParams lp = card.getLayoutParams();
            lp.width = w;
            lp.height = w * 9 / 16;
            card.setLayoutParams(lp);
            ViewGroup.LayoutParams cl = caption.getLayoutParams();
            cl.width = w;
            caption.setLayoutParams(cl);
        }

        void colour(Palette p, boolean focused) {
            boolean dark = p.isDark();
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(10 * density);
            bg.setColor(dark ? 0xFF2A1A1C : 0xFFD9D8DE);
            if (focused) bg.setStroke((int) (2 * density), dark ? 0xE6FFFFFF : 0xFF17171A);
            card.setForeground(focused ? bg.getConstantState().newDrawable().mutate() : null);
            if (focused) ((GradientDrawable) card.getForeground()).setColor(0x00000000);
            card.setBackground(bg);
            title.setTextColor(p.text);
            meta.setTextColor(p.textMuted);
            GradientDrawable trackBg = new GradientDrawable();
            trackBg.setCornerRadius(2 * density);
            trackBg.setColor(dark ? 0x40FFFFFF : 0x3317171A);
            track.setBackground(trackBg);
            GradientDrawable bar = new GradientDrawable();
            bar.setCornerRadius(2 * density);
            bar.setColor(p.text);
            progress.setBackground(bar);
        }
    }
}

package dev.grokhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 48 dp pill, 22 dp icon, 14 dp left padding. Apps, Settings, and the wing actions. */
final class IconPill extends LinearLayout {
    private final ImageView glyph;
    private final ArcMark arc;
    private final TextView text;
    private final GradientDrawable fill;
    private int icon;
    private String label = "";
    private boolean busy;

    IconPill(Context context, String label, int icon, Runnable action) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(dp(context, 48));
        setPadding(dp(context, 14), 0, dp(context, 16), 0);
        fill = new GradientDrawable();
        fill.setColor(0xff17191c);
        fill.setCornerRadius(dp(context, 24));
        fill.setStroke(dp(context, 1), 0xff6b7079);
        setBackground(fill);
        glyph = new ImageView(context);
        glyph.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        glyph.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(glyph, new LayoutParams(dp(context, 22), dp(context, 22)));
        arc = new ArcMark(context);
        arc.setVisibility(GONE);
        addView(arc, new LayoutParams(dp(context, 18), dp(context, 18)));
        text = new TextView(context);
        text.setTextColor(Color.WHITE);
        text.setTextSize(15);
        text.setSingleLine(true);
        text.setPadding(dp(context, 8), 0, 0, 0);
        text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(text, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        bind(label, icon, action);
    }

    /** @return true when the label or icon changed and the row should measure again. */
    boolean bind(String label, int icon, Runnable action) {
        String next = label == null ? "" : label;
        boolean changed = !next.equals(this.label) || this.icon != icon;
        this.label = next;
        this.icon = icon;
        text.setText(this.label);
        if (!busy) setContentDescription(this.label);
        setOnClickListener(v -> action.run());
        showMark();
        return changed;
    }

    /** Stopping: white arc, muted label and border, button not clickable. */
    void setBusy(boolean value) {
        if (busy == value) return;
        busy = value;
        setEnabled(!value);
        setClickable(!value);
        text.setTextColor(value ? 0xffa7adb6 : Color.WHITE);
        fill.setStroke(dp(getContext(), 1), value ? 0xff34383e : 0xff6b7079);
        setContentDescription(value ? "Stopping" : label);
        showMark();
        requestLayout();
    }

    void freezeSpinner() { arc.freeze(); }

    private void showMark() {
        boolean spin = busy;
        arc.setVisibility(spin ? VISIBLE : GONE);
        boolean showIcon = !spin && icon != 0;
        glyph.setVisibility(showIcon ? VISIBLE : GONE);
        if (showIcon) glyph.setImageResource(icon);
        text.setPadding(dp(getContext(), showIcon || spin ? 8 : 0), 0, 0, 0);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** White three-quarter arc. A framework spinner stays blank until its animation ticks. */
    private static final class ArcMark extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private final Runnable tick = new Runnable() {
            @Override public void run() {
                if (frozen || getVisibility() != VISIBLE || !ValueAnimator.areAnimatorsEnabled()) return;
                start = (start + 8f) % 360f;
                invalidate();
                postOnAnimation(this);
            }
        };
        private float start = -90f;
        private boolean frozen;

        ArcMark(Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.WHITE);
            setMinimumWidth(dp(context, 16));
            setMinimumHeight(dp(context, 16));
        }

        void freeze() {
            frozen = true;
            removeCallbacks(tick);
            start = -90f;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float density = getResources().getDisplayMetrics().density;
            float stroke = Math.max(2.25f * density, Math.min(getWidth(), getHeight()) * 0.16f);
            paint.setStrokeWidth(stroke);
            float inset = stroke * 0.5f + density * 0.5f;
            oval.set(inset, inset, getWidth() - inset, getHeight() - inset);
            if (oval.width() <= stroke || oval.height() <= stroke) return;
            canvas.drawArc(oval, start, 270f, false, paint);
        }

        @Override protected void onVisibilityChanged(View changedView, int visibility) {
            super.onVisibilityChanged(changedView, visibility);
            if (getVisibility() == VISIBLE && !frozen && ValueAnimator.areAnimatorsEnabled()) {
                removeCallbacks(tick);
                postOnAnimation(tick);
            } else removeCallbacks(tick);
        }

        @Override protected void onDetachedFromWindow() {
            removeCallbacks(tick);
            super.onDetachedFromWindow();
        }
    }
}

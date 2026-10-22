package dev.grokhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 48 dp status pill. The spec draws 40 dp; the touch target is 48 dp. */
final class ConnectionChip extends LinearLayout {
    private final GradientDrawable dot;
    private final TextView label;

    ConnectionChip(Context context, Runnable open) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(14);
        setPadding(pad, 0, pad, 0);
        setMinimumHeight(dp(48));
        setMinimumWidth(dp(48));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff17191c);
        background.setCornerRadius(dp(24));
        background.setStroke(dp(1), 0xff6b7079);
        setBackground(background);
        setOnClickListener(v -> open.run());
        setClickable(true);
        setFocusable(true);
        dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(ConnectionRules.dotColor(ConnectionRules.Mode.OFFLINE));
        View mark = new View(context);
        mark.setBackground(dot);
        addView(mark, new LayoutParams(dp(10), dp(10)));
        label = new TextView(context);
        label.setTextColor(Color.WHITE);
        label.setTextSize(15);
        label.setSingleLine(true);
        label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams text = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        text.leftMargin = dp(8);
        addView(label, text);
        bind(ConnectionRules.Mode.OFFLINE);
    }

    void bind(ConnectionRules.Mode mode) {
        String text = ConnectionRules.chipLabel(mode);
        dot.setColor(ConnectionRules.dotColor(mode));
        label.setText(text);
        setContentDescription(text + ", computer connection");
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

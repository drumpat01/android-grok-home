package dev.grokhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Title and detail under the bot. Buttons live in {@link HomeControls}. */
final class StatusBlock extends LinearLayout {
    private final View dot;
    private final TextView title;
    private final TextView clock;
    private final TextView detail;
    private boolean task;
    private Runnable detailAction;

    StatusBlock(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(24);
        setPadding(pad, 0, pad, 0);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER);
        row.setOrientation(HORIZONTAL);
        dot = new View(context);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(0xff3ddc84);
        dot.setBackground(disc);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotParams.setMarginEnd(dp(8));
        row.addView(dot, dotParams);
        title = line(context, 26, Color.WHITE);
        clock = line(context, 20, 0xffa7adb6);
        row.addView(title);
        row.addView(clock);
        addView(row, new LayoutParams(-2, -2));
        detail = line(context, 15, 0xffb9c0c9);
        detail.setMaxLines(2);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams detailParams = new LayoutParams(-1, -2);
        detailParams.topMargin = dp(4);
        addView(detail, detailParams);
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        showIdle("Ready when you are", "Talk to Max");
    }

    void showIdle(String heading, String subtitle) {
        task = false;
        dot.setVisibility(GONE);
        clock.setVisibility(GONE);
        title.setText(heading == null ? "" : heading);
        title.setTextColor(Color.WHITE);
        if (subtitle == null || subtitle.isEmpty()) detail.setVisibility(GONE);
        else { detail.setVisibility(VISIBLE); detail.setText(subtitle); }
        setMinimumHeight(dp(StagePlacement.IDLE_BLOCK_DP));
    }

    void showWorking(String heading, String clockText, boolean seen, String body) {
        task = true;
        dot.setVisibility(VISIBLE);
        title.setText(heading == null ? "" : heading);
        title.setTextColor(Color.WHITE);
        clock.setVisibility(VISIBLE);
        clock.setText(" · " + (clockText == null ? "" : clockText));
        clock.setContentDescription(seen ? clockText : "Elapsed " + clockText);
        showBody(body);
        setMinimumHeight(dp(StagePlacement.TASK_BLOCK_DP));
    }

    void showTitle(String heading, String body) {
        task = true;
        dot.setVisibility(GONE);
        clock.setVisibility(GONE);
        title.setText(heading == null ? "" : heading);
        title.setTextColor(Color.WHITE);
        showBody(body);
        setMinimumHeight(dp(StagePlacement.TASK_BLOCK_DP));
    }

    /** Subtitle tap. A null action clears the target so idle copy is not a button. */
    void setDetailAction(Runnable action) {
        detailAction = action;
        if (action == null) {
            detail.setOnClickListener(null);
            detail.setClickable(false);
            detail.setMinHeight(0);
            return;
        }
        detail.setOnClickListener(v -> { if (detailAction != null) detailAction.run(); });
        detail.setClickable(true);
        detail.setMinHeight(dp(48));
    }

    String titleText() { return title.getText() == null ? "" : title.getText().toString(); }
    String detailText() { return detail.getVisibility() == VISIBLE && detail.getText() != null ? detail.getText().toString() : ""; }
    int blockHeight() { return dp(task ? StagePlacement.TASK_BLOCK_DP : StagePlacement.IDLE_BLOCK_DP); }

    private void showBody(String body) {
        if (body == null || body.isEmpty()) detail.setVisibility(GONE);
        else { detail.setVisibility(VISIBLE); detail.setText(body); }
    }

    private TextView line(Context context, float sp, int color) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        return view;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

package dev.grokhome;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import java.util.function.Consumer;

/** The one voice switch. The whole row toggles it. kit/w2-task can drop this into the Voice group. */
final class ListenSwitchRow extends LinearLayout {
    ListenSwitchRow(Context context, boolean on, Consumer<Boolean> changed) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(dp(context, 64));
        setPadding(0, dp(context, 8), 0, dp(context, 8));
        setClickable(true);
        LinearLayout text = new LinearLayout(context);
        text.setOrientation(VERTICAL);
        text.setClickable(false);
        TextView title = new TextView(context);
        title.setText(VoiceCopy.LISTEN_TITLE);
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        TextView sub = new TextView(context);
        sub.setText(VoiceCopy.LISTEN_SUB);
        sub.setTextColor(0xffa7adb6);
        sub.setTextSize(13);
        text.addView(title);
        text.addView(sub);
        text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        Switch toggle = new Switch(context);
        toggle.setChecked(on);
        toggle.setMinimumHeight(dp(context, 48));
        toggle.setContentDescription(VoiceCopy.LISTEN_TITLE + ". " + VoiceCopy.LISTEN_SUB);
        toggle.setOnCheckedChangeListener((button, checked) -> changed.accept(checked));
        addView(toggle, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(context, 48)));
        setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}

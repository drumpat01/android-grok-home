package dev.grokhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

/**
 * One bottom row: Apps, a wing, the 76 dp mic, a wing, Settings.
 * The mic stays on the screen center. Idle wings are Type and History.
 * kit/w2-task swaps them for Stop task and Open chat with {@link #setWings}.
 */
final class HomeControls extends FrameLayout {
    interface Callbacks {
        void onApps();
        void onType();
        void onHistory();
        void onSettings();
        void onMicTap();
        void onMicLongPress();
    }

    private static final int ARMED_FILL = 0xffeaf6fc;
    private static final int ARMED_ICON = 0xff0e1a20;
    private static final int HALO = 0x2eb6ddf2;
    private static final int OUTLINE = 0xff6b7079;
    private static final int SURFACE = 0xff17191c;
    private static final int STOP = 0xffe5484d;

    private final ImageButton micButton;
    private final View halo;
    private final View micStage;
    private final IconPill appsPill;
    private final IconPill settingsPill;
    private final IconPill leftPill;
    private final IconPill rightPill;

    HomeControls(Context context, Callbacks callbacks) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        appsPill = new IconPill(context, "Apps", R.drawable.ic_apps, callbacks::onApps);
        settingsPill = new IconPill(context, "Settings", R.drawable.ic_settings, callbacks::onSettings);
        leftPill = new IconPill(context, "Type", R.drawable.ic_keyboard, callbacks::onType);
        rightPill = new IconPill(context, "History", R.drawable.ic_history, callbacks::onHistory);
        FrameLayout stage = new FrameLayout(context);
        halo = new View(context);
        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.OVAL);
        glow.setColor(HALO);
        halo.setBackground(glow);
        stage.addView(halo, frame(context, 88));
        micButton = new ImageButton(context);
        micButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        micButton.setOnClickListener(v -> callbacks.onMicTap());
        micButton.setOnLongClickListener(v -> { callbacks.onMicLongPress(); return true; });
        stage.addView(micButton, frame(context, 76));
        stage.setLayoutParams(new LayoutParams(dp(context, 88), dp(context, 88)));
        micStage = stage;
        addView(appsPill);
        addView(leftPill);
        addView(stage);
        addView(rightPill);
        addView(settingsPill);
        setPhase(MicState.Phase.ARMED, "", false);
    }

    /** Swap the controls beside the mic. Apps, the mic, and Settings stay put. */
    void setWings(String leftLabel, int leftIcon, Runnable left, String rightLabel, int rightIcon, Runnable right) {
        boolean changed = leftPill.bind(leftLabel, leftIcon, left) | rightPill.bind(rightLabel, rightIcon, right);
        if (changed) requestLayout();
    }

    /** The left wing shows a spinner and ignores taps. Used while a task is stopping. */
    void setLeftBusy(boolean busy) { leftPill.setBusy(busy); }

    /** Holds the stopping arc on a frame where it is obvious. Captures use this. */
    void freezeSpinner() { leftPill.freezeSpinner(); }

    /** Chat keeps Apps and Settings. The mic and its wings step aside. */
    void setCenterVisible(boolean visible) {
        int show = visible ? VISIBLE : INVISIBLE;
        leftPill.setVisibility(show);
        micStage.setVisibility(show);
        rightPill.setVisibility(show);
    }

    void setPhase(MicState.Phase phase, String bot, boolean temporaryMute) {
        micButton.setContentDescription(MicState.spokenLabel(phase, bot));
        ViewCompat.replaceAccessibilityAction(micButton,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
            MicState.longPressLabel(temporaryMute),
            (view, arguments) -> micButton.performLongClick());
        int pad = dp(getContext(), 22);
        switch (phase) {
            case ARMED:
                micButton.setImageResource(R.drawable.ic_mic);
                micButton.setColorFilter(ARMED_ICON, PorterDuff.Mode.SRC_IN);
                micButton.setBackground(circle(ARMED_FILL, 0));
                halo.setVisibility(VISIBLE);
                micButton.setTooltipText(VoiceCopy.HOLD_TOOLTIP);
                break;
            case RECORDING:
                micButton.setImageResource(R.drawable.ic_stop);
                micButton.clearColorFilter();
                micButton.setBackground(circle(SURFACE, STOP));
                halo.setVisibility(INVISIBLE);
                micButton.setTooltipText(VoiceCopy.HOLD_TOOLTIP);
                pad = dp(getContext(), 28);
                break;
            case MUTED:
                micButton.setImageResource(R.drawable.ic_mic_off);
                micButton.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                micButton.setBackground(circle(SURFACE, OUTLINE));
                halo.setVisibility(INVISIBLE);
                micButton.setTooltipText(VoiceCopy.UNMUTE_ACTION);
                break;
            case SWITCH_OFF:
            case NO_PERMISSION:
                micButton.setImageResource(R.drawable.ic_mic);
                micButton.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                micButton.setBackground(circle(SURFACE, OUTLINE));
                halo.setVisibility(INVISIBLE);
                micButton.setTooltipText(VoiceCopy.HOLD_TOOLTIP);
                break;
            default:
                MicState.Phase unknown = phase;
                throw new IllegalStateException("Unhandled mic phase " + unknown);
        }
        micButton.setPadding(pad, pad, pad, pad);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        for (int i = 0; i < getChildCount(); i++) measureAtMost(getChildAt(i), width, height);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        int side = dp(getContext(), HomeBand.ROW_SIDE_DP);
        int gap = dp(getContext(), 16);
        place(appsPill, side, height);
        place(settingsPill, width - side - settingsPill.getMeasuredWidth(), height);
        if (micStage.getVisibility() == GONE) return;
        int micLeft = (width - micStage.getMeasuredWidth()) / 2;
        place(micStage, micLeft, height);
        place(leftPill, micLeft - gap - leftPill.getMeasuredWidth(), height);
        place(rightPill, micLeft + micStage.getMeasuredWidth() + gap, height);
    }

    private void measureAtMost(View child, int width, int height) {
        LayoutParams params = (LayoutParams) child.getLayoutParams();
        int childWidth = params != null && params.width > 0
            ? MeasureSpec.makeMeasureSpec(params.width, MeasureSpec.EXACTLY)
            : MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
        int childHeight = params != null && params.height > 0
            ? MeasureSpec.makeMeasureSpec(params.height, MeasureSpec.EXACTLY)
            : MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);
        child.measure(childWidth, childHeight);
    }

    private void place(View child, int x, int height) {
        if (child.getVisibility() == GONE) return;
        int childHeight = child.getMeasuredHeight();
        int y = (height - childHeight) / 2;
        child.layout(x, y, x + child.getMeasuredWidth(), y + childHeight);
    }

    private FrameLayout.LayoutParams frame(Context context, int size) {
        return new FrameLayout.LayoutParams(dp(context, size), dp(context, size), Gravity.CENTER);
    }

    private GradientDrawable circle(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        if (stroke != 0) drawable.setStroke(dp(getContext(), 1), stroke);
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}

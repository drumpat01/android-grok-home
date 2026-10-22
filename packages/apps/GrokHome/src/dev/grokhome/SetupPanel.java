package dev.grokhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.View.MeasureSpec;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 460 dp first-run checklist. The primary button and footer stay outside the scroll. */
final class SetupPanel extends LinearLayout {
    interface Host {
        void meet();
        void connect();
        void allowMic();
        void openMicSettings();
        void useKeyboard();
        void chooseHome();
        void skipCurrent();
        void finish();
    }

    private final Host host;
    private final TextView count;
    private final GradientDrawable[] bars = new GradientDrawable[SetupProgress.STEPS];
    private final Row[] rows = new Row[SetupProgress.STEPS];
    private final Button skip;
    private final Button primary;
    private final TextView footer;
    private final ScrollView scroll;
    private final LinearLayout micActions;
    private final TextView checkedAgain;
    private int announcedCount = -1;
    private SetupProgress shown = SetupProgress.load(false, false, 0, 0, false);
    private boolean homeAttempted;
    private boolean settingsInstead;

    SetupPanel(Context context, Host host) {
        super(context);
        this.host = host;
        setOrientation(VERTICAL);
        setBackground(shape(0xff191c20, 20, 0xff34383e));
        setElevation(dp(12));
        setPadding(dp(20), dp(16), dp(20), dp(16));
        setOnClickListener(v -> {});
        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label("Set up Grok Home", 20, Color.WHITE), new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        count = label("0 of 4 done", 15, 0xffa7adb6);
        header.addView(count);
        body.addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout progress = new LinearLayout(context);
        progress.setOrientation(LinearLayout.HORIZONTAL);
        progress.setPadding(0, dp(4), 0, dp(14));
        for (int step = 0; step < SetupProgress.STEPS; step++) {
            View bar = new View(context);
            bars[step] = new GradientDrawable();
            bars[step].setCornerRadius(dp(2));
            bars[step].setColor(0xff34383e);
            bar.setBackground(bars[step]);
            LinearLayout.LayoutParams piece = new LinearLayout.LayoutParams(0, dp(4), 1);
            if (step > 0) piece.leftMargin = dp(6);
            progress.addView(bar, piece);
        }
        body.addView(progress);

        for (int step = 0; step < SetupProgress.STEPS; step++) {
            rows[step] = new Row(context, step);
            body.addView(rows[step].root);
        }

        skip = button("Skip", host::skipCurrent, false);
        skip.setVisibility(GONE);
        LinearLayout.LayoutParams skipPad = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(48));
        skipPad.bottomMargin = dp(8);
        body.addView(skip, skipPad);

        micActions = new LinearLayout(context);
        micActions.setOrientation(LinearLayout.HORIZONTAL);
        micActions.setVisibility(GONE);
        micActions.addView(button("Use the keyboard instead", host::useKeyboard, false), new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(48)));
        LinearLayout.LayoutParams micPad = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        micPad.bottomMargin = dp(8);
        body.addView(micActions, micPad);

        TextView next = label("WHAT HAPPENS NEXT", 15, 0xffa7adb6);
        next.setPadding(0, dp(8), 0, dp(8));
        body.addView(next);
        TextView zoom = label("Finish zooms out once so you can see the whole team. Pinch any time to do it again.", 15, 0xffe6e1dc);
        body.addView(zoom);
        checkedAgain = label("Home screen and microphone are checked again each time you come back to Grok Home.", 15, 0xffa7adb6);
        checkedAgain.setPadding(0, dp(10), 0, dp(16));
        body.addView(checkedAgain);

        primary = button("Get started", this::runCurrent, true);
        LayoutParams primaryPad = new LayoutParams(LayoutParams.MATCH_PARENT, dp(52));
        primaryPad.topMargin = dp(8);
        addView(primary, primaryPad);
        footer = label("Skipped steps wait in Settings.", 15, 0xffa7adb6);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(12), 0, 0);
        addView(footer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int widthSpec = MeasureSpec.makeMeasureSpec(Math.max(0, width - getPaddingLeft() - getPaddingRight()), MeasureSpec.EXACTLY);
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        int maxHeight = mode == MeasureSpec.UNSPECIFIED ? 1 << 20 : MeasureSpec.getSize(heightMeasureSpec);
        int pad = getPaddingTop() + getPaddingBottom();
        int primaryH = 0;
        if (primary.getVisibility() != GONE) {
            primary.measure(widthSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
            primaryH = primary.getMeasuredHeight() + dp(8);
        }
        int footerH = 0;
        if (footer.getVisibility() != GONE) {
            footer.measure(widthSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
            footerH = footer.getMeasuredHeight();
        }
        int scrollMax = Math.max(0, maxHeight - pad - primaryH - footerH);
        int scrollMode = mode == MeasureSpec.EXACTLY ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST;
        scroll.measure(widthSpec, MeasureSpec.makeMeasureSpec(scrollMax, scrollMode));
        int height = mode == MeasureSpec.EXACTLY ? maxHeight : Math.min(maxHeight, pad + primaryH + footerH + scroll.getMeasuredHeight());
        setMeasuredDimension(width, height);
    }

    void bind(SetupProgress progress, ConnectionState connection, boolean micDenied) {
        bind(progress, connection, micDenied, false);
    }

    void bind(SetupProgress progress, ConnectionState connection, boolean micDenied, boolean settingsInstead) {
        shown = progress;
        this.homeAttempted = progress.homeAttempted;
        this.settingsInstead = settingsInstead && micDenied;
        int done = progress.doneCount();
        String summary = done + " of 4 done";
        count.setText(summary);
        if (announcedCount >= 0 && announcedCount != done) count.announceForAccessibility(summary);
        announcedCount = done;
        int current = progress.current();
        for (int step = 0; step < SetupProgress.STEPS; step++) {
            bars[step].setColor(progress.stepDone(step) ? 0xff3ddc84 : 0xff34383e);
            boolean highlight = step == current;
            rows[step].bind(progress, connection, highlight, step == current, homeAttempted);
            if (progress.complete()) {
                rows[step].root.setFocusable(false);
                rows[step].root.clearFocus();
            }
        }
        skip.setText(skipLabel(current));
        skip.setVisibility(current >= 0 ? VISIBLE : GONE);
        primary.setText(primaryLabel(current));
        boolean showMic = micDenied && current == SetupProgress.MIC;
        micActions.setVisibility(showMic ? VISIBLE : GONE);
        checkedAgain.setVisibility(showMic ? GONE : VISIBLE);
        footer.setVisibility(progress.skipped == 0 ? GONE : VISIBLE);
    }

    private void runCurrent() {
        int current = shown.current();
        if (current < 0) {
            host.finish();
            return;
        }
        switch (current) {
            case SetupProgress.MEET: host.meet(); break;
            case SetupProgress.CONNECT: host.connect(); break;
            case SetupProgress.MIC: if (settingsInstead) host.openMicSettings(); else host.allowMic(); break;
            case SetupProgress.HOME: host.chooseHome(); break;
            default: throw new AssertionError(current);
        }
    }

    private String primaryLabel(int current) {
        switch (current) {
            case SetupProgress.MEET: return "Get started";
            case SetupProgress.CONNECT: return "Add address";
            case SetupProgress.MIC: return settingsInstead ? "Open settings" : "Allow microphone";
            case SetupProgress.HOME: return homeAttempted ? "Try again" : "Set as Home";
            case -1: return "Finish";
            default: throw new AssertionError(current);
        }
    }

    private String skipLabel(int current) {
        if (current == SetupProgress.HOME && homeAttempted) return "Skip for now";
        return "Skip";
    }

    private final class Row {
        final LinearLayout root;
        final GradientDrawable fill;
        final TextView mark;
        final TextView title;
        final TextView detail;
        final TextView chevron;
        final int step;

        Row(Context context, int step) {
            this.step = step;
            root = new LinearLayout(context);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(dp(12), dp(12), dp(12), dp(12));
            root.setMinimumHeight(dp(72));
            fill = new GradientDrawable();
            fill.setColor(0xff101214);
            fill.setCornerRadius(dp(14));
            root.setBackground(fill);
            mark = label("✓", 12, Color.WHITE);
            mark.setGravity(Gravity.CENTER);
            root.addView(mark, new LinearLayout.LayoutParams(dp(22), dp(22)));
            LinearLayout column = new LinearLayout(context);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setPadding(dp(12), 0, 0, 0);
            title = label(titleFor(step), 16, Color.WHITE);
            detail = label("", 15, 0xffa7adb6);
            detail.setPadding(0, dp(2), 0, 0);
            column.addView(title);
            column.addView(detail);
            root.addView(column, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
            chevron = label("›", 22, Color.WHITE);
            chevron.setGravity(Gravity.CENTER);
            chevron.setVisibility(GONE);
            chevron.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            root.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(48)));
            root.setOnClickListener(v -> onClick());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(8);
            root.setLayoutParams(params);
        }

        void bind(SetupProgress progress, ConnectionState connection, boolean highlight, boolean current, boolean homeAttempted) {
            boolean done = progress.stepDone(step);
            boolean skipped = progress.stepSkipped(step) && !done;
            title.setText(titleFor(step));
            detail.setText(detailFor(step, progress, connection, homeAttempted));
            mark.setText(done ? "✓" : skipped ? "-" : "");
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            if (done) {
                dot.setColor(0xff3ddc84);
                mark.setTextColor(0xff102116);
            } else {
                dot.setColor(0xff101214);
                dot.setStroke(dp(1), 0xff6b7079);
                mark.setTextColor(skipped ? 0xffa7adb6 : Color.WHITE);
            }
            mark.setBackground(dot);
            fill.setStroke(dp(highlight ? 2 : 1), highlight ? 0xff3ddc84 : 0xff6b7079);
            chevron.setVisibility(current ? VISIBLE : GONE);
            root.setContentDescription(title.getText() + ", " + detail.getText());
            root.setStateDescription(done ? "Done" : skipped ? "Skipped" : "Not done");
        }

        void onClick() {
            switch (step) {
                case SetupProgress.MEET: host.meet(); break;
                case SetupProgress.CONNECT: host.connect(); break;
                case SetupProgress.MIC: host.allowMic(); break;
                case SetupProgress.HOME: host.chooseHome(); break;
                default: throw new AssertionError(step);
            }
        }
    }

    private static String titleFor(int step) {
        switch (step) {
            case SetupProgress.MEET: return "Meet Max";
            case SetupProgress.CONNECT: return "Connect your computer";
            case SetupProgress.MIC: return "Allow the microphone";
            case SetupProgress.HOME: return "Set as Home";
            default: throw new AssertionError(step);
        }
    }

    private static String detailFor(int step, SetupProgress progress, ConnectionState connection, boolean homeAttempted) {
        if (progress.stepSkipped(step) && !progress.stepDone(step)) return "Skipped";
        switch (step) {
            case SetupProgress.MEET:
                return progress.stepDone(step) ? "Max said hi" : "Tap to say hi";
            case SetupProgress.CONNECT:
                return connectDetail(progress, connection);
            case SetupProgress.MIC:
                return progress.stepDone(step) ? "Allowed · say Hey Bots anytime" : "Say Hey Bots to talk hands free";
            case SetupProgress.HOME:
                if (progress.stepDone(step)) return "Grok Home is your Home screen\nChecked when you came back";
                return homeAttempted ? "Not set yet" : "Opens the Home screen picker";
            default:
                throw new AssertionError(step);
        }
    }

    private static String connectDetail(SetupProgress progress, ConnectionState connection) {
        if (!progress.stepDone(SetupProgress.CONNECT)) return "Add your computer's address";
        if (connection == null || !connection.known()) return "Address saved";
        switch (connection.mode()) {
            case CONNECTED:
                String transport = connection.transportLabel();
                return transport == null || transport.isEmpty() ? "Connected" : "Connected · " + transport;
            case AWAY: return "Away · private";
            case OFFLINE: return "Address saved";
            default: throw new AssertionError(connection.mode());
        }
    }

    private Button button(String text, Runnable action, boolean light) {
        Button button = new Button(getContext());
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setMinHeight(dp(48));
        button.setPadding(dp(18), 0, dp(18), 0);
        if (light) {
            button.setTextColor(0xff121416);
            button.setBackground(shape(0xfff2f4f6, 26, 0xfff2f4f6));
        } else {
            button.setTextColor(Color.WHITE);
            button.setBackground(shape(0xff2a2e33, 24, 0xff6b7079));
        }
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private TextView label(String value, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

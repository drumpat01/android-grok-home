package dev.grokhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Themed Computer sheet. Same charcoal panel as chat, 460 dp, top right. */
final class ComputerSheet extends ScrollView {
    interface Host {
        void close();
        void tryNow();
        void save(String stored);
        String address();
    }

    private final Host host;
    private final LinearLayout card;
    private final GradientDrawable cardFill;
    private final View cardDot;
    private final TextView cardTitle;
    private final TextView cardDetail;
    private final EditText address;
    private final TextView addressError;
    private final TextView tabletTitle;
    private final TextView tabletDetail;
    private final TextView tailscaleTitle;
    private final TextView tailscaleDetail;
    private final TextView voiceTitle;
    private final TextView voiceDetail;
    private final View tabletMark;
    private final View tailscaleMark;
    private final View voiceMark;
    private final View tabletRow;
    private final View tailscaleRow;
    private final View voiceRow;
    private final GradientDrawable tabletFill;
    private final GradientDrawable tailscaleFill;
    private final GradientDrawable voiceFill;
    private final Button what;
    private final Button showAddress;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable clearFlash;
    private boolean editing;
    private boolean revealed;
    private String plain = "";
    private ConnectionState shown;

    ComputerSheet(Context context, Host host) {
        super(context);
        this.host = host;
        setFillViewport(true);
        setClipToPadding(false);
        setBackground(panel());
        setElevation(dp(12));
        setOnClickListener(v -> {});
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(16), dp(20), dp(20));
        addView(body, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Computer", 20, Color.WHITE);
        header.addView(title, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        header.addView(action("Close", host::close, false));
        body.addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));

        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        cardFill = new GradientDrawable();
        cardFill.setCornerRadius(dp(16));
        card.setBackground(cardFill);
        LinearLayout cardHead = new LinearLayout(context);
        cardHead.setGravity(Gravity.CENTER_VERTICAL);
        cardDot = new View(context);
        cardHead.addView(cardDot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        cardTitle = text("", 16, Color.WHITE);
        LinearLayout.LayoutParams titlePad = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        titlePad.leftMargin = dp(8);
        cardHead.addView(cardTitle, titlePad);
        card.addView(cardHead);
        cardDetail = text("", 15, 0xffe6e1dc);
        cardDetail.setPadding(0, dp(8), 0, dp(12));
        card.addView(cardDetail);
        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button tryNow = action("Try now", this::onTryNow, true);
        actions.addView(tryNow, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(48)));
        what = action("What to check", this::whatToCheck, false);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(48));
        gap.leftMargin = dp(8);
        actions.addView(what, gap);
        card.addView(actions);
        LinearLayout.LayoutParams cardPad = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cardPad.topMargin = dp(12);
        body.addView(card, cardPad);

        TextView addressLabel = text("Address", 15, 0xffa7adb6);
        addressLabel.setPadding(0, dp(18), 0, dp(8));
        body.addView(addressLabel);
        address = new EditText(context);
        address.setSingleLine(true);
        address.setTextSize(16);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(0xffa7adb6);
        address.setHint("100.101.12.34:8787");
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_DONE);
        address.setMinHeight(dp(52));
        address.setPadding(dp(16), dp(12), dp(16), dp(12));
        address.setBackground(field());
        address.setOnFocusChangeListener((v, focused) -> {
            editing = focused;
            if (focused) presentAddress();
            else {
                boolean valid = commitAddress();
                if (valid) revealed = false;
                if (valid || address.getText().toString().indexOf('\u2022') >= 0) presentAddress();
            }
        });
        address.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                address.clearFocus();
                return true;
            }
            return false;
        });
        LinearLayout addressRow = new LinearLayout(context);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.addView(address, new LinearLayout.LayoutParams(0, dp(52), 1));
        showAddress = action("Show", this::toggleAddress, false);
        showAddress.setMinWidth(dp(48));
        LinearLayout.LayoutParams showPad = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(48));
        showPad.leftMargin = dp(8);
        addressRow.addView(showAddress, showPad);
        body.addView(addressRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(52)));
        addressError = text("", 15, 0xfff4a62a);
        addressError.setPadding(0, dp(6), 0, 0);
        addressError.setVisibility(GONE);
        body.addView(addressError);
        TextView hint = text("Your private Tailscale address works at home and away.", 15, 0xffa7adb6);
        hint.setPadding(0, dp(8), 0, 0);
        body.addView(hint);

        TextView checks = text("Checks", 15, 0xffa7adb6);
        checks.setPadding(0, dp(18), 0, dp(8));
        body.addView(checks);
        tabletFill = rowFill();
        tabletRow = checkRow(tabletFill);
        tabletMark = markOf(tabletRow);
        tabletTitle = titleOf(tabletRow);
        tabletDetail = detailOf(tabletRow);
        body.addView(tabletRow);
        tailscaleFill = rowFill();
        tailscaleRow = checkRow(tailscaleFill);
        tailscaleMark = markOf(tailscaleRow);
        tailscaleTitle = titleOf(tailscaleRow);
        tailscaleDetail = detailOf(tailscaleRow);
        body.addView(tailscaleRow);
        voiceFill = rowFill();
        voiceRow = checkRow(voiceFill);
        voiceMark = markOf(voiceRow);
        voiceTitle = titleOf(voiceRow);
        voiceDetail = detailOf(voiceRow);
        body.addView(voiceRow);
        fillAddress(host.address());
    }

    void bind(ConnectionState state) {
        shown = state;
        long age = state.syncAgeMs(SystemClock.elapsedRealtime());
        ConnectionRules.Mode mode = state.mode();
        paintCard(mode);
        cardDot.setBackground(dot(ConnectionRules.dotColor(mode)));
        switch (mode) {
            case CONNECTED:
                cardTitle.setText("Connected · " + ConnectionRules.syncCompact(age));
                cardDetail.setText("Grok Home Voice is answering.");
                break;
            case AWAY:
                cardTitle.setText("Away · using your private network");
                cardDetail.setText(ConnectionRules.syncSentence(age) + ".");
                break;
            case OFFLINE:
                cardTitle.setText(ConnectionRules.OFFLINE_TITLE);
                cardDetail.setText(ConnectionRules.syncSentence(age) + ". Bots keep working on the computer. " + ConnectionRules.retryCountdown(state.retryInMs(SystemClock.elapsedRealtime())) + ".");
                break;
            default:
                throw new AssertionError(mode);
        }
        if (!editing && addressError.getVisibility() != VISIBLE) fillAddress(host.address());
        boolean online = state.tabletOnline();
        paintCheck(tabletMark, tabletTitle, tabletDetail, tabletRow, online, "Tablet is online", "Tablet is offline",
            online ? (state.transportLabel().isEmpty() ? "Online" : state.transportLabel()) : "No connection");
        paintTailscale(state);
        boolean voice = state.voiceAnswering();
        paintCheck(voiceMark, voiceTitle, voiceDetail, voiceRow, voice, "Grok Home Voice on your computer", "Grok Home Voice on your computer",
            voice ? "Answering" : state.voiceDetail());
        what.setVisibility(firstProblem() == null ? GONE : VISIBLE);
    }

    private void paintTailscale(ConnectionState state) {
        boolean on = state.tailscaleOn();
        if (on) {
            paintCheck(tailscaleMark, tailscaleTitle, tailscaleDetail, tailscaleRow, true, "Tailscale is on", "Tailscale is off", "Private network active");
            return;
        }
        if (tailnetAddress()) {
            paintCheck(tailscaleMark, tailscaleTitle, tailscaleDetail, tailscaleRow, false, "Tailscale is on", "Tailscale is off", "Private network is off");
            return;
        }
        tailscaleMark.setBackground(dot(0xff7d848e));
        tailscaleTitle.setText("Tailscale is off");
        tailscaleDetail.setText("Only needed away from home");
        tailscaleRow.setContentDescription("Tailscale is off, Only needed away from home");
    }

    private boolean tailnetAddress() {
        return ConnectionRules.hostKind(host.address()) == ConnectionRules.HostKind.TAILNET;
    }

    private void onTryNow() {
        if (!commitAddress()) return;
        host.tryNow();
    }

    private boolean commitAddress() {
        String typed = address.getText().toString();
        String raw = typed.indexOf('\u2022') >= 0 ? plain : typed;
        ConnectionRules.Address checked = ConnectionRules.check(raw);
        if (!checked.ok()) {
            addressError.setText(checked.message);
            addressError.setVisibility(VISIBLE);
            address.setError(null);
            return false;
        }
        address.setError(null);
        addressError.setVisibility(GONE);
        String current = host.address() == null ? "" : host.address().trim();
        if (!checked.stored.equals(current)) host.save(checked.stored);
        return true;
    }

    private void whatToCheck() {
        View target = firstProblem();
        if (target == null) return;
        flash(target);
        String title = titleOf(target).getText().toString();
        String detail = detailOf(target).getText().toString();
        target.announceForAccessibility(title + ", " + detail);
        if (!fullyVisible(target)) smoothScrollTo(0, target.getTop());
    }

    private void flash(View target) {
        GradientDrawable fill = (GradientDrawable) target.getBackground();
        fill.setStroke(dp(2), 0xfff4a62a);
        if (clearFlash != null) main.removeCallbacks(clearFlash);
        clearFlash = () -> fill.setStroke(0, 0);
        main.postDelayed(clearFlash, 2000);
    }

    private boolean fullyVisible(View target) {
        if (target.getHeight() <= 0) return true;
        Rect visible = new Rect();
        if (!target.getLocalVisibleRect(visible)) return false;
        return visible.height() >= target.getHeight() - dp(1);
    }

    View firstProblem() {
        if (shown == null || !shown.tabletOnline()) return tabletRow;
        if (tailnetAddress() && !shown.tailscaleOn()) return tailscaleRow;
        if (!shown.voiceAnswering()) return voiceRow;
        return null;
    }

    private void toggleAddress() {
        revealed = !revealed;
        presentAddress();
    }

    private void fillAddress(String stored) {
        plain = ConnectionRules.display(stored);
        presentAddress();
    }

    private void presentAddress() {
        String next = editing || revealed ? plain : ConnectionRules.mask(plain);
        if (!next.equals(address.getText().toString())) address.setText(next);
        boolean open = editing || revealed;
        showAddress.setText(open ? "Hide" : "Show");
        showAddress.setContentDescription(open ? "Hide address" : "Show address");
        showAddress.setVisibility(editing ? GONE : VISIBLE);
    }

    private void paintCard(ConnectionRules.Mode mode) {
        int fill;
        int stroke;
        switch (mode) {
            case CONNECTED: fill = 0xff12261a; stroke = 0xff1f6b3a; break;
            case AWAY: fill = 0xff121e2a; stroke = 0xff1f4a6b; break;
            case OFFLINE: fill = 0xff2a1f12; stroke = 0xff6b4a1f; break;
            default: throw new AssertionError(mode);
        }
        cardFill.setColor(fill);
        cardFill.setStroke(dp(1), stroke);
    }

    private void paintCheck(View mark, TextView title, TextView detail, View row, boolean ok, String okTitle, String badTitle, String caption) {
        mark.setBackground(dot(ok ? 0xff3ddc84 : 0xfff4a62a));
        title.setText(ok ? okTitle : badTitle);
        detail.setText(caption == null ? "" : caption);
        row.setContentDescription(title.getText() + ", " + detail.getText());
    }

    private GradientDrawable rowFill() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff101214);
        background.setCornerRadius(dp(12));
        return background;
    }

    private LinearLayout checkRow(GradientDrawable background) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setMinimumHeight(dp(64));
        row.setFocusable(true);
        row.setBackground(background);
        View mark = new View(getContext());
        LinearLayout.LayoutParams markPad = new LinearLayout.LayoutParams(dp(10), dp(10));
        markPad.rightMargin = dp(12);
        row.addView(mark, markPad);
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(text("", 16, Color.WHITE));
        TextView caption = text("", 15, 0xffa7adb6);
        caption.setPadding(0, dp(2), 0, 0);
        column.addView(caption);
        row.addView(column, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        row.setLayoutParams(params);
        return row;
    }

    private static View markOf(View row) { return ((LinearLayout) row).getChildAt(0); }
    private static TextView titleOf(View row) { return (TextView) ((LinearLayout) ((LinearLayout) row).getChildAt(1)).getChildAt(0); }
    private static TextView detailOf(View row) { return (TextView) ((LinearLayout) ((LinearLayout) row).getChildAt(1)).getChildAt(1); }

    private Button action(String label, Runnable run, boolean light) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setMinHeight(dp(48));
        button.setMinWidth(dp(48));
        button.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(24));
        if (light) {
            background.setColor(0xfff2f4f6);
            button.setTextColor(0xff121416);
        } else {
            background.setColor(0xff2a2e33);
            background.setStroke(dp(1), 0xff6b7079);
            button.setTextColor(Color.WHITE);
        }
        button.setBackground(background);
        button.setOnClickListener(v -> run.run());
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable panel() {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xff191c20);
        shape.setCornerRadius(dp(20));
        shape.setStroke(dp(1), 0xff34383e);
        return shape;
    }

    private GradientDrawable field() {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xff101214);
        shape.setCornerRadius(dp(12));
        shape.setStroke(dp(1), 0xff6b7079);
        return shape;
    }

    private GradientDrawable dot(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        return shape;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

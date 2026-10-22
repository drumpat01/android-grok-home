package dev.grokhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import java.time.ZoneId;
import java.util.Calendar;

/**
 * Read-only question card. The tablet never approves and never sends an answer.
 * Banner copy is {@link #APPROVE}.
 */
final class QuestionCard extends FrameLayout {
    static final String APPROVE = "Approve this in the Grok Bot app.";
    static final String EMPTY_BODY_SUFFIX = " does not need anything from you right now.";
    static final String OFFLINE = "Computer offline. Answers work again when it reconnects. Nothing is queued.";
    static final String ANSWERED = "Already answered in the Grok Bot app. The hand goes down now.";
    static final String EXPIRED = "This question expired.";
    static final String LATER = "Later";
    static final String OPEN_CHAT = "Open chat";
    static final String CLOSE = "Close";
    static final String SHOW_MORE = "Show more";
    static final String SHOW_LESS = "Show less";
    static final String REST = "Open chat to read the rest";

    interface Host {
        void openChat(String bot);
        void later(String bot, String needsYouId, long untilMillis);
        void dismiss(String bot);
    }

    private final Host host;
    private final LinearLayout panel;
    private final ImageView avatar;
    private final TextView title;
    private final TextView meta;
    private final TextView counter;
    private final TextView contextLine;
    private final TextView question;
    private final TextView rest;
    private final TextView showMore;
    private final TextView banner;
    private final Button openChat;
    private final Button later;
    private final float density;
    private String bot = "";
    private String needsId = "";
    private boolean open;
    private boolean expanded;
    private boolean empty;
    private float downX, downY;

    QuestionCard(Context context, Host host) {
        super(context);
        this.host = host;
        density = getResources().getDisplayMetrics().density;
        setClickable(true);
        setOnClickListener(v -> dismiss());
        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        panel.setBackground(cardBackground());
        panel.setElevation(dp(12));
        panel.setOnClickListener(v -> { });
        panel.setClickable(true);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        LayoutParams panelParams = new LayoutParams(panelWidth(screenW, screenH, density), LayoutParams.WRAP_CONTENT);
        addView(panel, panelParams);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(48));
        avatar = new ImageView(context);
        header.addView(avatar, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, dp(8), 0);
        title = label(context, 20, Color.WHITE);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        meta = label(context, 12, 0xFFA7ADB6);
        titles.addView(title);
        titles.addView(meta);
        header.addView(titles, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        counter = label(context, 13, 0xFFA7ADB6);
        counter.setPadding(0, 0, dp(8), 0);
        header.addView(counter);
        header.addView(new DecorativeHand(context), new LinearLayout.LayoutParams(dp(36), dp(36)));
        panel.addView(header);

        contextLine = label(context, 13, 0xFFA7ADB6);
        contextLine.setMaxLines(1);
        contextLine.setEllipsize(TextUtils.TruncateAt.END);
        contextLine.setPadding(0, dp(8), 0, 0);
        panel.addView(contextLine);

        question = label(context, 18, Color.WHITE);
        question.setLineSpacing(dp(8), 1f);
        question.setMaxLines(4);
        question.setEllipsize(TextUtils.TruncateAt.END);
        question.setPadding(0, dp(12), 0, 0);
        panel.addView(question);

        rest = label(context, 13, 0xFFA7ADB6);
        rest.setText(REST);
        rest.setVisibility(GONE);
        rest.setPadding(0, dp(8), 0, 0);
        panel.addView(rest);

        showMore = label(context, 14, 0xFFB9E5FF);
        showMore.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        showMore.setMinHeight(dp(48));
        showMore.setGravity(Gravity.CENTER_VERTICAL);
        showMore.setText(SHOW_MORE);
        showMore.setVisibility(GONE);
        showMore.setOnClickListener(v -> toggleMore());
        panel.addView(showMore);

        banner = label(context, 15, Color.WHITE);
        banner.setPadding(dp(12), dp(12), dp(12), dp(12));
        banner.setBackground(bannerBackground(0xFF1D2228, 0xFF3A434D));
        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        bannerParams.topMargin = dp(12);
        panel.addView(banner, bannerParams);

        View divider = new View(context);
        divider.setBackgroundColor(0xFF34383E);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.topMargin = dp(16);
        dividerParams.bottomMargin = dp(12);
        panel.addView(divider, dividerParams);

        LinearLayout footer = new LinearLayout(context);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        openChat = button(context, OPEN_CHAT, true);
        later = button(context, LATER, false);
        later.setTextColor(0xFFD2D6DB);
        later.setBackgroundColor(Color.TRANSPARENT);
        footer.addView(openChat, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(48));
        laterParams.leftMargin = dp(8);
        footer.addView(later, laterParams);
        panel.addView(footer);

        openChat.setOnClickListener(v -> { String name = bot; dismiss(); host.openChat(name); });
        later.setOnClickListener(v -> snooze(60L * 60L * 1000L));
        later.setOnLongClickListener(v -> { choices(); return true; });
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    /**
     * Read-only sentence for the card. Open chat and Later stay separate actions.
     * A sentence that already ends with punctuation is not given a second period.
     */
    static String spoken(String title, String question, String banner) {
        StringBuilder out = new StringBuilder();
        appendSentence(out, title);
        appendSentence(out, question);
        appendSentence(out, banner);
        return out.toString();
    }

    private static void appendSentence(StringBuilder out, String sentence) {
        if (sentence == null) return;
        String text = sentence.trim();
        if (text.isEmpty()) return;
        if (out.length() > 0) out.append(' ');
        out.append(text);
        char last = text.charAt(text.length() - 1);
        if (last != '.' && last != '?' && last != '!') out.append('.');
    }

    static int panelWidth(int screenWidthPx, int screenHeightPx, float density) {
        float d = density <= 0f ? 1f : density;
        boolean landscape = screenWidthPx >= screenHeightPx;
        if (landscape) return Math.min(Math.round(460f * d), screenWidthPx - Math.round(48f * d));
        return Math.max(dpStatic(120, d), screenWidthPx - Math.round(32f * d));
    }

    boolean isOpen() { return open && getVisibility() == VISIBLE; }
    String bot() { return bot; }
    int answerControlCount() { return 0; }
    CharSequence bannerText() { return banner.getText(); }
    int panelMeasuredWidth() { return panel.getLayoutParams().width; }

    void show(String name, int color, NeedsYou.Item[] items, boolean offline, BotStageState.Clear clear) {
        bot = name == null ? "" : name;
        NeedsYou.Item[] rows = items == null ? new NeedsYou.Item[0] : items;
        empty = rows.length == 0;
        expanded = false;
        String asked = "";
        question.setMaxLines(4);
        rest.setVisibility(GONE);
        showMore.setText(SHOW_MORE);
        avatar.setImageDrawable(SolidAvatar.icon(BotIdentity.shape(bot), color));
        if (empty) {
            needsId = "";
            title.setText(bot.isEmpty() ? "Needs you" : bot);
            meta.setText(clear == BotStageState.Clear.EXPIRED ? EXPIRED : "No open questions");
            counter.setText("");
            contextLine.setVisibility(GONE);
            question.setText(clear == BotStageState.Clear.EXPIRED ? EXPIRED : bot + EMPTY_BODY_SUFFIX);
            banner.setVisibility(GONE);
            showMore.setVisibility(GONE);
            later.setText(CLOSE);
            later.setOnClickListener(v -> dismiss());
            later.setOnLongClickListener(null);
        } else {
            NeedsYou.Item item = rows[0];
            needsId = item.id;
            title.setText(bot + " needs you");
            meta.setText(NeedsYou.askedLabel(item.created, System.currentTimeMillis(), ZoneId.systemDefault()));
            counter.setText(rows.length > 1 ? "1 of " + rows.length : "");
            if (item.context == null || item.context.isEmpty()) contextLine.setVisibility(GONE);
            else { contextLine.setVisibility(VISIBLE); contextLine.setText("About · " + item.context); }
            asked = item.question == null ? "" : item.question.trim();
            question.setText(asked.isEmpty() ? bot + " needs you." : asked);
            banner.setVisibility(VISIBLE);
            banner.setText(offline ? OFFLINE : APPROVE);
            later.setText(LATER);
            later.setOnClickListener(v -> snooze(60L * 60L * 1000L));
            later.setOnLongClickListener(v -> { choices(); return true; });
            question.post(this::syncMore);
        }
        String detail = empty ? question.getText().toString() : asked;
        String description = spoken(title.getText().toString(), detail, banner.getVisibility() == VISIBLE ? banner.getText().toString() : "");
        panel.setAccessibilityPaneTitle(description);
        panel.setContentDescription(description);
        setContentDescription(description);
        open = true;
        setVisibility(VISIBLE);
        bringToFront();
        place();
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            boolean landscape = getWidth() == 0 || getWidth() >= getHeight();
            panel.setAlpha(0f);
            panel.setTranslationX(landscape ? dp(40) : 0);
            panel.setTranslationY(landscape ? 0 : dp(48));
            panel.animate().alpha(1f).translationX(0f).translationY(0f).setDuration(240)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
    }

    void showAnswered() {
        if (!open) return;
        banner.setVisibility(VISIBLE);
        banner.setText(ANSWERED);
        banner.setBackground(bannerBackground(0xFF1D2228, 0xFF3A434D));
        postDelayed(this::dismiss, 2000);
    }

    void dismiss() {
        if (!open) return;
        open = false;
        String name = bot;
        Runnable end = () -> { setVisibility(GONE); host.dismiss(name); };
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) { end.run(); return; }
        panel.animate().alpha(0f).setDuration(200).withEndAction(end).start();
    }

    void place() {
        int w = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        int h = getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels;
        boolean landscape = w >= h;
        LayoutParams params = (LayoutParams) panel.getLayoutParams();
        params.width = panelWidth(w, h, density);
        params.height = LayoutParams.WRAP_CONTENT;
        if (landscape) {
            params.gravity = Gravity.TOP | Gravity.RIGHT;
            params.setMargins(dp(16), dp(16), dp(16), dp(16));
        } else {
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            params.setMargins(dp(16), dp(16), dp(16), dp(16));
        }
        panel.setLayoutParams(params);
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { place(); }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { downX = event.getX(); downY = event.getY(); }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!open) return super.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            boolean landscape = getWidth() >= getHeight();
            boolean inside = event.getX() >= panel.getLeft() && event.getX() <= panel.getRight()
                    && event.getY() >= panel.getTop() && event.getY() <= panel.getBottom();
            if (!inside && Math.hypot(dx, dy) < dp(12)) { dismiss(); return true; }
            if (inside && landscape && dx > dp(72) && Math.abs(dx) > Math.abs(dy) * 1.4f) { dismiss(); return true; }
            if (inside && !landscape && dy > dp(72) && Math.abs(dy) > Math.abs(dx) * 1.4f) { dismiss(); return true; }
        }
        return super.onTouchEvent(event);
    }

    private void syncMore() {
        if (empty) { showMore.setVisibility(GONE); return; }
        boolean truncated = question.getLayout() != null && question.getLineCount() > 0
                && question.getLayout().getEllipsisCount(question.getLineCount() - 1) > 0;
        boolean longText = question.getText() != null && question.getText().length() > 180;
        showMore.setVisibility(truncated || longText || expanded ? VISIBLE : GONE);
        if (expanded && question.getLayout() != null && question.getLineCount() >= 12
                && question.getLayout().getEllipsisCount(question.getLineCount() - 1) > 0) rest.setVisibility(VISIBLE);
    }

    private void toggleMore() {
        expanded = !expanded;
        question.setMaxLines(expanded ? 12 : 4);
        showMore.setText(expanded ? SHOW_LESS : SHOW_MORE);
        if (!expanded) rest.setVisibility(GONE);
        question.post(this::syncMore);
    }

    private void snooze(long duration) {
        String name = bot;
        String id = needsId;
        dismiss();
        host.later(name, id, System.currentTimeMillis() + duration);
    }

    private void choices() {
        PopupMenu menu = new PopupMenu(getContext(), later);
        menu.getMenu().add("15 min");
        menu.getMenu().add("1 hour");
        menu.getMenu().add("Tomorrow at 7:00 AM");
        menu.setOnMenuItemClickListener(item -> {
            CharSequence label = item.getTitle();
            if ("15 min".contentEquals(label)) snooze(15L * 60L * 1000L);
            else if ("1 hour".contentEquals(label)) snooze(60L * 60L * 1000L);
            else host.later(bot, needsId, tomorrowSeven());
            if ("Tomorrow at 7:00 AM".contentEquals(label)) dismiss();
            return true;
        });
        menu.show();
    }

    private long tomorrowSeven() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 7);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private TextView label(Context context, int sp, int color) {
        TextView view = new TextView(context);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private Button button(Context context, String text, boolean filled) {
        Button view = new Button(context);
        view.setText(text);
        view.setAllCaps(false);
        view.setTextColor(Color.WHITE);
        view.setTextSize(15);
        view.setMinHeight(dp(48));
        view.setMinimumHeight(dp(48));
        view.setPadding(dp(18), dp(4), dp(18), dp(4));
        if (filled) view.setBackground(buttonBackground());
        return view;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xFF191C20);
        drawable.setCornerRadius(dp(20));
        drawable.setStroke(dp(1), 0xFF34383E);
        return drawable;
    }

    private GradientDrawable buttonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xFF17191C);
        drawable.setCornerRadius(dp(28));
        drawable.setStroke(Math.max(1, dp(1)), 0xFF6B7079);
        return drawable;
    }

    private GradientDrawable bannerBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(float value) { return Math.round(value * density); }
    private static int dpStatic(float value, float density) { return Math.round(value * density); }

    private static final class DecorativeHand extends View {
        private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path hand = new Path();

        DecorativeHand(Context context) {
            super(context);
            disc.setColor(0xFF191C20);
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(Color.WHITE);
            glyph.setColor(Color.WHITE);
            StageMarks.writeHand(hand);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            float size = Math.min(getWidth(), getHeight());
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            ring.setStrokeWidth(Math.max(2f * getResources().getDisplayMetrics().density, 1f));
            canvas.drawCircle(cx, cy, size * 0.48f, disc);
            canvas.drawCircle(cx, cy, size * 0.48f - ring.getStrokeWidth(), ring);
            canvas.save();
            canvas.translate(cx - size * 0.32f, cy - size * 0.32f);
            canvas.scale(size * 0.64f / 24f, size * 0.64f / 24f);
            canvas.drawPath(hand, glyph);
            canvas.restore();
        }
    }
}

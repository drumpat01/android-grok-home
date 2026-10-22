package dev.grokhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.FrameLayout;
import org.json.JSONObject;

/**
 * Wires the stage reducer to the bot view and the read-only question card.
 * Ember reads {@link #stage()} rather than a second priority list.
 */
final class RaiseHandController {
    interface Host {
        void openChat(String bot);
        void setCardOpen(boolean open);
        boolean quiet(long now);
        boolean reduced();
        boolean offline();
        void announce(String sentence);
        int color(String bot);
        void onOverview(boolean overview);
    }

    private final BotStageState stage;
    private final Host host;
    private final SharedPreferences prefs;
    private final boolean debuggable;
    private QuestionCard card;
    private String openBot = "";

    RaiseHandController(Context context, SharedPreferences prefs, Host host) {
        this.host = host;
        this.prefs = prefs;
        this.debuggable = RaiseHandDebug.allowed(context);
        this.stage = new BotStageState(ConfettiGate.prefs(prefs));
    }

    BotStageState stage() { return stage; }
    boolean cardOpen() { return card != null && card.isOpen(); }

    void attach(FrameLayout root) {
        card = new QuestionCard(root.getContext(), new QuestionCard.Host() {
            @Override public void openChat(String bot) { host.openChat(bot); }
            @Override public void later(String bot, String needsYouId, long until) {
                prefsSnooze(root.getContext(), needsYouId, until);
                stage.snooze(needsYouId, until);
            }
            @Override public void dismiss(String bot) {
                stage.noteDismiss(bot, System.currentTimeMillis());
                openBot = "";
                host.setCardOpen(false);
            }
        });
        card.setVisibility(android.view.View.GONE);
        card.setElevation(root.getResources().getDisplayMetrics().density * 24f);
        root.addView(card, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    void observe(JSONObject snapshot, boolean tabletBusy, String phase) {
        if (snapshot == null) return;
        long now = System.currentTimeMillis();
        String bot = snapshot.optString("bot");
        BotStageState.Input input = input(snapshot, tabletBusy, phase, now);
        rememberSnooze(input.needsYou, now);
        stage.observe(bot, input, now);
        if (debuggable) RaiseHandDebug.applySnapshot(snapshot, stage, now);
        if (cardOpen() && bot.equals(card.bot())) refreshOpenCard(bot);
        announce(now);
    }

    void observeLocal(String bot, String phase, boolean tabletBusy) {
        if (bot == null || BotIdentity.index(bot) < 0) return;
        long now = System.currentTimeMillis();
        BotStageState.Input input = new BotStageState.Input();
        input.phase = phase;
        input.tabletBusy = tabletBusy;
        input.working = tabletBusy || "working".equals(phase) || "stopping".equals(phase);
        input.reducedMotion = host.reduced();
        input.quietHours = host.quiet(now);
        input.needsYou = stage.hands(bot);
        input.replyToken = "";
        stage.observe(bot, input, now);
        announce(now);
    }

    void tick(BotView bot) {
        long now = System.currentTimeMillis();
        stage.tick(now);
        push(bot);
        announce(now);
    }

    void push(BotView bot) {
        if (bot == null) return;
        for (String name : BotIdentity.NAMES) bot.setStageFrame(name, stage.frame(name));
    }

    void open(String bot) {
        if (card == null || bot == null || BotIdentity.index(bot) < 0) return;
        if (!NeedsYou.hasQuestion(stage.hands(bot))) return;
        openBot = bot;
        host.setCardOpen(true);
        card.show(bot, host.color(bot), stage.hands(bot), host.offline(), stage.clearReason(bot));
        card.bringToFront();
    }

    void closeCard() {
        if (card != null && card.isOpen()) card.dismiss();
    }

    void applyIntent(Intent intent, BotView bot) {
        if (!debuggable || intent == null) return;
        long now = System.currentTimeMillis();
        boolean any = RaiseHandDebug.applyIntent(intent, stage, now);
        boolean frameGroup = bot != null && (bot.isOverview() || RaiseHandDebug.overview(intent));
        if (RaiseHandDebug.overview(intent) && bot != null) {
            host.onOverview(true);
            bot.setOverview(true);
        }
        if (frameGroup) {
            bot.focusDebugOverview(
                    intent.getStringExtra(RaiseHandDebug.EXTRA_NEEDS_YOU),
                    intent.getStringExtra(RaiseHandDebug.EXTRA_WORKING),
                    intent.getStringExtra(RaiseHandDebug.EXTRA_DONE));
        } else {
            String focus = RaiseHandDebug.focusName(intent, false);
            if (focus != null && bot != null) bot.centerBot(focus);
        }
        if (any) push(bot);
        if (RaiseHandDebug.openCard(intent)) {
            String name = intent.getStringExtra(RaiseHandDebug.EXTRA_NEEDS_YOU);
            if (name == null || BotIdentity.index(name) < 0) name = "Echo";
            String openName = name;
            if (bot != null) bot.post(() -> open(openName));
            else open(openName);
        }
        announce(now);
    }

    private void refreshOpenCard(String bot) {
        if (stage.hands(bot).length == 0 && stage.clearReason(bot) == BotStageState.Clear.ANSWERED) card.showAnswered();
    }

    private void announce(long now) {
        String name = stage.takeAnnouncement();
        if (name.isEmpty() || cardOpen() || host.quiet(now)) return;
        host.announce(name + " needs you");
    }

    private BotStageState.Input input(JSONObject snapshot, boolean tabletBusy, String phase, long now) {
        BotStageState.Input input = new BotStageState.Input();
        input.stale = snapshot.optBoolean("stale", true);
        input.working = !input.stale && snapshot.optBoolean("working");
        input.tabletBusy = tabletBusy;
        input.phase = phase;
        input.replyToken = tokenOf(snapshot);
        input.needsYou = NeedsYou.parse(snapshot);
        input.reducedMotion = host.reduced();
        input.quietHours = host.quiet(now);
        return input;
    }

    static String tokenOf(JSONObject snapshot) {
        if (snapshot == null) return "";
        String id = snapshot.optString("reply_id", "");
        if (!id.isEmpty()) return id + "#" + snapshot.optString("reply_revision", snapshot.optString("reply_seq", "0"));
        return BotActivityState.token(BotActivityState.latestReply(snapshot.optJSONArray("entries"), snapshot.optString("bot")));
    }

    private void rememberSnooze(NeedsYou.Item[] items, long now) {
        if (prefs == null || items == null) return;
        for (NeedsYou.Item item : items) {
            if (item == null) continue;
            long until = prefs.getLong("snooze_" + item.id, 0L);
            if (until > now) stage.snooze(item.id, until, now);
        }
    }

    private static void prefsSnooze(Context context, String id, long until) {
        if (id == null || id.isEmpty()) return;
        context.getSharedPreferences("home", 0).edit().putLong("snooze_" + id, until).apply();
    }
}

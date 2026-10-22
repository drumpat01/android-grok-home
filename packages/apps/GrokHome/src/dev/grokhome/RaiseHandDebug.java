package dev.grokhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import org.json.JSONObject;

/** Debug-only demo of a raised hand, a working ring, and confetti. Release builds ignore it. */
final class RaiseHandDebug {
    static final String EXTRA_NEEDS_YOU = "dev.grokhome.DEBUG_NEEDS_YOU";
    static final String EXTRA_WORKING = "dev.grokhome.DEBUG_WORKING";
    static final String EXTRA_DONE = "dev.grokhome.DEBUG_DONE";
    static final String EXTRA_QUESTION = "dev.grokhome.DEBUG_QUESTION";
    static final String EXTRA_OPEN_CARD = "dev.grokhome.DEBUG_OPEN_CARD";
    static final String EXTRA_OVERVIEW = "dev.grokhome.DEBUG_OVERVIEW";
    static final String EXTRA_FORCE_PILL = "dev.grokhome.DEBUG_FORCE_PILL";
    static final String FIELD_NEEDS_YOU = "debug_needs_you";
    static final String FIELD_WORKING = "debug_working";
    static final String FIELD_DONE = "debug_done";
    static final String FIELD_QUESTION = "debug_question";

    private RaiseHandDebug() {}

    static boolean allowed(Context context) {
        return context != null && (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    static boolean applyIntent(Intent intent, BotStageState stage, long now) {
        if (intent == null || stage == null) return false;
        boolean any = false;
        any |= lock(stage, intent.getStringExtra(EXTRA_NEEDS_YOU), BotStageState.State.NEEDS_YOU, intent.getStringExtra(EXTRA_QUESTION), now);
        any |= lock(stage, intent.getStringExtra(EXTRA_WORKING), BotStageState.State.WORKING, "", now);
        any |= lock(stage, intent.getStringExtra(EXTRA_DONE), BotStageState.State.DONE, "debug-done-" + now, now);
        if (intent.hasExtra(EXTRA_FORCE_PILL)) {
            stage.debugForcePill(intent.getBooleanExtra(EXTRA_FORCE_PILL, false));
            any = true;
        }
        return any;
    }

    static boolean applySnapshot(JSONObject snapshot, BotStageState stage, long now) {
        if (snapshot == null || stage == null) return false;
        boolean any = false;
        any |= lock(stage, snapshot.optString(FIELD_NEEDS_YOU, ""), BotStageState.State.NEEDS_YOU, snapshot.optString(FIELD_QUESTION, ""), now);
        any |= lock(stage, snapshot.optString(FIELD_WORKING, ""), BotStageState.State.WORKING, "", now);
        any |= lock(stage, snapshot.optString(FIELD_DONE, ""), BotStageState.State.DONE, "debug-done-" + snapshot.optString(FIELD_DONE, ""), now);
        return any;
    }

    static boolean openCard(Intent intent) {
        return intent != null && intent.getBooleanExtra(EXTRA_OPEN_CARD, false);
    }

    static boolean overview(Intent intent) {
        return intent != null && intent.getBooleanExtra(EXTRA_OVERVIEW, false);
    }

    /**
     * Bot to settle onto after the debug extras lock a hand, a ring, or confetti.
     * Overview draws neighbors closer than 2.8 slots, so a reach of 2 stays on
     * screen. When the named bots fit in that window, the center that shows all
     * of them is returned. Otherwise the first named bot, in needs you, working,
     * done order.
     */
    static String focusName(String needsYou, String working, String done, boolean frameGroup) {
        int[] ids = new int[3];
        int count = 0;
        count = push(ids, count, needsYou);
        count = push(ids, count, working);
        count = push(ids, count, done);
        if (count == 0) return null;
        if (!frameGroup) return BotIdentity.NAMES[ids[0]];
        int roster = BotIdentity.NAMES.length;
        int chosen = ids[0];
        int chosenSpan = Integer.MAX_VALUE;
        boolean covers = false;
        for (int center = 0; center < roster; center++) {
            int span = 0;
            for (int i = 0; i < count; i++) span = Math.max(span, ringDistance(center, ids[i], roster));
            if (span > OVERVIEW_REACH) continue;
            boolean closer = span < chosenSpan;
            boolean sameAndFirst = span == chosenSpan && center == ids[0];
            if (!covers || closer || sameAndFirst) {
                chosen = center;
                chosenSpan = span;
                covers = true;
            }
        }
        return BotIdentity.NAMES[chosen];
    }

    static String focusName(Intent intent, boolean frameGroup) {
        if (intent == null) return null;
        return focusName(intent.getStringExtra(EXTRA_NEEDS_YOU), intent.getStringExtra(EXTRA_WORKING), intent.getStringExtra(EXTRA_DONE), frameGroup);
    }

    /** Settled carousel slots still drawn. The view skips depth at or past 2.8. */
    static final int OVERVIEW_REACH = 2;

    private static int push(int[] ids, int count, String name) {
        int index = BotIdentity.index(name);
        if (index < 0) return count;
        for (int i = 0; i < count; i++) if (ids[i] == index) return count;
        ids[count] = index;
        return count + 1;
    }

    private static int ringDistance(int a, int b, int count) {
        int delta = Math.abs(a - b) % count;
        return Math.min(delta, count - delta);
    }

    private static boolean lock(BotStageState stage, String bot, BotStageState.State state, String question, long now) {
        if (bot == null || BotIdentity.index(bot) < 0) return false;
        NeedsYou.Item item = state == BotStageState.State.NEEDS_YOU ? NeedsYou.debug(bot, question) : null;
        String token = state == BotStageState.State.DONE ? (question == null || question.isEmpty() ? "debug-done" : question) : "";
        stage.debugLock(bot, state, item, token, now);
        return true;
    }
}

package dev.grokhome;

/**
 * At most two bots animate on a screen. Everyone else uses the static pose.
 *
 * <p>Ember: fill {@code priority} (lower wins) and {@code eligible}, then call
 * {@link #grant}. Priority order is rise, done, nudge, selected working,
 * other working, glow breath. Natural idle sway is not a slot.
 */
final class MotionSlots {
    static final int LIMIT = 2;
    static final int RISE = 10;
    static final int DONE = 20;
    static final int NUDGE = 30;
    static final int WORKING_SELECTED = 40;
    static final int WORKING = 50;
    static final int BREATH = 60;
    static final int NONE = 100;

    private MotionSlots() {}

    static int priority(BotStageState.Frame frame, boolean selected) {
        if (frame == null) return NONE;
        BotStageState.OneShot shot = frame.oneShot;
        if (frame.state == BotStageState.State.NEEDS_YOU && shot == BotStageState.OneShot.RAISE) return RISE;
        if (frame.state == BotStageState.State.DONE && shot == BotStageState.OneShot.DONE) return DONE;
        if (shot == BotStageState.OneShot.NUDGE) return NUDGE;
        if (frame.state == BotStageState.State.WORKING) return selected ? WORKING_SELECTED : WORKING;
        if (frame.state == BotStageState.State.NEEDS_YOU && shot == BotStageState.OneShot.NONE && frame.breathing) return BREATH;
        return NONE;
    }

    static void grant(int count, int[] priority, boolean[] eligible, boolean[] out) {
        for (int i = 0; i < count; i++) out[i] = false;
        int first = best(count, priority, eligible, -1);
        if (first < 0) return;
        out[first] = true;
        if (LIMIT < 2) return;
        int second = best(count, priority, eligible, first);
        if (second >= 0) out[second] = true;
    }

    private static int best(int count, int[] priority, boolean[] eligible, int skip) {
        int pick = -1;
        int rank = NONE;
        for (int i = 0; i < count; i++) {
            if (i == skip || eligible == null || !eligible[i] || priority == null) continue;
            int value = priority[i];
            if (value < rank) {
                rank = value;
                pick = i;
            }
        }
        return pick;
    }
}

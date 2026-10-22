package dev.grokhome;

import android.os.Handler;
import android.os.Looper;
import java.util.HashSet;
import java.util.Set;

/**
 * Scripted demo of the whole team, for showing the app when no bot feed is available.
 * Some bots work, some celebrate again and again, some have a hand up, some do nothing,
 * and some carry a new-reply dot. Nothing is sent to any bot and nothing is saved.
 */
final class DemoMode {
    /** Boolean intent extra that turns the demo on or off, for adb and tests. */
    static final String EXTRA = "dev.grokhome.DEMO_MODE";

    static final String[] WORKING = {"Echo", "Luna", "Willow"};
    static final String[] CELEBRATING = {"Clover", "Scout"};
    static final String[] HANDS = {"Sage", "Orion", "Ember"};
    static final String[] QUESTIONS = {"Merge the Tesla page fix?", "Keep the paid plan?", "Ship the caption fix?"};
    static final String[] UNREAD = {"Scout", "Atlas", "Willow", "Nova", "Echo"};

    /** Each celebrating bot throws confetti this often. Bots start one stagger apart so there is always some. */
    static final long CONFETTI_EVERY_MS = 3600L;
    static final long CONFETTI_STAGGER_MS = 1800L;

    interface Host {
        /** Redraw the carousel, chooser badges and wall from the current state. */
        void demoChanged();
    }

    private final BotStageState stage;
    private final BotActivityState activity;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean on;
    private int token;

    DemoMode(BotStageState stage, BotActivityState activity, Host host) {
        this.stage = stage;
        this.activity = activity;
        this.host = host;
    }

    boolean on() { return on; }

    void start() {
        if (on || stage == null || activity == null) return;
        on = true;
        Set<String> working = new HashSet<>();
        for (String name : WORKING) working.add(name);
        Set<String> unread = new HashSet<>();
        for (String name : UNREAD) unread.add(name);
        activity.setDemo(working, unread);

        long now = System.currentTimeMillis();
        for (String name : BotIdentity.NAMES) {
            if (contains(WORKING, name)) {
                stage.debugLock(name, BotStageState.State.WORKING, null, "", now);
            } else if (contains(HANDS, name)) {
                stage.debugLock(name, BotStageState.State.NEEDS_YOU, NeedsYou.debug(name, QUESTIONS[indexOf(HANDS, name)]), "", now);
            } else if (!contains(CELEBRATING, name)) {
                stage.debugLock(name, BotStageState.State.IDLE, null, "", now);
            }
        }
        for (int i = 0; i < CELEBRATING.length; i++) scheduleConfetti(CELEBRATING[i], i * CONFETTI_STAGGER_MS);
        host.demoChanged();
    }

    void stop() {
        if (!on) return;
        release();
        host.demoChanged();
    }

    /** Stops the timers and puts everything back to live data, without redrawing. */
    void release() {
        on = false;
        handler.removeCallbacksAndMessages(null);
        if (activity != null) activity.clearDemo();
        if (stage != null) stage.forgetDebug();
    }

    private void scheduleConfetti(String name, long delay) {
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!on) return;
                stage.debugLock(name, BotStageState.State.DONE, null, "demo-done-" + (++token), System.currentTimeMillis());
                host.demoChanged();
                handler.postDelayed(this, CONFETTI_EVERY_MS);
            }
        }, delay);
    }

    private static boolean contains(String[] names, String name) { return indexOf(names, name) >= 0; }

    private static int indexOf(String[] names, String name) {
        for (int i = 0; i < names.length; i++) if (names[i].equals(name)) return i;
        return -1;
    }
}

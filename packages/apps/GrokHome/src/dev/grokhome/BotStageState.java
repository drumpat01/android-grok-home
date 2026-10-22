package dev.grokhome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-bot stage reducer. Needs you outranks done, then working, then idle.
 * A hand is raised only from {@code needs_you} items, never from message text.
 *
 * <p>Implements {@link NeedsYouSource}. Ember: pass this to {@code AmbientWall.setNeedsYou}
 * and read {@link #items()}. Also {@link #frame(String)} and {@link #hands(String)}
 * for the stage. {@link #tick} expires one-shots. Snooze with {@link #snooze}.
 */
final class BotStageState implements NeedsYouSource {
    static final long HAND_HOLD_MS = 30L * 60L * 1000L;
    static final long TOKEN_WINDOW_MS = 10_000L;
    static final long NUDGE_GAP_MS = 10L * 60L * 1000L;
    static final long DISMISS_HOLD_MS = 10L * 60L * 1000L;

    enum State { IDLE, WORKING, DONE, NEEDS_YOU }
    enum OneShot { NONE, DONE, RAISE, NUDGE, LOWER }
    enum Clear { NONE, ANSWERED, EXPIRED }

    static final class Input {
        boolean stale;
        boolean working;
        boolean tabletBusy;
        String phase;
        String replyToken = "";
        NeedsYou.Item[] needsYou = new NeedsYou.Item[0];
        boolean reducedMotion;
        boolean quietHours;
    }

    static final class Frame {
        final State state;
        final OneShot oneShot;
        final long oneShotStartMillis;
        final long sinceMillis;
        final boolean held;
        final boolean showRing;
        final boolean breathing;
        final String question;
        final String questionShort;
        final int openCount;
        final String needsYouId;
        final String celebrateToken;
        final boolean quiet;
        final boolean reduced;

        Frame(State state, OneShot oneShot, long oneShotStartMillis, long sinceMillis, boolean held,
                boolean showRing, boolean breathing, String question, String questionShort, int openCount,
                String needsYouId, String celebrateToken, boolean quiet, boolean reduced) {
            this.state = state;
            this.oneShot = oneShot;
            this.oneShotStartMillis = oneShotStartMillis;
            this.sinceMillis = sinceMillis;
            this.held = held;
            this.showRing = showRing;
            this.breathing = breathing;
            this.question = question == null ? "" : question;
            this.questionShort = questionShort == null ? "" : questionShort;
            this.openCount = openCount;
            this.needsYouId = needsYouId == null ? "" : needsYouId;
            this.celebrateToken = celebrateToken;
            this.quiet = quiet;
            this.reduced = reduced;
        }

        static Frame idle() {
            return new Frame(State.IDLE, OneShot.NONE, 0L, 0L, false, false, false, "", "", 0, "", null, false, false);
        }
    }

    private final ConfettiGate.Store celebrated;
    private final Map<String, Memory> bots = new HashMap<>();
    private final Map<String, Long> snoozeUntil = new HashMap<>();
    private String pendingAnnounce = "";
    private boolean debugForcePill;
    private Runnable debugListener;

    BotStageState(ConfettiGate.Store celebrated) {
        this.celebrated = celebrated == null ? new MapStore() : celebrated;
    }

    Frame observe(String bot, Input input, long now) {
        if (bot == null || BotIdentity.index(bot) < 0) return Frame.idle();
        Memory memory = memory(bot);
        if (memory.debug) return frame(memory, now);
        Input in = input == null ? new Input() : input;
        boolean reduced = in.reducedMotion;
        boolean quiet = in.quietHours;
        memory.reduced = reduced;
        memory.quiet = quiet;
        String phase = in.phase == null ? "" : in.phase;
        boolean error = "error".equals(phase);
        boolean cancelled = "stopped".equals(phase) || "cancelled".equals(phase) || "stop_failed".equals(phase);
        boolean tabletComplete = "finished".equals(phase) || "complete".equals(phase);
        boolean liveWorking = !in.stale && !error && !cancelled && (in.working || in.tabletBusy
                || "working".equals(phase) || "stopping".equals(phase));
        String token = in.replyToken == null ? "" : in.replyToken;
        NeedsYou.Item[] raw = NeedsYou.active(in.needsYou, now);
        NeedsYou.Item[] incoming = raw;
        Clear clear = Clear.NONE;
        if (in.stale && raw.length == 0 && memory.hands.length > 0 && now - memory.handsAt <= HAND_HOLD_MS) {
            incoming = memory.hands;
        } else if (in.stale && raw.length == 0 && memory.hands.length > 0) {
            incoming = new NeedsYou.Item[0];
            memory.hands = incoming;
            memory.dimmed = false;
        } else if (!in.stale || raw.length > 0) {
            if (!in.stale && raw.length == 0 && memory.hands.length > 0) {
                clear = expired(memory.hands, now) ? Clear.EXPIRED : Clear.ANSWERED;
            }
            incoming = unsnoozed(raw, now);
            memory.hands = incoming;
            memory.handsAt = now;
            memory.dimmed = raw.length > 0 && incoming.length == 0;
        }
        memory.clear = clear;
        boolean needs = incoming.length > 0;
        if (liveWorking) memory.lastWorkingAt = now;

        if (!memory.seen) {
            memory.seen = true;
            memory.wasWorking = liveWorking;
            memory.pendingWorking = liveWorking;
            memory.seenToken = token;
            memory.hands = incoming;
            memory.handsAt = now;
            memory.since = now;
            memory.oneShot = OneShot.NONE;
            memory.held = needs;
            memory.state = needs ? State.NEEDS_YOU : liveWorking ? State.WORKING : State.IDLE;
            memory.celebrateToken = null;
            rememberQuestion(memory, incoming);
            return frame(memory, now);
        }

        boolean idleEdge = memory.wasWorking && !liveWorking && !in.stale;
        long age = idleEdge ? Math.max(0L, now - memory.lastWorkingAt) : 0L;
        boolean newToken = !token.isEmpty() && !token.equals(memory.seenToken);
        boolean doneEdge = !needs && !in.stale && !error && !cancelled
                && ((idleEdge && newToken && age <= TOKEN_WINDOW_MS) || tabletComplete);
        if (!token.isEmpty()) memory.seenToken = token;
        memory.pendingWorking = liveWorking;

        if (needs) {
            boolean arrived = memory.state != State.NEEDS_YOU;
            memory.state = State.NEEDS_YOU;
            memory.celebrateToken = null;
            if (arrived) {
                memory.held = false;
                memory.since = now;
                if (quiet || reduced) {
                    memory.oneShot = OneShot.NONE;
                    memory.held = true;
                    memory.lastNudge = now;
                } else {
                    memory.oneShot = OneShot.RAISE;
                    memory.oneShotStart = now;
                    memory.pendingAnnounce = true;
                }
            }
            rememberQuestion(memory, incoming);
            memory.wasWorking = liveWorking;
            return frame(memory, now);
        }

        if (memory.state == State.NEEDS_YOU && memory.oneShot != OneShot.LOWER) {
            memory.oneShot = OneShot.LOWER;
            memory.oneShotStart = now;
            memory.wasWorking = liveWorking;
            rememberQuestion(memory, incoming);
            return frame(memory, now);
        }

        if (memory.oneShot == OneShot.DONE || memory.oneShot == OneShot.LOWER || memory.oneShot == OneShot.RAISE) {
            memory.wasWorking = liveWorking;
            expire(memory, now);
            return frame(memory, now);
        }

        if (doneEdge && memory.state != State.DONE) {
            memory.state = State.DONE;
            memory.oneShot = (quiet || reduced) ? OneShot.NONE : OneShot.DONE;
            memory.oneShotStart = now;
            memory.since = now;
            memory.celebrateToken = null;
            if (memory.oneShot == OneShot.DONE && newToken) {
                long gateAge = idleEdge ? age : 0L;
                ConfettiGate.Decision decision = ConfettiGate.decide(celebrated.get(bot), token, gateAge, false, false);
                if (decision.storeToken != null) celebrated.put(bot, decision.storeToken);
                if (decision.celebrate && !quiet && !reduced) memory.celebrateToken = token;
            } else if (idleEdge && newToken && age > ConfettiGate.MAX_EDGE_AGE_MS) {
                celebrated.put(bot, token);
            }
            if (quiet || reduced) {
                memory.state = liveWorking ? State.WORKING : State.IDLE;
                memory.oneShot = OneShot.NONE;
            }
        } else if (idleEdge && newToken && age > ConfettiGate.MAX_EDGE_AGE_MS) {
            celebrated.put(bot, token);
            memory.state = State.IDLE;
            memory.oneShot = OneShot.NONE;
            memory.celebrateToken = null;
        } else if (liveWorking) {
            if (memory.state != State.WORKING) memory.since = now;
            memory.state = State.WORKING;
            memory.oneShot = OneShot.NONE;
            memory.celebrateToken = null;
        } else if (memory.state != State.DONE) {
            memory.state = State.IDLE;
            memory.oneShot = OneShot.NONE;
            memory.celebrateToken = null;
        }
        memory.wasWorking = liveWorking;
        expire(memory, now);
        rememberQuestion(memory, incoming);
        return frame(memory, now);
    }

    void tick(long now) {
        for (Memory memory : bots.values()) {
            if (memory.debug) continue;
            expire(memory, now);
            if (memory.quiet && memory.state == State.NEEDS_YOU) memory.lastNudge = now;
            if (memory.state == State.NEEDS_YOU && memory.oneShot == OneShot.NONE && !memory.quiet && !memory.reduced
                    && !memory.debug && now >= memory.suppressUntil) {
                long anchor = memory.lastNudge == 0L ? memory.since : memory.lastNudge;
                if (now - anchor >= NUDGE_GAP_MS) {
                    memory.oneShot = OneShot.NUDGE;
                    memory.oneShotStart = now;
                    memory.lastNudge = now;
                }
            }
        }
    }

    Frame frame(String bot) {
        Memory memory = bots.get(bot);
        if (memory == null) return Frame.idle();
        return frame(memory, System.currentTimeMillis());
    }

    NeedsYou.Item[] hands(String bot) {
        Memory memory = bots.get(bot);
        if (memory == null || memory.hands == null) return new NeedsYou.Item[0];
        return memory.hands;
    }

    /** Raised hands only. A snoozed hand is not up, so it is left out. */
    @Override public List<NeedsYouNotice> items() {
        List<NeedsYouNotice> rows = new ArrayList<>();
        for (Map.Entry<String, Memory> entry : bots.entrySet()) {
            Memory memory = entry.getValue();
            if (memory.dimmed || memory.state != State.NEEDS_YOU || memory.hands == null) continue;
            for (NeedsYou.Item item : memory.hands) {
                if (item == null) continue;
                rows.add(NeedsYou.notice(entry.getKey(), item));
            }
        }
        return rows;
    }

    Clear clearReason(String bot) {
        Memory memory = bots.get(bot);
        return memory == null ? Clear.NONE : memory.clear;
    }

    /**
     * Chooser glyph. 1 is a raised hand, 2 is the same glyph while that hand is snoozed.
     */
    int handBadge(String bot) {
        Memory memory = bots.get(bot);
        if (memory == null) return 0;
        if (memory.dimmed) return 2;
        return memory.state == State.NEEDS_YOU ? 1 : 0;
    }

    boolean staleHands(String bot) {
        Memory memory = bots.get(bot);
        return memory != null && memory.hands.length > 0 && memory.state == State.NEEDS_YOU;
    }

    void snooze(String id, long untilMillis) {
        snooze(id, untilMillis, System.currentTimeMillis());
    }

    void snooze(String id, long untilMillis, long now) {
        if (id == null || id.isEmpty()) return;
        snoozeUntil.put(id, untilMillis);
        for (Memory memory : bots.values()) {
            if (memory.hands.length == 0) continue;
            NeedsYou.Item[] next = unsnoozed(memory.hands, now);
            if (next.length != memory.hands.length) {
                memory.hands = next;
                memory.dimmed = next.length == 0;
                if (next.length == 0 && memory.state == State.NEEDS_YOU && memory.oneShot != OneShot.LOWER) {
                    memory.oneShot = OneShot.LOWER;
                    memory.oneShotStart = now;
                }
            }
        }
    }

    void noteDismiss(String bot, long now) {
        Memory memory = bots.get(bot);
        if (memory != null) memory.suppressUntil = now + DISMISS_HOLD_MS;
    }

    boolean snoozed(String id, long now) {
        Long until = snoozeUntil.get(id);
        return until != null && until > now;
    }

    /**
     * Demo lock. Later snapshots do not clear it until {@link #clearDebug}.
     * The done hop does not expire while this lock is held, so the wall can
     * still read Done after the stage has been up for a few seconds.
     */
    void debugLock(String bot, State state, NeedsYou.Item item, String token, long now) {
        if (bot == null || BotIdentity.index(bot) < 0 || state == null) return;
        Memory memory = memory(bot);
        memory.debug = true;
        memory.seen = true;
        memory.state = state;
        memory.since = now;
        memory.oneShotStart = now;
        memory.held = false;
        memory.reduced = false;
        memory.quiet = false;
        memory.hands = item == null ? new NeedsYou.Item[0] : new NeedsYou.Item[] {item};
        memory.handsAt = now;
        memory.pendingWorking = state == State.WORKING;
        memory.wasWorking = state == State.WORKING;
        memory.clear = Clear.NONE;
        if (state == State.NEEDS_YOU) {
            memory.oneShot = OneShot.RAISE;
            memory.pendingAnnounce = true;
        } else if (state == State.DONE) {
            memory.oneShot = OneShot.DONE;
            memory.celebrateToken = token == null || token.isEmpty() ? "debug-done" : token;
            celebrated.put(bot, memory.celebrateToken);
        } else if (state == State.WORKING) {
            memory.oneShot = OneShot.NONE;
            memory.celebrateToken = null;
        } else {
            memory.oneShot = OneShot.NONE;
            memory.celebrateToken = null;
        }
        rememberQuestion(memory, memory.hands);
        notifyDebug();
    }

    /** Demo mode off: forget every locked bot so live data starts clean. */
    void forgetDebug() {
        bots.values().removeIf(memory -> memory.debug);
        notifyDebug();
    }

    void clearDebug() {
        for (Memory memory : bots.values()) memory.debug = false;
        notifyDebug();
    }

    /** Debug builds only. The wall treats placed needs-you tiles as off screen for the pill. */
    void setDebugListener(Runnable listener) { debugListener = listener; }

    void debugForcePill(boolean on) {
        debugForcePill = on;
        notifyDebug();
    }

    boolean debugForcePill() { return debugForcePill; }

    private void notifyDebug() {
        if (debugListener != null) debugListener.run();
    }

    String takeAnnouncement() {
        String name = "";
        for (Map.Entry<String, Memory> entry : bots.entrySet()) {
            if (entry.getValue().pendingAnnounce) {
                entry.getValue().pendingAnnounce = false;
                if (name.isEmpty()) name = entry.getKey();
            }
        }
        pendingAnnounce = name;
        return name;
    }

    private Frame frame(Memory memory, long now) {
        expire(memory, now);
        NeedsYou.Item oldest = memory.hands.length == 0 ? null : memory.hands[0];
        boolean breath = memory.state == State.NEEDS_YOU && memory.oneShot == OneShot.NONE && !memory.quiet
                && !memory.reduced && now - memory.since >= 0 && now - memory.since < 60_000L;
        return new Frame(memory.state, memory.oneShot, memory.oneShotStart, memory.since, memory.held,
                memory.state == State.WORKING, breath, memory.question, memory.questionShort, memory.hands.length,
                oldest == null ? "" : oldest.id, memory.celebrateToken, memory.quiet, memory.reduced);
    }

    private void expire(Memory memory, long now) {
        if (memory.debug) return;
        if (memory.oneShot == OneShot.DONE && now - memory.oneShotStart >= StagePose.DONE_MS) {
            memory.oneShot = OneShot.NONE;
            memory.celebrateToken = null;
            memory.state = memory.pendingWorking ? State.WORKING : State.IDLE;
            memory.since = now;
        } else if (memory.oneShot == OneShot.LOWER && now - memory.oneShotStart >= StagePose.LOWER_MS) {
            memory.oneShot = OneShot.NONE;
            memory.state = memory.pendingWorking ? State.WORKING : State.IDLE;
            memory.since = now;
            memory.celebrateToken = null;
        } else if (memory.oneShot == OneShot.RAISE && now - memory.oneShotStart >= StagePose.RAISE_MS) {
            memory.oneShot = OneShot.NONE;
            memory.since = now;
            memory.lastNudge = now;
            memory.held = true;
        } else if (memory.oneShot == OneShot.NUDGE && now - memory.oneShotStart >= StagePose.NUDGE_MS) {
            memory.oneShot = OneShot.NONE;
        }
    }

    private NeedsYou.Item[] unsnoozed(NeedsYou.Item[] items, long now) {
        if (items == null || items.length == 0) return new NeedsYou.Item[0];
        int count = 0;
        for (NeedsYou.Item item : items) if (item != null && !snoozed(item.id, now)) count++;
        if (count == items.length) return items;
        NeedsYou.Item[] kept = new NeedsYou.Item[count];
        int index = 0;
        for (NeedsYou.Item item : items) if (item != null && !snoozed(item.id, now)) kept[index++] = item;
        return kept;
    }

    private static boolean expired(NeedsYou.Item[] items, long now) {
        if (items == null || items.length == 0) return false;
        for (NeedsYou.Item item : items) if (item != null && !item.expired(now)) return false;
        return true;
    }

    private static void rememberQuestion(Memory memory, NeedsYou.Item[] items) {
        if (items == null || items.length == 0) {
            memory.question = "";
            memory.questionShort = "";
            return;
        }
        memory.question = items[0].question;
        memory.questionShort = items[0].questionShort;
    }

    private Memory memory(String bot) {
        Memory memory = bots.get(bot);
        if (memory == null) {
            memory = new Memory();
            bots.put(bot, memory);
        }
        return memory;
    }

    private static final class Memory {
        boolean seen;
        boolean debug;
        boolean wasWorking;
        boolean pendingWorking;
        boolean held;
        boolean reduced;
        boolean quiet;
        boolean pendingAnnounce;
        boolean dimmed;
        long lastWorkingAt;
        long handsAt;
        long since;
        long oneShotStart;
        long lastNudge;
        long suppressUntil;
        String seenToken = "";
        String question = "";
        String questionShort = "";
        String celebrateToken;
        State state = State.IDLE;
        OneShot oneShot = OneShot.NONE;
        Clear clear = Clear.NONE;
        NeedsYou.Item[] hands = new NeedsYou.Item[0];
    }

    private static final class MapStore implements ConfettiGate.Store {
        private final Map<String, String> tokens = new HashMap<>();
        @Override public String get(String bot) { return tokens.getOrDefault(bot, ""); }
        @Override public void put(String bot, String token) { tokens.put(bot, token); }
    }
}

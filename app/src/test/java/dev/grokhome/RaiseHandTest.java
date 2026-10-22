package dev.grokhome;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Stage timing, confetti gating, and needs_you parsing. No Android regex. */
public class RaiseHandTest {
    @Test public void doneHopHitsTheKeyframes() {
        assertEquals(0, StagePose.doneHop(0), 0.0001);
        assertEquals(-0.02, StagePose.doneHop(120), 0.0001);
        assertEquals(0.14, StagePose.doneHop(600), 0.0001);
        assertEquals(0, StagePose.doneHop(1000), 0.0001);
        assertEquals(0.03, StagePose.doneHop(1125), 0.0001);
        assertEquals(0, StagePose.doneHop(1250), 0.0001);
        assertEquals(0, StagePose.doneHop(1800), 0.0001);
        assertTrue(StagePose.DONE_MS < 3000);
        assertTrue(StagePose.RAISE_MS < 3000);
        assertTrue(StagePose.NUDGE_MS < 3000);
        assertTrue(StagePose.LOWER_MS < 3000);
    }

    @Test public void riseAndWorkingStayInsideTheStyleTable() {
        StagePose pose = new StagePose();
        pose.fill(BotStageState.State.NEEDS_YOU, BotStageState.OneShot.RAISE, 400, "Max", 1f, false, false, true);
        assertEquals(0.12, pose.hop, 0.0001);
        pose.fill(BotStageState.State.NEEDS_YOU, BotStageState.OneShot.RAISE, 500, "Max", 1f, false, false, true);
        assertEquals(0.10, pose.hop, 0.0001);
        pose.fill(BotStageState.State.NEEDS_YOU, BotStageState.OneShot.RAISE, 700, "Max", 1f, false, false, true);
        assertEquals(0.045, pose.roll, 0.0001);
        assertEquals(0, pose.shift, 0.0001);
        for (String name : new String[] {"Nova", "Sage", "Orion", "Pixel"}) {
            pose.fill(BotStageState.State.NEEDS_YOU, BotStageState.OneShot.RAISE, 700, name, 1f, false, false, true);
            assertEquals(name, 0, pose.roll, 0.0001);
            assertEquals(name, 0, pose.pitch, 0.0001);
        }
        pose.fill(BotStageState.State.NEEDS_YOU, BotStageState.OneShot.RAISE, 700, "Pixel", 1f, false, false, true);
        assertTrue(Math.abs(pose.shift) <= 0.025 + 0.0001);
        assertTrue(Math.abs(pose.yaw) <= 0.08 + 0.0001);
        pose.fill(BotStageState.State.NEEDS_YOU, BotStageState.OneShot.RAISE, 700, "Scout", 1f, false, false, true);
        assertTrue(Math.abs(pose.yaw) <= 0.10 + 0.0001);
        assertEquals(-1.12f, StagePose.chipY("Orion"), 0.0001);
        pose.fill(BotStageState.State.WORKING, BotStageState.OneShot.NONE, 400, "Luna", 1f, false, false, true);
        assertTrue(pose.hop <= 0.02);
        pose.fill(BotStageState.State.DONE, BotStageState.OneShot.DONE, 600, "Nova", 1.5f, false, false, true);
        assertEquals(0.14, pose.hop, 0.0001);
        pose.fill(BotStageState.State.NEEDS_YOU, BotStageState.OneShot.NONE, 1000, "Echo", 0f, false, false, true);
        assertEquals(StagePose.HELD_HOP, pose.hop, 0.0001);
        assertEquals(0, pose.yaw, 0.0001);
        assertEquals(StagePose.RING_FROZEN, pose.ringPhase, 0.0001);
        pose.fill(BotStageState.State.NEEDS_YOU, BotStageState.OneShot.RAISE, 700, "Max", 1f, false, true, true);
        assertFalse(pose.showGlow);
        assertEquals(0.45f, pose.chipAlpha, 0.0001);
        assertEquals(0, pose.yaw, 0.0001);
        pose.fill(BotStageState.State.WORKING, BotStageState.OneShot.NONE, 800, "Scout", 1f, false, false, false);
        assertEquals(0, pose.hop, 0.0001);
        assertEquals(0.18, pose.yaw, 0.0001);
        // The working ring keeps turning even when the bot is not in a slot.
        assertEquals(StagePose.RING_FROZEN + 800 / 3200.0 * Math.PI * 2, pose.ringPhase, 0.0001);
        pose.fill(BotStageState.State.WORKING, BotStageState.OneShot.NONE, 800, "Scout", 1f, true, false, true);
        assertEquals(StagePose.RING_FROZEN, pose.ringPhase, 0.0001);
        for (String name : BotIdentity.NAMES) {
            assertTrue(name, Float.isFinite(StagePose.chipX(name)));
            assertTrue(name, Float.isFinite(StagePose.chipY(name)));
        }
    }

    @Test public void confettiGatePersistsTheTokenAndSkipsAStaleEdge() {
        assertFalse(ConfettiGate.decide("", "reply", 0, false, true).celebrate);
        assertFalse(ConfettiGate.decide("", "reply", 0, true, false).celebrate);
        assertFalse(ConfettiGate.decide("", "", 0, false, false).celebrate);
        assertFalse(ConfettiGate.decide("reply", "reply", 0, false, false).celebrate);
        ConfettiGate.Decision fresh = ConfettiGate.decide("", "reply", 0, false, false);
        assertTrue(fresh.celebrate);
        assertEquals("reply", fresh.storeToken);
        assertTrue(ConfettiGate.decide("", "reply", 60_000, false, false).celebrate);
        ConfettiGate.Decision late = ConfettiGate.decide("", "reply", 60_001, false, false);
        assertFalse(late.celebrate);
        assertEquals("reply", late.storeToken);
        assertFalse(ConfettiGate.decide(late.storeToken, "reply", 0, false, false).celebrate);
        Mem store = new Mem();
        store.put("Nova", "reply");
        BotStageState rebuilt = new BotStageState(store);
        rebuilt.observe("Nova", input(false, true, "", null), 1_000);
        BotStageState.Frame again = rebuilt.observe("Nova", input(false, false, "reply", null), 2_000);
        assertNull(again.celebrateToken);
    }

    @Test public void reducerIgnoresQuestionMarksAndCelebratesOnce() throws Exception {
        Mem store = new Mem();
        BotStageState stage = new BotStageState(store);
        JSONObject asking = new JSONObject().put("needs_you", new JSONArray()).put("entries", new JSONArray().put(new JSONObject().put("role", "Bot").put("text", "Ready?")));
        assertEquals(0, NeedsYou.parse(asking).length);
        BotStageState.Frame idle = stage.observe("Echo", input(false, false, "old", NeedsYou.parse(asking)), 5_000);
        assertEquals(BotStageState.State.IDLE, idle.state);

        BotStageState.Frame working = stage.observe("Echo", input(false, true, "old", null), 6_000);
        assertEquals(BotStageState.State.WORKING, working.state);
        assertTrue(working.showRing);

        NeedsYou.Item hand = approval("hand-1", "Merge the Tesla page fix?", 6_000);
        BotStageState.Frame both = stage.observe("Echo", input(false, true, "old", new NeedsYou.Item[] {hand}), 7_000);
        assertEquals(BotStageState.State.NEEDS_YOU, both.state);
        assertFalse(both.showRing);

        BotStageState first = new BotStageState(new Mem());
        BotStageState.Frame held = first.observe("Max", input(false, false, "", new NeedsYou.Item[] {hand}), 8_000);
        assertEquals(BotStageState.State.NEEDS_YOU, held.state);
        assertEquals(BotStageState.OneShot.NONE, held.oneShot);
        assertTrue(held.held);

        BotStageState raised = new BotStageState(new Mem());
        raised.observe("Sage", input(false, false, "", null), 9_000);
        BotStageState.Frame rise = raised.observe("Sage", input(false, false, "", new NeedsYou.Item[] {approval("hand-2", "Ship it?", 9_100)}), 9_200);
        assertEquals(BotStageState.OneShot.RAISE, rise.oneShot);

        BotStageState done = new BotStageState(store);
        done.observe("Luna", input(false, true, "a", null), 10_000);
        BotStageState.Frame hop = done.observe("Luna", input(false, false, "b", null), 11_000);
        assertEquals("b", hop.celebrateToken);
        assertEquals("b", store.get("Luna"));
        done.tick(11_000 + StagePose.DONE_MS);
        BotStageState.Frame repeat = done.observe("Luna", input(false, false, "b", null), 11_000 + StagePose.DONE_MS + 1);
        assertNull(repeat.celebrateToken);

        BotStageState late = new BotStageState(store);
        late.observe("Nova", input(false, true, "before", null), 20_000);
        BotStageState.Frame skipped = late.observe("Nova", input(false, false, "late-token", null), 90_000);
        assertNull(skipped.celebrateToken);
        assertEquals("late-token", store.get("Nova"));
        BotStageState next = new BotStageState(store);
        next.observe("Nova", input(false, true, "other", null), 100_000);
        BotStageState.Frame blocked = next.observe("Nova", input(false, false, "late-token", null), 101_000);
        assertNull(blocked.celebrateToken);

        BotStageState stale = new BotStageState(new Mem());
        stale.observe("Luna", input(false, true, "s", null), 1);
        BotStageState.Frame staleIdle = stale.observe("Luna", staleInput("fresh"), 2);
        assertFalse(staleIdle.state == BotStageState.State.DONE);

        BotStageState error = new BotStageState(new Mem());
        error.observe("Clover", input(false, true, "e", null), 1);
        BotStageState.Input failed = input(false, false, "e2", null);
        failed.phase = "error";
        assertFalse(error.observe("Clover", failed, 2).state == BotStageState.State.DONE);

        BotStageState overlap = new BotStageState(new Mem());
        overlap.observe("Pixel", input(false, true, "d", null), 1);
        BotStageState.Frame heldHand = overlap.observe("Pixel", input(false, false, "d2", new NeedsYou.Item[] {hand}), 2);
        assertEquals(BotStageState.State.NEEDS_YOU, heldHand.state);
        assertNull(heldHand.celebrateToken);

        BotStageState tablet = new BotStageState(new Mem());
        tablet.observe("Orion", input(false, true, "t", null), 1);
        BotStageState.Input finished = input(false, false, "t2", null);
        finished.phase = "finished";
        assertEquals(BotStageState.State.DONE, tablet.observe("Orion", finished, 2).state);

        BotStageState snooze = new BotStageState(new Mem());
        snooze.observe("Scout", input(false, false, "", null), 1);
        snooze.observe("Scout", input(false, false, "", new NeedsYou.Item[] {approval("snooze-me", "Wait?", 2)}), 3);
        snooze.snooze("snooze-me", 3 + 3_600_000L, 4);
        assertEquals(0, snooze.hands("Scout").length);
        assertEquals(2, snooze.handBadge("Scout"));
        snooze.tick(4 + StagePose.LOWER_MS);
        assertFalse(snooze.frame("Scout").state == BotStageState.State.NEEDS_YOU);

        BotStageState hold = new BotStageState(new Mem());
        hold.observe("Echo", input(false, false, "", new NeedsYou.Item[] {hand}), 1_000);
        BotStageState.Frame kept = hold.observe("Echo", staleInput(null), 1_000 + BotStageState.HAND_HOLD_MS);
        assertEquals(BotStageState.State.NEEDS_YOU, kept.state);
        long dropAt = 1_000 + BotStageState.HAND_HOLD_MS + 1;
        BotStageState.Frame dropping = hold.observe("Echo", staleInput(null), dropAt);
        assertEquals(BotStageState.OneShot.LOWER, dropping.oneShot);
        assertEquals(0, hold.hands("Echo").length);
        hold.tick(dropAt + StagePose.LOWER_MS);
        assertFalse(hold.frame("Echo").state == BotStageState.State.NEEDS_YOU);
    }

    @Test public void needsYouParseDropsGarbageAndExpiredRows() throws Exception {
        assertEquals(0, NeedsYou.parse((JSONObject) null).length);
        assertEquals(0, NeedsYou.parse(new JSONObject()).length);
        assertEquals(0, NeedsYou.parse(new JSONArray()).length);
        NeedsYou.Item[] rows = NeedsYou.parse(new JSONArray()
                .put(new JSONObject().put("id", "a").put("kind", "approval").put("question", "Approve this?").put("answerable_here", true).put("created", 2))
                .put(new JSONObject().put("id", "bad").put("kind", "note"))
                .put(new JSONObject().put("id", "q").put("kind", "question").put("question", "Which color?").put("created", 1).put("answerable_here", true)
                        .put("options", new JSONArray()
                                .put(new JSONObject().put("id", "1").put("label", "Red").put("recommended", true))
                                .put(new JSONObject().put("id", "2").put("label", "Blue"))
                                .put(new JSONObject().put("id", "3").put("label", "Green"))
                                .put(new JSONObject().put("id", "4").put("label", "Gold"))
                                .put(new JSONObject().put("id", "5").put("label", "Extra")))));
        assertEquals(2, rows.length);
        assertEquals("q", rows[0].id);
        assertFalse(rows[1].answerableHere);
        assertEquals(4, rows[0].options.length);
        NeedsYou.Item[] live = NeedsYou.active(new NeedsYou.Item[] {
                new NeedsYou.Item("old", "approval", "Gone", "Gone", "", 1, 50, false, new NeedsYou.Option[0]),
                rows[1]
        }, 50);
        assertEquals(1, live.length);
        assertEquals("a", live[0].id);
        assertEquals("Needs you · Approve this?", NeedsYou.tileLine(rows[1]));
        assertEquals("Needs you", NeedsYou.tileLine(new NeedsYou.Item("x", "error", "", "", "", 0, 0, false, new NeedsYou.Option[0])));
        NeedsYou.Item fake = NeedsYou.debug("Echo", "");
        assertEquals("approval", fake.kind);
        assertFalse(fake.answerableHere);
        assertEquals("Merge the Tesla page fix?", fake.question);
        assertEquals("Asked just now", NeedsYou.askedLabel(0, 10, ZoneId.of("UTC")));
    }

    @Test public void slotsPreferARiseAndCapAtTwo() {
        int count = 10;
        int[] priority = new int[count];
        boolean[] eligible = new boolean[count];
        boolean[] out = new boolean[count];
        for (int i = 0; i < count; i++) {
            priority[i] = MotionSlots.RISE;
            eligible[i] = true;
        }
        MotionSlots.grant(count, priority, eligible, out);
        int granted = 0;
        for (boolean slot : out) if (slot) granted++;
        assertEquals(2, granted);
        priority[0] = MotionSlots.DONE;
        priority[1] = MotionSlots.DONE;
        priority[2] = MotionSlots.RISE;
        for (int i = 3; i < count; i++) priority[i] = MotionSlots.NONE;
        MotionSlots.grant(count, priority, eligible, out);
        assertTrue(out[2]);
        assertTrue(out[0] ^ out[1]);
        eligible[2] = false;
        MotionSlots.grant(count, priority, eligible, out);
        assertFalse(out[2]);
        assertTrue(out[0]);
        assertTrue(out[1]);
    }

    @Test public void quietHoursWrapPastMidnight() {
        ZoneId zone = ZoneId.of("UTC");
        long night = java.time.ZonedDateTime.of(2024, 1, 1, 23, 0, 0, 0, zone).toInstant().toEpochMilli();
        assertTrue(QuietHours.active(true, QuietHours.DEFAULT_START, QuietHours.DEFAULT_END, 23 * 60));
        assertTrue(QuietHours.active(true, QuietHours.DEFAULT_START, QuietHours.DEFAULT_END, 6 * 60 + 59));
        assertFalse(QuietHours.active(true, QuietHours.DEFAULT_START, QuietHours.DEFAULT_END, 7 * 60));
        assertFalse(QuietHours.active(true, QuietHours.DEFAULT_START, QuietHours.DEFAULT_END, 12 * 60));
        assertFalse(QuietHours.active(false, QuietHours.DEFAULT_START, QuietHours.DEFAULT_END, 23 * 60));
        assertEquals(23 * 60, QuietHours.minuteOfDay(night, zone));
        assertEquals("Quiet hours until 7:00 AM", QuietHours.note(QuietHours.DEFAULT_END));
    }

    @Test public void stageItemsMatchTheWallNotice() throws Exception {
        BotStageState stage = new BotStageState(new Mem());
        assertTrue(stage.items().isEmpty());
        JSONObject asking = new JSONObject().put("needs_you", new JSONArray())
                .put("entries", new JSONArray().put(new JSONObject().put("text", "Ready?")));
        stage.observe("Sage", input(false, false, "", NeedsYou.parse(asking)), 5_000);
        assertTrue(stage.items().isEmpty());
        NeedsYou.Item hand = approval("handoff:req-1", "Merge the Tesla page fix?", 1_000);
        stage.observe("Sage", input(false, false, "", new NeedsYou.Item[] {hand}), 6_000);
        assertEquals(1, stage.items().size());
        NeedsYouNotice notice = stage.items().get(0);
        assertEquals("Sage", notice.bot);
        assertEquals("handoff:req-1", notice.id);
        assertEquals("Merge the Tesla page fix?", notice.question);
        assertEquals(1_000L, notice.created);
        NeedsYouNotice projected = NeedsYou.notice("Sage", hand);
        assertEquals(notice.id, projected.id);
        assertEquals(notice.questionShort, projected.questionShort);
        stage.snooze("handoff:req-1", 6_000 + 3_600_000L, 6_100);
        assertTrue(stage.items().isEmpty());
    }

    @Test public void copyAvoidsDashesAndUnlock() {
        String blob = NeedsYou.tileLine(NeedsYou.debug("Max", "Merge the Tesla page fix?"))
                + BotTalkBack.stateSentence(BotStageState.State.NEEDS_YOU, "Merge the Tesla page fix?")
                + BotTalkBack.stateSentence(BotStageState.State.WORKING, "")
                + BotTalkBack.stateSentence(BotStageState.State.DONE, "")
                + BotTalkBack.stateSentence(BotStageState.State.IDLE, "")
                + NeedsYou.askedLabel(0, 1, ZoneId.of("UTC"))
                + "Approve this in the Grok Bot app."
                + "Computer offline. Answers work again when it reconnects. Nothing is queued."
                + "Already answered in the Grok Bot app. The hand goes down now."
                + "This question expired."
                + "Tap the raised hand to answer";
        assertFalse(blob.contains("\u2014"));
        assertFalse(blob.contains("\u2013"));
        assertFalse(blob.toLowerCase().contains("unlock"));
        assertEquals("Approve this in the Grok Bot app.", QuestionCard.APPROVE);
        assertEquals("Computer offline. Answers work again when it reconnects. Nothing is queued.", QuestionCard.OFFLINE);
        assertEquals("Already answered in the Grok Bot app. The hand goes down now.", QuestionCard.ANSWERED);
        assertEquals("This question expired.", QuestionCard.EXPIRED);
        String card = QuestionCard.spoken("Echo needs you", "Merge the Tesla page fix?", QuestionCard.APPROVE);
        assertEquals("Echo needs you. Merge the Tesla page fix? Approve this in the Grok Bot app.", card);
        assertEquals(card, BotTalkBack.chipDescription("Echo", "Merge the Tesla page fix?"));
        assertFalse(card.contains("Double tap to answer"));
        assertEquals("Echo needs you. Merge the Tesla page fix? " + QuestionCard.OFFLINE,
                QuestionCard.spoken("Echo needs you", "Merge the Tesla page fix?", QuestionCard.OFFLINE));
        assertEquals("Echo. Echo" + QuestionCard.EMPTY_BODY_SUFFIX,
                QuestionCard.spoken("Echo", "Echo" + QuestionCard.EMPTY_BODY_SUFFIX, ""));
        assertFalse(card.contains("\u2014"));
        assertFalse(card.contains("\u2013"));
        assertFalse(card.toLowerCase().contains("unlock"));
    }

    @Test public void debugOverviewCentersTheNamedBotsOnATablet() {
        float density = DebugOverviewFocus.TABLET_DENSITY;
        DebugOverviewFocus.Fit echo = DebugOverviewFocus.fit("Echo", "Luna", "Nova",
                DebugOverviewFocus.TABLET_WIDTH, DebugOverviewFocus.TABLET_HEIGHT, density, 1f);
        assertNotNull(echo);
        assertEquals(4.5f, echo.slot, 0.001f);
        assertTrue(echo.gapScale < 1f);
        assertEquals(1f, echo.sizeScale, 0.001f);
        assertTrue(echo.keeps("Echo"));
        assertTrue(echo.keeps("Luna"));
        assertTrue(echo.keeps("Nova"));
        DebugOverviewFocus.Fit spread = DebugOverviewFocus.fit("Max", "Echo", "Luna",
                DebugOverviewFocus.TABLET_WIDTH, DebugOverviewFocus.TABLET_HEIGHT, density, 1f);
        assertNotNull(spread);
        assertTrue(spread.keeps("Max"));
        assertTrue(spread.keeps("Echo"));
        assertFalse(spread.keeps("Luna"));
        assertNull(DebugOverviewFocus.fit(null, "", "Nope", DebugOverviewFocus.TABLET_WIDTH, DebugOverviewFocus.TABLET_HEIGHT, density, 1f));
    }

    @Test public void debugDoneLockHoldsPastTheHopUntilCleared() {
        BotStageState stage = new BotStageState(new Mem());
        long then = System.currentTimeMillis() - StagePose.DONE_MS - 4_000L;
        stage.debugLock("Nova", BotStageState.State.DONE, null, "debug-done", then);
        assertEquals(BotStageState.State.DONE, stage.frame("Nova").state);
        assertEquals(BotStageState.OneShot.DONE, stage.frame("Nova").oneShot);
        assertEquals(BotStageState.State.DONE, stage.frame("Nova").state);
        stage.clearDebug();
        assertEquals(BotStageState.State.IDLE, stage.frame("Nova").state);
        assertFalse(NeedsYou.hasQuestion(null));
        assertFalse(NeedsYou.hasQuestion(new NeedsYou.Item[] {
                new NeedsYou.Item("blank", "approval", "  ", "", "", 1L, 0L, false, null)}));
        assertTrue(NeedsYou.hasQuestion(new NeedsYou.Item[] {NeedsYou.debug("Sage", "Merge the Tesla page fix?")}));
    }

    @Test public void debugFocusWithoutOverviewUsesTheFirstNamedBot() {
        assertEquals("Max", RaiseHandDebug.focusName("Max", null, "", false));
        assertEquals("Nova", RaiseHandDebug.focusName("Nova", "Echo", "Pixel", false));
        assertEquals("Echo", RaiseHandDebug.focusName("Echo", "Luna", "Nova", false));
    }

    private static BotStageState.Input input(boolean stale, boolean working, String token, NeedsYou.Item[] hands) {
        BotStageState.Input input = new BotStageState.Input();
        input.stale = stale;
        input.working = working;
        input.replyToken = token;
        input.needsYou = hands == null ? new NeedsYou.Item[0] : hands;
        return input;
    }

    private static BotStageState.Input staleInput(String token) {
        BotStageState.Input input = input(true, false, token, null);
        return input;
    }

    private static NeedsYou.Item approval(String id, String question, long created) throws Exception {
        return NeedsYou.parse(new JSONArray().put(new JSONObject()
                .put("id", id).put("kind", "approval").put("question", question).put("created", created).put("answerable_here", true)))[0];
    }

    private static final class Mem implements ConfettiGate.Store {
        private final Map<String, String> tokens = new HashMap<>();
        @Override public String get(String bot) { return tokens.getOrDefault(bot, ""); }
        @Override public void put(String bot, String token) { if (token != null) tokens.put(bot, token); }
    }
}

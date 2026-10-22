package dev.grokhome;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class AmbientAttentionTest {
    @Test public void quietHoursWrapsPastMidnight() {
        assertTrue(QuietHours.active(true, QuietHours.DEFAULT_START, QuietHours.DEFAULT_END, 23 * 60));
        assertTrue(QuietHours.active(true, 1260, 420, 30));
        assertTrue(QuietHours.active(true, 1260, 420, 419));
        assertFalse(QuietHours.active(true, 1260, 420, 420));
        assertFalse(QuietHours.active(true, 1260, 420, 20 * 60 + 59));
        assertTrue(QuietHours.active(true, 1260, 420, 21 * 60));
        assertFalse(QuietHours.active(true, 1260, 420, 12 * 60));
        assertFalse(QuietHours.active(false, 1260, 420, 23 * 60));
        assertFalse(QuietHours.active(true, 600, 600, 600));
        assertTrue(QuietHours.active(true, 9 * 60, 17 * 60, 12 * 60));
        assertFalse(QuietHours.active(true, 9 * 60, 17 * 60, 18 * 60));
        assertEquals("Quiet hours until 7:00 AM", QuietHours.note(420));
        assertEquals("Quiet hours until 9:00 PM", QuietHours.note(1260));
        assertEquals("Quiet hours until 12:30 PM", QuietHours.note(12 * 60 + 30));
        ZoneId chicago = ZoneId.of("America/Chicago");
        ZonedDateTime night = ZonedDateTime.of(2026, 10, 18, 23, 0, 0, 0, chicago);
        ZonedDateTime afterMidnight = ZonedDateTime.of(2026, 10, 19, 0, 30, 0, 0, chicago);
        assertEquals(23 * 60, QuietHours.minuteOfDay(night.toInstant().toEpochMilli(), chicago));
        assertEquals(30, QuietHours.minuteOfDay(afterMidnight.toInstant().toEpochMilli(), chicago));
        assertTrue(QuietHours.SETTINGS_DEFAULT_ENABLED);
    }

    @Test public void edgePillUsesTheOldestBotThenACount() {
        assertEquals("", EdgePill.label(List.of(), 300f, text -> 10f));
        assertEquals("Sage needs you \u203A", EdgePill.label(List.of("Sage"), 400f, text -> 10f));
        assertEquals("1 bot needs you \u203A", EdgePill.label(List.of("Sage"), 20f, text -> 80f));
        assertEquals("2 bots need you \u203A", EdgePill.label(List.of("Sage", "Clover"), 400f, text -> 10f));
        assertEquals("3 bots need you \u203A", EdgePill.label(List.of("Sage", "Clover", "Luna"), 40f, text -> 10f));
        assertTrue(EdgePill.textLimit(2f) < EdgePill.MAX_WIDTH_DP * 2f);
        assertEquals(48f, EdgePill.HEIGHT_DP, 0f);
        assertEquals(24f, EdgePill.MARGIN_DP, 0f);
        assertEquals(360f, EdgePill.MAX_WIDTH_DP, 0f);
    }

    @Test public void talkBackLabelsNameTheQuestionAndThePill() {
        assertEquals("Sage. Needs you: Merge the Tesla page fix? Double tap to open.", WallTalkBack.tile("Sage", "Merge the Tesla page fix?", false, false));
        assertEquals("Sage. Needs you: Merge the Tesla page fix? Double tap to open.", WallTalkBack.tile("Sage", "Merge the Tesla page fix?", true, true));
        assertFalse(WallTalkBack.tile("Sage", "Merge the Tesla page fix?", true, true).contains("Working"));
        assertEquals("Sage. Needs you: Ship the caption fix. Double tap to open.", WallTalkBack.tile("Sage", "Ship the caption fix", false, false));
        assertEquals("Sage. Needs you: Ship the caption fix. Double tap to open.", WallTalkBack.tile("Sage", "Ship the caption fix.", false, false));
        assertEquals("Sage. Needs you: Wait\u2026 Double tap to open.", WallTalkBack.tile("Sage", "Wait\u2026", false, false));
        assertEquals("Clover. Needs you. Double tap to open.", WallTalkBack.tile("Clover", "", false, false));
        assertEquals("Luna. Double tap to open chat.", WallTalkBack.tile("Luna", null, false, false));
        assertEquals("Luna. Working. Double tap to open chat.", WallTalkBack.tile("Luna", null, true, false));
        assertEquals("Nova. New reply. Double tap to open chat.", WallTalkBack.tile("Nova", null, false, true));
        assertEquals("Sage needs you. Double tap to open.", WallTalkBack.pill(1, "Sage"));
        assertEquals("2 bots need you. Double tap to open.", WallTalkBack.pill(2, "Sage"));
        assertEquals("4 bots need you. Double tap to open.", WallTalkBack.pill(4, "Orion"));
        assertNoAnswerPrompt(WallTalkBack.tile("Luna", null, false, false));
        assertNoAnswerPrompt(WallTalkBack.pill(2, "Sage"));
    }

    @Test public void oldestNeedsYouOutranksAWorkingFeaturedLine() {
        assertEquals("Sage", HomeActivity.featuredAmbientBot("Sage", "Max", "Nova"));
        assertEquals("Clover", HomeActivity.featuredAmbientBot("Clover", "Max", null));
        assertEquals("Max", HomeActivity.featuredAmbientBot(null, "Max", "Nova"));
        assertEquals("Max", HomeActivity.featuredAmbientBot("", "Max", "Nova"));
        assertEquals("Nova", HomeActivity.featuredAmbientBot(null, null, "Nova"));
        assertEquals("", HomeActivity.featuredAmbientBot(null, "", null));
    }

    @Test public void stageTileLabelsNameDoneWorkingNeedsYouAndIdle() {
        assertEquals("Nova. Done. Double tap to open chat.", WallTalkBack.tile("Nova", BotStageState.State.DONE, null));
        assertEquals("Luna. Working. Double tap to open chat.", WallTalkBack.tile("Luna", BotStageState.State.WORKING, null));
        assertEquals("Luna. Double tap to open chat.", WallTalkBack.tile("Luna", BotStageState.State.IDLE, null));
        String needs = WallTalkBack.tile("Sage", BotStageState.State.NEEDS_YOU, "Merge the Tesla page fix?");
        assertEquals("Sage. Needs you: Merge the Tesla page fix? Double tap to open.", needs);
        assertFalse(needs.contains("Working"));
        assertFalse(needs.contains("Done"));
        assertEquals("Clover. Needs you. Double tap to open.", WallTalkBack.tile("Clover", BotStageState.State.NEEDS_YOU, ""));
        assertNoAnswerPrompt(WallTalkBack.tile("Nova", BotStageState.State.DONE, null));
        assertNoAnswerPrompt(WallTalkBack.tile("Luna", BotStageState.State.WORKING, null));
        assertNoAnswerPrompt(needs);
        assertNoAnswerPrompt(WallTalkBack.tile("Luna", BotStageState.State.IDLE, null));
    }

    private static void assertNoAnswerPrompt(String label) {
        assertFalse(label, label.contains("Double tap to answer"));
    }

    @Test public void tileChipAndGlowUseThatBotsColor() {
        assertEquals(0xffd1d7dd, NeedsYouTint.mark("Nova", 0xffffffff));
        assertEquals(NeedsYouTint.KIT_MARK, NeedsYouTint.chipRing("Nova", 0xffffffff, false));
        assertEquals(0xff14c370, NeedsYouTint.mark("Clover", 0xff14c370));
        assertEquals(0xff905afb, NeedsYouTint.border("Sage", 0xff905afb, false));
        assertEquals(128, NeedsYouTint.border("Sage", 0xff905afb, true) >>> 24);
        assertEquals(0xff191c20, NeedsYouTint.chipFill(false));
        int quietChip = NeedsYouTint.chipGlyph(true);
        assertEquals(Math.round(0.45f * 255f), quietChip >>> 24);
        assertEquals(0.22f * 0.8f, NeedsYouTint.glowAlpha("Nova"), 0.0001f);
        assertEquals(0.22f * 1.2f, NeedsYouTint.glowAlpha("Orion"), 0.0001f);
        assertEquals(0.22f, NeedsYouTint.glowAlpha("Max"), 0.0001f);
        assertTrue(NeedsYouTint.glowAlpha("Orion") > NeedsYouTint.glowAlpha("Luna"));
    }
}

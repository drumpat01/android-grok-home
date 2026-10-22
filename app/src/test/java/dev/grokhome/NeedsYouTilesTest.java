package dev.grokhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

public class NeedsYouTilesTest {
    @Test public void oldestNeedsYouComesFirst() {
        List<NeedsYouNotice> notices = List.of(
            notice("Luna", "h", "Ship the caption fix?", 30),
            notice("Sage", "p", "Merge the Tesla page fix?", 10),
            notice("Clover", "s", "Keep the paid plan?", 20)
        );
        assertEquals(List.of("Sage", "Clover", "Luna"), names(NeedsYouTiles.order(notices, BotIdentity.NAMES, false)));
        assertEquals("Needs you \u00b7 Merge the Tesla page fix?", NeedsYouTiles.order(notices, BotIdentity.NAMES, false).get(0).line);
    }

    @Test public void tiesBreakByRosterThenIdAndOneTilePerBot() {
        List<NeedsYouNotice> notices = List.of(
            notice("Sage", "b", "Later question", 5),
            notice("Pixel", "s", "Same moment", 5),
            notice("Sage", "a", "Earlier id", 5),
            notice("Sage", "c", "Newer question", 9)
        );
        List<NeedsYouTiles.Tile> tiles = NeedsYouTiles.order(notices, BotIdentity.NAMES, false);
        assertEquals(List.of("Pixel", "Sage"), names(tiles));
        assertEquals("a", tiles.get(1).id);
        assertEquals("Needs you \u00b7 Earlier id", tiles.get(1).line);
    }

    @Test public void unknownBotsDropAndOrionStays() {
        List<NeedsYouNotice> notices = new ArrayList<>();
        notices.add(notice("Siri", "x", "Not on the roster", 1));
        notices.add(notice("", "y", "Missing bot", 1));
        notices.add(notice("Orion", "arm", "Check the gray triangle?", 4));
        notices.add(null);
        notices.add(new NeedsYouNotice("Nova", "", "No id", "", 2));
        List<NeedsYouTiles.Tile> tiles = NeedsYouTiles.order(notices, BotIdentity.NAMES, false);
        assertEquals(List.of("Orion"), names(tiles));
        assertEquals(14, BotIdentity.NAMES.length);
        assertEquals("Orion", BotIdentity.NAMES[6]);
    }

    @Test public void quietHoursDoesNotReorder() {
        List<NeedsYouNotice> notices = List.of(
            notice("Luna", "o", "Open the hexagon?", 8),
            notice("Echo", "i", "Heart check?", 3),
            notice("Max", "m", "Crown question?", 8)
        );
        assertEquals(names(NeedsYouTiles.order(notices, BotIdentity.NAMES, false)), names(NeedsYouTiles.order(notices, BotIdentity.NAMES, true)));
        assertEquals(List.of("Echo", "Max", "Luna"), names(NeedsYouTiles.order(notices, BotIdentity.NAMES, true)));
    }

    @Test public void emptyAndShortQuestionsStayClean() {
        assertEquals("Needs you \u00b7 Sign in", NeedsYouTiles.line(new NeedsYouNotice("Nova", "k", "  ", "Sign in", 1)));
        assertEquals("Needs you", NeedsYouTiles.line(notice("Nova", "k", "  ", 1)));
        String longQuestion = "Merge the Tesla page fix and then check the tablet caption width before we ship the build today please";
        String spoken = NeedsYouTiles.clip(longQuestion, 80);
        assertEquals(80, spoken.codePointCount(0, spoken.length()));
        assertFalse(spoken.contains("\u2014"));
        assertFalse(spoken.contains("\u2013"));
    }

    @Test public void nullInputIsEmpty() {
        assertTrue(NeedsYouTiles.order(null, BotIdentity.NAMES, false).isEmpty());
        assertTrue(NeedsYouTiles.order(List.of(), null, true).isEmpty());
    }

    private static NeedsYouNotice notice(String bot, String id, String question, long created) {
        return new NeedsYouNotice(bot, id, question, question, created);
    }

    private static List<String> names(List<NeedsYouTiles.Tile> tiles) {
        List<String> names = new ArrayList<>();
        for (NeedsYouTiles.Tile tile : tiles) names.add(tile.bot);
        return names;
    }

    @Test public void copyAvoidsDashesAndUnlock() {
        String[] lines = {
            NeedsYouTiles.line(notice("Sage", "p", "Merge the Tesla page fix?", 1)),
            NeedsYouTiles.line(notice("Sage", "p", "", 1)),
            QuietHours.note(420),
            QuietHours.note(1260),
            EdgePill.label(List.of("Sage"), 400f, text -> text.length() * 8f),
            EdgePill.label(List.of("Sage", "Clover", "Luna"), 400f, text -> 10f),
            WallTalkBack.tile("Sage", "Merge the Tesla page fix?", false, false),
            WallTalkBack.tile("Luna", null, false, false),
            WallTalkBack.pill(1, "Sage"),
            WallTalkBack.pill(2, "Sage")
        };
        for (String line : lines) {
            assertFalse(line, line.indexOf('\u2014') >= 0 || line.indexOf('\u2013') >= 0);
            assertFalse(line, line.toLowerCase(Locale.US).contains("unlock"));
        }
    }
}

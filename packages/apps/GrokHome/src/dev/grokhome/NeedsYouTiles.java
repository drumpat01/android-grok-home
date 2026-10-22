package dev.grokhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Needs-you tiles in attention order: oldest first.
 * Ties break by roster order, then id. Bots outside the roster are dropped.
 * Quiet hours does not change this order.
 */
final class NeedsYouTiles {
    static final class Tile {
        final String bot;
        final String id;
        final String line;
        final String spoken;
        final long created;

        Tile(String bot, String id, String line, String spoken, long created) {
            this.bot = bot;
            this.id = id;
            this.line = line;
            this.spoken = spoken;
            this.created = created;
        }
    }

    static List<Tile> order(List<NeedsYouNotice> notices, String[] roster, boolean quietHours) {
        List<Tile> ranked = rank(notices, roster);
        // Quiet hours changes brightness only. Both paths keep oldest-first order.
        if (quietHours) return Collections.unmodifiableList(new ArrayList<>(ranked));
        return Collections.unmodifiableList(ranked);
    }

    static String line(NeedsYouNotice notice) {
        String body = body(notice);
        if (body.isEmpty()) return "Needs you";
        return "Needs you \u00b7 " + body;
    }

    private static List<Tile> rank(List<NeedsYouNotice> notices, String[] roster) {
        Map<String, Integer> index = new HashMap<>();
        if (roster != null) {
            for (int i = 0; i < roster.length; i++) {
                if (roster[i] != null && !index.containsKey(roster[i])) index.put(roster[i], i);
            }
        }
        Map<String, NeedsYouNotice> oldest = new HashMap<>();
        if (notices != null) {
            for (NeedsYouNotice notice : notices) {
                if (notice == null || notice.bot.isEmpty() || notice.id.isEmpty()) continue;
                if (!index.containsKey(notice.bot)) continue;
                NeedsYouNotice prior = oldest.get(notice.bot);
                if (prior == null || before(notice, prior)) oldest.put(notice.bot, notice);
            }
        }
        List<NeedsYouNotice> rows = new ArrayList<>(oldest.values());
        rows.sort((left, right) -> {
            int byTime = Long.compare(left.created, right.created);
            if (byTime != 0) return byTime;
            int byRoster = Integer.compare(index.get(left.bot), index.get(right.bot));
            if (byRoster != 0) return byRoster;
            return left.id.compareTo(right.id);
        });
        List<Tile> tiles = new ArrayList<>(rows.size());
        for (NeedsYouNotice notice : rows) {
            String spoken = clip(body(notice), 80);
            tiles.add(new Tile(notice.bot, notice.id, line(notice), spoken, notice.created));
        }
        return tiles;
    }

    private static boolean before(NeedsYouNotice notice, NeedsYouNotice prior) {
        if (notice.created != prior.created) return notice.created < prior.created;
        return notice.id.compareTo(prior.id) < 0;
    }

    private static String body(NeedsYouNotice notice) {
        String question = oneLine(notice.question);
        if (!question.isEmpty()) return question;
        return oneLine(notice.questionShort);
    }

    private static String oneLine(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    static String clip(String value, int codePoints) {
        if (value == null || value.isEmpty()) return "";
        if (value.codePointCount(0, value.length()) <= codePoints) return value;
        return value.substring(0, value.offsetByCodePoints(0, codePoints));
    }

    private NeedsYouTiles() {}
}

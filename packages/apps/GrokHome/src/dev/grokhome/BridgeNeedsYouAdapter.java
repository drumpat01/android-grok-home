package dev.grokhome;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter over the activity snapshots the bridge already returns.
 * Each snapshot is one bot plus a needs_you array. Message text is ignored.
 */
final class BridgeNeedsYouAdapter implements NeedsYouSource {
    private static final int QUESTION_CAP = 600;
    private final Map<String, List<NeedsYouNotice>> byBot = new LinkedHashMap<>();
    private long nowMs = System.currentTimeMillis();

    static NeedsYouSource empty() {
        return Collections::emptyList;
    }

    void setClock(long nowMs) {
        this.nowMs = nowMs;
    }

    /** Replace one bot's rows. An empty or missing needs_you clears that bot. */
    void accept(JSONObject snapshot) {
        if (snapshot == null) return;
        String bot = snapshot.optString("bot", "").trim();
        if (bot.isEmpty()) return;
        byBot.put(bot, parseItems(bot, snapshot.optJSONArray("needs_you"), nowMs));
    }

    @Override public List<NeedsYouNotice> items() {
        List<NeedsYouNotice> all = new ArrayList<>();
        for (List<NeedsYouNotice> rows : byBot.values()) all.addAll(rows);
        return all;
    }

    static List<NeedsYouNotice> parse(JSONObject snapshot, long nowMs) {
        if (snapshot == null) return List.of();
        String bot = snapshot.optString("bot", "").trim();
        if (bot.isEmpty()) return List.of();
        return parseItems(bot, snapshot.optJSONArray("needs_you"), nowMs);
    }

    private static List<NeedsYouNotice> parseItems(String bot, JSONArray rows, long nowMs) {
        if (rows == null || rows.length() == 0) return List.of();
        List<NeedsYouNotice> notices = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String id = row.optString("id", "").trim();
            if (id.isEmpty()) continue;
            if (row.has("expires") && !row.isNull("expires")) {
                long expires = row.optLong("expires", 0L);
                if (expires > 0L && expires <= nowMs) continue;
            }
            long created = row.optLong("created", 0L);
            if (created <= 0L) created = nowMs;
            String question = clip(row.optString("question", ""), QUESTION_CAP);
            String questionShort = clip(row.optString("question_short", ""), 80);
            notices.add(new NeedsYouNotice(bot, id, question, questionShort, created));
        }
        return notices;
    }

    private static String clip(String value, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() <= max) return trimmed;
        return trimmed.substring(0, max);
    }

    BridgeNeedsYouAdapter() {}
}

package dev.grokhome;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Parsed {@code needs_you} rows from {@code /bot/activity}.
 *
 * <p>The wall reads {@link NeedsYouSource#items()} as {@link NeedsYouNotice}.
 * {@link #notice(String, Item)} projects a card row onto that shape.
 * {@link #parse(JSONObject)} and {@link #parse(JSONArray)} still build {@link Item}
 * for the question card, which also carries kind, context, expires, and options.
 * A missing or empty array is empty. Message text never becomes a hand.
 * Wall tile copy is {@code NeedsYouTiles.line}, not {@link #tileLine(Item)}.
 */
final class NeedsYou {
    static final String KIND_APPROVAL = "approval";
    static final String KIND_QUESTION = "question";
    static final String KIND_ERROR = "error";
    static final int QUESTION_CAP = 600;
    static final int CONTEXT_CAP = 80;

    static final class Option {
        final String id;
        final String label;
        final boolean recommended;
        final boolean confirm;

        Option(String id, String label, boolean recommended, boolean confirm) {
            this.id = id;
            this.label = label;
            this.recommended = recommended;
            this.confirm = confirm;
        }
    }

    static final class Item {
        final String id;
        final String kind;
        final String question;
        final String questionShort;
        final String context;
        final long created;
        final long expires;
        final boolean answerableHere;
        final Option[] options;

        Item(String id, String kind, String question, String questionShort, String context,
                long created, long expires, boolean answerableHere, Option[] options) {
            this.id = id;
            this.kind = kind;
            this.question = question;
            this.questionShort = questionShort;
            this.context = context;
            this.created = created;
            this.expires = expires;
            this.answerableHere = answerableHere;
            this.options = options == null ? new Option[0] : options;
        }

        boolean expired(long nowMillis) {
            return expires > 0 && expires <= nowMillis;
        }
    }

    private NeedsYou() {}

    static Item[] parse(JSONObject snapshot) {
        if (snapshot == null) return new Item[0];
        return parse(snapshot.optJSONArray("needs_you"));
    }

    static Item[] parse(JSONArray rows) {
        if (rows == null || rows.length() == 0) return new Item[0];
        List<Item> kept = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            Item item = item(row);
            if (item != null) kept.add(item);
        }
        Collections.sort(kept, (a, b) -> Long.compare(a.created, b.created));
        return kept.toArray(new Item[0]);
    }

    /** Drops expired rows. Does not snooze and does not read message text. */
    static Item[] active(Item[] items, long nowMillis) {
        if (items == null || items.length == 0) return new Item[0];
        List<Item> kept = new ArrayList<>();
        for (Item item : items) if (item != null && !item.expired(nowMillis)) kept.add(item);
        return kept.toArray(new Item[0]);
    }

    static String askedLabel(long created, long nowMillis, ZoneId zone) {
        if (created <= 0) return "Asked just now";
        long delta = Math.max(0L, nowMillis - created);
        if (delta < 60_000L) return "Asked just now";
        if (delta < 60L * 60_000L) return "Asked " + (delta / 60_000L) + " min ago";
        ZoneId use = zone == null ? ZoneId.systemDefault() : zone;
        ZonedDateTime time = Instant.ofEpochMilli(created).atZone(use);
        int hour = time.getHour();
        int minute = time.getMinute();
        int hour12 = hour % 12;
        if (hour12 == 0) hour12 = 12;
        String suffix = hour < 12 ? "AM" : "PM";
        String minutes = minute < 10 ? "0" + minute : Integer.toString(minute);
        return "Asked " + hour12 + ":" + minutes + " " + suffix;
    }

    /** Wall and status line. Uses the short question when the bridge sent one. */
    static String tileLine(Item item) {
        if (item == null) return "";
        String question = item.questionShort == null || item.questionShort.isEmpty() ? item.question : item.questionShort;
        question = oneLine(question);
        if (question.isEmpty()) return "Needs you";
        return "Needs you · " + question;
    }

    /** Wall row. Same fields as {@link NeedsYouNotice}. */
    static NeedsYouNotice notice(String bot, Item item) {
        if (item == null) return new NeedsYouNotice(bot, "", "", "", 0L);
        return new NeedsYouNotice(bot, item.id, item.question, item.questionShort, item.created);
    }

    /** True when a hand carries a question the card can show. */
    static boolean hasQuestion(Item[] items) {
        if (items == null) return false;
        for (Item item : items) {
            if (item == null) continue;
            if (item.question != null && !item.question.trim().isEmpty()) return true;
            if (item.questionShort != null && !item.questionShort.trim().isEmpty()) return true;
        }
        return false;
    }

    static Item debug(String bot, String question) {
        String name = bot == null || bot.isEmpty() ? BotIdentity.PRIMARY : bot;
        String text = question == null || question.isEmpty() ? "Merge the Tesla page fix?" : question;
        return new Item("debug:" + name, KIND_APPROVAL, clip(text, QUESTION_CAP), clip(oneLine(text), 80),
                "", System.currentTimeMillis(), 0L, false, new Option[0]);
    }

    private static Item item(JSONObject row) {
        if (row == null) return null;
        String kind = row.optString("kind", "");
        if (!KIND_APPROVAL.equals(kind) && !KIND_QUESTION.equals(kind) && !KIND_ERROR.equals(kind)) return null;
        String id = row.optString("id", "").trim();
        if (id.isEmpty()) return null;
        if (id.length() > 200) id = id.substring(0, 200);
        String question = clip(row.optString("question", "").trim(), QUESTION_CAP);
        String title = clip(row.optString("title", "").trim(), CONTEXT_CAP);
        String suppliedShort = clip(row.optString("question_short", "").trim(), CONTEXT_CAP);
        String questionShort = suppliedShort.isEmpty() ? clip(oneLine(question), CONTEXT_CAP) : suppliedShort;
        if (questionShort.isEmpty() && !title.isEmpty()) questionShort = title;
        String context = clip(row.optString("context", "").trim(), CONTEXT_CAP);
        long created = row.optLong("created", 0L);
        long expires = row.has("expires") && !row.isNull("expires") ? row.optLong("expires", 0L) : 0L;
        boolean answerable = row.optBoolean("answerable_here", false) && !KIND_APPROVAL.equals(kind);
        Option[] options = answerable ? options(row.optJSONArray("options")) : new Option[0];
        return new Item(id, kind, question, questionShort, context, created, expires, answerable, options);
    }

    private static Option[] options(JSONArray rows) {
        if (rows == null || rows.length() == 0) return new Option[0];
        List<Option> kept = new ArrayList<>();
        boolean recommended = false;
        int limit = Math.min(4, rows.length());
        for (int i = 0; i < limit; i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String id = row.optString("id", "").trim();
            String label = clip(row.optString("label", "").trim(), 24);
            if (id.isEmpty() || label.isEmpty()) continue;
            boolean flag = row.optBoolean("recommended", false) && !recommended;
            if (flag) recommended = true;
            kept.add(new Option(id, label, flag, row.optBoolean("confirm", false)));
        }
        return kept.toArray(new Option[0]);
    }

    private static String oneLine(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder out = new StringBuilder(value.length());
        boolean space = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') c = ' ';
            if (c == ' ') {
                if (space || out.length() == 0) continue;
                space = true;
            } else space = false;
            out.append(c);
        }
        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == ' ') end--;
        return out.substring(0, end);
    }

    private static String clip(String value, int cap) {
        if (value == null) return "";
        return value.length() <= cap ? value : value.substring(0, cap);
    }
}

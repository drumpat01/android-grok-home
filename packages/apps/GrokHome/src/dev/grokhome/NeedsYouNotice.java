package dev.grokhome;

/** One bridge needs_you row, already tied to a bot. The wall does not approve it. */
final class NeedsYouNotice {
    final String bot;
    final String id;
    final String question;
    final String questionShort;
    final long created;

    NeedsYouNotice(String bot, String id, String question, String questionShort, long created) {
        this.bot = bot == null ? "" : bot;
        this.id = id == null ? "" : id;
        this.question = question == null ? "" : question;
        this.questionShort = questionShort == null ? "" : questionShort;
        this.created = created;
    }
}

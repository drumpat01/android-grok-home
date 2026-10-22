package dev.grokhome;

/** TalkBack sentences for a bot. The interrupt hint is present only while speech is playing. */
public final class BotTalkBack {
    private BotTalkBack() {}

    public static String contentDescription(String name, String shape, String colorName, boolean darkEyes, boolean primary, boolean overview, boolean speaking, boolean working, boolean unread) {
        StringBuilder text = new StringBuilder();
        if (overview) {
            text.append("Bot carousel. ").append(name).append(" centered. Swipe for more bots; tap a side bot to center it; tap center or pinch out to zoom in. ");
        }
        if (primary) text.append("Max, primary bot with a small gold crown. ");
        else if (name != null && !name.isEmpty()) text.append(name).append(". ");
        text.append(colorName).append(' ').append(shape).append(" assistant with ").append(darkEyes ? "black" : "white").append(" eyes.");
        if (name != null && !name.isEmpty()) {
            if (speaking) text.append(' ').append(name).append(" is speaking. Tap ").append(name).append(" to stop.");
            if (working) text.append(' ').append(name).append(" is working.");
            if (unread) text.append(" New reply from ").append(name).append('.');
        }
        return text.toString();
    }

    public static String speakingDetail(String name) {
        return "Tap " + name + " to stop";
    }

    /** Appended after {@link #contentDescription}. Idle adds nothing. */
    public static String stateSentence(BotStageState.State state, String question) {
        if (state == null) return "";
        switch (state) {
            case NEEDS_YOU: {
                String text = clip(question, 80);
                return text.isEmpty() ? "Needs you." : "Needs you: " + text + ".";
            }
            case WORKING:
                return "Working.";
            case DONE:
                return "Finished. New reply.";
            case IDLE:
                return "";
            default: {
                BotStageState.State unknown = state;
                throw new IllegalStateException("Unhandled stage " + unknown);
            }
        }
    }

    public static String answerAction(String name) {
        return "Answer " + name + "'s question";
    }

    public static String chipDescription(String name, String question) {
        String who = name == null ? "" : name;
        String title = who.isEmpty() ? "Needs you" : who + " needs you";
        return QuestionCard.spoken(title, question, QuestionCard.APPROVE);
    }

    private static String clip(String question, int cap) {
        if (question == null) return "";
        StringBuilder out = new StringBuilder(question.length());
        boolean space = false;
        for (int i = 0; i < question.length(); i++) {
            char c = question.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') c = ' ';
            if (c == ' ') {
                if (space || out.length() == 0) continue;
                space = true;
            } else space = false;
            out.append(c);
            if (out.length() >= cap) break;
        }
        return out.toString().trim();
    }
}

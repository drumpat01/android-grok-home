package dev.grokhome;

/** TalkBack labels for ambient tiles and the off-screen pill. */
final class WallTalkBack {
    private static final String OPEN_CHAT = " Double tap to open chat.";

    static String tile(String name, String needsYouQuestion, boolean working, boolean unread) {
        if (needsYouQuestion != null) return needsYou(name, needsYouQuestion);
        StringBuilder text = new StringBuilder(name).append('.');
        if (working) text.append(" Working.");
        if (unread) text.append(" New reply.");
        text.append(OPEN_CHAT);
        return text.toString();
    }

    /** Name plus the stage word the tile shows. Needs you outranks Done and Working. Idle adds no word. */
    static String tile(String name, BotStageState.State state, String needsYouQuestion) {
        BotStageState.State use = state == null ? BotStageState.State.IDLE : state;
        switch (use) {
            case NEEDS_YOU -> { return needsYou(name, needsYouQuestion); }
            case WORKING -> { return name + ". Working." + OPEN_CHAT; }
            case DONE -> { return name + ". Done." + OPEN_CHAT; }
            case IDLE -> { return name + "." + OPEN_CHAT; }
            default -> {
                BotStageState.State unknown = use;
                throw new IllegalStateException("Unhandled stage " + unknown);
            }
        }
    }

    private static String needsYou(String name, String needsYouQuestion) {
        if (needsYouQuestion == null || needsYouQuestion.isEmpty()) return name + ". Needs you. Double tap to open.";
        String gap = endsWithPunctuation(needsYouQuestion) ? " " : ". ";
        return name + ". Needs you: " + needsYouQuestion + gap + "Double tap to open.";
    }

    static String pill(int count, String bot) {
        if (count <= 1) return bot + " needs you. Double tap to open.";
        return count + " bots need you. Double tap to open.";
    }

    private static boolean endsWithPunctuation(String text) {
        if (text == null || text.isEmpty()) return false;
        char last = text.charAt(text.length() - 1);
        return last == '.' || last == '?' || last == '!' || last == '\u2026';
    }

    private WallTalkBack() {}
}

package dev.grokhome;

/** Wake phrases the offline recognizer may report. The grammar itself restricts what can be heard. */
final class WakeWord {
    private WakeWord(){}
    /** Empty when the utterance is only a wake phrase. Null when it is not a wake. */
    static String remainder(String spoken){
        if(spoken==null)return null;
        String text=spoken.trim().toLowerCase(java.util.Locale.US).replaceAll("[,.!?]+"," ").trim().replaceAll("\\s+"," ");
        return SpeechEngine.isWake(text)?"":null;
    }
    /** Command shortcut kept beside Hey Bots. It is not shown in the UI. */
    static boolean maxShortcut(String spoken){
        return spoken!=null && spoken.trim().matches("(?i)(hey |wake up )?"+java.util.regex.Pattern.quote(BotIdentity.PRIMARY)+"[.!?]?");
    }
}

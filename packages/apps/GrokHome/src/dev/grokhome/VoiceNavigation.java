package dev.grokhome;

import java.util.Locale;

/** Deliberately exact commands: ordinary dictated messages pass through unchanged. */
final class VoiceNavigation {
    enum Action { SELECT, NEXT, PREVIOUS, OVERVIEW, FOCUS, HANDOFF, ROUTE, CANCEL }
    final Action action;final String bot;final String topic;
    private VoiceNavigation(Action action,String bot){this(action,bot,"");}
    private VoiceNavigation(Action action,String bot,String topic){this.action=action;this.bot=bot;this.topic=topic;}
    static VoiceNavigation parse(String spoken){
        if(spoken==null)return null;
        String clean=spoken.trim().replaceAll("\\s+", " ").replaceAll("[.!?]+$", "");
        String text=clean.toLowerCase(Locale.US);
        if(text.equals("cancel task") || text.equals("stop task"))return new VoiceNavigation(Action.CANCEL,null);
        if(text.equals("who can help") || text.startsWith("who can help ") || text.startsWith("who can help?")){String topic=clean.replaceFirst("(?i)^who can help[?]?\\s*", "").trim();return new VoiceNavigation(Action.ROUTE,null,topic);}
        if(text.equals("next bot"))return new VoiceNavigation(Action.NEXT,null);
        if(text.equals("previous bot") || text.equals("last bot"))return new VoiceNavigation(Action.PREVIOUS,null);
        if(text.equals("show lineup") || text.equals("show the lineup") || text.equals("zoom out"))return new VoiceNavigation(Action.OVERVIEW,null);
        if(text.equals("zoom in"))return new VoiceNavigation(Action.FOCUS,null);
        for(String name:BotIdentity.NAMES){
            String normalized=name.toLowerCase(Locale.US);
            for(String prefix:new String[]{"show ","switch to ","show me ","let me talk to "}){
                String target=text.startsWith(prefix)?text.substring(prefix.length()):"";
                String alias=name.equals("Scout")?"j d leads":normalized;
                if(prefix.equals("let me talk to "))for(String candidate:new String[]{normalized,alias,name.equals("Nova")?"kitt":normalized}){
                    String marker=candidate+" about ";
                    if(target.startsWith(marker) && target.length()>marker.length())return new VoiceNavigation(Action.HANDOFF,name,clean.substring(text.indexOf(" about ")+7).replaceAll("[.!?]+$", "").trim());
                }
                if(target.equals(normalized) || name.equals("Nova") && target.equals("kitt") || name.equals("Scout") && target.equals("j d leads"))return new VoiceNavigation(Action.SELECT,name);
            }
        }
        return null;
    }
}

package dev.grokhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class VoiceNavigationTest {
    @Test public void acceptsOnlyCompleteLocalCommands(){
        assertEquals("Max",VoiceNavigation.parse("  Show Max! ").bot);
        assertEquals("Scout",VoiceNavigation.parse("switch to J D Leads").bot);
        assertEquals(VoiceNavigation.Action.NEXT,VoiceNavigation.parse("Next bot").action);
        assertEquals(VoiceNavigation.Action.PREVIOUS,VoiceNavigation.parse("Previous bot.").action);
        assertEquals(VoiceNavigation.Action.OVERVIEW,VoiceNavigation.parse("Show the lineup").action);
        assertEquals(VoiceNavigation.Action.FOCUS,VoiceNavigation.parse("Zoom in").action);
        for(String text:new String[]{"Show Max how to fix this","Next bot please write a report","Tell Max hello","show an unknown bot","please explain zoom in", ""})assertNull(text,VoiceNavigation.parse(text));
        assertNull(VoiceNavigation.parse(null));
    }
    @Test public void routingAndCancellation(){
        assertEquals(VoiceNavigation.Action.ROUTE,VoiceNavigation.parse("Who can help?").action);
        assertEquals("with my website",VoiceNavigation.parse("Who can help with my website").topic);
        assertEquals(VoiceNavigation.Action.CANCEL,VoiceNavigation.parse("Cancel task").action);
        assertEquals("this",VoiceNavigation.parse("Let me talk to Nova about this.").topic);
        assertEquals("Scout",VoiceNavigation.parse("Let me talk to J D Leads about finding customers").bot);
    }
    @Test public void allRosterNamesNavigateWithoutLosingSpaces(){for(String name:BotIdentity.NAMES)assertEquals(name,VoiceNavigation.parse("Show "+name).bot);}
    @Test public void letMeTalkToSelectsEveryBotAndRejectsLongerRequests(){
        for(String name:BotIdentity.NAMES){
            VoiceNavigation command=VoiceNavigation.parse("Let me talk to "+name);
            assertEquals(VoiceNavigation.Action.SELECT,command.action);
            assertEquals(name,command.bot);
        }
        assertEquals("Max",VoiceNavigation.parse("  LET me  TALK to Max! ").bot);
        assertEquals("Scout",VoiceNavigation.parse("Let me talk to J D Leads.").bot);
        assertNull(VoiceNavigation.parse("Let me talk to an unknown bot"));
        assertEquals("Nova",VoiceNavigation.parse("Let me talk to Kitt").bot);
        assertEquals(VoiceNavigation.Action.HANDOFF,VoiceNavigation.parse("Let me talk to Kitt about the website").action);
        assertEquals("the website",VoiceNavigation.parse("Let me talk to Kitt about the website").topic);
        assertEquals(VoiceNavigation.Action.HANDOFF,VoiceNavigation.parse("Let me talk to Max about the release").action);
        assertEquals("the release",VoiceNavigation.parse("Let me talk to Max about the release").topic);
    }
}

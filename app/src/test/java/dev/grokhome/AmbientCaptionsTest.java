package dev.grokhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class AmbientCaptionsTest {
    @Test public void unreadTilesUseThePreviewOrTheFallback(){
        assertEquals("4.5.2 in review",AmbientCaptions.line(false,null,true,"4.5.2 in review"));
        String longer="The release candidate is now in review and waiting on the tablet";
        assertEquals(longer,AmbientCaptions.line(false,"",true,longer));
        assertEquals("New reply",AmbientCaptions.line(false,null,true,""));
        assertEquals("New reply",AmbientCaptions.line(false,null,true,null));
        assertEquals("New reply",AmbientCaptions.line(false,null,true,"  "));
    }

    @Test public void readTilesStayQuietAndWorkingKeepsItsTaskLine(){
        assertEquals("",AmbientCaptions.line(false,null,false,"4.5.2 in review"));
        assertEquals("",AmbientCaptions.line(false,"Working",false,""));
        assertEquals("Checking the release",AmbientCaptions.line(true,"Checking the release",true,"4.5.2 in review"));
        assertEquals("Working",AmbientCaptions.line(true,"  ",true,"4.5.2 in review"));
        assertEquals("Working",AmbientCaptions.line(true,null,false,null));
        assertEquals("One line of the task",AmbientCaptions.line(true,"One line\nof the task",false,null));
    }

    @Test public void maxUnreadReplyShowsThePreviewLine(){
        assertEquals("4.5.2 in review",AmbientCaptions.line(false,null,true,"4.5.2 in review"));
        assertEquals("New reply",AmbientCaptions.line(false,null,true,""));
        assertEquals("",AmbientCaptions.line(false,null,false,"4.5.2 in review"));
    }
}

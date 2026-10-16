package dev.grokhome;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class ChatTranscriptTest {
    @Test public void importsIndentedLegacyBoundariesWithoutPuttingUserTextInBotBubble(){
        List<String[]> messages=ChatTranscript.parse("Max: Earlier.\n\n    You: Hello\n\nMax: Ready.\n\n    ","Max");
        assertEquals(3,messages.size());assertArrayEquals(new String[]{"You","Hello"},messages.get(1));assertArrayEquals(new String[]{"Max","Ready."},messages.get(2));
    }
    @Test public void keepsParagraphsAndCombinesStreamedBotMessages(){
        List<String[]> messages=ChatTranscript.parse("You: Two lines\nkeep this\n\nMax: First paragraph.\n\nAnother paragraph.\n\nMax: Final part.\n\n","Max");
        assertEquals(2,messages.size());assertEquals("Two lines\nkeep this",messages.get(0)[1]);assertEquals("First paragraph.\n\nAnother paragraph.\n\nFinal part.",messages.get(1)[1]);
    }
    @Test public void handlesWindowsNewlinesAndLiteralBotNames(){
        List<String[]> messages=ChatTranscript.parse("You: Hello\r\n  \r\n  A.Bot: Hello back\r\n\r\n","A.Bot");assertEquals(2,messages.size());assertArrayEquals(new String[]{"A.Bot","Hello back"},messages.get(1));
    }
}

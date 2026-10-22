package dev.grokhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class WakeWordTest {
    @Test public void ordinarySpeechIsNotAWake(){
        assertNull(WakeWord.remainder("Max"));
        assertNull(WakeWord.remainder("hey maxwell"));
        assertNull(WakeWord.remainder("where is my latest iphone app review"));
        assertNull(WakeWord.remainder("bots"));
        assertNull(WakeWord.remainder("hey max"));
        assertNull(WakeWord.remainder(null));
        assertNull(WakeWord.remainder(""));
    }
    @Test public void wakePhrasesAreRecognised(){
        assertEquals("",WakeWord.remainder("Hey Bots"));
        assertEquals("",WakeWord.remainder("hey bots!"));
        assertNull(WakeWord.remainder("hey bot"));
        assertEquals("",WakeWord.remainder("okay bots"));
        assertEquals("",WakeWord.remainder("Okay Bots."));
    }
    @Test public void okayBotsIsAHiddenAliasAndMaxIsOnlyAShortcut(){
        assertNotNull(WakeWord.remainder("okay bots"));
        assertNull(WakeWord.remainder("Hey Max"));
        assertNull(WakeWord.remainder("Max"));
        assertNull(WakeWord.remainder("Wake up Max"));
        assertTrue(WakeWord.maxShortcut("Max"));
        assertTrue(WakeWord.maxShortcut("Hey Max!"));
        assertTrue(WakeWord.maxShortcut("Wake up Max"));
        assertFalse(WakeWord.maxShortcut("Hey Bots"));
        assertFalse(WakeWord.maxShortcut("hey maxwell"));
    }
}

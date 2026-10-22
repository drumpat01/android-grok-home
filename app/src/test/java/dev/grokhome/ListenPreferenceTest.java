package dev.grokhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class ListenPreferenceTest {
    @Test public void migrationKeepsWakeOnWhenTheMicWasNotMuted() {
        assertTrue(ListenPreference.migratedSwitch(false));
    }

    @Test public void aSavedMuteBecomesTheSwitchOff() {
        assertFalse(ListenPreference.migratedSwitch(true));
    }

    @Test public void wakeNeedsTheSwitchAndAClearTemporaryMute() {
        assertFalse(ListenPreference.migratedSwitch(true));
        assertTrue(ListenPreference.migratedSwitch(false));
    }

    @Test public void copyDoesNotUseDashes() {
        for (String line : new String[] {
            VoiceCopy.ARMED_SUBTITLE, VoiceCopy.RECORDING_SUBTITLE, VoiceCopy.MUTED_SUBTITLE,
            VoiceCopy.SWITCH_OFF_SUBTITLE, VoiceCopy.PERMISSION_SUBTITLE,
            VoiceCopy.WAKE_HINT, VoiceCopy.LISTEN_TITLE, VoiceCopy.LISTEN_SUB,
            VoiceCopy.DRAWER_NOTE, VoiceCopy.HOLD_TOOLTIP, VoiceCopy.MUTE_ACTION, VoiceCopy.UNMUTE_ACTION
        }) {
            assertEquals(line, -1, line.indexOf('\u2014'));
            assertEquals(line, -1, line.indexOf('\u2013'));
            assertFalse(line, line.contains("Okay"));
        }
        assertTrue(VoiceCopy.ARMED_SUBTITLE.contains("Hey Bots"));
        assertEquals("Say Hey Bots", VoiceCopy.WAKE_HINT);
        assertEquals("Mic off for now · tap the mic", VoiceCopy.MUTED_SUBTITLE);
        assertEquals("Listen for Hey Bots", VoiceCopy.LISTEN_TITLE);
        assertEquals("Mic stays on while Home is open", VoiceCopy.LISTEN_SUB);
        assertEquals("Hold the mic to turn it off for now. Listening pauses when the screen is off.", VoiceCopy.DRAWER_NOTE);
        assertEquals("Hold to turn the mic off", VoiceCopy.HOLD_TOOLTIP);
    }
}

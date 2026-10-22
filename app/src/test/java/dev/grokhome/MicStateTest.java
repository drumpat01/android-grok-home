package dev.grokhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class MicStateTest {
    @Test public void recordingWinsAndTheOtherPhasesStayDistinct() {
        assertEquals(MicState.Phase.ARMED, MicState.phase(false, true, false, true));
        assertEquals(MicState.Phase.RECORDING, MicState.phase(true, true, false, true));
        assertEquals(MicState.Phase.RECORDING, MicState.phase(true, true, true, true));
        assertEquals(MicState.Phase.RECORDING, MicState.phase(true, false, false, false));
        assertEquals(MicState.Phase.MUTED, MicState.phase(false, true, true, true));
        assertEquals(MicState.Phase.SWITCH_OFF, MicState.phase(false, false, false, true));
        assertEquals(MicState.Phase.NO_PERMISSION, MicState.phase(false, true, false, false));
    }

    @Test public void talkBackNamesTheBotAndTheLongPress() {
        assertEquals("Talk to Max. Listening for Hey Bots", MicState.spokenLabel(MicState.Phase.ARMED, "Max"));
        assertEquals("Stop listening", MicState.spokenLabel(MicState.Phase.RECORDING, "Max"));
        assertEquals("Talk to Sage. Mic off for now", MicState.spokenLabel(MicState.Phase.MUTED, "Sage"));
        assertEquals(VoiceCopy.MUTE_ACTION, MicState.longPressLabel(false));
        assertEquals(VoiceCopy.UNMUTE_ACTION, MicState.longPressLabel(true));
    }

    @Test public void idleSubtitleCarriesTheMicState() {
        assertEquals(VoiceCopy.ARMED_SUBTITLE, MicState.subtitle(MicState.Phase.ARMED));
        assertEquals(VoiceCopy.RECORDING_SUBTITLE, MicState.subtitle(MicState.Phase.RECORDING));
        assertEquals("Mic off for now · tap the mic", MicState.subtitle(MicState.Phase.MUTED));
        assertEquals("Tap the mic to talk", MicState.subtitle(MicState.Phase.SWITCH_OFF));
        assertEquals("Mic access is off · Open settings", MicState.subtitle(MicState.Phase.NO_PERMISSION));
    }

    @Test public void tapStartsUnlessACommandIsRecording() {
        assertEquals(MicState.Tap.START, MicState.onTap(MicState.Phase.ARMED));
        assertEquals(MicState.Tap.START, MicState.onTap(MicState.Phase.MUTED));
        assertEquals(MicState.Tap.START, MicState.onTap(MicState.Phase.SWITCH_OFF));
        assertEquals(MicState.Tap.START, MicState.onTap(MicState.Phase.NO_PERMISSION));
        assertEquals(MicState.Tap.STOP, MicState.onTap(MicState.Phase.RECORDING));
    }

    @Test public void longPressTogglesTheTemporaryMute() {
        assertTrue(MicState.mutedAfterLongPress(false));
        assertFalse(MicState.mutedAfterLongPress(true));
    }
}

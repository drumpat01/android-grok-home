package dev.grokhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConnectionStateTest {
    @Test public void pulseRefreshesWhenTheSyncWordingChanges() {
        ConnectionState state = new ConnectionState();
        int[] calls = {0};
        state.observe(snapshot -> calls[0]++);
        state.noteSynced(1_000);
        int afterSync = calls[0];
        state.pulse(1_000);
        assertEquals(afterSync, calls[0]);
        state.pulse(61_000);
        assertTrue(calls[0] > afterSync);
    }

    @Test public void pulseRefreshesTheRetrySecond() {
        ConnectionState state = new ConnectionState();
        int[] calls = {0};
        state.observe(snapshot -> calls[0]++);
        state.publish(ConnectionRules.Mode.OFFLINE, true, "Wi-Fi", true, false, "No answer", 8_000, 1_000);
        int after = calls[0];
        state.pulse(1_000);
        assertEquals(after, calls[0]);
        state.pulse(2_100);
        assertTrue(calls[0] > after);
    }
}

package dev.grokhome;

import org.junit.Test;
import static org.junit.Assert.*;

/** Chip mapping, address checks, retry, and sync phrases. No VPN flag and no network name. */
public class ConnectionRulesTest {
    @Test public void savedUrlPlusHealthPicksTheChip() {
        assertEquals(ConnectionRules.Mode.CONNECTED, ConnectionRules.map(true, true, ConnectionRules.HostKind.LAN));
        assertEquals(ConnectionRules.Mode.CONNECTED, ConnectionRules.map(true, true, ConnectionRules.HostKind.LOOPBACK));
        assertEquals(ConnectionRules.Mode.AWAY, ConnectionRules.map(true, true, ConnectionRules.HostKind.TAILNET));
        assertEquals(ConnectionRules.Mode.OFFLINE, ConnectionRules.map(true, false, ConnectionRules.HostKind.LAN));
        assertEquals(ConnectionRules.Mode.OFFLINE, ConnectionRules.map(true, false, ConnectionRules.HostKind.TAILNET));
        assertEquals(ConnectionRules.Mode.OFFLINE, ConnectionRules.map(false, true, ConnectionRules.HostKind.TAILNET));
        assertEquals(ConnectionRules.Mode.OFFLINE, ConnectionRules.map(false, false, ConnectionRules.HostKind.LAN));
    }

    @Test public void tailnetHostIsPrivateEvenOnTheHomeCouch() {
        assertEquals(ConnectionRules.HostKind.TAILNET, ConnectionRules.hostKind("https://100.101.12.34:8787"));
        assertEquals(ConnectionRules.HostKind.TAILNET, ConnectionRules.hostKind("https://my-computer.example-tailnet.ts.net:9443"));
        assertEquals(ConnectionRules.HostKind.LAN, ConnectionRules.hostKind("http://192.168.1.20:8787"));
        assertEquals(ConnectionRules.HostKind.LOOPBACK, ConnectionRules.hostKind("http://127.0.0.1:8787"));
        assertEquals(ConnectionRules.HostKind.OTHER, ConnectionRules.hostKind("https://example.com:443"));
        assertTrue(ConnectionRules.isTailscaleIpv4("100.64.0.1"));
        assertTrue(ConnectionRules.isTailscaleIpv4("100.127.255.255"));
        assertFalse(ConnectionRules.isTailscaleIpv4("100.63.0.1"));
        assertFalse(ConnectionRules.isTailscaleIpv4("100.128.0.1"));
        assertFalse(ConnectionRules.isTailscaleIpv4("192.168.1.20"));
    }

    @Test public void chipLabelsAndDots() {
        assertEquals("Connected", ConnectionRules.chipLabel(ConnectionRules.Mode.CONNECTED));
        assertEquals("Away · private", ConnectionRules.chipLabel(ConnectionRules.Mode.AWAY));
        assertEquals("Offline · retrying", ConnectionRules.chipLabel(ConnectionRules.Mode.OFFLINE));
        assertEquals(0xff3ddc84, ConnectionRules.dotColor(ConnectionRules.Mode.CONNECTED));
        assertEquals(0xff7eb6ff, ConnectionRules.dotColor(ConnectionRules.Mode.AWAY));
        assertEquals(0xfff4a62a, ConnectionRules.dotColor(ConnectionRules.Mode.OFFLINE));
    }

    @Test public void offlineCopyDoesNotSayVoiceOpensApps() {
        String copy = ConnectionRules.OFFLINE_TITLE + " " + ConnectionRules.OFFLINE_SAVED + " " + ConnectionRules.OFFLINE_ASLEEP;
        assertFalse(copy.toLowerCase(java.util.Locale.US).contains("app"));
        assertEquals("Can't reach your computer", ConnectionRules.OFFLINE_TITLE);
        assertEquals("Your saved chats are still here", ConnectionRules.OFFLINE_SAVED);
        assertEquals("Is it asleep?", ConnectionRules.OFFLINE_ASLEEP);
    }

    @Test public void addressRequiresAPortAndStoresAUrl() {
        ConnectionRules.Address missing = ConnectionRules.check("100.101.12.34");
        assertFalse(missing.ok());
        assertEquals(ConnectionRules.PORT_HINT, missing.message);
        assertEquals(ConnectionRules.PORT_HINT, ConnectionRules.check("").message);
        assertEquals(ConnectionRules.PORT_HINT, ConnectionRules.check("http://192.168.1.20").message);

        ConnectionRules.Address tailnet = ConnectionRules.check("100.101.12.34:8787");
        assertTrue(tailnet.ok());
        assertEquals("https://100.101.12.34:8787", tailnet.stored);
        assertEquals("100.101.12.34:8787", ConnectionRules.display(tailnet.stored));
        assertEquals("100.•••.•••.•••:8787", ConnectionRules.mask(ConnectionRules.display(tailnet.stored)));
        assertEquals("192.•••.•••.•••:8787", ConnectionRules.mask("192.168.1.20:8787"));
        assertEquals(ConnectionRules.HostKind.TAILNET, ConnectionRules.hostKind(tailnet.stored));

        ConnectionRules.Address lan = ConnectionRules.check("192.168.1.20:8787");
        assertEquals("http://192.168.1.20:8787", lan.stored);
        assertEquals(ConnectionRules.HostKind.LAN, ConnectionRules.hostKind(lan.stored));

        ConnectionRules.Address named = ConnectionRules.check("https://my-computer.example-tailnet.ts.net:9443");
        assertTrue(named.ok());
        assertEquals("https://my-computer.example-tailnet.ts.net:9443", named.stored);

        assertFalse(ConnectionRules.check("http://100.101.12.34:8787").ok());
        assertFalse(ConnectionRules.check("http://8.8.8.8:80").ok());
        assertEquals("Enter the server address without a path", ConnectionRules.check("http://192.168.1.20:8787/health").message);
    }

    @Test public void retryBackoffCapsAtThirtySeconds() {
        assertEquals(0, ConnectionRules.retryDelayMs(0));
        assertEquals(2000, ConnectionRules.retryDelayMs(1));
        assertEquals(4000, ConnectionRules.retryDelayMs(2));
        assertEquals(8000, ConnectionRules.retryDelayMs(3));
        assertEquals(16000, ConnectionRules.retryDelayMs(4));
        assertEquals(30000, ConnectionRules.retryDelayMs(5));
        assertEquals(30000, ConnectionRules.retryDelayMs(9));
        assertEquals("Retrying in 8 seconds", ConnectionRules.retryCountdown(8000));
        assertEquals("Retrying in 1 second", ConnectionRules.retryCountdown(1000));
    }

    @Test public void lastSyncIsATabletAgeNotAHealthField() {
        assertEquals("not synced yet", ConnectionRules.syncCompact(-1));
        assertEquals("Not synced yet", ConnectionRules.syncSentence(-1));
        assertEquals("just now", ConnectionRules.syncCompact(0));
        assertEquals("just now", ConnectionRules.syncCompact(3_000));
        assertEquals("just now", ConnectionRules.syncCompact(9_999));
        assertEquals("Seen 1 min ago", ConnectionRules.syncCompact(10_000));
        assertEquals("Seen 1 min ago", ConnectionRules.syncCompact(59_000));
        assertEquals("Seen 1 min ago", ConnectionRules.syncCompact(60_000));
        assertEquals("Seen 3 min ago", ConnectionRules.syncCompact(180_000));
        assertEquals("Seen 1 hr ago", ConnectionRules.syncCompact(3_600_000));
        assertEquals("Seen 2 hr ago", ConnectionRules.syncCompact(7_200_000));
        assertEquals("Last synced 4 minutes ago", ConnectionRules.syncSentence(4 * 60 * 1000));
        assertEquals("Last synced 1 minute ago", ConnectionRules.syncSentence(60 * 1000));
        ConnectionRules.Health health = ConnectionRules.Health.parse(200,
            "{\"service\":\"grok-home-voice\",\"version\":\"0.19.2\",\"started_at\":1700000000}");
        assertTrue(health.answering);
        assertEquals("0.19.2", health.version);
        assertEquals(1700000000_000L, health.startedAtMillis);
        assertFalse(ConnectionRules.Health.parse(200, "{\"service\":\"other\"}").answering);
        assertFalse(ConnectionRules.Health.parse(503, "{\"service\":\"grok-home-voice\"}").answering);
        ConnectionState state = new ConnectionState();
        assertFalse(state.known());
        state.noteSynced(5_000);
        state.publish(ConnectionRules.Mode.CONNECTED, true, "Wi-Fi", false, true, "Answering", 0, 8_000);
        assertTrue(state.known());
        assertEquals(3_000, state.syncAgeMs(8_000));
        assertEquals(ConnectionRules.Mode.CONNECTED, state.mode());
    }
}

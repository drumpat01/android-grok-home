package dev.grokhome;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * While Connected, /health is probed again after 60 seconds.
 * A failure flips the chip to Offline and the existing backoff takes over.
 * Ambient and onPause do not add a probe.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ConnectionMonitorTest {
    private static final String HEALTH = "{\"service\":\"grok-home-voice\",\"version\":\"0.19.2\",\"started_at\":0}";

    private MockWebServer server;
    private ConnectionState state;
    private ConnectionMonitor monitor;

    @Before public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        state = new ConnectionState();
        String base = server.url("/").toString();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String address = base;
        monitor = new ConnectionMonitor(state, () -> address,
                () -> new TabletNetwork.Snapshot(true, "Wi-Fi", false),
                new OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build());
    }

    @After public void tearDown() throws Exception {
        monitor.stop();
        server.shutdown();
    }

    @Test public void failedPeriodicProbeMovesConnectedToOffline() throws Exception {
        server.enqueue(new MockResponse().setBody(HEALTH));
        monitor.start();
        until(() -> state.mode() == ConnectionRules.Mode.CONNECTED && state.voiceAnswering());
        assertEquals(1, server.getRequestCount());

        server.enqueue(new MockResponse().setResponseCode(500));
        ShadowLooper.idleMainLooper(30, TimeUnit.SECONDS);
        assertRequestCount(1);
        assertEquals(ConnectionRules.Mode.CONNECTED, state.mode());

        ShadowLooper.idleMainLooper(30, TimeUnit.SECONDS);
        until(() -> state.mode() == ConnectionRules.Mode.OFFLINE);
        assertEquals(2, server.getRequestCount());
        assertFalse(state.voiceAnswering());
        assertTrue(server.takeRequest(1, TimeUnit.SECONDS).getPath().endsWith("/health"));
        assertTrue(server.takeRequest(1, TimeUnit.SECONDS).getPath().endsWith("/health"));

        server.enqueue(new MockResponse().setResponseCode(500));
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        assertRequestCount(2);
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        until(() -> server.getRequestCount() == 3);
        assertEquals(ConnectionRules.Mode.OFFLINE, state.mode());
    }

    @Test public void ambientAndPauseDoNotSendTheConnectedProbe() throws Exception {
        server.enqueue(new MockResponse().setBody(HEALTH));
        monitor.start();
        until(() -> state.mode() == ConnectionRules.Mode.CONNECTED);
        assertEquals(1, server.getRequestCount());

        monitor.setAmbient(true);
        ShadowLooper.idleMainLooper(60, TimeUnit.SECONDS);
        assertRequestCount(1);
        assertEquals(ConnectionRules.Mode.CONNECTED, state.mode());

        monitor.setAmbient(false);
        ShadowLooper.idleMainLooper(59, TimeUnit.SECONDS);
        assertRequestCount(1);

        server.enqueue(new MockResponse().setBody(HEALTH));
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        until(() -> server.getRequestCount() == 2);
        assertEquals(ConnectionRules.Mode.CONNECTED, state.mode());

        monitor.stop();
        ShadowLooper.idleMainLooper(60, TimeUnit.SECONDS);
        assertRequestCount(2);
    }

    @Test public void leavingAmbientEarlyDoesNotPostponeTheConnectedProbe() throws Exception {
        server.enqueue(new MockResponse().setBody(HEALTH));
        monitor.start();
        until(() -> state.mode() == ConnectionRules.Mode.CONNECTED);
        long due = monitor.connectedProbeDueElapsed();
        assertTrue(due > android.os.SystemClock.elapsedRealtime());

        monitor.setAmbient(true);
        monitor.setAmbient(false);
        assertEquals(due, monitor.connectedProbeDueElapsed());

        long remaining = due - android.os.SystemClock.elapsedRealtime();
        ShadowLooper.idleMainLooper(Math.max(0, remaining - 1000), TimeUnit.MILLISECONDS);
        assertRequestCount(1);

        server.enqueue(new MockResponse().setBody(HEALTH));
        ShadowLooper.idleMainLooper(1500, TimeUnit.MILLISECONDS);
        until(() -> server.getRequestCount() == 2);
        assertEquals(ConnectionRules.Mode.CONNECTED, state.mode());
    }

    private void assertRequestCount(int count) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500);
        while (server.getRequestCount() != count && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(count, server.getRequestCount());
    }

    private void until(BooleanSupplier predicate) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!predicate.getAsBoolean() && System.nanoTime() < deadline) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(10);
        }
        assertTrue(predicate.getAsBoolean());
    }
}

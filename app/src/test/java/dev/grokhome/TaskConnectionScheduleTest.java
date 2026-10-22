package dev.grokhome;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Task redraws and Stop must not fight the connection chip. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TaskConnectionScheduleTest {
    private static final String HEALTH = "{\"service\":\"grok-home-voice\",\"version\":\"0.19.2\",\"started_at\":0}";

    private MockWebServer server;
    private ConnectionMonitor monitor;
    private int health;

    @Before public void setUp() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                if (path.contains("/health")) {
                    health++;
                    return new MockResponse().setBody(HEALTH);
                }
                return new MockResponse().setBody("{\"bot\":\"Max\",\"stale\":true,\"working\":false}");
            }
        });
        server.start();
    }

    @After public void tearDown() throws Exception {
        if (monitor != null) monitor.stop();
        server.shutdown();
    }

    @Test public void renderTaskDoesNotMoveTheConnectedProbe() throws Exception {
        String base = serverBase();
        prepareHome(base);
        ActivityController<HomeActivity> controller = Robolectric.buildActivity(HomeActivity.class).create();
        HomeActivity home = controller.get();
        ConnectionState state = (ConnectionState) field(home, "connection");
        monitor = new ConnectionMonitor(state, () -> base,
            () -> new TabletNetwork.Snapshot(true, "Wi-Fi", false),
            new OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build());
        Field monitorField = HomeActivity.class.getDeclaredField("connectionMonitor");
        monitorField.setAccessible(true);
        monitorField.set(home, monitor);
        controller.start().resume();
        until(() -> health >= 1 && state.mode() == ConnectionRules.Mode.CONNECTED);
        long due = monitor.connectedProbeDueElapsed();
        assertTrue(due > SystemClock.elapsedRealtime());

        ShadowLooper.idleMainLooper(30, TimeUnit.SECONDS);
        home.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 12, 12, 0));
        home.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 12, 12, 0));
        TaskProgress progress = (TaskProgress) field(home, "taskProgress");
        progress.working("Max", "Check the build");
        call(home, "renderTask");
        View stop = find(home.getWindow().getDecorView(), "Stop task");
        assertTrue(stop != null);
        stop.performClick();
        ShadowLooper.idleMainLooper();
        call(home, "updateStatus");
        call(home, "showKeyboard");
        ShadowLooper.idleMainLooper();

        assertEquals(due, monitor.connectedProbeDueElapsed());
        assertEquals(1, health);

        long remaining = due - SystemClock.elapsedRealtime();
        ShadowLooper.idleMainLooper(Math.max(0, remaining - 1000), TimeUnit.MILLISECONDS);
        assertEquals(due, monitor.connectedProbeDueElapsed());
        assertEquals(1, health);

        ShadowLooper.idleMainLooper(1500, TimeUnit.MILLISECONDS);
        until(() -> health >= 2);
        assertEquals(2, health);
        assertEquals(ConnectionRules.Mode.CONNECTED, state.mode());
    }

    @Test public void stopTaskWhileOfflineShowsTheOfflineCopy() throws Exception {
        prepareHome("http://192.168.1.20:8787");
        HomeActivity home = Robolectric.buildActivity(HomeActivity.class).setup().get();
        ((ConnectionMonitor) field(home, "connectionMonitor")).stop();
        ConnectionState state = (ConnectionState) field(home, "connection");
        publishOffline(state);

        TaskProgress progress = (TaskProgress) field(home, "taskProgress");
        progress.working("Max", "Check the build");
        text(home, "voiceTitle", "Max is working");
        progress.stopped("Max", System.currentTimeMillis());
        text(home, "voiceTitle", "Task stopped");
        call(home, "renderTask");
        StatusBlock status = (StatusBlock) field(home, "statusBlock");
        assertEquals("Task stopped", status.titleText());

        progress.stopped("Max", System.currentTimeMillis() - 5000);
        text(home, "voiceTitle", "Task stopped");
        call(home, "renderTask");
        assertEquals(ConnectionRules.OFFLINE_TITLE, status.titleText());
        assertEquals(ConnectionRules.OFFLINE_SAVED, status.detailText());

        call(home, "renderTask");
        assertEquals(ConnectionRules.OFFLINE_TITLE, status.titleText());

        text(home, "voiceTitle", "Ready when you are");
        text(home, "voiceDetail", "Talk to Max");
        publishOffline(state);
        assertEquals(ConnectionRules.OFFLINE_TITLE, status.titleText());
        assertEquals(ConnectionRules.OFFLINE_SAVED, status.detailText());
    }

    private static void prepareHome(String server) {
        RuntimeEnvironment.getApplication().getSharedPreferences("home", 0).edit()
            .clear()
            .putBoolean("ambient_enabled", false)
            .putBoolean("muted", true)
            .putBoolean("auto_listen", false)
            .putBoolean("wake_mic_v1", true)
            .putBoolean("hey_bots_v1", true)
            .putBoolean("setup_dismissed", true)
            .putString("selected_bot", "Max")
            .putString("voice_server", server)
            .commit();
    }

    private String serverBase() {
        String base = server.url("/").toString();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    private static void publishOffline(ConnectionState state) {
        long now = SystemClock.elapsedRealtime();
        state.publish(ConnectionRules.Mode.OFFLINE, true, "Wi-Fi", false, false,
            "No answer on port 8787. Is the computer asleep?", now + 8000, now);
    }

    private static Object field(HomeActivity home, String name) throws Exception {
        Field found = HomeActivity.class.getDeclaredField(name);
        found.setAccessible(true);
        return found.get(home);
    }

    private static void text(HomeActivity home, String name, String value) throws Exception {
        Field found = HomeActivity.class.getDeclaredField(name);
        found.setAccessible(true);
        found.set(home, value);
    }

    private static View find(View root, String description) {
        if (description.equals(String.valueOf(root.getContentDescription()))) return root;
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void call(HomeActivity home, String name) throws Exception {
        Method method = HomeActivity.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(home);
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

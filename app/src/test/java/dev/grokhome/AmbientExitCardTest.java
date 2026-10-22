package dev.grokhome;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Leaving ambient on a tile that does not need you must not open a question card.
 * The debug done extra is the path Luna used.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 32, qualifiers = "w1205dp-h753dp-land-340dpi")
public class AmbientExitCardTest {
    @Test public void doneTileExitDoesNotOpenAnEmptyCard() throws Exception {
        ApplicationInfo info = RuntimeEnvironment.getApplication().getApplicationInfo();
        int saved = info.flags;
        info.flags = saved | ApplicationInfo.FLAG_DEBUGGABLE;
        try {
            RuntimeEnvironment.getApplication().getSharedPreferences("home", 0).edit()
                    .putString("voice_server", "http://127.0.0.1:8787")
                    .putBoolean("setup_dismissed", true)
                    .commit();
            Intent launch = new Intent(RuntimeEnvironment.getApplication(), HomeActivity.class);
            launch.putExtra(RaiseHandDebug.EXTRA_DONE, "Nova");
            launch.putExtra(RaiseHandDebug.EXTRA_OVERVIEW, true);
            HomeActivity home = Robolectric.buildActivity(HomeActivity.class, launch).setup().get();
            ShadowLooper.idleMainLooper();
            RaiseHandController hands = (RaiseHandController) field(home, "hands");
            long then = System.currentTimeMillis() - StagePose.DONE_MS - 4_000L;
            assertTrue(RaiseHandDebug.applyIntent(launch, hands.stage(), then));
            enter(home);
            assertTrue((Boolean) field(home, "ambient"));
            AmbientWall wall = (AmbientWall) field(home, "wall");
            assertEquals("Nova. Done. Double tap to open chat.", wall.tileDescription("Nova"));

            home.showRaiseHandCard("Nova");
            assertFalse(hands.cardOpen());
            QuestionCard card = card(home);
            assertFalse(card.isOpen());
            assertEquals(View.GONE, card.getVisibility());
            assertFalse((Boolean) field(home, "ambient"));

            enter(home);
            place(home);
            tap(wall, "Nova");
            assertFalse((Boolean) field(home, "ambient"));
            assertFalse(hands.cardOpen());
            assertFalse(card.isOpen());
            assertEquals(View.GONE, card.getVisibility());
            CharSequence description = card.getContentDescription();
            assertTrue(description == null || description.toString().trim().isEmpty());
            assertEquals("Nova", field(home, "selectedBot"));
            assertNotNull(field(home, "chatPanel"));
        } finally {
            info.flags = saved;
        }
    }

    @Test public void needsYouTileStillOpensTheCard() throws Exception {
        ApplicationInfo info = RuntimeEnvironment.getApplication().getApplicationInfo();
        int saved = info.flags;
        info.flags = saved | ApplicationInfo.FLAG_DEBUGGABLE;
        try {
            RuntimeEnvironment.getApplication().getSharedPreferences("home", 0).edit()
                    .putString("voice_server", "http://127.0.0.1:8787")
                    .putBoolean("setup_dismissed", true)
                    .commit();
            HomeActivity home = Robolectric.buildActivity(HomeActivity.class).setup().get();
            ShadowLooper.idleMainLooper();
            RaiseHandController hands = (RaiseHandController) field(home, "hands");
            Intent intent = new Intent();
            intent.putExtra(RaiseHandDebug.EXTRA_NEEDS_YOU, "Sage");
            intent.putExtra(RaiseHandDebug.EXTRA_QUESTION, "Merge the Tesla page fix?");
            assertTrue(RaiseHandDebug.applyIntent(intent, hands.stage(), System.currentTimeMillis()));
            enter(home);
            home.showRaiseHandCard("Sage");
            assertTrue(hands.cardOpen());
            QuestionCard card = card(home);
            assertTrue(card.isOpen());
            assertEquals(View.VISIBLE, card.getVisibility());
            assertNotNull(card.getContentDescription());
            assertTrue(card.getContentDescription().toString().contains("Merge the Tesla page fix?"));
            assertFalse((Boolean) field(home, "ambient"));
            assertNull(field(home, "chatPanel"));
        } finally {
            info.flags = saved;
        }
    }

    @Test public void plainTileLeavesAmbientAndOpensThatBotsChat() throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("home", 0).edit()
                .putString("voice_server", "http://127.0.0.1:8787")
                .putBoolean("setup_dismissed", true)
                .putString("selected_bot", "Max")
                .commit();
        HomeActivity home = Robolectric.buildActivity(HomeActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        enter(home);
        place(home);
        AmbientWall wall = (AmbientWall) field(home, "wall");
        assertEquals("Luna. Double tap to open chat.", wall.tileDescription("Luna"));
        tap(wall, "Luna");
        assertFalse((Boolean) field(home, "ambient"));
        assertEquals("Luna", field(home, "selectedBot"));
        assertNotNull(field(home, "chatPanel"));
        RaiseHandController hands = (RaiseHandController) field(home, "hands");
        assertFalse(hands.cardOpen());
        assertEquals(View.GONE, card(home).getVisibility());
    }

    private static void enter(HomeActivity home) throws Exception {
        java.lang.reflect.Field resumed = HomeActivity.class.getDeclaredField("resumed");
        resumed.setAccessible(true);
        resumed.setBoolean(home, true);
        java.lang.reflect.Method method = HomeActivity.class.getDeclaredMethod("enterAmbient", boolean.class);
        method.setAccessible(true);
        method.invoke(home, true);
    }

    private static void place(HomeActivity home) {
        View root = home.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(2560, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 2560, 1600);
    }

    private static void tap(AmbientWall wall, String name) {
        float gap = 8f * wall.getResources().getDisplayMetrics().density;
        for (AmbientLayout.Slot slot : AmbientLayout.place(BotIdentity.NAMES, wall.getWidth(), wall.getHeight(), gap)) {
            if (!slot.name.equals(name)) continue;
            MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, (slot.left + slot.right) / 2f, (slot.top + slot.bottom) / 2f, 0);
            assertTrue(wall.dispatchTouchEvent(event));
            event.recycle();
            return;
        }
        throw new AssertionError(name);
    }

    private static QuestionCard card(HomeActivity home) throws Exception {
        FrameLayout root = (FrameLayout) field(home, "root");
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) instanceof QuestionCard) return (QuestionCard) root.getChildAt(i);
        }
        throw new AssertionError("question card");
    }

    private static Object field(HomeActivity home, String name) throws Exception {
        java.lang.reflect.Field found = HomeActivity.class.getDeclaredField(name);
        found.setAccessible(true);
        return found.get(home);
    }
}

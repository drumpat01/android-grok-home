package dev.grokhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSystemClock;
import static org.junit.Assert.*;

/**
 * Full HomeActivity frames at 1205×753 dp, 340 dpi. Ambient stays off:
 * the unread-preview pattern crashes on this Android, and that fix is elsewhere.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w1205dp-h753dp-land-340dpi")
public class ConnectionHomeCaptureTest {
    @Before public void keepAmbientOff() {
        RuntimeEnvironment.getApplication().getSharedPreferences("home", 0).edit()
            .clear()
            .putBoolean("ambient_enabled", false)
            .putBoolean("muted", true)
            .putBoolean("auto_listen", false)
            .putBoolean("wake_mic_v1", true)
            .putBoolean("hey_bots_v1", true)
            .putString("selected_bot", "Max")
            .putString("voice_server", "http://192.168.1.20:8787")
            .commit();
    }

    @Test public void capturesHomeSheetAndChat() throws Exception {
        File dir = ScreenshotDirs.output();
        assertTrue(dir.exists() || dir.mkdirs());
        Activity home = launch();
        save(shot(home), new File(dir, "w1-connection-home-connected-after-v4.png"));
        publish(home, ConnectionRules.Mode.AWAY);
        save(shot(home), new File(dir, "w1-connection-home-away-after-v4.png"));
        publish(home, ConnectionRules.Mode.OFFLINE);
        save(shot(home), new File(dir, "w1-connection-home-offline-after-v4.png"));

        publish(home, ConnectionRules.Mode.OFFLINE);
        call(home, "showVoiceConnection");
        settle(home);
        assertTrue(text(decor(home), "What to check"));
        assertTrue(stageMessage(home), stageIsLeftOfSheet(home));
        assertCrownClearsChooser(home);
        assertControlsMatchStage(home);
        save(shot(home), new File(dir, "w1-connection-sheet-offline-after-v4.png"));
        click(decor(home), "What to check");
        Bitmap flash = shot(home);
        assertTrue(amber(flash) > 40);
        save(flash, new File(dir, "w1-connection-sheet-what-to-check-after-v4.png"));
        call(home, "closeOverlay");

        publish(home, ConnectionRules.Mode.CONNECTED);
        call(home, "showVoiceConnection");
        settle(home);
        assertCrownClearsChooser(home);
        assertFalse(text(decor(home), "What to check"));
        assertTrue(text(decor(home), "192.•••.•••.•••:8787"));
        assertTrue(text(decor(home), "Only needed away from home"));
        assertTrue(text(decor(home), "just now"));
        assertFalse(text(decor(home), "last sync"));
        save(shot(home), new File(dir, "w1-connection-sheet-connected-after-v5.png"));
        EditText field = find(decor(home), EditText.class);
        assertTrue(field.requestFocus());
        ShadowLooper.idleMainLooper();
        Button show = anyButton(decor(home), "Hide");
        assertNotNull(show);
        assertEquals(View.GONE, show.getVisibility());
        field.setText("not-an-address");
        click(decor(home), "Try now");
        assertNull(field.getError());
        assertTrue(text(decor(home), "Add a port, like 100.101.12.34:8787"));
        settle(home);
        save(shot(home), new File(dir, "w1-connection-sheet-address-error-after-v4.png"));
        call(home, "closeOverlay");

        publish(home, ConnectionRules.Mode.CONNECTED);
        ((NativeActivityFeed) field(home, "nativeFeed")).stop();
        Field stale = HomeActivity.class.getDeclaredField("nativeStale");
        stale.setAccessible(true);
        stale.setBoolean(home, false);
        Method keyboard = HomeActivity.class.getDeclaredMethod("showKeyboard", boolean.class, boolean.class);
        keyboard.setAccessible(true);
        keyboard.invoke(home, false, false);
        settle(home);
        View chat = (View) field(home, "chatPanel");
        assertTrue(chat.getWidth() > 100);
        assertEquals(0f, chat.getTranslationX(), 1f);
        save(shot(home), new File(dir, "w1-connection-chat-chip-after-v4.png"));
        assertTrue(text(decor(home), "Connected"));
        assertTrue(text(decor(home), "Activity synced"));
        assertFalse(text(decor(home), "sync unavailable"));
    }

    @Test
    @Config(qualifiers = "w753dp-h1205dp-port-340dpi")
    public void capturesPortraitSheet() throws Exception {
        Activity home = launch();
        publish(home, ConnectionRules.Mode.OFFLINE);
        call(home, "showVoiceConnection");
        settle(home);
        View sheet = (View) field(home, "computerSheet");
        View bot = (View) field(home, "bot");
        View root = (View) field(home, "root");
        assertTrue(sheet.getHeight() + " vs " + root.getHeight(), sheet.getHeight() <= root.getHeight() * 0.70f + 2);
        assertTrue(bot.getBottom() + " vs " + sheet.getTop(), bot.getBottom() <= sheet.getTop() + 2);
        assertCrownClearsChooser(home);
        save(shot(home), new File(ScreenshotDirs.output(), "w1-connection-sheet-portrait-after-v4.png"));
    }

    private static Activity launch() throws Exception {
        ShadowSystemClock.advanceBy(java.time.Duration.ofMinutes(5));
        HomeActivity home = Robolectric.buildActivity(HomeActivity.class).setup().get();
        ((ConnectionMonitor) field(home, "connectionMonitor")).stop();
        publish(home, ConnectionRules.Mode.CONNECTED);
        ((BotView) field(home, "bot")).restEyes();
        return home;
    }

    private static void publish(Activity home, ConnectionRules.Mode mode) throws Exception {
        ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(3));
        long now = android.os.SystemClock.elapsedRealtime();
        ConnectionState state = (ConnectionState) field(home, "connection");
        state.noteSynced(Math.max(1, now - (mode == ConnectionRules.Mode.CONNECTED ? 3_000 : 4 * 60 * 1000)));
        boolean voice = mode != ConnectionRules.Mode.OFFLINE;
        boolean tailscale = mode == ConnectionRules.Mode.AWAY;
        String address = mode == ConnectionRules.Mode.CONNECTED ? "http://192.168.1.20:8787" : "https://100.101.12.34:8787";
        home.getSharedPreferences("home", 0).edit().putString("voice_server", address).commit();
        state.publish(mode, true, mode == ConnectionRules.Mode.AWAY ? "mobile data" : "Wi-Fi", tailscale, voice,
            voice ? "Answering" : "No answer on port 8787. Is the computer asleep?",
            voice ? 0 : now + 8_000, now);
    }

    private static void settle(Activity home) throws Exception {
        Method dock = HomeActivity.class.getDeclaredMethod("dockStageSheet");
        dock.setAccessible(true);
        dock.invoke(home);
        ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        endSlide(fieldOrNull(home, "computerSheet"));
        endSlide(fieldOrNull(home, "chatPanel"));
        View decor = decor(home);
        int width = decor.getWidth();
        int height = decor.getHeight();
        decor.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);
        ((BotView) field(home, "bot")).restEyes();
    }

    private static void endSlide(Object view) {
        if (!(view instanceof View)) return;
        View sliding = (View) view;
        sliding.animate().cancel();
        sliding.setTranslationX(0);
    }

    private static Object fieldOrNull(Activity home, String name) {
        try { return field(home, name); }
        catch (Exception error) { return null; }
    }

    private static void assertCrownClearsChooser(Activity home) throws Exception {
        BotView bot = (BotView) field(home, "bot");
        View chooser = (View) field(home, "botChooser");
        float crown = bot.getTop() + bot.crownTop();
        float limit = chooser.getBottom() + 16f * home.getResources().getDisplayMetrics().density;
        assertTrue("crown " + crown + " chooser " + limit, crown >= limit - 1f);
    }

    private static void assertControlsMatchStage(Activity home) throws Exception {
        View bot = (View) field(home, "bot");
        View controls = (View) field(home, "homeControls");
        assertEquals(bot.getWidth(), controls.getWidth());
        assertEquals(bot.getLeft(), controls.getLeft());
    }

    private static int amber(Bitmap bitmap) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); y += 3) {
            for (int x = 0; x < bitmap.getWidth(); x += 3) {
                int pixel = bitmap.getPixel(x, y);
                int red = (pixel >> 16) & 0xff;
                int green = (pixel >> 8) & 0xff;
                int blue = pixel & 0xff;
                if (red > 220 && green > 130 && green < 200 && blue < 80) count++;
            }
        }
        return count;
    }

    private static String stageMessage(Activity home) throws Exception {
        View bot = (View) field(home, "bot");
        View sheet = (View) field(home, "computerSheet");
        return "bot " + bot.getLeft() + "," + bot.getRight() + " sheet " + sheet.getLeft() + "," + sheet.getRight();
    }

    private static boolean stageIsLeftOfSheet(Activity home) throws Exception {
        View bot = (View) field(home, "bot");
        View sheet = (View) field(home, "computerSheet");
        return bot.getWidth() > 0 && sheet.getWidth() > 0 && bot.getRight() <= sheet.getLeft();
    }

    private static View decor(Activity home) { return home.getWindow().getDecorView(); }

    private static Bitmap shot(Activity home) {
        View view = decor(home);
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, view.getWidth()), Math.max(1, view.getHeight()), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static void call(Activity home, String name) throws Exception {
        Method method = HomeActivity.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(home);
    }

    private static Object field(Activity home, String name) throws Exception {
        Field found = HomeActivity.class.getDeclaredField(name);
        found.setAccessible(true);
        return found.get(home);
    }

    private static boolean text(View view, String value) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(value) && view.getVisibility() == View.VISIBLE) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (text(group.getChildAt(i), value)) return true;
        }
        return false;
    }

    private static void click(View view, String label) {
        Button button = button(view, label);
        assertNotNull(label, button);
        button.performClick();
    }

    private static Button anyButton(View view, String label) {
        if (view instanceof Button && ((Button) view).getText().toString().equals(label)) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = anyButton(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Button button(View view, String label) {
        if (view instanceof Button && ((Button) view).getText().toString().equals(label)) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = button(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T extends View> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = find(group.getChildAt(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void save(Bitmap bitmap, File file) throws Exception {
        file.getParentFile().mkdirs();
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        bitmap.recycle();
    }
}

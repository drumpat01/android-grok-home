package dev.grokhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
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
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

/**
 * Full HomeActivity first-run frames at 1205×753 dp, 340 dpi.
 * Ambient stays off: the unread-preview pattern crashes on this Android.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w1205dp-h753dp-land-340dpi")
public class FirstRunScreenshotTest {
    private static final int MEET = 1 << SetupProgress.MEET;
    private static final int CONNECT = 1 << SetupProgress.CONNECT;
    private static final int MIC = 1 << SetupProgress.MIC;

    @Before public void keepAmbientOff() {
        RuntimeEnvironment.getApplication().getSharedPreferences("home", 0).edit().clear().commit();
    }

    @Test public void currentStepIsThePrimaryActionAndMaxKeepsTheStage() throws Exception {
        File dir = artifacts();
        seed(false, MEET, 0, false);
        HomeActivity home = launch();
        settle(home);
        assertStageLeftOfPanel(home);
        assertCrownClearsChooser(home);
        assertSettingsStaysBesideThePanel(home);
        assertTrue(greenStroke(topStroke(described(decor(home), "Connect your computer"))));
        assertFalse(greenStroke(topStroke(described(decor(home), "Set as Home"))));
        assertTrue(text(decor(home), "1 of 4 done"));
        assertTrue(text(decor(home), "›"));
        assertNotNull(button(decor(home), "Add address"));
        assertNull(button(decor(home), "Finish"));
        assertNotNull(button(decor(home), "Skip"));
        assertFalse(text(decor(home), "I'll do this later"));
        assertTrue(state(decor(home), "Done"));
        assertTrue(state(decor(home), "Not done"));
        assertPanelFillsTheScreen(home);
        assertFalse(text(decor(home), "Skipped steps wait in Settings."));
        save(shot(home), new File(dir, "w1-first-run-connect-after-v5.png"));

        call(home, "finishSetup");
        SetupProgress setup = (SetupProgress) field(home, "setup");
        assertFalse(setup.stepSkipped(SetupProgress.CONNECT));
        assertFalse(setup.stepDone(SetupProgress.CONNECT));
        assertTrue(setup.dismissed);
    }

    @Test public void micDeniedOffersSettingsAndTheKeyboard() throws Exception {
        seed(false, MEET | CONNECT, 0, false);
        HomeActivity home = launch();
        home.onRequestPermissionsResult(7, new String[]{android.Manifest.permission.RECORD_AUDIO}, new int[]{PackageManager.PERMISSION_DENIED});
        settle(home);
        assertTrue(text(decor(home), "Open settings"));
        assertTrue(text(decor(home), "Use the keyboard instead"));
        assertNull(button(decor(home), "Allow microphone"));
        assertNull(button(decor(home), "Finish"));
        View panel = (View) field(home, "setupPanel");
        assertOnScreen(button(panel, "Open settings"));
        assertOnScreen(button(panel, "Use the keyboard instead"));
        assertFalse(text(panel, "Home screen and microphone are checked again"));
        assertFalse(text(panel, "Skipped steps wait in Settings."));
        assertPanelFillsTheScreen(home);
        assertEquals(View.GONE, ((View) field(home, "micSettings")).getVisibility());
        assertTrue(text(decor(home), "Turn on the microphone in Android settings, or use the keyboard."));
        assertCrownClearsChooser(home);
        save(shot(home), new File(artifacts(), "w1-first-run-mic-denied-after-v4.png"));
    }

    @Test public void homeNotHeldOffersTryAgain() throws Exception {
        grantMicrophone();
        seed(false, MEET | CONNECT | MIC, 0, true);
        HomeActivity home = launch();
        settle(home);
        assertTrue(text(decor(home), "Not set yet"));
        assertNotNull(button(decor(home), "Try again"));
        assertNotNull(button(decor(home), "Skip for now"));
        assertNull(button(decor(home), "Finish"));
        assertTrue(state(decor(home), "Not done"));
        assertCrownClearsChooser(home);
        assertPanelFillsTheScreen(home);
        assertFalse(text(decor(home), "Skipped steps wait in Settings."));
        save(shot(home), new File(artifacts(), "w1-first-run-home-not-set-after-v4.png"));
    }

    @Test public void skippedRowStaysVisible() throws Exception {
        seed(false, MEET, CONNECT, false);
        HomeActivity home = launch();
        settle(home);
        assertTrue(text(decor(home), "Skipped"));
        assertTrue(state(decor(home), "Skipped"));
        assertTrue(exact(decor(home), "-"));
        assertNotNull(button(decor(home), "Allow microphone"));
        assertPanelFillsTheScreen(home);
        assertOnScreen(textView((View) field(home, "setupPanel"), "Skipped steps wait in Settings."));
        save(shot(home), new File(artifacts(), "w1-first-run-skipped-after-v4.png"));
    }

    @Test public void finishShowsTheTeamAndThePinchHint() throws Exception {
        grantMicrophone();
        seed(true, 0xF, 0, false);
        HomeActivity home = launch();
        publish(home);
        forceComplete(home);
        settle(home);
        assertStageLeftOfPanel(home);
        assertTrue(text(decor(home), "4 of 4 done"));
        assertTrue(text(decor(home), "You're all set"));
        assertTrue(text(decor(home), "Connected · Wi-Fi"));
        View homeRow = described(decor(home), "Set as Home");
        assertFalse(homeRow.isFocusable());
        assertFalse(homeRow.hasFocus());
        assertFalse(greenStroke(topStroke(homeRow)));
        assertNotNull(button(decor(home), "Finish"));
        assertFalse(text(decor(home), "Skipped steps wait in Settings."));
        assertPanelFillsTheScreen(home);
        save(shot(home), new File(artifacts(), "w1-first-run-done-after-v4.png"));
        click(decor(home), "Finish");
        settle(home);
        assertNull(field(home, "setupPanel"));
        BotView bot = (BotView) field(home, "bot");
        assertTrue(bot.isOverview());
        assertEquals(1f, bot.zoomLevel(), 0.02f);
        TextView hint = (TextView) field(home, "pinchHint");
        assertEquals("Pinch to see the whole team", hint.getText().toString());
        assertNotNull(hint.getBackground());
        float density = home.getResources().getDisplayMetrics().density;
        float belowName = bot.overviewNameBottom() + 16f * density;
        assertEquals(belowName, (float) hint.getTop(), 3f);
        TextView title = textView(decor(home), "Ready when you are");
        View status = (View) field(home, "homeStatus");
        assertNotNull(title);
        int titleTop = status.getTop() + title.getTop();
        assertTrue("hint " + hint.getTop() + "-" + hint.getBottom() + " name " + belowName + " title " + titleTop,
            hint.getBottom() <= titleTop);
        assertTrue(text(decor(home), "Pinch to see the whole team"));
        save(shot(home), new File(artifacts(), "w1-first-run-finished-after-v4.png"));
    }

    @Test public void existingInstallOpensOnHome() throws Exception {
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
        HomeActivity home = launch();
        publish(home);
        settle(home);
        assertNull(field(home, "setupPanel"));
        assertFalse(text(decor(home), "Set up Grok Home"));
        assertTrue(text(decor(home), "Apps"));
        assertTrue(text(decor(home), "Settings"));
        assertTrue(text(decor(home), "Connected"));
        save(shot(home), new File(artifacts(), "w1-first-run-existing-after-v4.png"));
    }

    @Test
    @Config(qualifiers = "w753dp-h1205dp-port-340dpi")
    public void portraitPanelLeavesTheCrownClear() throws Exception {
        seed(false, MEET, 0, false);
        HomeActivity home = launch();
        settle(home);
        View bot = (View) field(home, "bot");
        View panel = (View) field(home, "setupPanel");
        View root = (View) field(home, "root");
        assertTrue(panel.getHeight() + " vs " + root.getHeight(), panel.getHeight() <= root.getHeight() * 0.70f + 2);
        assertTrue(bot.getBottom() + " vs " + panel.getTop(), bot.getBottom() <= panel.getTop() + 2);
        assertTrue(bot.getHeight() > home.getResources().getDisplayMetrics().density * 190);
        assertOnScreen(button(panel, "Add address"));
        assertFalse(text(panel, "Skipped steps wait in Settings."));
        assertCrownClearsChooser(home);
        assertEquals(View.GONE, ((View) field(home, "connectionChip")).getVisibility());
        save(shot(home), new File(artifacts(), "w1-first-run-portrait-after-v4.png"));
    }

    @Test public void meetMaxOffersGetStarted() throws Exception {
        seed(false, 0, 0, false);
        HomeActivity home = launch();
        settle(home);
        assertNotNull(button(decor(home), "Get started"));
        assertNull(button(decor(home), "Finish"));
        assertTrue(text(decor(home), "Hi, I'm Max"));
        assertCrownClearsChooser(home);
        assertPanelFillsTheScreen(home);
        assertFalse(text(decor(home), "Skipped steps wait in Settings."));
        save(shot(home), new File(artifacts(), "w1-first-run-meet-after-v4.png"));
    }

    @Test public void homeKeyShowsSetupWhenItIsNotDone() throws Exception {
        seed(false, 0, 0, false);
        HomeActivity home = launch();
        assertNotNull(field(home, "setupPanel"));
        home.onNewIntent(homeIntent());
        ShadowLooper.idleMainLooper();
        assertNotNull(field(home, "setupPanel"));
        assertNotNull(button(decor(home), "Get started"));
        assertTrue(text(decor(home), "Set up Grok Home"));
        home.onNewIntent(new Intent(Intent.ACTION_MAIN));
        ShadowLooper.idleMainLooper();
        assertNull(field(home, "setupPanel"));
    }

    @Test public void homeKeyReloadsSetupAfterPrefsAreCleared() throws Exception {
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
        HomeActivity home = launch();
        assertNull(field(home, "setupPanel"));
        home.onNewIntent(homeIntent());
        ShadowLooper.idleMainLooper();
        assertNull(field(home, "setupPanel"));
        RuntimeEnvironment.getApplication().getSharedPreferences("home", 0).edit()
            .remove("voice_server")
            .remove("setup_started")
            .remove("setup_done")
            .remove("setup_skipped")
            .remove("setup_dismissed")
            .remove("setup_home_attempted")
            .commit();
        home.onNewIntent(homeIntent());
        ShadowLooper.idleMainLooper();
        assertNotNull(field(home, "setupPanel"));
        assertNotNull(button(decor(home), "Get started"));
        assertTrue(text(decor(home), "Set up Grok Home"));
    }

    @Test public void homeKeyLeavesAFinishedSetupClosed() throws Exception {
        seed(false, 0, 0, false);
        HomeActivity home = launch();
        call(home, "finishSetup");
        assertNull(field(home, "setupPanel"));
        home.onNewIntent(homeIntent());
        ShadowLooper.idleMainLooper();
        assertNull(field(home, "setupPanel"));
        assertFalse(text(decor(home), "Set up Grok Home"));
    }

    @Test public void healthyProbeCompletesConnectWhenTheAddressIsSaved() throws Exception {
        seed(true, MEET, 0, false);
        HomeActivity home = launch();
        SetupProgress setup = (SetupProgress) field(home, "setup");
        assertFalse(setup.stepDone(SetupProgress.CONNECT));
        publishMode(home, ConnectionRules.Mode.OFFLINE, false);
        setup = (SetupProgress) field(home, "setup");
        assertFalse(setup.stepDone(SetupProgress.CONNECT));
        assertNotNull(button(decor(home), "Add address"));
        publish(home);
        setup = (SetupProgress) field(home, "setup");
        assertTrue(setup.stepDone(SetupProgress.CONNECT));
        assertNotNull(button(decor(home), "Allow microphone"));
        assertEquals(MEET | CONNECT, home.getSharedPreferences("home", 0).getInt("setup_done", 0));
    }

    @Test public void answeringWithoutASavedAddressLeavesConnectOpen() throws Exception {
        seed(false, MEET, 0, false);
        HomeActivity home = launch();
        publish(home);
        SetupProgress setup = (SetupProgress) field(home, "setup");
        assertFalse(setup.stepDone(SetupProgress.CONNECT));
        assertNotNull(button(decor(home), "Add address"));
    }

    @Test public void finishedChecklistDropsTheHomeOutline() throws Exception {
        grantMicrophone();
        seed(false, MEET | CONNECT | MIC, 0, true);
        HomeActivity home = launch();
        settle(home);
        View homeRow = described(decor(home), "Set as Home");
        assertTrue(homeRow.isFocusable());
        assertTrue(greenStroke(topStroke(homeRow)));
        forceComplete(home);
        assertFalse(homeRow.isFocusable());
        assertFalse(homeRow.hasFocus());
        assertFalse(greenStroke(topStroke(homeRow)));
    }

    private static void seed(boolean address, int done, int skipped, boolean homeAttempted) {
        android.content.SharedPreferences.Editor editor = RuntimeEnvironment.getApplication().getSharedPreferences("home", 0).edit()
            .clear()
            .putBoolean("ambient_enabled", false)
            .putBoolean("muted", true)
            .putBoolean("auto_listen", false)
            .putBoolean("wake_mic_v1", true)
            .putBoolean("hey_bots_v1", true)
            .putString("selected_bot", "Max")
            .putBoolean("setup_started", true)
            .putInt("setup_done", done)
            .putInt("setup_skipped", skipped)
            .putBoolean("setup_dismissed", false)
            .putBoolean("setup_home_attempted", homeAttempted);
        if (address) editor.putString("voice_server", "http://192.168.1.20:8787");
        editor.commit();
    }

    private static void grantMicrophone() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.RECORD_AUDIO);
    }

    private static HomeActivity launch() throws Exception {
        HomeActivity home = Robolectric.buildActivity(HomeActivity.class).setup().get();
        ((ConnectionMonitor) field(home, "connectionMonitor")).stop();
        ShadowLooper.idleMainLooper();
        return home;
    }

    private static Intent homeIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    }

    private static void publish(Activity home) throws Exception {
        ConnectionState state = (ConnectionState) field(home, "connection");
        long now = android.os.SystemClock.elapsedRealtime();
        state.noteSynced(Math.max(1, now));
        state.publish(ConnectionRules.Mode.CONNECTED, true, "Wi-Fi", false, true, "Answering", 0, now);
    }

    private static void publishMode(Activity home, ConnectionRules.Mode mode, boolean voice) throws Exception {
        ConnectionState state = (ConnectionState) field(home, "connection");
        long now = android.os.SystemClock.elapsedRealtime();
        state.publish(mode, mode != ConnectionRules.Mode.OFFLINE, "Wi-Fi", false, voice, voice ? "Answering" : "No answer", 0, now);
    }

    private static void forceComplete(HomeActivity home) throws Exception {
        SetupProgress setup = (SetupProgress) field(home, "setup");
        setup = setup.complete(SetupProgress.MEET).complete(SetupProgress.CONNECT).complete(SetupProgress.MIC).complete(SetupProgress.HOME);
        Field found = HomeActivity.class.getDeclaredField("setup");
        found.setAccessible(true);
        found.set(home, setup);
        Method bind = HomeActivity.class.getDeclaredMethod("bindSetup");
        bind.setAccessible(true);
        bind.invoke(home);
    }

    private static void assertPanelFillsTheScreen(HomeActivity home) throws Exception {
        View panel = (View) field(home, "setupPanel");
        View root = (View) field(home, "root");
        float density = home.getResources().getDisplayMetrics().density;
        int expected = root.getHeight() - Math.round(32f * density);
        assertEquals(expected, panel.getHeight());
        Button primary = null;
        for (String label : new String[]{"Get started", "Add address", "Allow microphone", "Open settings", "Set as Home", "Try again", "Finish"}) {
            primary = button(panel, label);
            if (primary != null) break;
        }
        assertNotNull(primary);
        assertTrue("primary " + primary.getBottom() + " panel " + panel.getHeight(), primary.getBottom() > panel.getHeight() - density * 140);
    }

    private static void assertSettingsStaysBesideThePanel(HomeActivity home) throws Exception {
        View panel = (View) field(home, "setupPanel");
        View pill = described(decor(home), "Settings");
        assertNotNull(pill);
        Rect settings = new Rect();
        Rect sheet = new Rect();
        assertTrue(pill.getGlobalVisibleRect(settings));
        assertTrue(panel.getGlobalVisibleRect(sheet));
        assertTrue("settings " + settings + " panel " + sheet, settings.right <= sheet.left + 1 && settings.width() > 0 && settings.height() > 0);
    }

    private static void assertStageLeftOfPanel(HomeActivity home) throws Exception {
        View bot = (View) field(home, "bot");
        View panel = (View) field(home, "setupPanel");
        View root = (View) field(home, "root");
        float density = home.getResources().getDisplayMetrics().density;
        assertTrue("bot " + bot.getRight() + " panel " + panel.getLeft(), bot.getWidth() > 0 && bot.getRight() <= panel.getLeft());
        assertEquals(StageSheet.landscapeStageWidth(root.getWidth(), panel.getWidth(), density), bot.getWidth());
    }

    private static void settle(Activity home) throws Exception {
        Method dock = HomeActivity.class.getDeclaredMethod("dockStageSheet");
        dock.setAccessible(true);
        dock.invoke(home);
        ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        View sheet = (View) field(home, "setupPanel");
        if (sheet != null) { sheet.animate().cancel(); sheet.setTranslationX(0); }
        View decor = decor(home);
        int width = decor.getWidth();
        int height = decor.getHeight();
        decor.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);
        ((BotView) field(home, "bot")).restEyes();
    }

    private static void assertCrownClearsChooser(Activity home) throws Exception {
        BotView bot = (BotView) field(home, "bot");
        View chooser = (View) field(home, "botChooser");
        float crown = bot.getTop() + bot.crownTop();
        float limit = chooser.getBottom() + 16f * home.getResources().getDisplayMetrics().density;
        assertTrue("crown " + crown + " limit " + limit, crown >= limit - 1f);
    }

    private static File artifacts() {
        File dir = ScreenshotDirs.output();
        assertTrue(dir.exists() || dir.mkdirs());
        return dir;
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

    private static View described(View view, String prefix) {
        CharSequence description = view.getContentDescription();
        if (description != null && description.toString().startsWith(prefix)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = described(group.getChildAt(i), prefix);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int topStroke(View row) {
        assertTrue(row.getWidth() > 0 && row.getHeight() > 0);
        Bitmap bitmap = Bitmap.createBitmap(row.getWidth(), row.getHeight(), Bitmap.Config.ARGB_8888);
        row.draw(new Canvas(bitmap));
        int color = bitmap.getPixel(bitmap.getWidth() / 2, 0);
        bitmap.recycle();
        return color;
    }

    private static boolean greenStroke(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return green > 160 && green > red + 40 && green > blue + 20;
    }

    private static TextView textView(View view, String value) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(value) && view.getVisibility() == View.VISIBLE) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = textView(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean exact(View view, String value) {
        if (view instanceof TextView && value.equals(((TextView) view).getText().toString()) && view.getVisibility() == View.VISIBLE) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (exact(group.getChildAt(i), value)) return true;
        }
        return false;
    }

    private static boolean text(View view, String value) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(value) && view.getVisibility() == View.VISIBLE) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (text(group.getChildAt(i), value)) return true;
        }
        return false;
    }

    private static boolean state(View view, String value) {
        if (value.contentEquals(view.getStateDescription() == null ? "" : view.getStateDescription())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (state(group.getChildAt(i), value)) return true;
        }
        return false;
    }

    private static void assertOnScreen(View view) {
        assertNotNull(view);
        Rect visible = new Rect();
        assertTrue(view.getGlobalVisibleRect(visible));
        assertTrue(view + " visible " + visible + " size " + view.getWidth() + "x" + view.getHeight(),
            visible.width() >= view.getWidth() - 2 && visible.height() >= view.getHeight() - 2);
    }

    private static void click(View view, String label) {
        Button button = button(view, label);
        assertNotNull(label, button);
        button.performClick();
    }

    private static Button button(View view, String label) {
        if (view instanceof Button && label.equals(((Button) view).getText().toString()) && view.getVisibility() == View.VISIBLE) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = button(group.getChildAt(i), label);
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

package dev.grokhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** After captures of the voice chrome at 1205×753 dp, 340 dpi. Before shots are already on disk. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 32)
public class VoiceHomeScreenshotTest {
    private static final int WIDTH = 2561, HEIGHT = 1600;

    @Test public void capturesVoiceChrome() throws Exception {
        File dir = ScreenshotDirs.output();
        assertTrue(dir.isDirectory());
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.RECORD_AUDIO);
        RuntimeEnvironment.getApplication().getSharedPreferences("home", 0).edit()
            .putBoolean("muted", false)
            .putBoolean(ListenPreference.KEY, true)
            .putBoolean(ListenPreference.MIGRATED, true)
            .putBoolean("stage_overview", false)
            .putBoolean(QuietHours.ENABLED, false)
            .commit();
        tablet(340, true);
        HomeActivity activity = Robolectric.buildActivity(HomeActivity.class).create().get();
        openHomeEyes(activity);
        Bitmap home = draw(activity, WIDTH, HEIGHT);
        save(home, new File(dir, "w2-voice-home-after.png"));
        assertEquals(WIDTH, home.getWidth());
        assertEquals(HEIGHT, home.getHeight());
        View decor = activity.getWindow().getDecorView();
        assertNotNull(find(decor, "Apps"));
        assertNotNull(find(decor, "Type"));
        assertNotNull(find(decor, "History"));
        assertNotNull(find(decor, "Settings"));
        assertNotNull(find(decor, MicState.spokenLabel(MicState.Phase.ARMED, "Max")));
        StatusBlock status = (StatusBlock) field(activity, "statusBlock");
        TextView subtitle = (TextView) field(status, "detail");
        TextView title = (TextView) field(status, "title");
        assertEquals(VoiceCopy.ARMED_SUBTITLE, subtitle.getText().toString());
        assertEquals(0xffb9c0c9, subtitle.getCurrentTextColor());
        assertEquals(26f * 340f / 160f, title.getTextSize(), 0.6f);
        assertEquals(15f * 340f / 160f, subtitle.getTextSize(), 0.6f);
        View band = (View) field(activity, "homeControls");
        assertEquals(Math.round(88 * 340f / 160f), band.getHeight());
        assertNo(decor, "Mute microphone");
        assertNo(decor, "Automatically listen");
        assertNo(decor, "Okay");
        setListening(activity, true);
        openHomeEyes(activity);
        Bitmap recording = draw(activity, WIDTH, HEIGHT);
        save(recording, new File(dir, "w2-voice-home-recording.png"));
        assertEquals(VoiceCopy.RECORDING_SUBTITLE, subtitle.getText().toString());
        assertNotNull(find(decor, "Stop listening"));
        setListening(activity, false);
        activity.getSharedPreferences("home", 0).edit().putBoolean("muted", true).commit();
        invoke(activity, "updateStatus");
        openHomeEyes(activity);
        Bitmap muted = draw(activity, WIDTH, HEIGHT);
        save(muted, new File(dir, "w2-voice-home-muted.png"));
        assertEquals(VoiceCopy.MUTED_SUBTITLE, subtitle.getText().toString());
        assertNotNull(find(decor, MicState.spokenLabel(MicState.Phase.MUTED, "Max")));
        ListenPreference.writeSwitch(activity.getSharedPreferences("home", 0), false);
        invoke(activity, "updateStatus");
        openHomeEyes(activity);
        Bitmap switched = draw(activity, WIDTH, HEIGHT);
        save(switched, new File(dir, "w2-voice-home-switch-off.png"));
        assertEquals(VoiceCopy.SWITCH_OFF_SUBTITLE, subtitle.getText().toString());
        ListenPreference.writeSwitch(activity.getSharedPreferences("home", 0), true);
        invoke(activity, "updateStatus");
        invoke(activity, "showDrawer");
        settle(activity.getWindow().getDecorView());
        Bitmap drawer = draw(activity, WIDTH, HEIGHT);
        save(drawer, new File(dir, "w2-voice-drawer-after.png"));
        View drawerDecor = activity.getWindow().getDecorView();
        assertTrue(settled(drawerDecor));
        assertNotNull(find(drawerDecor, VoiceCopy.LISTEN_TITLE + ". " + VoiceCopy.LISTEN_SUB));
        assertNotNull(findText(drawerDecor, VoiceCopy.DRAWER_NOTE));
        assertNo(drawerDecor, "Mute microphone");
        invoke(activity, "removeOverlay");
        fieldSet(activity, "resumed", true);
        java.lang.reflect.Method enter = HomeActivity.class.getDeclaredMethod("enterAmbient", boolean.class);
        enter.setAccessible(true);
        enter.invoke(activity, true);
        AmbientWall wall = (AmbientWall) field(activity, "wall");
        openAmbientEyes(wall);
        Bitmap hidden = draw(activity, WIDTH, HEIGHT);
        assertEquals(0, bottomInk(hidden));
        wall.setWakeListening(true);
        openAmbientEyes(wall);
        Bitmap ambient = draw(activity, WIDTH, HEIGHT);
        save(ambient, new File(dir, "w2-voice-ambient-after.png"));
        assertTrue(hintInk(ambient) > 0);
        home.recycle();
        recording.recycle();
        muted.recycle();
        switched.recycle();
        drawer.recycle();
        hidden.recycle();
        ambient.recycle();
        tablet(340, false);
        HomeActivity portrait = Robolectric.buildActivity(HomeActivity.class).create().get();
        openHomeEyes(portrait);
        Bitmap tall = draw(portrait, HEIGHT, WIDTH);
        save(tall, new File(dir, "w2-voice-home-after-portrait.png"));
        assertEquals(VoiceCopy.ARMED_SUBTITLE, ((TextView) field(field(portrait, "statusBlock"), "detail")).getText().toString());
        tall.recycle();
    }

    private static void openHomeEyes(HomeActivity activity) throws Exception {
        Object bot = field(activity, "bot");
        Object motion = field(bot, "motion");
        motion.getClass().getMethod("seed", double.class).invoke(motion, Math.PI / 1.13);
    }

    private static void openAmbientEyes(AmbientWall wall) throws Exception {
        java.lang.reflect.Field motions = AmbientWall.class.getDeclaredField("motions");
        motions.setAccessible(true);
        for (Object motion : ((java.util.Map<?, ?>) motions.get(wall)).values()) {
            motion.getClass().getMethod("seed", double.class).invoke(motion, Math.PI / 1.13);
        }
    }

    private static void setListening(HomeActivity activity, boolean on) throws Exception {
        Object conversation = field(activity, "conversation");
        java.lang.reflect.Field listening = conversation.getClass().getDeclaredField("listening");
        listening.setAccessible(true);
        listening.setBoolean(conversation, on);
        invoke(activity, "updateStatus");
    }

    private static void settle(View view) {
        view.animate().cancel();
        view.setTranslationX(0);
        view.setTranslationY(0);
        view.setAlpha(1);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) settle(group.getChildAt(i));
        }
    }

    private static boolean settled(View view) {
        if (view.getTranslationX() != 0 || view.getTranslationY() != 0) return false;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (!settled(group.getChildAt(i))) return false;
        }
        return true;
    }

    private static void tablet(int dpi, boolean land) {
        float density = dpi / 160f;
        int width = land ? WIDTH : HEIGHT, height = land ? HEIGHT : WIDTH;
        int widthDp = Math.round(width / density), heightDp = Math.round(height / density);
        RuntimeEnvironment.setQualifiers("w" + widthDp + "dp-h" + heightDp + "dp-" + (land ? "land-" : "") + dpi + "dpi");
        android.content.Context context = RuntimeEnvironment.getApplication();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        metrics.density = density;
        metrics.scaledDensity = density;
        metrics.densityDpi = dpi;
        metrics.xdpi = dpi;
        metrics.ydpi = dpi;
        metrics.widthPixels = width;
        metrics.heightPixels = height;
        android.content.res.Configuration configuration = context.getResources().getConfiguration();
        configuration.densityDpi = dpi;
        configuration.screenWidthDp = widthDp;
        configuration.screenHeightDp = heightDp;
        configuration.orientation = land
            ? android.content.res.Configuration.ORIENTATION_LANDSCAPE
            : android.content.res.Configuration.ORIENTATION_PORTRAIT;
        context.getResources().updateConfiguration(configuration, metrics);
    }

    private static Bitmap draw(HomeActivity activity, int width, int height) {
        View root = activity.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static void invoke(Object owner, String name) throws Exception {
        java.lang.reflect.Method method = owner.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(owner);
    }

    private static Object field(Object owner, String name) throws Exception {
        java.lang.reflect.Field found = owner.getClass().getDeclaredField(name);
        found.setAccessible(true);
        return found.get(owner);
    }

    private static void fieldSet(Object owner, String name, Object value) throws Exception {
        java.lang.reflect.Field found = owner.getClass().getDeclaredField(name);
        found.setAccessible(true);
        found.set(owner, value);
    }

    private static View find(View view, String description) {
        CharSequence current = view.getContentDescription();
        if (current != null && description.contentEquals(current)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void assertNo(View view, String banned) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null) assertFalse(text.toString(), text.toString().contains(banned));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) assertNo(group.getChildAt(i), banned);
        }
    }

    private static View findText(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int bottomInk(Bitmap bitmap) {
        int count = 0;
        for (int y = bitmap.getHeight() - 70; y < bitmap.getHeight() - 36; y += 2) {
            for (int x = bitmap.getWidth() / 2 - 120; x < bitmap.getWidth() / 2 + 120; x += 4) {
                if ((bitmap.getPixel(x, y) & 0xffffff) != 0) count++;
            }
        }
        return count;
    }

    /** Glyphs of solid #7D838C, allowing the antialiased edge. */
    private static int hintInk(Bitmap bitmap) {
        int count = 0;
        for (int y = bitmap.getHeight() - 70; y < bitmap.getHeight() - 36; y++) {
            for (int x = bitmap.getWidth() / 2 - 140; x < bitmap.getWidth() / 2 + 140; x++) {
                int pixel = bitmap.getPixel(x, y) & 0xffffff;
                int red = (pixel >> 16) & 0xff, green = (pixel >> 8) & 0xff, blue = pixel & 0xff;
                if (Math.abs(red - 0x7d) < 18 && Math.abs(green - 0x83) < 18 && Math.abs(blue - 0x8c) < 18) count++;
            }
        }
        return count;
    }

    private static void save(Bitmap bitmap, File file) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); }
    }
}

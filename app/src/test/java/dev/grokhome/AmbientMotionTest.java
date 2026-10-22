package dev.grokhome;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Stage motion scales the ambient wall, and 0 percent draws once and stops. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AmbientMotionTest {
    @Test public void stageMotionScalesEveryTileAndZeroStopsTheLoop() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("home", 0).edit().putInt("stage_motion", 0).commit();
        AmbientWall wall = new AmbientWall(app);
        Map<String, Motion> motions = motions(wall);
        for (Motion motion : motions.values()) assertEquals(0, motion.intensity, 0);

        setMotion(app, 100);
        wall.update(new HashMap<String, String>(), new HashSet<String>(), new HashSet<String>());
        for (int i = 0; i < BotIdentity.NAMES.length; i++) {
            double base = 0.35 + (i % 4) * 0.12;
            assertEquals(base, motions.get(BotIdentity.NAMES[i]).intensity, 0.0001);
        }

        setMotion(app, 150);
        wall.update(new HashMap<String, String>(), new HashSet<String>(), new HashSet<String>());
        assertEquals(0.35 * 1.5, motions.get("Max").intensity, 0.0001);

        setMotion(app, 50);
        wall.update(new HashMap<String, String>(), new HashSet<String>(), new HashSet<String>());
        assertEquals(0.35 * 0.5, motions.get("Max").intensity, 0.0001);

        setMotion(app, 0);
        layout(wall, 800, 480);
        wall.update(new HashMap<String, String>(), new HashSet<String>(), new HashSet<String>());
        draw(wall);
        assertEquals(FrameThrottle.STOP, wall.scheduledFrameDelay);
        for (Motion motion : motions.values()) {
            assertEquals(0, motion.intensity, 0);
            assertEquals(0, motion.yaw, 0);
            assertEquals(1, motion.blink(), 0);
        }

        setMotion(app, 100);
        wall.update(new HashMap<String, String>(), new HashSet<String>(), new HashSet<String>());
        draw(wall);
        assertTrue(wall.scheduledFrameDelay >= 0);
        assertEquals(FrameThrottle.FRAME_MS, wall.scheduledFrameDelay);
    }

    private static void setMotion(Application app, int percent) {
        app.getSharedPreferences("home", 0).edit().putInt("stage_motion", percent).commit();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Motion> motions(AmbientWall wall) throws Exception {
        Field field = AmbientWall.class.getDeclaredField("motions");
        field.setAccessible(true);
        return (Map<String, Motion>) field.get(wall);
    }

    private static void layout(View view, int width, int height) {
        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
        view.measure(widthSpec, heightSpec);
        view.layout(0, 0, width, height);
    }

    private static void draw(AmbientWall wall) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(wall.getWidth(), wall.getHeight(), Bitmap.Config.ARGB_8888);
        Method onDraw = AmbientWall.class.getDeclaredMethod("onDraw", Canvas.class);
        onDraw.setAccessible(true);
        onDraw.invoke(wall, new Canvas(bitmap));
        bitmap.recycle();
    }
}

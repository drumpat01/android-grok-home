package dev.grokhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Card and stage stills at 1205x753 dp, density 340. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 32, qualifiers = "w1205dp-h753dp-land-340dpi")
public class RaiseHandScreenshotTest {
    private static final float DENSITY = 340f / 160f;

    @Test public void questionCardAndStageStill() throws Exception {
        File dir = new File(ScreenshotDirs.output(), "screenshots");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException(dir.getAbsolutePath());
        Context landscape = tablet(1205, 753, true);
        int landW = Math.round(1205 * DENSITY);
        int landH = Math.round(753 * DENSITY);
        QuestionCard before = card(landscape);
        before.setVisibility(android.view.View.GONE);
        Bitmap beforeBitmap = draw(before, landW, landH);
        save(beforeBitmap, new File(dir, "w3-ryh-card-landscape-before.png"));
        beforeBitmap.recycle();

        before.show("Echo", 0xfff69412, new NeedsYou.Item[] {NeedsYou.debug("Echo", "Merge the Tesla page fix?")}, false, BotStageState.Clear.NONE);
        Bitmap after = draw(before, landW, landH);
        save(after, new File(dir, "w3-ryh-card-landscape-after.png"));
        assertEquals(QuestionCard.APPROVE, before.bannerText().toString());
        assertEquals("Echo needs you. Merge the Tesla page fix? Approve this in the Grok Bot app.", String.valueOf(before.getContentDescription()));
        assertFalse(String.valueOf(before.getContentDescription()).contains("Double tap to answer"));
        assertActions(before, QuestionCard.OPEN_CHAT, QuestionCard.LATER);
        assertEquals(0, before.answerControlCount());
        assertEquals(QuestionCard.panelWidth(landW, landH, DENSITY), before.panelMeasuredWidth());
        assertEquals(Math.round(460 * DENSITY), before.panelMeasuredWidth());
        assertTargets(before);
        after.recycle();

        Context portrait = tablet(753, 1205, false);
        int portW = Math.round(753 * DENSITY);
        int portH = Math.round(1205 * DENSITY);
        QuestionCard tall = card(portrait);
        tall.show("Echo", 0xfff69412, new NeedsYou.Item[] {NeedsYou.debug("Echo", "Merge the Tesla page fix?")}, false, BotStageState.Clear.NONE);
        Bitmap tallBitmap = draw(tall, portW, portH);
        save(tallBitmap, new File(dir, "w3-ryh-card-portrait-after.png"));
        assertEquals(portW - Math.round(32 * DENSITY), tall.panelMeasuredWidth());
        assertTargets(tall);
        tallBitmap.recycle();

        Context stageContext = tablet(1205, 753, true);
        stageContext.getSharedPreferences("home", 0).edit().putInt("stage_motion", 100).putInt("stage_size", 100).putInt("stage_light", 100).commit();
        BotView view = new BotView(stageContext);
        view.setActive(false);
        view.setBotName("Echo");
        view.setBallColor(0xfff69412);
        Bitmap plain = draw(view, landW, landH);
        save(plain, new File(dir, "w3-ryh-stage-before.png"));
        plain.recycle();
        long now = System.currentTimeMillis();
        view.setStageFrame("Echo", new BotStageState.Frame(BotStageState.State.NEEDS_YOU, BotStageState.OneShot.NONE, 0, now - 1000, true,
                false, false, "Merge the Tesla page fix?", "Merge the Tesla page fix?", 1, "debug:Echo", null, false, false));
        Bitmap hand = draw(view, landW, landH);
        save(hand, new File(dir, "w3-ryh-stage-hand-after.png"));
        hand.recycle();
        view.setStageFrame("Luna", new BotStageState.Frame(BotStageState.State.WORKING, BotStageState.OneShot.NONE, 0, now - 500, false,
                true, false, "", "", 0, "", null, false, false));
        view.setBotName("Luna");
        view.setBallColor(0xff1884fc);
        Bitmap ring = draw(view, landW, landH);
        save(ring, new File(dir, "w3-ryh-stage-ring-after.png"));
        ring.recycle();
    }

    @Test public void kitConfettiContrastsOnWhiteAndBlackWithoutLeavingThePalette() {
        int echo = 0xfff69412;
        int[] echoTints = ConfettiTints.of("Echo", echo);
        assertEquals(echo, echoTints[0]);
        assertEquals(ConfettiTints.mix(echo, Color.WHITE, .45f), echoTints[1]);
        assertEquals(Color.WHITE, echoTints[2]);
        assertEquals(ConfettiTints.mix(echo, Color.BLACK, .15f), echoTints[3]);
        assertEquals(0, ConfettiTints.edge("Echo"));
        assertEquals(0, ConfettiTints.edge("Max"));

        int[] kit = ConfettiTints.of("Nova", Color.WHITE);
        assertEquals(ConfettiTints.KIT_PALETTE.length, kit.length);
        int edge = ConfettiTints.edge("Nova");
        assertTrue(edge != 0);
        assertTrue(contrast(edge, Color.WHITE) >= 3.0);
        assertTrue(contrast(edge, Color.BLACK) >= 3.0);
        for (int i = 0; i < kit.length; i++) {
            assertEquals(ConfettiTints.mix(ConfettiTints.KIT_PALETTE[i], ConfettiTints.KIT_BODY, 0.85f), kit[i]);
            assertTrue(coolGray(kit[i]));
            assertTrue("fill on white " + Integer.toHexString(kit[i]), contrast(kit[i], Color.WHITE) >= 3.0);
            assertTrue("fill on black " + Integer.toHexString(kit[i]), contrast(kit[i], Color.BLACK) >= 3.0);
        }
        assertTrue(coolGray(edge));
        assertTrue(contrast(edge, Color.WHITE) > contrast(kit[2], Color.WHITE));
    }

    private static boolean coolGray(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return blue >= red && Math.abs(red - green) <= 20 && Math.abs(green - blue) <= 20;
    }

    private static double contrast(int a, int b) {
        double lighter = Math.max(luminance(a), luminance(b));
        double darker = Math.min(luminance(a), luminance(b));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * channel(Color.red(color)) + 0.7152 * channel(Color.green(color)) + 0.0722 * channel(Color.blue(color));
    }

    private static double channel(int value) {
        double unit = value / 255.0;
        return unit <= 0.04045 ? unit / 12.92 : Math.pow((unit + 0.055) / 1.055, 2.4);
    }

    @Test public void debugOverviewMarksSitInsideTheTabletMargin() {
        float margin = 48f * DENSITY;
        int width = 2560;
        int height = 1600;
        Context context = tablet(1205, 753, true);
        context.getSharedPreferences("home", 0).edit().putInt("stage_motion", 100).putInt("stage_size", 100).putInt("stage_light", 100).commit();
        BotView echoSet = focusedOverview(context, width, height, "Echo", "Luna", "Nova");
        assertMarkInside(echoSet, "Nova", margin, width);
        assertMarkInside(echoSet, "Echo", margin, width);
        assertMarkInside(echoSet, "Luna", margin, width);
        assertTrue(echoSet.debugMarkRight("Luna") - echoSet.debugCenter("Luna") >= 1.18f * echoSet.debugRadius("Luna"));
        assertTrue(echoSet.debugMarkRight("Nova") - echoSet.debugMarkLeft("Nova") >= 2f * echoSet.debugRadius("Nova") - 0.5f);

        BotView spread = focusedOverview(context, width, height, "Max", "Echo", "Luna");
        assertMarkInside(spread, "Max", margin, width);
        assertMarkInside(spread, "Echo", margin, width);
        assertTrue(Float.isNaN(spread.debugMarkLeft("Luna")));
    }

    private static BotView focusedOverview(Context context, int width, int height, String needs, String working, String done) {
        BotView view = new BotView(context);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        view.setBotName("Max");
        long now = System.currentTimeMillis();
        if (BotIdentity.index(needs) >= 0) view.setStageFrame(needs, stageFrame(BotStageState.State.NEEDS_YOU, now));
        if (BotIdentity.index(working) >= 0) view.setStageFrame(working, stageFrame(BotStageState.State.WORKING, now));
        if (BotIdentity.index(done) >= 0) view.setStageFrame(done, stageFrame(BotStageState.State.DONE, now));
        view.focusDebugOverview(needs, working, done);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        bitmap.recycle();
        return view;
    }

    private static BotStageState.Frame stageFrame(BotStageState.State state, long now) {
        boolean hand = state == BotStageState.State.NEEDS_YOU;
        return new BotStageState.Frame(state, BotStageState.OneShot.NONE, 0L, now - 1000L, hand,
                state == BotStageState.State.WORKING, false, "", "", hand ? 1 : 0, "", null, false, false);
    }

    private static void assertMarkInside(BotView view, String name, float margin, int width) {
        float left = view.debugMarkLeft(name);
        float right = view.debugMarkRight(name);
        assertFalse(name + " was not drawn", Float.isNaN(left) || Float.isNaN(right));
        assertTrue(name + " left " + left + " margin " + margin, left >= margin);
        assertTrue(name + " right " + right + " limit " + (width - margin), right <= width - margin);
    }

    private static QuestionCard card(Context context) {
        return new QuestionCard(context, new QuestionCard.Host() {
            @Override public void openChat(String bot) {}
            @Override public void later(String bot, String needsYouId, long untilMillis) {}
            @Override public void dismiss(String bot) {}
        });
    }

    private static Bitmap draw(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static void assertActions(View view, String... labels) {
        ArrayList<String> found = new ArrayList<>();
        collectButtons(view, found);
        for (String label : labels) assertTrue(found.toString(), found.contains(label));
    }

    private static void collectButtons(View view, ArrayList<String> found) {
        if (view instanceof Button button && button.getVisibility() == View.VISIBLE) found.add(button.getText().toString());
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collectButtons(group.getChildAt(i), found);
        }
    }

    private static void assertTargets(View view) {
        int min = Math.round(48 * DENSITY);
        if (view instanceof Button button && button.getVisibility() == View.VISIBLE) {
            assertTrue(button.getText() + " height " + button.getHeight(), button.getHeight() >= min);
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) assertTargets(group.getChildAt(i));
        }
    }

    private static void save(Bitmap bitmap, File file) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
    }

    private static Context tablet(int widthDp, int heightDp, boolean land) {
        String orientation = land ? "land" : "port";
        RuntimeEnvironment.setQualifiers("w" + widthDp + "dp-h" + heightDp + "dp-" + orientation + "-340dpi");
        Context context = RuntimeEnvironment.getApplication();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        metrics.density = DENSITY;
        metrics.scaledDensity = DENSITY;
        metrics.densityDpi = 340;
        metrics.xdpi = 340;
        metrics.ydpi = 340;
        android.content.res.Configuration configuration = context.getResources().getConfiguration();
        configuration.densityDpi = 340;
        configuration.screenWidthDp = widthDp;
        configuration.screenHeightDp = heightDp;
        configuration.orientation = land ? android.content.res.Configuration.ORIENTATION_LANDSCAPE : android.content.res.Configuration.ORIENTATION_PORTRAIT;
        context.getResources().updateConfiguration(configuration, metrics);
        return context;
    }
}

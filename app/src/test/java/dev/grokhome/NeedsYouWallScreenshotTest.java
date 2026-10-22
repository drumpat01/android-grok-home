package dev.grokhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeProviderCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Ambient wall stills for an empty needs_you list, one hand, three hands, and quiet hours. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 32, qualifiers = "w1205dp-h753dp-land-340dpi")
public class NeedsYouWallScreenshotTest {
    private static final int WIDTH = 2560, HEIGHT = 1600;

    @Test public void emptyNeedsYouMatchesTodaysWall() throws Exception {
        Context context = tablet();
        Map<String, String> lines = captions();
        Set<String> working = new HashSet<>(Arrays.asList("Max", "Willow"));
        Set<String> unread = new HashSet<>(Collections.singletonList("Nova"));
        Bitmap today = render(context, lines, working, unread, null, false);
        BridgeNeedsYouAdapter empty = new BridgeNeedsYouAdapter();
        empty.accept(snapshot("Nova", "none", "", 1L, true));
        Bitmap cleared = render(context, lines, working, unread, empty, false);
        assertTrue("Empty needs_you changed the wall", today.sameAs(cleared));
        AmbientLayout.Slot ivy = slot("Luna");
        assertEquals(AmbientWall.TILE_FILL, today.getPixel(Math.round(ivy.left + 8), Math.round((ivy.top + ivy.bottom) / 2f)));
        assertEquals(Color.BLACK, today.getPixel(WIDTH - 40, 40));
        File dir = ScreenshotDirs.output();
        save(today, new File(dir, "needs-you-wall-0.png"));
        today.recycle();
        cleared.recycle();
    }

    @Test public void oneNeedsYouTileUsesThatBotsColor() throws Exception {
        Context context = tablet();
        BridgeNeedsYouAdapter adapter = new BridgeNeedsYouAdapter();
        adapter.setClock(5_000L);
        adapter.accept(snapshot("Clover", "handoff:cedar", "Keep the paid plan?", 2_000L, false));
        adapter.accept(questionOnly("Nova"));
        Bitmap bitmap = render(context, captions(), working(), unread(), adapter, false);
        AmbientWall wall = lastWall;
        assertEquals(Arrays.asList("Clover"), wall.needsYouOrder());
        assertEquals("Clover. Needs you: Keep the paid plan? Double tap to open.", wall.tileDescription("Clover"));
        assertEquals("Nova. New reply. Double tap to open chat.", wall.tileDescription("Nova"));
        assertEquals("Willow. Working. Double tap to open chat.", wall.tileDescription("Willow"));
        assertEquals("", wall.pillLabel());
        assertBorder(bitmap, slot("Clover"), 0xff14c370, false);
        assertNoBorder(bitmap, slot("Luna"));
        assertTrue(whiteCaption(bitmap, slot("Clover")) > 40);
        File dir = ScreenshotDirs.output();
        save(bitmap, new File(dir, "needs-you-wall-1.png"));
        bitmap.recycle();
    }

    @Test public void threeNeedsYouTilesStayInOldestOrder() throws Exception {
        Context context = tablet();
        BridgeNeedsYouAdapter adapter = hands();
        adapter.accept(snapshot("Siri", "handoff:nope", "Not a roster bot", 1L, false));
        Bitmap bitmap = render(context, captions(), working(), unread(), adapter, false);
        assertEquals(Arrays.asList("Sage", "Clover", "Luna"), lastWall.needsYouOrder());
        assertBorder(bitmap, slot("Sage"), 0xfffb3499, false);
        assertBorder(bitmap, slot("Clover"), 0xff14c370, false);
        assertBorder(bitmap, slot("Luna"), 0xff905afb, false);
        assertNoBorder(bitmap, slot("Echo"));
        assertEquals("", lastWall.pillDescription());
        File dir = ScreenshotDirs.output();
        save(bitmap, new File(dir, "needs-you-wall-3.png"));
        bitmap.recycle();
    }

    @Test public void quietHoursDimsHandsAndPostsTheNote() throws Exception {
        Context context = tablet();
        Bitmap bitmap = render(context, captions(), working(), unread(), hands(), true);
        assertEquals(Arrays.asList("Sage", "Clover", "Luna"), lastWall.needsYouOrder());
        assertEquals("Quiet hours until 7:00 AM", lastWall.quietNote());
        assertBorder(bitmap, slot("Clover"), 0xff14c370, true);
        assertTrue("Quiet note missing", lightPixels(bitmap, slot("Max"), Math.round(6f * 340f / 160f), Math.round(50f * 340f / 160f)) > 12);
        assertQuietChip(bitmap, slot("Clover"));
        int corner = bitmap.getPixel(Math.round(slot("Clover").left + 18), Math.round(slot("Clover").top + 18));
        assertTrue("Quiet glow still bright " + Integer.toHexString(corner), Color.green(corner) < 90);
        File dir = ScreenshotDirs.output();
        save(bitmap, new File(dir, "needs-you-wall-quiet.png"));
        bitmap.recycle();
    }

    @Test public void offScreenHandsShowTheEdgePill() throws Exception {
        Context context = tablet();
        BridgeNeedsYouAdapter adapter = hands();
        adapter.accept(snapshot("Orion", "handoff:arm", "Check the gray triangle?", 4_000L, false));
        Bitmap bitmap = render(context, captions(), working(), unread(), adapter, false);
        AmbientWall wall = lastWall;
        moveOffScreen(wall, "Luna");
        moveOffScreen(wall, "Orion");
        wall.refreshPill();
        bitmap.eraseColor(Color.BLACK);
        draw(wall, bitmap);
        assertEquals("2 bots need you \u203A", wall.pillLabel());
        assertEquals("2 bots need you. Double tap to open.", wall.pillDescription());
        RectF frame = new RectF();
        wall.pillFrame(frame);
        assertTrue(frame.width() >= 48f);
        assertTrue(frame.height() >= 48f * (340f / 160f) - 2f);
        int text = 0;
        for (int y = Math.round(frame.top); y < frame.bottom; y += 2) for (int x = Math.round(frame.left); x < frame.right; x += 2) {
            int color = bitmap.getPixel(x, y);
            if (Color.red(color) > 200 && Color.green(color) > 200 && Color.blue(color) > 200) text++;
        }
        assertTrue("Pill text pixels " + text, text > 8);
        assertBorder(bitmap, slot("Sage"), 0xfffb3499, false);
        assertTalkBack(wall, "Sage", "Sage. Needs you: Merge the Tesla page fix? Double tap to open.");
        File dir = ScreenshotDirs.output();
        save(bitmap, new File(dir, "needs-you-edge-pill.png"));
        bitmap.recycle();
    }

    @Test public void needsYouStillsForMaxKitAndOrion() throws Exception {
        saveNeedsYou("Max", BotIdentity.color("Max"), "needs-you-max.png");
        saveNeedsYou("Nova", NeedsYouTint.KIT_MARK, "needs-you-kit.png");
        saveNeedsYou("Orion", BotIdentity.color("Orion"), "needs-you-orion.png");
    }

    @Test public void needsYouReplacesWorkingStatus() throws Exception {
        Context context = tablet();
        BotStageState stage = new BotStageState(null);
        stage.debugLock("Willow", BotStageState.State.NEEDS_YOU, NeedsYou.debug("Willow", "Check the ledger?"), "", 5_000L);
        Map<String, String> lines = captions();
        lines.put("Willow", "Working");
        Bitmap bitmap = render(context, lines, working(), unread(), stage, false);
        String spoken = lastWall.tileDescription("Willow");
        assertEquals("Willow. Needs you: Check the ledger? Double tap to open.", spoken);
        assertFalse(spoken.contains("Working"));
        assertTrue(whiteCaption(bitmap, slot("Willow")) > 40);
        float density = 340f / 160f;
        AmbientLayout.Slot juniper = slot("Willow");
        int dot = bitmap.getPixel(Math.round(juniper.left + 16f * density), Math.round(juniper.top + 16f * density));
        assertTrue("Working dot still showing " + Integer.toHexString(dot), dot != 0xff3ddc84);
        bitmap.recycle();
    }

    @Test public void workingRingFollowsTheStage() throws Exception {
        Context context = tablet();
        Bitmap plain = render(context, captions(), working(), unread(), null, false);
        BotStageState stage = new BotStageState(null);
        stage.debugLock("Willow", BotStageState.State.WORKING, null, "", 5_000L);
        Bitmap ring = render(context, captions(), working(), unread(), stage, false);
        assertTrue("Willow ring missing", changedPixels(plain, ring, slot("Willow")) > 8);
        assertEquals(0, changedPixels(plain, ring, slot("Luna")));
        save(ring, new File(ScreenshotDirs.output(), "needs-you-working-ring.png"));
        plain.recycle();
        ring.recycle();
    }

    @Test public void missingQuietPrefDefaultsOn() throws Exception {
        Context context = tablet();
        int minute = QuietHours.minuteOfDay(System.currentTimeMillis(), java.time.ZoneId.systemDefault());
        int start = Math.floorMod(minute - 30, 1440);
        int end = Math.floorMod(minute + 30, 1440);
        if (start == end) end = Math.floorMod(end + 1, 1440);
        context.getSharedPreferences("home", 0).edit().remove(QuietHours.ENABLED).putInt(QuietHours.START, start).putInt(QuietHours.END, end).commit();
        render(context, captions(), working(), unread(), hands(), false);
        assertTrue(lastWall.quietNote().startsWith("Quiet hours until "));
    }

    @Test public void quietPrefOffStaysBright() throws Exception {
        Context context = tablet();
        int minute = QuietHours.minuteOfDay(System.currentTimeMillis(), java.time.ZoneId.systemDefault());
        context.getSharedPreferences("home", 0).edit()
            .putBoolean(QuietHours.ENABLED, false)
            .putInt(QuietHours.START, Math.floorMod(minute - 30, 1440))
            .putInt(QuietHours.END, Math.floorMod(minute + 60, 1440))
            .commit();
        render(context, captions(), working(), unread(), hands(), false);
        assertEquals("", lastWall.quietNote());
    }

    @Test public void talkBackReadsStageFrameAndHands() throws Exception {
        Context context = tablet();
        BotStageState stage = new BotStageState(null);
        long now = System.currentTimeMillis();
        stage.debugLock("Nova", BotStageState.State.DONE, null, "debug-done", now);
        stage.debugLock("Willow", BotStageState.State.WORKING, null, "", now);
        stage.debugLock("Pixel", BotStageState.State.WORKING, null, "", now);
        stage.debugLock("Sage", BotStageState.State.NEEDS_YOU, NeedsYou.debug("Sage", "Merge the Tesla page fix?"), "", now);
        AmbientWall wall = new AmbientWall(context);
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);
        wall.update(captions(), working(), unread());
        wall.setNeedsYou(stage);
        assertEquals("Nova. Done. Double tap to open chat.", wall.tileDescription("Nova"));
        assertEquals("Willow. Working. Double tap to open chat.", wall.tileDescription("Willow"));
        assertEquals("Pixel. Working. Double tap to open chat.", wall.tileDescription("Pixel"));
        assertEquals("Luna. Double tap to open chat.", wall.tileDescription("Luna"));
        String sage = wall.tileDescription("Sage");
        assertEquals("Sage. Needs you: Merge the Tesla page fix? Double tap to open.", sage);
        assertFalse(sage.contains("Working"));
        assertFalse(wall.tileDescription("Nova").contains("Double tap to answer"));
        assertFalse(wall.tileDescription("Pixel").contains("Double tap to answer"));
        assertFalse(sage.contains("Double tap to answer"));
        assertFalse(wall.tileDescription("Luna").contains("Double tap to answer"));
        assertFalse(wall.pillDescription().contains("Double tap to answer"));
    }

    @Test public void debugDoneExtraKeepsTheWallLabelPastTheHop() throws Exception {
        Context context = tablet();
        BotStageState stage = new BotStageState(null);
        Intent intent = new Intent();
        intent.putExtra(RaiseHandDebug.EXTRA_DONE, "Nova");
        long then = System.currentTimeMillis() - StagePose.DONE_MS - 4_000L;
        assertTrue(RaiseHandDebug.applyIntent(intent, stage, then));
        AmbientWall wall = new AmbientWall(context);
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);
        wall.update(captions(), working(), unread());
        wall.setNeedsYou(stage);
        assertEquals(BotStageState.State.DONE, stage.frame("Nova").state);
        assertEquals("Nova. Done. Double tap to open chat.", wall.tileDescription("Nova"));
        assertEquals("Nova. Done. Double tap to open chat.", wall.tileDescription("Nova"));
    }

    @Test public void debugForcePillShowsWhileTilesFit() throws Exception {
        Context context = tablet();
        ApplicationInfo info = context.getApplicationInfo();
        int saved = info.flags;
        try {
            info.flags = saved | ApplicationInfo.FLAG_DEBUGGABLE;
            BotStageState stage = new BotStageState(null);
            AmbientWall wall = new AmbientWall(context);
            wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
            wall.layout(0, 0, WIDTH, HEIGHT);
            wall.update(captions(), working(), unread());
            wall.setNeedsYou(stage);
            assertEquals("", wall.pillLabel());
            Intent intent = new Intent();
            intent.putExtra(RaiseHandDebug.EXTRA_NEEDS_YOU, "Echo");
            intent.putExtra(RaiseHandDebug.EXTRA_FORCE_PILL, true);
            assertTrue(RaiseHandDebug.applyIntent(intent, stage, 5_000L));
            assertEquals(Arrays.asList("Echo"), wall.needsYouOrder());
            assertEquals("Echo needs you \u203A", wall.pillLabel());
            assertEquals("Echo needs you. Double tap to open.", wall.pillDescription());
            AmbientLayout.Slot echo = slot("Echo");
            assertTrue(echo.right <= WIDTH && echo.bottom <= HEIGHT);
            info.flags = saved & ~ApplicationInfo.FLAG_DEBUGGABLE;
            wall.refreshPill();
            assertEquals("", wall.pillLabel());
        } finally {
            info.flags = saved;
        }
    }

    private static AmbientWall lastWall;

    private static BridgeNeedsYouAdapter hands() throws Exception {
        BridgeNeedsYouAdapter adapter = new BridgeNeedsYouAdapter();
        adapter.setClock(9_000L);
        adapter.accept(snapshot("Sage", "handoff:sage", "Merge the Tesla page fix?", 1_000L, false));
        adapter.accept(snapshot("Clover", "handoff:cedar", "Keep the paid plan?", 2_000L, false));
        adapter.accept(snapshot("Luna", "handoff:ivy", "Ship the caption fix?", 3_000L, false));
        return adapter;
    }

    private static JSONObject snapshot(String bot, String id, String question, long created, boolean clear) throws Exception {
        JSONObject snapshot = new JSONObject();
        snapshot.put("bot", bot);
        JSONArray rows = new JSONArray();
        if (!clear) {
            JSONObject row = new JSONObject();
            row.put("id", id);
            row.put("kind", "approval");
            row.put("question", question);
            row.put("question_short", question);
            row.put("answerable_here", false);
            row.put("created", created);
            rows.put(row);
        }
        snapshot.put("needs_you", rows);
        return snapshot;
    }

    private static JSONObject questionOnly(String bot) throws Exception {
        JSONObject snapshot = new JSONObject();
        snapshot.put("bot", bot);
        snapshot.put("needs_you", new JSONArray());
        snapshot.put("entries", new JSONArray().put(new JSONObject().put("kind", "message").put("role", bot).put("text", "Want me to keep going?")));
        return snapshot;
    }

    private static Map<String, String> captions() {
        Map<String, String> lines = new HashMap<>();
        lines.put("Nova", "4.5.2 in review");
        lines.put("Max", "Working");
        return lines;
    }

    private static Set<String> working() {
        return new HashSet<>(Arrays.asList("Max", "Willow"));
    }

    private static Set<String> unread() {
        return new HashSet<>(Collections.singletonList("Nova"));
    }

    private static Context tablet() {
        RuntimeEnvironment.setQualifiers("w1205dp-h753dp-land-340dpi");
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("home", 0).edit()
            .putInt("stage_motion", 0)
            .putBoolean(QuietHours.ENABLED, false)
            .remove(QuietHours.START)
            .remove(QuietHours.END)
            .commit();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float density = 340f / 160f;
        metrics.density = density;
        metrics.scaledDensity = density;
        metrics.densityDpi = 340;
        metrics.xdpi = 340;
        metrics.ydpi = 340;
        metrics.widthPixels = WIDTH;
        metrics.heightPixels = HEIGHT;
        android.content.res.Configuration configuration = context.getResources().getConfiguration();
        configuration.densityDpi = 340;
        configuration.screenWidthDp = 1205;
        configuration.screenHeightDp = 753;
        configuration.orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        context.getResources().updateConfiguration(configuration, metrics);
        return context;
    }

    private static Bitmap render(Context context, Map<String, String> lines, Set<String> working, Set<String> unread, NeedsYouSource source, boolean quiet) throws Exception {
        AmbientWall wall = new AmbientWall(context);
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);
        Field avatars = AmbientWall.class.getDeclaredField("avatars");
        avatars.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String, SolidAvatar> ready = (Map<String, SolidAvatar>) avatars.get(wall);
        Set<String> shapes = new HashSet<>();
        for (String name : BotIdentity.NAMES) shapes.add(BotIdentity.shape(name));
        for (String shape : shapes) ready.put(shape, new SolidAvatar(context, shape));
        wall.update(lines, working, unread);
        if (source != null) wall.setNeedsYou(source);
        if (quiet) wall.setQuietHours(true, QuietHours.DEFAULT_END);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLACK);
        draw(wall, bitmap);
        lastWall = wall;
        return bitmap;
    }

    private static void draw(AmbientWall wall, Bitmap bitmap) throws Exception {
        Method onDraw = AmbientWall.class.getDeclaredMethod("onDraw", Canvas.class);
        onDraw.setAccessible(true);
        onDraw.invoke(wall, new Canvas(bitmap));
    }

    @SuppressWarnings("unchecked")
    private static void moveOffScreen(AmbientWall wall, String name) throws Exception {
        Field field = AmbientWall.class.getDeclaredField("now");
        field.setAccessible(true);
        Map<String, float[]> rects = (Map<String, float[]>) field.get(wall);
        rects.put(name, new float[]{-1200f, -1200f, -1000f, -1000f});
    }

    private static AmbientLayout.Slot slot(String name) {
        float density = 340f / 160f;
        for (AmbientLayout.Slot slot : AmbientLayout.place(BotIdentity.NAMES, WIDTH, HEIGHT, 8f * density)) {
            if (slot.name.equals(name)) return slot;
        }
        fail(name);
        return null;
    }

    private static void assertBorder(Bitmap bitmap, AmbientLayout.Slot slot, int tint, boolean quiet) {
        int samples = 0, hits = 0;
        int y = Math.round((slot.top + slot.bottom) / 2f);
        for (int x = Math.round(slot.left) + 1; x < slot.left + 10; x++) {
            samples++;
            if (borderPixel(bitmap.getPixel(x, y), tint, quiet)) hits++;
        }
        assertTrue(slot.name + " border hits " + hits + " of " + samples, hits >= 2);
    }

    private static void assertNoBorder(Bitmap bitmap, AmbientLayout.Slot slot) {
        int y = Math.round((slot.top + slot.bottom) / 2f);
        int color = bitmap.getPixel(Math.round(slot.left) + 2, y);
        assertEquals(slot.name + " edge " + Integer.toHexString(color), AmbientWall.TILE_FILL, color);
    }

    private static boolean borderPixel(int color, int tint, boolean quiet) {
        int red = Color.red(color), green = Color.green(color), blue = Color.blue(color);
        int tr = Color.red(tint), tg = Color.green(tint), tb = Color.blue(tint);
        if (!quiet) return Math.abs(red - tr) < 28 && Math.abs(green - tg) < 28 && Math.abs(blue - tb) < 28;
        int max = Math.max(red, Math.max(green, blue));
        return max > 28 && channel(red, tr) && channel(green, tg) && channel(blue, tb);
    }

    private static boolean channel(int got, int tint) {
        if (tint < 40) return got < 50;
        return got > 20 && got < tint;
    }

    private static void saveNeedsYou(String bot, int tint, String file) throws Exception {
        Context context = tablet();
        BridgeNeedsYouAdapter adapter = new BridgeNeedsYouAdapter();
        adapter.setClock(5_000L);
        adapter.accept(snapshot(bot, "handoff:" + bot, "Merge the Tesla page fix?", 2_000L, false));
        Bitmap bitmap = render(context, captions(), working(), unread(), adapter, false);
        assertEquals(Arrays.asList(bot), lastWall.needsYouOrder());
        assertEquals(bot + ". Needs you: Merge the Tesla page fix? Double tap to open.", lastWall.tileDescription(bot));
        assertBorder(bitmap, slot(bot), tint, false);
        save(bitmap, new File(ScreenshotDirs.output(), file));
        bitmap.recycle();
    }

    private static int changedPixels(Bitmap before, Bitmap after, AmbientLayout.Slot slot) {
        int changed = 0;
        int top = Math.max(0, Math.round(slot.top));
        int bottom = Math.min(before.getHeight(), Math.round(slot.bottom));
        int left = Math.max(0, Math.round(slot.left));
        int right = Math.min(before.getWidth(), Math.round(slot.right));
        for (int y = top; y < bottom; y += 2) for (int x = left; x < right; x += 2) {
            if (before.getPixel(x, y) != after.getPixel(x, y)) changed++;
        }
        return changed;
    }

    private static void assertQuietChip(Bitmap bitmap, AmbientLayout.Slot slot) {
        float density = 340f / 160f;
        int x = Math.round(slot.right - 26f * density);
        int y = Math.round(slot.top + 26f * density - 32f * density * 0.42f);
        int got = bitmap.getPixel(x, y);
        int dimmed = over(AmbientWall.TILE_FILL, AmbientWall.QUIET_DIM);
        int expected = over(dimmed, NeedsYouTint.chipFill(true));
        int stacked = over(over(AmbientWall.TILE_FILL, NeedsYouTint.chipFill(true)), AmbientWall.QUIET_DIM);
        assertTrue("chip " + Integer.toHexString(got) + " expected " + Integer.toHexString(expected), near(got, expected, 4));
        assertTrue("chip stacked on the dim " + Integer.toHexString(got), Math.abs(Color.red(got) - Color.red(stacked)) > 1
            || Math.abs(Color.green(got) - Color.green(stacked)) > 1
            || Math.abs(Color.blue(got) - Color.blue(stacked)) > 1);
    }

    private static int over(int dst, int src) {
        int alpha = Color.alpha(src);
        int inv = 255 - alpha;
        return Color.rgb(
            (Color.red(src) * alpha + Color.red(dst) * inv) / 255,
            (Color.green(src) * alpha + Color.green(dst) * inv) / 255,
            (Color.blue(src) * alpha + Color.blue(dst) * inv) / 255);
    }

    private static boolean near(int got, int expected, int slack) {
        return Math.abs(Color.red(got) - Color.red(expected)) <= slack
            && Math.abs(Color.green(got) - Color.green(expected)) <= slack
            && Math.abs(Color.blue(got) - Color.blue(expected)) <= slack;
    }

    private static int lightPixels(Bitmap bitmap, AmbientLayout.Slot slot, int inset, int span) {
        int count = 0;
        int left = Math.round(slot.left + inset), top = Math.round(slot.top + inset);
        for (int y = top; y < top + span; y++) for (int x = left; x < left + span * 4; x++) {
            if (x < 0 || y < 0 || x >= bitmap.getWidth() || y >= bitmap.getHeight()) continue;
            int color = bitmap.getPixel(x, y);
            if (Color.red(color) > 140 && Color.green(color) > 140 && Color.blue(color) > 140) count++;
        }
        return count;
    }

    private static int whiteCaption(Bitmap bitmap, AmbientLayout.Slot slot) {
        int count = 0;
        int top = Math.max(0, Math.round(slot.bottom - 36));
        int bottom = Math.min(bitmap.getHeight() - 1, Math.round(slot.bottom - 4));
        int left = Math.round(slot.left + 16);
        int right = Math.round(slot.right - 16);
        for (int y = top; y <= bottom; y++) for (int x = left; x < right; x++) {
            int color = bitmap.getPixel(x, y);
            if (Color.red(color) > 220 && Color.green(color) > 220 && Color.blue(color) > 220) count++;
        }
        return count;
    }

    private static void assertTalkBack(AmbientWall wall, String name, String expected) {
        AccessibilityDelegateCompat delegate = ViewCompat.getAccessibilityDelegate(wall);
        assertNotNull(delegate);
        AccessibilityNodeProviderCompat provider = delegate.getAccessibilityNodeProvider(wall);
        assertNotNull(provider);
        int id = -1;
        for (int i = 0; i < BotIdentity.NAMES.length; i++) if (BotIdentity.NAMES[i].equals(name)) id = i;
        AccessibilityNodeInfoCompat node = provider.createAccessibilityNodeInfo(id);
        assertNotNull(node);
        assertEquals(expected, node.getContentDescription());
        node = provider.createAccessibilityNodeInfo(BotIdentity.NAMES.length);
        assertNotNull(node);
        assertEquals(wall.pillDescription(), node.getContentDescription());
    }

    private static void save(Bitmap bitmap, File file) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
    }
}

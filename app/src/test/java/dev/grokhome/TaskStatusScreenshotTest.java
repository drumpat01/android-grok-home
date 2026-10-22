package dev.grokhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Software renders of the task status at 1205×753 dp, 340 dpi, plus portrait. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 32, qualifiers = "w1205dp-h753dp-land-340dpi")
public class TaskStatusScreenshotTest {
    private static final float DENSITY = 340f / 160f;
    private static final int LAND_W = Math.round(1205 * DENSITY);
    private static final int LAND_H = Math.round(753 * DENSITY);
    private static final String DETAIL = "Reviewing the new page with Sage";

    @Test public void capturesTaskStates() throws Exception {
        assertEquals(1_700_000_000_500L, BotConversation.startedAtMillis(new JSONObject().put("started_at", 1_700_000_000.5)));
        assertEquals(0, BotConversation.startedAtMillis(new JSONObject()));
        Context context = tablet(1205, 753, true);
        File dir = ScreenshotDirs.output();
        Bitmap before = scene(context, LAND_W, LAND_H, "Nova", BallColors.WHITE, false, null, null, null, true);
        save(before, new File(dir, "w2-task-working-before.png"));
        int card = ink(before, dp(24), dp(84), dp(260), dp(72));
        assertTrue("Legacy card should be visible, ink " + card, card > 80);
        before.recycle();

        Bitmap idle = scene(context, LAND_W, LAND_H, "Nova", BallColors.WHITE, true, TaskProgress.Phase.IDLE, null, null, false);
        save(idle, new File(dir, "w2-task-idle-after.png"));
        assertTrue(ink(idle, 0, 0, LAND_W, LAND_H) > 1000);
        idle.recycle();

        Bitmap working = scene(context, LAND_W, LAND_H, "Nova", BallColors.WHITE, true, TaskProgress.Phase.WORKING, "2:14", DETAIL, false);
        save(working, new File(dir, "w2-task-working-after.png"));
        int oldCard = ink(working, dp(24), dp(84), dp(260), dp(72));
        assertTrue("Top-left card should be gone, ink " + oldCard, oldCard < card / 2);
        assertTrue(ink(working, 0, LAND_H - dp(220), LAND_W, dp(180)) > 200);
        working.recycle();

        Bitmap seen = scene(context, LAND_W, LAND_H, "Max", BallColors.RED, true, TaskProgress.Phase.WORKING, "Seen 3 min ago", "", false);
        save(seen, new File(dir, "w2-task-seen-after.png"));
        seen.recycle();
        Bitmap stopping = scene(context, LAND_W, LAND_H, "Max", BallColors.RED, true, TaskProgress.Phase.STOPPING, "2:14", "", false);
        save(stopping, new File(dir, "w2-task-stopping-after.png"));
        stopping.recycle();
        Bitmap stopped = scene(context, LAND_W, LAND_H, "Nova", BallColors.WHITE, true, TaskProgress.Phase.STOPPED, null, "", false);
        save(stopped, new File(dir, "w2-task-stopped-after.png"));
        stopped.recycle();
        Bitmap failed = scene(context, LAND_W, LAND_H, "Nova", BallColors.WHITE, true, TaskProgress.Phase.STOP_FAILED, null, "", false);
        save(failed, new File(dir, "w2-task-stop-failed-after.png"));
        failed.recycle();
        Bitmap finished = scene(context, LAND_W, LAND_H, "Nova", BallColors.WHITE, true, TaskProgress.Phase.FINISHED, null, "", false);
        save(finished, new File(dir, "w2-task-finished-after.png"));
        finished.recycle();

        Context portrait = tablet(753, 1205, false);
        int width = Math.round(753 * DENSITY), height = Math.round(1205 * DENSITY);
        Bitmap tall = scene(portrait, width, height, "Nova", BallColors.WHITE, true, TaskProgress.Phase.WORKING, "2:14", DETAIL, false);
        save(tall, new File(dir, "w2-task-working-portrait-after.png"));
        assertTrue(ink(tall, 0, 0, width, height) > 1000);
        tall.recycle();
    }

    @Test public void stoppedKeepsTheStoppingTitleBaseline() {
        StatusBlock block = new StatusBlock(tablet(1205, 753, true));
        block.showTitle("Stopping Max's task", "");
        int stopping = block.blockHeight();
        assertEquals("", block.detailText());
        block.showTitle("Task stopped", "");
        assertEquals(stopping, block.blockHeight());
        assertEquals(dp(StagePlacement.TASK_BLOCK_DP), block.blockHeight());
        assertEquals("", block.detailText());
    }

    @Test public void stoppingSpinnerSitsInsideTheDisabledButton() {
        Context context = tablet(1205, 753, true);
        HomeControls bar = controls(context);
        bar.setWings("Stop task", R.drawable.ic_stop, () -> {}, "Open chat", R.drawable.ic_chat, () -> {});
        bar.setLeftBusy(true);
        bar.measure(View.MeasureSpec.makeMeasureSpec(LAND_W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dp(88), View.MeasureSpec.EXACTLY));
        bar.layout(0, 0, LAND_W, dp(88));
        View stop = find(bar, "Stopping");
        assertNotNull(stop);
        assertFalse(stop.isEnabled());
        assertFalse(stop.isClickable());
        bar.freezeSpinner();
        TextView label = null;
        View spinner = null;
        LinearLayout pill = (LinearLayout) stop;
        for (int i = 0; i < pill.getChildCount(); i++) {
            View child = pill.getChildAt(i);
            if (child instanceof TextView text) label = text;
            else if (child.getVisibility() == View.VISIBLE) spinner = child;
        }
        assertNotNull(spinner);
        assertEquals(View.VISIBLE, spinner.getVisibility());
        assertSame(stop, spinner.getParent());
        assertTrue("spinner width " + spinner.getWidth(), spinner.getWidth() >= dp(16));
        assertTrue("spinner height " + spinner.getHeight(), spinner.getHeight() >= dp(16));
        Bitmap spin = Bitmap.createBitmap(spinner.getWidth(), spinner.getHeight(), Bitmap.Config.ARGB_8888);
        spinner.draw(new Canvas(spin));
        int lit = 0;
        for (int y = 0; y < spin.getHeight(); y++) for (int x = 0; x < spin.getWidth(); x++) {
            int pixel = spin.getPixel(x, y);
            if (Color.alpha(pixel) > 200 && Color.red(pixel) > 220 && Color.green(pixel) > 220 && Color.blue(pixel) > 220) lit++;
        }
        assertTrue("spinner arc should paint, lit pixels " + lit, lit > 8);
        spin.recycle();
        assertNotNull(label);
        assertEquals(0xffa7adb6, label.getCurrentTextColor());
        Bitmap pillBitmap = Bitmap.createBitmap(stop.getWidth(), stop.getHeight(), Bitmap.Config.ARGB_8888);
        stop.draw(new Canvas(pillBitmap));
        int border = pillBitmap.getPixel(pillBitmap.getWidth() / 2, 0);
        assertEquals(0xff34383e, border);
        pillBitmap.recycle();
        assertTrue(spinner.getLeft() >= stop.getPaddingLeft());
        assertTrue(spinner.getRight() <= stop.getWidth() - stop.getPaddingRight());
        assertTrue(spinner.getTop() >= stop.getPaddingTop());
        assertTrue(spinner.getBottom() <= stop.getHeight() - stop.getPaddingBottom());
    }

    private static HomeControls controls(Context context) {
        return new HomeControls(context, new HomeControls.Callbacks() {
            public void onApps() {}
            public void onType() {}
            public void onHistory() {}
            public void onSettings() {}
            public void onMicTap() {}
            public void onMicLongPress() {}
        });
    }

    private static View find(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof android.view.ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = find(group.getChildAt(i), description);
            if (found != null) return found;
        }
        return null;
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

    private static Bitmap scene(Context context, int width, int height, String bot, int color, boolean lifted, TaskProgress.Phase phase, String clock, String detail, boolean legacy) {
        FrameLayout root = new FrameLayout(context);
        BotView view = new BotView(context);
        view.setActive(false);
        view.setBotName(bot);
        view.setBallColor(color);
        view.restEyes();
        root.addView(view, new FrameLayout.LayoutParams(width, height));
        int statusHeight = dp(phase == TaskProgress.Phase.IDLE ? StagePlacement.IDLE_BLOCK_DP : StagePlacement.TASK_BLOCK_DP);
        HomeBand.Placement place = HomeBand.place(height, dp(HomeBand.ROW_HEIGHT_DP), 0, DENSITY);
        if (legacy) legacyCard(context, root);
        else chrome(context, root, bot, phase, clock, detail, place, statusHeight);
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        if (lifted && !legacy) view.setStatusShift(1f, height - place.statusBottom - statusHeight);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static void chrome(Context context, FrameLayout root, String bot, TaskProgress.Phase phase, String clock, String detail, HomeBand.Placement place, int statusHeight) {
        StatusBlock block = new StatusBlock(context);
        boolean seen = clock != null && clock.startsWith("Seen");
        String body = seen ? TaskProgress.seenDetail("Check the build status", "Check the build status", detail) : detail;
        switch (phase) {
            case IDLE, NEEDS_YOU -> block.showIdle("Ready when you are", "Listening for Hey Bots · or tap the mic");
            case WORKING -> block.showWorking(bot + " is working", clock, seen, body);
            case STOPPING -> block.showTitle("Stopping " + bot + "'s task", "");
            case STOPPED -> block.showTitle("Task stopped", "");
            case STOP_FAILED -> block.showTitle("Couldn't stop the task", "");
            case FINISHED -> block.showTitle(bot + " finished", "");
            default -> throw new IllegalStateException("Unhandled task phase " + phase);
        }
        FrameLayout.LayoutParams status = new FrameLayout.LayoutParams(-1, statusHeight, Gravity.BOTTOM);
        status.bottomMargin = place.statusBottom;
        root.addView(block, status);
        HomeControls bar = controls(context);
        switch (phase) {
            case WORKING -> bar.setWings("Stop task", R.drawable.ic_stop, () -> {}, "Open chat", R.drawable.ic_chat, () -> {});
            case STOPPING -> bar.setWings("Stop task", R.drawable.ic_stop, () -> {}, "Open chat", R.drawable.ic_chat, () -> {});
            case STOP_FAILED -> bar.setWings("Try again", 0, () -> {}, "Open chat", R.drawable.ic_chat, () -> {});
            case FINISHED -> bar.setWings("View reply", 0, () -> {}, "Dismiss", 0, () -> {});
            case IDLE, STOPPED, NEEDS_YOU -> bar.setWings("Type", R.drawable.ic_keyboard, () -> {}, "History", R.drawable.ic_history, () -> {});
            default -> throw new IllegalStateException("Unhandled task phase " + phase);
        }
        bar.setLeftBusy(phase == TaskProgress.Phase.STOPPING);
        if (phase == TaskProgress.Phase.STOPPING) bar.freezeSpinner();
        bar.setPhase(MicState.Phase.ARMED, bot, false);
        FrameLayout.LayoutParams row = new FrameLayout.LayoutParams(-1, place.rowHeight, Gravity.BOTTOM);
        row.bottomMargin = place.rowBottom;
        root.addView(bar, row);
    }

    private static void legacyCard(Context context, FrameLayout root) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(4), dp(8), dp(4));
        android.graphics.drawable.GradientDrawable fill = new android.graphics.drawable.GradientDrawable();
        fill.setColor(0xff191c20);
        fill.setCornerRadius(dp(20));
        card.setBackground(fill);
        TextView label = new TextView(context);
        label.setText("Nova · Working · 2:14\n" + DETAIL);
        label.setTextSize(13);
        label.setTextColor(Color.WHITE);
        label.setMaxLines(2);
        card.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        Button cancel = new Button(context);
        cancel.setText("Cancel");
        cancel.setAllCaps(false);
        cancel.setTextColor(Color.WHITE);
        cancel.setTextSize(15);
        card.addView(cancel, new LinearLayout.LayoutParams(-2, dp(48)));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(260), -2, Gravity.TOP | Gravity.LEFT);
        params.topMargin = dp(84);
        params.leftMargin = dp(24);
        root.addView(card, params);
    }

    private static int dp(float value) { return Math.round(value * DENSITY); }

    private static int ink(Bitmap bitmap, int left, int top, int width, int height) {
        int count = 0;
        int right = Math.min(bitmap.getWidth(), left + width);
        int bottom = Math.min(bitmap.getHeight(), top + height);
        for (int y = Math.max(0, top); y < bottom; y += 3) for (int x = Math.max(0, left); x < right; x += 3) if ((bitmap.getPixel(x, y) & 0xffffff) != 0) count++;
        return count;
    }

    private static void save(Bitmap bitmap, File file) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
    }
}

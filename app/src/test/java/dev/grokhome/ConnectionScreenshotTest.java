package dev.grokhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;
import static org.junit.Assert.*;

/** Connection chip and Computer sheet at 1205×753 dp, 340 dpi, plus a portrait sheet. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w1205dp-h753dp-land-340dpi")
public class ConnectionScreenshotTest {
    private static final int WIDTH = 2561;
    private static final int HEIGHT = 1600;
    private static final float DENSITY = 340f / 160f;

    @Test public void whatToCheckStartsWithTailscaleWhenThatNetworkIsOff() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ComputerSheet tailnet = sheet(activity, "https://100.101.12.34:8787");
        ConnectionState down = new ConnectionState();
        long now = android.os.SystemClock.elapsedRealtime();
        down.publish(ConnectionRules.Mode.OFFLINE, true, "Wi-Fi", false, false,
            "No answer on port 8787. Is the computer asleep?", now + 8_000, now);
        tailnet.bind(down);
        assertTrue(tailnet.firstProblem().getContentDescription().toString().contains("Tailscale"));

        down.publish(ConnectionRules.Mode.OFFLINE, true, "Wi-Fi", false, true, "Answering", 0, now);
        tailnet.bind(down);
        assertTrue(tailnet.firstProblem().getContentDescription().toString().contains("Tailscale"));

        ComputerSheet lan = sheet(activity, "http://192.168.1.20:8787");
        down.publish(ConnectionRules.Mode.OFFLINE, true, "Wi-Fi", false, false,
            "No answer on port 8787. Is the computer asleep?", now + 8_000, now);
        lan.bind(down);
        assertTrue(lan.firstProblem().getContentDescription().toString().contains("Grok Home Voice"));

        down.publish(ConnectionRules.Mode.OFFLINE, false, "", false, false,
            "No answer on port 8787. Is the computer asleep?", now + 8_000, now);
        tailnet.bind(down);
        assertTrue(tailnet.firstProblem().getContentDescription().toString().contains("Tablet"));
    }

    @Test public void capturesConnectionStates() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        applyDensity(activity, WIDTH, HEIGHT, true);
        File dir = ScreenshotDirs.output();
        assertTrue(dir.exists() || dir.mkdirs());

        Bitmap before = renderBefore(activity);
        save(before, new File(dir, "w1-connection-dialog-before.png"));
        assertTrue(ink(before) > 1000);
        before.recycle();

        for (ConnectionRules.Mode mode : ConnectionRules.Mode.values()) {
            Bitmap home = renderHome(activity, mode);
            save(home, new File(dir, "w1-connection-home-" + fileName(mode) + "-component.png"));
            assertTrue(countColor(home, ConnectionRules.dotColor(mode)) > 20);
            home.recycle();
            Bitmap sheet = renderSheet(activity, mode, WIDTH, HEIGHT);
            save(sheet, new File(dir, "w1-connection-sheet-" + fileName(mode) + "-component.png"));
            assertTrue(ink(sheet) > 2000);
            sheet.recycle();
        }

        applyDensity(activity, HEIGHT, WIDTH, false);
        Bitmap portrait = renderSheet(activity, ConnectionRules.Mode.OFFLINE, HEIGHT, WIDTH);
        save(portrait, new File(dir, "w1-connection-sheet-portrait-component.png"));
        assertEquals(HEIGHT, portrait.getWidth());
        assertEquals(WIDTH, portrait.getHeight());
        assertTrue(ink(portrait) > 2000);
        portrait.recycle();
    }

    private static Bitmap renderBefore(Activity activity) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setText("http://100.101.12.34:8787");
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle("Computer connection")
            .setMessage("Home computer address for your Cursor bots. No xAI key needed.")
            .setView(input)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create();
        dialog.show();
        View decor = dialog.getWindow().getDecorView();
        int width = dp(activity, 460);
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(activity, 280), View.MeasureSpec.AT_MOST));
        decor.layout(0, 0, decor.getMeasuredWidth(), decor.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.BLACK);
        canvas.save();
        canvas.translate((WIDTH - decor.getWidth()) / 2f, (HEIGHT - decor.getHeight()) / 2f);
        decor.draw(canvas);
        canvas.restore();
        dialog.dismiss();
        return bitmap;
    }

    private static Bitmap renderHome(Activity activity, ConnectionRules.Mode mode) {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.BLACK);
        BotView bot = new BotView(activity);
        bot.setBotName("Max");
        bot.setBallColor(BallColors.RED);
        bot.restEyes();
        root.addView(bot, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ConnectionChip chip = new ConnectionChip(activity, () -> {});
        chip.bind(mode);
        FrameLayout.LayoutParams chipParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 48), Gravity.TOP | Gravity.RIGHT);
        chipParams.setMargins(dp(activity, 24), dp(activity, 24), dp(activity, 24), 0);
        root.addView(chip, chipParams);
        if (mode == ConnectionRules.Mode.OFFLINE) {
            LinearLayout status = new LinearLayout(activity);
            status.setOrientation(LinearLayout.VERTICAL);
            status.setGravity(Gravity.CENTER);
            TextView title = label(activity, ConnectionRules.OFFLINE_TITLE, 24, Color.WHITE);
            TextView subtitle = label(activity, ConnectionRules.OFFLINE_SAVED, 13, 0xffa7adb6);
            title.setGravity(Gravity.CENTER);
            subtitle.setGravity(Gravity.CENTER);
            status.addView(title);
            status.addView(subtitle);
            FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 72), Gravity.BOTTOM);
            statusParams.bottomMargin = Math.round(HEIGHT * .22f);
            root.addView(status, statusParams);
        }
        return draw(root, WIDTH, HEIGHT);
    }

    private static ComputerSheet sheet(Activity activity, String address) {
        return new ComputerSheet(activity, new ComputerSheet.Host() {
            public void close() {}
            public void tryNow() {}
            public void save(String stored) {}
            public String address() { return address; }
        });
    }

    private static Bitmap renderSheet(Activity activity, ConnectionRules.Mode mode, int width, int height) {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.BLACK);
        ConnectionState state = fixture(mode);
        ComputerSheet sheet = new ComputerSheet(activity, new ComputerSheet.Host() {
            public void close() {}
            public void tryNow() {}
            public void save(String stored) {}
            public String address() { return addressFor(mode); }
        });
        sheet.bind(state);
        int sheetWidth = Math.min(dp(activity, 460), width - dp(activity, 32));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(sheetWidth, height - dp(activity, 32), Gravity.TOP | Gravity.RIGHT);
        params.setMargins(0, dp(activity, 16), dp(activity, 16), dp(activity, 16));
        root.addView(sheet, params);
        return draw(root, width, height);
    }

    private static ConnectionState fixture(ConnectionRules.Mode mode) {
        ShadowSystemClock.advanceBy(java.time.Duration.ofMinutes(10));
        ConnectionState state = new ConnectionState();
        long now = android.os.SystemClock.elapsedRealtime();
        state.noteSynced(now - (mode == ConnectionRules.Mode.CONNECTED ? 3_000 : 4 * 60 * 1000));
        boolean voice = mode != ConnectionRules.Mode.OFFLINE;
        boolean tailscale = mode == ConnectionRules.Mode.AWAY;
        state.publish(mode, true, "Wi-Fi", tailscale, voice,
            voice ? "Answering" : "No answer on port 8787. Is the computer asleep?",
            voice ? 0 : now + 8_000, now);
        return state;
    }

    private static String addressFor(ConnectionRules.Mode mode) {
        return mode == ConnectionRules.Mode.CONNECTED ? "http://192.168.1.20:8787" : "https://100.101.12.34:8787";
    }

    private static Bitmap draw(View root, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        root.draw(canvas);
        return bitmap;
    }

    private static void applyDensity(Activity activity, int width, int height, boolean landscape) {
        float density = DENSITY;
        int widthDp = Math.round(width / density);
        int heightDp = Math.round(height / density);
        RuntimeEnvironment.setQualifiers("w" + widthDp + "dp-h" + heightDp + "dp-" + (landscape ? "land-" : "port-") + "340dpi");
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        metrics.density = density;
        metrics.scaledDensity = density;
        metrics.densityDpi = 340;
        metrics.xdpi = 340;
        metrics.ydpi = 340;
        metrics.widthPixels = width;
        metrics.heightPixels = height;
        android.content.res.Configuration configuration = activity.getResources().getConfiguration();
        configuration.densityDpi = 340;
        configuration.screenWidthDp = widthDp;
        configuration.screenHeightDp = heightDp;
        configuration.orientation = landscape
            ? android.content.res.Configuration.ORIENTATION_LANDSCAPE
            : android.content.res.Configuration.ORIENTATION_PORTRAIT;
        activity.getResources().updateConfiguration(configuration, metrics);
    }

    private static TextView label(Activity activity, String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static String fileName(ConnectionRules.Mode mode) {
        switch (mode) {
            case CONNECTED: return "connected";
            case AWAY: return "away";
            case OFFLINE: return "offline";
            default: throw new AssertionError(mode);
        }
    }

    private static int ink(Bitmap bitmap) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); y += 8) {
            for (int x = 0; x < bitmap.getWidth(); x += 8) {
                if ((bitmap.getPixel(x, y) & 0xffffff) != 0) count++;
            }
        }
        return count;
    }

    private static int countColor(Bitmap bitmap, int color) {
        int target = color & 0xffffff;
        int count = 0;
        for (int y = 0; y < Math.min(bitmap.getHeight(), 200); y++) {
            for (int x = bitmap.getWidth() / 2; x < bitmap.getWidth(); x++) {
                int pixel = bitmap.getPixel(x, y) & 0xffffff;
                if (Math.abs(Color.red(pixel) - Color.red(target)) < 28
                    && Math.abs(Color.green(pixel) - Color.green(target)) < 28
                    && Math.abs(Color.blue(pixel) - Color.blue(target)) < 28) count++;
            }
        }
        return count;
    }

    private static void save(Bitmap bitmap, File file) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
    }
}

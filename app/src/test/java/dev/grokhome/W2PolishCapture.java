package dev.grokhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Renders the screens this slice changes, at the tablet's 1205 x 753 dp landscape. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class W2PolishCapture {
    private static final String QUALIFIERS = "w1205dp-h753dp-land-340dpi";

    @Test
    @Config(sdk = 34, qualifiers = QUALIFIERS)
    public void landscape() throws Exception {
        capture(false);
    }

    @Test
    @Config(sdk = 34, qualifiers = "w753dp-h1205dp-port-340dpi")
    public void portraitChat() throws Exception {
        capture(true);
    }

    private static void capture(boolean portrait) throws Exception {
        String phase = System.getProperty("w2.phase", "after");
        File dir = new File(System.getProperty("w2.out", "build/w2-polish"));
        dir.mkdirs();
        HomeActivity activity = Robolectric.buildActivity(HomeActivity.class).setup().get();
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        FrameLayout root = (FrameLayout) activity.findViewById(android.R.id.content);
        layout(root, width, height);
        openBotEyes(root);
        if (!portrait) {
            write(dir, "w2-polish-home-" + phase + ".png", draw(root));
            clickDescription(root, "Choose bot");
            showOffscreenSlides(root);
            layout(root, width, height);
            openBotEyes(root);
            write(dir, "w2-polish-picker-" + phase + ".png", draw(root));
            clickText(root, "Close");
            clickDescription(root, "Settings");
            showOffscreenSlides(root);
            layout(root, width, height);
            openBotEyes(root);
            write(dir, "w2-polish-drawer-" + phase + ".png", draw(root));
            String note = "Shows the whole team. Max has the big tile, and the line under him says who is working or has a new reply. Tap a bot to open it.";
            assertEquals(note, textOf(root, note.substring(0, 20)));
            scrollToText(root, note.substring(0, 20));
            write(dir, "w2-polish-drawer-note-" + phase + ".png", draw(root));
            write(dir, "w2-polish-ambient-" + phase + ".png", wall(activity, width, height));
            List<SeekBar> sliders = new ArrayList<SeekBar>();
            collect(root, SeekBar.class, sliders);
            assertEquals(3, sliders.size());
            for (SeekBar slider : sliders) {
                assertEquals(String.valueOf(slider.getContentDescription()), "100 percent", String.valueOf(slider.getStateDescription()));
                assertEquals(null, slider.getForeground());
                if ("Bot size".contentEquals(slider.getContentDescription())) assertEquals(30, slider.getProgress());
            }
        }
        TextChatPanel panel = chatPanel(activity, width, height, portrait);
        write(dir, "w2-polish-chat-" + (portrait ? "portrait-" : "") + phase + ".png", draw(panel.getRootView()));
        noteSizes(panel, activity.findViewById(android.R.id.content), dir, phase, portrait, width, height, metrics.densityDpi);
        ImageButton mic = findMic(panel);
        int expected = Math.round(48 * metrics.density);
        assertTrue(mic != null);
        assertEquals(expected, mic.getWidth());
        assertEquals(expected, mic.getHeight());
        Button close = findButton(panel, "Close");
        assertTrue(close != null);
        assertTrue(close.getLeft() >= mic.getRight() + Math.round(8 * metrics.density) - 1);
        if (portrait) {
            TextChatPanel orion = orionChat(activity, width, height);
            write(dir, "w2-polish-chat-orion-" + phase + ".png", draw(orion.getRootView()));
        }
    }

    private static TextChatPanel chatPanel(Context context, int width, int height, boolean portrait) {
        return chatPanel(context, width, height, portrait, "Nova", BallColors.WHITE);
    }

    private static TextChatPanel chatPanel(Context context, int width, int height, boolean portrait, String name, int color) {
        FrameLayout stage = new FrameLayout(context);
        stage.setBackgroundColor(Color.BLACK);
        TextChatPanel panel = new TextChatPanel(context, name, color, "", new TextChatPanel.Actions() {
            public void send(String message) {}
            public void draft(String message) {}
            public void close() {}
        });
        float density = context.getResources().getDisplayMetrics().density;
        int panelWidth = portrait ? width - Math.round(48 * density) : Math.min(Math.round(460 * density), Math.round(width * .40f));
        int top = portrait ? Math.round(height * .36f) : Math.round(24 * density);
        int panelHeight = Math.max(Math.round(190 * density), height - top - Math.round(24 * density));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(panelWidth, panelHeight, Gravity.TOP | Gravity.RIGHT);
        params.topMargin = top;
        params.rightMargin = Math.round(24 * density);
        stage.addView(panel, params);
        layout(stage, width, height);
        return panel;
    }

    private static TextChatPanel orionChat(Context context, int width, int height) {
        TextChatPanel panel = chatPanel(context, width, height, true, "Orion", BotIdentity.color("Orion"));
        panel.setStatus("Waiting on the bridge for Orion's latest reply about the tablet layout", false);
        panel.setHistory("You: The portrait chat is tight. Can you keep the mic off the Close button and still show a full reply?\n\n"
            + "Orion: The header mic is 48 dp, and Close sits 8 dp to its right so the outlines no longer touch. "
            + "This reply wraps across the portrait column, with a second sentence so the bubble is a real message rather than a one-word stub.");
        layout(panel.getRootView(), width, height);
        return panel;
    }

    private static Bitmap wall(Context context, int width, int height) throws Exception {
        AmbientWall wall = new AmbientWall(context);
        layout(wall, width, height);
        Field avatars = AmbientWall.class.getDeclaredField("avatars");
        avatars.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String, SolidAvatar> ready = (Map<String, SolidAvatar>) avatars.get(wall);
        Set<String> shapes = new HashSet<String>();
        for (String name : BotIdentity.NAMES) shapes.add(BotIdentity.shape(name));
        for (String shape : shapes) ready.put(shape, new SolidAvatar(context, shape));
        HashSet<String> working = new HashSet<String>();
        working.add("Sage");
        HashSet<String> unread = new HashSet<String>();
        unread.add("Nova");
        HashMap<String, String> lines = new HashMap<String, String>();
        lines.put("Sage", "Checking the reply");
        lines.put("Nova", "New reply");
        wall.setVisibility(View.VISIBLE);
        wall.update(lines, working, unread);
        layout(wall, width, height);
        openWallEyes(wall);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.BLACK);
        Method onDraw = AmbientWall.class.getDeclaredMethod("onDraw", Canvas.class);
        onDraw.setAccessible(true);
        onDraw.invoke(wall, canvas);
        return bitmap;
    }

    /** The drawer starts translated off screen and slides in. Captures should show the settled panel. */
    private static void showOffscreenSlides(View view) {
        view.setTranslationX(0);
        view.setAlpha(1);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) showOffscreenSlides(group.getChildAt(i));
        }
    }

    private static void noteSizes(View chat, View drawerRoot, File dir, String phase, boolean portrait, int width, int height, int dpi) throws Exception {
        StringBuilder note = new StringBuilder();
        note.append("phase=").append(phase).append(" portrait=").append(portrait)
            .append(" px=").append(width).append("x").append(height).append(" dpi=").append(dpi).append('\n');
        ImageButton mic = findMic(chat);
        note.append("chatMic=").append(mic == null ? "none" : mic.getWidth() + "x" + mic.getHeight()).append('\n');
        List<SeekBar> sliders = new ArrayList<>();
        collect(drawerRoot, SeekBar.class, sliders);
        for (SeekBar slider : sliders) {
            note.append("slider ").append(slider.getContentDescription()).append(" state=")
                .append(slider.getStateDescription()).append(" progress=").append(slider.getProgress()).append('\n');
        }
        File file = new File(dir, "w2-polish-notes-" + (portrait ? "portrait-" : "") + phase + ".txt");
        java.io.FileWriter writer = new java.io.FileWriter(file);
        writer.write(note.toString());
        writer.close();
    }

    private static ImageButton findMic(View root) {
        List<ImageButton> buttons = new ArrayList<>();
        collect(root, ImageButton.class, buttons);
        for (ImageButton button : buttons) if ("Voice navigation".contentEquals(button.getContentDescription())) return button;
        return null;
    }

    /** The first frame is a blink. Step each pose until the eyes are open ovals. */
    private static void openBotEyes(View root) throws Exception {
        List<BotView> bots = new ArrayList<BotView>();
        collect(root, BotView.class, bots);
        Field motion = BotView.class.getDeclaredField("motion");
        motion.setAccessible(true);
        Field lineup = BotView.class.getDeclaredField("lineup");
        lineup.setAccessible(true);
        for (BotView bot : bots) {
            openEyes((Motion) motion.get(bot));
            BotView[] row = (BotView[]) lineup.get(bot);
            if (row == null) continue;
            for (BotView item : row) openEyes((Motion) motion.get(item));
        }
    }

    @SuppressWarnings("unchecked")
    private static void openWallEyes(AmbientWall wall) throws Exception {
        Field field = AmbientWall.class.getDeclaredField("motions");
        field.setAccessible(true);
        Map<String, Motion> motions = (Map<String, Motion>) field.get(wall);
        for (Motion motion : motions.values()) openEyes(motion);
    }

    private static void openEyes(Motion motion) {
        double kept = motion.intensity;
        if (kept <= 0) motion.intensity = 1;
        motion.seed(1.4);
        motion.step(0.02);
        for (int i = 0; i < 40 && motion.blink() < 0.9; i++) motion.step(0.05);
        motion.intensity = kept;
    }

    private static void clickDescription(View view, String description) {
        CharSequence current = view.getContentDescription();
        if (current != null && description.contentEquals(current)) {
            view.performClick();
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) clickDescription(group.getChildAt(i), description);
        }
    }

    private static String textOf(View root, String prefix) {
        TextView view = findText(root, prefix);
        assertTrue(view != null);
        return String.valueOf(view.getText());
    }

    private static TextView findText(View view, String prefix) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && value.toString().startsWith(prefix)) return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), prefix);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** The ambient note sits under the stage sliders. Scroll it into the drawer viewport. */
    private static void scrollToText(View root, String prefix) {
        TextView target = findText(root, prefix);
        if (target == null) return;
        int y = 0;
        View current = target;
        while (current != null && !(current instanceof ScrollView)) {
            y += current.getTop();
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        if (current instanceof ScrollView) ((ScrollView) current).scrollTo(0, Math.max(0, y - 48));
    }

    private static Button findButton(View root, String label) {
        List<Button> buttons = new ArrayList<Button>();
        collect(root, Button.class, buttons);
        for (Button button : buttons) if (label.contentEquals(button.getText())) return button;
        return null;
    }

    private static void clickText(View view, String text) {
        if (view instanceof android.widget.Button && text.contentEquals(((android.widget.Button) view).getText())) {
            view.performClick();
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) clickText(group.getChildAt(i), text);
        }
    }

    private static <T> void collect(View view, Class<T> type, List<T> into) {
        if (type.isInstance(view)) into.add(type.cast(view));
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), type, into);
        }
    }

    private static void layout(View view, int width, int height) {
        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
        view.measure(widthSpec, heightSpec);
        view.layout(0, 0, width, height);
    }

    private static Bitmap draw(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static void write(File dir, String name, Bitmap bitmap) throws Exception {
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
    }
}

package dev.grokhome;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Docks the Computer sheet and the setup panel so the bot stage stays visible.
 * Landscape leaves the sheet's right inset only, which is 729 dp of stage on a
 * 1205 dp screen (460 dp sheet). Portrait anchors a bottom sheet at most 70% tall.
 * The docked radius never grows past the undocked single-view size, and the
 * crown stays at least 16 dp below the bot chooser.
 */
final class StageSheet {
    private StageSheet() {}

    static int sheetWidth(int rootWidth, boolean portrait, float density) {
        if (portrait) return Math.max(dp(density, 200), rootWidth - dp(density, 32));
        return Math.min(dp(density, 460), Math.max(dp(density, 280), rootWidth - dp(density, 32)));
    }

    /** Width of the landscape stage, the area left of the sheet. */
    static int landscapeStageWidth(int rootWidth, int sheetWidth, float density) {
        return Math.max(dp(density, 200), rootWidth - sheetWidth - dp(density, 16));
    }

    /** Undocked single-view radius. Docking must not grow past this. */
    static float singleViewRadius(int rootWidth, int rootHeight, float stageScale) {
        boolean portrait = rootHeight > rootWidth;
        float widthFactor = portrait ? 0.31f : 0.157f;
        return Math.min(rootWidth * widthFactor, rootHeight * 0.31f) * stageScale;
    }

    /**
     * Radius and center that keep the crown at or below {@code crownLine} and the
     * body at least {@code gap} above {@code statusTop}. Returns {radius, centerY}.
     */
    static float[] placement(float cap, float naturalCenter, float statusTop, float crownLine, float gap) {
        float rise = BotView.CROWN_RISE;
        float radius = cap;
        float room = statusTop - gap;
        if (room > crownLine + gap) {
            float maxRadius = (room - crownLine) / (rise + 1f);
            if (maxRadius < radius) radius = Math.max(gap, maxRadius);
        }
        float center = Math.max(naturalCenter, crownLine + rise * radius);
        if (statusTop > 0 && center + radius + gap > statusTop) {
            float fitted = (statusTop - gap - crownLine) / (rise + 1f);
            if (fitted < radius) radius = Math.max(gap, fitted);
            center = crownLine + rise * radius;
        }
        return new float[]{radius, center};
    }

    static void dock(View root, View bot, View status, View controls, View sheet) {
        int rootWidth = root.getWidth();
        int rootHeight = root.getHeight();
        if (rootWidth <= 0 || rootHeight <= 0 || bot == null || status == null || sheet == null) return;
        float density = root.getResources().getDisplayMetrics().density;
        if (rootHeight > rootWidth) dockPortrait(rootWidth, rootHeight, density, bot, status, controls, sheet);
        else dockLandscape(rootWidth, rootHeight, density, bot, status, controls, sheet);
    }

    static void restore(View root, View bot, View status, View controls) {
        if (bot instanceof BotView) ((BotView) bot).clearDockPlacement();
        if (bot == null || status == null) return;
        FrameLayout.LayoutParams botParams = frame(bot);
        botParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        botParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        botParams.gravity = Gravity.TOP | Gravity.LEFT;
        botParams.setMargins(0, 0, 0, 0);
        bot.setLayoutParams(botParams);
        FrameLayout.LayoutParams statusParams = frame(status);
        statusParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        statusParams.gravity = Gravity.BOTTOM;
        statusParams.topMargin = 0;
        int height = root == null ? 0 : root.getHeight();
        float density = root == null ? 1f : root.getResources().getDisplayMetrics().density;
        int rowHeight = controls != null && controls.getHeight() > 0 ? controls.getHeight() : dp(density, HomeBand.ROW_HEIGHT_DP);
        HomeBand.Placement band = height > 0 ? HomeBand.place(height, rowHeight, 0, density) : null;
        statusParams.bottomMargin = band != null ? band.statusBottom : dp(density, 108);
        status.setLayoutParams(statusParams);
        if (controls != null) {
            FrameLayout.LayoutParams controlParams = frame(controls);
            controlParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            controlParams.height = dp(density, HomeBand.ROW_HEIGHT_DP);
            controlParams.gravity = Gravity.BOTTOM;
            controlParams.bottomMargin = band != null ? band.rowBottom : dp(density, HomeBand.ROW_BOTTOM_DP);
            controls.setLayoutParams(controlParams);
        }
    }

    private static void dockLandscape(int rootWidth, int rootHeight, float density, View bot, View status, View controls, View sheet) {
        int sheetW = sheetWidth(rootWidth, false, density);
        int stage = landscapeStageWidth(rootWidth, sheetW, density);
        FrameLayout.LayoutParams sheetParams = frame(sheet);
        sheetParams.width = sheetW;
        sheetParams.height = Math.max(dp(density, 200), rootHeight - dp(density, 32));
        sheetParams.gravity = Gravity.TOP | Gravity.RIGHT;
        sheetParams.setMargins(0, dp(density, 16), dp(density, 16), dp(density, 16));
        sheet.setLayoutParams(sheetParams);
        FrameLayout.LayoutParams botParams = frame(bot);
        botParams.width = stage;
        botParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        botParams.gravity = Gravity.TOP | Gravity.LEFT;
        botParams.setMargins(0, 0, 0, 0);
        bot.setLayoutParams(botParams);
        int statusH = statusHeight(status, density);
        FrameLayout.LayoutParams statusParams = frame(status);
        statusParams.width = stage;
        statusParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
        statusParams.topMargin = 0;
        status.setLayoutParams(statusParams);
        place(bot, rootHeight, rootWidth, rootHeight, rootHeight - statusParams.bottomMargin - statusH, density);
        if (controls != null) {
            FrameLayout.LayoutParams controlParams = frame(controls);
            controlParams.width = stage;
            controlParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
            controls.setLayoutParams(controlParams);
        }
    }

    private static void dockPortrait(int rootWidth, int rootHeight, float density, View bot, View status, View controls, View sheet) {
        int sheetW = sheetWidth(rootWidth, true, density);
        int maxHeight = Math.round(rootHeight * 0.70f);
        sheet.measure(View.MeasureSpec.makeMeasureSpec(sheetW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
        int sheetH = Math.max(dp(density, 160), Math.min(maxHeight, sheet.getMeasuredHeight()));
        FrameLayout.LayoutParams sheetParams = frame(sheet);
        sheetParams.width = sheetW;
        sheetParams.height = sheetH;
        sheetParams.gravity = Gravity.BOTTOM;
        sheetParams.setMargins(dp(density, 16), 0, dp(density, 16), dp(density, 16));
        sheet.setLayoutParams(sheetParams);
        int stageH = Math.max(dp(density, 190), rootHeight - sheetH - dp(density, 16));
        FrameLayout.LayoutParams botParams = frame(bot);
        botParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        botParams.height = stageH;
        botParams.gravity = Gravity.TOP | Gravity.LEFT;
        botParams.setMargins(0, 0, 0, 0);
        bot.setLayoutParams(botParams);
        int statusH = statusHeight(status, density);
        int statusTop = Math.max(dp(density, 88), stageH - statusH - dp(density, 8));
        FrameLayout.LayoutParams statusParams = frame(status);
        statusParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        statusParams.gravity = Gravity.TOP | Gravity.LEFT;
        statusParams.bottomMargin = 0;
        statusParams.topMargin = statusTop;
        status.setLayoutParams(statusParams);
        place(bot, stageH, rootWidth, rootHeight, statusTop, density);
        if (controls != null) {
            FrameLayout.LayoutParams controlParams = frame(controls);
            controlParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            controlParams.gravity = Gravity.BOTTOM;
            controls.setLayoutParams(controlParams);
        }
    }

    private static void place(View bot, int viewHeight, int rootWidth, int rootHeight, int statusTop, float density) {
        if (!(bot instanceof BotView)) return;
        BotView avatar = (BotView) bot;
        float cap = singleViewRadius(rootWidth, rootHeight, avatar.stageScale());
        float natural = viewHeight * 0.40f;
        float[] placed = placement(cap, natural, statusTop, dp(density, 88), dp(density, 16));
        avatar.setDockPlacement(placed[0], placed[1]);
    }

    private static int statusHeight(View status, float density) {
        if (status.getHeight() > 0) return status.getHeight();
        ViewGroup.LayoutParams params = status.getLayoutParams();
        if (params != null && params.height > 0) return params.height;
        return dp(density, 72);
    }

    private static FrameLayout.LayoutParams frame(View view) {
        ViewGroup.LayoutParams current = view.getLayoutParams();
        if (current instanceof FrameLayout.LayoutParams) return (FrameLayout.LayoutParams) current;
        FrameLayout.LayoutParams created = new FrameLayout.LayoutParams(0, 0);
        view.setLayoutParams(created);
        return created;
    }

    private static int dp(float density, float value) {
        return Math.round(value * density);
    }
}

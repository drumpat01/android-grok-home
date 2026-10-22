package dev.grokhome;

/**
 * Single-view stage math shared by {@link BotView} and the layout tests.
 * Fractions are the same in pixels and dp. Callers pass floats only; nothing here allocates.
 */
final class StagePlacement {
    static final float SINGLE_Y = 0.38f;
    static final float SINGLE_SCALE = 0.75f;
    static final float LANDSCAPE_R_CAP = 0.30f;
    /** Mesh tip of CrownRenderer at peak 1, in radii above the center. */
    static final float CROWN_TIP = 1.285f;
    /**
     * How far the idle bob lifts that tip, in radii. The body bob is 0.035 and crown tilt
     * adds a little more; the emulator peak rose about 13 dp at the old 0.8 scale (0.087 radii).
     */
    static final float BOB_UP = 0.087f;
    static final float CHOOSER_BOTTOM_DP = 72f;
    /** Clearance from the chooser bottom to the crown tip at the top of the bob. */
    static final float CROWN_GAP_DP = 16f;
    /** Spotlight ellipse in radii, from drawSpotlight's circle(0, 1.22, 1.85) scaled by 0.24. */
    static final float SPOT_TOP = 1.22f - 1.85f * 0.24f;
    static final float SPOT_BOTTOM = 1.22f + 1.85f * 0.24f;
    static final float IDLE_BLOCK_DP = 60f;
    static final float TASK_BLOCK_DP = 78f;

    private StagePlacement() {}

    static float baseRadius(float width, float height, boolean chat, boolean portrait, boolean primary, float stageSize) {
        float r = Math.min(width * (chat ? .27f : portrait ? .31f : .157f), height * (chat ? (primary ? .24f : .28f) : .31f)) * stageSize;
        if (!chat && !portrait) r = Math.min(r, height * LANDSCAPE_R_CAP);
        return r;
    }

    static float unliftedCenter(float height, boolean chat) { return height * (chat ? .46f : .40f); }

    static float placedCenter(float height, float stageY, float shift) {
        return stageY + (height * SINGLE_Y - stageY) * shift;
    }

    /**
     * Scale toward 0.75 while the status block is visible. Extra caps run only when a status top
     * is known, so a unit canvas can request the plain scale by passing statusTop &lt;= 0.
     */
    static float placedRadius(float singleR, float shift, float centerY, float statusTop, float density) {
        float drawR = singleR * (1f - (1f - SINGLE_SCALE) * shift);
        if (shift <= .001f || statusTop <= 0f || density <= 0f) return drawR;
        float crownLimit = (centerY - (CHOOSER_BOTTOM_DP + CROWN_GAP_DP) * density) / (CROWN_TIP + BOB_UP);
        if (crownLimit > 1f) drawR = Math.min(drawR, crownLimit);
        float room = (statusTop - 1f - centerY) / SPOT_BOTTOM;
        if (room > 1f) drawR = Math.min(drawR, room);
        return drawR > 1f ? drawR : 1f;
    }

    static float spotlightTop(float centerY, float radius) { return centerY + SPOT_TOP * radius; }
    static float spotlightBottom(float centerY, float radius) { return centerY + SPOT_BOTTOM * radius; }
    static float crownTop(float centerY, float radius) { return centerY - CROWN_TIP * radius; }
    /** Crown mesh tip at the highest point of the idle bob. */
    static float crownTopAtPeak(float centerY, float radius) { return centerY - (CROWN_TIP + BOB_UP) * radius; }
}

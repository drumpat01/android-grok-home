package dev.grokhome;

/**
 * Rigid state pose for one bot. Hop, shift, and yaw for everyone.
 * Roll and pitch are written only for Max. No scale.
 *
 * <p>Ember: call {@link #fill} and read the fields, or {@link #chipX(String)} and
 * {@link #chipY(String)} for the hand chip anchor in radii. Pass intensity
 * already in the 0 to 1.5 slider range. Values above 1 are capped.
 * {@code slotted} false, {@code reduced} true, or {@code quiet} true select the static form.
 */
final class StagePose {
    static final long DONE_MS = 1800L;
    static final long RAISE_MS = 2400L;
    static final long NUDGE_MS = 800L;
    static final long LOWER_MS = 400L;
    static final long SLOT_WAIT_MS = 4000L;
    static final float HELD_HOP = 0.10f;
    static final float RING_FROZEN = 0.75f;

    double roll;
    double pitch;
    double yaw;
    double hop;
    double shift;
    float glow;
    float ringPhase = RING_FROZEN;
    float chipScale;
    float chipAlpha;
    float spotlightBoost = 1f;
    boolean showChip;
    boolean showRing;
    boolean showGlow;

    void clear() {
        roll = pitch = yaw = hop = shift = 0;
        glow = chipScale = chipAlpha = 0f;
        ringPhase = RING_FROZEN;
        spotlightBoost = 1f;
        showChip = showRing = showGlow = false;
    }

    void fill(BotStageState.State state, BotStageState.OneShot shot, long elapsedMs, String bot,
            float intensity, boolean reduced, boolean quiet, boolean slotted) {
        clear();
        BotStageState.State useState = state == null ? BotStageState.State.IDLE : state;
        BotStageState.OneShot useShot = shot == null ? BotStageState.OneShot.NONE : shot;
        float scale = intensity <= 0f ? 0f : Math.min(1f, intensity);
        boolean frozen = reduced || scale == 0f;
        Style style = style(bot);
        boolean max = BotIdentity.PRIMARY.equals(bot);
        switch (useState) {
            case IDLE -> { }
            case WORKING -> working(elapsedMs, style, max, scale, frozen, quiet, slotted);
            case DONE -> done(useShot, elapsedMs, scale, frozen, quiet, slotted);
            case NEEDS_YOU -> needs(useShot, elapsedMs, style, max, scale, frozen, quiet, slotted);
            default -> {
                BotStageState.State unknown = useState;
                throw new IllegalStateException("Unhandled stage " + unknown);
            }
        }
        if (!max) {
            roll = 0;
            pitch = 0;
        }
    }

    static float chipX(String bot) { return style(bot).chipX; }
    static float chipY(String bot) { return style(bot).chipY; }
    static int accent(String bot, int savedColor) {
        Style style = style(bot);
        return style.accent == 0 ? savedColor : style.accent;
    }

    private void working(long elapsedMs, Style style, boolean max, float scale, boolean frozen, boolean quiet, boolean slotted) {
        showRing = true;
        boolean still = frozen || quiet || !slotted;
        // The ring is the working indicator, so it keeps turning in quiet hours and between carousel slots; only reduced motion stops it.
        ringPhase = frozen ? RING_FROZEN : (float) (RING_FROZEN + (Math.max(0, elapsedMs) / 3200.0) * Math.PI * 2);
        if (frozen) return;
        double lean = still ? 1.0 : easeOutCubic(Math.min(1.0, Math.max(0, elapsedMs) / 400.0));
        yaw = 0.18 * lean * scale;
        if (max) pitch = style.workPitch * lean * scale;
        if (still) return;
        long period = Math.floorMod(Math.max(0, elapsedMs), 3000L);
        if (period < 1200L) {
            double seconds = period / 1000.0;
            hop = 0.015 * Math.abs(Math.sin(2 * Math.PI * 2.5 * seconds)) * scale;
        }
    }

    private void done(BotStageState.OneShot shot, long elapsedMs, float scale, boolean frozen, boolean quiet, boolean slotted) {
        if (shot != BotStageState.OneShot.DONE || frozen || quiet || !slotted || elapsedMs < 0) return;
        long t = elapsedMs;
        hop = doneHop(t) * scale;
        spotlightBoost = (float) doneBoost(t);
    }

    private void needs(BotStageState.OneShot shot, long elapsedMs, Style style, boolean max, float scale,
            boolean frozen, boolean quiet, boolean slotted) {
        showChip = true;
        chipScale = 1f;
        chipAlpha = quiet ? 0.45f : 1f;
        hop = HELD_HOP * (frozen ? 1f : Math.max(scale, frozen ? 1f : scale));
        if (frozen) hop = HELD_HOP;
        else hop = HELD_HOP * (scale == 0f ? 1f : scale);
        showGlow = !quiet && !frozen;
        glow = showGlow ? 0.22f * style.glowScale : 0f;
        if (frozen) {
            showGlow = !quiet;
            glow = showGlow ? 0.22f * style.glowScale : 0f;
            return;
        }
        if (quiet || !slotted || shot == BotStageState.OneShot.NONE) {
            if (!quiet && slotted && shot == BotStageState.OneShot.NONE && elapsedMs >= 0 && elapsedMs < 60_000L) {
                glow = (float) ((0.22 + 0.04 * Math.sin(2 * Math.PI * elapsedMs / 4000.0)) * style.glowScale);
                showGlow = true;
            }
            return;
        }
        switch (shot) {
            case RAISE -> raise(elapsedMs, style, max, scale);
            case NUDGE -> nudge(elapsedMs, style, max, scale);
            case LOWER -> lower(elapsedMs, style, scale);
            case DONE, NONE -> { }
            default -> {
                BotStageState.OneShot unknown = shot;
                throw new IllegalStateException("Unhandled shot " + unknown);
            }
        }
    }

    private void raise(long elapsedMs, Style style, boolean max, float scale) {
        long t = Math.max(0, elapsedMs);
        if (t < 400) hop = lerp(0, 0.12, easeOutCubic(t / 400.0)) * scale;
        else if (t < 500) hop = lerp(0.12, HELD_HOP, (t - 400) / 100.0) * scale;
        else hop = HELD_HOP * scale;
        if (t < 200) {
            chipScale = 0.6f;
            chipAlpha = 0f;
        } else if (t < 500) {
            double u = easeOutCubic((t - 200) / 300.0);
            chipScale = (float) lerp(0.6, 1, u);
            chipAlpha = (float) u;
        } else {
            chipScale = 1f;
            chipAlpha = 1f;
        }
        double sway = t >= 500 && t < 2100 ? Math.sin(2 * Math.PI * (t - 500) / 800.0) : 0;
        applySway(sway, style, max, scale);
        if (t < 500) glow = (float) (lerp(0, 0.32, easeOutCubic(t / 500.0)) * style.glowScale);
        else if (t < 2400) glow = (float) (lerp(0.32, 0.22, (t - 500) / 1900.0) * style.glowScale);
        else glow = 0.22f * style.glowScale;
        showGlow = glow > 0f;
        spotlightBoost = 1.15f;
    }

    private void nudge(long elapsedMs, Style style, boolean max, float scale) {
        long t = Math.max(0, elapsedMs);
        double sway = t > NUDGE_MS ? 0 : Math.sin(2 * Math.PI * t / (double) NUDGE_MS);
        hop = HELD_HOP * scale;
        applySway(sway, style, max, scale);
        double breath = t > NUDGE_MS ? 0.22 : 0.22 + 0.04 * Math.sin(Math.PI * t / (double) NUDGE_MS);
        glow = (float) (breath * style.glowScale);
        showGlow = true;
        chipScale = 1f;
        chipAlpha = 1f;
    }

    private void lower(long elapsedMs, Style style, float scale) {
        long t = Math.max(0, elapsedMs);
        double u = easeInOut(Math.min(1.0, t / (double) LOWER_MS));
        hop = HELD_HOP * (1 - u) * scale;
        chipAlpha = t >= 200 ? 0f : (float) (1 - t / 200.0);
        chipScale = 1f;
        showChip = chipAlpha > 0.02f;
        glow = t >= 300 ? 0f : (float) (0.22 * (1 - t / 300.0) * style.glowScale);
        showGlow = glow > 0.001f;
        spotlightBoost = (float) (1 + 0.15 * (1 - u));
    }

    private void applySway(double sway, Style style, boolean max, float scale) {
        yaw += style.swayYaw * sway * scale;
        if (max) roll = style.swayRoll * sway * scale;
        else shift = style.swayShift * sway * scale;
    }

    static double doneHop(long t) {
        if (t <= 0) return 0;
        if (t < 120) return lerp(0, -0.02, easeIn(t / 120.0));
        if (t < 600) return lerp(-0.02, 0.14, easeOutCubic((t - 120) / 480.0));
        if (t < 1000) return lerp(0.14, 0, easeIn((t - 600) / 400.0));
        if (t < 1250) return Math.sin(Math.PI * (t - 1000) / 250.0) * 0.03;
        return 0;
    }

    static double doneBoost(long t) {
        if (t <= 0) return 1;
        if (t <= 600) return lerp(1, 1.25, t / 600.0);
        if (t >= 1400) return 1;
        return lerp(1.25, 1, (t - 600) / 800.0);
    }

    private static double easeIn(double u) {
        u = clamp01(u);
        return u * u;
    }

    private static double easeOutCubic(double u) {
        u = clamp01(u);
        double v = 1 - u;
        return 1 - v * v * v;
    }

    private static double easeInOut(double u) {
        u = clamp01(u);
        return u < 0.5 ? 2 * u * u : 1 - Math.pow(-2 * u + 2, 2) / 2;
    }

    private static double lerp(double a, double b, double u) {
        return a + (b - a) * clamp01(u);
    }

    private static double clamp01(double u) {
        if (u < 0) return 0;
        if (u > 1) return 1;
        return u;
    }

    static final class Style {
        final float swayShift;
        final float swayYaw;
        final float swayRoll;
        final float workPitch;
        final float chipX;
        final float chipY;
        final float glowScale;
        final int accent;

        Style(float swayShift, float swayYaw, float swayRoll, float workPitch, float chipX, float chipY, float glowScale, int accent) {
            this.swayShift = swayShift;
            this.swayYaw = swayYaw;
            this.swayRoll = swayRoll;
            this.workPitch = workPitch;
            this.chipX = chipX;
            this.chipY = chipY;
            this.glowScale = glowScale;
            this.accent = accent;
        }
    }

    private static final Style MAX = new Style(0f, 0.12f, 0.045f, 0.04f, 1.02f, -0.78f, 1f, 0);
    private static final Style JD = new Style(0.035f, 0.10f, 0f, 0f, 0.73f, -0.99f, 1f, 0);
    private static final Style KIT = new Style(0.035f, 0.12f, 0f, 0f, 0.78f, -1.00f, 0.8f, 0xFFD1D7DD);
    private static final Style IRIS = new Style(0.035f, 0.12f, 0f, 0f, 0.78f, -0.93f, 1f, 0);
    private static final Style Pixel = new Style(0.025f, 0.08f, 0f, 0f, 0.86f, -0.70f, 1f, 0);
    private static final Style IVY = new Style(0.035f, 0.12f, 0f, 0f, 0.74f, -0.80f, 1f, 0);
    private static final Style CEDAR = new Style(0.035f, 0.12f, 0f, 0f, 0.73f, -0.99f, 1f, 0);
    private static final Style JUNIPER = new Style(0.035f, 0.12f, 0f, 0f, 0.77f, -0.86f, 1f, 0);
    private static final Style SAGE = new Style(0.035f, 0.12f, 0f, 0f, 0.71f, -0.91f, 1f, 0);
    private static final Style ORION = new Style(0.025f, 0.10f, 0f, 0f, 0.74f, -1.12f, 1.2f, 0);
    private static final Style OTHER = new Style(0.035f, 0.12f, 0f, 0f, 0.78f, -0.95f, 1f, 0);

    static Style style(String bot) {
        if (bot == null) return OTHER;
        if (bot.equals(BotIdentity.PRIMARY)) return MAX;
        return switch (bot) {
            case "Scout" -> JD;
            case "Nova" -> KIT;
            case "Echo" -> IRIS;
            case "Pixel" -> Pixel;
            case "Ivy" -> IVY;
            case "Cedar" -> CEDAR;
            case "Juniper" -> JUNIPER;
            case "Sage" -> SAGE;
            case "Orion" -> ORION;
            default -> OTHER;
        };
    }
}

package dev.grokhome;

import android.graphics.RadialGradient;
import android.graphics.Shader;

/** Bot tint for a needs-you tile. Nova and Orion follow the spec's glow exceptions. */
final class NeedsYouTint {
    static final int KIT_MARK = 0xffd1d7dd;
    static final int CHIP_FILL = 0xff191c20;
    static final float HELD_GLOW = 0.22f;
    private static final float INNER = 0.4f / 1.75f;

    static int mark(String bot, int saved) {
        int color = "Nova".equals(bot) ? KIT_MARK : saved;
        return color | 0xff000000;
    }

    static float glowAlpha(String bot) {
        float scale = "Nova".equals(bot) ? 0.8f : "Orion".equals(bot) ? 1.2f : 1f;
        return HELD_GLOW * scale;
    }

    static int border(String bot, int saved, boolean quiet) {
        int color = mark(bot, saved);
        return quiet ? scaleAlpha(color, 0.5f) : color;
    }

    static int chipFill(boolean quiet) {
        return quiet ? scaleAlpha(CHIP_FILL, 0.45f) : CHIP_FILL;
    }

    static int chipRing(String bot, int saved, boolean quiet) {
        int color = mark(bot, saved);
        return quiet ? scaleAlpha(color, 0.45f) : color;
    }

    static int chipGlyph(boolean quiet) {
        return quiet ? scaleAlpha(0xffffffff, 0.45f) : 0xffffffff;
    }

    /** Unit radial glow. Scale it to 1.75r and move it onto the body. */
    static RadialGradient glow(String bot, int saved) {
        float alpha = glowAlpha(bot);
        int tint = mark(bot, saved);
        int full = scaleAlpha(tint, alpha);
        int mid = scaleAlpha(tint, alpha * 0.45f);
        int clear = tint & 0x00ffffff;
        return new RadialGradient(0f, 0f, 1f,
            new int[]{full, full, mid, clear},
            new float[]{0f, INNER, 0.6f, 1f},
            Shader.TileMode.CLAMP);
    }

    static int scaleAlpha(int color, float alpha) {
        int channel = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (color & 0x00ffffff) | (channel << 24);
    }

    private NeedsYouTint() {}
}

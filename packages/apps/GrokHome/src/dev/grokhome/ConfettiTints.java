package dev.grokhome;

import android.graphics.Color;

/**
 * Confetti uses only that bot's tints.
 * Nova's spec grays are #D1D7DD, #EEF1F4, #FFFFFF, and #9AA4AE. Those light
 * crumbs disappear on the white body, so each one is deepened toward the
 * darkest stop of Nova's own shade. A slightly darker edge from that same
 * gray keeps the rim readable on the body and on the black background.
 */
final class ConfettiTints {
    static final int[] KIT_PALETTE = {0xFFD1D7DD, 0xFFEEF1F4, 0xFFFFFFFF, 0xFF9AA4AE};
    /** Last stop of the white body gradient in BotView. */
    static final int KIT_BODY = 0xFF78828D;
    private static final float KIT_DEEPEN = 0.85f;
    private static final float KIT_EDGE_DEEPEN = 0.22f;

    private ConfettiTints() {}

    static int[] of(String bot, int color) {
        if ("Nova".equals(bot)) {
            int[] out = new int[KIT_PALETTE.length];
            for (int i = 0; i < out.length; i++) out[i] = mix(KIT_PALETTE[i], KIT_BODY, KIT_DEEPEN);
            return out;
        }
        int base = color == 0 ? BotIdentity.color(bot) : color;
        return new int[] {base, mix(base, Color.WHITE, .45f), Color.WHITE, mix(base, Color.BLACK, .15f)};
    }

    /** Outline color, or 0 when the bot draws crumbs with no extra edge. */
    static int edge(String bot) {
        if (!"Nova".equals(bot)) return 0;
        return mix(KIT_BODY, Color.BLACK, KIT_EDGE_DEEPEN);
    }

    static int mix(int from, int to, float amount) {
        float keep = 1f - amount;
        return Color.rgb(
                Math.round(Color.red(from) * keep + Color.red(to) * amount),
                Math.round(Color.green(from) * keep + Color.green(to) * amount),
                Math.round(Color.blue(from) * keep + Color.blue(to) * amount));
    }
}

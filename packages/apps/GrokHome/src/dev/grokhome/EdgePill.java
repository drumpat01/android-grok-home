package dev.grokhome;

import java.util.List;

/** Off-screen needs-you pill. Same height and fill as the bot chooser. */
final class EdgePill {
    static final float HEIGHT_DP = 48f;
    static final float MARGIN_DP = 24f;
    static final float MAX_WIDTH_DP = 360f;
    static final float RADIUS_DP = 28f;
    static final float TEXT_SP = 15f;
    static final float PAD_DP = 18f;
    static final int FILL = 0xff17191c;
    static final int STROKE = 0xff6b7079;
    static final String CHEVRON = "\u203A";

    interface Width {
        float of(String text);
    }

    static String label(List<String> bots, float maxTextWidthPx, Width width) {
        if (bots == null || bots.isEmpty()) return "";
        if (bots.size() == 1) {
            String single = bots.get(0) + " needs you " + CHEVRON;
            if (width == null || width.of(single) <= maxTextWidthPx) return single;
            return "1 bot needs you " + CHEVRON;
        }
        return bots.size() + " bots need you " + CHEVRON;
    }

    static float textLimit(float density) {
        return Math.max(1f, (MAX_WIDTH_DP - PAD_DP * 2f) * density);
    }

    private EdgePill() {}
}

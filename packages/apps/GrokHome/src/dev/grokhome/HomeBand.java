package dev.grokhome;

/**
 * Bottom band shared with kit/w2-task. One pass places the status block and the
 * single 88 dp row (Apps, an action, the mic, an action, Settings). The status
 * block sits 16 dp above that row. Task actions replace Type and History in the
 * same row through {@link HomeControls#setWings}. {@code taskRowPx} stays 0 for
 * the r3 mock; pass a height only if a second strip has to lift the status.
 */
final class HomeBand {
    /** 76 dp mic plus a 6 dp halo on each side, so the glow is inside the row. */
    static final int ROW_HEIGHT_DP = 88;
    static final int ROW_BOTTOM_DP = 12;
    static final int ROW_SIDE_DP = 24;
    /** Gap from the top of the row to the bottom of the status block. */
    static final int BAND_GAP_DP = 16;
    static final int CROWN_CLEARANCE_DP = 16;
    static final int CHOOSER_TOP_DP = 24;
    static final int CHOOSER_HEIGHT_DP = 48;
    /** Single-view center. Echo, Oct 17 7:15 PM CT. */
    static final float STAGE_CENTER_FRACTION = 0.37f;
    /** Single-view radius scale. Echo review of PR #7: 0.78 leaves a 16 dp crown gap. */
    static final float SINGLE_BOT_SCALE = 0.78f;
    /** Crown tip in unit space at a full peak. Matches CrownRenderer. */
    static final float CROWN_TIP_Y = -1.285f;

    static final class Placement {
        final int statusBottom;
        final int rowBottom;
        final int rowHeight;
        final int gapAboveRow;

        Placement(int statusBottom, int rowBottom, int rowHeight, int gapAboveRow) {
            this.statusBottom = statusBottom;
            this.rowBottom = rowBottom;
            this.rowHeight = rowHeight;
            this.gapAboveRow = gapAboveRow;
        }
    }

    private HomeBand() {}

    static Placement place(int heightPx, int rowHeightPx, int taskRowPx, float density) {
        int rowBottom = Math.round(ROW_BOTTOM_DP * density);
        int rowHeight = rowHeightPx > 0 ? rowHeightPx : Math.round(ROW_HEIGHT_DP * density);
        int gap = Math.round(BAND_GAP_DP * density);
        int extra = Math.max(0, taskRowPx);
        int statusBottom = rowBottom + rowHeight + gap + extra;
        return new Placement(statusBottom, rowBottom, rowHeight, gap + extra);
    }

    /** Distance from the chooser bottom to the crown tip. Positive means clear air. */
    static float crownChooserGapDp(float stageYDp, float radiusDp) {
        float crownTop = stageYDp + CROWN_TIP_Y * radiusDp;
        float chooserBottom = CHOOSER_TOP_DP + CHOOSER_HEIGHT_DP;
        return crownTop - chooserBottom;
    }
}

package dev.grokhome;

import org.junit.Test;
import static org.junit.Assert.*;

/** Q11 at the design frame, 1205×753 dp, plus the lifted 0.38 H placement. */
public class StagePlacementTest {
    private static final float W = 1205f, H = 753f;

    @Test public void legacyAnchorSitsInsideTheSpotlight() {
        float radius = StagePlacement.baseRadius(W, H, false, false, true, 1f);
        float center = StagePlacement.unliftedCenter(H, false);
        assertEquals(189.185f, radius, .05f);
        assertEquals(301.2f, center, .05f);
        float spotBottom = StagePlacement.spotlightBottom(center, radius);
        float statusBottom = H - Math.round(H * .22f);
        float statusTop = statusBottom - 72f;
        assertTrue("Unlifted glow reaches the old status block: bottom " + spotBottom + " top " + statusTop, spotBottom > statusTop);
        assertEquals(616f, spotBottom, 1f);
        assertEquals(515f, statusTop, 1f);
    }

    @Test public void liftedStageClearsStatusAtDesignSizeAndAt125Percent() {
        assertClears(1f, false);
        assertClears(1.25f, false);
    }

    @Test public void portraitTaskClearsTheStatusBlock() {
        float width = 753f, height = 1205f;
        float radius = StagePlacement.baseRadius(width, height, false, true, true, 1f);
        assertEquals(width * .31f, radius, .05f);
        float center = StagePlacement.placedCenter(height, StagePlacement.unliftedCenter(height, false), 1f);
        int statusTop = statusTop(Math.round(height), Math.round(StagePlacement.TASK_BLOCK_DP), 1f);
        float draw = StagePlacement.placedRadius(radius, 1f, center, statusTop, 1f);
        assertTrue(StagePlacement.spotlightBottom(center, draw) < statusTop);
    }

    @Test public void crownPeakClearsSixteenDpAtDesignSizeAndAt125Percent() {
        float center = StagePlacement.placedCenter(H, StagePlacement.unliftedCenter(H, false), 1f);
        int statusTop = statusTop(Math.round(H), Math.round(StagePlacement.TASK_BLOCK_DP), 1f);
        float draw = placed(W, H, false, 1f, 1f, center, statusTop);
        float gap = (StagePlacement.crownTopAtPeak(center, draw) - StagePlacement.CHOOSER_BOTTOM_DP);
        assertTrue("Crown peak gap " + gap, gap >= StagePlacement.CROWN_GAP_DP - .05f);
        float larger = StagePlacement.baseRadius(W, H, false, false, true, 1.25f);
        assertEquals(H * StagePlacement.LANDSCAPE_R_CAP, larger, .05f);
        float capped = StagePlacement.placedRadius(larger, 1f, center, statusTop, 1f);
        assertTrue(capped < larger * StagePlacement.SINGLE_SCALE);
        float peak = StagePlacement.crownTopAtPeak(center, capped) - StagePlacement.CHOOSER_BOTTOM_DP;
        assertTrue("125% peak gap " + peak, peak >= StagePlacement.CROWN_GAP_DP - .05f);
    }

    @Test public void crownPeakClearsSixteenDpAt240And340Dpi() {
        float[] densities = {240f / 160f, 340f / 160f};
        for (float density : densities) {
            assertPeakClearance(1205f, 753f, false, density);
            assertPeakClearance(753f, 1205f, true, density);
        }
    }

    @Test public void homeBandIsTheOnlyRowUnderTheStatus() {
        float density = 340f / 160f;
        int height = Math.round(H * density);
        int row = Math.round(HomeBand.ROW_HEIGHT_DP * density);
        HomeBand.Placement place = HomeBand.place(height, row, 0, density);
        assertEquals(row, place.rowHeight);
        assertEquals(Math.round(HomeBand.ROW_BOTTOM_DP * density), place.rowBottom);
        assertEquals(Math.round(HomeBand.BAND_GAP_DP * density), place.gapAboveRow);
        assertEquals(place.rowBottom + place.rowHeight + place.gapAboveRow, place.statusBottom);
        assertEquals(statusTop(height, Math.round(StagePlacement.TASK_BLOCK_DP * density), density), height - place.statusBottom - Math.round(StagePlacement.TASK_BLOCK_DP * density));
    }

    private static float placed(float width, float height, boolean portrait, float stageSize, float density, float center, float statusTop) {
        float radius = StagePlacement.baseRadius(width, height, false, portrait, true, stageSize);
        return StagePlacement.placedRadius(radius, 1f, center, statusTop, density);
    }

    private static void assertPeakClearance(float widthDp, float heightDp, boolean portrait, float density) {
        int width = Math.round(widthDp * density);
        int height = Math.round(heightDp * density);
        float center = StagePlacement.placedCenter(height, StagePlacement.unliftedCenter(height, false), 1f);
        int top = statusTop(height, Math.round(StagePlacement.TASK_BLOCK_DP * density), density);
        float draw = placed(width, height, portrait, 1f, density, center, top);
        float chooser = StagePlacement.CHOOSER_BOTTOM_DP * density;
        float gapDp = (StagePlacement.crownTopAtPeak(center, draw) - chooser) / density;
        assertTrue(portrait + " @" + density + " gap " + gapDp + " r " + (draw / density), gapDp >= 16f - .05f);
        assertTrue(StagePlacement.spotlightBottom(center, draw) < top);
    }

    private static void assertClears(float stageSize, boolean portrait) {
        float radius = StagePlacement.baseRadius(W, H, false, portrait, true, stageSize);
        float center = StagePlacement.placedCenter(H, StagePlacement.unliftedCenter(H, false), 1f);
        int top = statusTop(Math.round(H), Math.round(StagePlacement.TASK_BLOCK_DP), 1f);
        float draw = StagePlacement.placedRadius(radius, 1f, center, top, 1f);
        float spot = StagePlacement.spotlightBottom(center, draw);
        assertTrue("stage " + stageSize + " spot " + spot + " status " + top, spot < top - 1f);
        int idleTop = statusTop(Math.round(H), Math.round(StagePlacement.IDLE_BLOCK_DP), 1f);
        float idleDraw = StagePlacement.placedRadius(radius, 1f, center, idleTop, 1f);
        assertTrue(StagePlacement.spotlightBottom(center, idleDraw) < idleTop);
    }

    /** Status top from the merged 88 dp row: 12 dp margin, 16 dp gap, no second strip. */
    private static int statusTop(int heightPx, int statusHeightPx, float density) {
        int row = Math.round(HomeBand.ROW_HEIGHT_DP * density);
        HomeBand.Placement place = HomeBand.place(heightPx, row, 0, density);
        return heightPx - place.statusBottom - Math.max(0, statusHeightPx);
    }
}

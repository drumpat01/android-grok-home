package dev.grokhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class HomeBandTest {
    @Test public void statusSitsSixteenDpAboveThe88dpRow() {
        float density = 340f / 160f;
        int height = Math.round(753 * density);
        int row = Math.round(HomeBand.ROW_HEIGHT_DP * density);
        HomeBand.Placement band = HomeBand.place(height, row, 0, density);
        assertEquals(Math.round(HomeBand.ROW_BOTTOM_DP * density), band.rowBottom);
        assertEquals(row, band.rowHeight);
        assertEquals(Math.round(HomeBand.BAND_GAP_DP * density), band.gapAboveRow);
        assertEquals(band.rowBottom + band.rowHeight + band.gapAboveRow, band.statusBottom);
        assertEquals(16, HomeBand.BAND_GAP_DP);
    }

    @Test public void anExtraTaskStripLiftsTheStatusBlock() {
        float density = 2.125f;
        int height = Math.round(753 * density);
        int row = Math.round(HomeBand.ROW_HEIGHT_DP * density);
        HomeBand.Placement idle = HomeBand.place(height, row, 0, density);
        HomeBand.Placement lifted = HomeBand.place(height, row, Math.round(56 * density), density);
        assertTrue(lifted.statusBottom > idle.statusBottom);
        assertTrue(lifted.gapAboveRow >= Math.round(HomeBand.BAND_GAP_DP * density));
    }

    @Test public void scale078ClearsTheCrownBySixteenDp() {
        assertEquals(0.78f, HomeBand.SINGLE_BOT_SCALE, 0f);
        assertEquals(16, HomeBand.CROWN_CLEARANCE_DP);
        float height = 753f;
        float radius = Math.min(1205f * 0.157f, height * 0.31f) * HomeBand.SINGLE_BOT_SCALE;
        float stageY = height * HomeBand.STAGE_CENTER_FRACTION;
        float gap = HomeBand.crownChooserGapDp(stageY, radius);
        assertTrue("crown/chooser gap is " + gap + " dp", gap >= HomeBand.CROWN_CLEARANCE_DP);
    }
}

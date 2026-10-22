package dev.grokhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class StageSheetTest {
    @Test public void landscapeStageIsThe729DpLeftOfA460DpSheet() {
        float density = 340f / 160f;
        int root = Math.round(1205 * density);
        int sheet = StageSheet.sheetWidth(root, false, density);
        int stage = StageSheet.landscapeStageWidth(root, sheet, density);
        assertEquals(Math.round(460 * density), sheet);
        assertEquals(Math.round(729 * density), stage);
        assertTrue(stage + sheet <= root);
    }

    @Test public void dockedRadiusStaysUnderTheSingleViewSizeAndClearsTheChooser() {
        float density = 340f / 160f;
        int rootW = Math.round(1205 * density);
        int rootH = Math.round(753 * density);
        float cap = StageSheet.singleViewRadius(rootW, rootH, 1f);
        assertEquals(189f * density, cap, 2f);
        int statusTop = rootH - Math.round(rootH * .22f) - Math.round(72 * density);
        float[] placed = StageSheet.placement(cap, rootH * 0.40f, statusTop, Math.round(88 * density), Math.round(16 * density));
        assertTrue(placed[0] <= cap + 0.5f);
        float crown = placed[1] - BotView.CROWN_RISE * placed[0];
        assertTrue(crown >= Math.round(88 * density) - 1);
        assertTrue(placed[1] + placed[0] + Math.round(16 * density) <= statusTop + 1);
    }

    @Test public void portraitSheetIsFullWidthMinus32Dp() {
        float density = 340f / 160f;
        int root = Math.round(753 * density);
        assertEquals(root - Math.round(32 * density), StageSheet.sheetWidth(root, true, density));
    }
}

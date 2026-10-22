package dev.grokhome;

import android.graphics.Path;

/** Static hand glyph for a needs-you tile chip. 24dp grid, filled. */
final class WallHandChip {
    static Path glyph() {
        Path path = new Path();
        StageMarks.writeHand(path);
        return path;
    }

    private WallHandChip() {}
}

package dev.grokhome;

import android.graphics.Path;

/** Crescent drawn beside the quiet-hours note. 16 unit grid, disk with a circular cut. */
final class QuietMark {
    static Path moon() {
        Path disk = new Path();
        disk.addCircle(6.6f, 8f, 6.1f, Path.Direction.CW);
        Path cut = new Path();
        cut.addCircle(10.2f, 6.6f, 5.5f, Path.Direction.CW);
        disk.op(cut, Path.Op.DIFFERENCE);
        return disk;
    }

    private QuietMark() {}
}

package dev.grokhome;

import java.util.Arrays;

/**
 * Debug overview scroll. The carousel stays centered on the midpoint of the
 * flagged bots. Slot spacing is normally 0.185 of the width, which leaves Nova
 * and Juniper clipped on a 2560-wide tablet once the ring and the body are
 * counted. This tightens spacing, then scale, only as far as the marks need
 * to sit inside a 48 dp margin. The normal carousel does not read these scales.
 */
final class DebugOverviewFocus {
    static final int TABLET_WIDTH = 2560;
    static final int TABLET_HEIGHT = 1600;
    static final float TABLET_DENSITY = 340f / 160f;
    static final float MARGIN_DP = 48f;
    static final float SLOT_FRACTION = 0.185f;
    static final float MIN_GAP_SCALE = 0.45f;
    static final float MIN_SIZE_SCALE = 0.50f;
    /** The view skips a bot once its slot depth reaches this, and fades it out. */
    static final float DRAW_LIMIT = 2.8f;

    private DebugOverviewFocus() {}

    static final class Fit {
        final float slot;
        final float gapScale;
        final float sizeScale;
        final String needsYou;
        final String working;
        final String done;

        Fit(float slot, float gapScale, float sizeScale, String needsYou, String working, String done) {
            this.slot = slot;
            this.gapScale = gapScale;
            this.sizeScale = sizeScale;
            this.needsYou = needsYou == null ? "" : needsYou;
            this.working = working == null ? "" : working;
            this.done = done == null ? "" : done;
        }

        boolean keeps(String name) {
            return name != null && (name.equals(needsYou) || name.equals(working) || name.equals(done));
        }
    }

    /**
     * @return null when none of the names are bots
     */
    static Fit fit(String needsYou, String working, String done, int widthPx, int heightPx, float density, float stageSize) {
        String[] ordered = new String[3];
        int[] role = new int[3];
        int count = 0;
        count = push(ordered, role, count, needsYou, 0);
        count = push(ordered, role, count, working, 1);
        count = push(ordered, role, count, done, 2);
        if (count == 0 || widthPx <= 0 || heightPx <= 0) return null;
        String[] chosen = new String[count];
        int[] chosenRole = new int[count];
        int kept = 0;
        for (int i = 0; i < count; i++) {
            chosen[kept] = ordered[i];
            chosenRole[kept] = role[i];
            if (solve(chosen, chosenRole, kept + 1, widthPx, heightPx, density, stageSize) != null) kept++;
        }
        if (kept == 0) return null;
        return solve(chosen, chosenRole, kept, widthPx, heightPx, density, stageSize);
    }

    /** Horizontal span of the body plus the ring or the hand chip, in pixels. */
    static void writeSpan(float slotX, float shiftPx, float radius, boolean ring, boolean chip, float chipScale, float chipX,
            float density, float[] left, float[] right, int index) {
        float bodyLeft = slotX + shiftPx - radius;
        float bodyRight = slotX + shiftPx + radius;
        float markLeft = bodyLeft;
        float markRight = bodyRight;
        if (ring && radius > 1f) {
            float stroke = Math.max(3f * density, 0.03f * radius);
            float half = 1.18f * radius + stroke * 0.5f;
            markLeft = Math.min(markLeft, slotX - half);
            markRight = Math.max(markRight, slotX + half);
        }
        if (chip && chipScale > 0.02f && radius > 1f) {
            float visual = clamp(0.42f * radius, 40f * density, 56f * density) * clamp(chipScale, 0f, 1.5f);
            float rad = visual * 0.5f;
            float cx = slotX + shiftPx + chipX * radius;
            markLeft = Math.min(markLeft, cx - rad);
            markRight = Math.max(markRight, cx + rad);
        }
        left[index] = markLeft;
        right[index] = markRight;
    }

    static float marginPx(float density) {
        return MARGIN_DP * (density <= 0f ? 1f : density);
    }

    private static Fit solve(String[] ordered, int[] role, int count, int width, int height, float density, float stageSize) {
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) ids[i] = BotIdentity.index(ordered[i]);
        float slot = midpoint(ids, count, BotIdentity.NAMES.length);
        if (!withinDraw(ids, count, slot)) return null;
        float margin = marginPx(density);
        if (places(ordered, role, count, slot, 1f, 1f, width, height, density, stageSize, margin)) {
            return pack(ordered, role, count, slot, 1f, 1f);
        }
        if (places(ordered, role, count, slot, MIN_GAP_SCALE, 1f, width, height, density, stageSize, margin)) {
            float lo = MIN_GAP_SCALE;
            float hi = 1f;
            for (int step = 0; step < 24; step++) {
                float mid = (lo + hi) * 0.5f;
                if (places(ordered, role, count, slot, mid, 1f, width, height, density, stageSize, margin)) lo = mid;
                else hi = mid;
            }
            return pack(ordered, role, count, slot, lo, 1f);
        }
        if (!places(ordered, role, count, slot, MIN_GAP_SCALE, MIN_SIZE_SCALE, width, height, density, stageSize, margin)) return null;
        float lo = MIN_SIZE_SCALE;
        float hi = 1f;
        for (int step = 0; step < 24; step++) {
            float mid = (lo + hi) * 0.5f;
            if (places(ordered, role, count, slot, MIN_GAP_SCALE, mid, width, height, density, stageSize, margin)) lo = mid;
            else hi = mid;
        }
        return pack(ordered, role, count, slot, MIN_GAP_SCALE, lo);
    }

    private static boolean withinDraw(int[] ids, int count, float slot) {
        int roster = BotIdentity.NAMES.length;
        for (int i = 0; i < count; i++) {
            if (Math.abs(offset(slot, ids[i], roster)) >= DRAW_LIMIT) return false;
        }
        return true;
    }

    private static boolean places(String[] ordered, int[] role, int count, float slot, float gapScale, float sizeScale,
            int width, int height, float density, float stageSize, float margin) {
        int roster = BotIdentity.NAMES.length;
        float gap = width * SLOT_FRACTION * gapScale;
        float[] left = new float[1];
        float[] right = new float[1];
        for (int i = 0; i < count; i++) {
            int index = BotIdentity.index(ordered[i]);
            float depth = Math.abs(offset(slot, index, roster));
            float radius = radius(width, height, stageSize, sizeScale, depth);
            float x = width * 0.5f + offset(slot, index, roster) * gap;
            boolean ring = role[i] == 1;
            boolean chip = role[i] == 0;
            float chipScale = chip ? 1f : 0f;
            float chipX = StagePose.chipX(ordered[i]);
            writeSpan(x, 0f, radius, ring, chip, chipScale, chipX, density, left, right, 0);
            if (left[0] < margin + 1f || right[0] > width - margin - 1f) return false;
        }
        return true;
    }

    static float radius(int width, int height, float stageSize, float sizeScale, float depth) {
        float small = Math.min(width * 0.083f, height * 0.21f) * stageSize * sizeScale;
        float depthScale = 1f - clamp(depth, 0f, 2.5f) * 0.17f;
        return small * depthScale;
    }

    private static Fit pack(String[] ordered, int[] role, int count, float slot, float gapScale, float sizeScale) {
        String needs = "";
        String working = "";
        String done = "";
        for (int i = 0; i < count; i++) {
            if (role[i] == 0) needs = ordered[i];
            else if (role[i] == 1) working = ordered[i];
            else done = ordered[i];
        }
        return new Fit(slot, gapScale, sizeScale, needs, working, done);
    }

    private static float offset(float slot, int index, int roster) {
        float delta = index - wrap(slot, roster);
        if (delta > roster / 2f) delta -= roster;
        if (delta < -roster / 2f) delta += roster;
        return delta;
    }

    private static float midpoint(int[] ids, int count, int roster) {
        if (count <= 1) return ids[0];
        int[] sorted = new int[count];
        System.arraycopy(ids, 0, sorted, 0, count);
        Arrays.sort(sorted);
        int bestGap = -1;
        int arcStart = sorted[0];
        for (int i = 0; i < count; i++) {
            int here = sorted[i];
            int next = sorted[(i + 1) % count];
            int gap = (next - here + roster) % roster;
            if (gap > bestGap) {
                bestGap = gap;
                arcStart = next;
            }
        }
        return wrap(arcStart + (roster - bestGap) / 2f, roster);
    }

    private static float wrap(float value, int roster) {
        float mod = value % roster;
        if (mod < 0f) mod += roster;
        return mod;
    }

    private static int push(String[] names, int[] role, int count, String name, int kind) {
        int index = BotIdentity.index(name);
        if (index < 0) return count;
        for (int i = 0; i < count; i++) {
            if (BotIdentity.index(names[i]) == index) return count;
        }
        names[count] = name;
        role[count] = kind;
        return count + 1;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

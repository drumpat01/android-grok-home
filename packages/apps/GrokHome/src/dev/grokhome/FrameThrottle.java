package dev.grokhome;

/**
 * Schedules ambient frames. While something is still moving and system animations
 * are on, the next frame starts one 30 fps step after this frame's start, so time
 * spent drawing comes out of that budget. Otherwise the caller draws once and stops.
 * The clock is injectable so tests can step time without a device.
 */
public final class FrameThrottle {
    /** 1000/30 in integer milliseconds. This is the cap Nova's rule uses. */
    public static final long FRAME_MS = 33;
    /** Do not schedule another frame. */
    public static final long STOP = -1;

    public interface Clock {
        long uptimeMillis();
    }

    private final Clock clock;
    private long frameStartMs = Long.MIN_VALUE;
    private long lastStartMs = Long.MIN_VALUE;

    public FrameThrottle(Clock clock) {
        if (clock == null) throw new IllegalArgumentException("clock");
        this.clock = clock;
    }

    /** Call at the start of a frame, before the draw work. */
    public void beginFrame() {
        frameStartMs = clock.uptimeMillis();
    }

    /**
     * Call after a frame has been painted.
     *
     * @param moving some tile still has a non-zero motion intensity
     * @param animatorsEnabled {@code ValueAnimator.areAnimatorsEnabled()}
     * @return milliseconds until the next frame, or {@link #STOP}
     */
    public long afterDraw(boolean moving, boolean animatorsEnabled) {
        long now = clock.uptimeMillis();
        long start = frameStartMs == Long.MIN_VALUE ? now : frameStartMs;
        long previous = lastStartMs;
        lastStartMs = start;
        frameStartMs = Long.MIN_VALUE;
        if (!animatorsEnabled || !moving) return STOP;
        long next = start + FRAME_MS;
        if (previous != Long.MIN_VALUE && now < previous + FRAME_MS) next = previous + FRAME_MS;
        long delay = next - now;
        return delay < 0 ? 0 : delay;
    }
}

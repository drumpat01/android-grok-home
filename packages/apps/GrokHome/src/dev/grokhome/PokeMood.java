package dev.grokhome;

/** Counts rapid taps. A pause longer than {@link #GAP_MS} starts over. */
final class PokeMood {
    static final long GAP_MS = 500;
    private int count;
    private long last = Long.MIN_VALUE;

    boolean poke(long now) {
        if (last != Long.MIN_VALUE && now - last > GAP_MS) count = 0;
        last = now;
        if (++count < 10) return false;
        count = 0;
        return true;
    }
}

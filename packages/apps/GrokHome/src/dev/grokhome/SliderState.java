package dev.grokhome;

/** The value TalkBack should announce for a stage slider, in the same units as the caption. */
public final class SliderState {
    private SliderState() {}

    public static String description(int value) {
        return value + " percent";
    }
}

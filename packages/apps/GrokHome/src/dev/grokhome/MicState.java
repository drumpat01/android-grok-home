package dev.grokhome;

/**
 * The home mic has one job per gesture.
 * Tap while armed, muted, or switched off starts a command. Tap while recording stops it.
 * Long-press toggles the temporary mute. That mute ends on the next mic tap, on leaving
 * ambient, when the screen turns off, and in onStop. It is not kept for the next visit.
 * A wake cannot end it, because a muted tablet is not running the wake grammar.
 */
final class MicState {
    enum Phase { ARMED, RECORDING, MUTED, SWITCH_OFF, NO_PERMISSION }

    enum Tap { START, STOP }

    private MicState() {}

    static Phase phase(boolean recording, boolean switchOn, boolean temporaryMute, boolean permission) {
        if (recording) return Phase.RECORDING;
        if (!permission) return Phase.NO_PERMISSION;
        if (!switchOn) return Phase.SWITCH_OFF;
        if (temporaryMute) return Phase.MUTED;
        return Phase.ARMED;
    }

    /** Idle status subtitle. The mic state lives here, under the title. */
    static String subtitle(Phase phase) {
        switch (phase) {
            case ARMED: return VoiceCopy.ARMED_SUBTITLE;
            case RECORDING: return VoiceCopy.RECORDING_SUBTITLE;
            case MUTED: return VoiceCopy.MUTED_SUBTITLE;
            case SWITCH_OFF: return VoiceCopy.SWITCH_OFF_SUBTITLE;
            case NO_PERMISSION: return VoiceCopy.PERMISSION_SUBTITLE;
            default:
                Phase unknown = phase;
                throw new IllegalStateException("Unhandled mic phase " + unknown);
        }
    }

    /** TalkBack name for the mic button. */
    static String spokenLabel(Phase phase, String bot) {
        String name = bot == null || bot.isEmpty() ? "your bot" : bot;
        switch (phase) {
            case ARMED: return "Talk to " + name + ". Listening for Hey Bots";
            case RECORDING: return "Stop listening";
            case MUTED: return "Talk to " + name + ". Mic off for now";
            case SWITCH_OFF: return "Talk to " + name + ". " + VoiceCopy.SWITCH_OFF_SUBTITLE;
            case NO_PERMISSION: return "Mic access is off. Open settings";
            default:
                Phase unknown = phase;
                throw new IllegalStateException("Unhandled mic phase " + unknown);
        }
    }

    /** Name of the long-press accessibility action. */
    static String longPressLabel(boolean temporaryMute) {
        return temporaryMute ? VoiceCopy.UNMUTE_ACTION : VoiceCopy.MUTE_ACTION;
    }

    static Tap onTap(Phase phase) {
        switch (phase) {
            case ARMED:
            case MUTED:
            case SWITCH_OFF:
            case NO_PERMISSION:
                return Tap.START;
            case RECORDING:
                return Tap.STOP;
            default:
                Phase unknown = phase;
                throw new IllegalStateException("Unhandled mic phase " + unknown);
        }
    }

    /** Long-press flips the temporary mute. Returns the muted value after the press. */
    static boolean mutedAfterLongPress(boolean muted) {
        return !muted;
    }
}

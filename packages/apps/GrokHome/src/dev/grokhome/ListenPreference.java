package dev.grokhome;

import android.content.SharedPreferences;

/**
 * The Settings switch is auto-listen: wake stays armed while Home is open.
 * Mute is a separate temporary override. It clears on the next mic tap and
 * when ambient closes. A wake does not clear it. {@code auto_listen} used to
 * open a command session as well; the switch no longer does that.
 */
final class ListenPreference {
    static final String KEY = "listen_hey_bots";
    static final String MIGRATED = "listen_hey_bots_v1";

    private ListenPreference() {}

    /**
     * Wake was armed whenever the mic was not muted, even with auto_listen left
     * off. The migrated switch keeps that. A saved mute becomes the switch off,
     * so the choice survives, and the temporary mute starts clear.
     */
    static boolean migratedSwitch(boolean muted) {
        return !muted;
    }

    static void migrate(SharedPreferences prefs) {
        if (prefs.getBoolean(MIGRATED, false)) return;
        boolean on = migratedSwitch(prefs.getBoolean("muted", false));
        prefs.edit().putBoolean(KEY, on).putBoolean(MIGRATED, true).putBoolean("muted", false).apply();
    }

    static void writeSwitch(SharedPreferences prefs, boolean on) {
        prefs.edit().putBoolean(KEY, on).putBoolean("auto_listen", on).putBoolean(MIGRATED, true).putBoolean("muted", false).apply();
    }

    static void setMuted(SharedPreferences prefs, boolean muted) {
        prefs.edit().putBoolean("muted", muted).apply();
    }

    static boolean switchOn(SharedPreferences prefs) {
        if (prefs.contains(KEY)) return prefs.getBoolean(KEY, false);
        return prefs.getBoolean("auto_listen", false);
    }

    /** Wake may arm only while the switch is on and the temporary mute is clear. */
    static boolean wakeEnabled(SharedPreferences prefs) {
        return switchOn(prefs) && !prefs.getBoolean("muted", false);
    }
}

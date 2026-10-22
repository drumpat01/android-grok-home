package dev.grokhome;

import android.content.SharedPreferences;

/**
 * Done confetti fires once per reply token.
 * Skip when the working-to-idle edge is more than 60 seconds old.
 * The token is stored either way so a later poll cannot fire it.
 */
final class ConfettiGate {
    static final long MAX_EDGE_AGE_MS = 60_000L;
    static final String PREF_PREFIX = "celebrated_token_";

    interface Store {
        String get(String bot);
        void put(String bot, String token);
    }

    static final class Decision {
        final boolean celebrate;
        final String storeToken;

        Decision(boolean celebrate, String storeToken) {
            this.celebrate = celebrate;
            this.storeToken = storeToken;
        }
    }

    private ConfettiGate() {}

    static String key(String bot) {
        return PREF_PREFIX + (bot == null ? "" : bot);
    }

    static Store prefs(SharedPreferences prefs) {
        return new Store() {
            @Override public String get(String bot) {
                return prefs.getString(key(bot), "");
            }
            @Override public void put(String bot, String token) {
                if (bot == null || token == null) return;
                prefs.edit().putString(key(bot), token).apply();
            }
        };
    }

    /**
     * @param baseline first snapshot for this bot. Never celebrates.
     * @param edgeAgeMs age of the working-to-idle edge. Values above 60s skip.
     */
    static Decision decide(String stored, String token, long edgeAgeMs, boolean stale, boolean baseline) {
        if (baseline || stale || token == null || token.isEmpty()) return new Decision(false, null);
        if (token.equals(stored)) return new Decision(false, null);
        if (edgeAgeMs > MAX_EDGE_AGE_MS) return new Decision(false, token);
        return new Decision(true, token);
    }
}

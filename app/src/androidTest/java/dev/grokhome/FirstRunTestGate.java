package dev.grokhome;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;

/** Keeps the first-run panel off screens these tests were written against. */
final class FirstRunTestGate {
    private final SharedPreferences prefs;
    private final boolean seeded;

    FirstRunTestGate() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE);
        seeded = !prefs.contains("voice_server") && !prefs.getBoolean("setup_started", false);
        if (seeded) {
            prefs.edit().putBoolean("setup_started", true).putInt("setup_done", 0xF).putBoolean("setup_dismissed", true).commit();
        }
    }

    void restore() {
        if (seeded) prefs.edit().remove("setup_started").remove("setup_done").remove("setup_skipped").remove("setup_dismissed").commit();
    }
}

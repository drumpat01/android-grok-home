package dev.grokhome;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;

/** Device-local quiet hours. The window wraps past midnight. Defaults are 9:00 PM to 7:00 AM. */
final class QuietHours {
    static final String ENABLED = "quiet_enabled";
    static final String START = "quiet_start_min";
    static final String END = "quiet_end_min";
    static final int DEFAULT_START = 1260;
    static final int DEFAULT_END = 420;
    /** Settings switch default. A missing pref dims the wall when the clock is inside the window. */
    static final boolean SETTINGS_DEFAULT_ENABLED = true;

    static boolean active(boolean enabled, int startMinute, int endMinute, int minuteOfDay) {
        if (!enabled) return false;
        int start = Math.floorMod(startMinute, 1440);
        int end = Math.floorMod(endMinute, 1440);
        int minute = Math.floorMod(minuteOfDay, 1440);
        if (start == end) return false;
        if (start < end) return minute >= start && minute < end;
        return minute >= start || minute < end;
    }

    static int minuteOfDay(long epochMillis, ZoneId zone) {
        ZoneId id = zone == null ? ZoneId.systemDefault() : zone;
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), id).get(ChronoField.MINUTE_OF_DAY);
    }

    static String note(int endMinute) {
        int minute = Math.floorMod(endMinute, 1440);
        int hour = minute / 60;
        int mins = minute % 60;
        int hour12 = hour % 12;
        if (hour12 == 0) hour12 = 12;
        String suffix = hour < 12 ? "AM" : "PM";
        return "Quiet hours until " + hour12 + ":" + (mins < 10 ? "0" : "") + mins + " " + suffix;
    }

    private QuietHours() {}
}

package dev.grokhome;

/** Selected-bot task chrome. Pure state: no views and no Android types. */
final class TaskProgress {
    enum Phase { IDLE, WORKING, STOPPING, STOPPED, STOP_FAILED, FINISHED, NEEDS_YOU }

    static final long STOPPED_HOLD_MS = 4000;
    static final long FINISHED_HOLD_MS = 6000;
    static final long SKEW_MS = 5000;

    /** A real origin is a tablet or gateway start. A notice is only when this tablet first saw a native run. */
    static final class Clock {
        final boolean real;
        final long originMillis;
        Clock(boolean real, long originMillis) { this.real = real; this.originMillis = originMillis; }
    }

    private Phase phase = Phase.IDLE;
    private String bot = "";
    private String detail = "";
    private boolean needsYou;
    private long holdUntil;
    private Clock clock = new Clock(false, 0);

    Phase phase() { return phase; }
    String bot() { return bot; }
    String detail() { return detail; }
    boolean needsYou() { return needsYou; }
    boolean realClock() { return clock.real; }
    long originMillis() { return clock.originMillis; }

    boolean showsTask() {
        return switch (phase) {
            case WORKING, STOPPING, STOP_FAILED, FINISHED -> true;
            case IDLE, STOPPED, NEEDS_YOU -> false;
        };
    }

    /** The elapsed clock runs only while the task is still going. Stopping hides it. */
    boolean showsTimer() { return phase == Phase.WORKING; }

    void setClock(Clock value) { if (value != null) clock = value; }
    void clearClock() { clock = new Clock(false, 0); }

    void idle() {
        phase = Phase.IDLE;
        needsYou = false;
        detail = "";
        holdUntil = 0;
    }

    /** A stop already in flight keeps the spinner. A new run replaces a finished or stopped hold. */
    void working(String name, String taskDetail) {
        if (phase == Phase.STOPPING || phase == Phase.STOP_FAILED) return;
        phase = Phase.WORKING;
        bot = name == null ? "" : name;
        detail = taskDetail == null ? "" : taskDetail;
        needsYou = false;
        holdUntil = 0;
    }

    void stopping(String name, String taskDetail) {
        phase = Phase.STOPPING;
        bot = name == null ? "" : name;
        detail = taskDetail == null ? "" : taskDetail;
        needsYou = false;
        holdUntil = 0;
    }

    void stopped(String name, long now) {
        phase = Phase.STOPPED;
        bot = name == null ? "" : name;
        detail = "";
        needsYou = false;
        holdUntil = now + STOPPED_HOLD_MS;
    }

    void stopFailed(String name, String taskDetail) {
        phase = Phase.STOP_FAILED;
        bot = name == null ? "" : name;
        detail = taskDetail == null ? "" : taskDetail;
        needsYou = false;
        holdUntil = 0;
    }

    /** Ignore a finish that arrives while stop is still in flight. */
    void finished(String name, String taskDetail, long now) {
        if (phase == Phase.STOPPING) return;
        phase = Phase.FINISHED;
        bot = name == null ? "" : name;
        detail = taskDetail == null ? "" : taskDetail;
        needsYou = false;
        holdUntil = now + FINISHED_HOLD_MS;
    }

    /** Wave 3 draws the card. This slice only remembers that the bot needs you. */
    void handoffNeedsYou(String name) {
        phase = Phase.NEEDS_YOU;
        bot = name == null ? "" : name;
        detail = "";
        needsYou = true;
        holdUntil = 0;
    }

    void dismiss() { idle(); }

    /** @return true when a 4s or 6s hold expired into idle */
    boolean tick(long now) {
        if (phase == Phase.STOPPED && now >= holdUntil) { idle(); return true; }
        if (phase == Phase.FINISHED && now >= holdUntil) { idle(); return true; }
        return false;
    }

    String title() {
        return switch (phase) {
            case WORKING -> bot + " is working";
            case STOPPING -> "Stopping " + bot + "'s task";
            case STOPPED -> "Task stopped";
            case STOP_FAILED -> "Couldn't stop the task";
            case FINISHED -> bot + " finished";
            case IDLE, NEEDS_YOU -> "";
        };
    }

    String clockLabel(long now) {
        long elapsed = clock.originMillis > 0 ? Math.max(0, now - clock.originMillis) : 0;
        if (clock.real && clock.originMillis > 0) return clockText(elapsed);
        return seenText(elapsed);
    }

    boolean seen() { return !clock.real || clock.originMillis <= 0; }

    static String clockText(long elapsedMillis) {
        long seconds = Math.max(0, elapsedMillis / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return hours + ":" + pad(minutes) + ":" + pad(secs);
        return minutes + ":" + pad(secs);
    }

    static String seenText(long elapsedMillis) {
        long minutes = Math.max(0, elapsedMillis / 60000);
        if (minutes < 1) return "Seen just now";
        if (minutes == 1) return "Seen 1 min ago";
        if (minutes < 60) return "Seen " + minutes + " min ago";
        long hours = minutes / 60;
        if (hours == 1) return "Seen 1 hr ago";
        return "Seen " + hours + " hr ago";
    }

    /**
     * Server start wins, then the pref this tablet wrote when it sent the job.
     * Otherwise reuse the notice time. last_run and reply_time are not inputs.
     */
    static Clock resolve(long now, long serverMillis, long storedStart, long storedNotice) {
        if (serverMillis > 0 && serverMillis <= now + SKEW_MS) return new Clock(true, serverMillis);
        if (storedStart > 0 && storedStart <= now + SKEW_MS) return new Clock(true, storedStart);
        long notice = storedNotice > 0 ? storedNotice : now;
        return new Clock(false, notice);
    }

    /**
     * A Seen line must not repeat a request that already finished, stopped, or failed.
     * A different live line, such as a new native note, still shows.
     */
    static String seenDetail(String savedRequest, String retiredRequest, String liveDetail) {
        if (genericProgress(liveDetail)) return "";
        if (sameRequest(liveDetail, savedRequest) || sameRequest(liveDetail, retiredRequest)) return "";
        return liveDetail.trim();
    }

    static boolean sameRequest(String value, String request) {
        if (value == null || request == null) return false;
        String left = value.trim();
        String right = request.trim();
        if (left.isEmpty() || right.isEmpty()) return false;
        String clipped = right.substring(0, Math.min(120, right.length()));
        return left.equals(right) || left.equals(clipped);
    }

    static boolean genericProgress(String value) {
        return value == null || value.isEmpty()
            || value.equals("Waiting for bot reply")
            || value.equals("Finding the right bot")
            || value.equals("Working on your request")
            || value.equals("Stopping…")
            || value.equals("Stopping task…")
            || value.equals("Checking your request")
            || value.equals("Task complete")
            || value.equals("Task stopped")
            || value.equals("Check reply")
            || value.equals("Couldn't stop the task");
    }

    private static String pad(long value) { return value < 10 ? "0" + value : Long.toString(value); }
}

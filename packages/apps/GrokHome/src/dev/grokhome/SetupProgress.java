package dev.grokhome;

/**
 * Four setup steps, persisted as bit masks. An existing install that already
 * saved a computer address never enters first run.
 */
final class SetupProgress {
    static final int MEET = 0;
    static final int CONNECT = 1;
    static final int MIC = 2;
    static final int HOME = 3;
    static final int STEPS = 4;
    private static final int ALL = 0xF;

    final int done;
    final int skipped;
    final boolean dismissed;
    final boolean existingInstall;
    /** True after Set as Home was opened and the role was not held on return. */
    final boolean homeAttempted;

    private SetupProgress(int done, int skipped, boolean dismissed, boolean existingInstall, boolean homeAttempted) {
        this.done = done & ALL;
        this.skipped = (skipped & ALL) & ~this.done;
        this.dismissed = dismissed;
        this.existingInstall = existingInstall;
        this.homeAttempted = homeAttempted;
    }

    static SetupProgress load(boolean savedAddress, boolean started, int done, int skipped, boolean dismissed) {
        return load(savedAddress, started, done, skipped, dismissed, false);
    }

    static SetupProgress load(boolean savedAddress, boolean started, int done, int skipped, boolean dismissed, boolean homeAttempted) {
        if (savedAddress && !started) return new SetupProgress(ALL, 0, true, true, false);
        return new SetupProgress(done, skipped, dismissed, false, homeAttempted);
    }

    boolean stepDone(int step) { return (done & bit(step)) != 0; }
    boolean stepSkipped(int step) { return (skipped & bit(step)) != 0; }

    int doneCount() {
        int count = 0;
        for (int step = 0; step < STEPS; step++) if (stepDone(step)) count++;
        return count;
    }

    /** First step that is neither done nor skipped, or -1. */
    int current() {
        for (int step = 0; step < STEPS; step++) if (!stepDone(step) && !stepSkipped(step)) return step;
        return -1;
    }

    boolean complete() { return done == ALL; }

    /** Fresh and unfinished installs see the panel until Finish. */
    boolean showOnLaunch() { return !existingInstall && !dismissed; }

    /** Settings offers Finish setup while any step is still open. */
    boolean showFinishRow() { return !existingInstall && !complete(); }

    SetupProgress complete(int step) {
        return new SetupProgress(done | bit(step), skipped, dismissed, false, homeAttempted);
    }

    SetupProgress skip(int step) {
        if (stepDone(step)) return this;
        return new SetupProgress(done, skipped | bit(step), dismissed, false, homeAttempted);
    }

    /** A skipped row can be tried again. */
    SetupProgress focus(int step) {
        if ((skipped & bit(step)) == 0) return this;
        return new SetupProgress(done, skipped & ~bit(step), dismissed, false, homeAttempted);
    }

    /** The Home picker was opened. Resume uses this when the role is still not held. */
    SetupProgress attemptHome() {
        if (homeAttempted) return this;
        return new SetupProgress(done, skipped, dismissed, existingInstall, true);
    }

    SetupProgress dismiss() {
        if (dismissed) return this;
        return new SetupProgress(done, skipped, true, existingInstall, homeAttempted);
    }

    SetupProgress reopen() {
        if (!dismissed) return this;
        return new SetupProgress(done, skipped, false, false, homeAttempted);
    }

    /**
     * Home and microphone are checked again on resume. A finished, dismissed
     * setup stays finished so a later role change does not reopen the wizard.
     */
    SetupProgress reconcile(boolean micGranted, boolean homeHeld) {
        if (existingInstall || (dismissed && complete())) return this;
        int next = done;
        next = micGranted ? next | bit(MIC) : next & ~bit(MIC);
        next = homeHeld ? next | bit(HOME) : next & ~bit(HOME);
        if (next == done) return this;
        return new SetupProgress(next, skipped, dismissed, false, homeAttempted);
    }

    private static int bit(int step) {
        if (step < 0 || step >= STEPS) throw new IllegalArgumentException("step");
        return 1 << step;
    }
}

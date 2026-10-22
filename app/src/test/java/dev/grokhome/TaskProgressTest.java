package dev.grokhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class TaskProgressTest {
    @Test public void clockFormatsMinutesAndHours() {
        assertEquals("0:00", TaskProgress.clockText(0));
        assertEquals("0:09", TaskProgress.clockText(9_000));
        assertEquals("2:14", TaskProgress.clockText(134_000));
        assertEquals("59:59", TaskProgress.clockText(3_599_000));
        assertEquals("1:00:00", TaskProgress.clockText(3_600_000));
        assertEquals("1:02:03", TaskProgress.clockText(3_723_000));
    }

    @Test public void seenAgoUsesNoticeTimeNotAFakeTimer() {
        assertEquals("Seen just now", TaskProgress.seenText(0));
        assertEquals("Seen just now", TaskProgress.seenText(59_000));
        assertEquals("Seen 1 min ago", TaskProgress.seenText(60_000));
        assertEquals("Seen 3 min ago", TaskProgress.seenText(180_000));
        assertEquals("Seen 1 hr ago", TaskProgress.seenText(3_600_000));
        assertEquals("Seen 2 hr ago", TaskProgress.seenText(7_200_000));
    }

    @Test public void holdsExpireAndStopIsNotClobbered() {
        TaskProgress progress = new TaskProgress();
        long now = 1_000_000L;
        progress.working("Nova", "Reviewing the new page");
        assertEquals(TaskProgress.Phase.WORKING, progress.phase());
        assertTrue(progress.showsTask());
        assertEquals("Nova is working", progress.title());
        progress.stopping("Nova", "Stopping…");
        assertEquals("Stopping Nova's task", progress.title());
        assertFalse(progress.showsTimer());
        progress.working("Nova", "should not replace the spinner");
        assertEquals(TaskProgress.Phase.STOPPING, progress.phase());
        assertEquals("Stopping Nova's task", progress.title());
        progress.finished("Nova", "too soon", now);
        assertEquals(TaskProgress.Phase.STOPPING, progress.phase());
        progress.stopped("Nova", now);
        assertEquals("Task stopped", progress.title());
        assertFalse(progress.showsTask());
        assertFalse(progress.tick(now + 3_999));
        assertEquals(TaskProgress.Phase.STOPPED, progress.phase());
        assertTrue(progress.tick(now + TaskProgress.STOPPED_HOLD_MS));
        assertEquals(TaskProgress.Phase.IDLE, progress.phase());

        progress.working("Max", "page");
        progress.stopFailed("Max", "Couldn't stop the task");
        assertEquals("Couldn't stop the task", progress.title());
        assertFalse(progress.tick(now + 60_000));
        assertEquals(TaskProgress.Phase.STOP_FAILED, progress.phase());
        progress.working("Max", "still stopping");
        assertEquals(TaskProgress.Phase.STOP_FAILED, progress.phase());

        progress.finished("Nova", "Done", now);
        assertEquals("Nova finished", progress.title());
        assertTrue(progress.showsTask());
        assertFalse(progress.tick(now + 5_999));
        assertTrue(progress.tick(now + TaskProgress.FINISHED_HOLD_MS));
        assertEquals(TaskProgress.Phase.IDLE, progress.phase());
    }

    @Test public void stoppingTitleHidesTheTimer() {
        TaskProgress progress = new TaskProgress();
        progress.setClock(new TaskProgress.Clock(true, 1_000L));
        progress.working("Max", "Reading the changelog");
        assertTrue(progress.showsTimer());
        assertEquals("Max is working", progress.title());
        progress.stopping("Max", "Stopping…");
        assertEquals("Stopping Max's task", progress.title());
        assertFalse(progress.title().contains("-"));
        assertFalse(progress.title().contains("\u2013"));
        assertFalse(progress.title().contains("\u2014"));
        assertFalse(progress.showsTimer());
        assertEquals(TaskProgress.Phase.STOPPING, progress.phase());
    }

    @Test public void seenStateDropsARequestThatAlreadyEnded() {
        assertEquals("", TaskProgress.seenDetail("Check the build status", "", "Check the build status"));
        assertEquals("", TaskProgress.seenDetail("", "Check the build status", "Check the build status"));
        assertEquals("", TaskProgress.seenDetail("Check the build status", "Check the build status", "Waiting for bot reply"));
        assertEquals("Reading the changelog", TaskProgress.seenDetail("", "Check the build status", "Reading the changelog"));
        assertTrue(TaskProgress.sameRequest("Check the build status", "Check the build status"));
    }

    @Test public void needsYouShowsNoNewTaskChrome() {
        TaskProgress progress = new TaskProgress();
        progress.working("Nova", "Reviewing");
        progress.handoffNeedsYou("Nova");
        assertEquals(TaskProgress.Phase.NEEDS_YOU, progress.phase());
        assertTrue(progress.needsYou());
        assertFalse(progress.showsTask());
        assertEquals("", progress.title());
        progress.dismiss();
        assertEquals(TaskProgress.Phase.IDLE, progress.phase());
        assertFalse(progress.needsYou());
    }

    @Test public void resolvePrefersServerStartThenStoredStartThenNotice() {
        long now = 10_000_000L;
        TaskProgress.Clock server = TaskProgress.resolve(now, now - 5_000, now, now);
        assertTrue(server.real);
        assertEquals(now - 5_000, server.originMillis);
        TaskProgress.Clock stored = TaskProgress.resolve(now, 0, now - 8_000, now);
        assertTrue(stored.real);
        assertEquals(now - 8_000, stored.originMillis);
        TaskProgress.Clock notice = TaskProgress.resolve(now, 0, 0, now - 180_000);
        assertFalse(notice.real);
        assertEquals(now - 180_000, notice.originMillis);
        TaskProgress.Clock fresh = TaskProgress.resolve(now, 0, 0, 0);
        assertFalse(fresh.real);
        assertEquals(now, fresh.originMillis);
        // A start more than 5s in the future is not a clock. last_run.at is not passed in.
        TaskProgress.Clock skewed = TaskProgress.resolve(now, now + 6_000, now - 1_000, 0);
        assertTrue(skewed.real);
        assertEquals(now - 1_000, skewed.originMillis);
    }

    @Test public void workingClockLabelFollowsTheResolvedOrigin() {
        TaskProgress progress = new TaskProgress();
        long now = 5_000_000L;
        progress.setClock(new TaskProgress.Clock(true, now - 134_000));
        progress.working("Nova", "Reviewing");
        assertEquals("2:14", progress.clockLabel(now));
        assertFalse(progress.seen());
        progress.setClock(new TaskProgress.Clock(false, now - 180_000));
        assertEquals("Seen 3 min ago", progress.clockLabel(now));
        assertTrue(progress.seen());
    }
}

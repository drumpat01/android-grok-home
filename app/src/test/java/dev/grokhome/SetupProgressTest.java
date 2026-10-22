package dev.grokhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class SetupProgressTest {
    @Test public void savedAddressSkipsFirstRun() {
        SetupProgress setup = SetupProgress.load(true, false, 0, 0, false);
        assertTrue(setup.existingInstall);
        assertTrue(setup.complete());
        assertFalse(setup.showOnLaunch());
        assertFalse(setup.showFinishRow());
    }

    @Test public void freshInstallShowsThePanelAndKeepsSkippedStepsInSettings() {
        SetupProgress setup = SetupProgress.load(false, false, 0, 0, false);
        assertTrue(setup.showOnLaunch());
        assertEquals(0, setup.doneCount());
        assertEquals(SetupProgress.MEET, setup.current());
        setup = setup.complete(SetupProgress.MEET).skip(SetupProgress.CONNECT);
        assertEquals(SetupProgress.MIC, setup.current());
        assertTrue(setup.stepSkipped(SetupProgress.CONNECT));
        setup = setup.dismiss();
        assertFalse(setup.showOnLaunch());
        assertTrue(setup.showFinishRow());
        setup = setup.reopen().focus(SetupProgress.CONNECT);
        assertFalse(setup.stepSkipped(SetupProgress.CONNECT));
        assertEquals(SetupProgress.CONNECT, setup.current());
    }

    @Test public void resumeRechecksMicrophoneAndHomeUntilSetupIsFinished() {
        SetupProgress setup = SetupProgress.load(false, true, 1 << SetupProgress.MEET, 0, false);
        setup = setup.reconcile(true, true);
        assertTrue(setup.stepDone(SetupProgress.MIC));
        assertTrue(setup.stepDone(SetupProgress.HOME));
        assertFalse(setup.stepDone(SetupProgress.CONNECT));
        setup = setup.reconcile(false, false);
        assertFalse(setup.stepDone(SetupProgress.MIC));
        assertFalse(setup.stepDone(SetupProgress.HOME));
        setup = setup.complete(SetupProgress.CONNECT).complete(SetupProgress.MIC).complete(SetupProgress.HOME).dismiss();
        assertTrue(setup.complete());
        assertSame(setup, setup.reconcile(false, false));
    }

    @Test public void homeAttemptStaysUntilTheRoleIsHeld() {
        SetupProgress setup = SetupProgress.load(false, true, 1 << SetupProgress.MEET, 0, false).attemptHome();
        assertTrue(setup.homeAttempted);
        setup = setup.reconcile(true, false);
        assertTrue(setup.homeAttempted);
        assertFalse(setup.stepDone(SetupProgress.HOME));
        setup = setup.reconcile(true, true);
        assertTrue(setup.stepDone(SetupProgress.HOME));
        assertTrue(setup.homeAttempted);
    }

    @Test public void startedInstallWithAnAddressStillResumes() {
        SetupProgress setup = SetupProgress.load(true, true, 1 << SetupProgress.MEET, 0, false);
        assertFalse(setup.existingInstall);
        assertTrue(setup.showOnLaunch());
        assertEquals(SetupProgress.CONNECT, setup.current());
    }
}

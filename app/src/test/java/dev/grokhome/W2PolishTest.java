package dev.grokhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class W2PolishTest {
    @Test public void capsAtThirtyFramesPerSecond() {
        MutableClock clock = new MutableClock();
        FrameThrottle throttle = new FrameThrottle(clock);
        int draws = 0;
        long elapsed = 0;
        while (elapsed < 10_000) {
            draws++;
            long delay = throttle.afterDraw(true, true);
            assertEquals(FrameThrottle.FRAME_MS, delay);
            elapsed += delay;
            clock.now = elapsed;
        }
        double fps = (draws - 1) * 1000.0 / (elapsed - FrameThrottle.FRAME_MS);
        assertEquals(304, draws);
        assertEquals(30.303, fps, 0.01);
    }

    @Test public void drawTimeComesOutOfTheFrameBudget() {
        MutableClock clock = new MutableClock();
        FrameThrottle throttle = new FrameThrottle(clock);
        throttle.beginFrame();
        clock.now = 8;
        assertEquals(25, throttle.afterDraw(true, true));
        clock.now = 33;
        throttle.beginFrame();
        clock.now = 41;
        assertEquals(25, throttle.afterDraw(true, true));
    }

    @Test public void stopsWhenAnimatorsAreDisabled() {
        MutableClock clock = new MutableClock();
        FrameThrottle throttle = new FrameThrottle(clock);
        assertEquals(FrameThrottle.STOP, throttle.afterDraw(true, false));
        clock.now = 10_000;
        assertEquals(FrameThrottle.STOP, throttle.afterDraw(true, false));
    }

    @Test public void earlyDrawWaitsOutTheRemainder() {
        MutableClock clock = new MutableClock();
        FrameThrottle throttle = new FrameThrottle(clock);
        assertEquals(FrameThrottle.FRAME_MS, throttle.afterDraw(true, true));
        clock.now = 10;
        assertEquals(FrameThrottle.FRAME_MS - 10, throttle.afterDraw(true, true));
    }

    @Test public void stopsWhenNothingIsMoving() {
        FrameThrottle throttle = new FrameThrottle(new MutableClock());
        assertEquals(FrameThrottle.STOP, throttle.afterDraw(false, true));
    }

    @Test public void resumesOnTheDrawAfterAStateChange() {
        MutableClock clock = new MutableClock();
        FrameThrottle throttle = new FrameThrottle(clock);
        assertEquals(FrameThrottle.STOP, throttle.afterDraw(true, false));
        clock.now = 1_500;
        assertEquals(FrameThrottle.FRAME_MS, throttle.afterDraw(true, true));
    }

    @Test public void blinkAndSwayStillMoveAtThirtyFps() {
        Motion motion = new Motion();
        motion.intensity = 1;
        motion.step(FrameThrottle.FRAME_MS / 1000.0);
        double previousYaw = motion.yaw;
        double minBlink = motion.blink();
        boolean sawMidBlink = false;
        double largestStep = 0;
        for (int i = 0; i < 30 * 12; i++) {
            motion.step(FrameThrottle.FRAME_MS / 1000.0);
            double blink = motion.blink();
            if (blink < minBlink) minBlink = blink;
            if (blink > 0.2 && blink < 0.8) sawMidBlink = true;
            double step = Math.abs(motion.yaw - previousYaw);
            if (step > largestStep) largestStep = step;
            previousYaw = motion.yaw;
        }
        assertTrue("blink closed to " + minBlink, minBlink < 0.25);
        assertTrue(sawMidBlink);
        assertTrue("sway step " + largestStep, largestStep > 0 && largestStep < 0.08);
    }

    @Test public void zeroIntensityRestsThePose() {
        Motion motion = new Motion();
        motion.intensity = 1;
        for (int i = 0; i < 10; i++) motion.step(0.05);
        assertTrue(Math.abs(motion.yaw) > 0 || Math.abs(motion.roll) > 0);
        motion.intensity = 0;
        motion.step(0);
        assertEquals(0, motion.yaw, 0);
        assertEquals(0, motion.roll, 0);
        assertEquals(0, motion.pitch, 0);
        assertEquals(1, motion.blink(), 0);
    }

    @Test public void speakingHintNamesASphereAndANonSphere() {
        String kitQuiet = BotTalkBack.contentDescription("Nova", "sphere", "White", true, false, false, false, false, false);
        String kitSpeaking = BotTalkBack.contentDescription("Nova", "sphere", "White", true, false, false, true, false, false);
        String echoQuiet = BotTalkBack.contentDescription("Echo", "heart", "Amber", false, false, false, false, false, false);
        String echoSpeaking = BotTalkBack.contentDescription("Echo", "heart", "Amber", false, false, false, true, false, false);
        String kitWorking = BotTalkBack.contentDescription("Nova", "sphere", "White", true, false, false, false, true, false);
        String kitUnread = BotTalkBack.contentDescription("Nova", "sphere", "White", true, false, false, false, false, true);
        assertTrue(kitQuiet.contains("sphere"));
        assertFalse(kitQuiet.contains("speaking"));
        assertFalse(kitQuiet.contains("Tap"));
        assertTrue(kitSpeaking.contains("Nova is speaking. Tap Nova to stop."));
        assertFalse(kitSpeaking.contains("Tap the sphere"));
        assertFalse(kitSpeaking.contains("interrupt"));
        assertTrue(echoQuiet.contains("heart"));
        assertFalse(echoQuiet.contains("sphere"));
        assertFalse(echoQuiet.contains("Tap"));
        assertTrue(echoSpeaking.contains("Echo is speaking. Tap Echo to stop."));
        assertFalse(echoSpeaking.contains("sphere"));
        assertEquals("Tap Nova to stop", BotTalkBack.speakingDetail("Nova"));
        assertEquals("Tap Echo to stop", BotTalkBack.speakingDetail("Echo"));
        assertTrue(kitWorking.contains("Nova is working."));
        assertFalse(kitWorking.contains("Tap"));
        assertFalse(kitWorking.contains("New reply"));
        assertTrue(kitUnread.contains("New reply from Nova."));
        assertFalse(kitUnread.contains("working"));
        assertFalse(kitQuiet.contains("working"));
        assertFalse(kitQuiet.contains("New reply"));
    }

    @Test public void sliderAnnouncesTheCaptionValue() {
        assertEquals("100 percent", SliderState.description(100));
        assertEquals("70 percent", SliderState.description(70));
        assertEquals("125 percent", SliderState.description(125));
    }

    private static final class MutableClock implements FrameThrottle.Clock {
        long now;
        public long uptimeMillis() { return now; }
    }
}

package dev.grokhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BotTrickTest {
    @Test public void slashCommandsStayExact() {
        assertEquals(BotTrick.Name.KICKFLIP, BotTrick.command(" /Kickflip! "));
        assertEquals(BotTrick.Name.BARRELROLL, BotTrick.command("/barrelroll"));
        assertEquals(BotTrick.Name.BOUNCE, BotTrick.command("/bounce"));
        assertEquals(BotTrick.Name.PLAYDEAD, BotTrick.command("/playdead."));
        assertEquals(BotTrick.Name.ROLLOVER, BotTrick.command("/rollover"));
        assertNull(BotTrick.command("do a kick flip"));
        assertNull(BotTrick.command("/kickflip please"));
        assertNull(BotTrick.command(null));
    }

    @Test public void everyTrickStartsAndLandsAtRest() {
        for (BotTrick.Name name : BotTrick.Name.values()) {
            assertRest(name, 0f);
            assertRest(name, 1f);
        }
    }

    @Test public void tricksTurnTheVolumeAndLandUpright() {
        BotTrick kick = BotTrick.pose(BotTrick.Name.KICKFLIP, 0.5f);
        assertTrue(kick.hop > 0.8);
        assertEquals(Math.PI, kick.yaw, 0.2);
        BotTrick barrel = BotTrick.pose(BotTrick.Name.BARRELROLL, 0.5f);
        assertTrue(barrel.roll > Math.PI * 1.5);
        assertEquals(0, barrel.yaw, 1e-6);
        BotTrick bounce = BotTrick.pose(BotTrick.Name.BOUNCE, 0.29f);
        assertTrue(bounce.hop > 0.7);
        assertEquals(0, bounce.roll, 1e-6);
        assertEquals(0, bounce.pitch, 1e-6);
        assertEquals(0, bounce.yaw, 1e-6);
        BotTrick dead = BotTrick.pose(BotTrick.Name.PLAYDEAD, 0.45f);
        assertEquals(Math.PI / 2, dead.pitch, 0.05);
        assertEquals(0, dead.roll, 1e-6);
        BotTrick roll = BotTrick.pose(BotTrick.Name.ROLLOVER, 0.5f);
        assertTrue(roll.shift > 0.7);
        assertTrue(roll.yaw > 2);
    }

    private static void assertRest(BotTrick.Name name, float time) {
        BotTrick pose = BotTrick.pose(name, time);
        assertEquals(name.name(), 0, Math.sin(pose.roll), 1e-6);
        assertEquals(name.name(), 0, Math.sin(pose.pitch), 1e-6);
        assertEquals(name.name(), 0, Math.sin(pose.yaw), 1e-6);
        assertEquals(name.name(), 0, pose.hop, 1e-6);
        assertEquals(name.name(), 0, pose.shift, 1e-6);
    }
}

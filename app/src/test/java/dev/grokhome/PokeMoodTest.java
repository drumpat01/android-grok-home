package dev.grokhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PokeMoodTest {
    @Test public void tenFastTapsAngerOnceThenTheNextTen() {
        PokeMood mood = new PokeMood();
        long time = 1_000;
        for (int tap = 1; tap <= 9; tap++) {
            time += 200;
            assertFalse(mood.poke(time));
        }
        time += 200;
        assertTrue(mood.poke(time));
        for (int tap = 1; tap <= 9; tap++) {
            time += 200;
            assertFalse(mood.poke(time));
        }
        assertTrue(mood.poke(time + 200));
    }

    @Test public void aPauseStartsTheCountOver() {
        PokeMood mood = new PokeMood();
        long time = 5_000;
        for (int tap = 0; tap < 9; tap++) assertFalse(mood.poke(time += 100));
        time += PokeMood.GAP_MS + 1;
        for (int tap = 0; tap < 9; tap++) assertFalse(mood.poke(time += 100));
        assertTrue(mood.poke(time + 100));
    }
}

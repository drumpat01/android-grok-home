package dev.grokhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class MotionTest {
    @Test public void seededMotionsDoNotFaceTheSameWay(){
        Motion first=new Motion(),second=new Motion();
        first.intensity=second.intensity=.5;
        second.seed(4.2);
        for(int i=0;i<40;i++){first.step(.05);second.step(.05);}
        assertTrue(Math.abs(first.yaw-second.yaw)>.2);
    }

    @Test public void restSeedOpensTheBlink(){
        Motion motion=new Motion();
        assertTrue(motion.blink()<.2);
        motion.seed(1.39);
        motion.step(0.016);
        assertEquals(1,motion.blink(),1e-6);
    }
}

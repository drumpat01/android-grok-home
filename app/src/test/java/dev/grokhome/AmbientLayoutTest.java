package dev.grokhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class AmbientLayoutTest {
    @Test public void landscapeGivesMaxTheLargeSquare(){
        AmbientLayout.Slot[] slots=AmbientLayout.place(BotIdentity.NAMES,2560,1600,8);
        AmbientLayout.Slot max=find(slots,"Max"),kit=find(slots,"Nova"),orion=find(slots,"Orion");
        assertTrue(max.featured);
        assertSquare(max);assertSquare(kit);assertSquare(orion);
        assertTrue(max.side()>kit.side()*2.5f);
        assertTrue(kit.left>max.right);
        assertTrue(orion.top>kit.top);
        assertInside(slots,2560,1600);
        assertNoOverlap(slots);
    }
    @Test public void portraitStacksTheSmallSquaresUnderMax(){
        AmbientLayout.Slot[] slots=AmbientLayout.place(BotIdentity.NAMES,1600,2560,8);
        AmbientLayout.Slot max=find(slots,"Max"),sage=find(slots,"Sage");
        assertTrue(max.featured);
        assertSquare(max);assertSquare(sage);
        assertTrue(sage.top>max.bottom);
        assertTrue(max.side()>sage.side()*2.5f);
        assertInside(slots,1600,2560);
        assertNoOverlap(slots);
    }
    private static void assertSquare(AmbientLayout.Slot slot){assertEquals(slot.name,slot.side(),slot.bottom-slot.top,1.5f);}
    private static void assertInside(AmbientLayout.Slot[] slots,float width,float height){
        for(AmbientLayout.Slot slot:slots){assertTrue(slot.left>=-1);assertTrue(slot.top>=-1);assertTrue(slot.right<=width+1);assertTrue(slot.bottom<=height+1);}
    }
    private static AmbientLayout.Slot find(AmbientLayout.Slot[] slots,String name){for(AmbientLayout.Slot slot:slots)if(slot.name.equals(name))return slot;fail(name);return null;}
    private static void assertNoOverlap(AmbientLayout.Slot[] slots){
        for(int i=0;i<slots.length;i++)for(int j=i+1;j<slots.length;j++){
            AmbientLayout.Slot a=slots[i],b=slots[j];
            assertTrue(a.name+" overlaps "+b.name,a.right<=b.left+1 || b.right<=a.left+1 || a.bottom<=b.top+1 || b.bottom<=a.top+1);
        }
    }
}

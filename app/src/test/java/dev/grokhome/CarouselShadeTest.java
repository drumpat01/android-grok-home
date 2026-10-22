package dev.grokhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class CarouselShadeTest {
    @Test public void bodyDimCapsBrightnessLossAtFifteenPercent(){
        assertEquals(0f,CarouselShade.bodyDim(0f),1e-6f);
        assertEquals(0f,CarouselShade.bodyDim(-2f),1e-6f);
        float shallow=(1f-(float)Math.exp(-0.85f*0.05f))*0.62f;
        assertTrue(shallow<0.15f);
        assertEquals(shallow,CarouselShade.bodyDim(0.05f),1e-4f);
        assertEquals(0.15f,CarouselShade.bodyDim(1f),1e-4f);
        assertEquals(0.15f,CarouselShade.bodyDim(2f),1e-4f);
        assertEquals(0.15f,CarouselShade.bodyDim(40f),1e-4f);
    }

    @Test public void scaleRgbKeepsHueAndLeavesWhiteWhite(){
        int gold=0xfff69412;
        int scaled=CarouselShade.scaleRgb(gold,0.85f);
        assertEquals(Math.round(0xf6*0.85f),(scaled>>16)&0xff);
        assertEquals(Math.round(0x94*0.85f),(scaled>>8)&0xff);
        assertEquals(Math.round(0x12*0.85f),scaled&0xff);
        assertEquals(hue(gold),hue(scaled),1f);
        assertTrue(saturation(scaled)>0.8f);
        assertTrue(Math.abs(saturation(gold)-saturation(scaled))<0.1f);
        int kit=CarouselShade.scaleRgb(0xffffffff,1f-CarouselShade.bodyDim(2f));
        int orion=CarouselShade.scaleRgb(0xff777777,1f-CarouselShade.bodyDim(1f));
        assertTrue(((kit>>16)&0xff)>=216);
        assertEquals((kit>>16)&0xff,(kit>>8)&0xff);
        assertEquals((kit>>16)&0xff,kit&0xff);
        assertTrue(((kit>>16)&0xff)>((orion>>16)&0xff)+60);
        assertEquals(gold,CarouselShade.scaleRgb(gold,1f));
    }

    @Test public void spotlightCoverKeepsTheFloor(){
        assertEquals(0.55f,CarouselShade.spotlightCover(0f),1e-6f);
        assertEquals(0.55f,CarouselShade.spotlightCover(0.18f),1e-6f);
        assertEquals(0.55f,CarouselShade.spotlightCover(0.55f),1e-6f);
        assertEquals(0.90f,CarouselShade.spotlightCover(0.90f),1e-6f);
        assertEquals(1f,CarouselShade.spotlightCover(1f),1e-6f);
    }

    @Test public void sideLabelAlphaClampsAndCenterDoesNot(){
        assertEquals(0.85f,CarouselShade.labelAlpha(0.18f,true),1e-6f);
        assertEquals(0.85f,CarouselShade.labelAlpha(0f,true),1e-6f);
        assertEquals(0.85f,CarouselShade.labelAlpha(-1f,true),1e-6f);
        assertEquals(0.85f,CarouselShade.labelAlpha(0.85f,true),1e-6f);
        assertEquals(0.92f,CarouselShade.labelAlpha(0.92f,true),1e-6f);
        assertEquals(1f,CarouselShade.labelAlpha(2f,true),1e-6f);
        assertEquals(0.18f,CarouselShade.labelAlpha(0.18f,false),1e-6f);
        assertEquals(0f,CarouselShade.labelAlpha(-0.4f,false),1e-6f);
        assertEquals(1f,CarouselShade.labelAlpha(1f,false),1e-6f);
        assertEquals(1f,CarouselShade.labelAlpha(4f,false),1e-6f);
    }

    private static float hue(int color){
        float red=((color>>16)&0xff)/255f,green=((color>>8)&0xff)/255f,blue=(color&0xff)/255f;
        float max=Math.max(red,Math.max(green,blue)),min=Math.min(red,Math.min(green,blue)),delta=max-min;
        if(delta<1e-4f)return 0f;
        float hue;
        if(max==red)hue=(green-blue)/delta;
        else if(max==green)hue=(blue-red)/delta+2f;
        else hue=(red-green)/delta+4f;
        hue*=60f;
        if(hue<0f)hue+=360f;
        return hue;
    }

    private static float saturation(int color){
        float red=((color>>16)&0xff)/255f,green=((color>>8)&0xff)/255f,blue=(color&0xff)/255f;
        float max=Math.max(red,Math.max(green,blue)),min=Math.min(red,Math.min(green,blue));
        float light=(max+min)/2f,delta=max-min;
        if(delta<1e-4f)return 0f;
        return delta/(1f-Math.abs(2f*light-1f));
    }
}

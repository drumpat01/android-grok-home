package dev.grokhome;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.*;
import android.util.DisplayMetrics;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Carousel stills at 2560x1600 and 1600x2560, density 340 (1205x753 dp). */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=32)
public class CarouselScreenshotTest {
    private static final float DPI=340f;
    private static final float DENSITY=DPI/160f;
    private static final int LAND_W=2560,LAND_H=1600,PORT_W=1600,PORT_H=2560;
    /** Peak of sin(t*0.73)*0.60 + sin(t*0.31)*0.20, with eyes open. */
    private static final double PEAK_YAW=0.7984294964099135;
    private static final double PEAK_TIME=45.211;

    @Test public void landscapeOverviewShowsSideEyesLabelsAndDots()throws Exception{
        Bitmap bitmap=render("Echo",LAND_W,LAND_H,true,false);
        save(bitmap,"w2-carousel-overview-"+phase()+".png");
        long nanos=timeDraws("Echo",LAND_W,LAND_H,true,12);
        System.out.println("Carousel overview draw "+(nanos/1_000_000L)+" ms for 12 frames ("+(nanos/12/1_000_000L)+" ms each)");
        if(asserting()){
            assertEquals(2560,bitmap.getWidth());
            assertEquals(1600,bitmap.getHeight());
            Slot kit=slot(LAND_W,LAND_H,"Echo","Nova");
            Slot pixel=slot(LAND_W,LAND_H,"Echo","Pixel");
            Slot ivy=slot(LAND_W,LAND_H,"Echo","Sage");
            Slot echo=slot(LAND_W,LAND_H,"Echo","Echo");
            assertWhiteEyes(bitmap,kit);
            assertWhiteEyes(bitmap,pixel);
            assertWhiteEyes(bitmap,ivy);
            assertSideLabel(bitmap,ivy,LAND_W);
            assertSideLabel(bitmap,pixel,LAND_W);
            assertGreenDot(bitmap,kit);
            assertUnreadDot(bitmap,ivy);
            assertTrue(ink(bitmap,echo)>40);
        }
        bitmap.recycle();
    }

    @Test public void landscapeRosterShowsCrownSageAndOrion()throws Exception{
        Bitmap bitmap=render("Max",LAND_W,LAND_H,true,false);
        save(bitmap,"w2-carousel-roster-"+phase()+".png");
        save(bitmap,"w2-carousel-landscape-max-"+phase()+".png");
        if(asserting()){
            Slot max=slot(LAND_W,LAND_H,"Max","Max");
            Slot sage=slot(LAND_W,LAND_H,"Max",BotIdentity.NAMES[BotIdentity.NAMES.length-2]);
            Slot orion=slot(LAND_W,LAND_H,"Max",BotIdentity.NAMES[BotIdentity.NAMES.length-1]);
            Slot kit=slot(LAND_W,LAND_H,"Max","Nova");
            assertGoldCrown(bitmap,max);
            assertWhiteEyes(bitmap,sage);
            assertWhiteEyes(bitmap,orion);
            assertDarkEyes(bitmap,max);
            assertTrue(ink(bitmap,max)>80);
            Slot jd=slot(LAND_W,LAND_H,"Max","Scout");
            assertKitStaysWhite(bitmap,max);
            assertBodyHue(bitmap,jd,"Scout");
        }
        bitmap.recycle();
    }

    @Test public void portraitCarouselKeepsTheRow()throws Exception{
        Bitmap bitmap=render("Echo",PORT_W,PORT_H,false,false);
        save(bitmap,"w2-carousel-portrait-"+phase()+".png");
        if(asserting()){
            assertEquals(1600,bitmap.getWidth());
            assertEquals(2560,bitmap.getHeight());
            Slot pixel=slot(PORT_W,PORT_H,"Echo","Pixel");
            Slot kit=slot(PORT_W,PORT_H,"Echo","Nova");
            assertWhiteEyes(bitmap,pixel);
            assertWhiteEyes(bitmap,kit);
            assertGreenDot(bitmap,kit);
            assertTrue(bitmap.getHeight()>bitmap.getWidth());
        }
        bitmap.recycle();
    }

    @Test public void landscapeJdRosterDimsMaxCrown()throws Exception{
        Bitmap bitmap=render("Scout",LAND_W,LAND_H,true,false);
        save(bitmap,"w2-carousel-roster-jd-"+phase()+".png");
        if(asserting()){
            Slot max=slot(LAND_W,LAND_H,"Scout","Max");
            Slot jd=slot(LAND_W,LAND_H,"Scout","Scout");
            assertSideCrown(bitmap,max);
            assertWhiteEyes(bitmap,jd);
            assertTrue(ink(bitmap,jd)>40);
        }
        bitmap.recycle();
    }

    @Test public void portraitRosterShowsOrionAndSage()throws Exception{
        Bitmap bitmap=render("Max",PORT_W,PORT_H,false,false);
        save(bitmap,"w2-carousel-portrait-roster-"+phase()+".png");
        if(asserting()){
            assertEquals(1600,bitmap.getWidth());
            assertEquals(2560,bitmap.getHeight());
            Slot sage=slot(PORT_W,PORT_H,"Max",BotIdentity.NAMES[BotIdentity.NAMES.length-2]);
            Slot orion=slot(PORT_W,PORT_H,"Max",BotIdentity.NAMES[BotIdentity.NAMES.length-1]);
            assertWhiteEyes(bitmap,sage);
            assertWhiteEyes(bitmap,orion);
            assertSideLabel(bitmap,sage,PORT_W);
            assertTrue(ink(bitmap,orion)>30);
        }
        bitmap.recycle();
    }

    @Test public void sideBotsKeepHueWithKitAndIris()throws Exception{
        Bitmap landscape=render("Pixel",LAND_W,LAND_H,true,false);
        save(landscape,"w2-carousel-landscape-sides-"+phase()+".png");
        Bitmap portrait=render("Pixel",PORT_W,PORT_H,false,false);
        save(portrait,"w2-carousel-portrait-sides-"+phase()+".png");
        if(asserting()){
            assertEquals(2560,landscape.getWidth());
            assertEquals(1600,landscape.getHeight());
            assertEquals(1600,portrait.getWidth());
            assertEquals(2560,portrait.getHeight());
            for(Bitmap bitmap:new Bitmap[]{landscape,portrait}){
                int width=bitmap.getWidth(),height=bitmap.getHeight();
                Slot kit=slot(width,height,"Pixel","Nova");
                Slot echo=slot(width,height,"Pixel","Echo");
                assertBodyHue(bitmap,kit,"Nova");
                assertBodyHue(bitmap,echo,"Echo");
                assertWhiteEyes(bitmap,echo);
                assertWhiteEyes(bitmap,kit);
            }
        }
        landscape.recycle();
        portrait.recycle();
    }

    @Test public void swayAtPeakYawDimsJdAndDogeEdges()throws Exception{
        Bitmap bitmap=render("Echo",LAND_W,LAND_H,true,true);
        save(bitmap,"w2-carousel-sway-"+phase()+".png");
        Slot jd=slot(LAND_W,LAND_H,"Echo","Scout");
        Slot pixel=slot(LAND_W,LAND_H,"Echo","Sage");
        saveEdge(bitmap,jd,"w2-carousel-sway-jd-edge-"+phase()+".png");
        saveEdge(bitmap,pixel,"w2-carousel-sway-pixel-edge-"+phase()+".png");
        if(asserting()){
            assertDimmedLimb(bitmap,jd,"Scout");
            assertDimmedLimb(bitmap,pixel,"Sage");
        }
        bitmap.recycle();
    }

    private static boolean asserting(){return !"before".equals(System.getenv("CAROUSEL_PHASE"));}
    private static String phase(){return asserting()?"after":"before";}

    private static Bitmap render(String center,int width,int height,boolean landscape,boolean sway)throws Exception{
        Context context=tablet(width,height,landscape);
        context.getSharedPreferences("home",0).edit().putInt("stage_size",100).putInt("stage_light",100).putInt("stage_motion",0).commit();
        BotView view=new BotView(context);
        warm(view);
        view.setBotName(center);
        view.restoreOverview(true);
        view.setActivity("Nova",true,false);
        view.setActivity("Sage",false,true);
        view.setActive(false);
        if(sway)posePeakYaw(view);else openEyes(view);
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        view.layout(0,0,width,height);
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        assertTrue(inkAll(bitmap)>500);
        return bitmap;
    }

    private static long timeDraws(String center,int width,int height,boolean landscape,int frames)throws Exception{
        Context context=tablet(width,height,landscape);
        context.getSharedPreferences("home",0).edit().putInt("stage_size",100).putInt("stage_light",100).putInt("stage_motion",0).commit();
        BotView view=new BotView(context);
        warm(view);
        view.setBotName(center);
        view.restoreOverview(true);
        view.setActivity("Nova",true,false);
        view.setActive(false);
        openEyes(view);
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        view.layout(0,0,width,height);
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        view.draw(canvas);
        long start=System.nanoTime();
        for(int i=0;i<frames;i++)view.draw(canvas);
        long elapsed=System.nanoTime()-start;
        bitmap.recycle();
        return elapsed;
    }

    private static Context tablet(int width,int height,boolean landscape){
        int widthDp=Math.round(width/DENSITY),heightDp=Math.round(height/DENSITY);
        RuntimeEnvironment.setQualifiers("w"+widthDp+"dp-h"+heightDp+"dp-"+(landscape?"land":"port")+"-340dpi");
        Context context=RuntimeEnvironment.getApplication();
        DisplayMetrics metrics=context.getResources().getDisplayMetrics();
        metrics.density=DENSITY;metrics.scaledDensity=DENSITY;metrics.densityDpi=340;metrics.xdpi=DPI;metrics.ydpi=DPI;
        metrics.widthPixels=width;metrics.heightPixels=height;
        Configuration configuration=context.getResources().getConfiguration();
        configuration.densityDpi=340;configuration.screenWidthDp=widthDp;configuration.screenHeightDp=heightDp;
        configuration.orientation=landscape?Configuration.ORIENTATION_LANDSCAPE:Configuration.ORIENTATION_PORTRAIT;
        context.getResources().updateConfiguration(configuration,metrics);
        return context;
    }

    private static void warm(BotView view)throws Exception{
        Field lineup=BotView.class.getDeclaredField("lineup");lineup.setAccessible(true);
        Method ensure=BotView.class.getDeclaredMethod("ensureAvatar");ensure.setAccessible(true);
        for(BotView item:(BotView[])lineup.get(view))ensure.invoke(item);
        ensure.invoke(view);
    }

    private static void openEyes(BotView view)throws Exception{
        Field lineup=BotView.class.getDeclaredField("lineup");lineup.setAccessible(true);
        Field motion=BotView.class.getDeclaredField("motion");motion.setAccessible(true);
        for(BotView item:(BotView[])lineup.get(view))((Motion)motion.get(item)).intensity=0;
        ((Motion)motion.get(view)).intensity=0;
    }

    private static void posePeakYaw(BotView view)throws Exception{
        Field lineup=BotView.class.getDeclaredField("lineup");lineup.setAccessible(true);
        Field motion=BotView.class.getDeclaredField("motion");motion.setAccessible(true);
        for(BotView item:(BotView[])lineup.get(view))pose((Motion)motion.get(item));
        pose((Motion)motion.get(view));
    }

    private static void pose(Motion motion){
        motion.intensity=1;
        motion.seed(PEAK_TIME);
        motion.yaw=PEAK_YAW;
        motion.pitch=0;
        motion.roll=0;
        motion.x=0;
        motion.y=0;
    }

    private static void saveEdge(Bitmap bitmap,Slot slot,String name)throws Exception{
        int halfW=Math.round(slot.r*1.55f),halfH=Math.round(slot.r*.48f);
        int left=clampInt(Math.round(slot.x)-halfW,0,bitmap.getWidth()-2);
        int top=clampInt(Math.round(slot.y)-halfH,0,bitmap.getHeight()-2);
        int right=clampInt(Math.round(slot.x)+halfW,left+1,bitmap.getWidth());
        int bottom=clampInt(Math.round(slot.y)+halfH,top+1,bitmap.getHeight());
        Bitmap crop=Bitmap.createBitmap(bitmap,left,top,right-left,bottom-top);
        Bitmap scaled=Bitmap.createScaledBitmap(crop,crop.getWidth()*4,crop.getHeight()*4,true);
        save(scaled,name);
        scaled.recycle();
        crop.recycle();
    }

    private static int clampInt(int value,int min,int max){return Math.max(min,Math.min(max,value));}

    private static void save(Bitmap bitmap,String name)throws Exception{
        File dir=screenshotDir();
        assertTrue(dir.exists()||dir.mkdirs());
        try(FileOutputStream output=new FileOutputStream(new File(dir,name))){
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));
        }
    }

    /** Instrumentation extra {@code screenshotDir}. Today's directory stays the default. */
    private static File screenshotDir(){
        String override=instrumentationArgument("screenshotDir");
        if(override==null || override.trim().isEmpty())return new File(ScreenshotDirs.output(),"screenshots");
        return new File(override.trim());
    }

    private static String instrumentationArgument(String key){
        try{
            Class<?> registry=Class.forName("androidx.test.platform.app.InstrumentationRegistry");
            Object instrumentation=registry.getMethod("getInstrumentation").invoke(null);
            Object arguments=instrumentation.getClass().getMethod("getArguments").invoke(instrumentation);
            if(arguments==null)return null;
            Object value=arguments.getClass().getMethod("getString",String.class).invoke(arguments,key);
            return value instanceof String?(String)value:null;
        }catch(Throwable ignored){
            return null;
        }
    }

    private static final class Slot{final float x,y,r;Slot(float x,float y,float r){this.x=x;this.y=y;this.r=r;}}

    private static Slot slot(int width,int height,String center,String name){
        int centerIndex=BotIdentity.index(center),index=BotIdentity.index(name);
        int delta=Math.floorMod(index-centerIndex,BotIdentity.NAMES.length);
        if(delta>BotIdentity.NAMES.length/2)delta-=BotIdentity.NAMES.length;
        float depth=Math.abs(delta);
        float smallR=Math.min(width*.083f,height*.21f);
        float scale=1f-Math.min(depth,2.5f)*.17f;
        float r=smallR*scale;
        float x=width/2f+delta*width*.185f;
        float y=height*HomeBand.STAGE_CENTER_FRACTION+smallR*(1f-scale);
        return new Slot(x,y,r);
    }

    private static void assertWhiteEyes(Bitmap bitmap,Slot slot){
        int bright=brightest(bitmap,slot.x+slot.r*.28f,slot.y-slot.r*.17f,slot.r*.16f);
        int other=brightest(bitmap,slot.x-slot.r*.28f,slot.y-slot.r*.17f,slot.r*.16f);
        int eye=Math.max(bright,other);
        assertTrue("White eye "+eye+" at "+slot.x+","+slot.y,eye>200);
    }

    private static void assertDarkEyes(Bitmap bitmap,Slot slot){
        int left=darkest(bitmap,slot.x-slot.r*.28f,slot.y-slot.r*.17f,slot.r*.14f);
        int right=darkest(bitmap,slot.x+slot.r*.28f,slot.y-slot.r*.17f,slot.r*.14f);
        int eye=Math.min(left,right);
        assertTrue("Dark eye "+eye,eye<70);
    }

    private static void assertGoldCrown(Bitmap bitmap,Slot slot){
        int gold=0;
        int top=Math.max(0,Math.round(slot.y-slot.r*1.35f)),bottom=Math.round(slot.y-slot.r*.7f);
        int left=Math.max(0,Math.round(slot.x-slot.r*.5f)),right=Math.min(bitmap.getWidth()-1,Math.round(slot.x+slot.r*.5f));
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){
            int color=bitmap.getPixel(x,y);
            if(Color.red(color)>170 && Color.green(color)>90 && Color.blue(color)<90)gold++;
        }
        assertTrue("Crown pixels "+gold,gold>12);
    }

    private static void assertSideCrown(Bitmap bitmap,Slot slot){
        int gold=0;
        int top=Math.max(0,Math.round(slot.y-slot.r*1.45f)),bottom=Math.round(slot.y-slot.r*.55f);
        int left=Math.max(0,Math.round(slot.x-slot.r*.7f)),right=Math.min(bitmap.getWidth()-1,Math.round(slot.x+slot.r*.7f));
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){
            int color=bitmap.getPixel(x,y);
            int r=Color.red(color),g=Color.green(color),b=Color.blue(color);
            if(r>160 && g>70 && b<130 && r>b+30)gold++;
        }
        assertTrue("Side crown pixels "+gold,gold>12);
    }

    private static void assertKitStaysWhite(Bitmap bitmap,Slot kit){
        int kitFace=brightest(bitmap,kit.x,kit.y,kit.r*.12f);
        assertTrue("Nova face "+kitFace,kitFace>170);
    }

    private static void assertBodyHue(Bitmap bitmap,Slot slot,String name){
        long red=0,green=0,blue=0,count=0;
        int left=Math.max(0,Math.round(slot.x-slot.r*.55f)),right=Math.min(bitmap.getWidth()-1,Math.round(slot.x+slot.r*.55f));
        int top=Math.max(0,Math.round(slot.y-slot.r*.55f)),bottom=Math.min(bitmap.getHeight()-1,Math.round(slot.y+slot.r*.55f));
        for(int y=top;y<=bottom;y+=2)for(int x=left;x<=right;x+=2){
            if(Math.hypot(x-slot.x,y-slot.y)>slot.r*.55f)continue;
            int color=bitmap.getPixel(x,y);
            int r=Color.red(color),g=Color.green(color),b=Color.blue(color);
            int max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));
            if(max<40 || (min>200 && max>230))continue;
            if(max>0 && (max-min)/(float)max<0.25f)continue;
            red+=r;green+=g;blue+=b;count++;
        }
        assertTrue(name+" chromatic pixels "+count,count>12);
        int sample=Color.rgb((int)(red/count),(int)(green/count),(int)(blue/count));
        float[] got=new float[3],ref=new float[3];
        Color.colorToHSV(sample,got);
        Color.colorToHSV(BotIdentity.color(name),ref);
        float dh=Math.abs(got[0]-ref[0]);
        if(dh>180f)dh=360f-dh;
        assertTrue(name+" hue "+got[0]+" ref "+ref[0]+" sat "+got[1]+" val "+got[2]+" "+Integer.toHexString(sample),dh<18f && got[1]>0.35f && got[2]>0.42f);
    }

    private static void assertDimmedLimb(Bitmap bitmap,Slot slot,String name){
        int white=0;
        int left=Math.max(0,Math.round(slot.x-slot.r)),right=Math.min(bitmap.getWidth()-1,Math.round(slot.x+slot.r));
        int top=Math.max(0,Math.round(slot.y-slot.r)),bottom=Math.min(bitmap.getHeight()-1,Math.round(slot.y+slot.r));
        for(int y=top;y<=bottom;y+=2)for(int x=left;x<=right;x+=2){
            int color=bitmap.getPixel(x,y);
            int red=Color.red(color),green=Color.green(color),blue=Color.blue(color);
            int max=Math.max(red,Math.max(green,blue)),min=Math.min(red,Math.min(green,blue));
            if(max>230 && min>200)white++;
        }
        assertTrue(name+" white eyes "+white,white>8);
        assertBodyHue(bitmap,slot,name);
    }

    private static void assertSideLabel(Bitmap bitmap,Slot slot,int width){
        float labelY=slot.y+slot.r*1.8f+18f*DENSITY;
        int hits=0;
        int top=Math.max(0,Math.round(labelY-16f*DENSITY)),bottom=Math.min(bitmap.getHeight()-1,Math.round(labelY));
        int left=Math.max(0,Math.round(slot.x-40f*DENSITY)),right=Math.min(width-1,Math.round(slot.x+40f*DENSITY));
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){
            int color=bitmap.getPixel(x,y);
            if(Color.red(color)>140 && Color.green(color)>140 && Color.blue(color)>140)hits++;
        }
        assertTrue("Side label pixels "+hits,hits>8);
    }

    private static void assertGreenDot(Bitmap bitmap,Slot slot){
        float labelY=slot.y+slot.r*1.8f+18f*DENSITY;
        int hits=0;
        int top=Math.max(0,Math.round(labelY-14f*DENSITY)),bottom=Math.min(bitmap.getHeight()-1,Math.round(labelY+4f*DENSITY));
        int left=Math.max(0,Math.round(slot.x-70f*DENSITY)),right=Math.round(slot.x);
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){
            int color=bitmap.getPixel(x,y);
            if(Color.green(color)>170 && Color.green(color)>Color.red(color)+40 && Color.green(color)>Color.blue(color)+20)hits++;
        }
        assertTrue("Working dot pixels "+hits,hits>6);
    }

    private static void assertUnreadDot(Bitmap bitmap,Slot slot){
        float labelY=slot.y+slot.r*1.8f+18f*DENSITY;
        int hits=0;
        int top=Math.max(0,Math.round(labelY-16f*DENSITY)),bottom=Math.min(bitmap.getHeight()-1,Math.round(labelY+6f*DENSITY));
        int left=Math.round(slot.x),right=Math.min(bitmap.getWidth()-1,Math.round(slot.x+50f*DENSITY));
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){
            int color=bitmap.getPixel(x,y);
            if(Color.blue(color)>180 && Color.blue(color)>Color.red(color) && Color.green(color)>140)hits++;
        }
        assertTrue("Unread dot pixels "+hits,hits>4);
    }

    private static int brightest(Bitmap bitmap,float cx,float cy,float radius){
        int best=0;
        int left=Math.max(0,Math.round(cx-radius)),right=Math.min(bitmap.getWidth()-1,Math.round(cx+radius));
        int top=Math.max(0,Math.round(cy-radius)),bottom=Math.min(bitmap.getHeight()-1,Math.round(cy+radius));
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){
            int color=bitmap.getPixel(x,y);
            best=Math.max(best,Math.min(Color.red(color),Math.min(Color.green(color),Color.blue(color))));
        }
        return best;
    }

    private static int darkest(Bitmap bitmap,float cx,float cy,float radius){
        int best=255;
        int left=Math.max(0,Math.round(cx-radius)),right=Math.min(bitmap.getWidth()-1,Math.round(cx+radius));
        int top=Math.max(0,Math.round(cy-radius)),bottom=Math.min(bitmap.getHeight()-1,Math.round(cy+radius));
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){
            int color=bitmap.getPixel(x,y);
            if((color&0xffffff)==0)continue;
            best=Math.min(best,(Color.red(color)+Color.green(color)+Color.blue(color))/3);
        }
        return best;
    }

    private static int ink(Bitmap bitmap,Slot slot){
        int count=0;
        int left=Math.max(0,Math.round(slot.x-slot.r)),right=Math.min(bitmap.getWidth()-1,Math.round(slot.x+slot.r));
        int top=Math.max(0,Math.round(slot.y-slot.r)),bottom=Math.min(bitmap.getHeight()-1,Math.round(slot.y+slot.r));
        for(int y=top;y<=bottom;y+=3)for(int x=left;x<=right;x+=3)if((bitmap.getPixel(x,y)&0xffffff)!=0)count++;
        return count;
    }

    private static int inkAll(Bitmap bitmap){
        int count=0;
        for(int y=0;y<bitmap.getHeight();y+=8)for(int x=0;x<bitmap.getWidth();x+=8)if((bitmap.getPixel(x,y)&0xffffff)!=0)count++;
        return count;
    }
}

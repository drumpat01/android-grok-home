package dev.grokhome;

import android.view.*;
import android.graphics.Bitmap;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import android.content.pm.ActivityInfo;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.lang.reflect.*;
import static org.junit.Assert.*;

/** Real touch streams against the stage. No bot messages are sent. */
@RunWith(AndroidJUnit4.class)
public class BotCarouselUiTest {
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static void invoke(Object owner,String name)throws Exception{Method m=owner.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(owner);}
    private static void select(HomeActivity home,String name)throws Exception{Method m=HomeActivity.class.getDeclaredMethod("selectBot",String.class,int.class);m.setAccessible(true);m.invoke(home,name,BotIdentity.color(name));}
    private static void point(HomeActivity home,long time,int action,float x,float y){MotionEvent e=MotionEvent.obtain(time,time,action,x,y,0);home.dispatchTouchEvent(e);e.recycle();}
    private static void swipe(HomeActivity home,int direction)throws Exception{
        BotView bot=(BotView)field(home,"bot");float x=bot.getWidth()*.5f,y=bot.getHeight()*.4f;
        long time=android.os.SystemClock.uptimeMillis();float travel=bot.getWidth()*.23f*direction;
        point(home,time,MotionEvent.ACTION_DOWN,x,y);
        for(int i=1;i<=8;i++)point(home,time+i*20,MotionEvent.ACTION_MOVE,x-travel*i/8,y);
        point(home,time+180,MotionEvent.ACTION_UP,x-travel,y);
    }
    private static void multi(HomeActivity home,long start,long time,int action,float x,float y,float span){
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[2];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[2];
        for(int i=0;i<2;i++){properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=x+(i==0?-span:span)/2;coords[i].y=y;coords[i].pressure=1;coords[i].size=1;}
        MotionEvent e=MotionEvent.obtain(start,time,action,2,properties,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);home.dispatchTouchEvent(e);e.recycle();
    }
    private static void pinch(HomeActivity home,boolean inward)throws Exception{
        BotView bot=(BotView)field(home,"bot");float x=bot.getWidth()*.5f,y=bot.getHeight()*.4f;
        float from=bot.getWidth()*(inward?.40f:.12f),to=bot.getWidth()*(inward?.12f:.40f);long t=android.os.SystemClock.uptimeMillis();
        point(home,t,MotionEvent.ACTION_DOWN,x-from/2,y);
        multi(home,t,t+20,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),x,y,from);
        for(int i=1;i<=12;i++)multi(home,t,t+20+i*20,MotionEvent.ACTION_MOVE,x,y,from+(to-from)*i/12);
        multi(home,t,t+280,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),x,y,to);
        point(home,t+300,MotionEvent.ACTION_UP,x-to/2,y);
    }
    private static void capture(String name)throws Exception{
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);
        File folder=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("carousel-tests");folder.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
    private static void pauseFeed(HomeActivity home)throws Exception{((NativeActivityFeed)field(home,"nativeFeed")).stop();}
    @Test public void touchNavigationWrapsAndZoomsWithAndWithoutChat()throws Exception{
        final String[] original=new String[1];
        try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)){
            scenario.onActivity(home->{try{original[0]=(String)field(home,"selectedBot");pauseFeed(home);home.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);select(home,"Max");}catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(1500);
            scenario.onActivity(home->{try{swipe(home,1);}catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(1100);
            scenario.onActivity(home->{try{assertEquals("Scout",field(home,"selectedBot"));pinch(home,true);}catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(600);capture("carousel-landscape");
            scenario.onActivity(home->{try{
                BotView bot=(BotView)field(home,"bot");assertTrue("Pinch in opens overview",bot.isOverview());
                select(home,"Luna");swipe(home,1);
            }catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(600);
            scenario.onActivity(home->{try{assertEquals("Max",field(home,"selectedBot"));swipe(home,-1);}catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(600);
            scenario.onActivity(home->{try{assertEquals("Luna",field(home,"selectedBot"));invoke(home,"showKeyboard");pauseFeed(home);assertNotNull(field(home,"chatPanel"));swipe(home,1);}catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(600);capture("carousel-chat-landscape");
            scenario.onActivity(home->{try{assertEquals("Max",field(home,"selectedBot"));assertNotNull("Stage swipe keeps chat open",field(home,"chatPanel"));pinch(home,false);}catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(600);
            scenario.onActivity(home->{try{BotView bot=(BotView)field(home,"bot");assertFalse("Pinch out returns to single bot",bot.isOverview());assertNotNull(field(home,"chatPanel"));swipe(home,1);}catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(1100);
            scenario.onActivity(home->{try{assertEquals("Scout",field(home,"selectedBot"));assertNotNull(field(home,"chatPanel"));swipe(home,-1);}catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(1100);
            scenario.onActivity(home->{try{assertEquals("Max",field(home,"selectedBot"));assertNotNull(field(home,"chatPanel"));pinch(home,true);}catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(600);
            scenario.onActivity(home->{try{BotView bot=(BotView)field(home,"bot");assertTrue(bot.isOverview());long t=android.os.SystemClock.uptimeMillis();point(home,t,MotionEvent.ACTION_DOWN,bot.getWidth()/2f,bot.getHeight()*.4f);point(home,t+100,MotionEvent.ACTION_UP,bot.getWidth()/2f,bot.getHeight()*.4f);assertFalse("Center tap zooms in",bot.isOverview());home.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);}catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(900);
            scenario.onActivity(home->{try{pinch(home,true);}catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(600);capture("carousel-chat-portrait");
            scenario.onActivity(home->{try{assertTrue(((BotView)field(home,"bot")).isOverview());assertNotNull(field(home,"chatPanel"));swipe(home,1);}catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(600);
            scenario.onActivity(home->{try{assertEquals("Scout",field(home,"selectedBot"));((BotView)field(home,"bot")).setOverview(false);select(home,original[0]);pauseFeed(home);invoke(home,"closeOverlay");home.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);}catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(1200);
        }
    }
}

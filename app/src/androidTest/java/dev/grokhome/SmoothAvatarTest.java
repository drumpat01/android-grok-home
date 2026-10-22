package dev.grokhome;

import android.graphics.*;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.lang.reflect.*;

@RunWith(AndroidJUnit4.class)
public class SmoothAvatarTest {
    private FirstRunTestGate firstRun;
    @Before public void hideFirstRun(){firstRun=new FirstRunTestGate();}
    @After public void restoreFirstRun(){firstRun.restore();}
    @Test public void roundedSurfacesRenderAtFrontSideAndBack() throws Exception {
        try(ActivityScenario<HomeActivity> activity=ActivityScenario.launch(HomeActivity.class)){
            for(String name:new String[]{"Echo","Luna","Sage","Scout","Clover","Luna","Pixel"}){
                for(int pose=0;pose<4;pose++){
                    final int index=pose;
                    activity.onActivity(a->{try{
                        FrameLayoutFinder finder=new FrameLayoutFinder();
                        BotView view=finder.find(a.findViewById(android.R.id.content));
                        view.setActive(false);view.setBotName(name);view.setBallColor(BotIdentity.color(name));
                        Field motionField=BotView.class.getDeclaredField("motion");motionField.setAccessible(true);Motion motion=(Motion)motionField.get(view);
                        motion.yaw=new double[]{0,.65,1.35,Math.PI}[index];motion.pitch=0;motion.roll=0;motion.x=0;motion.y=0;view.invalidate();
                        Field avatarField=BotView.class.getDeclaredField("avatar");avatarField.setAccessible(true);Object avatar=avatarField.get(view);
                        Field smoothField=SolidAvatar.class.getDeclaredField("smooth");smoothField.setAccessible(true);assertNotNull("GPU fallback for "+name,smoothField.get(avatar));
                    }catch(ReflectiveOperationException e){throw new RuntimeException(e);}});
                    Thread.sleep(350);
                    Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(screenshot);
                    int colored=0;
                    for(int y=280;y<1080;y+=8)for(int x=780;x<1780;x+=8){int color=screenshot.getPixel(x,y);if(Math.max(Color.red(color),Math.max(Color.green(color),Color.blue(color)))-Math.min(Color.red(color),Math.min(Color.green(color),Color.blue(color)))>25)colored++;}
                    assertTrue("Invisible GPU body: "+name+" pose "+pose,colored>900);
                    File folder=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("render-tests");folder.mkdirs();
                    try(FileOutputStream output=new FileOutputStream(new File(folder,name.replace(' ','-')+"-"+pose+".png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,output);}screenshot.recycle();
                }
            }
        }
    }
    @Test public void animatedSurfacesKeepTurning() throws Exception {
        try(ActivityScenario<HomeActivity> activity=ActivityScenario.launch(HomeActivity.class)){
            for(String name:new String[]{"Echo","Sage","Luna"}){
                activity.onActivity(a->{BotView view=new FrameLayoutFinder().find(a.findViewById(android.R.id.content));view.setBotName(name);view.setBallColor(BotIdentity.color(name));view.setSpeaking(true);view.setActive(true);});
                Thread.sleep(8500);
            }
        }
    }
    static final class FrameLayoutFinder {
        BotView find(View v){if(v instanceof BotView)return (BotView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){BotView result=find(((ViewGroup)v).getChildAt(i));if(result!=null)return result;}return null;}
    }
}

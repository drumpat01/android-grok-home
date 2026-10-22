package dev.grokhome;

import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.lang.reflect.*;
import static org.junit.Assert.*;

/** Synthetic read-only UI regressions; never sends bot messages. */
@RunWith(AndroidJUnit4.class)
public class ChatMotionUiTest {
    private FirstRunTestGate firstRun;
    @Before public void hideFirstRun(){firstRun=new FirstRunTestGate();}
    @After public void restoreFirstRun(){firstRun.restore();}
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static void invoke(Object owner,String name)throws Exception{Method m=owner.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(owner);}
    private static void switchTo(HomeActivity home,String name)throws Exception{Method m=HomeActivity.class.getDeclaredMethod("selectBot",String.class,int.class);m.setAccessible(true);m.invoke(home,name,BotIdentity.color(name));}
    @Test public void longHistoryIsVirtualizedAndChatSurvivesBotChooserAndBack()throws Exception{
        try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)){
            final String[] original=new String[1];
            scenario.onActivity(home->{try{
                original[0]=(String)field(home,"selectedBot");
                ((NativeActivityFeed)field(home,"nativeFeed")).stop();invoke(home,"showKeyboard");
                TextChatPanel panel=(TextChatPanel)field(home,"chatPanel");panel.setStatus("Ready when you are",false);
                JSONArray rows=new JSONArray();String body="A long saved reply. ".repeat(150);
                for(int i=0;i<1000;i++)rows.put(new JSONObject().put("id","synthetic-"+i).put("kind","message").put("role","Max").put("text",body+i));
                long start=android.os.SystemClock.uptimeMillis();panel.setNativeHistory(rows);
                assertTrue("Loading history must not create a thousand views",android.os.SystemClock.uptimeMillis()-start<250);
            }catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(600);
            scenario.onActivity(home->{try{
                TextChatPanel panel=(TextChatPanel)field(home,"chatPanel");ListView list=(ListView)field(panel,"scroll");
                assertEquals(1000,list.getCount());assertTrue(list.getChildCount()>0);assertTrue("Only visible rows should exist",list.getChildCount()<20);
                invoke(home,"showBotChooser");assertSame(panel,field(home,"chatPanel"));
                invoke(home,"closeOverlay");assertSame(panel,field(home,"chatPanel"));
                home.onBackPressed();assertSame(panel,field(home,"chatPanel"));
                switchTo(home,original[0].equals("Nova")?"Max":"Nova");assertNotNull(field(home,"chatPanel"));
                ((NativeActivityFeed)field(home,"nativeFeed")).stop();
                assertEquals("Message "+field(home,"selectedBot"),((EditText)field(field(home,"chatPanel"),"input")).getHint().toString());
            }catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(1200);
            scenario.onActivity(home->{try{switchTo(home,original[0]);((NativeActivityFeed)field(home,"nativeFeed")).stop();}catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(1200);
            scenario.onActivity(home->{try{
                View panel=(View)field(home,"chatPanel");int[] location=new int[2];panel.getLocationOnScreen(location);
                long t=android.os.SystemClock.uptimeMillis();float x=location[0]+40,y=location[1]+80;
                MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(t,t+300,MotionEvent.ACTION_UP,x+180,y,0);
                home.dispatchTouchEvent(down);home.dispatchTouchEvent(up);down.recycle();up.recycle();
            }catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(500);
            scenario.onActivity(home->{try{assertNull("Right swipe dismisses chat",field(home,"chatPanel"));invoke(home,"showKeyboard");invoke(home,"closeOverlay");}catch(Exception e){throw new AssertionError(e);}});
            Thread.sleep(500);
            scenario.onActivity(home->{try{assertNull("Close dismisses chat",field(home,"chatPanel"));}catch(Exception e){throw new AssertionError(e);}});
        }
    }
}

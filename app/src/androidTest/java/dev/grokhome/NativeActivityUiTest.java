package dev.grokhome;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.content.pm.ActivityInfo;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.lang.reflect.*;

/** Synthetic contact data only: never sends to a bot or installs on the tablet. */
@RunWith(AndroidJUnit4.class)
public class NativeActivityUiTest {
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static void invoke(Object owner,String name)throws Exception{Method m=owner.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(owner);}
    private static TextView text(View view,String value){if(view instanceof TextView && ((TextView)view).getText().toString().equals(value))return (TextView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){TextView result=text(group.getChildAt(i),value);if(result!=null)return result;}}return null;}
    private FirstRunTestGate firstRun;
    @Before public void hideFirstRun(){firstRun=new FirstRunTestGate();}
    @After public void restoreFirstRun(){firstRun.restore();}
    @Test public void nativeContactExpandsAndDraftSurvivesRotationAndDismiss()throws Exception{
        final String[] originalDraft=new String[1];
        try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)){
            scenario.onActivity(home->{try{
                ((NativeActivityFeed)field(home,"nativeFeed")).stop();invoke(home,"showKeyboard");
                TextChatPanel panel=(TextChatPanel)field(home,"chatPanel");
                JSONArray rows=new JSONArray().put(new JSONObject().put("id","contact-1").put("kind","contact").put("from","Max").put("to","Nova").put("text","Please check the release status.").put("time",1792166400000L)).put(new JSONObject().put("id","reply-1").put("kind","message").put("role","Max").put("text","The update is ready.").put("time",1792166401000L));
                panel.setStatus("Ready when you are",false);panel.setRemoteState(false,false);panel.setNativeHistory(rows);
            }catch(Exception error){throw new AssertionError(error);}});
            Thread.sleep(600);
            scenario.onActivity(home->{try{
                TextChatPanel panel=(TextChatPanel)field(home,"chatPanel");
                assertNotNull(text(panel,"Max → Nova"));TextView detail=text(panel,"Please check the release status.");assertEquals(View.GONE,detail.getVisibility());
                View parent=(View)detail.getParent();parent.performClick();assertEquals(View.VISIBLE,detail.getVisibility());
                originalDraft[0]=((EditText)field(panel,"input")).getText().toString();
                ((EditText)field(panel,"input")).setText("Draft stays here");
                panel.setRemoteState(true,false);assertNotNull(text(panel,field(home,"selectedBot")+" is working…"));assertFalse(((Button)field(panel,"send")).isEnabled());
                panel.setRemoteState(false,false);assertTrue(((Button)field(panel,"send")).isEnabled());
                home.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }catch(Exception error){throw new AssertionError(error);}});
            Thread.sleep(800);
            scenario.onActivity(home->{try{
                TextChatPanel panel=(TextChatPanel)field(home,"chatPanel");assertNotNull(panel);assertEquals("Draft stays here",((EditText)field(panel,"input")).getText().toString());assertNotNull(text(panel,"Max → Nova"));
                ((EditText)field(panel,"input")).setText(originalDraft[0]);invoke(home,"closeOverlay");
            }catch(Exception error){throw new AssertionError(error);}});
            Thread.sleep(500);
            scenario.onActivity(home->{try{assertNull(field(home,"chatPanel"));}catch(Exception error){throw new AssertionError(error);}});
        }
    }
}

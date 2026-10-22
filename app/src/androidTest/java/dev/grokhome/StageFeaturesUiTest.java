package dev.grokhome;

import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Synthetic loopback activity only; all user preferences are restored after closing Home. */
@RunWith(AndroidJUnit4.class)
public class StageFeaturesUiTest {
    private volatile boolean allowTasks,finishTask,cancelledTask;
    private final java.util.concurrent.ConcurrentHashMap<String,JSONObject> jobs=new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.List<JSONObject> submitted=new java.util.concurrent.CopyOnWriteArrayList<>();
    private SharedPreferences prefs;private Map<String,?> original;private MockWebServer server;
    private ActivityScenario<HomeActivity> scenario;private final AtomicInteger posts=new AtomicInteger(),kitVersion=new AtomicInteger(1),maxVersion=new AtomicInteger(1);
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static void invoke(Object owner,String name)throws Exception{Method m=owner.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(owner);}
    private static void enterAmbient(Object owner)throws Exception{Method m=owner.getClass().getDeclaredMethod("enterAmbient",boolean.class);m.setAccessible(true);m.invoke(owner,true);}
    private static View control(View view,String description){if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=control(((ViewGroup)view).getChildAt(i),description);if(found!=null)return found;}return null;}
    private JSONObject snapshot(String name)throws Exception{
        JSONArray rows=new JSONArray();int count=name.equals("Max")?80:4;
        for(int i=0;i<count;i++)rows.put(new JSONObject().put("id",name+"-"+i).put("seq",""+(i+1)).put("updatedSeq",""+(i+1)).put("time",i+1).put("kind","message").put("role",i%2==0?"You":name).put("text","Saved conversation "+i+". "+"A useful reply with enough lines for the history viewport. ".repeat(5)));
        if(name.equals("Nova") && kitVersion.get()>1)rows.put(new JSONObject().put("id","kit-new").put("seq","100").put("updatedSeq","100").put("time",100).put("kind","message").put("role","Nova").put("text","A new synthetic reply."));
        if(name.equals("Max") && maxVersion.get()>1)rows.put(new JSONObject().put("id","max-new").put("seq","500").put("updatedSeq","500").put("time",500).put("kind","message").put("role","Max").put("text","The new synthetic reply has arrived."));
        return new JSONObject().put("bot",name).put("stale",false).put("working",name.equals("Nova") && kitVersion.get()>1).put("entries",rows);
    }
    @Before public void setup()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();prefs=context.getSharedPreferences("home",0);original=new HashMap<>(prefs.getAll());
        server=new MockWebServer();server.setDispatcher(new Dispatcher(){@Override public MockResponse dispatch(RecordedRequest request){try{
            if(!"GET".equals(request.getMethod())){
                posts.incrementAndGet();if(!allowTasks)return new MockResponse().setResponseCode(500);
                JSONObject input=new JSONObject(request.getBody().readUtf8());
                if(request.getPath().equals("/bot/cancel")){cancelledTask=true;return new MockResponse().setBody("{\"status\":\"cancelled\"}");}
                submitted.add(input);jobs.put(input.getString("request_id"),input);return new MockResponse().setResponseCode(202).setBody("{}");
            }
            if(request.getPath().startsWith("/bot/jobs/")){
                String id=request.getPath().substring("/bot/jobs/".length());JSONObject input=jobs.get(id);if(input==null)return new MockResponse().setResponseCode(404);
                boolean routing=input.optString("action").equals("route");
                return new MockResponse().setBody(new JSONObject().put("status",routing || finishTask?"finished":"running").put("route_bot",routing?"Nova":"").put("reply",routing?"ROUTE: Nova":finishTask?"Synthetic task complete":"").put("progress","Checking your request").toString());
            }
            String name=request.getRequestUrl().queryParameter("bot");if(name==null)return new MockResponse().setResponseCode(404);
            JSONObject data=snapshot(name);
            if("1".equals(request.getRequestUrl().queryParameter("summary"))){JSONObject row=BotActivityState.latestReply(data.getJSONArray("entries"),name);data=new JSONObject().put("bot",name).put("stale",false).put("working",data.optBoolean("working")).put("reply_id",row.optString("id")).put("reply_revision",row.optString("updatedSeq")).put("reply_seq",row.optString("seq")).put("reply_time",row.optLong("time")).put("latestUnreadPreview",UnreadPreview.preview(row.optString("text")));}
            return new MockResponse().setBody(data.toString());
        }catch(Exception e){return new MockResponse().setResponseCode(500);}}});server.start();
        SharedPreferences.Editor edit=prefs.edit().putString("voice_server",server.url("/").toString().replaceAll("/$","")).putString("selected_bot","Max").putBoolean("chat_open",false).putBoolean("stage_overview",true).putInt("stage_size",100).putInt("stage_light",100).putInt("stage_motion",100).putBoolean("ambient_enabled",true).putBoolean("auto_listen",false).putBoolean("muted",false);
        for(String key:new HashSet<>(prefs.getAll().keySet()))if(key.startsWith("activity_") || key.startsWith("chat_place_") || key.startsWith("bot_job_"))edit.remove(key);edit.commit();
        scenario=ActivityScenario.launch(HomeActivity.class);
    }
    @After public void cleanup()throws Exception{
        if(scenario!=null)scenario.close();
        SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> entry:original.entrySet()){
            Object value=entry.getValue();String key=entry.getKey();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Set)edit.putStringSet(key,(Set<String>)value);
        }edit.commit();server.shutdown();
    }
    @Test public void floorSpotlightIsStationaryWhileBotBobs()throws Exception{
        scenario.onActivity(home->{try{
            BotView view=(BotView)field(home,"bot");view.setActive(false);view.restoreOverview(false);view.layout(0,0,1000,800);
            java.lang.reflect.Field shift=view.getClass().getDeclaredField("statusShift");shift.setAccessible(true);shift.setFloat(view,1f);
            java.lang.reflect.Field ceiling=view.getClass().getDeclaredField("statusCeiling");ceiling.setAccessible(true);ceiling.setFloat(view,0f);
            Object motion=field(view,"motion");Field y=motion.getClass().getDeclaredField("y");y.setAccessible(true);
            Bitmap first=Bitmap.createBitmap(1000,800,Bitmap.Config.ARGB_8888),second=Bitmap.createBitmap(1000,800,Bitmap.Config.ARGB_8888);
            y.setDouble(motion,-.12);view.draw(new android.graphics.Canvas(first));y.setDouble(motion,.12);view.draw(new android.graphics.Canvas(second));
            float floor=800*StagePlacement.SINGLE_Y,radius=Math.min(1000*.157f,800*.31f)*StagePlacement.SINGLE_SCALE;
            int lit=0;for(int py=(int)(floor+radius*1.30f);py<Math.min(800,(int)(floor+radius*1.6f));py++)for(int px=200;px<800;px++){
                int a=first.getPixel(px,py),b=second.getPixel(px,py);assertEquals("Floor pixel moved at "+px+","+py,a,b);if((a&0xffffff)!=0)lit++;
            }
            assertTrue("Test samples a visible floor glow",lit>1000);first.recycle();second.recycle();
        }catch(Exception e){throw new AssertionError(e);}});
    }
    @Test public void ambientVoiceWakeStartsRecognitionAndDoesNotSendWakePhrase()throws Exception{
        Thread.sleep(1000);
        scenario.onActivity(home->{try{enterAmbient(home);assertTrue(diagnostic(home),(Boolean)field(home,"ambient"));}catch(Exception e){throw new AssertionError(e);}});
        Thread.sleep(1700);
        scenario.onActivity(home->{try{BotConversation conversation=(BotConversation)field(home,"conversation");assertTrue(diagnostic(home),conversation.listening());assertEquals("Speak to Max",field(home,"voiceDetail"));conversation.recognized("Hey Max");assertFalse((Boolean)field(home,"ambient"));}catch(Exception e){throw new AssertionError(e);}});
        Thread.sleep(900);
        scenario.onActivity(home->{try{assertEquals("Max",field(home,"selectedBot"));assertTrue(((BotConversation)field(home,"conversation")).listening());}catch(Exception e){throw new AssertionError(e);}});
        assertEquals("Wake words never become bot messages",0,posts.get());
    }
    @Test public void contextHandoffRoutesOnceAndTaskCanBeCancelled()throws Exception{
        allowTasks=true;Thread.sleep(1200);
        scenario.onActivity(home->{try{invoke(home,"showKeyboard");((BotConversation)field(home,"conversation")).recognized("Let me talk to Nova about this");}catch(Exception e){throw new AssertionError(e);}});
        Thread.sleep(1800);
        assertEquals(1,submitted.size());JSONObject request=submitted.get(0);assertEquals("Nova",request.getString("bot"));assertTrue(request.getString("message").contains("handoff from Max"));assertTrue(request.getString("message").contains("Saved conversation 79"));assertTrue(request.getString("message").length()<=4000);
        scenario.onActivity(home->{try{assertNotNull(field(home,"chatPanel"));StatusBlock block=(StatusBlock)field(home,"statusBlock");assertTrue(block.titleText().contains("working"));((BotConversation)field(home,"conversation")).cancelTask();}catch(Exception e){throw new AssertionError(e);}});
        Thread.sleep(800);assertTrue(cancelledTask);
        scenario.onActivity(home->{try{assertFalse(((BotConversation)field(home,"conversation")).busy());assertTrue(((StatusBlock)field(home,"statusBlock")).titleText().contains("Task stopped"));}catch(Exception e){throw new AssertionError(e);}});
        capture("task-cancelled");
    }
    @Test public void whoCanHelpSelectsRecommendedBotAndDeliversRequest()throws Exception{
        allowTasks=true;Thread.sleep(1000);
        scenario.onActivity(home->{try{((BotConversation)field(home,"conversation")).recognized("Who can help?");assertTrue((Boolean)field(home,"awaitingRoute"));((BotConversation)field(home,"conversation")).recognized("Please review my website");}catch(Exception e){throw new AssertionError(e);}});
        Thread.sleep(2000);assertEquals(2,submitted.size());assertEquals("route",submitted.get(0).getString("action"));assertEquals("Max",submitted.get(0).getString("bot"));assertEquals("Nova",submitted.get(1).getString("bot"));assertEquals("Please review my website",submitted.get(1).getString("message"));
        finishTask=true;Thread.sleep(800);
        scenario.onActivity(home->{try{assertEquals("Nova",field(home,"selectedBot"));assertFalse(((BotConversation)field(home,"conversation")).busy());assertTrue(((StatusBlock)field(home,"statusBlock")).titleText().contains("finished"));}catch(Exception e){throw new AssertionError(e);}});
        capture("task-complete");
    }
    @Test public void ambientKeepsChatAndWakesMaxOnTouchOrSpeech()throws Exception{
        Thread.sleep(1000);prefs.edit().putBoolean("muted",true).commit();
        scenario.onActivity(home->{try{invoke(home,"showKeyboard");TextChatPanel panel=(TextChatPanel)field(home,"chatPanel");panel.hideKeyboard();((BotView)field(home,"bot")).centerBot("Echo");}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(800);
        scenario.onActivity(home->{try{enterAmbient(home);assertTrue(diagnostic(home),(Boolean)field(home,"ambient"));assertEquals("Max",field(home,"selectedBot"));assertTrue(((BotView)field(home,"bot")).isOverview());assertEquals(View.INVISIBLE,((View)field(home,"chatLayer")).getVisibility());assertEquals(View.INVISIBLE,((View)field(home,"homeControls")).getVisibility());}catch(Exception e){throw new AssertionError(e);}});
        Thread.sleep(700);scenario.onActivity(home->{try{assertEquals(((View)field(home,"root")).getWidth(),((BotView)field(home,"bot")).getWidth());}catch(Exception e){throw new AssertionError(e);}});capture("ambient-lineup");
        scenario.onActivity(home->{try{tap(home,400,300);assertFalse((Boolean)field(home,"ambient"));assertEquals("Max",field(home,"selectedBot"));assertNotNull(field(home,"chatPanel"));assertEquals(View.VISIBLE,((View)field(home,"chatLayer")).getVisibility());enterAmbient(home);((BotConversation)field(home,"conversation")).recognized("Show Nova");assertFalse((Boolean)field(home,"ambient"));}catch(Exception e){throw new AssertionError(e);}});
        Thread.sleep(700);scenario.onActivity(home->{try{assertEquals("Nova",field(home,"selectedBot"));assertNotNull(field(home,"chatPanel"));}catch(Exception e){throw new AssertionError(e);}});assertEquals(0,posts.get());
    }
    private static void capture(String name)throws Exception{Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File folder=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("stage-features-tests");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    private static void tap(HomeActivity home,float x,float y){long t=android.os.SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(t,t+100,MotionEvent.ACTION_UP,x,y,0);home.dispatchTouchEvent(down);home.dispatchTouchEvent(up);down.recycle();up.recycle();}
    @Test public void sideTapsUnreadControlsAndRememberedChatSurviveRecreation()throws Exception{
        Thread.sleep(1500);
        scenario.onActivity(home->{try{invoke(home,"showKeyboard");}catch(Exception e){throw new AssertionError(e);}});
        Thread.sleep(800);
        scenario.onActivity(home->{try{
            BotView bot=(BotView)field(home,"bot");TextChatPanel panel=(TextChatPanel)field(home,"chatPanel");panel.hideKeyboard();
            BotActivityState activity=(BotActivityState)field(home,"activityState");activity.update(snapshot("Nova"));kitVersion.set(2);activity.update(snapshot("Nova"));invoke(home,"refreshActivity");assertTrue(activity.unread("Nova"));assertTrue(activity.working("Nova"));
            ((ListView)field(panel,"scroll")).setSelectionFromTop(12,-17);
        }catch(Exception e){throw new AssertionError(e);}});
        Thread.sleep(500);capture("unread-working-carousel");
        final JSONObject[] viewport=new JSONObject[1];
        scenario.onActivity(home->{try{
            TextChatPanel panel=(TextChatPanel)field(home,"chatPanel");viewport[0]=panel.saveViewport();assertFalse(viewport[0].optBoolean("latest"));
            BotView bot=(BotView)field(home,"bot");tap(home,bot.getWidth()*(.5f+.185f),bot.getHeight()*.46f);
        }catch(Exception e){throw new AssertionError(e);}});
        Thread.sleep(700);
        scenario.onActivity(home->{try{assertEquals("Scout",field(home,"selectedBot"));assertTrue(((BotView)field(home,"bot")).isOverview());assertNotNull(field(home,"chatPanel"));((BotView)field(home,"bot")).centerBot("Max");}catch(Exception e){throw new AssertionError(e);}});
        Thread.sleep(700);
        scenario.onActivity(home->{try{
            TextChatPanel panel=(TextChatPanel)field(home,"chatPanel");JSONObject restored=panel.saveViewport();assertEquals(viewport[0].getString("anchor"),restored.getString("anchor"));assertEquals(viewport[0].getInt("top"),restored.getInt("top"));
            invoke(home,"showDrawer");View root=home.findViewById(android.R.id.content);
            ((SeekBar)control(root,"Bot size")).setProgress(20);((SeekBar)control(root,"Spotlight brightness")).setProgress(135);((SeekBar)control(root,"Animation intensity")).setProgress(45);
            assertEquals(90,prefs.getInt("stage_size",0));assertEquals(135,prefs.getInt("stage_light",0));assertEquals(45,prefs.getInt("stage_motion",0));
        }catch(Exception e){throw new AssertionError(e);}});
        Thread.sleep(400);capture("stage-controls");
        scenario.onActivity(home->{try{invoke(home,"closeOverlay");}catch(Exception e){throw new AssertionError(e);}});
        scenario.recreate();Thread.sleep(1200);
        scenario.onActivity(home->{try{
            assertTrue(((BotView)field(home,"bot")).isOverview());assertEquals("Max",field(home,"selectedBot"));TextChatPanel panel=(TextChatPanel)field(home,"chatPanel");assertNotNull(panel);
            JSONObject restored=panel.saveViewport();assertEquals(viewport[0].getString("anchor"),restored.getString("anchor"));assertTrue(((BotActivityState)field(home,"activityState")).unread("Nova"));
            assertEquals(.9f,(Float)field(field(home,"bot"),"stageSize"),.001f);
        }catch(Exception e){throw new AssertionError(e);}});
        assertEquals("Navigation never posts bot messages",0,posts.get());
    }
    @Test public void newReplyStaysUnreadWhileReadingOlderMessagesAndClearsAtLatest()throws Exception{
        final JSONObject[] before=new JSONObject[1];
        Thread.sleep(1200);scenario.onActivity(home->{try{invoke(home,"showKeyboard");}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(800);
        scenario.onActivity(home->{try{((NativeActivityFeed)field(home,"nativeFeed")).stop();((BotOverviewFeed)field(home,"overviewFeed")).stop();TextChatPanel panel=(TextChatPanel)field(home,"chatPanel");panel.hideKeyboard();((ListView)field(panel,"scroll")).setSelectionFromTop(8,-12);}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(400);
        scenario.onActivity(home->{try{before[0]=((TextChatPanel)field(home,"chatPanel")).saveViewport();maxVersion.set(2);JSONObject data=snapshot("Max");((BotActivityState)field(home,"activityState")).update(data);((TextChatPanel)field(home,"chatPanel")).setNativeHistory(data.getJSONArray("entries"));invoke(home,"refreshActivity");}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(500);
        scenario.onActivity(home->{try{assertTrue(((BotActivityState)field(home,"activityState")).unread("Max"));TextChatPanel panel=(TextChatPanel)field(home,"chatPanel");assertEquals(before[0].getString("anchor"),panel.saveViewport().getString("anchor"));assertEquals(before[0].getInt("top"),panel.saveViewport().getInt("top"));ListView list=(ListView)field(panel,"scroll");list.setSelection(list.getCount()-1);}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(600);
        scenario.onActivity(home->{try{assertFalse("Unread clears only once the latest reply is visible",((BotActivityState)field(home,"activityState")).unread("Max"));}catch(Exception e){throw new AssertionError(e);}});
        assertEquals(0,posts.get());
    }
    private static Bitmap screen(){return InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();}
    private static int changedFloorPixels(Bitmap a,Bitmap b){
        int changed=0;for(int y=(int)(a.getHeight()*.43f);y<a.getHeight()*.66f;y+=2)for(int x=(int)(a.getWidth()*.32f);x<a.getWidth()*.68f;x+=2){int one=a.getPixel(x,y),two=b.getPixel(x,y);if(Math.abs(android.graphics.Color.red(one)-android.graphics.Color.red(two))>3)changed++;}return changed;
    }
    @Test public void workingSpotlightPulsesAndReturnsToSteady()throws Exception{
        Thread.sleep(1300);
        scenario.onActivity(home->{try{((NativeActivityFeed)field(home,"nativeFeed")).stop();((BotOverviewFeed)field(home,"overviewFeed")).stop();BotView bot=(BotView)field(home,"bot");bot.setActive(false);bot.setActivity("Max",true,false);bot.invalidate();}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(100);Bitmap one=screen();
        Thread.sleep(500);scenario.onActivity(home->{try{((BotView)field(home,"bot")).invalidate();}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(100);Bitmap two=screen();
        Thread.sleep(500);scenario.onActivity(home->{try{((BotView)field(home,"bot")).invalidate();}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(100);Bitmap three=screen();
        assertTrue("Working spotlight must visibly pulse",Math.max(changedFloorPixels(one,two),changedFloorPixels(two,three))>100);one.recycle();two.recycle();three.recycle();
        scenario.onActivity(home->{try{BotView bot=(BotView)field(home,"bot");bot.setActivity("Max",false,false);bot.invalidate();}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(100);one=screen();
        Thread.sleep(600);scenario.onActivity(home->{try{((BotView)field(home,"bot")).invalidate();}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(100);two=screen();
        assertEquals("Finished work returns to a steady spotlight",0,changedFloorPixels(one,two));one.recycle();two.recycle();
    }
    private String diagnostic(HomeActivity home)throws Exception{
        Method unlocked=HomeActivity.class.getDeclaredMethod("unlocked");unlocked.setAccessible(true);
        return " resumed="+field(home,"resumed")+" unlocked="+unlocked.invoke(home)+" muted="+prefs.getBoolean("muted",false)+" ambient="+field(home,"ambient")+" ime="+field(home,"chatImeBottom")+" pending="+field(home,"pendingVoiceBot")+" switching="+field(home,"switchingBot")+" busy="+((BotConversation)field(home,"conversation")).busy();
    }
    private void awaitListening()throws Exception{
        final boolean[] ready={false};long deadline=android.os.SystemClock.uptimeMillis()+5000;
        while(android.os.SystemClock.uptimeMillis()<deadline){scenario.onActivity(home->{try{ready[0]=((BotConversation)field(home,"conversation")).listening();}catch(Exception e){throw new AssertionError(e);}});if(ready[0])return;Thread.sleep(100);}
        scenario.onActivity(home->{try{fail("Recognizer did not become ready: "+field(home,"voiceTitle")+" / "+field(home,"voiceDetail")+diagnostic(home));}catch(Exception e){throw new AssertionError(e);}});
    }
    @Test public void voiceSwitchStartsAnotherRecognitionSessionAfterArrival()throws Exception{
        Thread.sleep(1000);
        scenario.onActivity(home->{try{invoke(home,"showKeyboard");((BotConversation)field(home,"conversation")).recognized("Let me talk to Nova");}catch(Exception e){throw new AssertionError(e);}});awaitListening();
        scenario.onActivity(home->{try{assertEquals("Nova",field(home,"selectedBot"));assertEquals("Speak to Nova",field(home,"voiceDetail"));assertNotNull(field(home,"chatPanel"));assertFalse(prefs.getBoolean("auto_listen",true));BotConversation conversation=(BotConversation)field(home,"conversation");conversation.stopAudio();conversation.recognized("Next bot");}catch(Exception e){throw new AssertionError(e);}});awaitListening();
        scenario.onActivity(home->{try{assertEquals("Echo",field(home,"selectedBot"));BotConversation conversation=(BotConversation)field(home,"conversation");conversation.stopAudio();((BotView)field(home,"bot")).setOverview(false);conversation.recognized("Let me talk to Max");}catch(Exception e){throw new AssertionError(e);}});awaitListening();
        scenario.onActivity(home->{try{assertEquals("Max",field(home,"selectedBot"));assertFalse((Boolean)field(home,"switchingBot"));assertEquals("Speak to Max",field(home,"voiceDetail"));((BotConversation)field(home,"conversation")).stopAudio();invoke(home,"closeOverlay");}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(400);
        scenario.onActivity(home->{try{assertNull(field(home,"chatPanel"));((BotConversation)field(home,"conversation")).recognized("Let me talk to Nova");}catch(Exception e){throw new AssertionError(e);}});awaitListening();
        scenario.onActivity(home->{try{assertEquals("Nova",field(home,"selectedBot"));assertEquals("Speak to Nova",field(home,"voiceDetail"));((BotConversation)field(home,"conversation")).stopAudio();}catch(Exception e){throw new AssertionError(e);}});
        assertEquals(0,posts.get());
    }
    @Test public void pendingVoiceContinuationCancelsOnPauseMuteAndManualSwitch()throws Exception{
        Thread.sleep(1000);scenario.onActivity(home->{try{((BotConversation)field(home,"conversation")).recognized("Let me talk to Nova");}catch(Exception e){throw new AssertionError(e);}});
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);Thread.sleep(900);
        scenario.onActivity(home->{try{assertEquals("",field(home,"pendingVoiceBot"));assertFalse(((BotConversation)field(home,"conversation")).listening());}catch(Exception e){throw new AssertionError(e);}});
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);Thread.sleep(500);
        scenario.onActivity(home->{try{assertFalse(((BotConversation)field(home,"conversation")).listening());((BotView)field(home,"bot")).setOverview(false);((BotConversation)field(home,"conversation")).recognized("Let me talk to Max");prefs.edit().putBoolean("muted",true).apply();}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(1500);
        scenario.onActivity(home->{try{assertFalse(((BotConversation)field(home,"conversation")).listening());prefs.edit().putBoolean("muted",false).apply();((BotView)field(home,"bot")).setOverview(true);((BotConversation)field(home,"conversation")).recognized("Let me talk to Nova");java.lang.reflect.Method select=HomeActivity.class.getDeclaredMethod("selectBot",String.class,int.class);select.setAccessible(true);select.invoke(home,"Echo",BotIdentity.color("Echo"));}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(900);
        scenario.onActivity(home->{try{assertEquals("Echo",field(home,"selectedBot"));assertFalse(((BotConversation)field(home,"conversation")).listening());BotConversation conversation=(BotConversation)field(home,"conversation");conversation.recognized("Let me talk to Max");conversation.recognized("Let me talk to Nova");}catch(Exception e){throw new AssertionError(e);}});awaitListening();
        scenario.onActivity(home->{try{assertEquals("Nova",field(home,"selectedBot"));((BotConversation)field(home,"conversation")).stopAudio();java.lang.reflect.Method select=HomeActivity.class.getDeclaredMethod("selectBot",String.class,int.class);select.setAccessible(true);select.invoke(home,"Max",BotIdentity.color("Max"));}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(900);
        scenario.onActivity(home->{try{assertEquals("Max",field(home,"selectedBot"));assertFalse("Manual switches do not start the microphone",((BotConversation)field(home,"conversation")).listening());}catch(Exception e){throw new AssertionError(e);}});
        assertEquals(0,posts.get());
    }
    @Test public void recognizedCommandsUseLocalNavigationAndPreserveChat()throws Exception{
        Thread.sleep(1200);scenario.onActivity(home->{try{invoke(home,"showKeyboard");((BotConversation)field(home,"conversation")).recognized("Let me talk to Nova");}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(700);
        scenario.onActivity(home->{try{assertEquals("Nova",field(home,"selectedBot"));assertNotNull(field(home,"chatPanel"));((BotConversation)field(home,"conversation")).recognized("Next bot");}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(700);
        scenario.onActivity(home->{try{assertEquals("Echo",field(home,"selectedBot"));((BotConversation)field(home,"conversation")).recognized("Previous bot");}catch(Exception e){throw new AssertionError(e);}});Thread.sleep(700);
        scenario.onActivity(home->{try{assertEquals("Nova",field(home,"selectedBot"));((BotConversation)field(home,"conversation")).recognized("Zoom in");assertFalse(((BotView)field(home,"bot")).isOverview());((BotConversation)field(home,"conversation")).recognized("Show lineup");assertTrue(((BotView)field(home,"bot")).isOverview());assertNotNull(control((View)field(home,"chatPanel"),"Voice navigation"));}catch(Exception e){throw new AssertionError(e);}});
        assertEquals(0,posts.get());
    }
}

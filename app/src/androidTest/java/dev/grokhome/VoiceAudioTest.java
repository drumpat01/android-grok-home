package dev.grokhome;

import android.content.*;
import android.media.AudioManager;
import android.os.ParcelFileDescriptor;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.json.*;

/** Real Android audio against a local mock socket; no xAI calls or saved audio. */
@RunWith(AndroidJUnit4.class)
public class VoiceAudioTest {
    private SharedPreferences prefs;
    private boolean hadAuto,hadMuted,previousAuto,previousMuted;
    @Before public void isolatePreferences(){
        prefs=InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("home",Context.MODE_PRIVATE);
        hadAuto=prefs.contains("auto_listen");hadMuted=prefs.contains("muted");
        previousAuto=prefs.getBoolean("auto_listen",false);previousMuted=prefs.getBoolean("muted",false);
        prefs.edit().putBoolean("auto_listen",false).putBoolean("muted",true).commit();
        firstRun=new FirstRunTestGate();
    }
    private FirstRunTestGate firstRun;
    @After public void restorePreferences(){
        firstRun.restore();
        SharedPreferences.Editor edit=prefs.edit();
        if(hadAuto)edit.putBoolean("auto_listen",previousAuto);else edit.remove("auto_listen");
        if(hadMuted)edit.putBoolean("muted",previousMuted);else edit.remove("muted");edit.commit();
    }
    @Test public void realAudioStreamsDrainsAndReleases() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        try(ParcelFileDescriptor permission=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("pm grant dev.grokhome android.permission.RECORD_AUDIO")){
            new java.io.FileInputStream(permission.getFileDescriptor()).read(new byte[1024]);
        }
        try(ActivityScenario<HomeActivity> activity=ActivityScenario.launch(HomeActivity.class);
            MockWebServer broker=new MockWebServer();MockWebServer realtime=new MockWebServer()){
            broker.start();realtime.start();
            AudioManager manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            int originalMode=manager.getMode();
            CountDownLatch ready=new CountDownLatch(1),mic=new CountDownLatch(1),played=new CountDownLatch(1),drained=new CountDownLatch(1);
            AtomicReference<WebSocket> socket=new AtomicReference<>();AtomicReference<String> failure=new AtomicReference<>();AtomicBoolean wasSpeaking=new AtomicBoolean();
            broker.enqueue(new MockResponse().setBody("{\"value\":\"TEST_EPHEMERAL_ONLY\"}"));
            realtime.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener(){
                @Override public void onOpen(WebSocket ws,Response response){socket.set(ws);}
                @Override public void onMessage(WebSocket ws,String raw){
                    try{String type=new JSONObject(raw).optString("type");
                        if(type.equals("session.update"))ws.send("{\"type\":\"session.updated\"}");
                        if(type.equals("input_audio_buffer.append"))mic.countDown();
                    }catch(JSONException e){failure.set("Malformed input event");}
                }
            }));
            VoiceClient client=new VoiceClient(new OkHttpClient(),
                (capture,speech,error)->new AndroidAudio(context,capture,speech,error),new VoiceClient.Listener(){
                    public void state(String title,String detail){if(title.equals("Listening"))ready.countDown();if(title.equals("Audio unavailable")||title.equals("Voice session failed"))failure.set(detail);}
                    public void speaking(boolean value){if(value){wasSpeaking.set(true);played.countDown();}else if(wasSpeaking.get())drained.countDown();}
                    public void transcript(String role,String text){}
                },realtime.url("/realtime").toString());
            try{
                client.start(broker.url("/").toString(),true,null);
                assertTrue("Audio initialization: "+failure.get(),ready.await(10,TimeUnit.SECONDS));
                assertTrue("Microphone bytes were not streamed",mic.await(5,TimeUnit.SECONDS));
                String silence=Base64.getEncoder().encodeToString(new byte[24000]);
                socket.get().send(new JSONObject().put("type","response.audio.delta").put("item_id","test-audio").put("delta",silence).toString());
                assertTrue("Playback did not start",played.await(5,TimeUnit.SECONDS));
                assertTrue("Playback never drained",drained.await(5,TimeUnit.SECONDS));
                client.setMicrophone(false);assertFalse(client.microphoneEnabled());
                client.stop();assertFalse(client.active());assertNull(failure.get());
                assertEquals("Communication mode was not restored",originalMode,manager.getMode());
            }finally{client.close();}
        }
    }

    @Test public void jitterIsPrimedAndShortRepliesDoNotReplay() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        try(ActivityScenario<HomeActivity> activity=ActivityScenario.launch(HomeActivity.class)){
            AtomicInteger starts=new AtomicInteger(),drains=new AtomicInteger();AtomicReference<String> failure=new AtomicReference<>();
            AndroidAudio audio=new AndroidAudio(context,bytes->{},value->{if(value)starts.incrementAndGet();else drains.incrementAndGet();},failure::set);
            try{
                audio.beginResponse();int initialDrains=drains.get();
                audio.play(new byte[1920]); // First packet: 40 ms, then a gap.
                Thread.sleep(100);
                assertFalse("A tiny first packet started before priming",audio.outputPlaying());
                assertEquals(0,starts.get());assertEquals(0,audio.underruns());
                audio.play(new byte[9600]); // 200 ms burst.
                for(int burst=0;burst<5;burst++){Thread.sleep(160);audio.play(new byte[9600]);}
                audio.finishResponse();
                long deadline=System.currentTimeMillis()+5000;
                while(drains.get()==initialDrains && System.currentTimeMillis()<deadline)Thread.sleep(20);
                assertEquals("Playback restarted during buffered stream",1,starts.get());
                assertTrue("Response did not drain",drains.get()>initialDrains);
                assertTrue("Repeated underruns while streaming: "+audio.underruns(),audio.underruns()<=1);
                Thread.sleep(250);assertFalse("Completed response restarted",audio.outputPlaying());assertEquals(1,starts.get());
                audio.beginResponse();int beforeShort=drains.get();
                audio.play(new byte[960]);audio.finishResponse();
                deadline=System.currentTimeMillis()+2000;
                while((starts.get()<2 || drains.get()==beforeShort) && System.currentTimeMillis()<deadline)Thread.sleep(20);
                assertEquals("A short reply was never flushed",2,starts.get());
                assertTrue(drains.get()>beforeShort);Thread.sleep(200);assertEquals("Short reply replayed",2,starts.get());
                audio.beginResponse();audio.play(new byte[960]);audio.interrupt();
                Thread.sleep(250);assertFalse(audio.outputPlaying());assertEquals("Interrupted prebuffer was played",2,starts.get());
                assertNull(failure.get());
            }finally{audio.close();}
        }
    }
}

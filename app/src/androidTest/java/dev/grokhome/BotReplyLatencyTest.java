package dev.grokhome;

import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import okhttp3.mockwebserver.*;
import org.json.*;

/** Real tablet TTS with staged mock bot replies; never sends to a hosted bot. */
@RunWith(AndroidJUnit4.class)
public class BotReplyLatencyTest {
    private final String bot="__latency_test";
    private BotConversation conversation;
    private Context context;
    private final AtomicBoolean released=new AtomicBoolean(),finalServed=new AtomicBoolean();
    private final AtomicInteger starts=new AtomicInteger();
    private final List<String> transcripts=new CopyOnWriteArrayList<>();
    private final CountDownLatch firstSpeech=new CountDownLatch(1),secondText=new CountDownLatch(1),done=new CountDownLatch(1);
    private void onMain(Runnable run){InstrumentationRegistry.getInstrumentation().runOnMainSync(run);}
    @After public void clean(){
        if(conversation!=null)onMain(()->conversation.close());
        if(context!=null){SharedPreferences prefs=context.getSharedPreferences("home",0);SharedPreferences.Editor edit=prefs.edit();for(String key:prefs.getAll().keySet())if(key.endsWith(bot))edit.remove(key);edit.commit();}
    }
    private MockWebServer start() throws Exception {return start(false);}
    private MockWebServer start(boolean textOnly) throws Exception {
        MockWebServer server=new MockWebServer();
        server.setDispatcher(new Dispatcher(){public MockResponse dispatch(RecordedRequest request){
            try{
                if(request.getMethod().equals("POST"))return new MockResponse().setResponseCode(202).setBody("{}");
                boolean finished=released.get();if(finished)finalServed.set(true);
                String reply="Hello from the first message."+(finished?"\n\nSecond message has arrived.":"");
                return new MockResponse().setBody(new JSONObject().put("status",finished?"finished":"running").put("reply",reply).toString());
            }catch(Exception e){return new MockResponse().setResponseCode(500);}
        }});server.start();final String testBase=server.url("/").toString().replaceAll("/$","");context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        onMain(()->{
            conversation=new BotConversation(context,new BotConversation.Listener(){
                public void state(String title,String detail){if(title.equals("Ready when you are") && finalServed.get())done.countDown();}
                public void speaking(boolean value){if(value){starts.incrementAndGet();firstSpeech.countDown();}}
                public void transcript(String role,String value){if(!role.equals("You")){transcripts.add(value);if(value.contains("Second message"))secondText.countDown();}}
            });conversation.select(testBase,bot);conversation.setTextChat(textOnly);
        });
        AtomicBoolean ready=new AtomicBoolean();
        for(int i=0;i<150;i++){onMain(()->ready.set(conversation.speechReady() && !conversation.voices().isEmpty()));if(ready.get())break;Thread.sleep(100);}
        assertTrue("Installed tablet TTS voice required",ready.get());
        onMain(()->{if(textOnly)conversation.sendText("Mock streaming test");else conversation.sendVoice("Mock streaming test");});return server;
    }
    @Test public void startsSpeechBeforeTurnEndsAndDoesNotRepeatPolledText() throws Exception {
        try(MockWebServer server=start()){
            assertTrue("First speech must start while the turn is running",firstSpeech.await(15,TimeUnit.SECONDS));
            assertFalse("Final reply was not yet released",finalServed.get());
            Thread.sleep(650);assertEquals(Collections.singletonList("Hello from the first message."),transcripts);
            released.set(true);assertTrue(secondText.await(5,TimeUnit.SECONDS));assertTrue(done.await(20,TimeUnit.SECONDS));
            assertEquals(Arrays.asList("Hello from the first message.","Second message has arrived."),transcripts);
        }
    }
    @Test public void textChatStaysSilentEvenIfClosedBeforeFinalMessage() throws Exception {
        try(MockWebServer server=start(true)){
            for(int i=0;i<50 && transcripts.isEmpty();i++)Thread.sleep(100);
            assertEquals(Collections.singletonList("Hello from the first message."),transcripts);
            assertEquals(0,starts.get());
            onMain(()->conversation.setTextChat(false));released.set(true);
            assertTrue(secondText.await(5,TimeUnit.SECONDS));assertTrue(done.await(5,TimeUnit.SECONDS));
            assertEquals("Closing text chat must not turn its reply into speech",0,starts.get());assertEquals(2,transcripts.size());
        }
    }
    @Test public void typedMessageStaysSilentAfterChatClosesAndConversationResumes() throws Exception {
        try(MockWebServer server=start(true)){
            for(int i=0;i<50 && transcripts.isEmpty();i++)Thread.sleep(100);
            assertEquals(Collections.singletonList("Hello from the first message."),transcripts);
            onMain(()->{conversation.setTextChat(false);conversation.pause();conversation.resume();});
            released.set(true);assertTrue(secondText.await(5,TimeUnit.SECONDS));assertTrue(done.await(5,TimeUnit.SECONDS));
            assertEquals("Typed replies never start tablet speech",0,starts.get());assertEquals(2,transcripts.size());
        }
    }
    @Test public void interruptKeepsLaterTextInHistoryWithoutRestartingSpeech() throws Exception {
        try(MockWebServer server=start()){
            assertTrue(firstSpeech.await(15,TimeUnit.SECONDS));onMain(()->conversation.interruptSpeech());
            int before=starts.get();released.set(true);assertTrue(secondText.await(5,TimeUnit.SECONDS));Thread.sleep(1000);
            assertEquals("Later messages must stay silent after interrupt",before,starts.get());assertEquals(2,transcripts.size());
        }
    }
}

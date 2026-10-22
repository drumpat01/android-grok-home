package dev.grokhome;

import org.junit.*;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.json.*;

public class VoiceClientTest {
    private MockWebServer broker, realtime;
    private VoiceClient client;
    private volatile WebSocket serverSocket;
    private volatile FakeAudio audio;
    private final List<JSONObject> sent = new CopyOnWriteArrayList<>();
    private final List<String> states = new CopyOnWriteArrayList<>();
    private final List<String> details = new CopyOnWriteArrayList<>();
    private final List<String> transcripts = new CopyOnWriteArrayList<>();
    private final List<Boolean> speaking = new CopyOnWriteArrayList<>();
    private class FakeAudio implements VoiceClient.AudioIO {
        Consumer<byte[]> capture; Consumer<Boolean> speech;
        volatile boolean mic,closed;volatile int played,interrupted;
        FakeAudio(Consumer<byte[]> capture,Consumer<Boolean> speech){this.capture=capture;this.speech=speech;}
        public void microphone(boolean enabled){mic=enabled;}
        public void play(byte[] data){played+=data.length;speech.accept(true);}
        public int interrupt(){interrupted++;return 320;}
        public void close(){closed=true;mic=false;}
    }
    @Before public void setUp() throws Exception {
        broker=new MockWebServer();realtime=new MockWebServer();broker.start();realtime.start();
        realtime.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener(){
            public void onOpen(WebSocket ws,Response r){serverSocket=ws;}
            public void onMessage(WebSocket ws,String raw){
                try{JSONObject e=new JSONObject(raw);sent.add(e);
                    if(e.optString("type").equals("session.update"))ws.send("{\"type\":\"session.updated\"}");
                }catch(JSONException error){throw new AssertionError(error);}
            }
        }));
        client=new VoiceClient(new OkHttpClient.Builder().readTimeout(2,TimeUnit.SECONDS).build(),
            (capture,speech,failure)->{audio=new FakeAudio(capture,speech);return audio;},new VoiceClient.Listener(){
                public void state(String title,String detail){states.add(title);details.add(detail);}
                public void speaking(boolean value){speaking.add(value);}
                public void transcript(String role,String text){transcripts.add(role+":"+text);}
            },realtime.url("/realtime").toString());
        client.setBotName("Nova");
    }
    @After public void tearDown() throws Exception {client.close();broker.shutdown();realtime.shutdown();}
    private void token(){broker.enqueue(new MockResponse().setBody("{\"value\":\"TEST_EPHEMERAL\"}"));}
    private void start(boolean mic) throws Exception {token();client.start(broker.url("/").toString(),mic,null);await(()->audio!=null);await(()->states.contains(mic?"Listening":"Ready when you are"));}
    private static void await(BooleanSupplier predicate) throws Exception {long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!predicate.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(10);assertTrue("Timed out",predicate.getAsBoolean());}
    private boolean sent(String type){return sent.stream().anyMatch(e->e.optString("type").equals(type));}
    private void event(String raw){assertTrue(serverSocket.send(raw));}
    @Test public void missingKeyStopsBeforeAudioOrWebSocket() throws Exception {
        broker.enqueue(new MockResponse().setResponseCode(503).setBody("{\"error\":\"key_missing\"}"));
        client.start(broker.url("/").toString(),true,null);await(()->states.contains("Add your xAI API key"));
        assertFalse(client.active());assertNull(audio);assertNull(serverSocket);
    }
    @Test public void authFailureIsActionable() throws Exception {
        broker.enqueue(new MockResponse().setResponseCode(502).setBody("{\"error\":\"xai_auth\"}"));
        client.start(broker.url("/").toString(),true,null);await(()->states.contains("Check your xAI API key"));assertFalse(client.active());
    }
    @Test public void duplexAudioAndTranscripts() throws Exception {
        start(true);assertTrue(audio.mic);
        RecordedRequest tokenRequest=broker.takeRequest(1,TimeUnit.SECONDS);assertEquals("POST",tokenRequest.getMethod());assertEquals("{}",tokenRequest.getBody().readUtf8());
        RecordedRequest socketRequest=realtime.takeRequest(1,TimeUnit.SECONDS);assertEquals("Bearer TEST_EPHEMERAL",socketRequest.getHeader("Authorization"));
        audio.capture.accept(new byte[]{1,0,2,0});await(()->sent("input_audio_buffer.append"));
        event("{\"type\":\"response.created\",\"response\":{\"id\":\"r1\"}}");
        event("{\"type\":\"response.output_audio.delta\",\"response_id\":\"r1\",\"item_id\":\"i1\",\"delta\":\"AQACAA==\"}");
        event("{\"type\":\"conversation.item.input_audio_transcription.completed\",\"item_id\":\"u1\",\"transcript\":\"Hello\"}");
        event("{\"type\":\"response.output_audio_transcript.done\",\"item_id\":\"i1\",\"transcript\":\"Hi\"}");
        await(()->audio.played==4 && transcripts.size()==2);assertTrue(speaking.contains(true));
        assertTrue(details.contains("Tap Nova to stop"));
        assertFalse(details.contains("Tap the sphere to interrupt"));
        client.setMicrophone(false);assertFalse(audio.mic);await(()->sent("input_audio_buffer.clear"));
    }
    @Test public void interruptionFlushesAndTruncatesAtHeardAudio() throws Exception {
        start(true);
        event("{\"type\":\"response.created\",\"response\":{\"id\":\"r1\"}}");
        event("{\"type\":\"response.audio.delta\",\"response_id\":\"r1\",\"item_id\":\"i1\",\"delta\":\"AQACAA==\"}");
        await(()->audio.played==4);
        event("{\"type\":\"input_audio_buffer.speech_started\"}");await(()->sent("conversation.item.truncate"));
        JSONObject truncate=sent.stream().filter(e->e.optString("type").equals("conversation.item.truncate")).findFirst().get();assertEquals(320,truncate.getInt("audio_end_ms"));
        event("{\"type\":\"response.audio.delta\",\"response_id\":\"r1\",\"item_id\":\"i1\",\"delta\":\"AQACAA==\"}");
        event("{\"type\":\"response.created\",\"response\":{\"id\":\"r2\"}}");
        event("{\"type\":\"response.audio.delta\",\"response_id\":\"r2\",\"item_id\":\"i2\",\"delta\":\"AQACAA==\"}");
        await(()->audio.played==8);assertTrue(audio.interrupted>0);
    }
    @Test public void stopDuringTokenFetchCannotReopenSession() throws Exception {
        broker.enqueue(new MockResponse().setBody("{\"value\":\"TEST_EPHEMERAL\"}").setBodyDelay(500,TimeUnit.MILLISECONDS));
        client.start(broker.url("/").toString(),true,null);assertNotNull(broker.takeRequest(1,TimeUnit.SECONDS));client.stop();
        Thread.sleep(650);assertFalse(client.active());assertNull(audio);assertNull(serverSocket);assertEquals("Ready when you are",states.get(states.size()-1));
    }
    @Test public void stoppedSessionIgnoresLateAudioCallbacks() throws Exception {
        start(true);FakeAudio old=audio;client.stop();int count=speaking.size();old.capture.accept(new byte[4]);old.speech.accept(true);
        assertTrue(old.closed);assertFalse(client.active());assertEquals(count,speaking.size());
    }
    @Test public void keyboardWorksWithoutMicrophone() throws Exception {
        token();client.text(broker.url("/").toString(),"Hello from keyboard");await(()->sent("response.create"));
        assertFalse(audio.mic);assertTrue(transcripts.contains("You:Hello from keyboard"));assertTrue(sent("conversation.item.create"));
    }
    @Test public void malformedAudioClosesSession() throws Exception {
        start(true);event("{\"type\":\"response.audio.delta\",\"delta\":\"BAD!\"}");await(()->states.contains("Voice session failed"));assertTrue(audio.closed);
    }
    @Test public void serverAddressValidation(){
        assertEquals("http://127.0.0.1:8766/session",VoiceClient.sessionUrl("http://127.0.0.1:8766"));
        assertEquals("http://192.168.1.20:8787/session",VoiceClient.sessionUrl("http://192.168.1.20:8787"));
        assertEquals("http://172.16.0.1/session",VoiceClient.sessionUrl("http://172.16.0.1"));
        assertEquals("http://10.0.0.1/session",VoiceClient.sessionUrl("http://10.0.0.1"));
        for(String invalid:Arrays.asList("http://example.com","http://8.8.8.8","http://172.32.0.1","http://192.168.1.20.example.com","https://user:pass@example.com","https://example.com/wrong","bad")){
            try{VoiceClient.sessionUrl(invalid);fail("Accepted "+invalid);}catch(IllegalArgumentException expected){}
        }
    }
}

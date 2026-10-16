package dev.grokhome;

import org.junit.*;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;
import org.json.*;

/** Opt-in live diagnostic: reports audio metadata only; never logs keys or audio. */
public class LiveAudioDiagnosticTest {
    @Test public void inspectLiveAudioFormat() throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("GROK_LIVE_AUDIO_CHECK")));
        OkHttpClient http=new OkHttpClient.Builder().readTimeout(30,TimeUnit.SECONDS).build();
        String token;
        try(Response r=http.newCall(new Request.Builder().url("http://127.0.0.1:8787/session")
            .post(RequestBody.create("{}",MediaType.get("application/json"))).build()).execute()){
            assertTrue("Local token broker failed",r.isSuccessful());token=new JSONObject(r.body().string()).getString("value");
        }
        CountDownLatch finished=new CountDownLatch(1);
        Map<String,Integer> counts=new ConcurrentHashMap<>();
        List<Integer> sizes=new CopyOnWriteArrayList<>();
        long[] stats=new long[5];byte[][] last=new byte[1][];
        WebSocket socket=http.newWebSocket(new Request.Builder().url("wss://api.x.ai/v1/realtime?model=grok-voice-latest")
            .header("Authorization","Bearer "+token).build(),new WebSocketListener(){
            public void onOpen(WebSocket ws,Response r){
                JSONObject format=VoiceClient.obj("format",VoiceClient.obj("type","audio/pcm","rate",24000));
                ws.send(VoiceClient.obj("type","session.update","session",VoiceClient.obj("voice","eve","turn_detection",VoiceClient.obj("type","server_vad"),"audio",VoiceClient.obj("input",format,"output",format))).toString());
            }
            public void onMessage(WebSocket ws,String raw){try{
                JSONObject e=new JSONObject(raw);String type=e.optString("type");counts.merge(type,1,Integer::sum);
                if(type.equals("session.updated")){
                    System.out.println("NEGOTIATED_AUDIO="+e.getJSONObject("session").optJSONObject("audio"));
                    ws.send(VoiceClient.obj("type","conversation.item.create","item",VoiceClient.obj("type","message","role","user","content",new JSONArray().put(VoiceClient.obj("type","input_text","text","Say the numbers one through five clearly.")))).toString());
                    ws.send("{\"type\":\"response.create\"}");
                }
                if(type.equals("response.audio.delta")||type.equals("response.output_audio.delta")){
                    byte[] pcm=Base64.getDecoder().decode(e.getString("delta"));long now=System.nanoTime();
                    if(stats[1]!=0)stats[2]=Math.max(stats[2],(now-stats[1])/1000000);
                    if(last[0]!=null && Arrays.equals(last[0],pcm))stats[3]++;
                    stats[0]+=pcm.length;stats[1]=now;last[0]=pcm;sizes.add(pcm.length);
                    if((pcm.length&1)!=0)stats[4]++;
                }
                if(type.equals("response.done")||type.equals("error"))finished.countDown();
            }catch(Exception error){finished.countDown();}}
            public void onFailure(WebSocket ws,Throwable error,Response r){finished.countDown();}
        });
        try{
            assertTrue("Live diagnostic timed out",finished.await(35,TimeUnit.SECONDS));
            System.out.println("EVENT_COUNTS="+counts);
            System.out.println("CHUNK_BYTES="+sizes);
            System.out.println("TOTAL_BYTES="+stats[0]+" MAX_GAP_MS="+stats[2]+" ADJACENT_DUPLICATES="+stats[3]+" ODD_CHUNKS="+stats[4]);
            assertTrue("No live audio returned",stats[0]>0);
        }finally{socket.cancel();http.dispatcher().executorService().shutdownNow();http.connectionPool().evictAll();}
    }
}

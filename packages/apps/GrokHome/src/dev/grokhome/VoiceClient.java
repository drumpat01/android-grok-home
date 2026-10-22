package dev.grokhome;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import okhttp3.*;
import org.json.*;

/** Realtime transport. All session mutations are serialized; stale callbacks are ignored. */
public final class VoiceClient implements AutoCloseable {
    public interface Listener {
        void state(String title, String detail);
        void speaking(boolean speaking);
        void transcript(String role, String text);
    }
    public interface AudioIO {
        void microphone(boolean enabled);
        void play(byte[] pcm);
        default void beginResponse() {}
        default void finishResponse() {}
        int interrupt();
        void close();
    }
    public interface AudioFactory {
        AudioIO create(Consumer<byte[]> capture, Consumer<Boolean> speaking, Consumer<String> failure);
    }
    private static final MediaType JSON = MediaType.get("application/json");
    private final OkHttpClient http;
    private final Listener listener;
    private final AudioFactory audioFactory;
    private final String realtimeUrl;
    private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "voice-timers"); t.setDaemon(true); return t;
    });
    private Session current;
    private String botName = BotIdentity.PRIMARY;

    private static final class Session {
        Call tokenCall; WebSocket socket; AudioIO audio;
        boolean ready, mic, responseActive, receivedAudio;
        String pendingText, itemId, responseId;
        int contentIndex;
        ScheduledFuture<?> timeout, limit;
        final Set<String> transcripts = new HashSet<>();
        final Set<String> cancelled = new HashSet<>();
    }
    public VoiceClient(AudioFactory factory, Listener listener) {
        this(new OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS).pingInterval(20, TimeUnit.SECONDS).build(),
            factory, listener, "wss://api.x.ai/v1/realtime?model=grok-voice-latest");
    }
    VoiceClient(OkHttpClient http, AudioFactory factory, Listener listener, String realtimeUrl) {
        this.http=http;audioFactory=factory;this.listener=listener;this.realtimeUrl=realtimeUrl;
    }
    public static String sessionUrl(String base) {
        HttpUrl url = HttpUrl.parse(base.trim());
        if (url == null || !url.username().isEmpty() || !url.password().isEmpty()
            || url.query()!=null || url.fragment()!=null) throw new IllegalArgumentException("Invalid server address");
        boolean loopback = Arrays.asList("127.0.0.1", "localhost", "10.0.2.2").contains(url.host());
        if (!url.isHttps() && !loopback && !LocalNetwork.isPrivateIpv4(url.host())) throw new IllegalArgumentException("Use a home-network IP address or HTTPS");
        if (!url.encodedPath().equals("/")) throw new IllegalArgumentException("Enter the server address without a path");
        return url.newBuilder().addPathSegment("session").build().toString();
    }
    public synchronized void setBotName(String name) {
        if (name != null && !name.isEmpty()) botName = name;
    }
    public synchronized boolean active() { return current != null; }
    public synchronized boolean microphoneEnabled() { return current != null && current.mic; }
    public synchronized void start(String base, boolean microphone, String text) {
        stopInternal();
        final String endpoint;
        try { endpoint=sessionUrl(base); }
        catch(IllegalArgumentException e){listener.state("Check voice connection",e.getMessage());return;}
        Session s=new Session();s.mic=microphone;s.pendingText=text;current=s;
        listener.state("Connecting", "Connecting to Grok voice");
        s.timeout=timers.schedule(()->fail(s,"Connection timed out","Tap microphone to retry"),30,TimeUnit.SECONDS);
        s.tokenCall=http.newCall(new Request.Builder().url(endpoint).post(RequestBody.create("{}",JSON)).build());
        s.tokenCall.enqueue(new Callback(){
            @Override public void onFailure(Call call,IOException e){fail(s,"Voice server unavailable","Start the local voice server, then retry");}
            @Override public void onResponse(Call call,Response response){
                try(Response r=response){
                    String body=r.body()==null?"{}":r.body().string();
                    JSONObject data=new JSONObject(body);
                    if(!r.isSuccessful()){
                        String code=data.optString("error");
                        if(code.equals("key_missing"))fail(s,"Add your xAI API key","Add XAI_API_KEY to backend/.env on your computer");
                        else if(code.equals("xai_auth"))fail(s,"Check your xAI API key","Update the key in backend/.env");
                        else if(code.equals("xai_billing"))fail(s,"Check xAI billing","Your API account needs available credit");
                        else if(code.contains("rate_limit"))fail(s,"Please wait a moment","Voice requests are limited. Try again shortly");
                        else fail(s,"Voice connection failed","Check the local server and xAI account");
                        return;
                    }
                    String token=data.getString("value");
                    if(token.isEmpty())throw new JSONException("Empty token");
                    openSocket(s,token);
                }catch(Exception e){fail(s,"Voice connection failed","The voice server returned an invalid response");}
            }
        });
    }
    private synchronized void openSocket(Session s,String token){
        if(current!=s)return;
        s.socket=http.newWebSocket(new Request.Builder().url(realtimeUrl).header("Authorization","Bearer "+token).build(),new WebSocketListener(){
            @Override public void onOpen(WebSocket socket,Response response){
                synchronized(VoiceClient.this){if(current!=s){socket.close(1000,"Cancelled");return;}s.socket=socket;
                    JSONObject format=obj("format",obj("type","audio/pcm","rate",24000));
                    send(s,obj("type","session.update","session",obj("voice","eve",
                        "instructions","You are Grok, a helpful voice assistant on a tablet. Keep spoken replies concise and natural. You cannot control apps or device settings through this connection.",
                        "turn_detection",obj("type","server_vad"),"audio",obj("input",format,"output",format))));
                }
            }
            @Override public void onMessage(WebSocket socket,String text){event(s,text);}
            @Override public void onFailure(WebSocket socket,Throwable error,Response response){fail(s,"Voice disconnected","Check your connection and tap microphone to retry");}
            @Override public void onClosing(WebSocket socket,int code,String reason){socket.close(1000,"Closed");fail(s,"Voice session ended","Tap microphone to reconnect");}
        });
    }
    static JSONObject obj(Object... pairs){JSONObject j=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)j.put((String)pairs[i],pairs[i+1]);}catch(JSONException e){throw new IllegalArgumentException(e);}return j;}
    private boolean send(Session s,JSONObject event){return s.socket!=null && s.socket.send(event.toString());}
    private synchronized void event(Session s,String raw){
        if(current!=s)return;
        try{
            JSONObject e=new JSONObject(raw);String type=e.optString("type");
            switch(type){
            case "session.updated":
                if(s.ready)break;s.ready=true;s.timeout.cancel(false);
                try{s.audio=audioFactory.create(bytes->capture(s,bytes),speaking->speaking(s,speaking),error->fail(s,"Audio unavailable",error));}
                catch(RuntimeException error){fail(s,"Audio unavailable","Check microphone and speaker availability");return;}
                if(current!=s){s.audio.close();return;}
                s.audio.microphone(s.mic);
                if(current!=s)return;
                s.limit=timers.schedule(()->fail(s,"Voice session ended","Ten-minute session limit reached. Tap microphone to reconnect"),10,TimeUnit.MINUTES);
                idle(s);
                if(s.pendingText!=null){sendText(s,s.pendingText);s.pendingText=null;}
                break;
            case "input_audio_buffer.speech_started":
                interrupt(s,false);listener.state("Listening","Speak naturally · tap microphone to stop");break;
            case "input_audio_buffer.speech_stopped":listener.state("Thinking","Grok is responding");break;
            case "response.created":
                s.responseActive=true;s.responseId=e.optJSONObject("response")==null?null:e.getJSONObject("response").optString("id");
                if(s.audio!=null)s.audio.beginResponse();
                s.itemId=null;s.receivedAudio=false;break;
            case "response.output_audio.delta":case "response.audio.delta":
                if(!s.ready || s.cancelled.contains(e.optString("response_id")))break;
                s.itemId=e.optString("item_id",s.itemId);s.contentIndex=e.optInt("content_index",0);
                byte[] pcm=Base64.getDecoder().decode(e.getString("delta"));
                if((pcm.length&1)!=0)throw new IllegalArgumentException("Invalid PCM");
                s.receivedAudio=true;s.audio.play(pcm);break;
            case "response.output_audio.done":case "response.audio.done":
                if(s.audio!=null && !s.cancelled.contains(e.optString("response_id")))s.audio.finishResponse();break;
            case "response.done":
                s.responseActive=false;
                if(s.audio!=null)s.audio.finishResponse();
                JSONObject response=e.optJSONObject("response");
                if(response!=null && response.optString("status").equals("failed")){fail(s,"Grok could not respond","Check your xAI account and retry");return;}
                if(!s.receivedAudio)idle(s);
                break;
            case "conversation.item.input_audio_transcription.completed":
                transcript(s,"You",e.optString("transcript"),e.optString("item_id"));break;
            case "response.output_audio_transcript.done":case "response.audio_transcript.done":
                transcript(s,"Grok",e.optString("transcript"),e.optString("item_id"));break;
            case "response.output_text.done":case "response.text.done":
                transcript(s,"Grok",e.optString("text"),e.optString("item_id"));break;
            case "error":
                JSONObject error=e.optJSONObject("error");
                if(error!=null && error.optString("code").equals("response_cancel_not_active"))break;
                fail(s,"Grok reported an error","Tap microphone to start a new session");break;
            default:break;
            }
        }catch(Exception e){fail(s,"Voice session failed","Unable to process voice audio. Tap microphone to retry");}
    }
    private void transcript(Session s,String role,String text,String id){
        if(!text.trim().isEmpty() && s.transcripts.add(role+":"+id+":"+text))listener.transcript(role,text.trim());
    }
    private synchronized void capture(Session s,byte[] pcm){
        if(current!=s || !s.ready || !s.mic)return;
        if(s.socket.queueSize()>256000){fail(s,"Connection is too slow","Tap microphone to reconnect");return;}
        if(!send(s,obj("type","input_audio_buffer.append","audio",Base64.getEncoder().encodeToString(pcm))))
            fail(s,"Voice disconnected","Tap microphone to reconnect");
    }
    private synchronized void speaking(Session s,boolean value){
        if(current!=s)return;listener.speaking(value);
        if(value)listener.state("Speaking",BotTalkBack.speakingDetail(botName));else idle(s);
    }
    private void idle(Session s){listener.state(s.mic?"Listening":"Ready when you are",s.mic?"Speak naturally · tap microphone to stop":"Microphone off · tap to speak");}
    public synchronized void setMicrophone(boolean enabled){
        Session s=current;if(s==null)return;s.mic=enabled;
        if(s.audio!=null)s.audio.microphone(enabled);
        if(current!=s)return;
        if(s.ready){if(!enabled)send(s,obj("type","input_audio_buffer.clear"));idle(s);}
    }
    public synchronized void text(String base,String text){
        if(text.trim().isEmpty())return;
        if(current==null){start(base,false,text.trim());return;}
        if(!current.ready){listener.state("Connecting","Wait for the voice connection before sending");return;}
        sendText(current,text.trim());
    }
    private void sendText(Session s,String text){
        interrupt(s,true);
        JSONArray content=new JSONArray().put(obj("type","input_text","text",text));
        send(s,obj("type","conversation.item.create","item",obj("type","message","role","user","content",content)));
        send(s,obj("type","response.create"));listener.transcript("You",text);listener.state("Thinking","Grok is responding");
    }
    public synchronized void interrupt(){if(current!=null)interrupt(current,true);}
    private void interrupt(Session s,boolean manual){
        int heard=s.audio==null?0:s.audio.interrupt();listener.speaking(false);
        if(s.responseId!=null && s.responseActive){
            s.cancelled.add(s.responseId);
            if(manual)send(s,obj("type","response.cancel"));
        }
        if(s.itemId!=null){send(s,obj("type","conversation.item.truncate","item_id",s.itemId,"content_index",s.contentIndex,"audio_end_ms",heard));s.itemId=null;}
        if(s.ready)idle(s);
    }
    private synchronized void fail(Session s,String title,String detail){if(current!=s)return;stopInternal();listener.state(title,detail);}
    public synchronized void stop(){stopInternal();listener.state("Ready when you are","Microphone off · tap to speak");}
    private void stopInternal(){
        Session s=current;current=null;if(s==null)return;
        if(s.timeout!=null)s.timeout.cancel(false);if(s.limit!=null)s.limit.cancel(false);
        if(s.tokenCall!=null)s.tokenCall.cancel();if(s.socket!=null)s.socket.cancel();
        if(s.audio!=null)s.audio.close();listener.speaking(false);
    }
    @Override public synchronized void close(){stopInternal();timers.shutdownNow();http.dispatcher().cancelAll();http.connectionPool().evictAll();}
}

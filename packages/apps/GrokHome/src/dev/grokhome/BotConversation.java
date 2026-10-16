package dev.grokhome;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.*;
import android.speech.tts.*;
import java.util.*;
import okhttp3.*;
import org.json.*;

/** Talks to the user's existing Cursor bot; Android handles speech locally where available. */
final class BotConversation {
    interface Listener {
        void state(String title,String detail);
        void speaking(boolean value);
        void transcript(String role,String value);
        default boolean command(String spoken){return false;}
        default void routed(String bot,String request){}
        default void task(String phase,String detail){}
    }
    interface Roster { void result(JSONArray bots,String error); }
    private final Context context;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final OkHttpClient http=new OkHttpClient.Builder().callTimeout(15,java.util.concurrent.TimeUnit.SECONDS).build();
    private TextToSpeech tts;
    private boolean speechReady,listening,closed,textChat,foreground=true;
    interface RecognizerFactory {SpeechRecognizer create(Context context);}
    private RecognizerFactory recognizerFactory=BotConversation::defaultRecognizer;
    private SpeechRecognizer recognizer;
    private static SpeechRecognizer defaultRecognizer(Context context){return android.os.Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)?SpeechRecognizer.createOnDeviceSpeechRecognizer(context):SpeechRecognizer.createSpeechRecognizer(context);}
    void setRecognizerFactory(RecognizerFactory factory){stopAudio();recognizerFactory=factory;}
    private String server,name,job,utterance,lastSpoken="";
    private int generation,receivedChars,speechPart;
    private String silentJob="";
    private String routeRequest="";
    private Call poll;

    BotConversation(Context context,Listener listener){
        this.context=context;this.listener=listener;
        tts=new TextToSpeech(context,status->{if(closed)return;speechReady=status==TextToSpeech.SUCCESS;if(speechReady && tts!=null){speechReady=tts.setLanguage(Locale.US)>=0;applyVoice(savedVoice());}});
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            public void onStart(String id){main.post(()->{if(!closed && foreground && utterance!=null && id.startsWith(utterance+":")){android.util.Log.i("GrokHomeBots","Speech playback started");listener.speaking(true);listener.state(name+" is speaking","Tap the sphere to stop speech");}});}
            public void onDone(String id){main.post(()->{
                if(closed || !foreground || utterance==null || !id.startsWith(utterance+":"))return;
                listener.speaking(false);
                if(id.equals(utterance+":done")){android.util.Log.i("GrokHomeBots","Speech playback completed");utterance=null;listener.state("Ready when you are","Talk to "+name);}
            });}
            public void onError(String id){main.post(()->{if(!closed && utterance!=null && id.startsWith(utterance+":")){utterance=null;listener.speaking(false);listener.state("Reply received","Read it in History; Android speech is unavailable");}});}
        });
    }
    void select(String server,String name){stop();this.server=server;this.name=name;job=context.getSharedPreferences("home",0).getString("bot_job_"+name,null);lastSpoken=context.getSharedPreferences("home",0).getString("bot_spoken_"+name,"");
        android.content.SharedPreferences prefs=context.getSharedPreferences("home",0);
        receivedChars=job!=null && job.equals(prefs.getString("bot_read_job_"+name,""))?prefs.getInt("bot_read_chars_"+name,0):0;
        routeRequest=job!=null?prefs.getString("bot_route_"+name,""):"";
        silentJob=prefs.getString("bot_silent_job_"+name,"");
    }
    boolean listening(){return listening;}
    boolean busy(){return job!=null;}
    void setTextChat(boolean enabled){textChat=enabled;if(enabled)interruptSpeech();}
    boolean speechReady(){return speechReady;}
    String savedVoice(){return context.getSharedPreferences("home",0).getString("bot_voice_"+name,"");}
    List<Voice> voices(){
        ArrayList<Voice> result=new ArrayList<>();
        if(!speechReady || tts.getVoices()==null)return result;
        for(Voice voice:tts.getVoices())if(!voice.isNetworkConnectionRequired() && (voice.getFeatures()==null || !voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)))result.add(voice);
        result.sort(Comparator.comparing((Voice v)->!v.getLocale().getLanguage().equals("en")).thenComparing(v->v.getLocale().getDisplayName()).thenComparing(Voice::getName));
        return result;
    }
    private boolean applyVoice(String id){
        List<Voice> available=voices();Voice chosen=null;
        for(Voice v:available)if(v.getName().equals(id)){chosen=v;break;}
        if(chosen==null){
            Voice defaultVoice=tts.getDefaultVoice();
            for(Voice v:available)if(defaultVoice!=null && v.getName().equals(defaultVoice.getName())){chosen=v;break;}
            if(chosen==null)for(Voice v:available)if(v.getLocale().equals(Locale.US)){chosen=v;break;}
            if(chosen==null && !available.isEmpty())chosen=available.get(0);
        }
        return chosen!=null && tts.setVoice(chosen)==TextToSpeech.SUCCESS;
    }
    boolean previewVoice(String id){
        stopAudio();if(!speechReady || !applyVoice(id))return false;
        speakReply("Hello, I'm "+name+". This is my voice.","preview-"+UUID.randomUUID());return true;
    }
    void saveVoice(String id){context.getSharedPreferences("home",0).edit().putString("bot_voice_"+name,id).apply();}
    void finishVoicePreview(){stopAudio();if(speechReady)applyVoice(savedVoice());listener.state("Ready when you are","Talk to "+name);}
    void resume(){foreground=true;if(job!=null)pollJob(generation);}
    void pause(){foreground=false;generation++;if(poll!=null)poll.cancel();stopAudio();}
    void stop(){generation++;job=null;if(poll!=null)poll.cancel();stopAudio();}
    void stopAudio(){
        listening=false;utterance=null;if(recognizer!=null){SpeechRecognizer previous=recognizer;recognizer=null;previous.destroy();}
        if(tts!=null)tts.stop();listener.speaking(false);
    }
    void close(){closed=true;pause();if(tts!=null)tts.shutdown();http.dispatcher().cancelAll();}
    void interruptSpeech(){if(job!=null){silentJob=job;context.getSharedPreferences("home",0).edit().putString("bot_silent_job_"+name,job).apply();}stopAudio();listener.state("Ready when you are","Talk to "+name);}
    void listen(){
        stopAudio();
        if(!SpeechRecognizer.isRecognitionAvailable(context)){listener.state("Speech input unavailable","Use Keyboard, or enable an Android speech service");return;}
        recognizer=recognizerFactory.create(context);
        final SpeechRecognizer input=recognizer;
        recognizer.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle b){if(recognizer!=input || !foreground)return;listening=true;listener.state("Listening","Speak to "+name);}
            public void onBeginningOfSpeech(){}public void onRmsChanged(float f){}public void onBufferReceived(byte[] b){}
            public void onEndOfSpeech(){if(recognizer!=input || !foreground)return;listening=false;listener.state("One moment","Recognizing your message");}
            public void onError(int error){if(recognizer!=input || !foreground)return;listening=false;listener.state("Tap microphone to try again",error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS?"Allow microphone access":"You can also use Keyboard");}
            public void onResults(Bundle b){if(recognizer!=input || !foreground)return;listening=false;ArrayList<String> values=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(values!=null && !values.isEmpty())recognized(values.get(0));}
            public void onPartialResults(Bundle b){}public void onEvent(int type,Bundle b){}
        });
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US").putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
        try{recognizer.startListening(intent);}catch(RuntimeException error){listening=false;listener.state("Speech input unavailable","Use Keyboard, or check Android speech settings");}
    }
    void recognized(String spoken){if(!listener.command(spoken))sendVoice(spoken);}
    void route(String request){if(job!=null){listener.state(name+" is busy","Try Who can help after this task finishes");return;}routeRequest=request;send(request,false,false);}
    void sendText(String message){send(message,false,false);}
    void sendVoice(String message){send(message,false,true);}
    private void send(String message,boolean check,boolean audioReply){
        if(job!=null){listener.state(name+" is thinking","Wait for this reply before sending another message");return;}
        stopAudio();final int ticket=++generation;String id=UUID.randomUUID().toString();
        JSONObject data=new JSONObject();try{data.put("bot",name).put("request_id",id).put("action",!routeRequest.isEmpty()?"route":check?"check":"ask");if(!check)data.put("message",message);}catch(JSONException impossible){return;}
        listener.state(name+" is thinking","Waiting for your bot");
        // Save the request id before sending, so leaving Home doesn't resend a message.
        // Reply mode belongs to the submitted message, not the panel's visibility
        // or the recognizer (which stops listening before a voice reply arrives).
        job=id;receivedChars=0;silentJob=audioReply?"":id;
        context.getSharedPreferences("home",0).edit().putString("bot_job_"+name,id).putString("bot_read_job_"+name,id).putInt("bot_read_chars_"+name,0).putString("bot_silent_job_"+name,silentJob).apply();
        context.getSharedPreferences("home",0).edit().putString("bot_route_"+name,routeRequest).putLong("bot_started_"+name,System.currentTimeMillis()).putString("bot_task_"+name,message==null?"Checking reply":message.substring(0,Math.min(160,message.length()))).apply();
        listener.task("working",routeRequest.isEmpty()?"Waiting for bot reply":"Finding the right bot");
        if(!check && routeRequest.isEmpty())listener.transcript("You",message);
        Request request=new Request.Builder().url(server+"/bot/jobs").post(RequestBody.create(data.toString(),MediaType.get("application/json"))).build();
        http.newCall(request).enqueue(new Callback(){
            public void onFailure(Call call,java.io.IOException e){main.post(()->{if(ticket==generation && foreground){listener.task("error","Connection interrupted · check reply");listener.state("Connection interrupted","Choose Check reply from Bots; don't resend yet");}});}
            public void onResponse(Call call,Response response){try(Response r=response){String body=r.body().string();JSONObject result=new JSONObject(body);main.post(()->{if(ticket!=generation || !foreground)return;if(!r.isSuccessful()){clearJob();listener.task("error","Bot unavailable");listener.state("Bot unavailable",result.optString("error","Check the computer bridge"));}else pollJob(ticket);});}catch(Exception e){onFailure(call,new java.io.IOException(e));}}
        });
    }
    void cancelTask(){
        final String target=name,expectedJob=job;final int ticket=generation;
        JSONObject data=new JSONObject();try{data.put("bot",target);if(expectedJob!=null)data.put("request_id",expectedJob);}catch(JSONException impossible){return;}
        listener.task("working","Stopping task…");
        http.newCall(new Request.Builder().url(server+"/bot/cancel").post(RequestBody.create(data.toString(),MediaType.get("application/json"))).build()).enqueue(new Callback(){
            public void onFailure(Call call,java.io.IOException error){main.post(()->{if(ticket==generation && foreground)listener.task("error","Couldn’t confirm stop · check reply");});}
            public void onResponse(Call call,Response response){try(Response r=response){boolean success=r.isSuccessful();main.post(()->{
                if(ticket!=generation || !foreground || !target.equals(name))return;
                if(!success){listener.task("error","Couldn’t stop task · try again");return;}
                if(poll!=null)poll.cancel();generation++;clearJob();routeRequest="";stopAudio();
                listener.task("cancelled","Task cancelled");listener.state("Task cancelled","Talk to "+name);
            });}}
        });
    }
    private void clearJob(){job=null;routeRequest="";context.getSharedPreferences("home",0).edit().remove("bot_job_"+name).remove("bot_route_"+name).apply();}
    void checkReply(){if(job!=null){pollJob(generation);return;}send(null,true,false);}
    private void pollJob(int ticket){
        if(job==null || closed || !foreground || ticket!=generation)return;
        final String requestedJob=job;
        poll=http.newCall(new Request.Builder().url(server+"/bot/jobs/"+requestedJob).build());
        poll.enqueue(new Callback(){
            public void onFailure(Call call,java.io.IOException e){main.post(()->{if(ticket==generation && foreground && requestedJob.equals(job)){listener.task("error","Connection interrupted · check reply");listener.state("Connection interrupted","Open Bots → Check reply when the computer is available");}});}
            public void onResponse(Call call,Response response){try(Response r=response){JSONObject data=new JSONObject(r.body().string());boolean success=r.isSuccessful();main.post(()->{
                if(ticket!=generation || !foreground || closed || !requestedJob.equals(job))return;
                if(!success){clearJob();listener.task("error","Check reply");listener.state("Check reply","Open Bots → Check reply to recover the conversation");return;}
                String status=data.optString("status");
                String reply=data.optString("reply","");String completed=job;
                // Each BDK result contains whole delivered messages. Consume the
                // stable prefix once, without waiting for all bot work to finish.
                if(routeRequest.isEmpty() && !completed.equals(lastSpoken) && reply.length()>receivedChars){
                    String delta=reply.substring(receivedChars).trim();receivedChars=reply.length();
                    context.getSharedPreferences("home",0).edit().putString("bot_read_job_"+name,completed).putInt("bot_read_chars_"+name,receivedChars).apply();
                    if(!delta.isEmpty()){
                        listener.transcript(name,delta);
                        if(speechReady && !textChat && !completed.equals(silentJob))queueSpeech(delta,completed);
                        else listener.state("Reply received",textChat?"Read "+name+"'s reply in chat":"Open Keyboard to read "+name+"'s reply");
                    }
                }
                if(status.equals("running") || status.equals("cancelling")){listener.task("working",data.optString("progress","Waiting for bot reply"));if(utterance==null)listener.state(name+" is working","Waiting for your bot");main.postDelayed(()->pollJob(ticket),250);return;}
                String routedRequest=routeRequest;routeRequest="";
                clearJob();
                listener.task(status.equals("finished")?"complete":status.equals("cancelled")?"cancelled":"error",status.equals("finished")?"Task complete":status.equals("cancelled")?"Task cancelled":"Check reply");
                if(!routedRequest.isEmpty()){
                    String target=data.optString("route_bot");
                    if(status.equals("finished") && BotIdentity.index(target)>=0)listener.routed(target,routedRequest);
                    else listener.state("Couldn’t choose a bot","Try Who can help again, or choose a bot");
                    return;
                }
                lastSpoken=completed;context.getSharedPreferences("home",0).edit().putString("bot_spoken_"+name,completed).apply();
                if(completed.equals(utterance))finishSpeech(completed);
                else listener.state(status.equals("cancelled")?"Task cancelled":status.equals("error")?"Check reply":"Ready when you are",status.equals("error")?"Your bot may still be working; choose Check reply":"Talk to "+name);

            });}catch(Exception e){onFailure(call,new java.io.IOException(e));}}
        });
    }
    private void speakReply(String reply,String id){if(queueSpeech(reply,id))finishSpeech(id);}
    private void finishSpeech(String id){
        if(tts.playSilentUtterance(1,TextToSpeech.QUEUE_ADD,id+":done")==TextToSpeech.ERROR){utterance=null;listener.speaking(false);listener.state("Reply received","Read it in History; Android speech is unavailable");}
    }
    private boolean queueSpeech(String reply,String id){
        boolean append=id.equals(utterance);
        if(!append){
            if(!id.startsWith("preview-") && !applyVoice(savedVoice())){listener.state("Reply received","Install a tablet voice in Android speech settings; read this reply in History");return false;}
            utterance=id;speechPart=0;
        }
        int start=0,limit=Math.min(TextToSpeech.getMaxSpeechInputLength()-1,700);
        while(start<reply.length()){
            int end=Math.min(start+limit,reply.length());
            if(end<reply.length()){
                int space=reply.lastIndexOf(' ',end);if(space>start+limit/2)end=space;
                if(end>start && Character.isHighSurrogate(reply.charAt(end-1)))end--;
            }
            String chunk=reply.substring(start,end);String tag=id+":part"+(speechPart++);
            if(tts.speak(chunk,!append && start==0?TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD,null,tag)==TextToSpeech.ERROR){utterance=null;listener.speaking(false);listener.state("Reply received","Read it in History; Android speech is unavailable");return false;}
            start=end;
        }
        return true;
    }
    void roster(String base,Roster receiver){
        http.newCall(new Request.Builder().url(base+"/bots").build()).enqueue(new Callback(){
            public void onFailure(Call call,java.io.IOException e){main.post(()->{if(!closed)receiver.result(null,"Start Grok Home Voice on your computer");});}
            public void onResponse(Call call,Response response){try(Response r=response){JSONArray bots=new JSONObject(r.body().string()).getJSONArray("bots");main.post(()->{if(!closed)receiver.result(bots,null);});}catch(Exception e){onFailure(call,new java.io.IOException(e));}}
        });
    }
}

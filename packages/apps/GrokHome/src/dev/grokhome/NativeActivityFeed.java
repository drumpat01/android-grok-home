package dev.grokhome;

import android.os.*;
import okhttp3.*;
import org.json.*;
import java.util.concurrent.TimeUnit;

/** Foreground-only, read-only sync. Never sends bot messages or invokes TTS. */
final class NativeActivityFeed {
    interface Listener {void update(JSONObject snapshot);}
    private final Handler main=new Handler(Looper.getMainLooper());
    private final OkHttpClient http=new OkHttpClient.Builder().callTimeout(35,TimeUnit.SECONDS).build();
    private final Listener listener;
    private Call call;
    private int generation;
    private boolean active;
    NativeActivityFeed(Listener listener){this.listener=listener;}
    void start(String base,String name){stop();active=true;fetch(base,name,generation);}
    void stop(){active=false;generation++;main.removeCallbacksAndMessages(null);if(call!=null)call.cancel();}
    void close(){stop();http.dispatcher().cancelAll();
        // Closing pooled TLS sockets can write a close-notify packet. Keep it off the UI thread.
        http.dispatcher().executorService().execute(()->http.connectionPool().evictAll());
    }
    private void fetch(String base,String name,int ticket){
        if(!active || ticket!=generation)return;
        HttpUrl url=HttpUrl.parse(base+"/bot/activity");if(url==null)return;
        call=http.newCall(new Request.Builder().url(url.newBuilder().addQueryParameter("bot",name).build()).build());
        call.enqueue(new Callback(){
            public void onFailure(Call call,java.io.IOException error){deliver(null);}
            public void onResponse(Call call,Response response){
                JSONObject data=null;try(Response r=response){if(r.isSuccessful())data=new JSONObject(r.body().string());}catch(Exception ignored){}
                deliver(data);
            }
            private void deliver(JSONObject value){main.post(()->{
                if(!active || ticket!=generation)return;
                JSONObject result=value;
                if(result==null || !name.equals(result.optString("bot"))){result=new JSONObject();try{result.put("bot",name).put("stale",true);}catch(JSONException ignored){}}
                listener.update(result);
                main.postDelayed(()->fetch(base,name,ticket),result.optBoolean("stale")?20000:4000);
            });}
        });
    }
}

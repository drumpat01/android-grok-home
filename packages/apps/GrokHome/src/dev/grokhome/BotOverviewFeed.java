package dev.grokhome;

import android.os.*;
import okhttp3.*;
import org.json.*;
import java.util.concurrent.TimeUnit;

/** One bounded, staggered read at a time. Stops when Home is not in the foreground. */
final class BotOverviewFeed {
    interface Listener {void update(JSONObject snapshot);}
    private final Handler main=new Handler(Looper.getMainLooper());
    private final OkHttpClient http=new OkHttpClient.Builder().callTimeout(12,TimeUnit.SECONDS).build();
    private final Listener listener;
    private Call call;private int generation,index;private boolean active;
    BotOverviewFeed(Listener listener){this.listener=listener;}
    void start(String base){stop();active=true;fetch(base,generation);}
    void stop(){active=false;generation++;main.removeCallbacksAndMessages(null);if(call!=null)call.cancel();}
    void close(){stop();http.dispatcher().cancelAll();http.dispatcher().executorService().execute(()->http.connectionPool().evictAll());}
    private void fetch(String base,int ticket){
        if(!active || ticket!=generation)return;
        String name=BotIdentity.NAMES[index];index=(index+1)%BotIdentity.NAMES.length;
        HttpUrl url=HttpUrl.parse(base+"/bot/activity");if(url==null)return;
        call=http.newCall(new Request.Builder().url(url.newBuilder().addQueryParameter("bot",name).addQueryParameter("summary","1").build()).build());
        call.enqueue(new Callback(){
            public void onFailure(Call call,java.io.IOException error){deliver(null);}
            public void onResponse(Call call,Response response){JSONObject data=null;try(Response result=response){if(result.isSuccessful())data=new JSONObject(result.body().string());}catch(Exception ignored){}deliver(data);}
            private void deliver(JSONObject value){main.post(()->{
                if(!active || ticket!=generation)return;
                JSONObject result=value;
                if(result==null || !name.equals(result.optString("bot"))){result=new JSONObject();try{result.put("bot",name).put("stale",true);}catch(JSONException ignored){}}
                listener.update(result);main.postDelayed(()->fetch(base,ticket),result.optBoolean("stale")?2500:1000);
            });}
        });
    }
}

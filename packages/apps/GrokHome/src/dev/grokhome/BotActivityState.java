package dev.grokhome;

import android.content.SharedPreferences;
import java.math.BigInteger;
import java.util.*;
import org.json.*;

/** Persisted unread markers are independent of whichever bot is currently selected. */
final class BotActivityState {
    private final SharedPreferences prefs;
    private final Map<String,Boolean> working=new HashMap<>();
    private Set<String> demoWorking,demoUnread;
    BotActivityState(SharedPreferences prefs){this.prefs=prefs;}
    static JSONObject latestReply(JSONArray entries,String name){
        if(entries!=null)for(int i=entries.length()-1;i>=0;i--){JSONObject row=entries.optJSONObject(i);if(row!=null && row.optString("kind").equals("message") && row.optString("role").equals(name))return row;}
        return null;
    }
    static String token(JSONObject row){return row==null || row.optString("id").isEmpty()?"":row.optString("id")+"#"+row.optString("updatedSeq",row.optString("seq","0"));}
    void update(JSONObject snapshot){
        String name=snapshot.optString("bot");if(BotIdentity.index(name)<0)return;
        working.put(name,!snapshot.optBoolean("stale",true) && snapshot.optBoolean("working"));
        if(snapshot.optBoolean("stale",true))return;
        JSONObject reply=latestReply(snapshot.optJSONArray("entries"),name);
        String id=reply==null?snapshot.optString("reply_id"):reply.optString("id");
        String revision=reply==null?snapshot.optString("reply_revision","0"):reply.optString("updatedSeq",reply.optString("seq","0"));
        String sequence=reply==null?snapshot.optString("reply_seq","0"):reply.optString("seq","0");
        long time=reply==null?snapshot.optLong("reply_time"):reply.optLong("time");
        String token=id.isEmpty()?"":id+"#"+revision,key="activity_token_"+name;
        boolean initialized=prefs.contains(key);
        if(initialized){
            if(token.isEmpty() || time<prefs.getLong("activity_time_"+name,0))return;
            try{if(time==prefs.getLong("activity_time_"+name,0) && new BigInteger(sequence).compareTo(new BigInteger(prefs.getString("activity_seq_"+name,"0")))<0)return;}catch(NumberFormatException ignored){}
            try{if(id.equals(prefs.getString("activity_id_"+name,"")) && new BigInteger(revision).compareTo(new BigInteger(prefs.getString("activity_revision_"+name,"0")))<0)return;}catch(NumberFormatException ignored){}
            if(token.equals(prefs.getString(key,""))){rememberPreview(name,previewFrom(snapshot,reply));return;}
        }
        SharedPreferences.Editor edit=prefs.edit().putString(key,token).putLong("activity_time_"+name,time).putString("activity_seq_"+name,sequence).putString("activity_id_"+name,id).putString("activity_revision_"+name,revision);
        String preview=previewFrom(snapshot,reply);
        edit.putString("activity_preview_"+name,preview==null?"":preview);
        if(!initialized)edit.putString("activity_seen_"+name,token);edit.apply();
    }
    private void rememberPreview(String name,String preview){
        String bounded=storedPreview(preview);
        if(bounded.isEmpty())return;
        String key="activity_preview_"+name;
        if(!bounded.equals(prefs.getString(key,"")))prefs.edit().putString(key,bounded).apply();
    }
    /** Opening line only, then the visible ellipsized length. Later lines are the private tail and are never stored. */
    static String previewFrom(JSONObject snapshot,JSONObject reply){
        String raw=null;
        if(reply!=null && reply.has("text"))raw=reply.optString("text");
        else if(snapshot!=null && snapshot.has("latestUnreadPreview"))raw=snapshot.optString("latestUnreadPreview");
        return raw==null?null:storedPreview(raw);
    }
    static String storedPreview(String raw){
        if(raw==null || raw.isEmpty())return "";
        return UnreadPreview.preview(openingLine(raw));
    }
    private static String openingLine(String raw){
        String text=raw.replace("\r\n","\n").replace('\r','\n').replace('\u2028','\n').replace('\u2029','\n').replace('\u0085','\n');
        int start=0;
        while(start<text.length()){
            int end=text.indexOf('\n',start);
            if(end<0)end=text.length();
            String line=text.substring(start,end);
            if(!line.trim().isEmpty())return line;
            start=end+1;
        }
        return "";
    }
    String preview(String name){return prefs.getString("activity_preview_"+name,"");}
    /** Demo mode: these bots read as working or unread, whatever the live feed says. */
    void setDemo(Set<String> working,Set<String> unread){demoWorking=working;demoUnread=unread;}
    void clearDemo(){demoWorking=null;demoUnread=null;}
    boolean working(String name){if(demoWorking!=null)return demoWorking.contains(name);return Boolean.TRUE.equals(working.get(name));}
    boolean unread(String name){if(demoUnread!=null)return demoUnread.contains(name);String token=prefs.getString("activity_token_"+name,"");return !token.isEmpty() && !token.equals(prefs.getString("activity_seen_"+name,""));}
    boolean anyUnread(){for(String name:BotIdentity.NAMES)if(unread(name))return true;return false;}
    void viewed(String name,String token){if(!token.isEmpty() && token.equals(prefs.getString("activity_token_"+name,"")))prefs.edit().putString("activity_seen_"+name,token).apply();}
    void clearWorking(){working.clear();}
}

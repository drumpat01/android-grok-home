package dev.grokhome;

import android.content.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BotActivityStateTest {
    private SharedPreferences prefs;
    @Before public void setup(){prefs=InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("activity-state-test",0);prefs.edit().clear().commit();}
    @After public void cleanup(){prefs.edit().clear().commit();}
    private JSONObject summary(String id,int revision,int time,boolean working,boolean stale)throws Exception{return new JSONObject().put("bot","Nova").put("reply_id",id).put("reply_revision",""+revision).put("reply_seq",""+time).put("reply_time",time).put("working",working).put("stale",stale);}
    @Test public void unreadIsBaselineSafePersistentAndClearsOnlyForTheCurrentReply()throws Exception{
        BotActivityState state=new BotActivityState(prefs);state.update(summary("r1",1,1,false,false));assertFalse(state.unread("Nova"));
        state.update(summary("r2",2,2,true,false));assertTrue(state.unread("Nova"));assertTrue(state.working("Nova"));
        state.viewed("Nova","r1#1");assertTrue(state.unread("Nova"));
        state.update(summary("r1",1,1,false,false));assertTrue(state.unread("Nova"));assertEquals("r2#2",prefs.getString("activity_token_Nova",""));
        state.update(summary("r2",4,2,true,false));state.update(summary("r2",3,2,true,false));assertEquals("r2#4",prefs.getString("activity_token_Nova",""));
        state.update(summary("r2",4,2,true,true));assertFalse(state.working("Nova"));assertTrue(state.unread("Nova"));
        state=new BotActivityState(prefs);assertTrue(state.unread("Nova"));state.viewed("Nova","r2#4");assertFalse(state.unread("Nova"));
    }
    @Test public void aFirstReplyAfterAnEmptyBaselineIsUnreadAndContactsDoNotCount()throws Exception{
        BotActivityState state=new BotActivityState(prefs);state.update(summary("",0,0,false,false));state.update(summary("first",1,1,false,false));assertTrue(state.unread("Nova"));
        JSONArray entries=new JSONArray().put(new JSONObject().put("id","bot").put("kind","message").put("role","Nova").put("seq","8"))
            .put(new JSONObject().put("id","contact").put("kind","contact")).put(new JSONObject().put("id","user").put("kind","message").put("role","You"));
        assertEquals("bot#8",BotActivityState.token(BotActivityState.latestReply(entries,"Nova")));
    }
}

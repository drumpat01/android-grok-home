package dev.grokhome;

import android.content.SharedPreferences;
import android.os.Looper;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** The persisted unread preview is a short head snippet. The rest of the body never lands in prefs. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32)
public class BotActivityStatePreviewTest {
    private static final String SECRET="SECRET-TAIL "+"private ".repeat(30);
    private SharedPreferences prefs;

    @Before public void setup(){
        prefs=RuntimeEnvironment.getApplication().getSharedPreferences("activity-preview-unit",0);
        prefs.edit().clear().commit();
    }

    @After public void cleanup(){prefs.edit().clear().commit();}

    @Test public void storedPreviewKeepsTheHeadAndDropsTheTail()throws Exception{
        String headline="Ready for review";
        String body="**"+headline+"**\n\n"+SECRET;
        BotActivityState state=new BotActivityState(prefs);
        state.update(new JSONObject().put("bot","Nova").put("stale",false).put("working",false).put("entries",entries(body)));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertHeadOnly(state.preview("Nova"),headline);
        assertHeadOnly(BotActivityState.previewFrom(new JSONObject(),new JSONObject().put("text",body)),headline);
        BotActivityState reloaded=new BotActivityState(prefs);
        assertHeadOnly(reloaded.preview("Nova"),headline);
        assertPrefsOmitTail();
    }

    @Test public void longOpeningLineIsEllipsizedBeforeTheTail()throws Exception{
        String body=("word ".repeat(40))+SECRET;
        BotActivityState state=new BotActivityState(prefs);
        state.update(new JSONObject().put("bot","Nova").put("stale",false).put("working",false)
            .put("reply_id","huge").put("reply_revision","4").put("reply_seq","4").put("reply_time",4)
            .put("latestUnreadPreview",body));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        String stored=state.preview("Nova");
        assertTrue(stored.endsWith(UnreadPreview.ELLIPSIS));
        assertTrue(stored.codePointCount(0,stored.length())<=UnreadPreview.LIMIT);
        assertTrue(stored.startsWith("word "));
        assertFalse(stored,stored.contains("SECRET-TAIL"));
        assertFalse(stored,stored.contains("private"));
        assertPrefsOmitTail();
    }

    @Test public void unicodeLineBreakDoesNotPullTheTailIntoTheSnippet(){
        String stored=BotActivityState.storedPreview("Ready for review\u2028"+SECRET);
        assertEquals("Ready for review",stored);
        assertFalse(stored,stored.contains("SECRET-TAIL"));
        stored=BotActivityState.storedPreview("Ready for review\u0085"+SECRET);
        assertEquals("Ready for review",stored);
        assertFalse(stored.contains("SECRET-TAIL"));
    }

    private static JSONArray entries(String body)throws Exception{
        return new JSONArray()
            .put(new JSONObject().put("id","old").put("kind","message").put("role","Nova").put("seq","1").put("updatedSeq","1").put("time",1).put("text","older"))
            .put(new JSONObject().put("id","user").put("kind","message").put("role","You").put("text",SECRET))
            .put(new JSONObject().put("id","new").put("kind","message").put("role","Nova").put("seq","2").put("updatedSeq","2").put("time",2).put("text",body));
    }

    private static void assertHeadOnly(String stored,String headline){
        assertEquals(headline,stored);
        assertTrue(stored.codePointCount(0,stored.length())<=UnreadPreview.LIMIT);
        assertFalse(stored,stored.contains("SECRET-TAIL"));
        assertFalse(stored.contains("private"));
        assertFalse(stored.contains("\n"));
    }

    private void assertPrefsOmitTail(){
        for(Map.Entry<String,?> entry:prefs.getAll().entrySet()){
            String value=String.valueOf(entry.getValue());
            assertFalse(entry.getKey()+"="+value,value.contains("SECRET-TAIL"));
            assertFalse(entry.getKey()+"="+value,value.contains("private"));
        }
    }
}

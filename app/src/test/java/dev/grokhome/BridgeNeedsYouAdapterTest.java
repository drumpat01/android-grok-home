package dev.grokhome;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class BridgeNeedsYouAdapterTest {
    @Test public void readsTheBridgeShapeAndIgnoresTranscriptQuestions() throws Exception {
        JSONObject snapshot = new JSONObject();
        snapshot.put("bot", "Sage");
        snapshot.put("entries", new JSONArray().put(new JSONObject().put("kind", "message").put("role", "Sage").put("text", "Should I merge it?")));
        JSONObject row = new JSONObject();
        row.put("id", "handoff:req-1");
        row.put("kind", "approval");
        row.put("question", "Merge the Tesla page fix?");
        row.put("question_short", "Merge the Tesla page fix?");
        row.put("question_short_truncated", false);
        row.put("answerable_here", false);
        row.put("created", 1000);
        row.put("expires", JSONObject.NULL);
        snapshot.put("needs_you", new JSONArray().put(row));

        List<NeedsYouNotice> notices = BridgeNeedsYouAdapter.parse(snapshot, 5_000L);
        assertEquals(1, notices.size());
        assertEquals("Sage", notices.get(0).bot);
        assertEquals("handoff:req-1", notices.get(0).id);
        assertEquals("Merge the Tesla page fix?", notices.get(0).question);
        assertEquals(1000L, notices.get(0).created);
        assertFalse(notices.get(0).question.contains("Should I merge"));
    }

    @Test public void emptyNeedsYouAndAQuestionMarkReplyStayEmpty() throws Exception {
        JSONObject snapshot = new JSONObject();
        snapshot.put("bot", "Nova");
        snapshot.put("needs_you", new JSONArray());
        snapshot.put("entries", new JSONArray().put(new JSONObject().put("text", "Are you there?")));
        assertTrue(BridgeNeedsYouAdapter.parse(snapshot, 1L).isEmpty());
        assertTrue(BridgeNeedsYouAdapter.parse(new JSONObject().put("bot", "Nova"), 1L).isEmpty());
    }

    @Test public void expiredRowsDropAndALaterSnapshotReplacesTheBot() throws Exception {
        BridgeNeedsYouAdapter adapter = new BridgeNeedsYouAdapter();
        adapter.setClock(10_000L);
        adapter.accept(item("Clover", "old", "Keep the paid plan?", 1_000L, 5_000L));
        adapter.accept(item("Sage", "live", "Merge the Tesla page fix?", 2_000L, 0L));
        assertEquals(1, adapter.items().size());
        assertEquals("Sage", adapter.items().get(0).bot);

        JSONObject clear = new JSONObject();
        clear.put("bot", "Sage");
        clear.put("needs_you", new JSONArray());
        adapter.accept(clear);
        assertTrue(adapter.items().isEmpty());
        assertTrue(BridgeNeedsYouAdapter.empty().items().isEmpty());
    }

    @Test public void staleSnapshotKeepsTheHandTheBridgeStillSends() throws Exception {
        JSONObject snapshot = item("Orion", "handoff:arm", "Check the gray triangle?", 4_000L, 0L);
        snapshot.put("stale", true);
        List<NeedsYouNotice> notices = BridgeNeedsYouAdapter.parse(snapshot, 4_500L);
        assertEquals(1, notices.size());
        assertEquals("Orion", notices.get(0).bot);
    }

    private static JSONObject item(String bot, String id, String question, long created, long expires) throws Exception {
        JSONObject row = new JSONObject();
        row.put("id", id);
        row.put("kind", "approval");
        row.put("question", question);
        row.put("question_short", question);
        row.put("created", created);
        if (expires > 0L) row.put("expires", expires);
        JSONObject snapshot = new JSONObject();
        snapshot.put("bot", bot);
        snapshot.put("needs_you", new JSONArray().put(row));
        return snapshot;
    }
}

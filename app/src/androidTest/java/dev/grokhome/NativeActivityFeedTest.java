package dev.grokhome;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
@RunWith(AndroidJUnit4.class)
public class NativeActivityFeedTest {
 private NativeActivityFeed feed;
 private void onMain(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
 @After public void clean(){if(feed!=null)onMain(()->feed.close());}
 @Test public void changingBotsDropsThePreviousBotsDelayedResponse()throws Exception{
  CountDownLatch kitRequest=new CountDownLatch(1),maxResult=new CountDownLatch(1);List<String> received=new CopyOnWriteArrayList<>();
  try(MockWebServer server=new MockWebServer()){
   server.setDispatcher(new Dispatcher(){public MockResponse dispatch(RecordedRequest request){
    if(request.getPath().contains("bot=Nova")){kitRequest.countDown();return new MockResponse().setBody("{\"bot\":\"Nova\",\"entries\":[]}").setBodyDelay(700,TimeUnit.MILLISECONDS);}
    return new MockResponse().setBody("{\"bot\":\"Max\",\"entries\":[],\"stale\":false}");
   }});server.start();String base=server.url("/").toString().replaceAll("/$","");
   onMain(()->{feed=new NativeActivityFeed(data->{received.add(data.optString("bot"));if(data.optString("bot").equals("Max"))maxResult.countDown();});feed.start(base,"Nova");});
   assertTrue(kitRequest.await(3,TimeUnit.SECONDS));onMain(()->feed.start(base,"Max"));assertTrue(maxResult.await(3,TimeUnit.SECONDS));Thread.sleep(800);
   assertEquals(Collections.singletonList("Max"),received);onMain(()->feed.stop());
  }
 }
 @Test public void failedSyncReportsStaleRatherThanAnEmptySuccessfulHistory()throws Exception{
  CountDownLatch result=new CountDownLatch(1);List<Boolean> stale=new CopyOnWriteArrayList<>();
  try(MockWebServer server=new MockWebServer()){
   server.enqueue(new MockResponse().setResponseCode(403));server.start();String base=server.url("/").toString().replaceAll("/$","");
   onMain(()->{feed=new NativeActivityFeed(data->{stale.add(data.optBoolean("stale"));result.countDown();});feed.start(base,"Nova");});
   assertTrue(result.await(3,TimeUnit.SECONDS));assertEquals(Collections.singletonList(true),stale);onMain(()->feed.stop());
  }
 }
}

package dev.grokhome;
import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@RunWith(AndroidJUnit4.class)
public class AppUpdaterTest {
 private Context context;private AppUpdater updater;
 @Before public void setup(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();updater=new AppUpdater(context);}
 @After public void clean(){updater.close();}
 private String hash(byte[] bytes)throws Exception{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format("%02x",b&255));return out.toString();}
 private AppUpdateInfo info(byte[] bytes,long code)throws Exception{return new AppUpdateInfo(new JSONObject().put("package","dev.grokhome").put("version_code",code).put("version_name","test").put("size",bytes.length).put("sha256",hash(bytes)).put("apk_path","/app/update.apk?sha256="+hash(bytes)));}
 @Test public void newerMatchingSignedApkDownloadsAndIsSharedWithInstaller()throws Exception{
  File fixture=new File(context.getFilesDir(),"update-fixture.apk");assertTrue("Push signed newer test fixture to emulator sandbox first",fixture.isFile());byte[] bytes=Files.readAllBytes(fixture.toPath());
  try(MockWebServer server=new MockWebServer()){
   server.enqueue(new MockResponse().setBody(new okio.Buffer().write(bytes)));server.start();String base=server.url("/").toString().replaceAll("/$","");CountDownLatch done=new CountDownLatch(1);AtomicReference<File> ready=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();
   updater.download(base,info(bytes,updater.installedVersion()+1),new AppUpdater.Listener(){public void available(AppUpdateInfo i){}public void progress(int p){}public void ready(File f){ready.set(f);done.countDown();}public void failed(String message){error.set(message);done.countDown();}});
   assertTrue(done.await(20,TimeUnit.SECONDS));assertNull(error.get());assertNotNull(ready.get());
   android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(context,context.getPackageName()+".updates",ready.get());assertEquals("content",uri.getScheme());
   try(InputStream input=context.getContentResolver().openInputStream(uri)){assertEquals(bytes.length,input.available());}
  }
 }
 @Test public void rejectsWrongVersionAndBadChecksumBeforeInstaller()throws Exception{
  File fixture=new File(context.getFilesDir(),"update-fixture.apk");byte[] bytes=Files.readAllBytes(fixture.toPath());
  try{updater.verifyArchive(fixture,info(bytes,updater.installedVersion()+2));fail("Wrong APK version accepted");}catch(SecurityException expected){}
  try(MockWebServer server=new MockWebServer()){
   byte[] damaged=bytes.clone();damaged[damaged.length-1]^=1;server.enqueue(new MockResponse().setBody(new okio.Buffer().write(damaged)));server.start();String base=server.url("/").toString().replaceAll("/$","");CountDownLatch done=new CountDownLatch(1);AtomicBoolean rejected=new AtomicBoolean();
   updater.download(base,info(bytes,updater.installedVersion()+1),new AppUpdater.Listener(){public void available(AppUpdateInfo i){}public void progress(int p){}public void ready(File f){done.countDown();}public void failed(String message){rejected.set(true);done.countDown();}});
   assertTrue(done.await(20,TimeUnit.SECONDS));assertTrue(rejected.get());
  }
 }
}

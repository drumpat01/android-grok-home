package dev.grokhome;
import android.view.*;
import android.view.accessibility.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.lang.reflect.*;

/** Emulator only: opens Android's approval screen and cancels; never installs. */
@RunWith(AndroidJUnit4.class)
public class AppUpdateInstallerUiTest {
 @Test public void androidStillRequiresAnExplicitInstallationApproval()throws Exception{
  android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
  assertTrue("Grant unknown-app permission in emulator test setup only",instrumentation.getTargetContext().getPackageManager().canRequestPackageInstalls());
  long before=instrumentation.getTargetContext().getPackageManager().getPackageInfo("dev.grokhome",0).getLongVersionCode();
  try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)){
   scenario.onActivity(home->{try{
    File apk=new File(home.getCacheDir(),"updates/GrokHome-update.apk");assertTrue(apk.isFile());
    Field field=HomeActivity.class.getDeclaredField("pendingInstall");field.setAccessible(true);field.set(home,apk);
    Method open=HomeActivity.class.getDeclaredMethod("openUpdateInstaller");open.setAccessible(true);open.invoke(home);
   }catch(Exception error){throw new AssertionError(error);}});
   boolean approval=false;
   for(int attempt=0;attempt<50;attempt++){
    Thread.sleep(100);AccessibilityNodeInfo root=instrumentation.getUiAutomation().getRootInActiveWindow();
    if(root!=null && root.getPackageName()!=null && root.getPackageName().toString().contains("packageinstaller") && !root.findAccessibilityNodeInfosByText("Update").isEmpty()){approval=true;break;}
   }
   try{assertTrue("Android update confirmation must be shown",approval);}finally{instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);}
  }
  assertEquals("Test must not install the newer fixture",before,instrumentation.getTargetContext().getPackageManager().getPackageInfo("dev.grokhome",0).getLongVersionCode());
 }
}

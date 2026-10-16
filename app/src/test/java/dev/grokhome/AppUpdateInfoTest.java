package dev.grokhome;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;
public class AppUpdateInfoTest {
 private JSONObject valid()throws Exception{return new JSONObject().put("package","dev.grokhome").put("version_code",24).put("version_name","next").put("size",1234).put("sha256","a".repeat(64)).put("apk_path","/app/update.apk?sha256="+"a".repeat(64));}
 @Test public void acceptsOnlyPinnedLocalUpdatePath()throws Exception{AppUpdateInfo info=new AppUpdateInfo(valid());assertEquals(24,info.code);assertEquals(1234,info.size);}
 @Test public void refusesOtherPackagesExternalPathsAndOversize()throws Exception{
  for(JSONObject bad:new JSONObject[]{valid().put("package","other.app"),valid().put("apk_path","https://elsewhere.example/app.apk"),valid().put("size",AppUpdateInfo.MAX_BYTES+1),valid().put("sha256","bad")}){
   try{new AppUpdateInfo(bad);fail("Invalid metadata accepted");}catch(JSONException expected){}
  }
 }
}

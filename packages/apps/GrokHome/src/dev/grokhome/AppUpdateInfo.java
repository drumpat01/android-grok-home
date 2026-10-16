package dev.grokhome;
import org.json.*;

final class AppUpdateInfo {
    static final long MAX_BYTES=128L*1024*1024;
    final long code,size;final String name,sha,path;
    AppUpdateInfo(JSONObject value)throws JSONException{
        code=value.getLong("version_code");size=value.getLong("size");name=value.getString("version_name");sha=value.getString("sha256");path=value.getString("apk_path");
        if(!value.getString("package").equals("dev.grokhome") || code<=0 || size<=0 || size>MAX_BYTES || name.length()>100 || !sha.matches("[a-f0-9]{64}") || !path.equals("/app/update.apk?sha256="+sha))throw new JSONException("Invalid update metadata");
    }
}

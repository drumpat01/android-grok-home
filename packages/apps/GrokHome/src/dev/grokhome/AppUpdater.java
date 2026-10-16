package dev.grokhome;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;
import org.json.*;

/** User-started update checks/downloads. Android owns the installation approval. */
final class AppUpdater {
    interface Listener {void available(AppUpdateInfo info);void progress(int percent);void ready(File file);void failed(String message);}
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final OkHttpClient http=new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(3,TimeUnit.MINUTES).build();
    private volatile boolean closed;
    AppUpdater(Context context){this.context=context;}
    long installedVersion(){try{return context.getPackageManager().getPackageInfo(context.getPackageName(),0).getLongVersionCode();}catch(PackageManager.NameNotFoundException e){return 0;}}
    private void post(Runnable action){main.post(()->{if(!closed)action.run();});}
    void check(String base,Listener listener){worker.execute(()->{
        try{VoiceClient.sessionUrl(base);
            try(Response r=http.newCall(new Request.Builder().url(base+"/app/update").build()).execute()){
                if(!r.isSuccessful() || r.body()==null || r.body().contentLength()>16384)throw new IOException();
                AppUpdateInfo info=new AppUpdateInfo(new JSONObject(r.body().string()));post(()->listener.available(info));
            }
        }catch(Exception error){post(()->listener.failed("Couldn’t check for updates. Check the computer connection and try again."));}
    });}
    void download(String base,AppUpdateInfo info,Listener listener){worker.execute(()->{
        File folder=new File(context.getCacheDir(),"updates");File partial=new File(folder,"download.part");
        try{VoiceClient.sessionUrl(base);if(!folder.isDirectory() && !folder.mkdirs())throw new IOException();
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0,last=0;
            try(Response response=http.newCall(new Request.Builder().url(base+info.path).build()).execute()){
                if(!response.isSuccessful() || response.body()==null)throw new IOException();
                try(InputStream in=response.body().byteStream();OutputStream out=new FileOutputStream(partial)){
                    byte[] buffer=new byte[65536];int count;
                    while((count=in.read(buffer))!=-1){
                        if(closed || Thread.currentThread().isInterrupted())throw new IOException();
                        total+=count;if(total>info.size)throw new IOException();digest.update(buffer,0,count);out.write(buffer,0,count);
                        if(SystemClock.elapsedRealtime()-last>100){last=SystemClock.elapsedRealtime();int percent=(int)(100*total/info.size);post(()->listener.progress(percent));}
                    }
                }
            }
            if(total!=info.size || !hex(digest.digest()).equals(info.sha))throw new IOException();
            verifyArchive(partial,info);
            File apk=new File(folder,"GrokHome-update.apk");if(apk.exists() && !apk.delete())throw new IOException();if(!partial.renameTo(apk))throw new IOException();
            post(()->listener.ready(apk));
        }catch(Exception error){partial.delete();post(()->listener.failed("Update couldn’t be verified or downloaded. Check the computer connection and try again."));}
    });}
    void verifyArchive(File file,AppUpdateInfo info)throws Exception{
        PackageManager pm=context.getPackageManager();PackageInfo archive=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo installed=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(archive==null || !context.getPackageName().equals(archive.packageName) || archive.getLongVersionCode()!=info.code || info.code<=installed.getLongVersionCode() || !signers(archive).equals(signers(installed)))throw new SecurityException("Invalid app update");
    }
    private static Set<String> signers(PackageInfo info)throws Exception{
        if(info.signingInfo==null)throw new SecurityException();Set<String> result=new HashSet<>();
        for(android.content.pm.Signature signer:info.signingInfo.getApkContentsSigners())result.add(hex(MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())));
        if(result.isEmpty())throw new SecurityException();return result;
    }
    private static String hex(byte[] data){StringBuilder value=new StringBuilder();for(byte item:data)value.append(String.format(java.util.Locale.ROOT,"%02x",item&255));return value.toString();}
    void close(){closed=true;http.dispatcher().cancelAll();worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}

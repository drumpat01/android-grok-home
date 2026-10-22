package dev.grokhome;

import java.io.File;

/** Screenshot directory for ambient-wall renders. */
final class ScreenshotDirs {
    static final String PROPERTY="grok.screenshot.dir";
    static final String ENV="GROK_SCREENSHOT_DIR";
    private static final File CLOUD=new File("/opt/cursor/artifacts");

    static File output(){
        File dir=choose(System.getProperty(PROPERTY),System.getenv(ENV),CLOUD,fallback());
        if(!dir.isDirectory() && !dir.mkdirs())
            throw new IllegalStateException("Cannot create screenshot dir "+dir.getAbsolutePath());
        return dir;
    }

    static File choose(String property,String env,File cloud,File fallback){
        String override=text(property);
        if(override==null)override=text(env);
        if(override!=null)return new File(override);
        if(cloud!=null && cloud.isDirectory() && cloud.canWrite())return cloud;
        return fallback;
    }

    static File fallback(){
        File cwd=new File(System.getProperty("user.dir")).getAbsoluteFile();
        if(new File(cwd,"app/build.gradle").isFile())return new File(cwd,"app/build/screenshots");
        return new File(cwd,"build/screenshots");
    }

    private static String text(String value){
        if(value==null)return null;
        String trimmed=value.trim();
        return trimmed.isEmpty()?null:trimmed;
    }

    private ScreenshotDirs(){}
}

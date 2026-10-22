package dev.grokhome;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScreenshotDirTest {
    @Test public void propertyAndEnvOverrideTheCloudDir()throws Exception{
        File cloud=Files.createTempDirectory("shot-cloud").toFile();
        File fallback=new File("app/build/screenshots");
        assertEquals(new File("/tmp/from-property"),ScreenshotDirs.choose("/tmp/from-property","/tmp/from-env",cloud,fallback));
        assertEquals(new File("/tmp/from-env"),ScreenshotDirs.choose("  ","/tmp/from-env",cloud,fallback));
        assertEquals(new File("/tmp/from-env"),ScreenshotDirs.choose(null,"/tmp/from-env",cloud,fallback));
    }

    @Test public void cloudDirIsUsedOnlyWhenItExistsAndIsWritable()throws Exception{
        File cloud=Files.createTempDirectory("shot-cloud").toFile();
        File missing=new File(cloud,"missing");
        File file=new File(cloud,"not-a-dir");
        assertTrue(file.createNewFile());
        File fallback=new File("app/build/screenshots");
        assertEquals(cloud,ScreenshotDirs.choose(null,null,cloud,fallback));
        assertEquals(fallback,ScreenshotDirs.choose(null,"",missing,fallback));
        assertEquals(fallback,ScreenshotDirs.choose(null,null,file,fallback));
    }

    @Test public void outputHonorsTheSystemProperty()throws Exception{
        File dir=Files.createTempDirectory("shot-out").toFile();
        String previous=System.getProperty(ScreenshotDirs.PROPERTY);
        System.setProperty(ScreenshotDirs.PROPERTY,dir.getAbsolutePath());
        try{assertEquals(dir.getAbsoluteFile(),ScreenshotDirs.output().getAbsoluteFile());}
        finally{if(previous==null)System.clearProperty(ScreenshotDirs.PROPERTY);else System.setProperty(ScreenshotDirs.PROPERTY,previous);}
    }

    @Test public void fallbackLivesUnderBuild(){
        File dir=ScreenshotDirs.fallback();
        assertTrue(dir.getPath().endsWith("build/screenshots") || dir.getPath().endsWith("build\\screenshots"));
    }

    @Test public void wallScreenshotTestDoesNotHardcodeTheCloudDir()throws Exception{
        File root=new File(System.getProperty("user.dir")).getAbsoluteFile();
        for(int i=0;i<6 && root!=null && !new File(root,"app/src/test/java/dev/grokhome/AmbientWallScreenshotTest.java").isFile();i++)
            root=root.getParentFile();
        File test=new File(root,"app/src/test/java/dev/grokhome/AmbientWallScreenshotTest.java");
        assertTrue(test.isFile());
        String text=new String(Files.readAllBytes(test.toPath()),StandardCharsets.UTF_8);
        assertFalse(text.contains("/opt/cursor/artifacts"));
        assertTrue(text.contains("ScreenshotDirs.output()"));
    }
}

package dev.grokhome;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Field;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Loads the ambient caption path on a device, where the Unicode character-class flag crashes. */
@RunWith(AndroidJUnit4.class)
public class AmbientCaptionDeviceTest {
    @Test public void previewAndCaptionLoadOnDevice()throws Exception{
        assertEquals("4.5.2 in review",AmbientCaptions.line(false,null,true,UnreadPreview.preview("**4.5.2** in review")));
        assertEquals("Hello world",UnreadPreview.preview("Hello\u00A0\u3000\nworld"));
        assertEquals("A B",UnreadPreview.preview("A\u0085\u2028B"));
        assertEquals("New reply",AmbientCaptions.line(false,null,true,""));
        assertEquals("Working",AmbientCaptions.line(true,null,true,"4.5.2 in review"));
        assertEquals("Checking the release",AmbientCaptions.line(true,"Checking the release",true,"4.5.2 in review"));
        int banned=Pattern.UNICODE_CHARACTER_CLASS|Pattern.CANON_EQ;
        int patterns=0;
        for(Field field:UnreadPreview.class.getDeclaredFields()){
            if(!Pattern.class.equals(field.getType()))continue;
            field.setAccessible(true);
            Pattern pattern=(Pattern)field.get(null);
            assertEquals(field.getName(),0,pattern.flags()&banned);
            patterns++;
        }
        assertTrue(patterns>0);
    }
}

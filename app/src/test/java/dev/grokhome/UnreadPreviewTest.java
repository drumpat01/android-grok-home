package dev.grokhome;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;
import static org.junit.Assert.*;

public class UnreadPreviewTest {
    @Test public void stripsMarkdownLinksAndCode(){
        assertEquals("4.5.2 in review",UnreadPreview.preview("**4.5.2** in review"));
        assertEquals("4.5.2 in review",UnreadPreview.preview("__4.5.2__ in review"));
        assertEquals("4.5.2 in review",UnreadPreview.preview("~~4.5.2~~ in review"));
        assertEquals("4.5.2 in review",UnreadPreview.preview("*4.5.2* in review"));
        assertEquals("4.5.2 in review",UnreadPreview.preview("[4.5.2 in review](https://ci.example/sk-live-SECRET)"));
        assertEquals("status",UnreadPreview.preview("![status](https://cdn.example/secret.png)"));
        assertEquals("See",UnreadPreview.preview("See <https://ci.example/sk-live-SECRET>"));
        assertEquals("npm test passed",UnreadPreview.preview("`npm test` passed"));
        assertEquals("const ready = true Ship it",UnreadPreview.preview("```js\nconst ready = true\n```\nShip it"));
        assertEquals("4.5.2 in review",UnreadPreview.preview("# 4.5.2 in review"));
        assertEquals("4.5.2 in review",UnreadPreview.preview("> 4.5.2 in review"));
        assertEquals("4.5.2 in review",UnreadPreview.preview("- 4.5.2 in review"));
        assertEquals("4.5.2 in review",UnreadPreview.preview("1. 4.5.2 in review"));
        assertEquals("4.5.2 in review",UnreadPreview.preview("<b>4.5.2</b> in review"));
        assertFalse(UnreadPreview.preview("[ok](https://example.com/sk-live-SECRET)").contains("SECRET"));
        assertEquals("snake_case stays",UnreadPreview.preview("snake_case stays"));
    }

    @Test public void collapsesNewlinesAndMarkdownBlankLines(){
        assertEquals("Hello world",UnreadPreview.preview("Hello\n\nworld"));
        assertEquals("Hello world",UnreadPreview.preview("Hello\r\nworld"));
        assertEquals("Hello world item",UnreadPreview.preview("Hello\n\nworld\n- item"));
        assertEquals("4.5.2 in review",UnreadPreview.preview("  4.5.2   \n  in\treview  "));
    }

    @Test public void collapsesUnicodeWhitespace(){
        assertEquals("Hello world",UnreadPreview.preview("Hello\u00A0world"));
        assertEquals("Hello world",UnreadPreview.preview("Hello\u1680\u2003world"));
        assertEquals("Hello world",UnreadPreview.preview("Hello\u2028\u2029\u202Fworld"));
        assertEquals("Hello world",UnreadPreview.preview("Hello\u205F\u3000world"));
        assertEquals("A B C",UnreadPreview.preview("A\u0085B\u2000C"));
        assertEquals("",UnreadPreview.preview("\u00A0\u3000\u2028"));
        assertEquals("4.5.2 in review",UnreadPreview.preview("**4.5.2**\u00A0in\u3000review"));
    }

    @Test public void patternsDoNotUseAndroidUnsupportedFlags()throws Exception{
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
        File src=new File(repoRoot(),"packages/apps/GrokHome/src");
        assertTrue(src.isDirectory());
        List<String> hits=new ArrayList<>();
        scan(src,hits);
        assertEquals(hits.toString(),0,hits.size());
    }

    @Test public void truncatesLongTextOnAWordBoundary(){
        String text="Alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron";
        String preview=UnreadPreview.preview(text);
        assertTrue(preview.endsWith(UnreadPreview.ELLIPSIS));
        assertTrue(preview.codePointCount(0,preview.length())<=UnreadPreview.LIMIT);
        String body=preview.substring(0,preview.length()-UnreadPreview.ELLIPSIS.length());
        assertTrue(text.startsWith(body));
        assertTrue(text.startsWith(body+" ") || text.equals(body));
        assertFalse(body.endsWith(" "));
        String hard="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".repeat(4);
        String cut=UnreadPreview.preview(hard);
        assertTrue(cut.endsWith(UnreadPreview.ELLIPSIS));
        assertTrue(cut.codePointCount(0,cut.length())<=UnreadPreview.LIMIT);
        assertTrue(hard.startsWith(cut.substring(0,cut.length()-1)));
    }

    @Test public void emptyTextFallsBackToNothing(){
        assertEquals("",UnreadPreview.preview(null));
        assertEquals("",UnreadPreview.preview(""));
        assertEquals("",UnreadPreview.preview("   \n\t  "));
        assertEquals("",UnreadPreview.preview("```\n```"));
        assertEquals("",UnreadPreview.preview("[ ](https://example.com/secret)"));
        assertEquals("",UnreadPreview.preview("https://example.com/sk-live-SECRET"));
    }

    @Test public void keepsEmojiAndCombiningMarksIntact(){
        assertEquals("👍 4.5.2 in review",UnreadPreview.preview("👍 4.5.2 in review"));
        assertEquals("👨‍👩‍👧‍👦 says hello",UnreadPreview.preview("👨‍👩‍👧‍👦 says hello"));
        assertEquals("🇺🇸 ready",UnreadPreview.preview("🇺🇸 ready"));
        assertEquals("Cafe\u0301 is open",UnreadPreview.preview("Cafe\u0301 is open"));
        String marks="e\u0301".repeat(50);
        String cut=UnreadPreview.preview(marks);
        assertTrue(cut.endsWith(UnreadPreview.ELLIPSIS));
        String body=cut.substring(0,cut.length()-UnreadPreview.ELLIPSIS.length());
        assertTrue(body.length()%2==0);
        assertFalse(body.endsWith("e"));
        assertTrue(cut.codePointCount(0,cut.length())<=UnreadPreview.LIMIT);
        String emoji="😀".repeat(80);
        String faces=UnreadPreview.preview(emoji);
        assertTrue(faces.endsWith(UnreadPreview.ELLIPSIS));
        assertTrue(faces.codePointCount(0,faces.length())<=UnreadPreview.LIMIT);
        assertTrue(faces.indexOf('\uFFFD')<0);
        for(int i=0;i<faces.length();i++){
            if(Character.isHighSurrogate(faces.charAt(i)))assertTrue(i+1<faces.length() && Character.isLowSurrogate(faces.charAt(i+1)));
            if(Character.isLowSurrogate(faces.charAt(i)))assertTrue(i>0 && Character.isHighSurrogate(faces.charAt(i-1)));
        }
        String sentence="😀start "+ "token ".repeat(30);
        String sentencePreview=UnreadPreview.preview(sentence);
        assertTrue(sentencePreview.startsWith("😀start "));
        assertTrue(sentencePreview.endsWith(UnreadPreview.ELLIPSIS));
        assertFalse(sentencePreview.contains("toke…") || sentencePreview.endsWith("tok"+UnreadPreview.ELLIPSIS));
    }

    @Test public void tileLabelFallsBackOnlyWhenUnread(){
        assertEquals("4.5.2 in review",UnreadPreview.tileLabel(true,"4.5.2 in review"));
        assertEquals("4.5.2 in review",UnreadPreview.tileLabel(true,"  4.5.2 in review  "));
        assertEquals(UnreadPreview.NEW_REPLY,UnreadPreview.tileLabel(true,""));
        assertEquals(UnreadPreview.NEW_REPLY,UnreadPreview.tileLabel(true,"   "));
        assertEquals(UnreadPreview.NEW_REPLY,UnreadPreview.tileLabel(true,null));
        assertEquals("",UnreadPreview.tileLabel(false,"4.5.2 in review"));
        assertEquals("",UnreadPreview.tileLabel(false,""));
        assertEquals("",UnreadPreview.tileLabel(false,null));
    }

    private static File repoRoot(){
        File dir=new File(System.getProperty("user.dir")).getAbsoluteFile();
        for(int i=0;i<6 && dir!=null;i++){
            if(new File(dir,"packages/apps/GrokHome/src").isDirectory())return dir;
            dir=dir.getParentFile();
        }
        fail("Grok Home sources not found from "+System.getProperty("user.dir"));
        return null;
    }

    private static void scan(File root,List<String> hits)throws IOException{
        try(Stream<Path> paths=Files.walk(root.toPath())){
            Iterator<Path> it=paths.iterator();
            while(it.hasNext()){
                Path path=it.next();
                if(!path.toString().endsWith(".java"))continue;
                String text=new String(Files.readAllBytes(path),StandardCharsets.UTF_8);
                if(text.contains("UNICODE_CHARACTER_CLASS") || text.contains("CANON_EQ") || text.contains("(?U)") || text.contains("(?c)"))
                    hits.add(path.toString());
            }
        }
    }
}

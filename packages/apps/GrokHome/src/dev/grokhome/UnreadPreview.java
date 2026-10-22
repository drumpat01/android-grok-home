package dev.grokhome;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** One-line plain-text prefix of a bot reply. Matches the bridge preview exactly. */
final class UnreadPreview {
    static final int LIMIT=60;
    static final String ELLIPSIS="\u2026";
    static final String NEW_REPLY="New reply";
    private static final Pattern FENCE=Pattern.compile("(?m)^[ \\t]*(?:`{3}|~{3})[^\\n]*$");
    private static final Pattern IMAGE=Pattern.compile("!\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern LINK=Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern AUTOLINK=Pattern.compile("<https?://[^>\\s]+>",Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE=Pattern.compile("`+([^`]*)`+");
    private static final Pattern BOLD=Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern BOLD_US=Pattern.compile("__([^_]+)__");
    private static final Pattern STRIKE=Pattern.compile("~~([^~]+)~~");
    private static final Pattern ITALIC=Pattern.compile("(?<!\\*)\\*(?!\\*)([^*]+)\\*(?!\\*)");
    private static final Pattern ITALIC_US=Pattern.compile("(?<!\\w)_(?!_)([^_]+)_(?!\\w)");
    private static final Pattern HTML=Pattern.compile("<[^>\\n]+>");
    private static final Pattern URL=Pattern.compile("https?://\\S+",Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER=Pattern.compile("(?m)^[ \\t]{0,3}#{1,6}[ \\t]*");
    private static final Pattern QUOTE=Pattern.compile("(?m)^[ \\t]{0,3}>[ \\t]?");
    private static final Pattern RULE=Pattern.compile("(?m)^[ \\t]*([-*_])(?:[ \\t]*\\1){2,}[ \\t]*$");
    private static final Pattern LIST=Pattern.compile("(?m)^[ \\t]{0,3}(?:[-*+]|\\d+[.)])[ \\t]+");
    // Android rejects the Unicode character-class flag, and plain \s is ASCII-only.
    // U+0085 plus the spaces below are the extra characters that flag used to match.
    private static final Pattern SPACE=Pattern.compile("[\\s\u00A0\u0085\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000]+");

    static String tileLabel(boolean unread,String preview){
        if(!unread)return "";
        if(preview==null)return NEW_REPLY;
        String trimmed=preview.trim();
        return trimmed.isEmpty()?NEW_REPLY:trimmed;
    }

    static String preview(String text){
        if(text==null || text.isEmpty())return "";
        String cleaned=SPACE.matcher(strip(text)).replaceAll(" ").trim();
        if(cleaned.isEmpty())return "";
        return truncate(cleaned,LIMIT);
    }

    private static String strip(String text){
        text=text.replace("\r\n","\n").replace('\r','\n');
        text=FENCE.matcher(text).replaceAll(" ");
        text=IMAGE.matcher(text).replaceAll("$1");
        text=LINK.matcher(text).replaceAll("$1");
        text=AUTOLINK.matcher(text).replaceAll(" ");
        text=INLINE.matcher(text).replaceAll("$1");
        text=BOLD.matcher(text).replaceAll("$1");
        text=BOLD_US.matcher(text).replaceAll("$1");
        text=STRIKE.matcher(text).replaceAll("$1");
        text=ITALIC.matcher(text).replaceAll("$1");
        text=ITALIC_US.matcher(text).replaceAll("$1");
        text=HTML.matcher(text).replaceAll(" ");
        text=URL.matcher(text).replaceAll(" ");
        text=HEADER.matcher(text).replaceAll("");
        text=QUOTE.matcher(text).replaceAll("");
        text=RULE.matcher(text).replaceAll(" ");
        text=LIST.matcher(text).replaceAll("");
        return text;
    }

    private static String truncate(String text,int limit){
        if(text.codePointCount(0,text.length())<=limit)return text;
        int budget=limit-ELLIPSIS.codePointCount(0,ELLIPSIS.length());
        StringBuilder prefix=new StringBuilder();
        int taken=0;
        for(String cluster:graphemes(text)){
            int size=cluster.codePointCount(0,cluster.length());
            if(taken+size>budget)break;
            prefix.append(cluster);
            taken+=size;
        }
        String cut=prefix.toString().replaceAll(" +$","");
        if(!cut.isEmpty() && cut.codePointCount(0,cut.length())<text.codePointCount(0,text.length()) && !text.startsWith(cut+" ") && cut.indexOf(' ')>=0)
            cut=cut.substring(0,cut.lastIndexOf(' '));
        return cut.isEmpty()?ELLIPSIS:cut+ELLIPSIS;
    }

    private static List<String> graphemes(String text){
        List<String> clusters=new ArrayList<>();
        StringBuilder buf=new StringBuilder();
        text.codePoints().forEach(code->{
            String ch=new String(Character.toChars(code));
            if(buf.length()>0 && !joins(buf.toString(),ch)){
                clusters.add(buf.toString());
                buf.setLength(0);
            }
            buf.append(ch);
        });
        if(buf.length()>0)clusters.add(buf.toString());
        return clusters;
    }

    private static boolean joins(String buf,String ch){
        int code=ch.codePointAt(0);
        int type=Character.getType(code);
        if(type==Character.NON_SPACING_MARK || type==Character.COMBINING_SPACING_MARK || type==Character.ENCLOSING_MARK)return true;
        if(code==0xFE0E || code==0xFE0F || code==0x20E3 || (code>=0x1F3FB && code<=0x1F3FF) || (code>=0xE0020 && code<=0xE007F))return true;
        if(code==0x200D || buf.endsWith("\u200d"))return true;
        return regional(code) && buf.codePointCount(0,buf.length())==1 && regional(buf.codePointAt(0));
    }

    private static boolean regional(int code){return code>=0x1F1E6 && code<=0x1F1FF;}

    private UnreadPreview(){}
}

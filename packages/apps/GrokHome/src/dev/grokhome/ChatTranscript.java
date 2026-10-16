package dev.grokhome;

import java.util.*;
import java.util.regex.*;

/** Imports existing plain-text history, including whitespace from older saves. */
final class ChatTranscript {
    static List<String[]> parse(String history,String name){
        Pattern pattern=Pattern.compile("(?:^|\\r?\\n[\\t ]*\\r?\\n)[\\t ]*(You|"+Pattern.quote(name)+"): ");Matcher matcher=pattern.matcher(history);
        ArrayList<String[]> entries=new ArrayList<>();String role=null;int start=0;
        while(matcher.find()){
            if(role!=null)add(entries,role,history.substring(start,matcher.start()).trim());
            else if(matcher.start()>0)add(entries,name,history.substring(0,matcher.start()).trim());
            role=matcher.group(1);start=matcher.end();
        }
        add(entries,role==null?name:role,history.substring(start).trim());return entries;
    }
    private static void add(List<String[]> entries,String role,String text){
        if(text.isEmpty())return;
        if(!role.equals("You") && !entries.isEmpty() && entries.get(entries.size()-1)[0].equals(role))entries.get(entries.size()-1)[1]+="\n\n"+text;
        else entries.add(new String[]{role,text});
    }
}

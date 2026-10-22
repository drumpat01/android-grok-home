package dev.grokhome;

/** Caption under one ambient tile. A live task keeps its status; unread tiles show a preview. */
final class AmbientCaptions {
    static String line(boolean featuredWorking,String taskDetail,boolean unread,String preview){
        if(featuredWorking){
            String detail=taskDetail==null?"":taskDetail.replace('\n',' ').replace('\r',' ').trim();
            return detail.isEmpty()?"Working":detail;
        }
        return UnreadPreview.tileLabel(unread,preview);
    }
    private AmbientCaptions(){}
}

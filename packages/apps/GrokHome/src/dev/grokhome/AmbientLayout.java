package dev.grokhome;

/** Square work wall. Max's square is much larger; every other bot stays visible beside him. */
final class AmbientLayout {
    static final class Slot {
        final String name;
        final float left,top,right,bottom;
        final boolean featured;
        Slot(String name,float left,float top,float right,float bottom,boolean featured){
            this.name=name;this.left=left;this.top=top;this.right=right;this.bottom=bottom;this.featured=featured;
        }
        float side(){return right-left;}
    }
    static Slot[] place(String[] names,float width,float height,float gap){
        Slot[] slots=new Slot[names.length];
        if(width<=gap*2 || height<=gap*2 || names.length==0){for(int i=0;i<names.length;i++)slots[i]=new Slot(names[i],0,0,0,0,false);return slots;}
        int lead=0;
        for(int i=0;i<names.length;i++)if(names[i].equals(BotIdentity.PRIMARY))lead=i;
        boolean wide=width>=height;
        // The small squares sit three across (portrait) or three down (landscape) next to Max's 3x3 block.
        // Ten bots or fewer fit in 3 cells along the other axis; each further three bots add one more.
        int more=Math.max(3,(names.length-1+2)/3);
        float small=wide?Math.min((height-4*gap)/3f,(width-(more+4)*gap)/(3f+more)):Math.min((width-4*gap)/3f,(height-(more+4)*gap)/(3f+more));
        if(small<1)small=1;
        float large=3*small+2*gap;
        float blockW=wide?(3+more)*small+(more+4)*gap:3*small+4*gap;
        float blockH=wide?3*small+4*gap:(3+more)*small+(more+4)*gap;
        float ox=(width-blockW)/2,oy=(height-blockH)/2;
        int index=0,cell=0;
        for(int i=0;i<names.length;i++){
            if(i==lead){
                slots[index++]=wide?new Slot(names[i],ox+gap,oy+gap,ox+gap+large,oy+gap+large,true)
                    :new Slot(names[i],ox+(blockW-large)/2,oy+gap,ox+(blockW-large)/2+large,oy+gap+large,true);
            }else{
                int col=wide?cell%more:cell%3,row=wide?cell/more:cell/3;cell++;
                float left,top;
                if(wide){left=ox+gap+large+gap+col*(small+gap);top=oy+gap+row*(small+gap);}
                else{left=ox+gap+col*(small+gap);top=oy+gap+large+gap+row*(small+gap);}
                slots[index++]=new Slot(names[i],left,top,left+small,top+small,false);
            }
        }
        return slots;
    }
    private AmbientLayout(){}
}

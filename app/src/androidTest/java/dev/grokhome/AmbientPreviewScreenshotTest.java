package dev.grokhome;

import android.content.Context;
import android.graphics.*;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Renders the ambient wall at the SM-T733 grid size: 2560×1600, 240 dpi. */
@RunWith(AndroidJUnit4.class)
public class AmbientPreviewScreenshotTest {
    private static final int WIDTH=2560, HEIGHT=1600;
    private static final String LONG_MESSAGE="The release candidate **4.5.2** is now in review and waiting on the final tablet check before we publish the build to the team";

    @Test public void capturesBeforeAndAfterUnreadPreviews()throws Exception{
        Context context=tabletContext();
        float density=context.getResources().getDisplayMetrics().density;
        assertEquals(1.5f,density,.01f);
        String longPreview=UnreadPreview.preview(LONG_MESSAGE);
        assertTrue(longPreview.endsWith(UnreadPreview.ELLIPSIS));
        assertTrue(longPreview.codePointCount(0,longPreview.length())<=UnreadPreview.LIMIT);
        float gap=8*density;
        AmbientLayout.Slot echo=slot(AmbientLayout.place(BotIdentity.NAMES,WIDTH,HEIGHT,gap),"Echo");
        int avail=Math.max(1,Math.round(echo.side()-AmbientWall.CAPTION_INSET_DP*2*density));
        TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        paint.setTextSize(AmbientWall.captionSp(false)*density);
        StaticLayout longLine=AmbientWall.singleLine(longPreview,paint,avail);
        StaticLayout shortLine=AmbientWall.singleLine("4.5.2 in review",paint,avail);
        StaticLayout fallback=AmbientWall.singleLine("New reply",paint,avail);
        assertEquals(1,longLine.getLineCount());
        assertEquals(1,shortLine.getLineCount());
        assertEquals(1,fallback.getLineCount());
        assertTrue("Long preview must ellipsize inside a small tile",longLine.getEllipsisCount(0)>0);
        assertEquals(0,shortLine.getEllipsisCount(0));
        assertEquals(0,fallback.getEllipsisCount(0));
        assertFalse(longLine.getText().toString().contains("\n"));
        AmbientLayout.Slot max=slot(AmbientLayout.place(BotIdentity.NAMES,WIDTH,HEIGHT,gap),"Max");
        paint.setTextSize(AmbientWall.captionSp(true)*density);
        StaticLayout maxLine=AmbientWall.singleLine("4.5.2 in review",paint,Math.max(1,Math.round(max.side()-AmbientWall.CAPTION_INSET_DP*2*density)));
        assertEquals(1,maxLine.getLineCount());
        assertEquals(0,maxLine.getEllipsisCount(0));
        assertTrue(AmbientWall.captionSp(true)>AmbientWall.captionSp(false));
        assertTrue(paint.getTextSize()/density>=13f);

        Set<String> unread=new HashSet<>(Arrays.asList("Nova","Echo","Pixel"));
        Bitmap before=render(context,single("Nova","New reply"),unread);
        Map<String,String> afterLines=new HashMap<>();
        afterLines.put("Nova","4.5.2 in review");
        afterLines.put("Echo",longPreview);
        afterLines.put("Pixel","New reply");
        Bitmap after=render(context,afterLines,unread);
        assertTrue(ink(before)>5000);
        assertTrue(ink(after)>5000);
        assertTrue(captionPixels(before,slot(place(context),"Nova"),density)>80);
        assertTrue(captionPixels(after,slot(place(context),"Nova"),density)>80);
        assertTrue(captionPixels(after,slot(place(context),"Echo"),density)>80);
        assertTrue(captionPixels(after,slot(place(context),"Pixel"),density)>80);
        assertTrue(captionPixels(after,slot(place(context),"Luna"),density)<captionPixels(after,slot(place(context),"Pixel"),density));
        save(before,"ambient-grid-before-2560x1600.png");
        save(after,"ambient-grid-after-2560x1600.png");
        save(strip(context,after,density), "ambient-grid-after-tiles.png");
        before.recycle();after.recycle();
    }

    private static Context tabletContext(){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        DisplayMetrics metrics=context.getResources().getDisplayMetrics();
        metrics.density=1.5f;metrics.scaledDensity=1.5f;metrics.densityDpi=240;metrics.xdpi=240;metrics.ydpi=240;
        android.content.res.Configuration configuration=context.getResources().getConfiguration();
        configuration.densityDpi=240;
        context.getResources().updateConfiguration(configuration,metrics);
        return context;
    }

    private static Bitmap render(Context context,Map<String,String> lines,Set<String> unread)throws Exception{
        Bitmap[] bitmap=new Bitmap[1];
        Throwable[] failure=new Throwable[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try{bitmap[0]=renderOnMain(context,lines,unread);}
            catch(Throwable error){failure[0]=error;}
        });
        if(failure[0]!=null)throw new Exception(failure[0]);
        return bitmap[0];
    }

    private static Bitmap renderOnMain(Context context,Map<String,String> lines,Set<String> unread)throws Exception{
        AmbientWall wall=new AmbientWall(context);
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(HEIGHT,View.MeasureSpec.EXACTLY));
        wall.layout(0,0,WIDTH,HEIGHT);
        Field avatars=AmbientWall.class.getDeclaredField("avatars");avatars.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,SolidAvatar> ready=(Map<String,SolidAvatar>)avatars.get(wall);
        Set<String> shapes=new HashSet<>();
        for(String name:BotIdentity.NAMES)shapes.add(BotIdentity.shape(name));
        for(String shape:shapes)ready.put(shape,new SolidAvatar(context,shape));
        wall.update(lines,Collections.emptySet(),unread);
        Bitmap bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        wall.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static Map<String,String> single(String name,String line){Map<String,String> lines=new HashMap<>();lines.put(name,line);return lines;}
    private static AmbientLayout.Slot[] place(Context context){return AmbientLayout.place(BotIdentity.NAMES,WIDTH,HEIGHT,8*context.getResources().getDisplayMetrics().density);}
    private static AmbientLayout.Slot slot(AmbientLayout.Slot[] slots,String name){for(AmbientLayout.Slot slot:slots)if(slot.name.equals(name))return slot;fail(name);return null;}

    private static int ink(Bitmap bitmap){
        int count=0;
        for(int y=0;y<bitmap.getHeight();y+=6)for(int x=0;x<bitmap.getWidth();x+=6)if((bitmap.getPixel(x,y)&0xffffff)!=0)count++;
        return count;
    }

    private static int captionPixels(Bitmap bitmap,AmbientLayout.Slot slot,float density){
        int count=0,top=Math.max(0,(int)(slot.bottom-28*density)),bottom=Math.min(bitmap.getHeight()-1,(int)(slot.bottom-2));
        int left=Math.max(0,(int)slot.left+8),right=Math.min(bitmap.getWidth(),(int)slot.right-8);
        for(int y=top;y<=bottom;y++)for(int x=left;x<right;x++){
            int color=bitmap.getPixel(x,y);
            int red=Color.red(color),green=Color.green(color),blue=Color.blue(color);
            int target=AmbientWall.CAPTION_COLOR;
            if(Math.abs(red-Color.red(target))<36 && Math.abs(green-Color.green(target))<36 && Math.abs(blue-Color.blue(target))<36)count++;
        }
        return count;
    }

    private static Bitmap strip(Context context,Bitmap source,float density){
        String[] names={"Nova","Echo","Pixel","Luna"};
        String[] titles={"Short preview","Truncated preview","Falls back to New reply","Nothing unread"};
        AmbientLayout.Slot[] slots=place(context);
        int header=Math.round(36*density),gap=Math.round(12*density);
        AmbientLayout.Slot sample=slot(slots,"Nova");
        int side=Math.round(sample.side()),width=names.length*side+(names.length+1)*gap,height=side+header+gap*2;
        Bitmap sheet=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(sheet);canvas.drawColor(Color.BLACK);
        Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);label.setColor(Color.WHITE);label.setTextSize(14*density);label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        for(int i=0;i<names.length;i++){
            AmbientLayout.Slot slot=slot(slots,names[i]);
            int left=gap+i*(side+gap);
            canvas.drawBitmap(source,new Rect(Math.round(slot.left),Math.round(slot.top),Math.round(slot.right),Math.round(slot.bottom)),new Rect(left,header,left+side,header+side),null);
            canvas.drawText(titles[i],left+side/2f,header-10*density,label);
        }
        return sheet;
    }

    private static void save(Bitmap bitmap,String name)throws Exception{
        File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null);
        assertNotNull(dir);if(!dir.exists())assertTrue(dir.mkdirs());
        File file=new File(dir,name);
        try(FileOutputStream output=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));}
        System.out.println("SCREENSHOT "+file.getAbsolutePath());
    }
}

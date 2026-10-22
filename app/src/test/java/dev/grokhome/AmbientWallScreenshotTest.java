package dev.grokhome;

import android.content.Context;
import android.graphics.*;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.os.Looper;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Software renders of AmbientWall at the SM-T733 grid, 2560×1600. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=32, qualifiers="hdpi")
public class AmbientWallScreenshotTest {
    private static final int WIDTH=2560, HEIGHT=1600;
    private static final String LONG_MESSAGE="The release candidate **4.5.2** is now in review and waiting on the final tablet check before we publish the build to the team";

    @Test public void capturesBeforeAndAfterUnreadPreviews()throws Exception{
        Context context=tabletContext();
        float density=context.getResources().getDisplayMetrics().density;
        assertEquals(1.5f,density,.01f);
        String longPreview=UnreadPreview.preview(LONG_MESSAGE);
        assertTrue(longPreview.endsWith(UnreadPreview.ELLIPSIS));
        assertTrue(longPreview.codePointCount(0,longPreview.length())<=UnreadPreview.LIMIT);
        int avail=Math.max(1,Math.round(slot("Echo").side()-AmbientWall.CAPTION_INSET_DP*2*density));
        TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        paint.setTextSize(AmbientWall.captionSp(false)*density);
        StaticLayout longLine=AmbientWall.singleLine(longPreview,paint,avail);
        StaticLayout shortLine=AmbientWall.singleLine("4.5.2 in review",paint,avail);
        StaticLayout fallback=AmbientWall.singleLine("New reply",paint,avail);
        assertEquals(1,longLine.getLineCount());
        assertEquals(1,shortLine.getLineCount());
        assertEquals(1,fallback.getLineCount());
        assertTrue(longLine.getEllipsisCount(0)>0);
        assertEquals(0,shortLine.getEllipsisCount(0));
        assertEquals(0,fallback.getEllipsisCount(0));

        Set<String> unread=new HashSet<>(Arrays.asList("Nova","Echo","Pixel"));
        Bitmap before=render(context,single("Nova","New reply"),unread);
        Map<String,String> afterLines=new HashMap<>();
        afterLines.put("Nova","4.5.2 in review");
        afterLines.put("Echo",longPreview);
        afterLines.put("Pixel","New reply");
        Bitmap after=render(context,afterLines,unread);
        File dir=ScreenshotDirs.output();
        assertTrue(dir.isDirectory());
        save(before,new File(dir,"ambient-grid-before-2560x1600.png"));
        save(after,new File(dir,"ambient-grid-after-2560x1600.png"));
        int beforeKit=captionPixels(before,slot("Nova"),density),beforeIris=captionPixels(before,slot("Echo"),density);
        int afterKit=captionPixels(after,slot("Nova"),density),afterIris=captionPixels(after,slot("Echo"),density);
        int afterDoge=captionPixels(after,slot("Pixel"),density),afterLuna=captionPixels(after,slot("Luna"),density);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue("Before render is blank",ink(before)>5000);
        assertTrue("After render is blank",ink(after)>5000);
        assertTrue("before Nova caption "+beforeKit,beforeKit>200);
        assertTrue("before Echo caption "+beforeIris,beforeIris<120);
        assertTrue("after Nova caption "+afterKit,afterKit>200);
        assertTrue("after Echo caption "+afterIris,afterIris>200);
        assertTrue("after Pixel caption "+afterDoge,afterDoge>200);
        assertTrue("after Luna caption "+afterLuna,afterLuna<120);
        Bitmap tiles=strip(after,density);
        save(tiles,new File(dir,"ambient-grid-after-tiles.png"));
        before.recycle();after.recycle();tiles.recycle();
    }

    @Test
    @Config(sdk=32, qualifiers="w1205dp-h753dp-land-340dpi")
    public void capturesThreeFortyDpiPreviews()throws Exception{
        Context context=tabletContext(340);
        DisplayMetrics metrics=context.getResources().getDisplayMetrics();
        float density=metrics.density;
        assertEquals(340,metrics.densityDpi);
        assertEquals(340f/160f,density,.001f);
        assertEquals(1205,Math.round(WIDTH/density));
        assertEquals(753,Math.round(HEIGHT/density));
        String longPreview=UnreadPreview.preview(LONG_MESSAGE);
        String maxPreview="4.5.2 in review";
        assertOneLine(longPreview,false,density,slot("Echo",density).side(),true);
        assertOneLine("4.5.2 in review",false,density,slot("Nova",density).side(),false);
        assertOneLine("New reply",false,density,slot("Pixel",density).side(),false);
        assertOneLine(maxPreview,true,density,slot("Max",density).side(),false);
        assertTrue(AmbientWall.captionSp(true)>AmbientWall.captionSp(false));
        assertTrue(AmbientWall.captionSp(false)>=13f);

        Set<String> beforeUnread=new HashSet<>(Arrays.asList("Nova","Echo","Pixel"));
        Bitmap before=render(context,single("Nova","New reply"),beforeUnread);
        Map<String,String> afterLines=new HashMap<>();
        afterLines.put("Max",maxPreview);
        afterLines.put("Nova","4.5.2 in review");
        afterLines.put("Echo",longPreview);
        afterLines.put("Pixel","New reply");
        Bitmap after=render(context,afterLines,new HashSet<>(Arrays.asList("Max","Nova","Echo","Pixel")));
        int fill=sampleFill(after,slot("Luna",density),density);
        double ratio=contrast(AmbientWall.CAPTION_COLOR,fill);
        System.out.println("Measured tile fill #"+String.format("%06X",fill&0xffffff)+" caption #"+String.format("%06X",AmbientWall.CAPTION_COLOR&0xffffff)+" contrast "+String.format("%.2f",ratio)+":1");
        assertEquals("Tile fill",AmbientWall.TILE_FILL,fill);
        assertTrue("Contrast "+ratio,ratio>=4.5);
        assertInset(after,slot("Echo",density),density);
        assertInset(after,slot("Nova",density),density);
        assertInset(after,slot("Pixel",density),density);
        assertInset(after,slot("Max",density),density);
        assertDot(after,slot("Nova",density),density);
        assertTrue(captionPixels(after,slot("Max",density),density)>200);
        assertTrue(captionPixels(after,slot("Nova",density),density)>200);
        assertTrue(captionPixels(after,slot("Echo",density),density)>200);
        assertTrue(captionPixels(after,slot("Pixel",density),density)>200);
        assertTrue(captionPixels(after,slot("Luna",density),density)<120);
        assertTrue(captionPixels(before,slot("Echo",density),density)<120);
        File dir=ScreenshotDirs.output();
        assertTrue(dir.isDirectory());
        save(before,new File(dir,"ambient-grid-before-340dpi.png"));
        save(after,new File(dir,"ambient-grid-after-340dpi.png"));
        Bitmap tiles=strip(after,density);
        save(tiles,new File(dir,"ambient-grid-tiles-340dpi.png"));
        Bitmap max=maxFrame(after,density);
        save(max,new File(dir,"ambient-grid-max-340dpi.png"));
        before.recycle();after.recycle();tiles.recycle();max.recycle();
    }

    private static Context tabletContext(){return tabletContext(240);}
    private static Context tabletContext(int dpi){
        float density=dpi/160f;
        int widthDp=Math.round(WIDTH/density),heightDp=Math.round(HEIGHT/density);
        RuntimeEnvironment.setQualifiers("w"+widthDp+"dp-h"+heightDp+"dp-land-"+dpi+"dpi");
        Context context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("home",0).edit().putBoolean(QuietHours.ENABLED,false).commit();
        DisplayMetrics metrics=context.getResources().getDisplayMetrics();
        metrics.density=density;metrics.scaledDensity=density;metrics.densityDpi=dpi;metrics.xdpi=dpi;metrics.ydpi=dpi;
        metrics.widthPixels=WIDTH;metrics.heightPixels=HEIGHT;
        android.content.res.Configuration configuration=context.getResources().getConfiguration();
        configuration.densityDpi=dpi;configuration.screenWidthDp=widthDp;configuration.screenHeightDp=heightDp;
        configuration.orientation=android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        context.getResources().updateConfiguration(configuration,metrics);
        return context;
    }

    private static Bitmap render(Context context,Map<String,String> lines,Set<String> unread)throws Exception{
        AmbientWall wall=new AmbientWall(context);
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(HEIGHT,View.MeasureSpec.EXACTLY));
        wall.layout(0,0,WIDTH,HEIGHT);
        Field avatars=AmbientWall.class.getDeclaredField("avatars");avatars.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,SolidAvatar> ready=(Map<String,SolidAvatar>)avatars.get(wall);
        Set<String> shapes=new HashSet<>();
        for(String name:BotIdentity.NAMES)shapes.add(BotIdentity.shape(name));
        for(String shape:shapes)ready.put(shape,new SolidAvatar(context,shape));
        wall.update(lines,Collections.emptySet(),unread);
        wall.setVisibility(View.INVISIBLE);
        Bitmap bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.BLACK);
        Method onDraw=AmbientWall.class.getDeclaredMethod("onDraw",Canvas.class);onDraw.setAccessible(true);onDraw.invoke(wall,canvas);
        return bitmap;
    }

    private static Map<String,String> single(String name,String line){Map<String,String> lines=new HashMap<>();lines.put(name,line);return lines;}
    private static AmbientLayout.Slot slot(String name){return slot(name,1.5f);}
    private static AmbientLayout.Slot slot(String name,float density){
        for(AmbientLayout.Slot slot:AmbientLayout.place(BotIdentity.NAMES,WIDTH,HEIGHT,8*density))if(slot.name.equals(name))return slot;
        fail(name);return null;
    }

    private static int ink(Bitmap bitmap){
        int count=0;
        for(int y=0;y<bitmap.getHeight();y+=6)for(int x=0;x<bitmap.getWidth();x+=6)if((bitmap.getPixel(x,y)&0xffffff)!=0)count++;
        return count;
    }

    private static int captionPixels(Bitmap bitmap,AmbientLayout.Slot slot,float density){
        int count=0,top=Math.max(0,(int)(slot.bottom-28*density)),bottom=Math.min(bitmap.getHeight()-1,(int)slot.bottom-2);
        int left=Math.max(0,(int)slot.left+8),right=Math.min(bitmap.getWidth(),(int)slot.right-8);
        for(int y=top;y<=bottom;y++)for(int x=left;x<right;x++){
            int color=bitmap.getPixel(x,y),red=Color.red(color),green=Color.green(color),blue=Color.blue(color);
            int target=AmbientWall.CAPTION_COLOR;
            if(Math.abs(red-Color.red(target))<36 && Math.abs(green-Color.green(target))<36 && Math.abs(blue-Color.blue(target))<36)count++;
        }
        return count;
    }

    private static Bitmap strip(Bitmap source,float density){
        String[] names={"Nova","Echo","Pixel","Luna"};
        String[] titles={"Short preview","Truncated preview","Falls back to New reply","Nothing unread"};
        int header=Math.round(36*density),gap=Math.round(12*density),side=Math.round(slot("Nova",density).side());
        Bitmap sheet=Bitmap.createBitmap(names.length*side+(names.length+1)*gap,side+header+gap*2,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(sheet);canvas.drawColor(Color.BLACK);
        Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);label.setColor(Color.WHITE);label.setTextSize(14*density);label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        for(int i=0;i<names.length;i++){
            AmbientLayout.Slot tile=slot(names[i],density);
            int left=gap+i*(side+gap);
            canvas.drawBitmap(source,new Rect(Math.round(tile.left),Math.round(tile.top),Math.round(tile.right),Math.round(tile.bottom)),new Rect(left,header,left+side,header+side),null);
            canvas.drawText(titles[i],left+side/2f,header-8*density,label);
        }
        return sheet;
    }

    private static void assertOneLine(String caption,boolean lead,float density,float side,boolean ellipsis){
        TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        paint.setTextSize(AmbientWall.captionSp(lead)*density);
        int avail=Math.max(1,Math.round(side-AmbientWall.CAPTION_INSET_DP*2*density));
        StaticLayout layout=AmbientWall.singleLine(caption,paint,avail);
        assertEquals(1,layout.getLineCount());
        assertTrue(layout.getLineWidth(0)<=avail+1f);
        if(ellipsis)assertTrue(layout.getEllipsisCount(0)>0);else assertEquals(0,layout.getEllipsisCount(0));
        assertTrue(paint.getTextSize()/density+0.01f>=13f);
    }

    private static int sampleFill(Bitmap bitmap,AmbientLayout.Slot slot,float density){
        return bitmap.getPixel(Math.round(slot.left+36*density),Math.round(slot.bottom-6*density));
    }

    private static void assertInset(Bitmap bitmap,AmbientLayout.Slot slot,float density){
        int inset=Math.round(AmbientWall.CAPTION_INSET_DP*density),clear=Math.round(8*density),hits=0,edge=0,clipped=0;
        int top=Math.max(0,Math.round(slot.bottom-40*density)),bottom=Math.min(bitmap.getHeight()-1,Math.round(slot.bottom));
        for(int y=top;y<=bottom;y++)for(int x=Math.round(slot.left);x<Math.round(slot.right);x++){
            if(!captionPixel(bitmap.getPixel(x,y)))continue;
            hits++;
            if(x<slot.left+inset-2 || x>slot.right-inset+1)edge++;
            if(y>slot.bottom-clear)clipped++;
        }
        assertTrue(slot.name+" caption "+hits,hits>30);
        assertEquals(slot.name+" caption meets the side",0,edge);
        assertEquals(slot.name+" caption clips the bottom",0,clipped);
    }

    private static void assertDot(Bitmap bitmap,AmbientLayout.Slot slot,float density){
        int cx=Math.round(slot.left+16*density),cy=Math.round(slot.top+16*density);
        int center=bitmap.getPixel(cx,cy);
        assertTrue(Color.blue(center)>180 && Color.blue(center)>Color.red(center));
        int outside=bitmap.getPixel(cx+Math.round(6*density),cy);
        assertTrue(Color.red(outside)<40 && Color.green(outside)<40 && Color.blue(outside)<40);
    }

    private static boolean captionPixel(int color){
        int target=AmbientWall.CAPTION_COLOR;
        return Math.abs(Color.red(color)-Color.red(target))<36 && Math.abs(Color.green(color)-Color.green(target))<36 && Math.abs(Color.blue(color)-Color.blue(target))<36;
    }

    private static double contrast(int foreground,int background){
        double hi=Math.max(luminance(foreground),luminance(background)),lo=Math.min(luminance(foreground),luminance(background));
        return (hi+.05)/(lo+.05);
    }

    private static double luminance(int color){
        return .2126*channel(Color.red(color))+.7152*channel(Color.green(color))+.0722*channel(Color.blue(color));
    }

    private static double channel(int value){
        double s=value/255.0;
        return s<=0.04045?s/12.92:Math.pow((s+0.055)/1.055,2.4);
    }

    private static Bitmap maxFrame(Bitmap source,float density){
        AmbientLayout.Slot tile=slot("Max",density);
        int pad=Math.round(18*density),header=Math.round(40*density);
        int left=Math.round(tile.left),top=Math.round(tile.top),right=Math.round(tile.right),bottom=Math.round(tile.bottom);
        int sideW=right-left,sideH=bottom-top;
        Bitmap frame=Bitmap.createBitmap(sideW+pad*2,sideH+header+pad,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(frame);canvas.drawColor(Color.BLACK);
        canvas.drawBitmap(source,new Rect(left,top,right,bottom),new Rect(pad,header,pad+sideW,header+sideH),null);
        Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);label.setColor(Color.WHITE);label.setTextSize(16*density);label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        canvas.drawText("Max · larger unread preview",frame.getWidth()/2f,header-12*density,label);
        return frame;
    }

    private static void save(Bitmap bitmap,File file)throws Exception{
        try(FileOutputStream output=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));}
    }
}

package dev.grokhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

/** Ambient work wall of the real 3D avatars. Max's square and avatar are much larger. */
final class AmbientWall extends View {
    interface Open { void bot(String name); }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillFill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path eye=new Path(),face=new Path(),tileClip=new Path(),hand=WallHandChip.glyph(),moon=QuietMark.moon();
    private final RectF tileRound=new RectF(),pillBounds=new RectF();
    private final Matrix glowMatrix=new Matrix();
    private final CrownRenderer crown=new CrownRenderer();
    private final double[] point=new double[3];
    private final Map<String,float[]> from=new HashMap<>(),to=new HashMap<>(),now=new HashMap<>();
    private final Map<String,Motion> motions=new HashMap<>();
    private final Map<String,SolidAvatar> avatars=new HashMap<>();
    private final Map<String,RadialGradient> glowShaders=new HashMap<>();
    private final Map<String,NeedsYouTiles.Tile> needsYouByName=new HashMap<>();
    private final Set<String> working=new HashSet<>(),unread=new HashSet<>();
    private final Map<String,String> lines=new HashMap<>();
    private final TextPaint linePaint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pillPaint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint notePaint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private final WallNodes nodes=new WallNodes(this);
    private final StageMarks marks;
    private final StagePose pose=new StagePose();
    private final boolean[] ringOn=new boolean[BotIdentity.NAMES.length];
    private final float[] ringPhase=new float[BotIdentity.NAMES.length];
    private final long[] ringSince=new long[BotIdentity.NAMES.length];
    private final boolean[] ringSpins=new boolean[BotIdentity.NAMES.length];
    private final float[] markX=new float[BotIdentity.NAMES.length],markY=new float[BotIdentity.NAMES.length],markR=new float[BotIdentity.NAMES.length];
    private final Map<String,StageMarks> confetti=new HashMap<>();
    private final boolean[] chipOn=new boolean[BotIdentity.NAMES.length];
    private NeedsYouSource needsYou=BridgeNeedsYouAdapter.empty();
    private BotStageState stage;
    private List<NeedsYouTiles.Tile> needsYouTiles=List.of();
    private Open open,needsYouOpen;
    private float anim=1;
    private long lastFrame;
    private boolean warming;
    private boolean wakeListening;
    private boolean quietPinned,quietHours;
    private int quietEndMinute=QuietHours.DEFAULT_END;
    private String quietNote=QuietHours.note(QuietHours.DEFAULT_END);
    private String pillLabel="",pillSpoken="",pillBot="";
    private ValueAnimator animator;
    private final FrameThrottle frames=new FrameThrottle(SystemClock::uptimeMillis);
    private final Runnable frameTick=new Runnable(){public void run(){if(getVisibility()==VISIBLE)invalidate();}};
    private final double[] baseIntensity=new double[BotIdentity.NAMES.length];
    long scheduledFrameDelay=FrameThrottle.STOP;
    AmbientWall(Context context){
        super(context);setBackgroundColor(Color.BLACK);setClickable(true);
        text.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));text.setTextAlign(Paint.Align.CENTER);
        linePaint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));linePaint.setColor(CAPTION_COLOR);linePaint.setTextAlign(Paint.Align.LEFT);
        pillPaint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));pillPaint.setColor(Color.WHITE);pillPaint.setTextAlign(Paint.Align.CENTER);
        notePaint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));notePaint.setColor(QUIET_NOTE);
        borderPaint.setStyle(Paint.Style.STROKE);
        face.addCircle(0,0,1,Path.Direction.CW);
        for(int i=0;i<BotIdentity.NAMES.length;i++){
            Motion motion=new Motion();
            baseIntensity[i]=.35+(i%4)*.12;
            motion.intensity=baseIntensity[i];
            motion.seed(i*2.6+(i%3)*1.7);
            motions.put(BotIdentity.NAMES[i],motion);
        }
        marks=new StageMarks(context);
        applyStageMotion();
        ViewCompat.setAccessibilityDelegate(this,nodes);
    }
    /** Base sway times stage_motion / 100. Zero percent rests every tile so the loop can stop. */
    private void applyStageMotion(){
        int percent=getContext().getSharedPreferences("home",0).getInt("stage_motion",100);
        if(percent<0)percent=0;if(percent>150)percent=150;
        double scale=percent/100.0;
        for(int i=0;i<BotIdentity.NAMES.length;i++){
            Motion motion=motions.get(BotIdentity.NAMES[i]);
            if(motion!=null)motion.intensity=baseIntensity[i]*scale;
        }
    }
    void setOpen(Open open){this.open=open;}
    /** Needs-you tile and edge-pill taps, including TalkBack. Other tiles stay on {@link #setOpen}. */
    void setNeedsYouOpen(Open card){needsYouOpen=card;}
    /** Bridge rows for needs-you tiles. A {@link BotStageState} also supplies the ring and chip. */
    void setNeedsYou(NeedsYouSource source){
        if(stage!=null)stage.setDebugListener(null);
        needsYou=source==null?BridgeNeedsYouAdapter.empty():source;
        stage=needsYou instanceof BotStageState?(BotStageState)needsYou:null;
        if(stage!=null)stage.setDebugListener(this::pullStage);
        pullStage();
    }
    private void pullStage(){
        refreshAttention();
        refreshPill();
        invalidate();
    }
    /** Quiet-hours look. endMinute is minutes from midnight, 420 for 7:00 AM. */
    void setQuietHours(boolean active,int endMinute){
        quietPinned=true;
        quietHours=active;
        quietEndMinute=Math.floorMod(endMinute,1440);
        quietNote=QuietHours.note(quietEndMinute);
        refreshAttention();
        refreshPill();
        invalidate();
    }
    /** Faint wake line. Shown only while the shared wake grammar is running. */
    void setWakeListening(boolean on){if(wakeListening==on)return;wakeListening=on;invalidate();}
    void update(Map<String,String> captions,Set<String> working,Set<String> unread){
        applyStageMotion();
        lines.clear();
        if(captions!=null)for(Map.Entry<String,String> entry:captions.entrySet()){
            String value=entry.getValue()==null?"":entry.getValue().replace('\n',' ').replace('\r',' ').trim();
            if(!value.isEmpty())lines.put(entry.getKey(),value);
        }
        this.working.clear();this.working.addAll(working);this.unread.clear();this.unread.addAll(unread);
        refreshAttention();
        layoutTargets();
        refreshPill();
        invalidate();
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){layoutTargets();refreshPill();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();warm();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(frameTick);warming=false;loader.shutdownNow();super.onDetachedFromWindow();}
    @Override public void setVisibility(int visibility){super.setVisibility(visibility);if(visibility!=VISIBLE)removeCallbacks(frameTick);}
    @Override public boolean dispatchHoverEvent(MotionEvent event){
        return nodes.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }
    private void warm(){
        if(warming)return;warming=true;
        Set<String> shapes=new HashSet<>();
        for(String name:BotIdentity.NAMES)shapes.add(BotIdentity.shape(name));
        for(String shape:shapes)loader.execute(()->{
            if(!warming)return;
            SolidAvatar avatar=new SolidAvatar(getContext(),shape);
            post(()->{if(warming){avatars.put(shape,avatar);invalidate();}});
        });
    }
    private void refreshAttention(){
        refreshQuietFromPrefs();
        List<NeedsYouNotice> notices=needsYou.items();
        if(notices==null)notices=List.of();
        needsYouTiles=NeedsYouTiles.order(notices,BotIdentity.NAMES,quietHours);
        needsYouByName.clear();
        for(NeedsYouTiles.Tile tile:needsYouTiles)needsYouByName.put(tile.bot,tile);
        glowShaders.clear();
        if(!quietHours)for(NeedsYouTiles.Tile tile:needsYouTiles)glowShaders.put(tile.bot,NeedsYouTint.glow(tile.bot,prefsColor(tile.bot)));
        readStage();
        nodes.invalidateRoot();
    }
    /** Working ring from {@link BotStageState#frame}. Hand chip from {@link BotStageState#hands}. */
    private void readStage(){
        for(int i=0;i<BotIdentity.NAMES.length;i++){
            ringOn[i]=false;
            chipOn[i]=false;
            ringPhase[i]=StagePose.RING_FROZEN;
            ringSince[i]=0L;
            ringSpins[i]=false;
        }
        if(stage==null)return;
        for(int i=0;i<BotIdentity.NAMES.length;i++){
            String name=BotIdentity.NAMES[i];
            BotStageState.Frame frame=stage.frame(name);
            NeedsYou.Item[] raised=stage.hands(name);
            if(frame.showRing){
                ringOn[i]=true;
                pose.fill(frame.state,frame.oneShot,0L,name,0f,frame.reduced,quietHours||frame.quiet,false);
                ringPhase[i]=pose.ringPhase;
                ringSince[i]=frame.sinceMillis;
                ringSpins[i]=!frame.reduced;
            }
            if(frame.celebrateToken!=null && !frame.reduced && !frame.quiet && !quietHours){
                StageMarks fx=confetti.get(name);
                if(fx==null){fx=new StageMarks(getContext());fx.bigBurst();confetti.put(name,fx);}
                fx.prepare(name,prefsColor(name));
                fx.arm(frame.celebrateToken,SystemClock.uptimeMillis());
            }
            chipOn[i]=frame.state==BotStageState.State.NEEDS_YOU && raised.length>0;
        }
    }
    private void refreshQuietFromPrefs(){
        if(quietPinned)return;
        SharedPreferences prefs=getContext().getSharedPreferences("home",0);
        if(!prefs.getBoolean(QuietHours.ENABLED,QuietHours.SETTINGS_DEFAULT_ENABLED)){quietHours=false;return;}
        int start=prefs.getInt(QuietHours.START,QuietHours.DEFAULT_START);
        int end=prefs.getInt(QuietHours.END,QuietHours.DEFAULT_END);
        quietEndMinute=Math.floorMod(end,1440);
        quietNote=QuietHours.note(quietEndMinute);
        quietHours=QuietHours.active(true,start,quietEndMinute,QuietHours.minuteOfDay(System.currentTimeMillis(),ZoneId.systemDefault()));
    }
    private void layoutTargets(){
        if(getWidth()<=0 || getHeight()<=0)return;
        float gap=8*getResources().getDisplayMetrics().density;
        AmbientLayout.Slot[] slots=AmbientLayout.place(BotIdentity.NAMES,getWidth(),getHeight(),gap);
        boolean moved=false;
        for(AmbientLayout.Slot slot:slots){
            float[] next=new float[]{slot.left,slot.top,slot.right,slot.bottom},previous=to.get(slot.name);
            if(previous==null || previous[0]!=next[0] || previous[1]!=next[1] || previous[2]!=next[2] || previous[3]!=next[3])moved=true;
        }
        if(!moved && now.size()==slots.length)return;
        if(animator!=null)animator.cancel();
        for(AmbientLayout.Slot slot:slots){
            float[] next=new float[]{slot.left,slot.top,slot.right,slot.bottom};
            if(!now.containsKey(slot.name))now.put(slot.name,next.clone());
            from.put(slot.name,now.get(slot.name).clone());to.put(slot.name,next);
        }
        if(!ValueAnimator.areAnimatorsEnabled()){anim=1;for(AmbientLayout.Slot slot:slots)now.put(slot.name,to.get(slot.name).clone());return;}
        anim=0;animator=ValueAnimator.ofFloat(0,1);animator.setDuration(340);
        animator.addUpdateListener(a->{anim=(float)a.getAnimatedValue();blend();invalidate();});
        animator.start();
    }
    private void blend(){
        for(Map.Entry<String,float[]> entry:to.entrySet()){
            float[] start=from.get(entry.getKey()),end=entry.getValue(),at=now.get(entry.getKey());
            if(start==null || at==null)continue;
            for(int i=0;i<4;i++)at[i]=start[i]+(end[i]-start[i])*anim;
        }
    }
    void refreshPill(){
        List<String> off=new ArrayList<>();
        boolean anyPlaced=false;
        boolean forceOff=stage!=null && stage.debugForcePill() && RaiseHandDebug.allowed(getContext());
        for(NeedsYouTiles.Tile tile:needsYouTiles){
            if(!isPlaced(tile.bot))continue;
            anyPlaced=true;
            if(forceOff || !isOnScreen(tile.bot))off.add(tile.bot);
        }
        if(!anyPlaced || off.isEmpty() || getWidth()<=0){
            pillLabel="";pillSpoken="";pillBot="";pillBounds.setEmpty();
            nodes.invalidateRoot();
            return;
        }
        float density=getResources().getDisplayMetrics().density;
        float scaled=getResources().getDisplayMetrics().scaledDensity;
        pillPaint.setTextSize(EdgePill.TEXT_SP*(scaled>0?scaled:density));
        pillLabel=EdgePill.label(off,EdgePill.textLimit(density),pillPaint::measureText);
        pillBot=off.get(0);
        pillSpoken=WallTalkBack.pill(off.size(),pillBot);
        float textWidth=pillPaint.measureText(pillLabel);
        float width=Math.min(EdgePill.MAX_WIDTH_DP*density,textWidth+EdgePill.PAD_DP*2f*density);
        float height=EdgePill.HEIGHT_DP*density;
        float right=getWidth()-EdgePill.MARGIN_DP*density;
        float left=Math.max(EdgePill.MARGIN_DP*density,right-width);
        float top=EdgePill.MARGIN_DP*density;
        pillBounds.set(left,top,right,top+height);
        nodes.invalidateRoot();
    }
    private boolean isPlaced(String name){
        float[] rect=now.get(name);
        return rect!=null && rect[2]-rect[0]>1f && rect[3]-rect[1]>1f;
    }
    private boolean isOnScreen(String name){
        float[] rect=now.get(name);
        return rect!=null && rect[0]>=-1f && rect[1]>=-1f && rect[2]<=getWidth()+1f && rect[3]<=getHeight()+1f;
    }
    @Override protected void onDraw(Canvas canvas){
        frames.beginFrame();
        if(anim<1)blend();
        long nowMs=SystemClock.uptimeMillis();
        double dt=lastFrame==0?0:Math.min(.05,(nowMs-lastFrame)/1000.0);lastFrame=nowMs;
        float density=getResources().getDisplayMetrics().density;
        boolean animators=ValueAnimator.areAnimatorsEnabled();
        boolean moving=false;
        for(String name:BotIdentity.NAMES){
            float[] rect=now.get(name);if(rect==null || rect[2]<=rect[0])continue;
            Motion motion=motions.get(name);
            double kept=motion.intensity;if(kept>0)moving=true;if(!animators)motion.intensity=0;motion.step(animators?dt:0);if(!animators)motion.intensity=kept;
            boolean lead=name.equals(BotIdentity.PRIMARY);
            NeedsYouTiles.Tile attention=needsYouByName.get(name);
            String caption=attention!=null?attention.line:lines.get(name);if(caption==null)caption="";
            paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(TILE_FILL);
            canvas.drawRoundRect(rect[0],rect[1],rect[2],rect[3],12*density,12*density,paint);
            float pad=14*density,width=rect[2]-rect[0];
            float inset=CAPTION_INSET_DP*density,bottom=12*density,gap=4*density;
            text.setColor(Color.WHITE);text.setTextSize((lead?22f:13f)*density);text.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics nameMetrics=text.getFontMetrics();
            StaticLayout layout=null;
            if(!caption.isEmpty()){
                float scaled=getResources().getDisplayMetrics().scaledDensity;
                linePaint.setColor(attention!=null?Color.WHITE:CAPTION_COLOR);linePaint.setTextSize(captionSp(lead)*(scaled>0?scaled:density));
                layout=singleLine(caption,linePaint,Math.max(1,Math.round(width-inset*2)));
            }
            float captionBlock=layout==null?0:layout.getHeight()+gap;
            float nameBaseline=rect[3]-bottom-captionBlock-nameMetrics.descent;
            float shapeBox=Math.min(width-pad*2,nameBaseline+nameMetrics.ascent-rect[1]-pad);
            float radius=shapeBox*(lead?.36f:.28f);
            float avatarX=(rect[0]+rect[2])/2f,avatarY=rect[1]+pad+shapeBox/2f;
            {int mi=BotIdentity.index(name);if(mi>=0){markX[mi]=avatarX;markY[mi]=avatarY;markR[mi]=shapeBox>8?radius:0f;}}
            if(attention!=null && !quietHours && shapeBox>8)drawGlow(canvas,name,rect,avatarX,avatarY,radius,density);
            if(workingRing(name) && shapeBox>8){
                canvas.save();
                canvas.clipRect(rect[0],rect[1],rect[2],rect[3]);
                int ri=BotIdentity.index(name);
                float phase=ringPhase[ri];
                if(ringSpins[ri]){phase=(float)(StagePose.RING_FROZEN+(Math.max(0L,System.currentTimeMillis()-ringSince[ri])/3200.0)*Math.PI*2);moving=true;}
                marks.drawRing(canvas,avatarX,avatarY,radius,name,prefsColor(name),phase);
                canvas.restore();
            }
            if(shapeBox>8){canvas.save();canvas.clipRect(rect[0],rect[1],rect[2],rect[3]);drawAvatar(canvas,name,motion,avatarX,avatarY,radius);canvas.restore();}
            canvas.drawText(name,(rect[0]+rect[2])/2,nameBaseline,text);
            if(layout!=null){canvas.save();canvas.translate(rect[0]+inset,nameBaseline+nameMetrics.descent+gap);layout.draw(canvas);canvas.restore();}
            if(attention==null && (working.contains(name) || unread.contains(name))){
                paint.setColor(working.contains(name)?0xff3ddc84:0xff7eb6ff);
                canvas.drawCircle(rect[0]+16*density,rect[1]+16*density,4*density,paint);
            }
        }
        if(wakeListening){text.setColor(0xff7d838c);text.setTextSize(13f*density);text.setTextAlign(Paint.Align.CENTER);canvas.drawText(VoiceCopy.WAKE_HINT,getWidth()/2f,getHeight()-20f*density,text);}
        if(quietHours){
            canvas.drawColor(QUIET_DIM);
            drawQuietNote(canvas,density);
        }
        for(String name:BotIdentity.NAMES){
            if(!handChip(name))continue;
            float[] rect=now.get(name);if(rect==null || rect[2]<=rect[0])continue;
            drawBorder(canvas,rect,NeedsYouTint.border(name,prefsColor(name),quietHours),density);
            drawChip(canvas,name,rect,name.equals(BotIdentity.PRIMARY),density);
        }
        for(String name:BotIdentity.NAMES)if(drawCelebration(canvas,name))moving=true;
        drawPill(canvas,density);
        removeCallbacks(frameTick);
        long delay=FrameThrottle.STOP;
        if(getVisibility()==VISIBLE)delay=frames.afterDraw(moving,animators);
        scheduledFrameDelay=delay;
        if(delay>=0)postDelayed(frameTick,delay);
    }
    private void drawGlow(Canvas canvas,String name,float[] rect,float cx,float cy,float radius,float density){
        RadialGradient shader=glowShaders.get(name);if(shader==null)return;
        glowMatrix.setScale(radius*1.75f,radius*1.75f);
        glowMatrix.postTranslate(cx,cy);
        shader.setLocalMatrix(glowMatrix);
        glowPaint.setShader(shader);
        tileClip.rewind();
        tileRound.set(rect[0],rect[1],rect[2],rect[3]);
        tileClip.addRoundRect(tileRound,12*density,12*density,Path.Direction.CW);
        canvas.save();
        canvas.clipPath(tileClip);
        canvas.drawCircle(cx,cy,radius*1.75f,glowPaint);
        canvas.restore();
        glowPaint.setShader(null);
    }
    private void drawBorder(Canvas canvas,float[] rect,int color,float density){
        float half=density;
        borderPaint.setStrokeWidth(2f*density);
        borderPaint.setColor(color);
        canvas.drawRoundRect(rect[0]+half,rect[1]+half,rect[2]-half,rect[3]-half,12*density,12*density,borderPaint);
    }
    private void drawChip(Canvas canvas,String name,float[] rect,boolean lead,float density){
        float diameter=(lead?44f:32f)*density;
        float cx=rect[2]-26f*density,cy=rect[1]+26f*density;
        int saved=prefsColor(name);
        chipPaint.setShader(null);
        chipPaint.setStyle(Paint.Style.FILL);
        chipPaint.setColor(NeedsYouTint.chipFill(quietHours));
        canvas.drawCircle(cx,cy,diameter/2f,chipPaint);
        chipPaint.setStyle(Paint.Style.STROKE);
        chipPaint.setStrokeWidth(2f*density);
        chipPaint.setColor(NeedsYouTint.chipRing(name,saved,quietHours));
        canvas.drawCircle(cx,cy,diameter/2f-density,chipPaint);
        float glyph=diameter*0.62f;
        canvas.save();
        canvas.translate(cx-glyph/2f,cy-glyph/2f);
        canvas.scale(glyph/24f,glyph/24f);
        chipPaint.setStyle(Paint.Style.FILL);
        chipPaint.setColor(NeedsYouTint.chipGlyph(quietHours));
        canvas.drawPath(hand,chipPaint);
        canvas.restore();
    }
    private void drawQuietNote(Canvas canvas,float density){
        float[] rect=now.get(BotIdentity.PRIMARY);
        if(rect==null || rect[2]<=rect[0])return;
        float size=14f*density,left=rect[0]+28f*density,top=rect[1]+12f*density;
        canvas.save();
        canvas.translate(left,top);
        canvas.scale(size/16f,size/16f);
        notePaint.setStyle(Paint.Style.FILL);
        notePaint.setColor(QUIET_NOTE);
        canvas.drawPath(moon,notePaint);
        canvas.restore();
        notePaint.setTextAlign(Paint.Align.LEFT);
        notePaint.setTextSize(12f*density);
        Paint.FontMetrics metrics=notePaint.getFontMetrics();
        float baseline=top+size/2f-(metrics.ascent+metrics.descent)/2f;
        canvas.drawText(quietNote,left+size+6f*density,baseline,notePaint);
    }
    private void drawPill(Canvas canvas,float density){
        if(pillLabel.isEmpty() || pillBounds.width()<=0f)return;
        float radius=EdgePill.RADIUS_DP*density;
        pillFill.setStyle(Paint.Style.FILL);
        pillFill.setColor(EdgePill.FILL);
        canvas.drawRoundRect(pillBounds,radius,radius,pillFill);
        pillFill.setStyle(Paint.Style.STROKE);
        pillFill.setStrokeWidth(density);
        pillFill.setColor(EdgePill.STROKE);
        float inset=density/2f;
        canvas.drawRoundRect(pillBounds.left+inset,pillBounds.top+inset,pillBounds.right-inset,pillBounds.bottom-inset,radius,radius,pillFill);
        pillPaint.setColor(Color.WHITE);
        pillPaint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics=pillPaint.getFontMetrics();
        float baseline=pillBounds.centerY()-(metrics.ascent+metrics.descent)/2f;
        canvas.drawText(pillLabel,pillBounds.centerX(),baseline,pillPaint);
    }
    static final int TILE_FILL=0xff141414,CAPTION_COLOR=0xffa0a4ab,QUIET_NOTE=0xffc5cad1,QUIET_DIM=0x4d000000;
    static final float CAPTION_INSET_DP=12f;
    /** Small tiles stay at 13sp. Max's featured tile uses the larger caption. */
    static float captionSp(boolean lead){return lead?18f:13f;}
    static StaticLayout singleLine(String caption,TextPaint paint,int width){
        int avail=Math.max(1,width);
        return StaticLayout.Builder.obtain(caption,0,caption.length(),paint,avail)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setEllipsizedWidth(avail)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build();
    }
    private void drawAvatar(Canvas canvas,String name,Motion motion,float cx,float cy,float radius){
        canvas.save();canvas.clipRect(cx-radius*1.6f,cy-radius*1.6f,cx+radius*1.6f,cy+radius*1.6f);
        canvas.translate(cx,cy);canvas.scale(radius,radius);
        int color=prefsColor(name);
        SolidAvatar avatar=avatars.get(BotIdentity.shape(name));
        if(name.equals(BotIdentity.PRIMARY)){crown.prepare(motion);crown.draw(canvas,false);}
        if(avatar!=null)avatar.draw(canvas,motion,color,radius);
        else{paint.setColor(color);canvas.drawPath(SolidAvatar.outline(BotIdentity.shape(name)),paint);}
        canvas.save();canvas.clipPath(face);
        if(name.equals(BotIdentity.PRIMARY))crown.drawContact(canvas);
        paint.setShader(null);paint.setColor(color==BallColors.WHITE?0xff12161a:Color.WHITE);
        double blink=motion.blink();
        for(double ex:new double[]{-.28,.28}){
            eye.rewind();boolean started=false;
            for(int i=0;i<=64;i++){
                double a=i/64.0*Math.PI*2,lx=.105*Math.cos(a),ly=(.16*Math.sin(a)+.075*Math.signum(Math.sin(a)))*blink;
                double x=ex+lx,y=-.17+ly,z=avatar==null?Math.sqrt(Math.max(0,1-x*x-y*y)):avatar.surfaceZ(x,y);
                motion.rotate(x,y,z,point);
                if(point[2]<.015)continue;
                if(!started){eye.moveTo((float)point[0],(float)point[1]);started=true;}else eye.lineTo((float)point[0],(float)point[1]);
            }
            if(started){eye.close();canvas.drawPath(eye,paint);}
        }
        canvas.restore();
        if(name.equals(BotIdentity.PRIMARY))crown.draw(canvas,true);
        canvas.restore();
    }
    private int prefsColor(String name){
        return getContext().getSharedPreferences("home",0).getInt("bot_color_"+name,BotIdentity.color(name));
    }
    List<String> needsYouOrder(){
        List<String> names=new ArrayList<>();
        for(NeedsYouTiles.Tile tile:needsYouTiles)names.add(tile.bot);
        return names;
    }
    String tileDescription(String name){
        if(stage!=null){
            BotStageState.Frame frame=stage.frame(name);
            NeedsYou.Item[] raised=stage.hands(name);
            if(raised.length>0 || frame.state==BotStageState.State.NEEDS_YOU){
                NeedsYouTiles.Tile tile=needsYouByName.get(name);
                String question=tile!=null?tile.spoken:spokenQuestion(raised,frame);
                return WallTalkBack.tile(name,BotStageState.State.NEEDS_YOU,question==null?"":question);
            }
            if(frame.state==BotStageState.State.WORKING || frame.state==BotStageState.State.DONE)
                return WallTalkBack.tile(name,frame.state,null);
        }
        NeedsYouTiles.Tile tile=needsYouByName.get(name);
        boolean busy=working.contains(name),fresh=unread.contains(name);
        if(tile!=null)return WallTalkBack.tile(name,tile.spoken,busy,fresh);
        return WallTalkBack.tile(name,null,busy,fresh);
    }
    private static String spokenQuestion(NeedsYou.Item[] raised,BotStageState.Frame frame){
        if(raised.length>0 && raised[0]!=null){
            if(raised[0].question!=null && !raised[0].question.isEmpty())return raised[0].question;
            if(raised[0].questionShort!=null && !raised[0].questionShort.isEmpty())return raised[0].questionShort;
        }
        return frame.question==null?"":frame.question;
    }
    /** Finished-work confetti, drawn over the neighbors so it is not cut off at the tile edge. */
    private boolean drawCelebration(Canvas canvas,String name){
        int index=BotIdentity.index(name);
        StageMarks fx=confetti.get(name);
        if(index<0 || fx==null || markR[index]<=1f)return false;
        long up=SystemClock.uptimeMillis();
        fx.step(up);
        if(!fx.confettiLive())return false;
        fx.drawConfetti(canvas,markX[index],markY[index],markR[index],up);
        return true;
    }
    private boolean workingRing(String name){
        int index=BotIdentity.index(name);
        return index>=0 && ringOn[index];
    }
    private boolean handChip(String name){
        if(stage!=null){
            int index=BotIdentity.index(name);
            return index>=0 && chipOn[index];
        }
        return needsYouByName.containsKey(name);
    }
    String pillDescription(){return pillLabel.isEmpty()?"":pillSpoken;}
    String pillLabel(){return pillLabel;}
    void pillFrame(RectF out){out.set(pillBounds);}
    String quietNote(){return quietHours?quietNote:"";}
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()!=MotionEvent.ACTION_UP)return true;
        if(open==null && needsYouOpen==null)return true;
        float x=event.getX(),y=event.getY();
        if(!pillLabel.isEmpty() && pillBounds.contains(x,y)){activate(pillBot,true);return true;}
        for(String name:BotIdentity.NAMES){
            float[] rect=now.get(name);
            if(rect!=null && x>=rect[0] && x<=rect[2] && y>=rect[1] && y<=rect[3]){activate(name,needsYouByName.containsKey(name));break;}
        }
        return true;
    }
    private void activate(String name){activate(name,needsYouByName.containsKey(name));}
    private void activate(String name,boolean card){
        Open target=card&&needsYouOpen!=null?needsYouOpen:open;
        if(target!=null && name!=null && !name.isEmpty())target.bot(name);
    }
    private void copyBounds(String name,Rect out){
        float[] rect=now.get(name);
        if(rect==null){out.set(0,0,1,1);return;}
        out.set(Math.round(rect[0]),Math.round(rect[1]),Math.round(Math.max(rect[2],rect[0]+1f)),Math.round(Math.max(rect[3],rect[1]+1f)));
        if(out.right<=out.left)out.right=out.left+1;
        if(out.bottom<=out.top)out.bottom=out.top+1;
    }
    private final class WallNodes extends ExploreByTouchHelper {
        private final Rect hit=new Rect();
        WallNodes(View host){super(host);}
        @Override protected int getVirtualViewAt(float x,float y){
            if(!pillLabel.isEmpty() && pillBounds.contains(x,y))return BotIdentity.NAMES.length;
            for(int i=0;i<BotIdentity.NAMES.length;i++){
                float[] rect=now.get(BotIdentity.NAMES[i]);
                if(rect!=null && x>=rect[0] && x<=rect[2] && y>=rect[1] && y<=rect[3])return i;
            }
            return INVALID_ID;
        }
        @Override protected void getVisibleVirtualViews(List<Integer> views){
            for(int i=0;i<BotIdentity.NAMES.length;i++)if(isPlaced(BotIdentity.NAMES[i]))views.add(i);
            if(!pillLabel.isEmpty())views.add(BotIdentity.NAMES.length);
        }
        @Override protected void onPopulateNodeForVirtualView(int id,AccessibilityNodeInfoCompat node){
            if(id==BotIdentity.NAMES.length){
                node.setContentDescription(pillSpoken);
                hit.set(Math.round(pillBounds.left),Math.round(pillBounds.top),Math.round(pillBounds.right),Math.round(pillBounds.bottom));
            }else{
                String name=BotIdentity.NAMES[id];
                node.setContentDescription(tileDescription(name));
                copyBounds(name,hit);
            }
            if(hit.width()<=0 || hit.height()<=0)hit.set(0,0,1,1);
            node.setBoundsInParent(hit);
            node.setClickable(true);
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
        }
        @Override protected boolean onPerformActionForVirtualView(int id,int action,Bundle arguments){
            if(action!=AccessibilityNodeInfoCompat.ACTION_CLICK)return false;
            if(id==BotIdentity.NAMES.length){activate(pillBot,true);return true;}
            if(id<0 || id>=BotIdentity.NAMES.length)return false;
            activate(BotIdentity.NAMES[id]);
            return true;
        }
    }
}

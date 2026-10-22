package dev.grokhome;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.animation.ValueAnimator;
import android.os.Bundle;
import java.util.concurrent.*;

public final class BotView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Motion motion = new Motion();
    private final Path eyePath = new Path(), sphere = new Path(), bodyOutline = new Path();
    private final CrownRenderer crown = new CrownRenderer();
    private final double[] point = new double[3];
    private static final double[] EYES = {-.28,.28};
    private final Shader whiteShade = new RadialGradient(-.35f,-.45f,1.8f,
        new int[]{0xffffffff,0xfff1f2f5,0xffd1d7dd,0xff78828d},new float[]{0,.30f,.65f,1},Shader.TileMode.CLAMP);
    private final Shader whiteRim = new LinearGradient(-1,0,.1f,0,new int[]{0x948bd0f0,0x1a8bd0f0,0x008bd0f0},new float[]{0,.45f,1},Shader.TileMode.CLAMP);
    private final Shader eyes = new LinearGradient(-.4f,-.3f,.4f,.3f,new int[]{0xff17232b,0xff080c10,0xff273039},new float[]{0,.5f,1},Shader.TileMode.CLAMP);
    private final Shader shadow = new RadialGradient(0,1.22f,1.85f,new int[]{0xff9bb5ca,0xa89bb5ca,0x3c9bb5ca,0x00000000},new float[]{0,.32f,.72f,1},Shader.TileMode.CLAMP);
    private Shader shade=whiteShade,rim=whiteRim;
    private int ballColor=BallColors.WHITE;
    private long last;
    private boolean active, primaryBot,chatLayout,working,unread,speaking;
    static final float CROWN_RISE = 1.38f;
    private float stageSize=1,spotlightBrightness=1,animationIntensity=1;
    private float dockRadiusCap, dockMinCenter;
    private final Paint notification=new Paint(Paint.ANTI_ALIAS_FLAG);
    private String botName="",shape="sphere";
    private volatile SolidAvatar avatar;
    interface Navigation {void select(String name,int direction);}
    private Navigation navigation;
    private final boolean rendererOnly;
    private BotView[] lineup;
    private ExecutorService geometryLoader;
    private volatile boolean disposed;
    private ScaleGestureDetector scaleDetector;
    private ValueAnimator zoomAnimator,positionAnimator,liftAnimator;
    private float statusShift,statusCeiling;
    private boolean shiftReady;
    private float zoom,position,touchX,touchY,dragStart,scaleTotal=1,scaleStart;
    private boolean debugFocus,debugFocusQueued;
    private int debugFocusRetries;
    private float debugGapScale=1f,debugSizeScale=1f;
    private String debugNeeds="",debugWorking="",debugDone="";
    private final float[] debugMarkLeft=nanSlots(),debugMarkRight=nanSlots(),debugCenter=nanSlots(),debugRadius=nanSlots();
    private boolean overview,dragging,multiTouch,notifyingSelection;
    private final BotTouchFeedback touchFeedback;
    private final PokeMood pokes = new PokeMood();
    private float anger;
    private ValueAnimator angerAnimator,trickAnimator;
    private BotTrick pose;
    private final StageMarks marks;
    private final StagePose visual=new StagePose();
    private BotStageState.Frame stageFrame;
    private float hopDraw,shiftDraw;
    private boolean slotted=true,carouselSettling;
    private float glowDepth,markPresence=1f;
    private HandListener handListener;
    private int[] slotPriority;
    private boolean[] slotEligible,slotGrant,slotRanked;
    private String lastTalk="";
    private final Paint captions=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect labelBounds=new Rect();
    private final LightingColorFilter[] shadeFilters=new LightingColorFilter[256];
    interface HandListener { void open(String name); }
    public BotView(Context context){this(context,false);}
    private BotView(Context context,boolean rendererOnly){
        super(context);this.rendererOnly=rendererOnly;marks=new StageMarks(context);touchFeedback=rendererOnly?null:new BotTouchFeedback(context);sphere.addCircle(0,0,1,Path.Direction.CW);cacheOutline();
        for(int level=0;level<shadeFilters.length;level++)shadeFilters[level]=new LightingColorFilter(Color.rgb(level,level,level),0);
        if(!rendererOnly){
            lineup=new BotView[BotIdentity.NAMES.length];geometryLoader=Executors.newSingleThreadExecutor();
            android.content.SharedPreferences prefs=context.getSharedPreferences("home",0);
            for(int i=0;i<lineup.length;i++){
                BotView item=new BotView(context,true);item.botName=BotIdentity.NAMES[i];item.primaryBot=i==0;item.shape=BotIdentity.shape(item.botName);item.cacheOutline();
                item.setBallColor(prefs.getInt("bot_color_"+item.botName,BotIdentity.color(item.botName)));lineup[i]=item;
            }
            scaleDetector=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScaleBegin(ScaleGestureDetector detector){
                    cancelPosition();if(zoomAnimator!=null)zoomAnimator.cancel();multiTouch=true;dragging=false;scaleTotal=1;scaleStart=zoom;return true;
                }
                @Override public boolean onScale(ScaleGestureDetector detector){
                    scaleTotal*=detector.getScaleFactor();zoom=clamp(scaleStart-(float)Math.log(scaleTotal)*1.6f,0,1);invalidate();return true;
                }
                @Override public void onScaleEnd(ScaleGestureDetector detector){boolean next=scaleTotal<.90f || scaleTotal<=1.10f && zoom>.5f;if(next!=overview)touch();setOverview(next);}
            });
            setFocusable(true);postDelayed(this::warmLineup,800);
        }
        if(!rendererOnly)applyStagePreferences();
        describe();
    }
    private void warmLineup(){
        if(disposed)return;
        for(BotView item:lineup)if(!item.shape.equals("sphere"))geometryLoader.execute(()->{
            if(disposed)return;item.ensureAvatar();post(()->{if(!disposed){if(botName.equals(item.botName))avatar=item.avatar;invalidate();}});
        });
    }
    private synchronized void ensureAvatar(){if(avatar==null && !shape.equals("sphere"))avatar=new SolidAvatar(getContext(),shape);}
    void setNavigation(Navigation listener){navigation=listener;}
    boolean isOverview(){return overview;}
    void restoreOverview(boolean value){overview=value;zoom=value?1:0;describe();invalidate();}
    void applyStagePreferences(){
        android.content.SharedPreferences prefs=getContext().getSharedPreferences("home",0);
        stageSize=clamp(prefs.getInt("stage_size",100)/100f,.70f,1.25f);
        spotlightBrightness=clamp(prefs.getInt("stage_light",100)/100f,0,1.5f);
        animationIntensity=clamp(prefs.getInt("stage_motion",100)/100f,0,1.5f);
        if(lineup!=null)for(BotView item:lineup){item.stageSize=stageSize;item.spotlightBrightness=spotlightBrightness;item.animationIntensity=animationIntensity;}
        invalidate();
    }
    void setHandListener(HandListener listener){handListener=listener;}
    void setStageFrame(String name,BotStageState.Frame frame){
        int index=BotIdentity.index(name);if(index<0)return;
        if(lineup!=null)lineup[index].applyFrame(frame);
        if(botName.equals(name) || rendererOnly)applyFrame(frame);
        invalidate();
    }
    private void applyFrame(BotStageState.Frame frame){
        String talk=frame==null?"":frame.state+"|"+frame.question;
        stageFrame=frame;
        if(frame!=null && frame.celebrateToken!=null && !frame.reduced && !frame.quiet)marks.arm(frame.celebrateToken,android.os.SystemClock.uptimeMillis());
        marks.prepare(botName,ballColor);
        if(!talk.equals(lastTalk)){lastTalk=talk;describe();}
    }
    void setActivity(String name,boolean working,boolean unread){
        int index=BotIdentity.index(name);if(index<0 || lineup==null)return;
        lineup[index].working=working;lineup[index].unread=unread;
        if(botName.equals(name)){this.working=working;this.unread=unread;describe();}invalidate();
    }
    private static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
    private static int wrap(int index){return Math.floorMod(index,BotIdentity.NAMES.length);}
    public void setBotName(String name){
        botName=name;primaryBot=BotIdentity.PRIMARY.equals(name);int index=BotIdentity.index(name);
        if(!rendererOnly && index>=0){
            BotView item=lineup[index];item.ensureAvatar();shape=item.shape;cacheOutline();avatar=item.avatar;motion=item.motion;working=item.working;unread=item.unread;stageFrame=item.stageFrame;ballColor=item.ballColor;shade=item.shade;rim=item.rim;marks.prepare(botName,ballColor);
            if(!notifyingSelection){cancelPosition();position=index;}
        }else{String next=BotIdentity.shape(name);if(!next.equals(shape)){shape=next;avatar=null;ensureAvatar();cacheOutline();}}
        describe();invalidate();
    }
    void setOverview(boolean value){
        if(rendererOnly)return;
        if(!value)clearDebugOverview();
        if(overview){cancelPosition();selectPosition(Math.round(position),0);}
        overview=value;getContext().getSharedPreferences("home",0).edit().putBoolean("stage_overview",value).apply();
        if(zoomAnimator!=null)zoomAnimator.cancel();
        if(!ValueAnimator.areAnimatorsEnabled()){zoom=value?1:0;invalidate();describe();return;}
        zoomAnimator=ValueAnimator.ofFloat(zoom,value?1:0);zoomAnimator.setDuration(340);zoomAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        zoomAnimator.addUpdateListener(a->{zoom=(float)a.getAnimatedValue();invalidate();});zoomAnimator.start();describe();
    }
    private void cancelPosition(){if(positionAnimator!=null){positionAnimator.removeAllListeners();positionAnimator.cancel();positionAnimator=null;}}
    private void selectPosition(int target,int direction){
        position=wrap(target);String name=BotIdentity.NAMES[wrap(target)];
        if(navigation!=null && !name.equals(botName)){notifyingSelection=true;navigation.select(name,direction);notifyingSelection=false;}
        describe();invalidate();
    }
    void centerBot(String name){
        int index=BotIdentity.index(name);if(index<0)return;
        int current=Math.round(position),delta=wrap(index-wrap(current));if(delta>lineup.length/2)delta-=lineup.length;
        settle(current+delta);
    }
    /** Debug overview. Centers the flagged bots and pulls spacing in until their marks fit. */
    void focusDebugOverview(String needsYou,String working,String done){
        if(rendererOnly)return;
        debugNeeds=needsYou==null?"":needsYou;
        debugWorking=working==null?"":working;
        debugDone=done==null?"":done;
        debugFocusQueued=true;
        debugFocusRetries=0;
        applyQueuedDebugFocus();
    }
    float debugMarkLeft(String name){return debugMark(debugMarkLeft,name);}
    float debugMarkRight(String name){return debugMark(debugMarkRight,name);}
    float debugCenter(String name){return debugMark(debugCenter,name);}
    float debugRadius(String name){return debugMark(debugRadius,name);}
    private float debugMark(float[] values,String name){
        int index=BotIdentity.index(name);
        return index<0||values==null?Float.NaN:values[index];
    }
    private void clearDebugOverview(){
        debugFocus=false;debugFocusQueued=false;debugGapScale=1f;debugSizeScale=1f;
    }
    private void applyQueuedDebugFocus(){
        if(!debugFocusQueued)return;
        if(getWidth()<=0||getHeight()<=0){
            if(debugFocusRetries++>5)return;
            post(this::applyQueuedDebugFocus);
            return;
        }
        debugFocusRetries=0;
        float density=getResources().getDisplayMetrics().density;
        DebugOverviewFocus.Fit fit=DebugOverviewFocus.fit(debugNeeds,debugWorking,debugDone,getWidth(),getHeight(),density,stageSize);
        if(fit==null){debugFocusQueued=false;return;}
        debugFocus=true;debugFocusQueued=false;
        debugGapScale=fit.gapScale;debugSizeScale=fit.sizeScale;
        if(zoomAnimator!=null){zoomAnimator.cancel();zoomAnimator=null;}
        cancelPosition();
        overview=true;zoom=1f;
        if(lineup!=null)position=wrapFloat(fit.slot,lineup.length);
        describe();invalidate();
    }
    private static float[] nanSlots(){
        float[] values=new float[BotIdentity.NAMES.length];
        for(int i=0;i<values.length;i++)values[i]=Float.NaN;
        return values;
    }
    /** Debug overview. Lands on a fractional slot and does not snap to a neighbor. */
    void focusSlot(float slot){
        if(rendererOnly||lineup==null||Float.isNaN(slot))return;
        int count=lineup.length;
        float delta=wrapFloat(slot,count)-wrapFloat(position,count);
        if(delta>count/2f)delta-=count;
        if(delta<-count/2f)delta+=count;
        settleSlot(position+delta,count);
    }
    void nextBot(int direction){if(overview)settle(Math.round(position)+direction);else if(navigation!=null){touch();navigation.select(BotIdentity.NAMES[wrap(BotIdentity.index(botName)+direction)],direction);}}
    void touch(){if(touchFeedback!=null)touchFeedback.play();}
    private void settle(int target){
        cancelPosition();int direction=target>=position?1:-1;
        if(!ValueAnimator.areAnimatorsEnabled()){selectPosition(target,direction);return;}
        positionAnimator=ValueAnimator.ofFloat(position,target);positionAnimator.setDuration(260);positionAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        positionAnimator.addUpdateListener(a->{position=(float)a.getAnimatedValue();invalidate();});
        positionAnimator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator animation){positionAnimator=null;selectPosition(target,direction);}});positionAnimator.start();
    }
    private void settleSlot(float target,int count){
        cancelPosition();
        if(!ValueAnimator.areAnimatorsEnabled()){position=wrapFloat(target,count);describe();invalidate();return;}
        positionAnimator=ValueAnimator.ofFloat(position,target);positionAnimator.setDuration(260);positionAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        positionAnimator.addUpdateListener(a->{position=(float)a.getAnimatedValue();invalidate();});
        positionAnimator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator animation){positionAnimator=null;position=wrapFloat(target,count);describe();invalidate();}});positionAnimator.start();
    }
    private static float wrapFloat(float value,int count){float mod=value%count;if(mod<0f)mod+=count;return mod;}
    @Override public boolean onTouchEvent(MotionEvent event){
        if(rendererOnly)return super.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                cancelPosition();touchX=event.getX();touchY=event.getY();dragStart=position;dragging=false;multiTouch=false;return true;
            case MotionEvent.ACTION_POINTER_DOWN:multiTouch=true;return true;
            case MotionEvent.ACTION_MOVE:
                if(multiTouch)return true;
                float dx=event.getX()-touchX,dy=event.getY()-touchY;
                if(!dragging && Math.abs(dx)>ViewConfiguration.get(getContext()).getScaledTouchSlop() && Math.abs(dx)>Math.abs(dy)*1.4f){dragging=true;getParent().requestDisallowInterceptTouchEvent(true);}
                if(dragging && overview){position=dragStart-dx/spacing();invalidate();}return true;
            case MotionEvent.ACTION_UP:
                if(multiTouch){multiTouch=false;return true;}
                if(!dragging){String hand=chipAt(event.getX(),event.getY());if(hand!=null){touch();if(overview && !hand.equals(botName)){int index=BotIdentity.index(hand),current=Math.round(position),deltaIndex=wrap(index-wrap(current));if(deltaIndex>lineup.length/2)deltaIndex-=lineup.length;settle(current+deltaIndex);}if(handListener!=null)handListener.open(hand);return true;}}
                float delta=event.getX()-touchX;
                if(dragging){
                    int direction=delta<0?1:-1;
                    if(overview){int target=Math.round(position);if(target==Math.round(dragStart) && Math.abs(delta)>dp(45))target+=direction;settle(target);}
                    else if(Math.abs(delta)>dp(45) && navigation!=null)navigation.select(BotIdentity.NAMES[wrap(BotIdentity.index(botName)+direction)],direction);
                }else if(overview){
                    if(Math.abs(event.getY()-stageY())<getHeight()*.32f){
                        int offset=Math.round((event.getX()-getWidth()/2f)/spacing());
                        if(offset==0)performClick();else if(Math.abs(offset)<=2)settle(Math.round(position)+offset);
                    }else settle(Math.round(position));
                }else performClick();return true;
            case MotionEvent.ACTION_CANCEL:
                if(overview && dragging)settle(Math.round(position));dragging=false;multiTouch=false;return true;
            default:return true;
        }
    }
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
    private float sp(float value){return value*getResources().getDisplayMetrics().scaledDensity;}
    private void cacheOutline(){
        bodyOutline.rewind();
        if(shape.equals("sphere"))bodyOutline.addCircle(0,0,1,Path.Direction.CW);
        else bodyOutline.set(SolidAvatar.outline(shape));
    }
    private float spacing(){return getWidth()*.185f;}
    private float stageY(){
        // Carousel and the dock stay on main's 0.37 row. Single view starts at 0.40 and lifts toward StagePlacement.SINGLE_Y.
        float fraction=(overview || zoom>.001f || docked())?HomeBand.STAGE_CENTER_FRACTION:.40f;
        return getHeight()*(chatLayout?.46f:fraction);
    }
    void setDockPlacement(float radiusCapPx, float minCenterYPx){dockRadiusCap=radiusCapPx;dockMinCenter=minCenterYPx;invalidate();}
    void clearDockPlacement(){dockRadiusCap=0;dockMinCenter=0;invalidate();}
    /** Opens the eyes. Time 0 is mid-blink, so a single frozen frame would show them shut. */
    void restEyes(){motion.seed(1.39);motion.step(0.016);invalidate();}
    float zoomLevel(){return zoom;}
    float stageScale(){return stageSize;}
    float crownTop(){return centerY()-CROWN_RISE*drawnRadius(getHeight()>getWidth());}
    float restingBodyBottom(){
        if(overview){float smallR=Math.min(getWidth()*.083f,getHeight()*.21f)*stageSize;return stageY()+smallR;}
        return centerY()+drawnRadius(getHeight()>getWidth());
    }
    /** Bottom of the centered overview name, matching the caption drawn under the bot. */
    float overviewNameBottom(){
        return overviewNameBottom(getWidth(),getHeight(),getResources().getDisplayMetrics().density,stageSize);
    }
    static float overviewNameBottom(int width,int height,float density,float stageScale){
        float smallR=Math.min(width*.083f,height*.21f)*stageScale;
        return height*.40f+smallR*1.8f+18f*density;
    }
    private boolean docked(){return dockRadiusCap>0 || dockMinCenter>0;}
    private float drawnRadius(boolean portrait){
        float radius=Math.min(getWidth()*(chatLayout?.27f:portrait?.31f:.157f),getHeight()*(chatLayout?(primaryBot?.24f:.28f):.31f))*stageSize;
        if(!chatLayout && dockRadiusCap<=0)radius*=HomeBand.SINGLE_BOT_SCALE;
        if(dockRadiusCap>0)radius=Math.min(radius,dockRadiusCap);
        return radius;
    }
    private float centerY(){float y=stageY();if(dockMinCenter>0)y=Math.max(y,dockMinCenter);return y;}
    /** First call snaps. Later calls animate unless the system has reduced motion on. */
    void setStatusShift(float value,float ceilingPx){
        statusCeiling=ceilingPx;float next=clamp(value,0f,1f);
        if(!shiftReady || !ValueAnimator.areAnimatorsEnabled()){
            shiftReady=true;if(liftAnimator!=null)liftAnimator.cancel();statusShift=next;invalidate();return;
        }
        if(Math.abs(next-statusShift)<.001f){invalidate();return;}
        if(liftAnimator!=null)liftAnimator.cancel();
        liftAnimator=ValueAnimator.ofFloat(statusShift,next);liftAnimator.setDuration(340);liftAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        liftAnimator.addUpdateListener(a->{statusShift=(float)a.getAnimatedValue();invalidate();});liftAnimator.start();
    }
    @Override protected void onDetachedFromWindow(){
        disposed=true;if(geometryLoader!=null)geometryLoader.shutdownNow();if(zoomAnimator!=null)zoomAnimator.cancel();if(liftAnimator!=null)liftAnimator.cancel();if(angerAnimator!=null)angerAnimator.cancel();if(trickAnimator!=null)trickAnimator.cancel();cancelPosition();if(touchFeedback!=null)touchFeedback.release();super.onDetachedFromWindow();
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);if(rendererOnly)return;
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,overview?"Zoom in to "+botName:"Interrupt speech"));
        if(stageFrame!=null && stageFrame.state==BotStageState.State.NEEDS_YOU)info.addAction(new AccessibilityNodeInfo.AccessibilityAction(ACTION_ANSWER,BotTalkBack.answerAction(botName)));
    }
    private static final int ACTION_ANSWER=0x47524b21;
    @Override public boolean performAccessibilityAction(int action,Bundle arguments){
        if(action==ACTION_ANSWER && stageFrame!=null && stageFrame.state==BotStageState.State.NEEDS_YOU){if(handListener!=null)handListener.open(botName);return true;}
        if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){
            int direction=action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1;if(overview)settle(Math.round(position)+direction);else if(navigation!=null)navigation.select(BotIdentity.NAMES[wrap(BotIdentity.index(botName)+direction)],direction);return true;
        }
        return super.performAccessibilityAction(action,arguments);
    }
    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider(){
        if(rendererOnly)return null;
        if(handNodes==null)handNodes=new HandNodes();
        return handNodes;
    }
    private HandNodes handNodes;
    private void describe(){
        String text=BotTalkBack.contentDescription(botName,shape,BallColors.name(ballColor),ballColor==BallColors.WHITE,primaryBot,overview,speaking,working,unread);
        if(stageFrame!=null){String extra=BotTalkBack.stateSentence(stageFrame.state,stageFrame.question);if(!extra.isEmpty())text=text+" "+extra;}
        setContentDescription(text);
    }
    public void setChatLayout(boolean value){chatLayout=value;invalidate();}
    public void setSpeaking(boolean value) { motion.setSpeaking(value); if(speaking==value)return; speaking=value; describe(); }
    public void setBallColor(int value){
        ballColor=BallColors.normalize(value);
        if(!rendererOnly && lineup!=null){int index=BotIdentity.index(botName);if(index>=0)lineup[index].setBallColor(ballColor);}
        if(ballColor==BallColors.WHITE){shade=whiteShade;rim=whiteRim;}
        else{
            // The exact swatch is the base tone; highlights/shadows retain the
            // approved spherical shading without a blue tint over the palette.
            shade=new RadialGradient(-.35f,-.45f,1.8f,new int[]{mix(ballColor,Color.WHITE,.18f),ballColor,mix(ballColor,Color.BLACK,.22f),mix(ballColor,Color.BLACK,.48f)},new float[]{0,.35f,.72f,1},Shader.TileMode.CLAMP);
            rim=null;
        }
        describe();marks.prepare(botName,ballColor);
        invalidate();
    }
    private static int mix(int from,int to,float amount){return Color.rgb(Math.round(Color.red(from)*(1-amount)+Color.red(to)*amount),Math.round(Color.green(from)*(1-amount)+Color.green(to)*amount),Math.round(Color.blue(from)*(1-amount)+Color.blue(to)*amount));}
    public void setActive(boolean value) { active=value;last=0;if(value)invalidate(); }
    @Override public boolean performClick(){touch();notePoke();if(overview){setOverview(false);return true;}super.performClick();return true;}
    private void notePoke(){
        if(!pokes.poke(android.os.SystemClock.uptimeMillis()) || !ValueAnimator.areAnimatorsEnabled())return;
        if(angerAnimator!=null)angerAnimator.cancel();
        angerAnimator=ValueAnimator.ofFloat(0f,1f,1f,0f);angerAnimator.setDuration(1500);
        angerAnimator.addUpdateListener(animation->{anger=(float)animation.getAnimatedValue();invalidate();});
        angerAnimator.addListener(new android.animation.AnimatorListenerAdapter(){public void onAnimationEnd(android.animation.Animator animation){anger=0;invalidate();}});
        angerAnimator.start();
    }
    void play(BotTrick.Name trick){
        if(rendererOnly || !ValueAnimator.areAnimatorsEnabled())return;
        if(trickAnimator!=null)trickAnimator.cancel();
        trickAnimator=ValueAnimator.ofFloat(0f,1f);trickAnimator.setDuration(BotTrick.duration(trick));
        trickAnimator.addUpdateListener(animation->{pose=BotTrick.pose(trick,(float)animation.getAnimatedValue());invalidate();});
        trickAnimator.addListener(new android.animation.AnimatorListenerAdapter(){public void onAnimationEnd(android.animation.Animator animation){pose=null;invalidate();}});
        trickAnimator.start();
    }
    @Override protected void onDraw(Canvas canvas) {
        long now=System.nanoTime();double dt=active && last!=0?(now-last)/1e9:0;last=now;
        boolean portrait=getHeight()>getWidth();
        boolean held=docked();
        float singleR=held?drawnRadius(portrait):StagePlacement.baseRadius(getWidth(),getHeight(),chatLayout,portrait,primaryBot,stageSize);
        canvas.drawColor(Color.BLACK);
        long uptime=android.os.SystemClock.uptimeMillis();
        long wall=System.currentTimeMillis();
        assignSlots(zoom<.001f);
        if(zoom<.001f){
            float y=held?centerY():chatLayout?stageY():StagePlacement.placedCenter(getHeight(),stageY(),statusShift);
            float drawR=held||chatLayout?singleR:StagePlacement.placedRadius(singleR,statusShift,y,statusCeiling,getResources().getDisplayMetrics().density);
            if(active)step(dt);
            carouselSettling=positionAnimator!=null;glowDepth=0f;markPresence=1f;resolvePose(wall);marks.step(uptime);
            float bodyX=getWidth()/2f+(float)motion.x*drawR+shiftDraw*drawR,bodyY=y+(float)motion.y*drawR-hopDraw*drawR;
            drawSpotlight(canvas,getWidth()/2f,y,drawR);drawAttentionGlow(canvas,bodyX,bodyY,drawR,1f);
            drawAt(canvas,getWidth()/2f+(float)motion.x*drawR,y+(float)motion.y*drawR,drawR);
            if(visual.showChip)marks.drawChip(canvas,bodyX,bodyY,drawR,botName,ballColor,visual.chipScale,visual.chipAlpha*markPresence);
            else marks.chipDrawn=false;
            marks.drawConfetti(canvas,bodyX,bodyY,drawR,uptime);
        }
        else{
            float sizeMul=debugFocus?debugSizeScale:1f,gapMul=debugFocus?debugGapScale:1f;
            float smallR=Math.min(getWidth()*.083f,getHeight()*.21f)*stageSize*sizeMul,gap=getWidth()*1.1f*(1-zoom)+spacing()*gapMul*zoom;
            for(int i=0;i<debugMarkLeft.length;i++){debugMarkLeft[i]=Float.NaN;debugMarkRight[i]=Float.NaN;debugCenter[i]=Float.NaN;debugRadius[i]=Float.NaN;}
            int middle=Math.round(position);
            // Draw outer bots first so the center stays in front while the row moves.
            for(int distance=3;distance>=0;distance--)for(int side=-1;side<=1;side+=2){
                if(distance==0 && side==1)continue;int slot=middle+distance*side,index=wrap(slot);float offset=slot-position,depth=Math.abs(offset);
                if(depth>=2.8f)continue;
                BotView item=index==BotIdentity.index(botName)?this:lineup[index];if(active)item.step(dt);
                float scale=1-clamp(depth,0,2.5f)*.17f,r=(singleR*(1-zoom)+smallR*scale*zoom);
                float x=getWidth()/2f+offset*gap,y=stageY()+smallR*(1-scale)*zoom;
                float light=(float)Math.exp(-depth*.85f);float alpha=depth<.01f?1:light*(depth>.5f?zoom:1);
                float edge=depth>2.2f?clamp((2.8f-depth)/.6f,0,1):1;alpha*=edge;
                boolean offCenter=depth>=.5f;float fade=(offCenter?zoom:1f)*edge;
                float spot=CarouselShade.spotlightCover(depth<.01f?1f:light)*fade;
                item.carouselSettling=positionAnimator!=null;item.glowDepth=depth;item.markPresence=Math.max(.75f,alpha);
                item.resolvePose(wall);item.marks.step(uptime);
                float bodyX=x+item.shiftDraw*r,bodyY=y+(float)item.motion.y*r-item.hopDraw*r;
                DebugOverviewFocus.writeSpan(x,item.shiftDraw*r,r,item.visual.showRing,item.visual.showChip,item.visual.chipScale,StagePose.chipX(item.botName),getResources().getDisplayMetrics().density,debugMarkLeft,debugMarkRight,index);
                debugCenter[index]=x;debugRadius[index]=r;
                // Spotlight and the attention glow stay outside the outline dim.
                // Raise Your Hand sets the glow's own alpha. Body dim does not scale it.
                item.drawSpotlight(canvas,x,y,r,spot);
                item.drawAttentionGlow(canvas,bodyX,bodyY,r,Math.max(.55f,light)*fade);
                item.drawCarouselBot(canvas,x,y+(float)item.motion.y*r,r,CarouselShade.bodyDim(depth),fade);
                if(item.visual.showChip)item.marks.drawChip(canvas,bodyX,bodyY,r,item.botName,item.ballColor,item.visual.chipScale,item.visual.chipAlpha*item.markPresence);
                else item.marks.chipDrawn=false;
                item.marks.drawConfetti(canvas,bodyX,bodyY,r,uptime);
                captions.setTextAlign(Paint.Align.CENTER);captions.setTextSize(sp(offCenter?14:16));
                captions.setColor(offCenter?0xffc3c8cf:Color.WHITE);
                float textAlpha=offCenter?CarouselShade.labelAlpha(light,true)*fade:(depth<.01f?1:light)*zoom*edge;
                captions.setAlpha(Math.round(255*clamp(textAlpha,0,1)));
                float labelY=y+r*1.8f+dp(18);String name=BotIdentity.NAMES[index];
                canvas.drawText(name,x,labelY,captions);
                float half=captions.measureText(name)/2f;
                captions.getTextBounds("H",0,1,labelBounds);
                float dotY=labelY+labelBounds.exactCenterY();
                if(item.working){
                    notification.setStyle(Paint.Style.FILL);notification.setColor(0xff3ddc84);notification.setAlpha(Math.round(255*clamp(textAlpha,0,1)));
                    canvas.drawCircle(x-half-dp(10),dotY,dp(4),notification);
                }
                if(item.unread){
                    float indicatorAlpha=Math.max(.75f,alpha)*zoom;notification.setColor(0xffb9e5ff);
                    float dotX=x+half+dp(10);
                    notification.setAlpha((int)(65*indicatorAlpha));canvas.drawCircle(dotX,dotY,dp(7),notification);
                    notification.setAlpha((int)(255*indicatorAlpha));canvas.drawCircle(dotX,dotY,dp(3),notification);
                }
            }
        }
        if(active && (marks.confettiLive() || stageMoving() || animationIntensity>0 && ValueAnimator.areAnimatorsEnabled()))postInvalidateOnAnimation();
    }
    private boolean stageMoving(){
        if(stageFrame!=null && stageFrame.oneShot!=BotStageState.OneShot.NONE && !stageFrame.reduced && !stageFrame.quiet)return true;
        if(lineup==null)return false;
        for(BotView item:lineup)if(item.stageFrame!=null && item.stageFrame.oneShot!=BotStageState.OneShot.NONE && !item.stageFrame.reduced && !item.stageFrame.quiet)return true;
        for(BotView item:lineup)if(item.marks.confettiLive())return true;
        return false;
    }
    private void assignSlots(boolean single){
        if(lineup==null){slotted=true;return;}
        int count=BotIdentity.NAMES.length;
        if(slotPriority==null){slotPriority=new int[count];slotEligible=new boolean[count];slotGrant=new boolean[count];slotRanked=new boolean[count];}
        int selected=BotIdentity.index(botName);
        for(int i=0;i<count;i++){
            BotView item=i==selected?this:lineup[i];
            float depth=single?i==selected?0f:99f:ringDepth(i);
            slotEligible[i]=depth<=1f;
            slotPriority[i]=MotionSlots.priority(item.stageFrame,i==selected);
            slotRanked[i]=slotPriority[i]!=MotionSlots.WORKING;
        }
        for(int rank=0;rank<count;rank++){
            int best=-1;long newest=Long.MIN_VALUE;
            for(int i=0;i<count;i++){
                if(slotRanked[i] || slotPriority[i]!=MotionSlots.WORKING)continue;
                BotView item=i==selected?this:lineup[i];
                long since=item.stageFrame==null?0L:item.stageFrame.sinceMillis;
                if(best<0 || since>=newest){newest=since;best=i;}
            }
            if(best<0)break;
            slotPriority[best]=MotionSlots.WORKING+rank;
            slotRanked[best]=true;
        }
        MotionSlots.grant(count,slotPriority,slotEligible,slotGrant);
        for(int i=0;i<count;i++){BotView item=i==selected?this:lineup[i];item.slotted=slotGrant[i];}
    }
    private float ringDepth(int index){
        float raw=index-position;float half=BotIdentity.NAMES.length/2f;
        if(raw>half)raw-=BotIdentity.NAMES.length;if(raw<-half)raw+=BotIdentity.NAMES.length;
        return Math.abs(raw);
    }
    private void resolvePose(long now){
        hopDraw=0f;shiftDraw=0f;visual.clear();
        boolean user=pose!=null || speaking || anger>.04f || carouselSettling;
        if(pose!=null){motion.roll+=pose.roll;motion.pitch+=pose.pitch;motion.yaw+=pose.yaw;hopDraw=(float)pose.hop;shiftDraw=(float)pose.shift;}
        if(stageFrame==null)return;
        long origin=stageFrame.oneShot==BotStageState.OneShot.NONE?stageFrame.sinceMillis:stageFrame.oneShotStartMillis;
        long elapsed=origin<=0?0:Math.max(0L,now-origin);
        boolean slot=slotted && !carouselSettling;
        visual.fill(stageFrame.state,stageFrame.oneShot,elapsed,botName,animationIntensity,stageFrame.reduced,stageFrame.quiet,slot);
        if(user)return;
        if(stageFrame.state==BotStageState.State.WORKING)motion.yaw*=.5;
        if(primaryBot || BotIdentity.PRIMARY.equals(botName)){motion.roll+=visual.roll;motion.pitch+=visual.pitch;}
        motion.yaw+=visual.yaw;hopDraw=(float)visual.hop;shiftDraw=(float)visual.shift;
    }
    private String chipAt(float x,float y){
        if(marks.hit(x,y))return botName;
        if(!overview || lineup==null)return null;
        for(int i=0;i<lineup.length;i++)if(lineup[i].marks.hit(x,y))return BotIdentity.NAMES[i];
        return null;
    }
    private void step(double dt){motion.intensity=ValueAnimator.areAnimatorsEnabled()?animationIntensity:0;motion.step(dt);if(!botName.isEmpty() && !primaryBot){motion.roll=0;motion.pitch=0;}}
    private void drawSpotlight(Canvas canvas,float cx,float floorY,float r){drawSpotlight(canvas,cx,floorY,r,1f);}
    private void drawSpotlight(Canvas canvas,float cx,float floorY,float r,float cover){
        canvas.save();canvas.translate(cx,floorY);canvas.scale(r,r);
        boolean steady=stageFrame!=null && (stageFrame.state!=BotStageState.State.WORKING || stageFrame.quiet || stageFrame.reduced);
        float pulse=!steady && working && animationIntensity>0 && ValueAnimator.areAnimatorsEnabled()?.78f+.22f*(float)Math.sin(android.os.SystemClock.uptimeMillis()/650.0):1;
        float lift=1f-(float)Math.min(.4,hopDraw*.22f);
        float boost=visual.spotlightBoost<=0f?1f:visual.spotlightBoost;
        paint.setAlpha((int)(255*clamp(spotlightBrightness*.57f*pulse*cover*boost,0,1)));paint.setShader(shadow);canvas.save();canvas.scale(lift,.24f*lift,0,1.22f);canvas.drawCircle(0,1.22f,1.85f,paint);canvas.restore();paint.setAlpha(255);
        canvas.restore();
        if(visual.showRing)marks.drawRing(canvas,cx,floorY,r,botName,ballColor,visual.ringPhase);
    }
    /**
     * Raise Your Hand glow. Drawn outside the outline dim, with its own alpha.
     * presence is pinch and edge fade only. It is not the body dim.
     */
    private void drawAttentionGlow(Canvas canvas,float cx,float cy,float r,float presence){
        if(!visual.showGlow)return;
        float depth=glowDepth<=0f?1f:(float)Math.max(.55,Math.exp(-glowDepth*.85));
        marks.drawGlow(canvas,cx,cy,r,visual.glow*markPresence,depth*presence);
    }
    private void drawCarouselBot(Canvas canvas,float cx,float cy,float r,float bodyDim,float fade){
        canvas.save();canvas.translate(cx+shiftDraw*r,cy-hopDraw*r);canvas.scale(r,r);
        // Pinch and edge fade only. Settled carousel bots skip the offscreen layer.
        // Eyes are drawn after this restore, and the glow is drawn before this method.
        int layer=0;boolean fadeLayer=fade<0.999f;
        if(fadeLayer)layer=canvas.saveLayerAlpha(-1.95f,-1.8f,1.95f,2f,Math.round(255f*clamp(fade,0f,1f)));
        if(primaryBot){crown.prepare(motion);crown.setDim(bodyDim);crown.draw(canvas,false);}
        // At most 15% darker, by scaling RGB. No black mix and no alpha over the background.
        // SmoothAvatar receives the scaled color. Sphere gradients use the same keep as a multiply.
        float keep=1f-bodyDim;
        int bodyColor=avatar==null?ballColor:CarouselShade.scaleRgb(ballColor,keep);
        drawBody(canvas,r,bodyColor,avatar==null?bodyDim:0f);
        canvas.save();canvas.clipPath(sphere);
        if(rim!=null && avatar==null){paint.setShader(rim);canvas.drawRect(-1,-1,1,1,paint);}
        if(primaryBot)crown.drawContact(canvas);
        canvas.restore();
        if(primaryBot)crown.draw(canvas,true);
        if(fadeLayer)canvas.restoreToCount(layer);
        canvas.save();canvas.clipPath(sphere);drawEyes(canvas,fadeLayer?fade:1f);canvas.restore();
        canvas.restore();
    }
    private void drawAt(Canvas canvas,float cx,float cy,float r){
        canvas.save();canvas.translate(cx+shiftDraw*r,cy-hopDraw*r);canvas.scale(r,r);
        if(primaryBot){crown.prepare(motion);crown.setDim(0f);crown.draw(canvas,false);}
        drawBody(canvas,r,ballColor,0f);
        canvas.save();canvas.clipPath(sphere);
        if(rim!=null && avatar==null){paint.setShader(rim);canvas.drawRect(-1,-1,1,1,paint);}
        if(primaryBot)crown.drawContact(canvas);
        drawEyes(canvas,1f);
        canvas.restore();
        if(primaryBot)crown.draw(canvas,true);
        canvas.restore();
    }
    private void drawBody(Canvas canvas,float r,int color,float shadeDim){
        paint.setAlpha(255);paint.setColorFilter(null);
        if(avatar==null){
            paint.setShader(shade);
            if(shadeDim>0.004f){
                int level=Math.round((1f-Math.min(1f,shadeDim))*255f);
                if(level<0)level=0;else if(level>255)level=255;
                if(level<255)paint.setColorFilter(shadeFilters[level]);
            }
            if(shape.equals("sphere"))canvas.drawCircle(0,0,1,paint);else canvas.drawPath(bodyOutline,paint);
            paint.setColorFilter(null);
        }else avatar.draw(canvas,motion,color,r);
    }
    private void drawEyes(Canvas canvas,float eyeAlpha){
        paint.setShader(ballColor==BallColors.WHITE?eyes:null);paint.setColor(Color.WHITE);paint.setAlpha(Math.round(255f*clamp(eyeAlpha,0f,1f)));
        double blink=motion.blink();
        int eyeColor=ballColor==BallColors.WHITE?0xff12161a:Color.WHITE;
        for(double eye:EYES){
            if(avatar!=null){
                double nx=(avatar.surfaceZ(eye-.01,-.17)-avatar.surfaceZ(eye+.01,-.17))/.02;
                double ny=(avatar.surfaceZ(eye,-.18)-avatar.surfaceZ(eye,-.16))/.02;
                motion.rotate(nx,ny,1,point);if(point[2]<.015)continue;
            }
            eyePath.rewind();boolean started=false;
            double slant=(eye<0?0.62:-0.62)*anger,squint=1-0.58*anger;
            for(int i=0;i<=96;i++){
                double a=i/96.0*Math.PI*2,lx=.105*Math.cos(a),ly=(.16*Math.sin(a)+.075*Math.signum(Math.sin(a)))*blink*squint;
                double x=eye+lx*Math.cos(slant)-ly*Math.sin(slant),y=-.17+lx*Math.sin(slant)+ly*Math.cos(slant)+.025*anger;
                motion.rotate(x,y,avatar==null?Math.sqrt(Math.max(0,1-x*x-y*y)):avatar.surfaceZ(x,y),point);
                if(avatar==null && point[2]<.015)continue;
                if(!started){eyePath.moveTo((float)point[0],(float)point[1]);started=true;}else eyePath.lineTo((float)point[0],(float)point[1]);
            }
            eyePath.close();canvas.drawPath(eyePath,paint);
            if(anger>.04f)drawBrow(canvas,eye,eyeColor);
        }
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);
    }
    private void drawBrow(Canvas canvas,double eye,int color){
        double outerX=eye+(eye<0?-.16:.16),innerX=eye+(eye<0?.12:-.12),outerY=-.40,innerY=-.40+.12*anger;
        eyePath.rewind();boolean started=false;
        for(int i=0;i<=8;i++){
            double u=i/8.0,x=outerX+(innerX-outerX)*u,y=outerY+(innerY-outerY)*u;
            motion.rotate(x,y,avatar==null?Math.sqrt(Math.max(0,1-x*x-y*y)):avatar.surfaceZ(x,y),point);
            if(avatar==null && point[2]<.015)continue;
            if(!started){eyePath.moveTo((float)point[0],(float)point[1]);started=true;}else eyePath.lineTo((float)point[0],(float)point[1]);
        }
        Shader shader=paint.getShader();paint.setShader(null);paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.05f);paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawPath(eyePath,paint);paint.setShader(shader);paint.setStyle(Paint.Style.FILL);
    }
    /** Virtual chip nodes. The host node stays the existing bot sentence. */
    private final class HandNodes extends AccessibilityNodeProvider {
        @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
            if (virtualViewId == HOST_VIEW_ID) {
                AccessibilityNodeInfo host = AccessibilityNodeInfo.obtain(BotView.this);
                onInitializeAccessibilityNodeInfo(host);
                if (lineup == null) {
                    if (marks.chipDrawn) host.addChild(BotView.this, 1);
                } else {
                    for (int i = 0; i < lineup.length; i++) if (chipItem(i).marks.chipDrawn) host.addChild(BotView.this, i + 1);
                }
                return host;
            }
            int index = virtualViewId - 1;
            String name = chipName(index);
            BotView item = chipItem(index);
            if (name == null || item == null || !item.marks.chipDrawn) return null;
            AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain(BotView.this, virtualViewId);
            node.setClassName("android.widget.Button");
            node.setPackageName(getContext().getPackageName());
            node.setSource(BotView.this, virtualViewId);
            node.setParent(BotView.this);
            String question = item.stageFrame == null ? "" : item.stageFrame.question;
            node.setContentDescription(BotTalkBack.chipDescription(name, question));
            node.setClickable(true);
            node.setFocusable(true);
            node.setEnabled(true);
            node.setVisibleToUser(true);
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
            Rect bounds = new Rect(Math.round(item.marks.hitLeft), Math.round(item.marks.hitTop), Math.round(item.marks.hitRight), Math.round(item.marks.hitBottom));
            node.setBoundsInParent(bounds);
            int[] origin = new int[2];
            getLocationOnScreen(origin);
            Rect screen = new Rect(bounds);
            screen.offset(origin[0], origin[1]);
            node.setBoundsInScreen(screen);
            return node;
        }
        @Override public boolean performAction(int virtualViewId, int action, Bundle arguments) {
            if (virtualViewId == HOST_VIEW_ID) return BotView.this.performAccessibilityAction(action, arguments);
            if (action != AccessibilityNodeInfo.ACTION_CLICK) return false;
            String name = chipName(virtualViewId - 1);
            if (name == null || handListener == null) return false;
            handListener.open(name);
            return true;
        }
        private String chipName(int index) {
            if (lineup == null) return index == 0 ? botName : null;
            if (index < 0 || index >= BotIdentity.NAMES.length) return null;
            return BotIdentity.NAMES[index];
        }
        private BotView chipItem(int index) {
            if (lineup == null) return index == 0 ? BotView.this : null;
            if (index < 0 || index >= lineup.length) return null;
            int selected = BotIdentity.index(botName);
            return index == selected ? BotView.this : lineup[index];
        }
    }
}

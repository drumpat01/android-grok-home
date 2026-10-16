package dev.grokhome;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.animation.ValueAnimator;
import android.os.Bundle;
import java.util.concurrent.*;

public final class BotView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Motion motion = new Motion();
    private final Path eyePath = new Path(), sphere = new Path();
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
    private boolean active, primaryBot,chatLayout,working,unread;
    private float stageSize=1,spotlightBrightness=1,animationIntensity=1;
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
    private ValueAnimator zoomAnimator,positionAnimator;
    private float zoom,position,touchX,touchY,dragStart,scaleTotal=1,scaleStart;
    private boolean overview,dragging,multiTouch,notifyingSelection;
    private final Paint captions=new Paint(Paint.ANTI_ALIAS_FLAG);
    public BotView(Context context){this(context,false);}
    private BotView(Context context,boolean rendererOnly){
        super(context);this.rendererOnly=rendererOnly;sphere.addCircle(0,0,1,Path.Direction.CW);
        if(!rendererOnly){
            lineup=new BotView[BotIdentity.NAMES.length];geometryLoader=Executors.newSingleThreadExecutor();
            android.content.SharedPreferences prefs=context.getSharedPreferences("home",0);
            for(int i=0;i<lineup.length;i++){
                BotView item=new BotView(context,true);item.botName=BotIdentity.NAMES[i];item.primaryBot=i==0;item.shape=BotIdentity.shape(item.botName);
                item.setBallColor(prefs.getInt("bot_color_"+item.botName,BotIdentity.color(item.botName)));lineup[i]=item;
            }
            scaleDetector=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScaleBegin(ScaleGestureDetector detector){
                    cancelPosition();if(zoomAnimator!=null)zoomAnimator.cancel();multiTouch=true;dragging=false;scaleTotal=1;scaleStart=zoom;return true;
                }
                @Override public boolean onScale(ScaleGestureDetector detector){
                    scaleTotal*=detector.getScaleFactor();zoom=clamp(scaleStart-(float)Math.log(scaleTotal)*1.6f,0,1);invalidate();return true;
                }
                @Override public void onScaleEnd(ScaleGestureDetector detector){setOverview(scaleTotal<.90f || scaleTotal<=1.10f && zoom>.5f);}
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
    void setActivity(String name,boolean working,boolean unread){
        int index=BotIdentity.index(name);if(index<0 || lineup==null)return;
        lineup[index].working=working;lineup[index].unread=unread;
        if(botName.equals(name)){this.working=working;this.unread=unread;}invalidate();
    }
    private static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
    private static int wrap(int index){return Math.floorMod(index,BotIdentity.NAMES.length);}
    public void setBotName(String name){
        botName=name;primaryBot="Max".equals(name);int index=BotIdentity.index(name);
        if(!rendererOnly && index>=0){
            BotView item=lineup[index];item.ensureAvatar();shape=item.shape;avatar=item.avatar;motion=item.motion;working=item.working;unread=item.unread;ballColor=item.ballColor;shade=item.shade;rim=item.rim;
            if(!notifyingSelection){cancelPosition();position=index;}
        }else{String next=BotIdentity.shape(name);if(!next.equals(shape)){shape=next;avatar=null;ensureAvatar();}}
        describe();invalidate();
    }
    void setOverview(boolean value){
        if(rendererOnly)return;
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
    void nextBot(int direction){if(overview)settle(Math.round(position)+direction);else if(navigation!=null)navigation.select(BotIdentity.NAMES[wrap(BotIdentity.index(botName)+direction)],direction);}
    private void settle(int target){
        cancelPosition();int direction=target>=position?1:-1;
        if(!ValueAnimator.areAnimatorsEnabled()){selectPosition(target,direction);return;}
        positionAnimator=ValueAnimator.ofFloat(position,target);positionAnimator.setDuration(260);positionAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        positionAnimator.addUpdateListener(a->{position=(float)a.getAnimatedValue();invalidate();});
        positionAnimator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator animation){positionAnimator=null;selectPosition(target,direction);}});positionAnimator.start();
    }
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
    private float spacing(){return getWidth()*.185f;}
    private float stageY(){return getHeight()*(chatLayout?.46f:.40f);}
    @Override protected void onDetachedFromWindow(){
        disposed=true;if(geometryLoader!=null)geometryLoader.shutdownNow();if(zoomAnimator!=null)zoomAnimator.cancel();cancelPosition();super.onDetachedFromWindow();
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);if(rendererOnly)return;
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,overview?"Zoom in to "+botName:"Interrupt speech"));
    }
    @Override public boolean performAccessibilityAction(int action,Bundle arguments){
        if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){
            int direction=action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1;if(overview)settle(Math.round(position)+direction);else if(navigation!=null)navigation.select(BotIdentity.NAMES[wrap(BotIdentity.index(botName)+direction)],direction);return true;
        }
        return super.performAccessibilityAction(action,arguments);
    }
    private void describe(){
        setContentDescription((overview?"Bot carousel. "+botName+" centered. Swipe for more bots; tap a side bot to center it; tap center or pinch out to zoom in. ":"")+(primaryBot?"Max, primary bot with a small gold crown. ":botName.isEmpty()?"":botName+". ")+BallColors.name(ballColor)+" "+shape+" assistant with "+(ballColor==BallColors.WHITE?"black":"white")+" eyes. Tap to interrupt speech");
    }
    public void setChatLayout(boolean value){chatLayout=value;invalidate();}
    public void setSpeaking(boolean value) { motion.setSpeaking(value); }
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
        describe();
        invalidate();
    }
    private static int mix(int from,int to,float amount){return Color.rgb(Math.round(Color.red(from)*(1-amount)+Color.red(to)*amount),Math.round(Color.green(from)*(1-amount)+Color.green(to)*amount),Math.round(Color.blue(from)*(1-amount)+Color.blue(to)*amount));}
    public void setActive(boolean value) { active=value;last=0;if(value)invalidate(); }
    @Override public boolean performClick(){if(overview){setOverview(false);return true;}super.performClick();return true;}
    @Override protected void onDraw(Canvas canvas) {
        long now=System.nanoTime();double dt=active && last!=0?(now-last)/1e9:0;last=now;
        boolean portrait=getHeight()>getWidth();
        float singleR=Math.min(getWidth()*(chatLayout?.27f:portrait?.31f:.157f),getHeight()*(chatLayout?(primaryBot?.24f:.28f):.31f))*stageSize;
        canvas.drawColor(Color.BLACK);
        if(zoom<.001f){if(active)step(dt);drawSpotlight(canvas,getWidth()/2f,stageY(),singleR);drawAt(canvas,getWidth()/2f+(float)motion.x*singleR,stageY()+(float)motion.y*singleR,singleR);}
        else{
            float smallR=Math.min(getWidth()*.083f,getHeight()*.21f)*stageSize,gap=getWidth()*1.1f*(1-zoom)+spacing()*zoom;
            int middle=Math.round(position);
            // Draw outer bots first so the center stays in front while the row moves.
            for(int distance=3;distance>=0;distance--)for(int side=-1;side<=1;side+=2){
                if(distance==0 && side==1)continue;int slot=middle+distance*side,index=wrap(slot);float offset=slot-position,depth=Math.abs(offset);
                if(depth>=2.8f)continue;
                BotView item=index==BotIdentity.index(botName)?this:lineup[index];if(active)item.step(dt);
                float scale=1-clamp(depth,0,2.5f)*.17f,r=(singleR*(1-zoom)+smallR*scale*zoom);
                float x=getWidth()/2f+offset*gap,y=stageY()+smallR*(1-scale)*zoom;
                float light=(float)Math.exp(-depth*.85f);float alpha=depth<.01f?1:light*(depth>.5f?zoom:1);
                if(depth>2.2f)alpha*=clamp((2.8f-depth)/.6f,0,1);
                int layer=canvas.saveLayerAlpha(x-r*1.95f,y-r*1.8f,x+r*1.95f,y+r*2,(int)(255*alpha));
                item.drawSpotlight(canvas,x,y,r);item.drawAt(canvas,x,y+(float)item.motion.y*r,r);canvas.restoreToCount(layer);
                captions.setColor(Color.WHITE);captions.setTextAlign(Paint.Align.CENTER);captions.setTextSize(dp(depth<.5f?16:13));captions.setAlpha((int)(255*alpha*zoom));
                float labelY=y+r*1.8f+dp(18);canvas.drawText(BotIdentity.NAMES[index],x,labelY,captions);
                if(item.unread){
                    float indicatorAlpha=Math.max(.75f,alpha)*zoom;notification.setColor(0xffb9e5ff);notification.setAlpha((int)(255*indicatorAlpha));
                    float dotX=x+captions.measureText(BotIdentity.NAMES[index])/2+dp(10),dotY=labelY-dp(4);
                    notification.setAlpha((int)(65*indicatorAlpha));canvas.drawCircle(dotX,dotY,dp(7),notification);
                    notification.setAlpha((int)(255*indicatorAlpha));canvas.drawCircle(dotX,dotY,dp(3),notification);
                }
            }
        }
        if(active && animationIntensity>0 && ValueAnimator.areAnimatorsEnabled())postInvalidateOnAnimation();
    }
    private void step(double dt){motion.intensity=ValueAnimator.areAnimatorsEnabled()?animationIntensity:0;motion.step(dt);if(!botName.isEmpty() && !primaryBot){motion.roll=0;motion.pitch=0;}}
    private void drawSpotlight(Canvas canvas,float cx,float floorY,float r){
        canvas.save();canvas.translate(cx,floorY);canvas.scale(r,r);
        float pulse=working && animationIntensity>0 && ValueAnimator.areAnimatorsEnabled()?.78f+.22f*(float)Math.sin(android.os.SystemClock.uptimeMillis()/650.0):1;
        paint.setAlpha((int)(255*clamp(spotlightBrightness*.57f*pulse,0,1)));paint.setShader(shadow);canvas.save();canvas.scale(1,.24f,0,1.22f);canvas.drawCircle(0,1.22f,1.85f,paint);canvas.restore();paint.setAlpha(255);
        canvas.restore();
    }
    private void drawAt(Canvas canvas,float cx,float cy,float r){
        canvas.save();canvas.translate(cx,cy);canvas.scale(r,r);
        if(primaryBot){crown.prepare(motion);crown.draw(canvas,false);}
        if(avatar==null){paint.setShader(shade);if(shape.equals("sphere"))canvas.drawCircle(0,0,1,paint);else canvas.drawPath(SolidAvatar.outline(shape),paint);}else avatar.draw(canvas,motion,ballColor,r);
        canvas.save();canvas.clipPath(sphere);
        if(rim!=null && avatar==null){paint.setShader(rim);canvas.drawRect(-1,-1,1,1,paint);}
        if(primaryBot)crown.drawContact(canvas);
        paint.setShader(ballColor==BallColors.WHITE?eyes:null);paint.setColor(Color.WHITE);
        double blink=motion.blink();
        for(double eye:EYES){
            if(avatar!=null){
                double nx=(avatar.surfaceZ(eye-.01,-.17)-avatar.surfaceZ(eye+.01,-.17))/.02;
                double ny=(avatar.surfaceZ(eye,-.18)-avatar.surfaceZ(eye,-.16))/.02;
                motion.rotate(nx,ny,1,point);if(point[2]<.015)continue;
            }
            eyePath.rewind();boolean started=false;
            for(int i=0;i<=96;i++){
                double a=i/96.0*Math.PI*2,x=eye+.105*Math.cos(a),y=-.17+(.16*Math.sin(a)+.075*Math.signum(Math.sin(a)))*blink;
                motion.rotate(x,y,avatar==null?Math.sqrt(Math.max(0,1-x*x-y*y)):avatar.surfaceZ(x,y),point);
                if(avatar==null && point[2]<.015)continue;
                if(!started){eyePath.moveTo((float)point[0],(float)point[1]);started=true;}else eyePath.lineTo((float)point[0],(float)point[1]);
            }
            eyePath.close();canvas.drawPath(eyePath,paint);
        }
        paint.setShader(null);canvas.restore();
        if(primaryBot)crown.draw(canvas,true);
        canvas.restore();
    }
}

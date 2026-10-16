package dev.grokhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Checkable;
import android.widget.RadioButton;

/** A 48 dp keyboard-accessible radio target around a 28 dp palette circle. */
final class ColorSwatch extends View implements Checkable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int color;
    private final float density;
    private boolean checked;
    ColorSwatch(Context context,int index){
        super(context);color=BallColors.VALUES[index];density=getResources().getDisplayMetrics().density;
        setContentDescription(BallColors.NAMES[index]);setClickable(true);setFocusable(true);
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),null,new ColorDrawable(Color.WHITE)));
    }
    @Override protected void onDraw(Canvas canvas){
        float cx=getWidth()/2f,cy=getHeight()/2f;
        if(checked){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f*density);paint.setColor(0xff92969c);canvas.drawCircle(cx,cy,18*density,paint);}
        paint.setStyle(Paint.Style.FILL);paint.setColor(color);canvas.drawCircle(cx,cy,14*density,paint);
    }
    @Override public boolean isChecked(){return checked;}
    @Override public void setChecked(boolean value){checked=value;setSelected(value);invalidate();}
    @Override public void toggle(){setChecked(!checked);}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);info.setClassName(RadioButton.class.getName());info.setCheckable(true);info.setChecked(checked);
    }
}

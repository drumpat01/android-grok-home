package dev.grokhome;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.nio.*;
import java.util.*;

/** GPU ray-cast rounded extrusion: continuous surface normals, no visible triangles. */
@TargetApi(33)
final class SmoothAvatar {
    private static final int SIZE=768;
    private static final float SPAN=2.6f, UNIT=SPAN/SIZE;
    private static final Map<String,DistanceMap> CACHE=new HashMap<>();
    private static final class DistanceMap {
        final Bitmap bitmap;final float[] distance;
        DistanceMap(Bitmap bitmap,float[] distance){this.bitmap=bitmap;this.distance=distance;}
    }
    private final RuntimeShader shader;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DistanceMap map;
    private final double[] axis=new double[3];
    SmoothAvatar(Context context,String shape){
        try(InputStream stream=context.getResources().openRawResource(R.raw.rounded_avatar);
            ByteArrayOutputStream source=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int size;while((size=stream.read(buffer))!=-1)source.write(buffer,0,size);
            shader=new RuntimeShader(source.toString("UTF-8"));
        }catch(IOException error){throw new IllegalStateException("Avatar shader unavailable",error);}
        synchronized(CACHE){
            DistanceMap cached=CACHE.get(shape);
            if(cached==null){cached=createDistanceMap(shape);CACHE.put(shape,cached);}map=cached;
        }
        BitmapShader input=new BitmapShader(map.bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
        input.setFilterMode(BitmapShader.FILTER_MODE_NEAREST);
        shader.setInputBuffer("outlineDistance",input);paint.setShader(shader);
        android.util.Log.i("GrokHomeRender","GPU rounded surface ready: "+shape);
    }
    void draw(Canvas canvas,Motion motion,int color,float radius){
        motion.rotate(1,0,0,axis);setAxis("axisX");motion.rotate(0,1,0,axis);setAxis("axisY");motion.rotate(0,0,1,axis);setAxis("axisZ");
        shader.setFloatUniform("pixelSize",1f/radius);shader.setColorUniform("baseColor",color);
        canvas.drawRect(-1.5f,-1.5f,1.5f,1.5f,paint);
    }
    private void setAxis(String name){shader.setFloatUniform(name,(float)axis[0],(float)axis[1],(float)axis[2]);}
    double surfaceZ(double x,double y){
        double d=distance(x,y),q=Math.max(0,d+.62);
        return Math.sqrt(Math.max(0,.62*.62-q*q));
    }
    private float distance(double x,double y){
        double tx=(x+1.3)/UNIT-.5,ty=(y+1.3)/UNIT-.5;
        int ix=Math.max(0,Math.min(SIZE-2,(int)Math.floor(tx))),iy=Math.max(0,Math.min(SIZE-2,(int)Math.floor(ty)));
        float fx=(float)Math.max(0,Math.min(1,tx-ix)),fy=(float)Math.max(0,Math.min(1,ty-iy));int i=iy*SIZE+ix;
        return (map.distance[i]*(1-fx)+map.distance[i+1]*fx)*(1-fy)+(map.distance[i+SIZE]*(1-fx)+map.distance[i+SIZE+1]*fx)*fy;
    }
    // Intentional raw half-float bit storage in the texture byte buffer.
    @android.annotation.SuppressLint("HalfFloat")
    private static DistanceMap createDistanceMap(String shape){
        Bitmap mask=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(mask);
        canvas.scale(1/UNIT,1/UNIT);canvas.translate(1.3f,1.3f);Paint fill=new Paint();fill.setColor(Color.WHITE);canvas.drawPath(SolidAvatar.outline(shape),fill);
        int[] pixels=new int[SIZE*SIZE];mask.getPixels(pixels,0,SIZE,0,0,SIZE,SIZE);mask.recycle();
        boolean[] inside=new boolean[pixels.length];for(int i=0;i<pixels.length;i++)inside[i]=(pixels[i]>>>24)>127;
        float[] toInside=transform(inside,true),toOutside=transform(inside,false);
        for(int i=0;i<pixels.length;i++){
            float distance=(float)(Math.sqrt(inside[i]?toOutside[i]:toInside[i])-.5)*UNIT*(inside[i]?-1:1);
            toInside[i]=distance;
        }
        // Smooth subpixel raster stair steps before computing continuous normals.
        float[] temporary=new float[toInside.length];
        for(int pass=0;pass<2;pass++){
            for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
                float sum=0;for(int k=-2;k<=2;k++)sum+=toInside[y*SIZE+Math.max(0,Math.min(SIZE-1,x+k))];
                temporary[y*SIZE+x]=sum/5;
            }
            for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
                float sum=0;for(int k=-2;k<=2;k++)sum+=temporary[Math.max(0,Math.min(SIZE-1,y+k))*SIZE+x];
                toInside[y*SIZE+x]=sum/5;
            }
        }
        // Store signed distance directly in a floating-point channel. Packing it
        // into 8-bit colors introduces visible bands when differentiating normals.
        ByteBuffer data=ByteBuffer.allocateDirect(SIZE*SIZE*8).order(ByteOrder.nativeOrder());
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
            int left=Math.max(0,x-16),right=Math.min(SIZE-1,x+16),top=Math.max(0,y-16),bottom=Math.min(SIZE-1,y+16);
            float nx=(toInside[y*SIZE+right]-toInside[y*SIZE+left])/((right-left)*UNIT);
            float ny=(toInside[bottom*SIZE+x]-toInside[top*SIZE+x])/((bottom-top)*UNIT);
            data.putShort(android.util.Half.toHalf(toInside[y*SIZE+x]));
            data.putShort(android.util.Half.toHalf(nx));data.putShort(android.util.Half.toHalf(ny));data.putShort(android.util.Half.toHalf(1f));
        }
        data.rewind();Bitmap texture=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.RGBA_F16);
        texture.copyPixelsFromBuffer(data);
        return new DistanceMap(texture,toInside);
    }
    // Exact squared Euclidean distance transform, linear in the number of pixels.
    private static float[] transform(boolean[] inside,boolean target){
        float[] result=new float[inside.length];float[] input=new float[SIZE],output=new float[SIZE];
        int[] sites=new int[SIZE];double[] boundaries=new double[SIZE+1];
        for(int y=0;y<SIZE;y++){
            for(int x=0;x<SIZE;x++)input[x]=inside[y*SIZE+x]==target?0:1e10f;
            transformLine(input,output,sites,boundaries);System.arraycopy(output,0,result,y*SIZE,SIZE);
        }
        for(int x=0;x<SIZE;x++){
            for(int y=0;y<SIZE;y++)input[y]=result[y*SIZE+x];transformLine(input,output,sites,boundaries);
            for(int y=0;y<SIZE;y++)result[y*SIZE+x]=output[y];
        }
        return result;
    }
    private static void transformLine(float[] f,float[] d,int[] v,double[] z){
        int k=0;v[0]=0;z[0]=Double.NEGATIVE_INFINITY;z[1]=Double.POSITIVE_INFINITY;
        for(int q=1;q<SIZE;q++){
            double s;
            while(true){int p=v[k];s=((double)f[q]+q*q-f[p]-p*p)/(2*(q-p));if(s>z[k] || k==0)break;k--;}
            k++;v[k]=q;z[k]=s;z[k+1]=Double.POSITIVE_INFINITY;
        }
        k=0;for(int q=0;q<SIZE;q++){while(z[k+1]<q)k++;double delta=q-v[k];d[q]=(float)(delta*delta+f[v[k]]);}
    }
}

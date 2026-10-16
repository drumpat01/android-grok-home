package dev.grokhome;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.*;

/** Rounded, volumetric forms with a shaded front/back and eyes placed on the curved surface. */
final class SolidAvatar {
    private static final int AROUND=96,LAYERS=20;
    private static final double DEPTH=.62;
    private SmoothAvatar smooth;
    private final Vertex[][] mesh=new Vertex[LAYERS+1][AROUND];
    private final Face[] faces=new Face[LAYERS*AROUND];
    private final float[] triangles=new float[faces.length*12];
    private final int[] colors=new int[faces.length*6];
    private final float[] outlineX=new float[AROUND],outlineY=new float[AROUND];
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final double[] axisX=new double[3],axisY=new double[3],axisZ=new double[3];
    private static final Comparator<Face> ORDER=(a,b)->Double.compare(a.depth,b.depth);
    private static final class Vertex {
        double x,y,z,nx,ny,nz,pz,nzView;float px,py;int color;
    }
    private static final class Face {
        final Vertex a,b,c,d;double depth;
        Face(Vertex a,Vertex b,Vertex c,Vertex d){this.a=a;this.b=b;this.c=c;this.d=d;}
    }
    SolidAvatar(android.content.Context context,String shape){
        if(android.os.Build.VERSION.SDK_INT>=33)try{smooth=new SmoothAvatar(context,shape);}catch(RuntimeException error){android.util.Log.e("GrokHomeRender","GPU unavailable; using mesh",error);}
        Path path=outline(shape);PathMeasure measure=new PathMeasure(path,true);float[] p=new float[2];
        for(int i=0;i<AROUND;i++){measure.getPosTan(measure.getLength()*i/AROUND,p,null);outlineX[i]=p[0];outlineY[i]=p[1];}
        for(int layer=0;layer<=LAYERS;layer++){
            double latitude=-Math.PI/2+Math.PI*layer/LAYERS,cos=Math.cos(latitude),sin=Math.sin(latitude);
            // A rounded extrusion: broad, softly domed faces and a rounded edge,
            // rather than pulling every lobe into a pinched center like a sphere.
            double scale=Math.sqrt(Math.max(0,cos)),height=Math.copySign(Math.sqrt(Math.abs(sin)),sin);
            for(int i=0;i<AROUND;i++){
                int before=(i+AROUND-1)%AROUND,after=(i+1)%AROUND;
                double dx=outlineX[after]-outlineX[before],dy=outlineY[after]-outlineY[before];
                Vertex v=new Vertex();v.x=outlineX[i]*scale;v.y=outlineY[i]*scale;v.z=DEPTH*height;
                double ds=Math.abs(cos)<1e-8?0:-.5*sin/Math.sqrt(cos);
                double dz=Math.abs(sin)<1e-8?0:DEPTH*.5*cos/Math.sqrt(Math.abs(sin));
                double nx=dy*dz,ny=-dx*dz,nz=-(outlineX[i]*dy-outlineY[i]*dx)*ds;
                if(layer==0 || layer==LAYERS){nx=0;ny=0;nz=layer==0?-1:1;}
                else if(layer==LAYERS/2){nx=dy;ny=-dx;nz=0;}
                double n=Math.sqrt(nx*nx+ny*ny+nz*nz);v.nx=nx/n;v.ny=ny/n;v.nz=nz/n;mesh[layer][i]=v;
            }
        }
        int f=0;for(int layer=0;layer<LAYERS;layer++)for(int i=0;i<AROUND;i++){int j=(i+1)%AROUND;faces[f++]=new Face(mesh[layer][i],mesh[layer][j],mesh[layer+1][j],mesh[layer+1][i]);}
    }
    static Path outline(String shape){
        Path p=new Path();
        if(shape.equals("square")){p.addRoundRect(-.91f,-.91f,.91f,.91f,.30f,.30f,Path.Direction.CW);return p;}
        if(shape.equals("capsule")){p.addRoundRect(-1.10f,-.70f,1.10f,.70f,.70f,.70f,Path.Direction.CW);return p;}
        if(shape.equals("heart")){
            p.moveTo(0,-.46f);p.cubicTo(.48f,-1.15f,1.10f,-.75f,.98f,-.17f);
            p.cubicTo(.91f,.33f,.38f,.78f,0,1.02f);p.cubicTo(-.38f,.78f,-.91f,.33f,-.98f,-.17f);
            p.cubicTo(-1.10f,-.75f,-.48f,-1.15f,0,-.46f);p.close();return p;
        }
        if(shape.equals("hexagon") || shape.equals("star")){
            int count=shape.equals("hexagon")?6:12;float[] x=new float[count],y=new float[count];
            for(int i=0;i<count;i++){double a=i*2*Math.PI/count-(shape.equals("star")?Math.PI/2:0),r=shape.equals("star") && i%2==1?.65:.99;x[i]=(float)(r*Math.cos(a));y[i]=(float)(r*Math.sin(a));}
            float rounding=shape.equals("star")?.26f:.13f;
            for(int i=0;i<count;i++){
                int before=(i+count-1)%count,after=(i+1)%count;
                float a=x[i]*(1-rounding)+x[before]*rounding,b=y[i]*(1-rounding)+y[before]*rounding;
                if(i==0)p.moveTo(a,b);else p.lineTo(a,b);
                p.quadTo(x[i],y[i],x[i]*(1-rounding)+x[after]*rounding,y[i]*(1-rounding)+y[after]*rounding);
            }
            p.close();return p;
        }
        for(int i=0;i<192;i++){
            double a=i*Math.PI*2/192,r=1;
            if(shape.equals("flower")){
                double phase=(a+Math.PI/2)%(Math.PI/3);if(phase<0)phase+=Math.PI/3;phase=Math.min(phase,Math.PI/3-phase);
                r=.48*Math.cos(phase)+Math.sqrt(.51*.51-.48*.48*Math.sin(phase)*Math.sin(phase));
            }else if(shape.equals("cloud")){
                double[][] circles={{-.48,.15,.42},{-.34,-.22,.48},{.16,-.38,.51},{.57,-.05,.38},{.33,.25,.42},{-.07,.25,.43}};
                r=0;for(double[] c:circles){double along=c[0]*Math.cos(a)+c[1]*Math.sin(a),disc=c[2]*c[2]-c[0]*c[0]-c[1]*c[1]+along*along;if(disc>=0)r=Math.max(r,along+Math.sqrt(disc));}
            }
            float x=(float)(r*Math.cos(a)),y=(float)(r*Math.sin(a)*(shape.equals("cloud")?.85:1));
            if(i==0)p.moveTo(x,y);else p.lineTo(x,y);
        }
        p.close();return p;
    }
    double surfaceZ(double x,double y){
        if(smooth!=null)return smooth.surfaceZ(x,y);
        // Find the intersection of a ray from the center with this upright outline.
        double radius=Math.hypot(x,y);if(radius<.00001)return DEPTH;
        double dx=x/radius,dy=y/radius,edge=1;
        for(int i=0;i<AROUND;i++){
            int j=(i+1)%AROUND;double sx=outlineX[j]-outlineX[i],sy=outlineY[j]-outlineY[i],den=dx*sy-dy*sx;
            if(Math.abs(den)<1e-9)continue;
            double t=(outlineX[i]*sy-outlineY[i]*sx)/den,u=(outlineX[i]*dy-outlineY[i]*dx)/den;
            if(t>0 && u>=0 && u<=1){edge=t;break;}
        }
        return DEPTH*Math.pow(Math.max(0,1-Math.pow(radius/edge,4)),.25);
    }
    void draw(Canvas canvas,Motion motion,int baseColor,float radius){
        if(smooth!=null && canvas.isHardwareAccelerated()){smooth.draw(canvas,motion,baseColor,radius);return;}
        motion.rotate(1,0,0,axisX);motion.rotate(0,1,0,axisY);motion.rotate(0,0,1,axisZ);
        for(Vertex[] ring:mesh)for(Vertex v:ring){
            v.px=(float)(v.x*axisX[0]+v.y*axisY[0]+v.z*axisZ[0]);v.py=(float)(v.x*axisX[1]+v.y*axisY[1]+v.z*axisZ[1]);v.pz=v.x*axisX[2]+v.y*axisY[2]+v.z*axisZ[2];
            double nx=v.nx*axisX[0]+v.ny*axisY[0]+v.nz*axisZ[0],ny=v.nx*axisX[1]+v.ny*axisY[1]+v.nz*axisZ[1];
            v.nzView=v.nx*axisX[2]+v.ny*axisY[2]+v.nz*axisZ[2];
            double light=.65+.45*Math.max(0,-.42*nx-.57*ny+.71*v.nzView);
            double highlight=.13*Math.pow(Math.max(0,-.23*nx-.31*ny+.922*v.nzView),20);
            v.color=Color.rgb(channel(Color.red(baseColor),light,highlight),channel(Color.green(baseColor),light,highlight),channel(Color.blue(baseColor),light,highlight));
        }
        for(Face face:faces)face.depth=(face.a.pz+face.b.pz+face.c.pz+face.d.pz)*.25;
        Arrays.sort(faces,ORDER);int count=0;
        for(Face face:faces){
            if(face.a.nzView+face.b.nzView+face.c.nzView+face.d.nzView<0)continue;
            count=put(count,face.a);count=put(count,face.b);count=put(count,face.c);
            count=put(count,face.a);count=put(count,face.c);count=put(count,face.d);
        }
        paint.setColor(Color.WHITE);canvas.drawVertices(Canvas.VertexMode.TRIANGLES,count*2,triangles,0,null,0,colors,0,null,0,0,paint);
    }
    private int put(int index,Vertex v){triangles[index*2]=v.px;triangles[index*2+1]=v.py;colors[index]=v.color;return index+1;}
    private static int channel(int base,double light,double highlight){return (int)Math.min(255,base*light+255*highlight);}
    static Drawable icon(String shape,int color){
        return new Drawable(){
            final Path outline=outline(shape);final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            public void draw(Canvas c){Rect b=getBounds();c.save();c.translate(b.exactCenterX(),b.exactCenterY());c.scale(b.width()*.43f,b.height()*.43f);paint.setColor(color);c.drawPath(outline,paint);if(color==BallColors.BLACK){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.07f);paint.setColor(0xff44464b);c.drawPath(outline,paint);paint.setStyle(Paint.Style.FILL);}c.restore();}
            public void setAlpha(int alpha){paint.setAlpha(alpha);}public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
            public int getIntrinsicWidth(){return 24;}public int getIntrinsicHeight(){return 24;}
        };
    }
}

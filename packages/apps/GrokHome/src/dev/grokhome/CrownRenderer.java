package dev.grokhome;

import android.graphics.*;
import java.util.*;

/** Small hollow gold crown, seated on the unit sphere and rotated with its head. */
final class CrownRenderer {
    private static final int SEGMENTS=96;
    private static final double RADIUS=.33, THICKNESS=.027;
    // The circular lower band contacts the sphere all the way around its cap.
    private static final double BASE_Y=-Math.sqrt(1-RADIUS*RADIUS);
    private final Vertex[][] rings=new Vertex[6][SEGMENTS];
    private final ArrayList<Face> faces=new ArrayList<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path(), contact=new Path();
    private final LightingColorFilter[] dimFilters=new LightingColorFilter[256];
    private int dimLevel=255;
    private final double[] axisX=new double[3],axisY=new double[3],axisZ=new double[3];
    private static final Comparator<Face> DEPTH=(a,b)->Double.compare(a.depth,b.depth);

    private static final class Vertex {
        final double x,y,z;float px,py;double depth;
        Vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
    }
    private static final class Face {
        final Vertex[] vertices;final double nx,ny,nz,finish;
        double depth;int color;
        Face(Vertex a,Vertex b,Vertex c,Vertex d,double finish){
            vertices=new Vertex[]{a,b,c,d};this.finish=finish;
            double ux=b.x-a.x,uy=b.y-a.y,uz=b.z-a.z;
            double vx=d.x-a.x,vy=d.y-a.y,vz=d.z-a.z;
            double x=uy*vz-uz*vy,y=uz*vx-ux*vz,z=ux*vy-uy*vx;
            double length=Math.sqrt(x*x+y*y+z*z);nx=x/length;ny=y/length;nz=z/length;
        }
    }
    CrownRenderer(){
        for(int i=0;i<SEGMENTS;i++){
            double angle=i*Math.PI*2/SEGMENTS;
            double phase=(i*6.0/SEGMENTS)%1;
            double peak=1-Math.abs(phase*2-1);
            double tipRadius=RADIUS+.035*peak,tipY=-1.055-.23*peak;
            double[] radii={RADIUS,RADIUS,tipRadius,RADIUS-THICKNESS,RADIUS-THICKNESS,tipRadius-THICKNESS};
            double[] heights={BASE_Y,-1.025,tipY,BASE_Y,-1.025,tipY};
            for(int ring=0;ring<6;ring++)rings[ring][i]=new Vertex(radii[ring]*Math.cos(angle),heights[ring],radii[ring]*Math.sin(angle));
        }
        for(int i=0;i<SEGMENTS;i++){
            int j=(i+1)%SEGMENTS;
            strip(0,1,i,j,1);strip(1,2,i,j,1);
            strip(4,3,i,j,.69);strip(5,4,i,j,.69);
            faces.add(new Face(rings[2][i],rings[2][j],rings[5][j],rings[5][i],1.12));
        }
        for(int level=0;level<dimFilters.length;level++)dimFilters[level]=new LightingColorFilter(Color.rgb(level,level,level),0);
    }
    /** Side Max darkens with the body. 0 leaves the gold alone. */
    void setDim(float bodyDim){
        float keep=1f-Math.max(0f,Math.min(1f,bodyDim));
        int level=Math.round(keep*255f);
        if(level<0)level=0;else if(level>255)level=255;
        if(level==dimLevel)return;
        dimLevel=level;
        paint.setColorFilter(level==255?null:dimFilters[level]);
    }
    private void strip(int lower,int upper,int i,int j,double finish){faces.add(new Face(rings[lower][i],rings[lower][j],rings[upper][j],rings[upper][i],finish));}
    void prepare(Motion motion){
        motion.rotate(1,0,0,axisX);motion.rotate(0,1,0,axisY);motion.rotate(0,0,1,axisZ);
        for(Vertex[] ring:rings)for(Vertex v:ring){
            v.px=(float)(v.x*axisX[0]+v.y*axisY[0]+v.z*axisZ[0]);
            v.py=(float)(v.x*axisX[1]+v.y*axisY[1]+v.z*axisZ[1]);
            v.depth=v.x*axisX[2]+v.y*axisY[2]+v.z*axisZ[2];
        }
        for(Face face:faces){
            face.depth=0;for(Vertex v:face.vertices)face.depth+=v.depth*.25;
            double nx=face.nx*axisX[0]+face.ny*axisY[0]+face.nz*axisZ[0];
            double ny=face.nx*axisX[1]+face.ny*axisY[1]+face.nz*axisZ[1];
            double nz=face.nx*axisX[2]+face.ny*axisY[2]+face.nz*axisZ[2];
            double diffuse=Math.max(0,-.413*nx-.596*ny+.688*nz);
            double shine=Math.pow(Math.max(0,-.224*nx-.324*ny+.919*nz),28)*.36;
            double light=(.52+.48*diffuse)*face.finish;
            face.color=Color.rgb(channel(225,light,shine),channel(172,light,shine),channel(53,light,shine));
        }
        faces.sort(DEPTH);
        // Soft-looking contact arc lies on the sphere, underneath the band.
        contact.rewind();boolean started=false;
        double radius=RADIUS+.008,y=-Math.sqrt(1-radius*radius);
        for(int i=0;i<=SEGMENTS;i++){
            double a=i*Math.PI*2/SEGMENTS,x=radius*Math.cos(a),z=radius*Math.sin(a);
            double depth=x*axisX[2]+y*axisY[2]+z*axisZ[2];
            float px=(float)(x*axisX[0]+y*axisY[0]+z*axisZ[0]),py=(float)(x*axisX[1]+y*axisY[1]+z*axisZ[1]);
            if(depth<0){started=false;continue;}
            if(!started){contact.moveTo(px,py);started=true;}else contact.lineTo(px,py);
        }
    }
    private static int channel(int base,double light,double shine){return (int)Math.min(255,Math.round(base*light+255*shine));}
    void drawContact(Canvas canvas){
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.026f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(0x55220d00);canvas.drawPath(contact,paint);paint.setStyle(Paint.Style.FILL);
    }
    void draw(Canvas canvas,boolean front){
        // Slightly overlap adjacent facets so antialiasing doesn't leave hairline seams.
        paint.setStyle(Paint.Style.FILL_AND_STROKE);paint.setStrokeWidth(.003f);paint.setStrokeJoin(Paint.Join.ROUND);
        for(Face face:faces){
            if((face.depth>=0)!=front)continue;
            path.rewind();path.moveTo(face.vertices[0].px,face.vertices[0].py);
            for(int i=1;i<4;i++)path.lineTo(face.vertices[i].px,face.vertices[i].py);
            path.close();paint.setColor(face.color);canvas.drawPath(path,paint);
        }
    }
}

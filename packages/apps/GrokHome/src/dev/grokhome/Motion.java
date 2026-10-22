package dev.grokhome;

/** Deterministic character motion; angles remain continuous across mode changes. */
public final class Motion {
    private double time, speechTime, blend, spin;
    private boolean speaking;
    public double intensity=1;
    public double x, y, yaw, pitch, roll;
    public void setSpeaking(boolean value) {
        speaking = value;
    }
    public void seed(double seconds){time=Math.max(0,seconds);}
    public void step(double dt) {
        if(intensity<=0){x=y=yaw=pitch=roll=0;return;}
        dt = Math.max(0, Math.min(dt, 0.05))*intensity; time += dt;
        blend += ((speaking ? 1 : 0) - blend) * (1 - Math.exp(-dt * 1.6));
        // Preserve phase through interruptions and finish each twirl smoothly.
        double phase = speechTime % 8;
        if (speaking || blend > .001 || (phase > 4.5 && phase < 6.9)) {
            speechTime += dt;
            double cycle = speechTime % 8;
            double u = Math.max(0, Math.min(1, (cycle - 4.5) / 2.4));
            spin = (Math.floor(speechTime / 8) + u*u*u*(10+u*(-15+6*u))) * Math.PI*2;
        }
        yaw = (Math.sin(time*.73)*.60+Math.sin(time*.31)*.2)*(1-blend)
            + Math.sin(speechTime*1.6)*.40*blend + spin;
        pitch = Math.sin(time*.9)*.10 + blend*Math.sin(speechTime*1.9)*.11;
        roll = Math.sin(time*.7)*.065 + blend*Math.sin(speechTime*1.7)*.26;
        x = blend*Math.sin(speechTime*1.5)*.115;
        y = Math.sin(time)*.035 + blend*Math.sin(speechTime*1.8)*.070;
        x*=intensity;y*=intensity;pitch*=intensity;roll*=intensity;
    }
    public double blink() { if(intensity<=0)return 1;return 1-.91*Math.pow(Math.max(0,Math.cos(time*1.13)),65); }
    public double[] rotate(double x,double y,double z) {
        double[] result = new double[3]; rotate(x,y,z,result); return result;
    }
    public void rotate(double x,double y,double z,double[] result) {
        double a=x*Math.cos(yaw)+z*Math.sin(yaw), c=-x*Math.sin(yaw)+z*Math.cos(yaw);
        double b=y*Math.cos(pitch)-c*Math.sin(pitch); c=y*Math.sin(pitch)+c*Math.cos(pitch);
        result[0]=a*Math.cos(roll)-b*Math.sin(roll); result[1]=a*Math.sin(roll)+b*Math.cos(roll); result[2]=c;
    }
}

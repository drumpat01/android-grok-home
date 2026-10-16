import dev.grokhome.Motion;
public final class MotionCheck {
    public static void main(String[] args) {
        Motion m = new Motion(); double lastYaw=0; boolean initialized=false;
        for(int i=0;i<36000;i++) {
            if(i%137==0)m.setSpeaking(true);
            if(i%211==0)m.setSpeaking(false);
            m.step(1.0/120);
            if(!Double.isFinite(m.yaw)||!Double.isFinite(m.roll)||!Double.isFinite(m.blink()))throw new AssertionError("Non-finite pose");
            if(initialized && Math.abs(m.yaw-lastYaw)>.08)throw new AssertionError("Abrupt yaw at frame "+i);
            lastYaw=m.yaw;initialized=true;
        }
        m.setSpeaking(false);
        for(int i=0;i<2400;i++)m.step(1.0/120);
        if(Math.abs(m.x)>.00001)throw new AssertionError("Speaking motion did not settle");
        System.out.println("PASS: 5 minutes of rapid speech interruptions, bounded angular steps, finite poses, idle settling.");
    }
}

package dev.grokhome;

/** Local blob tricks. Rotation is rigid, so the volume stays intact and lands upright. */
final class BotTrick {
    enum Name { KICKFLIP, BARRELROLL, BOUNCE, PLAYDEAD, ROLLOVER }
    final Name name;
    final double roll;
    final double pitch;
    final double yaw;
    final double hop;
    final double shift;

    private BotTrick(Name name, double roll, double pitch, double yaw, double hop, double shift) {
        this.name = name;
        this.roll = roll;
        this.pitch = pitch;
        this.yaw = yaw;
        this.hop = hop;
        this.shift = shift;
    }

    static Name command(String spoken) {
        if (spoken == null) return null;
        String text = spoken.trim().toLowerCase(java.util.Locale.US).replaceAll("\\s+", " ").replaceAll("[.!?]+$", "");
        switch (text) {
            case "/kickflip": return Name.KICKFLIP;
            case "/barrelroll": return Name.BARRELROLL;
            case "/bounce": return Name.BOUNCE;
            case "/playdead": return Name.PLAYDEAD;
            case "/rollover": return Name.ROLLOVER;
            default: return null;
        }
    }

    static String label(Name name) {
        switch (name) {
            case KICKFLIP: return "Kickflip";
            case BARRELROLL: return "Barrel roll";
            case BOUNCE: return "Bounce";
            case PLAYDEAD: return "Playing dead";
            case ROLLOVER: return "Rollover";
            default: return "";
        }
    }

    static long duration(Name name) {
        switch (name) {
            case KICKFLIP: return 1100;
            case BARRELROLL: return 1500;
            case BOUNCE: return 980;
            case PLAYDEAD: return 2400;
            case ROLLOVER: return 1400;
            default: return 1000;
        }
    }

    static BotTrick pose(Name name, float time) {
        double t = Math.max(0, Math.min(1, time));
        switch (name) {
            case KICKFLIP:
                return new BotTrick(name, 0, 0, smooth(t) * Math.PI * 2, Math.sin(Math.PI * t) * 1.15, 0);
            case BARRELROLL:
                return new BotTrick(name, smooth(t) * Math.PI * 4, 0, 0, Math.sin(Math.PI * t) * 0.35, 0);
            case BOUNCE: {
                double first = t < 0.58 ? Math.sin(Math.PI * t / 0.58) : 0;
                double second = t >= 0.58 ? Math.sin(Math.PI * (t - 0.58) / 0.42) : 0;
                return new BotTrick(name, 0, 0, 0, first * 0.95 + second * 0.42, 0);
            }
            case PLAYDEAD: {
                double tip = t < 0.22 ? smooth(t / 0.22) : t < 0.70 ? 1 : 1 - smooth((t - 0.70) / 0.30);
                return new BotTrick(name, 0, tip * Math.PI / 2, 0, 0, 0);
            }
            case ROLLOVER:
                return new BotTrick(name, 0, 0, smooth(t) * Math.PI * 2, Math.sin(Math.PI * t) * 0.22, Math.sin(Math.PI * t) * 0.85);
            default:
                return new BotTrick(name, 0, 0, 0, 0, 0);
        }
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}

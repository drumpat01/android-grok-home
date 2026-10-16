package dev.grokhome;

/** Upright identities from the example roster; animation supplies only natural motion. */
final class BotIdentity {
    static final String[] NAMES={"Max","Scout","Nova","Echo","Pixel","Sage","Clover","Orion","Luna"};
    static final String[] SHAPES={"sphere","star","sphere","heart","capsule","cloud","flower","hexagon","square"};
    static final int[] COLORS={BallColors.RED,0xfff9670e,BallColors.WHITE,0xfff69412,0xfffb3499,0xff13b8a3,0xff14c370,0xff1884fc,0xff905afb};
    static int index(String name){for(int i=0;i<NAMES.length;i++)if(NAMES[i].equals(name))return i;return -1;}
    static String shape(String name){int i=index(name);return i<0?"sphere":SHAPES[i];}
    static int color(String name){int i=index(name);return i<0?BallColors.WHITE:COLORS[i];}
    private BotIdentity(){}
}

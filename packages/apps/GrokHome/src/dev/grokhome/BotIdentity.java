package dev.grokhome;

/** Upright identities from the configured roster; animation supplies only natural motion. */
final class BotIdentity {
    static final String[] NAMES={"Max","Scout","Nova","Echo","Pixel","Sage","Orion","Luna","Clover","Willow","Ember","Atlas","Juniper","Harbor"};
    static final String[] SHAPES={"sphere","star","sphere","heart","capsule","square","triangle","cloud","hexagon","square","sphere","sphere","hexagon","capsule"};
    static final int[] COLORS={BallColors.WHITE,0xfff9670e,BallColors.RED,0xfffb3499,0xff777777,0xfffb3499,0xff777777,0xff905afb,0xff14c370,0xfffb3499,BallColors.RED,BallColors.RED,0xffa16b39,0xff777777};
    /** The coordinator: first in the roster. It gets the crown, the big wall tile and the voice shortcuts. */
    static final String PRIMARY=NAMES[0];
    static int index(String name){for(int i=0;i<NAMES.length;i++)if(NAMES[i].equals(name))return i;return -1;}
    static String shape(String name){int i=index(name);return i<0?"sphere":SHAPES[i];}
    static int color(String name){int i=index(name);return i<0?BallColors.WHITE:COLORS[i];}
    private BotIdentity(){}
}

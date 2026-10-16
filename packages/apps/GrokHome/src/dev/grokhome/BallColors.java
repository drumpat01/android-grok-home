package dev.grokhome;

/** Opaque center pixels sampled from the user's supplied palette, in row order. */
final class BallColors {
    static final int WHITE=0xffffffff;
    static final int RED=0xfffc2c3e;
    static final int BLACK=0xff111115;
    static final int[] VALUES={WHITE,0xffa16b39,RED,0xfff9670e,0xfff69412,0xff14c370,
        0xff13b8a3,0xff1884fc,0xff905afb,0xfffb3499,0xff777777};
    static final String[] NAMES={"White","Brown","Red","Orange","Amber","Green","Teal","Blue","Purple","Pink","Gray"};
    static int index(int color){for(int i=0;i<VALUES.length;i++)if(VALUES[i]==color)return i;return 0;}
    static int normalize(int color){return color==BLACK?BLACK:VALUES[index(color)];}
    static String name(int color){return color==BLACK?"Black":NAMES[index(color)];}
    private BallColors(){}
}

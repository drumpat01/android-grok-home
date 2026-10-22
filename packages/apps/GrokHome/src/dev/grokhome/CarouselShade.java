package dev.grokhome;

/** Depth dim and label alpha for the carousel. Pure math, no drawing. */
final class CarouselShade {
    private CarouselShade(){}

    /**
     * How far a side bot's brightness may fall. The curve is the old depth
     * shape, capped so a side bot loses at most 15% brightness.
     */
    static float bodyDim(float depth){
        if(depth<=0f)return 0f;
        float curve=(1f-(float)Math.exp(-0.85*depth))*0.62f;
        return Math.min(0.15f,curve);
    }

    /** Multiply RGB. Hue and saturation stay. This is not an alpha. */
    static int scaleRgb(int color,float keep){
        if(keep>=1f)return color;
        if(keep<=0f)return color&0xff000000;
        int red=Math.round(((color>>16)&0xff)*keep);
        int green=Math.round(((color>>8)&0xff)*keep);
        int blue=Math.round((color&0xff)*keep);
        if(red>255)red=255;
        if(green>255)green=255;
        if(blue>255)blue=255;
        return (color&0xff000000)|(red<<16)|(green<<8)|blue;
    }

    /** Spotlight multiplier. The floor stays at least 0.55, before pinch and edge fade. */
    static float spotlightCover(float depthAlpha){
        return Math.max(0.55f,depthAlpha);
    }

    /**
     * Label alpha. Side labels never sit below 0.85.
     * Center labels keep the depth alpha they were given.
     */
    static float labelAlpha(float depthAlpha,boolean side){
        float alpha=depthAlpha<=0f?0f:Math.min(1f,depthAlpha);
        return side?Math.max(0.85f,alpha):alpha;
    }
}

package dev.grokhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/** Preallocated hand chip, working ring, glow, and confetti. Nothing in the draw methods allocates. */
final class StageMarks {
    private static final int BURSTS = 2;
    private static final int PER_BURST = 80;
    private static final int SPAWN = 16;

    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crumbEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path hand = new Path();
    private final RectF oval = new RectF();
    private final RectF crumb = new RectF();
    private final Particle[] particles = new Particle[BURSTS * PER_BURST];
    private final int[] tints = new int[4];
    private int edgeTint;
    private RadialGradient glowShader;
    private int glowColor = 1;
    private int preparedColor = 1;
    private String preparedBot = "";
    private String armedToken = "";
    private int burstCursor;
    // Burst shape. The defaults are the carousel's small burst; bigBurst() is the wall's celebration.
    private int spawn = SPAWN;
    private float launchY = -0.15f;
    private float speedMin = 1.6f;
    private float speedSpan = 0.8f;
    private float spreadDegrees = 75f;
    private float originSpread = 0f;
    private float pieceScale = 1f;
    private long lastStep;
    private final float density;

    float hitLeft, hitTop, hitRight, hitBottom;
    boolean chipDrawn;

    StageMarks(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        disc.setStyle(Paint.Style.FILL);
        disc.setColor(0xFF191C20);
        chipRing.setStyle(Paint.Style.STROKE);
        glyph.setStyle(Paint.Style.FILL);
        glyph.setColor(Color.WHITE);
        track.setStyle(Paint.Style.STROKE);
        track.setColor(0x1AFFFFFF);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStyle(Paint.Style.FILL);
        bit.setStyle(Paint.Style.FILL);
        crumbEdge.setStyle(Paint.Style.STROKE);
        crumbEdge.setStrokeJoin(Paint.Join.ROUND);
        crumbEdge.setStrokeCap(Paint.Cap.ROUND);
        buildHand(hand);
        for (int i = 0; i < particles.length; i++) particles[i] = new Particle();
    }

    void prepare(String bot, int color) {
        if (bot == null) bot = "";
        if (bot.equals(preparedBot) && color == preparedColor && glowShader != null) return;
        preparedBot = bot;
        preparedColor = color;
        int[] next = ConfettiTints.of(bot, color);
        System.arraycopy(next, 0, tints, 0, 4);
        edgeTint = ConfettiTints.edge(bot);
        int tint = "Nova".equals(bot) ? 0xFFD1D7DD : color;
        if ("Orion".equals(bot)) tint = color;
        if (tint != glowColor || glowShader == null) {
            glowColor = tint;
            int mid = (tint & 0x00FFFFFF) | (Math.round(0.45f * 255f) << 24);
            int edge = tint & 0x00FFFFFF;
            glowShader = new RadialGradient(0f, 0f, 1.75f, new int[] {tint | 0xFF000000, mid, edge}, new float[] {0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    /** Many larger pieces, thrown up from above the top of the body, so a finished bot is easy to spot. */
    void bigBurst() {
        spawn = 80;
        launchY = -0.95f;
        speedMin = 3.4f;
        speedSpan = 2.4f;
        spreadDegrees = 55f;
        originSpread = 0.7f;
        pieceScale = 1.7f;
    }

    void arm(String token, long now) {
        if (token == null || token.isEmpty() || token.equals(armedToken)) return;
        armedToken = token;
        int start = burstCursor * PER_BURST;
        burstCursor = (burstCursor + 1) % BURSTS;
        int seed = token.hashCode();
        if (seed == 0) seed = 1;
        for (int i = 0; i < PER_BURST; i++) {
            Particle p = particles[start + i];
            p.alive = i < spawn;
            if (!p.alive) continue;
            seed = seed * 1103515245 + 12345;
            float u = ((seed >>> 8) & 0xffff) / 65535f;
            seed = seed * 1103515245 + 12345;
            float v = ((seed >>> 8) & 0xffff) / 65535f;
            double degrees = -90.0 + (u * 2.0 * spreadDegrees - spreadDegrees);
            double radians = Math.toRadians(degrees);
            double speed = speedMin + v * speedSpan;
            p.x = originSpread > 0f ? (u - 0.5f) * 2f * originSpread : 0f;
            p.y = launchY;
            p.vx = (float) (Math.cos(radians) * speed);
            p.vy = (float) (Math.sin(radians) * speed);
            p.spin = u * 360f;
            p.spinVel = (v * 2f - 1f) * 360f;
            p.born = now;
            p.shape = i % 3 == 2 ? 1 : 0;
            p.tint = i % 4;
        }
    }

    /** Advances by the real time since the last call, so a slower redraw rate does not slow the confetti. */
    void step(long now) {
        float dt = lastStep == 0L ? 0.016f : Math.min(0.05f, Math.max(0f, (now - lastStep) / 1000f));
        lastStep = now;
        for (Particle p : particles) {
            if (!p.alive) continue;
            float age = now - p.born;
            if (age >= 1700f) {
                p.alive = false;
                continue;
            }
            p.vy += 3.2f * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.spin += p.spinVel * dt;
        }
    }

    boolean confettiLive() {
        for (Particle p : particles) if (p.alive) return true;
        return false;
    }

    void drawGlow(Canvas canvas, float cx, float cy, float radius, float alpha, float depthFactor) {
        if (alpha <= 0.01f || glowShader == null || radius <= 1f) return;
        float factor = depthFactor < 0.55f ? 0.55f : depthFactor;
        int paintAlpha = Math.round(255f * clamp(alpha * factor));
        if (paintAlpha <= 0) return;
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(radius, radius);
        glowPaint.setShader(glowShader);
        glowPaint.setAlpha(paintAlpha);
        canvas.drawCircle(0f, 0f, 1.75f, glowPaint);
        glowPaint.setShader(null);
        canvas.restore();
    }

    void drawRing(Canvas canvas, float cx, float floorY, float radius, String bot, int color, float phase) {
        if (radius <= 1f) return;
        float rx = 1.18f * radius;
        float ry = 0.24f * rx;
        float cy = floorY + 1.22f * radius;
        oval.set(cx - rx, cy - ry, cx + rx, cy + ry);
        track.setStrokeWidth(Math.max(2f * density, 0.018f * radius));
        canvas.drawOval(oval, track);
        arc.setStrokeWidth(Math.max(3f * density, 0.03f * radius));
        arc.setColor(StagePose.accent(bot, ConfettiTints.mix(color, Color.WHITE, 0.35f)));
        arc.setAlpha(Math.round(0.92f * 255f));
        canvas.drawArc(oval, (float) Math.toDegrees(phase), 70f, false, arc);
    }

    void drawChip(Canvas canvas, float cx, float cy, float radius, String bot, int color, float scale, float alpha) {
        chipDrawn = false;
        if (alpha <= 0.02f || scale <= 0.02f || radius <= 1f) {
            hitLeft = hitTop = hitRight = hitBottom = 0f;
            return;
        }
        float visual = clamp(0.42f * radius, 40f * density, 56f * density) * clamp(scale, 0f, 1.5f);
        float x = cx + StagePose.chipX(bot) * radius;
        float y = cy + StagePose.chipY(bot) * radius;
        int paintAlpha = Math.round(255f * clamp(alpha));
        disc.setAlpha(paintAlpha);
        chipRing.setAlpha(paintAlpha);
        chipRing.setStrokeWidth(Math.max(2f * density, 1f));
        chipRing.setColor(StagePose.accent(bot, color));
        canvas.drawCircle(x, y, visual * 0.5f, disc);
        canvas.drawCircle(x, y, visual * 0.5f - density, chipRing);
        glyph.setAlpha(paintAlpha);
        float glyphSize = visual * 0.62f;
        canvas.save();
        canvas.translate(x - glyphSize * 0.5f, y - glyphSize * 0.5f);
        canvas.scale(glyphSize / 24f, glyphSize / 24f);
        canvas.drawPath(hand, glyph);
        canvas.restore();
        float hit = Math.max(visual, 48f * density);
        hitLeft = x - hit * 0.5f;
        hitTop = y - hit * 0.5f;
        hitRight = x + hit * 0.5f;
        hitBottom = y + hit * 0.5f;
        chipDrawn = true;
    }

    void drawConfetti(Canvas canvas, float cx, float cy, float radius, long now) {
        if (radius <= 1f) return;
        float stroke = Math.max(1.5f * density, 0.016f * radius);
        if (edgeTint != 0) {
            crumbEdge.setColor(edgeTint);
            crumbEdge.setStrokeWidth(stroke);
        }
        for (Particle p : particles) {
            if (!p.alive) continue;
            float age = now - p.born;
            float fade = 1f;
            if (age >= 900f) fade = 1f - (age - 900f) / 800f;
            if (fade <= 0f) continue;
            int alpha = Math.round(255f * clamp(fade));
            bit.setColor(tints[p.tint]);
            bit.setAlpha(alpha);
            if (edgeTint != 0) crumbEdge.setAlpha(alpha);
            float px = cx + p.x * radius;
            float py = cy + p.y * radius;
            canvas.save();
            canvas.translate(px, py);
            canvas.rotate(p.spin);
            if (p.shape == 1) {
                float r = 0.035f * radius * pieceScale;
                if (edgeTint != 0) canvas.drawCircle(0f, 0f, r, crumbEdge);
                canvas.drawCircle(0f, 0f, r, bit);
            } else {
                float w = clamp(0.056f * radius * pieceScale, 4f * density * pieceScale, 8f * density * pieceScale);
                float h = clamp(0.12f * radius * pieceScale, 8f * density * pieceScale, 14f * density * pieceScale);
                crumb.set(-w * 0.5f, -h * 0.5f, w * 0.5f, h * 0.5f);
                if (edgeTint != 0) canvas.drawRoundRect(crumb, w * 0.35f, w * 0.35f, crumbEdge);
                canvas.drawRoundRect(crumb, w * 0.35f, w * 0.35f, bit);
            }
            canvas.restore();
        }
    }

    boolean hit(float x, float y) {
        return chipDrawn && x >= hitLeft && x <= hitRight && y >= hitTop && y <= hitBottom;
    }

    static void writeHand(Path path) {
        path.reset();
        buildHand(path);
    }

    /** Four separate fingers, the palm, and the thumb. Same glyph the wall used before the stage chip. */
    private static void buildHand(Path path) {
        round(path, 7.1f, 11.0f, 17.4f, 21.6f, 2.8f);
        round(path, 7.6f, 3.4f, 10.2f, 13.0f, 1.2f);
        round(path, 10.6f, 2.0f, 13.2f, 12.8f, 1.2f);
        round(path, 13.6f, 3.2f, 16.2f, 13.0f, 1.2f);
        round(path, 16.4f, 5.4f, 18.8f, 13.2f, 1.1f);
        round(path, 3.2f, 12.0f, 8.4f, 16.4f, 1.7f);
    }

    private static void round(Path path, float left, float top, float right, float bottom, float radius) {
        path.addRoundRect(left, top, right, bottom, radius, radius, Path.Direction.CW);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value) {
        return clamp(value, 0f, 1f);
    }

    private static final class Particle {
        float x, y, vx, vy, spin, spinVel;
        long born;
        int shape, tint;
        boolean alive;
    }
}

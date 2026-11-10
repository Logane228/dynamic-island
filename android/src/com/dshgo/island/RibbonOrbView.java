package com.dshgo.island;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Second charging orb: dotted particle ribbons orbiting inside a glass sphere,
 * a Canvas nod to the "particle ribbon" preset of the Liquid Orb shader.
 */
public class RibbonOrbView extends View implements OrbAnim {

    private static final int[] RIBBON = {0xFF7BDFF2, 0xFFFF9FF3, 0xFFB388FF};
    private static final int DOTS = 78;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spec = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();

    private float phase = 0f;
    private boolean running = true;

    public RibbonOrbView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) / 2f - dp(2f);

        clip.reset();
        clip.addCircle(cx, cy, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);

        bg.setShader(new RadialGradient(cx, cy - r * 0.3f, r * 1.4f,
                new int[]{0xFF141A2E, 0xFF080A14, 0xFF03040A},
                new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, bg);

        for (int layer = 0; layer < 3; layer++) {
            float tilt = -0.85f + layer * 0.42f;
            float spin = phase * (0.5f + layer * 0.22f);
            float radius = r * (0.62f + layer * 0.1f);
            float wobble = 0.28f + layer * 0.06f;
            dot.setColor(RIBBON[layer]);

            for (int i = 0; i < DOTS; i++) {
                double theta = (i / (double) DOTS) * Math.PI * 2 + spin;
                float x = (float) Math.cos(theta);
                float z = (float) Math.sin(theta);
                float y = (float) (Math.sin(theta * 2 + phase * 1.4 + layer) * wobble);

                float y2 = y * (float) Math.cos(tilt) - z * (float) Math.sin(tilt);
                float z2 = y * (float) Math.sin(tilt) + z * (float) Math.cos(tilt);

                float depth = (z2 + 1f) / 2f;
                float px = cx + x * radius;
                float py = cy + y2 * radius;
                float size = dp(0.9f + depth * 2.1f);

                dot.setAlpha((int) (35 + depth * 205));
                canvas.drawCircle(px, py, size, dot);

                if (depth > 0.82f) {
                    dot.setAlpha(38);
                    canvas.drawCircle(px, py, size * 2.6f, dot);
                }
            }
        }

        canvas.restore();

        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(dp(1.2f));
        rim.setShader(new RadialGradient(cx, cy, r,
                new int[]{0x00FFFFFF, 0x33FFFFFF, 0x99FFFFFF},
                new float[]{0.55f, 0.9f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, rim);

        spec.setShader(new RadialGradient(cx - r * 0.34f, cy - r * 0.4f, r * 0.5f,
                new int[]{0x44FFFFFF, 0x00FFFFFF}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, spec);

        if (running) {
            phase += 0.028f;
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

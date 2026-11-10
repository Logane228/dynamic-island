package com.dshgo.island;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Canvas approximation of the Liquid Orb / Siri-Wave shader: a dark glass sphere
 * with three iridescent sine bands drifting through it, a rim light and a
 * specular highlight. Runs at 60 fps and costs one Path per band.
 */
public class LiquidOrbView extends View implements OrbAnim {

    private static final int[] BAND = {0xFF7BDFF2, 0xFFFF9FF3, 0xFFFFFFFF};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spec = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path clip = new Path();

    private float phase = 0f;
    private boolean running = true;

    public LiquidOrbView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
    }

    @Override public void stop() {
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

        bg.setShader(new RadialGradient(cx, cy - r * 0.25f, r * 1.35f,
                new int[]{0xFF20213A, 0xFF0B0C16, 0xFF05060A},
                new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, bg);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setShader(null);

        for (int band = 0; band < 3; band++) {
            float amp = r * 0.13f * (1f - band * 0.22f);
            float freq = 3.4f + band * 1.35f;
            float offset = (band - 1) * r * 0.17f;
            float speed = 1f + band * 0.4f;

            path.reset();
            int steps = 64;
            for (int i = 0; i <= steps; i++) {
                float x = -r + (2f * r) * i / steps;
                float envelope = (float) Math.pow(Math.cos(Math.PI * x / (2.15f * r)), 2);
                float y = offset + amp * envelope
                        * (float) Math.sin(freq * x / r + phase * speed + band * 1.7f);
                if (i == 0) path.moveTo(cx + x, cy + y);
                else path.lineTo(cx + x, cy + y);
            }

            paint.setColor(BAND[band]);
            paint.setAlpha(band == 2 ? 235 : 165);
            paint.setStrokeWidth(band == 2 ? dp(2.1f) : dp(5.2f));
            canvas.drawPath(path, paint);

            paint.setAlpha(band == 2 ? 55 : 40);
            paint.setStrokeWidth(dp(13f));
            canvas.drawPath(path, paint);
        }

        canvas.restore();

        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(dp(1.3f));
        rim.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r,
                new int[]{0x99FFFFFF, 0x11FFFFFF, 0x77FFFFFF},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, rim);

        spec.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.42f, r * 0.55f,
                new int[]{0x55FFFFFF, 0x00FFFFFF}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, spec);

        if (running) {
            phase += 0.055f;
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

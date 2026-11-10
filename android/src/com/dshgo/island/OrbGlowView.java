package com.dshgo.island;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.view.View;

/**
 * Iridescent "liquid glass" bloom on the capsule border, inspired by the
 * Siri-wave orb: three pastel sweep gradients rotating at different speeds
 * (pink → amber → cyan → violet) plus a soft inner glow, so the edge looks like
 * light refracting through glass rather than a hard ring.
 *
 * Non-interactive: never consumes touches.
 */
public class OrbGlowView extends View {

    private static final int[] GLASS = {
            0xFFFFB3E6, // pink
            0xFFFFE29A, // amber
            0xFFB8F3FF, // cyan
            0xFFD9C2FF, // violet
            0xFF9FFFE0, // mint
            0xFFFFB3E6
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();

    private float radius = 40f;
    private float phase = 0f;
    private boolean running = true;

    public OrbGlowView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setRadius(float radiusPx) {
        if (Math.abs(radius - radiusPx) < 0.5f) return;
        radius = radiusPx;
        invalidate();
    }

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

        // three passes: crisp iridescent edge, then two soft blooms inside it
        for (int i = 0; i < 3; i++) {
            float stroke = dp(i == 0 ? 2.2f : (i == 1 ? 5.5f : 9.5f));
            paint.setStrokeWidth(stroke);
            paint.setAlpha(i == 0 ? 255 : (i == 1 ? 70 : 34));

            SweepGradient gradient = new SweepGradient(cx, cy, GLASS, null);
            matrix.setRotate(phase * (i == 0 ? 1f : -0.65f) + i * 36f, cx, cy);
            gradient.setLocalMatrix(matrix);
            paint.setShader(gradient);

            float inset = stroke / 2f + dp(0.6f);
            rect.set(inset, inset, w - inset, h - inset);
            canvas.drawRoundRect(rect, Math.max(0, radius - inset), Math.max(0, radius - inset), paint);
        }

        paint.setAlpha(255);

        if (running) {
            phase = (phase + 0.55f) % 360f;
            postInvalidateDelayed(33);
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

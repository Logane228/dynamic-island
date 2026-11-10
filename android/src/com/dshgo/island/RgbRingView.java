package com.dshgo.island;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.view.View;

/**
 * Thin animated RGB ring drawn on the capsule border. Non-interactive: it never
 * consumes touches, so the pill's gesture handler keeps working.
 */
public class RgbRingView extends View {

    private static final int[] COLORS = {
            0xFFFF2D55, 0xFFFF9500, 0xFFFFD60A, 0xFF30D158,
            0xFF32D4E0, 0xFF5E5CE6, 0xFFBF5AF2, 0xFFFF2D55
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();

    private float radius = 40f;
    private float phase = 0f;
    private boolean running = true;

    public RgbRingView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.6f));
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
        if (getWidth() == 0 || getHeight() == 0) return;

        float inset = paint.getStrokeWidth() / 2f;
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        SweepGradient gradient = new SweepGradient(cx, cy, COLORS, null);
        matrix.setRotate(phase, cx, cy);
        gradient.setLocalMatrix(matrix);
        paint.setShader(gradient);

        canvas.drawRoundRect(rect, radius, radius, paint);

        if (running) {
            phase = (phase + 0.7f) % 360f;
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

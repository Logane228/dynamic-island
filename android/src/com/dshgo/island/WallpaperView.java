package com.dshgo.island;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Mesh-gradient wallpaper that drifts slower than the pages (parallax). */
public class WallpaperView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint base = new Paint();
    private float offset = 0f;
    private boolean tinted = false;

    public WallpaperView(Context context) {
        super(context);
        base.setShader(new LinearGradient(0, 0, 0, 2400,
                new int[]{0xFF12142A, 0xFF080A16, 0xFF04050A},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    public void setParallax(float offsetPx) {
        offset = offsetPx;
        invalidate();
    }

    public void setTinted(boolean tinted) {
        this.tinted = tinted;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.save();
        canvas.translate(offset * 0.06f, 0);
        canvas.scale(1.08f, 1.08f, w / 2f, h / 2f);

        canvas.drawRect(-w, -h, w * 2, h * 2, base);

        int[] centers = {
                (int) (w * 0.20f), (int) (h * 0.16f),
                (int) (w * 0.86f), (int) (h * 0.20f),
                (int) (w * 0.74f), (int) (h * 0.86f),
                (int) (w * 0.28f), (int) (h * 0.78f)
        };
        int[] colors = {0xAA7C4DFF, 0x99266EFF, 0x88FF5E78, 0x5500E0C6};
        float[] radii = {w * 0.75f, w * 0.65f, w * 0.8f, w * 0.6f};

        for (int i = 0; i < 4; i++) {
            int c = colors[i];
            int transparent = Color.argb(0, Color.red(c), Color.green(c), Color.blue(c));
            paint.setShader(new RadialGradient(centers[i * 2], centers[i * 2 + 1], radii[i],
                    adjusted(c), transparent, Shader.TileMode.CLAMP));
            canvas.drawCircle(centers[i * 2], centers[i * 2 + 1], radii[i], paint);
        }
        canvas.restore();
    }

    private int adjusted(int color) {
        if (!tinted) return color;
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        int grey = (int) (0.32f * r + 0.5f * g + 0.18f * b);
        return Color.argb(Color.alpha(color), grey / 2, grey / 2, grey / 2);
    }
}

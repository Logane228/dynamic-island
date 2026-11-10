package com.dshgo.island;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;

/**
 * Two-page horizontal pager with drag-follow and snap. Reports the scroll
 * offset so the wallpaper can move at a slower rate (parallax).
 */
public class GlassPager extends ViewGroup {

    public interface Listener {
        void onPageChanged(int page);
        void onScroll(float offsetPx, float fraction);
    }

    private final Scroller scroller;
    private final int slop;

    private float downX, downY, lastX;
    private boolean dragging, lockedHorizontal;
    private int page = 0;
    private Listener listener;

    public GlassPager(Context context) {
        super(context);
        scroller = new Scroller(context);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setWillNotDraw(false);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public int getPage() {
        return page;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).layout(i * width, 0, (i + 1) * width, b - t);
        }
        scrollTo(page * width, 0);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                lastX = downX;
                dragging = false;
                lockedHorizontal = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                float dx = Math.abs(ev.getX() - downX);
                float dy = Math.abs(ev.getY() - downY);
                if (!lockedHorizontal && (dx > slop || dy > slop)) {
                    lockedHorizontal = dx > dy;
                }
                if (lockedHorizontal && dx > slop) {
                    dragging = true;
                    lastX = ev.getX();
                    return true;
                }
                break;
            }
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (getChildCount() < 2) return false;
        float width = getWidth();

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                lastX = downX;
                scroller.abortAnimation();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = ev.getX() - lastX;
                lastX = ev.getX();
                float target = getScrollX() - dx;
                target = Math.max(0, Math.min(width * (getChildCount() - 1), target));
                scrollTo((int) target, 0);
                notifyScroll();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float pageWidth = width;
                float current = getScrollX();
                int targetPage = Math.round(current / pageWidth);
                int max = getChildCount() - 1;
                targetPage = Math.max(0, Math.min(max, targetPage));
                setPage(targetPage, true);
                return true;
            }
            default:
                return true;
        }
    }

    private void notifyScroll() {
        float width = Math.max(1, getWidth());
        float offset = -getScrollX();
        if (listener != null) listener.onScroll(offset, getScrollX() / width);
    }

    public void setPage(int target, boolean animate) {
        int max = Math.max(0, getChildCount() - 1);
        page = Math.max(0, Math.min(max, target));
        int targetX = page * getWidth();
        if (animate) {
            scroller.startScroll(getScrollX(), 0, targetX - getScrollX(), 0, 320);
            invalidate();
        } else {
            scrollTo(targetX, 0);
            notifyScroll();
        }
        if (listener != null) listener.onPageChanged(page);
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            notifyScroll();
            postInvalidateOnAnimation();
        }
    }
}

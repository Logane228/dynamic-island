package com.dshgo.island;

import android.accessibilityservice.AccessibilityService;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.content.SharedPreferences;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The floating pill: owns the overlay window, morphs between states and reacts to
 * media sessions, notifications, charging and its own timer.
 */
public class IslandController {

    private static final int NONE = 0, MEDIA = 1, NOTIF = 2, CHARGE = 3, TIMER = 4, DONE = 5;
    private static final int CHARGE_BIG = 6, QUICK = 7, DASH = 8, LIVE = 9, CALL = 10, CALL_ENDED = 11, CHARGE_BOLT = 12;
    private static final int POS_VERSION = 6;

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());

    private WindowManager wm;
    private Context winCtx;
    private int winType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    private FrameLayout root;
    private OrbGlowView rgbRing;
    private LinearLayout compact;
    private LinearLayout expanded;
    private GradientDrawable bg;
    private WindowManager.LayoutParams lp;
    private ValueAnimator sizeAnim;

    private int mode = NONE;
    private boolean open = false;

    // media
    private MediaSessionManager msm;
    private MediaController mc;
    private ProgressBar mediaBar;
    private TextView mediaElapsed;
    private ImageButton mediaPlay;
    private boolean mediaTicking = false;

    // notification
    private String notifTitle = "";
    private String notifText = "";
    private Drawable notifIcon;
    private PendingIntent notifIntent;

    // charging
    private boolean wasCharging = false;
    private int pct = 0;

    // timer
    private boolean timerOn = false, timerPaused = false;
    private long timerEndAt = 0, timerRemain = 0;
    private TextView timerSmall, timerBig, timerStatus;
    private final Runnable timerTick = new Runnable() {
        @Override public void run() { tickTimer(); }
    };

    // call live activity
    private boolean callActive = false, callMuted = false;
    private long callStart = 0;
    private int callRoute = 0;
    private static final String[] CALL_ROUTES = {"Phone", "Speaker", "Bluetooth"};
    private String callName = "Camille Laurent";
    private TextView callNameTv, callTimerTv, callSubTv;
    private View[] callBars;
    private float[] callLevels = {0.4f, 0.7f, 0.9f, 0.6f, 0.45f};
    private final Runnable callTick = new Runnable() {
        @Override public void run() {
            if (!callActive) return;
            String t = fmt(System.currentTimeMillis() - callStart);
            if (callTimerTv != null) callTimerTv.setText(t);
            if (callSubTv != null) callSubTv.setText(CALL_ROUTES[callRoute] + " · " + t);
            updateWave();
            h.postDelayed(this, 90);
        }
    };

    // live activities (ongoing / progress notifications from any app)
    private String liveKey;
    private String liveTitle = "", liveText = "";
    private int liveProgress = 0, liveMax = 0;
    private long liveWhen = 0;
    private boolean liveChrono = false, liveCountDown = false;
    private Drawable liveIcon;
    private PendingIntent liveIntent;
    private Notification.Action[] liveActions;
    private TextView livePctTv, liveTitleTv, liveTextTv, liveTimeTv;
    private ProgressBar liveBar;
    private ValueAnimator liveDemoAnim;
    private final Runnable liveTick = new Runnable() {
        @Override public void run() {
            if (mode != LIVE) return;
            String t = liveRightText();
            if (livePctTv != null) livePctTv.setText(t);
            if (liveTimeTv != null) liveTimeTv.setText(t);
            h.postDelayed(this, 1000);
        }
    };

    // idle widgets + dashboard
    private ImageView idleIcon;
    private TextView idleText;
    private int idleIdx = 0;
    private long lastRotate = 0;
    private TextView dashTime, dashDate, dashBat, dashAlarm, dashMedia, dashTimer;

    // secondary activity bubble
    private LinearLayout bubbleRoot;
    private TextView bubbleText;
    private WindowManager.LayoutParams bubbleLp;
    private boolean bubbleAttached = false;
    private int bubbleMode = -1;

    // transient auto-hide
    private Runnable endTransient;

    // charging animation
    private TextView chargePctTv;
    private ProgressBar chargeBar;
    private ValueAnimator chargeAnim;
    private ObjectAnimator boltPulse;
    private OrbAnim orbPulseView;
    private final Runnable chargeToCompact = new Runnable() {
        @Override public void run() {
            if (mode != CHARGE_BIG) return;
            applyMode(CHARGE, false);
            h.postDelayed(chargeHide, 2200);
        }
    };
    private final Runnable chargeHide = new Runnable() {
        @Override public void run() {
            if (mode == CHARGE) revert();
        }
    };

    IslandController(Context c) {
        ctx = c.getApplicationContext();
    }

    /* ------------------------------------------------------------------ setup */

    void attach() {
        AccessibilityService a11y = IslandA11yService.instance;
        if (a11y != null) {
            winCtx = a11y;
            winType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            winCtx = ctx;
            winType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        wm = (WindowManager) winCtx.getSystemService(Context.WINDOW_SERVICE);
        buildWindow();
        if (root == null) return;
        ctx.registerReceiver(batteryRx, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        setupMedia();
        applyMode(NONE, false);
    }

    boolean isAttached() {
        return root != null;
    }

    void destroy() {
        h.removeCallbacksAndMessages(null);
        cancelChargeAnims();
        if (liveDemoAnim != null) liveDemoAnim.cancel();
        mediaTicking = false;
        if (mc != null) {
            try { mc.unregisterCallback(mcCb); } catch (Exception ignored) {}
            mc = null;
        }
        try { ctx.unregisterReceiver(batteryRx); } catch (Exception ignored) {}
        hideBubble();
        if (rgbRing != null) rgbRing.stop();
        if (root != null) {
            try { wm.removeView(root); } catch (Exception ignored) {}
            root = null;
        }
    }

    private void buildWindow() {
        root = new FrameLayout(ctx);
        bg = new GradientDrawable();
        bg.setColor(0xFF000000);
        bg.setCornerRadius(dp(18));
        bg.setStroke(Math.max(1, dp(1) / 2), 0x26FFFFFF);
        root.setBackground(bg);
        root.setClipToOutline(true);
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) { return onRootTouch(e); }
        });

        compact = new LinearLayout(ctx);
        compact.setOrientation(LinearLayout.HORIZONTAL);
        compact.setGravity(Gravity.CENTER_VERTICAL);
        compact.setPadding(dp(12), 0, dp(12), 0);

        expanded = new LinearLayout(ctx);
        expanded.setOrientation(LinearLayout.VERTICAL);
        expanded.setPadding(dp(16), dp(14), dp(16), dp(14));
        expanded.setVisibility(View.GONE);

        root.addView(compact, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(expanded, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        rgbRing = new OrbGlowView(ctx);
        root.addView(rgbRing, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rgbRing.setRadius(dp(18));

        lp = new WindowManager.LayoutParams(dp(126), dp(36),
                winType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        // Without this the window is laid out inside the content area, i.e. its
        // y origin is the bottom of the status bar, so "6 dp" was really
        // 6 dp *below* the bar and the pill could never wrap the camera.
        if (Build.VERSION.SDK_INT >= 30) {
            lp.setFitInsetsTypes(0);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        applyEdge(prefs().getInt("posEdge", 0));
        lp.windowAnimations = 0;

        try {
            wm.addView(root, lp);
        } catch (Exception e) {
            root = null;
        }
    }

    /** Status bar + cutout inset in px; the resource alone is not reliable here. */
    private int topInset() {
        int measured = prefs().getInt("insetTop", -1);
        if (measured > 0) return measured;
        int inset = 0;
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                WindowMetrics metrics = wm.getCurrentWindowMetrics();
                WindowInsets wi = metrics.getWindowInsets();
                inset = Math.max(
                        wi.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top,
                        wi.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout()).top);
            }
        } catch (Exception ignored) { }
        if (inset <= 0) {
            int id = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
            inset = id > 0 ? ctx.getResources().getDimensionPixelSize(id) : 0;
        }
        return Math.max(inset, dp(28));
    }

    /* ------------------------------------------------------------- rendering */

    private void applyMode(int m, boolean wantOpen) {
        if (root == null) return;
        mode = m;
        open = wantOpen && (m == MEDIA || m == TIMER);

        cancelChargeAnims();
        compact.removeAllViews();
        expanded.removeAllViews();
        mediaBar = null;
        mediaElapsed = null;
        mediaPlay = null;
        timerSmall = null;
        timerBig = null;
        timerStatus = null;
        chargeBar = null;
        chargePctTv = null;
        if (orbPulseView != null) { orbPulseView.stop(); orbPulseView = null; }
        idleIcon = null;
        idleText = null;
        dashTime = null;
        dashDate = null;
        dashBat = null;
        dashAlarm = null;
        dashMedia = null;
        dashTimer = null;
        livePctTv = null;
        liveTitleTv = null;
        liveTextTv = null;
        liveTimeTv = null;
        liveBar = null;
        callNameTv = null;
        callTimerTv = null;
        callSubTv = null;
        callBars = null;

        boolean big = m == CHARGE_BIG || m == QUICK || m == DASH;

        int w = dp(126), hgt = dp(36), radius = dp(18);
        switch (m) {
            case NONE:
                // idle capsule stays empty: no automatic clock/widget rotation
                w = dp(126);
                break;
            case DASH:
                w = Math.min(ctx.getResources().getDisplayMetrics().widthPixels - dp(24), dp(340));
                hgt = dp(250);
                radius = dp(34);
                buildDashboard();
                break;
            case LIVE:
                if (open) { w = expandedWidth(); hgt = dp(172); radius = dp(30); buildLiveExpanded(); }
                else { w = dp(300); hgt = dp(40); buildLiveCompact(); }
                break;
            case CALL:
                if (open) { w = expandedWidth(); hgt = dp(196); radius = dp(32); buildCallExpanded(); }
                else { w = dp(252); hgt = dp(40); buildCallCompact(); }
                break;
            case CALL_ENDED:
                w = dp(230); hgt = dp(38);
                buildCallEnded();
                break;
            case MEDIA:
                if (open) { w = expandedWidth(); hgt = dp(182); radius = dp(30); buildMediaExpanded(); }
                else { w = dp(238); hgt = dp(36); buildMediaCompact(); }
                break;
            case NOTIF:
                w = notifWidth(); hgt = dp(50);
                buildNotifCompact();
                break;
            case CHARGE:
                w = dp(184); hgt = dp(36);
                buildChargeCompact();
                break;
            case CHARGE_BIG:
                w = dp(300); hgt = dp(200); radius = dp(40);
                buildChargeBig();
                break;
            case CHARGE_BOLT:
                w = dp(286); hgt = dp(140); radius = dp(36);
                buildChargeBolt();
                break;
            case QUICK:
                w = dp(300); hgt = dp(126); radius = dp(34);
                buildQuick();
                break;
            case TIMER:
                if (open) { w = dp(316); hgt = dp(100); radius = dp(26); buildTimerExpanded(); }
                else { w = dp(186); hgt = dp(36); buildTimerCompact(); }
                break;
            case DONE:
                w = dp(212); hgt = dp(36);
                buildDoneCompact();
                break;
            default:
                break;
        }

        compact.setVisibility((open || big) ? View.GONE : View.VISIBLE);
        expanded.setVisibility((open || big) ? View.VISIBLE : View.GONE);
        expanded.setAlpha(1f);
        if (open) {
            expanded.setAlpha(0f);
            expanded.animate().alpha(1f).setDuration(180).start();
        }

        animateSize(w, hgt, radius, big);
        syncMediaTicker();

        h.removeCallbacks(bubbleSync);
        h.postDelayed(bubbleSync, 300);

        h.removeCallbacks(dashTick);
        if (m == NONE || m == DASH) h.post(dashTick);
        h.removeCallbacks(liveTick);
        if (m == LIVE) h.post(liveTick);
        h.removeCallbacks(callTick);
        if (m == CALL && callActive) h.post(callTick);
    }

    private void animateSize(final int targetW, final int targetH, final int radius) {
        animateSize(targetW, targetH, radius, false);
    }

    private void animateSize(final int targetW, final int targetH, final int radius, boolean big) {
        if (lp == null) return;
        if (sizeAnim != null) sizeAnim.cancel();
        final int fromW = lp.width, fromH = lp.height;
        final float fromR = bg.getCornerRadius();
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(big ? 380 : 240);
        a.setInterpolator(big ? new OvershootInterpolator(1.15f) : new DecelerateInterpolator(1.8f));
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator an) {
                float t = (Float) an.getAnimatedValue();
                lp.width = Math.max(1, (int) (fromW + (targetW - fromW) * t));
                lp.height = Math.max(1, (int) (fromH + (targetH - fromH) * t));
                bg.setCornerRadius(fromR + (radius - fromR) * t);
                if (rgbRing != null) rgbRing.setRadius(fromR + (radius - fromR) * t);
                try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
                root.invalidate();
            }
        });
        sizeAnim = a;
        a.start();
    }

    /* --------------------------------------------------------------- compact */

    private void buildMediaCompact() {
        MediaMetadata md = mc != null ? mc.getMetadata() : null;
        String title = md != null ? str(md.getText(MediaMetadata.METADATA_KEY_TITLE)) : null;
        if (title == null) title = "Now playing";

        ImageView art = new ImageView(ctx);
        int s = dp(24);
        art.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        Bitmap bm = artwork(md);
        if (bm != null) art.setImageBitmap(round(bm, s, dp(7)));
        else art.setBackground(gradient(dp(7)));
        compact.addView(art);

        TextView t = tv(title, 13f, 0xFFFFFFFF, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(10);
        t.setLayoutParams(p);
        compact.addView(t);
    }

    private void buildChargeBig() {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout orbWrap = new FrameLayout(ctx);
        OrbAnim orb = chargeStyle() == 4 ? new RibbonOrbView(ctx) : new LiquidOrbView(ctx);
        orbWrap.addView((View) orb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        chargePctTv = tv("0%", 22f, 0xFFFFFFFF, true);
        chargePctTv.setFontFeatureSettings("tnum");
        chargePctTv.setShadowLayer(dp(6), 0, 0, 0xAA000000);
        FrameLayout.LayoutParams pctLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pctLp.gravity = Gravity.CENTER;
        orbWrap.addView(chargePctTv, pctLp);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(dp(116), dp(116));
        col.addView(orbWrap, olp);
        orbPulseView = orb;

        TextView label = tv("Charging", 11f, 0x99FFFFFF, false);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(6);
        col.addView(label, llp);

        chargeBar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        chargeBar.setMax(100);
        chargeBar.setProgressTintList(ColorStateList.valueOf(accent()));
        chargeBar.setProgressBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(150), dp(4));
        bp.topMargin = dp(8);
        chargeBar.setLayoutParams(bp);
        col.addView(chargeBar);

        expanded.addView(col, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final int target = Math.max(1, Math.min(100, pct));
        chargeAnim = ValueAnimator.ofInt(0, target);
        chargeAnim.setDuration(1100);
        chargeAnim.setInterpolator(new DecelerateInterpolator());
        chargeAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                int v = (Integer) a.getAnimatedValue();
                if (chargePctTv != null) chargePctTv.setText(v + "%");
                if (chargeBar != null) chargeBar.setProgress(v);
            }
        });
        chargeAnim.start();
    }

    private void buildChargeBolt() {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView bolt = new ImageView(ctx);
        bolt.setImageResource(R.drawable.ic_bolt);
        bolt.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(30)));
        col.addView(bolt);

        TextView label = tv("Charging", 11f, 0x99FFFFFF, false);
        label.setPadding(0, dp(3), 0, 0);
        col.addView(label);

        chargePctTv = tv("0%", 26f, 0xFFFFFFFF, true);
        chargePctTv.setFontFeatureSettings("tnum");
        col.addView(chargePctTv);

        chargeBar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        chargeBar.setMax(100);
        chargeBar.setProgressTintList(ColorStateList.valueOf(accent()));
        chargeBar.setProgressBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(150), dp(4));
        bp.topMargin = dp(5);
        chargeBar.setLayoutParams(bp);
        col.addView(chargeBar);

        expanded.addView(col, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        boltPulse = ObjectAnimator.ofFloat(bolt, View.ALPHA, 1f, 0.42f);
        boltPulse.setDuration(820);
        boltPulse.setRepeatCount(ValueAnimator.INFINITE);
        boltPulse.setRepeatMode(ValueAnimator.REVERSE);
        boltPulse.start();

        final int target = Math.max(1, Math.min(100, pct));
        chargeAnim = ValueAnimator.ofInt(0, target);
        chargeAnim.setDuration(1100);
        chargeAnim.setInterpolator(new DecelerateInterpolator());
        chargeAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                int v = (Integer) a.getAnimatedValue();
                if (chargePctTv != null) chargePctTv.setText(v + "%");
                if (chargeBar != null) chargeBar.setProgress(v);
            }
        });
        chargeAnim.start();
    }

    private void buildQuick() {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addQuickBtn(row, R.drawable.ic_stat, "Open", new View.OnClickListener() {
            @Override public void onClick(View v) {
                applyMode(NONE, false);
                openApp();
            }
        });
        addQuickBtn(row, R.drawable.ic_timer, "Timer", new View.OnClickListener() {
            @Override public void onClick(View v) { startTimer(300); }
        });
        addQuickBtn(row, R.drawable.ic_bolt, "Charge", new View.OnClickListener() {
            @Override public void onClick(View v) { pct = 68; showCharging(); }
        });
        expanded.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void addQuickBtn(LinearLayout parent, int iconRes, String label, View.OnClickListener l) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setClickable(true);
        GradientDrawable bgq = new GradientDrawable();
        bgq.setColor(0x14FFFFFF);
        bgq.setCornerRadius(dp(18));
        col.setBackground(bgq);
        col.setPadding(0, dp(12), 0, dp(10));
        col.setOnClickListener(l);

        ImageView iv = new ImageView(ctx);
        iv.setImageResource(iconRes);
        iv.setColorFilter(Color.WHITE);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
        col.addView(iv);

        TextView t = tv(label, 11f, 0xCCFFFFFF, false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(6);
        col.addView(t, tp);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(92), 1f);
        p.leftMargin = dp(5);
        p.rightMargin = dp(5);
        parent.addView(col, p);
    }

    private void buildNotifCompact() {
        ImageView ic = new ImageView(ctx);
        int s = dp(26);
        ic.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        if (notifIcon != null) ic.setImageDrawable(notifIcon);
        else ic.setBackground(gradient(dp(8)));
        compact.addView(ic);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = dp(10);
        col.setLayoutParams(cp);

        TextView t1 = tv(notifTitle, 13f, 0xFFFFFFFF, true);
        t1.setSingleLine(true);
        t1.setEllipsize(TextUtils.TruncateAt.END);
        TextView t2 = tv(notifText, 11f, 0x99FFFFFF, false);
        t2.setSingleLine(true);
        t2.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(t1);
        col.addView(t2);
        compact.addView(col);
    }

    private void buildChargeCompact() {
        ImageView ic = new ImageView(ctx);
        ic.setImageResource(R.drawable.ic_bolt);
        ic.setLayoutParams(new LinearLayout.LayoutParams(dp(16), dp(16)));
        compact.addView(ic);

        TextView t = tv("Charging  " + pct + "%", 13f, 0xFFFFFFFF, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = dp(8);
        compact.addView(t, p);
    }

    private void buildDoneCompact() {
        ImageView ic = new ImageView(ctx);
        ic.setImageResource(R.drawable.ic_timer);
        ic.setLayoutParams(new LinearLayout.LayoutParams(dp(16), dp(16)));
        compact.addView(ic);

        TextView t = tv("Timer finished", 13f, 0xFFFFFFFF, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = dp(8);
        compact.addView(t, p);
    }

    private void buildTimerCompact() {
        ImageView ic = new ImageView(ctx);
        ic.setImageResource(R.drawable.ic_timer);
        ic.setLayoutParams(new LinearLayout.LayoutParams(dp(16), dp(16)));
        compact.addView(ic);

        timerSmall = tv(fmt(timerRemain), 13f, 0xFFFFFFFF, true);
        timerSmall.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = dp(8);
        compact.addView(timerSmall, p);

        TextView lbl = tv("Focus", 11f, 0x99FFFFFF, false);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.leftMargin = dp(7);
        compact.addView(lbl, lp2);
    }

    /* -------------------------------------------------------------- expanded */

    private void buildMediaExpanded() {
        MediaMetadata md = mc != null ? mc.getMetadata() : null;
        String title = md != null ? str(md.getText(MediaMetadata.METADATA_KEY_TITLE)) : null;
        String artist = md != null ? str(md.getText(MediaMetadata.METADATA_KEY_ARTIST)) : null;
        if (title == null) title = "Now playing";
        if (artist == null) artist = "";

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        ImageView art = new ImageView(ctx);
        int s = dp(60);
        art.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        Bitmap bm = artwork(md);
        if (bm != null) art.setImageBitmap(round(bm, s, dp(14)));
        else art.setBackground(gradient(dp(14)));
        top.addView(art);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = dp(12);
        col.setLayoutParams(cp);

        TextView t1 = tv(title, 15f, 0xFFFFFFFF, true);
        t1.setSingleLine(true);
        t1.setEllipsize(TextUtils.TruncateAt.END);
        TextView t2 = tv(artist, 12f, 0x99FFFFFF, false);
        t2.setSingleLine(true);
        t2.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(t1);
        col.addView(t2);
        top.addView(col);
        expanded.addView(top);

        ProgressBar pb = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(1000);
        pb.setProgressTintList(ColorStateList.valueOf(0xFFB06BFF));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        plp.topMargin = dp(10);
        pb.setLayoutParams(plp);
        expanded.addView(pb);
        mediaBar = pb;

        LinearLayout times = new LinearLayout(ctx);
        times.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(4);
        times.setLayoutParams(tlp);
        mediaElapsed = tv("0:00", 10f, 0x88FFFFFF, false);
        times.addView(mediaElapsed);
        times.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));
        long dur = md != null ? md.getLong(MediaMetadata.METADATA_KEY_DURATION) : 0;
        times.addView(tv(fmt(dur), 10f, 0x88FFFFFF, false));
        expanded.addView(times);

        LinearLayout ctr = new LinearLayout(ctx);
        ctr.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(4);
        ctr.setLayoutParams(clp);

        ctr.addView(iconBtn(R.drawable.ic_prev, dp(40), false, new View.OnClickListener() {
            @Override public void onClick(View v) { skip(-1); }
        }));
        mediaPlay = iconBtn(R.drawable.ic_pause, dp(44), true, new View.OnClickListener() {
            @Override public void onClick(View v) { togglePlay(); }
        });
        ctr.addView(mediaPlay);
        ctr.addView(iconBtn(R.drawable.ic_next, dp(40), false, new View.OnClickListener() {
            @Override public void onClick(View v) { skip(1); }
        }));
        expanded.addView(ctr);

        refreshMediaLabels();
    }

    private void buildTimerExpanded() {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        timerBig = tv(fmt(timerRemain), 30f, 0xFFFFFFFF, true);
        timerBig.setFontFeatureSettings("tnum");
        row.addView(timerBig);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = dp(14);
        col.setLayoutParams(cp);
        timerStatus = tv(timerPaused ? "Paused" : "Focus · running", 11f, 0x99FFFFFF, false);
        col.addView(timerStatus);
        row.addView(col);

        row.addView(iconBtn(timerPaused ? R.drawable.ic_play : R.drawable.ic_pause, dp(40), true,
                new View.OnClickListener() {
                    @Override public void onClick(View v) { toggleTimerPause(); }
                }));
        row.addView(iconBtn(R.drawable.ic_close, dp(40), false, new View.OnClickListener() {
            @Override public void onClick(View v) { closeTimer(); }
        }));
        expanded.addView(row);
    }

    /* ----------------------------------------------------------- media glue */

    private void setupMedia() {
        msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (msm == null) return;
        try {
            msm.addOnActiveSessionsChangedListener(
                    new MediaSessionManager.OnActiveSessionsChangedListener() {
                        @Override public void onActiveSessionsChanged(List<MediaController> controllers) {
                            onMediaChanged();
                        }
                    }, new ComponentName(ctx, NotifListener.class), h);
        } catch (Exception ignored) { }
        onMediaChanged();
    }

    private final MediaController.Callback mcCb = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { onMediaChanged(); }

        @Override public void onMetadataChanged(MediaMetadata metadata) {
            if (mode == MEDIA) applyMode(MEDIA, open);
        }
    };

    private void bindMedia(MediaController p) {
        if (p == mc) return;
        if (mc != null) {
            try { mc.unregisterCallback(mcCb); } catch (Exception ignored) {}
        }
        mc = p;
        if (mc != null) {
            try { mc.registerCallback(mcCb, h); } catch (Exception ignored) {}
        }
    }

    private MediaController pickPlaying() {
        if (msm == null) return null;
        try {
            List<MediaController> list = msm.getActiveSessions(new ComponentName(ctx, NotifListener.class));
            for (MediaController c : list) {
                PlaybackState ps = c.getPlaybackState();
                if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) return c;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void onMediaChanged() {
        MediaController p = pickPlaying();
        if (p == null) {
            if (mode == MEDIA) {
                bindMedia(null);
                revert();
            }
            return;
        }
        bindMedia(p);
        if (mode == NONE || mode == MEDIA) {
            if (mode == MEDIA && open) refreshMediaLabels();
            else applyMode(MEDIA, false);
        }
    }

    private void togglePlay() {
        if (mc == null || mc.getTransportControls() == null) return;
        PlaybackState ps = mc.getPlaybackState();
        boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
        try {
            if (playing) mc.getTransportControls().pause();
            else mc.getTransportControls().play();
        } catch (Exception ignored) {}
    }

    private void skip(int dir) {
        if (mc == null || mc.getTransportControls() == null) return;
        try {
            if (dir < 0) mc.getTransportControls().skipToPrevious();
            else mc.getTransportControls().skipToNext();
        } catch (Exception ignored) {}
    }

    private void refreshMediaLabels() {
        if (mc == null) return;
        PlaybackState ps = mc.getPlaybackState();
        MediaMetadata md = mc.getMetadata();
        long pos = ps != null ? ps.getPosition() : 0;
        long dur = md != null ? md.getLong(MediaMetadata.METADATA_KEY_DURATION) : 0;
        if (mediaBar != null && dur > 0) {
            mediaBar.setProgress((int) Math.max(0, Math.min(1000, pos * 1000 / dur)));
        }
        if (mediaElapsed != null) mediaElapsed.setText(fmt(pos));
        if (mediaPlay != null && ps != null) {
            mediaPlay.setImageResource(ps.getState() == PlaybackState.STATE_PLAYING
                    ? R.drawable.ic_pause : R.drawable.ic_play);
        }
    }

    private void syncMediaTicker() {
        boolean want = mode == MEDIA && open;
        if (want && !mediaTicking) {
            mediaTicking = true;
            h.post(mediaTick);
        } else if (!want) {
            mediaTicking = false;
        }
    }

    private final Runnable mediaTick = new Runnable() {
        @Override public void run() {
            if (!mediaTicking) return;
            refreshMediaLabels();
            h.postDelayed(this, 500);
        }
    };

    /* --------------------------------------------------------- notifications */

    public void onNotification(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            if (pkg == null || pkg.equals(ctx.getPackageName())) return;
            if (pkg.equals("android") || pkg.equals("com.android.systemui")) return;
            Notification n = sbn.getNotification();
            if (n == null) return;
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            Bundle ex = n.extras;
            if (ex == null || ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) return;
            CharSequence title = ex.getCharSequence(Notification.EXTRA_TITLE);
            if (title == null || title.length() == 0) return;

            if (isLiveNotification(n)) {
                handleLive(sbn, n, title);
                return;
            }
            if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;

            notifTitle = title.toString();
            CharSequence text = ex.getCharSequence(Notification.EXTRA_TEXT);
            notifText = text == null ? "" : text.toString();
            try {
                notifIcon = ctx.getPackageManager().getApplicationIcon(pkg);
            } catch (Exception e) {
                notifIcon = null;
            }
            notifIntent = n.contentIntent;
            showTransient(NOTIF);
        } catch (Exception ignored) { }
    }

    /* --------------------------------------------------------- multi-task */

    private int activeTaskCount() {
        int count = 0;
        if (callActive) count++;
        if (timerOn) count++;
        if (liveKey != null) count++;
        return count;
    }

    /** Cycle the concurrent activities: call → timer → live → call. */
    private void switchTask(int direction) {
        int[] modes = {CALL, TIMER, LIVE};
        boolean[] on = {callActive, timerOn, liveKey != null};
        int total = 0;
        for (boolean b : on) if (b) total++;
        if (total < 2) return;

        int index = -1;
        for (int i = 0; i < modes.length; i++) {
            if (on[i] && modes[i] == mode) { index = i; break; }
        }
        if (index < 0) {
            for (int i = 0; i < modes.length; i++) if (on[i]) { index = i; break; }
        } else {
            int step = 0;
            do {
                index = (index + direction + modes.length) % modes.length;
                step++;
            } while (!on[index] && step < modes.length);
        }
        applyMode(modes[index], false);
    }

    private void addTaskDots(LinearLayout parent) {
        int total = activeTaskCount();
        if (total < 2) return;
        for (int i = 0; i < total; i++) {
            View dot = new View(ctx);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == 0 ? accent() : 0x55FFFFFF);
            dot.setBackground(g);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(4), dp(4));
            p.leftMargin = dp(3);
            parent.addView(dot, p);
        }
    }

    /* --------------------------------------------------------------- call */

    boolean isCallActive() { return callActive; }
    boolean isCallMuted() { return callMuted; }
    long callStartedAt() { return callStart; }
    String callRouteLabel() { return CALL_ROUTES[callRoute]; }
    void toggleMuteFromScreen() { toggleMute(); }
    void cycleRouteFromScreen() { cycleRoute(); }
    void endCallFromScreen() { endCall(); }

    private void startCall() {
        callActive = true;
        callMuted = false;
        callRoute = 0;
        callStart = System.currentTimeMillis();
        applyMode(CALL, false);
    }

    private void endCall() {
        if (!callActive) return;
        callActive = false;
        h.removeCallbacks(callTick);
        applyMode(CALL_ENDED, false);
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (mode == CALL_ENDED) applyMode(NONE, false);
            }
        }, 2500);
    }

    private void toggleMute() {
        callMuted = !callMuted;
        applyMode(CALL, open);
    }

    private void cycleRoute() {
        callRoute = (callRoute + 1) % CALL_ROUTES.length;
        applyMode(CALL, open);
    }

    private void updateWave() {
        if (callBars == null) return;
        final float[] shape = {0.55f, 0.9f, 1f, 0.72f, 0.6f};
        for (int i = 0; i < callBars.length; i++) {
            callLevels[i] += (float) (Math.random() - 0.5) * 0.4f;
            callLevels[i] = Math.max(0.15f, Math.min(1f, callLevels[i]));
            if (callBars[i] != null) {
                callBars[i].setScaleY(callMuted ? 0.08f : Math.max(0.12f, callLevels[i] * shape[i]));
            }
        }
    }

    private LinearLayout waveRow() {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        callBars = new View[5];
        for (int i = 0; i < 5; i++) {
            View bar = new View(ctx);
            GradientDrawable g = new GradientDrawable();
            g.setColor(callMuted ? 0xFF8E8E93 : accent());
            g.setCornerRadius(dp(2));
            bar.setBackground(g);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(3), dp(14));
            p.leftMargin = dp(1);
            p.rightMargin = dp(1);
            row.addView(bar, p);
            bar.setScaleY(0.4f);
            callBars[i] = bar;
        }
        return row;
    }

    private void buildCallCompact() {
        View dot = new View(ctx);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(callMuted ? 0xFF8E8E93 : 0xFF30D158);
        dot.setBackground(dg);
        compact.addView(dot, new LinearLayout.LayoutParams(dp(6), dp(6)));
        addTaskDots(compact);

        LinearLayout wave = waveRow();
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wp.leftMargin = dp(8);
        compact.addView(wave, wp);

        callNameTv = tv(callName, 13f, 0xFFFFFFFF, true);
        callNameTv.setSingleLine(true);
        callNameTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        np.leftMargin = dp(8);
        compact.addView(callNameTv, np);

        callTimerTv = tv(fmt(System.currentTimeMillis() - callStart), 12.5f, 0xFFB9BECD, false);
        callTimerTv.setFontFeatureSettings("tnum");
        compact.addView(callTimerTv);
    }

    private void buildCallExpanded() {
        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openCallScreen(); }
        });

        FrameLayout av = new FrameLayout(ctx);
        GradientDrawable ag = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF4F8CFF, 0xFF7A4DFF});
        ag.setShape(GradientDrawable.OVAL);
        av.setBackground(ag);
        TextView init = tv(callName.isEmpty() ? "?" : callName.substring(0, 1).toUpperCase(),
                18f, 0xFFFFFFFF, true);
        init.setGravity(Gravity.CENTER);
        av.addView(init, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        head.addView(av, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout meta = new LinearLayout(ctx);
        meta.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mp.leftMargin = dp(12);
        head.addView(meta, mp);

        TextView name = tv(callName, 15.5f, 0xFFFFFFFF, true);
        name.setSingleLine(true);
        meta.addView(name);
        callSubTv = tv(CALL_ROUTES[callRoute] + " · " + fmt(System.currentTimeMillis() - callStart),
                12f, 0x99FFFFFF, false);
        meta.addView(callSubTv);

        if (activeTaskCount() > 1) {
            TextView prev = tv("‹", 22f, 0x99FFFFFF, false);
            prev.setPadding(dp(6), 0, dp(6), 0);
            prev.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { switchTask(-1); }
            });
            head.addView(prev);

            TextView next = tv("›", 22f, 0x99FFFFFF, false);
            next.setPadding(dp(6), 0, dp(6), 0);
            next.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { switchTask(1); }
            });
            head.addView(next);
        } else {
            TextView chev = tv("›", 22f, 0x66FFFFFF, false);
            head.addView(chev);
        }
        expanded.addView(head);

        LinearLayout ctr = new LinearLayout(ctx);
        ctr.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(18);
        expanded.addView(ctr, clp);

        ctr.addView(callControl(callMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic,
                callMuted ? "Unmute" : "Mute",
                callMuted ? 0xFFFFFFFF : 0x24FFFFFF, callMuted, false,
                new View.OnClickListener() {
                    @Override public void onClick(View v) { toggleMute(); }
                }), weight1());

        ctr.addView(callControl(R.drawable.ic_speaker, CALL_ROUTES[callRoute],
                0x24FFFFFF, false, false, new View.OnClickListener() {
                    @Override public void onClick(View v) { cycleRoute(); }
                }), weight1());

        ctr.addView(callControl(R.drawable.ic_end, "End", 0xFFFF453A, false, true,
                new View.OnClickListener() {
                    @Override public void onClick(View v) { endCall(); }
                }), weight1());
    }

    private LinearLayout.LayoutParams weight1() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout callControl(int iconRes, String label, int bg, boolean darkIcon,
                                     boolean rotate, View.OnClickListener l) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        ImageButton b = new ImageButton(ctx);
        b.setImageResource(iconRes);
        b.setColorFilter(darkIcon ? Color.BLACK : Color.WHITE);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(bg);
        b.setBackground(g);
        b.setPadding(dp(14), dp(14), dp(14), dp(14));
        if (rotate) b.setRotation(135f);
        b.setOnClickListener(l);
        col.addView(b, new LinearLayout.LayoutParams(dp(56), dp(56)));

        TextView t = tv(label, 10.5f, 0x99FFFFFF, false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(7);
        col.addView(t, tp);
        return col;
    }

    private void buildCallEnded() {
        ImageView ic = new ImageView(ctx);
        ic.setImageResource(R.drawable.ic_end);
        ic.setRotation(135f);
        ic.setColorFilter(0xFF8E8E93);
        ic.setLayoutParams(new LinearLayout.LayoutParams(dp(16), dp(16)));
        compact.addView(ic);

        TextView t = tv("Call ended", 13f, 0xFFFFFFFF, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = dp(8);
        compact.addView(t, p);

        TextView d = tv(fmt(System.currentTimeMillis() - callStart), 11.5f, 0x66FFFFFF, false);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p2.leftMargin = dp(6);
        compact.addView(d, p2);
    }

    /* ------------------------------------------------------- live activities */

    private boolean isLiveNotification(Notification n) {
        Bundle ex = n.extras;
        if (ex == null) return false;
        int max = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
        boolean chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false);
        boolean ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        return max > 0 || (chrono && ongoing);
    }

    private void handleLive(StatusBarNotification sbn, Notification n, CharSequence title) {
        Bundle ex = n.extras;
        liveKey = sbn.getKey();
        liveTitle = title.toString();
        CharSequence text = ex.getCharSequence(Notification.EXTRA_TEXT);
        liveText = text == null ? "" : text.toString();
        liveMax = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
        liveProgress = ex.getInt(Notification.EXTRA_PROGRESS, 0);
        liveChrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false);
        liveCountDown = ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false);
        liveWhen = n.when;
        try {
            liveIcon = ctx.getPackageManager().getApplicationIcon(sbn.getPackageName());
        } catch (Exception e) {
            liveIcon = null;
        }
        liveIntent = n.contentIntent;
        liveActions = n.actions;

        if (mode == LIVE) {
            refreshLive();
        } else if (mode == NONE || mode == MEDIA || mode == TIMER) {
            applyMode(LIVE, false);
        }
    }

    public void onNotificationRemoved(String key) {
        if (key != null && key.equals(liveKey)) {
            liveKey = null;
            if (mode == LIVE) revert();
        }
    }

    private void refreshLive() {
        if (liveTitleTv != null) liveTitleTv.setText(liveTitle);
        if (liveTextTv != null) liveTextTv.setText(liveText);
        String right = liveRightText();
        if (livePctTv != null) livePctTv.setText(right);
        if (liveTimeTv != null) liveTimeTv.setText(right);
        if (liveBar != null && liveMax > 0) {
            liveBar.setProgress((int) Math.max(0, Math.min(1000, liveProgress * 1000f / liveMax)));
        }
    }

    private String liveRightText() {
        if (liveMax > 0) {
            int pct = (int) Math.max(0, Math.min(100, liveProgress * 100f / liveMax));
            return pct + "%";
        }
        if (liveChrono) {
            long diff = liveCountDown
                    ? (liveWhen - System.currentTimeMillis())
                    : (System.currentTimeMillis() - liveWhen);
            if (diff < 0) diff = 0;
            long s = diff / 1000;
            if (s >= 3600) return (s / 3600) + String.format(Locale.US, ":%02d:%02d", (s % 3600) / 60, s % 60);
            return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
        }
        return "";
    }

    private void cancelLive() {
        if (liveKey != null && NotifListener.instance != null) {
            try { NotifListener.instance.cancelNotification(liveKey); } catch (Exception ignored) { }
        }
        liveKey = null;
        if (mode == LIVE) revert();
    }

    private void buildLiveCompact() {
        ImageView ic = new ImageView(ctx);
        int s = dp(18);
        ic.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        if (liveIcon != null) ic.setImageDrawable(liveIcon);
        else ic.setBackground(gradient(dp(5)));
        compact.addView(ic);

        liveTitleTv = tv(liveTitle, 13f, 0xFFFFFFFF, true);
        liveTitleTv.setSingleLine(true);
        liveTitleTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(9);
        compact.addView(liveTitleTv, tp);

        livePctTv = tv(liveRightText(), 12f, 0xFFB9BECD, false);
        livePctTv.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.leftMargin = dp(8);
        compact.addView(livePctTv, pp);
    }

    private void buildLiveExpanded() {
        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        ImageView ic = new ImageView(ctx);
        int s = dp(36);
        ic.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        if (liveIcon != null) ic.setImageDrawable(liveIcon);
        else ic.setBackground(gradient(dp(9)));
        top.addView(ic);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = dp(12);
        col.setLayoutParams(cp);
        liveTitleTv = tv(liveTitle, 14f, 0xFFFFFFFF, true);
        liveTitleTv.setSingleLine(true);
        liveTitleTv.setEllipsize(TextUtils.TruncateAt.END);
        liveTextTv = tv(liveText, 11.5f, 0x99FFFFFF, false);
        liveTextTv.setSingleLine(true);
        liveTextTv.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(liveTitleTv);
        col.addView(liveTextTv);
        top.addView(col);

        liveTimeTv = tv(liveRightText(), 12f, 0xFFB9BECD, false);
        liveTimeTv.setFontFeatureSettings("tnum");
        top.addView(liveTimeTv);
        expanded.addView(top);

        liveBar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        liveBar.setMax(1000);
        liveBar.setProgressTintList(ColorStateList.valueOf(0xFF30D158));
        liveBar.setProgressBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        bp.topMargin = dp(10);
        expanded.addView(liveBar, bp);
        if (liveMax > 0) liveBar.setProgress((int) Math.max(0, Math.min(1000, liveProgress * 1000f / liveMax)));
        else liveBar.setVisibility(View.GONE);

        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(10);
        expanded.addView(actions, alp);

        if (liveIntent != null) {
            actions.addView(liveActionBtn("Open", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try { liveIntent.send(); } catch (Exception ignored) { }
                }
            }));
        }
        if (liveActions != null) {
            int added = 0;
            for (final Notification.Action a : liveActions) {
                if (added >= 2) break;
                if (a == null || a.title == null || a.actionIntent == null) continue;
                actions.addView(liveActionBtn(a.title.toString(), new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        try { a.actionIntent.send(); } catch (Exception ignored) { }
                    }
                }));
                added++;
            }
        }
        actions.addView(liveActionBtn("Close", new View.OnClickListener() {
            @Override public void onClick(View v) { cancelLive(); }
        }));
    }

    private Button liveActionBtn(String label, View.OnClickListener l) {
        Button b = new Button(ctx);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(12f);
        b.setTextColor(0xFFFFFFFF);
        b.setStateListAnimator(null);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x1FFFFFFF);
        g.setCornerRadius(dp(12));
        b.setBackground(g);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, dp(6), 0, dp(6));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(4);
        p.rightMargin = dp(4);
        b.setLayoutParams(p);
        return b;
    }

    /* --------------------------------------------------------------- battery */

    private final BroadcastReceiver batteryRx = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            pct = level;
            if (dashBat != null) dashBat.setText(pct + "%" + (charging ? " · charging" : ""));
            if (idleText != null && idleIdx % 3 == 1) idleText.setText(pct + "%");
            if (charging && !wasCharging) {
                wasCharging = true;
                if (mode == NONE || mode == MEDIA || mode == TIMER || mode == QUICK) showCharging();
            } else if (!charging) {
                wasCharging = false;
            }
        }
    };

    /* ------------------------------------------------- window ownership */

    /** Move the island into the accessibility overlay layer (above the status bar). */
    void useAccessibilityOverlay(AccessibilityService svc) {
        if (winType == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) return;
        rebindWindow(svc, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
    }

    /** Fall back to a normal application overlay when accessibility is turned off. */
    void useAppOverlay() {
        if (winType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) return;
        rebindWindow(ctx, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
    }

    private void rebindWindow(Context c, int type) {
        int prevMode = mode;
        boolean prevOpen = open;
        hideBubble();
        if (rgbRing != null) rgbRing.stop();
        if (root != null) {
            try { wm.removeView(root); } catch (Exception ignored) {}
            root = null;
        }
        winCtx = c;
        winType = type;
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        buildWindow();
        if (root == null) return;
        applyMode(prevMode, prevOpen);
    }

    /* ------------------------------------------------------- idle dashboard */

    private final Runnable dashTick = new Runnable() {
        @Override public void run() {
            if (mode == DASH) {
                updateDash();
                h.postDelayed(this, 1000);
            } else if (mode == NONE) {
                updateIdleTick();
                h.postDelayed(this, 1000);
            }
        }
    };

    private void updateIdleTick() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRotate > 8000) {
            lastRotate = now;
            idleIdx = (idleIdx + 1) % 3;
            applyIdleWidget();
        }
        if (idleIdx == 0 && idleText != null) idleText.setText(hhmm());
    }

    private void applyIdleWidget() {
        if (idleIcon == null || idleText == null) return;
        switch (idleIdx % 3) {
            case 0:
                idleIcon.setImageResource(R.drawable.ic_clock);
                idleText.setText(hhmm());
                break;
            case 1:
                idleIcon.setImageResource(R.drawable.ic_battery);
                idleText.setText(pct + "%");
                break;
            default:
                idleIcon.setImageResource(R.drawable.ic_timer);
                idleText.setText(alarmText());
                break;
        }
    }

    private void buildIdle() {
        ImageView ic = new ImageView(ctx);
        ic.setLayoutParams(new LinearLayout.LayoutParams(dp(17), dp(17)));
        compact.addView(ic);
        idleIcon = ic;

        idleText = tv("", 13.5f, 0xFFFFFFFF, true);
        idleText.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = dp(8);
        compact.addView(idleText, p);
        applyIdleWidget();
    }

    private void buildDashboard() {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        dashTime = tv("", 32f, 0xFFFFFFFF, true);
        dashTime.setFontFeatureSettings("tnum");
        dashTime.setIncludeFontPadding(false);
        top.addView(dashTime);

        dashDate = tv("", 12f, 0x99FFFFFF, false);
        dashDate.setGravity(Gravity.END);
        top.addView(dashDate, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(top);

        LinearLayout row1 = new LinearLayout(ctx);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row1lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row1lp.topMargin = dp(10);
        col.addView(row1, row1lp);

        dashBat = chip(R.drawable.ic_battery, "", new View.OnClickListener() {
            @Override public void onClick(View v) { }
        });
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b1.rightMargin = dp(4);
        row1.addView(dashBat, b1);

        dashAlarm = chip(R.drawable.ic_timer, "", new View.OnClickListener() {
            @Override public void onClick(View v) { openAlarms(); }
        });
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b2.leftMargin = dp(4);
        row1.addView(dashAlarm, b2);

        dashMedia = chip(R.drawable.ic_play, "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (mc != null) applyMode(MEDIA, true);
            }
        });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(8);
        col.addView(dashMedia, mlp);

        dashTimer = chip(R.drawable.ic_timer, "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (timerOn) applyMode(TIMER, true);
            }
        });
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(8);
        col.addView(dashTimer, tlp);

        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(12);
        col.addView(actions, alp);

        actions.addView(actionBtn("Open", new View.OnClickListener() {
            @Override public void onClick(View v) { applyMode(NONE, false); openApp(); }
        }));
        actions.addView(actionBtn("Timer", new View.OnClickListener() {
            @Override public void onClick(View v) { startTimer(300); }
        }));
        actions.addView(actionBtn("Charge", new View.OnClickListener() {
            @Override public void onClick(View v) { pct = 68; showCharging(); }
        }));

        expanded.addView(col, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateDash();
    }

    private void updateDash() {
        if (dashTime == null) return;
        dashTime.setText(hhmm());
        dashDate.setText(new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(new Date()));
        if (dashBat != null) dashBat.setText(pct + "%" + (wasCharging ? " · charging" : ""));
        if (dashAlarm != null) dashAlarm.setText(alarmText());
        if (dashMedia != null) dashMedia.setText(mediaText());
        if (dashTimer != null) dashTimer.setText(timerOn ? fmt(timerRemain) : "No timer");
    }

    private TextView chip(int icon, String text, View.OnClickListener l) {
        TextView t = tv(text, 12f, 0xE6FFFFFF, false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setCompoundDrawablePadding(dp(7));
        try {
            Drawable d = ctx.getResources().getDrawable(icon, null);
            d.setBounds(0, 0, dp(15), dp(15));
            t.setCompoundDrawables(d, null, null, null);
        } catch (Exception ignored) { }
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x14FFFFFF);
        g.setCornerRadius(dp(12));
        t.setBackground(g);
        t.setPadding(dp(10), dp(8), dp(10), dp(8));
        t.setOnClickListener(l);
        return t;
    }

    private Button actionBtn(String label, View.OnClickListener l) {
        Button b = new Button(ctx);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(12.5f);
        b.setTextColor(0xFFFFFFFF);
        b.setStateListAnimator(null);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF23232F);
        g.setCornerRadius(dp(13));
        b.setBackground(g);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, dp(7), 0, dp(7));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(4);
        p.rightMargin = dp(4);
        b.setLayoutParams(p);
        return b;
    }

    private String hhmm() {
        return new SimpleDateFormat("H:mm", Locale.getDefault()).format(new Date());
    }

    private String alarmText() {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            AlarmManager.AlarmClockInfo info = am != null ? am.getNextAlarmClock() : null;
            if (info == null) return "No alarm";
            return new SimpleDateFormat("H:mm", Locale.getDefault())
                    .format(new Date(info.getTriggerTime()));
        } catch (Exception e) {
            return "No alarm";
        }
    }

    private String mediaText() {
        if (mc == null) return "Nothing playing";
        MediaMetadata md = mc.getMetadata();
        String title = md != null ? str(md.getText(MediaMetadata.METADATA_KEY_TITLE)) : null;
        return title != null ? title : "Playing";
    }

    private void openAlarms() {
        Intent i = new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { ctx.startActivity(i); } catch (Exception ignored) { }
    }

    /* ------------------------------------------------- secondary activity */

    private final Runnable bubbleSync = new Runnable() {
        @Override public void run() { updateBubble(); }
    };

    private void ensureBubble() {
        if (bubbleRoot != null) return;
        bubbleRoot = new LinearLayout(ctx);
        bubbleRoot.setOrientation(LinearLayout.HORIZONTAL);
        bubbleRoot.setGravity(Gravity.CENTER_VERTICAL);
        bubbleRoot.setPadding(dp(13), 0, dp(13), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF000000);
        bg.setCornerRadius(dp(17));
        bg.setStroke(Math.max(1, dp(1) / 2), 0x26FFFFFF);
        bubbleRoot.setBackground(bg);

        bubbleText = tv("", 12f, 0xFFE6E9F2, false);
        bubbleRoot.addView(bubbleText);

        bubbleRoot.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int target = bubbleMode;
                hideBubble();
                showTask(target);
            }
        });
        bubbleRoot.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                int target = bubbleMode;
                hideBubble();
                if (target == CALL || target == TIMER || target == LIVE) applyMode(target, true);
                return true;
            }
        });
    }

    private void showTask(int m) {
        if (m == CALL && callActive) applyMode(CALL, false);
        else if (m == TIMER && timerOn) applyMode(TIMER, false);
        else if (m == LIVE && liveKey != null) applyMode(LIVE, false);
    }

    private int nextTaskMode() {
        int[] modes = {CALL, TIMER, LIVE};
        boolean[] on = {callActive, timerOn, liveKey != null};
        int current = -1;
        for (int i = 0; i < modes.length; i++) {
            if (on[i] && modes[i] == mode) { current = i; break; }
        }
        if (current < 0) return -1;
        for (int step = 1; step < modes.length; step++) {
            int idx = (current + step) % modes.length;
            if (on[idx]) return modes[idx];
        }
        return -1;
    }

    private String bubbleLabel(int m) {
        if (m == CALL) return "Call";
        if (m == TIMER) return "Timer · " + fmt(timerRemain);
        if (m == LIVE) {
            String pct = liveRightText();
            return pct.isEmpty() ? "Live" : "Live · " + pct;
        }
        return "";
    }

    private void updateBubble() {
        if (root == null || lp == null || wm == null) { hideBubble(); return; }
        if (open || activeTaskCount() < 2) { hideBubble(); return; }

        int next = nextTaskMode();
        if (next < 0) { hideBubble(); return; }

        ensureBubble();
        bubbleMode = next;
        bubbleText.setText(bubbleLabel(next));

        int width = ViewGroup.LayoutParams.WRAP_CONTENT;
        int height = dp(34);
        if (bubbleLp == null) {
            bubbleLp = new WindowManager.LayoutParams(width, height, winType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            if (Build.VERSION.SDK_INT >= 30) bubbleLp.setFitInsetsTypes(0);
            bubbleLp.windowAnimations = 0;
        }
        bubbleLp.gravity = lp.gravity;
        bubbleLp.x = lp.x;
        bubbleLp.y = lp.y + (lp.height > 0 ? lp.height : dp(40)) + dp(8);

        try {
            if (!bubbleAttached) {
                wm.addView(bubbleRoot, bubbleLp);
                bubbleAttached = true;
            } else {
                wm.updateViewLayout(bubbleRoot, bubbleLp);
            }
        } catch (Exception ignored) { }
    }

    private void hideBubble() {
        if (!bubbleAttached || bubbleRoot == null) return;
        try { wm.removeView(bubbleRoot); } catch (Exception ignored) { }
        bubbleAttached = false;
        bubbleMode = -1;
    }

    /* ---------------------------------------------------------- touch & drag */

    private float dragStartRawY = 0f;
    private float dragStartRawX = 0f;
    private boolean moved = false;
    private int dragStartY = 0;
    private boolean dragging = false;
    private long downAt = 0;
    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            if (!dragging) expandCurrent();
        }
    };

    private void expandCurrent() {
        switch (mode) {
            case CALL:
            case TIMER:
            case LIVE:
                if (!open) applyMode(mode, true);
                break;
            case NONE:
                // tap always answers visually: quick panel, no clock
                applyMode(QUICK, false);
                break;
            default:
                openApp();
                break;
        }
    }

    private boolean onRootTouch(MotionEvent e) {
        if (lp == null || root == null) return false;
        final int slop = ViewConfiguration.get(ctx).getScaledTouchSlop();

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartRawX = e.getRawX();
                dragStartRawY = e.getRawY();
                dragStartY = lp.y;
                dragging = false;
                moved = false;
                downAt = SystemClock.uptimeMillis();
                h.postDelayed(longPress, 800);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = e.getRawX() - dragStartRawX;
                float dy = e.getRawY() - dragStartRawY;
                if (!moved && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                    moved = true;
                    h.removeCallbacks(longPress);
                }
                if (moved && !isPositionLocked() && Math.abs(dy) >= Math.abs(dx)) {
                    int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
                    int ny = (int) (dragStartY + dy);
                    ny = Math.max(0, Math.min(screenH - dp(80), ny));
                    if (ny != lp.y) {
                        lp.y = ny;
                        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP: {
                h.removeCallbacks(longPress);
                float ux = e.getRawX() - dragStartRawX;
                float uy = e.getRawY() - dragStartRawY;
                long duration = SystemClock.uptimeMillis() - downAt;

                if (Math.abs(ux) > Math.abs(uy) && Math.abs(ux) > dp(48)) {
                    bump("swipes");
                    switchTask(ux < 0 ? 1 : -1);
                } else if (uy < -dp(48) && open) {
                    applyMode(mode, false);
                } else if (uy > dp(48) && !open && (mode == CALL || mode == TIMER)) {
                    applyMode(mode, true);
                } else if (!moved && duration < 800) {
                    // a tap is a tap: tiny finger jitter no longer becomes a drag
                    onTap();
                } else if (moved && !isPositionLocked()) {
                    prefs().edit().putInt("y", lp.y).putInt("posVer", POS_VERSION).apply();
                    updateBubble();
                }
                dragging = false;
                moved = false;
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                h.removeCallbacks(longPress);
                dragging = false;
                moved = false;
                return true;

            default:
                return false;
        }
    }

    private void bump(String key) {
        prefs().edit().putInt(key, prefs().getInt(key, 0) + 1).apply();
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences("island", Context.MODE_PRIVATE);
    }

    /** 0 = centre around the camera, 1 = under the bar, 2 = right edge, 3 = left edge. */
    private void applyEdge(int edge) {
        int savedY = prefs().getInt("y", -1);
        if (prefs().getInt("posVer", 0) != POS_VERSION) savedY = -1;
        int defaultY;
        switch (edge) {
            case 2:
                lp.gravity = Gravity.TOP | Gravity.END;
                lp.x = dp(6);
                defaultY = Math.max(dp(4), topInset() - dp(34));
                break;
            case 3:
                lp.gravity = Gravity.TOP | Gravity.START;
                lp.x = dp(6);
                defaultY = Math.max(dp(4), topInset() - dp(34));
                break;
            case 1:
                lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                lp.x = 0;
                defaultY = Math.max(topInset() + dp(2), dp(40));
                break;
            default:
                lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                lp.x = 0;
                defaultY = cameraY();
                break;
        }
        lp.y = savedY >= 0 ? savedY : defaultY;
    }

    private void relayout() {
        if (root == null || wm == null) return;
        try {
            wm.removeView(root);
        } catch (Exception ignored) {}
        applyEdge(prefs().getInt("posEdge", 0));
        try {
            wm.addView(root, lp);
        } catch (Exception ignored) {}
    }

    boolean isPositionLocked() {
        return prefs().getBoolean("lockPos", true);
    }

    void setPositionLocked(boolean locked) {
        prefs().edit().putBoolean("lockPos", locked).apply();
    }

    /** Live vertical position in px, used by the in-app slider. */
    void setY(int y) {
        if (lp == null || root == null) return;
        int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
        int clamped = Math.max(0, Math.min(screenH - dp(80), y));
        lp.y = clamped;
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
        prefs().edit().putInt("y", clamped).putInt("posVer", POS_VERSION).apply();
    }

    int currentY() {
        return lp != null ? lp.y : -1;
    }

    /** Top of a 36 dp pill so its centre lands on the front camera hole. */
    private int cameraY() {
        int center = prefs().getInt("cutCenterY", -1);
        if (center > 0) return Math.max(0, center - dp(18));
        // No cutout rect reported on this device: a 40 dp pill starting at 6 dp
        // still wraps the punch-hole but keeps its top edge out of the status
        // bar's pull-down strip, where touches are stolen by SystemUI.
        return dp(6);
    }

    /** 0 = camera, 1 = below the bar, 2 = right edge, 3 = left edge. */
    void setPosMode(int edge) {
        prefs().edit()
                .putInt("posEdge", edge)
                .putBoolean("lockPos", true)
                .remove("y")
                .putInt("posVer", POS_VERSION)
                .apply();
        relayout();
    }

    void resetPosition() {
        prefs().edit()
                .putInt("posEdge", 0)
                .remove("y")
                .putInt("posVer", POS_VERSION)
                .apply();
        relayout();
    }

    /* ----------------------------------------------------------------- timer */

    public void startTimer(int seconds) {
        timerRemain = seconds * 1000L;
        timerOn = true;
        timerPaused = false;
        timerEndAt = SystemClock.elapsedRealtime() + timerRemain;
        h.removeCallbacks(timerTick);
        applyMode(TIMER, false);
        h.post(timerTick);
    }

    private void tickTimer() {
        if (!timerOn) return;
        long left = timerPaused ? timerRemain : timerEndAt - SystemClock.elapsedRealtime();
        if (left <= 0) {
            timerOn = false;
            timerRemain = 0;
            vibrate();
            showTransient(DONE);
            return;
        }
        timerRemain = left;
        updateTimerLabels();
        h.postDelayed(timerTick, 400);
    }

    private void updateTimerLabels() {
        String s = fmt(timerRemain);
        if (timerSmall != null) timerSmall.setText(s);
        if (timerBig != null) timerBig.setText(s);
        if (timerStatus != null) timerStatus.setText(timerPaused ? "Paused" : "Focus · running");
        if (dashTimer != null) dashTimer.setText(fmt(timerRemain));
    }

    private void toggleTimerPause() {
        if (!timerOn) return;
        if (timerPaused) {
            timerEndAt = SystemClock.elapsedRealtime() + timerRemain;
            timerPaused = false;
            h.removeCallbacks(timerTick);
            h.post(timerTick);
        } else {
            timerRemain = Math.max(0, timerEndAt - SystemClock.elapsedRealtime());
            timerPaused = true;
            h.removeCallbacks(timerTick);
        }
        applyMode(TIMER, true);
    }

    private void closeTimer() {
        timerOn = false;
        h.removeCallbacks(timerTick);
        revert();
    }

    /* ------------------------------------------------------------ behaviour */

    int chargeStyle() {
        return prefs().getInt("chargeStyle", 0);
    }

    void setChargeStyle(int style) {
        prefs().edit().putInt("chargeStyle", style).apply();
    }

    private void showCharging() {
        h.removeCallbacks(chargeToCompact);
        h.removeCallbacks(chargeHide);
        int style = chargeStyle();
        if (style == 3) return;                       // animation off
        if (style == 2) {                             // compact pill only
            applyMode(CHARGE, false);
            h.postDelayed(chargeHide, 3000);
            return;
        }
        applyMode(style == 0 || style == 4 ? CHARGE_BIG : CHARGE_BOLT, false);
        h.postDelayed(chargeToCompact, 3200);
    }

    private void cancelChargeAnims() {
        if (chargeAnim != null) {
            chargeAnim.cancel();
            chargeAnim = null;
        }
        if (boltPulse != null) {
            boltPulse.cancel();
            boltPulse = null;
        }
    }

    private void onTap() {
        bump("taps");
        haptic();
        switch (mode) {
            case NOTIF:
                if (notifIntent != null) {
                    try { notifIntent.send(); } catch (Exception ignored) {}
                }
                cancelTransient();
                break;
            case CHARGE:
            case DONE:
                cancelTransient();
                break;
            case MEDIA:
            case TIMER:
                applyMode(mode, !open);
                break;
            case QUICK:
            case DASH:
                applyMode(NONE, false);
                break;
            case LIVE:
                applyMode(LIVE, !open);
                break;
            case CALL:
                applyMode(CALL, !open);
                break;
            case CALL_ENDED:
                applyMode(NONE, false);
                break;
            case NONE:
                // tap always answers visually: quick panel, no clock
                applyMode(QUICK, false);
                break;
            default:
                break;
        }
    }

    private void showTransient(int m) {
        applyMode(m, false);
        if (endTransient != null) h.removeCallbacks(endTransient);
        endTransient = new Runnable() {
            @Override public void run() {
                if (mode == NOTIF || mode == CHARGE || mode == DONE) revert();
            }
        };
        h.postDelayed(endTransient, 5000);
    }

    private void cancelTransient() {
        if (endTransient != null) {
            h.removeCallbacks(endTransient);
            endTransient = null;
        }
        revert();
    }

    private void revert() {
        if (callActive) {
            applyMode(CALL, false);
            return;
        }
        if (timerOn) {
            applyMode(TIMER, false);
            return;
        }
        if (liveKey != null) {
            applyMode(LIVE, false);
            return;
        }
        MediaController p = pickPlaying();
        if (p != null) {
            bindMedia(p);
            applyMode(MEDIA, false);
        } else {
            applyMode(NONE, false);
        }
    }

    public void demo(String kind) {
        if ("charge".equals(kind)) {
            pct = 32;
            showCharging();
        } else if ("notif".equals(kind)) {
            notifTitle = "Alex";
            notifText = "Are we still on for 8?";
            notifIcon = null;
            notifIntent = null;
            showTransient(NOTIF);
        } else if ("timer".equals(kind)) {
            startTimer(300);
        } else if ("idle".equals(kind)) {
            applyMode(NONE, false);
        } else if ("call".equals(kind)) {
            startCall();
        } else if ("clear".equals(kind)) {
            callActive = false;
            h.removeCallbacks(callTick);
            liveKey = null;
            timerOn = false;
            h.removeCallbacks(timerTick);
            applyMode(NONE, false);
        } else if ("live".equals(kind)) {
            demoLive();
        } else if ("resetpos".equals(kind)) {
            resetPosition();
        } else if ("poscam".equals(kind)) {
            setPosMode(0);
        } else if ("posbar".equals(kind)) {
            setPosMode(1);
        } else if ("posright".equals(kind)) {
            setPosMode(2);
        } else if ("posleft".equals(kind)) {
            setPosMode(3);
        }
    }

    private void demoLive() {
        liveKey = "demo";
        liveTitle = "Downloading island.apk";
        liveText = "in progress";
        liveProgress = 8;
        liveMax = 100;
        liveChrono = false;
        liveWhen = System.currentTimeMillis();
        liveIcon = null;
        liveIntent = null;
        liveActions = null;
        applyMode(LIVE, false);
        if (liveDemoAnim != null) liveDemoAnim.cancel();
        liveDemoAnim = ValueAnimator.ofInt(8, 100);
        liveDemoAnim.setDuration(20000);
        liveDemoAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                liveProgress = (Integer) a.getAnimatedValue();
                if (mode == LIVE) refreshLive();
            }
        });
        liveDemoAnim.start();
    }

    private void openCallScreen() {
        Intent i = new Intent(ctx, CallActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra("name", callName);
        try { ctx.startActivity(i); } catch (Exception ignored) { }
    }

    private void openApp() {
        Intent i = new Intent(ctx, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // Android 10+ blocks background activity starts; API 34 lets the sender
        // explicitly allow it for a PendingIntent, which is the only reliable
        // route from an overlay service.
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                PendingIntent pi = PendingIntent.getActivity(ctx, 0, i,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                android.app.ActivityOptions opts = android.app.ActivityOptions.makeBasic();
                opts.setPendingIntentBackgroundActivityStartMode(
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                pi.send(ctx, 0, null, null, null, null, opts.toBundle());
                return;
            } catch (Exception ignored) { }
        }
        try { ctx.startActivity(i); } catch (Exception ignored) {}
    }

    private void haptic() {
        try {
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(VibrationEffect.createOneShot(8, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {}
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {}
    }

    /* --------------------------------------------------------------- helpers */

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    private int expandedWidth() {
        return Math.min(ctx.getResources().getDisplayMetrics().widthPixels - dp(24), dp(360));
    }

    private int notifWidth() {
        return Math.min(ctx.getResources().getDisplayMetrics().widthPixels - dp(32), dp(340));
    }

    private TextView tv(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setIncludeFontPadding(false);
        return t;
    }

    private ImageButton iconBtn(int res, int size, boolean filled, View.OnClickListener l) {
        ImageButton b = new ImageButton(ctx);
        b.setImageResource(res);
        b.setColorFilter(Color.WHITE);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setPadding(dp(10), dp(10), dp(10), dp(10));
        if (filled) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0x1FFFFFFF);
            b.setBackground(g);
        } else {
            b.setBackgroundColor(Color.TRANSPARENT);
        }
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
        p.leftMargin = dp(3);
        p.rightMargin = dp(3);
        b.setLayoutParams(p);
        return b;
    }

    private Bitmap artwork(MediaMetadata md) {
        if (md == null) return null;
        Bitmap bm = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bm == null) bm = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bm == null) bm = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        return bm;
    }

    private Bitmap round(Bitmap src, int size, float radius) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Bitmap scaled = Bitmap.createScaledBitmap(src, size, size, true);
        p.setShader(new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0, 0, size, size), radius, radius, p);
        return out;
    }

    private int accent() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                return ctx.getColor(android.R.color.system_accent1_200);
            }
        } catch (Exception ignored) { }
        return 0xFF30D158;
    }

    private GradientDrawable gradient(float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF7B3CFF, 0xFFFF2F7B, 0xFFFF9B3D});
        g.setCornerRadius(radius);
        return g;
    }

    private static String str(CharSequence cs) {
        if (cs == null) return null;
        String s = cs.toString().trim();
        return s.length() == 0 ? null : s;
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms) / 1000;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}

package com.dshgo.island;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Full-screen call screen reached by a single tap on the expanded island card.
 * It is the endpoint of the island's one and only route.
 */
public class CallActivity extends Activity {

    private final Handler h = new Handler(Looper.getMainLooper());
    private String name = "Call";
    private TextView timer;
    private TextView muteLabel;
    private TextView routeLabel;
    private ImageButton muteBtn;
    private LinearLayout waveRow;
    private final View[] bars = new View[7];
    private final float[] levels = {0.4f, 0.7f, 0.9f, 0.6f, 0.8f, 0.5f, 0.65f};

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            IslandController c = IslandService.controller;
            if (c == null || !c.isCallActive()) {
                finish();
                return;
            }
            timer.setText(elapsed(c.callStartedAt()));
            animateWave();
            h.postDelayed(this, 110);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() != null && getIntent().getStringExtra("name") != null) {
            name = getIntent().getStringExtra("name");
        }
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        IslandController c = IslandService.controller;
        if (c == null || !c.isCallActive()) {
            finish();
            return;
        }
        render(c);
        h.removeCallbacks(tick);
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /* ------------------------------------------------------------------- ui */

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(54), dp(24), dp(36));
        root.setBackgroundColor(0xFF080A12);

        FrameLayout avatar = new FrameLayout(this);
        GradientDrawable ag = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF4F8CFF, 0xFF7A4DFF});
        ag.setShape(GradientDrawable.OVAL);
        avatar.setBackground(ag);
        TextView initial = new TextView(this);
        initial.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
        initial.setTextSize(40);
        initial.setTextColor(Color.WHITE);
        initial.setTypeface(Typeface.DEFAULT_BOLD);
        initial.setGravity(Gravity.CENTER);
        avatar.addView(initial, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(avatar, new LinearLayout.LayoutParams(dp(104), dp(104)));

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextSize(23);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(14);
        root.addView(title, tlp);

        timer = new TextView(this);
        timer.setText("0:00");
        timer.setTextSize(15);
        timer.setTextColor(0xFF9AA0B4);
        timer.setFontFeatureSettings("tnum");
        root.addView(timer);

        waveRow = new LinearLayout(this);
        waveRow.setOrientation(LinearLayout.HORIZONTAL);
        waveRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < bars.length; i++) {
            View bar = new View(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF30D158);
            bg.setCornerRadius(dp(3));
            bar.setBackground(bg);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(6), dp(40));
            bp.leftMargin = dp(2);
            bp.rightMargin = dp(2);
            waveRow.addView(bar, bp);
            bar.setScaleY(0.4f);
            bars[i] = bar;
        }
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(26);
        root.addView(waveRow, wlp);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(40);
        root.addView(controls, clp);

        LinearLayout muteCol = control(R.drawable.ic_mic, "Mute", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                IslandController c = IslandService.controller;
                if (c != null) c.toggleMuteFromScreen();
                render(c);
            }
        });
        muteBtn = (ImageButton) muteCol.getChildAt(0);
        muteLabel = (TextView) muteCol.getChildAt(1);
        controls.addView(muteCol, weight());

        LinearLayout routeCol = control(R.drawable.ic_speaker, "Phone", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                IslandController c = IslandService.controller;
                if (c != null) c.cycleRouteFromScreen();
                render(c);
            }
        });
        routeLabel = (TextView) routeCol.getChildAt(1);
        controls.addView(routeCol, weight());

        LinearLayout endCol = control(R.drawable.ic_end, "End", true, new View.OnClickListener() {
            @Override public void onClick(View v) {
                IslandController c = IslandService.controller;
                if (c != null) c.endCallFromScreen();
                finish();
            }
        });
        controls.addView(endCol, weight());

        TextView back = new TextView(this);
        back.setText("Back to island");
        back.setTextSize(13);
        back.setTextColor(0xFF8B90A3);
        back.setPadding(0, dp(34), 0, 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        root.addView(back);

        return root;
    }

    private LinearLayout control(int iconRes, String label, boolean end, View.OnClickListener l) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        ImageButton b = new ImageButton(this);
        b.setImageResource(iconRes);
        b.setColorFilter(Color.WHITE);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(end ? 0xFFFF453A : 0x24FFFFFF);
        b.setBackground(g);
        b.setPadding(dp(17), dp(17), dp(17), dp(17));
        if (end) b.setRotation(135f);
        b.setOnClickListener(l);
        col.addView(b, new LinearLayout.LayoutParams(dp(66), dp(66)));

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(11);
        t.setTextColor(0xFF9AA0B4);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(8);
        col.addView(t, tp);
        return col;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    /* -------------------------------------------------------------- state */

    private void render(IslandController c) {
        if (c == null) return;
        boolean muted = c.isCallMuted();
        muteLabel.setText(muted ? "Unmute" : "Mute");
        muteBtn.setImageResource(muted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(muted ? 0xFFFFFFFF : 0x24FFFFFF);
        muteBtn.setBackground(g);
        muteBtn.setColorFilter(muted ? Color.BLACK : Color.WHITE);
        routeLabel.setText(c.callRouteLabel());
        timer.setText(elapsed(c.callStartedAt()));
        for (View bar : bars) {
            bar.setAlpha(muted ? 0.3f : 1f);
        }
    }

    private void animateWave() {
        IslandController c = IslandService.controller;
        boolean muted = c != null && c.isCallMuted();
        float[] shape = {0.5f, 0.8f, 1f, 0.65f, 0.9f, 0.55f, 0.75f};
        for (int i = 0; i < bars.length; i++) {
            levels[i] += (float) (Math.random() - 0.5) * 0.45f;
            levels[i] = Math.max(0.15f, Math.min(1f, levels[i]));
            bars[i].setScaleY(muted ? 0.08f : Math.max(0.12f, levels[i] * shape[i]));
        }
    }

    private static String elapsed(long start) {
        long seconds = Math.max(0, (System.currentTimeMillis() - start) / 1000);
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

package com.dshgo.island;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Our own notification shade: quick tiles, brightness and the live notification
 * list, drawn on top of whatever is running. Opened by swiping down from the top
 * of the home screen.
 */
public class ShadeActivity extends Activity {

    private LinearLayout list;
    private TextView empty;
    private TextView clock;
    private TextView date;
    private boolean torchOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        buildUi();
        updateClock();
        loadNotifications();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateClock();
        loadNotifications();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    // ------------------------------------------------------------------

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xE6070910);
        root.setOnClickListener(v -> finish());

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(40), dp(16), dp(20));
        panel.setOnClickListener(v -> { });

        // header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.VERTICAL);
        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(34f);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        times.addView(clock);
        date = new TextView(this);
        date.setTextColor(0x99FFFFFF);
        date.setTextSize(12f);
        times.addView(date);
        header.addView(times, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextView battery = new TextView(this);
        battery.setTextColor(0xCCFFFFFF);
        battery.setTextSize(13f);
        battery.setGravity(Gravity.END);
        battery.setText(batteryText());
        right.addView(battery);
        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(0xE6FFFFFF);
        close.setTextSize(12f);
        close.setGravity(Gravity.CENTER);
        close.setBackground(glass(0x1FFFFFFF, 16));
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        closeLp.topMargin = dp(8);
        right.addView(close, closeLp);
        header.addView(right);
        panel.addView(header);

        // quick tiles
        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(16);

        tiles.addView(tile("📶", "Wi‑Fi", true, v -> openPanel(Build.VERSION.SDK_INT >= 29
                ? new Intent(android.provider.Settings.Panel.ACTION_WIFI)
                : new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))), weight());
        tiles.addView(tile("ᛒ", "Bluetooth", false, v -> openPanel(
                new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))), weight());
        final View[] flashRef = new View[1];
        flashRef[0] = tile("🔦", "Lampe", false, v -> {
            boolean on = toggleTorch();
            setTileOn(flashRef[0], "🔦", "Lampe", on);
        });
        tiles.addView(flashRef[0], weight());
        tiles.addView(tile("🌙", "Modes", false, v -> toggleDnd()), weight());
        panel.addView(tiles, tlp);

        // brightness
        LinearLayout brightRow = new LinearLayout(this);
        brightRow.setOrientation(LinearLayout.HORIZONTAL);
        brightRow.setGravity(Gravity.CENTER_VERTICAL);
        brightRow.setPadding(dp(14), dp(6), dp(14), dp(6));
        brightRow.setBackground(glass(0x14FFFFFF, 22));
        TextView sun = new TextView(this);
        sun.setText("☀");
        sun.setTextSize(15f);
        sun.setTextColor(0xCCFFFFFF);
        brightRow.addView(sun);
        SeekBar bright = new SeekBar(this);
        bright.setMax(255);
        bright.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        bright.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33FFFFFF));
        bright.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        int value = 128;
        try {
            value = android.provider.Settings.System.getInt(getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception ignored) { }
        bright.setProgress(Math.max(1, Math.min(255, value)));
        bright.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser) return;
                try {
                    android.provider.Settings.System.putInt(getContentResolver(),
                            android.provider.Settings.System.SCREEN_BRIGHTNESS, progress);
                } catch (Exception ignored) { }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        brightRow.addView(bright, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(12);
        panel.addView(brightRow, blp);

        // section label
        TextView label = new TextView(this);
        label.setText("Notifications");
        label.setTextColor(0xB3FFFFFF);
        label.setTextSize(12f);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(18);
        llp.bottomMargin = dp(8);
        panel.addView(label, llp);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        empty = new TextView(this);
        empty.setText("Aucune notification");
        empty.setTextColor(0x66FFFFFF);
        empty.setTextSize(12.5f);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(26), 0, 0);
        list.addView(empty);

        // clear all
        TextView clear = new TextView(this);
        clear.setText("Tout effacer");
        clear.setTextColor(Color.WHITE);
        clear.setTextSize(13.5f);
        clear.setTypeface(Typeface.DEFAULT_BOLD);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(0, dp(14), 0, dp(14));
        clear.setBackground(glass(0x1FFFFFFF, 26));
        clear.setOnClickListener(v -> clearAll());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(12);
        panel.addView(clear, clp);

        root.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private View tile(String glyph, String label, boolean on, View.OnClickListener click) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(10), dp(8), dp(10));
        tile.setBackground(glass(on ? 0x3340A0FF : 0x14FFFFFF, 22));
        tile.setOnClickListener(click);

        TextView g = new TextView(this);
        g.setText(glyph);
        g.setTextSize(18f);
        g.setTextColor(0xFFFFFFFF);
        g.setGravity(Gravity.CENTER);
        tile.addView(g);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(10.5f);
        l.setTextColor(0xE6FFFFFF);
        l.setGravity(Gravity.CENTER);
        l.setMaxLines(1);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(6);
        tile.addView(l, llp);
        return tile;
    }

    private void setTileOn(View tile, String glyph, String label, boolean on) {
        tile.setBackground(glass(on ? 0x3340A0FF : 0x14FFFFFF, 22));
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(8);
        return lp;
    }

    private GradientDrawable glass(int fill, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(Math.max(1, dp(0.7f)), 0x38FFFFFF);
        return g;
    }

    // ------------------------------------------------------------------

    private void updateClock() {
        Date now = new Date();
        if (clock != null) {
            clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        }
        if (date != null) {
            date.setText(new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now));
        }
    }

    private String batteryText() {
        try {
            BatteryManager bm = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            int pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            return pct + "%";
        } catch (Exception e) {
            return "";
        }
    }

    private void loadNotifications() {
        if (list == null) return;
        // keep the trailing "empty" label
        list.removeAllViews();

        List<StatusBarNotification> items = new ArrayList<>();
        NotifListener listener = NotifListener.instance;
        if (listener != null) {
            try {
                StatusBarNotification[] active = listener.getActiveNotifications();
                if (active != null) {
                    for (StatusBarNotification sbn : active) {
                        if (sbn == null) continue;
                        if (getPackageName().equals(sbn.getPackageName())) continue;
                        if ("com.android.systemui".equals(sbn.getPackageName())) continue;
                        if ((sbn.getNotification().flags
                                & android.app.Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                        items.add(sbn);
                    }
                }
            } catch (Exception ignored) { }
        }
        Collections.sort(items, new Comparator<StatusBarNotification>() {
            @Override public int compare(StatusBarNotification a, StatusBarNotification b) {
                return Long.compare(b.getPostTime(), a.getPostTime());
            }
        });

        if (items.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            list.addView(empty);
            return;
        }
        empty.setVisibility(View.GONE);
        for (StatusBarNotification sbn : items) {
            list.addView(notificationCard(sbn));
        }
    }

    private View notificationCard(final StatusBarNotification sbn) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.TOP);
        card.setPadding(dp(12), dp(12), dp(10), dp(12));
        card.setBackground(glass(0x14FFFFFF, 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        ImageView icon = new ImageView(this);
        Drawable d = null;
        try { d = getPackageManager().getApplicationIcon(sbn.getPackageName()); } catch (Exception ignored) { }
        if (d != null) {
            icon.setImageDrawable(d);
            card.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        }

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(12);

        String appName;
        try {
            appName = getPackageManager()
                    .getApplicationLabel(getPackageManager().getApplicationInfo(sbn.getPackageName(), 0))
                    .toString();
        } catch (Exception e) {
            appName = sbn.getPackageName();
        }

        TextView meta = new TextView(this);
        meta.setText(appName + " · " + relativeTime(sbn.getPostTime()));
        meta.setTextColor(0x88FFFFFF);
        meta.setTextSize(10.5f);
        texts.addView(meta);

        Bundle ex = sbn.getNotification().extras;
        CharSequence title = ex == null ? null : ex.getCharSequence(android.app.Notification.EXTRA_TITLE);
        CharSequence text = ex == null ? null : ex.getCharSequence(android.app.Notification.EXTRA_TEXT);
        if (title != null) {
            TextView t = new TextView(this);
            t.setText(title);
            t.setTextColor(Color.WHITE);
            t.setTextSize(13f);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setMaxLines(1);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(t);
        }
        if (text != null) {
            TextView t = new TextView(this);
            t.setText(text);
            t.setTextColor(0xCCFFFFFF);
            t.setTextSize(12f);
            t.setMaxLines(2);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(t);
        }
        card.addView(texts, tlp);

        TextView dismiss = new TextView(this);
        dismiss.setText("×");
        dismiss.setTextColor(0xAAFFFFFF);
        dismiss.setTextSize(16f);
        dismiss.setGravity(Gravity.CENTER);
        dismiss.setPadding(dp(8), 0, dp(4), 0);
        dismiss.setOnClickListener(v -> {
            NotifListener l = NotifListener.instance;
            if (l != null) {
                try { l.cancelNotification(sbn.getKey()); } catch (Exception ignored) { }
            }
            loadNotifications();
        });
        card.addView(dismiss);

        card.setOnClickListener(v -> {
            android.app.PendingIntent pi = sbn.getNotification().contentIntent;
            if (pi != null) {
                try {
                    pi.send();
                } catch (Exception ignored) { }
            }
            finish();
        });
        return card;
    }

    private String relativeTime(long when) {
        long diff = Math.max(0, System.currentTimeMillis() - when);
        long minutes = diff / 60000;
        if (minutes < 1) return "à l'instant";
        if (minutes < 60) return "il y a " + minutes + " min";
        long hours = minutes / 60;
        if (hours < 24) return "il y a " + hours + " h";
        return new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(when));
    }

    private void clearAll() {
        NotifListener l = NotifListener.instance;
        if (l != null) {
            try {
                for (StatusBarNotification sbn : l.getActiveNotifications()) {
                    if (getPackageName().equals(sbn.getPackageName())) continue;
                    if ((sbn.getNotification().flags
                            & android.app.Notification.FLAG_ONGOING_EVENT) != 0) continue;
                    try { l.cancelNotification(sbn.getKey()); } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        }
        loadNotifications();
    }

    private void openPanel(Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) { }
    }

    private boolean toggleTorch() {
        try {
            CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) return torchOn;
            torchOn = !torchOn;
            cm.setTorchMode(cm.getCameraIdList()[0], torchOn);
            return torchOn;
        } catch (Exception e) {
            torchOn = false;
            return false;
        }
    }

    private void toggleDnd() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        try {
            boolean priority = nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
            nm.setInterruptionFilter(priority
                    ? NotificationManager.INTERRUPTION_FILTER_ALL
                    : NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        } catch (SecurityException e) {
            try {
                startActivity(new Intent(android.provider.Settings
                        .ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            } catch (Exception ignored) { }
        }
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}

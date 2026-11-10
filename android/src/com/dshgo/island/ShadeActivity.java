package com.dshgo.island;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * iOS-style Notification Center: big clock, date, grouped glass stacks with a
 * count pill, expandable on tap, per-item and global clear.
 */
public class ShadeActivity extends Activity {

    private LinearLayout list;
    private TextView clock;
    private TextView date;
    private TextView battery;

    private final List<Group> groups = new ArrayList<>();
    private final java.util.Set<String> expanded = new java.util.HashSet<>();

    private static class Group {
        String pkg;
        String appName;
        Drawable icon;
        long newest;
        final List<StatusBarNotification> items = new ArrayList<>();
    }

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
    // Layout
    // ------------------------------------------------------------------

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF151833, 0xFF0A0C18, 0xFF04050A});
        root.setBackground(bg);
        root.setOnClickListener(v -> finish());

        // soft colour bloom so the glass has something to sit on
        View bloom = new View(this);
        android.graphics.Paint bloomPaint = new android.graphics.Paint();
        bloomPaint.setShader(new android.graphics.RadialGradient(dp(320), dp(120), dp(420),
                new int[]{0x557C4DFF, 0x00000000}, null, android.graphics.Shader.TileMode.CLAMP));
        bloom.setBackground(new android.graphics.drawable.PaintDrawable());
        root.addView(bloom, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(520)));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(18), dp(54), dp(18), dp(14));
        column.setOnClickListener(v -> { });

        // top row: clock + battery
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.TOP);

        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(56f);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        topRow.addView(clock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        battery = new TextView(this);
        battery.setTextColor(0x99FFFFFF);
        battery.setTextSize(12.5f);
        battery.setGravity(Gravity.END);
        topRow.addView(battery);
        column.addView(topRow);

        date = new TextView(this);
        date.setTextColor(0xE6FFFFFF);
        date.setTextSize(12f);
        date.setLetterSpacing(0.06f);
        date.setTypeface(Typeface.DEFAULT_BOLD);
        column.addView(date);

        // section header
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.HORIZONTAL);
        section.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(26);
        slp.bottomMargin = dp(10);

        TextView title = new TextView(this);
        title.setText("Notifications");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        section.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView clear = new TextView(this);
        clear.setText("✕");
        clear.setTextColor(0xCCFFFFFF);
        clear.setTextSize(13f);
        clear.setGravity(Gravity.CENTER);
        clear.setBackground(glass(0x1AFFFFFF, 15));
        clear.setOnClickListener(v -> clearAll());
        section.addView(clear, new LinearLayout.LayoutParams(dp(30), dp(30)));
        column.addView(section, slp);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(column, clp);

        // grabber
        View grabber = new View(this);
        GradientDrawable gg = new GradientDrawable();
        gg.setColor(0x40FFFFFF);
        gg.setCornerRadius(dp(3));
        grabber.setBackground(gg);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(dp(128), dp(5));
        glp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        glp.bottomMargin = dp(18);
        root.addView(grabber, glp);

        setContentView(root);
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    private void loadNotifications() {
        list.removeAllViews();
        groups.clear();

        NotifListener listener = NotifListener.instance;
        if (listener != null) {
            try {
                StatusBarNotification[] active = listener.getActiveNotifications();
                if (active != null) {
                    for (StatusBarNotification sbn : active) {
                        if (sbn == null) continue;
                        if (getPackageName().equals(sbn.getPackageName())) continue;
                        if ("com.android.systemui".equals(sbn.getPackageName())) continue;
                        Notification n = sbn.getNotification();
                        if (n == null) continue;
                        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                        CharSequence t = n.extras == null ? null
                                : n.extras.getCharSequence(Notification.EXTRA_TITLE);
                        if (t == null) continue;
                        addToGroup(sbn);
                    }
                }
            } catch (Exception ignored) { }
        }

        Collections.sort(groups, new Comparator<Group>() {
            @Override public int compare(Group a, Group b) { return Long.compare(b.newest, a.newest); }
        });

        if (groups.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Aucune notification");
            empty.setTextColor(0x66FFFFFF);
            empty.setTextSize(13f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, 0);
            list.addView(empty);
            return;
        }

        for (Group group : groups) {
            list.addView(groupView(group));
            if (expanded.contains(group.pkg)) {
                for (int i = 1; i < group.items.size(); i++) {
                    View extra = cardFor(group, group.items.get(i), 0);
                    LinearLayout holder = new LinearLayout(this);
                    holder.setPadding(dp(10), 0, 0, 0);
                    holder.addView(extra);
                    LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    hlp.bottomMargin = dp(8);
                    list.addView(holder, hlp);
                }
            }
        }
    }

    private void addToGroup(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        Group target = null;
        for (Group g : groups) {
            if (g.pkg.equals(pkg)) { target = g; break; }
        }
        if (target == null) {
            target = new Group();
            target.pkg = pkg;
            try {
                target.appName = getPackageManager()
                        .getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
            } catch (Exception e) {
                target.appName = pkg;
            }
            try { target.icon = getPackageManager().getApplicationIcon(pkg); } catch (Exception ignored) { }
            groups.add(target);
        }
        target.items.add(sbn);
        target.newest = Math.max(target.newest, sbn.getPostTime());
    }

    // ------------------------------------------------------------------
    // Cards
    // ------------------------------------------------------------------

    private View groupView(final Group group) {
        boolean open = expanded.contains(group.pkg);
        boolean stacked = group.items.size() > 1 && !open;

        FrameLayout wrap = new FrameLayout(this);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.bottomMargin = dp(10);
        wrap.setLayoutParams(wlp);

        if (stacked) {
            // two glass edges peeking under the top card
            for (int i = 2; i >= 1; i--) {
                View edge = new View(this);
                edge.setBackground(glass(i == 2 ? 0x0DFFFFFF : 0x16FFFFFF, 22));
                FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(74));
                elp.leftMargin = dp(6 * i);
                elp.rightMargin = dp(6 * i);
                elp.topMargin = dp(6 * i);
                wrap.addView(edge, elp);
            }
        }

        View card = cardFor(group, group.items.get(0), stacked ? group.items.size() : 0);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrap.addView(card, clp);

        if (stacked) {
            card.setOnClickListener(v -> {
                expanded.add(group.pkg);
                loadNotifications();
            });
        }

        return wrap;
    }

    private View cardFor(final Group group, final StatusBarNotification sbn, int count) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.TOP);
        card.setPadding(dp(14), dp(14), dp(12), dp(14));
        card.setBackground(glass(0x26FFFFFF, 22));
        card.setElevation(dp(6));

        if (group.icon != null) {
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(group.icon);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(38), dp(38));
            ilp.rightMargin = dp(12);
            card.addView(icon, ilp);
        }

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        TextView meta = new TextView(this);
        meta.setText(group.appName + "  ·  " + relativeTime(sbn.getPostTime()));
        meta.setTextColor(0x8CFFFFFF);
        meta.setTextSize(10.5f);
        texts.addView(meta);

        Notification n = sbn.getNotification();
        CharSequence title = n.extras == null ? null : n.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = n.extras == null ? null : n.extras.getCharSequence(Notification.EXTRA_TEXT);
        if (title != null) {
            TextView t = new TextView(this);
            t.setText(title);
            t.setTextColor(Color.WHITE);
            t.setTextSize(14.5f);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setMaxLines(1);
            t.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(3);
            texts.addView(t, lp);
        }
        if (text != null) {
            TextView t = new TextView(this);
            t.setText(text);
            t.setTextColor(0xC7FFFFFF);
            t.setTextSize(13f);
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(2);
            texts.addView(t, lp);
        }
        card.addView(texts, tlp);

        if (count > 1) {
            TextView badge = new TextView(this);
            badge.setText(String.valueOf(count));
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(11f);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(glass(0x33FFFFFF, 12));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(24), dp(24));
            blp.leftMargin = dp(8);
            card.addView(badge, blp);
        }

        card.setOnClickListener(v -> {
            PendingIntent pi = n.contentIntent;
            if (pi != null) {
                try { pi.send(); } catch (Exception ignored) { }
            }
            finish();
        });

        card.setOnLongClickListener(v -> {
            NotifListener l = NotifListener.instance;
            if (l != null) {
                try { l.cancelNotification(sbn.getKey()); } catch (Exception ignored) { }
            }
            loadNotifications();
            return true;
        });
        return card;
    }

    private void clearAll() {
        NotifListener l = NotifListener.instance;
        if (l != null) {
            try {
                for (StatusBarNotification sbn : l.getActiveNotifications()) {
                    if (getPackageName().equals(sbn.getPackageName())) continue;
                    if ((sbn.getNotification().flags & Notification.FLAG_ONGOING_EVENT) != 0) continue;
                    try { l.cancelNotification(sbn.getKey()); } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        }
        loadNotifications();
    }

    // ------------------------------------------------------------------

    private void updateClock() {
        Date now = new Date();
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        date.setText(new SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
                .format(now).toUpperCase(Locale.getDefault()));
        try {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            battery.setText(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + " %");
        } catch (Exception ignored) { }
    }

    private String relativeTime(long when) {
        long diff = Math.max(0, System.currentTimeMillis() - when);
        long minutes = diff / 60000;
        if (minutes < 1) return "maintenant";
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60;
        if (hours < 24) return hours + " h";
        return new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(when));
    }

    private GradientDrawable glass(int fill, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(Math.max(1, dp(0.6f)), 0x30FFFFFF);
        return g;
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}

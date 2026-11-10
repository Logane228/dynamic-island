package com.dshgo.island;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal home screen: clock, app grid, and a shortcut to the island settings.
 * Registering CATEGORY_HOME lets the user pick it as the default launcher.
 */
public class LauncherActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Drawable> iconCache = new HashMap<>();

    private TextView clock;
    private TextView date;
    private TextView defaultChip;
    private List<ResolveInfo> apps = new ArrayList<>();

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateClock();
            handler.postDelayed(this, 15000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadApps();
        updateClock();
        updateDefaultChip();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(tick);
        handler.post(tick);
        updateDefaultChip();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    public void onBackPressed() {
        // home screen: back does nothing
    }

    private void updateClock() {
        Date now = new Date();
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        date.setText(new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now));
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0B0D14, 0xFF05060B});
        root.setBackground(bg);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(22), dp(46), dp(22), dp(18));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.BOTTOM);

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.VERTICAL);

        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(52f);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        times.addView(clock);

        date = new TextView(this);
        date.setTextColor(0xFF9AA1B2);
        date.setTextSize(13f);
        times.addView(date);

        header.addView(times, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        defaultChip = new TextView(this);
        defaultChip.setText("Set as home");
        defaultChip.setTextSize(11.5f);
        defaultChip.setTextColor(0xFFFFFFFF);
        defaultChip.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(0x22FFFFFF);
        chipBg.setCornerRadius(dp(18));
        defaultChip.setBackground(chipBg);
        defaultChip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { requestHomeRole(); }
        });
        header.addView(defaultChip);

        column.addView(header);

        GridView grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(16));
        grid.setHorizontalSpacing(dp(2));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0x00000000));
        grid.setClipToPadding(false);
        grid.setPadding(0, dp(34), 0, dp(16));
        grid.setAdapter(new AppAdapter());
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                launch(apps.get(position));
            }
        });
        grid.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                openInfo(apps.get(position));
                return true;
            }
        });

        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        column.addView(grid, glp);

        TextView settings = new TextView(this);
        settings.setText("Island settings");
        settings.setTextSize(12f);
        settings.setTextColor(0xFF8E96A8);
        settings.setGravity(Gravity.CENTER);
        settings.setPadding(0, dp(10), 0, dp(6));
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(LauncherActivity.this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        });
        column.addView(settings);

        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> found = pm.queryIntentActivities(main, 0);
        List<ResolveInfo> filtered = new ArrayList<>();
        for (ResolveInfo info : found) {
            if (info.activityInfo == null) continue;
            if (getPackageName().equals(info.activityInfo.packageName)
                    && info.activityInfo.name.endsWith("LauncherActivity")) continue;
            filtered.add(info);
        }
        Collections.sort(filtered, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                String la = a.loadLabel(getPackageManager()).toString();
                String lb = b.loadLabel(getPackageManager()).toString();
                return la.compareToIgnoreCase(lb);
            }
        });
        apps = filtered;
    }

    private void launch(ResolveInfo info) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(info.activityInfo.packageName);
            if (intent == null) {
                intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setComponent(new android.content.ComponentName(
                        info.activityInfo.packageName, info.activityInfo.name));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) { }
    }

    private void openInfo(ResolveInfo info) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.fromParts("package", info.activityInfo.packageName, null));
            startActivity(i);
        } catch (Exception ignored) { }
    }

    private void requestHomeRole() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager rm = getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_HOME));
                    return;
                }
            }
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception ignored) { }
    }

    private boolean isDefaultHome() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager rm = getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    return rm.isRoleHeld(RoleManager.ROLE_HOME);
                }
            }
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && getPackageName().equals(ri.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    private void updateDefaultChip() {
        if (defaultChip == null) return;
        defaultChip.setVisibility(isDefaultHome() ? View.GONE : View.VISIBLE);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int position) { return apps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout item;
            ImageView icon;
            TextView label;

            if (convertView == null) {
                item = new LinearLayout(LauncherActivity.this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER_HORIZONTAL);

                icon = new ImageView(LauncherActivity.this);
                item.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));

                label = new TextView(LauncherActivity.this);
                label.setTextColor(0xFFE4E8F2);
                label.setTextSize(11.5f);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(1);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                llp.topMargin = dp(6);
                item.addView(label, llp);

                item.setTag(new View[]{icon, label});
            } else {
                item = (LinearLayout) convertView;
                View[] views = (View[]) item.getTag();
                icon = (ImageView) views[0];
                label = (TextView) views[1];
            }

            ResolveInfo info = apps.get(position);
            String pkg = info.activityInfo.packageName;
            Drawable cached = iconCache.get(pkg);
            if (cached == null) {
                cached = info.loadIcon(getPackageManager());
                iconCache.put(pkg, cached);
            }
            icon.setImageDrawable(cached);
            label.setText(info.loadLabel(getPackageManager()));
            return item;
        }
    }
}

package com.dshgo.island;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.CallLog;
import android.text.Editable;
import android.text.TextWatcher;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.inputmethod.EditorInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Liquid-glass home screen: parallax wallpaper, two swipeable pages, an
 * interactive quick-action widget, dock, and iOS-style jiggle editing.
 */
public class LauncherActivity extends Activity {

    private static final int PAGE_ONE_COUNT = 16;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WallpaperView wallpaper;
    private GlassPager pager;
    private LinearLayout dots;
    private TextView clock;
    private TextView date;

    private EditText search;
    private final List<AppEntry> allApps = new ArrayList<>();
    private String favoriteName;
    private String favoriteNumber;
    private View widgetCard;
    private TextView favTitle;
    private TextView favSub;
    private GridView grid0;
    private GridView grid1;
    private AppAdapter adapter0;
    private AppAdapter adapter1;

    private final List<AppEntry> pageApps0 = new ArrayList<>();
    private final List<AppEntry> pageApps1 = new ArrayList<>();
    private final Set<String> hidden = new HashSet<>();

    private boolean jiggle = false;
    private boolean tinted = false;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateClock();
            handler.postDelayed(this, 20000);
        }
    };

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hidden.addAll(prefs().getStringSet("hiddenApps", new HashSet<String>()));
        buildUi();
        loadApps();
        updateClock();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private float shadeDownY = -1f;

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                shadeDownY = ev.getY();
                break;
            case android.view.MotionEvent.ACTION_UP: {
                float upY = ev.getY();
                // swipe down from the very top opens our own shade
                if (shadeDownY >= 0 && shadeDownY < dp(40) && upY > dp(160)) {
                    shadeDownY = -1f;
                    try {
                        startActivity(new Intent(this, ShadeActivity.class));
                    } catch (Exception ignored) { }
                    return true;
                }
                shadeDownY = -1f;
                break;
            }
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override public void onBackPressed() {
        if (jiggle) { exitJiggle(); return; }
        // home screen: swallow back
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("island", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------
    // UI
    // ------------------------------------------------------------------

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF05060A);

        wallpaper = new WallpaperView(this);
        root.addView(wallpaper, match());

        pager = new GlassPager(this);
        pager.setListener(new GlassPager.Listener() {
            @Override public void onPageChanged(int page) {
                updateDots(page);
            }
            @Override public void onScroll(float offsetPx, float fraction) {
                wallpaper.setParallax(offsetPx);
            }
        });
        root.addView(pager, match());

        pager.addView(buildPage0(), match());
        pager.addView(buildPage1(), match());

        // header (clock) — floats above the pages
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(24), dp(28), dp(24), 0);
        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(44f);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        header.addView(clock);
        date = new TextView(this);
        date.setTextColor(0xB3FFFFFF);
        date.setTextSize(13f);
        header.addView(date);

        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.gravity = Gravity.TOP | Gravity.START;
        root.addView(header, hlp);

        LinearLayout searchWrap = new LinearLayout(this);
        searchWrap.setOrientation(LinearLayout.HORIZONTAL);
        searchWrap.setGravity(Gravity.CENTER_VERTICAL);
        searchWrap.setPadding(dp(14), 0, dp(14), 0);
        searchWrap.setBackground(glass(0x1FFFFFFF, 22));

        TextView magnifier = new TextView(this);
        magnifier.setText("🔍");
        magnifier.setTextSize(13f);
        searchWrap.addView(magnifier);

        search = new EditText(this);
        search.setHint("Rechercher");
        search.setHintTextColor(0x88FFFFFF);
        search.setTextColor(Color.WHITE);
        search.setTextSize(14f);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setPadding(dp(10), 0, 0, 0);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { filterApps(e.toString()); }
        });
        searchWrap.addView(search, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        slp.gravity = Gravity.TOP;
        slp.leftMargin = dp(20);
        slp.rightMargin = dp(20);
        slp.topMargin = dp(104);
        root.addView(searchWrap, slp);

        // appearance toggle
        TextView appearance = new TextView(this);
        appearance.setText("Aa");
        appearance.setTextColor(Color.WHITE);
        appearance.setTextSize(13f);
        appearance.setGravity(Gravity.CENTER);
        appearance.setBackground(glass(0x1FFFFFFF, 22));
        appearance.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleTint(); }
        });
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(dp(46), dp(46));
        alp.gravity = Gravity.END | Gravity.TOP;
        alp.rightMargin = dp(18);
        alp.topMargin = dp(44);
        root.addView(appearance, alp);

        // dots
        dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.gravity = Gravity.BOTTOM;
        dlp.bottomMargin = dp(118);
        root.addView(dots, dlp);
        for (int i = 0; i < 2; i++) {
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == 0 ? 0xFFFFFFFF : 0x55FFFFFF);
            dot.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(6), dp(6));
            lp.rightMargin = dp(6);
            dots.addView(dot, lp);
        }

        // dock
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setBackground(glass(0x1AFFFFFF, 30));
        dock.setPadding(dp(10), dp(10), dp(10), dp(10));
        dock.setElevation(dp(12));

        addDockItem(dock, "Téléphone", "com.google.android.dialer", dialIntent());
        addDockItem(dock, "Messages", "com.google.android.apps.messaging", smsIntent());
        addDockItem(dock, "Chrome", "com.android.chrome", webIntent());
        addDockItem(dock, "Island", getPackageName(),
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(96));
        dockLp.gravity = Gravity.BOTTOM;
        dockLp.leftMargin = dp(14);
        dockLp.rightMargin = dp(14);
        dockLp.bottomMargin = dp(12);
        root.addView(dock, dockLp);

        setContentView(root);
    }

    private void addDockItem(LinearLayout dock, String label, String pkg, Intent intent) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setOnClickListener(v -> launchIntent(intent));

        ImageView icon = new ImageView(this);
        Drawable drawable = null;
        try {
            drawable = getPackageManager().getApplicationIcon(pkg);
        } catch (Exception ignored) { }
        if (drawable == null) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(0x33FFFFFF);
            g.setCornerRadius(dp(16));
            icon.setImageDrawable(g);
        } else {
            icon.setImageDrawable(drawable);
        }
        cell.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextColor(0xCCFFFFFF);
        caption.setTextSize(10f);
        caption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(4);
        cell.addView(caption, clp);

        dock.addView(cell, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private View buildPage0() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20), dp(172), dp(20), dp(150));

        widgetCard = buildWidget();
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.bottomMargin = dp(14);
        column.addView(widgetCard, wlp);

        grid0 = buildGrid(column);
        adapter0 = new AppAdapter(pageApps0);
        grid0.setAdapter(adapter0);
        return column;
    }

    private View buildPage1() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20), dp(172), dp(20), dp(150));

        grid1 = buildGrid(column);
        adapter1 = new AppAdapter(pageApps1);
        grid1.setAdapter(adapter1);
        return column;
    }

    private GridView buildGrid(LinearLayout parent) {
        GridView grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(16));
        grid.setHorizontalSpacing(dp(2));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setClipToPadding(false);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0x00000000));
        grid.setPadding(0, dp(18), 0, 0);
        grid.setOnItemClickListener((parentView, view, position, id) -> {
            if (jiggle) { exitJiggle(); return; }
            AppAdapter adapter = (AppAdapter) parentView.getAdapter();
            launchIntent(adapter.getItemEntry(position).launch);
        });
        grid.setOnItemLongClickListener((parentView, view, position, id) -> {
            enterJiggle();
            return true;
        });
        parent.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return grid;
    }

    private View buildWidget() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(14));
        card.setBackground(glass(0x1FFFFFFF, 30));
        card.setElevation(dp(10));

        TextView eyebrow = new TextView(this);
        eyebrow.setText("RACCOURCIS");
        eyebrow.setTextSize(10f);
        eyebrow.setLetterSpacing(0.14f);
        eyebrow.setTextColor(0x99FFFFFF);
        card.addView(eyebrow);

        TextView title = new TextView(this);
        title.setText("Bonjour");
        title.setTextSize(19f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(2);
        tlp.bottomMargin = dp(12);
        card.addView(title, tlp);

        resolveFavorite();
        LinearLayout call = quickRow("★",
                favoriteName != null ? "Appeler " + favoriteName : "Choisir un favori",
                favoriteNumber != null ? favoriteNumber : "WhatsApp · contact fréquent",
                true);
        favTitle = (TextView) ((LinearLayout) call.getChildAt(1)).getChildAt(0);
        favSub = (TextView) ((LinearLayout) call.getChildAt(1)).getChildAt(1);
        call.setOnClickListener(v -> {
            if (favoriteNumber != null && !favoriteNumber.isEmpty()) {
                launchIntent(new Intent(Intent.ACTION_DIAL,
                        Uri.parse("tel:" + Uri.encode(favoriteNumber))));
            } else {
                pickFavorite();
            }
        });
        card.addView(call);

        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tilesLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tilesLp.topMargin = dp(6);

        LinearLayout repo = quickTile("🌐", "Ouvrir le repo", "github.com");
        repo.setOnClickListener(v -> launchIntent(webIntent()));
        tiles.addView(repo, tileWeight());

        LinearLayout wifi = quickTile("📶", "Wi‑Fi", "Activé");
        wifi.setOnClickListener(v -> {
            Intent panel = Build.VERSION.SDK_INT >= 29
                    ? new Intent(Settings.Panel.ACTION_WIFI)
                    : new Intent(Settings.ACTION_WIFI_SETTINGS);
            launchIntent(panel.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        tiles.addView(wifi, tileWeight());

        card.addView(tiles, tilesLp);
        return card;
    }

    private LinearLayout quickRow(String avatarText, String title, String subtitle, boolean primary) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(glass(0x1AFFFFFF, 20));

        TextView avatar = new TextView(this);
        avatar.setText(avatarText);
        avatar.setTextSize(14f);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setTextColor(Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable ag = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF7C4DFF, 0xFF2F6BFF});
        ag.setShape(GradientDrawable.OVAL);
        avatar.setBackground(ag);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textsLp.leftMargin = dp(12);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(14f);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(t);

        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextSize(11f);
        s.setTextColor(0x99FFFFFF);
        texts.addView(s);

        row.addView(texts, textsLp);
        return row;
    }

    private LinearLayout quickTile(String glyph, String label, String sub) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(13), dp(12), dp(13), dp(12));
        tile.setBackground(glass(0x14FFFFFF, 20));

        TextView g = new TextView(this);
        g.setText(glyph);
        g.setTextSize(18f);
        tile.addView(g);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(12f);
        l.setTextColor(Color.WHITE);
        l.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(8);
        tile.addView(l, llp);

        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextSize(10.5f);
        s.setTextColor(0x99FFFFFF);
        tile.addView(s);
        return tile;
    }

    private LinearLayout.LayoutParams tileWeight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(8);
        return lp;
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = new ArrayList<>();
        for (ResolveInfo info : pm.queryIntentActivities(main, 0)) {
            if (info.activityInfo == null) continue;
            if (getPackageName().equals(info.activityInfo.packageName)) continue;
            if (hidden.contains(info.activityInfo.packageName)) continue;
            found.add(info);
        }
        Collections.sort(found, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(getPackageManager()).toString()
                        .compareToIgnoreCase(b.loadLabel(getPackageManager()).toString());
            }
        });

        pageApps0.clear();
        pageApps1.clear();
        allApps.clear();
        for (int i = 0; i < found.size(); i++) {
            ResolveInfo info = found.get(i);
            AppEntry entry = new AppEntry();
            entry.label = info.loadLabel(pm).toString();
            entry.pkg = info.activityInfo.packageName;
            entry.icon = info.loadIcon(pm);
            entry.launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(new ComponentName(entry.pkg, info.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            allApps.add(entry);
            if (i < PAGE_ONE_COUNT) pageApps0.add(entry);
            else pageApps1.add(entry);
        }
        if (adapter0 != null) adapter0.notifyDataSetChanged();
        if (adapter1 != null) adapter1.notifyDataSetChanged();
    }

    private void filterApps(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        if (widgetCard != null) widgetCard.setVisibility(q.isEmpty() ? View.VISIBLE : View.GONE);
        pageApps0.clear();
        pageApps1.clear();
        if (q.isEmpty()) {
            for (int i = 0; i < allApps.size(); i++) {
                if (i < PAGE_ONE_COUNT) pageApps0.add(allApps.get(i));
                else pageApps1.add(allApps.get(i));
            }
        } else {
            for (AppEntry e : allApps) {
                if (e.label.toLowerCase(Locale.getDefault()).contains(q)) pageApps0.add(e);
            }
        }
        if (adapter0 != null) adapter0.notifyDataSetChanged();
        if (adapter1 != null) adapter1.notifyDataSetChanged();
        if (pager.getPage() != 0) pager.setPage(0, true);
    }

    /** Most-dialled contact from the call log, else the one saved by the picker. */
    private void resolveFavorite() {
        favoriteName = prefs().getString("favName", null);
        favoriteNumber = prefs().getString("favNumber", null);
        if (favoriteNumber != null && !favoriteNumber.isEmpty()) return;

        // 1) highest-count WhatsApp sender, resolved to a number via contacts
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            String topSender = null;
            int topCount = 0;
            for (java.util.Map.Entry<String, ?> e : prefs().getAll().entrySet()) {
                if (!e.getKey().startsWith("wa:")) continue;
                if (!(e.getValue() instanceof Integer)) continue;
                int count = (Integer) e.getValue();
                if (count > topCount) { topCount = count; topSender = e.getKey().substring(3); }
            }
            if (topSender != null && topCount >= 2 && resolveContactNumber(topSender)) return;
        }

        // 2) fall back to the most-dialled number in the system call log
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return;

        Cursor c = null;
        try {
            c = getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME},
                    null, null, CallLog.Calls.DATE + " DESC LIMIT 200");
            if (c == null) return;
            java.util.HashMap<String, Integer> counts = new java.util.HashMap<>();
            java.util.HashMap<String, String> names = new java.util.HashMap<>();
            while (c.moveToNext()) {
                String number = c.getString(0);
                if (number == null || number.isEmpty()) continue;
                counts.put(number, counts.getOrDefault(number, 0) + 1);
                String name = c.getString(1);
                if (name != null && !name.isEmpty()) names.put(number, name);
            }
            String best = null;
            int bestCount = 0;
            for (java.util.Map.Entry<String, Integer> e : counts.entrySet()) {
                if (e.getValue() > bestCount) { bestCount = e.getValue(); best = e.getKey(); }
            }
            if (best != null) {
                favoriteNumber = best;
                favoriteName = names.containsKey(best) ? names.get(best) : best;
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
    }

    private boolean resolveContactNumber(String displayName) {
        Cursor c = null;
        try {
            c = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                    new String[]{displayName}, null);
            if (c != null && c.moveToFirst()) {
                String number = c.getString(0);
                if (number != null && !number.isEmpty()) {
                    favoriteName = displayName;
                    favoriteNumber = number;
                    return true;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return false;
    }

    private void pickFavorite() {
        requestPermissions(new String[]{Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_CALL_LOG}, 7);
        try {
            startActivityForResult(new Intent(Intent.ACTION_PICK,
                    ContactsContract.Contacts.CONTENT_URI), 8);
        } catch (Exception ignored) { }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 8 || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Cursor c = getContentResolver().query(data.getData(),
                    new String[]{ContactsContract.Contacts._ID,
                            ContactsContract.Contacts.DISPLAY_NAME},
                    null, null, null);
            if (c != null && c.moveToFirst()) {
                String id = c.getString(0);
                String name = c.getString(1);
                c.close();
                Cursor p = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                        new String[]{id}, null);
                if (p != null && p.moveToFirst()) {
                    String number = p.getString(0);
                    p.close();
                    favoriteName = name;
                    favoriteNumber = number;
                    prefs().edit().putString("favName", name)
                            .putString("favNumber", number).apply();
                    refreshFavoriteUi();
                }
            }
        } catch (Exception ignored) { }
    }

    private void refreshFavoriteUi() {
        if (favTitle != null) {
            favTitle.setText(favoriteName != null ? "Appeler " + favoriteName : "Choisir un favori");
        }
        if (favSub != null) {
            favSub.setText(favoriteNumber != null ? favoriteNumber : "WhatsApp · contact fréquent");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 7) {
            resolveFavorite();
            refreshFavoriteUi();
        }
    }

    private void launchIntent(Intent intent) {
        if (jiggle) { exitJiggle(); return; }
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(Intent.createChooser(intent, null)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        }
    }

    private Intent dialIntent() {
        return new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+33600000000"));
    }

    private Intent smsIntent() {
        return new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"));
    }

    private Intent webIntent() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Logane228/dynamic-island"));
    }

    // ------------------------------------------------------------------
    // Jiggle editing
    // ------------------------------------------------------------------

    private void enterJiggle() {
        if (jiggle) return;
        jiggle = true;
        if (adapter0 != null) adapter0.notifyDataSetChanged();
        if (adapter1 != null) adapter1.notifyDataSetChanged();
        shake(grid0);
        shake(grid1);
    }

    private void exitJiggle() {
        if (!jiggle) return;
        jiggle = false;
        stopShake(grid0);
        stopShake(grid1);
        if (adapter0 != null) adapter0.notifyDataSetChanged();
        if (adapter1 != null) adapter1.notifyDataSetChanged();
    }

    private void shake(GridView grid) {
        if (grid == null) return;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            android.animation.ObjectAnimator anim = android.animation.ObjectAnimator
                    .ofFloat(child, "rotation", -1.2f, 1.2f);
            anim.setDuration(150 + (i % 4) * 15);
            anim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            anim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            anim.start();
            child.setTag(R.id.island_shake_tag, anim);
        }
    }

    private void stopShake(GridView grid) {
        if (grid == null) return;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            Object tag = child.getTag(R.id.island_shake_tag);
            if (tag instanceof android.animation.ObjectAnimator) {
                ((android.animation.ObjectAnimator) tag).cancel();
            }
            child.setRotation(0f);
        }
    }

    private void hideApp(AppEntry entry) {
        hidden.add(entry.pkg);
        prefs().edit().putStringSet("hiddenApps", new HashSet<>(hidden)).apply();
        pageApps0.remove(entry);
        pageApps1.remove(entry);
        if (adapter0 != null) adapter0.notifyDataSetChanged();
        if (adapter1 != null) adapter1.notifyDataSetChanged();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void updateClock() {
        Date now = new Date();
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        date.setText(new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now));
    }

    private void updateDots(int page) {
        for (int i = 0; i < dots.getChildCount(); i++) {
            dots.getChildAt(i).setAlpha(i == page ? 1f : 0.35f);
        }
    }

    private void toggleTint() {
        tinted = !tinted;
        wallpaper.setTinted(tinted);
        if (adapter0 != null) adapter0.notifyDataSetChanged();
        if (adapter1 != null) adapter1.notifyDataSetChanged();
    }

    private GradientDrawable glass(int fill, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(Math.max(1, dp(0.7f)), 0x38FFFFFF);
        return g;
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private static class AppEntry {
        String label;
        String pkg;
        Drawable icon;
        Intent launch;
    }

    // ------------------------------------------------------------------
    // Grid adapter
    // ------------------------------------------------------------------

    private class AppAdapter extends BaseAdapter {
        private final List<AppEntry> entries;

        AppAdapter(List<AppEntry> entries) {
            this.entries = entries;
        }

        AppEntry getItemEntry(int position) {
            return entries.get(position);
        }

        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            FrameLayout cell;
            ImageView icon;
            TextView label;
            TextView badge;

            if (convertView == null) {
                cell = new FrameLayout(LauncherActivity.this);
                LinearLayout box = new LinearLayout(LauncherActivity.this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setGravity(Gravity.CENTER_HORIZONTAL);

                icon = new ImageView(LauncherActivity.this);
                box.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));

                label = new TextView(LauncherActivity.this);
                label.setTextSize(11f);
                label.setTextColor(0xFFE8EBF4);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(1);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                llp.topMargin = dp(6);
                box.addView(label, llp);

                cell.addView(box, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                badge = new TextView(LauncherActivity.this);
                badge.setText("−");
                badge.setTextSize(15f);
                badge.setTextColor(Color.WHITE);
                badge.setGravity(Gravity.CENTER);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(0xE61C1C20);
                bg.setStroke(dp(1), 0x66FFFFFF);
                badge.setBackground(bg);
                FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(22), dp(22));
                blp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                blp.leftMargin = dp(18);
                cell.addView(badge, blp);

                cell.setTag(new Object[]{icon, label, badge});
            } else {
                cell = (FrameLayout) convertView;
                Object[] refs = (Object[]) cell.getTag();
                icon = (ImageView) refs[0];
                label = (TextView) refs[1];
                badge = (TextView) refs[2];
            }

            final AppEntry entry = entries.get(position);
            icon.setImageDrawable(entry.icon);
            if (tinted) {
                ColorMatrix cm = new ColorMatrix();
                cm.setSaturation(0.08f);
                icon.setColorFilter(new ColorMatrixColorFilter(cm));
            } else {
                icon.clearColorFilter();
            }
            label.setText(entry.label);

            badge.setVisibility(jiggle ? View.VISIBLE : View.GONE);
            badge.setOnClickListener(v -> hideApp(entry));
            return cell;
        }
    }
}

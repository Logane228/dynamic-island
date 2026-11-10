package com.dshgo.island;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.app.role.RoleManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int BG = 0xFF07070F;
    private static final int CARD = 0xFF12121C;
    private static final int BLUE = 0xFF0A84FF;

    private TextView overlayState;
    private TextView notifState;
    private TextView a11yState;
    private Button a11yButton;
    private Button startBtn;
    private TextView debug;
    private String debugBase = "measuring status bar…";
    private android.widget.SeekBar posSeek;
    private Switch lockSwitch;
    private final Button[] chargeBtns = new Button[5];
    private final Button[] notifBtns = new Button[3];
    private static final int[] CHARGE_STYLES = {0, 4, 1, 2, 3};
    private TextView posLabel;
    private final Handler h = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        measureInsets();
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        syncPosition();
    }

    private void syncPosition() {
        if (posSeek == null) return;
        int y = IslandService.controller != null ? IslandService.controller.currentY() : -1;
        int dpVal = y >= 0 ? Math.round(y / getResources().getDisplayMetrics().density) : 24;
        posSeek.setProgress(Math.max(0, Math.min(100, dpVal)));
        if (lockSwitch != null) {
            boolean locked = IslandService.controller != null
                    ? IslandService.controller.isPositionLocked()
                    : getSharedPreferences("island", MODE_PRIVATE).getBoolean("lockPos", true);
            lockSwitch.setChecked(locked);
        }
        posLabel.setText("top " + dpVal + " dp"
                + (IslandService.RUNNING ? "" : " · start the island first"));
    }

    private void measureInsets() {
        final View decor = getWindow().getDecorView();
        decor.post(new Runnable() {
            @Override public void run() {
                if (Build.VERSION.SDK_INT < 30) return;
                WindowInsets wi = decor.getRootWindowInsets();
                if (wi == null) return;
                int top = wi.getInsets(WindowInsets.Type.statusBars()).top;
                int cut = 0;
                DisplayCutout dc = wi.getDisplayCutout();
                if (dc != null) cut = dc.getSafeInsetTop();
                if (dc != null && !dc.getBoundingRects().isEmpty()) {
                    android.graphics.Rect r = dc.getBoundingRects().get(0);
                    getSharedPreferences("island", MODE_PRIVATE).edit()
                            .putInt("cutTop", r.top)
                            .putInt("cutBottom", r.bottom)
                            .putInt("cutCenterY", r.centerY())
                            .putInt("cutCenterX", r.centerX())
                            .apply();
                }
                int inset = Math.max(top, cut);
                if (inset > 0) {
                    getSharedPreferences("island", MODE_PRIVATE).edit().putInt("insetTop", inset).apply();
                    float d = getResources().getDisplayMetrics().density;
                    debugBase = "status bar " + Math.round(top / d) + " dp · cutout "
                            + Math.round(cut / d) + " dp";
                    updateDebug();
                }
            }
        });
    }

    /* ------------------------------------------------------------------- ui */

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(28));
        sv.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Island");
        title.setTextSize(34);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("A Dynamic Island for your Pixel 7 Pro. It floats over every app.");
        sub.setTextSize(13);
        sub.setTextColor(0xFF8B90A3);
        sub.setPadding(0, dp(6), 0, dp(18));
        root.addView(sub);

        root.addView(buildPreview());
        root.addView(spacer(dp(14)));

        LinearLayout perms = card();
        perms.addView(permRow("Display over other apps", "Draw the floating pill", "Allow",
                new View.OnClickListener() {
                    @Override public void onClick(View v) { requestOverlay(); }
                }));
        perms.addView(divider());
        perms.addView(permRow("Notification access", "Show incoming messages and media", "Enable",
                new View.OnClickListener() {
                    @Override public void onClick(View v) { requestNotifAccess(); }
                }));
        perms.addView(divider());
        perms.addView(permRow("Tap around the camera",
                "Accessibility service · lets the pill sit on the camera and still be pressed",
                "Enable", new View.OnClickListener() {
                    @Override public void onClick(View v) { openA11ySettings(); }
                }));
        root.addView(perms);
        root.addView(spacer(dp(16)));

        startBtn = new Button(this);
        startBtn.setAllCaps(false);
        startBtn.setTextSize(16);
        startBtn.setTextColor(Color.WHITE);
        startBtn.setTypeface(Typeface.DEFAULT_BOLD);
        startBtn.setStateListAnimator(null);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggle(); }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        root.addView(startBtn, blp);

        LinearLayout posCard = card();
        posCard.setPadding(dp(16), dp(12), dp(16), dp(6));
        LinearLayout posHead = new LinearLayout(this);
        posHead.setOrientation(LinearLayout.HORIZONTAL);
        posHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView posTitle = new TextView(this);
        posTitle.setText("Pill position");
        posTitle.setTextSize(14f);
        posTitle.setTextColor(Color.WHITE);
        posTitle.setTypeface(Typeface.DEFAULT_BOLD);
        posHead.addView(posTitle);
        posLabel = new TextView(this);
        posLabel.setTextSize(12f);
        posLabel.setTextColor(0xFF8B90A3);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        posHead.addView(posLabel, pl);
        posCard.addView(posHead);

        posSeek = new android.widget.SeekBar(this);
        posSeek.setMax(100);
        posSeek.setPadding(0, dp(4), 0, 0);
        posSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int value, boolean fromUser) {
                posLabel.setText("top " + value + " dp"
                        + (IslandService.RUNNING ? "" : " · start the island first"));
                if (fromUser && IslandService.controller != null) {
                    IslandService.controller.setY(dp(value));
                }
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar sb) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar sb) { }
        });
        posCard.addView(posSeek);

        lockSwitch = new Switch(this);
        lockSwitch.setText("Lock position");
        lockSwitch.setTextSize(13f);
        lockSwitch.setTextColor(0xFFB9BECD);
        lockSwitch.setPadding(0, dp(6), 0, 0);
        lockSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                if (IslandService.controller != null) {
                    IslandService.controller.setPositionLocked(checked);
                } else {
                    getSharedPreferences("island", MODE_PRIVATE).edit().putBoolean("lockPos", checked).apply();
                }
            }
        });
        posCard.addView(lockSwitch);
        LinearLayout.LayoutParams pclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pclp.topMargin = dp(10);
        root.addView(posCard, pclp);

        Button homeBtn = new Button(this);
        homeBtn.setAllCaps(false);
        homeBtn.setText("Set as default home screen");
        homeBtn.setTextSize(13.5f);
        homeBtn.setTextColor(0xFFFFFFFF);
        homeBtn.setStateListAnimator(null);
        GradientDrawable hbg = new GradientDrawable();
        hbg.setColor(0xFF1B1E26);
        hbg.setCornerRadius(dp(16));
        hbg.setStroke(dp(1), 0x22FFFFFF);
        homeBtn.setBackground(hbg);
        homeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                    } catch (Exception ignored) {}
                }
            }
        });
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(12);
        root.addView(homeBtn, hlp);

        Button previewBtn = new Button(this);
        previewBtn.setAllCaps(false);
        previewBtn.setText("Preview home screen");
        previewBtn.setTextSize(13.5f);
        previewBtn.setTextColor(0xFFB9C2D6);
        previewBtn.setStateListAnimator(null);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(0x0FFFFFFF);
        pbg.setCornerRadius(dp(16));
        previewBtn.setBackground(pbg);
        previewBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(MainActivity.this, LauncherActivity.class)); } catch (Exception ignored) {}
            }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(8);
        root.addView(previewBtn, plp);

        LinearLayout notifCard = card();
        notifCard.setPadding(dp(16), dp(12), dp(16), dp(14));
        TextView notifTitle = new TextView(this);
        notifTitle.setText("Island notifications");
        notifTitle.setTextSize(14f);
        notifTitle.setTextColor(Color.WHITE);
        notifTitle.setTypeface(Typeface.DEFAULT_BOLD);
        notifCard.addView(notifTitle);

        LinearLayout notifRow = new LinearLayout(this);
        notifRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] notifLabels = {"Tout", "Messages", "Off"};
        for (int i = 0; i < notifLabels.length; i++) {
            notifBtns[i] = choiceBtn(notifLabels[i], 0);
            final int value = i;
            notifBtns[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    getSharedPreferences("island", MODE_PRIVATE).edit()
                            .putInt("notifScope", value).apply();
                    if (IslandService.controller != null) {
                        IslandService.controller.notifyScopeChanged();
                    }
                    refreshNotifScope();
                }
            });
            notifRow.addView(notifBtns[i], weight());
        }
        LinearLayout.LayoutParams nrlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nrlp.topMargin = dp(8);
        notifCard.addView(notifRow, nrlp);

        LinearLayout.LayoutParams nclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nclp.topMargin = dp(12);
        root.addView(notifCard, nclp);

        LinearLayout chargeCard = card();
        chargeCard.setPadding(dp(16), dp(12), dp(16), dp(14));
        TextView chargeTitle = new TextView(this);
        chargeTitle.setText("Charging animation");
        chargeTitle.setTextSize(14f);
        chargeTitle.setTextColor(Color.WHITE);
        chargeTitle.setTypeface(Typeface.DEFAULT_BOLD);
        chargeCard.addView(chargeTitle);

        LinearLayout chargeRow = new LinearLayout(this);
        chargeRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] chargeLabels = {"Orb", "Ribbon", "Bolt", "Pill", "Off"};
        for (int i = 0; i < chargeLabels.length; i++) {
            chargeBtns[i] = choiceBtn(chargeLabels[i], CHARGE_STYLES[i]);
            chargeRow.addView(chargeBtns[i], weight());
        }
        LinearLayout.LayoutParams crlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        crlp.topMargin = dp(8);
        chargeCard.addView(chargeRow, crlp);

        LinearLayout.LayoutParams cclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cclp.topMargin = dp(12);
        root.addView(chargeCard, cclp);

        TextView tryLabel = new TextView(this);
        tryLabel.setText("TRY A LIVE ACTIVITY");
        tryLabel.setTextSize(11);
        tryLabel.setTextColor(0xFF6C7080);
        tryLabel.setLetterSpacing(0.12f);
        tryLabel.setPadding(0, dp(22), 0, dp(10));
        root.addView(tryLabel);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(demoBtn("Charging", "charge"), weight());
        row.addView(demoBtn("Message", "notif"), weight());
        row.addView(demoBtn("Timer", "timer"), weight());
        root.addView(row);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(demoBtn("Live", "live"), weight());
        row2.addView(demoBtn("Call", "call"), weight());
        row2.addView(demoBtn("Clear", "clear"), weight());
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2lp.topMargin = dp(8);
        root.addView(row2, r2lp);

        LinearLayout posRow = new LinearLayout(this);
        posRow.setOrientation(LinearLayout.HORIZONTAL);
        posRow.addView(posBtn("Around camera", "poscam"), weight());
        posRow.addView(posBtn("Below bar", "posbar"), weight());
        posRow.addView(posBtn("Auto", "resetpos"), weight());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(10);
        root.addView(posRow, rlp);

        LinearLayout edgeRow = new LinearLayout(this);
        edgeRow.setOrientation(LinearLayout.HORIZONTAL);
        edgeRow.addView(posBtn("Right edge", "posright"), weight());
        edgeRow.addView(posBtn("Left edge", "posleft"), weight());
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = dp(8);
        root.addView(edgeRow, elp);

        TextView foot = new TextView(this);
        foot.setText("Unlock the position to drag the pill; locked it stays around the camera. Tap to expand it; "
                + "long-press to come back here. Music appears by itself while something is playing.\n\nv4.9");
        foot.setTextSize(11.5f);
        foot.setTextColor(0xFF6C7080);
        foot.setLineSpacing(dp(3), 1f);
        foot.setPadding(0, dp(22), 0, 0);
        root.addView(foot);

        debug = new TextView(this);
        debug.setTextSize(10.5f);
        debug.setTextColor(0xFF4C5162);
        debug.setPadding(0, dp(8), 0, 0);
        debug.setText("measuring status bar…");
        root.addView(debug);

        return sv;
    }

    private View buildPreview() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1B1035, 0xFF0B0B16});
        g.setCornerRadius(dp(20));
        card.setBackground(g);
        card.setClipToOutline(true);

        FrameLayout area = new FrameLayout(this);
        area.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(96)));

        FrameLayout pill = new FrameLayout(this);
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(0xFF000000);
        pb.setCornerRadius(dp(18));
        pill.setBackground(pb);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(126), dp(36));
        plp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        plp.topMargin = dp(10);
        area.addView(pill, plp);

        View dot = new View(this);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(0xFF10141F);
        dot.setBackground(dg);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(10), dp(10));
        dlp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        dlp.rightMargin = dp(12);
        pill.addView(dot, dlp);

        TextView cap = new TextView(this);
        cap.setText("This is the idle pill. It stays out of the way until something happens.");
        cap.setTextSize(12);
        cap.setTextColor(0xFF9AA0B4);
        cap.setGravity(Gravity.CENTER);
        cap.setPadding(dp(16), 0, dp(16), dp(16));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.BOTTOM;
        area.addView(cap, clp);

        card.addView(area);
        return card;
    }

    private LinearLayout permRow(String name, String desc, String actionLabel, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(12), dp(14));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(14.5f);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setIncludeFontPadding(false);
        col.addView(t);

        TextView s = new TextView(this);
        s.setText(desc);
        s.setTextSize(11.5f);
        s.setTextColor(0xFF7C8194);
        s.setPadding(0, dp(3), 0, 0);
        col.addView(s);
        row.addView(col);

        if ("Display over other apps".equals(name)) overlayState = s;
        else if ("Tap around the camera".equals(name)) a11yState = s;
        else notifState = s;

        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(actionLabel);
        b.setTextSize(11.5f);
        b.setTextColor(Color.WHITE);
        b.setStateListAnimator(null);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF23232F);
        g.setCornerRadius(dp(10));
        b.setBackground(g);
        b.setPadding(dp(14), dp(6), dp(14), dp(6));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setOnClickListener(l);
        row.addView(b);
        if ("Tap around the camera".equals(name)) a11yButton = b;
        return row;
    }

    private Button choiceBtn(String label, final int style) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(11.5f);
        b.setTextColor(Color.WHITE);
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, dp(9), 0, dp(9));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x14FFFFFF);
        g.setCornerRadius(dp(12));
        b.setBackground(g);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                getSharedPreferences("island", MODE_PRIVATE).edit()
                        .putInt("chargeStyle", style).apply();
                if (IslandService.controller != null) {
                    IslandService.controller.setChargeStyle(style);
                    IslandService.controller.demo("charge");
                }
                refreshChargeStyle();
        refreshNotifScope();
            }
        });
        return b;
    }

    private void refreshNotifScope() {
        int scope = getSharedPreferences("island", MODE_PRIVATE).getInt("notifScope", 0);
        for (int i = 0; i < notifBtns.length; i++) {
            if (notifBtns[i] == null) continue;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(12));
            g.setColor(i == scope ? 0xFF0A84FF : 0x14FFFFFF);
            notifBtns[i].setBackground(g);
            notifBtns[i].setTextColor(i == scope ? Color.WHITE : 0xFFB9BECD);
        }
    }

    private void refreshChargeStyle() {
        int style = IslandService.controller != null
                ? IslandService.controller.chargeStyle()
                : getSharedPreferences("island", MODE_PRIVATE).getInt("chargeStyle", 0);
        for (int i = 0; i < chargeBtns.length; i++) {
            if (chargeBtns[i] == null) continue;
            boolean on = CHARGE_STYLES[i] == style;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(12));
            g.setColor(on ? 0xFF0A84FF : 0x14FFFFFF);
            chargeBtns[i].setBackground(g);
            chargeBtns[i].setTextColor(on ? Color.WHITE : 0xFFB9BECD);
        }
    }

    private Button posBtn(String label, final String kind) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(12f);
        b.setTextColor(0xFF9AA0B4);
        b.setStateListAnimator(null);
        GradientDrawable rg = new GradientDrawable();
        rg.setColor(0xFF15151F);
        rg.setCornerRadius(dp(12));
        b.setBackground(rg);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, dp(9), 0, dp(9));
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { demo(kind); }
        });
        return b;
    }

    private Button demoBtn(String label, final String kind) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(Color.WHITE);
        b.setStateListAnimator(null);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF171722);
        g.setCornerRadius(dp(14));
        b.setBackground(g);
        b.setPadding(0, dp(10), 0, dp(10));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { demo(kind); }
        });
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(18));
        c.setBackground(g);
        return c;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(0xFF1E1E2A);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        p.leftMargin = dp(16);
        v.setLayoutParams(p);
        return v;
    }

    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, h));
        return v;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(4);
        p.rightMargin = dp(4);
        return p;
    }

    /* --------------------------------------------------------------- state */

    private void updateDebug() {
        if (debug == null) return;
        int taps = getSharedPreferences("island", MODE_PRIVATE).getInt("taps", 0);
        int swipes = getSharedPreferences("island", MODE_PRIVATE).getInt("swipes", 0);
        debug.setText(debugBase + "  ·  taps " + taps + "  ·  swipes " + swipes);
    }

    private void refresh() {
        updateDebug();
        refreshChargeStyle();
        boolean overlay = Settings.canDrawOverlays(this);
        overlayState.setText(overlay ? "Allowed" : "Required · tap Allow");
        overlayState.setTextColor(overlay ? 0xFF30D158 : 0xFFFF9F0A);

        boolean a11y = IslandA11yService.instance != null;
        if (a11yState != null) {
            a11yState.setText(a11y ? "On · tap Settings to hide its floating shortcut"
                    : "Off · the pill sits just below the status bar");
            a11yState.setTextColor(a11y ? 0xFF30D158 : 0xFFFF9F0A);
        }
        if (a11yButton != null) a11yButton.setText(a11y ? "Settings" : "Enable");

        boolean notif = hasNotifAccess();
        notifState.setText(notif ? "Allowed" : "Optional · needed for messages and music");
        notifState.setTextColor(notif ? 0xFF30D158 : 0xFF7C8194);

        startBtn.setText(IslandService.RUNNING ? "Stop floating island" : "Start floating island");
        GradientDrawable g = new GradientDrawable();
        g.setColor(IslandService.RUNNING ? 0xFF2A1A1E : BLUE);
        g.setCornerRadius(dp(16));
        startBtn.setBackground(g);
    }

    private boolean hasNotifAccess() {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            return nm != null && nm.isNotificationListenerAccessGranted(
                    new ComponentName(this, NotifListener.class));
        } catch (Exception e) {
            return false;
        }
    }

    private void toggle() {
        if (IslandService.RUNNING) {
            startService(new Intent(this, IslandService.class).setAction(IslandService.ACTION_STOP));
            h.postDelayed(new Runnable() {
                @Override public void run() { refresh(); }
            }, 400);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Allow \"Display over other apps\" first");
            requestOverlay();
            return;
        }
        startForegroundService(new Intent(this, IslandService.class));
        h.postDelayed(new Runnable() {
            @Override public void run() { refresh(); }
        }, 600);
    }

    private void demo(String kind) {
        if (!Settings.canDrawOverlays(this)) {
            toast("Allow \"Display over other apps\" first");
            requestOverlay();
            return;
        }
        Intent i = new Intent(this, IslandService.class)
                .setAction(IslandService.ACTION_DEMO)
                .putExtra("kind", kind);
        if (IslandService.RUNNING) startService(i);
        else startForegroundService(i);
        h.postDelayed(new Runnable() {
            @Override public void run() { refresh(); }
        }, 600);
    }

    private void requestOverlay() {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    /** Straight to Island's accessibility page (where the shortcut can be switched off). */
    private void openA11ySettings() {
        String flat = new ComponentName(this, IslandA11yService.class).flattenToString();
        Intent i = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
        i.putExtra("android.provider.extra.COMPONENT_NAME", flat);
        i.putExtra("component_name", flat);
        try {
            startActivity(i);
            return;
        } catch (Exception ignored) { }
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception ignored) { }
    }

    private void requestNotifAccess() {
        String flat = new ComponentName(this, NotifListener.class).flattenToString();
        if (Build.VERSION.SDK_INT >= 30) {
            Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            i.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, flat);
            try {
                startActivity(i);
                return;
            } catch (Exception ignored) { }
        }
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

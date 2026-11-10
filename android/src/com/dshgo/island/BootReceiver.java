package com.dshgo.island;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/** Brings the island back after a reboot. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!Settings.canDrawOverlays(context)) return;
        try {
            Intent svc = new Intent(context, IslandService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc);
            else context.startService(svc);
        } catch (Exception ignored) {
            // Android 14+ may refuse a boot-time FGS start; the user can open the app instead
        }
    }
}

package com.dshgo.island;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: toggle the floating island without opening the app. */
public class IslandTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        update();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (IslandService.RUNNING) {
            startService(new Intent(this, IslandService.class).setAction(IslandService.ACTION_STOP));
        } else if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(i);
            return;
        } else {
            Intent svc = new Intent(this, IslandService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
            else startService(svc);
        }
        // the service flips RUNNING synchronously on the main thread
        update();
    }

    private void update() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(IslandService.RUNNING ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Island");
        try {
            t.setIcon(Icon.createWithResource(this, R.drawable.ic_stat));
        } catch (Exception ignored) { }
        t.updateTile();
    }
}

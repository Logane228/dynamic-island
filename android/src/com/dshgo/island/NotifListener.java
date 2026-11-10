package com.dshgo.island;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Bridges system notifications into the island; also the token used for media sessions. */
public class NotifListener extends NotificationListenerService {

    public static volatile NotifListener instance;

    @Override
    public void onListenerConnected() {
        instance = this;
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        IslandController c = IslandService.controller;
        if (c != null && sbn != null) c.onNotification(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        IslandController c = IslandService.controller;
        if (c != null && sbn != null) c.onNotificationRemoved(sbn.getKey());
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }
}

package com.dshgo.island;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Foreground service that owns the floating overlay. */
public class IslandService extends Service {

    public static final String ACTION_DEMO = "com.dshgo.island.DEMO";
    public static final String ACTION_STOP = "com.dshgo.island.STOP";

    public static volatile boolean RUNNING = false;
    public static IslandController controller;

    private static final String CHANNEL = "island";
    private static final int NOTIF_ID = 42;

    @Override
    public void onCreate() {
        super.onCreate();
        RUNNING = true;
        createChannel();
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
        controller = new IslandController(this);
        controller.attach();
        if (!controller.isAttached()) {
            // overlay permission missing/revoked: don't linger as a foreground service
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_STOP.equals(intent.getAction())) {
                stopSelf();
                return START_NOT_STICKY;
            }
            String kind = intent.getStringExtra("kind");
            if (kind != null && controller != null) controller.demo(kind);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (controller != null) controller.destroy();
        controller = null;
        RUNNING = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Floating island", NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        // Android requires a foreground-service notification. Keep it silent,
        // badge-less and collapsed so it stays out of the way in the shade.
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Island")
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(Notification.PRIORITY_MIN)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .build();
    }
}

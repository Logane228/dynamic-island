package com.dshgo.island;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Hosts the island window as TYPE_ACCESSIBILITY_OVERLAY.
 *
 * An application overlay is layered below the status bar, which owns every touch
 * in its 41 dp band, so a pill drawn around the camera can never be pressed.
 * Accessibility overlays sit above the status bar, so the pill can both wrap the
 * front camera and receive taps there.
 */
public class IslandA11yService extends AccessibilityService {

    public static volatile IslandA11yService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        IslandController c = IslandService.controller;
        if (c != null) c.useAccessibilityOverlay(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // no window content needed; the service only exists to own the overlay
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        IslandController c = IslandService.controller;
        if (c != null) c.useAppOverlay();
        return super.onUnbind(intent);
    }
}

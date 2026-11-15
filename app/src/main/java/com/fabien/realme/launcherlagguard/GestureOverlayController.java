package com.fabien.realme.launcherlagguard;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional replacement gesture targets for devices kept in three-button
 * navigation mode. These are accessibility overlays, NOT native Android
 * system-navigation gestures. No Shizuku/root/overlay permission is used.
 *
 * The touch targets intentionally occupy only narrow screen regions. The
 * service removes them on the lock screen and while doing its Recents cleanup.
 */
final class GestureOverlayController {
    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    private static final int BOTTOM = 2;

    private final AccessibilityService service;
    private final WindowManager windows;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<View> targets = new ArrayList<>();

    private boolean suspended;
    private boolean visible;
    private boolean unavailable;

    GestureOverlayController(AccessibilityService service) {
        this.service = service;
        windows = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
    }

    void setSuspended(boolean value) {
        suspended = value;
        refresh();
    }

    void refresh() {
        boolean enabled = GuardState.gesturesEnabled(service);
        KeyguardManager guard = (KeyguardManager) service.getSystemService(Context.KEYGUARD_SERVICE);
        boolean locked = guard != null && guard.isDeviceLocked();
        boolean wanted = enabled && !locked && !suspended && !unavailable;

        if (wanted == visible) {
            return;
        }
        if (!wanted) {
            removeTargets();
            return;
        }

        try {
            addTargets();
            visible = true;
        } catch (RuntimeException error) {
            removeTargets();
            unavailable = true;
            GuardState.setGestureStatus(service,
                    "Unavailable: " + error.getClass().getSimpleName());
        }
    }

    void retry() {
        unavailable = false;
        refresh();
    }

    void close() {
        removeTargets();
        handler.removeCallbacksAndMessages(null);
    }

    private void addTargets() {
        if (windows == null) {
            throw new IllegalStateException("Window manager is unavailable");
        }
        DisplayMetrics display = service.getResources().getDisplayMetrics();
        int edgeHeight = Math.round(display.heightPixels * 0.55f);

        addTarget(LEFT, dp(20), edgeHeight, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        addTarget(RIGHT, dp(20), edgeHeight, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        addTarget(BOTTOM, Math.round(display.widthPixels * 0.58f), dp(34),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

        GuardState.setGestureStatus(service, "Active: experimental gesture targets");
    }

    private void addTarget(int target, int width, int height, int gravity) {
        View view = new View(service);
        // Low-contrast touch zones: visible for testing, not meant as controls.
        view.setBackgroundColor(target == BOTTOM ? 0x243F51B5 : 0x103F51B5);
        view.setContentDescription(target == BOTTOM
                ? "Swipe up for Home, then hold for Recents"
                : "Swipe toward screen center for Back");
        view.setOnTouchListener(new GestureTouchListener(target));

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags, PixelFormat.TRANSLUCENT);
        params.gravity = gravity;
        params.setTitle("Launcher Lag Guard gesture target");
        windows.addView(view, params);
        targets.add(view);
    }

    private void removeTargets() {
        for (View target : targets) {
            try {
                windows.removeViewImmediate(target);
            } catch (RuntimeException ignored) {
                // Android may already have removed overlays on service disconnect.
            }
        }
        targets.clear();
        visible = false;
    }

    private int dp(float value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }

    private final class GestureTouchListener implements View.OnTouchListener {
        private final int target;
        private float startX;
        private float startY;
        private float lastY;
        private boolean fingerDown;
        private boolean recentsFired;
        private boolean holdScheduled;
        private Runnable holdAction;

        GestureTouchListener(int target) {
            this.target = target;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    cancelHold();
                    startX = event.getRawX();
                    startY = event.getRawY();
                    lastY = startY;
                    fingerDown = true;
                    recentsFired = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (!fingerDown) {
                        return true;
                    }
                    lastY = event.getRawY();
                    float movedUp = startY - lastY;
                    float movedAcross = Math.abs(event.getRawX() - startX);
                    if (target == BOTTOM && movedUp > dp(55)
                            && movedUp > movedAcross * 1.2f && !holdScheduled) {
                        holdScheduled = true;
                        holdAction = () -> {
                            if (fingerDown && startY - lastY > dp(55)) {
                                recentsFired = service.performGlobalAction(
                                        AccessibilityService.GLOBAL_ACTION_RECENTS);
                            }
                        };
                        handler.postDelayed(holdAction, 400);
                    } else if (target == BOTTOM && movedUp < dp(35)) {
                        cancelHold();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    float dx = event.getRawX() - startX;
                    float dy = event.getRawY() - startY;
                    fingerDown = false;
                    cancelHold();

                    if (recentsFired) {
                        return true;
                    }
                    if (target == BOTTOM && dy < -dp(55)
                            && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                    } else if (target == LEFT && dx > dp(48)
                            && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                    } else if (target == RIGHT && dx < -dp(48)
                            && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    fingerDown = false;
                    cancelHold();
                    return true;

                default:
                    return true;
            }
        }

        private void cancelHold() {
            if (holdAction != null) {
                handler.removeCallbacks(holdAction);
                holdAction = null;
            }
            holdScheduled = false;
        }
    }
}

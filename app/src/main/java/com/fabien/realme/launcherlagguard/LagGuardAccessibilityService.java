package com.fabien.realme.launcherlagguard;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Deque;

@SuppressWarnings("deprecation") // Keep node pooling and package lookups compatible with Android 8-12.
public final class LagGuardAccessibilityService extends AccessibilityService {
    private static final String SYSTEM_LAUNCHER_PACKAGE = "com.android.launcher";
    private static final ComponentName SYSTEM_LAUNCHER = new ComponentName(
            SYSTEM_LAUNCHER_PACKAGE, "com.android.launcher.Launcher");
    private static LagGuardAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable maybeRunTask = this::maybeRun;
    private boolean running;
    private int scanAttempts;
    private GestureOverlayController gestureOverlays;

    static void refreshGestureControls() {
        LagGuardAccessibilityService current = instance;
        if (current != null) {
            current.handler.post(() -> {
                if (current.gestureOverlays != null) {
                    current.gestureOverlays.retry();
                }
            });
        }
    }

    static void kick() {
        LagGuardAccessibilityService current = instance;
        if (current != null) {
            current.handler.post(current.maybeRunTask);
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        gestureOverlays = new GestureOverlayController(this);
        gestureOverlays.refresh();
        handler.postDelayed(maybeRunTask, 1500);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (gestureOverlays != null) {
            gestureOverlays.refresh();
        }
        if (GuardState.isPending(this) && !running) {
            handler.removeCallbacks(maybeRunTask);
            handler.postDelayed(maybeRunTask, 500);
        }
    }

    @Override
    public void onInterrupt() {
        GuardState.report(this, "Accessibility service interrupted; fix remains pending");
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        handler.removeCallbacksAndMessages(null);
        if (gestureOverlays != null) {
            gestureOverlays.close();
            gestureOverlays = null;
        }
        super.onDestroy();
    }

    private void maybeRun() {
        if (running || !GuardState.isPending(this)) {
            return;
        }

        UserManager users = (UserManager) getSystemService(USER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if ((users != null && !users.isUserUnlocked())
                || (keyguard != null && keyguard.isDeviceLocked())) {
            GuardState.report(this, "Pending: waiting for phone unlock");
            return;
        }

        running = true;
        if (gestureOverlays != null) {
            gestureOverlays.setSuspended(true);
        }
        scanAttempts = 0;
        GuardState.report(this, "Running: waiting for the launcher UI to settle");
        handler.postDelayed(this::startLauncher, 5000);
    }

    private void startLauncher() {
        GuardState.report(this, "Running: opening System Launcher");
        try {
            Intent launcher = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setComponent(SYSTEM_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launcher);
        } catch (RuntimeException error) {
            fail("Could not open System Launcher: " + error.getClass().getSimpleName());
            return;
        }

        handler.postDelayed(this::openRecents, 1800);
    }

    private void openRecents() {
        if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) {
            fail("Android rejected the Recents action");
            return;
        }
        GuardState.report(this, "Running: finding System Launcher card");
        handler.postDelayed(this::findAndDismissCard, 2200);
    }

    private void findAndDismissCard() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        Rect bounds = findLauncherCardBounds(root);
        if (root != null) {
            root.recycle();
        }

        if (bounds != null && isSafeCardBounds(bounds)) {
            swipeCardUp(bounds);
            return;
        }

        scanAttempts++;
        if (scanAttempts == 4) {
            swipeRecentsHorizontally();
        } else if (scanAttempts < 12) {
            handler.postDelayed(this::findAndDismissCard, 750);
        } else {
            fail("System Launcher card not found; no app was dismissed");
        }
    }

    @SuppressWarnings("deprecation") // Node pooling still applies on supported Android 8-12 devices.
    private Rect findLauncherCardBounds(AccessibilityNodeInfo root) {
        if (root == null) {
            return null;
        }

        String launcherLabel = getSystemLauncherLabel();
        Deque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(AccessibilityNodeInfo.obtain(root));
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.removeFirst();
            CharSequence text = node.getText();
            CharSequence description = node.getContentDescription();
            boolean labelMatches = launcherLabel.contentEquals(text == null ? "" : text)
                    || launcherLabel.contentEquals(description == null ? "" : description)
                    || "System Launcher".contentEquals(text == null ? "" : text)
                    || "System Launcher".contentEquals(description == null ? "" : description);
            if (labelMatches) {
                AccessibilityNodeInfo card = AccessibilityNodeInfo.obtain(node);
                while (!card.isClickable()) {
                    AccessibilityNodeInfo parent = card.getParent();
                    if (parent == null) {
                        break;
                    }
                    card.recycle();
                    card = parent;
                }
                Rect bounds = new Rect();
                card.getBoundsInScreen(bounds);
                card.recycle();
                node.recycle();
                recycleQueue(queue);
                return bounds;
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    queue.addLast(child);
                }
            }
            node.recycle();
        }
        return null;
    }

    private String getSystemLauncherLabel() {
        try {
            PackageManager packages = getPackageManager();
            ApplicationInfo info = packages.getApplicationInfo(SYSTEM_LAUNCHER_PACKAGE, 0);
            CharSequence label = packages.getApplicationLabel(info);
            if (!TextUtils.isEmpty(label)) {
                return label.toString();
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // The English fallback below matches the tested Realme GT 6 build.
        }
        return "System Launcher";
    }

    private boolean isSafeCardBounds(Rect bounds) {
        DisplayMetrics display = getResources().getDisplayMetrics();
        return bounds.width() >= display.widthPixels / 12
                && bounds.height() >= display.heightPixels / 4
                && bounds.centerX() >= 0
                && bounds.centerX() <= display.widthPixels
                && bounds.centerY() >= 0
                && bounds.centerY() <= display.heightPixels;
    }

    private void swipeCardUp(Rect bounds) {
        float x = bounds.centerX();
        float startY = bounds.centerY();
        float endY = Math.max(0, bounds.top / 4f);
        dispatchSwipe(x, startY, x, endY, 220, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                handler.postDelayed(() -> {
                    performGlobalAction(GLOBAL_ACTION_HOME);
                    GuardState.finish(LagGuardAccessibilityService.this,
                            "Success: System Launcher card dismissed");
                    running = false;
                    if (gestureOverlays != null) {
                        gestureOverlays.setSuspended(false);
                    }
                }, 350);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                fail("Launcher-card swipe was cancelled");
            }
        });
    }

    private void swipeRecentsHorizontally() {
        DisplayMetrics display = getResources().getDisplayMetrics();
        float y = display.heightPixels * 0.4f;
        dispatchSwipe(display.widthPixels * 0.85f, y,
                display.widthPixels * 0.15f, y, 300,
                new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        handler.postDelayed(LagGuardAccessibilityService.this::findAndDismissCard, 500);
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        handler.postDelayed(LagGuardAccessibilityService.this::findAndDismissCard, 500);
                    }
                });
    }

    private void dispatchSwipe(float startX, float startY, float endX, float endY,
                               long duration, GestureResultCallback callback) {
        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                .build();
        if (!dispatchGesture(gesture, callback, null)) {
            fail("Android rejected the accessibility gesture");
        }
    }

    private void fail(String message) {
        performGlobalAction(GLOBAL_ACTION_HOME);
        GuardState.finish(this, "Failed safely: " + message);
        running = false;
        if (gestureOverlays != null) {
            gestureOverlays.setSuspended(false);
        }
    }

    @SuppressWarnings("deprecation")
    private static void recycleQueue(Deque<AccessibilityNodeInfo> nodes) {
        while (!nodes.isEmpty()) {
            nodes.removeFirst().recycle();
        }
    }
}

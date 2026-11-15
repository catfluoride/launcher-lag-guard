package com.fabien.realme.launcherlagguard;

import android.content.Context;
import android.content.SharedPreferences;

final class GuardState {
    private static final String PREFS = "launcher_lag_guard";
    private static final String PENDING = "pending";
    private static final String LAST_RESULT = "last_result";
    private static final String GESTURES = "gesture_controls_enabled";
    private static final String GESTURE_STATUS = "gesture_controls_status";

    private GuardState() {}

    static SharedPreferences preferences(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void request(Context context, String reason) {
        preferences(context).edit()
                .putBoolean(PENDING, true)
                .putString(LAST_RESULT, "Pending: " + reason)
                .apply();
    }

    static boolean isPending(Context context) {
        return preferences(context).getBoolean(PENDING, false);
    }

    static void finish(Context context, String result) {
        preferences(context).edit()
                .putBoolean(PENDING, false)
                .putString(LAST_RESULT, result)
                .apply();
    }

    static void report(Context context, String result) {
        preferences(context).edit().putString(LAST_RESULT, result).apply();
    }

    static boolean gesturesEnabled(Context context) {
        return preferences(context).getBoolean(GESTURES, false);
    }

    static void setGesturesEnabled(Context context, boolean enabled) {
        preferences(context).edit()
                .putBoolean(GESTURES, enabled)
                .putString(GESTURE_STATUS, enabled ? "Enabled (waiting for service)" : "Disabled")
                .apply();
    }

    static void setGestureStatus(Context context, String status) {
        preferences(context).edit().putString(GESTURE_STATUS, status).apply();
    }

    static String gestureStatus(Context context) {
        return preferences(context).getString(GESTURE_STATUS, "Disabled");
    }

    static String lastResult(Context context) {
        return preferences(context).getString(LAST_RESULT, "Not run yet");
    }
}

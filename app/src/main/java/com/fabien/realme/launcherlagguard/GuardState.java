package com.fabien.realme.launcherlagguard;

import android.content.Context;
import android.content.SharedPreferences;

final class GuardState {
    private static final String PREFS = "launcher_lag_guard";
    private static final String PENDING = "pending";
    private static final String LAST_RESULT = "last_result";

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

    static String lastResult(Context context) {
        return preferences(context).getString(LAST_RESULT, "Not run yet");
    }
}

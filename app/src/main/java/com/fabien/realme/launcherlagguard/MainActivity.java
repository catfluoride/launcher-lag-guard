package com.fabien.realme.launcherlagguard;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public final class MainActivity extends Activity {
    private TextView status;
    private TextView gestureStatus;
    private Button gestureButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private View createContent() {
        int padding = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Launcher Lag Guard");
        title.setTextSize(26);
        title.setTextColor(Color.BLACK);
        root.addView(title, matchWrap());

        TextView explanation = new TextView(this);
        explanation.setText("After each reboot and first unlock, this service opens Recents, finds only the System Launcher card, dismisses it, and returns Home. It never disables the launcher package.");
        explanation.setTextSize(16);
        explanation.setTextColor(Color.DKGRAY);
        explanation.setPadding(0, dp(20), 0, dp(20));
        root.addView(explanation, matchWrap());

        status = new TextView(this);
        status.setTextSize(16);
        status.setTextColor(Color.BLACK);
        status.setPadding(0, 0, 0, dp(20));
        root.addView(status, matchWrap());

        Button settingsButton = new Button(this);
        settingsButton.setText("Open Accessibility settings");
        settingsButton.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        root.addView(settingsButton, matchWrap());

        Button runButton = new Button(this);
        runButton.setText("Run fix now");
        runButton.setOnClickListener(v -> runNow());
        root.addView(runButton, matchWrap());

        TextView gestureHeading = new TextView(this);
        gestureHeading.setText("Experimental replacement gestures");
        gestureHeading.setTextSize(20);
        gestureHeading.setTextColor(Color.BLACK);
        gestureHeading.setPadding(0, dp(24), 0, dp(8));
        root.addView(gestureHeading, matchWrap());

        TextView instructions = new TextView(this);
        instructions.setText("Use OxygenOS THREE-BUTTON mode. Enable the gesture targets below. "
                + "Test them while the navigation buttons are still visible. "
                + "Swipe up from the faint bottom strip for Home; swipe up and hold for Recents; "
                + "swipe inward from either faint side strip for Back. "
                + "The strips may intercept app content near the edges.");
        instructions.setTextSize(15);
        instructions.setTextColor(Color.DKGRAY);
        root.addView(instructions, matchWrap());

        gestureStatus = new TextView(this);
        gestureStatus.setTextSize(15);
        gestureStatus.setTextColor(Color.BLACK);
        gestureStatus.setPadding(0, dp(12), 0, dp(8));
        root.addView(gestureStatus, matchWrap());

        gestureButton = new Button(this);
        gestureButton.setOnClickListener(v -> {
            boolean next = !GuardState.gesturesEnabled(this);
            GuardState.setGesturesEnabled(this, next);
            LagGuardAccessibilityService.refreshGestureControls();
            refreshStatus();
        });
        root.addView(gestureButton, matchWrap());

        TextView advanced = new TextView(this);
        advanced.setText("OPTIONAL, ONLY AFTER TESTING:\n"
                + "Hide the system buttons with a computer (USB debugging):\n"
                + "adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural\n\n"
                + "Restore buttons using:\n"
                + "adb shell cmd overlay disable com.android.internal.systemui.navbar.gestural\n\n"
                + "These commands require ADB or an authorised Shizuku shell and may reset "
                + "on restart. If navigating becomes difficult, restore the buttons via ADB. "
                + "This app cannot run either command without system privileges.");
        advanced.setTextSize(14);
        advanced.setTextColor(Color.DKGRAY);
        advanced.setTextIsSelectable(true);
        advanced.setPadding(0, dp(20), 0, dp(10));
        root.addView(advanced, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private void runNow() {
        if (!isServiceEnabled()) {
            Toast.makeText(this, "Enable Launcher Lag Guard in Accessibility first", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        GuardState.request(this, "manual run");
        LagGuardAccessibilityService.kick();
        Toast.makeText(this, "Fix requested", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void refreshStatus() {
        String enabled = isServiceEnabled() ? "enabled" : "disabled";
        status.setText("Accessibility service: " + enabled + "\nLast status: " + GuardState.lastResult(this));
        if (gestureStatus != null && gestureButton != null) {
            boolean gestures = GuardState.gesturesEnabled(this);
            gestureStatus.setText("Gestures: " + GuardState.gestureStatus(this));
            gestureButton.setText(gestures
                    ? "Disable replacement gestures" : "Enable replacement gestures");
        }
    }

    private boolean isServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) {
            return false;
        }
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        String packageName = getPackageName();
        for (AccessibilityServiceInfo service : services) {
            if (service.getResolveInfo() != null
                    && packageName.equals(service.getResolveInfo().serviceInfo.packageName)) {
                return true;
            }
        }
        return false;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

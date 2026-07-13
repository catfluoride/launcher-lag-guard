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
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public final class MainActivity extends Activity {
    private TextView status;

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

        return root;
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

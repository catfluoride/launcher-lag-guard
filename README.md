# Launcher Lag Guard

Launcher Lag Guard is an experimental, rootless workaround for the Home-button
delay seen with third-party launchers on some Realme, Oppo and OnePlus phones.

On affected firmware, the Oplus System Launcher remains involved in
Quickstep/Recents even when Smart Launcher, Nova, Niagara or another launcher
owns the Android HOME role. After a reboot, its normal Launcher activity can
remain as a recent task and delay the return to the selected Home screen.

Launcher Lag Guard waits for the first unlock after boot, opens the stock
launcher as a temporary task, opens Recents, finds the exact System Launcher
card by its accessibility label, dismisses that card and returns Home.

This is a workaround for a firmware integration bug. It is not a replacement
launcher or a firmware patch.

## Status

The current code is an early test version. It has passed manual runs, app
upgrades and cold-reboot tests on this configuration:

| Item | Tested value |
| --- | --- |
| Phone | Realme GT 6 RMX3851 (Europe) |
| Android | 16 |
| realmeUI | `RMX3851_16.0.5.1010(EX01)` |
| Oplus System Launcher | 16.4.27 |
| Third-party launcher | Smart Launcher 6.6 build 012 |
| Navigation | Three-button navigation |
| Launcher Lag Guard | 1.2 |

Other phones and gesture navigation have not been validated yet. Reports from
Realme, Oppo and OnePlus users are welcome through the device-test issue form.

## Safety and privacy

Accessibility is a powerful Android capability, so the service must be enabled
manually after installation. The app uses it for three operations: open
Recents, inspect the active Recents tree, and send the dismiss gesture.

The implementation has several deliberate limits:

- It checks the localized System Launcher application label and card bounds
  before sending a vertical gesture.
- If the expected card cannot be identified, it returns Home without
  dismissing another application.
- It does not disable, suspend or force-stop `com.android.launcher` because
  that package also provides Quickstep/Recents.
- It does not change secure settings, request root, use Shizuku, or require a
  computer after setup.
- It has no Internet permission and does not collect or send data.
- Uninstalling the app removes the workaround completely.

The sequence is visible for a few seconds after the first unlock: System
Launcher opens, Recents appears briefly, then the selected Home screen returns.

## Build

Requirements:

- JDK 17
- Android SDK 34

Build and run Android lint:

```bash
./gradlew --no-daemon lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
Generated APKs and Gradle build directories are ignored by Git.

## Install and enable

Install a locally built APK:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then on the phone:

1. Open **Launcher Lag Guard**.
2. Tap **Open Accessibility settings**.
3. Select **Launcher Lag Guard**, review Android's disclosure and enable it.
4. Return to the app and use **Run fix now** for the first test.
5. Reboot, unlock once and confirm that the Home-button delay is gone.

Some Realme/Oppo/OnePlus builds may also require auto-launch permission or
unrestricted battery use. Start without these exceptions and add them only if
the service does not restart after a reboot.

## Remove or recover

Disable Launcher Lag Guard in Android Accessibility settings, or uninstall it:

```bash
adb uninstall com.fabien.realme.launcherlagguard
```

No stock-package state needs to be restored.

## Related public reports

- [XDA: Third-party launchers return-to-home delay](https://xdaforums.com/t/third-party-system-launchers-return-to-home-screen-delay-gesture-navigation-only.4655464/)
- [Oppo community: ColorOS custom launcher lag](https://community.oppo.com/thread/2054961285367332870)
- [Home Launcher Redirect](https://github.com/xieincz/home_launcher_redirect), a root/LSPosed alternative tested on ColorOS 16.0.1

## Licence

Licensed under the [Apache License 2.0](LICENSE).

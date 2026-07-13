# Contributing

Device reports are especially useful because the problem depends on firmware,
System Launcher and navigation mode.

Before opening a code change:

1. Open a device-test issue with the phone model, firmware, System Launcher
   version, third-party launcher and navigation mode.
2. Confirm whether the manual **Run fix now** action works.
3. Confirm whether the service runs after a cold reboot and first unlock.
4. Mention any visible side effect or failed-safe status shown by the app.

For code changes, run:

```bash
./gradlew --no-daemon lintDebug assembleDebug
```

Do not commit APKs, signing keys, Gradle caches or build directories.

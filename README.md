# NV-FIXER PRO v3.0 Simulator (native Kotlin / Jetpack Compose)

Native Android port of the HTML simulator. No WebView.
Simulation only; it does not interact with any real device.

Files (app/src/main/java/com/nvfixer/simulator):
- Logic.kt          profiles, Luhn check, IMEI generator, beep sound
- MainViewModel.kt  state + the repair simulation steps + saved history
- MainActivity.kt   Compose UI

## Build the APK on GitHub
1. Push the contents of this folder to a GitHub repo (branch `main`).
2. Open the **Actions** tab -> **Build APK** run.
3. Download the **NV-FIXER-PRO-debug-apk** artifact, unzip, install the APK on your phone.

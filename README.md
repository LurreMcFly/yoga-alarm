# Yoga Alarm

Native Android alarm clock that asks the user to complete a short yoga or mobility
routine to stop the alarm. Camera frames are processed on-device; alarms and settings
are stored locally.

## Current Android scope

- Alarm creation, editing, weekday recurrence, sounds, vibration, and limited snooze.
- Exact scheduling, lock-screen presentation, foreground alarm audio, and rescheduling
  after reboot or clock changes.
- User-started front-camera preview, on-device MediaPipe landmarks, and pose guides.
- Eight poses with partial-body-friendly scoring, stabilized detection, hold timers,
  transitions, and routine-wide audio fading.
- Free/Pro customization, paywall, purchase/restore paths, and debug-only Pro activation.

Billing remains a client-side prototype; the approved production catalog and purchase
verification still require implementation. See [the project handoff](knowledge.md),
[billing setup](docs/play-billing-setup.md), and the
[Android reliability checklist](docs/android-reliability-checklist.md) for current
decisions and remaining release work.

The original camera spike is recorded in the
[historical prototype baseline](docs/prototype-baseline.md).

## Browser tester

The browser design harness lives in `web-tester/`. See the
[project handoff](knowledge.md#8-browser-tester-and-privacy-site) for its role and the
separate privacy website work.

```bash
cd web-tester
pnpm run dev
```

Open `http://localhost:3000/`. Changes to the browser tester update automatically while the development server is running.

The browser tester does not validate Android alarm scheduling, lock-screen behavior, or reboot reliability.

Android now includes the approved onboarding on first use, with replay through **Settings → How to use Yoga Alarm**. It guides time, alarm name, repeat days, sound and routine setup using the real controls, then offers quiet five-second pose practice and a full-routine test before saving. The native flow still uses the normal alarm scheduling and permissions checks.

The browser tester retains the onboarding design preview. **Preview without camera** lets you test practice with simulated pose detection. Saved preview alarms stay in this browser.

The alarm setup preview was checked against the current Android source on 2026-10-17: Monday-first days, local-hour draft defaults, sound/Snooze/Stop button controls, the full-routine test above poses, and matching free/Pro catalogs. Sound previews use the Android audio files. OS permissions, scheduled/ringing alarms, real streak history, billing and Android camera behavior still require native testing; the browser streak is a layout sample and Pro purchases are unavailable here. Android references: `model/AlarmConfig.kt`, `ui/AlarmEditorScreen.kt`, and `ui/AlarmHomeScreen.kt` under `app/src/main/java/com/lurremcfly/yogaalarm/`.

## Run

Use JDK 17 and Android SDK 36. The app supports Android 8.0/API 26 and later.
Open the project in Android Studio, select a physical Android device, and run the `app`
configuration.

Command-line build, with `JAVA_HOME` pointing to your JDK 17 installation:

```bash
./gradlew :app:assembleDebug
```

Install an existing build:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Physical-device checks

Use the [Android reliability checklist](docs/android-reliability-checklist.md) for alarm
delivery, camera recovery, audio, and device coverage. Samsung Galaxy S25 is the primary
test device; include other manufacturers and an older supported Android version.

Check all eight poses at a distance where the phone is readable, in bright and dim
bedroom lighting, with loose clothing, side-facing poses, and partially visible bodies.
Record recognition stability and visible overlay lag. Also verify that backgrounding
and reopening restores the camera, and that ten minutes of continuous use does not
freeze or crash.

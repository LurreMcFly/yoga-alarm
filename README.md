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

In local development, **Settings → Pro preview** unlocks the Android Pro setup UI: all eight poses, sound selection, hold durations, and routines of 1–10 poses, without a purchase. The browser still tracks only Mountain, Warrior II, and Tree; camera testing of other poses stays in Android. The sound previews use the matching Android audio files.

For a clean recorded walkthrough, open `http://localhost:3000/?demo=pro`. This development-only profile starts with Pro enabled, one saved alarm, and a sample six-day streak. It skips onboarding and shows a blue touch indicator that glides between targets and pulses before actions. Button actions wait for the indicator to arrive, and sheets animate in and out for presentation. Its alarms persist separately from the regular browser tester. It adds no explanatory text to the app screens.

Add `&reset=1` to start that demo profile again with just its initial alarm. The reset applies only to demo alarms and removes itself from the URL so subsequent reloads preserve changes.

Tap the streak card to preview inline history: it expands above the alarms, shows personal best, and offers equally sized **1 month**, **3 months**, and **1 year** views, without day numbers in the monthly grid. Tap the header again to collapse it. The Pro demo uses sample yoga/rest days; the regular tester keeps an empty history. Android implements this UI with real retained activity and persistent personal best; iOS parity is tracked in `IOS_SYNC.md` (SYNC-022).

Android offers a styled tour invitation on first use, with an initial permission-setup page and replay through **Settings → How to use Yoga Alarm**. Replays skip the permission introduction; missing permissions remain available in the home checklist. The tour guides time, alarm name, repeat days, sound and routine setup using the real controls, with smooth step transitions and a slow, continuous routine preview. The sound sheet closes and the editor scrolls to the poses before the routine introduction appears. The tour then offers quiet five-second pose practice and goes directly to saving. Full-routine testing remains available in the editor and saved-alarm summary. The native flow still uses the normal alarm scheduling and permissions checks.

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

# iOS Sync Queue

Android is the reference implementation for the iOS app.

This file tracks Android changes that affect cross-platform app behavior and therefore may need to be replicated in the iOS repository.

## Pending

### SYNC-004 — Completion screen stays awake for ten seconds

Status: pending
Date: 2026-09-16
Android commit: <pending>

Changes:
- After finishing a yoga routine, keep the completion screen awake for ten seconds. Apply this to real alarms and test routines.
- Stop alarm sound, vibration, and camera processing immediately; record a real alarm completion once, without waiting for the ten seconds.
- For a locked phone, retain the alarm's lock-screen presentation during this grace period. Afterwards restore normal screen timeout and lock-screen behavior; do not force sleep.
- Leaving early cancels the grace period. Screen recreation must preserve the original deadline, and a previous routine's timer must not affect a new alarm.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/MainActivity.kt

iOS notes:
- Use native idle-timer and supported alarm presentation behavior to keep the completed routine visible briefly.

### SYNC-003 — First-launch permission setup popup

Status: pending
Date: 2026-09-16
Android commit: <pending>

Changes:
- On the first normal home-screen launch, show "Set up your yoga alarm" with explanations and individual access buttons. Never overlay a ringing alarm.
- Explain camera access for recognizing poses, notifications for alarm alerts and controls, and applicable platform access for timely and lock-screen alarms.
- Request access only after the user taps an action. Reflect current grants and refresh after returning from system settings; granted rows are noninteractive.
- Allow "Not now" ("Done" when ready). Persist that setup has been shown, retain the popup through screen recreation, and keep missing-access reminders on the home screen after dismissal.
- Existing installs without the setup flag also see the popup once on a normal home-screen visit.
- Show only applicable platform permissions; reuse existing denial and settings recovery behavior.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/MainActivity.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmApp.kt

iOS notes:
- Explain and request only equivalent supported iOS permissions; do not copy Android settings categories literally.

### SYNC-002 — Launcher icon safe-area spacing

Status: pending
Date: 2026-09-11
Android commit: <pending>

Changes:
- The launcher icon foreground now has a small uniform inset so the yoga pose's hands leave visible space against the left and right edges instead of reaching the adaptive-icon crop.
- Preserve the existing artwork, colors, silhouette, and proportions; only match the slightly roomier framing.

Relevant Android files:
- app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
- app/src/main/res/drawable/ic_launcher_foreground_inset.xml
- app/src/main/res/drawable-nodpi/ic_launcher_foreground.webp

iOS notes:
- Apply equivalent safe-area spacing to the iOS app icon source artwork before generating icon sizes.

### SYNC-001 — Yoga-only dismissal, emergency stops, and wake-up streak

Status: pending
Date: 2026-09-11
Android commit: <pending>

Changes:
- Alarm editing has three primary rows in this order: Sound, Snooze, and "Stop button." The Stop button option defaults to on for new and existing alarms, uses the supporting text "Allow manual alarm stop," and shows the manual Stop action when enabled. Turning it off hides the normal Stop action.
- Vibration remains independently configurable but has moved into the Sound sheet. Sound-only, vibration-only, both, and neither are supported.
- When "Stop button" is off for a fired alarm, the normal Stop action is hidden and Android Back remains blocked. Snooze remains available. Test routines always retain normal navigation.
- A subtle Emergency stop remains available. It warns that the streak will end and requires a 5-second hold while one of three monthly passes remains. After all passes are used, it requires a 15-second hold but is never blocked or paywalled.
- Camera permission denial or a camera failure provides an immediate technical Stop action. Technical exits do not consume a pass or break the streak.
- The app tracks one global daily yoga streak. Completing at least one genuinely fired alarm through yoga starts or extends it; multiple completions on one day count once. Snoozing preserves it. A normal or emergency stop breaks it. Test routines do not affect it.
- The streak appears on the alarm home screen, the completed-routine screen, and in the emergency-stop warning.
- With no streak, show "Start your yoga streak" and "Finish your yoga routine when your alarm rings to start your streak." Test routines still do not start a streak.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/model/AlarmConfig.kt
- app/src/main/java/com/lurremcfly/yogaalarm/model/WakeProgress.kt
- app/src/main/java/com/lurremcfly/yogaalarm/data/AlarmStore.kt
- app/src/main/java/com/lurremcfly/yogaalarm/data/WakeProgressStore.kt
- app/src/main/java/com/lurremcfly/yogaalarm/MainActivity.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmApp.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineCameraScreen.kt

iOS notes:
- Match the observable rules and copy using idiomatic iOS persistence and alarm presentation APIs.
- System-level termination cannot be prevented; the setting controls supported in-app dismissal paths.

<!--
Add new items here using the next sequential SYNC-XXX ID. Keep each item focused on
observable behavior and include enough detail for an iOS agent to implement it
without reconstructing the full Android history.

### SYNC-XXX — Short descriptive title

Status: pending
Date: YYYY-MM-DD
Android commit: <commit hash if available>

Changes:
- Describe the user-facing or behavioral change.
- Describe important rules, defaults, limits, and edge cases.
- Focus on what the app should do rather than Kotlin-specific implementation.

Relevant Android files:
- path/to/file.kt
- path/to/other/file.kt

iOS notes:
- Add useful implementation or parity notes.
- Match Android behavior and UX, but use idiomatic iOS architecture.
-->

## Completed

<!-- Move items here once implemented and verified on iOS. Change Status to completed. -->

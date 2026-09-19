# iOS Sync Queue

Android is the reference implementation for the iOS app.

This file tracks Android changes that affect cross-platform app behavior and therefore may need to be replicated in the iOS repository.

## Pending

### SYNC-007 — Pose illustration travels into the routine header

Status: pending
Date: 2026-09-17
Android commit: <pending>

Changes:
- When the routine first displays a pose or advances to a new one, show that pose's existing illustration centered on screen at about 3.75 times its final size. Hold it there for 1 second, then smoothly scale and move the same image into its existing small header slot over about 0.65 seconds.
- Render only one copy of the illustration during the transition so it appears to travel into place without flashing or overlapping a duplicate.
- The animation is visual only: it must not delay pose order, transition or hold timers, scoring, camera-based detection, controls, or other routine behavior. A rapid pose change should replace and restart the visual transition for the newest pose cleanly.
- Keep the existing routine layout, wording, illustration accessibility description, and final 96-point-equivalent image slot unchanged.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineCameraScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmTheme.kt

iOS notes:
- Implement this as a presentation-only animation driven by the currently displayed guide pose, without coupling it to routine timing or detection state.

### SYNC-006 — Alarm setup card highlights the next step

Status: pending
Date: 2026-09-16
Android commit: <pending>

Changes:
- While any alarm readiness requirement is incomplete, keep all four setup rows visible in this order: Camera, Notifications, Full-screen alarms, and Exact alarms.
- Show a checkmark for each completed requirement. Keep the existing Allow or Enable action for incomplete requirements, including the existing title, detail text, permission behavior, and tap feedback.
- Show one decorative 👉 beside only the first incomplete action. Keep nudging it with short, gently spaced bounces until that row is tapped, then stop. When that requirement becomes ready, move the pointer to the next incomplete row and resume nudging until its row is tapped.
- Keep the pointer decorative for accessibility and never use it or color as the only indication that an action is tappable.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt

iOS notes:
- Reuse the app's existing readiness refresh lifecycle and permission state; do not add parallel permission tracking.

### SYNC-005 — Privacy policy website

Status: pending
Date: 2026-09-16
Android commit: <pending>

Changes:
- The Privacy & safety policy link opens https://www.yogaalarm.com/privacy.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt

iOS notes:
- Use the same public policy URL.

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
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineRoute.kt

iOS notes:
- Use native idle-timer and supported alarm presentation behavior to keep the completed routine visible briefly.

### SYNC-003 — Permission setup stays on the home screen (popup reverted)

Status: pending
Date: 2026-09-16
Android commit: <pending>

Changes:
- The first-launch setup popup was reverted. Do not show an automatic permission popup on first launch or subsequent launches.
- Keep the existing home-screen readiness card with actions for missing permissions, including the existing denial and settings recovery behavior.
- Request access after the user taps an existing setup action or starts the camera. Previously stored initial_setup_shown values are ignored.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineRoute.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt

iOS notes:
- If the earlier popup was implemented, remove it. Preserve existing permission controls and supported platform permissions.

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
- While an alarm is ringing, its system notification offers a clear Start movement action, a Stop alarm action only when manual stopping is enabled, and a Snooze action only while configured snoozes remain. Stopping from the notification silences only that alarm and does not break the daily streak, so completing a later alarm that day can still continue it.
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
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineRoute.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmEditorScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineCameraScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/alarm/AlarmForegroundService.kt
- app/src/main/java/com/lurremcfly/yogaalarm/alarm/StopAlarmReceiver.kt

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

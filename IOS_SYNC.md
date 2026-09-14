# iOS Sync Queue

Android is the reference implementation for the iOS app.

This file tracks Android changes that affect cross-platform app behavior and therefore may need to be replicated in the iOS repository.

## Pending

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

# iOS Sync Queue

Android is the reference implementation for the iOS app.

This file tracks Android changes that affect cross-platform app behavior and therefore may need to be replicated in the iOS repository.

## Pending

iOS implementation checklist (source review: 2026-10-10): checked items are implemented in the current iOS working tree, including uncommitted changes. Unchecked items identify remaining implementation or verification. Source inspection is not device verification; entries stay Pending until their acceptance checks pass.

### SYNC-018 — Safe areas and resizable routine screens

Status: pending
Date: 2026-10-17
Android commit: <pending>

Changes:
- Support portrait, landscape and resizing on phones and larger screens. Keep interactive controls clear of top, bottom and side safe areas, including display cutouts and the keyboard. Screen backgrounds continue behind system bars; use dark system icons on light app screens and light icons during camera/routine presentation, restoring the prior appearance on exit.
- Make camera-ready controls a scrollable column so permission/start, snooze, stop and emergency-stop actions remain reachable in short windows and with large text. Preserve existing availability rules, snooze limits and emergency-stop confirmation behavior.
- In a routine window shorter than 600 dp and wider than it is tall, place the pose header in the left 40% and scrollable countdown/feedback/actions in the right 60%. Use a compact 72 dp pose glyph below Back/Stop so the controls cannot collide at narrow widths. Otherwise retain the 96 dp top glyph and bottom feedback, limiting the feedback height using the measured header height so it can scroll without covering the header at larger text sizes. Keep the camera preview and overlays behind the controls.
- Make the completion footer scrollable below the back control in short windows. Preserve greetings, streak presentation, Done and the existing ten-second completion wake deadline.
- Let the editor time picker scroll away when usable height, excluding safe areas and the keyboard, is shorter than 600 dp; retain its existing pinned behavior in taller windows. Keep draft edits and the current onboarding step through orientation changes. Bound full-page onboarding content to a readable 640 dp width and preserve scrolling and keyboard access.
- Rotation/resizing must preserve accumulated pose hold time, the active pose, snooze count and completion state. Reacquire camera tracking after interruption; do not replay a previously handled alarm launch or restart a completed routine. Refresh the preview crop and orientation, including a 180-degree rotation without a size change, while retaining selfie mirroring and aligned pose overlays.
- Keep all pose thresholds, routine durations, alarm scheduling, sound selection and purchase behavior unchanged. The Android Fragment/Gradle updates and Android-specific camera APIs need no iOS implementation.

Relevant Android files:
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/lurremcfly/yogaalarm/MainActivity.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmEditorScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/Onboarding.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineRoute.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineCameraScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/camera/PoseCameraController.kt

iOS acceptance checks:
- Check phone and tablet portrait/landscape, live resizing, large text, side safe areas and keyboard-open editing/onboarding; all controls remain reachable without overlap.
- Rotate during camera startup, holding, transition and completion; preserve progress and completion deadline, with no duplicate alarm presentation or completion credit.
- Check camera alignment after resizing and 180-degree rotation. Confirm snooze, stop, emergency stop, camera retry and Done retain their existing behavior.
- Verify readable system icons on light screens and dark routine screens and after leaving the routine.

### SYNC-017 — Structured Settings and configurable first day of week

Status: pending
Date: 2026-10-17
Android commit: <pending>

Changes:
- Restyle the scrollable Settings sheet with the app's cream background, white rounded cards, subtle borders, forest text/actions, and lime selection highlights. Group content into Preferences, Yoga Alarm Pro, and Help & info, with the muted app version below the cards. Help and privacy use full-width rows; preserve their existing destinations.
- Add a free, app-wide “First day of week” setting with mutually exclusive Monday and Sunday options. Default to Monday for new and existing installations regardless of locale. Save changes immediately and retain the preference across restarts.
- Keep “First day of week” and a compact M / S pill selector in one row. Use a lime highlight for the selected option, full Monday/Sunday accessibility labels and mutually exclusive selection semantics. Preserve at least 48-point touch targets and let the label wrap and the sheet scroll at larger text sizes.
- Keep free, lifetime and subscription Pro states in a dedicated card. Free users see Get Pro; lifetime users see permanent-access information. Consolidate renewal/end-date and cancellation actions into one “Manage subscription” button opening the existing platform subscription management destination. Explain automatic renewal or canceled renewal and where to find the relevant date; retain paid-period access and lifetime-access assurances. Show management whenever a subscription entitlement or known renewal state exists, including a lifetime user with a separate subscription.
- Order the repeat-day picker Monday–Sunday or Sunday–Saturday according to the preference when creating or editing an alarm, including the guided setup tour and replay.
- Change display order only: preserve selected weekdays, weekday defaults (Monday–Friday), existing alarms, and scheduled firing days. Keep canonical weekday IDs Monday = 1 through Sunday = 7; selecting the first item in Sunday-first mode selects Sunday, not Monday. Selecting no days continues to use the existing one-time alarm behavior.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AppInfoSheets.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmEditorScreen.kt

iOS acceptance checks:
- Verify Monday is initially selected; switch to Sunday, reopen Settings and restart the app, and confirm the preference persists.
- Create and edit alarms in both modes; verify day order, selected-day highlights, Sunday selection, and unchanged saved weekday identities and next occurrences. Replay the setup tour with Sunday first.
- Verify both options remain reachable with larger text and expose a single accessible radio selection.
- Verify the Settings groups, full-width help/privacy actions and version footer at narrow widths and larger text sizes. Check free, lifetime, renewing, canceled and unknown-renewal subscription states, including lifetime plus a separate subscription; confirm Get Pro and Manage subscription open their existing destinations.

### SYNC-016 — Guided first-use onboarding and Settings replay

Status: pending — implemented on Android; awaiting iOS implementation and device verification
Date: 2026-10-17
Android commit: <pending>

Final approved flow (2026-10-17):
- Use the native Android flow below as the current reference. The browser preview is design history and can differ from the latest native sequence.
- The invitation and optional first-use permission prelude are outside the numbered tour. The permission prelude shows “Getting ready”; the numbered tour has 14 steps. The separate “Test your full routine” teaching step has been removed.

| Step | Screen / prompt | Action and next behavior |
| --- | --- | --- |
| 1 | “An alarm that gets you moving.” | Introduce Set an alarm → Do your poses → Alarm stops. Set up an alarm advances to Add. |
| 2 | “Add an alarm” | Highlight the real Add alarm button. Tapping it opens a new editor draft with the slower screen transition. |
| 3 | “Set the time” | Scroll the actual hour/minute wheels, then Next. |
| 4 | “Name your alarm” | Edit the actual name field, then Next; clear focus and hide the keyboard. |
| 5 | “Choose repeat days” | Select repeat days, then Next. Green means selected; no days means a one-time alarm. |
| 6 | “Choose your alarm sound” | Tap the actual Sound row to open the sound sheet. |
| 7 | “Find your sound” | Preview/select either free sound. Next closes the sheet before moving to the routine introduction. |
| 8 | “Build your yoga routine” | Explain “Choose the poses you’ll do when your alarm rings.” Show the slow continuous three-pose preview, then Choose poses. |
| 9 | “Choose pose 1” | Swipe the first pose card among the three free poses, then Next. |
| 10 | “Choose pose 2” | Swipe the second card; explain the free 20-second holds, then Next. |
| 11 | “Choose pose 3” | Swipe the third card; explain top-to-bottom routine order. Try a pose opens placement instructions, without starting the camera yet. |
| 12 | “Try your first pose.” | Show the three-panel placement illustration. Try it now starts quiet five-second practice of the first chosen pose; Skip practice goes directly to Save. Successful practice shows Continue setup, which also goes directly to Save. |
| 13 | “Save your alarm” | Highlight the actual Save action. Back returns to placement; saving persists/schedules the configured alarm and opens the summary. |
| 14 | Saved-alarm summary | Show readiness, name, next occurrence when ready, ordered poses, per-pose durations and total duration. Offer Done and optional Test full routine. |

Observable behavior:
- On first launch, wait 600 ms on the home screen and offer a “Take a quick tour?” popup using the app’s cream background, 28 dp rounded corners, forest text/buttons, pose illustration and full-width Start tour button, with Later underneath. Start tour begins with a first-use-only “A few permissions first” page explaining why permissions are needed and reusing the normal camera/notifications/full-screen/exact-alarm checklist. Already granted permissions show as complete. Continue after granting permissions, or choose Set up later to continue the tour while retaining the home readiness checklist. Skip this page when all permissions are already ready, when it was previously introduced, and on Settings replay. Persist that it was introduced when leaving this page or skipping/finishing the tour. Later or dismissing the invitation suppresses future automatic invitations, with replay still available in Settings. Do not show the popup over another sheet, an editor/test, or a real ringing alarm.
- On iOS, use the equivalent native permission/readiness requirements; Android-only full-screen-intent and exact-alarm settings are not literal iOS permission requests. Keep the same first-use explanation, granted/denied/retry handling, deferral and replay behavior.
- Match the invitation’s position, width and height to the visible home permission card so the cream card fully covers its dark green background. Use a centered card with the same horizontal margins when no permission card exists. Keep content scrollable and clamp the card to the usable screen height for large text/small screens.
- Opening the alarm editor from home fades the home screen out over 450 ms and fades/slides the editor in over 1,100 ms after a 100 ms lead-in. This applies to Add alarm during the tour and normal use. Block taps during this transition to prevent duplicate actions; other navigation, especially real ringing alarms, remains immediate. Respect disabled/system-scaled animations.
- Keep the alarm-name field transparent with its normal text and underline colors when it becomes disabled outside its guided step. Clear focus and dismiss the keyboard when leaving Name, without introducing a grey fill or lingering focus highlight.
- Use a 350 ms fade out followed by a 900 ms fade in for guided Next/Back transitions, with a subtle 12 dp tooltip slide. Scroll the editor to the relevant control over 1,000 ms instead of jumping. Respect system animation scaling, including disabled animations. Prevent repeated Next/Back taps from queuing transitions.
- Use short, time-neutral copy to introduce the alarm → camera → poses → completed routine flow. Guide time, alarm name (40-character limit; generated names and blank-name fallback follow Android’s local-hour groups from SYNC-015), repeat days, sound selection, a dedicated yoga-routine introduction and each of three ordered pose slots. Back and Skip tour remain available; multi-choice steps advance with Next. Widen name, day and Sound highlights to nearly the editor card width and align rounded highlights with pose cards. Show a wide finger swipe without dashes or arrows.
- Use a dark translucent scrim (Android: #0D1913 at about 67% opacity), rounded spotlight corners up to 28 dp and a 2 dp lime outline. Leave only the active control available for interaction through the spotlight. Use a large pointing hand (54 sp on Android); on pose steps move the finger horizontally across about 160 dp over 1.5 seconds, then repeat after a short pause. Keep the gesture finger-only, without trailing dashes or arrows. Position hints around status/navigation bars and the keyboard, keep text scrollable at larger sizes, and scroll the editor so the active control and hint fit together.
- Guide the real editor controls: repeat-day order follows Settings (SYNC-017); Sound, Snooze and Stop button rows; Test full routine above the pose cards; no pose-reorder handle. The sound step opens Sound & vibration, offers the two free sounds (Sunbird Morning Call and Morning Temple Call), and supports ten-second local previews. Selecting a sound preserves the fully expanded sheet until Next; Pro sounds stay outside the guided selection. Stop previews when leaving the sheet or backgrounding the app. The routine introduction is a popout that scrolls slowly and continuously through the three numbered poses at a constant speed (six seconds per card, about 32 dp/second), with a repeated first card for a seamless loop. Do not pause or snap between poses; reduced-motion users see a static three-pose overview.
- Keep guided pose selection to the three free poses (Mountain, Warrior II, Tree). Explain the free 20-second holds and top-to-bottom routine order without leading users into Pro purchases.
- A new guided draft starts with Mountain → Warrior II → Tree, each held for 20 seconds (60 seconds total). Guide the same free catalog even for a Pro user. During the tour, disable adding/removing poses, duration changes, per-pose Try actions and the normal editor Test full routine action; keep the three pose slots available only during their respective selection steps. Restore ordinary editor/Pro behavior when the tour is skipped or finished. Keep the editor’s actual layout and current defaults rather than recreating an outdated mock screen; weekday defaults/order and local-hour time/name behavior follow SYNC-015/SYNC-017.
- In Find your sound, dim the entire screen outside the highlighted free sound choices, including the sheet’s drag handle and the editor behind/above the sheet. Use the same dark scrim opacity in the sheet and its modal backdrop. Keep guided switches in their actual on/off colors even when their interaction is temporarily disabled (on: lime track, forest thumb). After Next, fade the guide out, animate the sound sheet closed, scroll down to the first pose over 1,000 ms, pause briefly (200 ms unless animations are disabled), then reveal the Build your yoga routine popup with a 900 ms fade and subtle slide. Do not show the popup while the sheet is still visible. Stop sound previews before closing; preserve the chosen sound and draft. Back and Skip must also dismiss the sheet cleanly.
- Before optional practice, show the approved three-panel illustration: prop the phone upright on the floor facing the user, take about two steps back, then follow the pose. Start the camera only on explicit user action. Practice the first chosen pose for five seconds with sound, vibration and transition/completion cues off; preserve the configured routine and its durations. Practice and routine tests never record a real alarm completion or change streaks. Support retry and skipping practice. After successful practice, show a quiet completion screen with Continue setup; cancelling practice returns to placement.
- Reuse the approved static `onboarding_phone_setup.png` illustration of the default yoga woman, with the panels “Place your phone”, “Take 2 steps back” and “Follow the pose”. Do not substitute the rejected Remotion phone-placement scene or add a full-app animation. Placement copy also explains screen-facing orientation, “5 seconds · sound off” and local processing without recording. Returning to placement after a completed practice of the same first pose shows “You’ve got it.”, Next and Practise again; changing the first pose requires fresh practice. A practice cancellation/permission failure must leave the original alarm draft intact.
- After practice completion (or skipping practice), go directly to Save your alarm. Omit the separate Test your full routine teaching step; Save is step 13 of 14, and Back returns to phone placement/practice. Keep the normal Test full routine editor action and the optional test on the saved-alarm summary. Save explicitly through the normal persistence and OS scheduling path, then confirm the name, selected routine and durations. Show the next occurrence and “ready” only when the alarm is enabled and all existing readiness checks pass; otherwise say “saved” and direct the user to finish setup on the home screen. Preserve the existing native permissions checklist and scheduling-failure behavior (save switched off). Offer Done and Test full routine.
- Include permission setup at the start of the first-use tour. Persist tour completion when the user finishes or skips; allow replay through Settings → How to use Yoga Alarm without changing existing alarms automatically. Guide a new draft, restrict interactions to the highlighted control, preserve draft edits through Back and camera tests, and dismiss the keyboard when leaving the name step. Hardware Back navigates the guide or returns from practice/tests. A real ringing alarm takes priority over the guide, including during practice; it must retain the usual alarm completion/stop behavior. After the real alarm closes, an unfinished first-use tour can be offered again.
- Keep two persisted concepts: automatic-tour completion/dismissal, and whether the permission prelude has been introduced (Android keys `completed_v1` and `permissions_introduced_v1`). Back from Time to Add and then re-entering the editor must reuse the same unsaved draft. Skip tour in the editor leaves that draft editable without automatically saving; exiting the tour on home clears its pending draft. Settings replay creates a fresh guided draft and leaves existing saved alarms untouched. A full-routine test launched from the saved summary finishes the tour, uses the saved alarm’s normal sound/settings/durations, and returns home on exit.
- The browser remains a design/testing harness. Its camera-free simulation is not included in native onboarding.

Android references:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/Onboarding.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmEditorScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AppInfoSheets.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineRoute.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmTheme.kt
- app/src/main/java/com/lurremcfly/yogaalarm/model/AlarmConfig.kt
- app/src/test/java/com/lurremcfly/yogaalarm/ui/RoutineViewModelTest.kt
- app/src/main/res/drawable-nodpi/onboarding_phone_setup.png

Prototype references:
- web-tester/app/Onboarding.tsx
- web-tester/app/AlarmShell.tsx
- web-tester/app/AlarmOptions.tsx
- web-tester/app/page.tsx
- web-tester/app/globals.css
- web-tester/public/onboarding/phone-setup.png

Acceptance:
- Verify invitation alignment/complete coverage over the home permission card, its centered fallback when permissions are ready, small-screen/large-text scrolling, the slower Add alarm transition without double activation, name-field appearance after Next, initial permission page with partially/already granted permissions, denial/retry and Set up later, persisted omission on subsequent tours, Start/Later/dismiss behavior and Settings replay. Verify full-screen dimming around sound choices (including the handle/background), colored Sound switch, the ordered sheet-close → scroll → routine-popup transition, smooth step transitions and the continuous routine-preview loop, skip/back, sound selection and preview cleanup, each free pose selection, camera denial/retry, practice completion/cancellation, draft preservation, explicit save and next-alarm summary.
- Verify highlights follow the controls on small screens and with larger text; respect reduced motion and accessible navigation. Verify rotation, background/foreground, permission changes, and a fired alarm interrupting the guide or practice. Android compilation and focused unit tests pass; native device verification remains pending.
- Verify the final sequence has 14 numbered steps with Save at 13: both Skip practice and successful practice → Continue setup must go straight to Save, with no full-routine-test teaching step. Verify Back from the Save step returns to placement, including its completed-practice/retry state. Test the optional full routine from the final summary and the normal editor after leaving the guide.

Validation status:
- The latest Android debug APK builds successfully with the 14-step sequence. The 27 focused routine/draft/scheduler tests passed during this onboarding work, including isolation of quiet five-second practice from the configured routine; the final UI-only refinements were checked by compilation and diff review without rerunning those tests.
- Android-device screenshots/feedback informed the visual refinements. Final device acceptance of the latest revision, including timing, invitation coverage, name-field appearance and sound-sheet dimming, remains pending. This entry remains Pending until the iOS flow is implemented and verified; this documentation update does not change the iOS repository.

### SYNC-015 — Local-hour defaults and varied alarm names

Status: pending
Date: 2026-10-17
Android commit: <pending>

Changes:
- When New alarm is tapped, read the device's current local time once and set the draft to that hour with minutes `00`: 14:37 → 14:00, 00:20 → 00:00, 23:59 → 23:00. Preserve the existing weekday/repeat and other defaults. A time already passed schedules the next future occurrence on a selected weekday using the existing scheduler, rather than firing immediately.
- Randomly select a name from the matching time group below. Prefer names not already used by saved alarms; if all eight names in that group are used, allow a repeat from the same group. Use local selection with no network dependency.
- Morning, 05:00–10:59: “Rise & stretch ☀️”; “Morning movement”; “A fresh start 🌱”; “Wake up gently”; “Hello, new day 🌤️”; “Start with a stretch”; “Meet the morning 🌅”; “Your morning moment”.
- Midday, 11:00–16:59: “Midday reset 🌿”; “A little movement”; “Find your balance ⚖️”; “Stretch break”; “Pause & stretch 🧘”; “Make space for you”; “Refresh & refocus ✨”; “Time to move”.
- Evening, 17:00–21:59: “Evening unwind 🌙”; “Release the day”; “Sunset stretch 🌅”; “Room to breathe”; “Let the day go 🍃”; “Ease into evening”; “A softer pace 🌿”; “Evening balance”.
- Night, 22:00–04:59: “Moonlight movement 🌙”; “Quiet reset”; “Night owl stretch 🦉”; “Move at your pace”; “A mindful moment ✨”; “Quiet strength”; “Under the stars 🌌”; “A gentle stretch”.
- Keep the generated draft time and name stable through redraws, rotation/restoration, and testing a routine then returning. Changing the draft time does not change its name. Users can edit the name normally; keep the existing 40-character limit.
- On save, trim the name. If empty or whitespace-only, select a name using the final selected alarm hour and the same duplicate-avoidance rule, excluding the alarm being edited from existing names. Preserve nonblank names and do not rename existing saved alarms automatically.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/model/AlarmConfig.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmEditorScreen.kt
- app/src/test/java/com/lurremcfly/yogaalarm/model/AlarmDraftTest.kt

iOS acceptance checks:
- Create alarms around midnight and the 05:00, 11:00, 17:00, and 22:00 boundaries; confirm rounded-down local hours and matching names.
- Verify unused names are preferred, with valid repeats after all eight names are used.
- Change time, rotate/restore, test a routine and return, then save/reopen; confirm the draft name and selected time stay stable. Verify custom names, blank-name fallback based on the final hour, and existing saved alarms.
- Save a past rounded-down time and confirm the next occurrence respects selected weekdays.

### SYNC-014 — App version in Settings

Status: pending
Date: 2026-10-17
Android commit: <pending>

Changes:
- Show a read-only version label below Privacy & safety at the bottom of Settings, using muted 14-point text and an 8-point gap. Keep it in the existing scrollable content so it remains reachable with larger text.
- Format the label as `Version <release version> (<build number>)`, for example `Version 1.0.17 (18)` on the current Android build. Read both values from the installed app's build metadata so future releases update automatically; on iOS use the native app version and build number.
- Display the installed version immediately, including offline. This does not fetch the latest store version or check for updates.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AppInfoSheets.kt
- app/build.gradle.kts

iOS acceptance checks:
- Open Settings and confirm the label matches the installed iOS app's release version and build number, including offline and at larger text sizes.

### SYNC-013 — Five-second pose holds

Status: pending
Date: 2026-10-06
Android commit: <pending>

iOS implementation progress (2026-10-10):
- [x] Five-second holds in the shared duration catalog; 20-second default/free choice, Pro gates, scrollable picker, and 5–60-second benefits copy.
- [ ] Verify save/reopen, test/fired countdowns, free-user paywall routing, and all seven choices at accessibility text sizes on iOS.

Changes:
- Add 5 seconds as the shortest selectable hold duration, followed by the existing 10, 15, 20, 30, 45, and 60 seconds. Each pose can independently use 5 seconds; saving, testing, and fired routines use that duration.
- Preserve the 20-second default and existing access rules: 20 seconds is free; every other duration, including 5 seconds, requires Pro. Locked duration choices open the existing paywall.
- Let the duration picker scroll so all seven options remain reachable on smaller screens and with larger text. Update Pro benefits to describe 5–60-second holds.
- Pose detection/hold qualification and the 3-second between-pose transition are unchanged.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmEditorScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AppInfoSheets.kt
- docs/play-billing-setup.md

iOS acceptance checks:
- Select 5 seconds, save/reopen an alarm, and test or run it. Confirm the hold countdown uses 5 seconds and existing pose qualification still applies.
- Confirm free users retain 20 seconds and tapping 5 seconds opens the paywall. Check all seven choices remain reachable with larger text.

### SYNC-012 — Compact home layout with larger-text support

Status: pending
Date: 2026-10-06
Android commit: 31375f9c24d5046f7eb1ad8dda6433d3a634f4b3 (2026-10-10 streak update; earlier changes: <pending>)

iOS implementation progress (2026-10-10):
- [x] Header Settings gear, free-user GET PRO, Pro access through Settings, adaptive title/action layout, and fixed bottom Add alarm pill.
- [x] Compact alarm-card padding/time typography, list spacing, and scroll clearance above the add pill.
- [x] Implement the fixed 80-point streak card and adaptive date markers specified in SYNC-011.
- [x] Finish subscription Settings controls specified in SYNC-010.
- [ ] Verify small-phone/large-text layouts, safe areas, last-card clearance, and native alarm context-menu placement relative to the add pill.

Changes:
- Place Settings as a muted-ink gear in the header with an accessible label and 48-point touch area, eliminating its separate top row. Use 4-point spacing between header actions. The header no longer contains an add-alarm button.
- Place a fixed, bottom-center “+ Add alarm” pill above the bottom safe area, 12 points from it. Use lime background, forest content, fully rounded corners, a minimum 56-point height, 24-point plus icon, 8-point icon/text spacing, 14-point bold label, and a subtle shadow. Tapping opens the existing new-alarm editor. Keep the action visible while the list scrolls.
- Keep GET PRO in the header for free users. For existing Pro members, remove the header Pro pill and provide Pro/subscription access through Settings. GET PRO uses a white outlined pill, 10-point bold text, 10-point horizontal/8-point vertical padding, and a minimum 48-point touch height. It grows with native text size.
- Keep the full “Your alarms” title readable at larger text/display sizes. Use a 22-point title below 300 points of available header width, 24 below 340, otherwise 28. The title must wrap rather than truncate to “Your ala…”. If the title and actions cannot fit, wrap the action group together onto a second row with 10-point spacing. Preserve native text scaling; do not disable Dynamic Type to make the row fit.
- Start the home list 40 points below the top safe area with 22-point side padding. Retain 14-point spacing between list items and add another 14 points after the streak card, for a 28-point gap to the following card. Use the fixed-height compact streak layout in SYNC-011 (updated 2026-10-10). Finish with 96-point bottom padding above the bottom safe area so the final alarm can scroll clear of the fixed add pill. Position the existing alarm-action popup 80 points above the bottom safe area so it clears the pill.
- Compact alarm cards to 20-point horizontal/18-point vertical padding. Alarm times use 48-point text with 54-point line height; retain the existing name, switch, weekday, divider, and pose summary. Use 12-point gaps before and after the divider. Editing and toggling behavior remains unchanged.
- Settings contains the privacy and subscription controls specified in SYNC-005 and SYNC-010.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AppInfoSheets.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt
- app/src/main/res/drawable/ic_add.xml
- app/src/main/res/drawable/ic_settings.xml

iOS acceptance checks:
- Check default and largest accessibility text sizes, larger display size, and a small phone. Confirm the complete title remains readable, controls reflow, and Settings remains reachable.
- Check both free and Pro states: GET PRO stays visible for free users; paid users reach Pro through Settings. Verify the compact streak/count and adaptive markers fit side by side within an 80-point card, and the last alarm remains clear of the bottom safe area.
- Confirm the bottom Add alarm pill remains visible while scrolling, opens a new alarm, and does not obscure the last alarm or alarm-action popup. Check gesture navigation and three-button navigation safe areas.

Validation status:
- Streak update (2026-10-10): debug Kotlin compilation and 33 focused WakeProgress/StreakSchedule/AlarmScheduler tests passed. Device layout and zoom checks remain pending.
- Compact layout update (2026-10-06): debug Kotlin compilation and whitespace checks passed. Samsung device layout and larger-text verification remain pending.
- Android debug Kotlin compilation and eight focused WakeProgress tests passed for the 2026-10-05 changes. Device layout, animation timing, and real Play cancellation still need verification. iOS changes remain pending.

### SYNC-011 — Routine and alarm feedback animations

Status: pending
Date: 2026-10-04
Android commit: <pending> (2026-10-17 pause markers and completion-based eligibility; previous streak update: 31375f9c24d5046f7eb1ad8dda6433d3a634f4b3)

iOS implementation progress (2026-10-10):
- [x] Sunrise completion with measured whole-number docking, entrance-settled three-second hold, multi-digit/zero handling, and usable Done/back controls.
- [x] Pose-complete check/glow, find/hold/return feedback, saved-progress pause/resume presentation, and traveling pose illustration.
- [x] Session-owned earned-streak presentation, whole-count slide/fade, flame-only tap wobble, and Reduce Motion handling.
- [x] Recent completed-day persistence and bounded history reconstruction from an existing positive streak; future markers do not show checks.
- [x] Saved-card entrance keyed by alarm identity, insertion/removal reflow, and enable/disable color transitions.
- [x] Implement the fixed 80-point two-row card, scaled geometry budget, seven/five/three/one markers, label shortening, rest-day dashes, and full accessibility descriptions.
- [x] Add the specified 100 ms delay before the earned-count roll and coordinated flame wobble.
- [ ] Replace rest-day dashes with yellow pause markers, shown only after a completion with no intervening unfinished alarm day, as specified in the 2026-10-17 updates below.
- [ ] Finish scheduled-day accounting and migration rules in SYNC-001; do not restore a previously reset streak from old completion markers.
- [ ] Run iOS animation, recreation, rapid-tracking/toggle, midnight, large-text, and Reduce Motion acceptance checks.

Changes:
- Add five coordinated feedback animations: sunrise completion with a docking streak number, pose completion, find/hold/return feedback, an earned streak card with tap replay, and alarm changes. The specifications below describe the Android implementation, including the final resting state and when each animation may replay.
- These are presentation changes; use the updated scheduled-day streak accounting in SYNC-001. Keep alarm scheduling, hold progress, the 3-second next-pose transition, and the 10-second completion wake window. Test routines do not earn streak days. Pose selection stays unchanged.

#### Motion conventions

- Dimensions below are Android dp; use equivalent logical points on iOS. Text sizes are Android sp; preserve native text scaling. Use native text and shapes rather than a recording of the animation.
- Timed Android animations use the default `FastOutSlowIn` easing (cubic Bézier 0.4, 0, 0.2, 1). Spring durations depend on settling; do not replace them with loops. Damping ratios below describe the amount of bounce. Match the visible motion using native springs; Android stiffness values are references, not interchangeable iOS API values.
- Respect system motion preferences. With animations disabled or iOS Reduce Motion enabled, show the final state immediately, skip celebration waits, and keep all controls usable. Normal motion must never block dismissal, saving, toggling, deleting, or pose detection.

#### 1. Sunrise completion with streak number

Trigger and layout:
- Enter when the routine reaches COMPLETE. Replace the camera/pose UI with a full-screen vertical forest gradient: `#153523` at the top, `#07100A` at the bottom. No confetti.
- Reserve the bottom area for the existing greeting, completed-pose count, streak label, and Done button, with safe-area insets and 28-point horizontal/vertical padding. Center the sun horizontally in the remaining space above this footer and below the top controls. Its final diameter is the smallest of 220 points, 64% of screen width, and 80% of the remaining height, so it adapts to screen size and text scaling.
- The sun is a circle with a vertical gradient from pale yellow `#F9E4A7` to lime `#C9EE73`. A radial lime glow fades from 16% opacity at its center to transparent, extending to twice the sun radius.
- The final label is “🔥 N-day yoga streak”. The number and suffix use the same native bold 16-point font, with lime text. Render the actual integer count as text: support every digit 0–9 and multi-digit values such as 10, 30, and 100. Do not use separately drawn numeral paths or swap font families/weights between the large and final number.

Sequence, measured from entering COMPLETE:

| Time | Observable motion |
| --- | --- |
| 0–900 ms | The sun rises 90 points from below its final center, scales from 80% to 100%, and fades from transparent to opaque. The greeting and pose-count text fade in alongside it. |
| Starting at 0 ms | The number appears at the moving sun center in dark forest `#153523`. It fades in and springs from 25% of its large scale to full size, with a small overshoot (damping 0.65, stiffness 180). |
| During entrance, then 3000 ms after both entrance animations settle | Keep the number centered inside the sun, including while the sun rises. Hold the fully presented number for 3 seconds before docking. The flame and “-day yoga streak” suffix remain invisible; their final layout space is already reserved. |
| After the 3-second hold | Over 850 ms, move the same number from the sun center to its measured position in the footer label while shrinking to its normal 16-point size. Interpolate its color from dark forest to lime. Fade the flame and suffix from transparent to opaque over the same docking motion. |
| After docking | Scale the entire streak label to 106% over 140 ms, then spring back to 100% (damping 0.6). Leave the sun, greeting, label, and button stationary afterward. |

Number sizing and edge cases:
- Scale the whole number as one unit. Its large scale is limited by both 72% of the sun diameter divided by the measured number width and 78% of the sun diameter divided by the normal font size. This keeps wider counts inside the sun. Use the actual final text position as the destination, so all digits land precisely without a font or position jump.
- Keep the existing time-of-day greeting and completed-pose count. The Done button is visible and usable throughout; the existing top back action remains when applicable. Neither waits for the animation.
- Use the streak supplied by existing completion accounting. A test routine may animate the existing positive streak but must not increment it. A zero streak shows the sunrise, greeting, pose count, and Done button without a large number or streak label; zero still works as a digit within multi-digit counts.
- Do not replay an already-presented completion animation on screen recreation. If the supplied count updates as completion is recorded, show the updated count rather than an old value. Leaving the screen cancels its animation without delaying the existing completion/lock behavior.

#### 2. Pose complete

- Trigger only on a non-final pose entering the existing 3-second TRANSITION. Retain the hold ring with its completed progress; replace the countdown number with a lime checkmark, 64-point text, centered inside the ring.
- Fade the checkmark from transparent to opaque while scaling it from 65% to 100%, with a spring (damping 0.65). Simultaneously scale the ring/countdown container to 107% over 160 ms, then spring back to 100% (damping 0.6).
- Flash a soft lime radial glow behind the ring: 28% opacity at the center, fading to transparent at the edge of a 176-point area. Fade this glow to zero over 750 ms. There is one flash, not a repeating pulse.
- Below the ring, show “Next: [pose name]”, the next pose instruction, and “Starts in [remaining transition seconds]”. Feedback changes crossfade: incoming text takes 180 ms, outgoing text 120 ms. Keep the countdown running on the existing routine clock.
- When the guide pose changes, preserve its existing illustration arrival: it initially appears enlarged at screen center, holds for 1000 ms, then shrinks from 3.75× to 1× and moves into its 96-point header position over 650 ms. It fits inside the existing transition; add no extra wait before detecting the next pose.
- At the next pose, reset the completion check/glow state and show normal finding/holding UI. The final pose goes directly to the sunrise completion screen rather than adding another 3-second checkmark transition.

#### 3. Find → hold → return

- Keep the existing finding guidance and camera preview. The pose target overlay fades out when detection locks, using 220 ms; it remains visible while finding, with its existing lower opacity when almost matched.
- On entering HOLDING, show the existing countdown in 84-point medium text inside a 144-point progress ring, with a 2-point round-capped stroke. The ring is lime `#C9EE73`; its track is white at 12% opacity. Pulse the container once to 107% over 160 ms, then spring to 100% (damping 0.6).
- Under the ring, show “Hold” and “Stay with it while the alarm fades”. Keep the feedback centered and reserve at least 100 points of vertical space to reduce ring movement when the message changes.
- On tracking loss, retain the ring and countdown with the saved hold progress. Stop counting down; do not reset, drain, hide, or continue filling the ring. Settle any active pulse to normal size and transition the ring color to white at 45% opacity over 220 ms.
- Replace the feedback with “↶ Return to pose” and “[N] sec remaining · your time is saved”, using the same 180/120 ms incoming/outgoing crossfade. Show existing framing/error guidance below it when needed. Camera retry and applicable stop actions remain available.
- On returning to HOLDING, transition the ring back to lime over 220 ms, repeat the single 107% pulse, restore hold feedback, and continue from saved time/progress. Do not replay the large pose illustration merely because tracking resumes on the same pose. Rapid tracking changes cancel/retarget feedback; never queue repeated pulses or affect pose detection.

#### 4. Streak earned and flame wobble

- Trigger an earned celebration when the home streak count becomes greater than the last count presented in this app session. Initialize that presented count from stored progress on a cold launch, so opening the app with an existing streak does not celebrate it again. Preserve presentation state across screen recreation.
- Updated 2026-10-10: keep the streak card exactly 80 points tall, with 24-point corners, 82% white surface, hairline border, and 14-point horizontal/10-point vertical padding. Use two shared rows: the left “day streak” label aligns with weekday labels; the flame/count align vertically with the marker centers beneath. Keep 4 points between the rows and at least 12 points between the count group and markers. Never wrap markers underneath the count or enlarge the card. Preserve the flame-only tap wobble.
- Flame clipping fix (2026-10-10; Android commit: <pending>): center the flame in the marker-height value row using its natural emoji font bounds and padding, rather than forcing its line height into a glyph-size square. The full emoji must remain visible at rest and during the wobble at every supported display/text scale; keep the card at 80 points and retain the existing flame size and horizontal spacing.
- Measure the complete count and label at the current text/display scale. Prefer seven dates, with TODAY FIFTH: four previous days, today, and two upcoming days. If they do not fit, show five dates (today third), then three (today second), then only today. These are consecutive local calendar dates, not a fixed week or carousel. Shorten “day streak” to “streak” when five markers cannot fit with the full label; accessibility always announces the full count as an N-day yoga streak. Refresh on alarm edits, app resume, and local midnight; Android checks the date every minute.
- Default label text is 12 points with a 16-point label row; count text is bold 28 points. Labels and count grow with text scale within the fixed card budget: the label row caps at 24 points, label glyph size at 22 points, and count glyph size at the marker-row height. Markers start at 28 points, increase toward 32–34 points with larger text, and are limited by the height remaining below labels and the 4-point gap. Keep at least 6 points between marker columns and distribute remaining horizontal space evenly. Marker corners are 8 points. Completed dates show green `#81BD50` squares with white checks, including completions before a streak break. Unfinished scheduled dates have empty hairline squares; dates without alarms show the yellow pause marker described below. Today's weekday uses forest text and its square has a 2-point outline (forest when unfinished, green when completed); other outlines are 1 point. Provide full-date, today, completed, rest-day, not-completed, and upcoming accessibility descriptions.
- Updated 2026-10-17: rest-day markers use a solid yellow `#F5BF42` fill with a centered pause icon made of two forest-colored vertical bars, replacing the muted dash. Each bar is 12% of the marker width and 46% of its height, with a 12%-width gap and 1-point rounded corners. Use a matching yellow outline except for today's forest outline. Apply this to past, current, and upcoming rest days; a completed date always retains its green check even if no alarm is currently scheduled. Preserve the existing rest-day accessibility description, adaptive sizing, and streak accounting. Relevant Android file: `app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt`.
- Pause eligibility correction (2026-10-17): a date without an alarm is yellow only when it continues a chain from a completed yoga day. First install shows empty markers for past, current, and future dates. Dates before the first completion stay empty. A missed scheduled date ends the chain: later alarm-free dates stay empty until another real completion; earlier completed and paused dates remain visible. A pending alarm today or in the future is empty and does not authorize pauses after it until completed. Example: completed Friday → paused Saturday/Sunday → unfinished Monday → empty Tuesday; completing Monday allows an alarm-free Tuesday to show paused. If an active streak's last completion is older than the visible four-day history, its alarm-free days still show paused; an already broken streak must not revive pauses from an old completion. Stopping before today's completion preserves the numeric streak until midnight but does not mark today or following days as paused. This changes marker classification and its accessibility description only, not streak accounting. Relevant Android files: `app/src/main/java/com/lurremcfly/yogaalarm/model/WakeProgress.kt`, `app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt`, `app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt`, and `app/src/test/java/com/lurremcfly/yogaalarm/model/WakeProgressTest.kt`.
- Persist recent actual completion dates separately from the current streak; retain today and the previous four calendar days. Stopping a later alarm or breaking a streak does not erase recent completed-day checkmarks. Multiple same-day completions produce one marker, test routines never add markers, and future dates never show checks. On upgrade with no history, reconstruct up to five known consecutive completed dates from the existing streak count and last completion date; do not invent older interrupted history. The relevant persistence/model files are listed below.
- Initially show the previous count. After 100 ms, change to the earned count: the old number slides upward by its own height over 350 ms and fades out over 200 ms; the new number enters from one height below over 350 ms and fades in over 250 ms. Animate the entire integer string, including transitions such as 9 → 10; do not cycle through intermediate counts.
- At the same time as the number change, rotate only the flame to −18 degrees over 100 ms, then +18 degrees over 180 ms, then spring back to zero (damping 0.45). The card and label keep their normal size and position.
- Consume the earned presentation once when home presents it. Ordinary navigation, reopening the same screen, and a same-day routine that does not increase the count must not roll the number or replay the earned wobble.
- Tapping a positive streak card runs only the flame wobble immediately. No gray pressed/selected background, card scaling, or label scaling. Keep the count unchanged: no increment, persistence, navigation, or new earned event. Repeated taps restart/retarget the wobble from its current angle rather than stacking animations.
- For zero streaks, show count 0 and the same adaptive markers, preserving known recent completions. There is no earned-count roll or tap celebration. If the streak decreases/resets, show the new value without an earned celebration.

#### 5. Alarm changes

- On saving a new or edited alarm and returning home, animate only that saved card: start at 0% opacity, 24 points below its resting position, and 97% scale. Animate all three to 100% opacity, zero offset, and 100% scale using one spring (damping 0.8, stiffness 220). Consume this saved-card presentation once when the card is composed; an offscreen card may present when it first appears.
- Initial loaded alarms and ordinary returns home without a save do not run this card entrance. Keep cards keyed by stable alarm identity so mutations animate the correct card.
- Adding/removing cards fades their appearance/disappearance and springs remaining cards to their new list positions. Android uses Compose `animateItem()` defaults: spring-based opacity and placement with medium-low stiffness. Match smooth native list insertion/removal/reflow rather than inventing a fixed delay or delaying deletion.
- On enable/disable, interpolate the alarm name and time colors over 250 ms. Enabled is dark ink `#142018`; disabled name is muted ink `#647069` at 55% opacity and disabled time at 45%. The existing native switch still changes checked state normally. Quick toggles retarget colors from their current value.
- Scheduling/cancellation and persistence run immediately as before. Do not wait for an entrance, fade, or color transition. Preserve the existing saved-but-disabled state and message when scheduling fails, and the existing snackbar confirmation. No celebration may imply that an unscheduled alarm is enabled.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineCameraScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineRoute.kt (existing completion callback and wake deadline)
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineViewModel.kt (existing hold/transition state and timing)
- app/src/main/java/com/lurremcfly/yogaalarm/model/WakeProgress.kt
- app/src/main/java/com/lurremcfly/yogaalarm/data/WakeProgressStore.kt
- app/src/test/java/com/lurremcfly/yogaalarm/model/WakeProgressTest.kt

Android validation:
- Rolling five-day card: debug Kotlin compilation, ten focused `WakeProgressTest` cases, and whitespace checks passed. On-device layout and midnight rollover checks remain pending.
- Kotlin compilation and the 13 existing `RoutineViewModelTest` cases passed. On-device visual verification is still pending; no Android device was connected during implementation.

iOS acceptance checks:
- Finish a real routine with counts 1, 3, 7, 9, 10, 30, and 100. Confirm matching font/weight throughout, whole-number scaling, contrast inside the sun, exact docking into the label, no confetti, and a stable final state. Check the zero-streak test-routine case separately.
- Test smaller screens, landscape, large text, and safe-area insets. The sun should fit above the footer and the number should land at the actual label position; Done/back must remain usable during every stage.
- Dismiss completion before docking and recreate the screen after presentation. There must be no delayed navigation, duplicate streak day, forced replay, or extension of the completion wake window. Confirm test routines leave stored streak progress unchanged.
- Lose and regain tracking partway through a hold, including repeated rapid losses and a camera error. Confirm saved time/progress freezes and resumes, text/color/pulse changes settle, and retry/stop actions work. Complete a non-final pose; confirm check/glow/next guide fit within the original 3 seconds. Complete the final pose; confirm sunrise appears directly.
- Earn a day and return home, then tap repeatedly, navigate away/back, recreate the screen, and cold launch. Only the earned increase rolls the count; taps only wobble the flame and never gray or resize the card. Check 9 → 10, same-day completion, and streak reset. Confirm the completion number holds for 3 seconds after its entrance, then docks.
- Check seven/five/three/one-marker layouts over midnight, month/year boundaries, a streak break, multiple same-day completions, restart, and upgrade. Confirm today is fifth when seven dates are visible, recent completed dates survive a reset, future days never show checks, and qualifying rest days show yellow pause markers. Check first install, pauses before/after a missed alarm, completion after a break, pending upcoming alarms, and an active streak with its completion outside the visible history. At larger text/display sizes and with wide counts, the card remains 80 points tall with aligned label/value rows and full-date accessibility descriptions.
- Save new/existing alarms, scroll to an offscreen saved card, toggle rapidly, and delete first/middle/last cards. Confirm correct card identity, one saved entrance, smooth reflow, and immediate schedule/persistence updates. Test scheduling failure: the disabled state and existing failure message must remain accurate.
- Repeat the flows with motion disabled/Reduce Motion. Final states should appear immediately without celebration waits, count mutations, timing changes, or blocked controls.

iOS implementation update (2026-10-10): fixed-height adaptive card and delayed earned-count presentation implemented in AlarmHomeView.swift/StreakViews.swift; rest-day marker classification covered by an added WakeProgressTests regression. Scheduled-day streak accounting remains pending under SYNC-001. Structural checks and whitespace checks passed; Swift/Xcode are unavailable on this Linux host, so compilation, XCTest execution, and device layout/motion checks remain pending.

### SYNC-010 — Store purchases, localized prices, and restoration

Status: pending
Date: 2026-09-27
Android commit: <pending>

iOS implementation progress (2026-10-10):
- [x] StoreKit Monthly/Yearly/Lifetime catalog, localized prices, unavailable-plan states, retry, restore with price reload, and available-plan selection fallback.
- [x] Expanded paywall with Close, scrolling content above a fixed App Store purchase area, renewal terms, and separate selection/checkout.
- [x] Shared verified entitlement authority, lifetime precedence, pending/canceled checkout handling, active-Pro purchase blocking, transaction updates/finish, and restore/foreground refresh.
- [x] Verified subscription renewal/cancellation and paid-through-date text for subscription-only ownership; native subscription management is available.
- [x] Add explicit subscription/lifetime active Settings copy, prominent Cancel subscription/Manage subscription states, cancellation explanation, and separate renewal/end-date controls.
- [x] Show the verified localized renewal or subscription end date directly in Settings; use an App Store date/management link only when verified date information is unavailable.
- [x] Keep subscription status and management available when lifetime and a valid subscription coexist; explain that lifetime access is unaffected.
- [ ] Remove sandbox/Xcode Test Pro access and its entitlement bypass; the queue requires store-only unlocking in all builds.
- [ ] Verify real StoreKit checkout, deferred payments, restoration, expiry/grace/revocation, temporary failures, catalog IDs, and small-screen/large-text layout.

Changes:
- Restore Purchases retries loading product prices as well as checking owned purchases. If an earlier price lookup failed temporarily, a successful retry makes eligible purchase buttons usable again without restarting the app.
- Starting an already-connected billing session also reloads prices.
- Monthly and yearly select the `monthly` and `yearly` base plans of `yoga_alarm_pro`; lifetime uses the non-consumable `yoga_alarm_pro_lifetime` buy option. Map these choices to the iOS catalog independently.
- Lifetime's actual Play purchase-option ID is `yogaalarmprolifetime`, not `buy` (which was confused with the Buy purchase type). Correcting this filter allows the active lifetime product's price and checkout to appear. On iOS, match the actual App Store catalog identifiers rather than purchase-type labels.
- Always show Monthly, Yearly (annual), and Lifetime. Show localized store prices only; plans without prices display “Loading price…” during lookup or “Currently unavailable” afterward and cannot be selected or purchased. Default to Yearly when available, otherwise the first available plan; preserve the user's choice while it remains available. No introductory offers are selected.
- Open the paywall fully expanded with a visible Close action. Benefits, plans, restoration, and messages scroll independently above a fixed purchase area, which stays above the system navigation inset. Selected plans have a radio indicator and pale green highlight. Selecting a plan only updates the selection and price; it never launches checkout.
- The fixed purchase area shows the selected plan, localized billed amount and period, a full-width sky-blue (`#8ED8FF`) button with dark navy (`#102A43`) text, and renewal terms. Android labels subscriptions “Subscribe with Google Play” and lifetime “Buy with Google Play”, with a small Play icon. Use appropriate App Store wording/branding on iOS. Monthly/yearly say they renew monthly/annually and can be cancelled in the store; lifetime says “One-time payment. No subscription.”
- Keep unavailable plans visible even when other plans can be purchased. Offer a distinct Retry action when any plan is unavailable after loading or the store is disconnected; disable purchase when no plan is available or the store is disconnected. Restore Purchases remains an ownership action. The home entry says “GET PRO”, avoiding the implication of a free trial. Disable plan changes and repeat checkout while a purchase is opening.
- One entitlement source grants Pro for a purchased subscription or lifetime, with lifetime taking precedence. Pending payments never grant access; show a pending-payment message. Cancellation does not grant access. Already-owned errors trigger restoration. Active Pro disables new purchases; subscription owners can open store subscription management.
- Checkout cancellation returns to the paywall without an error or stuck purchase button. Payment failure leaves Pro locked and allows retry. Cancelling subscription renewal must not remove access before the paid entitlement expires.
- Settings → Pro displays “Subscription Pro active” and a prominent full-width outlined “Cancel subscription” button, with text explaining that cancellation is confirmed in the store and paid access continues until the period ends. After renewal cancellation, show “Subscription renewal canceled.” and change the button to “Manage subscription”. Include a separate “View renewal date in Google Play” link, or “View subscription end date in Google Play” after cancellation; use native App Store wording on iOS. The Pro sheet also provides subscription management. Android deep-links with the `yoga_alarm_pro` product and package.
- Lifetime settings display “Lifetime Pro active” and “One-time purchase. No renewal or expiry date.” Lifetime alone has no cancellation action. If a valid subscription also exists alongside lifetime, keep its renewal status and cancellation/date controls visible in Settings and the Pro sheet; explain that lifetime Pro access is unaffected by subscription cancellation. Lifetime still takes precedence for Pro access. Free users can open Get Pro from Settings.
- On store refresh after renewal cancellation, show “Renewal canceled” while preserving Pro through the paid period. Android reads the store's auto-renewing flag and currently directs users to Google Play for the exact end date. Do not invent a date from the initial purchase time. Displaying “Pro available until [localized date]” in Android remains pending backend verification of `purchases.subscriptionsv2.get` → `lineItems.expiryTime`; on iOS use verified native expiry/renewal information when available. Show renewal date for renewing subscriptions and access end date for canceled subscriptions; lifetime has no expiry.
- Restore on connection, foreground resume, and explicit Restore Purchases. Successful empty ownership queries remove access; temporary failures preserve the current session's entitlement. No entitlement persists across process death: offline cold starts stay free until the store responds.
- Remove the debug/free activation shortcut in all builds. All checkout uses the store; old debug preferences are ignored.
- Acknowledge accepted purchases (never consume lifetime), retry transient acknowledgement failures, and retry on future restoration. Interim verification trusts client purchase state; production needs backend verification, expiry/revocation handling and reliable acknowledgement. Restored subscriptions display “Subscription” because client records do not identify their base plan reliably.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/billing/PlayBillingManager.kt
- app/src/main/java/com/lurremcfly/yogaalarm/billing/PurchaseVerifier.kt
- app/src/main/java/com/lurremcfly/yogaalarm/model/ProPlan.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AppInfoSheets.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt
- app/src/main/res/drawable/ic_google_play.xml
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt
- app/src/test/java/com/lurremcfly/yogaalarm/billing/PurchaseVerifierTest.kt
- docs/play-billing-setup.md

iOS notes:
- Implement the same Monthly, Yearly, and non-consumable Lifetime choices using the independently configured App Store product IDs. Google Play product IDs, base-plan IDs, offer tokens, and acknowledgement calls are Android-specific and must not be copied into StoreKit code.
- Settings cancellation uses a full-width outlined button with a minimum 48-point touch height and labels “Cancel subscription” while renewal is enabled and “Manage subscription” after cancellation. Opening management does not itself cancel renewal; refresh verified store status when the app returns. Keep status, dates, and actions usable with larger text.
- Display “Next renewal: [localized date]” for an auto-renewing subscription and “Subscription ends: [localized date]” after cancellation, using verified StoreKit information. Do not wait for Android's backend integration when native verified date information is already available. If the date is unavailable, show an explicit App Store date/management link instead of estimating it. When lifetime and a subscription coexist, label the date as the subscription's date and keep lifetime access independent; lifetime alone has no end date.
- Preserve the existing native paywall design and use App Store localized prices. Keep one entitlement authority shared by all Pro feature gates; restore subscription and lifetime ownership after reinstall and when returning to the app.
- Handle native pending/deferred purchases without unlocking Pro. Use the native transaction verification and completion flow, including purchases that complete while the app is away. Lifetime ownership must remain restorable.
- Keep purchase verification isolated so the future backend can receive store-specific purchase evidence. Android's current client-only trust and offline cold-start behavior are interim limitations, not requirements to weaken iOS verification. Production must handle subscription expiry, grace period, account hold or equivalent store state, refunds/revocations, and durable transaction processing.
- Purchases restore through their originating store. No cross-store Google Play ↔ App Store entitlement transfer is implemented or promised.

iOS acceptance checks:
- Purchase Monthly, Yearly, and Lifetime separately; confirm localized prices, the selected store product, Pro unlock, and transaction completion.
- Cancel checkout, fail payment, and leave a purchase pending; none may grant new Pro. Complete a pending payment later and confirm access updates on return/restart.
- Reinstall and restore both subscription and lifetime purchases. Repeat Restore Purchases without duplicating entitlements or charging again.
- Confirm active Pro prevents a second purchase, lifetime takes precedence, and subscription management remains accessible for subscription owners.
- Open management from Settings and the Pro sheet. Cancel renewal, return to the app, and verify canceled status with Pro retained and the Settings button changed to “Manage subscription”. Verify lifetime alone explains no renewal/expiry, and lifetime plus a separate valid subscription still exposes subscription management. Confirm the correct paid-through date from verified store data; the Android in-app date remains a backend dependency.
- Verify Settings displays the localized verified date before and after cancellation, updates after renewal, and uses an App Store link when the date is unavailable. With lifetime plus a subscription, the subscription's end date must never imply lifetime Pro expires. Check date/button layout at accessibility text sizes.
- Verify renewal cancellation retains paid access, expiry/revocation removes the affected entitlement, and an independent valid lifetime purchase still grants Pro.
- Exercise unavailable products, temporary store failures, and reconnection. Verify there are no hardcoded price fallbacks or release free-Pro shortcuts.
- Verify the purchase area is visible immediately and stays fixed while scrolling on small screens and with larger text. Switching plans updates the billed amount, purchase wording, and renewal terms. Check the available-plan fallback, Retry, and separate restoration action.

Validation status:
- Subscription settings clarity update (2026-10-10): focused Android debug Kotlin compilation and whitespace checks passed. Cancellation/date links and lifetime-plus-subscription behavior still need on-device verification; exact in-app dates remain a backend dependency.
- Paywall layout update: focused Android debug Kotlin compilation and whitespace checks passed. On-device layout, large-text behavior, and real Google Play checkout still need verification.
- Android debug/release Kotlin compilation and eight focused billing tests passed. Real Google Play checkout still requires the Internal Testing checklist in `docs/play-billing-setup.md`.
- iOS implementation and StoreKit/device verification remain pending. Move this entire entry to Completed only after the native behavior is implemented and verified.

iOS implementation update (2026-10-10): verified subscription state is tracked separately from lifetime access, Settings/Pro show localized dates or native management fallback, and Settings uses the full-width cancellation/management control. Added cancellation/date/fallback and lifetime-plus-subscription regressions in ProPlanTests.swift. Structural checks and whitespace checks passed; Swift/Xcode are unavailable on this Linux host, so compilation, StoreKitTest execution, and device checks remain pending. The separate Test Pro access removal task remains pending.

### SYNC-009 — Save the pose selected after swiping back

Status: pending
Date: 2026-09-20
Android commit: <pending>

iOS implementation progress (2026-10-10):
- [x] Each idle, unlocked carousel selection updates the draft, including swiping back to the original pose; locked poses do not commit.
- [ ] Verify Mountain → Warrior II → Mountain through Save, reopen, and Test, plus other steps and locked-pose cases on iOS.

Changes:
- Each settled, unlocked pose selection updates the alarm draft, including returning to the pose that was selected when the editor opened. For example, Mountain → Warrior II → Mountain must save and test Mountain.
- Locked poses remain unavailable without Pro. Pose durations and routine order are unchanged.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmEditorScreen.kt

### SYNC-008 — Keep configured alarm times after a daylight-saving gap

Status: pending
Date: 2026-09-20
Android commit: <pending>

iOS implementation progress (2026-10-10):
- [x] Per-date configured-wall-time resolution, spring-gap minute preservation, overlap offset preference, and focused Stockholm regression tests authored.
- [ ] Run the iOS scheduling tests and verify native AlarmKit gap/overlap delivery and subsequent rescheduling; app next-occurrence calculations alone do not verify system delivery.

Changes:
- Resolve the configured local hour and minute independently for each candidate date. A spring daylight-saving gap may shift that day's alarm, but must not shift alarms on later dates.
- In Europe/Stockholm, 02:30 on March 29, 2026 resolves to 03:30. Scheduling the following Monday or Sunday must still use 02:30, including rescheduling after the gap-day alarm fires. One-time alarms moved to tomorrow follow the same rule.
- During an autumn overlap, preserve the current UTC offset when valid for the candidate time; otherwise use the zone's normal resolution. Existing weekday selection and strictly-future scheduling rules remain unchanged.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/alarm/AlarmScheduler.kt
- app/src/test/java/com/lurremcfly/yogaalarm/alarm/AlarmSchedulerTest.kt

### SYNC-007 — Pose illustration travels into the routine header

Status: pending
Date: 2026-09-17
Updated: 2026-10-20
Android commit: <pending>

iOS implementation progress (2026-10-10):
- [x] Single illustration travels from screen center at 3.75× after a one-second hold into the 96-point header slot over 0.65 seconds; presentation is separate from routine timing.
- [ ] Alternate each pose illustration's horizontal orientation on successive appearances, persisting the next orientation across sessions.
- [ ] Verify rapid pose changes, recreation/rotation, Reduce Motion, accessibility, and unchanged detection/countdown/control behavior on iOS.

Changes:
- When the routine first displays a pose or advances to a new one, show that pose's existing illustration centered on screen at about 3.75 times its final size. Hold it there for 1 second, then smoothly scale and move the same image into its existing small header slot over about 0.65 seconds.
- Render only one copy of the illustration during the transition so it appears to travel into place without flashing or overlapping a duplicate.
- Alternate each pose independently between its original image and a horizontal mirror, starting with the original on first use. Persist the next orientation across routines and app launches; alarm routines, test routines, and onboarding practice all count when the illustration appears, even if the user exits before completing the pose. Editor/catalog thumbnails do not count.
- Keep the chosen orientation throughout the large introduction, shrinking/moving animation, and small header display. Recomposition, rotation, background/resume, and camera retries must not advance alternation. Count the next-pose preview during the three-second transition as the start of that pose's appearance; entering its hold phase must not flip it again. Consecutive slots containing the same pose are separate appearances and restart the introduction with alternating orientation. Do not count the default placeholder pose before the routine starts or the completion screen.
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

iOS implementation progress (2026-10-10):
- [x] All four readiness rows remain visible while incomplete, with completed checks, actions, and a decorative bouncing pointer on only the first incomplete action.
- [x] Pointer stops after tapping its row; existing readiness refresh and Camera/AlarmKit permission state are reused.
- [ ] Verify grant/denial/Settings recovery, pointer advancement, app resume, VoiceOver, and Reduce Motion. AlarmKit authorization supplies the three alarm capability rows together on iOS.

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

iOS implementation progress (2026-10-10):
- [x] Privacy & safety is reached through header Settings; opening it dismisses Settings and shows the scrollable explanation and scalable policy action.
- [x] Privacy links use https://www.yogaalarm.com/privacy.
- [ ] Verify Settings-to-privacy presentation, policy opening, and accessibility text layout on iOS.

Changes:
- The Privacy & safety policy link opens https://www.yogaalarm.com/privacy.
- Remove the persistent Privacy & safety button from the bottom of the home screen. Place it under Settings, reached through the gear in the home header (see SYNC-012). Opening privacy closes Settings and shows the existing camera/privacy/safety explanation and Read privacy policy action. Let the explanation scroll and the policy button grow for larger text.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AppInfoSheets.kt

iOS notes:
- Use the same public policy URL.

### SYNC-004 — Completion screen stays awake for ten seconds

Status: pending
Date: 2026-09-16
Android commit: <pending>

iOS implementation progress (2026-10-10):
- [x] Session-owned monotonic ten-second foreground idle-timer deadline, immediate media/camera stop, cancellation on exit, and resume without extending the deadline.
- [ ] Lock-screen completion grace remains unmatched: the current iOS flow stops the system alarm before camera handoff and has no completed AlarmKit presentation.
- [ ] Verify real/test completion, immediate single completion recording, redraw/resume, early exit, and isolation from a subsequent routine on a physical iPhone.

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

iOS implementation progress (2026-10-10):
- [x] No automatic setup popup; home readiness actions and explicit camera start request access, with denial/Settings recovery and no legacy setup-flag dependency.
- [ ] Verify fresh/existing installs, cold-launch alarm routing, explicit permission prompts, and recovery on iOS. Separate Android alarm permissions are represented by AlarmKit authorization.

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

### SYNC-002 — Launcher artwork and safe-area spacing

Status: pending
Date: 2026-09-11
Updated: 2026-10-10
Android commit: fcf38b3a8d17f5cbb5e073b48c26707c12827e51 (2026-10-10 artwork update; earlier changes: <pending>)

iOS implementation progress (2026-10-10):
- [ ] Replace iOS icon assets with the newly supplied square sunrise/Warrior II artwork, using a suitable high-resolution source and preserving colors, silhouette, and proportions.
- [ ] Generate opaque iOS sizes without the old decorative frame or baked-in rounded/transparent corners; verify hands/feet remain visible under the installed platform mask.

Changes:
- Replace the framed launcher artwork with the supplied square artwork: a dark navy Warrior II silhouette over a coral/orange-to-yellow sunrise and navy ground, with no decorative border or baked-in rounded corners.
- Use the same artwork for all launcher sizes. Android's adaptive icon retains a uniform 4% foreground inset for safe-area spacing; the launcher supplies the final icon mask.

Relevant Android files:
- app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
- app/src/main/res/drawable/ic_launcher_foreground_inset.xml
- app/src/main/res/drawable-nodpi/ic_launcher_foreground.webp
- app/src/main/res/mipmap-mdpi/ic_launcher.png
- app/src/main/res/mipmap-hdpi/ic_launcher.png
- app/src/main/res/mipmap-xhdpi/ic_launcher.png
- app/src/main/res/mipmap-xxhdpi/ic_launcher.png
- app/src/main/res/mipmap-xxxhdpi/ic_launcher.png

iOS notes:
- Generate opaque iOS icon sizes from the new square artwork, preserving its colors, silhouette, and proportions. Let iOS apply rounded corners; do not add the old frame or transparent corners.
- Check the installed icon matches the new artwork and the pose's hands and feet remain visible within the platform mask.

### SYNC-001 — Yoga-only dismissal, emergency stops, and wake-up streak

Status: pending
Date: 2026-09-11
Android commit: <pending> (2026-10-17 deferred stop reset; previous streak update: 31375f9c24d5046f7eb1ad8dda6433d3a634f4b3)

iOS implementation progress (2026-10-10):
- [x] Sound/Snooze/Stop button editor order and copy, default-on Stop setting, vibration in Sound, conditional in-app Stop, and unrestricted test navigation.
- [x] Emergency 5/15-second holds with three monthly passes, after-completion warning/pass use, immediate technical camera exits, and streak-neutral system Stop recording.
- [x] Explicit alarm edits cancel pending snoozes with retryable cleanup; reconciliation retains pending snooze ownership/count.
- [x] Fired completions are idempotent and count once per local day; test routines do not earn days/markers; streak count is shown on home/completion/emergency UI.
- [ ] Replace consecutive-calendar-day reset logic with scheduled-day accounting that preserves unscheduled weekends and all-disabled periods.
- [ ] Persist exact due dates/effective first occurrences and evaluate the previous schedule before mutations; handle edits before/after due time, unchanged alarms, one-offs, firing/snooze dates, and rescheduling.
- [ ] Normal/emergency stops must preserve the streak for the rest of today and retain today's due-date obligation. Reset on the next local day only if yoga was not completed; keep technical/system exits neutral.
- [ ] Preserve saved progress on upgrade, start schedule tracking from the current time, and remove recovery of previously reset counts from old completion markers.
- [ ] Support or explicitly resolve current scheduling rejection of sound-disabled/vibration-disabled combinations and system Stop/secondary-action differences.
- [ ] Verify upcoming-shortcut routing does not start playback or mark an alarm fired, and clock/timezone rescheduling preserves snooze/count.
- [ ] Implement the adaptive zero/positive streak markers from SYNC-011 and run the full iOS streak/lifecycle acceptance matrix.

Changes:
- Alarm editing has three primary rows in this order: Sound, Snooze, and "Stop button." The Stop button option defaults to on for new and existing alarms, uses the supporting text "Allow manual alarm stop," and shows the manual Stop action when enabled. Turning it off hides the normal Stop action.
- Vibration remains independently configurable but has moved into the Sound sheet. Sound-only, vibration-only, both, and neither are supported.
- When "Stop button" is off for a fired alarm, the normal Stop action is hidden and Android Back remains blocked. Snooze remains available. Test routines always retain normal navigation.
- While an alarm is ringing, its system notification offers a clear Start movement action, a Stop alarm action only when manual stopping is enabled, and a Snooze action only while configured snoozes remain. Stopping from the notification silences only that alarm and does not break the daily streak, so completing a later alarm that day can still continue it.
- Opening a system shortcut for an upcoming alarm opens the app without starting playback or treating the alarm as fired. Ringing notifications still open the active routine.
- Rescheduling enabled alarms after a clock or timezone change must preserve any pending snooze and its remaining snooze count. Explicitly editing, disabling, or deleting an alarm still cancels its pending snooze.
- A subtle Emergency stop remains available. Before today's yoga is completed, a positive streak shows "Complete yoga before midnight to keep your N-day yoga streak." After completion today, it says stopping keeps the streak. It requires a 5-second hold while one of three monthly passes remains. After all passes are used, it requires a 15-second hold but is never blocked or paywalled. An emergency stop still consumes an available pass after today's completion.
- Camera permission denial or a camera failure provides an immediate technical Stop action. Technical exits do not consume a pass or break the streak.
- Updated 2026-10-17: the global yoga streak counts completed alarm days, skipping unscheduled days. Completing at least one genuinely fired alarm through yoga starts/extends it once per local calendar day. Unscheduled days preserve the count without incrementing it, including weekends and periods with every alarm disabled. Example: Friday count 5 remains 5 over a free weekend; completing Monday gives 6. Missing a day on which at least one alarm became due resets the streak when that calendar day ends; today remains available for completion. Snoozing preserves the existing rules. A normal or emergency stop preserves the streak and last completion date, and records today as due. Completing a later fired alarm that same day extends the existing streak; without completion it resets on the next local day, even if alarms were subsequently disabled or deleted. Repeated stops never increment the streak, and stopping after today's completion preserves it. Emergency stops still consume available monthly passes. Previously missed days still reset the streak when evaluated; stopping cannot restore an already broken streak. Test routines never count.
- Persist due calendar dates and the active schedule's effective first occurrence. Before saving any alarm creation/edit/enable/disable/delete, evaluate the previous schedule through the current time. An alarm removed before it becomes due creates no obligation; removal after it became due cannot erase that day's obligation. Adding/changing an alarm cannot create past missed days. Union multiple alarms on the same date into one obligation. For a one-off alarm, retain only its actual scheduled date, including when it automatically disables as it fires; do not treat an empty weekday set as daily recurrence. Record the scheduler's exact trigger date after successful scheduling/rescheduling, including boot and clock-change rescheduling. Preserve unchanged alarms' occurrence dates when saving unrelated edits. Keep recent due-date and actual-completion markers independently of the streak count.
- On upgrade without schedule history, preserve the existing saved streak and completion dates, then begin schedule tracking from the current time. A genuinely fired alarm always marks its actual firing date as scheduled, including the first alarm after upgrade and a snooze firing. Do not retroactively judge past dates using the current alarm settings or claim to restore counts already reset by the old behavior. Preserve progress over app restarts and refresh it after alarm changes, app resume, and midnight.
- The streak appears on the alarm home screen, the completed-routine screen, and in the emergency-stop warning.
- With no streak, the home card shows count 0 and the adaptive rolling day markers described in SYNC-011. Test routines still do not start a streak or add completed-day markers.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/model/AlarmConfig.kt
- app/src/main/java/com/lurremcfly/yogaalarm/model/WakeProgress.kt
- app/src/main/java/com/lurremcfly/yogaalarm/model/StreakSchedule.kt
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
- app/src/main/java/com/lurremcfly/yogaalarm/alarm/AlarmReceiver.kt
- app/src/main/java/com/lurremcfly/yogaalarm/alarm/AlarmScheduler.kt
- app/src/main/java/com/lurremcfly/yogaalarm/alarm/BootReceiver.kt

iOS notes:
- Verify weekday-only alarms across a weekend, a missed Monday, all alarms disabled, multiple alarms on one date, edits before/after an alarm time, one-off auto-disable, migration, restart, and month/year boundaries. For this update, leave the Android commit placeholder until a commit exists.
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

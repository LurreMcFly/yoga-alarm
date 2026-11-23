# iOS UX unification — implementation handoff

Date: 2026-11-09. Target repository: `/home/jolu/Projects/yoga-alarm-ios`.

Current-state note (2026-11-11): read the [fresh alignment audit](android-ios-ux-audit.md) before starting work. Try pose, ringing/recovery hierarchy, test completion, and the former panel-interaction gaps are now resolved in source. Small copy differences and native acceptance remain. This document describes the original plan, not a live completion checklist.

## Read this first

Implement the confirmed choices in [the Android-owned decision record](android-ios-ux-audit.md#confirmed-user-choices--2026-11-09). Do not apply rejected recommendations from the original audit.

This native iOS plan is stored in the Android repository for handoff. It is not a second sync queue. `../yoga_alarm/IOS_SYNC.md`, read from the iOS repository, remains authoritative for implementation status.

Read both repositories' `AGENTS.md` files. Read every Pending queue item before parity work. Use the [Android handoff](android-ux-unification-handoff.md) to understand reference changes and test cases. Recheck Android implementation commits as they land.

Complete Android reference changes and queue updates before declaring the matching iOS work complete. Parallel preparation is safe. Do not independently invent another set of shared behavior rules.

For monetization, retain the native planning/review step required by `SYNC-028`. Reconcile this handoff with the iOS native monetization plan before editing that logic. If the required local plan is absent, create it from this native file map and a fresh source review. Do not copy obsolete confirmation/restoration/testing requirements into it.

Source paths below are relative to the iOS repository. Read-only review used HEAD `9860dd3` plus the current working tree. No Xcode, simulator, StoreKit, or device validation was performed for this handoff.

## Preserve the current working tree

The source already contains uncommitted camera-first and system-Stop recovery work. Read `docs/camera-first-alarm-validation.md` before edits.

Existing modified files include:

- `YogaAlarm/Alarms/AlarmListModel.swift`
- `YogaAlarm/Alarms/ProductAlarmExperienceModel.swift`
- `YogaAlarm/Alarms/ProductAlarmService.swift`
- `YogaAlarm/Camera/CameraPipeline.swift`
- `YogaAlarm/Intents/AlarmIntents.swift`
- `YogaAlarm/Models/AlarmConfiguration.swift`
- `YogaAlarm/Routine/RoutineSessionModel.swift`
- `YogaAlarm/UI/AlarmEditorView.swift`
- `YogaAlarm/UI/ProductAlarmExperienceView.swift`
- `YogaAlarm/UI/RoutinePracticeView.swift`
- `YogaAlarm/YogaAlarmApp.swift`
- `YogaAlarmTests/OnboardingLayoutTests.swift`
- `YogaAlarmTests/ProductAlarmSchedulingTests.swift`
- `YogaAlarmTests/RoutineEngineTests.swift`

Recheck status when starting. Preserve these changes and the new camera validation document. Do not reset them to match the older audit.

### Camera-first flow to preserve

1. When Snooze remains available, the native action says Open Yoga Alarm.
2. It opens a live preview. Pose recognition, held seconds, and routine progress stay idle before Start.
3. When Snooze is unavailable, the native action says Start Yoga. The current flow starts the routine after preview readiness.
4. Active yoga has no Snooze. Preserve the existing Options flow and valid stop/exit handling.
5. The successful real-alarm path has no separate preparation page. Keep it that way.

The approved in-app action label is Start. The new preview currently says Start Yoga; change that visible preview label to Start without changing its behavior. Keep native Open Yoga Alarm / Start Yoga labels.

The placement-picture, top-Back, and preparation-footer choices concern **test preparation**. Do not insert that test page into the successful real-alarm path.

Keep the current system-Stop recovery prototype separate. Preserve its identity, finite bounds, deadlines, remaining snoozes, and terminal cleanup. Do not claim reliable recovery from source tests. Native acceptance remains required.

## Accepted scope

| Area | Required behavior | Decision IDs |
| --- | --- | --- |
| Native exceptions | Keep native controls, current Save/Cancel size, and intentional normal-navigation timing. Allow supported sound/vibration differences. Add no Stop-setting explanation. | 1, 6, 7, 10, 18, 21, 30 |
| Home | Remove Next/Snoozed time text; one-line routine with tail truncation; keep schedule progress, failure, and Retry. | 2, 3, 15, 19 |
| Time format | Follow phone by default; optional 24-hour or 12-hour with AM/PM; apply throughout app-owned wall-clock text and the editor. | 47, 47a–c |
| Trial and ownership | Pro trial; one Save; automatic future-only free conversion; no old-routine restoration or permanent free-settings Settings page. Restore purchases remains. | 12–14, 45, 55, 55a, 57 |
| Internal testing | Internal builds only, including effective model policy. Public builds ignore old test flags. | 16 |
| Layout and routine | Adaptive portrait/landscape; Pose x of y; paused inactive transition; no active Snooze; Android picture-arrival timing. | 4, 5, 8, 9, 20, 32 |
| Copy and preparation | US English; approved ring/test text; tour placement picture; top Back; no rejected preparation footer. | 17, 23, 33, 34, 37, 38, 52–54 |
| Tour and setup | Slow tour teaching motion, six-second cards, no invitation trial paragraph, Allow, completed/total progress, short permission explanation. | 11, 24, 36, 48, 49 |
| Editor and sound | Android switch colors; direct locked-sound offer; PRO badge; music emoji; immediate Snooze enable; swipe-only pose selection; swipe-dismissed option panels. | 22, 25–28, 35, 44, 51, 56 |
| Emergency and recovery UI | Android emergency hold/accessibility policy and no-streak text; remove Snoozing…, Try sound again, and Try saving again UI. | 39–43, 50 |
| Links | Privacy Policy and Terms of Use on the offer; Support on Privacy and Safety. | 29, 46 |
| Native entry | Preserve current iOS system labels. Use Start inside the app. | 31 |

Do not change free/Pro limits, pose scores, snooze counts, trial-date counting, subscription ownership, or paid-offline policy as incidental cleanup.

## Step 1 — Lock down internal-only testing

Files: `YogaAlarm/Alarms/AlarmListModel.swift`, `YogaAlarm/UI/AlarmHomeView.swift`, `YogaAlarm/Models/ProPreview.swift`, project build settings if needed.

1. Use Debug as the current internal build. Public Release/TestFlight configurations must default to no testing capability.
2. Put the UI behind that capability. Keep genuine purchase controls available in public builds.
3. Gate `testingModeEnabled` and related effective access in the model. Ignoring only the Settings UI is insufficient.
4. Public builds must ignore stored Test Pro access and Test trial records. Use normal ownership and normal trial state.
5. Reject test-setting mutations when the build is public. Avoid a startup interval that briefly grants test access.
6. Keep old test preferences readable without honoring them. Do not delete real transactions, normal trial progress, alarms, or history.
7. Keep `PRO_PREVIEW_ROLLOUT` independent. It does not identify an internal build.
8. If an internal signed Release is required, use an explicit compile-time condition enabled only in that configuration. Do not infer it from receipts or TestFlight at runtime.

A small build-policy value, with an explicit test override for unit tests, is sufficient. No new account service or build-flavor system is required.

## Step 2 — Single Save, automatic expiry, and legacy data

Files: `YogaAlarm/UI/AlarmEditorView.swift`, `YogaAlarm/Alarms/AlarmListModel.swift`, `YogaAlarm/Models/ProPreview.swift`, `YogaAlarm/Models/AlarmConfiguration.swift`, `YogaAlarm/UI/AlarmHomeView.swift`.

### Current blockers

- `AlarmEditorView` intercepts Save with `showsFallbackReview` and `TrialFallbackReview`.
- `AlarmListModel.save` rejects some trial saves unless metadata matches `confirmedFor`.
- `convertingExpiredCustomizations` skips records with missing or mismatched agreement data.
- Some expired legacy alarms still require manual free review.

Remove these requirements together. Deleting the sheet alone breaks Save and leaves legacy alarms on old policy.

### Accepted automatic rule

Use `AlarmCustomization.suggestedFreeFallback` as the starting point. Derive from the current saved future routine, not an archived original or active occurrence snapshot.

| Position | Keep an existing free pose | Otherwise use |
| --- | --- | --- |
| 1 | Yes | Mountain |
| 2 | Yes | Warrior II |
| 3 | Yes | Tree |

Use three positions and 20 seconds each. Fill missing positions. Ignore later positions for the free routine. Keep duplicate free poses. Keep a free sound; otherwise use Sunbird Morning Call.

For example, Chair → Tree → Triangle becomes Mountain → Tree → Tree. No confirmation page or separate confirmation button remains.

### Required model changes

1. Prepare valid derived fallback metadata during Save. Preserve checked persistence and stale-draft protection.
2. Remove the human-confirmation gate. A historical field can remain for compatibility; do not treat it as evidence of new user consent.
3. Apply the approved deterministic rule when an unconverted trial expires. Old confirmation requirements, manually selected fallback data, or archived originals must not select another routine or block conversion. Derive from the current saved trial customization.
4. Reconcile legacy missing/mismatched metadata through a checked model transition. Do not silently normalize all alarms while decoding the file.
5. Leave already-converted free alarms current. Do not restore Pro settings on purchase or restart.
6. Preserve optional Codable fields and schema compatibility. Historical `original`/`converted` fields may remain stored and inactive.
7. Remove review-specific editor banners, modals, buttons, and blocking flags that have no remaining function.
8. Retain legitimate purchase-resolution checks. Do not turn a pending transaction into paid access.

### Preserve occurrence and purchase safety

Use the current `stageFutureCustomization` and owned-registration logic. A ringing or snoozed occurrence must retain its snapshot, occurrence ID, remaining snoozes, and terminal cleanup.

- Convert future configuration only. Do not consume a one-time alarm twice or change weekday identity.
- Keep completion, trial accounting, pending outcomes, and conversion recoverable after a failed write.
- Keep one counted successful real-alarm date per local date. Tests and practice do not count.
- Do not convert verified paid owners. Do not equate temporary verification/offline uncertainty with trial expiry.
- Retain native StoreKit verification. Do not weaken it to match an Android implementation limitation.

### Remove routine restoration, retain purchase restoration

Remove Settings restoration/free-settings destinations, automatic restoration presentation, `TrialRestorationView`, selection state, and their user-facing handlers. Keep the current routine after purchase.

Do not remove Restore purchases, StoreKit transaction recovery, or subscription management. Do not erase historical customization data merely because its restoration UI is gone.

Update analytics and stale messages. Do not report a fallback-confirmed or routine-restored event for a removed interaction. Keep replay protection and optional collection behavior.

## Step 3 — Time format and the editor

Files: `YogaAlarm/UI/AlarmHomeView.swift`, `YogaAlarm/UI/AlarmEditorView.swift`, `YogaAlarm/UI/ProductAlarmExperienceView.swift`, `YogaAlarm/UI/OnboardingView.swift`, `YogaAlarm/Alarms/AlarmInactivityMonitor.swift`. Use the existing preferences owner.

1. Add Time format under Preferences: Follow phone, 24-hour, and 12-hour (AM/PM).
2. Persist a stable value. Missing or invalid data resolves to Follow phone.
3. Resolve the current system format again after activation and locale/time-preference changes.
4. Use one small shared formatting path for the multiple app callers. Keep US-English product wording separate from clock-format resolution.
5. Update home clocks and their accessibility labels, ringing/fallback clocks, tour summaries, and app-written missed-report time ranges.
6. Add a 1–12 hour wheel and AM/PM choice in 12-hour mode. Preserve the existing native wheel behavior and draft.
7. Keep stored hours 0–23. Map 12 AM to 0, 12 PM to 12, and 7 PM to 19.
8. Preserve minutes when switching format or AM/PM. A format change must not call Save or reschedule an alarm.
9. Keep weekday, timezone, and DST calculations in `ProductAlarmSchedule` unchanged.

Native AlarmKit clocks and other OS-owned time text follow the OS. The app preference controls app-authored text, not system chrome. Current widget countdowns are elapsed durations; do not change them into wall-clock text or add an App Group solely for this feature.

## Step 4 — Adaptive layout and camera orientation

Files: `YogaAlarm/Info.plist`, `YogaAlarm/UI/RoutinePracticeView.swift`, `YogaAlarm/UI/AlarmEditorView.swift`, `YogaAlarm/Camera/CameraPipeline.swift`, `YogaAlarm/Camera/CameraPreview.swift`, `YogaAlarm/Camera/MediaPipePoseLandmarker.swift`, `YogaAlarm/Camera/PreviewProjection.swift`.

This is a camera-coordinate change, not only a plist change. Current preview/capture paths use fixed 90-degree assumptions.

1. Add supported landscape orientations while preserving portrait. Use available view geometry for compact/wide layouts.
2. Coordinate capture rotation, preview rotation, MediaPipe input orientation, mirroring, and skeleton projection.
3. Use one consistent transform strategy. Do not rotate buffers and then apply the same rotation again in projection.
4. Check that scoring still receives the expected landmark coordinate system. Left/right pose rules must not change with screen rotation.
5. Preserve the new preview-only camera mode. Rotation must not enable pose detection before Start.
6. Adapt active guide/feedback placement to short/wide screens. Use Android's compact split as a reference, not a required pixel geometry.
7. Make test preparation scrollable. Keep the image and controls reachable with large text and safe-area insets.
8. Let the editor time wheel scroll away in short windows. Do not pin it over poses or the action bar.

Verify SDK-specific orientation assumptions against current official Apple documentation before choosing API changes. Record the chosen coordinate convention in the focused tests. Device checks must cover mirrored front-camera behavior and camera restart after rotation.

## Step 5 — Home and routine presentation

### Home

Files: `YogaAlarm/UI/AlarmHomeView.swift`, `YogaAlarm/Alarms/AlarmListModel.swift`.

- Remove the visible Next / Snoozed label from `AlarmSummaryCard`.
- Keep `nextConfirmedOccurrence` and related model helpers where other flows still need them.
- Change the routine summary to one line with tail truncation.
- Preserve existing schedule progress, needs-attention, Retry, failed-load protection, and save-and-switch-off behavior.
- Keep current card type/shape decisions unless a confirmed requirement changes them.

### Routine counter and transition

Files: `YogaAlarm/UI/RoutinePracticeView.swift`, `YogaAlarm/Routine/RoutineEngine.swift`, `YogaAlarm/Routine/RoutineSessionModel.swift`.

- Use Pose x of y instead of a bare fraction. Match the picture shown during next-pose introduction.
- Background transition pause is already implemented. Preserve `suspend`/`resume` and preview-specific suspension.
- Keep no held-time credit before Start or while inactive. Keep existing scores and three-second transitions.
- Change `TravelingPoseIllustration` to a 1,000 ms center hold and 650 ms travel.
- Keep slot identity, persistent mirroring, interruption cancellation, and Reduce Motion behavior.
- Do not make the visual animation gate recognition or routine timers.
- Remove Finding you… from product-visible branches without removing detection-startup logic.

### Ringing and test preparation

Use Android's information hierarchy where the dedicated ringing/failure screen is shown: clock and alarm name are prominent. Do not reintroduce that screen in front of a successful camera-first handoff.

Use the approved ringing instruction: “Place your phone where your full body fits on screen.”

For test preparation:

- Replace `figure.yoga` with the existing `onboarding_phone_setup` asset.
- Show top Back. Remove the bottom Back to alarm control when it duplicates that action.
- Use “Prop your phone upright on the floor facing you, take about two steps back, then follow the pose.”
- Remove the preparation-only camera/privacy footer. Do not remove privacy policy content, tour camera statements, or the unchanged ringing footer.
- Keep the camera off until the explicit test-start action.

Ordinary test completion must say “Test complete. Your streak did not change.” Keep quiet five-second tour practice separate, including Continue setup. Retain the ten-second completed-screen idle-timer grace.

History of audit F1: on November 10, Try pose reached the diagnostic `PosePracticeView`. The November 11 source recheck confirms it now uses `RoutinePracticeView` with the selected step's duration. F1 is resolved in source. Keep the timed product route and verify it on a device.

## Step 6 — Emergency stop and removed error controls

Files: `YogaAlarm/UI/RoutinePracticeView.swift`, `YogaAlarm/UI/ProductAlarmExperienceView.swift`, `YogaAlarm/Alarms/ProductAlarmExperienceModel.swift`.

### Emergency stop

1. Keep the five-second touch hold when a pass remains, or fifteen seconds otherwise.
2. Add an accessible Stop alarm activation without the timed hold. Invoke the same `onConfirm` route as touch.
3. Keep the same pass, streak, and terminal-outcome rules. Do not add a new bypass for ordinary touch taps.
4. Use the approved no-streak sentence instead of a 0-day warning.
5. Cancel hold progress on interruption or dismissal. Prevent duplicate confirmation.

### Removed UI, retained safety

- Remove Snoozing… label changes in both preview and ringing fallback. Keep `isSnoozing` and repeated-action guards.
- Remove Try sound again buttons. Keep audio errors, safe termination, and model cleanup.
- Remove Try saving again buttons and messages that instruct users to press them.
- Keep camera recovery controls. The user did not remove Try camera again.

**Do not strand a stopped session after removing its only retry button.** `ProductAlarmExperienceModel.stop` can fail after silencing the alarm. Preserve the original pending outcome and make recovery automatic through the existing owned model/lifecycle path. Use bounded retries; never restart the camera or re-ring to retry persistence.

Normal completion already has a generic Done path that retries a failed save. Keep that responsibility. Preserve useful error feedback and outcome replay protection. Do not replace the removed control with another dedicated retry button under a different label.

Use the existing failed-stop regression to verify that the outcome does not change on recovery. Failed writes must not lose an emergency pass, add a false completion, or clear a pending result.

## Step 7 — Tour, permission setup, switches, and option panels

Files: `YogaAlarm/UI/OnboardingView.swift`, `YogaAlarm/UI/OnboardingSpotlight.swift`, `YogaAlarm/UI/AlarmHomeView.swift`, `YogaAlarm/UI/CatalogPickerViews.swift`, `YogaAlarm/UI/AlarmEditorView.swift`, `YogaAlarm/Design/DesignTokens.swift`, `YogaAlarm/UI/ProPaywallView.swift`.

| Area | Required change |
| --- | --- |
| Tour invitation | Remove its trial paragraph. Keep the invitation and Later behavior. |
| Welcome | Use Pro trial. Remove the no-payment/no-automatic-charge sentence. Do not claim an upfront fallback confirmation remains. |
| Tour motion | Use six seconds per preview card and the approved slower teaching gestures. Align tour-specific fade/scroll motion with Android. Preserve Reduce Motion. |
| Navigation exception | Keep ordinary home/editor transition timing intentionally faster on iOS. Do not globally replace unrelated animation durations. |
| Permission setup and recovery | Show completed/total for the actual required iOS permissions. Use Allow on app-owned permission actions in setup, product camera recovery, and test camera recovery. Replace visible Open Settings / Allow camera permission labels, but keep correct settings routing and descriptive accessibility labels. Keep the separate Try camera again control for technical camera failures. |
| Permission explanation | Use “So your alarm rings and the camera can check your poses.” Show the full approved phone-placement instruction on the tour placement page as well as test preparation. |
| Switches | Use Android forest on-thumb, lime on-track, and darker off-track in guided and normal states. Preserve native control behavior. |
| Locked sounds | Remove `showsUpgrade` confirmation. Call `onUpgrade` directly after stopping preview. Keep the PRO badge and no locked playback. |
| Preview label | Use `🎵 Stop`, not the square symbol. Keep ten-second preview and background/dismiss cleanup. |
| Snooze panel | Changing duration or count immediately enables Snooze in the draft. Swipe dismissal retains those draft changes. |
| Option-panel dismissal | Remove normal Done buttons from sound, snooze, and duration panels. Keep swipe dismissal and tour-specific locking/Next behavior. |
| Duration panel | Do not add the rejected free-20-second sentence. Keep actual limits and upgrade routing. |
| Pose selection | Remove touch-tap selection of unlocked neighboring cards. Keep swipe selection, last valid selection after locked browsing, locked Pro actions, and the independent Try pose/duration controls. |
| Accessibility selection | Preserve an accessible equivalent to swipe selection. Touch swipe-only does not require making the carousel unusable with VoiceOver. Prefer adjustable next/previous navigation. |
| Vibration | Keep the extra Gentle pulse text absent. Respect supported platform modes. |
| Legal and support | Existing Privacy Policy, Terms of Use, and Support destinations already satisfy the main requirement. Preserve them and match approved capitalization. |
| US English | Use Practice, Customize, recognize, and other US spellings in shared visible copy. Do not rename stored identifiers as a spelling cleanup. |

The approved tour timing is distinct from ordinary navigation timing. Change the tour-specific `OnboardingFlow`, finger, scrolling, and overview paths where needed. Do not slow the camera-first alarm handoff.

For unsupported sound/vibration choices, retain truthful capability handling. Do not let a selectable unsupported choice fail only on Save. Verify supported native behavior before enabling a mode; current guards are not proof of a universal OS restriction.

## Queue coordination

Use the Android queue updates and new time-format ID created during Android implementation. Do not reserve separate IDs or create `ANDROID_SYNC.md`.

| Work | Existing queue references |
| --- | --- |
| Trial Save, automatic conversion, legacy records, no restoration, internal testing | `SYNC-028`; update old conflicting wording, not unrelated StoreKit/occurrence requirements. |
| Time format | The new next-available ID assigned by Android. Cross-reference `SYNC-017`, `SYNC-018`, and notification formatting where relevant. |
| Home status/Retry and Save outcome | `SYNC-019`; keep already-present source behavior and run acceptance. |
| Tour, permission, placement, copy | `SYNC-016`, `SYNC-006`, `SYNC-003` as applicable. Keep normal-navigation timing exception explicit. |
| Adaptive camera/editor | `SYNC-018`; do not claim complete after only changing orientations in the plist. |
| Counter, paused transition, test completion, picture timing | `SYNC-011`, `SYNC-007`. |
| One-line summary, duration text, links | `SYNC-012`, `SYNC-013`, `SYNC-010`, `SYNC-005`. |
| Preserve native delivery and limits | Keep `SYNC-004`, `SYNC-026`, `SYNC-027`, and current camera-first validation requirements visible. |

A source check does not complete a queue item. Keep unrelated pending checks pending. Move an entire entry to Completed only after its required native verification passes.

## Focused tests on macOS

Use the current Xcode workspace and scheme. The existing full `scripts/run-mac-validation.sh` is a later integration sweep, not the default first check.

| Tests | Cases to retain or add |
| --- | --- |
| `ProPreviewTests` | Positional free fallback, duplicate poses, missing positions, free/Pro sound, compatible metadata decoding. |
| `ProductAlarmSchedulingTests` | One Save without confirmation; legacy missing/mismatched data; expiry; paid/unknown ownership; snoozed snapshot; schedule failure; current routine after purchase. Preserve native recovery and stale-action tests. |
| `AlarmPersistenceTests` | Old files; optional archives retained; failed write; app restart; time preference migration if stored with model data. |
| `ProductAlarmExperienceTests` | Camera-first handoff; preview idle before Start; stop during preparation; failed-stop outcome preserved; bounded automatic save recovery without the removed button. |
| `RoutineEngineTests` | Keep `testTransitionAnnouncesNextPoseAndDoesNotAdvanceWhileSuspended`; preserve preview inactivity, hold pause, and foreground return. |
| `OnboardingLayoutTests` | Large text, short/wide windows, tour spotlight targets, 12-hour editor geometry, preview route continuity. |
| `AlarmInactivityMonitorTests` | Selected app time format in reports; unchanged duration/deadline behavior; no new background guarantee. |
| Focused format/build-policy tests | Follow phone/default/invalid preference; midnight/noon; AM/PM changes; canonical hour preserved; public build ignores old test flags. |
| Focused projection/accessibility checks | Rotation/mirroring transforms; overlay alignment; no scoring rotation drift; screen reader Emergency stop uses the same outcome handler. |

`ProductAlarmExperienceTests` is a class inside `YogaAlarmTests/ProductAlarmSchedulingTests.swift`. Do not look for a separate file or drop the class from scoped validation.

Example commands from the iOS root, after selecting an installed simulator:

```bash
# Set IOS_DESTINATION to an actual installed iOS Simulator destination.
bash -o pipefail -c 'xcodebuild -workspace YogaAlarm.xcworkspace -scheme YogaAlarm -destination "$IOS_DESTINATION" -only-testing:YogaAlarmTests/ProPreviewTests -only-testing:YogaAlarmTests/ProductAlarmSchedulingTests -only-testing:YogaAlarmTests/ProductAlarmExperienceTests test 2>&1 | tail -c 6000'
bash -o pipefail -c 'xcodebuild -workspace YogaAlarm.xcworkspace -scheme YogaAlarm -destination "$IOS_DESTINATION" -only-testing:YogaAlarmTests/RoutineEngineTests -only-testing:YogaAlarmTests/OnboardingLayoutTests -only-testing:YogaAlarmTests/AlarmInactivityMonitorTests test 2>&1 | tail -c 6000'
```

Update old tests that require upfront confirmation, pending manual free review, or routine restoration. Do not delete safety tests just because the old UI is gone. Native compilation and tests require macOS/Xcode; source-verification scripts do not substitute for them.

## Physical-device acceptance and completion report

1. Re-save existing alarms before comparing changed native action labels. Old registrations can retain old strings.
2. Exercise native Open Yoga Alarm with Snooze, and Start Yoga without it. Verify preview-only and automatic-start branches separately.
3. Confirm the in-app action says Start. Confirm no active-yoga Snooze and correct remaining uses before starting.
4. Rotate during preview, hold, pause, transition, and test preparation. Check camera framing, skeleton alignment, scoring, and no duplicate sessions.
5. Background during transition. Return and verify only the remaining active seconds elapse.
6. Check Follow phone, explicit 12/24 modes, midnight/noon, large text, and changed phone settings after resume. Alarm instants must be unchanged.
7. Save a trial routine once; complete the fifth qualifying real-alarm date. Confirm future-only conversion and preserved snoozed snapshots.
8. Check legacy records, failed writes, pending purchases, verified ownership, and process death. Confirm no restoration of old routines.
9. Inspect a public Release build with old test flags seeded. Confirm genuine purchase/trial behavior and no test UI.
10. Check swipe-only touch selection and accessible pose navigation. Check touch hold and VoiceOver Emergency stop.
11. Force sound/save failures. Confirm rejected buttons/text are absent, outcomes remain recoverable, and the stopped app is not stranded.
12. Keep the full native recovery checks from `docs/camera-first-alarm-validation.md`. Do not claim suspended/terminated guarantees that were not measured.
13. Check all approved copy, placement artwork, legal links, Support, sheet swipe dismissal, Snooze draft updates, and one-line home summaries.

Report files changed, Android reference commits, updated SYNC IDs, test results, device/OS evidence, and remaining issues. Separate already-present source behavior from newly implemented work. Keep stronger native purchase verification and unrelated uncommitted changes intact.

# Android UX unification — implementation handoff

Date: 2026-11-09. Target repository: `/home/jolu/Projects/yoga_alarm`.

Current-state note (2026-11-11): read the [fresh alignment audit](android-ios-ux-audit.md) before starting work. The main iOS flow gaps are now resolved in source. A few shared-copy differences remain, including Android's tour-pose hint and quiet-practice instruction. This document describes the original plan, not a live completion checklist.

## Read this first

Implement the confirmed choices in [the decision record](android-ios-ux-audit.md#confirmed-user-choices--2026-11-09). The original audit recommendations are not blanket approval. The decision record takes priority when the two disagree.

This document is an implementation plan. It does not report completed app changes. `IOS_SYNC.md` remains the only cross-platform implementation queue.

Read `AGENTS.md`, the decision record, and the relevant `IOS_SYNC.md` entries before editing. Preserve existing changes. Do not edit the iOS repository from this task.

The source review used Android HEAD `94be4d7` and its working tree. The following files already had changes:

- `IOS_SYNC.md`
- `app/build.gradle.kts`
- `app/release/app-release.aab`
- `app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt`
- `app/src/test/java/com/lurremcfly/yogaalarm/alarm/AlarmNotificationTest.kt`
- `docs/monetization-internal-testing.md`

Recheck this list when work starts. Do not reset it, regenerate the release bundle, or commit unrelated work.

Source paths beginning with `ui/`, `model/`, `data/`, `alarm/`, or `billing/` below are relative to `app/src/main/java/com/lurremcfly/yogaalarm/`.

## Required outcome and boundaries

Android remains the reference implementation. Port the approved iOS improvements into Android where needed. Keep the approved Android behavior where it already exists.

| Area | Accepted behavior | Decision IDs |
| --- | --- | --- |
| Platform appearance | Keep native control styles, current Save/Cancel bar sizes, and current normal navigation timing. Do not force identical animation durations. | 1, 10, 18, 21, 30 |
| Home | Show scheduling progress, failure, and Retry. Keep the routine summary to one line with an ellipsis. Do not add Next/Snoozed time text. | 2, 3, 15, 19 |
| Time format | Add Follow phone, 24-hour, and 12-hour (AM/PM). Default to Follow phone. Apply it throughout app-formatted wall-clock times. | 47, 47a–c |
| Trial and Save | Use Pro trial. Save once, without a free-settings confirmation page. Convert future alarms automatically at expiry. Do not offer old-routine restoration. | 12–14, 45, 55, 55a, 57 |
| Testing | Show and honor test controls only in internal builds. Public builds ignore saved test overrides. | 16 |
| Routine | Show Pose x of y. Pause the between-pose countdown while inactive. Offer Snooze only before yoga starts. | 4, 5, 8, 9 |
| Animation and selection | Keep Android's tour and new-pose animation timing. Select unlocked poses by swiping. | 11, 32, 44 |
| Preparation | Show the tour placement picture, full placement instructions, and top Back. Remove the preparation privacy footer. | 23, 38, 52, 53 |
| Emergency stop | Keep Android's touch hold and screen reader action. Keep the no-streak sentence. | 39, 50 |
| Removed controls | Do not add Snoozing…, Try sound again, or Try saving again. Remove Finding you… wherever it is visible. | 37, 40–43 |
| Sound and sheets | Direct locked-sound route to Pro; PRO badge; music emoji on Stop; immediate Snooze enable after option changes; swipe dismissal. | 22, 26–28, 51 |
| Text and links | US English; Allow permission labels; setup progress; approved ringing/test text; privacy/terms/support links. | 17, 20, 24, 25, 29, 31, 33–36, 46, 48, 49, 54, 56 |
| Platform exceptions | Keep supported sound/vibration differences. Add no system-Stop explanation. Keep the approved iOS native action labels. | 6, 7, 31 |

Use the existing pose thresholds, catalogs, hold choices, subscription rules, and alarm defaults. This work does not approve changing them. Preserve the ten-second completion grace and the existing inactivity rules.

## Current source findings

| Source | Current state and required work |
| --- | --- |
| `model/AlarmConfig.kt:72` | `suggestedFreeFallback()` already defines the accepted positional conversion. Reuse it. |
| `ui/AlarmEditorScreen.kt:587` | Save can open `fallbackReview`. The review sheet starts near line 641. Remove both the visible review and its Save interception. |
| `data/AlarmStore.kt:50` | Storage also requires matching confirmed fallback data. UI changes alone will leave Save blocked. |
| `data/ProTrialStore.kt:164,197,242` | Completion and reconciliation own expiry. Conversion skips missing/stale agreements and some archived originals. These old gates conflict with the new choices. |
| `ui/YogaAlarmRoot.kt:174,359,683` | Save schedules synchronously; permission recovery ignores schedule results; legacy trial alarms can be forced into free review. All need coordinated updates. |
| `ui/YogaAlarmRoot.kt:840` | Testing UI is public. Store-level test preferences can still grant access if only the UI is hidden. |
| `ui/RoutineViewModel.kt:110,122,223,275` | Pause stops the ticker but retains an absolute transition deadline. Background time can finish the countdown. |
| `ui/RoutineRoute.kt:396` | Test preparation has Back, but lacks the placement picture and has the footer the user rejected. |
| `ui/RoutineCameraScreen.kt:186,418` | Add pose-position context and distinguish ordinary test completion from a real alarm completion. |
| `ui/AppInfoSheets.kt` | Add the Time format preference and the missing legal/support destinations. Preserve current Settings groups. |

Line references are source-review entry points. Re-find the symbols after edits.

## Step 1 — Set the internal-build boundary

Files: `app/build.gradle.kts`, `data/ProTrialStore.kt`, `data/AlarmStore.kt`, `ui/YogaAlarmRoot.kt`, `ui/MonetizationTestingSection.kt`.

1. Use the existing Debug build as the current internal channel. Keep public Release disabled by default.
2. Gate both the Settings section and effective test-access policy. A saved preference must not override the build boundary.
3. Public builds must use genuine ownership and the normal trial record. Ignore saved Testing mode, Test Pro access, and Test trial flags.
4. Reject test-mode mutation calls when the build does not permit them. Cover service, receiver, Save, and reconciliation paths.
5. Keep saved test records compatible. Do not delete real ownership, normal trial history, alarms, or streak data.
6. Keep trial rollout and optional analytics gates separate from internal testing.
7. If a signed internal Release is needed, add an explicit compile-time capability. Do not infer it from an installation track.

`BuildConfig.DEBUG` is sufficient for the current Debug-only boundary. Avoid a new flavor or settings framework unless an internal Release is actually required.

Existing `SYNC-028` says all release builds expose Testing mode. The November 9 user decision supersedes that requirement. Update the queue when implementing this change.

## Step 2 — Implement one Save and automatic trial conversion

Files: `model/AlarmConfig.kt`, `model/ProTrial.kt`, `data/AlarmStore.kt`, `data/ProTrialStore.kt`, `data/AlarmOccurrenceStore.kt`, `ui/AlarmEditorScreen.kt`, `ui/YogaAlarmRoot.kt`.

### Accepted conversion rule

Build exactly three positions. Use the latest saved trial routine as the input.

| Position | If the existing pose is free | If the pose is Pro or missing |
| --- | --- | --- |
| 1 | Keep it | Mountain |
| 2 | Keep it | Warrior II |
| 3 | Keep it | Tree |

Every position uses 20 seconds. Discard positions after the third for the free routine. Keep a free sound. Replace a Pro sound with Sunbird Morning Call. Duplicate free poses remain valid.

Example: Chair → Tree → Triangle becomes Mountain → Tree → Tree. Do not deduplicate it into an unapproved sequence.

### Save and migration work

1. Keep the button label Save. Remove the confirmation sheet, its review links, and the interception that opens it.
2. Prepare valid automatic fallback metadata at the storage boundary for unpaid trial customizations. Every caller must get the same rule.
3. Remove the requirement for user confirmation. A persisted compatibility field may remain, but it must not mean a user confirmed anything.
4. Recompute from the current saved customization where needed. Do not apply stale fallback data from an older draft or archived routine.
5. Keep name trimming, the 40-character limit, stale-draft conflict checks, and Save-enables-alarm behavior.
6. Remove expired-trial paths that force a user back through free review. Normal permission/scheduling errors still need recovery.
7. Reconcile legacy exhausted trials with missing or mismatched agreement data through the checked trial transition. Do not mutate records in a normal `load()` call.
8. Do not let `trialOriginal` prevent conversion where conversion is now required. Keep historical fields readable; do not restore from them.
9. Repair invalid old `FreeTransition.APPLIED` records too. The old policy can mark APPLIED while a current Pro routine remains beside an archived original. For an exhausted, unretired, unpaid trial, reconcile eligible nonfree future configurations idempotently even if that old marker says APPLIED. Keep ownership-verification guards and do not spend another trial day.
10. Keep already-converted free alarms free. A format change, restart, or purchase must not restore a Pro routine. For an unconverted trial alarm, derive the approved projection from its current saved customization rather than an old manually selected fallback.

### Transaction and occurrence safety

An occurrence snapshot is the alarm configuration captured for one delivered alarm. It can outlive edits to future alarms.

- Keep the existing checked commit for occurrence completion, trial accounting, and conversion.
- Keep completion journals and replay protection. Count at most one successful real-alarm date per local date.
- Practice, test routines, permission failure, Stop, and Snooze alone must not consume trial days.
- Keep purchase-verification deferral. Pending payment must not grant ownership.
- Do not convert verified paid owners. Do not treat temporary offline paid-access expiry as a trial expiry.
- Preserve enabled state, alarm order, time, weekdays, and occurrence identity during conversion.
- Ringing and snoozed occurrences retain their snapshots. Only future saved configurations change.
- A failed commit must not spend a trial day without saving its corresponding state.

### Remove routine restoration

Remove automatic offers, selected-alarm state, callbacks, and user-facing routine restoration. Purchase keeps the current routine.

Keep **Restore purchases**. It restores verified ownership, not old pose lists. Keep transaction completion, ownership refresh, paid-access recovery, and old record decoding.

Update analytics at the same boundary. Do not emit `fallback_confirmed` without a confirmation. Do not emit routine-restored events from removed UI. Preserve meaningful automatic-conversion events and replay guards. Keep analytics optional and disabled unless already configured.

## Step 3 — Add Time format

Files: `ui/AppInfoSheets.kt`, `ui/YogaAlarmRoot.kt`, `ui/AlarmHomeScreen.kt`, `ui/AlarmEditorScreen.kt`, `ui/AlarmRingingScreen.kt`, `ui/Onboarding.kt`, `alarm/AlarmForegroundService.kt`.

1. Add one persisted preference beside First day of week in `app_settings`.
2. Store stable values for Follow phone, 24-hour, and 12-hour. Missing or unknown values mean Follow phone.
3. Resolve Follow phone from the current system preference. Refresh after resume or configuration changes.
4. Share one small format-resolution path across callers. Multiple callers justify reuse; a general localization framework is unnecessary.
5. Apply the choice to home clocks, ringing clocks, saved-tour summaries, accessibility text, and app-written missed-report time ranges.
6. In 12-hour mode, show hours 1–12 and AM/PM in the editor. Keep stored hours in the range 0–23.
7. Map 12 AM to 0 and 12 PM to 12. Changing format must preserve the selected time and draft minutes.
8. Make preference changes visible without restarting. Follow phone must continue following later system changes.

Do not change `AlarmScheduler.nextOccurrence`, stored weekday identity, timezone/DST rules, or snooze durations. Do not reschedule alarms solely because their display format changed.

Apply this preference to app-owned text. Android system notification timestamps and other OS-owned clocks keep the system format. Elapsed labels such as “60 sec” are durations, not wall-clock times.

## Step 4 — Add reliable scheduling feedback on Home

Files: `ui/YogaAlarmRoot.kt`, `ui/AlarmHomeScreen.kt`, `model/AlarmConfig.kt`, `data/AlarmStore.kt`, `alarm/AlarmScheduler.kt`, `alarm/BootReceiver.kt` as needed.

1. Use the existing root/store ownership. Do not build a second scheduler or repository layer.
2. Track scheduling progress per alarm. Perform blocking storage/scheduler work outside the UI thread so progress can render.
3. Pass state and Retry directly to `AlarmSummaryCard`.
4. Show “Alarm is being scheduled” during the operation. Show “Alarm needs attention” and Retry after failure.
5. Retain “saved and switched off” on scheduling failure. Make failure different from a user deliberately switching an alarm off. Check that the disabled/repair record actually committed before claiming it is switched off. A failed persistence write needs truthful error state, not a successful-save message.
6. Persist a minimal repair marker in the existing alarm record. Default it to false when reading older records. Keep pending-operation state transient.
7. Retry must load the latest record and make a guarded scheduling attempt. Successful Retry clears repair state and enables the repaired alarm.
8. Explicit user-off clears the repair request. Do not automatically enable a deliberately disabled alarm after permission recovery.
9. Reject stale results after edit, deletion, or another operation. Update fields against the latest record; do not overwrite an entire old list.
10. Handle permission-recovery scheduling results instead of discarding them. Reconcile current alarms and preserve active occurrences.

**Important:** `AlarmScheduler.schedule()` normally calls `cancel()`. That path also terminates in-flight occurrences. Retry and permission reconciliation must not cancel a valid ringing or snoozed occurrence. Use the existing preservation path correctly, or defer the attempt until safe. Recheck alarm identity after asynchronous work.

A schedule call accepted by Android is not proof of eventual physical delivery. The card must not claim more than the app can establish. Do not add a Next or Snoozed time line.

## Step 5 — Routine timing, preparation, and completion

Files: `ui/RoutineViewModel.kt`, `ui/RoutineRoute.kt`, `ui/RoutineCameraScreen.kt`, `ui/AlarmRingingScreen.kt`.

### Pause between poses

- Replace the background-sensitive transition deadline with remaining active time, or an equally small correctly rebased implementation.
- Decrement the remaining three seconds only while the session is active.
- Initialize and reset it with the existing start/stop paths.
- Preserve the announced next pose during pause. Resume without skipping the remaining time or crediting held seconds.
- Keep fresh-frame/detection requirements after the transition. Keep the existing detection thresholds and hold logic.

### Camera and preparation

- Show Pose x of y. Use the displayed guide pose during the next-pose preview; do not show the old position beside the new picture.
- Keep the 1,000 ms picture hold and 650 ms travel. Animation must not delay detection or hold timers.
- Keep active-camera Snooze absent. Preserve pre-camera Snooze behavior and use counts.
- Use `onboarding_phone_setup` on the test preparation screen. Keep it scrollable and safe-area aware.
- Show top Back. Use: “Prop your phone upright on the floor facing you, take about two steps back, then follow the pose.”
- Remove the preparation footer “Camera off · Processed on-device · Never recorded”. Do not remove unrelated privacy content or the unchanged ringing footer.
- Ringing instruction: “Place your phone where your full body fits on screen.”
- Do not add Finding you…, Snoozing…, Try sound again, or Try saving again. Keep technical error handling, camera recovery, and safe stopping.
- Keep Android's existing screen reader Stop alarm action and the touch hold. Both must use the same emergency-pass/streak handler.

### Completion

Ordinary test completion must show “Test complete. Your streak did not change.” Pass an explicit test/real context if the current shared component lacks it. Do not infer that context from a Back button.

Keep the quiet five-second onboarding practice separate. Keep its approved Practice complete / Continue setup flow. Fix US spelling and ordinary singular/plural output without changing success accounting.

Keep the ten-second keep-awake grace. Preserve durable outcome saving and replay protection without adding the rejected retry buttons.

## Step 6 — Copy, settings, and sound panels

| Target | Required edit |
| --- | --- |
| All shared product copy | Use US English. Use Pro trial, not Pro preview. Do not rename persisted schema values merely to change visible text. |
| Tour | Keep Android's current slow teaching motion and six-second cards. Keep normal Android navigation timing. Show the full approved placement instruction visibly on the tour placement page, not only in the image description. |
| Invitation | Android already lacks the extra trial paragraph. Preserve that state. |
| Welcome | Remove “No payment details or automatic charge.” Do not replace it with another version of the same sentence. Describe automatic free settings without claiming the user confirmed them. |
| Permission setup | Keep dynamic completed/total progress. Use Allow for all app-owned permission actions, including Full-screen/Exact rows currently labeled Enable and camera-recovery buttons labeled Allow camera. Preserve correct settings routing after denial and descriptive accessibility labels. Use the approved short explanation. |
| Switches | Keep forest on-thumb, lime on-track, and the Android off-state colors. These are the iOS reference. |
| Home summary | Keep one-line tail truncation. Do not add extra next-occurrence/snooze text. |
| Sound options | Show a PRO label for locked sounds. Route locked selections directly to the Pro offer. Keep locked playback unavailable. |
| Preview control | Keep Preview and `🎵 Stop`. Preserve ten-second preview limits and stop on dismissal/background. |
| Vibration | Remove Gentle pulse. Keep independent supported sound/vibration behavior. |
| Snooze options | Keep Android's immediate draft enable after changing duration/count. Swipe dismissal retains draft changes; it does not save the alarm. |
| Option panels | Do not add Done to sound, snooze, or pose-duration panels. Preserve guided-tour restrictions on dismissal. |
| Duration panel | Remove “20 seconds is included in the free routine.” Keep the actual free/Pro limits. |
| Pose selection | Keep unlocked selection swipe-only. Keep locked-card Pro actions and accessible navigation. |
| Pro offer | Add Privacy Policy and Terms of Use links. Preserve store pricing, ownership, pending-payment guards, and current plan ordering. |
| Privacy and Safety | Add Support. Preserve truthful analytics/privacy information. |

Use the current iOS destinations already present in source:

- Privacy Policy: `https://www.yogaalarm.com/privacy`
- Terms of Use: `https://yogaalarm.com/terms`
- Support: `https://yogaalarm.com/support`

Reuse the existing URL-opening pattern. Check that each destination opens. This plan does not audit or rewrite the external pages.

## Step 7 — Update the existing iOS queue

Make queue updates in the same changes as shared Android implementation. Include Android commit hashes when available. Do not mark iOS complete from Android test results.

| Change group | Queue work |
| --- | --- |
| Trial Save, automatic conversion, no routine restoration, internal testing | Update `SYNC-028`. Explicitly supersede upfront confirmation, legacy review blocking, automatic restoration, and public release test controls. Preserve unrelated purchase/occurrence requirements. |
| Time format | Use the next unused SYNC ID when implementation lands. Do not reserve an ID in this plan. Link related Settings and notification entries. |
| Home schedule state and Retry | Extend `SYNC-019` with durable repair state and observable pending/failure behavior. Do not duplicate its Save outcome contract. |
| Tour, placement, US copy, invitation/welcome | Update `SYNC-016`; cross-reference the permission setup requirements in `SYNC-006`. Preserve intentional normal-navigation timing differences. |
| Pose counter, transition pause, test completion | Update relevant `SYNC-011` behavior and reference existing `SYNC-007` picture timing. |
| Duration helper, card truncation, legal/support | Update relevant `SYNC-013`, `SYNC-012`, `SYNC-010`, and `SYNC-005` sections. |
| Platform protection | Keep `SYNC-018`, `SYNC-026`, `SYNC-027`, and the iOS camera-first validation notes visible. Do not erase native limitations or unrelated pending acceptance. |

The iOS agent must read the updated queue and reference Android implementation commits. The companion [iOS handoff](ios-ux-unification-handoff.md) supplies the native file map and checks.

## Focused validation

Do not create tests for every text edit. Add or update tests for changed business logic and recovery paths.

| Area | Required cases | Existing tests to extend |
| --- | --- | --- |
| Automatic conversion | First-three free retention; Pro/missing replacement; duplicates; 20-second holds; free/Pro sound; no confirmation on Save | `model/ProTrialTest.kt`, `data/AlarmStoreTrialTest.kt`, `data/ProTrialStoreTest.kt` |
| Legacy and purchase | Missing/stale fallback; APPLIED plus current Pro settings and archived original; already-converted alarm; paid/retired owner; pending ownership; failed commit; current routine retained after purchase | `data/ProTrialStoreTest.kt`, `data/AlarmStoreTrialTest.kt` |
| Occurrence safety | Fifth completion; snoozed snapshot; process recreation; duplicate completion; edit/delete race; stale action | `data/AlarmOccurrenceStoreTest.kt`, `data/WakeCompletionRecoveryTest.kt`, `alarm/AlarmNotificationTest.kt` |
| Testing boundary | Debug controls work; public policy ignores all seeded test flags; normal trial restored; genuine paid ownership unchanged | `data/ProTrialStoreTest.kt`, `data/AlarmStoreTrialTest.kt`; inspect a Release artifact |
| Time format | Missing/invalid preference; Follow phone refresh; 00:00, 12:00, 23:59; AM/PM edits; unchanged canonical hour, schedule, DST | A small focused preference/format test if needed; `model/AlarmDraftTest.kt`, `alarm/AlarmSchedulerTest.kt`, `alarm/AlarmNotificationTest.kt` |
| Schedule state | Pending → success/failure; persistence failure; Retry; user-off; restart; deletion; stale result; active snooze preserved | Focused tests at the existing scheduling/store boundary |
| Transition pause | Pause partway through transition; long background interval; repeated resume; correct next pose; no hold credit | `ui/RoutineViewModelTest.kt` |

Test paths in this table are relative to `app/src/test/java/com/lurremcfly/yogaalarm/`.

Example scoped commands, run from the Android root:

```bash
bash -o pipefail -c './gradlew :app:testDebugUnitTest --tests "com.lurremcfly.yogaalarm.data.ProTrialStoreTest" --tests "com.lurremcfly.yogaalarm.data.AlarmStoreTrialTest" --tests "com.lurremcfly.yogaalarm.data.AlarmOccurrenceStoreTest" --tests "com.lurremcfly.yogaalarm.data.WakeCompletionRecoveryTest" 2>&1 | tail -c 6000'
bash -o pipefail -c './gradlew :app:testDebugUnitTest --tests "com.lurremcfly.yogaalarm.ui.RoutineViewModelTest" --tests "com.lurremcfly.yogaalarm.alarm.AlarmSchedulerTest" --tests "com.lurremcfly.yogaalarm.alarm.AlarmNotificationTest" --tests "com.lurremcfly.yogaalarm.model.AlarmDraftTest" 2>&1 | tail -c 6000'
bash -o pipefail -c './gradlew :app:compileDebugKotlin :app:compileReleaseKotlin 2>&1 | tail -c 4000'
```

Run relevant groups after their changes. Do not repeatedly run all commands for unrelated copy edits. Release behavior needs explicit coverage; Debug-only tests do not prove test overrides are disabled in Release.

## Device acceptance and handoff result

Use a small phone, large text, portrait, and landscape. Check:

1. Time format updates every app-owned clock and editor, without changing an alarm's firing time.
2. Follow phone updates after returning from system settings. Test midnight and noon.
3. Save a Pro trial routine once. Complete the fifth distinct real-alarm day. Confirm automatic future-only conversion.
4. Keep another occurrence snoozed during conversion. It must retain its original snapshot and remaining uses.
5. Remove alarm permission, Save, restart, then Retry. Confirm visible repair state and no unintended enable of user-disabled alarms.
6. Background during the three-second transition. Return and finish only the remaining active time.
7. Test pose/full routine and onboarding practice. Confirm correct copy, no trial use, and no streak change.
8. Open all three option panels. Swipe them away. Confirm draft changes persist and no new Done buttons appear.
9. Check placement image, Back, permission progress, Allow routing, PRO badges, music emoji, and all three links.
10. Check genuine purchase restoration. Confirm there is no old-routine restoration offer and the current routine remains current.
11. In a public Release build, seed old test preferences and restart. Confirm no test UI, Pro bypass, or test-trial accounting.

Do not release from source checks alone. Make the final report list changed files, tests, device results, unresolved native issues, and updated SYNC IDs/commits. Separate source implementation from native acceptance. Avoid unrelated formatting or architecture work.

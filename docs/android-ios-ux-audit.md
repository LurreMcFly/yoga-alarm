# Android / iOS UX alignment audit

Audit date: 2026-11-11. This rechecks the November 10 findings after the iOS updates. The November 9 user decisions remain the reference.

## Conclusion

**The three main iOS gaps are now resolved in source.** Try pose uses the timed product test flow, the ringing/recovery hierarchy matches Android, and ordinary tests no longer show the personal streak celebration.

The previous duration-panel, locked-preview, and free-settings interaction gaps are also resolved.

**Exact text alignment is not complete.** Four small copy/formatting differences remain. The two misleading instructions are in Android: the fixed “four free poses” hint during trial/paid access, and the alarm-fading instruction during sound-off practice. Quiet-practice success headings and free-settings separators also differ.

The visible permission labels match. A descriptive screen reader label still differs after denial; this is a permitted accessibility difference.

No remaining major source mismatch was found in the inspected approved product flows. Native compilation, rendering, camera, and alarm-delivery checks remain open.

This recheck changed documentation only. It did not implement app fixes or mark iOS queue entries complete.

## What was checked

- Android: `/home/jolu/Projects/yoga_alarm`, HEAD `94be4d7` plus the current uncommitted implementation.
- iOS: `/home/jolu/Projects/yoga-alarm-ios`, HEAD `9860dd3` plus the current uncommitted implementation.
- Reference: all 57 numbered decisions, including 47a–c and 55a, recorded below.
- The new source was compared with the November 10 fingerprint baseline. Nine iOS source/test files changed; all checked Android source files were unchanged.
- Changed files: `UI/AlarmEditorView.swift`, `UI/AlarmHomeView.swift`, `UI/CatalogPickerViews.swift`, `UI/OnboardingView.swift`, `UI/ProPaywallView.swift`, `UI/ProductAlarmExperienceView.swift`, `UI/RoutinePracticeView.swift`, `UI/StreakViews.swift`, and `YogaAlarmTests/RoutineEngineTests.swift`.
- The changed product routes, all prior F1–F6 findings, and the affected confirmed decisions were inspected. Unchanged behavior retains the November 10 source evidence.
- No side-by-side app rendering was performed. No application tests were run in this recheck.
- A fingerprint check found no changes in the 157 checked source/resource/test files during this review.
- The November 10 asset check found all 11 bundled MP3 files identical by SHA-256. It was not repeated.

**Source match** means the inspected code follows the decision. It does not mean device acceptance passed.

**Device check** means the implementation is present, but rendering or native behavior still needs a device check.

**Intentional** means a difference was accepted. It is not a defect.

Sources below use **A** for `app/src/main/java/com/lurremcfly/yogaalarm/` in Android and **I** for `YogaAlarm/` in iOS. Current iOS references below were refreshed where the changed files affected the findings.

## Findings resolved since November 10

### F1 — Resolved in source: Try pose now uses the product test flow

The editor now copies the selected `PoseStep` into a one-step alarm and presents `RoutinePracticeView`. It retains the selected pose, duration, and step identity. It does not modify the original draft.

The test uses preparation with the placement picture, top Back, and explicit Start camera. Its default flags do not identify it as a real alarm or start it immediately. It then uses the same hold, counter, picture animation, and completion flow as ordinary tests.

Evidence:

- [iOS single-pose cover](/home/jolu/Projects/yoga-alarm-ios/YogaAlarm/UI/AlarmEditorView.swift:248).
- [iOS selected-step configuration](/home/jolu/Projects/yoga-alarm-ios/YogaAlarm/UI/AlarmEditorView.swift:762).
- [Shared test preparation](/home/jolu/Projects/yoga-alarm-ios/YogaAlarm/UI/RoutinePracticeView.swift:272).
- [New 45-second single-pose test](/home/jolu/Projects/yoga-alarm-ios/YogaAlarmTests/RoutineEngineTests.swift:155). The test checks the configured duration, not a default 20-second or quiet five-second hold. It was inspected, not executed.

No product construction of `PosePracticeView` remains in the current Swift source search. Its diagnostic source still exists; removing unused diagnostic code is not required for alignment.

### F2 — Resolved in source: ringing/recovery information hierarchy

The dedicated iOS screen now shows YOGA ALARM, the clock, a prominent alarm name, and smaller status text. This matches Android's information hierarchy while retaining native fonts.

Evidence: [iOS ringing/recovery layout](/home/jolu/Projects/yoga-alarm-ios/YogaAlarm/UI/ProductAlarmExperienceView.swift:107), A `ui/AlarmRingingScreen.kt:79`.

The separate camera-first success path is preserved.

### F3 — Resolved in source: test completion presentation

`RoutinePracticeView` now passes `isTest: !isFiredAlarm` to the celebration. Test mode substitutes **Test complete. Your streak did not change.** for the normal pose-count detail. It suppresses the streak label and number overlay.

Evidence: [Test context](/home/jolu/Projects/yoga-alarm-ios/YogaAlarm/UI/RoutinePracticeView.swift:405), [test detail and displayed streak](/home/jolu/Projects/yoga-alarm-ios/YogaAlarm/UI/StreakViews.swift:33), and I `UI/StreakViews.swift:90,115`.

Single-pose and full-routine tests use the test callbacks rather than real-alarm completion accounting. This is source evidence; runtime counting still needs native acceptance.

### F5 — Resolved in source: option-panel and locked-choice interactions

| Previous difference | Current result | Evidence |
| --- | --- | --- |
| Hold-duration selection | Both select and close the panel | A `ui/AlarmEditorScreen.kt:607`; I `UI/CatalogPickerViews.swift:206` |
| Future free-settings information | Both provide a separate presentation with counting rules, every alarm, free settings, and Edit routine | A `ui/YogaAlarmRoot.kt:897`; I `UI/ProPaywallView.swift:59`, `UI/AlarmHomeView.swift:693` |
| Locked sound Preview | Both route directly to Pro without playing locked audio | A `ui/AlarmEditorScreen.kt:1054`; I `UI/CatalogPickerViews.swift:59` |

iOS safely sequences offer dismissal, free-settings presentation, and editor opening. Its native sheet versus Android's dialog is an accepted presentation difference.

Neither app adds the rejected permanent free-settings Settings entry or a free-settings confirmation on Save.

## Remaining differences

### F4 — Small copy and formatting differences remain

| Context | Android | iOS | Assessment |
| --- | --- | --- | --- |
| Guided first-pose hint | Swipe to explore your four free poses. | Swipe to explore your available poses. | Android's fixed four-free wording is misleading when trial/paid access offers nine poses. |
| Quiet five-second practice hold | Stay with it while the alarm fades. | Stay with it for five seconds. | Android's text is misleading during sound-off practice. |
| Quiet-practice success heading | Nice work! ✓ | You’ve got it. | Both then say Practice complete. Choose one shared heading. |
| Free-settings routine summary | Commas separate poses | Arrows separate poses | Main values and limits match. Formatting differs. |

Evidence: A `ui/Onboarding.kt:309,440`, `ui/RoutineCameraScreen.kt:247`, `model/AlarmConfig.kt:140`; I `UI/OnboardingView.swift:51`, `UI/RoutinePracticeView.swift:396,458`, `Models/ProPreview.swift:130`.

The accurate iOS wording is the better candidate for the first two rows. The other rows need one shared copy choice. This audit does not apply those changes automatically.

The other F4 strings now match: trial welcome, camera startup detail, completed-today emergency explanation, expired-trial Pro headline, and Privacy Policy action.

### F6 — Minor accessibility wording still differs after permission denial

Both visible buttons say Allow.

Android's accessible action uses Allow. iOS denied readiness rows can announce Open Settings plus the permission name. Both routes correctly open system settings when needed.

Evidence: A `ui/AlarmHomeScreen.kt:737`; I `UI/AlarmHomeView.swift:817,823,829,895`.

This does not fail the visible-label choice. Descriptive accessibility text was allowed in the handoffs. It is a minor permitted wording difference.

## Confirmed-choice coverage

The table covers all 57 numbered decisions. Device checks remain necessary even for source matches.

| Decisions | Target | Current result | Evidence |
| --- | --- | --- | --- |
| 1, 10, 21, 30 | Keep native styling, bar size, and intentional navigation-speed differences | Intentional; preserved | A `ui/AlarmEditorScreen.kt:1094`; I `UI/AlarmEditorView.swift:666`; normal navigation retains platform timing |
| 2, 15, 19 | One-line summary; no Next/Snoozed line | Source match | A `ui/AlarmHomeScreen.kt:847`; I `UI/AlarmHomeView.swift:1200` |
| 3 | Scheduling progress, failure, Retry | Source match; Android focused tests passed | A `data/AlarmStore.kt:43`, `ui/AlarmHomeScreen.kt:857`; I `UI/AlarmHomeView.swift:1204` |
| 4 | Landscape and compact layouts | Source present; device check | A `ui/RoutineCameraScreen.kt:113`; I `Info.plist:40`, `UI/RoutinePracticeView.swift:299`, `Camera/CameraPreview.swift:40` |
| 5 | Snooze only before yoga | Source match in real-alarm paths | A `ui/RoutineRoute.kt`; I `UI/RoutinePracticeView.swift:194,299` |
| 6, 7 | Supported sound/vibration differences; no new Stop explanation | Intentional; preserved | I `UI/AlarmEditorView.swift:315`, `UI/CatalogPickerViews.swift:29`, `Alarms/ProductAlarmService.swift:45` |
| 8, 32 | Pose x of y; 1,000/650 ms picture arrival | Source match, including the new single-pose route | A `ui/RoutineCameraScreen.kt:106,159,199`; I `UI/RoutinePracticeView.swift:318,533`; resolved F1 |
| 9 | Pause between-pose countdown while inactive | Source match; Android regression passed | A `ui/RoutineViewModel.kt:111,225,279`; I `Routine/RoutineEngine.swift:53,91` |
| 11 | Slow tour teaching motion; six-second cards | Source match | A `ui/Onboarding.kt:122,127,139,393`; I `UI/OnboardingView.swift:77,101,386`, `UI/OnboardingSpotlight.swift:382` |
| 12, 17 | Pro trial; US English | Term/spelling match; broader copy still differs | A `ui/YogaAlarmRoot.kt:135`; I `UI/AlarmHomeView.swift:483`; F4 |
| 13, 14, 45 | No permanent free-settings Settings entry; no old-routine restoration | Source match; purchase restoration retained | A `ui/AppInfoSheets.kt:164`, `data/ProTrialStore.kt:198`; I `UI/AlarmHomeView.swift:8,1376` |
| 16 | Internal-only testing, including effective access | Source match; Android Release test passed | A `data/ProTrialStore.kt:65`; I `Models/ProPreview.swift:58`, `Alarms/AlarmListModel.swift:90` |
| 18 | Ask before adopting unresolved recommendations | Process rule retained | F4's remaining shared-copy choices are recorded, not silently implemented |
| 20 | Android ringing information hierarchy | Source match | I `UI/ProductAlarmExperienceView.swift:107,118,121`; resolved F2 |
| 22, 26, 51 | Direct locked-sound offer; music emoji; PRO badge | Source match for row selection and labels | A `ui/AlarmEditorScreen.kt:1034,1076`; I `UI/CatalogPickerViews.swift:41,62`; Preview interaction now matches; resolved F5 |
| 23, 38, 52, 53 | Placement picture/instruction; top Back; no preparation footer | Source match for full and single-pose tests | A `ui/RoutineRoute.kt:383`; I `UI/RoutinePracticeView.swift:272`, `UI/AlarmEditorView.swift:248`; resolved F1 |
| 24, 54 | No invitation trial paragraph; remove welcome no-payment sentence | Source match | A `ui/Onboarding.kt:241`; I `UI/OnboardingView.swift:329,449` |
| 25 | Android switch colors | Source match | A `ui/YogaAlarmTheme.kt:57`; I `Design/DesignTokens.swift:57` |
| 27 | Option changes enable Snooze immediately | Source match | A `ui/AlarmEditorScreen.kt:623`; I `UI/CatalogPickerViews.swift:149,161` |
| 28, 35, 56 | Swipe dismissal; no option Done; remove extra duration/vibration text | Source match | A `ui/AlarmEditorScreen.kt:1207`; I `UI/CatalogPickerViews.swift:17,139,196` |
| 29, 46 | Policy/terms/support links | Source match | A `ui/AppInfoSheets.kt:322,478`; I `UI/ProPaywallView.swift:140`, `UI/AlarmHomeView.swift:1513` |
| 31 | Keep native iOS labels; Start inside app | Intentional; source match | I `Alarms/ProductAlarmService.swift:50,92`, `UI/RoutinePracticeView.swift:225` |
| 33 | Shared ringing instruction | Source match | A `ui/AlarmRingingScreen.kt:118`; I `UI/ProductAlarmExperienceView.swift:128`, `UI/RoutinePracticeView.swift:202` |
| 34 | Test complete; streak unchanged | Source match for full and single-pose tests | I `UI/RoutinePracticeView.swift:405`, `UI/StreakViews.swift:33,90,115`; resolved F1/F3 |
| 36, 48, 49 | Allow; completed/total; short setup explanation | Visible source match | A `ui/AlarmHomeScreen.kt:694,714`; I `UI/AlarmHomeView.swift:809,885`; F6 |
| 37, 40, 41, 42, 43 | Remove rejected startup/busy/retry labels and controls | Source match in inspected product branches | A `ui/RoutineCameraScreen.kt`; I `UI/RoutinePracticeView.swift:414,437`, `UI/ProductAlarmExperienceView.swift` |
| 39, 50 | Shared touch/accessibility emergency action; no-streak sentence | Source match; device accessibility check remains | A `ui/RoutineRoute.kt:448,465,489`; I `UI/RoutinePracticeView.swift:552,562,582` |
| 44 | Swipe-only unlocked touch selection | Source match; accessible adjustment retained | A `ui/AlarmEditorScreen.kt:807`; I `UI/AlarmEditorView.swift:579` |
| 47, 47a–c | Follow phone/default; 12/24-hour; all app-owned times | Source match; real phone-preference refresh needs device check | A `data/TimeFormat.kt:8`, `ui/TimeFormat.kt:34`; I `Models/AlarmConfiguration.swift:98`, `UI/AlarmEditorView.swift:263` |
| 55, 55a, 57 | One Save; automatic positional free conversion; no confirmation | Source match; Android storage/legacy tests passed | A `data/AlarmStore.kt:99`, `data/ProTrialStore.kt:213,240`; I `Alarms/AlarmListModel.swift:241,466` |

## Important aligned behavior

- Both compute three 20-second free positions at trial expiry. They retain a free pose in each position or use Mountain / Warrior II / Tree. Both retain a free sound or use Sunbird Morning Call.
- Missing/stale fallback metadata is no longer a reason to demand confirmation. Android also reconciles legacy APPLIED records that still have Pro settings.
- Old-routine restoration UI is gone. Restore purchases remains. Existing archives are kept for compatibility.
- Public builds ignore saved test flags in the inspected policy. Android has an executed Release regression; the equivalent iOS regression is authored.
- Time format uses the same stored choices and default. Both keep canonical hours in 0–23 and format app-owned missed-report ranges.
- Both retain the normal three-second inter-pose transition and ten-second completion wake grace.
- Existing home history and drag features remain. This review found no new gap in those unchanged product structures.

These observations do not prove physical delivery, camera performance, every persistence race, or pixel-identical rendering.

## Accepted platform differences

Do not count these as failed alignment:

- Native controls, Save/Cancel dimensions, and intentional normal-navigation timing differences.
- The newer iOS camera-first success path and its preview/automatic-start branches, preserved by the iOS handoff.
- Native Open Yoga Alarm / Start Yoga labels on iOS, with Start inside the app.
- Unsupported sound/vibration modes being unavailable on iOS while supported choices remain on Android.
- OS-owned clocks, permission dialogs, purchase sheets, and notification chrome following native behavior.

The dedicated ringing/recovery hierarchy now matches Android; see resolved F2. Keep it separate from the accepted camera-first success path.

## Validation evidence and this recheck

On November 11, the nine changed iOS source/test files and prior findings were inspected. No application tests were executed in this recheck. The new single-pose duration test was inspected but not run.

The checked Android source files are unchanged from the November 10 audit. Its focused tests were not repeated. The following command and results are **November 10 evidence**, not a new run:

```bash
bash -o pipefail -c 'JAVA_HOME=/opt/android-studio-for-platform/jbr ./gradlew --offline \
  :app:testDebugUnitTest \
  --tests "com.lurremcfly.yogaalarm.data.TimeFormatTest" \
  --tests "com.lurremcfly.yogaalarm.data.AlarmStoreTrialTest" \
  --tests "com.lurremcfly.yogaalarm.data.ProTrialStoreTest" \
  --tests "com.lurremcfly.yogaalarm.data.AlarmSchedulingTest" \
  --tests "com.lurremcfly.yogaalarm.ui.RoutineViewModelTest" \
  :app:testReleaseUnitTest \
  --tests "com.lurremcfly.yogaalarm.data.PublicTrialPolicyTest" 2>&1 | tail -c 5000'
```

| Executed test class | Tests | Result |
| --- | --- | --- |
| Debug TimeFormatTest | 4 | Passed |
| Debug AlarmStoreTrialTest | 7 | Passed |
| Debug ProTrialStoreTest | 32 | Passed |
| Debug AlarmSchedulingTest | 8 | Passed |
| Debug RoutineViewModelTest | 16 | Passed |
| Release PublicTrialPolicyTest | 1 | Passed |
| **Total** | **68** | **No failures, errors, or skips** |

On November 10, the two selected Gradle test tasks executed successfully. The prerequisite compilation tasks were up to date. That was not a full test suite or a new APK build.

The prior iOS test review covered public-policy gating, legacy conversion, clock formats, compact layout, preview inactivity, transition suspension, and stopped-outcome recovery. The new review also inspected the configured-duration single-pose regression at `YogaAlarmTests/RoutineEngineTests.swift:155`. None of these iOS tests were executed here. This host has no Swift compiler or Xcode.

No physical-device, simulator, screenshot, VoiceOver/TalkBack, or store-purchase acceptance was performed during this audit. Earlier reported emulator checks are not counted as fresh results here.

## Device checks still required

1. Run focused XCTest and compile both iOS configurations on macOS.
2. Compare main screens, modal dismissal, and large text on both platforms.
3. Verify front-camera framing, overlay projection, and scoring in portrait and landscape.
4. Change the phone's clock setting, return to the app, and check Follow phone on every active screen.
5. Verify actual alarm delivery, Snooze, interruption, and terminal cleanup on devices.
6. Verify emergency touch holds and screen reader activation.
7. Force real save/schedule failures and check persistent repair plus safe completion recovery.
8. Verify StoreKit/Play ownership and public-build behavior using native purchase flows.

A passing source comparison is not enough to mark these checks complete.

## Queue and handoff notes

`IOS_SYNC.md` remains the only implementation-status queue. Historical source descriptions in it must not be used as proof that a feature is missing.

The November 11 recheck confirms F1, F2, F3, and F5 are resolved in current source. The remaining differences are F4's small copy/formatting items and F6's permitted descriptive accessibility label.

Keep affected queue entries Pending until required native checks pass. This audit does not mark any SYNC item complete.

Implementation references:

- [Android handoff](android-ux-unification-handoff.md)
- [iOS handoff](ios-ux-unification-handoff.md)

The handoffs describe the original implementation work. Use this current audit to distinguish resolved work from remaining gaps.

## Confirmed user choices — 2026-11-09

The choices below remain authoritative. Their current implementation status is shown above. The decision IDs are kept so implementation notes can refer to the conversation. Use US English and ASD-STE100 for questions and explanations.

| Question | Confirmed choice |
| --- | --- |
| 1 | Do not force custom controls to look identical by replacing platform-default styles. |
| 2, 19 | Remove the extra Next / Snoozed time line from iOS alarm cards. Do not add changing Snooze text to Android cards. |
| 3 | Show alarm setup progress, failures, and Retry on both home screens. |
| 4 | Support landscape and compact layouts on iOS, as Android does. |
| 5 | Offer Snooze before yoga starts. Do not offer it during the camera routine. |
| 6 | Allow platform differences in sound/vibration options where supported behavior differs. |
| 7 | Do not add the proposed explanation about system Stop controls to the Stop-button setting. This decision does not establish OS behavior. |
| 8 | Show the current pose number and total, such as Pose 2 of 3, on both camera screens. |
| 9 | Pause the between-pose countdown when the user leaves the app. Continue it when the user returns. |
| 10, 30 | Keep the current navigation timing on each platform. The faster iOS timing was intentional, to make its observed response closer to Android. Do not force equal animation durations across platforms. |
| 11 | Use Android's slower tour animations, including six seconds per pose-preview card. |
| 12 | Use Pro trial as the shared product term. |
| 13 | Do not add a permanent View free settings entry to Settings. |
| 14, 45 | Do not offer restoration of old trial/Pro routines, either in Settings or through an automatic post-purchase offer. Keep the current routine after purchase. This decision does not request deletion of historical saved data. |
| 15 | Use Android's one-line routine summary. Cut off long text with an ellipsis, described as `...` in this discussion. |
| 16 | Restrict testing controls to internal builds. |
| 17 | Use US English for shared product copy. |
| 18 | Continue asking about the remaining choices; do not automatically adopt every audit recommendation. |
| 20 | Use Android's ringing-screen information hierarchy. |
| 21 | Keep each platform's current Save / Cancel bar size. |
| 22 | Open the Pro offer directly for locked sounds. Remove the extra iOS confirmation. |
| 23 | Show the full phone-placement instruction on both apps: prop the phone upright on the floor, take about two steps back, then follow the pose. |
| 24 | Remove the Pro trial paragraph from the iOS tour invitation. Explain the trial on the welcome screen. |
| 25 | Use Android's switch colors on both apps. |
| 26 | Use Android's music symbol/emoji with Stop for sound previews. |
| 27 | Use Android's Snooze behavior: changing the duration or count also turns Snooze on in the draft. |
| 28 | Use swipe dismissal for sound, snooze, and pose-duration panels. Remove their existing iOS Done buttons. |
| 29 | Show Privacy Policy and Terms of Use links on both Pro offers. |
| 31 | Keep the iOS native labels Open Yoga Alarm when Snooze is available, otherwise Start Yoga. Use Start inside the app. |
| 32 | Use Android's new-pose picture timing: hold in the center for 1 second, then move to the header over 0.65 seconds. |
| 33 | Use the iOS ringing instruction on both: Place your phone where your full body fits on screen. |
| 34 | After a test routine, show Test complete. Your streak did not change. on both apps. |
| 35 | Remove 20 seconds is included in the free routine. from the pose-duration panel on both apps. |
| 36 | Use Allow for permission actions. Do not adopt the proposed switch to Open Settings as the shared label. |
| 37 | Remove Finding you… from both apps. This is a copy choice; it does not remove the camera or detection startup logic. |
| 38 | Use the tour's phone-placement picture on the preparation screen before a camera test, instead of the iOS yoga symbol or Android's lack of a picture. |
| 39 | Use Android's Emergency stop behavior on both apps: touch users hold for 5 seconds with emergency passes or 15 seconds without passes. Screen reader users can activate Stop alarm without the timed hold. Both routes use the same confirmation handler and emergency-pass/streak rules. |
| 40, 43 | Do not show Snoozing… on the Snooze button. Remove the existing iOS label. |
| 41, 43 | Do not show a Try sound again button. Remove the existing iOS action. |
| 42, 43 | Do not show a Try saving again button after completion. Remove the existing iOS action. This is a UI decision, not a request to discard results or ignore save failures. |
| 44 | Select unlocked poses by swiping only. Do not add tap selection to Android; remove the iOS tap-to-select behavior for neighboring unlocked cards. |
| 46 | Include a Support link on both Privacy and Safety pages. |
| 47, 47a–c | Add Time format to Settings on both apps. Offer Follow phone, 24-hour, and 12-hour (AM/PM); Follow phone is the default. Apply the chosen format throughout the app, including home cards, the alarm editor, ringing screens, tour summaries, and other app-formatted times. The 12-hour editor includes AM/PM. This changes presentation, not scheduled alarm times. |
| 48 | Show completed/total permission progress on both setup cards, such as Finish alarm setup · 2/4. Use the permissions needed on the platform to determine the total. |
| 49 | Use Android's short permission explanation on both: So your alarm rings and the camera can check your poses. |
| 50 | When the user has no streak, use Android's Emergency stop text on both: Use this only when you cannot complete your yoga routine. |
| 51 | Show a PRO label for locked sounds on both apps. |
| 52 | Show Back at the top of the preparation screen before a camera test on both apps. |
| 53 | Do not show Camera off · Processed on-device · Never recorded on either camera-test preparation screen. This answer concerns the preparation screens asked about, not every camera/privacy statement elsewhere. |
| 54 | Remove No payment details or automatic charge. from the Pro trial explanation on both welcome screens. |
| 55, 55a | Use Save. Remove the separate free-settings confirmation page. One Save action stores the trial routine; free settings apply automatically when the trial ends without another confirmation, using the rules confirmed in 57. |
| 56 | Remove Gentle pulse below Vibration on Android. Do not add that text to iOS. |
| 57 | Automatically use the existing suggested-free-setting rules when the trial ends. Use three 20-second poses; retain free poses in the first three positions; replace Pro or missing poses with that position's default (Mountain / Warrior II / Tree); retain a free sound, or use Sunbird Morning Call for a Pro sound. No extra confirmation is required. |

The audit records observations. It is not a second implementation queue. Unresolved shared-copy choices in F4 remain proposals.

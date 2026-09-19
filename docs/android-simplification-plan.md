# Android simplification review and plan

Reviewed 2026-09-19 against working tree based on `47e8954`. Implementation began after the user approved the plan and required preserving future-use and uncertain code. The original findings below retain their review-time file references; the status table records the resulting changes.

## Implementation status — 2026-09-19

| Items | Outcome |
| --- | --- |
| 1–3: removal candidates | Preserved diagnostic screens/metrics, unused state and metadata, the old picker, and preview dependencies because retained intent is uncertain. Removed confirmed unused imports during the UI moves. Billing and debug access are unchanged. |
| 4: alarm cleanup | Successful snooze reuses `closeFiredAlarm()`. The service shares playback-resource release between completion and destruction. |
| 5: UI organization | `MainActivity.kt` is now 87 lines. Existing UI moved into `YogaAlarmRoot.kt`, `RoutineRoute.kt`, `AlarmHomeScreen.kt`, `AlarmEditorScreen.kt`, `AppInfoSheets.kt`, and `YogaAlarmTheme.kt`. The routine route receives the existing ViewModel and collects its own UI state, reducing its parameters from 22 to 13. |
| 6: routine tracking | `start()` reuses `resetTracking()`; inactive/completed frames return early. The narrower transition reset and all timing constants remain unchanged. |
| 7: sound preview | Timeout, backgrounding, and toggle/switch handling reuse local preview cleanup. Disposal retains its direct resource release. |
| 8: readiness animation | Pointer position is read inside `graphicsLayer`, avoiding a composition-time animation read. Visual/device profiling remains pending. |
| 9: scoring | Failed required framing returns before metric calculations in five scorers. Weighted scoring avoids zipped pairs while retaining Float multiplication and Double accumulation. The optional visible-side rewrite was omitted because its extra restructuring offered little maintenance benefit. |
| 10: guide geometry | Points use existing `Offset` values; repeated hip projections are cached and the forced nullable-projection assertion is removed. Coordinates, order, fallback placement, and scaling are preserved. |
| 11: documentation | README and billing-status wording updated. `knowledge.md` and existing `IOS_SYNC.md` source references now point to the moved files. No new shared behavior or sync ID was introduced. |
| 12: release bundle | Deferred as separate housekeeping: an official artifact-retention location has not been established. The user's modified AAB and its tracking remain untouched. |

Validation completed:

- Debug Kotlin and unit-test sources compile; the mechanical UI move was compiled before route plumbing changed.
- All 43 selected tests pass: routine lifecycle, detection gate, skeleton smoothing, side-pose scoring, framing hints, projection, and saved-draft serialization. Added a regression for starting a different alarm after a camera error during a transition.
- A temporary comparison harness checked 200,016 original-versus-refactored pose evaluations with bit-identical scores, identical framing flags, and identical guidance. Inputs included null, full 33-point frames, confidence boundaries, deterministic random frames, jittered standing frames, and an accepted Warrior fixture; every pose had accepted samples. This is characterization evidence, not a benchmark or exhaustive proof over malformed inputs.
- Moved presentation function bodies were checked against the pre-move snapshot, allowing only shared-color naming and cross-file visibility changes. The two route functions contain the planned plumbing changes.
- Billing, debug access, model metadata, frame diagnostics, and Pose Lab files match the pre-refactor source byte-for-byte. Updated source references resolve, and whitespace checks pass.

Remaining validation: no Android device is connected. Alarm notification/binding, recreation/navigation, audio-preview, visual-overlay, and recomposition checks still need a physical device. No APK installation, release build, publish, or commit was performed. The existing one-second pose-introduction edit, version changes, release bundle, and onboarding project were preserved.

The strongest opportunities are reusing existing cleanup logic, separating the two large UI files by responsibility, and removing code confirmed to be obsolete. The core camera, scheduling, and pose-detection components are relatively small and contain important behavior that should be preserved.

**Preservation constraint added after review:** Preserve code, dependencies, diagnostics, and extension points added for future use. Having no current callers or readers is evidence of current non-use, not proof that something should be deleted. Before implementing a removal below, check its purpose in project documentation, relevant comments, and Git history. Remove it only when that evidence establishes it is obsolete or superseded. If intent remains uncertain, retain it and continue with the independent simplifications; do not require the user to reconstruct its history. Items 1–3 are conditional candidates under this rule, not unconditional deletion instructions.

`PlayBillingManager` must remain. It is already constructed and started by `MainActivity`, provides billing state, and handles purchase and restore callbacks. `knowledge.md:282–343` also documents the planned production billing work. Preserve that foundation and its `BillingUiState.loading` field during this cleanup; the field's current lack of readers does not establish whether it was reserved for upcoming UI work.

## Scope and evidence

Reviewed all 28 production Kotlin files (5,441 lines), all nine Android unit-test files (717 lines), Android build configuration, manifest, ProGuard rules, resource references, and supporting project/release documentation. Other repository directories were inventoried to establish their relationship to Android; the website and video applications were not given a separate implementation audit.

The review includes the existing uncommitted Android source/config changes. Existing changes to `IOS_SYNC.md`, `app/build.gradle.kts`, `app/release/app-release.aab`, `RoutineCameraScreen.kt`, and the untracked `remotion-onboarding/` directory were left untouched. References below point to the reviewed working tree; line numbers will move when implementation starts.

Findings are based on source inspection, caller/reference searches, resource-reference checks, and test inspection. No build, test execution, device run, dependency-resolution report, or performance profiling was performed. Performance benefits below are hypotheses supported by visible unnecessary work, not measured speedups.

## Follow-up: future-use and historical-purpose checks

Documentation and targeted Git history checks distinguish these cases:

| Component | Evidence of purpose | Decision for this cleanup |
| --- | --- | --- |
| `PlayBillingManager`, billing catalog, purchase/restore paths | Active app wiring; `knowledge.md:282–343` and `docs/play-billing-setup.md` specify further production billing work. | Preserve. The manager is an existing implementation with planned follow-up work. |
| `ProAccessStore`, debug Pro activation, `BuildConfig.DEBUG` guards | Active test access; `docs/play-billing-setup.md:121–126` explicitly permits no-charge activation in direct-installed debug builds. | Preserve as development support, including release guards. |
| Legacy sound aliases and optional persisted-field defaults | `AlarmConfig.fromStoredName()` and `AlarmStore.decode()` support previously saved configurations. | Preserve as backwards compatibility; old names are not dead features. |
| Separate frame/progress/UI flows and camera worker/buffer handling | `docs/performance-pass.md:7–24` records deliberate responsiveness and resource-management decisions. | Preserve. Their complexity has a documented current purpose. |
| `IOS_SYNC.md` and native iOS handoff material | Explicit cross-platform work queue and implementation guidance. | Preserve as support for the parallel app. |
| Pose Lab screen, debug view model, and inference metrics | Commit `809aa62` actively used the screen/view model in the original app. Current references are absent. The README documents diagnostic metrics, but is explicitly marked stale by `knowledge.md:456`. | Historical diagnostic feature; no confirmed future commitment found. Retain while its intended diagnostic use remains uncertain. |
| `AlarmSound.description` | Commit `d2552d2` displayed descriptions in the sound picker; `4a551fa` removed that UI usage. | Formerly used metadata, not evidence of a planned feature. Retain unless confirmed obsolete. |
| `PosePickerSheet` | Commit `26dc585` called it from the editor; current selection uses `PoseCarouselCard`. | Strong evidence of a superseded UI. Check for any retained purpose before deleting. |
| Billing `loading`, exported routine-progress state, Compose preview dependency | Current non-use is established; the inspected documentation/history does not establish a specific planned consumer. | Intent remains uncertain. Preserve rather than calling these confirmed future-proofing or confirmed dead code. |

These checks establish actual or former usage where evidence exists. They cannot prove the original author's unrecorded intentions. No implementation was changed as part of the follow-up.

## Recommended order

| ID | Improvement | Priority | Effort | Behavior risk |
| --- | --- | --- | --- | --- |
| 1 | Check prototype intent; remove only confirmed obsolete code | Conditional | Small | Establish future purpose first |
| 2 | Review unused state/metadata; remove obsolete fields and unused imports | Conditional | Small | Retain future-use fields |
| 3 | Check whether the preview dependency supports planned tooling | Conditional | Small | Retain if planned or uncertain |
| 4 | Reuse alarm cleanup already present in the app | Next | Small | Low to medium |
| 5 | Split large UI files along existing responsibilities | Next | Medium | Medium; Compose state identity |
| 6 | Simplify routine tracking reset and frame handling | Next | Small | Medium; timing-sensitive |
| 7 | Consolidate sound-preview cleanup | Next | Small | Medium; asynchronous audio |
| 8 | Defer readiness-animation state reads to drawing | Optional | Small | Low |
| 9 | Reduce unnecessary pose-scoring work | Optional | Small to medium | Medium; preserve numeric results |
| 10 | Simplify guide geometry and repeated point lookup | Optional | Small | Low to medium; visual equivalence |
| 11 | Correct stale entry-point documentation | First | Small | None for runtime |
| 12 | Stop tracking generated release bundles | Housekeeping | Small | Preserve existing artifact first |

“First” means high confidence and good maintenance value, not a production defect severity. Each item should be independently reviewable. There is no reason to implement all twelve in a single refactor.

## 1. Check prototype intent before removing unreachable code

**Evidence**

- `app/src/main/java/com/lurremcfly/yogaalarm/ui/CameraSpikeScreen.kt:38`: `CameraSpikeScreen` has no call sites in the Android source or manifest. The entire 280-line file serves that screen.
- `app/src/main/java/com/lurremcfly/yogaalarm/ui/PoseDebugViewModel.kt:24`: `PoseDebugViewModel` has no construction or `viewModel()` call site. Its UI state is consumed only by the unreachable screen.
- `app/src/main/java/com/lurremcfly/yogaalarm/model/BodyLandmarks.kt:17`: `FramingResult`, `BodyFraming`, and its private `NamedLandmark` are used only by that debug view model. Production uses `PoseScoring` framing instead.
- `app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmApp.kt:1375`: private `PosePickerSheet` has no callers. Pose selection now goes through `PoseCarouselCard` at line 917. The old sheet also contains obsolete free-only selection logic.

**Plan:** Check documentation and the commits that introduced or disconnected these components. Delete the two prototype UI files, unused framing types/object, and `PosePickerSheet` only where that check establishes they are superseded and have no retained future purpose. Keep `BodyPoint`, `BodyLandmarks`, `PoseCameraController`, production scoring, and the production skeleton overlay. Approximately 440 lines are candidates, not a deletion target.

Retain `PoseFrame.inferenceTimeMs` (`model/PoseFrame.kt:5`) and its calculation in `camera/PoseLandmarkerClient.kt:128` while the future diagnostic purpose is uncertain. Consider removal only if diagnostics are confirmed obsolete. It has no production consumer outside the unreachable feature. If removal is justified later, update positional `PoseFrame` construction in tests carefully: `capturedAtMs` remains essential to stale-frame rejection and must retain its meaning.

**Validation:** Repeat references checks, compile the Android Kotlin source and affected test sources, and run existing frame/projection/smoothing/routine tests if the `PoseFrame` constructor changes. Check the current editor still selects and previews poses. No replacement diagnostic framework or new debug route is necessary. If the Pose Lab is intentionally retained for future manual diagnosis, preserve it; making it reachable is separate feature work and is not a prerequisite for keeping it.

## 2. Review unused state and metadata; remove only confirmed obsolete fields

**Evidence and minimal changes**

The state/metadata removals below are conditional on the preservation check. Retain fields supporting planned features, and retain uncertain candidates. Unused imports can still be removed because reintroducing an import does not remove or redesign a capability.

- `ui/RoutineViewModel.kt:40–41,274–275`: `RoutineUiState.soundEnabled` and `alarmSound` are published but never read. Audio uses `AlarmConfig` and `RoutineProgress.alarmLevel`. Remove these UI-state properties and assignments, preserving the actual alarm configuration fields.
- `ui/RoutineViewModel.kt:46,257–258`: `RoutineProgress.routine` is published but never read. Remove that public state field and the calculation in `publish()`. Keep the routine-progress calculation in `updateAlarmLevel()` at lines 289–295: it actively controls the fade curve.
- `billing/PlayBillingManager.kt:29,62,106,187,205`: `BillingUiState.loading` is written but never read by the UI or manager. Preserve it and its writes in this cleanup given the unfinished billing work. Preserve `PlayBillingManager`, `connected`, `purchasing`, messages, offers, and entitlement behavior as well.
- `model/AlarmConfig.kt:19`: `AlarmSound.description` supplies ten descriptions that are never read. Remove the field and enum arguments. Keep display names, enum names, free/Pro flags, and legacy stored-name mappings.
- `MainActivity.kt:29`: unused `Arrangement` import. `ui/AlarmApp.kt:12,37`: unused `horizontalScroll` and `itemsIndexed` imports. Remove `LazyRow` too after deleting the old pose picker, if it has no remaining uses.

**Benefit:** Less state to synchronize, fewer misleading fields, and a smaller public surface. Removing unobserved fields can also avoid emissions caused solely by those values changing, but the maintenance improvement is the primary reason.

**Validation:** Confirm reads and destructuring uses remain absent before deleting fields; compile the affected source/test sets and run `RoutineViewModelTest` for the progress change. Do not remove Compose `getValue`/`setValue` imports: delegated state uses them implicitly. Do not remove similarly named persisted fields from `AlarmConfig`.

## 3. Check the future purpose of the direct Compose preview dependency

**Evidence:** `app/build.gradle.kts:65` declares `androidx.compose.ui:ui-tooling-preview`. There are no `@Preview`, `PreviewParameter`, or Compose tooling imports under `app/src`.

**Plan:** Check whether this declaration was added for planned Compose previews or a retained development workflow. Keep it if so, or if its purpose remains uncertain. Remove it only if confirmed obsolete. Keep debug `ui-tooling` unless Android Studio inspection support is intentionally being removed. CameraX, MediaPipe, Billing, lifecycle, Compose, JUnit, and coroutine-test all have concrete uses; this review found no basis for removing those libraries.

**Validation:** Check debug/release dependency resolution and scoped Kotlin compilation when implementing. A transitive dependency may still bring preview tooling in, and release shrinking is already enabled, so do not present this as a significant APK-size reduction. Do not combine this with version upgrades.

## 4. Reuse existing alarm cleanup

**Evidence:** `MainActivity.kt:217–225` already defines `closeFiredAlarm()`. The successful snooze branch at lines 357–364 repeats its service stop, window reset, state reset, and navigation sequence exactly.

**Plan:** Call `closeFiredAlarm()` only after `scheduleSnooze()` succeeds. Preserve the failed-scheduling path, which keeps the alarm active. This is the simplest confirmed duplication in the active application.

There is a second, smaller opportunity in `alarm/AlarmForegroundService.kt:39–50,148–156`: `finishRoutine()` and `onDestroy()` both close audio, cancel vibration, and release the wake lock. A private `releasePlaybackResources()` called by both is justified by actual reuse. Keep finish notifications, alarm identity checks, flow updates, `stopForeground()`, and `stopSelf()` at their present call sites.

**Validation:** Exercise snooze success and scheduling failure, ordinary stop, emergency stop, completion, and stopping from a notification while the UI is bound. The UI and receiver stop mechanisms are intentionally different: `SnoozeReceiver.kt:21` explains why `stopService()` alone is insufficient while bound. Do not collapse all stop paths into a generic command or merge their streak/pass semantics.

## 5. Split large UI files along existing responsibilities

**Evidence:** `MainActivity.kt` is 964 lines. The activity itself ends at line 170; the rest combines root navigation, persistence, permissions, billing, service binding, camera lifecycle, completion handling, audio, and presentation. `CameraPermissionRoute` alone spans lines 511–747 and accepts 22 parameters. `ui/AlarmApp.kt` is 1,553 lines and contains home UI, editor UI, all pickers, paywall, privacy sheet, and shared styling.

**Plan, in two separate steps**

1. Move existing functions without redesigning them. Keep activity/intent/window handling in `MainActivity.kt`; place the root composable and routine route in UI files. Separate home, editor/pickers, and paywall/privacy presentation in `AlarmApp.kt`. Keep small helpers next to their only consumers. Move genuinely shared pieces such as `PoseGlyph`, the existing colors, and switch styling together only where needed to avoid duplication.
2. After the moves are stable, let the stateful routine route receive the already-created `RoutineViewModel` and collect its UI state there, rather than forwarding its state plus seven view-model method callbacks through the root. Keep `RoutineCameraScreen` driven by state and callbacks. Keep the same ViewModel owner/lifetime and continue collecting frames/progress in their narrow consumers.

**Benefit:** Routine ticking no longer needs to be understood alongside billing and alarm editing, and changing a picker no longer means searching a 1,500-line file. Passing the existing ViewModel at the route boundary removes repetitive plumbing without introducing an interface, controller hierarchy, navigation library, or dependency-injection framework.

**Constraints:** Preserve `rememberSaveable` inputs, composition identity, lifecycle observers, effect keys, and `rememberUpdatedState` behavior. File moves alone do not justify new abstractions. Do not merge `pendingEditorAlarm` with `cameraAlarm`: testing a single pose intentionally needs a different routine while preserving the complete unsaved editor draft (`MainActivity.kt:318–322`).

**Validation:** Compile after the mechanical moves. Check home → editor → full-routine test → editor, single-pose test → editor, unsaved draft restoration, fired alarm interrupting another screen, configuration recreation, background/foreground transitions, and completion. Existing serialization tests do not replace these route/lifecycle checks.

## 6. Simplify routine tracking reset and frame handling

**Evidence:** `ui/RoutineViewModel.kt:78–104` manually resets almost the same tracking fields as `resetTracking()` at lines 185–197. `onPoseFrame()` at lines 142–168 wraps nearly its entire body in a session/phase conditional.

**Plan:** Reuse `resetTracking()` for the matching initialization fields in `start()`, leaving session initialization, hold progress, phase, volume, and UI-state initialization explicit. Use an early return for inactive/completed sessions in `onPoseFrame()` to remove a level of nesting. Preserve evaluation, smoothing, framing-grace, and detection ordering.

**Constraints:** The transition reset at lines 222–230 is deliberately narrower. It keeps the camera alive while changing poses; it must not blindly call the complete tracking reset. Do not unify camera readiness, framing confidence, pose acceptance, and session activity into one flag. They represent different conditions. Preserve the 50 ms ticker, 900 ms frame timeout, 350 ms framing grace, 3-second transition, and all current score/fade constants.

**Validation:** Run `RoutineViewModelTest`, `PoseDetectionGateTest`, and `SkeletonSmootherTest`. Extend focused coverage only if needed for starting a different configuration after a camera error or during a transition. Keep the existing tests for pause/resume, late frames, transition credit, completion, and 15/30 FPS timing.

## 7. Consolidate sound-preview cleanup

**Evidence:** `ui/AlarmApp.kt:1104–1121,1166–1179` maintains both `previewPlayer` and `previewing`, with repeated cleanup at timeout, lifecycle stop, disposal, and preview switching.

**Plan:** Add one small local `stopPreview()` that closes the current player and clears player/selection state. Reuse it in the repeated paths. Before calling it from the toggle handler, capture whether the tapped sound is already playing so toggling still stops it rather than immediately restarting it. Keep error-message clearing in the current interaction paths; stopping is not always an instruction to clear an error.

This should remain a local simplification inside the sound picker. A generic audio-session abstraction or rewriting all playback as effects would increase the risk and scope unnecessarily.

**Validation:** Manually check same-sound toggle, rapid sound switching, sheet dismissal, backgrounding, the 10-second timeout, playback failure, and locked Pro sound taps. Preserve preview volume `0.7`, alarm audio attributes, asynchronous preparation, and error callbacks. Keep `AlarmAudio`'s player-identity checks and callback detachment during release.

## 8. Defer readiness-animation reads to drawing

**Evidence:** `ui/AlarmApp.kt:421–433` reads `pointerOffset.value` while composing `AlarmReadinessCard`, passing a changing float into all four rows. `ReadinessRow` eventually uses that value only for `graphicsLayer.translationX` at line 476.

**Plan:** Pass a deferred value supplier, or the existing animation state, and read the offset inside the `graphicsLayer` block. Preserve the existing animation and which row displays the pointer. This is a small change at an existing component boundary, not a new animation abstraction.

**Benefit:** Avoid invalidating the whole readiness card in composition for a value only needed to move the pointer. The source shows the early read; the actual recomposition reduction should be verified rather than assumed.

**Validation:** Compare recomposition counts with Android Studio inspection and visually confirm the pointer sequence, tap suppression, and permission-return behavior. Preserve the 6 dp movement and current timing.

## 9. Reduce unnecessary work in pose scoring

**Evidence**

- `model/PoseScoring.kt:45–110,157–204`: Mountain, Warrior II, Tree, Triangle, and Goddess calculate their metrics even when required framing has already failed, then discard the score.
- `model/PoseScoring.kt:268`: `weighted()` allocates a zipped list of pairs solely to multiply and sum values.
- `model/PoseScoring.kt:113–117`: visible-side selection rebuilds the same landmark-index lists and allocates filtered/mapped collections. Routine processing calls this through both evaluation and the framing hint on side poses.

**Plan:** First return `PoseEvaluation(0f, false)` immediately after a failed required-framing check in the five scorers, matching the pattern already used in `wideLegFold()`. Separately, retain the small `weighted()` helper but sum matching indices without creating zipped pairs. Consider hoisting the repeated side-landmark indices; rewrite side selection only if the resulting two-side comparison is actually clearer.

**Constraints:** Preserve each pose's exact required landmarks, optional-ankle fallback, visibility/presence checks, coordinate margins, side tie-breaking, weights, and arithmetic order/precision. The current weighted calculation multiplies in Float and sums in Double before converting to Float; replacing it with a casual chain of Float additions could shift borderline scores. Do not introduce a scorer registry, shared metric graph, or table-driven pose engine.

**Validation:** Run `SidePoseScoringTest`, `PoseFramingHintTest`, and `RoutineViewModelTest`. Existing scoring fixtures emphasize side poses and wide fold, so add representative accepted/rejected and threshold-adjacent cases for the other poses before changing their arithmetic. Compare old/new scores and framing results over a fixed fixture set. Expect modest savings at roughly 15 inference frames per second; no claim of a major performance problem is supported.

## 10. Simplify guide geometry and repeated point lookup

**Evidence:** `ui/RoutineCameraScreen.kt:396–399` stores geometry as `Pair<Float, Float>` nested inside `List<Pair<Pair<Float, Float>, Pair<Float, Float>>>`. Rendering at line 386 accesses `.first`/`.second`. Hip projection at lines 365–366 repeats `mapped(23)`/`mapped(24)` for checks and values and then uses `!!`.

**Plan:** Use the already-imported Compose `Offset` for geometry points, keeping the existing `GuideGeometry` and pairs of segment endpoints. Use `.x`/`.y` and cache the mapped hips in local variables before branching. Prefer an explicit nullable projection branch over `projection!!` where that improves readability without disrupting the fallback guide.

**Benefit:** Coordinates become readable without interpreting three layers of pairs, and repeated projection/lookups disappear. No new vector/segment framework is needed.

**Validation:** Preserve every numeric coordinate, fallback position, scale, and facing-direction rule. Compare screenshots of all eight guide poses, both facing directions for side poses, missing landmarks, and wide-fold alignment. Retain `PreviewProjectionTest`. This is lower priority than deleting dead UI; the current geometry already works and is cached with `remember(pose)`.

## 11. Correct stale entry-point documentation

**Evidence:** `README.md:1–3,18–26` presents the current Android app as a camera spike and explicitly says pose classification, hold timing, audio fading, and scheduled alarms are not implemented. All four exist in the reviewed app. `docs/google-play-policy-declarations.md:57–58` lists planned product IDs, while `model/ProPlan.kt:10–12` still uses the prototype IDs; `knowledge.md:283–309` explains that distinction.

**Plan:** Rewrite the README's current-scope section around the shipped Android structure and link to `knowledge.md`, `docs/android-reliability-checklist.md`, and the billing setup document for details. Label the billing policy catalog as the intended setup and link to current implementation status. Keep build/run instructions and useful physical-device checks.

**Constraints:** Preserve `docs/prototype-baseline.md` as explicit history. Do not migrate product IDs, pricing, subscriptions, or verification behavior as a documentation cleanup. Do not remove the browser tester, privacy site, or onboarding-video project because Android does not compile them; those have separate documented purposes.

**Validation:** Check local links and ensure descriptions agree with source. No build or tests are needed.

## 12. Stop tracking generated release bundles

**Evidence:** `app/release/app-release.aab` is a tracked generated file of 44,143,096 bytes and has existing local modifications. `.gitignore:5` ignores `**/build/`, but not this output location. Release instructions in `docs/google-play-monetization-guide.md:208` and `docs/performance-pass.md:70` use `app/build/outputs/bundle/release/`.

**Plan:** Establish where the official signed bundle should be retained, preserve the existing artifact there, then stop tracking generated AAB output and add an appropriately narrow ignore rule. Prefer a single documented output location. Keep this housekeeping separate from source simplification and do not rewrite Git history.

**Benefit:** Avoid future large binary diffs and source/artifact ambiguity. Untracking does not remove the artifact from existing Git history or immediately shrink old clones.

**Validation:** Confirm the preserved bundle is accessible and identical before changing tracking. Check the resulting Git diff and output instructions. No source build is required for an ignore/tracking change. The current locally modified bundle must not be discarded.

## Areas to preserve or investigate separately

- **Camera architecture:** `PoseCameraController` and `PoseLandmarkerClient` separate lifecycle/binding from inference. They already use a single analysis worker, latest-frame backpressure, a frame-rate limit, reusable input buffers, and deterministic cleanup. Collapsing them or removing nested error/cleanup handling would not be a clear simplification.
- **Pose state and filtering:** Keep separate low-frequency UI state, frames, and progress flows. Keep display-only smoothing separate from raw landmark scoring. Thresholds, hysteresis, frame freshness, and reset differences are functionality, not incidental complexity.
- **Storage:** `AlarmStore` is only 68 lines and contains backwards-compatible decoding. There is no maintainability case here for Room, a repository interface, or a format migration. Preserve legacy sound-name aliases and optional-field defaults. `WakeProgressStore.load()` normalizes and persists rollover state; its write should not be removed as a supposedly pure-read optimization without examining clock/date behavior.
- **Platform configuration:** All Android binary resources have references. Keep current assets, API-specific themes, manifest components/permissions, `BuildConfig` generation, and documented MediaPipe JNI/protobuf/Flogger keep rules. No evidence supports deleting them.
- **Billing query ownership:** `PlayBillingManager.kt:40–41,170–200` stores per-refresh aggregation in manager-wide mutable fields. Overlapping refreshes can mix callbacks from separate requests. This is a concrete ownership concern, but fixing request races can alter observable entitlement/message behavior. Investigate with overlapping-response tests as a separate correctness task; do not silently include it in a behavior-preserving cleanup or build a new billing architecture around it.
- **Test cleanup:** `RoutineViewModelTest` has both `@After` cleanup and a `routineTest` finally block, plus several redundant end-of-test `stop()` calls. These are low-impact repetition. Remove only incidental duplicate cleanup when already editing the tests, preserving calls that are the behavior under test. A dedicated test-framework refactor is not worthwhile.

## Implementation and validation sequence

1. Make the documentation corrections and unused-import cleanup. Check documentation/comments/history for the future purpose of deletion candidates; remove only confirmed obsolete code, fields, or dependencies, and retain uncertain candidates. Preserve `PlayBillingManager` and its loading state. Keep the release-bundle housekeeping separate. Compile the touched Android/test source sets; run the frame/routine tests if changing `PoseFrame` or progress publication.
2. Reuse the exact existing snooze cleanup and consolidate service resource release. Validate the relevant alarm paths on device, especially bound-service notification actions and failed snooze scheduling.
3. Move UI functions in a mechanical commit. Change route plumbing only afterward, with navigation, saved-draft, recreation, and lifecycle checks.
4. Apply routine-reset and sound-preview simplifications independently. Run the affected existing unit tests and the focused audio checks above.
5. Take the animation, scoring, and geometry items only where the resulting diff is smaller or clearer. Add characterization coverage before numeric changes; measure performance if claiming performance improvements.

Use scoped Gradle tasks and test selectors rather than full builds/suites by default. For example, run `:app:testDebugUnitTest` with `--tests` selecting `RoutineViewModelTest`, `PoseDetectionGateTest`, or the relevant model test class. Dependency and source deletion checks can use `:app:compileDebugKotlin` and `:app:compileDebugUnitTestKotlin`; also check release compilation when changing release dependencies. Existing device checks are documented in `docs/android-reliability-checklist.md`.

All accepted refactors should preserve UI behavior, saved formats/defaults, pose acceptance, timing, audio, scheduling, and entitlement rules. Kotlin-only refactors, file moves, dead-code removal, and Android build cleanup do not require an iOS sync entry. If investigation reveals a shared behavioral bug and a fix is chosen, separate that change and update the existing relevant `IOS_SYNC.md` entry or add the next sequential entry as required by `AGENTS.md`.

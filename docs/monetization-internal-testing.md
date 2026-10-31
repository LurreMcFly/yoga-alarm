# In-app testing and Play Billing

This is a local preview and Play Billing acceptance runbook, not production approval. The release policy accepts client-side last-known Pro: subscriptions for seven days after a successful online ownership refresh, lifetime until a successful online refresh changes ownership. Backend verification is future hardening, not a release prerequisite. Client evidence cannot independently validate subscription expiry, refunds, revocations, or tampering, and a validated network does not prove Play returned fresh server data. Device/Play acceptance remains required; do not enable `monetizationBillingReady` merely to run these tests.

## Quick app testing

Build and install the app normally, including a signed release. No testing build variable is needed.

1. Open **Settings** and turn on **Testing mode** beneath Preferences.
2. Switch **Test Pro access** on for Pro features or off for Free features, regardless of real purchase ownership.
3. Turn **Testing mode** off to return to normal purchase and preview behavior.

Testing mode defaults off. Both choices persist across restart, and the previous Pro test choice returns when testing is enabled again. The controls are present in debug and release builds. Settings shows the selected test access while testing is on. These switches do not make a purchase, change Play purchase records, reset saved alarms, or reset history. Real alarm completions still finish normally while testing, but preview accounting, retirement and automatic fallback conversion are paused until testing is turned off. Changes are blocked during ringing, snooze, routines and other unsafe app flows. A failed save keeps the previous setting.

Validation (2026-10-31, Android commit <pending>): debug and release Kotlin compilation and the six focused JVM suites passed, with 58 tests in each variant and no skips or failures. Suites: `ProTrialStoreTest`, `AlarmStoreTrialTest`, `AlarmOccurrenceStoreTest`, `WakeCompletionRecoveryTest`, `PlayBillingLastKnownAccessTest`, and `ProTrialTest`. Coverage includes runtime-mode persistence/defaults, real-owner overrides, returning to genuine ownership, preview pause/resume, alarm editing, failed-write rollback and in-flight guards. Device UI and Google Play acceptance remain unverified.

## Build and signing

Use the existing Android Studio **Build > Generate Signed Bundle / APK > Android App Bundle > release** workflow with the existing upload key. No property edit or separate testing artifact is needed. The old `proTrialInternalTesting` property and `PRO_TRIAL_INTERNAL_TESTING` build gate have been removed. Public preview rollout and analytics remain separate settings.

The debug variant still installs beside the regular app as **Yoga Alarm (test)** with package `com.lurremcfly.yogaalarm.debug`; release uses the normal package. Both include the runtime controls.

Command-line bundle generation remains:

```bash
JAVA_HOME=/opt/android-studio-for-platform/jbr ./gradlew :app:bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`. This repository has no release signing configuration, so this command alone does not produce an upload-ready signed artifact. Use the existing signing wizard and a version code higher than previously uploaded artifacts. Keep keystore paths and passwords outside tracked files and command history. This source change does not install, sign, upload or publish an artifact.

## Google Play checks

Turn **Testing mode** off in Settings when verifying real purchase-driven feature access and restoration. The local access switches do not validate checkout or ownership.

Use the signed Play-installed app and a license tester for billing acceptance. Internal-track membership and license testing are separate settings; confirm checkout identifies a test payment method before paying. See [Google's billing testing guide](https://developer.android.com/google/play/billing/test), [license tester setup](https://support.google.com/googleplay/android-developer/answer/6062777), and the [billing acceptance checklist](play-billing-setup.md#internal-testing-acceptance-checklist).

The notes below predate the runtime Testing mode switch. References to testing build flags, separate testing artifacts, removed preview controls and historical validation results describe earlier versions.

## Historical preview controls

The detailed controls and phone sequence below describe the previous internal UI and are retained as historical test notes. The current simple workflow is **Quick app testing** above.

- **Test as non-paying user** lets an existing lifetime/subscription owner exercise the free/preview flow without refunding the purchase or changing Play accounts. It defaults off and persists in testing-enabled builds; builds with the testing flag false ignore a stale saved value. While on, preview UI, editing access, and real/synthetic preview-day accounting ignore paid access locally, and checkout is disabled. Actual Play entitlement, token verification, and ownership remain unchanged. This does not simulate a paid purchase or offline restoration. Use the license-test lifetime purchase described above for real paid/offline testing.
- Reset deterministically starts a fresh preview at zero of five days. It preserves current routines, sounds, schedules, alarms, confirmed fallback/customization metadata, purchases, occurrence history, and wake progress. The old trial-original restoration snapshot and the previous restoration-offer dismissal marker are cleared, allowing a later real purchase to offer restoration again. It never restores expired Pro configurations; to test another fallback conversion after expiry, re-edit through the normal editor and review the fallback again.
- Advance day exercises the same preview accounting and fifth-day fallback conversion as a real completion. It uses today or the day after the latest counted date, whichever is later, without changing the device clock or creating a real alarm occurrence/wake-progress record. Each press advances at most one day; an exhausted preview cannot advance further.
- Reset/advance refuse changes while paid ownership is detected unless **Test as non-paying user** is on. Checkout, verification, pending payment, and an active occurrence/routine still block changes, including switching test mode. UI and storage gates both enforce the restrictions; a failed write must not report success.
- Turning non-paying mode off restores access from real Play ownership without overwriting alarm edits. The existing restoration dialog may offer an explicit restoration where fallback configurations remain unchanged. Existing paid customizations without a confirmed trial fallback are not automatically converted: re-edit and confirm a free fallback to test expiry safely.
- The internal expiry switch defaults to the automatic paywall. Select **Keep Pro pill only at expiry** to leave the offer in the Home header. Both modes show the countdown only during an active preview. Builds with testing disabled ignore the saved switch.

For offline acceptance, buy or restore online, force-stop and reopen in airplane mode. Check subscription access before seven days and the reconnect state at/after seven days, including expiry while the app stays open. Lifetime must survive offline restarts until a successful online refresh changes ownership. Failed, partial, missing-result, offline, or pending-only responses must neither clear paid evidence nor renew the timestamp. Only a successful online snapshot of both product types may resolve an empty ownership result. At offline subscription-window expiry, new Pro edits lock but saved routines, sounds, enabled state, schedule, order and in-flight alarm snapshots stay intact. Restore online and verify Pro editing returns when still owned. These are required checks, not executed results.

First test free access, preview enrollment, customization/fallback confirmation, five advances, and expiry. Verify configured fallbacks apply to future alarms and controls never interrupt an in-flight alarm. Reset and repeat; verify expired Pro configurations are not resurrected. Then run the complete [Play acceptance checklist](play-billing-setup.md#internal-testing-acceptance-checklist), including pending payments, restoration, cancellation, and offline behavior. A local advance is a testing shortcut, not measured retention or a genuine successful-alarm day.

## Historical phone sequence

On 2026-10-31 the tester reported that real alarms and Mountain recognition worked on their phone. The cause of the earlier recognition failure remains unknown. This retest did not cover Play purchases.

1. Install the signed internal-track release using a license tester. Finish or skip onboarding, then open the Home gear → Settings → **Monetization testing**. Check enrollment and billing diagnostics. If this account owns lifetime or a subscription, enable **Test as non-paying user**. Confirm the test-mode notice; real Play ownership should still appear in billing diagnostics.
2. After enabling non-paying mode, tap **Start / reset local preview**, read the metadata warning, and confirm. Expect PREVIEW, 0 completed, 5 remaining. Enabling the mode alone does not reset an exhausted or retired preview. Create/edit a Pro routine and explicitly confirm its free fallback; note both summaries in Settings. Re-edit existing paid routines to establish a confirmed fallback rather than assuming old paid settings will convert.
3. Leave the expiry switch off. Check the Home subtitle starts at five alarm days in green. Advance once per tap and close Settings after each advance. Expect four in golden yellow, three in orange, two in red and one in dark red. Check that streak and history retain their previous values.
4. Advance the fifth day, then close Settings. Future alarms must use the confirmed fallback. Once Play catalog/ownership checks settle with prices available and Home is safe, the automatic expiry paywall appears. In non-paying test mode an owner can preview these offers, but checkout stays disabled. Continue free, restart, and confirm the converted routine remains.
5. Reset, select **Keep Pro pill only at expiry**, and reconfirm a Pro routine. Repeat five advances. The countdown disappears and the header pill opens the offer on demand. Restart and verify the same Home layout.
6. During a real ringing/snoozed alarm or active routine, and during checkout/pending payment/verification, confirm test changes are unavailable. Synthetic controls deliberately cannot expire a preview while an occurrence is active; existing real-completion regression tests cover snapshot preservation.
7. Turn **Test as non-paying user** off before purchase acceptance: checkout is intentionally unavailable while it is on. Confirm real lifetime/subscription access returns and alarm edits remain. For a new transaction, use a nonpaid license tester or separately refund/revoke an earlier test purchase; hiding ownership locally does not make an existing product purchasable again. Test success, cancel, decline/pending, restoration and post-expiry routine restoration without overwriting later edits. Do not reset between expiry and a restoration purchase: reset discards the old original backup. Actual transactions and offline cold restart remain acceptance work.

## Local validation — 2026-10-29

These are historical results from before the non-paying test mode was added. They do not validate that later change; its current validation must be recorded separately.

- Internal configuration: 107 focused tests passed, zero failures/errors; one normal-build-only gate test intentionally skipped. Debug/release Kotlin compilation and minified release AAB assembly passed.
- Normal configuration (no internal property): 102 focused tests passed, zero failures/errors; six internal-only tests intentionally skipped. Debug/release Kotlin compilation and minified release AAB assembly passed. Across both configurations every authored test ran.
- Suites: `ProTrialTest`, `ProTrialStoreTest`, `AlarmStoreTrialTest`, `AlarmOccurrenceStoreTest`, `WakeCompletionRecoveryTest`, `BillingUiStateTest`, `PurchaseVerifierTest`, `PaidEntitlementCacheTest`, `PlayBillingManagerTest`, `RoutineViewModelTest`, `AlarmNotificationTest`, `AlarmSchedulerTest`.
- Generated internal release BuildConfig has `DEBUG=false`, `PRO_TRIAL_INTERNAL_TESTING=true`, `PRO_TRIAL_ROLLOUT_ENABLED=true`, and the unchanged application ID. Both trial gates are false in normal debug/release builds. Analytics remains disabled.
- Inspected AAB DEX payloads: the internal release contains the menu/reset controls; the normal release contains neither those strings nor the internal expiry preference key after R8. The disabled-build test rejects reset/advance/presentation changes and ignores a stale saved banner setting.
- The internal unsigned AAB was preserved at `app/build/outputs/bundle/internalTesting/yoga-alarm-internal-testing-unsigned.aab`. The standard release output now contains the normal build from the final check. Generated test summaries and BuildConfig snapshots are in `app/build/reports/monetization-internal-testing/`.
- Both local AABs are unsigned. No release signing configuration or signing-related property keys were found in the inspected project/local properties; no credentials were printed. No upload, publication, Play Console inspection, or real purchase was performed.
- `adb devices -l` reported no connected device. Phone layout/lifecycle, actual checkout, pending-payment methods, purchase restoration, and trusted offline paid recovery are not device-verified. This historical result predates the client-only release policy above; physical-device paid offline acceptance remains outstanding.
- `git diff --check` passed. No iOS files were changed.

## Paid-owner testing fix — 2026-10-30

- Added the internal-only **Test as non-paying user** switch. No Play Billing ownership, verifier, acknowledgement, or purchase-cache implementation changed. Checkout is disabled by both the test paywall and its purchase callback; turning the mode off returns to normal Play-based access without automatically restoring alarm edits.
- Six focused suites ran in each configuration: `ProTrialTest`, `ProTrialStoreTest`, `AlarmStoreTrialTest`, `AlarmOccurrenceStoreTest`, `BillingUiStateTest`, and `PlayBillingManagerTest`. Internal mode: **44 passed**, two normal-only tests skipped. Normal mode: **37 passed**, nine internal-only tests skipped. Zero failures/errors; all four newly added regressions ran in their applicable configuration.
- Regressions cover a paid owner resetting and completing the preview through synthetic and real completion entry points, fallback conversion, paid reconciliation not retiring a simulated preview, restoration protection, returning to paid access, fallback confirmation even with actual ownership, active-occurrence guards, persistence, and normal builds ignoring the saved override.
- Debug/release Kotlin compilation and minified release AAB assembly passed for both configurations. The internal release has `DEBUG=false` and its controls enabled. The normal release has its controls disabled; its DEX contains neither the menu/switch text nor the new preference key.
- Updated unsigned internal output: `app/build/outputs/bundle/internalTesting/yoga-alarm-paid-owner-testing-unsigned.aab`. Evidence: `app/build/reports/monetization-paid-owner-testing/`. The standard `app/build/outputs/bundle/release/app-release.aab` contains the final normal-build check. The pre-existing signed `app/release/app-release.aab` was left untouched. Rebuild/sign with an unused version code before uploading this fix.
- `git diff --check` passed. No upload, Console change, phone UI validation, or actual Play transaction was performed. This historical run predates the client-only release policy above and did not validate offline recovery. This is Android-only testing infrastructure; no iOS sync entry was added.

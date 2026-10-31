# Monetization internal testing

This is a local preview and Play Billing acceptance runbook, not production approval. The current client purchase verifier trusts local Play records; trusted backend verification, expiry/revocation handling, recoverable offline paid access, and device acceptance remain production blockers. Do not enable `monetizationBillingReady` merely to run these tests.

## Build and signing

For this test cycle, **release includes the monetization testing controls by default**. At the user's request, `gradle.properties` now contains `proTrialInternalTesting=true`, so Android Studio's standard signing workflow picks it up. The separate `internalTesting` variant was removed. Google Play track membership itself does not enable app build flags.

In Android Studio:

1. Sync the project with Gradle after pulling this change.
2. Set a version code higher than the last uploaded version.
3. Choose **Build > Generate Signed Bundle / APK > Android App Bundle**, then your existing upload key.
4. On the build-variant selection step, select **release**. No extra command-line flag is needed.
5. Upload the newly generated signed AAB from the wizard's chosen destination. An old `app/release/app-release.aab` will not gain the controls merely because it is uploaded to the Internal testing track.

Command-line equivalent (unsigned until signed separately):

```bash
./gradlew :app:bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`. Release optimization, `DEBUG=false`, and the existing application ID are unchanged. Debug builds also honor the saved testing flag.

Verified on 2026-10-30: `:app:bundleRelease` with no extra flag succeeded, and its packaged DEX contains both **Monetization testing** and **Test as non-paying user**. Explicit `-PproTrialInternalTesting=false` generated both trial flags as false. App logic was unchanged; unit tests were not rerun for this configuration-only adjustment. The existing signed AAB was left untouched; generate a new signed release before uploading.

Use a Java 17+ runtime. On this workstation the verified bundled runtime is `/opt/android-studio-for-platform/jbr`; prefix a command with `JAVA_HOME=/opt/android-studio-for-platform/jbr` if Java is not on `PATH`.

Before public production distribution, set `proTrialInternalTesting=false` in `gradle.properties`, then rebuild. For a single command-line production build, `./gradlew :app:bundleRelease -PproTrialInternalTesting=false` overrides the saved project property. Production builds with the flag false ignore saved test-mode preferences. Public preview rollout still has its separate analytics, privacy, and billing-readiness gates.

This repository has no release signing configuration. Command-line bundle tasks alone do not produce upload-ready signed artifacts. Use the Android Studio **release** selection above for signing. Keep keystore paths and passwords outside tracked files and command history. Do not substitute a debug key or create a replacement key for this existing Play app. See [Android app signing](https://developer.android.com/studio/publish/app-signing).

Alternatively, sign the freshly built internal AAB locally with the JDK's `jarsigner`, replacing the paths and alias below with the existing upload key. Omit password arguments so it prompts securely:

```bash
jarsigner -keystore /path/to/existing-upload-key.jks \
  -signedjar /path/to/yoga-alarm-internal-signed.aab \
  app/build/outputs/bundle/release/app-release.aab UPLOAD_KEY_ALIAS
jarsigner -verify /path/to/yoga-alarm-internal-signed.aab
```

A successful signature check alone does not establish that the certificate matches the key registered in Play Console. No Console setting or registered certificate was checked during this implementation.

Use a version code higher than every previously uploaded artifact (currently the repository declares 32; the Console maximum has not been verified). Confirm the final artifact's package, version code, upload signature, and both BuildConfig gates before uploading. Never promote this controls-enabled AAB to production; rebuild with the testing property explicitly false. This repository change does not upload or publish anything.

## Play Console setup

The 2026-10-30 missing-menu investigation found that the local signed `app/release/app-release.aab` contained neither the menu nor the non-paying switch. A Play internal-track install cannot restore code removed from that artifact. The initial dedicated-variant solution passed builds and 20 enabled/13 disabled trial-store tests, but was replaced by the project-level flag above because the user wanted the existing release signing workflow.

Internal track access and license testing are separate settings:

1. Add the Google account to the app's Internal testing tester list, distribute the opt-in link, and opt in with that account. Install the release through Play for track acceptance.
2. Separately add the same account under Play Console **Settings > License testing**. Track membership alone does not enable test payment methods and can incur real charges.
3. Confirm monthly/yearly base plans and the lifetime purchase option from [the billing specification](play-billing-setup.md) are active and available in the tester's country.
4. Open checkout and verify it identifies a test purchase/payment method before completing payment. Use separate accounts or refund/revoke earlier test purchases between ownership scenarios.

License testers can also sideload matching-package debug builds for development. That does not replace acceptance of the signed Play-installed release. See [Google's billing testing guide](https://developer.android.com/google/play/billing/test) and [license tester setup](https://support.google.com/googleplay/android-developer/answer/6062777).

## Local preview controls

Settings exposes internal preview controls only in explicitly opted-in builds. They never fabricate a Play purchase, bypass checkout, or acknowledge a token. Real paid ownership still comes from the billing manager.

- **Test as non-paying user** lets an existing lifetime/subscription owner exercise the free/preview flow without refunding the purchase or changing Play accounts. It defaults off and persists in testing-enabled builds; builds with the testing flag false ignore a stale saved value. While on, preview UI, editing access, and real/synthetic preview-day accounting ignore paid access locally, and checkout is disabled. Actual Play entitlement, token verification, and ownership remain unchanged. This does not simulate a paid purchase or trusted offline recovery.
- Reset deterministically starts a fresh preview at zero of five days. It preserves current routines, sounds, schedules, alarms, confirmed fallback/customization metadata, purchases, occurrence history, and wake progress. The old trial-original restoration snapshot and the previous restoration-offer dismissal marker are cleared, allowing a later real purchase to offer restoration again. It never restores expired Pro configurations; to test another fallback conversion after expiry, re-edit through the normal editor and review the fallback again.
- Advance day exercises the same preview accounting and fifth-day fallback conversion as a real completion. It uses today or the day after the latest counted date, whichever is later, without changing the device clock or creating a real alarm occurrence/wake-progress record. Each press advances at most one day; an exhausted preview cannot advance further.
- Reset/advance refuse changes while paid ownership is detected unless **Test as non-paying user** is on. Checkout, verification, pending payment, and an active occurrence/routine still block changes, including switching test mode. UI and storage gates both enforce the restrictions; a failed write must not report success.
- Turning non-paying mode off restores access from real Play ownership without overwriting alarm edits. The existing restoration dialog may offer an explicit restoration where fallback configurations remain unchanged. Existing paid customizations without a confirmed trial fallback are not automatically converted: re-edit and confirm a free fallback to test expiry safely.
- The internal expiry-action switch is persisted only for testing; builds with the testing flag false ignore any stale saved choice. The default expiry behavior opens the safe automatic paywall; the optional Home banner uses a Keep Pro action. The preview countdown remains visible in either mode.

First test free access, preview enrollment, customization/fallback confirmation, five advances, and expiry. Verify configured fallbacks apply to future alarms and controls never interrupt an in-flight alarm. Reset and repeat; verify expired Pro configurations are not resurrected. Then run the complete [Play acceptance checklist](play-billing-setup.md#internal-testing-acceptance-checklist), including pending payments, restoration, cancellation, and offline behavior. A local advance is a testing shortcut, not measured retention or a genuine successful-alarm day.

## Short phone sequence

1. Install the signed internal-track release using a license tester. Finish or skip onboarding, then open the Home gear → Settings → **Monetization testing**. Check enrollment and billing diagnostics. If this account owns lifetime or a subscription, enable **Test as non-paying user**. Confirm the test-mode notice; real Play ownership should still appear in billing diagnostics.
2. After enabling non-paying mode, tap **Start / reset local preview**, read the metadata warning, and confirm. Expect PREVIEW, 0 completed, 5 remaining. Enabling the mode alone does not reset an exhausted or retired preview. Create/edit a Pro routine and explicitly confirm its free fallback; note both summaries in Settings. Re-edit existing paid routines to establish a confirmed fallback rather than assuming old paid settings will convert.
3. Leave the expiry switch off. Advance once per tap and close Settings after each advance: 4 remaining after day one, the unchanged optional invitation after day two, and the final-day notice after day four. Streak/history must remain unchanged.
4. Advance the fifth day, then close Settings. Future alarms must use the confirmed fallback. Once Play catalog/ownership checks settle with prices available and Home is safe, the automatic expiry paywall appears. In non-paying test mode an owner can preview these offers, but checkout stays disabled. Continue free, restart, and confirm the converted routine remains.
5. Reset, select **Expiry banner with optional upgrade**, and re-edit/reconfirm a Pro routine. Repeat five advances. Home shows expiry status and an optional **Keep Pro** action; no automatic paywall. Restart to check persistence. Switching modes after milestone five was already presented requires reset/repeat for a fair comparison.
6. During a real ringing/snoozed alarm or active routine, and during checkout/pending payment/verification, confirm test changes are unavailable. Synthetic controls deliberately cannot expire a preview while an occurrence is active; existing real-completion regression tests cover snapshot preservation.
7. Turn **Test as non-paying user** off before purchase acceptance: checkout is intentionally unavailable while it is on. Confirm real lifetime/subscription access returns and alarm edits remain. For a new transaction, use a nonpaid license tester or separately refund/revoke an earlier test purchase; hiding ownership locally does not make an existing product purchasable again. Test success, cancel, decline/pending, restoration and post-expiry routine restoration without overwriting later edits. Do not reset between expiry and a restoration purchase: reset discards the old original backup. Actual transactions and offline cold restart remain acceptance work.

The countdown and production day-two/prompt rules are unchanged. The banner selector and deterministic test controls are Android-only infrastructure; no new iOS sync entry is required.

## Local validation — 2026-10-29

These are historical results from before the non-paying test mode was added. They do not validate that later change; its current validation must be recorded separately.

- Internal configuration: 107 focused tests passed, zero failures/errors; one normal-build-only gate test intentionally skipped. Debug/release Kotlin compilation and minified release AAB assembly passed.
- Normal configuration (no internal property): 102 focused tests passed, zero failures/errors; six internal-only tests intentionally skipped. Debug/release Kotlin compilation and minified release AAB assembly passed. Across both configurations every authored test ran.
- Suites: `ProTrialTest`, `ProTrialStoreTest`, `AlarmStoreTrialTest`, `AlarmOccurrenceStoreTest`, `WakeCompletionRecoveryTest`, `BillingUiStateTest`, `PurchaseVerifierTest`, `PaidEntitlementCacheTest`, `PlayBillingManagerTest`, `RoutineViewModelTest`, `AlarmNotificationTest`, `AlarmSchedulerTest`.
- Generated internal release BuildConfig has `DEBUG=false`, `PRO_TRIAL_INTERNAL_TESTING=true`, `PRO_TRIAL_ROLLOUT_ENABLED=true`, and the unchanged application ID. Both trial gates are false in normal debug/release builds. Analytics remains disabled.
- Inspected AAB DEX payloads: the internal release contains the menu/reset controls; the normal release contains neither those strings nor the internal expiry preference key after R8. The disabled-build test rejects reset/advance/presentation changes and ignores a stale saved banner setting.
- The internal unsigned AAB was preserved at `app/build/outputs/bundle/internalTesting/yoga-alarm-internal-testing-unsigned.aab`. The standard release output now contains the normal build from the final check. Generated test summaries and BuildConfig snapshots are in `app/build/reports/monetization-internal-testing/`.
- Both local AABs are unsigned. No release signing configuration or signing-related property keys were found in the inspected project/local properties; no credentials were printed. No upload, publication, Play Console inspection, or real purchase was performed.
- `adb devices -l` reported no connected device. Phone layout/lifecycle, actual checkout, pending-payment methods, purchase restoration, and trusted offline paid recovery are not device-verified. `ClientPurchaseVerifier` remains the explicit production blocker described above.
- `git diff --check` passed. No iOS files were changed.

## Paid-owner testing fix — 2026-10-30

- Added the internal-only **Test as non-paying user** switch. No Play Billing ownership, verifier, acknowledgement, or purchase-cache implementation changed. Checkout is disabled by both the test paywall and its purchase callback; turning the mode off returns to normal Play-based access without automatically restoring alarm edits.
- Six focused suites ran in each configuration: `ProTrialTest`, `ProTrialStoreTest`, `AlarmStoreTrialTest`, `AlarmOccurrenceStoreTest`, `BillingUiStateTest`, and `PlayBillingManagerTest`. Internal mode: **44 passed**, two normal-only tests skipped. Normal mode: **37 passed**, nine internal-only tests skipped. Zero failures/errors; all four newly added regressions ran in their applicable configuration.
- Regressions cover a paid owner resetting and completing the preview through synthetic and real completion entry points, fallback conversion, paid reconciliation not retiring a simulated preview, restoration protection, returning to paid access, fallback confirmation even with actual ownership, active-occurrence guards, persistence, and normal builds ignoring the saved override.
- Debug/release Kotlin compilation and minified release AAB assembly passed for both configurations. The internal release has `DEBUG=false` and its controls enabled. The normal release has its controls disabled; its DEX contains neither the menu/switch text nor the new preference key.
- Updated unsigned internal output: `app/build/outputs/bundle/internalTesting/yoga-alarm-paid-owner-testing-unsigned.aab`. Evidence: `app/build/reports/monetization-paid-owner-testing/`. The standard `app/build/outputs/bundle/release/app-release.aab` contains the final normal-build check. The pre-existing signed `app/release/app-release.aab` was left untouched. Rebuild/sign with an unused version code before uploading this fix.
- `git diff --check` passed. No upload, Console change, phone UI validation, or actual Play transaction was performed. The trusted offline paid-recovery blocker remains unchanged. This is Android-only testing infrastructure; no iOS sync entry was added.

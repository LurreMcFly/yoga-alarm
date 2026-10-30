# Monetization internal testing

This is a local preview and Play Billing acceptance runbook, not production approval. The current client purchase verifier trusts local Play records; trusted backend verification, expiry/revocation handling, recoverable offline paid access, and device acceptance remain production blockers. Do not enable `monetizationBillingReady` merely to run these tests.

## Build and signing

Explicitly opt in for either build type:

```bash
./gradlew :app:assembleDebug -PproTrialInternalTesting=true
./gradlew :app:bundleRelease -PproTrialInternalTesting=true
```

Use a Java 17+ runtime. On this workstation the verified bundled runtime is `/opt/android-studio-for-platform/jbr`; prefix a command with `JAVA_HOME=/opt/android-studio-for-platform/jbr` if Java is not on `PATH`.

Both enable `BuildConfig.PRO_TRIAL_INTERNAL_TESTING` and preview assignment. The package remains `com.lurremcfly.yogaalarm`, so existing Play products can resolve. Ordinary debug and release builds leave testing controls disabled; public preview rollout retains its separate analytics, privacy, and billing-readiness gates. Do not save the internal property in project or user Gradle defaults.

The release bundle is `app/build/outputs/bundle/release/app-release.aab`. This repository has no release signing configuration, and local Gradle/SDK properties do not provide one. The command alone does not produce an upload-ready signed artifact. Use Android Studio **Build > Generate Signed Bundle / APK > Android App Bundle**, selecting the existing registered upload key and the release variant, with `proTrialInternalTesting=true` configured for that invocation. Keep keystore paths and passwords outside tracked files and command history. Do not substitute a debug key or create a replacement key for this existing Play app. See [Android app signing](https://developer.android.com/studio/publish/app-signing).

Alternatively, sign the freshly built internal AAB locally with the JDK's `jarsigner`, replacing the paths and alias below with the existing upload key. Omit password arguments so it prompts securely:

```bash
jarsigner -keystore /path/to/existing-upload-key.jks \
  -signedjar /path/to/yoga-alarm-internal-signed.aab \
  app/build/outputs/bundle/release/app-release.aab UPLOAD_KEY_ALIAS
jarsigner -verify /path/to/yoga-alarm-internal-signed.aab
```

A successful signature check alone does not establish that the certificate matches the key registered in Play Console. No Console setting or registered certificate was checked during this implementation.

Use a version code higher than every previously uploaded artifact (currently the repository declares 28; the Console maximum has not been verified). Confirm the final artifact's package, version code, upload signature, and both BuildConfig gates before uploading. Never promote this controls-enabled AAB to production; rebuild without the internal property. This repository change does not upload or publish anything.

## Play Console setup

Internal track access and license testing are separate settings:

1. Add the Google account to the app's Internal testing tester list, distribute the opt-in link, and opt in with that account. Install the release through Play for track acceptance.
2. Separately add the same account under Play Console **Settings > License testing**. Track membership alone does not enable test payment methods and can incur real charges.
3. Confirm monthly/yearly base plans and the lifetime purchase option from [the billing specification](play-billing-setup.md) are active and available in the tester's country.
4. Open checkout and verify it identifies a test purchase/payment method before completing payment. Use separate accounts or refund/revoke earlier test purchases between ownership scenarios.

License testers can also sideload matching-package debug builds for development. That does not replace acceptance of the signed Play-installed release. See [Google's billing testing guide](https://developer.android.com/google/play/billing/test) and [license tester setup](https://support.google.com/googleplay/android-developer/answer/6062777).

## Local preview controls

Settings exposes internal preview controls only in explicitly opted-in builds. They never fabricate a Play purchase, bypass checkout, or acknowledge a token. Real paid ownership still comes from the billing manager.

- Reset deterministically starts a fresh preview at zero of five days. It preserves current routines, sounds, schedules, alarms, confirmed fallback/customization metadata, purchases, occurrence history, and wake progress. The old trial-original restoration snapshot and the previous restoration-offer dismissal marker are cleared, allowing a later real purchase to offer restoration again. It never restores expired Pro configurations; to test another fallback conversion after expiry, re-edit through the normal editor and review the fallback again.
- Advance day exercises the same preview accounting and fifth-day fallback conversion as a real completion. It uses today or the day after the latest counted date, whichever is later, without changing the device clock or creating a real alarm occurrence/wake-progress record. Each press advances at most one day; an exhausted preview cannot advance further.
- Controls refuse changes while paid ownership, checkout, verification, pending payment, or an active occurrence/routine is present. Use a nonpaid tester for preview scenarios. UI and storage gates both enforce the restrictions; a failed write must not report success.
- The internal expiry-action switch is persisted only for testing; ordinary builds ignore any stale saved choice. The default expiry behavior opens the safe automatic paywall; the optional Home banner uses a Keep Pro action. The preview countdown remains visible in either mode.

First test free access, preview enrollment, customization/fallback confirmation, five advances, and expiry. Verify configured fallbacks apply to future alarms and controls never interrupt an in-flight alarm. Reset and repeat; verify expired Pro configurations are not resurrected. Then run the complete [Play acceptance checklist](play-billing-setup.md#internal-testing-acceptance-checklist), including pending payments, restoration, cancellation, and offline behavior. A local advance is a testing shortcut, not measured retention or a genuine successful-alarm day.

## Short phone sequence

1. Install the signed internal-track release with a nonpaid license tester. Finish or skip onboarding, then open the Home gear → Settings → **Monetization testing**. Check enrollment and billing diagnostics.
2. Tap **Start / reset local preview**, read the metadata warning, and confirm. Expect PREVIEW, 0 completed, 5 remaining. Create/edit a Pro routine and explicitly confirm its free fallback; note both summaries in Settings.
3. Leave the expiry switch off. Advance once per tap and close Settings after each advance: 4 remaining after day one, the unchanged optional invitation after day two, and the final-day notice after day four. Streak/history must remain unchanged.
4. Advance the fifth day, then close Settings. Future alarms must use the confirmed fallback. Once Play offers are buyable and Home is safe, the automatic expiry paywall appears. Continue free, restart, and confirm the converted routine remains.
5. Reset, select **Expiry banner with optional upgrade**, and re-edit/reconfirm a Pro routine. Repeat five advances. Home shows expiry status and an optional **Keep Pro** action; no automatic paywall. Restart to check persistence. Switching modes after milestone five was already presented requires reset/repeat for a fair comparison.
6. During a real ringing/snoozed alarm or active routine, and during checkout/pending payment/verification, confirm test changes are unavailable. Synthetic controls deliberately cannot expire a preview while an occurrence is active; existing real-completion regression tests cover snapshot preservation.
7. Test purchases separately with Play test payment methods: success, cancel, decline/pending, restoration and post-expiry routine restoration without overwriting later edits. Do not reset between expiry and a restoration purchase: reset discards the old original backup. Actual transactions and offline cold restart remain acceptance work.

The countdown and production day-two/prompt rules are unchanged. The banner selector and deterministic test controls are Android-only infrastructure; no new iOS sync entry is required.

## Local validation — 2026-10-29

- Internal configuration: 107 focused tests passed, zero failures/errors; one normal-build-only gate test intentionally skipped. Debug/release Kotlin compilation and minified release AAB assembly passed.
- Normal configuration (no internal property): 102 focused tests passed, zero failures/errors; six internal-only tests intentionally skipped. Debug/release Kotlin compilation and minified release AAB assembly passed. Across both configurations every authored test ran.
- Suites: `ProTrialTest`, `ProTrialStoreTest`, `AlarmStoreTrialTest`, `AlarmOccurrenceStoreTest`, `WakeCompletionRecoveryTest`, `BillingUiStateTest`, `PurchaseVerifierTest`, `PaidEntitlementCacheTest`, `PlayBillingManagerTest`, `RoutineViewModelTest`, `AlarmNotificationTest`, `AlarmSchedulerTest`.
- Generated internal release BuildConfig has `DEBUG=false`, `PRO_TRIAL_INTERNAL_TESTING=true`, `PRO_TRIAL_ROLLOUT_ENABLED=true`, and the unchanged application ID. Both trial gates are false in normal debug/release builds. Analytics remains disabled.
- Inspected AAB DEX payloads: the internal release contains the menu/reset controls; the normal release contains neither those strings nor the internal expiry preference key after R8. The disabled-build test rejects reset/advance/presentation changes and ignores a stale saved banner setting.
- The internal unsigned AAB was preserved at `app/build/outputs/bundle/internalTesting/yoga-alarm-internal-testing-unsigned.aab`. The standard release output now contains the normal build from the final check. Generated test summaries and BuildConfig snapshots are in `app/build/reports/monetization-internal-testing/`.
- Both local AABs are unsigned. No release signing configuration or signing-related property keys were found in the inspected project/local properties; no credentials were printed. No upload, publication, Play Console inspection, or real purchase was performed.
- `adb devices -l` reported no connected device. Phone layout/lifecycle, actual checkout, pending-payment methods, purchase restoration, and trusted offline paid recovery are not device-verified. `ClientPurchaseVerifier` remains the explicit production blocker described above.
- `git diff --check` passed. No iOS files were changed.

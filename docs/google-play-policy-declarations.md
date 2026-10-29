# Google Play policy declarations

Prepared for package `com.lurremcfly.yogaalarm`; repository review updated 2026-10-28. These draft answers describe the default analytics-disabled build. They do not update any published policy or Play Console declaration. Reassess for each release configuration.

## Data Safety

- Proposed answer for the default SDK-disabled build: **No app-operated collection or sharing**; confirm the final artifact and all applicable Play data types before submission. This answer must not be reused for an analytics-enabled build.
- Camera frames are processed ephemerally on-device by MediaPipe. They are not recorded, retained, or transmitted.
- Alarm configuration, routine choices, permission-readiness flags, wake progress, preview assignment/dates, confirmed fallbacks, and occurrence snapshots/outcomes are stored in app-private local preferences. Backup remains disabled; reinstall/clear-data can reset the local preview. Debug activation does not grant paid access.
- The default build has no account system, app backend, ads, analytics SDK, or crash-reporting SDK. An optional Firebase Analytics build path is authored but excluded unless explicitly enabled.
- Google Play handles purchase transactions. The app reads purchase and entitlement status through Play Billing and never receives payment-card information.
- Data deletion: users can delete individual alarms, clear app storage, or uninstall the app. There is no Yoga Alarm account to delete.
- Planned canonical privacy policy URL: `https://yogaalarm.com/privacy`

Do not enter the planned URL in Play Console until it is publicly reachable over HTTPS,
contains the final support email and developer identity, and has been checked on mobile.
Until then, retain the currently working hosted policy URL.

Confirm the default merged release manifest removes `INTERNET` and `ACCESS_NETWORK_STATE`, excludes Firebase Analytics, and retains `allowBackup="false"` before submitting these default-build answers.

Before enabling `-PmonetizationAnalyticsEnabled=true`, provide the real Firebase project configuration and pass `-PmonetizationPrivacyApproved=true` only after review. The enabled sink explicitly enables collection; audit Firebase automatic events/identifiers as well as the coarse custom funnel events, determine consent and retention settings, update the actual published policy and Play Data Safety answers, inspect the enabled release manifest, and verify delivery on device. Assignment identifiers are pseudonymous, not anonymous. Custom events exclude camera frames/landmarks, pose sequences, alarm names/times, raw purchase tokens, and monetary events; automatic SDK behavior still needs its own audit. Repository drafts do not establish that any of this external work is complete.

## Health apps declaration

- Health features: **Yes**.
- Select the closest **Activity and fitness / exercise or movement** category offered by the current form.
- The app guides users through short yoga or mobility poses and validates approximate pose shape.
- The app does not collect, store, or transmit health data and does not integrate with Health Connect.
- The app is not a medical device and makes no diagnostic, treatment, cure, or prevention claims.

Store-listing disclaimer:

> Yoga Alarm is a movement alarm, not a medical device. Move within your own ability and stop if you feel pain, dizziness, or discomfort.

## Exact alarm permission

- Permission: `USE_EXACT_ALARM`.
- Core purpose: Yoga Alarm is an alarm clock. Its user-requested wake alarms must ring at the exact selected time.
- If Play presents an exact-alarm declaration, select the alarm-clock/core-alarm use case.

## Full-screen intent

- Permission: `USE_FULL_SCREEN_INTENT`.
- Core purpose: show an actively ringing alarm over the lock screen so the user can snooze or begin the required movement routine.
- This is used only for user-created alarms, not marketing or general notifications.

## Other App content answers

- Ads: **No**.
- App access: no login is required. Give reviewers instructions for testing Pro with Play license-test products once those products are active.
- Target audience recommendation: adults / 18 and over. Do not select children unless the product and policy work is deliberately expanded for them.
- Content rating: complete the questionnaire truthfully; the current app contains no violence, sexual content, gambling, or user-generated content.

## Billing and subscriptions

The Android client uses the production catalog below and client acknowledgement. Its current `ClientPurchaseVerifier` checks local Play records, not signatures or backend evidence. A recoverable verifier/cache contract is authored, but the actual client verifier cannot restore trusted paid access on an offline cold start. Backend verification, expiry/refund reconciliation, and device acceptance remain release dependencies; see the [billing specification](play-billing-setup.md).

The paywall lists lifetime, yearly, monthly, with localized Play prices. The app-managed five-successful-alarm-day preview is authored behind a default-off public rollout gate; it is not a store subscription trial, requires no card or automatic charge, and does not change the permanent free tier. Enabling the public experiment also requires measurement configuration and trusted billing acceptance. Free routines have exactly three 20-second slots selected from four free poses (Mountain, Warrior II, Tree, Squat); Pro unlocks all nine current poses and ten sounds.

- Products: monthly, yearly, and lifetime Pro.
- Show Play-localized price, billing period, auto-renewal behavior, and restore/manage-purchase actions before purchase.
- Monthly and yearly subscriptions renew until canceled through Google Play. Lifetime is a one-time purchase.
- Subscription product: `yoga_alarm_pro`, with base plans `monthly` and `yearly`.
- One-time product: `yoga_alarm_pro_lifetime`, with permanent purchase-option ID `yogaalarmprolifetime` and purchase type **Buy**.

## Public website and support

- Canonical domain: `https://yogaalarm.com`
- Privacy policy: `https://yogaalarm.com/privacy`
- Terms: `https://yogaalarm.com/terms`
- Support: `https://yogaalarm.com/support`
- Download/store redirect: `https://yogaalarm.com/download`
- Publish a real monitored support email on the Support and Privacy pages and use the same
  address in Play Console.
- Pages must be accessible without login, render well on mobile, and remain stable during
  review.
- Re-check the public policy against the release build and the final Play Data Safety answers
  before each production submission.

## Asset evidence

Keep a private release evidence folder containing:

- Suno subscription/payment proof and the terms that applied when each alarm track was generated.
- Generation/export dates and original filenames for all alarm tracks.
- The source prompts or generation records for pose illustrations and the app icon.
- A copy of the relevant OpenAI terms in effect when those images were generated.

Do not publish receipts or personal account details inside the repository.

# Yoga Alarm billing specification

The Android release uses Play checkout, a local preview, and client-side last-known paid access. A backend verifier is future hardening, not a prerequisite for this release policy. Play-installed purchase and offline/lifecycle acceptance remain required. Firebase configuration is needed only if analytics is enabled.

This document is the source of truth for the current release billing policy.
Client-only limitations and optional future backend hardening are described below.
Changing product IDs or entitlement rules after the Play products are activated requires
an explicit product decision.

## Product principles

- The paywall retains its three choices, ordered lifetime, yearly, monthly. After the initial catalog attempt settles, default to the first available choice in that order; preserve explicit selection during refresh.
- Yoga Alarm does not require a customer account, login, name, or email.
- Google Play owns payment identity, checkout, renewals, cancellations, and restoration.
- Every paid plan grants the same `Pro` entitlement.
- Prices shown in production come from Google Play in the customer's local currency.
- Camera frames, pose results, alarm schedules, and health data never enter the billing backend.

## Permanent Google Play catalog

Package name: `com.lurremcfly.yogaalarm`

| Paywall choice | Play object | Permanent ID | Billing period | Launch price |
|---|---|---|---|---|
| Monthly | Subscription | `yoga_alarm_pro` | Base plan `monthly` | USD 7.99/month |
| Yearly | Subscription | `yoga_alarm_pro` | Base plan `yearly` | USD 19.99/year |
| Lifetime | One-time product, non-consumable | `yoga_alarm_pro_lifetime` | None | USD 29.99 once |

The lifetime product uses a Buy purchase option with permanent purchase-option ID
`yogaalarmprolifetime` (confirmed in Play Console on 2026-09-27). Buy is the purchase
type, not the option ID. It is not consumable, rentable, quantity-enabled, or time-limited.

Monthly and yearly are base plans of one subscription because they provide the same
benefits. Lifetime is a separate non-consumable product. Play Console controls regional
availability and localized prices.

The app queries one subscription product and selects its base-plan offers by
`basePlanId`, with no promotional `offerId`. Lifetime selects the `yogaalarmprolifetime` purchase
option with no promotional offer. Missing choices remain unavailable instead of
substituting a different offer or hardcoded price.

## Free and Pro access

Free remains useful indefinitely:

- Unlimited alarms.
- Exactly three pose slots using Mountain, Warrior II, Tree, or Squat.
- A fixed 20-second hold per pose.
- Sunbird Morning Call and Morning Temple Call.
- Core alarm, snooze, vibration, camera validation, and routine completion.

Every Pro purchase unlocks:

- All nine current poses and future Pro poses.
- One to ten poses per routine.
- Hold durations of 5, 10, 15, 20, 30, 45, or 60 seconds.
- All ten current alarm sounds and future Pro sounds.
- The same Pro feature set regardless of monthly, yearly, or lifetime payment.

No Google Play free trial or introductory offer is configured. A local five-successful-alarm-day Pro preview is authored behind a disabled rollout gate. Only the first genuine yoga alarm completion on each local date counts; practice, tests, snoozes, and stops do not. Trial customization requires an accepted free fallback before saving. After the fifth qualifying day, future configurations switch to that fallback; already ringing/snoozed occurrences retain their snapshots. Paid ownership remains distinct from feature access.

Testing controls default off (`proTrialInternalTesting=false`). For internal QA, opt in with `-PproTrialInternalTesting=true` when building the standard release, then sign that artifact with the existing upload key; Android Studio wizard instructions are in the runbook. For public signing, rebuild separately with testing false (explicitly `-PproTrialInternalTesting=false` for CLI builds). Public preview enrollment requires `-PproTrialRolloutEnabled=true` and `-PmonetizationBillingReady=true` after acceptance of this client-only policy and device testing. Analytics is optional. See the [internal testing runbook](monetization-internal-testing.md) for signing, tester and reset instructions.

## Purchase and entitlement rules

- Grant Pro only after Play reports `PURCHASED` and the client verifier accepts the known product, package, and nonempty token.
- Never grant Pro for a `PENDING` purchase. The client acknowledges accepted purchases promptly; lifetime is never consumed.
- Lifetime takes precedence if more than one valid plan is present.
- A last-known subscription grants offline Pro for seven days after its last successful online ownership refresh. This is a local access window, not the subscription's paid-through expiry.
- A successful online snapshot of both subscription and in-app ownership reconciles access, including an empty snapshot that clears saved paid access. Cancellation of renewal alone does not remove a still-owned purchase.
- Offline, failed, partial, missing-result, or pending-only queries neither clear saved paid access nor renew its timestamp. Pending never grants Pro. Lifetime remains available offline until a successful online refresh changes ownership.
- At the seven-day boundary, subscription editing access locks and the app asks the user to reconnect; expiry of this local window is not confirmation that they are free. Saved routines, schedules, ordering, and enabled states remain intact; ringing/snoozed occurrences keep their snapshots.
- An online check requires Android's validated-network signal. Play can still return cached records; this is a best-effort freshness check, not proof of a fresh Play server response. Refunds, revocations, subscription expiry, and clock/storage tampering cannot be independently enforced by this client.
- V1 does not implement in-app switching between monthly and yearly. Active subscribers use
  Google's Manage Subscription screen. This can be added later without changing the paywall.
- A customer with active Pro is not offered another Pro purchase in V1.

## Restoration and identity

Yoga Alarm does not maintain customer accounts. The app creates a random anonymous
installation ID containing no personally identifying information.

Google Play restores eligible purchases for the Google account currently active in Play:

- Query purchases when billing connects and when the app returns to the foreground.
- Keep a visible Restore Purchases action.
- Validate discovered purchases locally; do not log purchase tokens.
- A reinstall can create a new installation ID; the verified Play purchase remains the
  authority for restoring Pro.

Without a Yoga Alarm account, purchases do not transfer between Google Play and Apple's
App Store. This is accepted for V1. The future iOS app will use StoreKit and Apple's own
purchase restoration independently.

## Optional future backend data

A future backend should store only what is required to verify access and, later, attribute
creator commissions. No such backend is configured for this release:

- Random installation ID.
- Store and package identifier.
- Purchase token.
- Product and base-plan identifiers.
- Purchase state, purchase time, and entitlement expiry.
- Verification and acknowledgement timestamps.
- Optional creator slug/code associated with the installation.

It does not store customer names, email addresses, Google account data, advertising IDs,
raw IP addresses, device fingerprints, camera data, poses, or alarm configurations.

## Subscription configuration

- Both base plans are auto-renewing.
- Use a seven-day payment grace period.
- Use Google Play's automatically calculated account-hold duration.
- Allow resubscription through Google Play.
- Do not configure a trial or promotional offer for launch.
- Display the renewal period, price, and automatic-renewal statement directly on the paywall.
- Provide Restore Purchases and Manage Subscription actions.

## Debug and release behavior

- All builds now use Google Play checkout. The old debug activation and local entitlement
  store have been removed; existing debug preferences no longer grant access.
- Real purchases are tested through an internal Play track using license testers.
- All displayed prices come from Play. Unavailable prices display “Unavailable” and
  disable checkout for that choice. The fixed yearly savings percentage was removed.


## Measurement configuration and release gates

The default build excludes Firebase Analytics and the Google Services plugin, removes `INTERNET`, and retains `ACCESS_NETWORK_STATE` for purchase-refresh connectivity checks. Local trial/occurrence records do not establish measured revenue or retention. `-PmonetizationAnalyticsEnabled=true` requires a real `app/google-services.json` and `-PmonetizationPrivacyApproved=true`; the enabled sink then turns on Firebase collection. Review automatic SDK collection, consent/retention settings, published privacy text, and Play declarations before enabling it. Inspect the enabled merged release manifest and verify event delivery on a configured device. Authored funnel calls and SDK acceptance are not delivery receipts or authoritative financial reporting; reconcile store transactions separately. No live policy, Firebase project, or Console deployment is changed by these repository files.

## Current client implementation and future hardening

`PlayBillingManager.state.entitlement` represents paid ownership. Editing access may additionally come from an enrolled, unretired local preview with remaining days. Checkout requires resolved nonpaid ownership, a current available offer, no checkout/verification in flight, and no pending Pro payment; it is guarded in the manager and UI.
`PurchaseVerifier` is an injectable suspending boundary that receives package name,
product IDs, purchase state and purchase token. `ClientPurchaseVerifier` currently
trusts Play Billing's local purchase records after checking package, known product,
nonempty token and `PURCHASED` state. It performs **no signature verification or
server verification** and cannot independently validate expiry, refunds or tampering.
Pending records are never verified for access or acknowledged.

Both purchase-type queries must succeed while the device has a validated network before treating ownership as online-resolved. Accepted purchase callbacks may grant provisional session access, then reconcile with a fresh snapshot. Only a successful complete online ownership refresh creates or renews the durable client access record and its seven-day timestamp. Subscriptions are shown as “Subscription”, since the client record does not reliably identify monthly versus yearly. Cancellation of auto-renewal does not itself remove access; Google Play supplies the exact renewal or end date. Settings and the Pro sheet link to subscription management, including lifetime owners who also have a subscription.

The default verifier persists client-only last-known access across process death and offline cold starts: subscriptions for seven days from the last successful online check, lifetime until a successful online ownership refresh changes it. Restore/resume and periodic foreground checks reevaluate freshness and retry ownership. An offline or incomplete query cannot establish nonpaying ownership, clear the cache, or extend its timestamp. Expired offline subscription access disables new Pro editing without converting saved alarms or restarting a retired preview. The existing authenticated `RecoverablePurchaseVerifier` proof path is separate; local evidence must never be described as trusted server verification. Purchase-then-offline/device acceptance remains unexecuted unless recorded separately.

The client acknowledges accepted purchases, retries transient failures with backoff
up to 60 seconds while the manager is alive, and rediscovers unacknowledged purchases
on connection/resume/restore. Lifetime is never consumed. This cannot guarantee
acknowledgement while the app is closed; failures must be resolved before Google's
acknowledgement deadline to avoid automatic refunds.

Optional future backend hardening (not a prerequisite for this client-only release):

- Return verified subscription renewal status and `lineItems.expiryTime` from
  `purchases.subscriptionsv2.get`. Display the localized access end date after
  cancellation, or renewal date while auto-renewing. `Purchase.purchaseTime` is
  the initial signup date and must never be used to estimate renewed expiry.
  The current Settings and Pro sheets show renewal cancellation from
  `Purchase.isAutoRenewing` and link directly to Google Play for the exact date.
- Replace `ClientPurchaseVerifier` with a backend request. Verify tokens through the
  Google Play Developer API, validate package/product, and return trusted entitlement,
  base plan and expiry. Do not treat the client-provided state as proof.
- Make token processing idempotent and handle linked/replaced tokens. No app account
  currently exists; implement the anonymous installation association specified above.
- Move acknowledgement and durable retries to the backend, and remove the client
  acknowledgement responsibility when switching ownership.
- Process real-time developer notifications and revocation/refund events, reconcile
  with Google, and enforce grace period, hold and expiration using trusted state.
- Add a bounded offline cache based on server-verified expiry. Never put Play Developer
  API credentials in the app or log purchase tokens.

References: [Play integration](https://developer.android.com/google/play/billing/integrate),
[verification and acknowledgement security](https://developer.android.com/google/play/billing/security).

## Internal Testing acceptance checklist

Install the updated release from Internal Testing using the opted-in Google account
also listed as a license tester. Verify the checkout displays test payment methods.
Both auto-renewing base plans and the lifetime `yogaalarmprolifetime` option must be active and available
in the tester's country. Use separate tester accounts or refund/revoke existing test
purchases between scenarios: active Pro intentionally disables another purchase.

| Scenario | Expected result |
|---|---|
| Monthly | Local price; checkout for `yoga_alarm_pro` / `monthly`; payment approval unlocks Pro and is acknowledged. |
| Yearly | Local yearly price; checkout for `yoga_alarm_pro` / `yearly`; same entitlement and acknowledgement. |
| Lifetime | Local one-time price; `yoga_alarm_pro_lifetime` / `yogaalarmprolifetime`; permanent ownership, acknowledged and never consumed. |
| Cancel checkout | Returns to free state without an error or stuck checkout button. |
| Declined payment | No Pro; failure message; checkout can be retried. |
| Pending payment | Use a supported slow test payment method (e.g. lifetime); no Pro or acknowledgement while pending. Approval later unlocks and acknowledges, including after restart/resume. Decline never unlocks. Auto-renewing base plans do not expose every pending test method. |
| Reinstall + restore | Repeat for subscription and lifetime. Startup restores automatically; Restore purchases also reports the result. |
| Subscription already owned | Startup normally restores and disables checkout. If ownership is discovered during checkout, `ITEM_ALREADY_OWNED` triggers restoration automatically. |
| Lifetime already owned | Same recovery; never consumes ownership or sells a second copy. |
| Disconnect/reconnect | Loading failure leaves unavailable choices disabled. Restore/resume retries connection, prices and ownership. Last-known subscription Pro survives up to seven days; lifetime survives until a successful online refresh. |
| Offline cold start / running expiry | Buy and refresh online, terminate, then reopen offline: subscription Pro works before seven days and locks new Pro edits at the boundary with a reconnect action; lifetime remains available. Saved alarms and in-flight snapshots remain intact. Repeat while the app stays open. |
| Failed/partial/pending refresh | Neither clear last-known access nor extend its timestamp. Checkout and preview reconciliation must not interpret this as confirmed free ownership. |
| Successful online empty refresh | Both type queries succeed with a validated network and no owned purchases: clear saved access; subsequent offline start must not restore it. |
| Acknowledgement failure | Interrupt the network after approval; acknowledgement retries after connectivity returns. Restart/restore retries any still-unacknowledged record. |
| Cancel renewal / expire / refund-revoke | Renewal cancellation retains access while Play reports the purchase active. After expiry/revocation, a successful online foreground/restore snapshot of both product types removes that entitlement unless another valid Pro purchase exists. |
| Release bypass | No test activation control; old debug preferences cannot unlock Pro. |

The focused JVM tests cover client entitlement acceptance/rejection and catalog mapping;
they do not simulate Play checkout or certify Console configuration. Execute the table
on a Play-installed device before release. See [Google's license-testing guide](https://developer.android.com/google/play/billing/test).

## Acceptance criteria for the next buckets

Play Console configuration is complete when:

- The subscription and both base plans use the exact IDs above and are active.
- The lifetime one-time product is active.
- Regional prices and availability are configured.
- Grace period, account hold, and resubscription match this specification.
- License testers can retrieve all three choices from an internal-track build.

Release acceptance is complete when monthly, yearly, lifetime, pending,
cancellation, expiry, grace period, account hold, refund, restoration, reinstall, offline,
and temporary-service-outage paths all follow this specification.

# Yoga Alarm billing specification

Status: Android client integration implemented on 2026-09-26; backend verification and device acceptance testing remain outstanding.

This document is the source of truth for the target production billing implementation.
The temporary client-only implementation and its limitations are described below.
Changing product IDs or entitlement rules after the Play products are activated requires
an explicit product decision.

## Product principles

- The existing paywall design remains unchanged.
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
- Three-pose routines using Mountain, Warrior II, and Tree.
- A fixed 20-second hold per pose.
- Sunbird Morning Call and Morning Temple Call.
- Core alarm, snooze, vibration, camera validation, and routine completion.

Every Pro purchase unlocks:

- All eight current poses and future Pro poses.
- One to ten poses per routine.
- Hold durations of 5, 10, 15, 20, 30, 45, or 60 seconds.
- All ten current alarm sounds and future Pro sounds.
- The same Pro feature set regardless of monthly, yearly, or lifetime payment.

No free trial or introductory offer is included at launch.

## Purchase and entitlement rules

- Grant Pro only after Google reports the purchase state as `PURCHASED` and backend
  verification succeeds.
- Never grant Pro for a `PENDING` purchase.
- A verified purchase must be acknowledged promptly through the backend.
- Lifetime takes precedence if more than one valid plan is present.
- An active subscription grants Pro until its verified entitlement expiry.
- Cancelling auto-renewal does not remove Pro before the paid period expires.
- Pro remains available during Google's configured grace period.
- Pro is unavailable during account hold or after expiry, as reported by Google.
- A refund, revocation, or chargeback removes the entitlement associated with that purchase.
- A temporary Play or backend outage must not immediately remove previously verified Pro.
  Cached subscription access is valid only through its last known expiry; lifetime remains
  cached while awaiting reverification.
- V1 does not implement in-app switching between monthly and yearly. Active subscribers use
  Google's Manage Subscription screen. This can be added later without changing the paywall.
- A customer with active Pro is not offered another Pro purchase in V1.

## Restoration and identity

Yoga Alarm does not maintain customer accounts. The app creates a random anonymous
installation ID containing no personally identifying information.

Google Play restores eligible purchases for the Google account currently active in Play:

- Query purchases when billing connects and when the app returns to the foreground.
- Keep a visible Restore Purchases action.
- Send discovered purchase tokens to the backend for verification.
- A reinstall can create a new installation ID; the verified Play purchase remains the
  authority for restoring Pro.

Without a Yoga Alarm account, purchases do not transfer between Google Play and Apple's
App Store. This is accepted for V1. The future iOS app will use StoreKit and Apple's own
purchase restoration independently.

## Minimal backend data

The production backend stores only what is required to verify access and, later, attribute
creator commissions:

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

## Current client implementation and production replacement

`PlayBillingManager.state.entitlement` is the sole Pro authority used by the UI.
`PurchaseVerifier` is an injectable suspending boundary that receives package name,
product IDs, purchase state and purchase token. `ClientPurchaseVerifier` currently
trusts Play Billing's local purchase records after checking package, known product,
nonempty token and `PURCHASED` state. It performs **no signature verification or
server verification** and cannot independently validate expiry, refunds or tampering.
Pending records are never verified for access or acknowledged.

Both purchase-type queries must succeed before replacing current entitlement. A
failed query preserves session access; a successful empty snapshot removes access.
Callbacks grant accepted purchases immediately, then reconcile with a fresh snapshot.
Subscriptions are shown as “Subscription”, since the client record does not reliably
identify monthly versus yearly. Cancelling renewal does not itself remove access:
the app relies on the active purchases returned by Play. Lifetime takes precedence.
Settings explains that lifetime has no renewal or expiry. For subscriptions, a
full-width Cancel subscription button opens Google Play for confirmation; canceled
renewal changes the action to Manage subscription. A separate link opens the exact
renewal or subscription end date in Google Play. Subscription management remains
visible when the user owns both lifetime and a valid subscription, without affecting
lifetime access.

No entitlement or token is persisted by this implementation. An offline cold start
has no Pro until Play responds; an outage within a running session preserves access
but cannot enforce a trusted expiry. These are temporary deviations from the target
offline rules above, not a production offline-cache implementation.

The client acknowledges accepted purchases, retries transient failures with backoff
up to 60 seconds while the manager is alive, and rediscovers unacknowledged purchases
on connection/resume/restore. Lifetime is never consumed. This cannot guarantee
acknowledgement while the app is closed; failures must be resolved before Google's
acknowledgement deadline to avoid automatic refunds.

Before production:

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
| Disconnect/reconnect | Loading failure leaves unavailable choices disabled. Restore/resume retries connection, prices and ownership. Session Pro survives transient lookup failure. |
| Acknowledgement failure | Interrupt the network after approval; acknowledgement retries after connectivity returns. Restart/restore retries any still-unacknowledged record. |
| Cancel renewal / expire / refund-revoke | Renewal cancellation retains access while Play reports the purchase active. After expiry/revocation, a successful foreground/restore query removes that entitlement unless another valid Pro purchase exists. |
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

The later app/backend implementation is complete when monthly, yearly, lifetime, pending,
cancellation, expiry, grace period, account hold, refund, restoration, reinstall, offline,
and temporary-service-outage paths all follow this specification.

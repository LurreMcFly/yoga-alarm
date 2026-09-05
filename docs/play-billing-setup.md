# Yoga Alarm billing specification

Status: approved for Google Play setup on 2026-09-04.

This document is the source of truth for the first production billing implementation.
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

Monthly and yearly are base plans of one subscription because they provide the same
benefits. Lifetime is a separate non-consumable product. Play Console controls regional
availability and localized prices.

The current local identifiers `pro_monthly`, `pro_yearly`, and `pro_lifetime` are
prototype identifiers. They must be replaced during the app-billing implementation
after the catalog above exists in Play Console.

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
- Hold durations of 10, 15, 20, 30, 45, or 60 seconds.
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

- Direct-installed debug APKs may retain the clearly labelled no-charge test activation.
- Release and Play-distributed builds must never expose the debug activation path.
- Real purchases are tested through an internal Play track using license testers.
- Hardcoded dollar prices are debug fallbacks only and must not be presented as authoritative
  production pricing.

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

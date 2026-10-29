# Local monetization test — 2026-10-29

Result: the core trial accounting, automatic free conversion, disclosure and free-continuation paths passed local checks. Premium checkout is **not accepted for release**: actual Google Play transactions were unavailable, and the configured client verifier still lacks trusted offline paid access.

## Environment and scope

- Tested the current uncommitted Android checkout, version 1.0.27 (28).
- Built with `-PproTrialInternalTesting=true`; debug preview enrollment on, analytics off. Normal builds keep enrollment off. Enrollment is a persisted new-install 50/50 assignment; the temporary harness selected the preview cohort deterministically.
- Used an isolated API 35 Google APIs x86_64 emulator, 1080 × 2340, density 440. It has no Play Store/catalog. No existing device or emulator data was cleared.
- A separate, temporary instrumentation APK called the app's actual trial/occurrence stores with controlled completion dates. Its seeded alarm represented a previously confirmed fallback. This bypassed pose detection and the real camera/completion/Done flow; those are not claimed as device-tested.
- Separately created and saved a new alarm through the real editor, including duration selection and fallback confirmation.
- No application source was changed. This report is the only repository source addition. Generated evidence and harness sources are under `app/build/reports/monetization-local-2026-10-29/` (ignored build output).

## Executed results

| Check | Result |
| --- | --- |
| Focused JUnit/Robolectric checks | **101 passed**, zero failures/errors/skips across 12 suites. |
| Internal debug APK assembly | **Passed.** |
| Trial information | Home shows remaining successful alarm days; editor explains no payment details/automatic charge, first success per local date, and the fifth-completion transition. |
| Trial customization | A new alarm could change a hold from 20 to 5 seconds without a Pro lock. |
| Upfront fallback review | Save opened the actual three-pose, 20-second fallback and free sound. It explicitly warned that the routine increases from 45 to 60 seconds. Confirmation saved the trial routine and fallback together; persistence was inspected. |
| Same-day counting | Two distinct occurrences on October 20 left four days remaining, not three. |
| Skipped dates | October 20, 22, 24, 27 and 29 counted as five days; no consecutive-day requirement. |
| Second completion | Three days left. With offers unavailable, Home showed status and Retry rather than an unusable automatic offer. |
| Fourth completion | One day left and the explicit warning that the next qualifying completion switches future alarms to free settings. |
| Fifth completion | Active access became false, remaining became zero, transition became APPLIED. A one-pose Chair/5-second/paid-sound fixture changed to Mountain/Warrior II/Tree, 20 seconds each, Sunbird Morning Call. ID, enabled state, time, weekdays, snooze and other flags stayed unchanged; the original customization was retained. |
| Expired Home | Clearly states: “Your Pro preview is complete. Future alarms use your free settings.” Shows the converted 60-second routine. |
| Expiry paywall without store | Manual Keep Pro opened the expiry headline, unavailable lifetime/yearly/monthly plans, disabled purchase, and visible Continue free. Automatic presentation remained deferred and milestone 5 remained pending. |
| Continue free | Returned to Home with the converted routine and exhausted allowance intact. Restarted processes also loaded that state. |
| Expired timing controls | 20 seconds included; other durations visibly marked Pro/locked. |
| Simulated verified purchase after expiry | Calling reconciliation with paid/recoverable ownership retired the preview and cleared its pending offer without automatically replacing the free routine. Explicit restoration restored the original routine/sound. **This was store-layer simulation, not Play checkout or purchase-success UI.** |

Automated suites: `ProTrialTest`, `ProTrialStoreTest`, `AlarmStoreTrialTest`, `AlarmOccurrenceStoreTest`, `WakeCompletionRecoveryTest`, `BillingUiStateTest`, `PurchaseVerifierTest`, `PaidEntitlementCacheTest`, `PlayBillingManagerTest`, `RoutineViewModelTest`, `AlarmNotificationTest`, `AlarmSchedulerTest`.

These cover additional simulated cases: disabled/deleted alarms, stale saves, snoozed snapshots, failed expiry persistence, completion replay, restoration preserving newer edits, pending/canceled checkout guards, stale verification, authenticated cache rejection/expiry and completed-occurrence recovery. They do not establish physical-device behavior or Play server acceptance.

## Findings

1. **Release blocker, already documented: paid access after offline cold restart.** The real app uses `ClientPurchaseVerifier`, which produces no authenticated recoverable paid proof. Valid in-session access survives query errors, but a cold offline restart cannot restore paid access from this verifier. Preview retirement also deliberately requires recoverable proof. The simulated recoverable-purchase test does not remove this blocker. See `billing/PlayBillingManager.kt`, `billing/PurchaseVerifier.kt`, `billing/PaidEntitlementCache.kt`, `data/ProTrialStore.kt` and `docs/play-billing-setup.md`.
2. **Visible layout issue:** the wrapped expiry headline has crowded line spacing at the tested default display configuration. `ui/AppInfoSheets.kt` sets 30sp text without an explicit matching line height. See `expired-paywall.png` in the evidence folder. Verify a corrected line height at normal/large text and small widths.
3. **Minor copy issue:** Home says “Pro preview · 1 alarm days left.” Add singular/plural handling in `ui/YogaAlarmRoot.kt:136`. See `trial-day-four.png`.

## Purchase behavior and remaining acceptance

Source review and simulated billing tests show that accepted purchases grant Pro and close the paywall; post-expiry purchase offers selected original-routine restoration while preserving newer edits. Cancel grants nothing. Pending payment grants nothing, explains that Pro unlocks after confirmation, and blocks competing checkout. Ordinary trials continue with their remaining allowance; exhausted trials remain free. No automatic charge occurs when the local preview ends.

Still required with a Play-enabled Internal Testing installation and test account: actual lifetime/yearly/monthly success, cancel/decline/pending completion, purchase restoration, renewal/refund/revocation, trusted purchase-then-offline restart, and the real automatic expiry offer with buyable prices. Also untested on device: actual camera completions and Done sequencing, ringing/snooze across expiry, checkout interrupted by a new alarm, lock-screen/process-death/reboot paths, large text/landscape and analytics delivery. The local checks do not replace section 9 of the implementation plan.

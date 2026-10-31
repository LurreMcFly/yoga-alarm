# Yoga Alarm monetization implementation plan

Status: Android implementation present for internal validation; production preview enrollment and analytics are disabled by default. Production billing integration and device/store acceptance remain release blockers.
Prepared: 2026-10-27. Code/UX review: 2026-10-28. Android is the reference implementation.

## Implementation record — 2026-10-28

The implementation was revalidated against the dirty checkout and preserves the existing notification Start-to-camera changes. No Android implementation commit has been created; commit reference: `<pending>`. The review findings below remain source/lifecycle risks, not claims of reproduced device failures.

Implemented stages:

- **Occurrence safety:** delivery tokens, shared sound/routine snapshots, token-scoped notification/service/route actions, preserved snooze snapshots, completed outcomes and a recovery journal for streak recording. New recurring deliveries reset routine state. Completed/stopped/snoozed routes cannot restart audio merely by reattaching. Delivery and future-alarm conversion share a serialized preference boundary.
- **Local preview:** stable new-install 50/50 assignment before default writes, five distinct local completion dates, milestone supersession, explicit fallback metadata, checked completion/expiry commit, current-data save/reorder/delete/disable merges, disabled-alarm preservation, and conflict-safe restoration. Practice/test/stop paths do not invoke the real-completion recorder. Missing fallback data preserves a working alarm and requests review rather than inventing poses.
- **UX:** effective access is separate from paid ownership; guided paid catalog choices retain three teaching slots; ordinary editing supports existing lengths/durations; fallback review offers free pose/sound choices and total hold time before explicit save. Home/Settings expose preview status and purchasing. Safe home milestones and lifetime-first paywall retain Done/dismissal. Purchase success offers selectable restoration without replacing newer edits. Open editor customizations rebase while unrelated drafts survive, and pickers/drag state close on transition.
- **Billing foundations:** structured ownership/verification/pending/checkout guards, settled catalog publication, stable paywall selection, and authenticated offline-cache infrastructure. `RecoverablePurchaseVerifier` must supply/revalidate opaque authenticated proof and actual subscription expiry. **The default `ClientPurchaseVerifier` still cannot do this.** It never creates a trusted cache; the preview is retired only alongside recoverable paid access. Do not ship the preview with the current verifier and claim purchase-then-offline acceptance.
- **Measurement wiring:** a disabled sink in normal builds and a separately selected Firebase sink, coarse allowlisted funnel events, stable assignment/version, local replay guards, and separate restoration events. SDK acceptance is not confirmed delivery. Restores, client checkout outcomes and prices are not authoritative revenue; purchase history is explicitly unknown until verified reporting establishes it. No external project, backend, keys, transaction attribution, refunds, net revenue, consent approval or published disclosure was fabricated.

Build/rollout controls:

- Normal builds: preview enrollment and analytics off. Already enrolled preview state/fallback policy remains intact when enrollment is disabled; automatic commercial prompts stop.
- Internal product validation: the user requested the standard `release` signing workflow. This checkout now sets `proTrialInternalTesting=true` in `gradle.properties`, enabling preview enrollment and guarded Settings controls in release/debug without extra command-line flags. See the [internal testing runbook](monetization-internal-testing.md). Before public production distribution, set the property false and rebuild; `-PproTrialInternalTesting=false` overrides it for a single CLI build. The separate internalTesting variant was removed.
- Production enrollment: `-PproTrialRolloutEnabled=true` requires `-PmonetizationAnalyticsEnabled=true`, `-PmonetizationPrivacyApproved=true`, real `app/google-services.json`, and `-PmonetizationBillingReady=true`. These are explicit build gates/attestations, **not implementations of consent, verification or acceptance**. Keep them off until the dependencies below are resolved.
- Analytics-enabled builds select Firebase and retain network permissions. Default debug/release manifest overlays remove network permissions. Backup remains disabled; advertising ID collection and ad personalization defaults are disabled. Audit automatic SDK collection as well as the custom allowlist before enabling collection.

Validation record (updated during implementation):

- Executed: scoped debug Kotlin compilation and 93 JUnit/Robolectric regressions passed, covering trial/store/counting, billing/verifier/cache, occurrence/service/notification/scheduler and routine ViewModel paths. Subsequent integration changes are checked again before delivery; see the final validation update below.
- Executed: `adb devices -l` returned no connected devices. No physical-device, lock-screen gesture, process-kill/device, Google Play purchase, Firebase DebugView or production reporting verification is claimed.
- Authored UI behavior still requires the full section 9 device matrix, especially ten-pose picker shrink, live drag cancellation, large text, guided fallback modal ordering, same-ID next-day delivery, overlapping alarms/checkout, and exact visible offer exposure.

Final scoped validation:

- **Executed, passed:** debug and release Kotlin compilation; 135 tests across 14 focused JUnit/Robolectric suites (trial/model/store, occurrence recovery, notification/scheduler, routine ViewModel, billing/cache/verifier, wake-progress and schedule accounting). All newly authored JVM tests were included. Tests cover failed completion-commit rollback, pending/canceled purchase resolution, authenticated proof expiry during an offline session, stale verification, more than sixteen simultaneous snooze snapshots, and completion/streak replay. These tests use simulated stores/platform components; they do not verify Play servers or physical devices.
- **Executed, passed (prior configuration):** default merged release manifest has `allowBackup=false` and no `INTERNET`, `ACCESS_NETWORK_STATE` or advertising-ID permission. Default debug/release flags are false. The earlier internal flag enabled debug enrollment only; the current explicit internal flag also enables release enrollment and testing controls, with analytics still separately gated. Revalidate the current flags before distributing an artifact.
- **Executed, passed:** configuration checks reject public rollout without measurement/billing readiness, analytics without privacy approval, and analytics without a real Firebase project file. `git diff --check` passed.
- **Not run:** the Firebase-enabled source/dependency path has not been compiled (real project configuration is absent), and SDK event delivery/automatic collection/retention have not been validated. No production verification backend or signed-proof issuer is configured.
- **Not run:** all fifteen section 9 physical-device/Google Play scenarios remain acceptance work. `adb devices -l` showed no device. In particular, no lock-screen/process-kill behavior, actual Google Play cancellation/pending/restoration/renewal/refund, paid purchase followed by airplane-mode restart, or visual/accessibility layout result is claimed.
- **Not run:** full app assembly, the unrelated pose-detection suite, and any iOS build/test. Validation stayed scoped to affected behavior. Android changes remain uncommitted; retain `<pending>` implementation hashes.

Concrete production dependencies:

1. Implement and deploy the trusted Play verifier: authenticate purchase evidence, validate subscription expiry/renewal/refund/revocation, issue offline-verifiable proof, and wire the verifier into `PlayBillingManager` instead of `ClientPurchaseVerifier`. Supply real trust material/transport configuration; do not calculate subscription expiry from purchase time. Complete purchase/termination/airplane-mode/expiry/revocation acceptance before retiring paid previews in production.
2. Supply the Firebase project configuration; approve collection/consent/retention, audit automatic events, update the **published** privacy policy and Play Data safety declarations, and verify delivery in DebugView. The repository documentation describes requirements; it does not update external declarations.
3. Establish authoritative transaction/cohort attribution and store-report reconciliation for refunds, renewals and fees. Keep client signals provisional. No cohort-level 60-day realized-revenue conclusion is possible from these client events alone.
4. Execute section 9's device/Internal Testing matrix with each real store plan and lifecycle condition. These checks are independent of passing JVM tests.

## 0. Review outcome and changes to the original plan

The five-completion preview remains the recommended monetization experiment. The original plan was not implementation-ready: it allowed permanent use of saved Pro routines, underestimated alarm-session persistence, and omitted several payment races. These corrections are prerequisites, not evidence that the trial will increase revenue.

**Expiry decision confirmed by the user on 2026-10-28:** announce and confirm the actual free fallback when saving a trial routine; apply it to future alarms after the fifth qualifying completion. Closing the expiry offer does not extend Pro. Already ringing or snoozed occurrences keep the configuration with which they started. This replaces the previous indefinite manual-review grace policy throughout this plan.

Static review findings (this table uses unique filenames; full implementation paths are listed in section 8):

| Priority | Finding and code evidence | Required correction |
|---|---|---|
| P1 | Saved Pro routines could run forever under the old section 5; alarms execute stored settings (`AlarmReceiver.kt:15`, `AlarmForegroundService.kt:110`). | Confirm fallback before trial settings are saved; perform a local, automatic future-configuration transition at expiry. |
| P1 | `RoutineViewModel.kt:63–68` loses its phase on process death; `RoutineRoute.kt:149–169` can start a service for a restored fired route with a fresh non-complete phase. | Persist completed-occurrence outcomes and consult them before service reattachment. Device reproduction remains required. |
| P1 | `YogaAlarmRoot.kt:258–274`, `RoutineViewModel.kt:78–81`, and `RoutineRoute.kt:116` distinguish alarms by configuration ID rather than occurrence. | Distinguish a new recurring occurrence from reattachment to the prior one; preserve identity across notification taps and snooze. |
| P1 | Snooze only schedules a PendingIntent (`AlarmScheduler.kt:51–66`); closing the route removes in-memory fired state. | Preserve the occurrence's configuration across snooze and process recreation; a quiet service is not proof there is no in-flight alarm. |
| P1 | `AlarmStore.kt:19–23` replaces the entire list, while `AlarmReceiver.kt:32–33` independently disables one-shot alarms. | Merge changes into the latest stored alarms; prevent stale UI writes from restoring old enabled flags, deleted alarms, or Pro configurations. |
| P1 | `AlarmEditorScreen.kt:147–149` retains drafts by alarm ID and `:562` directly indexes an open duration picker. | Reconcile drafts/card identities and close transient controls on configuration transitions; validate at Save. |
| P1 | `PlayBillingManager.kt:127–169,283–295` does not block checkout on unresolved ownership, suspended verification, or a pending purchase. | Add structured purchase state and enforce checkout eligibility in the manager and UI. |
| P1 | Retiring the trial plus the documented absence of a paid offline cache can make a purchaser lose access after an offline restart. | Trusted paid-cache/expiry behavior is a release dependency for this flow, not a later optional improvement. |
| P2 | `PlayBillingManager.kt:185–208` publishes partial offers; the paywall derives selection from the currently available subset. | Settle the initial catalog before selecting a default and preserve explicit selection during refresh/checkout. |
| P2 | A one-time expiry offer can be displayed when no plans are buyable. | Defer the commercial milestone until actionable; free fallback and alarm behavior cannot depend on catalog availability. |
| P2 | `AndroidManifest.xml:14–19` explicitly removes network permissions; `:28` disables backup. | Name manifest changes in measurement/billing work; retain the no-backup policy and acknowledge reinstall resets. |
| P2 | Trial dates and `WakeProgressStore.recordCompletion()` could sample midnight separately. | Capture one occurrence completion time and pass it to both recorders. |

These are source-backed integration gaps and lifecycle risks, not claims of device-reproduced failures. Recheck line references against the implementation revision. Existing uncommitted notification Start changes must be preserved.

## 1. Product decisions

Implement an app-managed Pro preview for five successful alarm-completion days, followed by the existing permanent free tier. No card, store trial, subscription enrollment, calendar deadline, consecutive-day requirement, or automatic charge.

- Use the existing product name **Pro** throughout the UI. Do not introduce Premium as a second product name.
- A qualifying completion is a genuinely fired alarm completed through yoga, on a local calendar date not previously counted toward the preview. Any time of day qualifies; “morning” is marketing language, not a time restriction.
- Practice, full-routine tests, single-pose tests, snoozes, missed alarms, normal stops, emergency stops, and technical stops do not consume a day. A recovered camera error followed by a successful real completion does count.
- Snoozing itself consumes nothing; subsequently completing that snoozed real occurrence through yoga does count, subject to the once-per-date limit.
- Unlock Pro during initial alarm setup. Keep the default three-pose routine so the user need not customize anything.
- Keep USD reference prices at $7.99 monthly, $19.99 yearly, and $29.99 lifetime. All displayed amounts must still come from the store in local currency.
- Make lifetime the first and initially selected available option, then yearly, then monthly. Monthly remains visible and accessible.
- Keep purchases available throughout the preview. Paid ownership always takes precedence and suppresses trial offers.
- After completion two, show one optional inline purchase invitation. After completion four, show the one-day-remaining notice. After completion five, offer the paywall once it is safe to do so.
- Declining payment never removes alarms or streaks and never blocks dismissal. Before saving a trial customization, confirm the actual free routine/sound that future alarms will use after the fifth qualifying completion. At expiry apply those previously confirmed settings automatically; preserve in-flight occurrences.
- Existing installations retain their current experience; enroll only new eligible installations in the initial experiment. Restored purchasers receive their owned access.
- No discounts, extensions, referrals, social-sharing rewards, new paid content, or price experiments in this release.

## 2. What the code currently does

| Area | Current behavior | Required change |
|---|---|---|
| `ui/YogaAlarmRoot.kt` | Editor/home access comes directly from `billingState.entitlement != null`. Real completion is already distinguished from onboarding practice and tests. | Combine paid ownership with preview access for editing; keep ownership separate for checkout/status. Record trial progress at the existing real-completion callback. |
| `billing/PlayBillingManager.kt` | Queries localized offers, handles purchases/restoration, blocks another purchase when a paid entitlement exists. | Preserve these rules; expose ownership-check success/failure explicitly so unknown ownership is not treated as confirmed free. Add measurement hooks only where needed. |
| `ui/AppInfoSheets.kt` | Paywall defaults to yearly, lists monthly/yearly/lifetime, and Settings knows only paid/free. | Lifetime-first presentation, trial-aware copy, explicit free continuation, pending-transition status. |
| `ui/AlarmHomeScreen.kt` | Hides GET PRO when `isPro` is true. | A trial user must still see remaining days and an optional purchase entry. Paid users should not receive upsells. |
| `ui/AlarmEditorScreen.kt` | Gates poses, durations, sound selection, and pose count with `isPro`; guided setup separately filters poses/sounds to free choices. | Use effective feature access; allow Pro choices during trial setup; preserve the guided tour's fixed three teaching slots. |
| `ui/RoutineRoute.kt` | Finishes the alarm, invokes completion, and provides a ten-second completion presentation. | Preserve presentation behavior; add durable occurrence completion/recovery, stable completion time, and safe deferred commerce. |
| `data/AlarmStore.kt` | Stores the complete alarm configuration in local preferences. | Persist confirmed fallbacks/original customization and atomically transition future configurations at expiry. Never normalize on ordinary load. |
| `data/WakeProgressStore.kt` | Records local completion dates, streaks, schedule history, and emergency passes. | Keep independent of trial accounting; the trial cannot be reconstructed from streak/history. |
| Analytics | No analytics dependency/integration found in the Android app. | Add a defined measurement destination before calling this a measurable revenue experiment. |

Source paths in this document are relative to `app/src/main/java/com/lurremcfly/yogaalarm/` unless otherwise stated.

The current catalog has **nine poses**, four free: Mountain, Warrior II, Tree, and Squat. It has ten sounds, two free: Sunbird Morning Call and Morning Temple Call. Existing documentation still mentions three free/eight total poses; correct that drift when implementing.

## 3. Local state and access rules

Add a small `model/ProTrial.kt` model with pure transition/counting logic and a `data/ProTrialStore.kt` using the existing SharedPreferences approach. Trial expiry must be committed together with the affected future alarm configurations: use scoped keys in the existing `yoga_alarm` preference file and one narrowly defined local mutation path, rather than unrelated writes to separate preference files. Do not add Room, accounts, or a general entitlement framework for this feature.

Persist only the necessary state:

| Field | Purpose |
|---|---|
| Schema/experiment version | One-time migration and stable experiment interpretation. |
| Enrollment | Existing/ineligible, control, or trial variant; assigned once. |
| Counted local dates | Set of at most five ISO dates; derive completed/remaining counts. |
| Trial retired by purchase | Prevent an unused preview from restarting after subscription expiry/refund. This is not proof of current paid ownership. |
| Pending milestone | Offer after two, warning after four, or expiry after five. |
| Presented milestone markers | Avoid repeated automatic presentations after rotation/relaunch. |
| Free transition state | Not needed, pending local recovery, or applied. Never waiting for a post-expiry marketing acknowledgment. |

In the same persisted alarm data, retain each trial-customized alarm's confirmed free routine/sound, the customization it applies to, and its original Pro routine/sound after conversion. Use direct value comparisons; a separate hashing/version framework is unnecessary. Unchanged free configurations need no fallback metadata. Missing metadata on a trial configuration is a recoverable migration/error case, not permission to invent a surprise replacement.

Derive feature access from current paid ownership or an enrolled, unretired trial with fewer than five counted dates. Do not add `TRIAL` to `ProEntitlement`: that type represents purchases and is used to disable checkout.

Keep these concepts separate at call sites:

- **Paid ownership:** billing entitlement; controls account text, restoration, and duplicate purchase prevention.
- **Feature access:** paid ownership or remaining trial; controls creating/changing paid customization.
- **Trial status:** remaining days, milestones, and transition review.
- **Ownership resolution:** loading, successfully checked, or unavailable. `loading == false` alone is insufficient because the current manager also sets it after errors.

Important transitions:

| Event | Result |
|---|---|
| First eligible launch | Persist assignment before onboarding renders. Trial arm receives five days. |
| Qualifying completion on a new date | Persist the date and any newly due milestone together; cap at five. |
| Another completion on an already counted date | No trial decrement and no duplicate prompt. |
| Fifth completion | Persist the completion and convert future saved configurations to their previously confirmed free fallback in the same local transaction; lock new paid customization. In-flight snapshots remain intact. |
| Purchase/restoration confirms paid ownership | Retire trial, clear trial prompts, cancel unapplied downgrade, unlock features. If conversion already happened, offer explicit restoration without overwriting later edits. |
| Pending or canceled purchase | No paid entitlement; continue the independently valid trial/free state. |
| Known purchaser later loses paid access | Do not restart a trial; retain existing subscription-expiry handling unless separately changed. |
| Billing unavailable | No inference of paid expiry from a query error. Existing valid paid cache wins; a known local trial still ends according to its confirmed fallback policy. Alarms never wait for a network response. |

The local trial and its disclosed expiry must work offline after enrollment. Separate local access from network freshness. A temporary query failure preserves previously validated access through its known validity; it must neither revoke a paid owner nor grant an ordinary nonpaying trial unlimited extra days. A previously observed purchaser whose ownership cannot be established safely must not be treated as a new free user. Resolve that using the trusted cache described in section 6, not a network dependency in the alarm path.

While an initiated checkout is being verified, defer a conflicting transition until the purchase attempt reaches a known state. `PENDING` is a known nonpaid outcome: it does not extend the preview, and the UI explains that access unlocks when payment completes. Recheck ownership immediately before applying any transition. Serialize purchase-success and local-expiry updates so either ordering has a deterministic result.

Capture one completion timestamp (including its local date/zone) per real occurrence and use it for both trial accounting and `WakeProgressStore.recordCompletion(now)`. Re-delivery uses the original timestamp. Persist the occurrence outcome, trial date, new pending milestone, and any free transition before treating the local operation as finalized. Do not describe `rememberSaveable` or an asynchronous `apply()` alone as a durable success acknowledgment. Use one serialized off-main-thread commit for this small critical mutation, check its result, and recover conservatively from failure; stop audible playback immediately and never wait for analytics/network work. If persistence fails, granting an extra trial use is preferable to charging a phantom completion or re-ringing a dismissed alarm.

Completion dates prevent duplicate trial consumption, but do not identify alarm occurrences. Add a small occurrence token at real delivery and propagate it through service, activity, route, and notification/snooze intents. A new recurring delivery gets a new token; notification reattachment, rotation, and snooze retain it. Maintain a bounded persisted occurrence record containing the started configuration and terminal outcome. A completed occurrence must not restart service playback after process recreation. Consult this record before reattaching a saved fired route. Reuse a shared record for lifecycle and snooze protection; do not create a second trial-specific alarm scheduler.

Define cancellation/cleanup for these records: normal/technical/emergency stop and completion finish the occurrence without falsely counting yoga; cancellation/deletion cancels any pending snooze; delivery must not revive deleted alarms. A reboot invalidates obsolete transient snooze records when the associated OS alarm no longer exists. Preserve existing normal repeating-alarm rescheduling. A leftover record must never indefinitely block future free transitions.

Use local dates consistently with existing streaks. Timezone travel may allow another distinct calendar date; accept that rather than adding clock tampering defenses. Deleting an alarm or clearing history must not reset the preview. Reinstall/clear-data resets are an accepted limitation of an account-free local preview. The manifest currently has `allowBackup="false"`; retain that policy and do not promise automatic trial restoration or silently enable backup.

Expiry is immediate after the fifth qualifying completion, not midnight and not five elapsed days. A separately scheduled occurrence that has not started yet uses the free fallback even if it falls later that date. A previously started/snoozed occurrence retains its snapshot. Use “5 successful alarm days” with a short explanation that only the first success on each date counts and future alarms switch after the fifth; never describe it as a five-calendar-day free trial.

### Enrollment migration

Determine existing use before constructors/load functions write default preferences. Check existing alarm, onboarding, and wake-progress data; existing users are excluded from initial assignment. An old installation with no persisted use can be treated as a new first-use installation. Document this definition rather than claiming perfect reinstall detection.

Record observed paid ownership and retire any assigned trial when purchases restore. Never display a preview as a replacement for an owned purchase. Preserve current user data and previously paid access. In tests, explicitly exercise restore arriving after the initial screen.

## 4. Screen-by-screen changes

### Onboarding and alarm editor

Change `Onboarding.kt`, `AlarmEditorScreen.kt`, and the root wiring.

- Add a compact explanation before/while configuring the first alarm: “Try Pro for 5 successful alarm days. No payment details or automatic charge. Then your alarms use the free settings shown below unless you buy Pro.” Expose the same information to users who skip the tour.
- Keep the existing permission flow and tour; do not insert a compulsory paywall.
- Trial users can choose all nine poses and ten sounds. Remove the unconditional free-only filters during guided setup when effective Pro access is present.
- Keep the guided sequence at three poses so its Pose 1/2/3 targets and practice flow remain valid. Add an optional “Customize length & timing” action to the saved summary that finishes the tour and opens that alarm in the normal editor. Done still goes home. Do not force an additional editing step or redesign the tour around arbitrary routine lengths.
- Outside the tour, trial users can select 1–10 poses and the existing seven durations: 5, 10, 15, 20, 30, 45, 60 seconds.
- Maintain free restrictions after trial: exactly three slots, four free pose choices, 20 seconds per pose, two free sounds.
- Preserve unlimited alarms, time/day/name editing, snooze, vibration, stop controls, and streaks for free users.
- On the first save that introduces paid customization, show an inline summary of the actual free fallback, including poses, total hold time, and sound. Let the user adjust it using free choices. Require one explicit confirmation for that fallback before saving the trial customization; if declined, keep the draft or save a free configuration. Reuse the confirmation for subsequent changes only if the accepted fallback remains unchanged. Do not repeatedly show a modal when editing the name/time.
- Make the increase in effort explicit when applicable: one 5-second pose becoming three 20-second poses is a substantial change, not merely “fewer features.” If the free routine is unsuitable, offer editing/free setup or purchase; never imply that payment is required for an emergency stop.
- Audit save/reopen behavior, not only disabled controls: expired users must not save a Pro configuration from a stale draft. Validate changes against current effective access and the latest persisted alarm. Apply the fallback only through its confirmed transition, never an unrelated silent normalization on Save.
- On expiry, explicit restoration, or changed persisted customization while an editor is open, close sound/duration pickers and cancel drag state before rebasing routine/sound/card identities. Preserve unrelated unsaved name/time/day edits. Use safe slot lookup so shrinking ten poses to three cannot crash an open duration picker. Do not reset the whole draft merely because a billing refresh started.
- Preserve the selected pose by identity across guided/normal catalog changes and access changes. A carousel effect reacting to a new catalog must not be mistaken for a user selecting a different pose.

### Home and Settings

Change `AlarmHomeScreen.kt` and `AppInfoSheets.kt`.

- Paid: existing paid-status display; no trial prompts.
- Active trial: “Pro preview · 3 alarm days left,” with an optional “Keep Pro” action.
- Trial exhausted: “Your Pro preview is complete. Future alarms use your free settings,” with purchase and “View free routine” actions. When an already-started occurrence is finishing under its original snapshot, distinguish “This alarm” from “Future alarms.”
- Free: existing GET PRO action and contextual upgrades.
- Do not pass effective trial access into the old home `isPro` flag and accidentally hide purchasing.
- Settings shows trial status separately from subscription/lifetime ownership and keeps restoration available.
- These additions must fit small screens and larger text without squeezing the alarm list or obscuring readiness information.

### Completion milestones

Use the root's existing `onRoutineComplete` fired-alarm branch. Keep practice and test behavior unchanged. Never use streak length as the trigger.

| Completion | Presentation |
|---|---|
| 1 | Existing success screen, plus remaining allowance if space permits. No automatic offer. |
| 2 | One dismissible inline card: “Make this routine yours. Keep Pro with a one-time purchase or subscription.” Button opens the existing paywall. |
| 3 | No additional automatic offer. |
| 4 | Inline notice: “1 Pro alarm day left. After your next qualifying completion, future alarms use your free routine unless you buy Pro.” Offer an optional view of the accepted fallback. |
| 5 | Preserve the success screen and confirm that future alarms will use the previously accepted free settings. After Done and leaving alarm presentation, show the expiry paywall once. |

If the app closes or the screen locks, retain the pending milestone. Show the relevant latest milestone on the next safe foreground session; do not replay a backlog of earlier cards. Recompute against current ownership before presentation. Mark a commercial milestone presented only when the actionable offer actually appears. Opening an empty-price/error sheet does not consume the one automatic offer.

Automatic commercial presentation requires all of: resumed foreground activity, device unlocked, no active alarm/routine or pending alarm handoff, completed presentation dismissed, no onboarding/permission flow or other modal, confirmed nonpaid status, no checkout/verification/pending payment, and at least one currently buyable offer. Recheck on lifecycle/ownership/catalog changes and immediately before launch. If catalog lookup fails, show a small status/Retry entry and retain the offer for a later safe opportunity. The free transition itself must not wait for prices. Do not place a deferred paywall over an editor where the user has started another task.

The ten-second completion grace is for the existing alarm presentation, not a paywall countdown. Do not extend it, keep the device awake for commerce, or put checkout over the lock screen. If a new alarm arrives, dismiss/defer monetization UI immediately using the existing fired-alarm priority.

Keep the celebration and Done dominant. Prefer the second-completion invitation on the home screen immediately after Done if it does not fit the completion layout comfortably. Keep the fourth-completion warning visible in the next safe home session if it was missed. Never force the user to read an offer to finish yoga. Opening Google Play is an external activity: do not assume the app can forcibly dismiss that checkout if an alarm fires; test that normal alarm priority still works and reconcile the eventual purchase callback without reopening an offer over the alarm.

### Paywall

Update `ProPaywallSheet` in `AppInfoSheets.kt`; reuse the existing expanded sheet, scrolling benefits and fixed purchase area.

- Display lifetime, yearly, monthly in that order. Wait for the initial complete catalog attempt before choosing a default: lifetime when available, otherwise yearly, then monthly. Preserve explicit selection independently from the automatic default. Stage partial query results or keep selection unset until that first attempt settles, so yearly does not visibly switch to lifetime as results arrive.
- Refresh prices without changing the user's choice. If their chosen plan becomes unavailable, disable its checkout and explain why; let them select another plan instead of silently substituting one under the Buy button. Freeze the selected offer during checkout and revalidate it in the manager immediately before launch.
- Retain localized store prices, exact billing period, renewal terms, purchase/subscribe distinction, restore, retry, unavailable plans, and paid-owner duplicate purchase blocking.
- Keep existing catalog IDs and base plans. Do not configure a store free trial or pick promotional offers.
- Trial headline: “Keep your morning routine yours.” Expiry headline: “Keep your custom routine.” Explain benefits without implying streak loss or alarm failure.
- During the trial, closing returns to the remaining preview. At expiry, show a visible “Continue free” action that returns to home; the fallback was already confirmed when saving. Close/swipe/back has the same access outcome and does not extend Pro. Add an optional “View free settings” action without forcing a second decision funnel.
- Show the differences that actually apply to the user's saved alarms; if none use paid customization, say that their current routine can continue free unchanged.
- Purchase success suppresses queued offers and exits the transition flow. Pending payment clearly states that payment is awaiting confirmation; it does not erase valid trial access or claim paid access.

- Pass a small entry context to the same paywall: source plus the feature requested, where relevant. A locked duration should explain adjustable holds; a locked pose should explain access to that pose. Return to the original editor/chooser after cancellation or purchase without saving unrelated changes or losing the draft. If the alarm was deleted or a real alarm interrupted the route, do not resurrect stale UI.
- Keep the full billed amount adjacent to the purchase button. Use factual lifetime copy (“One-time payment. No subscription.”) rather than unsupported savings, scarcity, or a claim that lifetime is best for every user. Do not claim the user used a paid feature unless their saved configuration actually did.

## 5. Upfront-confirmed automatic transition to free

This is the main correctness risk. Use two distinct concepts: a saved configuration for **future occurrences**, and a fixed configuration snapshot for an occurrence that has **already started**. Trial expiry may update the first; it must not rewrite the second. Alarm playback and dismissal never wait for billing or analytics.

### Before a trial-customized alarm is saved

1. Derive a candidate free fallback: take the first three routine slots; keep free poses, replace paid poses with the corresponding Mountain/Warrior II/Tree default, and pad missing slots from those defaults. Set holds to 20 seconds. Keep a free sound or suggest Sunbird Morning Call for a paid sound. Preserve already allowed duplicate poses.
2. Show the actual result, including total hold time, and allow choosing any free poses/sound. The candidate is a suggestion, not permission to introduce an unfamiliar pose later.
3. Confirm: “After your fifth successful alarm day, future alarms use these free settings unless you buy Pro.” The trial routine and accepted fallback are saved together. This also applies outside onboarding and when re-enabling a saved trial alarm that lacks a valid fallback.
4. For a configuration already fully free, save normally and explain that it will continue unchanged. Avoid an unnecessary confirmation dialog.

### On the fifth qualifying completion

1. Stop audible playback immediately and record the terminal occurrence outcome. Use the single local completion/expiry mutation described in section 3; do not tie this to the purchase sheet being seen or dismissed.
2. Read the latest saved alarms and current locally established paid access. If already paid, suppress expiry. If verification of an initiated purchase is in flight, queue resolution as specified in section 3.
3. For each still-existing trial-customized alarm, retain its original routine/sound and replace only future routine/sound with the previously accepted fallback. Include disabled alarms so re-enabling them cannot revive an expired Pro routine. Skip unrelated legacy/paid configurations; never apply this migration to the existing-user/control group.
4. Preserve IDs, names, ordering, enabled state, time, repeat days, sound/vibration flags, snooze settings, and stop-button settings. In particular, a one-shot alarm auto-disabled by the receiver must stay disabled.
5. Persist the completion date, original customization, changed future settings, transition marker, and pending offer consistently in one preference transaction. Do not call the normal save-and-enable editor callback, cancel/reschedule alarms merely for a routine change, or overwrite the full list from a stale screen snapshot.
6. Refresh/rebase visible home/editor state. Show the actual free routine and optional purchase offer at the next safe opportunity; closing it does not undo the transition.

Serialize local read/merge/write operations against receiver/UI writes. A narrowly scoped update function in the existing store is preferable to a general repository framework. Audit the editor Save, enable/disable, delete, reorder, and one-shot receiver paths: after a transition none may write an earlier whole-list snapshot over the new settings. Reorder merges IDs into the current records, and customization updates modify only intended fields. Recheck reviewed fallback values at save; do not silently use a proposal for a different configuration.

### In-flight occurrence protection and recovery

- At real delivery, obtain the effective configuration and persist the occurrence snapshot before service/activity consumers use it. Both sound and camera routine must use the same snapshot; loading the latest alarm independently in each consumer can produce mixed behavior across the expiry boundary.
- Snoozing retains that occurrence and its snapshot, including through notification Snooze and process death. A future normal repeat gets a new occurrence and reads the new free settings. A pending normal alarm that has not fired is a future occurrence, not an indefinitely protected one.
- Use the same serialized storage boundary to order delivery/snapshot creation against expiry: whichever starts first determines the correct configuration, without a partially converted snapshot.
- Never use `firedRoutine == false` or an idle/stopped service as the sole safety check. Neither exposes a pending snooze.
- Recover an interrupted local transition before starting a new normal occurrence, using only persisted local data. If persistence or required fallback metadata is missing/corrupt, preserve a working alarm and surface repair on the next safe foreground session; do not choose unfamiliar poses or fail to ring. This is an exceptional recovery grace, not the standard expiry policy.
- Preserve the existing policy for overlapping different alarms; this feature does not promise a new alarm queue. Test a second alarm arriving at completion/expiry and prevent an old Done callback from stopping the new occurrence.

### Later purchase and restoration

Retain original routine/sound keyed by alarm ID; never back up scheduling/streak fields for restoration. After a purchase, unlock editing immediately and offer “Restore your trial routine” with a preview directly in the success flow, alongside “Keep current routine.” Do not make the user search Settings to recover what they just paid for. With several affected alarms, show a compact selectable list and summarize each change rather than opening a chain of dialogs. Restore only the selected, still-existing alarms' routine/sound, rebasing against their latest data. Do not overwrite later edits, resurrect deleted alarms, enable disabled alarms, or automatically change an already-started occurrence. Remove fallback/backups on deletion and preserve the original through repeated recovery attempts. If no paid customization was used, no restoration flow is needed.

## 6. Billing work and existing limitations

The preview is an app entitlement layered over the existing purchase system. It does not require a new product, billing provider, subscription offer, or account.

Keep `ProPlan.kt`, product IDs `yoga_alarm_pro` / `yoga_alarm_pro_lifetime`, base plans `monthly` / `yearly`, and lifetime option `yogaalarmprolifetime` unchanged. Keep the current prohibition on selling another plan to an active paid owner; subscription-to-lifetime switching is outside this change.

The repository's billing specification already identifies production gaps: `ClientPurchaseVerifier` performs no server verification, paid access is not persisted across an offline cold start, and refunds/renewals cannot be independently reconciled by this client. These are existing limitations, not features supplied by a trial counter.

- Add structured ownership-query, verification, and pending-payment states so loading/error/confirmed-empty are distinguishable. Checkout eligibility must be enforced both in `PlayBillingManager.purchase()` and the paywall: require resolved nonpaid ownership, a current usable offer, no active checkout, no verification in flight, and no pending Pro payment. A pending purchase is not ownership, but must prevent a second competing Pro checkout until it resolves.
- Keep a clear “Checking purchase…” state while the verifier is suspended; do not clear the busy guard merely because the store activity returned. Cancel/decline returns to a usable non-busy state. Resume/Restore during verification must not allow an older empty ownership snapshot to override a successful purchase.
- Do not interpret a failed query as expired ownership or consent to downgrade.
- Complete the already documented verification, trusted expiry/cache, acknowledgement, and refund/renewal reconciliation work before representing the production payment system as meeting that specification.
- Keep that production billing work in a separate change from trial/UI implementation so it can be reviewed and tested independently, but make paid offline-cache/expiry behavior a release dependency: buying during the preview must not make an offline cold start less capable than the free trial was. An “ever purchased” flag must not substitute for current trusted ownership.
- Retire the trial only alongside a recoverable paid-entitlement update. Verify purchase success, app termination, offline reopening, paid-through expiry, and later revocation. Do not use a cached initial purchase date to calculate subscription expiry.
- A public revenue experiment needs trustworthy purchase outcomes. Client checkout clicks, restoration callbacks, and cached prices are not authoritative realized revenue.

Google's integration guide describes verification before granting purchased content; its security guidance recommends backend verification/acknowledgement. Preserve that distinction from the local, non-purchased trial. References: [Play integration](https://developer.android.com/google/play/billing/integrate), [purchase security](https://developer.android.com/google/play/billing/security).

## 7. Measurement and experiment

### Add measurement deliberately

There is no analytics SDK in the current app. A local counter is sufficient for the product but cannot measure population retention or revenue per experiment arm. Measurement therefore needs its own implementation step.

Recommended starting option: Firebase Analytics for funnel events, with a persisted local experiment assignment; Remote Config is not necessary for the first fixed 50/50 experiment. Connect the project's Firebase configuration during that work. `app/src/main/AndroidManifest.xml` currently explicitly removes `INTERNET` and `ACCESS_NETWORK_STATE`; revise those merger rules when network-backed measurement/verification is introduced and inspect the merged release manifest. Dependencies alone will not make delivery work. Do not add multiple analytics products or a custom event pipeline. Setup reference: [Firebase Analytics for Android](https://firebase.google.com/docs/analytics/get-started?platform=android).

Before enabling collection, audit automatic SDK collection as well as custom events and update the actual published privacy policy, in-app disclosure where appropriate, and store declarations. Existing `docs/google-play-policy-declarations.md` explicitly says no analytics and no collected data; those claims cannot remain unchanged. Configure collection/consent and retention for the chosen deployment. Keep camera frames, landmarks, pose sequences, alarm names/times, and raw purchase tokens out of analytics. Do not call a persistent installation identifier anonymous in the privacy text; it is pseudonymous.

Use a small event set with experiment/version and entry point attached where relevant:

| Event | Purpose |
|---|---|
| Experiment assigned | Stable denominator and variant. |
| First alarm saved / first real alarm completed | Activation funnel. |
| Alarm day completed | Repeat-use counts and day-based retention; no pose content or scheduled time. |
| Trial milestone presented | Verify actual exposure at 2/4/5. |
| Trial customization saved | Record only coarse feature categories/count flags, not pose identities or routine contents, to learn whether users experienced paid value. |
| Paywall viewed | Entry point: manual, locked feature, second completion, expiry. |
| Offer unavailable | Separate a failed catalog/connection from an actionable paywall impression. |
| Plan selected / checkout started / checkout outcome | Distinguish intent, cancellation, pending payment, failure, and accepted purchase. |
| Fallback confirmed / free transition applied | Distinguish upfront acknowledgment from the actual automatic expiry; measure subsequent use. |
| Purchase/refund/renewal reconciled | Authoritative monetary outcomes from the billing/reporting work, separate from client funnel events. |

Where money is recorded, retain numeric amount, ISO currency, plan/base-plan ID, and transaction identity in the appropriate billing/reporting system. Do not parse formatted display prices or count a restored subscription as a new sale. Repeated ownership queries must not generate new revenue. Monthly/yearly use the same product ID, so product ID alone cannot distinguish the plan. Attribute the verified transaction to the stored experiment cohort and retain that association for renewals/refunds. Keep one canonical monetary source: automatic SDK purchase events and custom checkout events must not both add revenue. Restoring on another installation must not reattribute an old transaction to its new cohort.

If authoritative cohort-level revenue is not yet available, report client-observed purchases and estimated gross revenue as provisional. Reconcile totals with store reports; do not label them net revenue or claim refunds were measured by variant. Do not sum different currencies without a stated conversion method.

### Run one comparison

- Control: immediate free access, existing restrictions, optional purchases.
- Treatment: five-day usage-based preview plus the milestone/transition flow in this plan.
- Apply the same updated lifetime-first paywall, prices, and general purchase entry points to both. The experiment tests the preview experience, not a simultaneous price/layout change.
- Assign once before onboarding, persist locally, and retain assignment across upgrades. Existing users/purchasers are outside the new-user experiment. Define pre-existing purchase exclusion by a purchase that predates enrollment, not simply an entitlement becoming non-null after assignment. Purchases caused by the treatment stay in its numerator and denominator. Late restoration of a genuinely pre-existing purchase can establish ineligibility only under the same prespecified rule in both arms. If historical eligibility cannot be established reliably, report it as unknown rather than removing successful buyers opportunistically. Reinstalls may create a new observed installation; acknowledge that limitation.
- Record all measurable eligible first launches in the denominator, including people who never set an alarm or open a paywall. Report data-collection coverage; observed first launches are not identical to store downloads.
- Primary commercial readout: 60-day realized revenue per assigned first launch, after attributable refunds and store fees once trustworthy reporting exists. Continue tracking at 90/180 days for renewals and delayed purchases.
- Supporting metrics: paid conversion and plan mix; first-alarm success; days with completed alarms in the first 7/30 days; activity after expiry; payment failures and refunds. Alarm delivery/stop behavior is a release guardrail, not just a monetization metric.
- Define retention windows in advance, for example at least one real completion during days 7–13 and 30–36, alongside total completed days. Do not label these exact-day D7/D30 retention.
- Do not compare only trial finishers or paywall viewers: those are selected subsets. Do not use uninstalls as a directly available client event.
- At low volume, use the first cohorts to find UX problems. Report uncertainty; do not declare a modest uplift from a handful of purchases or change assignment mid-experiment.

The trial must demonstrate paid value without adding onboarding friction. Use early diagnostics to check whether users save any paid customization and whether the fallback explanation reduces first-alarm setup. If almost everyone keeps the free default, improve the existing customization entry points/copy before adding more offers or extending the trial. More paywall views alone are not success. Do not build new Premium features to compensate for an unmeasured funnel.

Separate internal product validation from commercial conclusions: local trial testing need not wait for a revenue warehouse, but claiming net revenue uplift does require the reporting described above. At low volume, present purchase/gross-revenue signals as provisional and avoid broad acquisition spending on that basis. Keep the fixed experiment small and reversible; instrument only the information needed to evaluate this hypothesis.

### Later price experiment

Only after deciding the trial flow, compare $29.99 versus $39.99 lifetime at unchanged monthly/yearly prices. This requires actual store-configured prices/offers and verified eligibility, not different hardcoded labels for the same checkout. Work out the catalog/offer mapping in a separate proposal; do not silently replace the permanent lifetime product. Measure total revenue across all plans and refunds because customers can substitute yearly for lifetime.

## 8. Files to add, change, and leave alone

| File | Planned responsibility |
|---|---|
| New `model/ProTrial.kt` | Pure local trial state/counting/milestone rules. |
| New `data/ProTrialStore.kt` | Enrollment, persistence, retirement, prompt markers. |
| `ui/YogaAlarmRoot.kt` | Compose state wiring, feature access, safe prompt sequencing, existing real-completion hook. |
| `ui/RoutineRoute.kt` and `ui/RoutineViewModel.kt` | Stable completion time, occurrence-aware reset/recovery, optional milestone content; preserve presentation/window behavior. |
| `MainActivity.kt`, `alarm/AlarmReceiver.kt`, `alarm/AlarmForegroundService.kt`, `alarm/AlarmScheduler.kt`, `alarm/SnoozeReceiver.kt` | Propagate occurrence identity and retain the started configuration through reattachment/snooze; never wait for network. Preserve current notification Start work. |
| `alarm/StopAlarmReceiver.kt`, `alarm/BootReceiver.kt` | Review terminal/cancellation/reboot cleanup for the small persisted occurrence record; preserve existing scheduling policy. |
| `ui/RoutineCameraScreen.kt` | Optional completion card/remaining allowance without obscuring Done; use home if the card crowds the celebration. |
| `ui/AlarmHomeScreen.kt` | Trial status and purchase entry distinct from paid access. |
| `ui/Onboarding.kt` | Trial explanation using existing layout. |
| `ui/AlarmEditorScreen.kt` | Effective access, guided catalog filtering, locked draft/save behavior, review of free replacements. |
| `ui/AppInfoSheets.kt` | Lifetime-first paywall, context-specific benefits, trial Settings status, free continuation/restoration preview. |
| `data/AlarmStore.kt` | Persist upfront fallbacks/original customization; serialize field-specific mutations and automatic expiry; retain a bounded occurrence snapshot/outcome using existing storage. Split a small occurrence store only if needed by multiple lifecycle consumers, not as a new repository framework. |
| `data/WakeProgressStore.kt` | Accept the same captured completion time through its existing parameter; retain independent streak semantics. |
| `model/AlarmConfig.kt` | Small pure free-conversion function if useful to share with preview/persistence/tests; do not change catalog/defaults. |
| `billing/PlayBillingManager.kt` | Ownership-resolution status; measurement integration without treating trials as purchases. |
| New focused tests | Trial state/store, free conversion/backups, completion/prompt sequencing, ownership resolution. |
| `app/src/main/AndroidManifest.xml`, build/config, and one analytics integration file | Network permission merger changes, analytics project wiring, collection settings, merged release manifest verification, and actual event-delivery checks. Preserve `allowBackup=false`. |
| `docs/play-billing-setup.md` | Replace “no trial,” separate paid/effective access, describe lifetime default, correct catalog counts. |
| `docs/google-play-monetization-guide.md` | Update offer presentation and explain no store trial is configured. |
| `docs/google-play-policy-declarations.md` | Correct stale billing descriptions and collection claims when the corresponding code ships. |
| `IOS_SYNC.md` | Update existing pending billing entry and add/extend trial behavior entry as appropriate during implementation. |

Keep pose detection thresholds, watchdog policy, emergency-pass rules, streak calculation, asset catalog, and subscription switching outside this change. The occurrence identity/snapshot work is a narrowly scoped safety prerequisite for automatic future-alarm conversion and lifecycle recovery; do not turn it into a scheduler rewrite or change alarm timing. Reuse any equivalent fixes already completed by ongoing alarm work.

Remove/replace assumptions, not working functionality: paid-only feature gates, trial-blind Settings/home labels, the yearly default selection, and free-only guided catalog filters for eligible users. Keep restore/retry/legal terms/free alarm functionality.

## 9. Validation and acceptance criteria

Use focused JUnit/Robolectric tests already supported by the project, scoped Kotlin compilation, and device/Internal Testing checks. No full suite/build is needed for the planning document.

### Automated checks

- Trial count: five distinct dates; repeated same-day completions; skipped days; demos/practice/stops; maximum count; date/timezone changes; stable completion replay after midnight.
- Persistence: restart/rotation; duplicate callback; migration before default writes; deletion does not reset; paid restoration retires trial; former purchaser does not regain trial.
- Access: trial unlocks all existing gates but permits eligible checkout; unresolved ownership, verification, pending payment, or active paid access block duplicate checkout; a query error never revokes valid cached paid access; local trial expiry still works offline.
- Prompt sequencing: milestones fire once; stale offers are superseded; purchases suppress queued prompts; missing prices defer the commercial milestone; new alarm interrupts in-app commerce; test routines do not display milestones; already-open Google Play callbacks cannot disrupt an alarm.
- Conversion: upfront confirmation is required for paid trial configurations; one/two/three/ten-pose routines, all-paid/mixed/free poses, all durations, paid/free sounds, disabled alarms, original-backup idempotence, delete/restore behavior, unchanged scheduling fields, and missing/corrupt fallback recovery.
- Storage races: one-shot auto-disable versus fifth completion; reordered/deleted alarms versus transition; stale editor Save versus free conversion; purchase success versus expiry; partial-write recovery. Every ordering preserves alarms and gives a consistent access result.
- Session recovery: completed occurrence after process death does not re-ring; next occurrence of the same recurring alarm is fresh; notification taps retain the current occurrence; snooze across expiry retains its snapshot; obsolete occurrence records do not block future alarms.
- Editor/catalog: entitlement/catalog changes preserve selection and unsaved unrelated edits; shrinking a routine closes invalid pickers; partial product loading never changes the selected billed plan under the user's finger.
- Measurement: assignments persist; late pre-existing restoration is distinguished from a new purchase; restore and repeated callbacks do not count sales twice; automatic/custom analytics events do not double revenue.
- Existing regressions: run relevant `RoutineViewModelTest`, alarm scheduler/notification tests if lifecycle paths are touched, and `PurchaseVerifierTest` if billing behavior changes.

### Device checks

1. Fresh trial/control installs and an upgraded existing installation.
2. Complete five real alarms on distinct dates using test harness controls; verify normal same-day alarms still count once. Keep harness overrides out of shipped user UI.
3. Purchase each plan before expiry and from the expiry paywall; test cancel, declined, pending, restore, delayed restore, and unavailable catalog.
4. Expire on the lock screen; Done, Back, rotation, screen off, process death, and a new alarm arriving must never trigger a new paywall over an alarm. Test an alarm arriving during an already-open Google Play checkout separately.
5. Accept a visible fallback during trial setup, complete the fifth day, and close/swipe away the expiry offer. The next normal occurrence must use exactly that fallback. No second acknowledgment is required and no Pro grace is created by dismissing the offer.
6. Verify disabled and one-shot auto-disabled alarms remain disabled, repeat schedules are unchanged, and already ringing/snoozed occurrences keep their original snapshot while later occurrences use free settings.
7. Purchase after conversion; offer restoration without overwriting later free edits automatically.
8. Verify onboarding practice still consumes zero days and remains structurally correct with Pro catalog choices.
9. Check small displays, landscape, large text, accessibility labels, and scrolling around the fixed purchase area.
10. Validate analytics delivery/deduplication separately from alarm behavior. No network failure may delay ringing, completion, or local trial persistence.
11. Purchase during the trial, terminate the app, enable airplane mode, and reopen: valid paid access must remain usable. Repeat with expired subscription cache and later verified revocation; trial must not restart.
12. Kill/recreate the process on the completed screen; the dismissed alarm must not re-ring. Leave a completed recurring alarm open until its next delivery; it must start a fresh routine, not reuse COMPLETE.
13. Test notification Start and Snooze, denied camera permission, in-flight permission requests, notification reattachment, and reboot. Preserve the current Start-to-camera behavior.
14. Keep a duration picker open on pose 10 while access/configuration transitions to three free poses; no crash, stale Pro save, or loss of unrelated unsaved edits. Exercise drag/reorder and sound preview interruption too.
15. Observe partial price loading, late lifetime price, full catalog failure, return from Play, verification delay, pending lifetime then subscription tap, and retry. Never change the purchased plan silently or consume the only automatic offer on an unusable sheet.

## 10. Delivery order and rollout

1. **Safety foundations:** reproduce the identified lifecycle risks, then implement only the necessary occurrence identity/snapshot/completed-outcome handling and current-data mutation safeguards. Preserve ongoing notification work. Add focused regression tests before wiring trial expiry into these paths.
2. **State, migration, and payment guards:** trial model/store, enrollment, structured ownership/verification/pending state, and serialized local completion transitions. No trial UI enabled yet. In the separate billing workstream, establish trusted paid offline access before production rollout.
3. **Feature access and setup:** editor gates, guided choices, optional customization entry, upfront fallback confirmation, and status in home/Settings. Validate active trial and paid-user behavior.
4. **Completion, offers, and expiry:** consistent completion timestamp, milestone sequencing, stable lifetime-first paywall, atomic automatic fallback, editor reconciliation, and explicit later restoration. Keep rollout disabled until the full flow passes focused/device checks.
5. **Measurement and payment readiness:** configure network permissions/analytics and accurate collection disclosures; integrate reporting with the separately specified production billing work. Verify first-launch denominator, transaction attribution, and purchase-then-offline behavior. Internal product/UX testing can proceed before complete commercial reporting exists.
6. **Internal Testing:** run the full scenario list above using real store checkout/test accounts. Update documentation and iOS sync entries alongside implemented behavior.
7. **Controlled rollout:** start with internal testers, then a small production rollout with the persisted 50/50 assignment. Advance only after alarm and checkout regressions are clear. Record app version/cohort start in reporting.
8. **Evaluate:** examine activation/expiry issues early, then wait for matched 60-day cohorts for the first commercial readout. Keep trial and price changes separate.

Rollback must stop new enrollment and automatic prompts without deleting progress, original configurations, or purchases. Honor already granted remaining trial days and their confirmed fallback policy unless a safety issue requires temporarily preserving access. Do not silently reset converted alarms or restore Pro routines. An app update is sufficient for an initial rollout; do not describe a remote kill switch unless one is actually implemented.

During implementation, update the pending billing sync entry for changed defaults/access semantics and use the next available SYNC ID for genuinely new trial behavior if not already covered. Preserve iOS implementation notes, reference Android paths and defaults/edge cases, and leave the commit placeholder until a real commit exists. Do not edit the iOS repository from this project.

## 11. Review scope and workspace state

The 2026-10-28 review inspected the plan, trial-relevant editor/onboarding/home/paywall flows, billing/catalog/verification logic, alarm receiver/scheduler/service and notification paths, routine state/lifecycle, alarm and wake-progress persistence, manifest, existing tests, billing documentation, and current alarm-related diffs. Two independent read-only reviews covered billing and alarm lifecycle. Findings were integrated into this document; no application code was edited.

At review time, pre-existing uncommitted changes affected `IOS_SYNC.md`, `MainActivity.kt`, `alarm/AlarmForegroundService.kt`, `alarm/AlarmScheduler.kt`, `ui/RoutineRoute.kt`, `ui/YogaAlarmRoot.kt`, and `app/src/test/java/com/lurremcfly/yogaalarm/alarm/AlarmNotificationTest.kt`. They implement notification Start-to-camera behavior and must be preserved. Re-read them before implementation rather than applying this plan against an older checkout.

Validation for this review is static source/diff inspection plus a scoped document whitespace check. No builds, app tests, or device experiments were run for a documentation-only change. Process-death, next-occurrence, lock-screen, and store-interruption findings remain explicit runtime validation requirements; this review cannot establish the absence of all bugs or prove a revenue uplift.

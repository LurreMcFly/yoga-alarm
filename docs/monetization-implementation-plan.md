# Yoga Alarm monetization implementation plan

Status: proposed implementation plan; no product behavior implemented by this document.
Prepared: 2026-10-27. Android is the reference implementation.

## 1. Product decisions

Implement an app-managed Pro preview for five successful alarm-completion days, followed by the existing permanent free tier. No card, store trial, subscription enrollment, calendar deadline, consecutive-day requirement, or automatic charge.

- Use the existing product name **Pro** throughout the UI. Do not introduce Premium as a second product name.
- A qualifying completion is a genuinely fired alarm completed through yoga, on a local calendar date not previously counted toward the preview. Any time of day qualifies; “morning” is marketing language, not a time restriction.
- Practice, full-routine tests, single-pose tests, snoozes, missed alarms, normal stops, emergency stops, and technical stops do not consume a day. A recovered camera error followed by a successful real completion does count.
- Unlock Pro during initial alarm setup. Keep the default three-pose routine so the user need not customize anything.
- Keep USD reference prices at $7.99 monthly, $19.99 yearly, and $29.99 lifetime. All displayed amounts must still come from the store in local currency.
- Make lifetime the first and initially selected available option, then yearly, then monthly. Monthly remains visible and accessible.
- Keep purchases available throughout the preview. Paid ownership always takes precedence and suppresses trial offers.
- After completion two, show one optional inline purchase invitation. After completion four, show the one-day-remaining notice. After completion five, offer the paywall once it is safe to do so.
- Declining payment never removes alarms or streaks and never blocks dismissal. Do not silently change an already configured alarm at expiry.
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
| `ui/RoutineRoute.kt` | Finishes the alarm, invokes completion, and provides a ten-second completion presentation. | Keep this lifecycle intact. Attach stable completion date/progress and defer commerce until dismissal/unlock. |
| `data/AlarmStore.kt` | Stores the complete alarm configuration in local preferences. | Add explicit conversion to free settings and preservation of original customization. Never normalize on ordinary load. |
| `data/WakeProgressStore.kt` | Records local completion dates, streaks, schedule history, and emergency passes. | Keep independent of trial accounting; the trial cannot be reconstructed from streak/history. |
| Analytics | No analytics dependency/integration found in the Android app. | Add a defined measurement destination before calling this a measurable revenue experiment. |

Source paths in this document are relative to `app/src/main/java/com/lurremcfly/yogaalarm/` unless otherwise stated.

The current catalog has **nine poses**, four free: Mountain, Warrior II, Tree, and Squat. It has ten sounds, two free: Sunbird Morning Call and Morning Temple Call. Existing documentation still mentions three free/eight total poses; correct that drift when implementing.

## 3. Local state and access rules

Add a small `model/ProTrial.kt` model with pure transition/counting logic and a `data/ProTrialStore.kt` using the existing SharedPreferences approach. Do not add Room, accounts, or a general entitlement framework for this feature.

Persist only the necessary state:

| Field | Purpose |
|---|---|
| Schema/experiment version | One-time migration and stable experiment interpretation. |
| Enrollment | Existing/ineligible, control, or trial variant; assigned once. |
| Counted local dates | Set of at most five ISO dates; derive completed/remaining counts. |
| Trial retired by purchase | Prevent an unused preview from restarting after subscription expiry/refund. This is not proof of current paid ownership. |
| Pending milestone | Offer after two, warning after four, or expiry after five. |
| Presented milestone markers | Avoid repeated automatic presentations after rotation/relaunch. |
| Free transition state | Not needed, awaiting review, or applied. |

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
| Fifth completion | New paid customization becomes locked; existing configured alarms stay intact until explicit review. |
| Purchase/restoration confirms paid ownership | Retire trial, clear trial prompts, cancel unapplied downgrade, unlock features. |
| Pending or canceled purchase | No paid entitlement; continue the independently valid trial/free state. |
| Known purchaser later loses paid access | Do not restart a trial; retain existing subscription-expiry handling unless separately changed. |
| Billing unavailable | No inference of subscription expiry, no automatic downgrade, no interference with alarms. |

The local trial should work offline after enrollment. Ownership checks should never block alarm delivery/completion. Suppress automatic purchase/downgrade prompts while ownership is unresolved; recheck pending prompts once ownership is resolved. Keep explicit Retry/Restore actions available.

Persist a stable completion date in the existing completion presentation before dispatching its callback. Re-delivery uses that date rather than reading a new date after midnight. The persisted date set makes callback repetition harmless. Reuse the existing completion guard; do not add occurrence IDs throughout the scheduler solely for this feature.

Use local dates consistently with existing streaks. Timezone travel may allow another distinct calendar date; accept that rather than adding clock tampering defenses. Deleting an alarm or clearing history must not reset the preview. Reinstall/clear-data resets are an accepted limitation of an account-free local preview; honor restored trial preferences if Android restores them.

### Enrollment migration

Determine existing use before constructors/load functions write default preferences. Check existing alarm, onboarding, and wake-progress data; existing users are excluded from initial assignment. An old installation with no persisted use can be treated as a new first-use installation. Document this definition rather than claiming perfect reinstall detection.

Record observed paid ownership and retire any assigned trial when purchases restore. Never display a preview as a replacement for an owned purchase. Preserve current user data and previously paid access. In tests, explicitly exercise restore arriving after the initial screen.

## 4. Screen-by-screen changes

### Onboarding and alarm editor

Change `Onboarding.kt`, `AlarmEditorScreen.kt`, and the root wiring.

- Add a compact explanation before/while configuring the first alarm: “Try all Pro features for 5 successful alarm days. No payment details. Then continue free or keep Pro.”
- Keep the existing permission flow and tour; do not insert a compulsory paywall.
- Trial users can choose all nine poses and ten sounds. Remove the unconditional free-only filters during guided setup when effective Pro access is present.
- Keep the guided sequence at three poses so its Pose 1/2/3 targets and practice flow remain valid. Explain that pose count can be changed after setup; immediately expose the normal editor afterward. Do not redesign the tour around arbitrary routine lengths.
- Outside the tour, trial users can select 1–10 poses and the existing seven durations: 5, 10, 15, 20, 30, 45, 60 seconds.
- Maintain free restrictions after trial: exactly three slots, four free pose choices, 20 seconds per pose, two free sounds.
- Preserve unlimited alarms, time/day/name editing, snooze, vibration, stop controls, and streaks for free users.
- Audit save/reopen behavior, not only disabled controls: expired users must not create a new paid configuration through a stale editor draft. Existing configurations awaiting review are preserved as described below.

### Home and Settings

Change `AlarmHomeScreen.kt` and `AppInfoSheets.kt`.

- Paid: existing paid-status display; no trial prompts.
- Active trial: “Pro preview · 3 alarm days left,” with an optional “Keep Pro” action.
- Trial exhausted, review pending: “Your Pro preview is complete,” with purchase and “Review free settings” actions.
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
| 4 | Inline notice: “1 Pro alarm day left. After that, keep Pro or review your free routine.” |
| 5 | Preserve the success screen. After Done and leaving alarm presentation, show the expiry paywall once. |

If the app closes or the screen locks, retain the pending milestone. Show the relevant latest milestone on the next safe foreground session; do not replay a backlog of earlier cards. Recompute against current ownership before presentation. Mark a milestone presented when it actually appears, not when merely queued.

The ten-second completion grace is for the existing alarm presentation, not a paywall countdown. Do not extend it, keep the device awake for commerce, or put checkout over the lock screen. If a new alarm arrives, dismiss/defer monetization UI immediately using the existing fired-alarm priority.

### Paywall

Update `ProPaywallSheet` in `AppInfoSheets.kt`; reuse the existing expanded sheet, scrolling benefits and fixed purchase area.

- Display lifetime, yearly, monthly in that order. Default to lifetime when available; fall back to yearly, then monthly. Preserve a user-selected available plan across price refreshes; do not jump selection while checkout is open.
- Retain localized store prices, exact billing period, renewal terms, purchase/subscribe distinction, restore, retry, unavailable plans, and paid-owner duplicate purchase blocking.
- Keep existing catalog IDs and base plans. Do not configure a store free trial or pick promotional offers.
- Trial headline: “Keep your morning routine yours.” Expiry headline: “Keep your custom routine.” Explain benefits without implying streak loss or alarm failure.
- During the trial, closing returns to the remaining preview. At expiry, show a visible “Continue free” action that opens the settings review when necessary. Close/swipe/back does not count as consent to change alarm settings.
- Show the differences that actually apply to the user's saved alarms; if none use paid customization, say that their current routine can continue free unchanged.
- Purchase success suppresses queued offers and exits the transition flow. Pending payment clearly states that payment is awaiting confirmation; it does not erase valid trial access or claim paid access.

## 5. Explicit transition to free

This is the main correctness risk. `AlarmReceiver`, `AlarmForegroundService`, and the camera route use saved configurations. Keep that reliable execution path independent of trial/billing/network state.

At five completions, lock new paid edits but do not rewrite alarms in the background. Existing alarms may continue using their saved customization until the user confirms a transition. This deliberate grace can last indefinitely if they never review it; preventing that would require changing the agreed no-surprise policy.

When the user selects Continue free:

1. Identify affected saved alarms, including disabled ones. Show current versus proposed routine/sound changes.
2. Offer valid free replacements and allow choosing among the four free poses and two free sounds before confirmation.
3. Default conversion: take the first three routine slots; keep free poses, replace paid poses with the corresponding Mountain/Warrior II/Tree default; pad missing slots from those defaults. Set every hold to 20 seconds. Keep a free sound or replace a paid sound with Sunbird Morning Call. Preserve already allowed duplicate poses.
4. Preserve IDs, names, ordering, enabled state, time, repeat days, sound/vibration flags, snooze settings, and stop-button settings.
5. Confirm with explicit wording such as “Use these free settings.” Then persist the original customization and replacement configuration together in one AlarmStore preference edit. Store a durable conversion marker so repeating confirmation after a crash cannot overwrite the original backup.
6. Mark the trial's free transition applied only after that persisted conversion succeeds. Do not call the normal save-and-enable editor callback for a batch downgrade; it could change enabled state. Because the schedule is unchanged and the receiver loads configuration by ID, avoid canceling/recreating pending alarms solely for routine/sound replacement.

Do not apply conversion while an alarm/routine is active or snoozed. If another alarm begins while the review is open, defer the review and preserve the active configuration. Exercise the near-trigger race on device.

Retain original routine and sound only, keyed by alarm ID, rather than copying unrelated scheduling/streak data. After a later purchase, offer restoration explicitly. Do not automatically overwrite free edits the user made in the meantime. Remove a backup when its alarm is deleted.

Until review is confirmed, existing alarm time/day/name edits may preserve their original customization. New alarms use valid free settings; attempts to change paid customization open the paywall. Do not silently normalize a legacy/trial routine just because its editor is opened. Take special care with the carousel's selection effect: displaying a locked saved pose must not overwrite it with a different free pose.

If every saved configuration already satisfies the free rules, Continue free simply acknowledges the transition. No backups or scheduling work are needed.

## 6. Billing work and existing limitations

The preview is an app entitlement layered over the existing purchase system. It does not require a new product, billing provider, subscription offer, or account.

Keep `ProPlan.kt`, product IDs `yoga_alarm_pro` / `yoga_alarm_pro_lifetime`, base plans `monthly` / `yearly`, and lifetime option `yogaalarmprolifetime` unchanged. Keep the current prohibition on selling another plan to an active paid owner; subscription-to-lifetime switching is outside this change.

The repository's billing specification already identifies production gaps: `ClientPurchaseVerifier` performs no server verification, paid access is not persisted across an offline cold start, and refunds/renewals cannot be independently reconciled by this client. These are existing limitations, not features supplied by a trial counter.

- Add an explicit successful-ownership-query indicator so loading/error/confirmed-empty are distinguishable.
- Do not interpret a failed query as expired ownership or consent to downgrade.
- Complete the already documented verification, trusted expiry/cache, acknowledgement, and refund/renewal reconciliation work before representing the production payment system as meeting that specification.
- Keep that production billing work in a separate change from trial/UI implementation so it can be reviewed and tested independently.
- A public revenue experiment needs trustworthy purchase outcomes. Client checkout clicks, restoration callbacks, and cached prices are not authoritative realized revenue.

Google's integration guide describes verification before granting purchased content; its security guidance recommends backend verification/acknowledgement. Preserve that distinction from the local, non-purchased trial. References: [Play integration](https://developer.android.com/google/play/billing/integrate), [purchase security](https://developer.android.com/google/play/billing/security).

## 7. Measurement and experiment

### Add measurement deliberately

There is no analytics SDK in the current app. A local counter is sufficient for the product but cannot measure population retention or revenue per experiment arm. Measurement therefore needs its own implementation step.

Recommended starting option: Firebase Analytics for funnel events, with a persisted local experiment assignment; Remote Config is not necessary for the first fixed 50/50 experiment. Connect the project's Firebase configuration during that work. Do not add multiple analytics products or a custom event pipeline. Setup reference: [Firebase Analytics for Android](https://firebase.google.com/docs/analytics/get-started?platform=android).

Before enabling collection, audit automatic SDK collection as well as custom events and update the actual published privacy policy, in-app disclosure where appropriate, and store declarations. Existing `docs/google-play-policy-declarations.md` explicitly says no analytics and no collected data; those claims cannot remain unchanged. Configure collection/consent and retention for the chosen deployment. Keep camera frames, landmarks, pose sequences, alarm names/times, and raw purchase tokens out of analytics. Do not call a persistent installation identifier anonymous in the privacy text; it is pseudonymous.

Use a small event set with experiment/version and entry point attached where relevant:

| Event | Purpose |
|---|---|
| Experiment assigned | Stable denominator and variant. |
| First alarm saved / first real alarm completed | Activation funnel. |
| Alarm day completed | Repeat-use counts and day-based retention; no pose content or scheduled time. |
| Trial milestone presented | Verify actual exposure at 2/4/5. |
| Paywall viewed | Entry point: manual, locked feature, second completion, expiry. |
| Plan selected / checkout started / checkout outcome | Distinguish intent, cancellation, pending payment, failure, and accepted purchase. |
| Free transition confirmed | Conversion to free and its effect on subsequent use. |
| Purchase/refund/renewal reconciled | Authoritative monetary outcomes from the billing/reporting work, separate from client funnel events. |

Where money is recorded, retain numeric amount, ISO currency, plan/base-plan ID, and transaction identity in the appropriate billing/reporting system. Do not parse formatted display prices or count a restored subscription as a new sale. Repeated ownership queries must not generate new revenue. Monthly/yearly use the same product ID, so product ID alone cannot distinguish the plan. Attribute the verified transaction to the stored experiment cohort and retain that association for renewals/refunds.

If authoritative cohort-level revenue is not yet available, report client-observed purchases and estimated gross revenue as provisional. Reconcile totals with store reports; do not label them net revenue or claim refunds were measured by variant. Do not sum different currencies without a stated conversion method.

### Run one comparison

- Control: immediate free access, existing restrictions, optional purchases.
- Treatment: five-day usage-based preview plus the milestone/transition flow in this plan.
- Apply the same updated lifetime-first paywall, prices, and general purchase entry points to both. The experiment tests the preview experience, not a simultaneous price/layout change.
- Assign once before onboarding, persist locally, and retain assignment across upgrades. Existing users/purchasers are outside the new-user experiment. Reinstalls may create a new observed installation; acknowledge that limitation.
- Record all measurable eligible first launches in the denominator, including people who never set an alarm or open a paywall. Report data-collection coverage; observed first launches are not identical to store downloads.
- Primary commercial readout: 60-day realized revenue per assigned first launch, after attributable refunds and store fees once trustworthy reporting exists. Continue tracking at 90/180 days for renewals and delayed purchases.
- Supporting metrics: paid conversion and plan mix; first-alarm success; days with completed alarms in the first 7/30 days; activity after expiry; payment failures and refunds. Alarm delivery/stop behavior is a release guardrail, not just a monetization metric.
- Define retention windows in advance, for example at least one real completion during days 7–13 and 30–36, alongside total completed days. Do not label these exact-day D7/D30 retention.
- Do not compare only trial finishers or paywall viewers: those are selected subsets. Do not use uninstalls as a directly available client event.
- At low volume, use the first cohorts to find UX problems. Report uncertainty; do not declare a modest uplift from a handful of purchases or change assignment mid-experiment.

### Later price experiment

Only after deciding the trial flow, compare $29.99 versus $39.99 lifetime at unchanged monthly/yearly prices. This requires actual store-configured prices/offers and verified eligibility, not different hardcoded labels for the same checkout. Work out the catalog/offer mapping in a separate proposal; do not silently replace the permanent lifetime product. Measure total revenue across all plans and refunds because customers can substitute yearly for lifetime.

## 8. Files to add, change, and leave alone

| File | Planned responsibility |
|---|---|
| New `model/ProTrial.kt` | Pure local trial state/counting/milestone rules. |
| New `data/ProTrialStore.kt` | Enrollment, persistence, retirement, prompt markers. |
| `ui/YogaAlarmRoot.kt` | Compose state wiring, feature access, safe prompt sequencing, existing real-completion hook. |
| `ui/RoutineRoute.kt` | Stable completion date and optional milestone content; preserve service/window lifecycle. |
| `ui/RoutineCameraScreen.kt` | Optional completion card/remaining allowance without obscuring Done; inspect current edits before touching. |
| `ui/AlarmHomeScreen.kt` | Trial status and purchase entry distinct from paid access. |
| `ui/Onboarding.kt` | Trial explanation using existing layout. |
| `ui/AlarmEditorScreen.kt` | Effective access, guided catalog filtering, locked draft/save behavior, review of free replacements. |
| `ui/AppInfoSheets.kt` | Lifetime-first paywall, trial Settings status, free continuation/review sheet. |
| `data/AlarmStore.kt` | Preserve original customization and apply confirmed free conversion idempotently. |
| `model/AlarmConfig.kt` | Small pure free-conversion function if useful to share with preview/persistence/tests; do not change catalog/defaults. |
| `billing/PlayBillingManager.kt` | Ownership-resolution status; measurement integration without treating trials as purchases. |
| New focused tests | Trial state/store, free conversion/backups, completion/prompt sequencing, ownership resolution. |
| Build/config and one analytics integration file | Only in measurement workstream, after configuring the analytics destination. |
| `docs/play-billing-setup.md` | Replace “no trial,” separate paid/effective access, describe lifetime default, correct catalog counts. |
| `docs/google-play-monetization-guide.md` | Update offer presentation and explain no store trial is configured. |
| `docs/google-play-policy-declarations.md` | Correct stale billing descriptions and collection claims when the corresponding code ships. |
| `IOS_SYNC.md` | Update existing pending billing entry and add/extend trial behavior entry as appropriate during implementation. |

Keep pose detection thresholds, alarm reliability/watchdog behavior, emergency passes, streak accounting, asset catalog, and subscription switching outside this change. No refactor of the alarm scheduler or purchase verifier solely to support trial counting.

Remove/replace assumptions, not working functionality: paid-only feature gates, trial-blind Settings/home labels, the yearly default selection, and free-only guided catalog filters for eligible users. Keep restore/retry/legal terms/free alarm functionality.

## 9. Validation and acceptance criteria

Use focused JUnit/Robolectric tests already supported by the project, scoped Kotlin compilation, and device/Internal Testing checks. No full suite/build is needed for the planning document.

### Automated checks

- Trial count: five distinct dates; repeated same-day completions; skipped days; demos/practice/stops; maximum count; date/timezone changes; stable completion replay after midnight.
- Persistence: restart/rotation; duplicate callback; migration before default writes; deletion does not reset; paid restoration retires trial; former purchaser does not regain trial.
- Access: trial unlocks all existing gates but permits checkout; paid ownership blocks duplicate purchase; pending payment does not create ownership; unresolved/error query never applies downgrade.
- Prompt sequencing: milestones fire once; stale offers are superseded; purchases suppress queued prompts; new alarm interrupts commerce; test routines do not display milestones.
- Conversion: one/two/three/ten-pose routines, all-paid/mixed/free poses, all durations, paid/free sounds, disabled alarms, original-backup idempotence, delete/restore behavior, unchanged scheduling fields.
- Existing regressions: run relevant `RoutineViewModelTest`, alarm scheduler/notification tests if lifecycle paths are touched, and `PurchaseVerifierTest` if billing behavior changes.

### Device checks

1. Fresh trial/control installs and an upgraded existing installation.
2. Complete five real alarms on distinct dates using test harness controls; verify normal same-day alarms still count once. Keep harness overrides out of shipped user UI.
3. Purchase each plan before expiry and from the expiry paywall; test cancel, declined, pending, restore, delayed restore, and unavailable catalog.
4. Expire on the lock screen; Done, Back, rotation, screen off, process death, and a new alarm arriving must never leave checkout obstructing an alarm.
5. Close the expiry paywall without consent; existing alarms retain their original poses/timings/sound. Confirm review later and verify the next alarm uses the displayed free settings.
6. Verify disabled alarms remain disabled, repeat alarms retain schedules, and snoozed/active alarms are not rewritten.
7. Purchase after conversion; offer restoration without overwriting later free edits automatically.
8. Verify onboarding practice still consumes zero days and remains structurally correct with Pro catalog choices.
9. Check small displays, landscape, large text, accessibility labels, and scrolling around the fixed purchase area.
10. Validate analytics delivery/deduplication separately from alarm behavior. No network failure may delay ringing, completion, or local trial persistence.

## 10. Delivery order and rollout

1. **State and migration:** trial model/store, ownership-resolution distinction, local tests. No trial UI enabled yet.
2. **Feature access and setup:** editor gates, guided choices, status in home/Settings. Validate active trial and paid-user behavior.
3. **Completion and offers:** stable completion counting, milestones, lifetime-first paywall. Keep automatic expiry activation off until step 4 is complete.
4. **Safe free transition:** review, backups, confirmed conversion, restore choice, and device regressions. This completes the functional preview.
5. **Measurement and payment readiness:** configure analytics and accurate collection disclosures; integrate reporting with the separately specified production billing work. Verify first-launch denominator and transaction attribution.
6. **Internal Testing:** run the full scenario list above using real store checkout/test accounts. Update documentation and iOS sync entries alongside implemented behavior.
7. **Controlled rollout:** start with internal testers, then a small production rollout with the persisted 50/50 assignment. Advance only after alarm and checkout regressions are clear. Record app version/cohort start in reporting.
8. **Evaluate:** examine activation/expiry issues early, then wait for matched 60-day cohorts for the first commercial readout. Keep trial and price changes separate.

Rollback must stop new enrollment and automatic prompts without deleting progress, original configurations, or purchases. Honor already granted remaining trial days. An app update is sufficient for an initial rollout; do not describe a remote kill switch unless one is actually implemented.

During implementation, update the pending billing sync entry for changed defaults/access semantics and use the next available SYNC ID for genuinely new trial behavior if not already covered. Preserve iOS implementation notes, reference Android paths and defaults/edge cases, and leave the commit placeholder until a real commit exists. Do not edit the iOS repository from this project.

## 11. Planning-time workspace state

At inspection, `IOS_SYNC.md` and `ui/RoutineCameraScreen.kt` already had uncommitted changes. This plan does not modify either. Re-read those changes before implementation and preserve them. No tests/builds were run because this change adds only this planning document.

# iOS Sync Queue

Android is the reference implementation for the iOS app.

This file tracks Android changes that affect cross-platform app behavior and therefore may need to be replicated in the iOS repository.

## Pending

### SYNC-013 — Five-second pose holds

Status: pending
Date: 2026-10-06
Android commit: <pending>

Changes:
- Add 5 seconds as the shortest selectable hold duration, followed by the existing 10, 15, 20, 30, 45, and 60 seconds. Each pose can independently use 5 seconds; saving, testing, and fired routines use that duration.
- Preserve the 20-second default and existing access rules: 20 seconds is free; every other duration, including 5 seconds, requires Pro. Locked duration choices open the existing paywall.
- Let the duration picker scroll so all seven options remain reachable on smaller screens and with larger text. Update Pro benefits to describe 5–60-second holds.
- Pose detection/hold qualification and the 3-second between-pose transition are unchanged.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmEditorScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AppInfoSheets.kt
- docs/play-billing-setup.md

iOS acceptance checks:
- Select 5 seconds, save/reopen an alarm, and test or run it. Confirm the hold countdown uses 5 seconds and existing pose qualification still applies.
- Confirm free users retain 20 seconds and tapping 5 seconds opens the paywall. Check all seven choices remain reachable with larger text.

### SYNC-012 — Compact home layout with larger-text support

Status: pending
Date: 2026-10-06
Android commit: <pending>

Changes:
- Place Settings as a muted-ink gear in the header with an accessible label and 48-point touch area, eliminating its separate top row. Use 4-point spacing between header actions. The header no longer contains an add-alarm button.
- Place a fixed, bottom-center “+ Add alarm” pill above the bottom safe area, 12 points from it. Use lime background, forest content, fully rounded corners, a minimum 56-point height, 24-point plus icon, 8-point icon/text spacing, 14-point bold label, and a subtle shadow. Tapping opens the existing new-alarm editor. Keep the action visible while the list scrolls.
- Keep GET PRO in the header for free users. For existing Pro members, remove the header Pro pill and provide Pro/subscription access through Settings. GET PRO uses a white outlined pill, 10-point bold text, 10-point horizontal/8-point vertical padding, and a minimum 48-point touch height. It grows with native text size.
- Keep the full “Your alarms” title readable at larger text/display sizes. Use a 22-point title below 300 points of available header width, 24 below 340, otherwise 28. The title must wrap rather than truncate to “Your ala…”. If the title and actions cannot fit, wrap the action group together onto a second row with 10-point spacing. Preserve native text scaling; do not disable Dynamic Type to make the row fit.
- Start the home list 40 points below the top safe area with 22-point side padding. Retain 14-point spacing between list items and add another 14 points after the streak card, for a 28-point gap to the following card. Use the fixed-height compact streak layout in SYNC-011 (updated 2026-10-10). Finish with 96-point bottom padding above the bottom safe area so the final alarm can scroll clear of the fixed add pill. Position the existing alarm-action popup 80 points above the bottom safe area so it clears the pill.
- Compact alarm cards to 20-point horizontal/18-point vertical padding. Alarm times use 48-point text with 54-point line height; retain the existing name, switch, weekday, divider, and pose summary. Use 12-point gaps before and after the divider. Editing and toggling behavior remains unchanged.
- Settings contains the privacy and subscription controls specified in SYNC-005 and SYNC-010.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AppInfoSheets.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt
- app/src/main/res/drawable/ic_add.xml
- app/src/main/res/drawable/ic_settings.xml

iOS acceptance checks:
- Check default and largest accessibility text sizes, larger display size, and a small phone. Confirm the complete title remains readable, controls reflow, and Settings remains reachable.
- Check both free and Pro states: GET PRO stays visible for free users; paid users reach Pro through Settings. Verify the compact streak/count and adaptive markers fit side by side within an 80-point card, and the last alarm remains clear of the bottom safe area.
- Confirm the bottom Add alarm pill remains visible while scrolling, opens a new alarm, and does not obscure the last alarm or alarm-action popup. Check gesture navigation and three-button navigation safe areas.

Validation status:
- Streak update (2026-10-10): debug Kotlin compilation and 33 focused WakeProgress/StreakSchedule/AlarmScheduler tests passed. Device layout and zoom checks remain pending.
- Compact layout update (2026-10-06): debug Kotlin compilation and whitespace checks passed. Samsung device layout and larger-text verification remain pending.
- Android debug Kotlin compilation and eight focused WakeProgress tests passed for the 2026-10-05 changes. Device layout, animation timing, and real Play cancellation still need verification. iOS changes remain pending.

### SYNC-011 — Routine and alarm feedback animations

Status: pending
Date: 2026-10-04
Android commit: <pending>

Changes:
- Add five coordinated feedback animations: sunrise completion with a docking streak number, pose completion, find/hold/return feedback, an earned streak card with tap replay, and alarm changes. The specifications below describe the Android implementation, including the final resting state and when each animation may replay.
- These are presentation changes; use the updated scheduled-day streak accounting in SYNC-001. Keep alarm scheduling, hold progress, the 3-second next-pose transition, and the 10-second completion wake window. Test routines do not earn streak days. Pose selection stays unchanged.

#### Motion conventions

- Dimensions below are Android dp; use equivalent logical points on iOS. Text sizes are Android sp; preserve native text scaling. Use native text and shapes rather than a recording of the animation.
- Timed Android animations use the default `FastOutSlowIn` easing (cubic Bézier 0.4, 0, 0.2, 1). Spring durations depend on settling; do not replace them with loops. Damping ratios below describe the amount of bounce. Match the visible motion using native springs; Android stiffness values are references, not interchangeable iOS API values.
- Respect system motion preferences. With animations disabled or iOS Reduce Motion enabled, show the final state immediately, skip celebration waits, and keep all controls usable. Normal motion must never block dismissal, saving, toggling, deleting, or pose detection.

#### 1. Sunrise completion with streak number

Trigger and layout:
- Enter when the routine reaches COMPLETE. Replace the camera/pose UI with a full-screen vertical forest gradient: `#153523` at the top, `#07100A` at the bottom. No confetti.
- Reserve the bottom area for the existing greeting, completed-pose count, streak label, and Done button, with safe-area insets and 28-point horizontal/vertical padding. Center the sun horizontally in the remaining space above this footer and below the top controls. Its final diameter is the smallest of 220 points, 64% of screen width, and 80% of the remaining height, so it adapts to screen size and text scaling.
- The sun is a circle with a vertical gradient from pale yellow `#F9E4A7` to lime `#C9EE73`. A radial lime glow fades from 16% opacity at its center to transparent, extending to twice the sun radius.
- The final label is “🔥 N-day yoga streak”. The number and suffix use the same native bold 16-point font, with lime text. Render the actual integer count as text: support every digit 0–9 and multi-digit values such as 10, 30, and 100. Do not use separately drawn numeral paths or swap font families/weights between the large and final number.

Sequence, measured from entering COMPLETE:

| Time | Observable motion |
| --- | --- |
| 0–900 ms | The sun rises 90 points from below its final center, scales from 80% to 100%, and fades from transparent to opaque. The greeting and pose-count text fade in alongside it. |
| Starting at 0 ms | The number appears at the moving sun center in dark forest `#153523`. It fades in and springs from 25% of its large scale to full size, with a small overshoot (damping 0.65, stiffness 180). |
| During entrance, then 3000 ms after both entrance animations settle | Keep the number centered inside the sun, including while the sun rises. Hold the fully presented number for 3 seconds before docking. The flame and “-day yoga streak” suffix remain invisible; their final layout space is already reserved. |
| After the 3-second hold | Over 850 ms, move the same number from the sun center to its measured position in the footer label while shrinking to its normal 16-point size. Interpolate its color from dark forest to lime. Fade the flame and suffix from transparent to opaque over the same docking motion. |
| After docking | Scale the entire streak label to 106% over 140 ms, then spring back to 100% (damping 0.6). Leave the sun, greeting, label, and button stationary afterward. |

Number sizing and edge cases:
- Scale the whole number as one unit. Its large scale is limited by both 72% of the sun diameter divided by the measured number width and 78% of the sun diameter divided by the normal font size. This keeps wider counts inside the sun. Use the actual final text position as the destination, so all digits land precisely without a font or position jump.
- Keep the existing time-of-day greeting and completed-pose count. The Done button is visible and usable throughout; the existing top back action remains when applicable. Neither waits for the animation.
- Use the streak supplied by existing completion accounting. A test routine may animate the existing positive streak but must not increment it. A zero streak shows the sunrise, greeting, pose count, and Done button without a large number or streak label; zero still works as a digit within multi-digit counts.
- Do not replay an already-presented completion animation on screen recreation. If the supplied count updates as completion is recorded, show the updated count rather than an old value. Leaving the screen cancels its animation without delaying the existing completion/lock behavior.

#### 2. Pose complete

- Trigger only on a non-final pose entering the existing 3-second TRANSITION. Retain the hold ring with its completed progress; replace the countdown number with a lime checkmark, 64-point text, centered inside the ring.
- Fade the checkmark from transparent to opaque while scaling it from 65% to 100%, with a spring (damping 0.65). Simultaneously scale the ring/countdown container to 107% over 160 ms, then spring back to 100% (damping 0.6).
- Flash a soft lime radial glow behind the ring: 28% opacity at the center, fading to transparent at the edge of a 176-point area. Fade this glow to zero over 750 ms. There is one flash, not a repeating pulse.
- Below the ring, show “Next: [pose name]”, the next pose instruction, and “Starts in [remaining transition seconds]”. Feedback changes crossfade: incoming text takes 180 ms, outgoing text 120 ms. Keep the countdown running on the existing routine clock.
- When the guide pose changes, preserve its existing illustration arrival: it initially appears enlarged at screen center, holds for 1000 ms, then shrinks from 3.75× to 1× and moves into its 96-point header position over 650 ms. It fits inside the existing transition; add no extra wait before detecting the next pose.
- At the next pose, reset the completion check/glow state and show normal finding/holding UI. The final pose goes directly to the sunrise completion screen rather than adding another 3-second checkmark transition.

#### 3. Find → hold → return

- Keep the existing finding guidance and camera preview. The pose target overlay fades out when detection locks, using 220 ms; it remains visible while finding, with its existing lower opacity when almost matched.
- On entering HOLDING, show the existing countdown in 84-point medium text inside a 144-point progress ring, with a 2-point round-capped stroke. The ring is lime `#C9EE73`; its track is white at 12% opacity. Pulse the container once to 107% over 160 ms, then spring to 100% (damping 0.6).
- Under the ring, show “Hold” and “Stay with it while the alarm fades”. Keep the feedback centered and reserve at least 100 points of vertical space to reduce ring movement when the message changes.
- On tracking loss, retain the ring and countdown with the saved hold progress. Stop counting down; do not reset, drain, hide, or continue filling the ring. Settle any active pulse to normal size and transition the ring color to white at 45% opacity over 220 ms.
- Replace the feedback with “↶ Return to pose” and “[N] sec remaining · your time is saved”, using the same 180/120 ms incoming/outgoing crossfade. Show existing framing/error guidance below it when needed. Camera retry and applicable stop actions remain available.
- On returning to HOLDING, transition the ring back to lime over 220 ms, repeat the single 107% pulse, restore hold feedback, and continue from saved time/progress. Do not replay the large pose illustration merely because tracking resumes on the same pose. Rapid tracking changes cancel/retarget feedback; never queue repeated pulses or affect pose detection.

#### 4. Streak earned and flame wobble

- Trigger an earned celebration when the home streak count becomes greater than the last count presented in this app session. Initialize that presented count from stored progress on a cold launch, so opening the app with an existing streak does not celebrate it again. Preserve presentation state across screen recreation.
- Updated 2026-10-10: keep the streak card exactly 80 points tall, with 24-point corners, 82% white surface, hairline border, and 14-point horizontal/10-point vertical padding. Use two shared rows: the left “day streak” label aligns with weekday labels; the flame/count align vertically with the marker centers beneath. Keep 4 points between the rows and at least 12 points between the count group and markers. Never wrap markers underneath the count or enlarge the card. Preserve the flame-only tap wobble.
- Measure the complete count and label at the current text/display scale. Prefer seven dates, with TODAY FIFTH: four previous days, today, and two upcoming days. If they do not fit, show five dates (today third), then three (today second), then only today. These are consecutive local calendar dates, not a fixed week or carousel. Shorten “day streak” to “streak” when five markers cannot fit with the full label; accessibility always announces the full count as an N-day yoga streak. Refresh on alarm edits, app resume, and local midnight; Android checks the date every minute.
- Default label text is 12 points with a 16-point label row; count text is bold 28 points. Labels and count grow with text scale within the fixed card budget: the label row caps at 24 points, label glyph size at 22 points, and count glyph size at the marker-row height. Markers start at 28 points, increase toward 32–34 points with larger text, and are limited by the height remaining below labels and the 4-point gap. Keep at least 6 points between marker columns and distribute remaining horizontal space evenly. Marker corners are 8 points. Completed dates show green `#81BD50` squares with white checks, including completions before a streak break. Unfinished scheduled dates have empty hairline squares; dates without alarms show a muted dash for a rest day. Today's weekday uses forest text and its square has a 2-point outline (forest when unfinished, green when completed); other outlines are 1 point. Provide full-date, today, completed, rest-day, not-completed, and upcoming accessibility descriptions.
- Persist recent actual completion dates separately from the current streak; retain today and the previous four calendar days. Stopping a later alarm or breaking a streak does not erase recent completed-day checkmarks. Multiple same-day completions produce one marker, test routines never add markers, and future dates never show checks. On upgrade with no history, reconstruct up to five known consecutive completed dates from the existing streak count and last completion date; do not invent older interrupted history. The relevant persistence/model files are listed below.
- Initially show the previous count. After 100 ms, change to the earned count: the old number slides upward by its own height over 350 ms and fades out over 200 ms; the new number enters from one height below over 350 ms and fades in over 250 ms. Animate the entire integer string, including transitions such as 9 → 10; do not cycle through intermediate counts.
- At the same time as the number change, rotate only the flame to −18 degrees over 100 ms, then +18 degrees over 180 ms, then spring back to zero (damping 0.45). The card and label keep their normal size and position.
- Consume the earned presentation once when home presents it. Ordinary navigation, reopening the same screen, and a same-day routine that does not increase the count must not roll the number or replay the earned wobble.
- Tapping a positive streak card runs only the flame wobble immediately. No gray pressed/selected background, card scaling, or label scaling. Keep the count unchanged: no increment, persistence, navigation, or new earned event. Repeated taps restart/retarget the wobble from its current angle rather than stacking animations.
- For zero streaks, show count 0 and the same adaptive markers, preserving known recent completions. There is no earned-count roll or tap celebration. If the streak decreases/resets, show the new value without an earned celebration.

#### 5. Alarm changes

- On saving a new or edited alarm and returning home, animate only that saved card: start at 0% opacity, 24 points below its resting position, and 97% scale. Animate all three to 100% opacity, zero offset, and 100% scale using one spring (damping 0.8, stiffness 220). Consume this saved-card presentation once when the card is composed; an offscreen card may present when it first appears.
- Initial loaded alarms and ordinary returns home without a save do not run this card entrance. Keep cards keyed by stable alarm identity so mutations animate the correct card.
- Adding/removing cards fades their appearance/disappearance and springs remaining cards to their new list positions. Android uses Compose `animateItem()` defaults: spring-based opacity and placement with medium-low stiffness. Match smooth native list insertion/removal/reflow rather than inventing a fixed delay or delaying deletion.
- On enable/disable, interpolate the alarm name and time colors over 250 ms. Enabled is dark ink `#142018`; disabled name is muted ink `#647069` at 55% opacity and disabled time at 45%. The existing native switch still changes checked state normally. Quick toggles retarget colors from their current value.
- Scheduling/cancellation and persistence run immediately as before. Do not wait for an entrance, fade, or color transition. Preserve the existing saved-but-disabled state and message when scheduling fails, and the existing snackbar confirmation. No celebration may imply that an unscheduled alarm is enabled.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineCameraScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineRoute.kt (existing completion callback and wake deadline)
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineViewModel.kt (existing hold/transition state and timing)
- app/src/main/java/com/lurremcfly/yogaalarm/model/WakeProgress.kt
- app/src/main/java/com/lurremcfly/yogaalarm/data/WakeProgressStore.kt
- app/src/test/java/com/lurremcfly/yogaalarm/model/WakeProgressTest.kt

Android validation:
- Rolling five-day card: debug Kotlin compilation, ten focused `WakeProgressTest` cases, and whitespace checks passed. On-device layout and midnight rollover checks remain pending.
- Kotlin compilation and the 13 existing `RoutineViewModelTest` cases passed. On-device visual verification is still pending; no Android device was connected during implementation.

iOS acceptance checks:
- Finish a real routine with counts 1, 3, 7, 9, 10, 30, and 100. Confirm matching font/weight throughout, whole-number scaling, contrast inside the sun, exact docking into the label, no confetti, and a stable final state. Check the zero-streak test-routine case separately.
- Test smaller screens, landscape, large text, and safe-area insets. The sun should fit above the footer and the number should land at the actual label position; Done/back must remain usable during every stage.
- Dismiss completion before docking and recreate the screen after presentation. There must be no delayed navigation, duplicate streak day, forced replay, or extension of the completion wake window. Confirm test routines leave stored streak progress unchanged.
- Lose and regain tracking partway through a hold, including repeated rapid losses and a camera error. Confirm saved time/progress freezes and resumes, text/color/pulse changes settle, and retry/stop actions work. Complete a non-final pose; confirm check/glow/next guide fit within the original 3 seconds. Complete the final pose; confirm sunrise appears directly.
- Earn a day and return home, then tap repeatedly, navigate away/back, recreate the screen, and cold launch. Only the earned increase rolls the count; taps only wobble the flame and never gray or resize the card. Check 9 → 10, same-day completion, and streak reset. Confirm the completion number holds for 3 seconds after its entrance, then docks.
- Check seven/five/three/one-marker layouts over midnight, month/year boundaries, a streak break, multiple same-day completions, restart, and upgrade. Confirm today is fifth when seven dates are visible, recent completed dates survive a reset, future days never show checks, and rest days show dashes. At larger text/display sizes and with wide counts, the card remains 80 points tall with aligned label/value rows and full-date accessibility descriptions.
- Save new/existing alarms, scroll to an offscreen saved card, toggle rapidly, and delete first/middle/last cards. Confirm correct card identity, one saved entrance, smooth reflow, and immediate schedule/persistence updates. Test scheduling failure: the disabled state and existing failure message must remain accurate.
- Repeat the flows with motion disabled/Reduce Motion. Final states should appear immediately without celebration waits, count mutations, timing changes, or blocked controls.

### SYNC-010 — Store purchases, localized prices, and restoration

Status: pending
Date: 2026-09-27
Android commit: <pending>

Changes:
- Restore Purchases retries loading product prices as well as checking owned purchases. If an earlier price lookup failed temporarily, a successful retry makes eligible purchase buttons usable again without restarting the app.
- Starting an already-connected billing session also reloads prices.
- Monthly and yearly select the `monthly` and `yearly` base plans of `yoga_alarm_pro`; lifetime uses the non-consumable `yoga_alarm_pro_lifetime` buy option. Map these choices to the iOS catalog independently.
- Lifetime's actual Play purchase-option ID is `yogaalarmprolifetime`, not `buy` (which was confused with the Buy purchase type). Correcting this filter allows the active lifetime product's price and checkout to appear. On iOS, match the actual App Store catalog identifiers rather than purchase-type labels.
- Always show Monthly, Yearly (annual), and Lifetime. Show localized store prices only; plans without prices display “Loading price…” during lookup or “Currently unavailable” afterward and cannot be selected or purchased. Default to Yearly when available, otherwise the first available plan; preserve the user's choice while it remains available. No introductory offers are selected.
- Open the paywall fully expanded with a visible Close action. Benefits, plans, restoration, and messages scroll independently above a fixed purchase area, which stays above the system navigation inset. Selected plans have a radio indicator and pale green highlight. Selecting a plan only updates the selection and price; it never launches checkout.
- The fixed purchase area shows the selected plan, localized billed amount and period, a full-width sky-blue (`#8ED8FF`) button with dark navy (`#102A43`) text, and renewal terms. Android labels subscriptions “Subscribe with Google Play” and lifetime “Buy with Google Play”, with a small Play icon. Use appropriate App Store wording/branding on iOS. Monthly/yearly say they renew monthly/annually and can be cancelled in the store; lifetime says “One-time payment. No subscription.”
- Keep unavailable plans visible even when other plans can be purchased. Offer a distinct Retry action when any plan is unavailable after loading or the store is disconnected; disable purchase when no plan is available or the store is disconnected. Restore Purchases remains an ownership action. The home entry says “GET PRO”, avoiding the implication of a free trial. Disable plan changes and repeat checkout while a purchase is opening.
- One entitlement source grants Pro for a purchased subscription or lifetime, with lifetime taking precedence. Pending payments never grant access; show a pending-payment message. Cancellation does not grant access. Already-owned errors trigger restoration. Active Pro disables new purchases; subscription owners can open store subscription management.
- Checkout cancellation returns to the paywall without an error or stuck purchase button. Payment failure leaves Pro locked and allows retry. Cancelling subscription renewal must not remove access before the paid entitlement expires.
- Settings → Pro displays “Subscription Pro active” and a prominent full-width outlined “Cancel subscription” button, with text explaining that cancellation is confirmed in the store and paid access continues until the period ends. After renewal cancellation, show “Subscription renewal canceled.” and change the button to “Manage subscription”. Include a separate “View renewal date in Google Play” link, or “View subscription end date in Google Play” after cancellation; use native App Store wording on iOS. The Pro sheet also provides subscription management. Android deep-links with the `yoga_alarm_pro` product and package.
- Lifetime settings display “Lifetime Pro active” and “One-time purchase. No renewal or expiry date.” Lifetime alone has no cancellation action. If a valid subscription also exists alongside lifetime, keep its renewal status and cancellation/date controls visible in Settings and the Pro sheet; explain that lifetime Pro access is unaffected by subscription cancellation. Lifetime still takes precedence for Pro access. Free users can open Get Pro from Settings.
- On store refresh after renewal cancellation, show “Renewal canceled” while preserving Pro through the paid period. Android reads the store's auto-renewing flag and currently directs users to Google Play for the exact end date. Do not invent a date from the initial purchase time. Displaying “Pro available until [localized date]” in Android remains pending backend verification of `purchases.subscriptionsv2.get` → `lineItems.expiryTime`; on iOS use verified native expiry/renewal information when available. Show renewal date for renewing subscriptions and access end date for canceled subscriptions; lifetime has no expiry.
- Restore on connection, foreground resume, and explicit Restore Purchases. Successful empty ownership queries remove access; temporary failures preserve the current session's entitlement. No entitlement persists across process death: offline cold starts stay free until the store responds.
- Remove the debug/free activation shortcut in all builds. All checkout uses the store; old debug preferences are ignored.
- Acknowledge accepted purchases (never consume lifetime), retry transient acknowledgement failures, and retry on future restoration. Interim verification trusts client purchase state; production needs backend verification, expiry/revocation handling and reliable acknowledgement. Restored subscriptions display “Subscription” because client records do not identify their base plan reliably.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/billing/PlayBillingManager.kt
- app/src/main/java/com/lurremcfly/yogaalarm/billing/PurchaseVerifier.kt
- app/src/main/java/com/lurremcfly/yogaalarm/model/ProPlan.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AppInfoSheets.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt
- app/src/main/res/drawable/ic_google_play.xml
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt
- app/src/test/java/com/lurremcfly/yogaalarm/billing/PurchaseVerifierTest.kt
- docs/play-billing-setup.md

iOS notes:
- Implement the same Monthly, Yearly, and non-consumable Lifetime choices using the independently configured App Store product IDs. Google Play product IDs, base-plan IDs, offer tokens, and acknowledgement calls are Android-specific and must not be copied into StoreKit code.
- Preserve the existing native paywall design and use App Store localized prices. Keep one entitlement authority shared by all Pro feature gates; restore subscription and lifetime ownership after reinstall and when returning to the app.
- Handle native pending/deferred purchases without unlocking Pro. Use the native transaction verification and completion flow, including purchases that complete while the app is away. Lifetime ownership must remain restorable.
- Keep purchase verification isolated so the future backend can receive store-specific purchase evidence. Android's current client-only trust and offline cold-start behavior are interim limitations, not requirements to weaken iOS verification. Production must handle subscription expiry, grace period, account hold or equivalent store state, refunds/revocations, and durable transaction processing.
- Purchases restore through their originating store. No cross-store Google Play ↔ App Store entitlement transfer is implemented or promised.

iOS acceptance checks:
- Purchase Monthly, Yearly, and Lifetime separately; confirm localized prices, the selected store product, Pro unlock, and transaction completion.
- Cancel checkout, fail payment, and leave a purchase pending; none may grant new Pro. Complete a pending payment later and confirm access updates on return/restart.
- Reinstall and restore both subscription and lifetime purchases. Repeat Restore Purchases without duplicating entitlements or charging again.
- Confirm active Pro prevents a second purchase, lifetime takes precedence, and subscription management remains accessible for subscription owners.
- Open management from Settings and the Pro sheet. Cancel renewal, return to the app, and verify canceled status with Pro retained and the Settings button changed to “Manage subscription”. Verify lifetime alone explains no renewal/expiry, and lifetime plus a separate valid subscription still exposes subscription management. Confirm the correct paid-through date from verified store data; the Android in-app date remains a backend dependency.
- Verify renewal cancellation retains paid access, expiry/revocation removes the affected entitlement, and an independent valid lifetime purchase still grants Pro.
- Exercise unavailable products, temporary store failures, and reconnection. Verify there are no hardcoded price fallbacks or release free-Pro shortcuts.
- Verify the purchase area is visible immediately and stays fixed while scrolling on small screens and with larger text. Switching plans updates the billed amount, purchase wording, and renewal terms. Check the available-plan fallback, Retry, and separate restoration action.

Validation status:
- Subscription settings clarity update (2026-10-10): focused Android debug Kotlin compilation and whitespace checks passed. Cancellation/date links and lifetime-plus-subscription behavior still need on-device verification; exact in-app dates remain a backend dependency.
- Paywall layout update: focused Android debug Kotlin compilation and whitespace checks passed. On-device layout, large-text behavior, and real Google Play checkout still need verification.
- Android debug/release Kotlin compilation and eight focused billing tests passed. Real Google Play checkout still requires the Internal Testing checklist in `docs/play-billing-setup.md`.
- iOS implementation and StoreKit/device verification remain pending. Move this entire entry to Completed only after the native behavior is implemented and verified.

### SYNC-009 — Save the pose selected after swiping back

Status: pending
Date: 2026-09-20
Android commit: <pending>

Changes:
- Each settled, unlocked pose selection updates the alarm draft, including returning to the pose that was selected when the editor opened. For example, Mountain → Warrior II → Mountain must save and test Mountain.
- Locked poses remain unavailable without Pro. Pose durations and routine order are unchanged.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmEditorScreen.kt

### SYNC-008 — Keep configured alarm times after a daylight-saving gap

Status: pending
Date: 2026-09-20
Android commit: <pending>

Changes:
- Resolve the configured local hour and minute independently for each candidate date. A spring daylight-saving gap may shift that day's alarm, but must not shift alarms on later dates.
- In Europe/Stockholm, 02:30 on March 29, 2026 resolves to 03:30. Scheduling the following Monday or Sunday must still use 02:30, including rescheduling after the gap-day alarm fires. One-time alarms moved to tomorrow follow the same rule.
- During an autumn overlap, preserve the current UTC offset when valid for the candidate time; otherwise use the zone's normal resolution. Existing weekday selection and strictly-future scheduling rules remain unchanged.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/alarm/AlarmScheduler.kt
- app/src/test/java/com/lurremcfly/yogaalarm/alarm/AlarmSchedulerTest.kt

### SYNC-007 — Pose illustration travels into the routine header

Status: pending
Date: 2026-09-17
Android commit: <pending>

Changes:
- When the routine first displays a pose or advances to a new one, show that pose's existing illustration centered on screen at about 3.75 times its final size. Hold it there for 1 second, then smoothly scale and move the same image into its existing small header slot over about 0.65 seconds.
- Render only one copy of the illustration during the transition so it appears to travel into place without flashing or overlapping a duplicate.
- The animation is visual only: it must not delay pose order, transition or hold timers, scoring, camera-based detection, controls, or other routine behavior. A rapid pose change should replace and restart the visual transition for the newest pose cleanly.
- Keep the existing routine layout, wording, illustration accessibility description, and final 96-point-equivalent image slot unchanged.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineCameraScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmTheme.kt

iOS notes:
- Implement this as a presentation-only animation driven by the currently displayed guide pose, without coupling it to routine timing or detection state.

### SYNC-006 — Alarm setup card highlights the next step

Status: pending
Date: 2026-09-16
Android commit: <pending>

Changes:
- While any alarm readiness requirement is incomplete, keep all four setup rows visible in this order: Camera, Notifications, Full-screen alarms, and Exact alarms.
- Show a checkmark for each completed requirement. Keep the existing Allow or Enable action for incomplete requirements, including the existing title, detail text, permission behavior, and tap feedback.
- Show one decorative 👉 beside only the first incomplete action. Keep nudging it with short, gently spaced bounces until that row is tapped, then stop. When that requirement becomes ready, move the pointer to the next incomplete row and resume nudging until its row is tapped.
- Keep the pointer decorative for accessibility and never use it or color as the only indication that an action is tappable.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt

iOS notes:
- Reuse the app's existing readiness refresh lifecycle and permission state; do not add parallel permission tracking.

### SYNC-005 — Privacy policy website

Status: pending
Date: 2026-09-16
Android commit: <pending>

Changes:
- The Privacy & safety policy link opens https://www.yogaalarm.com/privacy.
- Remove the persistent Privacy & safety button from the bottom of the home screen. Place it under Settings, reached through the gear in the home header (see SYNC-012). Opening privacy closes Settings and shows the existing camera/privacy/safety explanation and Read privacy policy action. Let the explanation scroll and the policy button grow for larger text.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AppInfoSheets.kt

iOS notes:
- Use the same public policy URL.

### SYNC-004 — Completion screen stays awake for ten seconds

Status: pending
Date: 2026-09-16
Android commit: <pending>

Changes:
- After finishing a yoga routine, keep the completion screen awake for ten seconds. Apply this to real alarms and test routines.
- Stop alarm sound, vibration, and camera processing immediately; record a real alarm completion once, without waiting for the ten seconds.
- For a locked phone, retain the alarm's lock-screen presentation during this grace period. Afterwards restore normal screen timeout and lock-screen behavior; do not force sleep.
- Leaving early cancels the grace period. Screen recreation must preserve the original deadline, and a previous routine's timer must not affect a new alarm.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/MainActivity.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineRoute.kt

iOS notes:
- Use native idle-timer and supported alarm presentation behavior to keep the completed routine visible briefly.

### SYNC-003 — Permission setup stays on the home screen (popup reverted)

Status: pending
Date: 2026-09-16
Android commit: <pending>

Changes:
- The first-launch setup popup was reverted. Do not show an automatic permission popup on first launch or subsequent launches.
- Keep the existing home-screen readiness card with actions for missing permissions, including the existing denial and settings recovery behavior.
- Request access after the user taps an existing setup action or starts the camera. Previously stored initial_setup_shown values are ignored.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineRoute.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt

iOS notes:
- If the earlier popup was implemented, remove it. Preserve existing permission controls and supported platform permissions.

### SYNC-002 — Launcher icon safe-area spacing

Status: pending
Date: 2026-09-11
Android commit: <pending>

Changes:
- The launcher icon foreground now has a small uniform inset so the yoga pose's hands leave visible space against the left and right edges instead of reaching the adaptive-icon crop.
- Preserve the existing artwork, colors, silhouette, and proportions; only match the slightly roomier framing.

Relevant Android files:
- app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
- app/src/main/res/drawable/ic_launcher_foreground_inset.xml
- app/src/main/res/drawable-nodpi/ic_launcher_foreground.webp

iOS notes:
- Apply equivalent safe-area spacing to the iOS app icon source artwork before generating icon sizes.

### SYNC-001 — Yoga-only dismissal, emergency stops, and wake-up streak

Status: pending
Date: 2026-09-11
Android commit: <pending>

Changes:
- Alarm editing has three primary rows in this order: Sound, Snooze, and "Stop button." The Stop button option defaults to on for new and existing alarms, uses the supporting text "Allow manual alarm stop," and shows the manual Stop action when enabled. Turning it off hides the normal Stop action.
- Vibration remains independently configurable but has moved into the Sound sheet. Sound-only, vibration-only, both, and neither are supported.
- When "Stop button" is off for a fired alarm, the normal Stop action is hidden and Android Back remains blocked. Snooze remains available. Test routines always retain normal navigation.
- While an alarm is ringing, its system notification offers a clear Start movement action, a Stop alarm action only when manual stopping is enabled, and a Snooze action only while configured snoozes remain. Stopping from the notification silences only that alarm and does not break the daily streak, so completing a later alarm that day can still continue it.
- Opening a system shortcut for an upcoming alarm opens the app without starting playback or treating the alarm as fired. Ringing notifications still open the active routine.
- Rescheduling enabled alarms after a clock or timezone change must preserve any pending snooze and its remaining snooze count. Explicitly editing, disabling, or deleting an alarm still cancels its pending snooze.
- A subtle Emergency stop remains available. Before today's yoga is completed, it warns that the streak will end; after completion today, it says stopping keeps the streak. It requires a 5-second hold while one of three monthly passes remains. After all passes are used, it requires a 15-second hold but is never blocked or paywalled. An emergency stop still consumes an available pass after today's completion.
- Camera permission denial or a camera failure provides an immediate technical Stop action. Technical exits do not consume a pass or break the streak.
- Updated 2026-10-10: the global yoga streak counts completed alarm days, skipping unscheduled days. Completing at least one genuinely fired alarm through yoga starts/extends it once per local calendar day. Unscheduled days preserve the count without incrementing it, including weekends and periods with every alarm disabled. Example: Friday count 5 remains 5 over a free weekend; completing Monday gives 6. Missing a day on which at least one alarm became due resets the streak when that calendar day ends; today remains available for completion. Snoozing preserves the existing rules. A normal or emergency stop before today's completion still breaks the streak; after completion, stopping a later alarm preserves it. Test routines never count.
- Persist due calendar dates and the active schedule's effective first occurrence. Before saving any alarm creation/edit/enable/disable/delete, evaluate the previous schedule through the current time. An alarm removed before it becomes due creates no obligation; removal after it became due cannot erase that day's obligation. Adding/changing an alarm cannot create past missed days. Union multiple alarms on the same date into one obligation. For a one-off alarm, retain only its actual scheduled date, including when it automatically disables as it fires; do not treat an empty weekday set as daily recurrence. Record the scheduler's exact trigger date after successful scheduling/rescheduling, including boot and clock-change rescheduling. Preserve unchanged alarms' occurrence dates when saving unrelated edits. Keep recent due-date and actual-completion markers independently of the streak count.
- On upgrade without schedule history, preserve the existing saved streak and completion dates, then begin schedule tracking from the current time. A genuinely fired alarm always marks its actual firing date as scheduled, including the first alarm after upgrade and a snooze firing. Do not retroactively judge past dates using the current alarm settings or claim to restore counts already reset by the old behavior. Preserve progress over app restarts and refresh it after alarm changes, app resume, and midnight.
- The streak appears on the alarm home screen, the completed-routine screen, and in the emergency-stop warning.
- With no streak, the home card shows count 0 and the adaptive rolling day markers described in SYNC-011. Test routines still do not start a streak or add completed-day markers.

Relevant Android files:
- app/src/main/java/com/lurremcfly/yogaalarm/model/AlarmConfig.kt
- app/src/main/java/com/lurremcfly/yogaalarm/model/WakeProgress.kt
- app/src/main/java/com/lurremcfly/yogaalarm/model/StreakSchedule.kt
- app/src/main/java/com/lurremcfly/yogaalarm/data/AlarmStore.kt
- app/src/main/java/com/lurremcfly/yogaalarm/data/WakeProgressStore.kt
- app/src/main/java/com/lurremcfly/yogaalarm/MainActivity.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/YogaAlarmRoot.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineRoute.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmHomeScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmEditorScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineCameraScreen.kt
- app/src/main/java/com/lurremcfly/yogaalarm/alarm/AlarmForegroundService.kt
- app/src/main/java/com/lurremcfly/yogaalarm/alarm/StopAlarmReceiver.kt
- app/src/main/java/com/lurremcfly/yogaalarm/alarm/AlarmReceiver.kt
- app/src/main/java/com/lurremcfly/yogaalarm/alarm/AlarmScheduler.kt
- app/src/main/java/com/lurremcfly/yogaalarm/alarm/BootReceiver.kt

iOS notes:
- Verify weekday-only alarms across a weekend, a missed Monday, all alarms disabled, multiple alarms on one date, edits before/after an alarm time, one-off auto-disable, migration, restart, and month/year boundaries. For this update, leave the Android commit placeholder until a commit exists.
- Match the observable rules and copy using idiomatic iOS persistence and alarm presentation APIs.
- System-level termination cannot be prevented; the setting controls supported in-app dismissal paths.

<!--
Add new items here using the next sequential SYNC-XXX ID. Keep each item focused on
observable behavior and include enough detail for an iOS agent to implement it
without reconstructing the full Android history.

### SYNC-XXX — Short descriptive title

Status: pending
Date: YYYY-MM-DD
Android commit: <commit hash if available>

Changes:
- Describe the user-facing or behavioral change.
- Describe important rules, defaults, limits, and edge cases.
- Focus on what the app should do rather than Kotlin-specific implementation.

Relevant Android files:
- path/to/file.kt
- path/to/other/file.kt

iOS notes:
- Add useful implementation or parity notes.
- Match Android behavior and UX, but use idiomatic iOS architecture.
-->

## Completed

<!-- Move items here once implemented and verified on iOS. Change Status to completed. -->

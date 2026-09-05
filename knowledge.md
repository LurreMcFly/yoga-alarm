# Yoga Alarm — project handoff

Last updated: 2026-09-05

Latest implementation checkpoint: the Android responsiveness pass is documented in
[`docs/performance-pass.md`](docs/performance-pass.md). It adds background camera/model
setup, capped analysis with reusable buffers, session lifecycle/stale-frame handling,
continuous service-owned audio, corrected Done/retry actions, editor feedback and saved
drafts, clearer morning guidance, and lossless artwork compression. The selfie camera,
close-range geometry, full model, and pose-scoring thresholds remain unchanged. Phone
validation of this pass is still required; see the expanded reliability checklist.
The final debug APK and unsigned release AAB built successfully; 23 unit tests passed
and lint reports no errors (36 warnings remain).

This is the starting document for anyone continuing Yoga Alarm. It describes the
product intent, current implementation, important decisions, known risks, and the
remaining route to a Google Play launch.

## 1. Product and end goal

Yoga Alarm is an alarm clock that wakes the user by requiring a short sequence of
yoga or mobility poses. The alarm becomes quieter while the user holds the requested
pose, becomes louder if the pose is lost, and stops after the routine is completed.

The intended reaction is:

> I hate that my alarm is going off, but after one minute of moving I am actually awake.

The long-term product is a **movement alarm clock**, not a strict yoga-training app.
The first launch targets tired, health-interested adults who want a calmer but still
effective reason to get out of bed.

The immediate objective is an Android launch that is polished and reliable enough for
real users to pay for. Samsung Galaxy S25 is the primary test device; broader Android
support is required for Google Play. iOS is a later project.

## 2. Core experience

Before bed, the user creates an alarm, chooses weekdays, sound, snooze settings, and a
pose routine. No account is required.

When the alarm fires:

1. Android wakes the screen and shows the ringing experience over the lock screen.
2. The user can snooze within the configured limit or start the camera.
3. The front camera fills the screen; processing happens on-device.
4. A generous translucent mannequin shows the target pose over the live preview.
5. Live landmark feedback shows where the app sees the user.
6. A clear attempt at the pose starts the hold timer.
7. Alarm volume fades gradually across the routine.
8. Brief tracking loss is tolerated; sustained pose loss pauses the timer and raises volume.
9. Poses advance without confirmation screens.
10. Completing the last pose stops audio and vibration.

The camera does not start automatically. The explicit **Start camera** action gives the
user time to get ready and makes camera use understandable.

## 3. Product principles

- **Alarm reliability is sacred.** Every other feature is secondary.
- **Generous detection.** Recognize a genuine attempt, not technically perfect yoga.
- **Movement, not punishment.** The default should take roughly one minute.
- **Faster than thinking.** Morning screens must be understandable while half asleep.
- **Free is useful forever.** Pro sells customization rather than removing basic utility.
- **Privacy by design.** Camera frames are processed on-device and never recorded or uploaded.
- **No customer account.** Google Play owns payment identity and purchase restoration.
- **Keep infrastructure small.** Do not build growth infrastructure before it is needed.

## 4. Current Free and Pro product

Free:

- Unlimited alarms.
- Exactly three poses per routine.
- Mountain, Warrior II, and Tree.
- Fixed 20-second holds.
- Sunbird Morning Call and Morning Temple Call.
- Alarm scheduling, camera validation, audio fading, vibration, and snooze.

Pro:

- All eight current poses.
- One to ten poses per routine.
- Hold durations of 10, 15, 20, 30, 45, or 60 seconds.
- All ten current alarm sounds.
- The same entitlement for every paid plan.

Approved launch pricing:

- Monthly: USD 7.99/month.
- Yearly: USD 19.99/year; the current paywall labels this as approximately 79% saved
  compared with twelve monthly payments.
- Lifetime: USD 29.99 once.
- No free trial or introductory offer at launch.

Google Play must supply localized production prices. Dollar prices in the app are
fallbacks for direct-installed debug builds.

The authoritative billing decisions are in
[`docs/play-billing-setup.md`](docs/play-billing-setup.md).

## 5. Major design decisions and rationale

### Native Android first

Reliable alarms, exact scheduling, foreground services, reboot recovery, lock-screen
presentation, audio routing, and camera behavior are platform-specific. The shipping
app is native Android with Jetpack Compose. The browser tester is for rapid design
iteration only.

### Partial-body-friendly pose detection

Requiring the entire body forced users several metres away from the phone, making the
screen difficult to see and the product impractical in small bedrooms. Pose scoring is
therefore designed to use the landmarks relevant to each pose and tolerate some missing
lower-body landmarks. The pose guide is also adapted to the visible camera area.

### Hysteresis and grace periods

Pose entry is deliberately stricter than pose retention. Once a pose is accepted, a
lower exit threshold and time grace period prevent flickering between “holding” and
“get into the pose.” This is essential to avoid telling a user who is clearly doing the
pose that they are wrong.

### Routine-wide audio progression

Volume fades as the user makes progress and does not reset to full volume between poses.
Transitions continue from the level reached at the end of the preceding pose. Losing a
pose raises the volume smoothly rather than jumping instantly.

### No automatic camera activation

The user explicitly starts the camera from the alarm screen. This was retained after UX
discussion because a user may want to dress or position the phone first.

### No Yoga Alarm customer account

Google Play will handle payment identity, subscription lifecycle, and restoration on
Android. The future Apple version will use StoreKit independently. Purchases will not
transfer between Android and iOS without a future Yoga Alarm account, which is an
accepted V1 limitation.

### Minimal billing identity

The future backend should be purchase-centric, not user-centric. It may store a random
installation ID, purchase token, product/base-plan IDs, verified state, and timestamps.
It should not store names, customer email, Google account information, advertising IDs,
camera data, pose data, or alarm schedules.

## 6. Current technical architecture

The Android package and permanent application ID are:

```text
com.lurremcfly.yogaalarm
```

Build targets:

```text
compileSdk 36
targetSdk 36
minSdk 26
Java/Kotlin target 17
```

Key dependencies:

- Jetpack Compose and Material 3 for UI.
- CameraX for the front-camera preview.
- MediaPipe Tasks Vision for on-device body landmarks.
- Google Play Billing Library 9.1.0.

Important source areas:

| Area | Main files |
|---|---|
| App coordination | `app/src/main/java/com/lurremcfly/yogaalarm/MainActivity.kt` |
| Alarm UI/editor/paywall | `app/src/main/java/com/lurremcfly/yogaalarm/ui/AlarmApp.kt` |
| Routine state machine | `app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineViewModel.kt` |
| Full-screen camera routine | `app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineCameraScreen.kt` |
| Camera and landmarks | `app/src/main/java/com/lurremcfly/yogaalarm/camera/` |
| Pose scoring/stability | `app/src/main/java/com/lurremcfly/yogaalarm/model/PoseScoring.kt` and `PoseDetectionGate.kt` |
| Alarm scheduling/delivery | `app/src/main/java/com/lurremcfly/yogaalarm/alarm/` |
| Alarm audio | `app/src/main/java/com/lurremcfly/yogaalarm/audio/AlarmAudio.kt` |
| Local persistence | `app/src/main/java/com/lurremcfly/yogaalarm/data/AlarmStore.kt` |
| Billing prototype | `app/src/main/java/com/lurremcfly/yogaalarm/billing/PlayBillingManager.kt` |
| Product catalog | `app/src/main/java/com/lurremcfly/yogaalarm/model/AlarmConfig.kt` and `ProPlan.kt` |

Alarms and settings are stored locally in private `SharedPreferences`. There is currently
no production backend, account system, analytics SDK, advertising SDK, or cloud sync.

The manifest intentionally removes `INTERNET` and `ACCESS_NETWORK_STATE`. This is valid
for the current on-device build but must be revisited when the purchase-verification
backend is added. That change also requires updating the privacy policy and Play Data
Safety answers.

## 7. Current implementation status

Implemented in the Android app:

- Alarm list, create, edit, enable, disable, and delete.
- Circular time selection, weekday recurrence, alarm naming, sound, vibration, and snooze.
- Configurable snooze duration and maximum count; snooze disappears after the final use.
- Exact alarm scheduling and foreground alarm service.
- Lock-screen/full-screen alarm presentation and screen wake.
- Reboot, app-update, manual-time, and timezone rescheduling receivers.
- Screen kept awake during the pose routine.
- Eight pose illustrations and pose-specific camera guides.
- On-device landmarks and scoring for all eight poses.
- Stabilized pose entry/loss behavior.
- Hold timer, transitions, dynamic audio, completion, and emergency stop.
- Ten compressed alarm tracks.
- Free/Pro feature gating and paywall presentation.
- Client-side Google Play Billing prototype plus debug-only Pro activation.
- Restore Purchases and Manage Subscription UI paths.
- Responsive home UI tested on two Samsung phone sizes.
- In-app privacy information and a dedicated privacy-policy site.

User-reported alarm tests that have worked include locked screen, process/app removal from
recents, reboot recovery, Samsung battery restrictions, silent/DND scenarios, Bluetooth
and headphones, multiple alarms, repeating and one-time alarms, and snooze limits.

Important terminology: Android Settings **Force stop** disables an app's alarms and
receivers until the user manually opens it again. No app can bypass that OS behavior.
Killing a process or swiping from recents is not the same as Settings Force stop.

Still requiring explicit reliability coverage:

- Manual clock changes forward and backward.
- Timezone changes and daylight-saving transitions.
- A non-Samsung device.
- An older supported Android device near API 26.
- Final release build rather than only debug APKs.

The debug APK output location is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The earlier debug APK was approximately 79 MiB. A release AAB built during the
2026-09-05 performance pass measured approximately 23.1 MiB estimated download for
the arm64/API 36 reference device, using bundletool. These are different build and
distribution formats; the difference is not the size saving from the code changes.
Play delivers device-specific native libraries rather than the universal debug set.

## 8. Browser tester and privacy site

`web-tester/` is a local browser design harness available at `http://localhost:3000/`
when its development server is running. It is useful for visual iteration but cannot
validate Android alarm, lock-screen, reboot, audio-routing, or background behavior.

Run it with:

```bash
cd web-tester
pnpm run dev
```

`privacy-site/` contains the dedicated public privacy-policy site. The intended URL is:

```text
https://yoga-alarm.polite-eel-9991.chatgpt.site/privacy
```

At the latest known checkpoint, the site had been deployed privately but still required
explicit public publication approval and a real public support email before release.
Verify its current deployment state rather than assuming it is public.

## 9. Billing status and approved direction

The current app billing code is a prototype. It queries separate subscription IDs
`pro_monthly` and `pro_yearly` plus one-time ID `pro_lifetime`, acknowledges on the client,
and grants the local entitlement from client-observed purchase state. This is not the
approved production model.

The approved permanent Play catalog is:

```text
Subscription product: yoga_alarm_pro
  Base plan: monthly — USD 7.99/month
  Base plan: yearly  — USD 19.99/year

One-time non-consumable: yoga_alarm_pro_lifetime — USD 29.99
```

The frontend should continue to look like three choices. The catalog implementation
behind it changes to one subscription with two base plans plus lifetime.

Production rules:

- Grant Pro only after Google reports `PURCHASED` and backend verification succeeds.
- Never grant entitlement for `PENDING`.
- Acknowledge verified purchases promptly on the backend.
- Cancellation retains access through the paid expiry.
- Grace period retains access; account hold does not.
- Refund/revocation removes its entitlement.
- Lifetime wins if multiple valid plans exist.
- Temporary service failure must not immediately remove previously verified access.
- Release builds must never expose debug Pro activation.

## 10. Remaining launch work, in order

### Bucket 2 — Google Play Console configuration

This is the immediate next checkpoint and is primarily manual console work:

1. Complete the organization developer and merchant/payments profiles.
2. Connect the Swedish AB bank account.
3. Create `yoga_alarm_pro`.
4. Add and activate `monthly` and `yearly` auto-renewing base plans.
5. Create and activate `yoga_alarm_pro_lifetime` as a non-consumable one-time product.
6. Configure regional availability and prices.
7. Configure seven-day grace, Google's automatic account hold, and resubscription.
8. Add license testers.
9. Upload an initial AAB to the internal testing track.

Do not invent different IDs in Play Console; activated IDs cannot be casually renamed or
reused.

### Bucket 3 — Productionize local app billing

- Replace prototype IDs with the approved subscription/base-plan structure.
- Select the correct offer token for each base plan instead of taking the first offer.
- Continue showing the existing three-choice paywall.
- Use Play-localized prices.
- Remove debug activation from release behavior.
- Handle purchase, pending, cancellation, restore, and billing-service failure correctly.
- Test using a Play-installed internal-track build, not a sideloaded APK.

### Bucket 4 — Minimal anonymous verification backend

Create only the endpoints and records needed for secure entitlement verification. There
is no customer account. The likely initial API surface is:

```text
POST /billing/google/verify
GET  /entitlements/{installation_id}
POST /billing/google/rtdn
```

Adding this backend means adding network access to Android and revising privacy/Data Safety
declarations. Keep camera and alarm data entirely out of it.

### Bucket 5 — RTDN lifecycle handling

Google Real-time Developer Notifications signal purchase-state changes. The backend must
then query the Google Play Developer API, treat that verified result as authoritative,
and process duplicate or out-of-order notifications idempotently.

Test purchase, renewal, cancellation, expiry, grace, account hold, recovery, refund,
revocation, lifetime refund, reinstall, offline startup, and temporary outages.

### Bucket 6 — Privacy-minimal creator attribution

This is a growth system, not a prerequisite for secure billing. Build it only after billing
works:

- `yogaalarm.app/r/{creator}` links.
- Google Install Referrer read once after install.
- Optional creator code before first purchase.
- First valid referral wins within the agreed attribution window.
- Aggregate click counts rather than raw click histories where possible.
- No advertising ID, raw IP storage, or fingerprinting.

### Bucket 7 — Commission ledger and minimal administration

Initially provide a private owner-only admin surface, monthly statements, and Wise CSV
exports. Do not build creator logins or automatic Wise payouts until manual operation is
actually painful.

Commission state must distinguish estimated, pending, available, paid, reversed, and
post-payout negative adjustments. Final payouts should be reconciled against Google
financial reports rather than assuming purchase callbacks equal settled net revenue.

### Final Play release work

- Generate and securely back up a release/upload signing key.
- Produce and inspect a signed release AAB.
- Complete store listing, screenshots, icon, descriptions, countries, content rating,
  health declaration, Data Safety, exact-alarm, and full-screen-intent declarations.
- Make the privacy policy public and add a support email.
- Retain private proof of commercial rights for Suno audio and generated illustrations.
- Run internal testing with real Play test transactions.
- Complete any closed-testing requirement applied to the developer account.
- Review the Play pre-launch report and resolve crashes, ANRs, accessibility, and policy issues.
- Perform a staged production rollout rather than releasing to everyone immediately.

## 11. Explicitly deferred scope

Do not add these unless the product direction changes:

- Customer accounts or cloud alarm sync.
- Detailed yoga-form or medical coaching.
- Video recording or upload.
- Social features, friends, or leaderboards.
- Health Connect, wearables, sleep tracking, or calories.
- AI-generated routines.
- Dozens of poses.
- AppsFlyer, Adjust, Branch, or other attribution SDKs.
- Complex fraud scoring or multi-touch attribution.
- Creator marketplace or creator-facing portal.
- Automatic Wise payouts.
- iOS before the Android paid experience is proven.

## 12. Build and test notes

The shell may not have `JAVA_HOME` configured. A known full JDK used successfully is:

```text
/home/jolu/.cache/traininglog-build-tools/jdk-17.0.19+10
```

Build a debug APK:

```bash
JAVA_HOME=/home/jolu/.cache/traininglog-build-tools/jdk-17.0.19+10 \
  ./gradlew :app:assembleDebug
```

Install over USB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Use the focused release matrix in
[`docs/android-reliability-checklist.md`](docs/android-reliability-checklist.md).

## 13. Repository and documentation warnings

At this handoff, the working tree is substantially dirty. The permanent package migration,
new source tree, resources, sounds, tests, policy documents, browser changes, and privacy
site appear as modified, deleted, or untracked relative to commit `2c3dfe6`. These changes
must be reviewed and committed before another major implementation bucket so the current
working product is not lost or confused with the old `com.yogaalarm.prototype` tree.

Some documents are historical rather than current:

- `docs/prototype-baseline.md` accurately records an old baseline, not today's app.
- `README.md` still describes the early camera spike and should be rewritten.
- `docs/google-play-policy-declarations.md` still lists the old billing product IDs and says
  no backend/network collection. Update it when billing/backend implementation begins.
- This `knowledge.md` and `docs/play-billing-setup.md` are the current product/billing sources
  of truth.

## 14. Recommended immediate next action

Before adding more code:

1. Review this handoff and the billing specification.
2. Commit the existing package migration and completed application work as a clean checkpoint.
3. Complete Google Play Console Bucket 2 using the exact approved catalog IDs.
4. Return to local Bucket 3 with real Play products available for testing.

The launch decision remains simple: Yoga Alarm succeeds only if a user can trust the alarm,
understand the camera experience half asleep, complete forgiving pose validation, and pay for
Pro without friction or loss of entitlement.

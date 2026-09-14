# First prompt for `LurreMcFly/yoga-alarm-ios`

Use the following as the first prompt in the new repository:

---

Build the native iOS version of Yoga Alarm in this repository. The Android app is the product and visual reference: reproduce its user-facing experience as closely as iOS permits, while using native Apple frameworks and interaction patterns rather than mechanically translating Kotlin code.

## Product background

Yoga Alarm is an alarm clock that wakes the user through a short sequence of yoga or mobility poses. The alarm becomes quieter while the requested pose is held, becomes louder after sustained pose loss, and stops when the routine is complete. It should feel calm, premium, and unavoidable enough to wake someone—not like punishment or strict yoga coaching.

The Android reference repository is `LurreMcFly/yoga-alarm`. On the current development machine it may also be available at:

```text
/home/jolu/Projects/yoga_alarm
```

Read its `knowledge.md` first. Then inspect the Android implementation and assets named below. Treat the current Android behavior, copy, catalog, and design as the source of truth unless this prompt explicitly describes an iOS difference.

The planned iOS bundle identifier is:

```text
com.lurremcfly.yogaalarm
```

Do not create App Store Connect records, certificates, remote products, or other irreversible external configuration without my approval.

## Critical platform decision

Target **iOS 26 or later** and use **AlarmKit** for real wake alarms. AlarmKit is the supported Apple framework for scheduled alarms that appear on the Lock Screen and can break through silent mode and Focus. Do not present ordinary local notifications as equivalent alarm reliability.

Use AlarmKit authorization with a clear `NSAlarmKitUsageDescription`. Support one-time and weekly repeating schedules, custom bundled sounds, snooze where configured, stable alarm identifiers, cancellation, and rescheduling after edits. Use an AlarmKit custom action/App Intent to open the matching ringing routine in the app.

Before porting the full app, prove this platform-critical flow on a physical iPhone:

1. Schedule an alarm.
2. Lock the phone and let the app leave the foreground.
3. Confirm the AlarmKit presentation appears and the selected sound plays.
4. Tap the custom **Start movement** action.
5. Open the correct alarm/routine without requiring the user to find it manually.
6. Hand audio from the system alarm presentation to the in-app routine cleanly, without an unintended silent gap, double playback, or volume jump.
7. Confirm snooze and emergency stop behavior.

Document any unavoidable Apple system behavior that differs from Android. Do not hide limitations behind mock UI.

Official references:

- <https://developer.apple.com/documentation/AlarmKit/scheduling-an-alarm-with-alarmkit>
- <https://developer.apple.com/videos/play/wwdc2025/230/>

## Working method

Deliver the port in reviewable buckets. For each bucket:

1. Inspect the corresponding Android behavior and state the exact parity target.
2. Present a concise implementation plan and any unavoidable iOS differences.
3. Implement and run focused validation.
4. Stop at a checkpoint and tell me exactly what to test before moving on.

Do not attempt the entire port in one unreviewable change. Keep the implementation simple and native.

Recommended buckets:

0. AlarmKit feasibility and physical-device handoff spike.
1. App shell, design system, local alarm list/editor, and persistence.
2. Camera preview, MediaPipe landmarks, pose guides, scoring, and stability.
3. Routine state machine, hold timer, transitions, audio fading, vibration, and completion.
4. AlarmKit production scheduling, snooze limits, lock-screen presentation, lifecycle reconciliation, and reliability matrix.
5. Free/Pro gating and StoreKit 2 purchases.
6. Privacy, accessibility, performance, release assets, TestFlight, and App Store readiness.

## Required product parity

### Alarm management

- Alarm list with create, edit, enable, disable, and two delete paths.
- Smooth circular hour/minute picker showing three rows.
- Alarm name.
- Week begins Monday.
- One-time and selected-weekday alarms.
- Sound selection and preview.
- Vibration toggle.
- Configurable snooze duration and maximum count; no snooze action after the configured final snooze.
- Persistent local storage with no Yoga Alarm account.
- Clear alarm-readiness status and repair actions when AlarmKit or camera access is unavailable.

### Ringing and routine flow

- Lock-screen system alarm with the alarm name, stop, optional snooze, and a clear **Start movement** action.
- The camera must never start automatically; the user explicitly starts it.
- Before camera activation, show short preparation guidance, a large centered Start camera button, snooze lower on the screen, and the privacy message at the bottom.
- Full-screen front-camera preview during the routine.
- Keep the display awake only while the ringing/routine experience is active, and restore normal idle behavior on every exit path.
- Generous translucent target-pose mannequin adapted to each pose and to partial-body framing.
- Live tracked-body feedback over the camera.
- Pose entry must recognize a clear attempt, not perfect yoga form.
- Pose retention must be more forgiving than entry, using hysteresis and grace periods so feedback does not flicker or tell someone who is holding the pose that they are wrong.
- Hold timer pauses only after sustained pose loss and resumes smoothly.
- Alarm volume fades materially across each successful hold.
- Volume must not reset between poses; the next pose starts from the previous pose's ending level.
- Losing a pose raises volume smoothly rather than jumping.
- Transition sound/haptic between poses.
- Emergency Stop button available during the camera routine.
- Completion stops alarm audio and vibration and shows the minimal Good morning result.

Audit every stop path: AlarmKit stop, snooze, app termination, camera denial, emergency stop, routine completion, navigation, interruptions, and errors. No path may leave audio, vibration, camera capture, or idle-timer overrides running accidentally.

## Pose detection

Use the front camera through AVFoundation. Keep all camera analysis on-device and never record or upload video.

Prefer MediaPipe Tasks Vision Pose Landmarker in live-stream mode with the same full pose-landmarker model as Android. This preserves the same landmark topology and makes it possible to port the existing geometry and threshold rules instead of inventing a second pose engine. If the current MediaPipe iOS integration cannot support the deployment target or required performance, document the evidence before proposing Apple Vision as a substitute.

Port and unit-test the behavior represented by these Android files:

```text
app/src/main/java/com/lurremcfly/yogaalarm/camera/
app/src/main/java/com/lurremcfly/yogaalarm/model/PoseScoring.kt
app/src/main/java/com/lurremcfly/yogaalarm/model/PoseDetectionGate.kt
app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineViewModel.kt
app/src/main/java/com/lurremcfly/yogaalarm/ui/RoutineCameraScreen.kt
```

Support the same eight poses:

- Mountain
- Warrior II
- Tree
- Chair
- Forward Fold
- Triangle
- Goddess
- Wide-Legged Fold

Preserve partial-body-friendly detection. Do not reintroduce a strict full-body-in-frame requirement. Measure detection latency and frame rate on a physical iPhone, and keep frame processing off the main actor.

## Visual design and assets

Use SwiftUI and reproduce the established Android design language:

- pale mint, blue, and soft lilac background gradients
- dark forest green primary text and controls
- lime green accent
- large white rounded cards with restrained borders and soft shadows
- spacious, minimal layouts
- premium typography, clear hierarchy, and large morning-friendly controls

Follow iOS safe areas, Dynamic Type, VoiceOver, reduced motion, light/dark appearance decisions, and native touch behavior. “One-to-one” means the same product identity and information architecture, not ignoring platform conventions.

Copy the existing generated pose art rather than recreating it. Web-ready PNG sources are in:

```text
web-tester/public/poses/mountain.png
web-tester/public/poses/warrior-two.png
web-tester/public/poses/tree.png
web-tester/public/poses/chair.png
web-tester/public/poses/forward-fold.png
web-tester/public/poses/triangle.png
web-tester/public/poses/goddess.png
web-tester/public/poses/wide-legged-fold.png
```

Android source artwork and the current icon are also available under:

```text
app/src/main/res/drawable-nodpi/
app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
```

Import them into an Xcode asset catalog with appropriate scale handling. Preserve visual centering, especially Mountain. Do not add white icon padding or replace the art with stick figures. Keep private proof of commercial rights outside the public repository.

Copy the ten alarm tracks and transition sound from `app/src/main/res/raw/`. Preserve user-facing names and Free/Pro access. Convert files only when an Apple API requires a supported audio format, and compare the result audibly before replacing a source asset.

## Free and Pro catalog

Free forever:

- unlimited alarms
- exactly three poses per routine
- Mountain, Warrior II, and Tree
- fixed 20-second holds
- Sunbird Morning Call and Morning Temple Call
- alarm scheduling, camera validation, audio fading, vibration, and snooze

Pro:

- all eight poses
- one to ten poses
- hold durations of 10, 15, 20, 30, 45, or 60 seconds
- all ten alarm sounds

Pricing shown by the current product:

- USD 7.99 monthly
- USD 19.99 yearly
- USD 29.99 lifetime
- no free trial at launch

Use StoreKit 2 and always display App Store-localized prices in production. The paywall should look like the Android paywall and present three choices. Monthly and yearly are separate auto-renewable products in one subscription group; lifetime is a non-consumable. Keep product identifiers centralized and treat these as provisional until App Store Connect setup is approved:

```text
com.lurremcfly.yogaalarm.pro.monthly
com.lurremcfly.yogaalarm.pro.yearly
com.lurremcfly.yogaalarm.pro.lifetime
```

Include Restore Purchases and Manage Subscription. Handle verified, pending, canceled, expired, revoked, refunded, upgraded, and offline states. Finish verified transactions. Add a local StoreKit configuration so all Pro flows can be tested before App Store Connect exists.

No Yoga Alarm account is required. Android and Apple purchases do not transfer across platforms in V1; make that limitation clear where needed.

Official StoreKit testing references:

- <https://developer.apple.com/documentation/xcode/setting-up-storekit-testing-in-xcode>
- <https://developer.apple.com/documentation/storekit/testing-at-all-stages-of-development-with-xcode-and-the-sandbox>

## Architecture constraints

- Native Swift and SwiftUI; use current stable Swift concurrency.
- Use AlarmKit, App Intents, ActivityKit where required, AVFoundation, StoreKit 2, and on-device pose detection.
- Prefer a small feature-oriented project over a broad framework or cross-platform abstraction.
- Keep the routine state machine and pose scoring as pure, testable logic.
- Keep external side effects behind focused services: alarm scheduling, camera, audio, persistence, and purchases.
- Store alarms/settings locally with a simple Codable model. Add migrations before changing persisted schemas.
- No accounts, cloud sync, analytics, advertising SDKs, recorded video, health-data collection, or speculative backend.
- Do not claim background execution capabilities that iOS does not provide.
- Use dependency injection only where it materially enables tests or previews; avoid framework-heavy architecture.

## Physical iPhone testing—the APK equivalent

iOS does not have an unrestricted APK-style install flow. Set up and document both supported workflows:

### Fast development loop

On a Mac with the current Xcode:

1. Connect or pair the iPhone with the Mac.
2. Trust the Mac and enable Developer Mode on the iPhone if requested.
3. Sign into Xcode with an Apple Account.
4. Use automatic signing with the selected development team.
5. Select the iPhone as the run destination and press Run.

This installs the current build directly on the phone and is the fastest equivalent to repeatedly installing Android debug APKs. A personal/free Apple account can be used for basic device development, but provisioning must be renewed periodically and some distribution capabilities require paid program membership.

### Repeatable beta installation

Use a paid Apple Developer Program membership, archive the app, upload it to App Store Connect, and distribute it with TestFlight. Testers install updates through the TestFlight app without reconnecting to Xcode. This is the preferred equivalent to sharing APK builds. External TestFlight testing may require beta review.

Also support Simulator previews for ordinary UI work, but never use Simulator results as proof for AlarmKit, camera, audio routing, StoreKit, locked-screen behavior, or performance.

Document prerequisites clearly: iOS development and signing require macOS with Xcode, and physical AlarmKit testing requires an iPhone running iOS 26 or later. If the current execution host is not macOS, do not pretend the app was compiled or device-tested. Implement only work that can be reviewed safely, then provide exact steps for continuing on a Mac.

Official device and distribution references:

- <https://developer.apple.com/documentation/xcode/running-your-app-on-simulated-or-physical-devices>
- <https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases>
- <https://developer.apple.com/testflight/>

## Privacy and App Store preparation

Use `https://yogaalarm.com/privacy`, `/terms`, and `/support` once those pages are public and updated for iOS. Before submission, update the shared privacy policy to mention Apple/StoreKit and confirm the App Privacy answers match the release binary.

Required privacy facts remain:

- no Yoga Alarm account
- camera frames processed ephemerally on-device
- video never recorded, retained, or uploaded by Yoga Alarm
- alarm and routine settings stored locally
- no ads or analytics in V1
- Apple processes payment details

Add precise camera and AlarmKit usage descriptions. Prepare App Store icon assets, screenshots, description, support URL, privacy URL, age rating, review notes, subscription disclosures, and the movement safety disclaimer. Yoga Alarm is a movement alarm, not a medical device; users should move within their ability and stop with pain, dizziness, or discomfort.

## Initial repository deliverables

- Xcode project with app and test targets
- README covering prerequisites, build/run, signing, direct iPhone installation, TestFlight, architecture, and known platform differences
- a short parity checklist mapped to the Android features
- `docs/ios-feasibility.md` recording the AlarmKit handoff spike and physical-device results
- centralized design tokens and product catalog
- imported app icon, pose illustrations, audio, and model assets
- unit tests for alarm occurrence calculations, snooze limits, pose scoring/gating, routine progression, volume progression, and product gating
- UI previews for key screen sizes and Dynamic Type
- local StoreKit test configuration
- no unresolved build warnings introduced by the project

## First checkpoint

Begin by:

1. Inspecting this repository and the Android source of truth.
2. Verifying that the host has macOS, current Xcode, and an iOS 26 SDK.
3. Producing a parity inventory and identifying true Apple-platform differences.
4. Implementing only Bucket 0: the smallest AlarmKit alarm-to-app handoff spike.
5. Giving me exact steps to install and test that spike on my iPhone.

Stop after Bucket 0 for review. Do not proceed to the complete UI port until the locked-phone alarm, custom Start movement action, and audio handoff have been tested on a physical iPhone.

---

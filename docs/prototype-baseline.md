# Yoga Alarm prototype baseline

Baseline date: 2026-08-26

This document freezes the state approved before native Android product work begins. The browser tester is the interaction reference; the Android app is the native implementation starting point.

## Locked product decisions

- Android first, with the Samsung Galaxy S25 as the primary device.
- Default routine: Mountain, Warrior II, and Tree for 20 seconds each.
- Camera stays off until the user presses **Start camera**.
- Camera processing stays on-device; frames are not recorded or uploaded.
- Detection should recognize a clear attempt, not judge perfect yoga form.
- The user sees the live camera, a generous pose guide, and live body tracking.
- Free users keep the three default poses and 20-second duration.
- No account or backend for the Android MVP.
- The browser tester remains a rapid UI and interaction test surface.

## Browser reference implementation

The browser tester currently provides:

- Alarm list and alarm editor.
- Circular hour and minute wheels.
- Alarm name, weekdays, sound, vibration, and snooze controls.
- Inline horizontal pose selection and duration selection.
- Three free poses and five visually locked Pro poses.
- All eight pose illustrations.
- Full-screen camera test with explicit camera activation.
- Live MediaPipe pose landmarks, pose-specific scoring, hold timer, transitions, and completion.
- Synthetic looping alarm audio that fades with hold progress and rises after pose loss.

Browser state sequence:

1. `idle`: camera off and privacy message visible.
2. `finding`: camera/model active; user moves into the pose.
3. `holding`: detection accepted; timer advances and audio fades.
4. `paused`: pose lost after the grace period; timer pauses and audio rises.
5. `transition`: three-second move to the next pose at 35% alarm level.
6. `complete`: audio stops and Good morning is shown.

### Browser detection baseline

- Inference is capped at roughly 15 FPS (`66 ms` minimum interval).
- Pose entry: score at least `0.74` continuously for `450 ms`.
- Pose loss: score below `0.50` continuously for `900 ms`.
- Scores between `0.50` and `0.74` preserve the current state.
- “Almost there” appears from score `0.55`.
- Default landmark visibility requirement is `0.38`.
- Framing permits landmarks slightly outside the normalized frame (`-0.08` to `1.08`).
- Hold audio falls from 100% toward a 15% minimum.
- Mountain scores torso alignment, relaxed straight arms, level knees, and compact stance.
- Warrior II scores horizontal straight arms, wide knees, leg shape when ankles are visible, and upright torso.
- Tree scores one raised/bent leg and upright torso; either supporting side is accepted.

## Android starting point

The native Android prototype currently provides:

- Jetpack Compose alarm list and editor.
- Alarm name, time, weekdays, enabled state, sound, vibration, and snooze settings.
- Three-step default routine and eight pose choices.
- Free/Pro presentation and duration selection.
- All eight pose illustrations in Android resources.
- Local alarm persistence in private `SharedPreferences`.
- Front-camera permission flow and on-device MediaPipe camera spike.
- No network permission and no video upload path.

Android camera framing currently uses a stricter `0.60` landmark confidence baseline. It remains a technical camera spike and is not yet wired to the configured multi-pose hold/audio routine.

## Known gaps at this baseline

- Android does not schedule or fire real alarms.
- No lock-screen, killed-app, reboot, exact-alarm, or foreground-service behavior exists.
- Android camera test does not yet execute the configured pose sequence or dynamic audio loop.
- Browser alarms are an interaction prototype, not OS alarms.
- Production alarm sounds and vibration patterns are not implemented.
- Onboarding, permission readiness, failure fallback, purchases, history, analytics, and release work remain.
- iOS has not been started.

## Pose asset inventory

The same eight transparent pose illustrations exist in browser and Android resources:

- Mountain
- Warrior II
- Tree
- Chair
- Forward Fold
- Triangle
- Goddess
- Wide-Legged Fold

## Manual regression checklist

### Browser alarm editor

- [ ] Alarm list opens without an error.
- [ ] Add and edit alarm flows open.
- [ ] Hour and minute wheels wrap continuously.
- [ ] Alarm name and weekdays can be changed.
- [ ] Sound, vibration, and snooze controls respond.
- [ ] Each pose card scrolls horizontally.
- [ ] Mountain, Warrior II, and Tree can be selected.
- [ ] All eight pose illustrations are visible.
- [ ] Locked poses visibly remain Pro-only.
- [ ] The free 20-second duration works.
- [ ] Cancel and Save return to the alarm list correctly.

### Browser camera routine

- [ ] Camera remains off before **Start camera** is pressed.
- [ ] Privacy copy is visible before activation.
- [ ] One press starts the camera, model, routine, and alarm loop.
- [ ] Live preview uses the front camera without excessive zoom.
- [ ] Pose guide remains restrained and readable.
- [ ] Live body tracking follows visible landmarks.
- [ ] A valid pose starts the countdown.
- [ ] Alarm volume fades while holding.
- [ ] Brief tracking loss does not immediately reject the pose.
- [ ] Sustained pose loss pauses the timer and raises the alarm.
- [ ] Completing a pose advances automatically after three seconds.
- [ ] Completing the final pose stops the alarm.
- [ ] Returning to the editor preserves the draft being tested.

### Android prototype

- [ ] App launches on the Galaxy S25.
- [ ] Alarm can be created, edited, enabled, and disabled.
- [ ] Saved alarms survive closing and reopening the app.
- [ ] All eight pose illustrations render.
- [ ] Test routine opens the camera permission flow.
- [ ] Camera preview and landmark detection run on-device.
- [ ] Returning from the camera restores the editor draft.

## Bucket 0 exit criteria

- Current behavior and thresholds are documented.
- Browser and Android pose assets are present.
- Browser type/build checks pass.
- The cheapest available Android compile check is recorded.
- This baseline is committed to Git before Bucket 1 changes begin.

## Automated validation at baseline

- Browser production build: passed with `npm run build`.
- Browser TypeScript check: passed with `tsc --noEmit` during the preceding camera checkpoint.
- Pose assets: all eight browser files exist, all eight Android files exist, and each browser/Android pair is byte-for-byte identical.
- Android Kotlin compile: attempted with an installed Java 17 runtime, but the sandbox cannot create Gradle's lock file in the read-only user Gradle cache. This is an environment limitation rather than a reported Kotlin compiler failure; device/Android Studio compilation remains part of the manual checkpoint.

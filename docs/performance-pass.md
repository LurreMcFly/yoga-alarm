# Android responsiveness pass — 2026-09-05

## Changes

- Done exits the routine; camera retry recreates the camera without discarding held time.
  Completing the routine releases the camera and stops audio/vibration immediately.
- CameraX binds the front-camera preview independently of model initialization, which
  now runs on the analysis worker. Startup distinguishes preview readiness from pose
  detection readiness and offers retry if no frames arrive within ten seconds.
- Analysis is limited to at most one frame per 67 ms (about 15 FPS). Camera preview
  is not throttled. Frames are skipped before bitmap copying/rotation.
- The RGBA input bitmap is reused; padded rows use a reusable packed buffer.
  Sequential MediaPipe VIDEO inference runs on the same worker, retains tracking,
  and permits deterministic release of each transformed image after inference.
- The full CPU model, 640×480 analysis target, front camera, selfie mirroring,
  FIT_CENTER preview, pose-scoring thresholds, and optional ankle handling remain.
- Routine processing starts/stops with a session and pauses in the background.
  Results older than 900 ms are rejected; stalled frames pause progress and offer retry.
  Score smoothing is based on elapsed time, calibrated to the old coefficients at 30 FPS.
- Landmark frames, progress/volume, and text/status are separate state streams.
  Landmark updates only update overlays; progress updates are collected by progress
  indicators and audio, rather than the whole screen. Guide geometry is cached per pose.
- Real alarms retain one service-owned audio player through camera startup and retries.
  Preparation is asynchronous. The UI binds to control volume; the service retains
  alarm audio/vibration and returns to full volume when the routine is backgrounded.
  Notification snooze explicitly stops playback even while the service is bound.
- Sound choices scroll. Preview toggles off, stops after ten seconds, and stops on
  dismissal/backgrounding. Audio errors appear in the sound sheet.
- Editor drafts and pending test configurations survive saved-instance-state restoration.
  Saving/enabling reports time until ringing; failed scheduling saves the alarm disabled
  and displays an error. Automatic startup rescheduling preserves pending snoozes.
- Paused routines retain the countdown and progress ring. Transitions display the next
  pose's guide and instruction during the existing three seconds. Framing hints use the
  scoring visibility rules and never require optional ankles or Tree hand positions.
- Nine PNG resources became lossless WebP resources. Decoded RGBA pixels were compared
  byte for byte; total source asset savings are 277,463 bytes (32.2% of those images).
- API 26 uses the legacy lock-screen/wake flags and a dark navigation bar; light
  navigation styling is qualified for API 27+. The manifest explicitly makes rear
  camera/autofocus optional while keeping the front camera required.

## Release size

Use `arm64-size-reference.json` for an English, arm64, API 36, 480-dpi reference device.
This is a reference specification, not a reading from a connected Galaxy S25.

The final release measurement in this pass was approximately:

| Metric | Size |
| --- | --- |
| Universal release AAB | 41.4 MiB |
| Selected device APKs, before download compression | 31.8 MiB |
| bundletool estimated compressed download | 23.1 MiB (24,224,340 bytes) |

The older approximately 79.3 MiB debug APK contains debug code and all four native
architectures. Comparing that with a device-specific release is not a measurement
of the speedup or size reduction from these source changes.

R8 minification and resource shrinking were already enabled and remain enabled.
All sounds and the full pose model remain bundled for offline alarm operation.
The existing sound loops are roughly 27–52 seconds, already compressed with VBR MP3.
Further shortening/re-encoding needs an audible quality and loop-boundary comparison.
The Lite model remains a candidate for a separate handset comparison; no accuracy
claim or model replacement was made without close-range, dim-bedroom testing.

Reproduce with the existing JDK, Android SDK, and Google's bundletool 1.18.1:

```bash
JAVA_HOME=/home/jolu/.cache/traininglog-build-tools/jdk-17.0.19+10 ./gradlew :app:bundleRelease
java -jar /path/to/bundletool-all-1.18.1.jar build-apks \
  --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=/tmp/yoga-arm64.apks \
  --device-spec=docs/arm64-size-reference.json
java -jar /path/to/bundletool-all-1.18.1.jar get-size total \
  --apks=/tmp/yoga-arm64.apks \
  --device-spec=docs/arm64-size-reference.json
```

Measurement APKs use local debug signing. The release AAB has no upload-signing
configuration and is not a Play-ready signed release.

## Validation and remaining device checks

Final validation succeeded: `:app:testDebugUnitTest` (23 tests, zero failures),
`:app:assembleDebug`, `:app:bundleRelease`, and `:app:lintDebug` (zero errors,
36 warnings, mainly existing dependency/platform/style recommendations).
Merged release manifest inspection confirms the required front camera and no
INTERNET permission. All nine compressed resources passed decoded-pixel equality checks.

Automated tests cover stale frames, background pause/resume, late callbacks after stop,
camera retry, startup timeout, completion, next-pose transitions, draft serialization,
and comparable entry timing at 15 and 30 FPS, plus existing gate/scheduling tests.

No Android device was connected during this pass. Actual startup latency, memory/GC
improvement, overlay alignment, on-device accessibility/layout, and sound continuity
remain unmeasured. Run the expanded `android-reliability-checklist.md` on the S25 and
an older device before release. In particular, compare all eight poses at a distance
where the selfie preview remains readable, under normal and dim bedroom lighting.

References: [MediaPipe Android guide](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker/android),
[Android app size guidance](https://developer.android.com/topic/performance/reduce-apk-size),
[MediaPlayer preparation](https://developer.android.com/media/platform/mediaplayer/basics).

# Yoga Alarm — Pose Lab

Android camera and body-landmark spike for the Yoga Alarm prototype.

## Browser tester

The temporary browser companion lives in `web-tester/`. It mirrors the three-pose interaction and supports both a live webcam and a no-camera simulation mode.

```bash
cd web-tester
pnpm run dev
```

Open `http://localhost:3000/`. Changes to the browser tester update automatically while the development server is running.

The browser tester does not validate Android alarm scheduling, lock-screen behavior, or reboot reliability.

## Current scope

- Mirrored front-camera preview
- On-device MediaPipe Pose Landmarker (Full model, CPU)
- Live skeleton overlay
- Full-body framing status
- Result-rate and inference-latency instrumentation

Pose classification, hold timing, audio fading, and scheduled alarms are intentionally not included yet.

## Run

Open the project in Android Studio, select a physical Android device, and run the `app` configuration.

Command-line build:

```bash
./gradlew assembleDebug
```

Install an existing build:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Galaxy S25 test checkpoint

Record result FPS, latency, framing behavior, and any visible skeleton lag for each case.

| Condition | Distance | Full body found? | FPS | Latency | Notes |
|---|---:|---|---:|---:|---|
| Bright room | 1.5 m | | | | |
| Bright room | 2 m | | | | |
| Bright room | 3 m | | | | |
| Typical bedroom light | 2 m | | | | |
| Dim bedroom light | 2 m | | | | |
| Loose clothing | 2 m | | | | |
| Side-facing body | 2 m | | | | |
| Head partly outside frame | 2 m | | | | |
| Ankles outside frame | 2 m | | | | |

Also verify that backgrounding and reopening the app restores the camera, and that ten minutes of continuous use does not freeze or crash.

'use client';

import type { NormalizedLandmark, PoseLandmarker } from '@mediapipe/tasks-vision';
import { useCallback, useEffect, useRef, useState, type MutableRefObject } from 'react';

type Mode = 'camera' | 'simulation';
type CameraState = 'idle' | 'loading' | 'ready' | 'error';
type SessionPhase = 'idle' | 'ready' | 'finding' | 'holding' | 'paused' | 'transition' | 'complete';

type AlarmAudio = {
  context: AudioContext;
  gain: GainNode;
  oscillators: OscillatorNode[];
};

type PoseEvaluation = {
  score: number;
  fullBody: boolean;
};

const routine = [
  { name: 'Mountain', cue: 'Stand tall with your arms relaxed', score: scoreMountain },
  { name: 'Warrior II', cue: 'Reach wide and bend either front knee', score: scoreWarriorTwo },
  { name: 'Tree', cue: 'Balance with one foot lifted', score: scoreTree },
] as const;

const skeletonConnections = [
  [11, 12], [11, 13], [13, 15], [12, 14], [14, 16],
  [11, 23], [12, 24], [23, 24], [23, 25], [25, 27],
  [27, 29], [29, 31], [24, 26], [26, 28], [28, 30], [30, 32],
] as const;

export default function Home() {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const landmarkerRef = useRef<PoseLandmarker | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const audioRef = useRef<AlarmAudio | null>(null);
  const animationRef = useRef<number | null>(null);
  const lastVideoTimeRef = useRef(-1);
  const lastInferenceAtRef = useRef(0);
  const previousResultAtRef = useRef(0);
  const previousTickAtRef = useRef(0);
  const detectedRef = useRef(false);
  const foundSinceRef = useRef<number | null>(null);
  const lostSinceRef = useRef<number | null>(null);
  const simulatedHoldRef = useRef(false);
  const modeRef = useRef<Mode>('camera');
  const phaseRef = useRef<SessionPhase>('idle');
  const poseIndexRef = useRef(0);
  const holdMsRef = useRef(0);
  const holdDurationRef = useRef(20_000);
  const transitionEndsAtRef = useRef(0);
  const soundEnabledRef = useRef(true);
  const displayUpdatedAtRef = useRef(0);
  const scoreRef = useRef(0);
  const fullBodyRef = useRef(false);
  const alarmLevelRef = useRef(0);

  const [mode, setMode] = useState<Mode>('camera');
  const [cameraState, setCameraState] = useState<CameraState>('idle');
  const [phase, setPhase] = useState<SessionPhase>('idle');
  const [poseIndex, setPoseIndex] = useState(0);
  const [holdMs, setHoldMs] = useState(0);
  const [durationSeconds, setDurationSeconds] = useState(20);
  const [score, setScore] = useState(0);
  const [fullBody, setFullBody] = useState(false);
  const [fps, setFps] = useState(0);
  const [latency, setLatency] = useState(0);
  const [transitionRemaining, setTransitionRemaining] = useState(3);
  const [alarmLevel, setAlarmLevel] = useState(0);
  const [soundEnabled, setSoundEnabled] = useState(true);
  const [simulating, setSimulating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const setSessionPhase = useCallback((next: SessionPhase) => {
    phaseRef.current = next;
    setPhase(next);
  }, []);

  const setCurrentPose = useCallback((next: number) => {
    poseIndexRef.current = next;
    setPoseIndex(next);
  }, []);

  const stopCamera = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
  }, []);

  const stopAudio = useCallback(() => {
    const audio = audioRef.current;
    if (!audio) return;
    audio.oscillators.forEach((oscillator) => oscillator.stop());
    void audio.context.close();
    audioRef.current = null;
    alarmLevelRef.current = 0;
    setAlarmLevel(0);
  }, []);

  const setAudioTarget = useCallback((level: number) => {
    const clamped = clamp(level);
    const roundedLevel = Math.round(clamped * 100);
    if (roundedLevel !== alarmLevelRef.current) {
      alarmLevelRef.current = roundedLevel;
      setAlarmLevel(roundedLevel);
    }
    const audio = audioRef.current;
    if (!audio) return;
    const gain = soundEnabledRef.current ? 0.085 * clamped : 0.0001;
    audio.gain.gain.setTargetAtTime(Math.max(gain, 0.0001), audio.context.currentTime, 0.22);
  }, []);

  const startAudio = useCallback(() => {
    if (!soundEnabledRef.current || audioRef.current) return;
    const context = new AudioContext();
    const gain = context.createGain();
    gain.gain.value = 0.0001;
    gain.connect(context.destination);

    const oscillators = [432, 576].map((frequency, index) => {
      const oscillator = context.createOscillator();
      const voiceGain = context.createGain();
      oscillator.type = index === 0 ? 'triangle' : 'sine';
      oscillator.frequency.value = frequency;
      voiceGain.gain.value = index === 0 ? 0.72 : 0.28;
      oscillator.connect(voiceGain).connect(gain);
      oscillator.start();
      return oscillator;
    });
    audioRef.current = { context, gain, oscillators };
  }, []);

  const resetRoutine = useCallback(() => {
    detectedRef.current = false;
    foundSinceRef.current = null;
    lostSinceRef.current = null;
    holdMsRef.current = 0;
    setHoldMs(0);
    simulatedHoldRef.current = false;
    setSimulating(false);
    scoreRef.current = 0;
    setScore(0);
    setCurrentPose(0);
    setSessionPhase(cameraState === 'ready' ? 'ready' : 'idle');
    stopAudio();
  }, [cameraState, setCurrentPose, setSessionPhase, stopAudio]);

  const startRoutine = useCallback(() => {
    startAudio();
    holdMsRef.current = 0;
    setHoldMs(0);
    simulatedHoldRef.current = false;
    setSimulating(false);
    setCurrentPose(0);
    detectedRef.current = false;
    setSessionPhase('finding');
  }, [setCurrentPose, setSessionPhase, startAudio]);

  const startCamera = useCallback(async () => {
    setCameraState('loading');
    setError(null);
    try {
      const [{ FilesetResolver, PoseLandmarker }, stream] = await Promise.all([
        import('@mediapipe/tasks-vision'),
        navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 720 } },
          audio: false,
        }),
      ]);
      streamRef.current = stream;
      if (!videoRef.current) throw new Error('Camera preview is unavailable.');
      videoRef.current.srcObject = stream;
      await videoRef.current.play();

      const vision = await FilesetResolver.forVisionTasks(`${window.location.origin}/wasm`);
      landmarkerRef.current = await PoseLandmarker.createFromOptions(vision, {
        baseOptions: {
          modelAssetPath: `${window.location.origin}/models/pose_landmarker_full.task`,
          delegate: 'CPU',
        },
        runningMode: 'VIDEO',
        numPoses: 1,
        minPoseDetectionConfidence: 0.5,
        minPosePresenceConfidence: 0.5,
        minTrackingConfidence: 0.5,
      });
      setCameraState('ready');
      setSessionPhase('ready');
    } catch (caught) {
      stopCamera();
      setCameraState('error');
      setSessionPhase('idle');
      setError(caught instanceof Error ? caught.message : 'Could not start the camera.');
    }
  }, [setSessionPhase, stopCamera]);

  const selectMode = useCallback((next: Mode) => {
    if (next === modeRef.current) return;
    stopAudio();
    if (next === 'simulation') {
      stopCamera();
      landmarkerRef.current?.close();
      landmarkerRef.current = null;
      setCameraState('ready');
      setSessionPhase('ready');
      fullBodyRef.current = true;
      setFullBody(true);
    } else {
      setCameraState('idle');
      setSessionPhase('idle');
      fullBodyRef.current = false;
      setFullBody(false);
    }
    modeRef.current = next;
    setMode(next);
    setError(null);
    detectedRef.current = false;
    foundSinceRef.current = null;
    lostSinceRef.current = null;
    setCurrentPose(0);
    holdMsRef.current = 0;
    setHoldMs(0);
    simulatedHoldRef.current = false;
    setSimulating(false);
  }, [setCurrentPose, setSessionPhase, stopAudio, stopCamera]);

  const toggleSound = useCallback(() => {
    const next = !soundEnabledRef.current;
    soundEnabledRef.current = next;
    setSoundEnabled(next);
    if (!next) stopAudio();
  }, [stopAudio]);

  useEffect(() => {
    const tick = (now: number) => {
      const deltaMs = previousTickAtRef.current === 0 ? 0 : Math.min(now - previousTickAtRef.current, 100);
      previousTickAtRef.current = now;

      let currentScore = scoreRef.current;
      let currentFullBody = fullBodyRef.current;
      let currentLandmarks: NormalizedLandmark[] | null = null;

      if (modeRef.current === 'simulation') {
        currentScore = simulatedHoldRef.current ? 0.95 : 0.22;
        currentFullBody = true;
        scoreRef.current = currentScore;
        fullBodyRef.current = true;
      } else {
        const video = videoRef.current;
        const landmarker = landmarkerRef.current;
        if (
          video && landmarker && video.readyState >= 2 &&
          video.currentTime !== lastVideoTimeRef.current &&
          now - lastInferenceAtRef.current >= 66
        ) {
          lastVideoTimeRef.current = video.currentTime;
          lastInferenceAtRef.current = now;
          const startedAt = performance.now();
          const result = landmarker.detectForVideo(video, now);
          const measuredLatency = performance.now() - startedAt;
          currentLandmarks = result.landmarks[0] ?? null;
          const evaluation = routine[poseIndexRef.current].score(currentLandmarks);
          currentScore = evaluation.score;
          currentFullBody = evaluation.fullBody;
          scoreRef.current = currentScore;
          fullBodyRef.current = currentFullBody;
          setLatency(measuredLatency);
          const resultDelta = previousResultAtRef.current === 0 ? 0 : now - previousResultAtRef.current;
          previousResultAtRef.current = now;
          if (resultDelta > 0) setFps((previous) => previous === 0 ? 1000 / resultDelta : previous * 0.8 + (1000 / resultDelta) * 0.2);
          drawSkeleton(canvasRef.current, video, currentLandmarks, detectedRef.current);
        }
      }

      updateDetection(currentScore, now, detectedRef, foundSinceRef, lostSinceRef);

      if (phaseRef.current === 'transition') {
        const remaining = Math.max(0, transitionEndsAtRef.current - now);
        setTransitionRemaining(Math.max(1, Math.ceil(remaining / 1000)));
        if (remaining === 0) {
          setCurrentPose(poseIndexRef.current + 1);
          holdMsRef.current = 0;
          setHoldMs(0);
          detectedRef.current = false;
          setSessionPhase('finding');
        }
      } else if (['finding', 'holding', 'paused'].includes(phaseRef.current)) {
        if (detectedRef.current) {
          if (phaseRef.current !== 'holding') setSessionPhase('holding');
          holdMsRef.current = Math.min(holdDurationRef.current, holdMsRef.current + deltaMs);
          if (holdMsRef.current >= holdDurationRef.current) {
            if (poseIndexRef.current === routine.length - 1) {
              simulatedHoldRef.current = false;
              setSimulating(false);
              setSessionPhase('complete');
              setAudioTarget(0);
              window.setTimeout(stopAudio, 700);
            } else {
              simulatedHoldRef.current = false;
              setSimulating(false);
              setSessionPhase('transition');
              transitionEndsAtRef.current = now + 3_000;
              setTransitionRemaining(3);
            }
          }
        } else {
          const nextPhase = holdMsRef.current > 0 ? 'paused' : 'finding';
          if (phaseRef.current !== nextPhase) setSessionPhase(nextPhase);
        }
      }

      const progress = holdMsRef.current / holdDurationRef.current;
      const nextAlarmLevel = phaseRef.current === 'complete' || phaseRef.current === 'ready' || phaseRef.current === 'idle'
        ? 0
        : phaseRef.current === 'transition'
          ? 0.35
          : phaseRef.current === 'holding'
            ? Math.max(0.15, 1 - progress * 0.85)
            : 1;
      setAudioTarget(nextAlarmLevel);

      if (now - displayUpdatedAtRef.current > 90) {
        displayUpdatedAtRef.current = now;
        setScore(currentScore);
        setFullBody(currentFullBody);
        setHoldMs(holdMsRef.current);
      }
      animationRef.current = requestAnimationFrame(tick);
    };

    animationRef.current = requestAnimationFrame(tick);
    return () => {
      if (animationRef.current !== null) cancelAnimationFrame(animationRef.current);
    };
  }, [setAudioTarget, setCurrentPose, setSessionPhase, stopAudio]);

  useEffect(() => () => {
    stopCamera();
    landmarkerRef.current?.close();
    stopAudio();
  }, [stopAudio, stopCamera]);

  const currentPose = routine[poseIndex];
  const progress = phase === 'complete' ? 1 : (poseIndex + holdMs / holdDurationRef.current) / routine.length;
  const remainingSeconds = Math.max(0, Math.ceil((holdDurationRef.current - holdMs) / 1000));
  const primaryLabel = getPrimaryLabel(cameraState, phase, mode, simulating);
  const status = getStatus(phase, fullBody, detectedRef.current, transitionRemaining, error, currentPose.cue);

  const handlePrimaryAction = () => {
    if (cameraState === 'idle' && mode === 'camera') void startCamera();
    else if (cameraState === 'error' && mode === 'camera') void startCamera();
    else if (phase === 'ready') startRoutine();
    else if (phase === 'complete') resetRoutine();
    else if (mode === 'simulation' && ['finding', 'holding', 'paused'].includes(phase)) {
      const next = !simulatedHoldRef.current;
      simulatedHoldRef.current = next;
      setSimulating(next);
    }
  };

  return (
    <main className="lab-shell">
      <header className="lab-header">
        <div className="brand-lockup">
          <span className="brand-mark" aria-hidden="true">Y</span>
          <div><p className="eyebrow">Yoga Alarm</p><h1>Browser Lab</h1></div>
        </div>
        <span className="local-badge"><i />Local tester</span>
      </header>

      <section className="workspace">
        <div className="stage-column">
          <div className="stage-toolbar">
            <div>
              <p className="eyebrow">Live experience</p>
              <h2>See the wake-up loop before it reaches your phone.</h2>
            </div>
            <span className="privacy-note">Camera stays in this browser</span>
          </div>

          <div className={`camera-stage ${cameraState === 'ready' && mode === 'camera' ? 'camera-live' : ''}`}>
            <video ref={videoRef} className="camera-video" muted playsInline aria-label="Mirrored camera preview" />
            <canvas ref={canvasRef} className="skeleton-canvas" aria-hidden="true" />
            <div className="camera-glow" />
            <div className="stage-progress">
              <span>{Math.min(poseIndex + 1, 3)} / 3</span>
              <div className="progress-track"><i style={{ width: `${progress * 100}%` }} /></div>
              <span className="alarm-readout">Alarm {alarmLevel}%</span>
            </div>

            <div className={`detection-pill ${fullBody ? 'detected' : ''}`}>
              <i />{mode === 'simulation' ? 'Simulation mode' : fullBody ? 'Full body detected' : 'Full body not visible'}
            </div>

            {cameraState !== 'ready' || mode === 'simulation' ? (
              <div className="framing-guide" aria-hidden="true">
                <span className="corner corner-tl" /><span className="corner corner-tr" />
                <span className="corner corner-bl" /><span className="corner corner-br" />
                <div className="guide-person"><i className="guide-head" /><i className="guide-body" /></div>
              </div>
            ) : null}

            <div className={`stage-copy phase-${phase}`}>
              <p className="pose-label">
                {phase === 'complete' ? 'Routine complete' : phase === 'transition' ? 'Coming up' : currentPose.name}
              </p>
              {phase === 'holding' ? <strong className="countdown">{remainingSeconds}</strong> : null}
              <strong>{phase === 'complete' ? 'Good morning ☀️' : phase === 'transition' ? `Next: ${routine[Math.min(poseIndex + 1, 2)].name}` : status.title}</strong>
              <span>{phase === 'complete' ? `${routine.length * durationSeconds} seconds of movement completed` : phase === 'transition' ? `Starting in ${transitionRemaining}` : status.detail || currentPose.cue}</span>
              {cameraState === 'ready' && phase !== 'idle' && phase !== 'ready' && phase !== 'complete' ? (
                <div className="confidence-row"><i style={{ width: `${score * 100}%` }} /><span>{Math.round(score * 100)}% pose match</span></div>
              ) : null}
            </div>

            <div className="stage-footer">
              <span className="metric"><b>{mode === 'camera' && fps ? fps.toFixed(1) : '—'}</b> FPS</span>
              <button
                type="button"
                className="primary-button"
                disabled={cameraState === 'loading' || phase === 'transition'}
                onClick={handlePrimaryAction}
              >
                {primaryLabel}
              </button>
              <span className="metric metric-right"><b>{mode === 'camera' && latency ? latency.toFixed(0) : '—'}</b> ms</span>
            </div>
          </div>
        </div>

        <aside className="control-panel">
          <div className="panel-heading"><p className="eyebrow">Test routine</p><span>{routine.length * durationSeconds} seconds</span></div>
          <ol className="routine-list">
            {routine.map((pose, index) => (
              <li key={pose.name} className={index === poseIndex && phase !== 'complete' ? 'active' : index < poseIndex || phase === 'complete' ? 'done' : ''}>
                <span className="pose-index">0{index + 1}</span>
                <div><b>{pose.name}</b><small>{index === poseIndex && phase !== 'complete' ? 'Current' : index < poseIndex || phase === 'complete' ? 'Complete' : 'Up next'}</small></div>
                <span className="pose-duration">{durationSeconds} sec</span>
              </li>
            ))}
          </ol>

          <div className="tester-card">
            <p className="eyebrow">Input</p>
            <div className="segmented-control">
              <button className={mode === 'camera' ? 'selected' : ''} onClick={() => selectMode('camera')}>Live camera</button>
              <button className={mode === 'simulation' ? 'selected' : ''} onClick={() => selectMode('simulation')}>Simulation</button>
            </div>
            <p className="control-help">Simulation lets you test timing and audio without matching a real pose.</p>
          </div>

          <div className="tester-card">
            <p className="eyebrow">Hold time</p>
            <div className="duration-buttons">
              {[5, 10, 20].map((seconds) => (
                <button
                  key={seconds}
                  className={durationSeconds === seconds ? 'selected' : ''}
                  disabled={!['idle', 'ready', 'complete'].includes(phase)}
                  onClick={() => {
                    setDurationSeconds(seconds);
                    holdDurationRef.current = seconds * 1000;
                  }}
                >{seconds}s</button>
              ))}
            </div>
            <button type="button" className="sound-toggle" onClick={toggleSound} aria-pressed={soundEnabled}>
              <span><i className={soundEnabled ? 'on' : ''} /></span>Alarm sound {soundEnabled ? 'on' : 'off'}
            </button>
          </div>

          {phase !== 'idle' && phase !== 'ready' ? <button className="reset-button" onClick={resetRoutine}>Reset routine</button> : null}
          <p className="panel-footnote">This companion tests the interaction. Android alarm scheduling, lock-screen behavior, and reboot reliability still require the device build.</p>
        </aside>
      </section>
    </main>
  );
}

function getPrimaryLabel(cameraState: CameraState, phase: SessionPhase, mode: Mode, simulating: boolean) {
  if (cameraState === 'loading') return 'Loading pose model…';
  if ((cameraState === 'idle' || cameraState === 'error') && mode === 'camera') return cameraState === 'error' ? 'Try camera again' : 'Start camera test';
  if (phase === 'ready') return 'Start routine';
  if (phase === 'complete') return 'Run it again';
  if (phase === 'transition') return 'Get ready';
  if (mode === 'simulation') return simulating ? 'Break simulated pose' : 'Simulate current pose';
  return phase === 'holding' ? 'Keep holding' : 'Move into pose';
}

function getStatus(phase: SessionPhase, fullBody: boolean, detected: boolean, transition: number, error: string | null, poseCue: string) {
  if (error) return { title: 'Camera unavailable', detail: error };
  if (phase === 'idle') return { title: 'Stand where your full body is visible', detail: 'The live camera preview will appear here' };
  if (phase === 'ready') return { title: 'You’re ready', detail: 'Start when you want to feel the complete loop' };
  if (phase === 'transition') return { title: `Starting in ${transition}`, detail: '' };
  if (!fullBody) return { title: 'Step back a little', detail: 'Keep your head and ankles inside the frame' };
  if (phase === 'holding' || detected) return { title: 'Pose found ✓', detail: 'Stay with it while the alarm fades' };
  if (phase === 'paused') return { title: 'Hold the pose', detail: 'The timer is paused and the alarm is returning' };
  if (phase === 'complete') return { title: 'Good morning', detail: '' };
  return { title: 'Move into the pose', detail: poseCue };
}

function updateDetection(
  score: number,
  now: number,
  detected: MutableRefObject<boolean>,
  foundSince: MutableRefObject<number | null>,
  lostSince: MutableRefObject<number | null>,
) {
  if (score >= 0.74) {
    lostSince.current = null;
    foundSince.current ??= now;
    if (now - foundSince.current >= 450) detected.current = true;
  } else if (score < 0.58) {
    foundSince.current = null;
    lostSince.current ??= now;
    if (now - lostSince.current >= 350) detected.current = false;
  }
}

function scoreMountain(landmarks: NormalizedLandmark[] | null): PoseEvaluation {
  if (!landmarks) return { score: 0, fullBody: false };
  const fullBody = isFullBodyVisible(landmarks);
  const shoulderWidth = Math.max(distance(landmarks[11], landmarks[12]), 0.05);
  const shoulderMid = midpoint(landmarks[11], landmarks[12]);
  const hipMid = midpoint(landmarks[23], landmarks[24]);
  const upright = clamp(1 - Math.abs(shoulderMid.x - hipMid.x) / (shoulderWidth * 0.65));
  const straightLegs = average(greaterScore(angle(landmarks[23], landmarks[25], landmarks[27]), 145, 170), greaterScore(angle(landmarks[24], landmarks[26], landmarks[28]), 145, 170));
  const armsDown = average(greaterScore(landmarks[15].y - landmarks[11].y, 0.12, 0.35), greaterScore(landmarks[16].y - landmarks[12].y, 0.12, 0.35));
  const stanceRatio = distance(landmarks[27], landmarks[28]) / shoulderWidth;
  const compactStance = rangeScore(stanceRatio, 0.25, 0.55, 1.45, 1.8);
  return { score: fullBody ? weighted([upright, straightLegs, armsDown, compactStance], [0.25, 0.3, 0.25, 0.2]) : 0, fullBody };
}

function scoreWarriorTwo(landmarks: NormalizedLandmark[] | null): PoseEvaluation {
  if (!landmarks) return { score: 0, fullBody: false };
  const fullBody = isFullBodyVisible(landmarks);
  const shoulderWidth = Math.max(distance(landmarks[11], landmarks[12]), 0.05);
  const shoulderMid = midpoint(landmarks[11], landmarks[12]);
  const hipMid = midpoint(landmarks[23], landmarks[24]);
  const horizontalArms = average(clamp(1 - Math.abs(landmarks[15].y - landmarks[11].y) / 0.2), clamp(1 - Math.abs(landmarks[16].y - landmarks[12].y) / 0.2));
  const straightArms = average(greaterScore(angle(landmarks[11], landmarks[13], landmarks[15]), 140, 170), greaterScore(angle(landmarks[12], landmarks[14], landmarks[16]), 140, 170));
  const wideLegs = greaterScore(distance(landmarks[27], landmarks[28]) / shoulderWidth, 1.45, 2.3);
  const leftKnee = angle(landmarks[23], landmarks[25], landmarks[27]);
  const rightKnee = angle(landmarks[24], landmarks[26], landmarks[28]);
  const kneePattern = Math.max(average(rangeScore(leftKnee, 70, 90, 140, 155), greaterScore(rightKnee, 140, 168)), average(rangeScore(rightKnee, 70, 90, 140, 155), greaterScore(leftKnee, 140, 168)));
  const upright = clamp(1 - Math.abs(shoulderMid.x - hipMid.x) / (shoulderWidth * 0.85));
  return { score: fullBody ? weighted([horizontalArms, straightArms, wideLegs, kneePattern, upright], [0.24, 0.18, 0.2, 0.24, 0.14]) : 0, fullBody };
}

function scoreTree(landmarks: NormalizedLandmark[] | null): PoseEvaluation {
  if (!landmarks) return { score: 0, fullBody: false };
  const fullBody = isFullBodyVisible(landmarks);
  const hipWidth = Math.max(distance(landmarks[23], landmarks[24]), 0.04);
  const shoulderWidth = Math.max(distance(landmarks[11], landmarks[12]), 0.05);
  const shoulderMid = midpoint(landmarks[11], landmarks[12]);
  const hipMid = midpoint(landmarks[23], landmarks[24]);
  const upright = clamp(1 - Math.abs(shoulderMid.x - hipMid.x) / (shoulderWidth * 0.7));
  const leftStanding = average(greaterScore(angle(landmarks[23], landmarks[25], landmarks[27]), 145, 170), lessScore(angle(landmarks[24], landmarks[26], landmarks[28]), 135, 75), lessScore(distance(landmarks[28], landmarks[25]) / hipWidth, 1.8, 0.55));
  const rightStanding = average(greaterScore(angle(landmarks[24], landmarks[26], landmarks[28]), 145, 170), lessScore(angle(landmarks[23], landmarks[25], landmarks[27]), 135, 75), lessScore(distance(landmarks[27], landmarks[26]) / hipWidth, 1.8, 0.55));
  return { score: fullBody ? weighted([Math.max(leftStanding, rightStanding), upright], [0.78, 0.22]) : 0, fullBody };
}

function isFullBodyVisible(landmarks: NormalizedLandmark[]) {
  return [0, 11, 12, 23, 24, 25, 26, 27, 28].every((index) => {
    const point = landmarks[index];
    return point && (point.visibility ?? 0) >= 0.55 && (point.x >= 0.01 && point.x <= 0.99) && (point.y >= 0.01 && point.y <= 0.99);
  });
}

function drawSkeleton(canvas: HTMLCanvasElement | null, video: HTMLVideoElement, landmarks: NormalizedLandmark[] | null, detected: boolean) {
  if (!canvas) return;
  const width = canvas.clientWidth;
  const height = canvas.clientHeight;
  if (!width || !height) return;
  const dpr = window.devicePixelRatio || 1;
  if (canvas.width !== width * dpr || canvas.height !== height * dpr) {
    canvas.width = width * dpr;
    canvas.height = height * dpr;
  }
  const context = canvas.getContext('2d');
  if (!context) return;
  context.setTransform(dpr, 0, 0, dpr, 0, 0);
  context.clearRect(0, 0, width, height);
  if (!landmarks || !video.videoWidth || !video.videoHeight) return;

  const scale = Math.max(width / video.videoWidth, height / video.videoHeight);
  const drawnWidth = video.videoWidth * scale;
  const drawnHeight = video.videoHeight * scale;
  const offsetX = (width - drawnWidth) / 2;
  const offsetY = (height - drawnHeight) / 2;
  const toCanvas = (point: NormalizedLandmark) => ({ x: offsetX + (1 - point.x) * drawnWidth, y: offsetY + point.y * drawnHeight });
  const color = detected ? '#c9ee73' : '#f0bc6b';

  context.strokeStyle = color;
  context.fillStyle = color;
  context.lineWidth = 4;
  context.lineCap = 'round';
  skeletonConnections.forEach(([startIndex, endIndex]) => {
    const start = landmarks[startIndex];
    const end = landmarks[endIndex];
    if ((start.visibility ?? 0) < 0.35 || (end.visibility ?? 0) < 0.35) return;
    const a = toCanvas(start);
    const b = toCanvas(end);
    context.beginPath();
    context.moveTo(a.x, a.y);
    context.lineTo(b.x, b.y);
    context.stroke();
  });
  landmarks.forEach((landmark) => {
    if ((landmark.visibility ?? 0) < 0.35) return;
    const point = toCanvas(landmark);
    context.beginPath();
    context.arc(point.x, point.y, 4.5, 0, Math.PI * 2);
    context.fill();
  });
}

function angle(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark) {
  const radians = Math.atan2(c.y - b.y, c.x - b.x) - Math.atan2(a.y - b.y, a.x - b.x);
  let degrees = Math.abs(radians * 180 / Math.PI);
  if (degrees > 180) degrees = 360 - degrees;
  return degrees;
}

function distance(a: NormalizedLandmark, b: NormalizedLandmark) {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

function midpoint(a: NormalizedLandmark, b: NormalizedLandmark) {
  return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
}

function greaterScore(value: number, minimum: number, ideal: number) {
  return clamp((value - minimum) / (ideal - minimum));
}

function lessScore(value: number, maximum: number, ideal: number) {
  return clamp((maximum - value) / (maximum - ideal));
}

function rangeScore(value: number, outerMin: number, innerMin: number, innerMax: number, outerMax: number) {
  if (value >= innerMin && value <= innerMax) return 1;
  if (value < innerMin) return clamp((value - outerMin) / (innerMin - outerMin));
  return clamp((outerMax - value) / (outerMax - innerMax));
}

function weighted(values: number[], weights: number[]) {
  return values.reduce((total, value, index) => total + value * weights[index], 0);
}

function average(...values: number[]) {
  return values.reduce((total, value) => total + value, 0) / values.length;
}

function clamp(value: number) {
  return Math.max(0, Math.min(1, value));
}

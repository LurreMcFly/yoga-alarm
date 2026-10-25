'use client';

import type { NormalizedLandmark, PoseLandmarker } from '@mediapipe/tasks-vision';
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type MutableRefObject } from 'react';
import AlarmShell, { createDefaultAlarm, type BrowserAlarm } from './AlarmShell';

type Mode = 'camera' | 'simulation';
type CameraState = 'idle' | 'loading' | 'ready' | 'error';
type SessionPhase = 'idle' | 'ready' | 'finding' | 'holding' | 'paused' | 'transition' | 'complete';

type AlarmAudio = {
  context: AudioContext;
  gain: GainNode;
  player: HTMLAudioElement;
};

type PoseEvaluation = {
  score: number;
  framed: boolean;
};

type GuideAnchor = {
  centerX: number;
  shoulderY: number;
  shoulderWidth: number;
  torsoHeight: number;
};

const routine = [
  { name: 'Mountain', cue: 'Stand tall with your arms relaxed', score: scoreMountain },
  { name: 'Warrior II', cue: 'Reach wide and bend either front knee', score: scoreWarriorTwo },
  { name: 'Tree', cue: 'Balance with one foot lifted', score: scoreTree },
  { name: 'Squat', cue: 'Face sideways, lower your hips to knee height and hold your hands at your chest', score: scoreSquat },
] as const;

const skeletonConnections = [
  [11, 12], [11, 13], [13, 15], [12, 14], [14, 16],
  [11, 23], [12, 24], [23, 24], [23, 25], [25, 27],
  [27, 29], [29, 31], [24, 26], [26, 28], [28, 30], [30, 32],
] as const;

export default function Home() {
  const [surface, setSurface] = useState<'app' | 'routine'>('app');
  const [activeAlarm, setActiveAlarm] = useState<BrowserAlarm>(createDefaultAlarm);
  const [resumeDraft, setResumeDraft] = useState<BrowserAlarm | null>(null);
  const [practice, setPractice] = useState(false);
  const [practiceResult, setPracticeResult] = useState<'complete' | 'cancelled' | null>(null);
  const practiceCompleteRef = useRef<(() => void) | null>(null);
  const cameraRequestRef = useRef(0);
  const activeRoutine = useMemo(() => activeAlarm.routine.map((step) => routine.find((pose) => pose.name === step.pose) ?? routine[0]), [activeAlarm]);
  const videoRef = useRef<HTMLVideoElement>(null);
  const guideCanvasRef = useRef<HTMLCanvasElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const guideAnchorRef = useRef<GuideAnchor | null>(null);
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
  const framedRef = useRef(false);
  const showLandmarksRef = useRef(false);
  const alarmLevelRef = useRef(0);

  const [mode, setMode] = useState<Mode>('camera');
  const [cameraState, setCameraState] = useState<CameraState>('idle');
  const [phase, setPhase] = useState<SessionPhase>('idle');
  const [poseIndex, setPoseIndex] = useState(0);
  const [holdMs, setHoldMs] = useState(0);
  const [durationSeconds, setDurationSeconds] = useState(20);
  const [score, setScore] = useState(0);
  const [framed, setFramed] = useState(false);
  const [showLandmarks, setShowLandmarks] = useState(false);
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
    cameraRequestRef.current += 1;
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
  }, []);

  const stopAudio = useCallback(() => {
    const audio = audioRef.current;
    if (!audio) return;
    audio.player.pause();
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
    const gain = soundEnabledRef.current ? 0.7 * clamped : 0.0001;
    audio.gain.gain.setTargetAtTime(Math.max(gain, 0.0001), audio.context.currentTime, 0.22);
  }, []);

  const startAudio = useCallback(() => {
    if (!soundEnabledRef.current || audioRef.current) return;
    const context = new AudioContext();
    const gain = context.createGain();
    gain.gain.value = 0.0001;
    gain.connect(context.destination);

    const player = new Audio(`/sounds/${activeAlarm.alarmSound}.mp3`);
    player.loop = true;
    context.createMediaElementSource(player).connect(gain);
    audioRef.current = { context, gain, player };
    void player.play().catch(() => {
      if (audioRef.current?.player === player) { stopAudio(); setError('Couldn’t play the alarm sound. Try again.'); }
    });
  }, [activeAlarm.alarmSound, stopAudio]);

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
    const firstDuration = activeAlarm.routine[0]?.duration ?? 20;
    holdDurationRef.current = firstDuration * 1000;
    setDurationSeconds(firstDuration);
    holdMsRef.current = 0;
    setHoldMs(0);
    simulatedHoldRef.current = false;
    setSimulating(false);
    setCurrentPose(0);
    detectedRef.current = false;
    setSessionPhase('finding');
  }, [activeAlarm, setCurrentPose, setSessionPhase, startAudio]);

  const startCamera = useCallback(async () => {
    const requestId = ++cameraRequestRef.current;
    setCameraState('loading');
    setError(null);
    try {
      const portrait = window.matchMedia('(max-width: 600px)').matches;
      const videoConstraints = (portrait
        ? {
            facingMode: 'user',
            width: { ideal: 1280 },
            height: { ideal: 960 },
            aspectRatio: { ideal: 4 / 3 },
            resizeMode: 'none',
          }
        : { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 720 } }) as MediaTrackConstraints;
      const { FilesetResolver, PoseLandmarker } = await import('@mediapipe/tasks-vision');
      if (requestId !== cameraRequestRef.current) return;
      const stream = await navigator.mediaDevices.getUserMedia({ video: videoConstraints, audio: false });
      if (requestId !== cameraRequestRef.current) {
        stream.getTracks().forEach((track) => track.stop());
        return;
      }
      streamRef.current = stream;
      const cameraTrack = stream.getVideoTracks()[0];
      const capabilities = cameraTrack.getCapabilities() as MediaTrackCapabilities & { zoom?: { min: number } };
      if (capabilities.zoom) {
        await cameraTrack.applyConstraints({
          advanced: [{ zoom: capabilities.zoom.min } as MediaTrackConstraintSet],
        }).catch(() => undefined);
      }
      if (requestId !== cameraRequestRef.current) return;
      if (!videoRef.current) throw new Error('Camera preview is unavailable.');
      videoRef.current.srcObject = stream;
      await videoRef.current.play();

      const vision = await FilesetResolver.forVisionTasks(`${window.location.origin}/wasm`);
      if (requestId !== cameraRequestRef.current) return;
      const landmarker = await PoseLandmarker.createFromOptions(vision, {
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
      if (requestId !== cameraRequestRef.current) { landmarker.close(); return; }
      landmarkerRef.current?.close();
      landmarkerRef.current = landmarker;
      setCameraState('ready');
    } catch (caught) {
      if (requestId !== cameraRequestRef.current) return;
      stopCamera();
      stopAudio();
      setCameraState('error');
      setSessionPhase('idle');
      setError(caught instanceof Error ? caught.message : 'Could not start the camera.');
    }
  }, [setSessionPhase, stopAudio, stopCamera]);

  const selectMode = useCallback((next: Mode) => {
    if (next === modeRef.current) return;
    stopAudio();
    if (next === 'simulation') {
      stopCamera();
      landmarkerRef.current?.close();
      landmarkerRef.current = null;
      setCameraState('ready');
      setSessionPhase('ready');
      framedRef.current = true;
      setFramed(true);
    } else {
      setCameraState('idle');
      setSessionPhase('idle');
      framedRef.current = false;
      setFramed(false);
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
      let currentFramed = framedRef.current;
      let currentLandmarks: NormalizedLandmark[] | null = null;

      if (modeRef.current === 'simulation') {
        currentScore = simulatedHoldRef.current ? 0.95 : 0.22;
        currentFramed = true;
        scoreRef.current = currentScore;
        framedRef.current = true;
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
          const evaluation = activeRoutine[poseIndexRef.current].score(currentLandmarks);
          currentScore = evaluation.score;
          currentFramed = evaluation.framed;
          scoreRef.current = currentScore;
          framedRef.current = currentFramed;
          setLatency(measuredLatency);
          const resultDelta = previousResultAtRef.current === 0 ? 0 : now - previousResultAtRef.current;
          previousResultAtRef.current = now;
          if (resultDelta > 0) setFps((previous) => previous === 0 ? 1000 / resultDelta : previous * 0.8 + (1000 / resultDelta) * 0.2);
          drawPoseGuide(guideCanvasRef.current, video, activeRoutine[poseIndexRef.current].name, currentLandmarks, detectedRef.current, guideAnchorRef);
          drawSkeleton(canvasRef.current, video, currentLandmarks, detectedRef.current);
        }
      }

      updateDetection(currentScore, now, detectedRef, foundSinceRef, lostSinceRef);

      if (phaseRef.current === 'transition') {
        const remaining = Math.max(0, transitionEndsAtRef.current - now);
        setTransitionRemaining(Math.max(1, Math.ceil(remaining / 1000)));
        if (remaining === 0) {
          const nextPose = poseIndexRef.current + 1;
          setCurrentPose(nextPose);
          const nextDuration = activeAlarm.routine[nextPose]?.duration ?? 20;
          holdDurationRef.current = nextDuration * 1000;
          setDurationSeconds(nextDuration);
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
            if (poseIndexRef.current === activeRoutine.length - 1) {
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
        setFramed(currentFramed);
        setHoldMs(holdMsRef.current);
      }
      animationRef.current = requestAnimationFrame(tick);
    };

    animationRef.current = requestAnimationFrame(tick);
    return () => {
      if (animationRef.current !== null) cancelAnimationFrame(animationRef.current);
    };
  }, [activeAlarm, activeRoutine, setAudioTarget, setCurrentPose, setSessionPhase, stopAudio]);

  useEffect(() => () => {
    stopCamera();
    landmarkerRef.current?.close();
    stopAudio();
  }, [stopAudio, stopCamera]);

  useEffect(() => {
    if (mode !== 'camera' || surface !== 'routine') return;
    const redraw = () => drawPoseGuide(guideCanvasRef.current, videoRef.current, activeRoutine[poseIndex].name, null, framed, guideAnchorRef);
    redraw();
    window.addEventListener('resize', redraw);
    return () => window.removeEventListener('resize', redraw);
  }, [activeRoutine, cameraState, framed, mode, poseIndex, surface]);

  const currentPose = activeRoutine[poseIndex] ?? activeRoutine[0];
  const progress = phase === 'complete' ? 1 : (poseIndex + holdMs / holdDurationRef.current) / activeRoutine.length;
  const remainingSeconds = Math.max(0, Math.ceil((holdDurationRef.current - holdMs) / 1000));
  const primaryLabel = getPrimaryLabel(cameraState, phase, mode, simulating);
  const status = getStatus(phase, framed, detectedRef.current, score, transitionRemaining, error, currentPose.cue, currentPose.name);

  const handlePrimaryAction = () => {
    if ((cameraState === 'idle' || cameraState === 'error') && mode === 'camera') {
      if (window.matchMedia('(max-width: 600px)').matches && !document.fullscreenElement) {
        void document.documentElement.requestFullscreen().catch(() => undefined);
      }
      guideAnchorRef.current = null;
      startRoutine();
      void startCamera();
    }
    else if (phase === 'ready') startRoutine();
    else if (phase === 'complete') resetRoutine();
    else if (mode === 'simulation' && ['finding', 'holding', 'paused'].includes(phase)) {
      const next = !simulatedHoldRef.current;
      simulatedHoldRef.current = next;
      setSimulating(next);
    }
  };

  const holdProgress = Math.min(1, holdMs / holdDurationRef.current);

  const handleTestConfiguredRoutine = (alarm: BrowserAlarm, editorAlarm: BrowserAlarm = alarm) => {
    if (alarm.routine.some((step) => !routine.some((pose) => pose.name === step.pose))) {
      window.alert('Camera testing for Pro poses is available in the Android app.');
      return;
    }
    resetRoutine();
    practiceCompleteRef.current = null;
    setPractice(false);
    soundEnabledRef.current = alarm.sound;
    setSoundEnabled(alarm.sound);
    setActiveAlarm(alarm);
    setResumeDraft(editorAlarm);
    const firstDuration = alarm.routine[0]?.duration ?? 20;
    setDurationSeconds(firstDuration);
    holdDurationRef.current = firstDuration * 1000;
    setSurface('routine');
  };

  const handleReturnToApp = () => {
    if (practice) {
      setPracticeResult(phase === 'complete' ? 'complete' : 'cancelled');
      if (phase === 'complete') practiceCompleteRef.current?.();
    }
    practiceCompleteRef.current = null;
    stopCamera();
    resetRoutine();
    setCameraState('idle');
    selectMode('camera');
    if (document.fullscreenElement) void document.exitFullscreen().catch(() => undefined);
    setSurface('app');
  };

  return (
    <>
    <div hidden={surface !== 'app'}>
      <AlarmShell
        resumeDraft={resumeDraft}
        active={surface === 'app'}
        onTestRoutine={handleTestConfiguredRoutine}
        practiceResult={practiceResult}
        onPractice={(alarm, onComplete) => {
          handleTestConfiguredRoutine({ ...alarm, sound: false, routine: [{ ...alarm.routine[0], duration: 5 }] }, alarm);
          practiceCompleteRef.current = onComplete;
          setPractice(true);
          setPracticeResult(null);
        }}
      />
    </div>
    {surface === 'routine' ? (
    <main className="minimal-routine">
      <div className={`camera-stage ${cameraState === 'ready' && mode === 'camera' ? 'camera-live' : ''}`}>
        <video ref={videoRef} className="camera-video" muted playsInline aria-label="Mirrored camera preview" />
        <canvas ref={guideCanvasRef} className="pose-guide-canvas" aria-hidden="true" />
        <canvas ref={canvasRef} className="skeleton-canvas" aria-hidden="true" />
        {practice && (cameraState !== 'ready' || mode === 'simulation') && phase !== 'complete' ? <img className="practice-pose-guide" src={`/poses/${currentPose.name.toLowerCase().replaceAll(' ', '-').replace('ii', 'two')}.png`} alt={`${currentPose.name} pose to practise`} /> : null}

        <button type="button" className="routine-back" onClick={handleReturnToApp}>{practice ? '‹ Back to setup' : '‹ Alarm'}</button>
        <div className="minimal-routine-progress">
          <span>{Math.min(poseIndex + 1, activeRoutine.length)} / {activeRoutine.length}</span>
          <div className="progress-track"><i style={{ width: `${progress * 100}%` }} /></div>
          <strong>{phase === 'complete' ? 'Done' : currentPose.name}</strong>
        </div>

        <div className={`stage-copy phase-${phase}`}>
          {phase === 'holding' ? (
            <div className="hold-progress" style={{ '--hold-progress': `${holdProgress * 360}deg` } as CSSProperties}>
              <strong className="countdown">{remainingSeconds}</strong>
            </div>
          ) : null}
          <strong>{phase === 'complete' ? practice ? 'Nice work! ✓' : 'Routine complete ✓' : phase === 'transition' ? `Next: ${activeRoutine[Math.min(poseIndex + 1, activeRoutine.length - 1)].name}` : status.title}</strong>
          <span>{phase === 'complete' && practice ? 'Practice complete.' : phase === 'transition' ? `Starting in ${transitionRemaining}` : status.detail || currentPose.cue}</span>
        </div>

        {cameraState !== 'ready' && phase !== 'complete' ? (
          <div className={`camera-privacy ${cameraState === 'loading' ? 'starting' : ''}`}>
            <b><i /> {cameraState === 'loading' ? 'Starting camera…' : 'Camera off'}</b>
            <span>Processed on-device · Never recorded</span>
          </div>
        ) : null}

        {['idle', 'ready', 'complete'].includes(phase) || cameraState === 'loading' || cameraState === 'error' || (practice && mode === 'simulation') ? (
          <button
            type="button"
            className="primary-button minimal-routine-action"
            disabled={cameraState === 'loading'}
            onClick={practice && phase === 'complete' ? handleReturnToApp : handlePrimaryAction}
          >{practice && phase === 'complete' ? 'Continue setup' : primaryLabel}</button>
        ) : null}
        {practice ? <div className="practice-preview-controls">
          <span>{mode === 'simulation' ? 'Simulation · no camera tracking' : '5-second practice · sound off'}</span>
          {phase !== 'complete' ? <button type="button" disabled={cameraState === 'loading'} onClick={() => { stopCamera(); selectMode(mode === 'camera' ? 'simulation' : 'camera'); }}>{mode === 'camera' ? 'Preview without camera' : 'Use camera'}</button> : null}
        </div> : null}
      </div>
    </main>
    ) : null}
    </>
  );
}

function getPrimaryLabel(cameraState: CameraState, phase: SessionPhase, mode: Mode, simulating: boolean) {
  if (cameraState === 'loading') return 'Preparing camera…';
  if ((cameraState === 'idle' || cameraState === 'error') && mode === 'camera') return cameraState === 'error' ? 'Try camera again' : 'Start camera';
  if (phase === 'ready') return 'Start routine';
  if (phase === 'complete') return 'Run it again';
  if (phase === 'transition') return 'Get ready';
  if (mode === 'simulation') return simulating ? 'Break simulated pose' : 'Simulate current pose';
  return phase === 'holding' ? 'Keep holding' : 'Move into pose';
}

function getStatus(phase: SessionPhase, framed: boolean, detected: boolean, score: number, transition: number, error: string | null, poseCue: string, pose: typeof routine[number]['name']) {
  if (error) return { title: 'Unable to start', detail: error };
  if (phase === 'idle') return { title: 'Ready to move?', detail: 'Place your phone, then start.' };
  if (phase === 'ready') return { title: 'You’re ready', detail: 'Start when you’re ready.' };
  if (phase === 'transition') return { title: `Starting in ${transition}`, detail: '' };
  if (!framed) return { title: 'Move into view', detail: pose === 'Squat' ? 'Turn sideways and include one full arm and leg, including your foot' : 'Keep your head, hands, hips and knees visible' };
  if (phase === 'holding' || detected) return { title: 'Pose found ✓', detail: 'Hold until the timer ends.' };
  if (phase === 'paused') return { title: 'Hold the pose', detail: 'Timer paused. Return to the pose.' };
  if (phase === 'complete') return { title: 'Routine complete', detail: '' };
  if (score >= 0.55) return { title: 'Almost there', detail: poseCue };
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
  } else if (score < 0.5) {
    foundSince.current = null;
    lostSince.current ??= now;
    if (now - lostSince.current >= 900) detected.current = false;
  }
}

function scoreMountain(landmarks: NormalizedLandmark[] | null): PoseEvaluation {
  if (!landmarks) return { score: 0, framed: false };
  const framed = isPoseFramed(landmarks, [0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26]);
  const shoulderWidth = Math.max(distance(landmarks[11], landmarks[12]), 0.05);
  const shoulderMid = midpoint(landmarks[11], landmarks[12]);
  const hipMid = midpoint(landmarks[23], landmarks[24]);
  const upright = clamp(1 - Math.abs(shoulderMid.x - hipMid.x) / (shoulderWidth * 0.65));
  const armsDown = average(greaterScore(landmarks[15].y - landmarks[11].y, 0.12, 0.35), greaterScore(landmarks[16].y - landmarks[12].y, 0.12, 0.35));
  const straightArms = average(greaterScore(angle(landmarks[11], landmarks[13], landmarks[15]), 130, 165), greaterScore(angle(landmarks[12], landmarks[14], landmarks[16]), 130, 165));
  const levelKnees = clamp(1 - Math.abs(landmarks[25].y - landmarks[26].y) / 0.12);
  const kneeStance = distance(landmarks[25], landmarks[26]) / shoulderWidth;
  const compactStance = rangeScore(kneeStance, 0.2, 0.42, 1.25, 1.65);
  return { score: framed ? weighted([upright, armsDown, straightArms, levelKnees, compactStance], [0.25, 0.27, 0.16, 0.14, 0.18]) : 0, framed };
}

function scoreWarriorTwo(landmarks: NormalizedLandmark[] | null): PoseEvaluation {
  if (!landmarks) return { score: 0, framed: false };
  const framed = isPoseFramed(landmarks, [0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26]);
  const shoulderWidth = Math.max(distance(landmarks[11], landmarks[12]), 0.05);
  const shoulderMid = midpoint(landmarks[11], landmarks[12]);
  const hipMid = midpoint(landmarks[23], landmarks[24]);
  const horizontalArms = average(clamp(1 - Math.abs(landmarks[15].y - landmarks[11].y) / (shoulderWidth * 0.9)), clamp(1 - Math.abs(landmarks[16].y - landmarks[12].y) / (shoulderWidth * 0.9)));
  const straightArms = average(greaterScore(angle(landmarks[11], landmarks[13], landmarks[15]), 140, 170), greaterScore(angle(landmarks[12], landmarks[14], landmarks[16]), 140, 170));
  const wideKnees = greaterScore(distance(landmarks[25], landmarks[26]) / shoulderWidth, 0.8, 1.55);
  const anklesVisible = isPoseFramed(landmarks, [27, 28], 0.28);
  const legShape = anklesVisible ? warriorLegShape(landmarks) : 0.72;
  const upright = clamp(1 - Math.abs(shoulderMid.x - hipMid.x) / (shoulderWidth * 0.85));
  return { score: framed ? weighted([horizontalArms, straightArms, wideKnees, legShape, upright], [0.29, 0.2, 0.24, 0.12, 0.15]) : 0, framed };
}

function scoreTree(landmarks: NormalizedLandmark[] | null): PoseEvaluation {
  if (!landmarks) return { score: 0, framed: false };
  const framed = isPoseFramed(landmarks, [0, 11, 12, 23, 24, 25, 26]);
  const hipWidth = Math.max(distance(landmarks[23], landmarks[24]), 0.04);
  const shoulderWidth = Math.max(distance(landmarks[11], landmarks[12]), 0.05);
  const shoulderMid = midpoint(landmarks[11], landmarks[12]);
  const hipMid = midpoint(landmarks[23], landmarks[24]);
  const torsoHeight = Math.max(distance(shoulderMid, hipMid), 0.08);
  const upright = clamp(1 - Math.abs(shoulderMid.x - hipMid.x) / (shoulderWidth * 0.7));
  const leftRaised = average(
    greaterScore((landmarks[26].y - landmarks[25].y) / torsoHeight, 0.08, 0.5),
    greaterScore(Math.abs(landmarks[25].x - landmarks[23].x) / hipWidth, 0.4, 1.25),
    clamp(1 - Math.abs(landmarks[26].x - landmarks[24].x) / (hipWidth * 0.9)),
  );
  const rightRaised = average(
    greaterScore((landmarks[25].y - landmarks[26].y) / torsoHeight, 0.08, 0.5),
    greaterScore(Math.abs(landmarks[26].x - landmarks[24].x) / hipWidth, 0.4, 1.25),
    clamp(1 - Math.abs(landmarks[25].x - landmarks[23].x) / (hipWidth * 0.9)),
  );
  return { score: framed ? weighted([Math.max(leftRaised, rightRaised), upright], [0.78, 0.22]) : 0, framed };
}

function visibleSide(landmarks: NormalizedLandmark[]): number | null {
  const sides = [0, 1].filter((side) => isPoseFramed(landmarks, [11, 13, 15, 23, 25, 27].map((index) => index + side)));
  sides.sort((a, b) => Math.min(...[11, 13, 15, 23, 25, 27].map((index) => landmarks[index + b].visibility ?? 0))
    - Math.min(...[11, 13, 15, 23, 25, 27].map((index) => landmarks[index + a].visibility ?? 0)));
  return sides[0] ?? null;
}

function scoreSquat(landmarks: NormalizedLandmark[] | null): PoseEvaluation {
  if (!landmarks) return { score: 0, framed: false };
  const side = visibleSide(landmarks);
  if (side === null) return { score: 0, framed: false };
  const shoulder = landmarks[11 + side];
  const wrist = landmarks[15 + side];
  const hip = landmarks[23 + side];
  const knee = landmarks[25 + side];
  const ankle = landmarks[27 + side];
  const torso = Math.max(distance(shoulder, hip), 0.08);
  const bentKnee = lessScore(angle(hip, knee, ankle), 70, 105);
  const hipsLow = lessScore((knee.y - hip.y) / torso, 0.05, 0.35);
  const torsoUp = greaterScore((hip.y - shoulder.y) / torso, 0.55, 0.85);
  const handAtChest = Math.min(
    lessScore(distance(wrist, shoulder) / torso, 0.6, 1),
    rangeScore((wrist.y - shoulder.y) / torso, -0.2, -0.05, 0.55, 0.85),
  );
  const feetBelowHips = greaterScore((ankle.y - hip.y) / torso, 0.15, 0.45);
  return { score: Math.min(bentKnee, hipsLow, torsoUp, handAtChest, feetBelowHips), framed: true };
}

function warriorLegShape(landmarks: NormalizedLandmark[]) {
  const leftKnee = angle(landmarks[23], landmarks[25], landmarks[27]);
  const rightKnee = angle(landmarks[24], landmarks[26], landmarks[28]);
  return Math.max(
    average(rangeScore(leftKnee, 65, 85, 145, 160), greaterScore(rightKnee, 135, 165)),
    average(rangeScore(rightKnee, 65, 85, 145, 160), greaterScore(leftKnee, 135, 165)),
  );
}

function isPoseFramed(landmarks: NormalizedLandmark[], indexes: number[], minimumVisibility = 0.38) {
  return indexes.every((index) => {
    const point = landmarks[index];
    return point && (point.visibility ?? 0) >= minimumVisibility && point.x >= -0.08 && point.x <= 1.08 && point.y >= -0.08 && point.y <= 1.08;
  });
}

type GuidePoint = readonly [number, number];
type GuideSegment = readonly [GuidePoint, GuidePoint];

function drawPoseGuide(
  canvas: HTMLCanvasElement | null,
  video: HTMLVideoElement | null,
  pose: typeof routine[number]['name'],
  landmarks: NormalizedLandmark[] | null,
  aligned: boolean,
  anchorRef: MutableRefObject<GuideAnchor | null>,
) {
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
  context.fillStyle = 'rgba(8, 14, 10, 0.24)';
  context.fillRect(0, 0, width, height);

  const layout = getVideoLayout(width, height, video);
  const measuredAnchor = getGuideAnchor(layout, landmarks, pose);
  const previousAnchor = anchorRef.current;
  const anchor = previousAnchor ? {
    centerX: lerp(previousAnchor.centerX, measuredAnchor.centerX, 0.18),
    shoulderY: lerp(previousAnchor.shoulderY, measuredAnchor.shoulderY, 0.18),
    shoulderWidth: lerp(previousAnchor.shoulderWidth, measuredAnchor.shoulderWidth, 0.18),
    torsoHeight: lerp(previousAnchor.torsoHeight, measuredAnchor.torsoHeight, 0.18),
  } : measuredAnchor;
  anchorRef.current = anchor;

  const geometry = getGuideGeometry(pose);
  const side = landmarks && pose === 'Squat' ? visibleSide(landmarks) : null;
  const direction = landmarks && side !== null && landmarks[25 + side].x > landmarks[23 + side].x ? -1 : 1;
  const xScale = pose === 'Squat' ? anchor.torsoHeight * direction : anchor.shoulderWidth;
  const point = ([x, y]: GuidePoint) => ({ x: anchor.centerX + x * xScale, y: anchor.shoulderY + y * anchor.torsoHeight });
  const bodyWidth = Math.max(42, anchor.shoulderWidth * 0.72);
  const drawSegments = () => {
    geometry.segments.forEach(([from, to]) => {
      const start = point(from);
      const end = point(to);
      context.beginPath();
      context.moveTo(start.x, start.y);
      context.lineTo(end.x, end.y);
      context.stroke();
    });
  };

  context.globalCompositeOperation = 'destination-out';
  context.strokeStyle = 'rgba(0,0,0,.9)';
  context.fillStyle = 'rgba(0,0,0,.9)';
  context.lineWidth = bodyWidth * 1.12;
  context.lineCap = 'round';
  context.lineJoin = 'round';
  drawSegments();
  const head = point(geometry.head);
  context.beginPath();
  context.arc(head.x, head.y, bodyWidth * 0.62, 0, Math.PI * 2);
  context.fill();

  context.globalCompositeOperation = 'source-over';
  context.strokeStyle = aligned ? 'rgba(201, 238, 115, .82)' : 'rgba(231, 235, 232, .52)';
  context.lineWidth = 2;
  context.setLineDash([8, 10]);
  drawSegments();
  context.beginPath();
  context.arc(head.x, head.y, bodyWidth * 0.38, 0, Math.PI * 2);
  context.stroke();
  context.setLineDash([]);
}

function getGuideGeometry(pose: typeof routine[number]['name']): { head: GuidePoint; segments: GuideSegment[] } {
  if (pose === 'Squat') {
    return {
      head: [0.02, -0.28],
      segments: [
        [[0, 0], [-0.25, 0.97]],
        [[0, 0], [0.4, 0.65]], [[0.4, 0.65], [0.35, 0.2]],
        [[-0.25, 0.97], [0.55, 0.9]], [[0.55, 0.9], [0.15, 1.65]],
      ],
    };
  }
  if (pose === 'Warrior II') {
    return {
      head: [0, -0.55],
      segments: [
        [[0, 0], [0, 1]],
        [[-0.5, 0], [-1.05, 0]], [[-1.05, 0], [-1.65, 0]],
        [[0.5, 0], [1.05, 0]], [[1.05, 0], [1.65, 0]],
        [[-0.24, 1], [-0.95, 1.75]], [[-0.95, 1.75], [-1.45, 2.55]],
        [[0.24, 1], [0.9, 1.65]], [[0.9, 1.65], [1.45, 2.45]],
      ],
    };
  }
  if (pose === 'Tree') {
    return {
      head: [0, -0.55],
      segments: [
        [[0, 0], [0, 1]],
        [[-0.5, 0], [-0.78, -0.55]], [[-0.78, -0.55], [-0.18, -1.02]],
        [[0.5, 0], [0.78, -0.55]], [[0.78, -0.55], [0.18, -1.02]],
        [[-0.24, 1], [-0.25, 2.55]],
        [[0.24, 1], [0.9, 1.62]], [[0.9, 1.62], [-0.12, 1.95]],
      ],
    };
  }
  return {
    head: [0, -0.55],
    segments: [
      [[0, 0], [0, 1]],
      [[-0.5, 0], [-0.62, 0.7]], [[-0.62, 0.7], [-0.52, 1.4]],
      [[0.5, 0], [0.62, 0.7]], [[0.62, 0.7], [0.52, 1.4]],
      [[-0.24, 1], [-0.3, 2.55]], [[0.24, 1], [0.3, 2.55]],
    ],
  };
}

function getGuideAnchor(layout: VideoLayout, landmarks: NormalizedLandmark[] | null, pose: typeof routine[number]['name']): GuideAnchor {
  const side = landmarks && pose === 'Squat' ? visibleSide(landmarks) : null;
  if (landmarks && side !== null) {
    const shoulder = toCanvasPoint(landmarks[11 + side], layout);
    const hip = toCanvasPoint(landmarks[23 + side], layout);
    const torsoHeight = Math.max(distance(shoulder, hip), layout.height * 0.08);
    return { centerX: shoulder.x, shoulderY: shoulder.y, shoulderWidth: torsoHeight * 0.45, torsoHeight };
  }
  if (landmarks && isPoseFramed(landmarks, [11, 12, 23, 24], 0.3)) {
    const leftShoulder = toCanvasPoint(landmarks[11], layout);
    const rightShoulder = toCanvasPoint(landmarks[12], layout);
    const leftHip = toCanvasPoint(landmarks[23], layout);
    const rightHip = toCanvasPoint(landmarks[24], layout);
    const shoulderMid = midpoint(leftShoulder, rightShoulder);
    const hipMid = midpoint(leftHip, rightHip);
    const shoulderWidth = Math.max(distance(leftShoulder, rightShoulder), layout.width * 0.12);
    return {
      centerX: shoulderMid.x,
      shoulderY: shoulderMid.y,
      shoulderWidth,
      torsoHeight: Math.max(distance(shoulderMid, hipMid), shoulderWidth * 0.8),
    };
  }
  return {
    centerX: layout.offsetX + layout.width / 2,
    shoulderY: layout.offsetY + layout.height * 0.3,
    shoulderWidth: Math.min(layout.width * 0.2, layout.height * 0.15),
    torsoHeight: layout.height * 0.2,
  };
}

type VideoLayout = { offsetX: number; offsetY: number; width: number; height: number };

function getVideoLayout(width: number, height: number, video: HTMLVideoElement | null): VideoLayout {
  if (!video?.videoWidth || !video.videoHeight) return { offsetX: 0, offsetY: 0, width, height };
  const contain = getComputedStyle(video).objectFit === 'contain';
  const scale = contain ? Math.min(width / video.videoWidth, height / video.videoHeight) : Math.max(width / video.videoWidth, height / video.videoHeight);
  const drawnWidth = video.videoWidth * scale;
  const drawnHeight = video.videoHeight * scale;
  return { offsetX: (width - drawnWidth) / 2, offsetY: (height - drawnHeight) / 2, width: drawnWidth, height: drawnHeight };
}

function toCanvasPoint(point: NormalizedLandmark, layout: VideoLayout) {
  return { x: layout.offsetX + (1 - point.x) * layout.width, y: layout.offsetY + point.y * layout.height };
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

  const layout = getVideoLayout(width, height, video);
  const color = detected ? 'rgba(201,238,115,.96)' : 'rgba(255,255,255,.82)';
  const glow = detected ? 'rgba(201,238,115,.3)' : 'rgba(240,188,107,.2)';

  context.lineCap = 'round';
  [12, 4].forEach((lineWidth, pass) => {
    context.strokeStyle = pass === 0 ? glow : color;
    context.lineWidth = lineWidth;
    context.shadowColor = pass === 0 ? glow : 'transparent';
    context.shadowBlur = pass === 0 ? 14 : 0;
    skeletonConnections.forEach(([startIndex, endIndex]) => {
      const start = landmarks[startIndex];
      const end = landmarks[endIndex];
      if ((start.visibility ?? 0) < 0.35 || (end.visibility ?? 0) < 0.35) return;
      const a = toCanvasPoint(start, layout);
      const b = toCanvasPoint(end, layout);
      context.beginPath();
      context.moveTo(a.x, a.y);
      context.lineTo(b.x, b.y);
      context.stroke();
    });
  });
  context.shadowBlur = 0;
  context.fillStyle = color;
  [11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28].forEach((index) => {
    const landmark = landmarks[index];
    if ((landmark.visibility ?? 0) < 0.35) return;
    const point = toCanvasPoint(landmark, layout);
    context.beginPath();
    context.arc(point.x, point.y, 3.5, 0, Math.PI * 2);
    context.fill();
  });
}

function angle(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark) {
  const radians = Math.atan2(c.y - b.y, c.x - b.x) - Math.atan2(a.y - b.y, a.x - b.x);
  let degrees = Math.abs(radians * 180 / Math.PI);
  if (degrees > 180) degrees = 360 - degrees;
  return degrees;
}

function distance(a: { x: number; y: number }, b: { x: number; y: number }) {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

function midpoint(a: { x: number; y: number }, b: { x: number; y: number }) {
  return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
}

function greaterScore(value: number, minimum: number, ideal: number) {
  return clamp((value - minimum) / (ideal - minimum));
}

function lessScore(value: number, goodAtOrBelow: number, zeroAtOrAbove: number) {
  return clamp((zeroAtOrAbove - value) / (zeroAtOrAbove - goodAtOrBelow));
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

function lerp(from: number, to: number, amount: number) {
  return from + (to - from) * amount;
}

'use client';

import { useEffect, useRef, useState } from 'react';

// Keep this catalog aligned with model/AlarmConfig.kt and res/raw on Android.
export const alarmSounds = [
  { id: 'sunbird_morning_call', name: 'Sunbird Morning Call', free: true },
  { id: 'morning_temple_call', name: 'Morning Temple Call', free: true },
  { id: 'lotus_sunrise_loop', name: 'Lotus Sunrise Loop', free: false },
  { id: 'dizi_dawn_alarm', name: 'Dizi Dawn Alarm', free: false },
  { id: 'silk_road_sunrise', name: 'Silk Road Sunrise', free: false },
  { id: 'sunrise_circle', name: 'Sunrise Circle', free: false },
  { id: 'bamboo_dawn_bell', name: 'Bamboo Dawn Bell', free: false },
  { id: 'morning_bell_run', name: 'Morning Bell Run', free: false },
  { id: 'sunrise_flow_loop', name: 'Sunrise Flow Loop', free: false },
  { id: 'rice_paper_dawn', name: 'Rice Paper Dawn', free: false },
] as const;

export type AlarmSoundId = typeof alarmSounds[number]['id'];

export function SoundPicker({ selected, vibration, onVibration, onSelect, onClose, onUpgrade }: {
  selected: AlarmSoundId;
  vibration: boolean;
  onVibration: (enabled: boolean) => void;
  onSelect: (sound: AlarmSoundId) => void;
  onClose: () => void;
  onUpgrade: () => void;
}) {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [previewing, setPreviewing] = useState<AlarmSoundId | null>(null);
  const [error, setError] = useState(false);

  const stopPreview = () => {
    audioRef.current?.pause();
    audioRef.current = null;
    if (timerRef.current) clearTimeout(timerRef.current);
    setPreviewing(null);
  };

  useEffect(() => {
    const onHidden = () => { if (document.hidden) stopPreview(); };
    document.addEventListener('visibilitychange', onHidden);
    return () => {
      document.removeEventListener('visibilitychange', onHidden);
      audioRef.current?.pause();
      audioRef.current = null;
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, []);

  const preview = async (sound: AlarmSoundId) => {
    const wasPlaying = previewing === sound;
    stopPreview();
    setError(false);
    if (wasPlaying) return;
    const player = new Audio(`/sounds/${sound}.mp3`);
    player.volume = 0.7;
    audioRef.current = player;
    setPreviewing(sound);
    player.onended = stopPreview;
    try {
      await player.play();
      if (audioRef.current === player) timerRef.current = setTimeout(stopPreview, 10_000);
    } catch {
      if (audioRef.current === player) { stopPreview(); setError(true); }
    }
  };

  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <section className="choice-sheet sound-picker" role="dialog" aria-modal="true" aria-labelledby="sound-title" onClick={(event) => event.stopPropagation()}>
        <i className="sheet-handle" />
        <div className="duration-sheet-heading"><h2 id="sound-title">Sound &amp; vibration</h2><button type="button" aria-label="Close sound selection" onClick={onClose}>×</button></div>
        <p>Upgrade to Pro to unlock the dimmed sounds.</p>
        <div className="alarm-setting"><div><strong>Vibration</strong><span>Gentle pulse</span></div><button type="button" className={`ui-switch ${vibration ? 'on' : ''}`} role="switch" aria-label="Vibration" aria-checked={vibration} onClick={() => onVibration(!vibration)}><i /></button></div>
        {error ? <p role="alert">Couldn’t play this sound. Try again.</p> : null}
        <div className="sound-options-scroll">
          <div data-tour="sound-choice" className="free-sound-options">
            {alarmSounds.filter((sound) => sound.free).map((sound) => <div key={sound.id} className={`sound-option ${selected === sound.id ? 'selected' : ''}`}>
              <button type="button" className="sound-select" aria-pressed={selected === sound.id} onClick={() => onSelect(sound.id)}>{sound.name}</button>
              <button type="button" className="sound-preview" aria-label={`${previewing === sound.id ? 'Stop' : 'Preview'} ${sound.name}`} onClick={() => void preview(sound.id)}>{previewing === sound.id ? '♫ Stop' : '♫ Preview'}</button>
            </div>)}
          </div>
          {alarmSounds.filter((sound) => !sound.free).map((sound) => <div key={sound.id} className="sound-option locked">
            <button type="button" className="sound-select" onClick={onUpgrade}>{sound.name}<small>PRO</small></button>
            <button type="button" className="sound-preview" aria-label={`Unlock ${sound.name}`} onClick={onUpgrade}>♫ Preview</button>
          </div>)}
        </div>
      </section>
    </div>
  );
}

export function SnoozePicker({ minutes, count, onMinutes, onCount, onClose }: {
  minutes: number;
  count: number;
  onMinutes: (value: number) => void;
  onCount: (value: number) => void;
  onClose: () => void;
}) {
  return <div className="sheet-backdrop" onClick={onClose}>
    <section className="choice-sheet snooze-picker" role="dialog" aria-modal="true" aria-labelledby="snooze-title" onClick={(event) => event.stopPropagation()}>
      <div className="duration-sheet-heading"><h2 id="snooze-title">Snooze</h2><button type="button" aria-label="Close snooze selection" onClick={onClose}>×</button></div>
      <h3>Snooze time</h3><div className="snooze-choices">{[5, 10, 15, 20].map((value) => <button type="button" key={value} aria-pressed={value === minutes} onClick={() => onMinutes(value)}>{value} min</button>)}</div>
      <h3>Number of snoozes</h3><div className="snooze-choices">{[1, 2, 3, 5].map((value) => <button type="button" key={value} aria-pressed={value === count} onClick={() => onCount(value)}>{value} ×</button>)}</div>
      <button type="button" className="tour-primary" onClick={onClose}>Done</button>
    </section>
  </div>;
}

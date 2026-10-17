'use client';

import { useCallback, useEffect, useRef, useState, type UIEvent, type WheelEvent } from 'react';
import Onboarding, { type TourStep } from './Onboarding';
import { alarmSounds, SoundPicker, SnoozePicker, type AlarmSoundId } from './AlarmOptions';

export type FreePoseName = 'Mountain' | 'Warrior II' | 'Tree';
export type AlarmPoseStep = { pose: FreePoseName; duration: number };
export type BrowserAlarm = {
  id: number;
  name: string;
  hour: number;
  minute: number;
  weekdays: number[];
  enabled: boolean;
  routine: AlarmPoseStep[];
  sound: boolean;
  vibration: boolean;
  snooze: boolean;
  alarmSound: AlarmSoundId;
  snoozeMinutes: number;
  snoozeCount: number;
  manualStop: boolean;
};

const freePoses: FreePoseName[] = ['Mountain', 'Warrior II', 'Tree'];
const proPoses = ['Chair', 'Forward Fold', 'Triangle', 'Goddess', 'Wide-Legged Fold'];
const allPoses = [...freePoses, ...proPoses];
const durations = [5, 10, 15, 20, 30, 45, 60];
const weekdays = [
  { value: 1, label: 'M' }, { value: 2, label: 'T' }, { value: 3, label: 'W' },
  { value: 4, label: 'T' }, { value: 5, label: 'F' }, { value: 6, label: 'S' }, { value: 7, label: 'S' },
];

function defaultAlarmName(hour: number, existingNames: string[]) {
  const names = hour >= 5 && hour < 11
    ? ['Rise & stretch ☀️', 'Morning movement', 'A fresh start 🌱', 'Wake up gently', 'Hello, new day 🌤️', 'Start with a stretch', 'Meet the morning 🌅', 'Your morning moment']
    : hour >= 11 && hour < 17
      ? ['Midday reset 🌿', 'A little movement', 'Find your balance ⚖️', 'Stretch break', 'Pause & stretch 🧘', 'Make space for you', 'Refresh & refocus ✨', 'Time to move']
      : hour >= 17 && hour < 22
        ? ['Evening unwind 🌙', 'Release the day', 'Sunset stretch 🌅', 'Room to breathe', 'Let the day go 🍃', 'Ease into evening', 'A softer pace 🌿', 'Evening balance']
        : ['Moonlight movement 🌙', 'Quiet reset', 'Night owl stretch 🦉', 'Move at your pace', 'A mindful moment ✨', 'Quiet strength', 'Under the stars 🌌', 'A gentle stretch'];
  const unused = names.filter((name) => !existingNames.includes(name));
  const choices = unused.length ? unused : names;
  return choices[Math.floor(Math.random() * choices.length)];
}

export function createDefaultAlarm(existingNames: string[] = []): BrowserAlarm {
  const hour = new Date().getHours();
  return {
    id: Date.now(),
    name: defaultAlarmName(hour, existingNames),
    hour,
    minute: 0,
    weekdays: [1, 2, 3, 4, 5],
    enabled: true,
    routine: freePoses.map((pose) => ({ pose, duration: 20 })),
    sound: true,
    vibration: true,
    snooze: true,
    alarmSound: 'sunbird_morning_call',
    snoozeMinutes: 5,
    snoozeCount: 1,
    manualStop: true,
  };
}

export default function AlarmShell({
  resumeDraft,
  onTestRoutine,
  onPractice,
  practiceResult,
  active,
}: {
  resumeDraft: BrowserAlarm | null;
  onTestRoutine: (alarm: BrowserAlarm, resumeAlarm?: BrowserAlarm) => void;
  onPractice: (alarm: BrowserAlarm, onComplete: () => void) => void;
  practiceResult: 'complete' | 'cancelled' | null;
  active: boolean;
}) {
  const [alarms, setAlarms] = useState<BrowserAlarm[]>([]);
  const [draft, setDraft] = useState<BrowserAlarm | null>(resumeDraft);
  const [loaded, setLoaded] = useState(false);
  const [durationSlot, setDurationSlot] = useState<number | null>(null);
  const [soundPickerOpen, setSoundPickerOpen] = useState(false);
  const [snoozePickerOpen, setSnoozePickerOpen] = useState(false);
  const [proOpen, setProOpen] = useState(false);
  const [tourStep, setTourStep] = useState<TourStep | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [savedTourAlarm, setSavedTourAlarm] = useState<BrowserAlarm | null>(null);
  const [practicePose, setPracticePose] = useState<FreePoseName | null>(null);
  const phoneRef = useRef<HTMLElement>(null);
  const tourDraftRef = useRef<BrowserAlarm | null>(null);

  useEffect(() => {
    const stored = window.localStorage.getItem('yoga-alarm-browser-alarms');
    if (stored) {
      try { setAlarms((JSON.parse(stored) as BrowserAlarm[]).map((alarm) => ({
        ...alarm,
        alarmSound: alarmSounds.some((sound) => sound.id === alarm.alarmSound) ? alarm.alarmSound : 'sunbird_morning_call',
        snoozeMinutes: alarm.snoozeMinutes ?? 5,
        snoozeCount: alarm.snoozeCount ?? 1,
        manualStop: alarm.manualStop ?? true,
      }))); } catch { setAlarms([]); }
    }
    if (!window.localStorage.getItem('yoga-alarm-browser-onboarding-v1')) setTourStep('welcome');
    setLoaded(true);
  }, []);

  const persist = (next: BrowserAlarm[]) => {
    setAlarms(next);
    window.localStorage.setItem('yoga-alarm-browser-alarms', JSON.stringify(next));
  };

  const saveDraft = () => {
    if (!draft) return;
    const saved = { ...draft, name: draft.name.trim() || defaultAlarmName(draft.hour, alarms.filter((alarm) => alarm.id !== draft.id).map((alarm) => alarm.name)) };
    persist(alarms.some((alarm) => alarm.id === saved.id)
      ? alarms.map((alarm) => alarm.id === saved.id ? saved : alarm)
      : [...alarms, saved]);
    setDraft(null);
    if (tourStep === 'save') {
      setSavedTourAlarm(saved);
      setTourStep('complete');
    }
  };

  const finishTour = useCallback(() => {
    window.localStorage.setItem('yoga-alarm-browser-onboarding-v1', 'seen');
    setTourStep(null);
    setSoundPickerOpen(false);
    tourDraftRef.current = null;
  }, []);

  const addAlarm = () => {
    setDraft(tourDraftRef.current ?? createDefaultAlarm(alarms.map((alarm) => alarm.name)));
    tourDraftRef.current = null;
    if (tourStep === 'add') setTourStep('time');
  };

  const replayTour = () => {
    setSettingsOpen(false);
    setSavedTourAlarm(null);
    setPracticePose(null);
    tourDraftRef.current = null;
    setTourStep('welcome');
  };

  if (!loaded) return <main className="app-preview-shell" />;

  return (
    <main className="app-preview-shell">
      <section ref={phoneRef} className={`phone-app ${draft ? 'editor-open' : ''} ${tourStep ? 'tour-active' : ''}`}>
        {draft ? (
          <AlarmEditor
            draft={draft}
            guided={tourStep !== null}
            onChange={setDraft}
            onCancel={() => setDraft(null)}
            onSave={saveDraft}
            onTest={() => onTestRoutine(draft)}
            onTryPose={(slot) => onTestRoutine({ ...draft, routine: [draft.routine[slot]] }, draft)}
            onSelectDuration={setDurationSlot}
            onSound={() => { setSoundPickerOpen(true); if (tourStep === 'sound') setTourStep('sound-choice'); }}
            onSnooze={() => setSnoozePickerOpen(true)}
            onUpgrade={() => setProOpen(true)}
          />
        ) : (
          <AlarmHome
            alarms={alarms}
            onAdd={addAlarm}
            onEdit={setDraft}
            onToggle={(id, enabled) => persist(alarms.map((alarm) => alarm.id === id ? { ...alarm, enabled } : alarm))}
            onSettings={() => setSettingsOpen(true)}
            onUpgrade={() => setProOpen(true)}
          />
        )}
        {draft && durationSlot !== null ? (
          <DurationPicker
            selected={draft.routine[durationSlot].duration}
            onUpgrade={() => { setDurationSlot(null); setProOpen(true); }}
            onClose={() => setDurationSlot(null)}
            onSelect={(duration) => {
              setDraft({ ...draft, routine: draft.routine.map((step, index) => index === durationSlot ? { ...step, duration } : step) });
              setDurationSlot(null);
            }}
          />
        ) : null}
        {draft && soundPickerOpen ? <SoundPicker
          selected={draft.alarmSound}
          vibration={draft.vibration}
          onVibration={(vibration) => setDraft({ ...draft, vibration })}
          onSelect={(alarmSound) => setDraft({ ...draft, alarmSound })}
          onClose={() => { setSoundPickerOpen(false); if (tourStep === 'sound-choice') setTourStep('sound'); }}
          onUpgrade={() => { setSoundPickerOpen(false); setProOpen(true); }}
        /> : null}
        {draft && snoozePickerOpen ? <SnoozePicker minutes={draft.snoozeMinutes} count={draft.snoozeCount} onMinutes={(snoozeMinutes) => setDraft({ ...draft, snoozeMinutes })} onCount={(snoozeCount) => setDraft({ ...draft, snoozeCount })} onClose={() => setSnoozePickerOpen(false)} /> : null}
        {settingsOpen ? (
          <div className="sheet-backdrop" onClick={() => setSettingsOpen(false)}>
            <section className="choice-sheet preview-settings" role="dialog" aria-modal="true" aria-labelledby="settings-title" onClick={(event) => event.stopPropagation()}>
              <div className="duration-sheet-heading"><h2 id="settings-title">Settings</h2><button type="button" aria-label="Close Settings" onClick={() => setSettingsOpen(false)}>×</button></div>
              <h3>Pro</h3><button type="button" className="tour-text-button" onClick={() => { setSettingsOpen(false); setProOpen(true); }}>Get Pro</button>
              <button type="button" className="settings-help" onClick={replayTour}><span>How to use Yoga Alarm</span><span aria-hidden="true">↗</span></button>
              <p>Replay the guide and practise a pose. Your saved alarms stay as they are.</p>
              <a href="/privacy">Privacy &amp; safety</a>
            </section>
          </div>
        ) : null}
        {proOpen ? <div className="sheet-backdrop" onClick={() => setProOpen(false)}><section className="choice-sheet preview-settings" role="dialog" aria-modal="true" aria-labelledby="pro-title" onClick={(event) => event.stopPropagation()}><div className="duration-sheet-heading"><h2 id="pro-title">Make it your own</h2><button type="button" aria-label="Close Pro" onClick={() => setProOpen(false)}>×</button></div><p>Unlock all poses, sounds and hold times, plus routines of up to 10 poses.</p><p>Purchases are available in the app.</p><button type="button" className="tour-primary" onClick={() => setProOpen(false)}>Back to preview</button></section></div> : null}
        {tourStep && active ? (
          <Onboarding
            step={tourStep}
            phoneRef={phoneRef}
            alarm={savedTourAlarm ?? draft}
            practised={practicePose === draft?.routine[0].pose && practiceResult === 'complete'}
            onStep={(step) => { if (step !== 'sound-choice') setSoundPickerOpen(false); if (step === 'sound-choice') setSoundPickerOpen(true); setTourStep(step); }}
            onSkip={finishTour}
            onBackToHome={() => { tourDraftRef.current = draft; setDraft(null); setTourStep('add'); }}
            onPractice={() => { if (draft) { setPracticePose(draft.routine[0].pose); onPractice(draft, () => setTourStep('test')); } }}
            onTest={() => { if (savedTourAlarm) { finishTour(); onTestRoutine(savedTourAlarm); } }}
          />
        ) : null}
      </section>
    </main>
  );
}

function AlarmHome({ alarms, onAdd, onEdit, onToggle, onSettings, onUpgrade }: {
  alarms: BrowserAlarm[];
  onAdd: () => void;
  onEdit: (alarm: BrowserAlarm) => void;
  onToggle: (id: number, enabled: boolean) => void;
  onSettings: () => void;
  onUpgrade: () => void;
}) {
  const today = new Date();
  return (
    <div className="alarm-home">
      <div className="phone-status"><span>Yoga Alarm</span><span>Browser preview</span></div>
      <div className="alarm-list-toolbar">
        <h1>Your alarms</h1><div className="home-toolbar-actions"><button type="button" className="get-pro-button" onClick={onUpgrade}>GET PRO</button><button type="button" className="settings-button" aria-label="Settings" onClick={onSettings}>⚙</button></div>
      </div>
      <section className="preview-streak" aria-label="Streak layout preview. No Android streak data is loaded."><div><small>day streak</small><strong>🔥 0</strong></div><div className="streak-dates">{[-4, -3, -2, -1, 0, 1, 2].map((offset) => { const date = new Date(today.getFullYear(), today.getMonth(), today.getDate() + offset); return <div key={offset} className={offset === 0 ? 'today' : ''}><small>{date.toLocaleDateString(undefined, { weekday: 'narrow' })}</small><span>{date.getDate()}</span></div>; })}</div></section>
      <div className="alarm-card-list">
        {alarms.length ? alarms.map((alarm) => (
          <article key={alarm.id} className={`alarm-summary ${alarm.enabled ? '' : 'disabled'}`} onClick={() => onEdit(alarm)}>
            <div>
              <span className="alarm-name">{alarm.name}</span>
              <strong>{two(alarm.hour)}:{two(alarm.minute)}</strong>
              <p>{weekdaySummary(alarm.weekdays)}</p>
            </div>
            <button
              type="button"
              className={`ui-switch ${alarm.enabled ? 'on' : ''}`}
              aria-label={`${alarm.enabled ? 'Disable' : 'Enable'} ${alarm.name}`}
              onClick={(event) => { event.stopPropagation(); onToggle(alarm.id, !alarm.enabled); }}
            ><i /></button>
            <footer>{alarm.routine.map((step) => step.pose).join(' · ')} · {alarm.routine.reduce((sum, step) => sum + step.duration, 0)} sec</footer>
          </article>
        )) : (
          <button type="button" className="empty-alarm-card" onClick={onAdd}>
            <strong>No alarms yet</strong>
            <span>Set a time. Choose your poses.</span>
            <b>Create alarm →</b>
          </button>
        )}
      </div>
      <button type="button" className="home-add-alarm" data-tour="add" onClick={onAdd}><span aria-hidden="true">＋</span> Add alarm</button>
    </div>
  );
}

function AlarmEditor({ draft, guided, onChange, onCancel, onSave, onTest, onTryPose, onSelectDuration, onSound, onSnooze, onUpgrade }: {
  draft: BrowserAlarm;
  guided: boolean;
  onChange: (alarm: BrowserAlarm) => void;
  onCancel: () => void;
  onSave: () => void;
  onTest: () => void;
  onTryPose: (slot: number) => void;
  onSelectDuration: (slot: number) => void;
  onSound: () => void;
  onSnooze: () => void;
  onUpgrade: () => void;
}) {
  return (
    <div className="alarm-editor">
      <div className="time-wheel" data-tour="time" aria-label="Alarm time">
        <TimeColumn value={draft.hour} count={24} onChange={(hour) => onChange({ ...draft, hour })} />
        <span className="time-colon">:</span>
        <TimeColumn value={draft.minute} count={60} onChange={(minute) => onChange({ ...draft, minute })} />
      </div>

      <section className="alarm-editor-card">
        <input className="alarm-name-input" data-tour="name" value={draft.name} maxLength={40} aria-label="Alarm name" onChange={(event) => onChange({ ...draft, name: event.target.value })} />
        <div className="weekday-picker" data-tour="days" aria-label="Repeat days">
          {weekdays.map((day) => {
            const selected = draft.weekdays.includes(day.value);
            return <button key={day.value} aria-label={['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][day.value]} aria-pressed={selected} className={selected ? 'selected' : ''} onClick={() => onChange({ ...draft, weekdays: selected ? draft.weekdays.filter((value) => value !== day.value) : [...draft.weekdays, day.value] })}>{day.label}</button>;
          })}
        </div>

        <div className="alarm-setting-list">
          <SettingRow title="Sound" detail={alarmSounds.find((sound) => sound.id === draft.alarmSound)?.name ?? alarmSounds[0].name} checked={draft.sound} onOpen={onSound} tour="sound" onChange={(sound) => onChange({ ...draft, sound })} />
          <SettingRow title="Snooze" detail={`${draft.snoozeMinutes} minutes, ${draft.snoozeCount} ${draft.snoozeCount === 1 ? 'time' : 'times'}`} checked={draft.snooze} onOpen={onSnooze} onChange={(snooze) => onChange({ ...draft, snooze })} />
          <SettingRow title="Stop button" detail="Allow manual alarm stop" checked={draft.manualStop} onChange={(manualStop) => onChange({ ...draft, manualStop })} />
        </div>

        <button type="button" className="test-routine-button" data-tour="test" onClick={onTest}><span><strong>Test full routine</strong><small>Play all {draft.routine.length} poses · {draft.routine.reduce((sum, step) => sum + step.duration, 0)} seconds</small></span><i aria-hidden="true">▶</i></button>
        <div className="pose-carousel-list">
          {draft.routine.map((step, index) => (
            <PoseCarouselCard
              key={index}
              index={index}
              step={step}
              guided={guided}
              onSelect={(pose) => onChange({ ...draft, routine: draft.routine.map((candidate, slot) => slot === index ? { ...candidate, pose } : candidate) })}
              onDuration={() => onSelectDuration(index)}
              onTry={() => onTryPose(index)}
              onUpgrade={onUpgrade}
            />
          ))}
        </div>
        <button type="button" className="add-pose-button" onClick={onUpgrade}>+ Add pose <span>PRO</span></button>
      </section>

      <div className="editor-actions">
        <button type="button" onClick={onCancel}>Cancel</button><i /><button type="button" data-tour="save" onClick={onSave}>Save</button>
      </div>
    </div>
  );
}

const TIME_ROW_HEIGHT = 72;
const TIME_WHEEL_CYCLES = 5;

function TimeColumn({ value, count, onChange }: { value: number; count: number; onChange: (value: number) => void }) {
  const columnRef = useRef<HTMLDivElement>(null);
  const initializedRef = useRef(false);

  useEffect(() => {
    if (!columnRef.current || initializedRef.current) return;
    columnRef.current.scrollTop = (count * 2 + value) * TIME_ROW_HEIGHT;
    initializedRef.current = true;
  }, [count, value]);

  const handleScroll = (event: UIEvent<HTMLDivElement>) => {
    const column = event.currentTarget;
    const row = Math.round(column.scrollTop / TIME_ROW_HEIGHT);
    const next = ((row % count) + count) % count;
    if (next !== value) onChange(next);
    if (row < count || row >= count * (TIME_WHEEL_CYCLES - 1)) {
      column.scrollTop = (count * 2 + next) * TIME_ROW_HEIGHT;
    }
  };

  return (
    <div ref={columnRef} className="time-column" role="spinbutton" aria-label={count === 24 ? 'Hours' : 'Minutes'} aria-valuemin={0} aria-valuemax={count - 1} aria-valuenow={value} tabIndex={0} onScroll={handleScroll} onKeyDown={(event) => {
      if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return;
      event.preventDefault();
      const next = (value + (event.key === 'ArrowUp' ? 1 : -1) + count) % count;
      onChange(next);
      columnRef.current?.scrollTo({ top: (count * 2 + next) * TIME_ROW_HEIGHT, behavior: 'smooth' });
    }}>
      {Array.from({ length: count * TIME_WHEEL_CYCLES }, (_, index) => (
        <button
          type="button"
          key={index}
          tabIndex={-1}
          aria-hidden="true"
          className={index % count === value ? 'selected' : ''}
          onClick={() => columnRef.current?.scrollTo({ top: index * TIME_ROW_HEIGHT, behavior: 'smooth' })}
        >{two(index % count)}</button>
      ))}
    </div>
  );
}

function PoseFigure({ pose }: { pose: string }) {
  const poseClass = pose.toLowerCase().replaceAll(' ', '-').replace('ii', 'two');
  return <img className="pose-illustration" draggable={false} src={`/poses/${poseClass}.png`} alt={`${pose} pose illustration`} />;
}

function SettingRow({ title, detail, checked, onChange, onOpen, tour }: { title: string; detail: string; checked: boolean; onChange: (checked: boolean) => void; onOpen?: () => void; tour?: string }) {
  return (
    <div className="alarm-setting">{onOpen ? <button type="button" data-tour={tour} className="setting-label" onClick={onOpen}><strong>{title}</strong><span>{detail}</span></button> : <div><strong>{title}</strong><span>{detail}</span></div>}<button type="button" className={`ui-switch ${checked ? 'on' : ''}`} role="switch" aria-label={title} aria-checked={checked} onClick={() => onChange(!checked)}><i /></button></div>
  );
}

function PoseCarouselCard({ index, step, guided, onSelect, onDuration, onTry, onUpgrade }: {
  index: number;
  step: AlarmPoseStep;
  guided: boolean;
  onSelect: (pose: FreePoseName) => void;
  onDuration: () => void;
  onTry: () => void;
  onUpgrade: () => void;
}) {
  const trackRef = useRef<HTMLDivElement>(null);
  const selectionTimerRef = useRef<number | null>(null);
  const swipeRef = useRef<{ x: number; scrollLeft: number; dragging: boolean } | null>(null);
  const suppressClickRef = useRef(false);
  const [focusedPose, setFocusedPose] = useState<string>(step.pose);
  const visiblePoses = guided ? freePoses : allPoses;

  useEffect(() => {
    const track = trackRef.current;
    const selectedIndex = visiblePoses.indexOf(step.pose);
    const item = track?.children[selectedIndex] as HTMLElement | undefined;
    if (!track || !item) return;
    const alignSelection = () => {
      if (selectionTimerRef.current !== null) window.clearTimeout(selectionTimerRef.current);
      track.scrollTo({ left: item.offsetLeft - (track.clientWidth - item.offsetWidth) / 2, behavior: 'instant' });
      setFocusedPose(step.pose);
    };
    alignSelection();
    const observer = new ResizeObserver(alignSelection);
    observer.observe(track);
    return () => observer.disconnect();
  }, [step.pose, visiblePoses]);

  useEffect(() => () => {
    if (selectionTimerRef.current !== null) window.clearTimeout(selectionTimerRef.current);
  }, []);

  const centeredPose = () => {
    const track = trackRef.current;
    if (!track) return step.pose;
    const center = track.scrollLeft + track.clientWidth / 2;
    const items = Array.from(track.children) as HTMLElement[];
    const closest = items.reduce((best, item) => {
      const distance = Math.abs(item.offsetLeft + item.offsetWidth / 2 - center);
      return distance < best.distance ? { distance, item } : best;
    }, { distance: Number.POSITIVE_INFINITY, item: items[0] });
    return closest.item?.dataset.pose ?? step.pose;
  };

  const handleScroll = () => {
    const pose = centeredPose();
    setFocusedPose(pose);
    if (selectionTimerRef.current !== null) window.clearTimeout(selectionTimerRef.current);
    if (swipeRef.current?.dragging) return;
    selectionTimerRef.current = window.setTimeout(() => {
      if (freePoses.includes(pose as FreePoseName)) onSelect(pose as FreePoseName);
    }, 120);
  };

  const handleWheel = (event: WheelEvent<HTMLDivElement>) => {
    if (Math.abs(event.deltaY) <= Math.abs(event.deltaX)) return;
    event.preventDefault();
    event.currentTarget.scrollLeft += event.deltaY;
  };

  return (
    <article
      className="pose-carousel-card"
      data-tour={`pose-${index + 1}`}
    >
      <header>
        <span>Pose {index + 1}</span>
      </header>
      <div ref={trackRef} className="inline-pose-track" onScroll={handleScroll} onWheel={handleWheel}
        onPointerDown={(event) => {
          if (event.pointerType !== 'mouse' || event.button !== 0) return;
          suppressClickRef.current = false;
          swipeRef.current = { x: event.clientX, scrollLeft: event.currentTarget.scrollLeft, dragging: false };
        }}
        onPointerMove={(event) => {
          const swipe = swipeRef.current;
          if (!swipe || Math.abs(event.clientX - swipe.x) < 6) return;
          swipe.dragging = true;
          event.currentTarget.setPointerCapture(event.pointerId);
          event.currentTarget.classList.add('dragging');
          event.currentTarget.scrollLeft = swipe.scrollLeft + swipe.x - event.clientX;
        }}
        onPointerUp={(event) => {
          const swipe = swipeRef.current;
          swipeRef.current = null;
          if (!swipe?.dragging) return;
          suppressClickRef.current = true;
          event.currentTarget.classList.remove('dragging');
          const pose = centeredPose();
          const selectedIndex = visiblePoses.indexOf(pose);
          const item = event.currentTarget.children[selectedIndex] as HTMLElement | undefined;
          if (item) event.currentTarget.scrollTo({ left: item.offsetLeft - (event.currentTarget.clientWidth - item.offsetWidth) / 2, behavior: 'smooth' });
          if (freePoses.includes(pose as FreePoseName)) onSelect(pose as FreePoseName);
        }}
        onPointerCancel={(event) => { swipeRef.current = null; event.currentTarget.classList.remove('dragging'); }}
        onClickCapture={(event) => { if (suppressClickRef.current) { event.preventDefault(); event.stopPropagation(); suppressClickRef.current = false; } }}
        onKeyDown={(event) => {
          if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
          event.preventDefault();
          const nextIndex = Math.max(0, Math.min(visiblePoses.length - 1, visiblePoses.indexOf(centeredPose()) + (event.key === 'ArrowRight' ? 1 : -1)));
          const item = event.currentTarget.children[nextIndex] as HTMLElement;
          event.currentTarget.scrollTo({ left: item.offsetLeft - (event.currentTarget.clientWidth - item.offsetWidth) / 2, behavior: 'smooth' });
        }}>
        {visiblePoses.map((pose) => {
          const locked = !freePoses.includes(pose as FreePoseName);
          const selected = pose === step.pose;
          const focused = pose === focusedPose;
          return (
            <div
              key={pose}
              data-pose={pose}
              className={`inline-pose-option ${selected ? 'selected' : ''} ${focused ? 'focused' : ''} ${locked ? 'locked' : ''}`}
              role="button"
              tabIndex={locked ? -1 : 0}
              onClick={() => { if (locked) onUpgrade(); else onSelect(pose as FreePoseName); }}
              onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); if (locked) onUpgrade(); else onSelect(pose as FreePoseName); } }}
            >
              <strong>{pose === 'Wide-Legged Fold' ? 'Wide-Leg Fold' : pose}</strong>
              <PoseFigure pose={pose} />
              {locked ? <span className="pose-lock">Unlock Pro</span> : selected ? (
                <div className="pose-card-actions">
                  <button type="button" disabled={guided} onClick={(event) => { event.stopPropagation(); onDuration(); }}>{step.duration} sec</button>
                  <button type="button" disabled={guided} onClick={(event) => { event.stopPropagation(); onTry(); }}>Try pose</button>
                </div>
              ) : null}
            </div>
          );
        })}
      </div>
      <div className="pose-carousel-dots" aria-hidden="true">
        {visiblePoses.map((pose) => <i key={pose} className={pose === focusedPose ? 'active' : ''} />)}
      </div>
    </article>
  );
}

function DurationPicker({ selected, onUpgrade, onClose, onSelect }: { selected: number; onUpgrade: () => void; onClose: () => void; onSelect: (duration: number) => void }) {
  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <section className="choice-sheet duration-sheet" role="dialog" aria-modal="true" aria-labelledby="duration-title" onClick={(event) => event.stopPropagation()}>
        <i className="sheet-handle" />
        <div className="duration-sheet-heading"><div><h2 id="duration-title">Hold duration</h2><p>20 seconds is included in the free routine.</p></div><button type="button" onClick={onClose} aria-label="Close duration selection">×</button></div>
        <div className="duration-choice-grid">
          {durations.map((duration) => {
            const locked = duration !== 20;
            return (
              <button
                key={duration}
                className={`${duration === selected ? 'selected' : ''} ${locked ? 'locked' : ''}`}
                aria-pressed={duration === selected}
                onClick={() => locked ? onUpgrade() : onSelect(duration)}
              >
                <span>{duration} seconds</span><small>{locked ? 'PRO 🔒' : duration === selected ? '✓' : ''}</small>
              </button>
            );
          })}
        </div>
      </section>
    </div>
  );
}

function weekdaySummary(days: number[]) {
  if (days.length === 7) return 'Every day';
  if ([1, 2, 3, 4, 5].every((day) => days.includes(day)) && days.length === 5) return 'Weekdays';
  if ([6, 7].every((day) => days.includes(day)) && days.length === 2) return 'Weekends';
  if (!days.length) return 'Once';
  const labels = ['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  return days.slice().sort().map((day) => labels[day]).join(', ');
}

function two(value: number) { return value.toString().padStart(2, '0'); }

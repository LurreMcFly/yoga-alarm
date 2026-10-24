'use client';

import { useCallback, useEffect, useRef, useState, type UIEvent, type WheelEvent } from 'react';
import Onboarding, { type TourStep } from './Onboarding';
import { alarmSounds, SoundPicker, SnoozePicker, type AlarmSoundId } from './AlarmOptions';
import RecordingPointer from './RecordingPointer';

export type FreePoseName = 'Mountain' | 'Warrior II' | 'Tree';
export type PoseName = FreePoseName | 'Chair' | 'Forward Fold' | 'Triangle' | 'Goddess' | 'Wide-Legged Fold';
export type AlarmPoseStep = { pose: PoseName; duration: number };
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
const proPoses: PoseName[] = ['Chair', 'Forward Fold', 'Triangle', 'Goddess', 'Wide-Legged Fold'];
const allPoses: PoseName[] = [...freePoses, ...proPoses];
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
  const [proPreview, setProPreview] = useState(false);
  const [recording, setRecording] = useState(false);
  const [tourStep, setTourStep] = useState<TourStep | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [savedTourAlarm, setSavedTourAlarm] = useState<BrowserAlarm | null>(null);
  const [practicePose, setPracticePose] = useState<PoseName | null>(null);
  const phoneRef = useRef<HTMLElement>(null);
  const tourDraftRef = useRef<BrowserAlarm | null>(null);
  const storageKeyRef = useRef('yoga-alarm-browser-alarms');

  useEffect(() => {
    const parameters = new URLSearchParams(window.location.search);
    const recordingMode = process.env.NODE_ENV === 'development' && parameters.get('demo') === 'pro';
    setProPreview(recordingMode);
    setRecording(recordingMode);
    storageKeyRef.current = recordingMode ? 'yoga-alarm-browser-pro-demo-v1' : 'yoga-alarm-browser-alarms';
    if (recordingMode && parameters.get('reset') === '1') {
      window.localStorage.removeItem(storageKeyRef.current);
      const url = new URL(window.location.href);
      url.searchParams.delete('reset');
      window.history.replaceState(null, '', url);
    }
    const stored = window.localStorage.getItem(storageKeyRef.current);
    if (stored) {
      try { setAlarms((JSON.parse(stored) as BrowserAlarm[]).map((alarm) => ({
        ...alarm,
        alarmSound: alarmSounds.some((sound) => sound.id === alarm.alarmSound) ? alarm.alarmSound : 'sunbird_morning_call',
        snoozeMinutes: alarm.snoozeMinutes ?? 5,
        snoozeCount: alarm.snoozeCount ?? 1,
        manualStop: alarm.manualStop ?? true,
      }))); } catch { setAlarms([]); }
    } else if (recordingMode) {
      const initial = [{ ...createDefaultAlarm(), name: 'Morning stretch', hour: 8, minute: 0 }];
      window.localStorage.setItem(storageKeyRef.current, JSON.stringify(initial));
      setAlarms(initial);
    }
    if (!recordingMode && !window.localStorage.getItem('yoga-alarm-browser-onboarding-v1')) setTourStep('welcome');
    setLoaded(true);
  }, []);

  const persist = (next: BrowserAlarm[]) => {
    setAlarms(next);
    window.localStorage.setItem(storageKeyRef.current, JSON.stringify(next));
  };

  const saveDraft = () => {
    if (!draft) return;
    const saved = { ...draft, enabled: true, name: draft.name.trim() || defaultAlarmName(draft.hour, alarms.filter((alarm) => alarm.id !== draft.id).map((alarm) => alarm.name)) };
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
    <main className={`app-preview-shell ${recording ? 'recording-demo' : ''}`}>
      {recording ? <RecordingPointer /> : null}
      <section ref={phoneRef} className={`phone-app ${draft ? 'editor-open' : ''} ${tourStep ? 'tour-active' : ''}`}>
        {draft ? (
          <AlarmEditor
            proPreview={proPreview}
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
            proPreview={proPreview}
            demoStreak={recording}
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
            proPreview={proPreview}
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
          proPreview={proPreview}
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
              {process.env.NODE_ENV === 'development' ? <div className="alarm-setting"><div><strong>Pro preview</strong><span>Local test only · no purchase.</span></div><button type="button" className={`ui-switch ${proPreview ? 'on' : ''}`} role="switch" aria-label="Pro preview" aria-checked={proPreview} onClick={() => setProPreview(!proPreview)}><i /></button></div> : null}
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

function AlarmHome({ alarms, proPreview, demoStreak, onAdd, onEdit, onToggle, onSettings, onUpgrade }: {
  alarms: BrowserAlarm[];
  proPreview: boolean;
  demoStreak: boolean;
  onAdd: () => void;
  onEdit: (alarm: BrowserAlarm) => void;
  onToggle: (id: number, enabled: boolean) => void;
  onSettings: () => void;
  onUpgrade: () => void;
}) {
  return (
    <div className="alarm-home">
      <div className="alarm-list-toolbar">
        <h1>Your alarms</h1><div className="home-toolbar-actions">{!proPreview ? <button type="button" className="get-pro-button" onClick={onUpgrade}>GET PRO</button> : null}<button type="button" className="settings-button" aria-label="Settings" onClick={onSettings}>⚙</button></div>
      </div>
      <StreakCard demo={demoStreak} />
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

function StreakCard({ demo }: { demo: boolean }) {
  const [expanded, setExpanded] = useState(false);
  const [months, setMonths] = useState<1 | 3 | 12>(3);
  const today = new Date();
  today.setHours(12, 0, 0, 0);
  const yearStart = new Date(today.getFullYear(), today.getMonth() - 11, 1, 12);
  const lastBreak = Array.from({ length: 12 }, (_, offset) => new Date(today.getFullYear(), today.getMonth(), today.getDate() - offset, 12))
    .filter((date) => date.getDay() !== 0 && date.getDay() !== 6)[6];
  const status = (date: Date) => {
    if (date > today) return 'upcoming';
    if (!demo) return 'empty';
    if (date.getDay() === 0 || date.getDay() === 6) return 'rest';
    if (date.getTime() === lastBreak.getTime()) return 'missed';
    if (date > lastBreak) return 'completed';
    return date.getDate() === 7 || date.getDate() === 19 ? 'missed' : 'completed';
  };
  let streak = 0;
  let best = 0;
  for (let date = new Date(yearStart); date <= today; date.setDate(date.getDate() + 1)) {
    const day = status(date);
    if (day === 'completed') { streak++; best = Math.max(best, streak); }
    else if (day === 'missed') streak = 0;
  }
  const firstMonth = new Date(today.getFullYear(), today.getMonth() - months + 1, 1, 12);
  const dayLabel = { completed: 'Yoga completed', rest: 'Rest day', missed: 'Yoga not completed', upcoming: 'Upcoming', empty: 'No history' };
  const dayCell = (date: Date) => (
    <span key={date.toISOString()} className={`streak-history-day ${status(date)} ${date.getTime() === today.getTime() ? 'current-day' : ''}`}
      title={`${date.toLocaleDateString(undefined, { dateStyle: 'full' })}: ${dayLabel[status(date)]}`}>
    </span>
  );
  const monthGrid = (date: Date, showDates: boolean) => {
    const padding = (date.getDay() + 6) % 7;
    const dayCount = new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate();
    return <div className={`streak-month-grid ${showDates ? 'numbered' : ''}`}>
      {showDates ? weekdays.map((day) => <small key={day.value}>{day.label}</small>) : null}
      {Array.from({ length: padding }, (_, index) => <span key={`pad-${index}`} />)}
      {Array.from({ length: dayCount }, (_, index) => dayCell(new Date(date.getFullYear(), date.getMonth(), index + 1, 12)))}
    </div>;
  };
  const firstMonday = new Date(firstMonth);
  firstMonday.setDate(firstMonday.getDate() - (firstMonday.getDay() + 6) % 7);
  const lastSunday = new Date(today);
  lastSunday.setDate(lastSunday.getDate() + (7 - lastSunday.getDay()) % 7);
  const weeks = Math.floor((lastSunday.getTime() - firstMonday.getTime()) / 604800000) + 1;
  const rangeLabel = months === 1
    ? today.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
    : `${firstMonth.toLocaleDateString(undefined, { month: 'short', ...(firstMonth.getFullYear() !== today.getFullYear() ? { year: 'numeric' as const } : {}) })} – ${today.toLocaleDateString(undefined, { month: 'short', year: 'numeric' })}`;

  return (
    <section className={`preview-streak ${expanded ? 'expanded' : ''}`} aria-label={demo ? 'Sample streak history' : 'Streak preview. No Android history is loaded.'}>
      <button type="button" className="streak-summary" aria-label={expanded ? 'Hide streak history' : 'Show streak history'} aria-expanded={expanded} aria-controls="streak-history" onClick={() => setExpanded(!expanded)}>
        <div className="streak-count"><small>day streak</small><strong>🔥 {streak}</strong></div>
        {expanded ? <div className="streak-personal-best"><small>Personal best</small><b>{best} days</b></div> :
          <div className="streak-dates">{[-4, -3, -2, -1, 0, 1, 2].map((offset) => {
            const date = new Date(today.getFullYear(), today.getMonth(), today.getDate() + offset, 12);
            const day = status(date);
            return <div key={offset} className={`${offset === 0 ? 'today' : ''} ${day}`}><small>{date.toLocaleDateString(undefined, { weekday: 'narrow' })}</small><span aria-label={dayLabel[day]}>{day === 'completed' ? '✓' : day === 'rest' ? 'Ⅱ' : date.getDate()}</span></div>;
          })}</div>}
        <span className="streak-chevron" aria-hidden="true">⌄</span>
      </button>
      <div className="streak-history-reveal" inert={!expanded}>
        <div className="streak-history-clip">
          <div id="streak-history" className="streak-history-inner" aria-label="Streak history">
            <div className="streak-history-heading"><span>{rangeLabel}</span><div className="streak-history-legend"><span><i className="completed" />Yoga</span><span><i className="rest" />Rest</span><span><i className="missed" />{demo ? 'Missed' : 'No history'}</span></div></div>
            <div className={`streak-history-plot range-${months}`} role="img" aria-label={`${rangeLabel}. ${demo ? 'Sample daily yoga and rest history. Green is completed yoga; yellow is rest; grey is missed.' : 'No streak history loaded.'}`}>
              {months === 1 ? monthGrid(firstMonth, true) : months === 3 ?
                <div className="streak-season-grid" style={{ gridTemplateColumns: `18px repeat(${weeks}, minmax(0, 1fr))` }}>
                  <span />{Array.from({ length: weeks }, (_, week) => {
                    const date = new Date(firstMonday); date.setDate(date.getDate() + week * 7);
                    const previous = new Date(date); previous.setDate(previous.getDate() - 7);
                    return <small key={week}>{week === 0 ? firstMonth.toLocaleDateString(undefined, { month: 'short' }) : previous.getMonth() !== date.getMonth() && date.getMonth() !== firstMonth.getMonth() ? date.toLocaleDateString(undefined, { month: 'short' }) : ''}</small>;
                  })}
                  {weekdays.map((day, row) => <div key={day.value} className="streak-season-row"><small>{row % 2 === 0 ? day.label : ''}</small>{Array.from({ length: weeks }, (_, week) => {
                    const date = new Date(firstMonday); date.setDate(date.getDate() + week * 7 + row);
                    return date < firstMonth ? <span key={week} /> : dayCell(date);
                  })}</div>)}
                </div> :
                <div className="streak-year-grid">{Array.from({ length: 12 }, (_, index) => {
                  const date = new Date(firstMonth.getFullYear(), firstMonth.getMonth() + index, 1, 12);
                  return <div className="streak-mini-month" key={index}><small>{date.toLocaleDateString(undefined, { month: 'short' })}</small>{monthGrid(date, false)}</div>;
                })}</div>}
            </div>
            <div className="streak-range-toggle" role="group" aria-label="Streak history range">{([1, 3, 12] as const).map((value) => <button key={value} type="button" aria-pressed={months === value} onClick={() => setMonths(value)}>{value === 12 ? '1 year' : value === 1 ? '1 month' : '3 months'}</button>)}</div>
          </div>
        </div>
      </div>
    </section>
  );
}

function AlarmEditor({ draft, guided, proPreview, onChange, onCancel, onSave, onTest, onTryPose, onSelectDuration, onSound, onSnooze, onUpgrade }: {
  draft: BrowserAlarm;
  guided: boolean;
  proPreview: boolean;
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
              proPreview={proPreview}
              onSelect={(pose) => onChange({ ...draft, routine: draft.routine.map((candidate, slot) => slot === index ? { ...candidate, pose } : candidate) })}
              onDuration={() => onSelectDuration(index)}
              onTry={() => onTryPose(index)}
              onUpgrade={onUpgrade}
            />
          ))}
        </div>
        <div className="routine-size-actions">
          {proPreview && draft.routine.length > 1 ? <button type="button" className="add-pose-button remove-pose-button" disabled={guided} onClick={() => onChange({ ...draft, routine: draft.routine.slice(0, -1) })}>− Remove pose</button> : null}
          <button type="button" className="add-pose-button" disabled={guided || (proPreview && draft.routine.length >= 10)} onClick={() => proPreview ? onChange({ ...draft, routine: [...draft.routine, { pose: allPoses[draft.routine.length % allPoses.length], duration: 20 }] }) : onUpgrade()}>+ Add pose{!proPreview ? <span>PRO</span> : null}</button>
        </div>
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
  const pendingValueRef = useRef<number | null>(null);

  useEffect(() => {
    if (!columnRef.current || initializedRef.current) return;
    columnRef.current.scrollTop = (count * 2 + value) * TIME_ROW_HEIGHT;
    initializedRef.current = true;
  }, [count, value]);

  const handleScroll = (event: UIEvent<HTMLDivElement>) => {
    const column = event.currentTarget;
    const row = Math.round(column.scrollTop / TIME_ROW_HEIGHT);
    const next = ((row % count) + count) % count;
    if (pendingValueRef.current !== null && next !== pendingValueRef.current) return;
    pendingValueRef.current = null;
    if (next !== value) onChange(next);
    if (row < count || row >= count * (TIME_WHEEL_CYCLES - 1)) {
      column.scrollTop = (count * 2 + next) * TIME_ROW_HEIGHT;
    }
  };

  return (
    <div ref={columnRef} className="time-column" role="spinbutton" aria-label={count === 24 ? 'Hours' : 'Minutes'} aria-valuemin={0} aria-valuemax={count - 1} aria-valuenow={value} tabIndex={0} onScroll={handleScroll} onPointerDown={() => { pendingValueRef.current = null; }} onWheel={() => { pendingValueRef.current = null; }} onKeyDown={(event) => {
      if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return;
      event.preventDefault();
      const next = ((pendingValueRef.current ?? value) + (event.key === 'ArrowUp' ? 1 : -1) + count) % count;
      pendingValueRef.current = next;
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
          onClick={() => {
            pendingValueRef.current = index % count;
            onChange(index % count);
            columnRef.current?.scrollTo({ top: index * TIME_ROW_HEIGHT, behavior: 'smooth' });
          }}
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

function PoseCarouselCard({ index, step, guided, proPreview, onSelect, onDuration, onTry, onUpgrade }: {
  index: number;
  step: AlarmPoseStep;
  guided: boolean;
  proPreview: boolean;
  onSelect: (pose: PoseName) => void;
  onDuration: () => void;
  onTry: () => void;
  onUpgrade: () => void;
}) {
  const trackRef = useRef<HTMLDivElement>(null);
  const selectionTimerRef = useRef<number | null>(null);
  const alignmentTargetRef = useRef<number | null>(null);
  const hasAlignedRef = useRef(false);
  const swipeRef = useRef<{ x: number; scrollLeft: number; dragging: boolean } | null>(null);
  const suppressClickRef = useRef(false);
  const [focusedPose, setFocusedPose] = useState<string>(step.pose);
  const visiblePoses = guided ? freePoses : allPoses;

  useEffect(() => {
    const track = trackRef.current;
    const selectedIndex = visiblePoses.indexOf(step.pose);
    const item = track?.children[selectedIndex] as HTMLElement | undefined;
    if (!track || !item) return;
    const alignSelection = (behavior: ScrollBehavior) => {
      if (selectionTimerRef.current !== null) window.clearTimeout(selectionTimerRef.current);
      const left = item.offsetLeft - (track.clientWidth - item.offsetWidth) / 2;
      alignmentTargetRef.current = Math.abs(track.scrollLeft - left) > 1 ? left : null;
      track.scrollTo({ left, behavior });
      setFocusedPose(step.pose);
    };
    alignSelection(hasAlignedRef.current ? 'smooth' : 'instant');
    hasAlignedRef.current = true;
    let width = track.clientWidth;
    const observer = new ResizeObserver(() => {
      if (track.clientWidth === width) return;
      width = track.clientWidth;
      alignSelection('instant');
    });
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
    if (alignmentTargetRef.current !== null) {
      if (Math.abs((trackRef.current?.scrollLeft ?? 0) - alignmentTargetRef.current) <= 1) alignmentTargetRef.current = null;
      return;
    }
    if (swipeRef.current?.dragging) return;
    selectionTimerRef.current = window.setTimeout(() => {
      if (proPreview || freePoses.includes(pose as FreePoseName)) onSelect(pose as PoseName);
    }, 120);
  };

  const handleWheel = (event: WheelEvent<HTMLDivElement>) => {
    alignmentTargetRef.current = null;
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
          alignmentTargetRef.current = null;
          if (selectionTimerRef.current !== null) window.clearTimeout(selectionTimerRef.current);
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
          const selectedIndex = visiblePoses.indexOf(pose as PoseName);
          const item = event.currentTarget.children[selectedIndex] as HTMLElement | undefined;
          if (item) event.currentTarget.scrollTo({ left: item.offsetLeft - (event.currentTarget.clientWidth - item.offsetWidth) / 2, behavior: 'smooth' });
          if (proPreview || freePoses.includes(pose as FreePoseName)) onSelect(pose as PoseName);
        }}
        onPointerCancel={(event) => { swipeRef.current = null; event.currentTarget.classList.remove('dragging'); }}
        onClickCapture={(event) => { if (suppressClickRef.current) { event.preventDefault(); event.stopPropagation(); suppressClickRef.current = false; } }}
        onKeyDown={(event) => {
          if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
          event.preventDefault();
          alignmentTargetRef.current = null;
          const nextIndex = Math.max(0, Math.min(visiblePoses.length - 1, visiblePoses.indexOf(centeredPose() as PoseName) + (event.key === 'ArrowRight' ? 1 : -1)));
          const item = event.currentTarget.children[nextIndex] as HTMLElement;
          event.currentTarget.scrollTo({ left: item.offsetLeft - (event.currentTarget.clientWidth - item.offsetWidth) / 2, behavior: 'smooth' });
        }}>
        {visiblePoses.map((pose) => {
          const locked = !proPreview && !freePoses.includes(pose as FreePoseName);
          const selected = pose === step.pose;
          const focused = pose === focusedPose;
          return (
            <div
              key={pose}
              data-pose={pose}
              className={`inline-pose-option ${selected ? 'selected' : ''} ${focused ? 'focused' : ''} ${locked ? 'locked' : ''}`}
              role="button"
              tabIndex={locked ? -1 : 0}
              onClick={() => { if (locked) onUpgrade(); else onSelect(pose); }}
              onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); if (locked) onUpgrade(); else onSelect(pose); } }}
            >
              <strong>{pose === 'Wide-Legged Fold' ? 'Wide-Leg Fold' : pose}</strong>
              <PoseFigure pose={pose} />
              {locked ? <span className="pose-lock">Tap to unlock Pro</span> : selected ? (
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

function DurationPicker({ selected, proPreview, onUpgrade, onClose, onSelect }: { selected: number; proPreview: boolean; onUpgrade: () => void; onClose: () => void; onSelect: (duration: number) => void }) {
  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <section className="choice-sheet duration-sheet" role="dialog" aria-modal="true" aria-labelledby="duration-title" onClick={(event) => event.stopPropagation()}>
        <i className="sheet-handle" />
        <div className="duration-sheet-heading"><div><h2 id="duration-title">Hold duration</h2><p>20 seconds is included in the free routine.</p></div><button type="button" onClick={onClose} aria-label="Close duration selection">×</button></div>
        <div className="duration-choice-list">
          {durations.map((duration) => {
            const locked = !proPreview && duration !== 20;
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

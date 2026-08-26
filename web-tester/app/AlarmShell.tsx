'use client';

import { useEffect, useRef, useState, type DragEvent, type PointerEvent, type UIEvent, type WheelEvent } from 'react';

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
};

const freePoses: FreePoseName[] = ['Mountain', 'Warrior II', 'Tree'];
const proPoses = ['Chair', 'Forward Fold', 'Triangle', 'Goddess', 'Wide-Legged Fold'];
const allPoses = [...freePoses, ...proPoses];
const durations = [10, 15, 20, 30, 45, 60];
const weekdays = [
  { value: 7, label: 'S' }, { value: 1, label: 'M' }, { value: 2, label: 'T' },
  { value: 3, label: 'W' }, { value: 4, label: 'T' }, { value: 5, label: 'F' }, { value: 6, label: 'S' },
];

export function createDefaultAlarm(): BrowserAlarm {
  return {
    id: Date.now(),
    name: 'Morning movement',
    hour: 7,
    minute: 0,
    weekdays: [1, 2, 3, 4, 5],
    enabled: true,
    routine: freePoses.map((pose) => ({ pose, duration: 20 })),
    sound: true,
    vibration: true,
    snooze: true,
  };
}

export default function AlarmShell({
  resumeDraft,
  onTestRoutine,
}: {
  resumeDraft: BrowserAlarm | null;
  onTestRoutine: (alarm: BrowserAlarm, resumeAlarm?: BrowserAlarm) => void;
}) {
  const [alarms, setAlarms] = useState<BrowserAlarm[]>([]);
  const [draft, setDraft] = useState<BrowserAlarm | null>(resumeDraft);
  const [loaded, setLoaded] = useState(false);
  const [durationSlot, setDurationSlot] = useState<number | null>(null);
  const [previewProDurations, setPreviewProDurations] = useState(false);

  useEffect(() => {
    const stored = window.localStorage.getItem('yoga-alarm-browser-alarms');
    if (stored) {
      try { setAlarms(JSON.parse(stored) as BrowserAlarm[]); } catch { setAlarms([]); }
    }
    setLoaded(true);
  }, []);

  const persist = (next: BrowserAlarm[]) => {
    setAlarms(next);
    window.localStorage.setItem('yoga-alarm-browser-alarms', JSON.stringify(next));
  };

  const saveDraft = () => {
    if (!draft) return;
    const saved = { ...draft, name: draft.name.trim() || 'Morning movement' };
    persist(alarms.some((alarm) => alarm.id === saved.id)
      ? alarms.map((alarm) => alarm.id === saved.id ? saved : alarm)
      : [...alarms, saved]);
    setDraft(null);
  };

  if (!loaded) return <main className="app-preview-shell" />;

  return (
    <main className="app-preview-shell">
      <section className={`phone-app ${draft ? 'editor-open' : ''}`}>
        {draft ? (
          <AlarmEditor
            draft={draft}
            previewProDurations={previewProDurations}
            onChange={setDraft}
            onCancel={() => setDraft(null)}
            onSave={saveDraft}
            onTest={() => onTestRoutine(draft)}
            onTryPose={(slot) => onTestRoutine({ ...draft, routine: [draft.routine[slot]] }, draft)}
            onSelectDuration={setDurationSlot}
          />
        ) : (
          <AlarmHome
            alarms={alarms}
            onAdd={() => setDraft(createDefaultAlarm())}
            onEdit={setDraft}
            onToggle={(id, enabled) => persist(alarms.map((alarm) => alarm.id === id ? { ...alarm, enabled } : alarm))}
          />
        )}
        {draft && durationSlot !== null ? (
          <DurationPicker
            selected={draft.routine[durationSlot].duration}
            previewPro={previewProDurations}
            onPreviewPro={setPreviewProDurations}
            onClose={() => setDurationSlot(null)}
            onSelect={(duration) => {
              setDraft({ ...draft, routine: draft.routine.map((step, index) => index === durationSlot ? { ...step, duration } : step) });
              setDurationSlot(null);
            }}
          />
        ) : null}
      </section>
    </main>
  );
}

function AlarmHome({ alarms, onAdd, onEdit, onToggle }: {
  alarms: BrowserAlarm[];
  onAdd: () => void;
  onEdit: (alarm: BrowserAlarm) => void;
  onToggle: (id: number, enabled: boolean) => void;
}) {
  return (
    <div className="alarm-home">
      <div className="phone-status"><span>Yoga Alarm</span><span>Local preview</span></div>
      <div className="alarm-list-toolbar">
        <div><h1>Your alarms</h1><p>Wake up moving.</p></div>
        <button type="button" className="round-add" onClick={onAdd} aria-label="Add alarm">+</button>
      </div>
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
            <span>Create a calm one-minute routine for tomorrow morning.</span>
            <b>Create alarm →</b>
          </button>
        )}
      </div>
    </div>
  );
}

function AlarmEditor({ draft, previewProDurations, onChange, onCancel, onSave, onTest, onTryPose, onSelectDuration }: {
  draft: BrowserAlarm;
  previewProDurations: boolean;
  onChange: (alarm: BrowserAlarm) => void;
  onCancel: () => void;
  onSave: () => void;
  onTest: () => void;
  onTryPose: (slot: number) => void;
  onSelectDuration: (slot: number) => void;
}) {
  const reorderRoutine = (from: number, to: number) => {
    if (from === to || to < 0 || to >= draft.routine.length) return;
    const routine = [...draft.routine];
    const [moved] = routine.splice(from, 1);
    routine.splice(to, 0, moved);
    onChange({ ...draft, routine });
  };

  return (
    <div className="alarm-editor">
      <div className="time-wheel" aria-label="Alarm time">
        <TimeColumn value={draft.hour} count={24} onChange={(hour) => onChange({ ...draft, hour })} />
        <span className="time-colon">:</span>
        <TimeColumn value={draft.minute} count={60} onChange={(minute) => onChange({ ...draft, minute })} />
      </div>

      <section className="alarm-editor-card">
        <input className="alarm-name-input" value={draft.name} maxLength={40} aria-label="Alarm name" onChange={(event) => onChange({ ...draft, name: event.target.value })} />
        <div className="weekday-picker" aria-label="Repeat days">
          {weekdays.map((day) => {
            const selected = draft.weekdays.includes(day.value);
            return <button key={day.value} className={selected ? 'selected' : ''} onClick={() => onChange({ ...draft, weekdays: selected ? draft.weekdays.filter((value) => value !== day.value) : [...draft.weekdays, day.value] })}>{day.label}</button>;
          })}
        </div>

        <div className="alarm-setting-list">
          <SettingRow title="Snooze" detail="5 minutes, once" checked={draft.snooze} onChange={(snooze) => onChange({ ...draft, snooze })} />
          <details className="alarm-options">
            <summary>Alarm options <span>Sound and vibration</span></summary>
            <SettingRow title="Sound" detail="Morning chimes" checked={draft.sound} onChange={(sound) => onChange({ ...draft, sound })} />
            <SettingRow title="Vibration" detail="Gentle pulse" checked={draft.vibration} onChange={(vibration) => onChange({ ...draft, vibration })} />
          </details>
        </div>

        <div className="routine-heading"><span>Morning routine</span><b>{draft.routine.length} poses · {draft.routine.reduce((sum, step) => sum + step.duration, 0)} seconds</b></div>
        <div className="pose-carousel-list">
          {draft.routine.map((step, index) => (
            <PoseCarouselCard
              key={index}
              index={index}
              step={step}
              onSelect={(pose) => onChange({ ...draft, routine: draft.routine.map((candidate, slot) => slot === index ? { ...candidate, pose } : candidate) })}
              onDuration={() => onSelectDuration(index)}
              onTry={() => onTryPose(index)}
              onReorder={reorderRoutine}
            />
          ))}
        </div>
        <button type="button" className="add-pose-button" disabled>+ Add another pose <span>PRO</span></button>
        <button type="button" className="test-routine-button" onClick={onTest}>Test complete routine</button>
      </section>

      <div className="editor-actions">
        <button type="button" onClick={onCancel}>Cancel</button><i /><button type="button" onClick={onSave}>Save</button>
      </div>
      {previewProDurations ? <span className="pro-preview-badge">Pro duration preview on</span> : null}
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
    <div ref={columnRef} className="time-column" onScroll={handleScroll}>
      {Array.from({ length: count * TIME_WHEEL_CYCLES }, (_, index) => (
        <button
          type="button"
          key={index}
          className={index % count === value ? 'selected' : ''}
          onClick={() => columnRef.current?.scrollTo({ top: index * TIME_ROW_HEIGHT, behavior: 'smooth' })}
        >{two(index % count)}</button>
      ))}
    </div>
  );
}

function PoseFigure({ pose }: { pose: string }) {
  const poseClass = pose.toLowerCase().replaceAll(' ', '-').replace('ii', 'two');
  return <img className="pose-illustration" src={`/poses/${poseClass}.png`} alt={`${pose} pose illustration`} />;
}

function SettingRow({ title, detail, checked, onChange }: { title: string; detail: string; checked: boolean; onChange: (checked: boolean) => void }) {
  return (
    <div className="alarm-setting"><div><strong>{title}</strong><span>{detail}</span></div><button type="button" className={`ui-switch ${checked ? 'on' : ''}`} onClick={() => onChange(!checked)}><i /></button></div>
  );
}

function PoseCarouselCard({ index, step, onSelect, onDuration, onTry, onReorder }: {
  index: number;
  step: AlarmPoseStep;
  onSelect: (pose: FreePoseName) => void;
  onDuration: () => void;
  onTry: () => void;
  onReorder: (from: number, to: number) => void;
}) {
  const trackRef = useRef<HTMLDivElement>(null);
  const selectionTimerRef = useRef<number | null>(null);
  const pointerStartYRef = useRef<number | null>(null);
  const [focusedPose, setFocusedPose] = useState<string>(step.pose);

  useEffect(() => {
    const track = trackRef.current;
    const selectedIndex = allPoses.indexOf(step.pose);
    const item = track?.children[selectedIndex] as HTMLElement | undefined;
    if (!track || !item) return;
    track.scrollTo({ left: item.offsetLeft - (track.clientWidth - item.offsetWidth) / 2, behavior: 'smooth' });
    setFocusedPose(step.pose);
  }, [step.pose]);

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
    selectionTimerRef.current = window.setTimeout(() => {
      if (freePoses.includes(pose as FreePoseName)) onSelect(pose as FreePoseName);
    }, 120);
  };

  const handleWheel = (event: WheelEvent<HTMLDivElement>) => {
    if (Math.abs(event.deltaY) <= Math.abs(event.deltaX)) return;
    event.preventDefault();
    event.currentTarget.scrollLeft += event.deltaY;
  };

  const handlePointerDown = (event: PointerEvent<HTMLButtonElement>) => {
    pointerStartYRef.current = event.clientY;
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const handlePointerUp = (event: PointerEvent<HTMLButtonElement>) => {
    if (pointerStartYRef.current === null) return;
    const movement = event.clientY - pointerStartYRef.current;
    pointerStartYRef.current = null;
    if (Math.abs(movement) > 32) onReorder(index, index + (movement > 0 ? 1 : -1));
  };

  const handleDragStart = (event: DragEvent<HTMLButtonElement>) => {
    event.dataTransfer.setData('text/plain', String(index));
    event.dataTransfer.effectAllowed = 'move';
  };

  return (
    <article
      className="pose-carousel-card"
      onDragOver={(event) => event.preventDefault()}
      onDrop={(event) => onReorder(Number(event.dataTransfer.getData('text/plain')), index)}
    >
      <header>
        <span>Pose {index + 1}</span>
        <button
          type="button"
          className="pose-drag-handle"
          draggable
          aria-label={`Move pose ${index + 1}. Drag vertically or use arrow keys.`}
          onDragStart={handleDragStart}
          onPointerDown={handlePointerDown}
          onPointerUp={handlePointerUp}
          onKeyDown={(event) => {
            if (event.key === 'ArrowUp') onReorder(index, index - 1);
            if (event.key === 'ArrowDown') onReorder(index, index + 1);
          }}
        >≡</button>
      </header>
      <div ref={trackRef} className="inline-pose-track" onScroll={handleScroll} onWheel={handleWheel}>
        {allPoses.map((pose) => {
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
              onClick={() => { if (!locked) onSelect(pose as FreePoseName); }}
              onKeyDown={(event) => { if (!locked && (event.key === 'Enter' || event.key === ' ')) onSelect(pose as FreePoseName); }}
            >
              <small>{locked ? 'PRO · LOCKED' : selected ? 'SELECTED' : 'FREE'}</small>
              <PoseFigure pose={pose} />
              <strong>{pose}</strong>
              {locked ? <span className="pose-lock">Unlock Pro</span> : selected ? (
                <div className="pose-card-actions">
                  <button type="button" onClick={(event) => { event.stopPropagation(); onDuration(); }}>{step.duration} sec</button>
                  <button type="button" onClick={(event) => { event.stopPropagation(); onTry(); }}>Try pose</button>
                </div>
              ) : <span className="swipe-to-select">Swipe to select</span>}
            </div>
          );
        })}
      </div>
      <div className="pose-carousel-dots" aria-hidden="true">
        {allPoses.map((pose) => <i key={pose} className={pose === focusedPose ? 'active' : ''} />)}
      </div>
    </article>
  );
}

function DurationPicker({ selected, previewPro, onPreviewPro, onClose, onSelect }: { selected: number; previewPro: boolean; onPreviewPro: (enabled: boolean) => void; onClose: () => void; onSelect: (duration: number) => void }) {
  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <section className="choice-sheet duration-sheet" onClick={(event) => event.stopPropagation()}>
        <i className="sheet-handle" />
        <div className="duration-sheet-heading"><div><h2>Hold duration</h2><p>How long should this pose be held?</p></div><button type="button" onClick={onClose} aria-label="Close duration selection">×</button></div>
        <div className="duration-choice-grid">
          {durations.map((duration) => {
            const locked = duration !== 20 && !previewPro;
            return (
              <button
                key={duration}
                className={`${duration === selected ? 'selected' : ''} ${locked ? 'locked' : ''}`}
                disabled={locked}
                aria-pressed={duration === selected}
                onClick={() => onSelect(duration)}
              >
                <strong>{duration}</strong><span>seconds</span><small>{locked ? 'PRO' : duration === selected ? 'Selected' : 'Choose'}</small>
              </button>
            );
          })}
        </div>
        <label className="duration-pro-preview"><span><strong>Preview Pro options</strong><small>Available in this browser tester</small></span><input type="checkbox" checked={previewPro} onChange={(event) => onPreviewPro(event.target.checked)} /></label>
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

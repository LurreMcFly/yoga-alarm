'use client';

import { useLayoutEffect, useRef, useState, type RefObject } from 'react';
import type { BrowserAlarm } from './AlarmShell';

const steps = ['welcome', 'add', 'time', 'name', 'days', 'sound', 'sound-choice', 'routine', 'pose-1', 'pose-2', 'pose-3', 'placement', 'test', 'save', 'complete'] as const;
export type TourStep = typeof steps[number];

export default function Onboarding({ step, phoneRef, alarm, practised, onStep, onSkip, onBackToHome, onPractice, onTest }: {
  step: TourStep;
  phoneRef: RefObject<HTMLElement | null>;
  alarm: BrowserAlarm | null;
  practised: boolean;
  onStep: (step: TourStep) => void;
  onSkip: () => void;
  onBackToHome: () => void;
  onPractice: () => void;
  onTest: () => void;
}) {
  const overlayRef = useRef<HTMLDivElement>(null);
  const tipRef = useRef<HTMLElement>(null);
  const [hole, setHole] = useState<{ top: number; left: number; width: number; height: number; borderRadius: number } | null>(null);
  const index = steps.indexOf(step);
  const fullScreen = step === 'welcome' || step === 'routine' || step === 'placement' || step === 'complete';
  const tapControl = step === 'add' || step === 'sound' || step === 'save';

  useLayoutEffect(() => {
    const phone = phoneRef.current;
    const overlay = overlayRef.current;
    if (!phone || !overlay) return;
    const target = fullScreen ? null : phone.querySelector<HTMLElement>(`[data-tour="${step}"]`);
    const scroller = phone.querySelector<HTMLElement>(step === 'sound-choice' ? '.sound-options-scroll' : '.alarm-editor, .alarm-home');
    const measure = () => {
      if (!target) { setHole(null); return; }
      const bounds = phone.getBoundingClientRect();
      const rect = target.getBoundingClientRect();
      const card = step === 'name' || step === 'days' || step === 'sound' || step === 'test' ? target.closest('.alarm-editor-card')?.getBoundingClientRect() : null;
      setHole({
        top: Math.max(0, rect.top - bounds.top - phone.clientTop - (card ? 6 : 0)),
        left: Math.max(0, (card ? card.left + 2 : rect.left) - bounds.left - phone.clientLeft),
        width: Math.min(phone.clientWidth, card ? card.width - 4 : rect.width),
        height: Math.min(phone.clientHeight, rect.height + (card ? 12 : 0)),
        borderRadius: Math.min(parseFloat(getComputedStyle(target).borderTopLeftRadius) || 18, rect.width / 2, rect.height / 2),
      });
    };
    const position = () => {
      if (target && scroller && step !== 'save' && step !== 'add') {
        const bounds = phone.getBoundingClientRect();
        const rect = target.getBoundingClientRect();
        const availableBottom = bounds.height - (tipRef.current?.offsetHeight ?? 220) - 90;
        const targetTop = Math.max(68, (68 + availableBottom - rect.height) / 2);
        scroller.scrollTop = step === 'time' || step === 'sound-choice' ? 0 : scroller.scrollTop + rect.top - bounds.top - targetTop;
      }
      measure();
    };
    position();
    const observer = new ResizeObserver(position);
    observer.observe(phone);
    if (target) observer.observe(target);
    if (tipRef.current) observer.observe(tipRef.current);
    scroller?.addEventListener('scroll', measure, { passive: true });

    // Keep keyboard navigation within the highlighted control and tour controls.
    const keydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { onSkip(); return; }
      if (event.key !== 'Tab') return;
      const selector = 'button:not(:disabled), a[href], input:not(:disabled), [tabindex="0"]';
      const controls = [...(target?.querySelectorAll<HTMLElement>(selector) ?? []), ...overlay.querySelectorAll<HTMLElement>(selector)];
      if (target?.matches(selector)) controls.unshift(target);
      const visible = controls.filter((control) => control.getClientRects().length > 0);
      if (!visible.length) return;
      const focused = visible.indexOf(document.activeElement as HTMLElement);
      const next = (focused + (event.shiftKey ? -1 : 1) + visible.length) % visible.length;
      event.preventDefault();
      visible[next].focus({ preventScroll: true });
    };
    phone.addEventListener('keydown', keydown);
    (step === 'name' ? target : overlay.querySelector<HTMLElement>('[data-tour-focus]'))?.focus({ preventScroll: true });
    return () => {
      observer.disconnect();
      scroller?.removeEventListener('scroll', measure);
      phone.removeEventListener('keydown', keydown);
    };
  }, [step, fullScreen, phoneRef, onSkip]);

  const back = () => {
    if (step === 'time') onBackToHome();
    else onStep(steps[Math.max(0, index - 1)]);
  };

  let nextAlarm = '';
  if (step === 'complete' && alarm) {
    const now = new Date();
    for (let offset = 0; offset <= 7; offset++) {
      const candidate = new Date(now.getFullYear(), now.getMonth(), now.getDate() + offset, alarm.hour, alarm.minute);
      if (candidate > now && (!alarm.weekdays.length || alarm.weekdays.includes(candidate.getDay() || 7))) {
        nextAlarm = candidate.toLocaleString(undefined, { weekday: 'long', hour: '2-digit', minute: '2-digit', hour12: false });
        break;
      }
    }
  }

  const copy = step === 'add'
    ? { title: 'Add an alarm', detail: 'Tap Add alarm.' }
    : step === 'time'
      ? { title: 'Set the time', detail: 'Scroll to choose.' }
      : step === 'name'
        ? { title: 'Name your alarm', detail: 'Pick a name you’ll recognise.' }
      : step === 'days'
        ? { title: 'Choose repeat days', detail: 'Green = selected. No days = once.' }
        : step === 'sound'
          ? { title: 'Choose your alarm sound', detail: 'Tap Sound.' }
          : step === 'sound-choice'
            ? { title: 'Find your sound', detail: 'Preview, then choose.' }
        : step === 'routine'
          ? { title: 'Build your yoga routine', detail: 'Choose the poses you’ll do when your alarm rings.' }
        : step === 'pose-1'
          ? { title: 'Choose pose 1', detail: 'Swipe to explore your three free poses.' }
          : step === 'pose-2'
            ? { title: 'Choose pose 2', detail: 'Each pose lasts 20 seconds in Free.' }
            : step === 'pose-3'
              ? { title: 'Choose pose 3', detail: 'Your routine plays from top to bottom.' }
              : step === 'test'
                ? { title: 'Test your full routine', detail: 'Optional: try all your poses together.' }
                : { title: 'Save your alarm', detail: 'Keep your time, name and poses.' };

  return (
    <div ref={overlayRef} className={`onboarding ${fullScreen ? 'onboarding-full' : ''} ${step === 'routine' ? 'onboarding-routine' : ''} ${step === 'save' || step === 'add' || step === 'sound-choice' ? 'onboarding-top-tip' : ''}`} role={fullScreen ? 'dialog' : 'region'} aria-modal={fullScreen || undefined} aria-label="How to use Yoga Alarm">
      {!fullScreen ? <>
        {hole ? <>
          <div className="tour-dim" style={{ top: 0, left: 0, right: 0, height: hole.top }} />
          <div className="tour-dim" style={{ top: hole.top, left: 0, width: hole.left, height: hole.height }} />
          <div className="tour-dim" style={{ top: hole.top, left: hole.left + hole.width, right: 0, height: hole.height }} />
          <div className="tour-dim" style={{ top: hole.top + hole.height, left: 0, right: 0, bottom: 0 }} />
          <div className="tour-outline" style={hole}>
            {step !== 'sound-choice' ? <span aria-hidden="true" className={`tour-hand ${step.startsWith('pose-') ? 'tour-hand-swipe' : ''}`}>{step === 'save' || step === 'add' ? '👇' : '👆'}</span> : null}
          </div>
        </> : <div className="tour-dim tour-dim-full" style={{ inset: 0 }} />}
      </> : null}

      {step !== 'complete' ? <nav className="tour-nav" aria-label="Tour navigation">
        <button type="button" onClick={index === 0 ? onSkip : back}>{index === 0 ? 'Close' : '← Back'}</button>
        <span>{index + 1} / {steps.length}</span>
        <button type="button" onClick={onSkip}>Skip tour</button>
      </nav> : null}

      {step === 'welcome' ? (
        <section className="tour-page tour-welcome">
          <span className="tour-eyebrow">Yoga Alarm</span>
          <h1>An alarm that<br />gets you moving.</h1>
          <img src="/poses/mountain.png" alt="Yoga Alarm’s guide standing in Mountain pose" className="tour-welcome-figure" />
          <div className="tour-how-it-works"><span><b>1</b> Set an alarm</span><span><b>2</b> Do your poses</span><span><b>3</b> Alarm stops</span></div>
          <p>Use the camera to follow your poses and stop the alarm.</p>
          <button type="button" className="tour-primary" data-tour-focus onClick={() => onStep('add')}>Set up an alarm <span>→</span></button>
        </section>
      ) : step === 'routine' ? (
        <section className="tour-routine-popup">
          <h1>Build your yoga routine</h1>
          <p>Choose the poses you’ll do when your alarm rings.</p>
          <div className="routine-overview-window"><ol className="routine-overview-scroll">{alarm?.routine.map((pose, slot) => <li className="routine-overview-card" key={slot}><span>Pose {slot + 1} of {alarm.routine.length}</span><img src={`/poses/${pose.pose.toLowerCase().replaceAll(' ', '-').replace('ii', 'two')}.png`} alt="" /><strong>{pose.pose}</strong><small>{pose.duration} sec</small></li>)}</ol></div>
          <button type="button" className="tour-primary" data-tour-focus onClick={() => onStep('pose-1')}>Choose poses <span>→</span></button>
        </section>
      ) : step === 'placement' ? (
        <section className="tour-page tour-placement">
          <h1>{practised ? 'You’ve got it.' : 'Try your first pose.'}</h1>
          <p>{practised ? 'Practice complete.' : 'Phone upright. Screen facing you.'}</p>
          <img className="tour-placement-image" src="/onboarding/phone-setup.png" alt="1. Place your phone upright on the floor. 2. Take two steps back. 3. Follow the pose." />
          {!practised ? <div className="tour-practice-note"><b>5 seconds · sound off</b></div> : null}
          <small>Processed on your device. Never recorded.</small>
          {practised ? <button type="button" className="tour-primary" data-tour-focus onClick={() => onStep('test')}>Next <span>→</span></button>
            : <button type="button" className="tour-primary" data-tour-focus onClick={onPractice}>Try it now <span>→</span></button>}
          <button type="button" className="tour-text-button" onClick={practised ? onPractice : () => onStep('test')}>{practised ? 'Practise again' : 'Skip practice'}</button>
        </section>
      ) : step === 'complete' && alarm ? (
        <section className="tour-page tour-complete">
          <span className="tour-success" aria-hidden="true">✓</span>
          <h1>Your alarm is ready.</h1>
          <p className="tour-saved-name">{alarm.name}</p>
          <div className="tour-alarm-confirmation"><span>Next alarm</span><strong>{nextAlarm}</strong><small>{alarm.weekdays.length ? 'Repeats on your selected days' : 'One-time alarm'}</small></div>
          <ol className="tour-routine-summary">{alarm.routine.map((pose, slot) => <li key={slot}><span>{slot + 1}</span><b>{pose.pose}</b><small>{pose.duration} sec</small></li>)}</ol>
          <p>{alarm.routine.reduce((total, pose) => total + pose.duration, 0)} seconds of movement.</p>
          <button type="button" className="tour-primary" data-tour-focus onClick={onSkip}>Done <span>✓</span></button>
          <button type="button" className="tour-text-button" onClick={onTest}>Test full routine</button>
          <small className="tour-preview-note">Preview only · alarms won’t ring here.</small>
        </section>
      ) : (
        <section ref={tipRef} className="tour-tip" key={step} aria-live="polite">
          <h2 data-tour-focus={tapControl || undefined} tabIndex={tapControl ? -1 : undefined}>{copy.title}</h2><p>{copy.detail}</p>
          {!tapControl ? <button type="button" className="tour-primary" data-tour-focus onClick={() => { if (step === 'name') phoneRef.current?.querySelector<HTMLInputElement>('[data-tour="name"]')?.blur(); onStep(steps[index + 1]); }}>{step === 'pose-3' ? 'Try a pose' : 'Next'} <span>→</span></button> : null}
        </section>
      )}
    </div>
  );
}

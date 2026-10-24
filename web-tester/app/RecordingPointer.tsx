'use client';

import { useEffect, useRef } from 'react';

export default function RecordingPointer() {
  const pointerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let startX = 0;
    let startY = 0;
    let pressed = false;
    let dragged = false;
    let actionTimer: ReturnType<typeof setTimeout> | undefined;
    const move = (event: PointerEvent) => {
      const pointer = pointerRef.current;
      if (!pointer) return;
      if (pressed && Math.hypot(event.clientX - startX, event.clientY - startY) > 6) dragged = true;
      pointer.dataset.dragging = String(pressed && dragged);
      pointer.style.left = `${event.clientX}px`;
      pointer.style.top = `${event.clientY}px`;
      pointer.style.opacity = '1';
    };
    const press = (event: PointerEvent) => {
      startX = event.clientX;
      startY = event.clientY;
      pressed = true;
      dragged = false;
      if (pointerRef.current) pointerRef.current.dataset.pressed = 'true';
      move(event);
    };
    const pulse = () => {
      pointerRef.current?.querySelector('.recording-dot')?.animate(
        [{ transform: 'scale(1)' }, { transform: 'scale(.72)', offset: .35 }, { transform: 'scale(1)' }],
        { duration: 320, easing: 'ease-in-out' },
      );
      pointerRef.current?.querySelector('.recording-click')?.animate(
        [{ transform: 'translate(-50%, -50%) scale(.5)', opacity: .7 }, { transform: 'translate(-50%, -50%) scale(1.7)', opacity: 0 }],
        { duration: 500, easing: 'ease-out' },
      );
    };
    const release = () => {
      pressed = false;
      if (pointerRef.current) pointerRef.current.dataset.pressed = 'false';
    };
    const click = (event: MouseEvent) => {
      if (!event.isTrusted || event.detail === 0 || dragged) return;
      const target = event.target instanceof Element ? event.target.closest<HTMLElement>('button, [role="button"]') : null;
      if (!target) {
        clearTimeout(actionTimer);
        actionTimer = setTimeout(pulse, 350);
        return;
      }
      if (target.matches(':disabled')) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      clearTimeout(actionTimer);
      actionTimer = setTimeout(() => {
        pulse();
        actionTimer = setTimeout(() => {
          if (!target.isConnected) return;
          const sheet = target.closest<HTMLElement>('.choice-sheet');
          if (sheet && (target.getAttribute('aria-label')?.startsWith('Close ') || target.closest('.duration-choice-list'))) {
            const exit = sheet.animate([{ transform: 'translateY(0)' }, { transform: 'translateY(100%)' }], { duration: 350, easing: 'cubic-bezier(.4, 0, 1, 1)', fill: 'forwards' });
            sheet.parentElement?.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 350, fill: 'forwards' });
            exit.onfinish = () => { if (target.isConnected) target.click(); };
          } else {
            target.click();
          }
        }, 120);
      }, 350);
    };
    document.addEventListener('pointermove', move, true);
    document.addEventListener('pointerdown', press, true);
    document.addEventListener('pointerup', release, true);
    document.addEventListener('pointercancel', release, true);
    document.addEventListener('click', click, true);
    return () => {
      document.removeEventListener('pointermove', move, true);
      document.removeEventListener('pointerdown', press, true);
      document.removeEventListener('pointerup', release, true);
      document.removeEventListener('pointercancel', release, true);
      document.removeEventListener('click', click, true);
      clearTimeout(actionTimer);
    };
  }, []);

  return <div ref={pointerRef} className="recording-pointer" aria-hidden="true"><i className="recording-click" /><i className="recording-dot" /></div>;
}

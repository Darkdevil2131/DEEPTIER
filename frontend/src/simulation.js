import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

const EARTH_RADIUS_KM = 6371;
const radians = degrees => degrees * Math.PI / 180;

export function distanceBetween(a, b) {
  const [lonA, latA] = a.map(radians);
  const [lonB, latB] = b.map(radians);
  const dLat = latB - latA;
  const dLon = lonB - lonA;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(latA) * Math.cos(latB) * Math.sin(dLon / 2) ** 2;
  return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

export function pointOnLine(points, fraction) {
  if (!points?.length) return null;
  if (points.length === 1) return { position: points[0], heading: 0 };
  const lengths = points.slice(1).map((point, index) => distanceBetween(points[index], point));
  const total = lengths.reduce((sum, length) => sum + length, 0) || 1;
  let remaining = Math.max(0, Math.min(1, fraction)) * total;
  for (let index = 0; index < lengths.length; index += 1) {
    if (remaining <= lengths[index] || index === lengths.length - 1) {
      const segment = lengths[index] || 1;
      const t = Math.min(1, remaining / segment);
      const from = points[index];
      const to = points[index + 1];
      const position = [from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t];
      const heading = Math.atan2((to[0] - from[0]) * Math.cos(radians(position[1])), to[1] - from[1]) * 180 / Math.PI;
      return { position, heading };
    }
    remaining -= lengths[index];
  }
  return { position: points.at(-1), heading: 0 };
}

export function getTransportStage(mode, progress) {
  const stages = {
    air: ['Cargo acceptance', 'Departure', 'Climb and cruise', 'Descent', 'Arrival', 'Customs'],
    sea: ['Port departure', 'Loading', 'Sailing', 'Transshipment', 'Arrival'],
    road: ['Pickup', 'On highway', 'In transit', 'Final approach', 'Delivery'],
    rail: ['Loading', 'Departure', 'Interchange', 'In transit', 'Arrival'],
    multimodal: ['Pickup', 'Inland transfer', 'Port or airport', 'International movement', 'Destination clearance', 'Final delivery'],
  };
  const steps = stages[mode] || stages.road;
  const index = Math.min(steps.length - 1, Math.floor(Math.max(0, progress) / 100 * steps.length));
  return { label: steps[index], index, count: steps.length };
}

export function locateOnCorridor(corridor, progress, disrupted = false) {
  const legs = corridor?.legs || [];
  if (!legs.length) return null;
  const durations = legs.map((leg, index) => leg.estimates.transitHours + (disrupted && index === 0 && leg.mode !== 'air' ? 12 : 0));
  const total = durations.reduce((sum, duration) => sum + duration, 0) || 1;
  let elapsed = Math.max(0, Math.min(1, progress / 100)) * total;
  let legIndex = legs.length - 1;
  for (let index = 0; index < durations.length; index += 1) {
    if (elapsed <= durations[index] || index === durations.length - 1) {
      legIndex = index;
      break;
    }
    elapsed -= durations[index];
  }
  const leg = legs[legIndex];
  const legProgress = progress >= 100 ? 1 : Math.max(0, Math.min(1, elapsed / (durations[legIndex] || 1)));
  const marker = pointOnLine(leg.geometry.coordinates, legProgress);
  const mode = corridor.modes.includes('multimodal') ? leg.mode : corridor.modes[0];
  return { ...marker, legIndex, legProgress, mode, stage: getTransportStage(mode, progress), leg };
}

export function useRouteSimulation({ corridor, autoPlay = false, playToken = 0, disrupted = false }) {
  const [progress, setProgress] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState(1);
  const [reducedMotion, setReducedMotion] = useState(false);
  const frame = useRef(0);
  const previousTime = useRef(null);
  const completed = useRef(false);
  const previousPlayToken = useRef(playToken);
  useEffect(() => {
    const preference = window.matchMedia?.('(prefers-reduced-motion: reduce)');
    if (!preference) return undefined;
    const updatePreference = () => setReducedMotion(preference.matches);
    updatePreference();
    preference.addEventListener?.('change', updatePreference);
    return () => preference.removeEventListener?.('change', updatePreference);
  }, []);
  const reset = useCallback(() => {
    setProgress(0);
    completed.current = false;
    previousTime.current = null;
  }, []);

  useEffect(() => {
    const requestedPlay = previousPlayToken.current !== playToken;
    previousPlayToken.current = playToken;
    reset();
    setPlaying(Boolean(autoPlay || requestedPlay));
  }, [corridor?.id, playToken, reset]);

  useEffect(() => {
    if (!playing || !corridor || completed.current) return undefined;
    if (reducedMotion) {
      setProgress(100);
      setPlaying(false);
      completed.current = true;
      return undefined;
    }
    const advance = now => {
      if (previousTime.current !== null) {
        const elapsed = Math.min(100, now - previousTime.current);
        setProgress(current => {
          const next = Math.min(100, current + elapsed * speed / 420);
          if (next >= 100) {
            completed.current = true;
            setPlaying(false);
          }
          return next;
        });
      }
      previousTime.current = now;
      if (!completed.current) frame.current = requestAnimationFrame(advance);
    };
    frame.current = requestAnimationFrame(advance);
    return () => {
      cancelAnimationFrame(frame.current);
      previousTime.current = null;
    };
  }, [playing, corridor?.id, speed, reducedMotion]);

  const pause = useCallback(() => setPlaying(false), []);
  const play = useCallback(() => {
    if (progress >= 100) reset();
    completed.current = false;
    setPlaying(true);
  }, [progress, reset]);
  const restart = useCallback(() => {
    reset();
    setPlaying(true);
  }, [reset]);
  const marker = useMemo(() => locateOnCorridor(corridor, progress, disrupted), [corridor, progress, disrupted]);

  return { progress, setProgress: value => { setProgress(Math.max(0, Math.min(100, Number(value)))); setPlaying(false); completed.current = false; }, playing, play, pause, restart, speed, setSpeed, marker };
}

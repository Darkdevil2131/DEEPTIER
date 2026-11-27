import React from 'react';
import { modeLabel } from './data/corridors';

export default function RouteTimeline({ corridor, progress = 0, activeLeg = 0 }) {
  if (!corridor) return null;
  return <ol className="route-timeline" aria-label="Route legs and progress">
    {corridor.legs.map((leg, index) => {
      const state = index < activeLeg || progress >= 100 ? 'completed' : index === activeLeg ? 'active' : 'upcoming';
      return <li className={`timeline-leg leg-${state}`} key={`${leg.from}-${leg.to}-${index}`} aria-current={state === 'active' ? 'step' : undefined}>
        <span className="timeline-step" aria-hidden="true">{state === 'completed' ? '✓' : String(index + 1).padStart(2, '0')}</span>
        <div><strong>{corridor.hubs[index]?.name} → {corridor.hubs[index + 1]?.name}</strong><span>{modeLabel(leg.mode)} · {leg.estimates.distanceKm.toLocaleString()} km · {leg.estimates.transitHours} h estimated</span></div>
      </li>;
    })}
  </ol>;
}

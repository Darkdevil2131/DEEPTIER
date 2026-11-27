import React from 'react';
import { corridors, modes, modeLabel } from './data/corridors';

export default function RouteSelector({ selectedId, onSelect, selectedMode, onModeChange, label = 'Corridor' }) {
  const items = selectedMode && selectedMode !== 'multimodal' ? corridors.filter(item => item.modes.includes(selectedMode)) : corridors;
  return <div className="route-selector">
    <label>{label}<select value={selectedId} onChange={event => onSelect(event.target.value)}>
      {items.map(item => <option key={item.id} value={item.id}>{item.label} · {item.scope === 'domestic' ? 'Domestic' : 'International'}</option>)}
    </select></label>
    {onModeChange && <label>Transport mode<select value={selectedMode} onChange={event => onModeChange(event.target.value)}>{modes.map(mode => <option key={mode} value={mode}>{modeLabel(mode)}</option>)}</select></label>}
  </div>;
}

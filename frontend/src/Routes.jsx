import React, { useMemo, useState } from 'react';
import { Plane, Ship, Truck, TrainFront, Boxes, Play, Pause, RotateCcw, GitCompareArrows, CircleHelp, ArrowUpRight } from 'lucide-react';
import AtlasMap from './AtlasMap';
import RouteSelector from './RouteSelector';
import RouteTimeline from './RouteTimeline';
import { corridors, corridorById, estimateSummary, mapHubs, modeLabel, routeAlternatives } from './data/corridors';
import { useRouteSimulation } from './simulation';
import { DataStatusBadge, Json, Metric, Panel, ProvenanceBadge } from './UI';
import { java } from './model';

export const agentLabels = { route_optimization:'Route optimization',carrier_research:'Carrier research',risk_compliance:'Risk & compliance',document_intelligence:'Document intelligence',market_procurement:'Market & procurement',logistics_orchestrator:'Logistics orchestrator' };
export { default as Agents } from './Agents';

const modeIcons = { road:Truck, rail:TrainFront, sea:Ship, air:Plane, multimodal:Boxes };
const primaryMode = corridor => corridor?.modes.includes('multimodal') ? 'multimodal' : corridor?.legs[0]?.mode || 'road';
const initialCorridorId = 'kochi-chennai';
const endpoints = [...new Set(corridors.map(corridor=>corridor.hubs[0].id))];
const destinations = [...new Set(corridors.map(corridor=>corridor.hubs.at(-1).id))];
const cargoTypes = ['General industrial goods','Machine parts','Temperature controlled','Hazardous materials'];
const legIcon = mode => { const Icon=modeIcons[mode]||CircleHelp; return <Icon size={15} aria-hidden="true"/>; };

export default function Routes({ live=false, caseKey, setCase, onResult, go, initialResult, initialShipment, showNetwork=false }) {
  const [selectedId, setSelectedId] = useState(corridorById[caseKey] ? caseKey : initialCorridorId);
  const [mode, setMode] = useState(primaryMode(corridorById[selectedId]));
  const [originId, setOriginId] = useState(corridorById[selectedId].hubs[0].id);
  const [destinationId, setDestinationId] = useState(corridorById[selectedId].hubs.at(-1).id);
  const [weight, setWeight] = useState(initialShipment?.weightKg || 500);
  const [cargoType, setCargoType] = useState(initialShipment?.cargoDescription || cargoTypes[1]);
  const [deadline, setDeadline] = useState(initialShipment?.deadline || '');
  const [disruption, setDisruption] = useState(false);
  const [autoPlay, setAutoPlay] = useState(false);
  const [playToken, setPlayToken] = useState(0);
  const [fitRequest, setFitRequest] = useState(0);
  const [compare, setCompare] = useState(false);
  const [javaBusy, setJavaBusy] = useState(false);
  const [javaError, setJavaError] = useState('');
  const [javaSuccess, setJavaSuccess] = useState('');
  const [javaResponse, setJavaResponse] = useState(initialResult || null);
  const [extra, setExtra] = useState('{}');
  const corridor = corridorById[selectedId] || corridors[0];
  const summary = useMemo(() => estimateSummary(corridor, weight), [corridor, weight]);
  const simulation = useRouteSimulation({ corridor, autoPlay, playToken, disrupted: disruption });
  const alternatives = routeAlternatives(corridor);
  const activeMode = corridor.modes.includes('multimodal') ? 'multimodal' : simulation.marker?.mode || mode;
  const ActiveIcon = modeIcons[activeMode] || Boxes;
  const stages = simulation.marker?.stage;
  const activeLeg = corridor.legs[simulation.marker?.legIndex || 0];
  const formattedCost = new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:0});
  const formattedMoneyRange = `${formattedCost.format(summary.costRangeInr[0])} – ${formattedCost.format(summary.costRangeInr[1])}`;
  const currentStatus = simulation.progress >= 100 ? 'Complete' : simulation.playing ? 'In progress' : simulation.progress > 0 ? 'Paused' : 'Ready to simulate';
  const selectedStart = corridor.hubs[0];
  const selectedEnd = corridor.hubs.at(-1);

  const selectCorridor = id => {
    const next = corridorById[id];
    if (!next) return;
    setSelectedId(id);
    setOriginId(next.hubs[0].id);
    setDestinationId(next.hubs.at(-1).id);
    setMode(primaryMode(next));
    setCase?.(next.id);
    setCompare(false);
    setJavaSuccess('');
  };
  const selectMode = nextMode => {
    setMode(nextMode);
    const match = corridors.find(item => item.modes.includes(nextMode) && item.hubs[0].id === originId && item.hubs.at(-1).id === destinationId)
      || corridors.find(item => item.modes.includes(nextMode));
    if (match) selectCorridor(match.id);
  };
  const selectOrigin = nextOrigin => {
    setOriginId(nextOrigin);
    const match = corridors.find(item => item.hubs[0].id === nextOrigin && item.hubs.at(-1).id === destinationId && item.modes.includes(mode))
      || corridors.find(item => item.hubs[0].id === nextOrigin && item.modes.includes(mode));
    if (match) selectCorridor(match.id);
  };
  const selectDestination = nextDestination => {
    setDestinationId(nextDestination);
    const match = corridors.find(item => item.hubs.at(-1).id === nextDestination && item.hubs[0].id === originId && item.modes.includes(mode))
      || corridors.find(item => item.hubs.at(-1).id === nextDestination && item.modes.includes(mode));
    if (match) selectCorridor(match.id);
  };
  const oneClickMode = requestedMode => {
    const match = corridors.find(item => item.legs.some(leg => leg.mode === requestedMode) && item.hubs[0].id === originId)
      || corridors.find(item => item.legs.some(leg => leg.mode === requestedMode));
    if (!match) return;
    setMode(requestedMode);
    selectCorridor(match.id);
    setFitRequest(current => current + 1);
    setPlayToken(current => current + 1);
  };
  const compareAction = () => setCompare(current => !current);

  async function runJavaReroute() {
    setJavaBusy(true);
    setJavaError('');
    setJavaSuccess('');
    onResult?.(null);
    try {
      const additional = JSON.parse(extra);
      const result = await java('/api/logistics/reroute', {
        useDemoDefaults: true,
        ...additional,
        origin: selectedStart.name,
        destination: selectedEnd.name,
        weightKg: Number(weight),
        cargoDescription: cargoType,
        allowedModes: corridor.modes.includes('multimodal') ? corridor.legs.map(leg=>leg.mode) : [activeMode],
      });
      setJavaResponse(result);
      onResult?.(result);
      setJavaSuccess('Java returned a new route response. Review its provenance and decision gates before use.');
    } catch (error) {
      setJavaError(error instanceof SyntaxError ? 'Technical response must be valid JSON.' : error.message);
      setJavaResponse(null);
    } finally {
      setJavaBusy(false);
    }
  }

  return <div className="route-workspace">
    <div className="route-intro"><div><span className="eyebrow">ROUTE SIMULATION</span><h2>Model the corridor. Validate before booking.</h2><p>Compare a planning estimate with an optional Java route response. No carrier availability is represented here.</p></div><div className="route-intro-actions"><ProvenanceBadge provenance="estimated"/>{go&&<button type="button" onClick={()=>go('Agent workflow')}>Inspect six agent results <ArrowUpRight size={15}/></button>}</div></div>
    <div className="mode-shortcuts" aria-label="Start a transport simulation"><button type="button" onClick={()=>oneClickMode('road')} aria-label="Switch to a road corridor and start the simulation">{legIcon('road')} Road</button><button type="button" onClick={()=>oneClickMode('air')} aria-label="Switch to an air corridor, fit the route, and start the flight simulation">{legIcon('air')} Flight</button><button type="button" onClick={()=>oneClickMode('sea')} aria-label="Switch to a sea corridor, fit the route, and start the ship simulation">{legIcon('sea')} Ship</button><button type="button" onClick={()=>selectMode('rail')}>{legIcon('rail')} Rail</button><button type="button" onClick={()=>selectMode('multimodal')}>{legIcon('multimodal')} Multimodal</button></div>
    <Panel title="Shipment and corridor controls" className="route-controls-panel">
      <div className="route-fields">
        <label>Origin<select value={originId} onChange={event=>selectOrigin(event.target.value)}>{endpoints.map(id=><option key={id} value={id}>{mapHubs.find(hub=>hub.id===id)?.name}</option>)}</select></label>
        <label>Destination<select value={destinationId} onChange={event=>selectDestination(event.target.value)}>{destinations.map(id=><option key={id} value={id}>{mapHubs.find(hub=>hub.id===id)?.name}</option>)}</select></label>
        <RouteSelector selectedId={selectedId} selectedMode={mode} onSelect={selectCorridor} onModeChange={selectMode}/>
        <label>Cargo weight (kg)<input type="number" min="1" max="1000000" value={weight} onChange={event=>setWeight(Math.max(1,Number(event.target.value)||1))}/></label>
        <label>Cargo type<select value={cargoType} onChange={event=>setCargoType(event.target.value)}>{cargoTypes.map(type=><option key={type}>{type}</option>)}</select></label>
        <label>Delivery deadline<input type="date" value={deadline} onChange={event=>setDeadline(event.target.value)}/></label>
      </div>
      <div className="simulation-options"><label className="check-control"><input type="checkbox" checked={disruption} onChange={event=>setDisruption(event.target.checked)}/>Model a disruption stress test</label><label className="check-control"><input type="checkbox" checked={autoPlay} onChange={event=>setAutoPlay(event.target.checked)}/>Auto-play when the corridor changes</label><span className="field-note">Disruption toggle adds a synthetic 12-hour inland delay. It is not a live incident.</span></div>
    </Panel>
    <div className="simulation-layout">
      <div className="simulation-main">
        <AtlasMap corridor={corridor} progress={simulation.progress} disrupted={disruption} alternatives={compare ? alternatives : []} showNetwork={showNetwork} fitRequest={fitRequest}/>
        <Panel title="Simulation controls" className="playback-panel">
          <div className="playback-actions"><button type="button" className="action-button" onClick={simulation.play} disabled={simulation.playing}><Play size={15}/> Play</button><button type="button" onClick={simulation.pause} disabled={!simulation.playing}><Pause size={15}/> Pause</button><button type="button" onClick={simulation.restart}><RotateCcw size={15}/> Restart</button><button type="button" onClick={compareAction} aria-pressed={compare}><GitCompareArrows size={15}/> {compare?'Hide comparison':'Compare alternatives'}</button>
            <label>Speed<select aria-label="Simulation speed" value={simulation.speed} onChange={event=>simulation.setSpeed(Number(event.target.value))}>{[0.5,1,2,4].map(speed=><option key={speed} value={speed}>{speed}×</option>)}</select></label>
          </div>
          <label className="timeline-control">Simulation progress<input type="range" min="0" max="100" step="1" value={simulation.progress} onChange={event=>simulation.setProgress(event.target.value)} aria-valuetext={`${Math.round(simulation.progress)} percent`}/><span>{Math.round(simulation.progress)}%</span></label>
          {compare && <div className="comparison-panel" role="region" aria-label="Alternative corridor comparison"><h3>Like-for-like alternatives</h3>{alternatives.length ? alternatives.map(item=><button type="button" key={item.id} onClick={()=>selectCorridor(item.id)}>{item.label}<span>{modeLabel(item.legs[0].mode)} · {item.provenance}</span></button>) : <p>No registered corridor has the same origin and destination. Nearby routes are not shown as directly comparable.</p>}</div>}
        </Panel>
        <RouteTimeline corridor={corridor} progress={simulation.progress} activeLeg={simulation.marker?.legIndex||0}/>
      </div>
      <aside className="simulation-side" aria-label="Selected route details">
        <Panel title="Selected route"><div className="selected-route-title"><span>{selectedStart.name}</span><i aria-hidden="true">→</i><span>{selectedEnd.name}</span></div>{deadline&&<p className="route-deadline">User-provided deadline: {deadline}</p>}<ProvenanceBadge provenance="estimated"/><DataStatusBadge status={simulation.progress >= 100?'complete':simulation.playing?'running':simulation.progress > 0?'review':'informational'} label={currentStatus}/>
          <div className="metrics route-metrics"><Metric label="Estimated route cost" value={formattedMoneyRange} note={`For ${Number(weight).toLocaleString()} kg · planning range`}/><Metric label="Estimated transit" value={`${summary.transitHours} hr`} note={disruption?'Includes a 12-hour synthetic delay':'Excludes customs and carrier-specific schedules'}/><Metric label="Estimated distance" value={`${summary.distanceKm.toLocaleString()} km`} note="Modelled from corridor geometry"/><Metric label="Confidence" value={`${Math.round(summary.confidence*100)}%`} note="Planning confidence, not a guarantee"/></div>
          <div className="risk-summary"><span>Modeled risk</span><DataStatusBadge status={summary.risk==='high'?'review':summary.risk==='medium'?'informational':'passed'} label={summary.risk[0].toUpperCase()+summary.risk.slice(1)}/></div>
          {activeMode==='air' && <section className="transport-detail"><h3><Plane size={16}/> Flight route details</h3><p>{selectedStart.name} → {selectedEnd.name} · great-circle estimate</p><div className="detail-grid"><span>Estimated transit<strong>{summary.transitHours} hours</strong></span><span>Delay state<strong>{disruption?'Synthetic +12 hr':'Not modelled'}</strong></span><span>Confidence<strong>{Math.round(summary.confidence*100)}%</strong></span></div></section>}
          {activeMode==='sea' && <section className="transport-detail vessel-card"><h3><Ship size={16}/> Container service (synthetic)</h3><p>Generic planning study · no carrier assignment</p><div className="detail-grid"><span>Origin port<strong>{selectedStart.name}</strong></span><span>Destination port<strong>{selectedEnd.name}</strong></span><span>Estimated transit<strong>{summary.transitHours} hours</strong></span><span>Current stage<strong>{stages?.label || 'Port departure'}</strong></span><span>Cargo status<strong>{simulation.progress>=100?'Arrived in simulation':'In transit · simulated'}</strong></span><span>Provenance<strong>Estimated geometry</strong></span></div></section>}
          {activeMode==='road' && <section className="transport-detail"><h3><Truck size={16}/> Road legs and hubs</h3>{corridor.legs.map((leg,index)=><div className={`leg-card ${index===simulation.marker?.legIndex?'leg-current':''}`} key={`${leg.from}-${leg.to}`}><span>{index < (simulation.marker?.legIndex||0)?'Completed':index===(simulation.marker?.legIndex||0)?'Active leg':'Upcoming'}</span><strong>{corridor.hubs[index].name} → {corridor.hubs[index+1].name}</strong><small>{leg.estimates.distanceKm.toLocaleString()} km · {leg.estimates.transitHours} h estimated</small></div>)}</section>}
          {activeMode==='rail' && <section className="transport-detail"><h3><TrainFront size={16}/> Rail corridor</h3><p>{corridor.hubs.map(hub=>hub.name).join(' → ')}</p><div className="detail-grid"><span>Capacity<strong>Illustrative 40–60 TEU train</strong></span><span>Transit<strong>{summary.transitHours} hours estimated</strong></span><span>Stage<strong>{stages?.label || 'Loading'}</strong></span></div></section>}
          {activeMode==='multimodal' && <section className="transport-detail"><h3><Boxes size={16}/> Mode handoffs</h3>{corridor.legs.map((leg,index)=><div className={`handoff ${index===simulation.marker?.legIndex?'handoff-active':''}`} key={`${leg.from}-${leg.to}`}><span>{legIcon(leg.mode)}</span><div><strong>{modeLabel(leg.mode)} · {corridor.hubs[index].name} → {corridor.hubs[index+1].name}</strong><small>{index===(simulation.marker?.legIndex||0)?`Active · ${stages?.label}`:index<(simulation.marker?.legIndex||0)?'Completed · simulated':'Upcoming'}</small></div></div>)}</section>}
          <div className="human-review-note"><DataStatusBadge status="review" label="Human validation required"/><p>Validate carrier capacity, schedule, customs clearance, route access, quote evidence, and final cost before procurement.</p></div>
        </Panel>
        <Panel title="Optional Java route response"><p>Send the selected shipment to the existing Java reroute endpoint. The response is separate from the planning estimate above.</p><details><summary>Additional shipment fields</summary><label>JSON request fields<textarea aria-label="Additional shipment fields" value={extra} onChange={event=>setExtra(event.target.value)}/></label></details>{live ? <button className="action-button" type="button" disabled={javaBusy} onClick={runJavaReroute}>{javaBusy?'Requesting Java route…':'Request route from Java'}</button> : <p className="field-note">Connect the Java API from the workspace to request a backend route.</p>}{javaError&&<p className="error" role="alert">{javaError}</p>}{javaSuccess&&<p className="notice" role="status">{javaSuccess}</p>}{javaResponse&&<><ProvenanceBadge provenance="live"/><Json data={javaResponse}/></>}</Panel>
      </aside>
    </div>
  </div>;
}

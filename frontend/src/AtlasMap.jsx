import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowDown, ArrowLeft, ArrowRight, ArrowUp, LocateFixed, Minus, Plus, Scan, Globe2 } from 'lucide-react';
import worldTopo from 'world-atlas/countries-110m.json';
import indiaBoundary from './data/india-states.json';
import { feature } from 'topojson-client';
import { corridors, mapHubs } from './data/corridors';
import MapLegend from './MapLegend';
import { locateOnCorridor } from './simulation';

const WIDTH = 1040;
const HEIGHT = 520;
const center = [WIDTH / 2, HEIGHT / 2];
const initialLayers = { domestic: true, international: true, ports: true, airports: true, roads: true, rail: true, seaLanes: true, airArcs: true, disruptions: true, depots: true, railHubs: true, industrialCities: true };
export const place = value => (value || '').replace(/\b\w/g, letter => letter.toUpperCase());
const project = ([longitude, latitude]) => [((longitude + 180) / 360) * WIDTH, ((90 - latitude) / 180) * HEIGHT];
const featureArea = points => Math.abs(points.reduce((area, point, index) => { const next = points[(index + 1) % points.length]; return area + point[0] * next[1] - next[0] * point[1]; }, 0));
const fitView = (points, padding = 76) => {
  if (!points?.length) return { zoom: 1, pan: [0, 0] };
  const projected = points.map(project);
  const xs = projected.map(point => point[0]);
  const ys = projected.map(point => point[1]);
  const minX = Math.min(...xs), maxX = Math.max(...xs), minY = Math.min(...ys), maxY = Math.max(...ys);
  const width = Math.max(maxX - minX, 4), height = Math.max(maxY - minY, 4);
  const zoom = Math.max(1, Math.min(10, Math.min((WIDTH - padding * 2) / width, (HEIGHT - padding * 2) / height)));
  const x = (minX + maxX) / 2, y = (minY + maxY) / 2;
  return { zoom, pan: [(center[0] - x) * zoom, (center[1] - y) * zoom] };
};
const geometryRings = geometry => geometry.type === 'Polygon' ? geometry.coordinates : geometry.coordinates.flat();
const topoCountries = feature(worldTopo, worldTopo.objects.countries).features;
const worldShapes = topoCountries.map((country,index) => {
  const rings = geometryRings(country.geometry);
  const d = rings.map(ring => `M${ring.map(point => project(point).map(value => value.toFixed(1)).join(',')).join('L')}Z`).join('');
  return { name:country.properties.name, d, india:country.properties.name==='India', key:`${country.properties.name}-${index}`, rings };
});
const indiaShapePoints = indiaBoundary.states.flatMap(state => state.rings.flat());
const indiaBoundsPoints = indiaShapePoints.length ? indiaShapePoints : [[68,7],[97,7],[97,36],[68,36]];
const indiaStates = indiaBoundary.states.map(state => {
  const positions = state.rings.flat();
  const longitude = positions.reduce((sum,point)=>sum+point[0],0)/positions.length;
  const latitude = positions.reduce((sum,point)=>sum+point[1],0)/positions.length;
  const d = state.rings.map(ring=>`M${ring.map(point=>project(point).map(value=>value.toFixed(1)).join(',')).join('L')}Z`).join('');
  return {name:state.name,d,position:project([longitude,latitude])};
});
const countryAnchors = (() => {
  const largest = new Map();
  for (const country of worldShapes) {
    for (const ring of country.rings) {
      const area = featureArea(ring);
      if (!largest.has(country.name) || largest.get(country.name).area < area) {
        const longitude=ring.reduce((sum,point)=>sum+point[0],0)/ring.length;
        const latitude=ring.reduce((sum,point)=>sum+point[1],0)/ring.length;
        largest.set(country.name,{area,position:project([longitude,latitude])});
      }
    }
  }
  return [...largest.entries()].map(([name,item])=>({name,...item.position,area:item.area}));
})();
const countrySet = new Set(['India','China','Russia','United States of America','Brazil','Australia','Canada','Mexico','Saudi Arabia','United Arab Emirates','Singapore','Japan','Germany','France','United Kingdom','South Africa','Kenya','Indonesia','Sri Lanka','Bangladesh','Netherlands']);
export default function AtlasMap({ corridor, progress = 0, disrupted = false, alternatives = [], showNetwork = false, fitRequest = 0, onFitRoute, onHubSelect }) {
  const [view, setView] = useState(() => ({ zoom: 1, pan: [0, 0] }));
  const [layers, setLayers] = useState(initialLayers);
  const drag = useRef(null);
  const originScope = corridor?.scope;
  const routePoint = locateOnCorridor(corridor, progress, disrupted);
  const visibleCorridor = corridor && layers[originScope];
  const routePoints = useMemo(() => corridor?.legs.flatMap(leg => leg.geometry.coordinates) || [], [corridor]);
  useEffect(() => { if (corridor) setView(fitView(routePoints, 90)); }, [corridor?.id, fitRequest]);
  const setZoom = amount => setView(current => ({ ...current, zoom: Math.max(1, Math.min(10, current.zoom * amount)) }));
  const fitRoute = () => { const next = fitView(routePoints, 90); setView(next); onFitRoute?.(); };
  const fitIndia = () => setView(fitView(indiaBoundsPoints, 72));
  const resetWorld = () => setView({ zoom: 1, pan: [0, 0] });
  const toggleLayer = key => setLayers(current => ({ ...current, [key]: !current[key] }));
  const mapTransform = `translate(${view.pan[0]} ${view.pan[1]}) translate(${center[0]} ${center[1]}) scale(${view.zoom}) translate(${-center[0]} ${-center[1]})`;
  const layerForMode = mode => mode === 'sea' ? 'seaLanes' : mode === 'air' ? 'airArcs' : mode === 'rail' ? 'rail' : 'roads';
  const networkCorridors = showNetwork ? corridors.filter(route => layers[route.scope] && route.id !== corridor?.id) : [];
  const visibleLeg = leg => layers[layerForMode(leg.mode)];
  const indiaCenter = project([78.5, 21]);
  const indiaScreen = [view.pan[0] + center[0] + (indiaCenter[0] - center[0]) * view.zoom, view.pan[1] + center[1] + (indiaCenter[1] - center[1]) * view.zoom];
  const indiaInsetVisible = view.zoom > 1.65 && Math.abs(indiaScreen[0] - center[0]) < WIDTH * 0.42 && Math.abs(indiaScreen[1] - center[1]) < HEIGHT * 0.42;
  const majorLabels = countryAnchors.filter(country => countrySet.has(country.name));
  const renderLabels = view.zoom > 2.4 ? countryAnchors : view.zoom > 1.55 ? majorLabels : [];
  const panBy = (x,y) => setView(current => ({ ...current, pan: [current.pan[0] + x, current.pan[1] + y] }));

  const handleKeyDown = event => {
    const movement = 28;
    if (event.key === 'ArrowLeft') { event.preventDefault(); panBy(movement,0); }
    if (event.key === 'ArrowRight') { event.preventDefault(); panBy(-movement,0); }
    if (event.key === 'ArrowUp') { event.preventDefault(); panBy(0,movement); }
    if (event.key === 'ArrowDown') { event.preventDefault(); panBy(0,-movement); }
    if (event.key === '+' || event.key === '=') { event.preventDefault(); setZoom(1.25); }
    if (event.key === '-') { event.preventDefault(); setZoom(.8); }
    if (event.key === '0') { event.preventDefault(); resetWorld(); }
  };

  const hubIsVisible = hub => {
    const selected = corridor?.hubs.some(routeHub => routeHub.id === hub.id);
    return selected || (hub.type === 'port' && layers.ports) || (hub.type === 'airport' && layers.airports) || (hub.type === 'rail hub' && layers.railHubs && view.zoom > 1.15) || (hub.type === 'inland container depot' && layers.depots && view.zoom > 1.15) || (hub.type === 'industrial city' && layers.industrialCities && view.zoom > 1.5);
  };
  const clusterGroups = new Map();
  if (view.zoom < 2.5) {
    mapHubs.filter(hub => hubIsVisible(hub) && !corridor?.hubs.some(routeHub => routeHub.id === hub.id) && ['port','airport'].includes(hub.type)).forEach(hub => {
      const [x,y]=project(hub.coordinates);
      const screenX=view.pan[0]+center[0]+(x-center[0])*view.zoom;
      const screenY=view.pan[1]+center[1]+(y-center[1])*view.zoom;
      const key=`${Math.floor(screenX/46)}:${Math.floor(screenY/46)}`;
      const group=clusterGroups.get(key)||{hubs:[],x:0,y:0};
      group.hubs.push(hub);group.x+=x;group.y+=y;clusterGroups.set(key,group);
    });
  }
  const hubClusters=[...clusterGroups.values()].filter(group=>group.hubs.length>1).map(group=>({...group,x:group.x/group.hubs.length,y:group.y/group.hubs.length}));
  const clusteredIds=new Set(hubClusters.flatMap(group=>group.hubs.map(hub=>hub.id)));
  const zoomToCluster=group=>{const zoom=Math.min(10,view.zoom*2.2);setView({zoom,pan:[(center[0]-group.x)*zoom,(center[1]-group.y)*zoom]})};

  const renderHub = hub => {
    const [x,y] = project(hub.coordinates);
    const selected = corridor?.hubs.some(routeHub => routeHub.id === hub.id);
    const show = selected || (hub.type === 'port' && layers.ports) || (hub.type === 'airport' && layers.airports) || (hub.type === 'rail hub' && layers.railHubs && view.zoom > 1.15) || (hub.type === 'inland container depot' && layers.depots && view.zoom > 1.15) || (hub.type === 'industrial city' && layers.industrialCities && view.zoom > 1.5);
    if (!show) return null;
    const isAirport = hub.type === 'airport';
    const [sx,sy] = [view.pan[0] + center[0] + (x-center[0])*view.zoom, view.pan[1] + center[1] + (y-center[1])*view.zoom];
    if (sx < -40 || sx > WIDTH + 40 || sy < -40 || sy > HEIGHT + 40) return null;
    return <g className={`hub-marker ${selected ? 'hub-selected' : ''} hub-${hub.type.replaceAll(' ','-')}`} key={hub.id} role="button" tabIndex="0" aria-label={`${hub.name}, ${hub.type}, ${hub.country}; ${selected ? `route ${hub.role}` : 'network location'}`} onClick={() => onHubSelect?.(hub)} onKeyDown={event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); onHubSelect?.(hub); } }}>
      <title>{`${hub.name} · ${hub.type} · ${hub.country}${selected ? ` · ${hub.role} in selected route` : ''}`}</title>
      <circle cx={x} cy={y} r={(selected ? 6 : 4)/view.zoom} className="hub-dot"/>
      {(selected || view.zoom > 2.2) && <text x={x+9/view.zoom} y={y-7/view.zoom} fontSize={12/view.zoom} className="hub-label">{hub.name}</text>}
      {isAirport && <circle cx={x} cy={y} r={1.6/view.zoom} className="hub-center"/>}
    </g>;
  };

  return <section className="atlas-panel" aria-label="Global corridor map">
    <header className="atlas-heading"><div><span className="eyebrow">GLOBAL ROUTE ATLAS</span><strong>{corridor?.label || 'World trade network'}</strong></div><span className="map-source">Offline geography · planning estimates</span></header>
    <div className="atlas-toolbar" aria-label="Map navigation controls">
      <div className="map-control-group"><button type="button" aria-label="Zoom in" title="Zoom in" onClick={() => setZoom(1.25)}><Plus size={16}/></button><button type="button" aria-label="Zoom out" title="Zoom out" onClick={() => setZoom(.8)}><Minus size={16}/></button><button type="button" aria-label="Reset map to world" title="Reset map to world" onClick={resetWorld}><Globe2 size={16}/><span>World</span></button></div>
      <div className="map-control-group"><button type="button" aria-label="Fit map to selected route" title="Fit selected route" onClick={fitRoute}><Scan size={16}/><span>Fit route</span></button><button type="button" aria-label="Fit map to India" title="Fit India" onClick={fitIndia}><LocateFixed size={16}/><span>Fit India</span></button></div>
      <div className="map-pan-controls" aria-label="Pan map with arrow buttons"><button type="button" aria-label="Pan up" onClick={() => panBy(0,32)}><ArrowUp size={14}/></button><button type="button" aria-label="Pan left" onClick={() => panBy(32,0)}><ArrowLeft size={14}/></button><button type="button" aria-label="Pan right" onClick={() => panBy(-32,0)}><ArrowRight size={14}/></button><button type="button" aria-label="Pan down" onClick={() => panBy(0,-32)}><ArrowDown size={14}/></button></div>
    </div>
    <div className="map-plot-wrap">
      <svg className="world-map" viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="application" aria-label="Interactive world map. Drag to pan; use arrow keys to pan, plus or minus to zoom, and zero to reset." tabIndex="0" onKeyDown={handleKeyDown} onPointerDown={event => { if (event.target.closest('[role="button"]')) return; drag.current = [event.clientX,event.clientY,...view.pan]; event.currentTarget.setPointerCapture(event.pointerId); }} onPointerMove={event => { if (!drag.current) return; const [x,y,panX,panY] = drag.current; const rect = event.currentTarget.getBoundingClientRect(); setView(current => ({...current,pan:[panX+(event.clientX-x)*WIDTH/rect.width,panY+(event.clientY-y)*HEIGHT/rect.height]})); }} onPointerUp={() => { drag.current = null; }} onPointerCancel={() => { drag.current = null; }}>
        <rect width={WIDTH} height={HEIGHT} className="ocean-fill"/>
        <g transform={mapTransform} className="world-geography">{worldShapes.map(shape => <path key={shape.key} d={shape.d} fillRule="evenodd" className={shape.india ? 'country-shape india-shape' : 'country-shape'}><title>{shape.name}</title></path>)}
          {view.zoom > 2.1 && indiaStates.map(state => <path key={state.name} d={state.d} className="state-boundary"><title>{state.name} state boundary · DataMeet India community · 2011</title></path>)}
          {view.zoom > 3.2 && indiaStates.map(state => <text key={state.name} x={state.position[0]} y={state.position[1]} fontSize={11/view.zoom} className="state-label">{state.name}</text>)}
          {renderLabels.map(country => <text key={country.name} x={country.x} y={country.y} fontSize={12/view.zoom} className="country-label">{country.name}</text>)}
          {networkCorridors.map(route => route.legs.filter(visibleLeg).map((leg,index) => <polyline key={`${route.id}-${index}`} points={leg.geometry.coordinates.map(point => project(point).join(',')).join(' ')} className={`network-route network-${leg.mode}`}/>))}
          {alternatives.map(route => route.legs.filter(visibleLeg).map((leg,index) => <polyline key={`alt-${route.id}-${index}`} points={leg.geometry.coordinates.map(point => project(point).join(',')).join(' ')} className={`alternative-route mode-${leg.mode}`}/>))}
          {visibleCorridor && corridor.legs.map((leg,index) => visibleLeg(leg) && <polyline key={`${corridor.id}-${index}`} points={leg.geometry.coordinates.map(point => project(point).join(',')).join(' ')} className={`selected-route mode-${leg.mode} ${index < routePoint?.legIndex || progress >= 100 ? 'route-completed' : index === routePoint?.legIndex ? 'route-active' : 'route-upcoming'}`} aria-label={`${leg.mode} route from ${corridor.hubs[index].name} to ${corridor.hubs[index+1].name}`}/>)}
          {hubClusters.map((group,index)=><g className="hub-cluster" key={`cluster-${index}`} role="button" tabIndex="0" aria-label={`Zoom to ${group.hubs.length} clustered logistics hubs: ${group.hubs.map(hub=>hub.name).join(', ')}`} onClick={()=>zoomToCluster(group)} onKeyDown={event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();zoomToCluster(group)}}}><title>{group.hubs.map(hub=>`${hub.name} · ${hub.type}`).join(' | ')}</title><circle cx={group.x} cy={group.y} r={11/view.zoom}/><text x={group.x} y={group.y+4/view.zoom} fontSize={12/view.zoom} textAnchor="middle">{group.hubs.length}</text></g>)}
          {mapHubs.filter(hub=>!clusteredIds.has(hub.id)).map(renderHub)}
          {disrupted && layers.disruptions && corridor?.legs[0] && (() => { const point = corridor.legs[0].geometry.coordinates[Math.floor(corridor.legs[0].geometry.coordinates.length/2)]; const [x,y]=project(point); return <g className="map-disruption" aria-label="Synthetic disruption stress test"><circle cx={x} cy={y} r={9/view.zoom}/><text x={x} y={y+4/view.zoom} fontSize={12/view.zoom} textAnchor="middle">!</text></g>; })()}
          {routePoint && visibleCorridor && visibleLeg(routePoint.leg) && <g className={`route-marker marker-${routePoint.mode}`} transform={`translate(${project(routePoint.position).join(' ')}) scale(${1/view.zoom}) rotate(${routePoint.heading})`} aria-hidden="true"><circle r="11" className="marker-halo"/><path d="M 0 -8 L 5 5 L 0 2 L -5 5 Z" className="marker-core"/></g>}
        </g>
        {indiaInsetVisible && <g className="india-overview" aria-label="India overview inset"><rect x={WIDTH-170} y={HEIGHT-132} width="150" height="112" rx="6"/><text x={WIDTH-158} y={HEIGHT-112}>INDIA OVERVIEW</text><g transform={`translate(${WIDTH-156} ${HEIGHT-104}) scale(.72) translate(-${Math.min(...indiaBoundsPoints.map(point=>project(point)[0]))} -${Math.min(...indiaBoundsPoints.map(point=>project(point)[1]))})`}>{worldShapes.filter(shape=>shape.india).map(shape=><path key={shape.key} d={shape.d} className="inset-india"/>)}</g><circle cx={WIDTH-113} cy={HEIGHT-74} r="4"/></g>}
      </svg>
    </div>
    <div className="map-help">Drag to pan · arrow keys move the map · + / − zoom · 0 resets to world</div>
    <MapLegend layers={layers} onToggle={toggleLayer}/>
    <details className="map-attribution"><summary>Geography sources and limitations</summary><p>Country boundaries: locally bundled world-atlas, based on Natural Earth data. India state boundaries: DataMeet India community via geoBoundaries, CC BY 2.5 IN, 2011 boundary vintage. Route geometry and all corridor economics are planning estimates.</p></details>
  </section>;
}

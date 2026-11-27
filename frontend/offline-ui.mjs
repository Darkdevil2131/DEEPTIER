import { Window } from 'happy-dom';
import fs from 'node:fs';
import assert from 'node:assert/strict';
import { corridors, corridorCounts } from './src/data/corridors.js';
import topology from 'world-atlas/countries-110m.json' with { type: 'json' };
import { feature } from 'topojson-client';

const window = new Window({
  url: 'http://127.0.0.1:18080',
  settings: {
    enableJavaScriptEvaluation: true,
    suppressInsecureJavaScriptEnvironmentWarning: true,
  },
});
let prefersReducedMotion = false;
const motionListeners = new Set();
window.matchMedia = query => ({
  media: query,
  get matches() {
    return query.includes('prefers-reduced-motion: reduce') && prefersReducedMotion;
  },
  addEventListener: (event, listener) => event === 'change' && motionListeners.add(listener),
  removeEventListener: (event, listener) => event === 'change' && motionListeners.delete(listener),
});
window.requestAnimationFrame = callback => setTimeout(() => callback(performance.now()), 16);
window.cancelAnimationFrame = clearTimeout;
window.fetch = (path, options) => fetch(new URL(path, 'http://127.0.0.1:18080'), { ...options, signal: undefined });

const html = fs.readFileSync('dist/index.html', 'utf8');
window.document.body.innerHTML = '<div id="root"></div>';
window.eval(html.slice(html.indexOf('<script>') + 8, html.lastIndexOf('</script>')));

const wait = (milliseconds = 40) => new Promise(resolve => setTimeout(resolve, milliseconds));
const text = () => window.document.body.textContent;
const buttonFor = label => {
  const button = [...window.document.querySelectorAll('button')].find(candidate =>
    candidate.textContent.trim() === label || candidate.getAttribute('aria-label') === label,
  );
  assert(button && !button.disabled, `Expected enabled button: ${label}`);
  return button;
};
const click = async label => {
  buttonFor(label).click();
  await wait();
};
const see = async value => {
  for (let attempt = 0; attempt < 100; attempt += 1) {
    if (text().includes(value)) return;
    await wait(50);
  }
  throw new Error(`Missing ${value}: ${text().slice(-500)}`);
};
const waitFor = async (predicate, message) => {
  for (let attempt = 0; attempt < 100; attempt += 1) {
    if (predicate()) return;
    await wait(20);
  }
  throw new Error(`Timed out waiting for ${message}`);
};
const routeProgress = () => Number(window.document.querySelector('.timeline-control input')?.value || 0);
const worldTransform = () => window.document.querySelector('.world-geography')?.getAttribute('transform');

await wait();
assert.equal(window.document.querySelectorAll('.scenario-indicators button').length, 3);
await click('Next transport scenario');
await see('Ocean freight');
assert.equal(window.document.querySelector('.freight-image-current')?.alt, 'Container vessel approaching a commercial port');
assert(window.document.querySelector('.freight-image-previous'), 'Scenario transition should cross-fade from the previous image');
await waitFor(() => !window.document.querySelector('.freight-image-previous'), 'scenario cross-fade cleanup');
await click('Explore route atlas');
await see('Kochi → Chennai');
assert(window.document.querySelectorAll('.world-map path').length > 50, 'World map should render country boundaries');

await click('Flight');
await see('Flight route details');
assert.equal(window.document.querySelector('.route-fields select')?.value, 'bengaluruAirport');
await waitFor(() => routeProgress() > 0, 'flight autoplay');
const flightFit = worldTransform();
await click('Zoom in');
assert.notEqual(worldTransform(), flightFit, 'Zoom control should change map scale');
await click('Flight');
await waitFor(() => worldTransform() === flightFit, 'repeat flight shortcut to refit route');
await waitFor(() => routeProgress() > 0, 'repeat flight autoplay');
await click('Pause');
const pausedProgress = routeProgress();
assert(text().includes('Paused'));
await wait(80);
assert.equal(routeProgress(), pausedProgress, 'Pause should hold simulation progress');
await click('Restart');
await waitFor(() => text().includes('In progress'), 'flight restart');

await click('Ship');
await see('Container service (synthetic)');
assert(text().includes('Estimated transit'));
assert(text().includes('Estimated geometry'));
await waitFor(() => routeProgress() > 0, 'ship autoplay');
await click('Rail');
await see('Rail corridor');
await click('Play');
await waitFor(() => routeProgress() > 0, 'rail playback');
await click('Pause');
assert(text().includes('Paused'));
await click('Multimodal');
await see('Mode handoffs');
const scrubber = window.document.querySelector('.timeline-control input');
scrubber.value = '85';
scrubber.dispatchEvent(new window.Event('input', { bubbles: true }));
await wait();
assert.equal(routeProgress(), 85, 'Timeline scrubber should set route progress');
await click('Road');
await see('Road legs and hubs');
await waitFor(() => routeProgress() > 0, 'road shortcut autoplay');
await click('Pause');
await click('Compare alternatives');
await see('Like-for-like alternatives');

const routeFit = worldTransform();
await click('Fit route');
assert(worldTransform());
await click('Zoom out');
const zoomedTransform = worldTransform();
assert.notEqual(zoomedTransform, routeFit);
await click('Pan left');
assert.notEqual(worldTransform(), zoomedTransform, 'Pan control should move the map');
await click('Fit India');
const indiaTransform = worldTransform();
assert.notEqual(indiaTransform, zoomedTransform);
await click('Reset map to world');
const worldView = worldTransform();
assert.match(worldView, /scale\(1\)/);
const map = window.document.querySelector('.world-map');
map.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true, cancelable: true }));
await wait();
assert.notEqual(worldTransform(), worldView, 'Arrow keys should pan the focused map');
map.dispatchEvent(new window.KeyboardEvent('keydown', { key: '0', bubbles: true, cancelable: true }));
await wait();
assert.match(worldTransform(), /scale\(1\)/);
assert([...window.document.querySelectorAll('.technical-disclosure')].every(disclosure => !disclosure.open), 'Technical response JSON should be collapsed by default');

await click('Route simulation');
await click('Inspect six agent results');
await see('Logistics orchestrator');
await see('One accountable decision.');
assert.equal(window.document.querySelectorAll('.specialist').length, 6);
for (let index = 0; index < 6; index += 1) {
  const specialists = [...window.document.querySelectorAll('.specialist')];
  specialists[index].click();
  await wait();
  assert(window.document.querySelector('.findingLead')?.textContent.length > 20);
  if (index === 3) assert(text().includes('Required · missing'), 'Missing documents should remain visible for review');
}
assert(text().includes('Human action required'));
assert(text().includes('No eligible quotation'));
await click('Replay recorded findings');
await wait(1400);
assert.equal(window.document.querySelector('.specialist[aria-pressed="true"] strong')?.textContent, 'Carrier research');
await click('Pause replay');

const pages = [
  'Overview', 'Global route atlas', 'Supply network', 'Shipment intake', 'BOM import',
  'Disruption analysis', 'What-if simulation', 'Route simulation', 'Carrier evidence',
  'Document intelligence', 'Agent workflow', 'Approval queue', 'RFQs and SAP handoff', 'Audit trail',
];
for (const page of pages) {
  await click(page);
  await see(page);
}
await click('Overview');
await see('No verified quotes');
assert(text().includes('No verified quotes'));

await click('Route simulation');
prefersReducedMotion = true;
for (const listener of motionListeners) listener();
await wait();
await click('Restart');
await waitFor(() => routeProgress() === 100, 'reduced-motion playback to jump to completion');
assert(text().includes('Complete'));

const countryFeatures = feature(topology, topology.objects.countries).features;
const pointInRing = (point, sourceRing) => {
  let previous = sourceRing[0][0];
  const ring = sourceRing.map(([longitude, latitude]) => {
    longitude += 360 * Math.round((previous - longitude) / 360);
    previous = longitude;
    return [longitude, latitude];
  });
  const offset = 360 * Math.round((point[0] - ring.reduce((sum, coordinate) => sum + coordinate[0], 0) / ring.length) / 360);
  let inside = false;
  for (let index = 0, previousIndex = ring.length - 1; index < ring.length; previousIndex = index++) {
    if ((ring[index][1] > point[1]) !== (ring[previousIndex][1] > point[1])
      && point[0] < (ring[previousIndex][0] - ring[index][0]) * (point[1] - ring[index][1])
        / (ring[previousIndex][1] - ring[index][1] || 1e-12) + ring[index][0] + offset) {
      inside = !inside;
    }
  }
  return inside;
};
const countryAt = point => countryFeatures.find(country => {
  const polygons = country.geometry.type === 'Polygon' ? [country.geometry.coordinates] : country.geometry.coordinates;
  return polygons.some(rings => pointInRing(point, rings[0]) && !rings.slice(1).some(ring => pointInRing(point, ring)));
});
const expectedDomestic = [
  'kochi-chennai','kochi-bengaluru','kochi-coimbatore-chennai','mumbai-pune','mumbai-ahmedabad-delhi',
  'mumbai-nagpur-kolkata','delhi-jaipur-ahmedabad-mumbai','delhi-lucknow-kolkata','chennai-bengaluru-hyderabad',
  'chennai-visakhapatnam-kolkata','bengaluru-hyderabad-nagpur-delhi','kolkata-bhubaneswar-visakhapatnam-chennai',
  'bhopal-indore-mumbai','bhopal-nagpur-hyderabad','rajkot-ahmedabad-mumbai','coimbatore-salem-chennai',
  'pune-hyderabad','pune-bengaluru','guwahati-siliguri-kolkata','delhi-mumbai-dfc','delhi-kolkata-dfc',
  'chennai-mumbai','hyderabad-mumbai','kanpur-delhi','surat-mumbai','tuticorin-chennai','amritsar-delhi',
  'chandigarh-delhi','patna-kolkata','raipur-nagpur','visakhapatnam-hyderabad',
];
const expectedInternational = [
  'jnpt-singapore','jnpt-jebel-ali','jnpt-rotterdam','jnpt-london-gateway','kamarajar-singapore',
  'kamarajar-dubai','kamarajar-rotterdam','kamarajar-shanghai','cochin-colombo-singapore','mundra-jebel-ali',
  'mundra-rotterdam','vizag-singapore','haldia-singapore','nhava-sheva-los-angeles','mumbai-new-york',
  'tuticorin-colombo','kandla-jebel-ali','pipavav-jebel-ali','mumbai-hamburg','mumbai-felixstowe',
  'chennai-busan','chennai-yokohama','mundra-jeddah','mumbai-durban','mumbai-mombasa','chennai-sydney',
  'kolkata-chittagong','bengaluru-dubai-air','hyderabad-dubai-air','delhi-frankfurt-air','mumbai-singapore-air',
  'delhi-heathrow-air','mumbai-hong-kong-air','chennai-singapore-air','delhi-chicago-air','mumbai-doha-air',
  'bengaluru-singapore-air','pune-jnpt-rotterdam','delhi-mundra-jebel-ali','hyderabad-chennai-singapore',
  'bengaluru-hyderabad-dubai-air',
];
const corridorById = new Map(corridors.map(corridor => [corridor.id, corridor]));
assert.equal(corridorCounts.domestic, 31);
assert.equal(corridorCounts.international, 41);
assert.equal(corridors.length, 72);
for (const id of expectedDomestic) {
  assert(corridorById.has(id), `Missing domestic corridor ${id}`);
  assert.equal(corridorById.get(id).scope, 'domestic');
}
for (const id of expectedInternational) {
  assert(corridorById.has(id), `Missing international corridor ${id}`);
  assert.equal(corridorById.get(id).scope, 'international');
}
let checkedSeaSamples = 0;
let seaLegCount = 0;
const checkedSegments = new Set();
for (const corridor of corridors) {
  assert(corridor.id && corridor.label && corridor.hubs.length >= 2);
  assert(['domestic', 'international'].includes(corridor.scope));
  assert(['live', 'historical', 'user-provided', 'estimated', 'synthetic', 'recorded'].includes(corridor.provenance));
  for (const leg of corridor.legs) {
    assert(['road', 'rail', 'sea', 'air'].includes(leg.mode));
    assert(leg.geometry.coordinates.length >= 2);
    const origin = corridor.hubs.find(hub => hub.id === leg.from).coordinates;
    const destination = corridor.hubs.find(hub => hub.id === leg.to).coordinates;
    assert(Math.abs(leg.geometry.coordinates[0][0] - origin[0]) < 0.001 && Math.abs(leg.geometry.coordinates[0][1] - origin[1]) < 0.001);
    assert(Math.abs(leg.geometry.coordinates.at(-1)[0] - destination[0]) < 0.001 && Math.abs(leg.geometry.coordinates.at(-1)[1] - destination[1]) < 0.001);
    assert(leg.estimates.distanceKm > 0 && leg.estimates.transitHours > 0);
    assert(leg.estimates.confidence > 0 && leg.estimates.confidence <= 1);
    if (leg.mode !== 'sea') continue;
    seaLegCount += 1;
    const points = leg.geometry.coordinates;
    for (let index = 1; index < points.length - 2; index += 1) {
      const from = points[index];
      const to = points[index + 1];
      const segmentKey = [from.join(','), to.join(',')].sort().join('|');
      if (checkedSegments.has(segmentKey)) continue;
      checkedSegments.add(segmentKey);
      const steps = Math.max(1, Math.ceil(Math.max(Math.abs(to[0] - from[0]), Math.abs(to[1] - from[1])) * 20));
      for (let step = 1; step < steps; step += 1) {
        const ratio = step / steps;
        const point = [from[0] + (to[0] - from[0]) * ratio, from[1] + (to[1] - from[1]) * ratio];
        const country = countryAt(point);
        checkedSeaSamples += 1;
        assert(!country, `${corridor.id} sea lane crosses ${country?.properties?.name} at ${point.join(',')}`);
      }
    }
  }
}

console.log(`Passed ${pages.length} workspace sections, all five transport modes, simulation and map controls, six agent findings, replay, and reduced-motion behavior; validated ${corridors.length} corridors (${corridorCounts.domestic} domestic, ${corridorCounts.international} international), ${seaLegCount} sea legs, and ${checkedSeaSamples.toLocaleString('en-US')} inland sea-route samples.`);
await window.happyDOM.abort();

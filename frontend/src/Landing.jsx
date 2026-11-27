import React, { useEffect, useRef, useState } from 'react';
import { ArrowLeft, ArrowRight, ArrowUpRight, Route, ShieldAlert, ClipboardCheck } from 'lucide-react';
import sea from '../assets/sea.webp';
import air from '../assets/air.webp';
import road from '../assets/road.webp';
import AtlasMap from './AtlasMap';
import { corridorById } from './data/corridors';

export function Brand() {
  return <span className="brand"><span className="brandmark" aria-hidden="true"><i/><i/><i/></span>DeepTier</span>;
}

const scenarios = [
  { id:'road', label:'Road freight', routeId:'kochi-chennai', image:road, alt:'Freight truck at an industrial loading area' },
  { id:'sea', label:'Ocean freight', routeId:'jnpt-singapore', image:sea, alt:'Container vessel approaching a commercial port' },
  { id:'air', label:'Air cargo', routeId:'bengaluru-dubai-air', image:air, alt:'Palletized cargo being prepared for air transport' },
];

export function Landing({ open }) {
  const [active, setActive] = useState(0);
  const [previousScenario, setPreviousScenario] = useState(null);
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const [reducedMotion, setReducedMotion] = useState(false);
  const transitionTimer = useRef(0);
  const paused = hovered || focused;

  useEffect(() => {
    const preference = window.matchMedia?.('(prefers-reduced-motion: reduce)');
    if (!preference) return undefined;
    const updatePreference = () => setReducedMotion(preference.matches);
    updatePreference();
    preference.addEventListener?.('change', updatePreference);
    return () => preference.removeEventListener?.('change', updatePreference);
  }, []);

  useEffect(() => () => window.clearTimeout(transitionTimer.current), []);

  const showScenario = index => {
    setPreviousScenario(scenarios[active]);
    setActive(index);
    window.clearTimeout(transitionTimer.current);
    transitionTimer.current = window.setTimeout(() => setPreviousScenario(null), 500);
  };

  useEffect(() => {
    if (reducedMotion) return undefined;
    let frame = 0;
    let previous = null;
    let elapsed = 0;
    const advance = now => {
      if (previous !== null && !paused) elapsed += Math.min(100, now - previous);
      previous = now;
      if (elapsed >= 8000) {
        elapsed = 0;
        showScenario((active + 1) % scenarios.length);
      }
      frame = window.requestAnimationFrame(advance);
    };
    frame = window.requestAnimationFrame(advance);
    return () => window.cancelAnimationFrame(frame);
  }, [active, paused, reducedMotion]);

  const move = amount => showScenario((active + amount + scenarios.length) % scenarios.length);
  const scenario = scenarios[active];
  const corridor = corridorById[scenario.routeId];

  return <div className="landing">
    <header className="mast"><button className="logoBtn" aria-label="DeepTier home"><Brand/></button><nav aria-label="Main navigation"><a href="#capabilities">Capabilities</a><a href="#atlas-preview">Route atlas</a></nav><button className="action" onClick={()=>open('Overview')}>Open operations workspace <ArrowUpRight size={16}/></button></header>
    <main>
      <section className="intro" aria-labelledby="landing-title">
        <div className="introcopy"><span className="eyebrow">SUPPLY CHAIN OPERATIONS INTELLIGENCE</span><h1 id="landing-title">Move with clarity<br/>when supply <span>shifts.</span></h1><p>DeepTier connects route planning, disruption evidence and approval workflows in one operations workspace.</p><div className="calls"><button className="action" onClick={()=>open('Overview')}>Open operations workspace <ArrowUpRight size={16}/></button><button onClick={()=>open('Global route atlas')}>Explore route atlas <ArrowRight size={16}/></button></div></div>
        <figure className="freight" aria-label="Transport scenario preview" onMouseEnter={()=>setHovered(true)} onMouseLeave={()=>setHovered(false)} onFocusCapture={()=>setFocused(true)} onBlurCapture={event=>{if(!event.currentTarget.contains(event.relatedTarget))setFocused(false)}} style={previousScenario?{backgroundImage:`url(${previousScenario.image})`}:undefined}>
          {previousScenario&&<img className="freight-image-previous" src={previousScenario.image} alt="" aria-hidden="true"/>}
          <img key={scenario.id} className="freight-image-current" src={scenario.image} alt={scenario.alt}/>
          <figcaption><div><span className="eyebrow">ILLUSTRATIVE TRANSPORT SCENARIO</span><b>{scenario.label}</b></div><div className="freight-controls"><button aria-label="Previous transport scenario" onClick={()=>move(-1)}><ArrowLeft size={16}/></button><button aria-label="Next transport scenario" onClick={()=>move(1)}><ArrowRight size={16}/></button></div></figcaption>
          <div className="scenario-indicators" role="group" aria-label="Choose a transport scenario">{scenarios.map((item,index)=><button key={item.id} aria-label={`Show ${item.label} scenario`} aria-pressed={active===index} onClick={()=>showScenario(index)}/>)}</div>
        </figure>
      </section>
      <section className="landingLower" id="atlas-preview" aria-labelledby="atlas-preview-title"><span className="eyebrow">GLOBAL NETWORK · INDIA FOCUS</span><h2 id="atlas-preview-title">See the route beyond the border</h2><p>Explore the world map and compare illustrative road, rail, sea and air corridors. All route economics and timing are planning estimates.</p><div className="landing-map-preview"><AtlasMap corridor={corridor} showNetwork={false}/></div><div className="landing-ctas"><button className="action" onClick={()=>open('Global route atlas')}>Explore route atlas <ArrowRight size={16}/></button><button onClick={()=>open('Disruption analysis')}>Analyze disruption <ArrowRight size={16}/></button></div></section>
      <section className="landingLower capabilities" id="capabilities" aria-labelledby="capabilities-title"><span className="eyebrow">DECISION SUPPORT, WITH EVIDENCE</span><h2 id="capabilities-title">From disruption signal to reviewed action</h2><div className="capability-grid">
        <article className="capability-card"><ShieldAlert size={22} aria-hidden="true"/><h3>Disruption intelligence</h3><p>Bring supplier dependencies and risk signals into one reviewable view.</p></article>
        <article className="capability-card"><Route size={22} aria-hidden="true"/><h3>Route and recovery planning</h3><p>Compare estimated corridors, transit windows and operational constraints.</p></article>
        <article className="capability-card"><ClipboardCheck size={22} aria-hidden="true"/><h3>Approval-ready procurement</h3><p>Keep evidence, decision gates and human approval attached to each action.</p></article>
      </div><div className="landing-ctas"><button className="action" onClick={()=>open('Overview')}>Open operations workspace <ArrowUpRight size={16}/></button><button onClick={()=>open('Disruption analysis')}>Analyze disruption <ArrowRight size={16}/></button></div><footer className="landing-footer"><span>DeepTier · Supply chain operations intelligence</span><span>Demo routes are synthetic or estimated; no carrier availability is implied.</span></footer></section>
    </main>
  </div>;
}

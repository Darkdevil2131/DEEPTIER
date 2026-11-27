import React, { useEffect, useState } from 'react';
import { Check, CircleHelp, FileText, GitBranch, ShieldAlert, Truck, Workflow } from 'lucide-react';
import traces from './data/traces.json';
import cases from './data/shipments.json';
import { corridorById } from './data/corridors';
import { money } from './model';
import { DataStatusBadge, Json, Line, ProvenanceBadge } from './UI';

const names = ['Route optimization','Carrier research','Risk and compliance','Document intelligence','Market and procurement','Logistics orchestrator'];
const ids = ['route_optimization','carrier_research','risk_compliance','document_intelligence','market_procurement','logistics_orchestrator'];
const icons = [GitBranch,Truck,ShieldAlert,FileText,Workflow,Check];
const titleCase = value => String(value ?? 'Not reported').replaceAll('_',' ').replace(/\b\w/g,letter=>letter.toUpperCase());
const provenanceKey = agent => String(agent.provenance?.[0]||'recorded').toLowerCase().replaceAll('_','-');

function agentDetails(id, data, shipment, trace) {
  const route = trace.route_optimization.data.recommended_route;
  const documentData = trace.document_intelligence.data;
  const carrierData = trace.carrier_research.data;
  const inputs = {
    route_optimization:`${shipment.origin} → ${shipment.destination} · ${shipment.weightKg} kg · allowed modes`,
    carrier_research:`Modeled route legs for ${shipment.shipmentId} · carrier quote evidence`,
    risk_compliance:`Shipment constraints, route and supplied compliance evidence`,
    document_intelligence:`Recorded document packet for ${shipment.shipmentId}`,
    market_procurement:`Route estimate, supplier records and quotation evidence`,
    logistics_orchestrator:`Recorded findings and decision gates from five specialists`,
  };
  const findings = {
    route_optimization:route?`${route.legs.length} legs · ${route.transit_hours_estimate} h estimated`:'No feasible route recorded',
    carrier_research:carrierData.carriers?.length?`${carrierData.carriers.length} synthetic carrier records returned`:'No carrier records returned',
    risk_compliance:data.flags?.length?`${data.flags.length} compliance or risk flags recorded`:'No risk flags were recorded',
    document_intelligence:data.valid?'Recorded document checks passed':`${data.missing_document_types?.length||0} document types missing`,
    market_procurement:data.eligible_quotes?`${data.eligible_quotes} eligible quotes in recorded evidence`:'No eligible quotation supports procurement',
    logistics_orchestrator:data.explanation?.conclusion||'Workflow findings are recorded; review open decision gates before procurement.',
  };
  const nextActions = {
    route_optimization:route?'Confirm road or rail access, timing and carrier cost.':'Review shipment constraints and request another route study.',
    carrier_research:carrierData.carriers?.length?'Obtain current signed quotations and verify capacity.':'Provide current carrier quotes before procurement.',
    risk_compliance:data.flags?.length?'Assign the open flags to a qualified reviewer.':'Confirm the applicable screening evidence is current.',
    document_intelligence:data.missing_document_types?.length?`Provide ${data.missing_document_types.join(', ')}.`:'Verify document authenticity and required approvals.',
    market_procurement:data.eligible_quotes?'Review quote validity and obtain human approval.':'Collect comparable, in-date supplier quotations.',
    logistics_orchestrator:data.decision?.gates?.some(gate=>gate.status!=='passed')?'Resolve the open decision gates before procurement.':'Review evidence and retain human approval before purchase.',
  };
  const confidence = data.confidence ?? data.recommended_route?.confidence;
  const reviewRequired = data.human_review_required;
  const warningList = [...(trace[id].warnings||[]), ...(id==='document_intelligence'?data.issues||[]:[])];
  return {
    input:inputs[id],finding:findings[id],nextAction:nextActions[id],
    confidence:typeof confidence==='number'?`${Math.round(confidence*100)}% · reported by recorded result`:'Not reported by recorded result',
    reviewRequired:reviewRequired===true?'Required before action':reviewRequired===false?'No additional review flag returned':'Not specified by recorded result',
    warnings:warningList.length?warningList:['No warning was included in this recorded result.'],
    dataClass:provenanceKey(trace[id]),
  };
}

export default function Agents({caseKey='kochi-chennai'}) {
  const trace=traces[caseKey]||traces['kochi-chennai'];
  const shipment=cases[caseKey]||cases['kochi-chennai'];
  const selectedCorridor=corridorById[caseKey]||corridorById['kochi-chennai'];
  const decision=trace.logistics_orchestrator.data.decision;
  const [selected,setSelected]=useState(0);
  const [playing,setPlaying]=useState(false);
  const [step,setStep]=useState(0);
  useEffect(()=>{if(!playing)return undefined;const timer=window.setTimeout(()=>{if(step>=5){setPlaying(false);return}setStep(step+1);setSelected(step+1)},1100);return()=>window.clearTimeout(timer)},[playing,step]);
  const selectedId=ids[selected];
  const selectedAgent=trace[selectedId];
  const detail=agentDetails(selectedId,selectedAgent.data,shipment,trace);
  const routeData=trace.route_optimization.data;
  const recommended=routeData.recommended_route;
  const activeGates=decision.gates.filter(gate=>gate.status!=='passed');
  const runTimestamp=trace.recordedAt||trace.completedAt||shipment.updatedAt||'Timestamp not supplied with recorded fixture';

  return <div className="brain">
    <header className="brainHero"><div><span className="eyebrow">RECORDED AGENT WORKFLOW</span><h2>Six specialist findings.<br/><em>One accountable decision.</em></h2><p>{selectedCorridor.label} · {shipment.shipmentId} · Replay only, no new agent execution</p></div><div className="verdict"><DataStatusBadge status="review" label="Human action required"/><strong>{titleCase(decision.status)}</strong><p>{activeGates.length?`${activeGates.length} decision gate${activeGates.length===1?'':'s'} still need review.`:'Review all evidence before procurement.'}</p><button onClick={()=>{setSelected(5);setPlaying(false)}}>Review decision gates</button></div></header>
    <div className="brainMetrics"><div><span>Estimated route cost</span><strong>{money(recommended?.cost_estimate_inr)}</strong><small>Recorded estimate · not a carrier quote</small></div><div><span>Estimated transit</span><strong>{recommended?.transit_hours_estimate??'Not reported'} <i>hours</i></strong><small>Route access and carrier schedule not verified</small></div><div><span>Data and review status</span><strong>Recorded run</strong><small>{runTimestamp} · human approval required before procurement</small></div></div>
    <section className="workflowSection"><div className="sectionHeading"><div><span className="eyebrow">SPECIALIST WORKSPACE</span><h3>Follow the evidence</h3></div><button className="replayButton" onClick={()=>{if(playing){setPlaying(false)}else{setStep(0);setSelected(0);setPlaying(true)}}}>{playing?'Pause replay':'Replay recorded findings'}</button></div><p className="replayNote">Select a specialist to review its input, finding, evidence class, confidence, warnings and required human action.</p>
      <div className="specialists">{ids.map((id,index)=>{const agent=trace[id];const d=agentDetails(id,agent.data,shipment,trace);const Icon=icons[index];return <button key={id} aria-pressed={selected===index} className={`specialist ${selected===index?'active ':''}${playing&&step===index?'replaying':''}`} onClick={()=>{setSelected(index);setPlaying(false)}}><span className="agentTop"><span className="agentIcon"><Icon size={16}/></span><small>0{index+1}</small></span><strong>{names[index]}</strong><span className="agentFinding">{d.finding}</span><span className="agent-card-meta"><span>Evidence: {titleCase(d.dataClass)}</span><span>Confidence: {d.confidence}</span><span>Last run: {runTimestamp}</span><span>Review: {d.reviewRequired}</span><span>Warning: {d.warnings[0]}</span><span>Next: {d.nextAction}</span></span><DataStatusBadge status={agent.status} label={titleCase(agent.status)}/></button>})}</div>
    </section>
    <div className="evidenceLayout"><section className="evidencePanel" key={selected}><div className="evidenceHeading"><div><span className="eyebrow">SPECIALIST 0{selected+1} · FINDINGS</span><h3>{names[selected]}</h3></div><ProvenanceBadge provenance={detail.dataClass}/></div>
      <p className="findingLead">{detail.finding}</p>
      <div className="agent-detail-grid"><Line label="Input" value={detail.input}/><Line label="Evidence class" value={titleCase(detail.dataClass)}/><Line label="Confidence" value={detail.confidence}/><Line label="Human review" value={detail.reviewRequired}/><Line label="Last updated / run" value={runTimestamp}/><Line label="Main finding" value={detail.finding}/></div>
      {selected===0&&<><div className="routeRibbon">{recommended?.legs?.map((leg,index)=><React.Fragment key={`${leg.from}-${leg.to}-${index}`}>{index===0&&<span>{titleCase(leg.from)}</span>}<b>→</b><span>{titleCase(leg.to)}</span></React.Fragment>)}</div><div className="comparisonRows"><div className="compareHead"><span>Recorded route options</span><span>Transit</span><span>Estimate</span></div>{(routeData.routes||[]).slice(0,4).map((route,index)=><div key={index}><span><b>{String(index+1).padStart(2,'0')}</b> {route.distance_km} km <small>{route.feasible?'Feasible in recorded constraints':'Does not meet recorded constraints'}</small></span><span>{route.transit_hours_estimate} h</span><strong>{money(route.cost_estimate_inr)}</strong></div>)}</div><p className="evidenceNote">{routeData.ranking_method||'Ranking method not included in recorded evidence.'} Route access and live vehicle availability are not verified.</p></>}
      {selected===1&&<><p className="findingLead">Carrier records in this fixture are synthetic. They do not confirm current service or capacity.</p>{selectedAgent.data.carriers?.length?selectedAgent.data.carriers.map(carrier=><div className="recordCard" key={carrier.carrier_id}><h4>{carrier.name}</h4><span className="evidenceBadge">Synthetic carrier record</span><dl><div><dt>Recorded capacity</dt><dd>{carrier.capacity_kg?.toLocaleString()??'Not reported'} kg</dd></div><div><dt>History records</dt><dd>{carrier.history_count??'Not reported'}</dd></div><div><dt>Reliability</dt><dd>{carrier.reliability_score??'Not established'}</dd></div></dl></div>):<DataStatusBadge status="unavailable" label="No carrier records returned"/>}<p className="evidenceNote">{selectedAgent.data.notice||'Current rates, schedules, and capacity require direct verification.'}</p></>}
      {selected===2&&<><DataStatusBadge status={selectedAgent.data.human_review_required?'review':'unavailable'} label={selectedAgent.data.human_review_required?'Human review required':'Review flag not returned'}/>{selectedAgent.data.flags?.map((flag,index)=><div className="findingBox" key={index}><span>{titleCase(flag.severity)} priority · recorded</span><h4>{titleCase(flag.code)}</h4><p>{flag.reason}</p></div>)}<p className="evidenceNote">{selectedAgent.data.checked_rules?.length??0} configured rules checked · live screening {selectedAgent.data.live_screening?'enabled':'not available in this run'}.</p></>}
      {selected===3&&<><DataStatusBadge status={selectedAgent.data.valid?'passed':'review'} label={selectedAgent.data.valid?'Recorded field checks passed':'Documents need review'}/><div className="documentList">{Object.keys(selectedAgent.data.extracted_fields||{}).map(key=><div key={key}><span>{titleCase(key.split(':')[0])}</span><b>Fields extracted · recorded</b></div>)}{(selectedAgent.data.missing_document_types||[]).map(key=><div className="missing" key={key}><span>{titleCase(key)}</span><b>Required · missing</b></div>)}</div><p className="evidenceNote">{selectedAgent.data.documents_checked||0} supplied documents checked. Signature and stamp authenticity are not verified.</p></>}
      {selected===4&&<><DataStatusBadge status={selectedAgent.data.eligible_quotes?'review':'unavailable'} label={selectedAgent.data.eligible_quotes?'Quotes need review':'No eligible quotation'}/><div className="quoteEmpty"><h4>{selectedAgent.data.quotes_received||0} quotations in the recorded result</h4><p>Confirm price, lane coverage, capacity, validity and supplier identity using current commercial evidence.</p></div><p className="evidenceNote">{selectedAgent.data.note||'No procurement note was supplied.'}</p></>}
      {selected===5&&<><p className="findingLead">{selectedAgent.data.explanation?.conclusion||'Decision explanation was not supplied.'}</p><div className="decisionGates">{(decision.gates||[]).map(gate=><div key={gate.gate}><span className={gate.status==='passed'?'gatePass':'gateHold'} aria-hidden="true">{gate.status==='passed'?'✓':'!'}</span><div><h4>{titleCase(gate.gate)}</h4><p>{gate.reason}</p></div><DataStatusBadge status={gate.status} label={titleCase(gate.status)}/></div>)}</div></>}
      <section className="next-action-card"><span className="eyebrow">NEXT ACTION</span><p>{detail.nextAction}</p><DataStatusBadge status={detail.reviewRequired.startsWith('Required')?'review':'unavailable'} label={detail.reviewRequired}/></section>
      <div className="warning-list"><h4>Warnings and validation limits</h4>{detail.warnings.map((warning,index)=><p key={index}>{warning}</p>)}</div><Json data={selectedAgent.data}/>
    </section><aside className="nextSteps"><span className="eyebrow">DECISION GATES</span><h3>What needs attention</h3>{activeGates.map((gate,index)=><div className="nextItem" key={gate.gate}><span>0{index+1}</span><div><h4>{titleCase(gate.gate)}</h4><p>{gate.reason}</p></div></div>)}<div className="evidenceScope"><b>Decision boundary</b><p>Recorded findings support review only. Estimated route data and synthetic carrier records do not authorize a purchase, booking or ERP write.</p></div></aside></div>
  </div>;
}

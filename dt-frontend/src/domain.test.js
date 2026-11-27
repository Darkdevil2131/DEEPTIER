import test from 'node:test';import assert from 'node:assert/strict';import {validateShipment,decisionReady,chainContinuous,classifyQuery,money} from './domain.js';
const base={origin:'Kochi',destination:'Pune',cargoDescription:'Castings',weightKg:500,allowedModes:['road','rail','sea','air']};
test('four modes supported',()=>assert.deepEqual(validateShipment(base),[]));
test('reject critical cargo exceeding total',()=>assert.ok(validateShipment({...base,impactScenario:{criticalWeightKg:501,targetDays:2,dailyStoppageInr:0}}).length));
test('missing or blocked decision fails closed',()=>{assert.equal(decisionReady({feasible:true}),false);assert.equal(decisionReady({feasible:true,selectedRoute:{feasible:true},decisionStatus:'blocked'}),false);});
test('ready decision requires both statuses and candidate',()=>assert.equal(decisionReady({feasible:true,selectedRoute:{feasible:true},decisionStatus:'ready_for_human_approval',decision:{status:'ready_for_human_approval',recommendedOption:'a',gateReasons:[]}}),true));
test('chain does not silently join disconnected legs',()=>assert.equal(chainContinuous([{fromHub:'A',toHub:'B',mode:'road'},{fromHub:'C',toHub:'D',mode:'rail'}]),false));
test('mixed query surfaces multiple capabilities',()=>assert.deepEqual(classifyQuery('port closure supplier inventory SAP'),['transport','network','disruption','finance','sap']));
test('missing cost is not zero',()=>assert.equal(money(null),'Unavailable'));

test('missing gate list fails closed',()=>assert.equal(decisionReady({feasible:true,selectedRoute:{feasible:true},decisionStatus:'ready_for_human_approval',decision:{status:'ready_for_human_approval',recommendedOption:'a'}}),false));

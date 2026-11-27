/**
 * Locally bundled, illustrative corridor catalog. Distances, costs, timing,
 * confidence, and disruptions are planning estimates, not carrier offers.
 * @typedef {'road'|'rail'|'sea'|'air'} TransportMode
 * @typedef {TransportMode|'multimodal'} SupportedMode
 * @typedef {'live'|'historical'|'user-provided'|'estimated'|'synthetic'|'recorded'} Provenance
 * @typedef {{id:string,name:string,coordinates:[number,number],type:string,country:string,role:string}} CorridorHub
 * @typedef {{name:string,coordinates:[number,number]}} RouteWaypoint
 * @typedef {{distanceKm:number,transitHours:number,costRangeInr:[number,number],risk:'low'|'medium'|'high',confidence:number}} LegEstimate
 * @typedef {{from:string,to:string,mode:TransportMode,geometry:{type:string,coordinates:[number,number][]},waypoints:RouteWaypoint[],estimates:LegEstimate,disruptions:unknown[]}} CorridorLeg
 * @typedef {{id:string,label:string,scope:'domestic'|'international',modes:SupportedMode[],legs:CorridorLeg[],hubs:CorridorHub[],disruptions:unknown[],provenance:Provenance}} Corridor
 */

const cityRows = [
  ['kochi','Kochi',76.2673,9.9312],['chennai','Chennai',80.2707,13.0827],['bengaluru','Bengaluru',77.5946,12.9716],['coimbatore','Coimbatore',76.9558,11.0168],['mumbai','Mumbai',72.8777,19.076],['pune','Pune',73.8567,18.5204],['ahmedabad','Ahmedabad',72.5714,23.0225],['delhi','Delhi',77.209,28.6139],['nagpur','Nagpur',79.0882,21.1458],['kolkata','Kolkata',88.3639,22.5726],['jaipur','Jaipur',75.7873,26.9124],['lucknow','Lucknow',80.9462,26.8467],['hyderabad','Hyderabad',78.4867,17.385],['bhopal','Bhopal',77.4126,23.2599],['indore','Indore',75.8577,22.7196],['rajkot','Rajkot',70.8022,22.3039],['salem','Salem',78.146,11.6643],['guwahati','Guwahati',91.7362,26.1445],['siliguri','Siliguri',88.3953,26.7271],['visakhapatnam','Visakhapatnam',83.2185,17.6868],['bhubaneswar','Bhubaneswar',85.8245,20.2961],['tuticorin','Tuticorin',78.1348,8.7642],['kanpur','Kanpur',80.3319,26.4499],['surat','Surat',72.8311,21.1702],['amritsar','Amritsar',74.8723,31.634],['chandigarh','Chandigarh',76.7794,30.7333],['patna','Patna',85.1376,25.5941],['raipur','Raipur',81.6296,21.2514],['mundraCity','Mundra',69.721,22.839],
  ['jnpt','JNPT · Nhava Sheva',72.95,18.95,'port'],['nhavaSheva','Nhava Sheva',72.95,18.95,'port'],['kamarajar','Kamarajar Port',80.33,13.3,'port'],['cochinPort','Cochin Port',76.25,9.97,'port'],['colombo','Colombo',79.85,6.95,'port','Sri Lanka'],['singapore','Singapore',103.83,1.25,'port','Singapore'],['jebelAli','Jebel Ali',55.04,24.99,'port','United Arab Emirates'],['dubai','Dubai',55.04,24.99,'port','United Arab Emirates'],['rotterdam','Rotterdam',4.13,51.96,'port','Netherlands'],['londonGateway','London Gateway',0.47,51.5,'port','United Kingdom'],['mundra','Mundra Port',69.72,22.73,'port'],['shanghai','Shanghai',121.4,31.4,'port','China'],['vizagPort','Visakhapatnam Port',83.3,17.68,'port'],['haldia','Kolkata / Haldia',88.08,21.91,'port'],['losAngeles','Los Angeles',-118.25,33.72,'port','United States'],['newYork','New York',-74,40.67,'port','United States'],['tuticorinPort','V.O. Chidambaranar Port',78.17,8.76,'port'],['kandla','Kandla',70.21,22.97,'port'],['pipavav','Pipavav',71.5,20.91,'port'],['hamburg','Hamburg',9.97,53.54,'port','Germany'],['felixstowe','Felixstowe',1.31,51.96,'port','United Kingdom'],['busan','Busan',129.08,35.1,'port','South Korea'],['yokohama','Yokohama',139.64,35.45,'port','Japan'],['jeddah','Jeddah',39.17,21.49,'port','Saudi Arabia'],['durban','Durban',31.02,-29.87,'port','South Africa'],['mombasa','Mombasa',39.67,-4.05,'port','Kenya'],['sydneyPort','Sydney',151.2,-33.86,'port','Australia'],['chittagong','Chittagong',91.8,22.3,'port','Bangladesh'],
  ['tughlakabadIcd','Tughlakabad Inland Container Depot',77.25,28.51,'inland container depot'],['dadriIcd','Dadri Inland Container Depot',77.55,28.56,'inland container depot'],['patliIcd','Patli Inland Container Depot',76.8,28.2,'inland container depot'],['whitefieldIcd','Whitefield Inland Container Depot',77.77,12.98,'inland container depot'],['puneIcd','Pune Inland Container Depot',73.98,18.53,'inland container depot'],['ahmedabadIcd','Ahmedabad Inland Container Depot',72.65,23.06,'inland container depot'],
  ['delhiRailHub','Delhi rail hub',77.22,28.66,'rail hub'],['mumbaiRailHub','Mumbai rail hub',72.9,19.1,'rail hub'],['nagpurRailHub','Nagpur rail hub',79.09,21.15,'rail hub'],['chennaiRailHub','Chennai rail hub',80.26,13.08,'rail hub'],['kolkataRailHub','Kolkata rail hub',88.34,22.58,'rail hub'],['hyderabadRailHub','Hyderabad rail hub',78.48,17.4,'rail hub'],['guwahatiRailHub','Guwahati rail hub',91.76,26.18,'rail hub'],['ahmedabadRailHub','Ahmedabad rail hub',72.6,23.0,'rail hub'],
  ['bengaluruAirport','Bengaluru airport',77.7066,13.1986,'airport'],['singaporeAirport','Singapore airport',103.99,1.36,'airport','Singapore'],['hyderabadAirport','Hyderabad airport',78.4294,17.2403,'airport'],['delhiAirport','Delhi airport',77.1,28.5562,'airport'],['mumbaiAirport','Mumbai airport',72.8656,19.0896,'airport'],['chennaiAirport','Chennai airport',80.1693,12.9941,'airport'],['dubaiAirport','Dubai airport',55.3644,25.2532,'airport','United Arab Emirates'],['frankfurtAirport','Frankfurt airport',8.5706,50.0379,'airport','Germany'],['heathrow','London Heathrow',-0.4543,51.47,'airport','United Kingdom'],['hongKongAirport','Hong Kong airport',113.9185,22.308,'airport','China'],['chicagoAirport','Chicago airport',-87.9073,41.9742,'airport','United States'],['dohaAirport','Doha airport',51.6138,25.2731,'airport','Qatar'],['colomboAirport','Colombo airport',79.8841,7.1808,'airport','Sri Lanka'],
];

const hubs = Object.fromEntries(cityRows.map(([id,name,longitude,latitude,type='industrial city',country='India']) => [id,{id,name,coordinates:[longitude,latitude],type,country}]));
const domesticSpecs = [
  ['kochi-chennai','Kochi → Chennai',['kochi','chennai'],'road'],
  ['kochi-bengaluru','Kochi → Bengaluru',['kochi','bengaluru'],'road'],
  ['kochi-coimbatore-chennai','Kochi → Coimbatore → Chennai',['kochi','coimbatore','chennai'],'road'],
  ['mumbai-pune','Mumbai → Pune',['mumbai','pune'],'road'],
  ['mumbai-ahmedabad-delhi','Mumbai → Ahmedabad → Delhi',['mumbai','ahmedabad','delhi'],'road'],
  ['mumbai-nagpur-kolkata','Mumbai → Nagpur → Kolkata',['mumbai','nagpur','kolkata'],'rail'],
  ['delhi-jaipur-ahmedabad-mumbai','Delhi → Jaipur → Ahmedabad → Mumbai',['delhi','jaipur','ahmedabad','mumbai'],'road'],
  ['delhi-lucknow-kolkata','Delhi → Lucknow → Kolkata',['delhi','lucknow','kolkata'],'rail'],
  ['chennai-bengaluru-hyderabad','Chennai → Bengaluru → Hyderabad',['chennai','bengaluru','hyderabad'],'road'],
  ['chennai-visakhapatnam-kolkata','Chennai → Visakhapatnam → Kolkata',['chennai','visakhapatnam','kolkata'],'rail'],
  ['bengaluru-hyderabad-nagpur-delhi','Bengaluru → Hyderabad → Nagpur → Delhi',['bengaluru','hyderabad','nagpur','delhi'],'rail'],
  ['kolkata-bhubaneswar-visakhapatnam-chennai','Kolkata → Bhubaneswar → Visakhapatnam → Chennai',['kolkata','bhubaneswar','visakhapatnam','chennai'],'rail'],
  ['bhopal-indore-mumbai','Bhopal → Indore → Mumbai',['bhopal','indore','mumbai'],'road'],
  ['bhopal-nagpur-hyderabad','Bhopal → Nagpur → Hyderabad',['bhopal','nagpur','hyderabad'],'rail'],
  ['rajkot-ahmedabad-mumbai','Rajkot → Ahmedabad → Mumbai',['rajkot','ahmedabad','mumbai'],'road'],
  ['coimbatore-salem-chennai','Coimbatore → Salem → Chennai',['coimbatore','salem','chennai'],'road'],
  ['pune-hyderabad','Pune → Hyderabad',['pune','hyderabad'],'road'],
  ['pune-bengaluru','Pune → Bengaluru',['pune','bengaluru'],'rail'],
  ['guwahati-siliguri-kolkata','Guwahati → Siliguri → Kolkata',['guwahati','siliguri','kolkata'],'rail'],
  ['delhi-mumbai-dfc','Delhi → Mumbai · Western DFC',['delhi','mumbai'],'rail'],
  ['delhi-kolkata-dfc','Delhi → Kolkata · Eastern DFC',['delhi','kolkata'],'rail'],
  ['chennai-mumbai','Chennai → Mumbai',['chennai','mumbai'],'rail'],
  ['hyderabad-mumbai','Hyderabad → Mumbai',['hyderabad','mumbai'],'road'],
  ['kanpur-delhi','Kanpur → Delhi',['kanpur','delhi'],'rail'],
  ['surat-mumbai','Surat → Mumbai',['surat','mumbai'],'rail'],
  ['tuticorin-chennai','Tuticorin → Chennai',['tuticorin','chennai'],'road'],
  ['amritsar-delhi','Amritsar → Delhi',['amritsar','delhi'],'rail'],
  ['chandigarh-delhi','Chandigarh → Delhi',['chandigarh','delhi'],'rail'],
  ['patna-kolkata','Patna → Kolkata',['patna','kolkata'],'rail'],
  ['raipur-nagpur','Raipur → Nagpur',['raipur','nagpur'],'rail'],
  ['visakhapatnam-hyderabad','Visakhapatnam → Hyderabad',['visakhapatnam','hyderabad'],'rail'],
];
const seaSpecs = [
  ['jnpt-singapore','Mumbai / JNPT → Singapore','jnpt','singapore','malacca'],
  ['jnpt-jebel-ali','Mumbai / JNPT → Dubai / Jebel Ali','jnpt','jebelAli','hormuz'],
  ['jnpt-rotterdam','Mumbai / JNPT → Rotterdam','jnpt','rotterdam','cape-europe'],
  ['jnpt-london-gateway','Mumbai / JNPT → London Gateway','jnpt','londonGateway','cape-europe'],
  ['kamarajar-singapore','Chennai / Kamarajar → Singapore','kamarajar','singapore','bay-malacca'],
  ['kamarajar-dubai','Chennai / Kamarajar → Dubai','kamarajar','jebelAli','east-hormuz'],
  ['kamarajar-rotterdam','Chennai / Kamarajar → Rotterdam','kamarajar','rotterdam','cape-europe-east'],
  ['kamarajar-shanghai','Chennai / Kamarajar → Shanghai','kamarajar','shanghai','malacca-north'],
  ['cochin-colombo-singapore','Cochin / Kochi → Colombo → Singapore','cochinPort','singapore','cochin-colombo',['colombo']],
  ['mundra-jebel-ali','Mundra → Dubai / Jebel Ali','mundra','jebelAli','hormuz'],
  ['mundra-rotterdam','Mundra → Rotterdam','mundra','rotterdam','cape-europe'],
  ['vizag-singapore','Visakhapatnam → Singapore','vizagPort','singapore','bay-malacca'],
  ['haldia-singapore','Kolkata / Haldia → Singapore','haldia','singapore','bay-malacca'],
  ['nhava-sheva-los-angeles','Nhava Sheva → Los Angeles','nhavaSheva','losAngeles','cape-pacific'],
  ['mumbai-new-york','Mumbai → New York','jnpt','newYork','cape-new-york'],
  ['tuticorin-colombo','Tuticorin → Colombo','tuticorinPort','colombo','palk-colombo'],
  ['kandla-jebel-ali','Kandla → Jebel Ali','kandla','jebelAli','hormuz'],
  ['pipavav-jebel-ali','Pipavav → Jebel Ali','pipavav','jebelAli','hormuz'],
  ['mumbai-hamburg','Mumbai → Hamburg','jnpt','hamburg','cape-europe'],
  ['mumbai-felixstowe','Mumbai → Felixstowe','jnpt','felixstowe','cape-europe'],
  ['chennai-busan','Chennai → Busan','kamarajar','busan','malacca-busan'],
  ['chennai-yokohama','Chennai → Yokohama','kamarajar','yokohama','malacca-yokohama'],
  ['mundra-jeddah','Mundra → Jeddah','mundra','jeddah','bab-el-mandeb-jeddah'],
  ['mumbai-durban','Mumbai → Durban','jnpt','durban','cape-durban'],
  ['mumbai-mombasa','Mumbai → Mombasa','jnpt','mombasa','east-africa'],
  ['chennai-sydney','Chennai → Sydney','kamarajar','sydneyPort','southern-australia'],
  ['kolkata-chittagong','Kolkata → Chittagong','haldia','chittagong','bay-bangladesh'],
];
const airSpecs = [
  ['bengaluru-dubai-air','Bengaluru → Dubai · air cargo','bengaluruAirport','dubaiAirport'],
  ['hyderabad-dubai-air','Hyderabad → Dubai · air cargo','hyderabadAirport','dubaiAirport'],
  ['delhi-frankfurt-air','Delhi → Frankfurt · air cargo','delhiAirport','frankfurtAirport'],
  ['mumbai-singapore-air','Mumbai → Singapore · air cargo','mumbaiAirport','singapore'],
  ['delhi-heathrow-air','Delhi → London Heathrow · air cargo','delhiAirport','heathrow'],
  ['mumbai-hong-kong-air','Mumbai → Hong Kong · air cargo','mumbaiAirport','hongKongAirport'],
  ['chennai-singapore-air','Chennai → Singapore · air cargo','chennaiAirport','singapore'],
  ['delhi-chicago-air','Delhi → Chicago · air cargo','delhiAirport','chicagoAirport'],
  ['mumbai-doha-air','Mumbai → Doha · air cargo','mumbaiAirport','dohaAirport'],
  ['bengaluru-singapore-air','Bengaluru → Singapore · air cargo','bengaluruAirport','singapore'],
];
const multimodalSpecs = [
  {id:'pune-jnpt-rotterdam',label:'Pune → JNPT road → sea → Rotterdam',stops:['pune','jnpt','rotterdam'],legModes:['road','sea'],strategies:['','cape-europe']},
  {id:'delhi-mundra-jebel-ali',label:'Delhi → Mundra rail → sea → Jebel Ali',stops:['delhi','mundra','jebelAli'],legModes:['rail','sea'],strategies:['','hormuz']},
  {id:'hyderabad-chennai-singapore',label:'Hyderabad → Chennai road → port → sea → Singapore',stops:['hyderabad','chennai','kamarajar','singapore'],legModes:['road','road','sea'],strategies:['','','bay-malacca']},
  {id:'bengaluru-hyderabad-dubai-air',label:'Bengaluru → Hyderabad air hub → Dubai',stops:['bengaluruAirport','hyderabadAirport','dubaiAirport'],legModes:['air','air'],strategies:['','']},
];

const SEA_LANES = {
  malacca:[[72.2,18.7],[71.8,15],[73,10],[78,5.5],[82,3],[88,1.5],[92,2.5],[96,8],[98,6],[99,5],[99.5,4],[100,3.8],[100.5,3],[101,2.5],[102,2],[103,1.5]],
  'bay-malacca':[[82,10],[82.2,7],[84,5],[90,2],[94,2.5],[96,8],[98,6],[99,5],[99.5,4],[100,3.8],[100.5,3],[101,2.5],[102,2],[103,1.5]],
  hormuz:[[70,18.5],[66,17],[63,16],[61,17],[60.5,20],[60,22],[60,23],[59.5,23.5],[59.2,24],[58.4,24.6],[57.5,25.3],[56.5,25.5]],
  'east-hormuz':[[82,10],[82.2,7],[84,5],[80,4],[75,5],[69,8],[63,12],[60.5,20],[60,22],[60,23],[59.5,23.5],[59.2,24],[58.4,24.6],[57.5,25.3],[56.5,25.5]],
  'suez-from-east':[[82,10],[82.2,7],[84,5],[80,4],[75,5],[69,8],[63,12],[60,13],[56,13],[52,12.6],[48,12.2],[45,12.1],[43.8,12.3],[43.6,12.5],[43.4,12.6],[43.2,12.5],[42.5,13.5],[42,20],[40,24],[38,28],[32.5,31.2],[25,34.5],[15,36],[5,36.2],[-5.6,35.9],[-8,42],[-8,48],[-5,50],[-1,52],[2,52]],
  'cape-europe':[[70,17],[65,14],[60,9],[55,-1],[52,-13],[49,-23],[43,-33],[34,-35],[24,-36],[18,-36],[5,-35],[-5,-30],[-15,-18],[-25,-3],[-22,10],[-20,20],[-15,32],[-15,40],[-15,48],[-14,54],[-12,58],[-8,60],[-5,62],[0,62],[2,62],[3,59],[3,57],[3,55],[3,53]],
  'cape-europe-east':[[82,10],[82.2,7],[84,5],[80,4],[75,2],[70,-5],[60,-15],[52,-25],[43,-33],[34,-35],[24,-36],[18,-36],[5,-35],[-5,-30],[-15,-18],[-25,-3],[-22,10],[-20,20],[-15,32],[-15,40],[-15,48],[-14,54],[-12,58],[-8,60],[-5,62],[0,62],[2,62],[3,59],[3,57],[3,55],[3,53]],
  'bab-el-mandeb-jeddah':[[68,16],[62,10],[57,11.5],[52,12.4],[48,12.4],[45,12.3],[43.8,12.3],[43.5,12.5],[43.4,12.6],[43.2,12.7],[42.8,13],[42.3,15],[41,18],[40,20]],
  'malacca-busan':[[82,10],[82.2,7],[84,5],[90,2],[96,8],[98,6],[99,5],[99.5,4],[100,3.8],[100.5,3],[101,2.5],[102,2],[103,1.2],[103.5,0.8],[106,0.8],[110,5],[114,15],[118,18],[121,20],[123.5,21],[125,25],[127,30],[129,34]],
  'malacca-yokohama':[[82,10],[82.2,7],[84,5],[90,2],[96,8],[98,6],[99,5],[99.5,4],[100,3.8],[100.5,3],[101,2.5],[102,2],[103,1.2],[103.5,0.8],[106,0.8],[110,5],[114,15],[118,18],[121,20],[123.5,21],[125,25],[130,30],[135,32],[140,34],[141,34.5]],
  'cape-pacific':[[72,18.7],[70,12],[66,4],[60,-8],[52,-22],[40,-35],[20,-48],[-5,-55],[-35,-59],[-60,-60],[-72,-59],[-82,-54],[-86,-45],[-86,-35],[-86,-20],[-86,-5],[-92,5],[-105,5],[-115,5],[-125,15],[-130,25],[-127,30],[-118,33]],
  'cape-new-york':[[70,17],[65,14],[60,9],[55,-1],[52,-13],[49,-23],[43,-33],[34,-35],[24,-36],[18,-36],[5,-35],[-5,-30],[-12,-18],[-18,-5],[-18,10],[-25,22],[-40,30],[-55,36],[-68,40],[-72,40]],
  'palk-colombo':[[79,9.7],[79.8,10.1],[80.4,10.2],[80.9,10.1],[81.3,9.4],[82.4,9],[82.4,8],[82.4,7],[82.4,6],[81.5,5.5],[80,5.5],[79.5,6],[79.3,6.5]],
  'cochin-colombo':[[75,8.5],[76,7],[78,5.8],[79.1,5.7],[79.6,6.2]],
  'colombo-singapore':[[80.3,5.2],[84,3],[90,2],[96,8],[98,6],[99,5],[99.5,4],[100,3.8],[100.5,3],[101,2.5],[102,2],[103,1.5]],
  'east-africa':[[68,14],[60,8],[55,4],[50,2],[46,0],[43,-2],[42,-4],[41,-4],[40,-4]],
  'cape-durban':[[68,14],[60,7],[55,-2],[52,-12],[50,-23],[46,-30],[40,-35],[34,-34],[32,-32],[31,-31]],
  'southern-australia':[[82,10],[82.2,7],[84,5],[80,4],[75,2],[65,-5],[60,-15],[55,-25],[50,-32],[45,-38],[70,-45],[100,-46],[125,-44],[145,-44],[151,-44],[153,-40],[153,-35]],
  'bay-bangladesh':[[88,20],[89,18],[91,17.5],[91.2,19]],
};

const haversine = (a,b) => {const r=Math.PI/180,dLat=(b[1]-a[1])*r,dLon=(b[0]-a[0])*r;const x=Math.sin(dLat/2)**2+Math.cos(a[1]*r)*Math.cos(b[1]*r)*Math.sin(dLon/2)**2;return 6371*2*Math.atan2(Math.sqrt(x),Math.sqrt(1-x))};
const round = (n,d=1) => Number(n.toFixed(d));
function greatCircle(a,b,steps=36){const rad=Math.PI/180,deg=180/Math.PI,[lon1,lat1]=a.map(v=>v*rad),[lon2,lat2]=b.map(v=>v*rad);const p1=[Math.cos(lat1)*Math.cos(lon1),Math.cos(lat1)*Math.sin(lon1),Math.sin(lat1)],p2=[Math.cos(lat2)*Math.cos(lon2),Math.cos(lat2)*Math.sin(lon2),Math.sin(lat2)];const angle=Math.acos(Math.max(-1,Math.min(1,p1[0]*p2[0]+p1[1]*p2[1]+p1[2]*p2[2])));return Array.from({length:steps+1},(_,i)=>{const t=i/steps,s=Math.sin(angle)||1,w1=Math.sin((1-t)*angle)/s,w2=Math.sin(t*angle)/s;const x=w1*p1[0]+w2*p2[0],y=w1*p1[1]+w2*p2[1],z=w1*p1[2]+w2*p2[2];return [Math.atan2(y,x)*deg,Math.atan2(z,Math.hypot(x,y))*deg]})}
function routeGeometry(mode,from,to,strategy=''){const a=hubs[from].coordinates,b=hubs[to].coordinates;if(mode==='air')return {type:'great-circle-arc',coordinates:greatCircle(a,b)};if(mode==='sea'){const points=SEA_LANES[strategy]||[];return {type:'hand-authored-sea-lane',coordinates:[a,...points,b]}}const dx=b[0]-a[0],dy=b[1]-a[1],length=Math.hypot(dx,dy)||1,offset=(mode==='rail'?-0.16:0.22);const via=[(a[0]+b[0])/2-(dy/length)*offset,(a[1]+b[1])/2+(dx/length)*offset];return {type:mode==='rail'?'estimated-rail-polyline':'estimated-road-polyline',coordinates:[a,[(a[0]*2+via[0])/3,(a[1]*2+via[1])/3],via,[(via[0]+b[0]*2)/3,(via[1]+b[1]*2)/3],b]}}
function estimateLeg(mode,geometry,index){const pts=geometry.coordinates;const distanceKm=round(pts.slice(1).reduce((n,p,i)=>n+haversine(pts[i],p),0));const speed={road:52,rail:44,sea:34,air:760}[mode];const transitHours=round(distanceKm/speed+(mode==='air'?2.1:mode==='sea'?28:mode==='rail'?6:1.5));const rate={road:72,rail:50,sea:23,air:430}[mode];const base=Math.max(800,distanceKm*rate);const factor=1+(index%4)*0.035;const risk=distanceKm>5500?'high':distanceKm>1700?'medium':'low';const confidence=round(Math.max(.48,Math.min(.82,.78-distanceKm/40000-index*.015)),2);return {distanceKm,transitHours,costRangeInr:[Math.round(base*factor*.82/1000)*1000,Math.round(base*factor*1.18/1000)*1000],risk,confidence}}
function createLeg(from,to,mode,strategy,index){const geometry=routeGeometry(mode,from,to,strategy);const waypoints=geometry.coordinates.slice(1,-1).map((coordinates,i)=>({name:mode==='sea'?`Sea lane waypoint ${i+1}`:mode==='air'?`Great-circle sample ${i+1}`:`Illustrative route point ${i+1}`,coordinates}));return {from,to,mode,geometry,waypoints,estimates:estimateLeg(mode,geometry,index),disruptions:[]}}
function makeCorridor({id,label,scope,stops,legModes,strategies=[],provenance='estimated'}){const legs=stops.slice(1).map((to,i)=>createLeg(stops[i],to,legModes[i],strategies[i],i));const corridorHubs=stops.map((hubId,i)=>{const hub=hubs[hubId];return {...hub,role:i===0?'origin':i===stops.length-1?'destination':'transfer'}});const supportedModes=[...new Set(legModes)];if(supportedModes.length>1||multimodalSpecs.some(spec=>spec.id===id))supportedModes.push('multimodal');return {id,label,scope,modes:supportedModes,legs,hubs:corridorHubs,disruptions:[],provenance}}
export const corridors = [
  ...domesticSpecs.map(([id,label,stops,mode])=>makeCorridor({id,label,scope:'domestic',stops,legModes:Array(stops.length-1).fill(mode)})),
  ...seaSpecs.map(([id,label,from,to,strategy,via=[]])=>makeCorridor({id,label,scope:'international',stops:[from,...via,to],legModes:Array(via.length+1).fill('sea'),strategies:via.length?[strategy,...Array(via.length).fill('colombo-singapore')]:[strategy]})),
  ...airSpecs.map(([id,label,from,to])=>makeCorridor({id,label,scope:'international',stops:[from,to==='singapore'?'singaporeAirport':to],legModes:['air']})),

  ...multimodalSpecs.map(({id,label,stops,legModes,strategies})=>makeCorridor({id,label,scope:'international',stops,legModes,strategies})),
];
export const corridorById = Object.fromEntries(corridors.map(c=>[c.id,c]));
export const mapHubs = Object.values(hubs);
export const corridorCounts = {domestic:corridors.filter(c=>c.scope==='domestic').length,international:corridors.filter(c=>c.scope==='international').length,total:corridors.length};
export const modes=['road','rail','sea','air','multimodal'];
export const modeLabel = mode => ({road:'Road',rail:'Rail',sea:'Sea',air:'Air',multimodal:'Multimodal'})[mode]||mode;
export function findCorridor({origin,destination,mode}){return corridors.find(c=>c.modes.includes(mode)&&c.hubs[0].id===origin&&c.hubs.at(-1).id===destination)||corridors.find(c=>c.hubs[0].id===origin&&c.hubs.at(-1).id===destination)}
export function estimateSummary(corridor,weightKg=500){const legs=corridor?.legs||[];const scale=Math.max(.15,Math.sqrt(Math.max(1,+weightKg||500)/500));return {distanceKm:legs.reduce((n,l)=>n+l.estimates.distanceKm,0),transitHours:legs.reduce((n,l)=>n+l.estimates.transitHours,0),costRangeInr:[Math.round(legs.reduce((n,l)=>n+l.estimates.costRangeInr[0]*scale,0)),Math.round(legs.reduce((n,l)=>n+l.estimates.costRangeInr[1]*scale,0))],risk:legs.some(l=>l.estimates.risk==='high')?'high':legs.some(l=>l.estimates.risk==='medium')?'medium':'low',confidence:legs.length?round(legs.reduce((n,l)=>n+l.estimates.confidence,0)/legs.length,2):0}}
export const routeAlternatives = corridor => corridor?corridors.filter(c=>c.id!==corridor.id&&c.hubs[0].id===corridor.hubs[0].id&&c.hubs.at(-1).id===corridor.hubs.at(-1).id):[];
export const corridorProvenanceText = provenance => ({estimated:'Planning estimate',synthetic:'Synthetic demonstration',recorded:'Recorded response',live:'Live Java response',historical:'Historical data', 'user-provided':'User-provided data'})[provenance]||'Unverified data';

import React from 'react';
import { DataStatusBadge } from './UI';

const layerLabels = [
  ['domestic','Domestic corridors'],['international','International corridors'],['ports','Seaports'],['airports','Airports'],['roads','Roads'],['rail','Rail'],['seaLanes','Sea lanes'],['airArcs','Air routes'],['disruptions','Disruption studies'],['depots','Inland container depots'],['railHubs','Rail hubs'],['industrialCities','Industrial cities'],
];

export default function MapLegend({ layers, onToggle }) {
  return <div className="map-legend">
    <details open>
      <summary>Map legend and layers</summary>
      <div className="legend-items" aria-label="Transport route legend">
        <span><i className="legend-line road"/>Road</span><span><i className="legend-line rail"/>Rail</span><span><i className="legend-line sea"/>Sea</span><span><i className="legend-line air"/>Air</span><span><i className="legend-line selected"/>Selected route</span><span><i className="legend-line alternative"/>Alternative</span><span><i className="legend-dot disruption"/>Modeled disruption</span>
      </div>
      <div className="map-layers">{layerLabels.map(([key,label])=><label key={key}><input type="checkbox" checked={layers[key]} onChange={()=>onToggle(key)}/>{label}</label>)}</div>
      <div className="provenance-legend"><span>Provenance</span><DataStatusBadge status="estimated" label="Planning estimate"/><DataStatusBadge status="informational" label="Recorded or synthetic"/></div>
    </details>
  </div>;
}

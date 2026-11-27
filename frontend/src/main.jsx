import React,{useState} from 'react';import {createRoot} from 'react-dom/client';import {Landing} from './Landing';import Workspace from './Workspace';
function App(){const [page,set]=useState('Landing');function go(p){set(p);window.scrollTo(0,0)}return page==='Landing'?<Landing open={go}/>:<Workspace page={page} go={go} home={()=>go('Landing')}/>}
createRoot(document.getElementById('root')).render(<App/>);

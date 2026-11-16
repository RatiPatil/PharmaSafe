// Deploy only tested main commits, backend first. Never print deploy-hook credentials.
const sha=process.env.GITHUB_SHA;
if (!/^[0-9a-f]{40}$/.test(sha||'')) throw Error('A full tested commit SHA is required.');
const services=[
 {name:'backend',hook:process.env.RENDER_BACKEND_DEPLOY_HOOK,url:process.env.RENDER_BACKEND_URL||'https://pharmasafe-qjm1.onrender.com',release:'/api/health/release'},
 {name:'frontend',hook:process.env.RENDER_FRONTEND_DEPLOY_HOOK,url:process.env.RENDER_FRONTEND_URL||'https://pharmasafe-frontend.onrender.com',release:'/release.json'},
];
for(const service of services){
 if(!service.hook)throw Error(`Missing RENDER_${service.name.toUpperCase()}_DEPLOY_HOOK repository secret. Configure the existing Render service deploy hook in GitHub Actions settings.`);
 const hook=new URL(service.hook);
 if(hook.protocol!=='https:'||hook.hostname!=='api.render.com')throw Error(`Invalid ${service.name} Render deploy-hook URL.`);
 const url=new URL(service.url);if(url.protocol!=='https:')throw Error('Use HTTPS public service URLs.');
}
for(const service of services){
 console.log(`Deploying tested ${service.name} commit ${sha.slice(0,12)}…`);
 const hook=new URL(service.hook);hook.searchParams.set('ref',sha);
 let response;
 try {response=await fetch(hook,{method:'POST',signal:AbortSignal.timeout(30000)});}catch{throw Error(`${service.name} deploy hook could not be reached.`);}
 if(!response.ok)throw Error(`${service.name} deploy hook failed with HTTP ${response.status}.`);
 let ready=false;
 for(let attempt=0;attempt<100;attempt++){
  try {
   const check=await fetch(service.url.replace(/\/+$/,'')+service.release+`?expected=${sha}&check=${attempt}`,{headers:{'Cache-Control':'no-cache'},signal:AbortSignal.timeout(15000)});
   const release=check.ok?await check.json():null;
   if(release?.commit===sha){ready=true;break;}
  }catch{/* Wait for Render startup and migration completion. */}
  await new Promise(resolve=>setTimeout(resolve,15000));
 }
 if(!ready)throw Error(`${service.name} did not serve the tested commit within the deployment window. Inspect Render logs; subsequent services were not deployed.`);
 if(service.name==='backend'){
  const check=await fetch(service.url+'/api/auth/access-roles',{signal:AbortSignal.timeout(30000)});
  if(!check.ok)throw Error('Deployed backend access-management API did not pass its smoke check.');
  const roles=await check.json();if(!Array.isArray(roles)||roles.length!==7||roles.some(r=>r.role==='ROLE_ADMIN'))throw Error('Deployed backend returned an unexpected public role catalog.');
 }
 console.log(`Verified ${service.name} serves ${sha.slice(0,12)}.`);
}

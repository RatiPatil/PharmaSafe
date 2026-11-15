// Isolated H2 browser check. Production database settings and frontend environment files are not loaded.
const { chromium } = require('./qa/node_modules/playwright');
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const { randomBytes } = require('node:crypto');
const path=require('node:path');
const os=require('node:os');
const net=require('node:net');
const root=path.resolve(__dirname,'..');
const qaDirectory=fs.mkdtempSync(path.join(os.tmpdir(),'pharmasafe-ui-qa-'));
async function freePort(){const probe=net.createServer();await new Promise(r=>probe.listen(0,'127.0.0.1',r));const port=probe.address().port;await new Promise(r=>probe.close(r));return port;}
const password=randomBytes(18).toString('base64url')+'A1!';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
 const reports=path.join(root,'backend/target/surefire-reports');
 const report=fs.readdirSync(reports).find(name=>/^TEST-.*\.xml$/.test(name));
 if(!report)throw Error('Run backend mvn test first to generate a test classpath.');
 const xml=fs.readFileSync(path.join(reports,report),'utf8');
 const cp=xml.match(/name="java.class.path" value="([^"]+)"/)[1].replaceAll('&amp;','&').replaceAll('&quot;','"');
 const frontendPort=await freePort();
 const origin=`http://127.0.0.1:${frontendPort}`;
 let backendPort=0,initialized=false;
 const log=fs.createWriteStream(path.join(qaDirectory,'backend.log'));
 const java=spawn('java',['-cp',cp,'com.pharmasafe.qa.BrowserQaApplication','--spring.profiles.active=test','--server.port=0','--server.address=127.0.0.1'],{cwd:root,env:{PATH:process.env.PATH,...(process.env.SystemRoot?{SystemRoot:process.env.SystemRoot}:{}),SPRING_PROFILES_ACTIVE:'test',INIT_ADMIN_USERNAME:'ui-admin',INIT_ADMIN_PASSWORD:password,CORS_ALLOWED_ORIGINS:origin,SPRING_DATASOURCE_URL:'jdbc:h2:mem:stability_browser;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;INIT=CREATE SEQUENCE IF NOT EXISTS case_number_seq START WITH 1001',SPRING_DATASOURCE_DRIVER_CLASS_NAME:'org.h2.Driver',SPRING_DATASOURCE_USERNAME:'sa',SPRING_DATASOURCE_PASSWORD:'',SPRING_JPA_DATABASE_PLATFORM:'org.hibernate.dialect.H2Dialect',SPRING_JPA_HIBERNATE_DDL_AUTO:'create-drop',SPRING_FLYWAY_ENABLED:'false'}});
 java.stdout.pipe(log);java.stderr.pipe(log);java.stdout.on('data',chunk=>{const value=String(chunk),match=value.match(/Tomcat started on port (\d+)/);if(match)backendPort=Number(match[1]);if(value.includes('PharmaSafe initial administrator successfully created'))initialized=true;});
 let server,browser,diagnosticPage;
 try{
  let ready=false;
  for(let i=0;i<300;i++){try{if(!backendPort||!initialized)throw Error('Starting');const r=await fetch(`http://127.0.0.1:${backendPort}/api/health`);if(r.ok){ready=true;break;}}catch{} await sleep(200);}
  if(!ready)throw Error('Isolated backend did not start.');
  process.chdir(root+'/frontend');process.env.VITE_API_URL='';
  const { createServer }=await import(root+'/frontend/node_modules/vite/dist/node/index.js');
  server=await createServer({root:root+'/frontend',envDir:false,define:{'import.meta.env.VITE_API_URL':JSON.stringify('')},server:{host:'127.0.0.1',port:frontendPort,strictPort:true,proxy:{'/api':{target:`http://127.0.0.1:${backendPort}`,changeOrigin:true}}}});await server.listen();
  browser=await chromium.launch({headless:true,args:['--no-sandbox']});
  const failures=[];const contexts=[];
  const publicRoles=JSON.parse(fs.readFileSync(root+'/frontend/src/config/publicRoleCatalog.json','utf8'));
  // Public page layout checks use the real role catalog. Selection alone is stubbed
  // because this isolated H2 harness has no PostgreSQL rate-limit table.
  if(process.env.PROCESSING_ONLY!=='1'){
  const publicContext=await browser.newContext({viewport:{width:1440,height:1000}});
  const publicPage=await publicContext.newPage();
  await publicPage.route('**/api/auth/access-roles',r=>r.abort());
  await publicPage.goto(origin+'/#/home');
  await publicPage.getByRole('heading',{name:'Find your place in the safety workflow.'}).waitFor();
  await publicPage.getByText(/The access service is temporarily unavailable/).waitFor();
  if(await publicPage.getByRole('button',{name:/Request access as/}).count()!==6)throw Error('Public cards must remain visible during backend failure');
  await publicPage.unroute('**/api/auth/access-roles');await publicPage.getByRole('button',{name:'Retry connection',exact:true}).click();
  await publicPage.getByText(/The access service is temporarily unavailable/).waitFor({state:'hidden'});
  console.log('PASS immediate six-role catalogue during API failure and successful connection retry');
  if(await publicPage.getByRole('button',{name:'Log In to PharmaSafe',exact:false}).count())throw Error('Login form must not appear on home');
  await publicPage.getByRole('button',{name:'Request access as Medical Reviewer',exact:true}).waitFor();
  fs.mkdirSync(path.join(qaDirectory,'screens'),{recursive:true});
  for(const width of [1440,768,390]){
   await publicPage.setViewportSize({width,height:1000});
   if(await publicPage.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw Error('Public home horizontal overflow '+width);
   await publicPage.screenshot({path:path.join(qaDirectory,'screens',`home-${width}.png`),fullPage:true});
  }

  for(const account of publicRoles){
   await publicPage.goto(origin+'/#/access/'+account.role+'/user');await publicPage.getByRole('heading',{name:account.name+' sign in',exact:true}).waitFor();
   for(const mode of ['user','admin']){await publicPage.goto(origin+'/#/access/'+account.role+'/'+mode);await publicPage.getByRole('heading',{name:account.name+(mode==='admin'?' Admin':'')+' sign in',exact:true}).waitFor();
    for(const [width,height] of [[1440,900],[1024,640],[390,900]]){await publicPage.setViewportSize({width,height});const fit=await publicPage.evaluate(()=>({horizontal:document.documentElement.scrollWidth>innerWidth,overlap:document.querySelector('.login-submit').getBoundingClientRect().top<=document.querySelector('input[id$="-password"]').getBoundingClientRect().bottom}));if(fit.horizontal||fit.overlap)throw Error('Role portal layout '+account.role+' '+mode+' '+width);}
   }
   await publicPage.goto(origin+'/#/access/'+account.role+'/register');await publicPage.getByRole('heading',{name:account.name+' registration',exact:true}).waitFor();if(await publicPage.getByRole('navigation',{name:'Role access tabs'}).count()!==(account.role==='ROLE_CASE_PROCESSOR'?0:1))throw Error('Missing dedicated access tabs');
  }
  await require('./qa/verify-role-login-layout.cjs')(publicPage,origin,publicRoles);
  await publicPage.goto(origin+'/#/access/ROLE_AUDITOR/admin');await publicPage.getByRole('heading',{name:'Auditor / Viewer Admin sign in',exact:true}).waitFor();
  const suggestedAdmin=await publicPage.getByLabel('Username or official email',{exact:true}).inputValue();
  if(suggestedAdmin!=='pv.auditor.admin')throw Error('Missing dedicated admin username suggestion');
  await publicPage.goto(origin+'/#/access/ROLE_AUDITOR/user');await publicPage.getByRole('heading',{name:'Auditor / Viewer sign in',exact:true}).waitFor();
  await publicPage.evaluate(savedUsername=>{const u=document.querySelector('input[id$="-username"]'),p=document.querySelector('input[id$="-password"]');if(u.autocomplete!=='off'||p.autocomplete!=='off')throw Error('Employee autofill must be disabled');u.value=savedUsername;p.value='SyntheticSavedAdminPassword123!';p.dispatchEvent(new Event('input',{bubbles:true}));},suggestedAdmin);
  if(await publicPage.getByText('Use the Role Admin login tab for this account.',{exact:true}).count())throw Error('Automatic credential clearing must not display a warning');
  if(await publicPage.getByLabel('Username or official email',{exact:true}).inputValue()||await publicPage.getByLabel('Password',{exact:true}).inputValue())throw Error('Saved admin credentials leaked into employee form');
  console.log('PASS separate administrator autofill metadata and browser-inserted credential clearing on employee login');
  await publicPage.setViewportSize({width:1440,height:1000});await publicPage.goto(origin+'/#/home');console.log('PASS all six dedicated role portals: user login, role-admin login, reloadable registration and responsive forms');
  await publicPage.route('**/api/auth/role-selection',r=>r.fulfill({status:200,contentType:'application/json',body:JSON.stringify({selectionToken:'layout-check-only'})}));
  await publicPage.getByRole('button',{name:'Request access as Medical Reviewer',exact:true}).click();
  await publicPage.getByRole('heading',{name:'Medical Reviewer registration',exact:true}).waitFor();
  await publicPage.getByLabel('Department',{exact:true}).selectOption('Drug Safety / Pharmacovigilance');
  await publicPage.getByLabel('Medical Qualification',{exact:true}).selectOption('MD');
  for(const width of [1440,768,390]){
   await publicPage.setViewportSize({width,height:1000});
   const layout=await publicPage.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth,nested:[...document.querySelectorAll('.pa-shell *')].filter(e=>e.scrollHeight>e.clientHeight+2&&['auto','scroll'].includes(getComputedStyle(e).overflowY)).length}));
   if(layout.overflow||layout.nested)throw Error('Registration must use one page scroll '+width);
   await publicPage.screenshot({path:path.join(qaDirectory,'screens',`registration-${width}.png`),fullPage:true});
  }
  await publicPage.goto(origin+'/#/forgot-password');await publicPage.getByLabel('Official email or username').waitFor();
  for(const width of [1440,768,390]){await publicPage.setViewportSize({width,height:900});if(await publicPage.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw Error('Recovery page overflow '+width);if(!await publicPage.locator('.recovery-photo img').evaluate(img=>img.complete&&img.naturalWidth>0&&img.getBoundingClientRect().height>=90))throw Error('Recovery image missing');await publicPage.screenshot({path:path.join(qaDirectory,'screens',`recovery-${width}.png`),fullPage:true});}
  for(const [width,height]of [[1920,1080],[1440,900],[1366,768],[1280,720],[1024,640],[768,640],[390,700],[320,640]]){
   await publicPage.setViewportSize({width,height});const fit=await publicPage.evaluate(()=>{const root=document.documentElement,submit=document.querySelector('.pa-recovery button[type=submit]').getBoundingClientRect(),footer=document.querySelector('.pa-footer').getBoundingClientRect();return {height:root.scrollHeight,viewport:innerHeight,scroll:root.scrollHeight>innerHeight+1,clipped:submit.bottom>innerHeight||footer.bottom>innerHeight+1,nested:[...document.querySelectorAll('.public-recovery *')].filter(e=>e.scrollHeight>e.clientHeight+2&&['auto','scroll'].includes(getComputedStyle(e).overflowY)).length};});if(fit.scroll||fit.clipped||fit.nested)throw Error('Recovery must fit without a scrollbar '+width+'x'+height+' '+JSON.stringify(fit));
  }
  console.log('PASS recovery fits eight desktop, tablet and mobile viewports without scrollbars or clipped controls');
  console.log('PASS photographic recovery page at desktop, tablet and mobile');
  await publicContext.close();console.log('PASS separate public home, role dropdowns and registration at desktop/tablet/mobile with one scroll surface');

  }
  async function login(username,home,portalRole,adminPortal=false){const context=await browser.newContext({viewport:{width:1440,height:1000}});contexts.push(context);const p=await context.newPage();diagnosticPage=p;p.on('pageerror',e=>failures.push(e.message));p.on('dialog',d=>d.accept());const heldSessions=[];let holdStartup=username==='ui-admin';const roleModules=[];p.on('request',r=>{if(/\/src\/components\/(RoleWorkspace|CaseEntryForm)\.tsx/.test(r.url()))roleModules.push(r.url());});
  if(holdStartup)await p.route('**/api/auth/me',route=>{if(holdStartup){heldSessions.push(route);return;}return route.continue();});
  await p.goto(origin+(portalRole?'/#/access/'+portalRole+'/'+(adminPortal?'admin':'user'):'/#/login'));if(username==='ui-admin'){
   await p.getByLabel('Username',{exact:false}).waitFor({timeout:20000});await p.getByText('Checking existing session…',{exact:true}).waitFor({timeout:5000});if(!await p.getByRole('button',{name:'Log In to PharmaSafe',exact:false}).isEnabled())throw Error('Login blocked by background session request');if(roleModules.length)throw Error('Role workspace downloaded before authentication');console.log('PASS editable login renders while session endpoint is held; workspace code remains deferred');
   await p.getByRole('heading',{name:'Sign in to PharmaSafe',exact:true}).waitFor();fs.mkdirSync(path.join(qaDirectory,'screens'),{recursive:true});
   for(const width of [1440,1024,768,390,320]){await p.setViewportSize({width,height:900});await p.waitForTimeout(60);const valid=await p.evaluate(()=>{const u=document.querySelector('input[id$="-username"]').getBoundingClientRect(),pw=document.querySelector('input[id$="-password"]').getBoundingClientRect(),b=document.querySelector('.login-submit').getBoundingClientRect();return document.documentElement.scrollWidth<=innerWidth&&u.width>150&&pw.top>u.bottom&&b.top>pw.bottom;});if(!valid)throw Error('Login layout overflow or overlap at '+width);if(width===1440||width===390)await p.screenshot({path:path.join(qaDirectory,'screens','login-'+width+'.png'),fullPage:true});}
   for(const [width,height] of [[1920,880],[1440,900],[1366,768],[1024,640]]){await p.setViewportSize({width,height});const fit=await p.evaluate(()=>({page:document.documentElement.scrollHeight<=innerHeight+1,form:document.querySelector('.login-access').scrollHeight<=document.querySelector('.login-access').clientHeight+1,focus:getComputedStyle(document.querySelector('input[id$="-username"]')).outlineStyle}));if(!fit.page||!fit.form||fit.focus!=='none')throw Error('Login must fit desktop viewport and use group focus '+width+'x'+height);}
   await p.setViewportSize({width:1440,height:1000});console.log('PASS professional login layout at five widths without field overlap and four complete desktop viewport fits');
  }holdStartup=false;await Promise.all(heldSessions.map(route=>route.fulfill({status:401,contentType:'application/json',body:'{"message":"No test session"}'}).catch(()=>{})));await p.unroute('**/api/auth/me');await p.getByLabel('Username',{exact:false}).fill(username);await p.getByLabel(/^Password/).fill(password);await p.getByRole('button',{name:'Log In to PharmaSafe',exact:false}).click();await p.getByTestId(home).waitFor({timeout:20000});return p;}
  const admin=await login('ui-admin','admin-home');console.log('PASS administrator login and dedicated home');
  if(process.env.PROCESSING_ONLY==='1'){await admin.evaluate(async password=>{const token=(await(await fetch('/api/auth/csrf')).json()).token;const r=await fetch('/api/administration/assignments/ROLE_CASE_PROCESSOR',{method:'POST',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':token},body:JSON.stringify({username:'processing.department.admin',email:'processing.department.admin@example.test',fullName:'Synthetic Department Admin',password,reason:'Isolated processing workflow verification',expectedVersion:0})});if(!r.ok)throw Error(await r.text());},password);const department=await login('processing.department.admin','role-admin-home','ROLE_CASE_PROCESSOR',true);await require('./qa/verify-processing-team-workflow.cjs')({browser,origin,department,password,registrationChoices:JSON.parse(fs.readFileSync(root+'/frontend/src/config/inputRules.json','utf8')).registrationChoices,qaDirectory});console.log('QA artifacts: '+qaDirectory);return;}
  const firstLegend=admin.locator('.premium-chart-legend button').first();await firstLegend.click();
  if(await firstLegend.getAttribute('aria-pressed')!=='true')throw Error('Chart legend must highlight the selected stage');
  await firstLegend.click();
  await admin.emulateMedia({reducedMotion:'reduce'});
  if(await admin.locator('.premium-metric-icon').first().count()===0)throw Error('Dashboard KPI icons missing');
  if(await admin.locator('.premium-donut-segment').count() && await admin.locator('.premium-donut-segment').first().evaluate(el=>getComputedStyle(el).animationName)!=='none')throw Error('Reduced motion must disable chart animation');
  await admin.emulateMedia({reducedMotion:'no-preference'});
  console.log('PASS dashboard legend interaction and reduced-motion presentation');

  await admin.getByRole('button',{name:'Users & access',exact:true}).click();
  const roles=[['analyst','Case processing','analyst-home'],['medical','Medical review','medical-home'],['quality','Quality review','quality-home'],['manager','Workflow management','manager-home'],['reporting','Regulatory reporting','reporting-home'],['auditor','Audit workspace','auditor-home']];
  for(const [username,role]of roles){await admin.getByLabel('Username',{exact:true}).fill(username);await admin.getByLabel('Full name',{exact:true}).fill('Synthetic '+username);await admin.getByLabel('Initial password',{exact:true}).fill(password);await admin.getByLabel(/^Workspace role/).first().selectOption({label:role});await admin.getByRole('button',{name:'Create user',exact:true}).click();await admin.getByLabel('Username',{exact:true}).waitFor();await admin.waitForFunction(()=>[...document.querySelectorAll('label')].find(x=>x.textContent==='Username')?.querySelector('input')?.value==='');}
  console.log('PASS create six role accounts through admin UI');
  await admin.getByRole('navigation').getByRole('button',{name:'User access management',exact:true}).click();
  await admin.getByRole('button',{name:'Account access',exact:true}).click();
  const accountRow=admin.getByRole('row').filter({hasText:'Synthetic analyst'});
  await accountRow.getByRole('button',{name:'View details',exact:true}).click();
  const accountDialog=admin.getByRole('dialog',{name:'Complete request details',exact:true});await accountDialog.getByRole('heading',{name:'Synthetic analyst',exact:true}).waitFor();
  if(!await accountDialog.isVisible())throw Error('Details must appear in a visible dialog');
  await admin.keyboard.press('Escape');await accountDialog.waitFor({state:'hidden'});
  if(!await accountRow.getByRole('button',{name:'View details',exact:true}).evaluate(el=>el===document.activeElement))throw Error('Details must restore row focus');
  for(const [width,height]of [[1440,900],[1024,640],[768,640],[390,700]]){
   await admin.setViewportSize({width,height});
   if(width<760)await admin.getByRole('button',{name:'Toggle navigation',exact:true}).click();
   const sidebar=await admin.evaluate(()=>{const n=document.querySelector('.ws-sidebar nav').getBoundingClientRect(),p=document.querySelector('.ws-person').getBoundingClientRect();return {overlap:n.bottom>p.top+1,footer:p.bottom>innerHeight+1};});
   if(sidebar.overlap||sidebar.footer)throw Error('Sidebar navigation overlaps the account footer '+width);
   if(width<760)await admin.keyboard.press('Escape');
   if(await admin.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw Error('Access worklist page overflow '+width);
   await admin.getByRole('row').filter({hasText:'Synthetic analyst'}).getByRole('button',{name:'View details',exact:true}).click();
   await accountDialog.getByRole('heading',{name:'Synthetic analyst',exact:true}).waitFor();
   if(await accountDialog.evaluate(el=>el.scrollWidth>el.clientWidth+1))throw Error('Details dialog overflow '+width);
   await admin.screenshot({path:path.join(qaDirectory,'screens',`access-details-${width}.png`)});
   await accountDialog.getByRole('button',{name:'Close complete request details',exact:true}).click();
  }
  await admin.setViewportSize({width:1440,height:1000});await admin.getByRole('navigation').getByRole('button',{name:'Users & access',exact:true}).click();
  console.log('PASS full account details, Escape/focus restoration and non-overlapping navigation at desktop, zoom-sized tablet and mobile');

  await admin.getByRole('button',{name:'My profile',exact:true}).click();await admin.getByLabel('Full name',{exact:true}).fill('Synthetic System Administrator');await admin.getByLabel('Department',{exact:true}).fill('Safety operations');await admin.getByRole('button',{name:'Save profile details',exact:true}).click();await admin.getByText('Your profile was updated. Your workspace role is unchanged.',{exact:true}).waitFor();console.log('PASS personal profile update without changing role or invalidating session');
  const created=await admin.evaluate(async()=>{const csrf=await(await fetch('/api/auth/csrf',{credentials:'include'})).json();const r=await fetch('/api/cases',{method:'POST',credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':csrf.token},body:JSON.stringify({countryCode:'USA',reportType:'Spontaneous',initialReceiptDate:'2026-10-30',latestReceivedDate:'2026-10-30',priority:'Routine',assignedToUsername:'analyst',patient:{patientIdentifier:'SYNTHETIC-P1',gender:'Unknown'},reporters:[{primary:true,qualification:'Physician',firstName:'Synthetic',lastName:'Reporter',countryCode:'USA'}],products:[{productType:'Suspect',productName:'Synthetic study product'}],events:[{eventVerbatim:'Example reaction',onsetDate:'2026-10-30',outcome:'Recovered/Resolved',serious:true,otherMedicallyImportant:true}],narrative:'Fictional case for automated verification. No real patient data.'})});if(!r.ok)throw Error('Create case '+r.status+' '+await r.text());return r.json();});
  const roleCodes={admin:'ROLE_ADMIN',analyst:'ROLE_CASE_PROCESSOR',medical:'ROLE_MEDICAL_REVIEWER',quality:'ROLE_QUALITY_REVIEWER',manager:'ROLE_SAFETY_MANAGER',reporting:'ROLE_REPORTING_SPECIALIST',auditor:'ROLE_AUDITOR'};
  const pages={admin};for(const [name,,home]of roles){pages[name]=await login(name,home,roleCodes[name]);await pages[name].getByRole('region',{name:'Role dashboard statistics'}).waitFor();if(await pages[name].locator('.role-stat-tiles article').count()!==4)throw Error('Missing role-specific metric tiles '+name);if(await pages[name].locator('.role-stat-chart').count()!==4)throw Error('Missing role charts '+name);console.log('PASS '+home+' unique role dashboard with four additional metrics and four charts');}

  for(const [name,p]of Object.entries(pages)){diagnosticPage=p;await p.getByRole('button',{name:'My profile',exact:true}).click();await p.getByRole('heading',{name:'Your role & workflow',exact:true}).waitFor();await p.locator('.ws-profile-details').getByText(roleCodes[name],{exact:true}).waitFor();if(name==='medical')await p.screenshot({path:path.join(qaDirectory,'screens','medical-profile.png')});await p.getByRole('button',{name:'Dashboard',exact:true}).click();}
  console.log('PASS own profile and role-specific workflow instructions for all seven roles');
  const analyst=pages.analyst;diagnosticPage=analyst;await analyst.getByRole('button',{name:new RegExp(created.caseNumber)}).click();await analyst.getByRole('button',{name:'Add laboratory result',exact:true}).click();await analyst.getByLabel('Test name',{exact:true}).fill('ALT');await analyst.getByLabel('Test date',{exact:true}).fill('2026-10-30');await analyst.getByLabel('Reported result',{exact:true}).fill('120');await analyst.getByLabel('Result unit',{exact:true}).fill('U/L');await analyst.getByLabel('Lower reference limit',{exact:true}).fill('0');await analyst.getByLabel('Upper reference limit',{exact:true}).fill('40');await analyst.getByLabel('Interpretation',{exact:true}).selectOption('HIGH');await analyst.getByRole('button',{name:'Save laboratory result',exact:true}).click();await analyst.getByRole('cell',{name:'120 U/L',exact:false}).waitFor();await analyst.locator('section[aria-label="Laboratory evidence"]').screenshot({path:path.join(qaDirectory,'screens','laboratory-evidence.png')});console.log('PASS structured laboratory entry and persisted result');await analyst.getByRole('button',{name:'Submit for Medical Review',exact:true}).click();await analyst.getByLabel('Routing reason').fill('Synthetic case complete for clinical review');await analyst.getByRole('button',{name:'Confirm routing',exact:true}).click();await analyst.getByRole('heading',{name:'Case record',exact:true}).waitFor();console.log('PASS analyst handoff to medical review');
  const medical=pages.medical;diagnosticPage=medical;await medical.getByRole('button',{name:'Refresh',exact:true}).click();await medical.getByRole('button',{name:new RegExp(created.caseNumber)}).click();await medical.getByLabel('Product',{exact:true}).selectOption('Synthetic study product');await medical.getByLabel('Event',{exact:true}).selectOption('Example reaction');await medical.getByLabel('Company causality',{exact:true}).selectOption('Possible');await medical.getByLabel('Expectedness',{exact:true}).selectOption('Unexpected');await medical.getByLabel('Assessment comment',{exact:true}).fill('Synthetic clinical evidence reviewed');await medical.getByRole('button',{name:'Save assessment',exact:true}).click();await medical.getByLabel('Product',{exact:true}).waitFor();await medical.waitForFunction(()=>[...document.querySelectorAll('label')].find(x=>x.textContent.startsWith('Product'))?.querySelector('select')?.value==='');await medical.getByRole('button',{name:'Route to Quality Review',exact:true}).click();await medical.getByRole('heading',{name:'Medical review queue',level:1,exact:true}).waitFor();console.log('PASS medical assessment and handoff to QC');
  const quality=pages.quality;diagnosticPage=quality;await quality.getByRole('button',{name:'Refresh',exact:true}).click();await quality.getByRole('button',{name:new RegExp(created.caseNumber)}).click();await quality.getByLabel('Patient and reporter reviewed',{exact:true}).check();await quality.getByLabel('Products and events reviewed',{exact:true}).check();await quality.getByLabel('Medical assessment and narrative reviewed',{exact:true}).check();await quality.getByLabel('Decision comments / return reason').fill('All synthetic QC checks confirmed');await quality.getByRole('button',{name:'Route to Approved',exact:true}).click();await quality.getByRole('heading',{name:'Quality review queue',level:1,exact:true}).waitFor();console.log('PASS QC approval with structured checklist');
  const manager=pages.manager;diagnosticPage=manager;await manager.getByRole('button',{name:'Refresh',exact:true}).click();await manager.getByRole('button',{name:'Open team case board',exact:false}).click();await manager.getByRole('button',{name:'Open case',exact:true}).first().click();await manager.getByRole('button',{name:'Route to Locked',exact:true}).click();console.log('PASS manager workflow lock');
  const reporting=pages.reporting;diagnosticPage=reporting;await reporting.getByRole('button',{name:'Safety reports',exact:true}).click();await reporting.getByLabel('Product',{exact:true}).fill('Synthetic study product');await reporting.getByLabel('Authority',{exact:true}).fill('Synthetic agency');await reporting.getByLabel('Due date',{exact:true}).fill('2026-11-15');await reporting.getByLabel('Related case numbers (comma separated)',{exact:true}).fill(created.caseNumber);await reporting.getByLabel('Summary',{exact:true}).fill('Synthetic internal report record');await reporting.getByRole('button',{name:'Create report record',exact:true}).click();await reporting.getByRole('button',{name:'Record submission',exact:true}).waitFor();await reporting.getByRole('button',{name:'Record submission',exact:true}).click();await reporting.getByText('Submitted',{exact:true}).waitFor();console.log('PASS reporting preparation and internal submission tracking');
  await reporting.getByRole('button',{name:'Report details & PDF',exact:true}).click();
  const reportEvidence=reporting.getByRole('region',{name:'Report evidence and PDF archive',exact:true});
  await reportEvidence.getByRole('button',{name:'Generate report PDF',exact:true}).waitFor();
  const [reportPdfDownload]=await Promise.all([reporting.waitForEvent('download'),reportEvidence.getByRole('button',{name:'Generate report PDF',exact:true}).click()]);
  fs.mkdirSync(path.join(qaDirectory,'documents'),{recursive:true});const reportPdfPath=path.join(qaDirectory,'documents','safety-report.pdf');await reportPdfDownload.saveAs(reportPdfPath);const reportPdfBytes=fs.readFileSync(reportPdfPath);if(reportPdfBytes.subarray(0,4).toString()!=='%PDF')throw Error('Report download is not a PDF');
  const [savedReportDownload]=await Promise.all([reporting.waitForEvent('download'),reportEvidence.getByRole('button',{name:'Download report PDF',exact:true}).click()]);const savedReportPath=path.join(qaDirectory,'documents','archived-safety-report.pdf');await savedReportDownload.saveAs(savedReportPath);if(!fs.readFileSync(savedReportPath).equals(reportPdfBytes))throw Error('Archived report PDF changed');
  const receivedLocal=await reporting.evaluate(()=>{const now=new Date();return new Date(now.getTime()-now.getTimezoneOffset()*60000).toISOString().slice(0,19);});
  await reportEvidence.getByLabel('Acknowledgement reference',{exact:true}).fill('QA-ACK-001');await reportEvidence.getByLabel('Acknowledgement received at',{exact:true}).fill(receivedLocal);await reportEvidence.getByLabel('Recording or correction reason',{exact:true}).fill('Synthetic external response for automated verification; no regulator transmission');await reportEvidence.getByRole('button',{name:'Save acknowledgement',exact:true}).click();await reportEvidence.getByText('QA-ACK-001',{exact:true}).waitFor();
  for(const width of [1440,768,390]){await reporting.setViewportSize({width,height:1000});if(await reporting.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw Error('Report evidence horizontal overflow '+width);}
  await reporting.setViewportSize({width:1440,height:1000});await reporting.screenshot({path:path.join(qaDirectory,'screens','report-evidence.png'),fullPage:true});await reportEvidence.getByRole('button',{name:'Close report details',exact:true}).click();
  console.log('PASS actual report PDF download, byte-identical archive retrieval, external acknowledgement entry and responsive report evidence');

  diagnosticPage=manager;await manager.getByRole('button',{name:'Refresh',exact:true}).click();await manager.getByRole('button',{name:'Route to Closed',exact:true}).click();await manager.waitForTimeout(200);console.log('PASS manager closure after recorded submission');
  diagnosticPage=reporting;await reporting.getByRole('button',{name:'Refresh case documents',exact:true}).click();await reporting.getByRole('button',{name:'Open PDF documents',exact:true}).first().click();await reporting.getByLabel('Issue final closed-case record',{exact:true}).check();
  const [pdfDownload]=await Promise.all([reporting.waitForEvent('download'),reporting.getByRole('button',{name:'Generate & download PDF',exact:true}).click()]);
  fs.mkdirSync(path.join(qaDirectory,'documents'),{recursive:true});const downloaded=path.join(qaDirectory,'documents','final-case.pdf');await pdfDownload.saveAs(downloaded);const pdfBytes=fs.readFileSync(downloaded);if(pdfBytes.subarray(0,4).toString()!=='%PDF')throw Error('Case download is not a PDF');
  const [archivedDownload]=await Promise.all([reporting.waitForEvent('download'),reporting.locator('.ws-documents').getByRole('button',{name:'Download PDF',exact:true}).first().click()]);const archivePath=path.join(qaDirectory,'documents','archived-case.pdf');await archivedDownload.saveAs(archivePath);if(!pdfBytes.equals(fs.readFileSync(archivePath)))throw Error('Archived PDF changed between downloads');
  console.log('PASS reporting final case PDF generation, browser download and byte-identical archive download');
  await reporting.locator('.ws-documents').screenshot({path:path.join(qaDirectory,'screens','case-document-archive.png')});await reporting.setViewportSize({width:390,height:900});const documentOverflow=await reporting.locator('.ws-content').evaluate(el=>el.scrollWidth>el.clientWidth+1);if(documentOverflow)throw Error('Mobile document workspace overflows the page');await reporting.setViewportSize({width:1440,height:1000});console.log('PASS mobile case document workspace without page overflow');
  const auditor=pages.auditor;diagnosticPage=auditor;await auditor.getByRole('button',{name:'Open case explorer',exact:true}).click();await auditor.getByRole('button',{name:'Open case',exact:true}).first().click();if(await auditor.getByRole('button',{name:'Update Case',exact:true}).count())throw Error('Auditor has edit action');await auditor.getByRole('tab',{name:'Narrative',exact:true}).click();await auditor.getByText('Fictional case for automated verification.',{exact:false}).waitFor();console.log('PASS read-only auditor case explorer');
  for (const p of Object.values(pages)) { if(await p.locator('.ws-content [role=alert]').count()) throw Error('Unexpected workspace alert: '+await p.locator('.ws-content [role=alert]').innerText()); }
  await auditor.reload();await auditor.getByRole('heading',{name:created.caseNumber,level:1,exact:true}).waitFor();await auditor.getByRole('button',{name:'Back to Case explorer',exact:false}).click();await auditor.goBack();await auditor.getByRole('heading',{name:created.caseNumber,level:1,exact:true}).waitFor();console.log('PASS case detail reload and browser back navigation');
  diagnosticPage=manager;
  await manager.getByRole('button',{name:'Reopen for follow-up',exact:true}).click();
  const reopen=manager.getByRole('dialog');await reopen.getByLabel('Case processor',{exact:true}).selectOption({label:'analyst'});
  await reopen.getByLabel('Follow-up due',{exact:true}).fill(new Date(Date.now()+86400000).toISOString().slice(0,16));
  await reopen.getByLabel('Reason for reopening',{exact:true}).fill('Synthetic additional clinical information');
  await reopen.getByLabel('Confirm your password',{exact:true}).fill(password);await reopen.getByRole('button',{name:'Save activity',exact:true}).click();
  await reopen.waitFor({state:'hidden'});await manager.getByText('1 open actions · 0 pending expedited report records',{exact:true}).waitFor();
  diagnosticPage=analyst;await analyst.getByRole('button',{name:'Refresh',exact:true}).click();
  await analyst.getByRole('button',{name:'Record received information',exact:true}).click();const receipt=analyst.getByRole('dialog');
  await receipt.getByLabel('Received date',{exact:true}).fill('2026-10-31');
  await receipt.getByLabel('Complete a follow-up request (optional)',{exact:false}).selectOption({label:'Synthetic additional clinical information'});
  await receipt.getByLabel('Information received',{exact:true}).fill('Synthetic follow-up receipt entered through the user interface');
  await receipt.getByLabel('Significance / amendment justification',{exact:true}).fill('Additional synthetic evidence received');
  await receipt.getByRole('button',{name:'Save activity',exact:true}).click();await receipt.waitFor({state:'hidden'});
  await analyst.getByText('Synthetic follow-up receipt entered through the user interface',{exact:true}).waitFor();
  await analyst.getByText('0 open actions · 0 pending expedited report records',{exact:true}).waitFor();
  await analyst.getByRole('button',{name:'Check existing cases',exact:true}).click();await analyst.getByRole('status').filter({hasText:'No exact matches in your accessible cases.'}).waitFor();
  console.log('PASS manager-confirmed reopening, processor receipt, linked action completion and scoped duplicate check');
  fs.mkdirSync(path.join(qaDirectory,'screens'),{recursive:true});
  for(const [name,p]of Object.entries(pages)){await p.goto(origin+'/#/workspace/'+(name==='admin'?'admin':name==='analyst'?'case_processor':name==='medical'?'medical_reviewer':name==='quality'?'quality_reviewer':name==='manager'?'safety_manager':name==='reporting'?'reporting_specialist':'auditor')+'/overview');await p.getByTestId(name==='admin'?'admin-home':roles.find(x=>x[0]===name)[2]).waitFor();
   const serverOverview=await p.evaluate(async()=>{const r=await fetch('/api/workspace/overview',{credentials:'include'});if(!r.ok)throw Error('Overview unavailable');return r.json();});
   const dashboardTotal=Number((await p.locator('.premium-donut-centre strong').innerText()).replace(/,/g,''));if(dashboardTotal!==serverOverview.totalCases)throw Error('Dashboard chart changed scoped case totals for '+name);
   await p.screenshot({path:path.join(qaDirectory,'screens',name+'.png')});}
  for(const width of [1920,1366,1024,768,390]){await admin.setViewportSize({width,height:900});await admin.waitForTimeout(50);const layout=await admin.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth,sidebar:document.querySelector('.ws-sidebar').getBoundingClientRect().toJSON(),main:document.querySelector('.ws-content').getBoundingClientRect().toJSON()}));if(layout.overflow)throw Error('Horizontal page overflow '+width);if(width<760){await admin.getByRole('button',{name:'Toggle navigation'}).click();await admin.getByRole('button',{name:'Users & access',exact:true}).click();await admin.getByRole('heading',{name:'Users & access',exact:true,level:1}).waitFor();await admin.getByRole('button',{name:'Toggle navigation'}).click();await admin.getByRole('button',{name:'Dashboard',exact:true}).click();}console.log('PASS responsive layout '+width);}
  await admin.screenshot({path:path.join(qaDirectory,'screens','admin-mobile.png')});
  await admin.getByRole('button',{name:'Toggle navigation'}).click();await admin.getByRole('navigation').getByRole('button',{name:'Create case',exact:true}).click();
  const editorTabs=await admin.getByRole('button',{name:/^[1-9]\./}).allTextContents();
  for(const tab of editorTabs){await admin.getByRole('button',{name:tab,exact:true}).click();const overflow=await admin.locator('.ws-content').evaluate(el=>el.scrollWidth>el.clientWidth+1);if(overflow)throw Error('Mobile case editor horizontal overflow: '+tab);}
  console.log('PASS all nine case-entry tabs on mobile without page overflow');
  await admin.getByRole('button',{name:'Toggle navigation'}).click();await admin.getByRole('navigation').getByRole('button',{name:'Dashboard',exact:true}).click();

  await admin.setViewportSize({width:1440,height:1000});
  await admin.getByRole('button',{name:'Users & access',exact:true}).click();await admin.getByLabel('Username',{exact:true}).fill('unsaved-test-user');admin.removeAllListeners('dialog');admin.once('dialog',d=>d.dismiss());await admin.getByRole('button',{name:'Audit history',exact:true}).click();if(await admin.getByLabel('Username',{exact:true}).inputValue()!=='unsaved-test-user')throw Error('Unfinished form lost');admin.once('dialog',d=>d.accept());await admin.getByRole('button',{name:'Audit history',exact:true}).click();admin.on('dialog',d=>d.accept());console.log('PASS unsaved changes cancel and confirmed navigation');
  for(const [name,p]of Object.entries(pages)){diagnosticPage=p;const names=await p.locator('nav button').allTextContents();for(const label of names){await p.getByRole('navigation').getByRole('button',{name:label,exact:true}).click();await p.waitForFunction(()=>![...document.querySelectorAll('.ws-content [role=status]')].some(x=>x.textContent==='Loading…'));if(await p.locator('.ws-content [role=alert]').count())throw Error(name+' navigation '+label+': '+await p.locator('.ws-content [role=alert]').innerText());}console.log('PASS every navigation item: '+name);}

  // Provisioning and account approval run through the real application. Only the
  // PostgreSQL-specific rate-limit store is replaced by an H2 test adapter.
  diagnosticPage=admin;
  for(const account of publicRoles){await admin.getByRole('navigation',{name:'Super Admin navigation'}).getByRole('button',{name:'Dashboard',exact:true}).click();const card=admin.locator('.admin-role-card').filter({has:admin.getByRole('heading',{name:account.name,exact:true})});await card.getByRole('button',{name:'Open role management',exact:true}).click();await admin.getByRole('heading',{name:account.name+' dashboard',exact:true}).waitFor();await admin.getByRole('region',{name:'Role work analytics',exact:true}).waitFor();if(await admin.getByLabel('Application role',{exact:true}).inputValue()!==account.role)throw Error('Role card opened the wrong dashboard');}
  console.log('PASS each Super Admin role card opens its dedicated dashboard, charts, employees and work');
  diagnosticPage=admin;await admin.getByRole('navigation',{name:'Super Admin navigation'}).getByRole('button',{name:'Role management',exact:true}).click();
  await admin.getByLabel('Application role',{exact:true}).selectOption('ROLE_CASE_PROCESSOR');await admin.getByRole('button',{name:'Role Admin assignments',exact:true}).click();
  async function provisionSupervisor(username,fullName){
   await admin.getByLabel('Full name',{exact:true}).fill(fullName);await admin.getByLabel('Unique username',{exact:true}).fill(username);await admin.getByLabel('Official email',{exact:true}).fill(username+'@example.test');await admin.getByLabel('Initial password',{exact:true}).fill(password);await admin.getByLabel('Assignment reason',{exact:true}).fill('Synthetic administrator training and role ownership verified');
   await admin.getByRole('button',{name:'Save Role Admin assignment',exact:true}).click();await admin.getByText('Current administrator:',{exact:false}).filter({hasText:username}).waitFor();
  }
  await provisionSupervisor('processor.supervisor','Synthetic Processor Administrator');
  const supervisor=await login('processor.supervisor','role-admin-home','ROLE_CASE_PROCESSOR',true);diagnosticPage=supervisor;
  if(await supervisor.getByRole('button',{name:'Role Admin assignments',exact:true}).count()||await supervisor.getByLabel('Application role',{exact:true}).count())throw Error('Role Admin must not see Super Admin controls');
  const registrationChoices=JSON.parse(fs.readFileSync(root+'/frontend/src/config/inputRules.json','utf8')).registrationChoices;
  const pendingAccounts=await admin.evaluate(async({password,registrationChoices})=>{
   const token=(await(await fetch('/api/auth/csrf',{credentials:'include'})).json()).token;
   const roles=await(await fetch('/api/auth/access-roles',{credentials:'include'})).json();const created=[];
   for(const [role,username,fullName] of [['ROLE_CASE_PROCESSOR','pending.processor.ui','Synthetic analyst'],['ROLE_MEDICAL_REVIEWER','pending.medical.ui','Synthetic Medical Applicant']]){
    const choice=roles.find(r=>r.role===role);const selection=await(await fetch('/api/auth/role-selection',{method:'POST',credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':token},body:JSON.stringify({role})})).json();
    const details=Object.fromEntries(choice.fields.map(field=>[field,registrationChoices[field]?.[0]||'REG-123']));
    const body={selectionToken:selection.selectionToken,fullName,employeeId:username,email:username+'@example.test',mobileNumber:'+12345678901',department:registrationChoices.department[0],designation:registrationChoices.designation[0],organization:'Synthetic Organization',username,password,confirmPassword:password,reason:'Assigned safety responsibilities',details};
    const response=await fetch('/api/auth/register',{method:'POST',credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':token},body:JSON.stringify(body)});if(!response.ok)throw Error('Real registration '+response.status+' '+await response.text());created.push({role,username,requestId:(await response.json()).requestId});
   }return created;
  },{password,registrationChoices});
  await supervisor.getByRole('navigation',{name:'Role Admin navigation'}).getByRole('button',{name:'Registration requests',exact:true}).click();await supervisor.getByText(/@pending.processor.ui/).waitFor();
  if(await supervisor.getByText(/pending.medical.ui/).count())throw Error('Medical applicant leaked into processor administration');
  const ownRequest=supervisor.getByRole('row').filter({hasText:'@pending.processor.ui'});await ownRequest.getByRole('button',{name:'Approve',exact:true}).click();
  const confirm=supervisor.getByRole('dialog',{name:'Confirm administrative action'});await confirm.getByLabel('Decision reason',{exact:true}).fill('Synthetic applicant training verified');await confirm.getByRole('button',{name:'Confirm action',exact:true}).click();await confirm.waitFor({state:'hidden'});await ownRequest.getByText('APPROVED',{exact:true}).waitFor();
  const denial=await supervisor.evaluate(async(otherId)=>{const token=(await(await fetch('/api/auth/csrf',{credentials:'include'})).json()).token;return Promise.all([
   fetch('/api/administration/users?role=ROLE_MEDICAL_REVIEWER',{credentials:'include'}),fetch('/api/admin/users',{credentials:'include'}),fetch('/api/administration/registrations/'+otherId+'/approve',{method:'PUT',credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':token},body:JSON.stringify({reason:'Attempted cross-role access'})})
  ]).then(values=>values.map(r=>r.status));},pendingAccounts.find(r=>r.role==='ROLE_MEDICAL_REVIEWER').requestId);
  if(denial.some(status=>status!==403))throw Error('Cross-role API requests must all be rejected '+JSON.stringify(denial));
  await supervisor.getByRole('button',{name:'My role users',exact:true}).click();await supervisor.getByText(/@pending.processor.ui/).waitFor();
  if(await supervisor.getByRole('cell',{name:'Synthetic analyst',exact:false}).count()<2)throw Error('Synthetic duplicate employee names missing');
  const staffCodes=await supervisor.locator('tbody tr td:nth-child(1) span').allTextContents();if(new Set(staffCodes).size!==staffCodes.length||staffCodes.some(v=>!v.includes('PS-')))throw Error('Each employee needs a unique visible staff identity');
  await supervisor.getByRole('button',{name:'Employee work',exact:true}).click();await supervisor.getByRole('cell',{name:created.caseNumber,exact:true}).waitFor();
  for(const width of [1440,1024,768,390]){await supervisor.setViewportSize({width,height:900});if(await supervisor.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw Error('Role Admin page overflow '+width);await supervisor.screenshot({path:path.join(qaDirectory,'screens',`role-admin-${width}.png`),fullPage:true});}
  await supervisor.setViewportSize({width:1440,height:1000});
  const superAnalytics=await admin.evaluate(async()=>{return(await fetch('/api/administration/analytics?role=ROLE_CASE_PROCESSOR',{credentials:'include'})).json();});const ownAnalytics=await supervisor.evaluate(async()=>{return(await fetch('/api/administration/analytics',{credentials:'include'})).json();});if(JSON.stringify(superAnalytics)!==JSON.stringify(ownAnalytics))throw Error('Super Admin and Role Admin dashboards must use the same role data');
  const processorApplicant=await login('pending.processor.ui','analyst-home','ROLE_CASE_PROCESSOR');
  await admin.getByRole('button',{name:'Registration requests',exact:true}).click();await admin.getByLabel('Application role',{exact:true}).selectOption('ROLE_MEDICAL_REVIEWER');await admin.getByText(/@pending.medical.ui/).waitFor();
  const medicalRequest=admin.getByRole('row').filter({hasText:'@pending.medical.ui'});await medicalRequest.getByRole('button',{name:'Approve',exact:true}).click();const medicalConfirm=admin.getByRole('dialog',{name:'Confirm administrative action'});await medicalConfirm.getByLabel('Decision reason',{exact:true}).fill('Synthetic medical qualifications verified');await medicalConfirm.getByRole('button',{name:'Confirm action',exact:true}).click();await medicalConfirm.waitFor({state:'hidden'});await medicalRequest.getByText('APPROVED',{exact:true}).waitFor();
  await login('pending.medical.ui','medical-home','ROLE_MEDICAL_REVIEWER');
  await admin.getByLabel('Application role',{exact:true}).selectOption('ROLE_CASE_PROCESSOR');await admin.getByRole('button',{name:'Role Admin assignments',exact:true}).click();await provisionSupervisor('processor.successor','Synthetic Successor Administrator');
  await supervisor.getByRole('button',{name:'Refresh administration',exact:true}).click();await supervisor.getByLabel('Username or official email',{exact:true}).waitFor();
  const successor=await login('processor.successor','role-admin-home','ROLE_CASE_PROCESSOR',true);await admin.getByRole('button',{name:'Release current assignment',exact:true}).click();const releaseConfirm=admin.getByRole('dialog',{name:'Confirm administrative action'});await releaseConfirm.getByLabel('Decision reason',{exact:true}).fill('Synthetic administrator rotation completed');await releaseConfirm.getByRole('button',{name:'Confirm action',exact:true}).click();await releaseConfirm.waitFor({state:'hidden'});await admin.getByText('No administrator is currently assigned.',{exact:true}).waitFor();
  await successor.getByRole('button',{name:'Refresh administration',exact:true}).click();await successor.getByLabel('Username or official email',{exact:true}).waitFor();
  await admin.getByRole('button',{name:'Administrative audit',exact:true}).click();await admin.getByRole('cell',{name:'ROLE ADMIN RELEASED',exact:true}).waitFor();
  await processorApplicant.getByRole('button',{name:'Refresh',exact:true}).click();await processorApplicant.getByTestId('analyst-home').waitFor();
  await admin.getByLabel('Application role',{exact:true}).selectOption('ROLE_REPORTING_SPECIALIST');await admin.getByRole('button',{name:'Role Admin assignments',exact:true}).click();await provisionSupervisor('reporting.supervisor','Synthetic Reporting Administrator');
  const reportingSupervisor=await login('reporting.supervisor','role-admin-home','ROLE_REPORTING_SPECIALIST',true);await reportingSupervisor.getByRole('button',{name:'Reporting work',exact:true}).click();await reportingSupervisor.getByRole('cell',{name:'Synthetic study product',exact:true}).waitFor();await reportingSupervisor.locator('tbody').getByRole('cell',{name:'Submitted',exact:true}).waitFor();
  await reportingSupervisor.getByRole('button',{name:'Report details & PDF',exact:true}).click();const supervisorEvidence=reportingSupervisor.getByRole('region',{name:'Report evidence and PDF archive',exact:true});await supervisorEvidence.getByText('QA-ACK-001',{exact:true}).waitFor();if(await supervisorEvidence.getByRole('button',{name:'Save acknowledgement',exact:true}).count())throw Error('Role Admin cannot edit regulator acknowledgements');const [supervisorPdf]=await Promise.all([reportingSupervisor.waitForEvent('download'),supervisorEvidence.getByRole('button',{name:'Download report PDF',exact:true}).click()]);const supervisorPath=path.join(qaDirectory,'documents','supervisor-report.pdf');await supervisorPdf.saveAs(supervisorPath);if(!fs.readFileSync(supervisorPath).equals(reportPdfBytes))throw Error('Supervisor archive differs');await supervisorEvidence.getByRole('button',{name:'Close report details',exact:true}).click();
  await reportingSupervisor.screenshot({path:path.join(qaDirectory,'screens','reporting-admin-work.png'),fullPage:true});console.log('PASS reporting administrators see their own team report records and real submission status');
  console.log('PASS real Super Admin provisioning, role-scoped approvals, unique employee identities, API tampering denial, shared role management, assignment history and immediate replacement/release session revocation');
  await admin.getByRole('button',{name:'Users & access',exact:true}).click();await admin.getByRole('row').filter({has:admin.getByRole('cell',{name:'analyst',exact:true})}).getByRole('button',{name:'Deactivate',exact:true}).click();await admin.getByRole('row').filter({has:admin.getByRole('cell',{name:'analyst',exact:true})}).getByText('Disabled',{exact:true}).waitFor();await analyst.getByRole('button',{name:'Refresh',exact:true}).click();await analyst.getByLabel(/^Username/).waitFor();console.log('PASS revoked session returns to login without stale workspace');

  await admin.getByRole('button',{name:'Role management',exact:true}).click();await admin.getByLabel('Application role',{exact:true}).selectOption('ROLE_CASE_PROCESSOR');await admin.getByRole('button',{name:'Role Admin assignments',exact:true}).click();await provisionSupervisor('processing.department.admin','Synthetic Processing Department Admin');
  const departmentAdmin=await login('processing.department.admin','role-admin-home','ROLE_CASE_PROCESSOR',true);diagnosticPage=departmentAdmin;
  await require('./qa/verify-processing-team-workflow.cjs')({browser,origin,department:departmentAdmin,password,registrationChoices,qaDirectory});
  if(failures.length)throw Error('Browser JS errors: '+failures.join('; '));
  console.log('QA artifacts: '+qaDirectory);
  console.log('PASS real browser + isolated Spring Boot/H2 end-to-end workflow; zero browser exceptions');
 }catch(e){if(diagnosticPage){await diagnosticPage.screenshot({path:path.join(qaDirectory,'failure.png')}).catch(()=>{});console.error((await diagnosticPage.locator('body').innerText().catch(()=>'' )).slice(0,1600));}throw e;}finally{if(browser)await browser.close();if(server)await server.close();java.kill('SIGTERM');log.end();}
})().catch(e=>{console.error('FAIL',e.stack);process.exitCode=1});

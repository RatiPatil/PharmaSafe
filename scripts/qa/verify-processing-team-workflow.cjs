// Runs against the full workflow's isolated H2 server; no production accounts/data.
const fs=require('node:fs');
const path=require('node:path');
module.exports=async function({browser,origin,department,password,registrationChoices,qaDirectory}){
 const pageErrors=[];
 async function api(page,url,method='GET',body){return page.evaluate(async({url,method,body})=>{const token=(await(await fetch('/api/auth/csrf',{credentials:'include'})).json()).token;const r=await fetch('/api/processing-teams'+url,{method,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':token},...(body?{body:JSON.stringify(body)}:{})});const value=await r.json();if(!r.ok)throw Error(method+' '+url+': '+r.status+' '+JSON.stringify(value));return value;},{url,method,body});}
 async function signIn(username,mode,home){const context=await browser.newContext({viewport:{width:1440,height:1000}});const p=await context.newPage();p.on('pageerror',e=>pageErrors.push(e.message));p.on('dialog',d=>d.accept());await p.goto(origin+'/#/access/ROLE_CASE_PROCESSOR/'+mode);await p.getByLabel('Username or official email',{exact:true}).fill(username);await p.getByLabel('Password',{exact:true}).fill(password);await p.getByRole('button',{name:'Log In to PharmaSafe',exact:true}).click();await p.getByTestId(home).waitFor();return p;}
 async function checkTaskActions(page){if(!await page.locator('.cp-task-detail').count())return;await page.locator('.cp-task-detail').evaluate(el=>el.scrollIntoView({block:'start'}));const action=await page.getByRole('button',{name:'← Back to worklist',exact:true}).boundingBox(),header=await page.locator('.ws-topbar').boundingBox();if(!action||!header||action.y<header.y+header.height-1)throw Error('Sticky header covers assignment controls');}
 const nav=(p,name)=>p.getByRole('navigation',{name:'Case Processing team navigation'}).getByRole('button',{name,exact:true});
 await department.getByRole('link',{name:'Processing teams & work',exact:true}).click();await department.getByTestId('processing-admin-home').waitFor();
 await nav(department,'Team Leads & teams').click();
 for(const [name,value]of Object.entries({'Team name':'Intake Alpha','Team Lead full name':'Synthetic Alpha Lead','Team Lead username':'team.alpha.lead','Team Lead official email':'team.alpha.lead@example.test','Initial Team Lead password':password,'Creation reason':'Synthetic team workflow verification'}))await department.getByLabel(name,{exact:true}).fill(value);
 await department.getByRole('button',{name:'Create Team Lead & team',exact:true}).click();await department.getByRole('heading',{name:'Intake Alpha',exact:true}).waitFor();
 const alpha=(await api(department,'/teams')).find(t=>t.name==='Intake Alpha');
 const beta=await api(department,'/teams','POST',{name:'Intake Beta',fullName:'Synthetic Beta Lead',username:'team.beta.lead',email:'team.beta.lead@example.test',password,reason:'Synthetic separate team boundary verification'});
 const lead=await signIn('team.alpha.lead','team-lead','processing-lead-home');
 const otherLead=await signIn('team.beta.lead','team-lead','processing-lead-home');
 const [preview]=await Promise.all([department.waitForEvent('popup'),department.getByRole('link',{name:'Open employee registration',exact:true}).first().click()]);await preview.getByRole('heading',{name:'Case Processor registration',exact:true}).waitFor();if(await preview.getByRole('navigation',{name:'Role access tabs'}).count())throw Error('Employee invite must not show login/admin tabs');await preview.close();
 async function register(username,team){
  const context=await browser.newContext({viewport:{width:1440,height:1000}}),p=await context.newPage();
  const byCode=username==='team.employee.two';
  await p.goto(origin+'/'+(byCode?'#/access/ROLE_CASE_PROCESSOR/register':team.registrationPath));
  await p.getByRole('heading',{name:'Case Processor registration',exact:true}).waitFor();
  if(byCode){await p.getByLabel('Team Lead ID',{exact:true}).fill(team.teamCode);await p.getByRole('button',{name:'Verify Team Lead',exact:true}).click();await p.getByRole('status').filter({hasText:'Selected: '+team.teamCode}).waitFor();}
  await p.getByText('Intake Alpha',{exact:false}).first().waitFor();
  for(const [name,value]of Object.entries({'Full name':username==='team.employee.one'?'Synthetic Employee One':'Synthetic Employee Two','Employee ID':username,'Official email address':username+'@example.test','Mobile number':'+15551234567','Organization':'Synthetic PV Organization','Username':username,'Password':password,'Confirm password':password,'Reason for access':'Synthetic team registration verification'}))await p.getByLabel(name,{exact:true}).fill(value);
  for(const select of await p.locator('.pa-registration select').all()){await select.selectOption({index:1});}
  const response=p.waitForResponse(r=>r.url().endsWith('/api/auth/register')&&r.request().method()==='POST');
  await p.getByRole('button',{name:'Submit access request',exact:true}).click();
  const r=await response;if(r.status()!==201)throw Error('Employee signup failed: '+r.status()+' '+await r.text());
  if(r.request().postDataJSON().teamRegistrationKey!==team.registrationPath.split('team=')[1])throw Error('Employee signup lost its selected team');
  await p.getByRole('heading',{name:'Request submitted',exact:true}).waitFor();const result=await r.json();await context.close();return result;
 }
 await register('team.employee.one',alpha);await register('team.employee.two',alpha);
 await nav(lead,'Registration requests').click();await lead.getByRole('button',{name:'Refresh team data',exact:true}).click();
 async function approve(page,username){const article=page.locator('.cp-registration-list article').filter({hasText:'@'+username});await article.getByRole('button',{name:'Approve employee',exact:true}).click();const dialog=page.getByRole('dialog');await dialog.getByLabel('Decision reason',{exact:true}).fill('Synthetic employee source training verified');await dialog.getByRole('button',{name:'Confirm decision',exact:true}).click();await dialog.waitFor({state:'hidden'});await article.getByText('APPROVED',{exact:true}).waitFor();}
 await approve(lead,'team.employee.one');await nav(department,'Registration requests').click();await approve(department,'team.employee.two');
 console.log('PASS team signup from real public link and approvals by Lead and department Admin');
 const members=await api(lead,`/teams/${alpha.id}/members`);const one=members.content.find(m=>m.username==='team.employee.one');
 await nav(lead,'Assign work').click();
 for(const [name,value]of Object.entries({'Task title':'Verify initial case facts','Employee staff ID':one.staffCode,'Task due date and time':new Date(Date.now()+86400000).toISOString().slice(0,16),'Assigned work description':'Verify patient, reporter, suspected drug and adverse event against the source.','Instructions and expected evidence':'Record evidence and remaining work without changing clinical approvals.'}))await lead.getByLabel(name,{exact:true}).fill(value);
 await lead.getByLabel('Case Processing stage',{exact:true}).selectOption('VALIDATION');await lead.getByRole('button',{name:'Assign work & generate PDFs',exact:true}).click();
 await lead.getByRole('row').filter({hasText:'Verify initial case facts'}).getByRole('button',{name:'Open assignment',exact:true}).click();await lead.getByRole('heading',{name:'Verify initial case facts',exact:true}).waitFor();
 console.log('PASS staff-ID assignment through the Team Lead form');
 const assigned=(await api(lead,'/work')).content.find(w=>w.title==='Verify initial case facts');
 const employee=await signIn('team.employee.one','user','processing-employee-home');
 await nav(employee,'My work & PDFs').click();await employee.getByRole('row').filter({hasText:'Verify initial case facts'}).getByRole('button',{name:'Open assignment',exact:true}).click();await employee.getByRole('heading',{name:'Submit my progress',exact:true}).waitFor();await checkTaskActions(employee);
 fs.mkdirSync(path.join(qaDirectory,'documents'),{recursive:true});async function pdf(page,label,file){const [d]=await Promise.all([page.waitForEvent('download'),page.getByRole('button',{name:label,exact:true}).first().click()]);const target=path.join(qaDirectory,'documents',file);await d.saveAs(target);const bytes=fs.readFileSync(target);if(bytes.subarray(0,5).toString()!=='%PDF-')throw Error('Invalid '+file);return bytes;}
 await pdf(employee,'Download assignment PDF','team-assignment.pdf');
 await employee.getByLabel('Progress status',{exact:true}).selectOption('COMPLETED');await employee.getByLabel('Work completed',{exact:true}).fill('Confirmed the four minimum criteria against synthetic source evidence.');await employee.getByLabel('Update or correction reason',{exact:true}).fill('Synthetic source verification completed');
 for(const name of ['Identifiable patient confirmed','Identifiable reporter confirmed','Suspected drug confirmed','Adverse event confirmed'])await employee.getByLabel(name,{exact:true}).check();
 await employee.getByRole('button',{name:'Submit progress & generate response PDF',exact:true}).click();await employee.getByText('Task completed',{exact:true}).waitFor();
 const employeePdf=await pdf(employee,'Download response PDF','team-response-employee.pdf');await lead.getByRole('button',{name:'Refresh task',exact:true}).click();await lead.getByText('Task completed',{exact:true}).waitFor();const leadPdf=await pdf(lead,'Download response PDF','team-response-lead.pdf');if(!employeePdf.equals(leadPdf))throw Error('Response PDF changed between employee and Lead');
 await api(lead,'/work','POST',{teamId:alpha.id,title:'Common duplicate-review checklist',description:'Review the common duplicate screening checklist.',instructions:'Use the existing case duplicate check.',stage:'DUPLICATE_REVIEW',priority:'Routine',dueAt:new Date(Date.now()+86400000).toISOString(),recipientMode:'ALL_TEAM'});
 const work=(await api(department,'/work')).content;if(work.filter(w=>w.title==='Common duplicate-review checklist').length!==2)throw Error('Broadcast must create two independent assignments');
 const denial=await otherLead.evaluate(async({teamId,workId})=>Promise.all([fetch('/api/processing-teams/overview?teamId='+teamId),fetch('/api/processing-teams/work/'+workId)]).then(rs=>rs.map(r=>r.status)),{teamId:alpha.id,workId:assigned.id});if(denial.some(s=>s!==403))throw Error('Cross-team read must fail '+denial);
 const second=await signIn('team.employee.two','user','processing-employee-home');const privateStatus=await second.evaluate(async(id)=>(await fetch('/api/processing-teams/work/'+id)).status,assigned.id);if(privateStatus!==403)throw Error('Employee accessed colleague task');
 await nav(department,'Dashboard').click();await department.getByRole('button',{name:'Refresh team data',exact:true}).click();await department.getByLabel('Team dashboard',{exact:true}).selectOption(String(alpha.id));await department.getByRole('img',{name:'3 scoped work assignments',exact:true}).waitFor();
 for(const [name,p]of [['processing-admin',department],['processing-lead',lead],['processing-employee',employee]])for(const width of [1440,390]){await p.setViewportSize({width,height:900});await checkTaskActions(p);if(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Processing page overflow '+name+' '+width);await p.screenshot({path:path.join(qaDirectory,'screens',name+'-'+width+'.png'),fullPage:true});}
 if(pageErrors.length)throw Error('Processing browser errors: '+pageErrors.join('; '));
 console.log('PASS Case Processing hierarchy: real Lead provisioning, team invitation, Lead/Admin approvals, employee-ID assignment, independent broadcast, completion checklist, shared immutable PDFs, cross-team/employee denials and mobile screens');
 for(const p of [lead,otherLead,employee,second])await p.context().close();
};

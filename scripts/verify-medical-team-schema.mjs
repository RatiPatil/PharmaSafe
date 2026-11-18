// Disposable PostgreSQL-compatible verification. No deployed database connection.
import {readFileSync,readdirSync} from 'node:fs';
import {resolve,join,dirname} from 'node:path';
import {pathToFileURL,fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';
const {PGlite}=await import(pathToFileURL(resolve(process.argv[2],'node_modules/@electric-sql/pglite/dist/index.js')).href);
const db=new PGlite();
const directory=join(dirname(fileURLToPath(import.meta.url)),'../backend/src/main/resources/db/migration');
try{
 for(const f of readdirSync(directory).filter(f=>/^V\d+__.*\.sql$/.test(f)).sort((a,b)=>Number(a.match(/^V(\d+)/)[1])-Number(b.match(/^V(\d+)/)[1])))await db.exec(readFileSync(join(directory,f),'utf8'));
 await db.exec("INSERT INTO users(username,password_hash,role) VALUES('mr-root','hash','ROLE_ADMIN'),('mr-lead-a','hash','ROLE_MEDICAL_TEAM_LEAD'),('mr-lead-b','hash','ROLE_MEDICAL_TEAM_LEAD')");
 for(let i=0;i<13;i++)await db.exec(`INSERT INTO users(username,password_hash,role) VALUES('mr-employee-${i}','hash','ROLE_MEDICAL_REVIEWER')`);
 const teamInsert=(name,lead,key)=>`INSERT INTO mr_teams(name,lead_user_id,registration_key,created_by_user_id,created_at) SELECT '${name}',l.id,'${key}',a.id,CURRENT_TIMESTAMP FROM users l CROSS JOIN users a WHERE l.username='${lead}' AND a.username='mr-root'`;
 await db.exec(teamInsert('Team A','mr-lead-a','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'));await db.exec(teamInsert('Team B','mr-lead-b','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'));
 const teamA=(await db.query("SELECT id FROM mr_teams WHERE name='Team A'")).rows[0].id,teamB=(await db.query("SELECT id FROM mr_teams WHERE name='Team B'")).rows[0].id;
 const uid=async name=>(await db.query('SELECT id FROM users WHERE username=$1',[name])).rows[0].id;
 const u0=await uid('mr-employee-0'),u1=await uid('mr-employee-1');
 await db.exec(`INSERT INTO mr_team_members(team_id,user_id,status,joined_at) VALUES(${teamA},${u0},'ACTIVE',CURRENT_TIMESTAMP),(${teamB},${u1},'ACTIVE',CURRENT_TIMESTAMP)`);
 await assert.rejects(db.exec(`INSERT INTO mr_team_members(team_id,user_id,status,joined_at) VALUES(${teamB},${u0},'ACTIVE',CURRENT_TIMESTAMP)`),e=>e.code==='23505');
 await assert.rejects(db.exec(`INSERT INTO mr_team_members(team_id,user_id,status,joined_at) VALUES(${teamA},${await uid('mr-lead-a')},'ACTIVE',CURRENT_TIMESTAMP)`),e=>e.code==='23514');
 await assert.rejects(db.exec("UPDATE users SET role='ROLE_ADMIN' WHERE username='mr-lead-a'"),e=>e.code==='23514');
 await assert.rejects(db.exec("UPDATE users SET role='ROLE_CASE_PROCESSOR' WHERE username='mr-employee-0'"),e=>e.code==='23514');
 const order=(team,lead)=>`INSERT INTO mr_work_orders(team_id,title,description,instructions,clinical_required,stage,priority,due_at,recipient_mode,created_by_user_id,created_at) VALUES(${team},'Synthetic work','Synthetic medical instructions','',FALSE,'SOURCE_REVIEW','Routine',CURRENT_TIMESTAMP+INTERVAL '1 day','INDIVIDUAL',${lead},CURRENT_TIMESTAMP)`;
 await db.exec(order(teamA,await uid('mr-lead-a')));await db.exec(order(teamB,await uid('mr-lead-b')));
 const orderA=(await db.query(`SELECT id FROM mr_work_orders WHERE team_id=${teamA}`)).rows[0].id,orderB=(await db.query(`SELECT id FROM mr_work_orders WHERE team_id=${teamB}`)).rows[0].id;
 const assignment=(o,t,u)=>`INSERT INTO mr_work_assignments(work_order_id,team_id,employee_user_id,created_at,updated_at) VALUES(${o},${t},${u},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)`;
 await assert.rejects(db.exec(assignment(orderA,teamB,u1)),e=>e.code==='23503');
 await assert.rejects(db.exec(assignment(orderA,teamA,u1)),e=>e.code==='23514'||e.code==='23503');
 await db.exec(assignment(orderA,teamA,u0));await db.exec(assignment(orderB,teamB,u1));
 const a=(await db.query(`SELECT id FROM mr_work_assignments WHERE team_id=${teamA}`)).rows[0].id,b=(await db.query(`SELECT id FROM mr_work_assignments WHERE team_id=${teamB}`)).rows[0].id;
 await assert.rejects(db.exec(`UPDATE mr_work_assignments SET employee_user_id=${u1},team_id=${teamB},work_order_id=${orderB} WHERE id=${a}`),e=>e.code==='23514');
 await assert.rejects(db.exec(`UPDATE mr_work_assignments SET progress=100,status='IN_PROGRESS' WHERE id=${a}`),e=>e.code==='23514');
 const update=(id,employee)=>`INSERT INTO mr_work_updates(assignment_id,employee_user_id,status,progress,work_done,pending_work,remaining_work,blockers,reason,checklist_json,evidence_reference,source_assignment_version,recorded_at) VALUES(${id},${employee},'IN_PROGRESS',20,'Synthetic work done','Source pending','Verify source','','Initial progress','{}'::jsonb,'',0,CURRENT_TIMESTAMP)`;
 await db.exec(update(a,u0));await db.exec(update(b,u1));
 await assert.rejects(db.exec(update(a,u0)),e=>e.code==='23505');
 const updateA=(await db.query(`SELECT id FROM mr_work_updates WHERE assignment_id=${a}`)).rows[0].id,updateB=(await db.query(`SELECT id FROM mr_work_updates WHERE assignment_id=${b}`)).rows[0].id;
 const doc=(id,assignmentId,updateId,kind)=>`INSERT INTO mr_work_documents(id,assignment_id,update_id,document_kind,generated_by_user_id,generated_at,snapshot_json,pdf_bytes,pdf_sha256,file_name) VALUES('${id}',${assignmentId},${updateId},'${kind}',${u0},CURRENT_TIMESTAMP,'{}'::jsonb,decode('25504446','hex'),repeat('a',64),'task.pdf')`;
 await db.exec(doc('original-assignment',a,'NULL','ASSIGNMENT'));await db.exec(doc('employee-response',a,updateA,'EMPLOYEE_RESPONSE'));
 await assert.rejects(db.exec(doc('foreign-response',a,updateB,'EMPLOYEE_RESPONSE')),e=>e.code==='23503'||e.code==='23505');
 for(const table of ['mr_work_orders','mr_work_updates','mr_work_documents'])await assert.rejects(db.exec(`DELETE FROM ${table}`),e=>e.code==='23514');
 await assert.rejects(db.exec("UPDATE mr_work_documents SET file_name='changed.pdf'"),e=>e.code==='23514');
 // Medical-specific allocation and immutable clinical evidence, independent of Case Processing.
 await db.exec("INSERT INTO users(username,password_hash,role) VALUES('wrong-department','hash','ROLE_CASE_PROCESSOR')");
 await assert.rejects(db.exec(`INSERT INTO mr_team_members(team_id,user_id,status,joined_at) VALUES(${teamA},${await uid('wrong-department')},'ACTIVE',CURRENT_TIMESTAMP)`),e=>e.code==='23514');
 await db.exec(`INSERT INTO cases(case_number,country_code,report_type,initial_receipt_date,latest_received_date,assigned_to_user_id,workflow_state) VALUES('MR-CLINICAL','USA','Spontaneous',CURRENT_DATE,CURRENT_DATE,${u0},'Medical Review')`);
 const clinical=(await db.query("SELECT id FROM cases WHERE case_number='MR-CLINICAL'")).rows[0].id;
 const allocation=actor=>`INSERT INTO mr_case_allocations(case_id,team_id,allocated_by,return_processor_id,reason,allocated_at) VALUES(${clinical},${teamA},${actor},${awaitedProcessor},'Synthetic clinical allocation',CURRENT_TIMESTAMP)`;
 const awaitedProcessor=await uid('wrong-department');
 await assert.rejects(db.exec(allocation(awaitedProcessor)),e=>e.code==='23514');await db.exec(allocation(await uid('mr-root')));
 await assert.rejects(db.exec(`INSERT INTO mr_work_orders(team_id,title,description,instructions,stage,priority,due_at,recipient_mode,created_by_user_id,created_at,case_id) VALUES(${teamB},'Wrong allocation','Clinical review','','QUALITY_HANDOFF','Routine',CURRENT_TIMESTAMP+INTERVAL '1 day','INDIVIDUAL',${await uid('mr-lead-b')},CURRENT_TIMESTAMP,${clinical})`),e=>e.code==='23514');
 await db.exec(`INSERT INTO mr_work_orders(team_id,title,description,instructions,stage,priority,due_at,recipient_mode,created_by_user_id,created_at,case_id) VALUES(${teamA},'Clinical allocation','Clinical review','','QUALITY_HANDOFF','Routine',CURRENT_TIMESTAMP+INTERVAL '1 day','INDIVIDUAL',${await uid('mr-lead-a')},CURRENT_TIMESTAMP,${clinical})`);
 const clinicalOrder=(await db.query("SELECT id FROM mr_work_orders WHERE title='Clinical allocation'")).rows[0].id;
 await db.exec(assignment(clinicalOrder,teamA,u0));const clinicalAssignment=(await db.query(`SELECT id FROM mr_work_assignments WHERE work_order_id=${clinicalOrder}`)).rows[0].id;
 const conclusion=(employee=u0,revision=0)=>`INSERT INTO mr_review_conclusions(assignment_id,case_id,employee_id,source_case_version,medical_narrative,review_summary,assessed_seriousness,severity_summary,source_reviewed,timeline_reviewed,recorded_at) VALUES(${clinicalAssignment},${clinical},${employee},${revision},'Synthetic medical narrative','Synthetic clinical conclusion','NON_SERIOUS','Mild intensity',TRUE,TRUE,CURRENT_TIMESTAMP)`;
 await assert.rejects(db.exec(conclusion(u1)),e=>e.code==='23514'||e.code==='23503');await assert.rejects(db.exec(conclusion(u0,1)),e=>e.code==='23514');await db.exec(conclusion());
 await assert.rejects(db.exec("UPDATE mr_review_conclusions SET medical_narrative='Changed source'"),e=>e.code==='23514');await assert.rejects(db.exec('DELETE FROM mr_review_conclusions'),e=>e.code==='23514');
 await assert.rejects(db.exec(`UPDATE mr_case_allocations SET reason='Change allocation with outstanding work' WHERE case_id=${clinical}`),e=>e.code==='23514');
 console.log('PASS Medical Review: separate department membership, allocation scope, current owned clinical conclusions, revision checks and immutable medical evidence');
 for(let i=2;i<=10;i++)await db.exec(`INSERT INTO mr_team_members(team_id,user_id,status,joined_at) VALUES(${teamA},${await uid('mr-employee-'+i)},'ACTIVE',CURRENT_TIMESTAMP)`);
 await assert.rejects(db.exec(`INSERT INTO mr_team_members(team_id,user_id,status,joined_at) VALUES(${teamA},${await uid('mr-employee-11')},'ACTIVE',CURRENT_TIMESTAMP)`),e=>e.code==='23514');
 await db.exec(`UPDATE users SET enabled=FALSE WHERE id=${u0}`);assert.equal((await db.query(`SELECT status FROM mr_team_members WHERE user_id=${u0}`)).rows[0].status,'SUSPENDED');
 await db.exec(`INSERT INTO mr_team_members(team_id,user_id,status,joined_at) VALUES(${teamA},${await uid('mr-employee-11')},'ACTIVE',CURRENT_TIMESTAMP)`);
 await assert.rejects(db.exec(`UPDATE users SET enabled=TRUE WHERE id=${u0}`),e=>e.code==='23514');
 for(let i=2;i<10;i++){await db.exec(`INSERT INTO users(username,password_hash,role) VALUES('mr-extra-lead-${i}','hash','ROLE_MEDICAL_TEAM_LEAD')`);await db.exec(teamInsert('Extra Team '+i,'mr-extra-lead-'+i,`00000000-0000-0000-0000-${String(i).padStart(12,'0')}`));}
 await db.exec("INSERT INTO users(username,password_hash,role) VALUES('mr-overflow-lead','hash','ROLE_MEDICAL_TEAM_LEAD')");await assert.rejects(db.exec(teamInsert('Overflow Team','mr-overflow-lead','eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee')),e=>e.code==='23514');
 await db.exec("INSERT INTO administrative_events(actor_username,actor_level,managed_role,action,reason,outcome,created_at) VALUES('mr-lead-a','TEAM_LEAD','ROLE_MEDICAL_REVIEWER','REGISTRATION_APPROVED','Synthetic verification','SUCCESS',CURRENT_TIMESTAMP)");

 console.log('PASS medical hierarchy: ten-team/ten-seat limits, exclusive active membership, account-state synchronization, fixed assignment ownership, composite team/employee/document FKs, append-only evidence and Team Lead audit identity');
}finally{await db.close()}

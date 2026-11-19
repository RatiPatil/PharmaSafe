// Disposable PostgreSQL-compatible verification; never connects to live data.
import {readFileSync,readdirSync} from 'node:fs';
import {resolve,join,dirname} from 'node:path';
import {pathToFileURL,fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';
const {PGlite}=await import(pathToFileURL(resolve(process.argv[2],'node_modules/@electric-sql/pglite/dist/index.js')).href);
const db=new PGlite(),directory=join(dirname(fileURLToPath(import.meta.url)),'../backend/src/main/resources/db/migration');
try{
 for(const f of readdirSync(directory).filter(f=>/^V\d+__.*\.sql$/.test(f)).sort((a,b)=>Number(a.match(/^V(\d+)/)[1])-Number(b.match(/^V(\d+)/)[1]))){
  if(f.startsWith('V22__'))await db.exec("INSERT INTO users(username,password_hash,role) VALUES('legacy-domain-a','hash','ROLE_DOMAIN_ADMIN'),('legacy-domain-b','hash','ROLE_DOMAIN_ADMIN'); INSERT INTO edu_domain_admins(user_id,domain,active,assigned_at) SELECT id,CASE WHEN username='legacy-domain-a' THEN 'ROLE_CASE_PROCESSOR' ELSE 'ROLE_MEDICAL_REVIEWER' END,TRUE,CURRENT_TIMESTAMP FROM users WHERE username IN ('legacy-domain-a','legacy-domain-b') ORDER BY id");
  await db.exec(readFileSync(join(directory,f),'utf8'));
  if(f.startsWith('V22__')){
   const migrated=(await db.query('SELECT a.domain,a.active,u.username FROM edu_domain_admins a JOIN users u ON u.id=a.user_id ORDER BY a.id')).rows;
   assert.equal(migrated.length,2);assert.equal(migrated[0].active,true);assert.equal(migrated[1].active,false);assert.ok(migrated.every(g=>g.domain===null));
   await assert.rejects(db.exec('UPDATE edu_domain_admins SET active=TRUE WHERE active=FALSE'),e=>e.code==='23505');
   await db.exec('UPDATE edu_domain_admins SET active=FALSE');
  }
 }
 await db.exec("INSERT INTO users(username,password_hash,role) VALUES('edu-root','hash','ROLE_ADMIN'),('edu-alpha','hash','ROLE_COLLEGE_ADMIN'),('edu-beta','hash','ROLE_COLLEGE_ADMIN'),('edu-domain','hash','ROLE_DOMAIN_ADMIN'),('edu-student','hash','ROLE_STUDENT'),('edu-other','hash','ROLE_STUDENT'),('edu-clinical','hash','ROLE_CASE_PROCESSOR')");
 const uid=async n=>(await db.query('SELECT id FROM users WHERE username=$1',[n])).rows[0].id;
 await db.exec("INSERT INTO edu_colleges(code,name,active,created_at) VALUES('SYN-A','Synthetic Alpha',TRUE,CURRENT_TIMESTAMP),('SYN-B','Synthetic Beta',TRUE,CURRENT_TIMESTAMP)");
 const ca=(await db.query("SELECT id FROM edu_colleges WHERE code='SYN-A'")).rows[0].id,cb=(await db.query("SELECT id FROM edu_colleges WHERE code='SYN-B'")).rows[0].id;
 await db.exec(`INSERT INTO edu_college_admins(user_id,college_id,active,assigned_at) VALUES(${await uid('edu-alpha')},${ca},TRUE,CURRENT_TIMESTAMP),(${await uid('edu-beta')},${cb},TRUE,CURRENT_TIMESTAMP)`);
 await assert.rejects(db.exec(`INSERT INTO edu_college_admins(user_id,college_id,active,assigned_at) VALUES(${await uid('edu-root')},${ca},TRUE,CURRENT_TIMESTAMP)`),e=>e.code==='23514');
 await db.exec(`INSERT INTO edu_domain_admins(user_id,domain,active,assigned_at) VALUES(${await uid('edu-domain')},NULL,TRUE,CURRENT_TIMESTAMP)`);
 await assert.rejects(db.exec(`INSERT INTO edu_domain_admins(user_id,domain,active,assigned_at) VALUES(${await uid('legacy-domain-b')},NULL,TRUE,CURRENT_TIMESTAMP)`),e=>e.code==='23505');
 await db.exec(`INSERT INTO edu_student_id_reservations(college_id,token_hash,expires_at) VALUES(${ca},repeat('a',64),CURRENT_TIMESTAMP+INTERVAL '1 hour'),(${cb},repeat('b',64),CURRENT_TIMESTAMP+INTERVAL '1 hour')`);
 const reservations=(await db.query('SELECT id,college_id,student_code FROM edu_student_id_reservations ORDER BY id')).rows;
 const student=(u,r,c)=>`INSERT INTO edu_students(user_id,college_id,reservation_id,student_code,created_at) VALUES(${u},${c},${r.id},'${r.student_code}',CURRENT_TIMESTAMP)`;
 await assert.rejects(db.exec(student(await uid('edu-clinical'),reservations[0],ca)),e=>e.code==='23514');
 await assert.rejects(db.exec(student(await uid('edu-student'),reservations[0],cb)),e=>e.code==='23514'||e.code==='23503');
 await db.exec('BEGIN;'+student(await uid('edu-student'),reservations[0],ca)+';'+student(await uid('edu-other'),reservations[1],cb)+";INSERT INTO edu_student_domains(student_id,domain,status,requested_at) SELECT s.id,d.domain,'PENDING',CURRENT_TIMESTAMP FROM edu_students s CROSS JOIN (SELECT DISTINCT domain FROM edu_domain_stages)d;COMMIT;");
 const studentId=(await db.query(`SELECT id FROM edu_students WHERE college_id=${ca}`)).rows[0].id,otherId=(await db.query(`SELECT id FROM edu_students WHERE college_id=${cb}`)).rows[0].id;
 await assert.rejects(db.exec(`UPDATE edu_students SET college_id=${cb} WHERE id=${studentId}`),e=>e.code==='23514');

 await assert.rejects(db.exec(`UPDATE edu_student_domains SET status='APPROVED',reviewer_id=${await uid('edu-beta')},reason='Wrong college',reviewed_at=CURRENT_TIMESTAMP WHERE student_id=${studentId}`),e=>e.code==='23514');
 await assert.rejects(db.exec(`UPDATE edu_student_domains SET status='APPROVED',reviewer_id=${await uid('edu-clinical')},reason='Clinical user cannot approve',reviewed_at=CURRENT_TIMESTAMP WHERE student_id=${studentId}`),e=>e.code==='23514');
 const task=(student=studentId,college=ca,actor,stage='Case intake')=>`INSERT INTO edu_tasks(college_id,student_id,assigned_by,domain,stage,title,description,instructions,due_at,status,progress,created_at,updated_at) VALUES(${college},${student},${actor},'ROLE_CASE_PROCESSOR','${stage}','Synthetic assignment','Source review exercise','Verify source evidence',CURRENT_TIMESTAMP+INTERVAL '1 day','NOT_STARTED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)`;
 await assert.rejects(db.exec(task(studentId,ca,await uid('edu-alpha'))),e=>e.code==='23514');
 await db.exec(`UPDATE edu_student_domains SET status='APPROVED',reviewer_id=${await uid('edu-root')},reason='Enrollment verified',reviewed_at=CURRENT_TIMESTAMP`);
 await assert.rejects(db.exec(task(studentId,cb,await uid('edu-beta'))),e=>e.code==='23514'||e.code==='23503');
 await assert.rejects(db.exec(task(studentId,ca,await uid('edu-beta'))),e=>e.code==='23514');
 await assert.rejects(db.exec(task(studentId,ca,await uid('edu-alpha'),'Causality assessment')),e=>e.code==='23503');
 await db.exec(task(studentId,ca,await uid('edu-alpha')));const tid=(await db.query('SELECT id FROM edu_tasks')).rows[0].id;
 await assert.rejects(db.exec(`UPDATE edu_tasks SET student_id=${otherId},college_id=${cb} WHERE id=${tid}`),e=>e.code==='23514');
 const submission=(student=studentId,version=0)=>`INSERT INTO edu_task_submissions(task_id,student_id,source_task_version,status,progress,work_done,pending_work,remaining_work,evidence_json,created_at) VALUES(${tid},${student},${version},'SUBMITTED',100,'Synthetic source reviewed','','','{}'::jsonb,CURRENT_TIMESTAMP)`;
 await assert.rejects(db.exec(submission(otherId)),e=>e.code==='23514'||e.code==='23503');await assert.rejects(db.exec(submission(studentId,1)),e=>e.code==='23514');await db.exec(submission());await assert.rejects(db.exec(submission()),e=>e.code==='23505');
 const sid=(await db.query('SELECT id FROM edu_task_submissions')).rows[0].id;
 await db.exec(`UPDATE edu_tasks SET status='SUBMITTED',progress=100,row_version=1 WHERE id=${tid}`);
 const feedback=actor=>`INSERT INTO edu_task_feedback(task_id,submission_id,reviewer_id,outcome,feedback,created_at) VALUES(${tid},${sid},${actor},'COMPLETED','Synthetic evidence accepted',CURRENT_TIMESTAMP)`;
 await assert.rejects(db.exec(feedback(await uid('edu-beta'))),e=>e.code==='23514');await db.exec(feedback(await uid('edu-alpha')));
 await db.exec(`INSERT INTO edu_submission_documents(submission_id,file_name,file_bytes,sha256,created_at) VALUES(${sid},'source.pdf',decode('255044462d','hex'),repeat('a',64),CURRENT_TIMESTAMP)`);
 const report=(actor,college=ca,student=studentId)=>`INSERT INTO edu_progress_reports(generated_by,college_id,student_id,domain,snapshot_json,pdf_bytes,sha256,file_name,created_at) VALUES(${actor},${college},${student},'ROLE_CASE_PROCESSOR','{}'::jsonb,decode('255044462d','hex'),repeat('b',64),'progress.pdf',CURRENT_TIMESTAMP)`;
 await assert.rejects(db.exec(report(await uid('edu-beta'))),e=>e.code==='23514');await assert.rejects(db.exec(report(await uid('edu-other'))),e=>e.code==='23514');await db.exec(report(await uid('edu-student')));await db.exec(report(await uid('edu-domain')).replace("'ROLE_CASE_PROCESSOR'","'ROLE_MEDICAL_REVIEWER'"));
 await assert.rejects(db.exec(`DELETE FROM edu_student_domains WHERE student_id=${studentId} AND domain='ROLE_MEDICAL_REVIEWER'`),e=>e.code==='23514');
 await db.exec("INSERT INTO users(username,password_hash,role) VALUES('partial-student','hash','ROLE_STUDENT');INSERT INTO edu_student_id_reservations(college_id,token_hash,expires_at) VALUES("+ca+",repeat('c',64),CURRENT_TIMESTAMP+INTERVAL '1 hour')");
 const partial=(await db.query("SELECT id,student_code FROM edu_student_id_reservations WHERE token_hash=repeat('c',64)")).rows[0];
 await assert.rejects(db.exec(student(await uid('partial-student'),partial,ca)),e=>e.code==='23514');
 const order=actor=>`INSERT INTO edu_work_orders(college_id,created_by,domain,stage,title,description,instructions,due_at,created_at) VALUES(${ca},${actor},'ROLE_CASE_PROCESSOR','Case intake','Synthetic assignment','Source review exercise','Verify source evidence',CURRENT_TIMESTAMP+INTERVAL '2 days',CURRENT_TIMESTAMP)`;
 await assert.rejects(db.exec(order(await uid('edu-alpha'))),e=>e.code==='23514');await db.exec(order(await uid('edu-domain')));
 const oid=(await db.query('SELECT id FROM edu_work_orders')).rows[0].id;
 const linked=task(studentId,ca,await uid('edu-alpha')).replace('college_id,student_id,assigned_by','source_order_id,college_id,student_id,assigned_by').replace('VALUES(',`VALUES(${oid},`);
 await db.exec(linked);
 await assert.rejects(db.exec(`UPDATE edu_work_orders SET instructions='Changed instructions',last_changed_by=${await uid('edu-domain')} WHERE id=${oid}`),e=>e.code==='23514');
 await db.exec(`INSERT INTO edu_management_changes(actor_id,record_kind,record_id,before_json,after_json,reason,created_at) VALUES(${await uid('edu-domain')},'TASK',${tid},'{}','{}','Verified change',CURRENT_TIMESTAMP)`);
 for(const table of ['edu_work_orders','edu_tasks','edu_students','edu_colleges','edu_management_changes'])await assert.rejects(db.exec(`DELETE FROM ${table}`),e=>e.code==='23514');
 for(const table of ['edu_task_submissions','edu_task_feedback','edu_submission_documents','edu_progress_reports'])await assert.rejects(db.exec(`DELETE FROM ${table}`),e=>e.code==='23514');
 await assert.rejects(db.exec("UPDATE users SET role='ROLE_CASE_PROCESSOR' WHERE username='edu-student'"),e=>e.code==='23514');
 console.log('PASS V23 mandatory seven-domain enrollment and single active all-domain admin upgrade and college workflow schema: college-bound unique Student IDs, fixed enrollment and task recipients, seven-domain stage integrity, scoped approvals/assignment/report authors, current revisions, composite FKs and immutable submissions/feedback/documents/reports');
}finally{await db.close();}

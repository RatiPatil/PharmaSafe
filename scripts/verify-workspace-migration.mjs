// Disposable PostgreSQL-compatible migration test. Never connects to a deployed database.
// Install @electric-sql/pglite in a temporary directory and pass that directory as argv[2].
import { readFileSync, readdirSync } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import assert from 'node:assert/strict';
const dependencyDirectory = process.argv[2];
if (!dependencyDirectory) throw new Error('Usage: node scripts/verify-workspace-migration.mjs <temporary npm directory containing @electric-sql/pglite>');
const { PGlite } = await import(pathToFileURL(resolve(dependencyDirectory, 'node_modules/@electric-sql/pglite/dist/index.js')).href);
const directory = join(dirname(fileURLToPath(import.meta.url)), '../backend/src/main/resources/db/migration');
for (const populated of [false, true]) {
 const db = new PGlite();
 try {
  for (const file of readdirSync(directory).filter(f => /^V\d+__.*\.sql$/.test(f)).sort((a,b)=>Number(a.match(/^V(\d+)/)[1])-Number(b.match(/^V(\d+)/)[1]))) {
   if (populated && file.startsWith('V7__')) {
    await db.exec(`INSERT INTO users(username,password_hash,role) VALUES ('historical','hash','ROLE_CUSTOM'),('legacy','hash','ROLE_USER');
     INSERT INTO cases(case_number,country_code,report_type,initial_receipt_date,latest_received_date,created_by_user_id)
       SELECT 'PRESERVED-CASE','USA','Spontaneous',CURRENT_DATE,CURRENT_DATE,id FROM users WHERE username='legacy';
     INSERT INTO case_patients(case_id,patient_identifier) SELECT id,'SYNTHETIC-PATIENT' FROM cases WHERE case_number='PRESERVED-CASE';`);
   }
   if(populated && file.startsWith('V8__')) await db.exec("INSERT INTO case_review_events(case_id,reviewer_username,from_state,to_state,outcome,comments) SELECT id,'legacy','Quality Review','Approved','APPROVED','Historical review retained' FROM cases WHERE case_number='PRESERVED-CASE'");
   await db.exec(readFileSync(join(directory,file),'utf8'));
  }
  assert.equal((await db.query('SELECT count(*)::int AS n FROM workspace_roles WHERE assignable')).rows[0].n,6);
  if (populated) {
   assert.equal((await db.query('SELECT count(*)::int AS n FROM users')).rows[0].n,2);
   assert.equal((await db.query("SELECT p.patient_identifier,c.row_version FROM cases c JOIN case_patients p ON p.case_id=c.id WHERE c.case_number='PRESERVED-CASE'")).rows[0].patient_identifier,'SYNTHETIC-PATIENT');
   assert.equal((await db.query("SELECT role FROM users WHERE username='legacy'")).rows[0].role,'ROLE_USER');
  }
  if(populated) {
   assert.equal((await db.query("SELECT auth_version::int AS version FROM users WHERE username='legacy'")).rows[0].version,0);
   assert.equal((await db.query("SELECT patient_reviewed FROM case_review_events WHERE comments='Historical review retained'")).rows[0].patient_reviewed,false);
  }
  if(populated) await assert.rejects(db.exec("INSERT INTO case_review_events(case_id,reviewer_username,from_state,to_state,outcome,comments) SELECT id,'qc','Quality Review','Approved','APPROVED','Missing evidence' FROM cases WHERE case_number='PRESERVED-CASE'"),e=>e.code==='23514');
  await assert.rejects(db.exec("INSERT INTO users(username,password_hash,role) VALUES ('invalid','hash','ROLE_TYPO')"),e=>e.code==='23503');
  await db.exec(`INSERT INTO users(username,password_hash,role) VALUES ('receipt-actor','hash','ROLE_ADMIN');
   INSERT INTO cases(case_number,country_code,report_type,initial_receipt_date,latest_received_date) VALUES ('RECEIPT-A','USA','Spontaneous',CURRENT_DATE,CURRENT_DATE),('RECEIPT-B','USA','Spontaneous',CURRENT_DATE,CURRENT_DATE);
   INSERT INTO case_action_items(case_id,description,assigned_user_id,due_at,created_by_user_id,action_code) SELECT c.id,'Follow up',u.id,CURRENT_TIMESTAMP,u.id,'FOLLOW_UP' FROM cases c CROSS JOIN users u WHERE c.case_number='RECEIPT-B' AND u.username='receipt-actor';`);
  const insert = `INSERT INTO case_follow_ups(case_id,follow_up_number,kind,received_date,justification,summary,previous_case_version,case_version,created_by_user_id,created_by_username) SELECT c.id,1,'FOLLOW_UP',CURRENT_DATE,'New information','Synthetic receipt',1,2,u.id,u.username FROM cases c CROSS JOIN users u WHERE c.case_number='RECEIPT-A' AND u.username='receipt-actor'`;
  await db.exec(insert);
  await assert.rejects(db.exec(insert),e=>e.code==='23505');
  await assert.rejects(db.exec(`UPDATE case_follow_ups SET related_action_item_id=(SELECT id FROM case_action_items WHERE description='Follow up')`),e=>e.code==='23503');
  await assert.rejects(db.exec(`UPDATE case_follow_ups SET kind='AMENDMENT',safety_received_date=CURRENT_DATE`),e=>e.code==='23514');
  assert.equal((await db.query('SELECT count(*)::int AS n FROM case_follow_ups')).rows[0].n,1);
  await db.exec(`INSERT INTO case_document_exports(id,case_id,document_kind,final_document,case_version,source_row_version,workflow_state,template_version,generated_by_user_id,generated_by_username,generated_at,snapshot_json,pdf_bytes,pdf_sha256,file_name)
   SELECT '11111111-1111-1111-1111-111111111111',c.id,'CASE_FORM',false,1,0,'Draft','case-tables-1.0',u.id,u.username,CURRENT_TIMESTAMP,'{"synthetic":true}'::jsonb,decode('25504446','hex'),repeat('a',64),'synthetic.pdf' FROM cases c CROSS JOIN users u WHERE c.case_number='RECEIPT-A' AND u.username='receipt-actor'`);
  await assert.rejects(db.exec("UPDATE case_document_exports SET file_name='changed.pdf'"),e=>e.code==='23514');
  await assert.rejects(db.exec('DELETE FROM case_document_exports'),e=>e.code==='23514');
  assert.equal((await db.query('SELECT count(*)::int AS n FROM case_document_exports')).rows[0].n,1);
  await db.exec(`INSERT INTO case_lab_results(case_id,test_name,test_date,result_text,interpretation,normal_low,normal_high,recorded_by,recorded_at) SELECT id,'ALT',CURRENT_DATE,'120','HIGH',0,40,'receipt-actor',CURRENT_TIMESTAMP FROM cases WHERE case_number='RECEIPT-A'`);
  await assert.rejects(db.exec("UPDATE case_lab_results SET result_text='999'"),e=>e.code==='23514');
  await assert.rejects(db.exec('DELETE FROM case_lab_results'),e=>e.code==='23514');
  await assert.rejects(db.exec("UPDATE case_lab_results SET voided_at=CURRENT_TIMESTAMP,voided_by='receipt-actor',void_reason=''"),e=>e.code==='23514');
  await db.exec("UPDATE case_lab_results SET voided_at=CURRENT_TIMESTAMP,voided_by='receipt-actor',void_reason='Wrong specimen'");
  await assert.rejects(db.exec("UPDATE case_lab_results SET void_reason='changed'"),e=>e.code==='23514');
  assert.equal((await db.query('SELECT result_text FROM case_lab_results')).rows[0].result_text,'120');
  await assert.rejects(db.exec(`INSERT INTO case_lab_results(case_id,test_name,test_date,result_text,interpretation,normal_low,normal_high,recorded_by,recorded_at) SELECT id,'ALT',CURRENT_DATE,'120','HIGH',80,40,'receipt-actor',CURRENT_TIMESTAMP FROM cases WHERE case_number='RECEIPT-A'`),e=>e.code==='23514');
  await db.exec(`INSERT INTO users(username,password_hash,role,email,employee_id,account_status,enabled) VALUES ('pending-access','hash','ROLE_MEDICAL_REVIEWER','pending@example.test','EMP-ACCESS','PENDING',false);
    INSERT INTO registration_requests(user_id,requested_role,reason,created_at) SELECT id,'ROLE_MEDICAL_REVIEWER','Training',CURRENT_TIMESTAMP FROM users WHERE username='pending-access';
    INSERT INTO registration_details(request_id,field_name,field_value) SELECT id,'medicalQualification','Synthetic' FROM registration_requests;
    INSERT INTO audit_events(event_type,username,status,details) VALUES ('REGISTRATION_SUBMITTED','pending-access','SUCCESS','Synthetic request');`);
  await assert.rejects(db.exec("INSERT INTO users(username,password_hash,role,email) VALUES ('duplicate-access','hash','ROLE_AUDITOR','PENDING@EXAMPLE.TEST')"),e=>e.code==='23505');
  await assert.rejects(db.exec("INSERT INTO users(username,password_hash,role,employee_id) VALUES ('duplicate-employee','hash','ROLE_AUDITOR','emp-access')"),e=>e.code==='23505');
  await assert.rejects(db.exec("UPDATE users SET account_status='INVALID' WHERE username='pending-access'"),e=>e.code==='23514');
  await assert.rejects(db.exec("UPDATE registration_requests SET requested_role='ROLE_ADMIN'"),e=>e.code==='23514');
  await assert.rejects(db.exec("UPDATE registration_details SET field_value='Changed'"),e=>e.code==='23514');
  await assert.rejects(db.exec("DELETE FROM registration_requests"),e=>e.code==='23514');
  await assert.rejects(db.exec("UPDATE audit_events SET details='Changed'"),e=>e.code==='23514');
  await assert.rejects(db.exec("DELETE FROM audit_events"),e=>e.code==='23514');
  if(populated)assert.equal((await db.query("SELECT account_status FROM users WHERE username='legacy'")).rows[0].account_status,'APPROVED');
  const rateSQL="INSERT INTO access_rate_limits(bucket,window_start,attempts) VALUES ('synthetic-rate',CURRENT_TIMESTAMP,1) ON CONFLICT(bucket) DO UPDATE SET attempts=CASE WHEN access_rate_limits.window_start < CURRENT_TIMESTAMP - INTERVAL '1 hour' THEN 1 ELSE access_rate_limits.attempts+1 END, window_start=CASE WHEN access_rate_limits.window_start < CURRENT_TIMESTAMP - INTERVAL '1 hour' THEN CURRENT_TIMESTAMP ELSE access_rate_limits.window_start END RETURNING attempts";
  assert.equal((await db.query(rateSQL)).rows[0].attempts,1);assert.equal((await db.query(rateSQL)).rows[0].attempts,2);
  await db.exec("UPDATE access_rate_limits SET window_start=CURRENT_TIMESTAMP-INTERVAL '2 hours'");assert.equal((await db.query(rateSQL)).rows[0].attempts,1);
  await db.exec("INSERT INTO password_reset_requests(user_id,status,created_at) SELECT id,'PENDING',CURRENT_TIMESTAMP FROM users WHERE username='pending-access'");
  await assert.rejects(db.exec("UPDATE password_reset_requests SET status='APPROVED'"),e=>e.code==='23514');
  await assert.rejects(db.exec("UPDATE password_reset_requests SET status='USED'"),e=>e.code==='23514');

  // Stable employee identity and immutable scoped administration evidence.
  const staff=(await db.query("SELECT id::text,staff_code FROM users WHERE username='receipt-actor'")).rows[0];assert.equal(staff.staff_code,'PS-'+staff.id);
  await assert.rejects(db.exec("UPDATE users SET staff_code='PS-forged' WHERE username='receipt-actor'"),e=>e.code==='428C9');
  assert.equal((await db.query('SELECT count(*)::int AS n FROM role_admin_assignments')).rows[0].n,6);
  await db.exec("INSERT INTO users(username,password_hash,role,email) VALUES ('scoped-admin','hash','ROLE_SCOPED_ADMIN','scoped@example.test'),('other-scoped-admin','hash','ROLE_SCOPED_ADMIN','other-scoped@example.test')");
  await db.exec("UPDATE role_admin_assignments SET administrator_id=(SELECT id FROM users WHERE username='scoped-admin'),assigned_by=(SELECT id FROM users WHERE username='receipt-actor'),assigned_at=CURRENT_TIMESTAMP WHERE application_role='ROLE_CASE_PROCESSOR'");
  await assert.rejects(db.exec("UPDATE role_admin_assignments SET administrator_id=(SELECT id FROM users WHERE username='scoped-admin'),assigned_by=(SELECT id FROM users WHERE username='receipt-actor'),assigned_at=CURRENT_TIMESTAMP WHERE application_role='ROLE_MEDICAL_REVIEWER'"),e=>e.code==='23505');
  await assert.rejects(db.exec("UPDATE role_admin_assignments SET administrator_id=(SELECT id FROM users WHERE username='receipt-actor'),assigned_by=(SELECT id FROM users WHERE username='receipt-actor'),assigned_at=CURRENT_TIMESTAMP WHERE application_role='ROLE_MEDICAL_REVIEWER'"),e=>e.code==='23514');
  await assert.rejects(db.exec("UPDATE users SET role='ROLE_ADMIN' WHERE username='scoped-admin'"),e=>e.code==='23514');
  await db.exec("INSERT INTO administrative_events(actor_id,actor_username,actor_level,managed_role,target_user_id,action,reason,outcome,created_at) SELECT id,username,'SUPER_ADMIN','ROLE_CASE_PROCESSOR',(SELECT id FROM users WHERE username='scoped-admin'),'ROLE_ADMIN_ASSIGNED','Synthetic assignment','SUCCESS',CURRENT_TIMESTAMP FROM users WHERE username='receipt-actor'");
  await assert.rejects(db.exec("UPDATE administrative_events SET reason='Changed'"),e=>e.code==='23514');await assert.rejects(db.exec('DELETE FROM administrative_events'),e=>e.code==='23514');
  // V14 report evidence must be immutable and V15 seeds only three labelled intake records.
  await db.exec(readFileSync(join(directory,'V15__three_labelled_synthetic_intake_cases.sql'),'utf8'));
  assert.equal((await db.query('SELECT count(*)::int AS n FROM cases WHERE synthetic_record')).rows[0].n,3);
  await db.exec(readFileSync(join(directory,'V15__three_labelled_synthetic_intake_cases.sql'),'utf8'));
  assert.equal((await db.query('SELECT count(*)::int AS n FROM cases WHERE synthetic_record')).rows[0].n,3);
  assert.equal((await db.query("SELECT count(*)::int AS n FROM cases WHERE synthetic_record AND workflow_state <> 'Data Entry'")).rows[0].n,0);
  assert.equal((await db.query('SELECT count(*)::int AS n FROM case_patients p JOIN cases c ON c.id=p.case_id WHERE c.synthetic_record')).rows[0].n,3);
  await assert.rejects(db.exec("UPDATE cases SET synthetic_record=FALSE WHERE synthetic_record"),e=>e.code==='23514');
  await assert.rejects(db.exec("INSERT INTO safety_reports(report_id,report_type,product,due_date,total_cases,serious_cases) VALUES('INVALID-COUNTS','PSUR','Synthetic',CURRENT_DATE,1,2)"),e=>e.code==='23514');
  await assert.rejects(db.exec("INSERT INTO safety_reports(report_id,report_type,product,due_date,period_start,period_end) VALUES('INVALID-PERIOD','PSUR','Synthetic',CURRENT_DATE,DATE '2026-11-10',DATE '2026-11-01')"),e=>e.code==='23514');
  await db.exec("INSERT INTO safety_reports(report_id,report_type,product,due_date) VALUES('EVIDENCE-TEST','PSUR','Synthetic',CURRENT_DATE)");
  await db.exec(`INSERT INTO report_document_exports(id,report_id,source_report_version,generated_by_user_id,generated_by_username,generated_at,snapshot_json,pdf_bytes,pdf_sha256,file_name) SELECT 'report-pdf',r.id,0,u.id,u.username,CURRENT_TIMESTAMP,'{}'::jsonb,decode('25504446','hex'),repeat('a',64),'report.pdf' FROM safety_reports r CROSS JOIN users u WHERE r.report_id='EVIDENCE-TEST' AND u.username='receipt-actor';
  INSERT INTO report_acknowledgements(report_id,outcome,reference,received_at,message,reason,recorded_by_user_id,recorded_by_username,recorded_at,source_report_version) SELECT r.id,'ACCEPTED','ACK-TEST',CURRENT_TIMESTAMP,'Synthetic','Test evidence',u.id,u.username,CURRENT_TIMESTAMP,0 FROM safety_reports r CROSS JOIN users u WHERE r.report_id='EVIDENCE-TEST' AND u.username='receipt-actor';`);
  await db.exec("INSERT INTO safety_report_cases(report_id,case_id) SELECT r.id,c.id FROM safety_reports r CROSS JOIN cases c WHERE r.report_id='EVIDENCE-TEST' AND c.case_number='RECEIPT-A'");
  await assert.rejects(db.exec("INSERT INTO safety_report_cases(report_id,case_id) SELECT r.id,c.id FROM safety_reports r CROSS JOIN cases c WHERE r.report_id='EVIDENCE-TEST' AND c.case_number='DEMO-PS-001'"),e=>e.code==='23514');
  await assert.rejects(db.exec("UPDATE report_document_exports SET file_name='changed.pdf'"),e=>e.code==='23514');
  await assert.rejects(db.exec('DELETE FROM report_document_exports'),e=>e.code==='23514');
  await assert.rejects(db.exec("UPDATE report_acknowledgements SET outcome='REJECTED'"),e=>e.code==='23514');
  await assert.rejects(db.exec('DELETE FROM report_acknowledgements'),e=>e.code==='23514');
  console.log(`PASS ${populated?'populated upgrade':'fresh install'}: V1–V15, six public roles and separate administrative authorities, FK integrity${populated?', preserved users/case/patient links':''}`);
 } finally { await db.close(); }
}

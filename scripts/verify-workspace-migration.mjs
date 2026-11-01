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
  assert.equal((await db.query('SELECT count(*)::int AS n FROM workspace_roles WHERE assignable')).rows[0].n,7);
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
  console.log(`PASS ${populated?'populated upgrade':'fresh install'}: V1–V11, seven roles, FK integrity${populated?', preserved users/case/patient links':''}`);
 } finally { await db.close(); }
}

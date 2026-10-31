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
  console.log(`PASS ${populated?'populated upgrade':'fresh install'}: V1–V8, seven roles, FK integrity${populated?', preserved users/case/patient links':''}`);
 } finally { await db.close(); }
}

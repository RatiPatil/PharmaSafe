// Disposable PostgreSQL-compatible database only. Never connects to Supabase.
import {readFileSync,readdirSync} from 'node:fs';
import {resolve,join,dirname} from 'node:path';
import {pathToFileURL,fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';
const {PGlite}=await import(pathToFileURL(resolve(process.argv[2],'node_modules/@electric-sql/pglite/dist/index.js')).href);
const db=new PGlite();const directory=join(dirname(fileURLToPath(import.meta.url)),'../backend/src/main/resources/db/migration');
try{
 for(const f of readdirSync(directory).filter(f=>/^V\d+__.*\.sql$/.test(f)).sort((a,b)=>Number(a.match(/^V(\d+)/)[1])-Number(b.match(/^V(\d+)/)[1])))await db.exec(readFileSync(join(directory,f),'utf8'));
 await db.exec("INSERT INTO users(username,password_hash,role) VALUES('series-analyst','hash','ROLE_PV_ANALYST'),('series-other','hash','ROLE_PV_ANALYST'),('series-manager','hash','ROLE_SAFETY_MANAGER'),('series-admin','hash','ROLE_SCOPED_ADMIN')");
 const uid=async name=>(await db.query('SELECT id FROM users WHERE username=$1',[name])).rows[0].id;
 const analyst=await uid('series-analyst'),other=await uid('series-other'),manager=await uid('series-manager'),admin=await uid('series-admin');
 await db.exec("INSERT INTO cases(case_number,country_code,report_type,initial_receipt_date,latest_received_date,workflow_state) VALUES('SERIES-CASE','USA','Spontaneous',CURRENT_DATE,CURRENT_DATE,'Approved'),('SERIES-UNREVIEWED','USA','Spontaneous',CURRENT_DATE,CURRENT_DATE,'Data Entry')");
 const cid=(await db.query("SELECT id FROM cases WHERE case_number='SERIES-CASE'")).rows[0].id,unreviewed=(await db.query("SELECT id FROM cases WHERE case_number='SERIES-UNREVIEWED'")).rows[0].id;
 async function transaction(work){await db.exec('BEGIN');try{await work();await db.exec('COMMIT');}catch(e){await db.exec('ROLLBACK');throw e;}}
 async function cohort(id,owner,caseId=cid,number='SERIES-CASE',revision=0,withMember=true){
  const line={caseId,caseNumber:number,sourceRowVersion:revision};
  await transaction(async()=>{await db.query("INSERT INTO pv_case_series(id,analyst_user_id,name,purpose,case_count,captured_at,criteria_json,snapshot_json,pdf_bytes,pdf_sha256) VALUES($1,$2,'Focused cohort','Synthetic source evaluation',1,CURRENT_TIMESTAMP,'{}'::jsonb,$3::jsonb,decode('25504446','hex'),repeat('a',64))",[id,owner,JSON.stringify({caseListing:[line]})]);if(withMember)await db.query('INSERT INTO pv_series_cases(series_id,case_id,source_row_version,case_number,snapshot_json) VALUES($1,$2,$3,$4,$5::jsonb)',[id,caseId,revision,number,JSON.stringify(line)]);});
 }
 const invalid=e=>e.code==='23514';
 await assert.rejects(cohort('invalid-owner',admin),invalid);
 await assert.rejects(cohort('invalid-state',analyst,unreviewed,'SERIES-UNREVIEWED'),invalid);
 await assert.rejects(cohort('invalid-version',analyst,cid,'SERIES-CASE',1),invalid);
 await assert.rejects(cohort('incomplete-members',analyst,cid,'SERIES-CASE',0,false),invalid);
 assert.equal((await db.query('SELECT count(*)::int AS n FROM pv_case_series')).rows[0].n,0);
 await cohort('own-series',analyst);await cohort('other-series',other);
 const report=async(id,owner=analyst,series='own-series',prior=null)=>db.query("INSERT INTO pv_analysis_records(id,analyst_user_id,title,objective,findings,recommendation,created_at,snapshot_json,pdf_bytes,pdf_sha256,series_id,supersedes_id) VALUES($1,$2,'Synthetic report','Objective','Findings','Recommendation',CURRENT_TIMESTAMP,'{}'::jsonb,decode('25504446','hex'),repeat('a',64),$3,$4)",[id,owner,series,prior]);
 await assert.rejects(report('wrong-series',analyst,'other-series'),invalid);await report('original');
 await assert.rejects(report('unrequested-revision',analyst,'own-series','original'),invalid);
 const review=async(actor=manager,previous=null,reportId='original',outcome='RETURNED',due=null)=>db.query("INSERT INTO pv_analysis_reviews(analysis_id,reviewer_user_id,previous_review_id,outcome,comments,follow_up_due_date,reviewed_at,snapshot_json,pdf_bytes,pdf_sha256) VALUES($1,$2,$3,$4,'Source review comments',$5,CURRENT_TIMESTAMP,'{}'::jsonb,decode('25504446','hex'),repeat('b',64)) RETURNING id",[reportId,actor,previous,outcome,due]);
 await assert.rejects(review(analyst),invalid);await assert.rejects(review(admin),invalid);
 const first=(await review()).rows[0].id;
 await assert.rejects(review(manager),invalid);
 await assert.rejects(review(manager,first,'original','FOLLOW_UP_REQUIRED',null),invalid);
 await assert.rejects(review(manager,first,'original','FOLLOW_UP_REQUIRED','2020-01-01'),invalid);
 await report('replacement',analyst,'own-series','original');
 await assert.rejects(report('second-replacement',analyst,'own-series','original'),e=>e.code==='23505');
 await assert.rejects(review(manager,first),invalid);
 await db.exec("UPDATE cases SET workflow_state='Data Entry' WHERE case_number='SERIES-CASE'");
 assert.equal((await db.query("SELECT snapshot_json->'caseListing'->0->>'caseNumber' AS number FROM pv_case_series WHERE id='own-series'")).rows[0].number,'SERIES-CASE');
 for(const table of ['pv_case_series','pv_series_cases','pv_analysis_reviews']){await assert.rejects(db.exec(`DELETE FROM ${table}`),invalid);}
 await assert.rejects(db.exec("UPDATE pv_analysis_reviews SET comments='Changed'"),invalid);
 await assert.rejects(db.exec("UPDATE pv_series_cases SET source_row_version=8"),invalid);
 await assert.rejects(db.exec("UPDATE pv_case_series SET purpose='Changed'"),invalid);
 console.log('PASS PV case-series and review schema: complete deferred membership, reviewed source revisions, author ownership, immutable cohort/report/review evidence, reviewer authority, stale review rejection and linked replacement history');
}finally{await db.close();}

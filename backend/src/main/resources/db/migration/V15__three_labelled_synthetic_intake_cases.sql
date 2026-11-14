-- Explicitly requested small demonstration dataset. No approvals or submissions are invented.
-- Existing installations only: a fresh empty installation has no accounts at Flyway time.
DO $$
DECLARE processor_id BIGINT; demo_id BIGINT; n INT;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM users WHERE role='ROLE_ADMIN' AND enabled AND account_status='APPROVED') THEN RETURN; END IF;
 SELECT id INTO processor_id FROM users WHERE role IN ('ROLE_CASE_PROCESSOR','ROLE_USER') AND enabled AND account_status='APPROVED' AND NOT locked ORDER BY id LIMIT 1;
 FOR n IN 1..3 LOOP
  demo_id:=NULL;
  INSERT INTO cases(case_number,country_code,report_type,initial_receipt_date,latest_received_date,workflow_state,priority,is_serious,narrative,company_comments,assigned_to_user_id,synthetic_record)
   VALUES('DEMO-PS-00'||n,'USA','Spontaneous',CURRENT_DATE-n,CURRENT_DATE-n,'Data Entry',CASE n WHEN 1 THEN 'Routine' WHEN 2 THEN 'Priority' ELSE 'Expedited' END,n=3,
   'SYNTHETIC TRAINING CASE. Fictional patient and study product. For demonstration only; not a real adverse event or regulatory submission.',
   'Small demonstration dataset requested by application owner. Clinical review has not been performed.',processor_id,TRUE)
   ON CONFLICT(case_number) DO NOTHING RETURNING id INTO demo_id;
  IF demo_id IS NULL THEN CONTINUE; END IF;
  INSERT INTO case_patients(case_id,patient_identifier,gender,age_value,age_unit) VALUES(demo_id,'SYNTHETIC-P'||n,'Unknown',30+n,'Years');
  INSERT INTO case_reporters(case_id,is_primary,qualification,first_name,last_name,country_code) VALUES(demo_id,TRUE,'Physician','Synthetic','Reporter','USA');
  INSERT INTO case_products(case_id,product_type,product_name) VALUES(demo_id,'Suspect','SYNTHETIC Study Product '||n);
  INSERT INTO case_events(case_id,event_verbatim,onset_date,outcome,is_serious,other_medically_important) VALUES(demo_id,CASE n WHEN 1 THEN 'Synthetic headache' WHEN 2 THEN 'Synthetic rash' ELSE 'Synthetic medically important reaction' END,CURRENT_DATE-n,'Unknown',n=3,n=3);
  INSERT INTO audit_events(event_type,username,status,details) VALUES('DEMO_DATA_SEEDED','database-migration','SUCCESS','Created explicitly labelled synthetic intake case DEMO-PS-00'||n||'; no clinical approval or regulatory submission.');
 END LOOP;
END $$;

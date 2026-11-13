import {describe,it,expect} from 'vitest';
import {validateInputPayload,fieldError,passwordPolicy} from './inputValidation';
describe('field-specific application validation',()=>{
 it.each([['fullName','Name123'],['email','invalid@address'],['phone','+123'],['phone','1234567890123456'],['countryCode','ZZZ'],['priority','Urgent'],['ageValue',-1],['heightCm',-10],['dosageAmount',0.12345],['productName','A'.repeat(129)]])('rejects inappropriate %s values',(field,value)=>expect(fieldError(field as string,value)).not.toBeNull());
 it('preserves international names and medical notation',()=>{
  expect(fieldError('fullName','José O’Connor')).toBeNull();expect(fieldError('resultText','ALT < 10 µg/L; 0.5 mg')).toBeNull();expect(fieldError('narrative','Patient reported dose 1.5 mg/day.\nFollow-up pending.')).toBeNull();
 });
 it('validates nested case values, date ordering and optional draft fields',()=>{
  const invalid=validateInputPayload('/api/cases',{draft:true,patient:{ageValue:200,ageUnit:'Years'},reporters:[{email:'no-email'}],products:[{startDate:'2026-10-20',stopDate:'2026-10-01'}]});expect(invalid['patient.ageValue']).toBeDefined();expect(invalid['reporters[0].email']).toBeDefined();expect(invalid['products[0].stopDate']).toBeDefined();
  expect(validateInputPayload('/api/cases',{draft:true,patient:{patientIdentifier:''},narrative:''})).toEqual({});
 });
 it('rejects unsupported dropdown selections and respects registration email capacity',()=>{
  expect(validateInputPayload('/api/auth/register',{department:'Invented department'}).department).toBeDefined();expect(fieldError('email','a'.repeat(120)+'@example.test','email','registration')).toBeNull();
 });
 it('enforces real calendar dates and timezone-bearing timestamps',()=>{
  expect(fieldError('dateOfBirth','2026-02-30')).not.toBeNull();expect(fieldError('dateOfBirth','2024-02-29')).toBeNull();expect(fieldError('contactAt','yesterday')).not.toBeNull();
 });
 it('checks UTF-8 password size without trimming meaningful characters',()=>{
  expect(passwordPolicy('short')).not.toBeNull();expect(passwordPolicy('LongPassword123!')).toBeNull();expect(passwordPolicy('A1'+ '🙂'.repeat(20))).not.toBeNull();expect(passwordPolicy('  LongPassword123!  ')).toBeNull();
 });
});

it("accepts an email identifier for existing login",()=>{ expect(validateInputPayload("/api/auth/login",{username:"safety@example.test",password:"existing"})).toEqual({}); });

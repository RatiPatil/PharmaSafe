import {readFileSync,writeFileSync} from 'node:fs';
import {isDeepStrictEqual} from 'node:util';
const frontend=new URL('../frontend/src/config/inputRules.json',import.meta.url),backend=new URL('../backend/src/main/resources/validation/input-rules.json',import.meta.url);
if(process.argv.includes('--sync'))writeFileSync(backend,readFileSync(frontend));
if(!isDeepStrictEqual(JSON.parse(readFileSync(frontend)),JSON.parse(readFileSync(backend))))throw Error('Frontend/backend validation rules differ. Run node scripts/verify-input-rules.mjs --sync after updating the canonical frontend rules.');
console.log('PASS identical frontend and backend field validation rules');

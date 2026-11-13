// Keep the immediately rendered public catalogue aligned with the server catalog.
import { readFileSync } from 'node:fs';
const read=path=>readFileSync(new URL('../'+path,import.meta.url),'utf8');
const source=read('backend/src/main/java/com/pharmasafe/service/AccessRoleCatalog.java');
const names=Object.fromEntries([...source.matchAll(/"(ROLE_\w+)","([^"\n]+)"/g)].map(m=>[m[1],m[2]]));
const expected=[...source.matchAll(/"(ROLE_\w+)",List.of\(([^)]*)\)/g)].map(m=>({role:m[1],name:names[m[1]],fields:[...m[2].matchAll(/"([^"]+)"/g)].map(v=>v[1])})).sort((a,b)=>a.role.localeCompare(b.role));
const actual=JSON.parse(read('frontend/src/config/publicRoleCatalog.json'));
if(expected.length!==6||JSON.stringify(actual)!==JSON.stringify(expected))throw Error('Public role catalogue must match all six server roles and fields.');
console.log('PASS public role cards match the server catalogue; administrator excluded');

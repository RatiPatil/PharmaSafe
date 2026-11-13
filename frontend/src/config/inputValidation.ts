import config from './inputRules.json';
type Rule={date?:boolean;instant?:boolean;max?:number;min?:number;scale?:number;number?:boolean;pattern?:string;email?:boolean;phone?:boolean;enum?:string[]};
const fields=config.fields as Record<string,Rule>,overrides=config.overrides as Record<string,Rule>;
export type FieldErrors=Record<string,string>;
export function inputContext(path:string){return path==='/api/auth/register'?'registration':path.startsWith('/api/admin')?'admin':path.startsWith('/api/reports')?'report':path.startsWith('/api/auth')?'auth':path.startsWith('/api/profile')?'profile':'case';}
export function fieldRule(field:string,path='',context='case'):Rule{return overrides[`${context}.${path||field}`]||(context==='registration'&&path.startsWith('details.')?overrides['registration.details.*']:undefined)||fields[field]||{};}
export function fieldError(field:string,value:unknown,path=field,context='case'):string|null{
 if(value==null||value==='')return null;const rule=fieldRule(field,path.replace(/\[\d+\]/g,'[]'),context);
 if(['password','confirmPassword','newPassword'].includes(field))return new TextEncoder().encode(String(value)).length>72?'Maximum 72 UTF-8 bytes.':null;
 if(rule.number){if(typeof value!=='number'||!Number.isFinite(value))return 'Enter a numeric value.';if(value<(rule.min??-Infinity)||value>(rule.max??Infinity))return `Enter a value between ${rule.min} and ${rule.max}.`;const factor=10**(rule.scale??0);if(Math.abs(value*factor-Math.round(value*factor))>1e-6)return `Use at most ${rule.scale} decimal places.`;return null;}
 if(typeof value!=='string')return null;
 if(value.length>(rule.max??config.defaultMax))return `Maximum ${rule.max??config.defaultMax} characters.`;
 if(!value.trim())return 'Enter a value or leave this optional field empty.';
 if(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/u.test(value))return 'Unsupported control characters.';
 const v=value.trim();
 if(rule.date){const parsed=new Date(v+'T00:00:00Z');if(!/^\d{4}-\d{2}-\d{2}$/.test(v)||!Number.isFinite(parsed.getTime())||parsed.toISOString().slice(0,10)!==v)return 'Enter a valid calendar date (YYYY-MM-DD).';}
 if(rule.instant&&(!/T.*(?:Z|[+-]\d{2}:\d{2})$/.test(v)||!Number.isFinite(Date.parse(v))))return 'Enter a valid date and time with timezone.';
 if(rule.pattern&&!new RegExp(`^(?:${rule.pattern})$`,'u').test(v))return 'Use the expected format for this field.';
 if(rule.email&&!/^[^\s@]+@[^\s@]+\.[^\s@]+$/u.test(v))return 'Enter a valid email address.';
 if(rule.phone){const digits=v.replace(/\D/g,'').length;if(digits<7||digits>15)return 'Enter a phone number containing 7–15 digits.';}
 if(rule.enum&&!rule.enum.includes(v))return 'Select a supported value.';
 if(context==='registration'){const choices=(config.registrationChoices as Record<string,string[]>)[field];if(choices&&!choices.includes(v))return 'Select an available registration option.';}
 return null;
}
export function validateInputPayload(path:string,body:unknown):FieldErrors{
 const errors:FieldErrors={},context=inputContext(path);const add=(p:string,error:string)=>{if(Object.keys(errors).length<30)errors[p]=`${p.replace(/([a-z])([A-Z])/g,'$1 $2')}: ${error}`;};
 function walk(value:unknown,p:string,depth=0){if(value==null)return;if(depth>16){add(p,'Input is too deeply nested.');return;}if(Array.isArray(value)){if(value.length>config.maxItems)add(p,'Too many entries.');else value.forEach((v,i)=>walk(v,`${p}[${i}]`,depth+1));return;}
  if(typeof value==='object'){
   const node=value as Record<string,unknown>;Object.entries(node).forEach(([key,v])=>walk(v,p?`${p}.${key}`:key,depth+1));
   for(const [start,end]of [['initialReceiptDate','latestReceivedDate'],['startDate','stopDate'],['startDate','endDate'],['onsetDate','stopDate'],['periodStart','periodEnd']]){if(typeof node[start]==='string'&&typeof node[end]==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(node[start])&&/^\d{4}-\d{2}-\d{2}$/.test(node[end])&&node[end]<node[start])add(p?`${p}.${end}`:end,`Must not be before ${start}.`);}
   if(typeof node.normalLow==='number'&&typeof node.normalHigh==='number'&&node.normalLow>node.normalHigh)add(`${p}.normalHigh`,'Must not be below the lower reference limit.');
   const today=new Date(),date=`${today.getFullYear()}-${String(today.getMonth()+1).padStart(2,'0')}-${String(today.getDate()).padStart(2,'0')}`;
   if(typeof node.dateOfBirth==='string'&&node.dateOfBirth>date)add(`${p}.dateOfBirth`,'Cannot be in the future.');
   const divisor:Record<string,number>={Years:1,Months:12,Weeks:52.18,Days:365.25,Decades:.1};if(typeof node.ageValue==='number'&&node.ageValue/(divisor[String(node.ageUnit)]||1)>150)add(`${p}.ageValue`,'Must represent an age of 150 years or less.');
   return;
  }
  const key=p.split('.').at(-1)||p,error=fieldError(key,value,p,context);if(error)add(p,error);
 }
 walk(body,'');return errors;
}
export function passwordPolicy(value:string){return value.length<12||new TextEncoder().encode(value).length>72||!/[A-Za-z]/.test(value)||!(/[^A-Za-z]/.test(value))?'Use 12–72 UTF-8 bytes with letters and a number or symbol.':null;}

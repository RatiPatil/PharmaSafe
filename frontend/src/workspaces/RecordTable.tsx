import type { ReactNode } from 'react';
import type { Row, Section } from './model';
import { text,date,dateTime,roleName } from './model';
const columns: Partial<Record<Section, [string,string][]>> = {
 users:[['username','Username'],['fullName','Full name'],['role','Workspace'],['enabled','Account'],['lastSuccessfulLoginAt','Last login']],
 audit:[['createdAt','Recorded'],['eventType','Event'],['username','Actor'],['status','Result'],['details','Details']],
 reports:[['reportId','Report'],['product','Product'],['reportType','Type'],['authority','Authority'],['dueDate','Due'],['status','Status']],
 tasks:[['caseNumber','Case'],['description','Action'],['assignedUsername','Assignee'],['dueAt','Due'],['status','Status']],
 contacts:[['caseNumber','Case'],['contactAt','Contact date'],['contactType','Channel'],['description','Description'],['responsibleUsername','Responsible user']],
};
export function RecordTable({section,rows,actions}:{section:Section;rows:Row[];actions?:(row:Row)=>ReactNode}) {
 const cols=columns[section]||[];
 return <div className="ws-table-wrap"><table><caption className="ws-sr">{section} records</caption><thead><tr>{cols.map(([key,label])=><th scope="col" key={key}>{label}</th>)}{actions&&<th scope="col">Actions</th>}</tr></thead><tbody>{rows.map((row,i)=><tr key={text(row.id??i)}>{cols.map(([key])=><td key={key}>{key==='role'?roleName(row[key]):key==='enabled'?<span className={`ws-badge ${row[key]?'':'muted'}`}>{row[key]?'Active':'Disabled'}</span>:key==='dueDate'?date(row[key]):['createdAt','contactAt','dueAt','lastSuccessfulLoginAt'].includes(key)?dateTime(row[key]):key==='status'?<span className={`ws-badge ${row[key]==='Overdue'||row.overdue?'danger':''}`}>{text(row[key])}{row.overdue&&row[key]!=='Overdue'?' · Overdue':''}</span>:text(row[key])}</td>)}{actions&&<td className="ws-row-actions">{actions(row)}</td>}</tr>)}</tbody></table></div>;
}

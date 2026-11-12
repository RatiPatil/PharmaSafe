import { useEffect, useRef, type ReactNode } from 'react';
export function AccessDialog({title,onClose,children,blocked=false}:{title:string;onClose:()=>void;children:ReactNode;blocked?:boolean}){
 const ref=useRef<HTMLDialogElement>(null);
 useEffect(()=>{const trigger=document.activeElement as HTMLElement|null;const overflow=document.body.style.overflow;document.body.style.overflow='hidden';const dialog=ref.current;if(dialog?.showModal)dialog.showModal();else dialog?.setAttribute('open','');return()=>{document.body.style.overflow=overflow;dialog?.close?.();if(trigger?.isConnected)trigger.focus({preventScroll:true});};},[]);
 return <dialog ref={ref} className="access-dialog" aria-label={title} aria-modal="true" onCancel={e=>{e.preventDefault();if(!blocked)onClose();}} onKeyDown={e=>{if(e.key==='Escape'){e.preventDefault();if(!blocked)onClose();}}}><header><div><p className="ws-eyebrow">USER ACCESS MANAGEMENT</p><h2>{title}</h2></div><button type="button" aria-label={`Close ${title.toLowerCase()}`} disabled={blocked} onClick={onClose}>Close ×</button></header><div className="access-dialog-body">{children}</div></dialog>;
}

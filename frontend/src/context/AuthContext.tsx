import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import type { ReactNode } from 'react';
import type { User, AuthResponse } from '../types/auth';
import { getApiUrl, getCsrfToken, refreshCsrfToken, setCsrfToken, fetchWithTimeout, fetchJsonWithTimeout, SESSION_EXPIRED_EVENT } from '../config/api';

interface AuthContextType {
 user: User | null; isAuthenticated: boolean; isLoading: boolean; isInitialLoading: boolean;
 error: string | null; isConnectionError: boolean;
 login: (username:string,password:string)=>Promise<boolean>;
 logout: ()=>Promise<void>; clearError: ()=>void; retryAuth: ()=>Promise<void>;
}
const AuthContext=createContext<AuthContextType|undefined>(undefined);
export function getXsrfToken():string|null {return getCsrfToken();}
function validSession(user:User|null):user is User {return !!user&&typeof user.id==='number'&&typeof user.username==='string'&&typeof user.role==='string';}

export const AuthProvider:React.FC<{children:ReactNode}>=({children})=>{
 const [user,setUser]=useState<User|null>(null);
 const [isInitialLoading,setIsInitialLoading]=useState(true);
 const [isLoading,setIsLoading]=useState(false);
 const [error,setError]=useState<string|null>(null);
 const [isConnectionError,setIsConnectionError]=useState(false);
 const generation=useRef(0);const active=useRef<AbortController|null>(null);const csrfPrefetch=useRef<AbortController|null>(null);
 // A newer sign-in, logout or expiry always wins over a late session-check response.
 const cancelActive=useCallback(()=>{generation.current++;active.current?.abort();active.current=null;csrfPrefetch.current?.abort();csrfPrefetch.current=null;},[]);
 const prefetchCsrf=useCallback(()=>{
  csrfPrefetch.current?.abort();const controller=new AbortController();csrfPrefetch.current=controller;
  void refreshCsrfToken(controller.signal).finally(()=>{if(csrfPrefetch.current===controller)csrfPrefetch.current=null;});
 },[]);
 const restoreSession=useCallback(async()=>{
  cancelActive();const current= generation.current;const controller=new AbortController();active.current=controller;
  setIsInitialLoading(true);
  try{
   const {response,data}=await fetchJsonWithTimeout<User>(getApiUrl('/api/auth/me'),{credentials:'include',headers:{Accept:'application/json'},signal:controller.signal});
   if(current!==generation.current||controller.signal.aborted)return;
   if(response.ok&&validSession(data)){setUser(data);setError(null);setIsConnectionError(false);prefetchCsrf();}
   else {setUser(null);setError(response.status===401?null:'Session verification failed. Please try signing in again.');setIsConnectionError(false);}
  }catch{
   if(current!==generation.current)return;
   setUser(null);setError('The authentication service did not respond. Please try signing in again; the server may be starting.');setIsConnectionError(true);
  }finally{if(current===generation.current){active.current=null;setIsInitialLoading(false);}}
 },[cancelActive,prefetchCsrf]);
 useEffect(()=>{void restoreSession();return cancelActive;},[restoreSession,cancelActive]);
 useEffect(()=>{
  const expire=()=>{cancelActive();setUser(null);setCsrfToken(null);setIsLoading(false);setIsInitialLoading(false);setError('Your session expired or your account access changed. Sign in again to continue.');setIsConnectionError(false);};
  window.addEventListener(SESSION_EXPIRED_EVENT,expire);return ()=>window.removeEventListener(SESSION_EXPIRED_EVENT,expire);
 },[cancelActive]);
 const login=async(username:string,password:string):Promise<boolean>=>{
  cancelActive();const current=generation.current;const controller=new AbortController();active.current=controller;
  setIsInitialLoading(false);setIsLoading(true);setError(null);setIsConnectionError(false);
  const stillCurrent=()=>current===generation.current&&!controller.signal.aborted;
  try{
   const xsrf=getCsrfToken();
   const {response,data}=await fetchJsonWithTimeout<AuthResponse>(getApiUrl('/api/auth/login'),{method:'POST',credentials:'include',signal:controller.signal,headers:{'Content-Type':'application/json',Accept:'application/json',...(xsrf?{'X-XSRF-TOKEN':xsrf}:{})},body:JSON.stringify({username,password})});
   if(!stillCurrent())return false;
   if(!response.ok||!data?.success||!validSession(data.user||null)){setError(data?.message||'Authentication failed. Please check your credentials.');return false;}
   // A successful credential response alone never grants workspace access.
   const verified=await fetchJsonWithTimeout<User>(getApiUrl('/api/auth/me'),{credentials:'include',headers:{Accept:'application/json'},signal:controller.signal});
   if(!stillCurrent())return false;
   if(!verified.response.ok||!validSession(verified.data)){
    setUser(null);setCsrfToken(null);setError('Your credentials were accepted, but the browser session could not be established. Check cookie settings or contact your administrator.');return false;
   }
   if(verified.data.id!==data.user!.id){setUser(null);setError('Could not verify the signed-in account. Sign in again.');return false;}
   setCsrfToken(null);setUser(verified.data);setError(null);setIsConnectionError(false);
   // Protected mutations obtain a fresh CSRF token before sending. Prefetch need not block navigation.
   prefetchCsrf();return true;
  }catch{if(stillCurrent()){setIsConnectionError(true);setError('Unable to reach authentication server. Please verify backend service.');}return false;}
  finally{if(current===generation.current){active.current=null;setIsLoading(false);}}
 };
 const logout=async():Promise<void>=>{
  cancelActive();const current=generation.current;setIsLoading(true);setIsInitialLoading(false);
  try{const token=getCsrfToken();await fetchWithTimeout(getApiUrl('/api/auth/logout'),{method:'POST',credentials:'include',headers:{Accept:'application/json',...(token?{'X-XSRF-TOKEN':token}:{})}});}catch{/* Clear local session even if the service is unavailable. */}
  finally{if(current===generation.current){setUser(null);setError(null);setIsConnectionError(false);setCsrfToken(null);setIsLoading(false);}}
 };
 const clearError=()=>{setError(null);setIsConnectionError(false);};
 return <AuthContext.Provider value={{user,isAuthenticated:!!user,isLoading,isInitialLoading,error,isConnectionError,login,logout,clearError,retryAuth:restoreSession}}>{children}</AuthContext.Provider>;
};
export const useAuth=():AuthContextType=>{const context=useContext(AuthContext);if(!context)throw new Error('useAuth must be used within an AuthProvider');return context;};

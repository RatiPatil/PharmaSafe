import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { apiRequest, SESSION_EXPIRED_EVENT, setCsrfToken } from '../config/api';
beforeEach(()=>setCsrfToken(null));
afterEach(()=>{vi.unstubAllGlobals();vi.restoreAllMocks();});
describe('workspace API session handling',()=>{
 it('ends the visible session on 401 without replaying a write',async()=>{
  const expired=vi.fn();window.addEventListener(SESSION_EXPIRED_EVENT,expired);
  const fetchMock=vi.fn(async(url:string)=>url.includes('/csrf')?{ok:true,status:200,json:async()=>({token:'fresh'})}:{ok:false,status:401,json:async()=>({})});
  vi.stubGlobal('fetch',fetchMock);
  await expect(apiRequest('/api/admin/users','POST',{username:'synthetic'})).rejects.toMatchObject({status:401});
  expect(fetchMock).toHaveBeenCalledTimes(2);expect(expired).toHaveBeenCalledTimes(1);
  window.removeEventListener(SESSION_EXPIRED_EVENT,expired);
 });
 it('uses a fresh CSRF token and session credentials for state changes',async()=>{
  setCsrfToken('stale');const calls:RequestInit[]=[];
  vi.stubGlobal('fetch',vi.fn(async(url:string,options:RequestInit)=>{calls.push(options);return{ok:true,status:200,json:async()=>url.includes('/csrf')?{token:'fresh'}:{saved:true}};}));
  await expect(apiRequest('/api/example','POST',{value:1})).resolves.toEqual({saved:true});
  expect(calls[1].credentials).toBe('include');expect(calls[1].headers).toMatchObject({'X-XSRF-TOKEN':'fresh'});
 });
 it('does not mistake forbidden access for an expired login',async()=>{
  const expired=vi.fn();window.addEventListener(SESSION_EXPIRED_EVENT,expired);
  vi.stubGlobal('fetch',vi.fn(async()=>({ok:false,status:403,json:async()=>({message:'Access denied'})})));
  await expect(apiRequest('/api/admin/users')).rejects.toMatchObject({status:403});expect(expired).not.toHaveBeenCalled();
  window.removeEventListener(SESSION_EXPIRED_EVENT,expired);
 });
});

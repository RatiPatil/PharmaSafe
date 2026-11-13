import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { ShieldCheck, Lock, User, Eye, EyeOff, AlertCircle, AlertTriangle, Loader2, RefreshCw, ArrowRight } from 'lucide-react';
import './LoginPage.css';
import { ROLE_ADMIN_USERNAMES } from './roleAdminAccounts';

export const LoginPage: React.FC<{applicationRole?:string;roleName?:string;adminLogin?:boolean;registerHref?:string}> = ({applicationRole,roleName,adminLogin=false,registerHref}) => {
  const { login, error, clearError, isLoading, isConnectionError, isInitialLoading } = useAuth();

  useEffect(()=>{if(applicationRole)clearError();},[applicationRole,adminLogin]);
  const [username, setUsername] = useState(adminLogin && applicationRole ? ROLE_ADMIN_USERNAMES[applicationRole] || '' : '');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [clientErrors, setClientErrors] = useState<{ username?: string; password?: string }>({});

  useEffect(() => {
    setUsername(adminLogin && applicationRole ? ROLE_ADMIN_USERNAMES[applicationRole] || '' : '');
    setPassword('');
    setShowPassword(false);
    setClientErrors({});
  }, [applicationRole, adminLogin]);

  const validate = (): boolean => {
    const errors: { username?: string; password?: string } = {};
    if (!username.trim()) {
      errors.username = 'Username is required';
    }
    if (username.trim().length>254) errors.username='Maximum 254 characters.';
    if (username.includes('@')&&!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(username.trim())) errors.username='Enter a valid official email address.';
    if (!password) {
      errors.password = 'Password is required';
    }
    if(new TextEncoder().encode(password).length>72)errors.password='Maximum 72 UTF-8 bytes.';
    setClientErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    clearError();

    if (!validate()) {
      return;
    }

    if(applicationRole)await login(username.trim(),password,{applicationRole,loginMode:adminLogin?'ROLE_ADMIN':'USER'});else await login(username.trim(),password);
  };

  const isLockedAccount = Boolean(error && error.toLowerCase().includes('lock'));
  const isInactiveAccount = Boolean(error && error.toLowerCase().includes('inactive'));
  const isConnFailure = Boolean(
    isConnectionError ||
    (error && (
      error.toLowerCase().includes('unable to reach') ||
      error.toLowerCase().includes('timed out') ||
      error.toLowerCase().includes('did not respond') ||
      error.toLowerCase().includes('service did not respond')
    ))
  );

  return (
    <main className="login-page">
      <div className="login-panel">
      <aside className="access-photo login-photo" aria-label="Connected clinical care">
        <img src="/images/clinical-team.webp" alt="Healthcare professional using a digital device in a clinical setting" width="1200" height="800" fetchPriority="high"/>
        <div className="access-photo-copy"><span><ShieldCheck size={17}/> PHARMASAFE SAFETY SUITE</span><h2>Connected care.<br/>Confident decisions.</h2><p>A clear view of the safety information that matters to your team.</p></div>
      </aside>
      <section className="login-access" aria-labelledby="login-title">
        <div className="login-access-top"><span role={isInitialLoading?'status':undefined}>{isInitialLoading?<Loader2 size={14} className="login-spinner" aria-hidden="true"/>:<Lock size={14} aria-hidden="true"/>}{isInitialLoading?'Checking existing session…':'Authorized access'}</span></div>
        <div className="login-form-container">
          <div className="login-heading"><span className="login-heading-icon"><Lock size={22} aria-hidden="true"/></span><p className="login-eyebrow">YOUR SAFETY WORKSPACE</p><h1 id="login-title">{roleName?`${roleName}${adminLogin?' Admin':''} sign in`:'Sign in to PharmaSafe'}</h1><p>{roleName?`Use your approved ${adminLogin?'Role Admin':'employee'} account for this role.`:'Access your approved safety workspace.'}</p></div>
          <p className="login-recovery-links"><a href={registerHref||"#/home"}>Request access</a><a href="#/forgot-password">Forgot password?</a></p><form onSubmit={handleSubmit} className="login-form" noValidate aria-busy={isLoading}>
            {error && <div role="alert" className={`login-alert ${isLockedAccount ? 'login-alert-warning' : isConnFailure ? 'login-alert-connection' : 'login-alert-error'}`}>
              {isLockedAccount ? <AlertTriangle size={19} aria-hidden="true"/> : <AlertCircle size={19} aria-hidden="true"/>}
              <div><strong>{isLockedAccount ? 'Account Locked' : isInactiveAccount ? 'Account Inactive' : isConnFailure ? 'Connection interrupted' : 'Authentication Failure'}</strong><p>{error}</p>{isConnFailure && <><button type="button" disabled={isLoading} onClick={handleSubmit} className="login-retry"><RefreshCw size={14} aria-hidden="true"/>Retry Authentication</button><p className="login-alert-hint">The service may take a little longer to start. Your entries have been kept.</p></>}</div>
            </div>}
            <div className="login-field"><label htmlFor="username">Username or official email</label><div className={`login-input-wrap ${clientErrors.username ? 'login-input-invalid' : ''}`}><User size={19} aria-hidden="true"/><input id="username" name="username" type="text" autoComplete="username" autoCapitalize="none" spellCheck={false} autoFocus required disabled={isLoading} value={username} aria-invalid={!!clientErrors.username} aria-describedby={clientErrors.username ? 'username-error' : undefined} placeholder="Enter your username" onChange={e=>{setUsername(e.target.value);if(clientErrors.username)setClientErrors(prev=>({...prev,username:undefined}));}}/></div>{clientErrors.username&&<p id="username-error" className="login-field-error">{clientErrors.username}</p>}</div>
            <div className="login-field"><label htmlFor="password">Password</label><div className={`login-input-wrap ${clientErrors.password ? 'login-input-invalid' : ''}`}><Lock size={19} aria-hidden="true"/><input id="password" name="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" required disabled={isLoading} value={password} aria-invalid={!!clientErrors.password} aria-describedby={clientErrors.password ? 'password-error' : undefined} placeholder="Enter your password" onChange={e=>{setPassword(e.target.value);if(clientErrors.password)setClientErrors(prev=>({...prev,password:undefined}));}}/><button type="button" disabled={isLoading} aria-label={showPassword ? 'Hide password' : 'Show password'} aria-pressed={showPassword} className="login-password-toggle" onClick={()=>setShowPassword(v=>!v)}>{showPassword ? <EyeOff size={19}/> : <Eye size={19}/>}</button></div>{clientErrors.password&&<p id="password-error" className="login-field-error">{clientErrors.password}</p>}</div>
            <button type="submit" className="login-submit" disabled={isLoading}>{isLoading ? <><Loader2 size={19} className="login-spinner" aria-hidden="true"/><span>Signing in…</span></> : <><span>Log In to PharmaSafe</span><ArrowRight size={18} aria-hidden="true"/></>}</button>
            {isLoading&&<p role="status" className="login-progress">Connecting to your workspace. Please allow a moment for the service to respond.</p>}
          </form>
          <div className="login-help"><ShieldCheck size={17} aria-hidden="true"/><p>Access is available after administrator approval.</p></div>
        </div>
        <footer className="login-footer"><span>PharmaSafe Safety Suite</span><span>For authorized users only</span></footer>
      </section>
      </div>
    </main>
  );
};

export default LoginPage;

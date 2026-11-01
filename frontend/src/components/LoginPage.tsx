import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { ShieldCheck, Lock, User, Eye, EyeOff, AlertCircle, AlertTriangle, Loader2, RefreshCw, ArrowRight, Check, FileText } from 'lucide-react';
import './LoginPage.css';

export const LoginPage: React.FC = () => {
  const { login, error, clearError, isLoading, isConnectionError, isInitialLoading } = useAuth();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [clientErrors, setClientErrors] = useState<{ username?: string; password?: string }>({});

  const validate = (): boolean => {
    const errors: { username?: string; password?: string } = {};
    if (!username.trim()) {
      errors.username = 'Username is required';
    }
    if (!password) {
      errors.password = 'Password is required';
    }
    setClientErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    clearError();

    if (!validate()) {
      return;
    }

    await login(username.trim(), password);
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
      <section className="login-story" aria-label="PharmaSafe">
        <div className="login-brand"><span className="login-brand-icon"><ShieldCheck aria-hidden="true" size={24}/></span><span>PharmaSafe<span className="login-brand-caption">SAFETY SUITE</span></span></div>
        <div className="login-story-content">
          <span className="login-kicker">CONNECTED CASE MANAGEMENT</span>
          <h2>Clarity for every<br/>safety case.</h2>
          <p>A focused workspace for your team to capture, review and manage safety information.</p>
          <div className="login-workflow" aria-hidden="true">
            <div className="login-workflow-heading"><span className="login-document-icon"><FileText size={21}/></span><div><strong>One case. A connected workflow.</strong><span>From intake to review and reporting</span></div><span className="login-workflow-dot"/></div>
            <div className="login-paper-lines"><span/><span/><span/></div>
            <div className="login-workflow-steps"><span><i><Check size={13}/></i>Capture</span><span><i><Check size={13}/></i>Review</span><span><i><ArrowRight size={13}/></i>Report</span></div>
          </div>
        </div>
        <p className="login-story-footer">Pharmacovigilance case management</p>
        <div className="login-orbit login-orbit-one" aria-hidden="true"/><div className="login-orbit login-orbit-two" aria-hidden="true"/>
      </section>
      <section className="login-access" aria-labelledby="login-title">
        <div className="login-access-top"><span role={isInitialLoading?'status':undefined}>{isInitialLoading?<Loader2 size={14} className="login-spinner" aria-hidden="true"/>:<Lock size={14} aria-hidden="true"/>}{isInitialLoading?'Checking existing session…':'Authorized access'}</span></div>
        <div className="login-form-container">
          <div className="login-heading"><span className="login-heading-icon"><Lock size={22} aria-hidden="true"/></span><p className="login-eyebrow">YOUR SAFETY WORKSPACE</p><h1 id="login-title">Welcome back.</h1><p>Sign in to your PharmaSafe workspace.</p></div>
          <form onSubmit={handleSubmit} className="login-form" noValidate aria-busy={isLoading}>
            {error && <div role="alert" className={`login-alert ${isLockedAccount ? 'login-alert-warning' : isConnFailure ? 'login-alert-connection' : 'login-alert-error'}`}>
              {isLockedAccount ? <AlertTriangle size={19} aria-hidden="true"/> : <AlertCircle size={19} aria-hidden="true"/>}
              <div><strong>{isLockedAccount ? 'Account Locked' : isInactiveAccount ? 'Account Inactive' : isConnFailure ? 'Connection interrupted' : 'Authentication Failure'}</strong><p>{error}</p>{isConnFailure && <><button type="button" disabled={isLoading} onClick={handleSubmit} className="login-retry"><RefreshCw size={14} aria-hidden="true"/>Retry Authentication</button><p className="login-alert-hint">The service may take a little longer to start. Your entries have been kept.</p></>}</div>
            </div>}
            <div className="login-field"><label htmlFor="username">Username</label><div className={`login-input-wrap ${clientErrors.username ? 'login-input-invalid' : ''}`}><User size={19} aria-hidden="true"/><input id="username" name="username" type="text" autoComplete="username" autoCapitalize="none" spellCheck={false} autoFocus required disabled={isLoading} value={username} aria-invalid={!!clientErrors.username} aria-describedby={clientErrors.username ? 'username-error' : undefined} placeholder="Enter your username" onChange={e=>{setUsername(e.target.value);if(clientErrors.username)setClientErrors(prev=>({...prev,username:undefined}));}}/></div>{clientErrors.username&&<p id="username-error" className="login-field-error">{clientErrors.username}</p>}</div>
            <div className="login-field"><label htmlFor="password">Password</label><div className={`login-input-wrap ${clientErrors.password ? 'login-input-invalid' : ''}`}><Lock size={19} aria-hidden="true"/><input id="password" name="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" required disabled={isLoading} value={password} aria-invalid={!!clientErrors.password} aria-describedby={clientErrors.password ? 'password-error' : undefined} placeholder="Enter your password" onChange={e=>{setPassword(e.target.value);if(clientErrors.password)setClientErrors(prev=>({...prev,password:undefined}));}}/><button type="button" disabled={isLoading} aria-label={showPassword ? 'Hide password' : 'Show password'} aria-pressed={showPassword} className="login-password-toggle" onClick={()=>setShowPassword(v=>!v)}>{showPassword ? <EyeOff size={19}/> : <Eye size={19}/>}</button></div>{clientErrors.password&&<p id="password-error" className="login-field-error">{clientErrors.password}</p>}</div>
            <button type="submit" className="login-submit" disabled={isLoading}>{isLoading ? <><Loader2 size={19} className="login-spinner" aria-hidden="true"/><span>Signing in…</span></> : <><span>Log In to PharmaSafe</span><ArrowRight size={18} aria-hidden="true"/></>}</button>
            {isLoading&&<p role="status" className="login-progress">Connecting to your workspace. Please allow a moment for the service to respond.</p>}
          </form>
          <div className="login-help"><ShieldCheck size={17} aria-hidden="true"/><p>Use the account provided by your administrator.<br/>Your role determines your workspace.</p></div>
        </div>
        <footer className="login-footer"><span>PharmaSafe Safety Suite</span><span>For authorized users only</span></footer>
      </section>
    </main>
  );
};

export default LoginPage;

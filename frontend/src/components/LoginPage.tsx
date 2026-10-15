import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { ShieldCheck, Lock, User, Eye, EyeOff, AlertCircle, AlertTriangle, Loader2 } from 'lucide-react';

export const LoginPage: React.FC = () => {
  const { login, error, clearError, isLoading } = useAuth();

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

  const isLockedAccount = error && error.toLowerCase().includes('lock');
  const isInactiveAccount = error && error.toLowerCase().includes('inactive');

  return (
    <div className="min-h-screen bg-[#E9EEF4] flex flex-col justify-between font-sans text-slate-800">
      {/* Argus Style Top Enterprise Header Banner */}
      <header className="argus-header-bar px-6 py-2.5 flex items-center justify-between shadow-sm">
        <div className="flex items-center space-x-3">
          <div className="bg-white/10 p-1.5 rounded border border-white/20">
            <ShieldCheck className="w-5 h-5 text-sky-300" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <span className="font-bold text-base tracking-wide text-white">PharmaSafe</span>
              <span className="text-[10px] bg-sky-500/30 text-sky-200 border border-sky-400/30 px-1.5 py-0.5 rounded font-mono">
                SAFETY MANAGEMENT
              </span>
            </div>
            <p className="text-[11px] text-sky-200/80 font-normal">
              Pharmacovigilance &amp; Adverse Event Reporting System
            </p>
          </div>
        </div>
        <div className="text-right text-[11px] text-sky-100 hidden sm:block">
          <div>Oracle Argus Safety 7.0.3 Architecture Baseline</div>
          <div className="text-sky-300 font-mono text-[10px]">Version 1.2.0-STAGE2 (Secure Auth)</div>
        </div>
      </header>

      {/* Main Login Card Container */}
      <main className="flex-1 flex items-center justify-center p-4">
        <div className="w-full max-w-md bg-white border border-[#B8CCE4] rounded shadow-lg overflow-hidden">
          {/* Card Header */}
          <div className="argus-sub-header px-5 py-3 border-b border-[#B8CCE4] flex items-center justify-between">
            <div className="flex items-center space-x-2 text-[#0E3D6B]">
              <Lock className="w-4 h-4 text-blue-700" />
              <span className="font-bold text-xs uppercase tracking-wider">System User Authentication</span>
            </div>
            <span className="text-[11px] text-slate-500 font-mono">Restricted Access</span>
          </div>

          <form onSubmit={handleSubmit} className="p-6 space-y-4" noValidate>
            {/* Global Server Error Banner */}
            {error && (
              <div
                role="alert"
                className={`p-3 rounded border text-xs flex items-start space-x-2.5 ${
                  isLockedAccount
                    ? 'bg-amber-50 border-amber-300 text-amber-900'
                    : isInactiveAccount
                    ? 'bg-slate-100 border-slate-300 text-slate-800'
                    : 'bg-rose-50 border-rose-300 text-rose-900'
                }`}
              >
                {isLockedAccount ? (
                  <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                ) : (
                  <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                )}
                <div>
                  <div className="font-semibold mb-0.5">
                    {isLockedAccount
                      ? 'Account Locked'
                      : isInactiveAccount
                      ? 'Account Inactive'
                      : 'Authentication Failure'}
                  </div>
                  <div>{error}</div>
                </div>
              </div>
            )}

            {/* Username Input */}
            <div>
              <label htmlFor="username" className="block text-xs font-semibold text-slate-700 mb-1">
                Username <span className="text-rose-600">*</span>
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                  <User className="w-4 h-4" />
                </div>
                <input
                  id="username"
                  name="username"
                  type="text"
                  autoComplete="username"
                  autoFocus
                  disabled={isLoading}
                  value={username}
                  onChange={(e) => {
                    setUsername(e.target.value);
                    if (clientErrors.username) {
                      setClientErrors((prev) => ({ ...prev, username: undefined }));
                    }
                  }}
                  className={`w-full pl-9 pr-3 py-1.5 text-xs bg-slate-50 border rounded focus:bg-white focus:outline-none focus:ring-1 focus:ring-blue-600 transition ${
                    clientErrors.username ? 'border-rose-500 bg-rose-50/50' : 'border-[#B8CCE4]'
                  }`}
                  placeholder="Enter your system username"
                />
              </div>
              {clientErrors.username && (
                <p className="mt-1 text-[11px] text-rose-600 font-medium">{clientErrors.username}</p>
              )}
            </div>

            {/* Password Input */}
            <div>
              <label htmlFor="password" className="block text-xs font-semibold text-slate-700 mb-1">
                Password <span className="text-rose-600">*</span>
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                  <Lock className="w-4 h-4" />
                </div>
                <input
                  id="password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  disabled={isLoading}
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (clientErrors.password) {
                      setClientErrors((prev) => ({ ...prev, password: undefined }));
                    }
                  }}
                  className={`w-full pl-9 pr-10 py-1.5 text-xs bg-slate-50 border rounded focus:bg-white focus:outline-none focus:ring-1 focus:ring-blue-600 transition ${
                    clientErrors.password ? 'border-rose-500 bg-rose-50/50' : 'border-[#B8CCE4]'
                  }`}
                  placeholder="Enter your password"
                />
                <button
                  type="button"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute inset-y-0 right-0 pr-3 flex items-center text-slate-400 hover:text-slate-600 cursor-pointer"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
              {clientErrors.password && (
                <p className="mt-1 text-[11px] text-rose-600 font-medium">{clientErrors.password}</p>
              )}
            </div>

            {/* Submit Button */}
            <div className="pt-2">
              <button
                type="submit"
                disabled={isLoading}
                className="w-full bg-[#124B8A] hover:bg-[#0E3D6B] active:bg-[#092B4D] text-white py-2 px-4 rounded text-xs font-semibold shadow transition flex items-center justify-center space-x-2 cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin text-sky-200" />
                    <span>Verifying Credentials...</span>
                  </>
                ) : (
                  <>
                    <Lock className="w-3.5 h-3.5 text-sky-200" />
                    <span>Log In to PharmaSafe</span>
                  </>
                )}
              </button>
            </div>

            {/* Enterprise Security Notice */}
            <div className="pt-3 border-t border-slate-200 text-[11px] text-slate-500 leading-relaxed">
              <p>
                <strong>Security Notice:</strong> Consecutive failed login attempts will trigger automatic account lockout.
                Audit records with timestamps and network identifiers are logged for all access events.
              </p>
            </div>
          </form>
        </div>
      </main>

      {/* Argus Style Enterprise Footer */}
      <footer className="bg-slate-200 border-t border-[#B8CCE4] px-6 py-2 text-[11px] text-slate-600 flex flex-col sm:flex-row items-center justify-between space-y-1 sm:space-y-0">
        <div>
          <span>PharmaSafe Pharmacovigilance Case Management</span> &bull; <span>Inspired by Oracle Argus Safety</span>
        </div>
        <div className="font-mono text-slate-500 text-[10px]">
          Session Security: HttpOnly Cookies &bull; CSRF Protected &bull; BCrypt Hashing
        </div>
      </footer>
    </div>
  );
};

export default LoginPage;

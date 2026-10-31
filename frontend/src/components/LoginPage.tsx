import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { ShieldCheck, Lock, User, Eye, EyeOff, AlertCircle, AlertTriangle, Loader2, RefreshCw } from 'lucide-react';

export const LoginPage: React.FC = () => {
  const { login, error, clearError, isLoading, isConnectionError } = useAuth();

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
    <div className="min-h-screen bg-[#F4F7FB] flex flex-col justify-between font-sans text-[#172B4D]">
      {/* Top Navigation Bar */}
      <header className="bg-[#0B1F33] border-b border-[#1A3654] px-6 py-3.5 flex items-center justify-between shadow-xs">
        <div className="flex items-center space-x-3">
          <div className="bg-[#102A43] p-2 rounded-lg border border-[#234567]">
            <ShieldCheck className="w-5 h-5 text-[#60A5FA]" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <span className="font-bold text-base tracking-tight text-white">PharmaSafe</span>
              <span className="text-[11px] bg-[#1E3A5F] text-[#93C5FD] border border-[#2563EB]/40 px-2 py-0.5 rounded font-medium">
                SAFETY WORKSPACE
              </span>
            </div>
            <p className="text-xs text-[#94A3B8] font-normal">
              Pharmacovigilance &amp; Adverse Event Reporting System
            </p>
          </div>
        </div>
        <div className="text-right text-xs text-[#94A3B8] hidden sm:block">
          <div className="font-medium text-[#E2E8F0]">PharmaSafe Safety Workspace</div>
          <div className="text-[#60A5FA] font-mono text-[11px]">Role-based access</div>
        </div>
      </header>

      {/* Main Login Card */}
      <main className="flex-1 flex items-center justify-center p-4 sm:p-6">
        <div className="w-full max-w-md bg-white border border-[#DCE4EE] rounded-xl shadow-sm overflow-hidden">
          {/* Card Header */}
          <div className="bg-[#F8FAFD] px-6 py-4 border-b border-[#DCE4EE] flex items-center justify-between">
            <div className="flex items-center space-x-2.5 text-[#102A43]">
              <Lock className="w-4 h-4 text-[#2563EB]" />
              <h1 className="font-semibold text-sm tracking-normal">System User Authentication</h1>
            </div>
            <span className="text-xs text-[#52657A] bg-[#EBF1F7] px-2 py-0.5 rounded font-medium">
              Restricted Access
            </span>
          </div>

          <form onSubmit={handleSubmit} className="p-6 sm:p-7 space-y-5" noValidate>
            {/* Global Server Error Alert */}
            {error && (
              <div
                role="alert"
                className={`p-3.5 rounded-lg border text-xs flex items-start space-x-3 ${
                  isLockedAccount
                    ? 'bg-[#FFFBEB] border-[#FDE68A] text-[#92400E]'
                    : isInactiveAccount
                    ? 'bg-[#F1F5F9] border-[#CBD5E1] text-[#334155]'
                    : isConnFailure
                    ? 'bg-[#EFF6FF] border-[#BFDBFE] text-[#1E40AF]'
                    : 'bg-[#FEF2F2] border-[#FECACA] text-[#991B1B]'
                }`}
              >
                {isLockedAccount ? (
                  <AlertTriangle className="w-4 h-4 text-[#D97706] shrink-0 mt-0.5" />
                ) : isConnFailure ? (
                  <AlertCircle className="w-4 h-4 text-[#2563EB] shrink-0 mt-0.5" />
                ) : (
                  <AlertCircle className="w-4 h-4 text-[#DC2626] shrink-0 mt-0.5" />
                )}
                <div className="space-y-1.5 flex-1">
                  <div className="font-semibold">
                    {isLockedAccount
                      ? 'Account Locked'
                      : isInactiveAccount
                      ? 'Account Inactive'
                      : 'Authentication Failure'}
                  </div>
                  <div className="leading-relaxed">{error}</div>
                  {isConnFailure && (
                    <div className="pt-1 flex flex-wrap items-center gap-2">
                      <button
                        type="button"
                        disabled={isLoading}
                        onClick={handleSubmit}
                        className="inline-flex items-center space-x-1.5 bg-[#102A43] hover:bg-[#1E3A5F] text-white px-2.5 py-1 rounded-md text-xs font-semibold cursor-pointer transition disabled:opacity-50"
                      >
                        <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
                        <span>Retry Authentication</span>
                      </button>
                      <span className="text-[11px] text-[#52657A]">
                        Render free instances take 30-50s to wake from cold sleep.
                      </span>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Username Input */}
            <div>
              <label htmlFor="username" className="block text-xs font-semibold text-[#172B4D] mb-1.5">
                Username <span className="text-[#DC2626]">*</span>
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-[#7A8B9E]">
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
                  className={`w-full pl-10 pr-3.5 py-2 text-sm bg-white border rounded-lg text-[#172B4D] placeholder-[#94A3B8] transition focus:outline-none focus:ring-2 focus:ring-[#2563EB]/20 focus:border-[#2563EB] ${
                    clientErrors.username ? 'border-[#DC2626] bg-[#FEF2F2]/30' : 'border-[#DCE4EE]'
                  }`}
                  placeholder="Enter your system username"
                />
              </div>
              {clientErrors.username && (
                <p className="mt-1.5 text-xs text-[#DC2626] font-medium">{clientErrors.username}</p>
              )}
            </div>

            {/* Password Input */}
            <div>
              <label htmlFor="password" className="block text-xs font-semibold text-[#172B4D] mb-1.5">
                Password <span className="text-[#DC2626]">*</span>
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-[#7A8B9E]">
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
                  className={`w-full pl-10 pr-10 py-2 text-sm bg-white border rounded-lg text-[#172B4D] placeholder-[#94A3B8] transition focus:outline-none focus:ring-2 focus:ring-[#2563EB]/20 focus:border-[#2563EB] ${
                    clientErrors.password ? 'border-[#DC2626] bg-[#FEF2F2]/30' : 'border-[#DCE4EE]'
                  }`}
                  placeholder="Enter your password"
                />
                <button
                  type="button"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-[#7A8B9E] hover:text-[#172B4D] transition cursor-pointer"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
              {clientErrors.password && (
                <p className="mt-1.5 text-xs text-[#DC2626] font-medium">{clientErrors.password}</p>
              )}
            </div>

            {/* Submit Button */}
            <div className="pt-2">
              <button
                type="submit"
                disabled={isLoading}
                className="w-full bg-[#2563EB] hover:bg-[#1D4ED8] active:bg-[#1E40AF] text-white py-2.5 px-4 rounded-lg text-sm font-semibold shadow-xs transition-colors flex items-center justify-center space-x-2 cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin text-white" />
                    <span>Verifying Credentials (Server warming up)...</span>
                  </>
                ) : (
                  <>
                    <Lock className="w-4 h-4 text-blue-100" />
                    <span>Log In to PharmaSafe</span>
                  </>
                )}
              </button>
            </div>

            {/* Security Notice */}
            <div className="pt-3 border-t border-[#DCE4EE] text-xs text-[#52657A] leading-relaxed">
              <p>
                <strong className="text-[#102A43]">Security Policy:</strong> Sign-in attempts and case changes are recorded. Access is restricted to authorized users.
              </p>
            </div>
          </form>
        </div>
      </main>

      {/* Modern Footer */}
      <footer className="bg-white border-t border-[#DCE4EE] px-6 py-3 text-xs text-[#52657A] flex flex-col sm:flex-row items-center justify-between gap-2">
        <div className="flex items-center space-x-2">
          <span className="font-medium text-[#102A43]">PharmaSafe</span>
          <span>&bull;</span>
          <span>Pharmacovigilance Case Management</span>
          <span>&bull;</span>
          <span>Inspired by Oracle Argus Safety</span>
        </div>
        <div className="font-mono text-[#7A8B9E] text-[11px]">
          Session Security: HttpOnly Cookies &bull; CSRF Protected &bull; BCrypt Hashing
        </div>
      </footer>
    </div>
  );
};

export default LoginPage;

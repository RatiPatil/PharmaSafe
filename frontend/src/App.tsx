import React from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { LoginPage } from './components/LoginPage';
import { Stage3Dashboard } from './components/Stage3Dashboard';
import { Loader2 } from 'lucide-react';

const AppContent: React.FC = () => {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#F4F7FB] flex flex-col items-center justify-center font-sans text-[#172B4D]">
        <div className="bg-white px-6 py-5 rounded-xl border border-[#DCE4EE] shadow-sm flex items-center space-x-3">
          <Loader2 className="w-5 h-5 animate-spin text-[#2563EB]" />
          <span className="text-sm font-medium text-[#102A43]">Verifying PharmaSafe Session Security...</span>
        </div>
      </div>
    );
  }

  return isAuthenticated ? <Stage3Dashboard /> : <LoginPage />;
};

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
};

export default App;

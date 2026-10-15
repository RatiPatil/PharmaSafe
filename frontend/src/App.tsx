import React from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { LoginPage } from './components/LoginPage';
import { Stage3Dashboard } from './components/Stage3Dashboard';
import { Loader2 } from 'lucide-react';

const AppContent: React.FC = () => {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#E9EEF4] flex flex-col items-center justify-center font-sans text-slate-700">
        <div className="bg-white p-6 rounded border border-[#B8CCE4] shadow-md flex items-center space-x-3">
          <Loader2 className="w-5 h-5 animate-spin text-[#124B8A]" />
          <span className="text-xs font-semibold">Verifying PharmaSafe Session Security...</span>
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

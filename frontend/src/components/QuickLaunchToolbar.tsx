import React, { useState, useEffect } from 'react';
import {
  FileImage,
  FilePlus,
  FolderOpen,
  XSquare,
  Printer,
  Save,
  CornerUpRight,
  CornerUpLeft,
  ListChecks,
  Lock,
  Stethoscope,
  Binary,
  FileText,
  FileCheck2,
  CheckSquare,
  Info
} from 'lucide-react';

export interface QuickAction {
  id: string;
  name: string;
  tooltip: string;
  shortcut: string;
  shortcutKey: string;
  icon: React.ComponentType<{ className?: string }>;
  stage: string;
  enabled: boolean;
}

export const QUICK_ACTIONS: QuickAction[] = [
  {
    id: 'new-image',
    name: 'New Case from Image',
    tooltip: 'Displays a new case from an image (Argus Quick Launch)',
    shortcut: 'CTRL+ALT+G',
    shortcutKey: 'g',
    icon: FileImage,
    stage: 'Stage 6',
    enabled: false
  },
  {
    id: 'new-case',
    name: 'New Case',
    tooltip: 'Displays the Initial Case Entry dialog (Argus Quick Launch)',
    shortcut: 'CTRL+ALT+N',
    shortcutKey: 'n',
    icon: FilePlus,
    stage: 'Stage 4',
    enabled: true
  },
  {
    id: 'open-case',
    name: 'Open Case',
    tooltip: 'Displays the Case Search dialog (Argus Quick Launch)',
    shortcut: 'CTRL+ALT+O',
    shortcutKey: 'o',
    icon: FolderOpen,
    stage: 'Stage 4',
    enabled: true
  },
  {
    id: 'close-case',
    name: 'Close Case',
    tooltip: 'Closes the current active case (Argus Quick Launch)',
    shortcut: 'CTRL+ALT+C',
    shortcutKey: 'c',
    icon: XSquare,
    stage: 'Stage 4',
    enabled: true
  },
  {
    id: 'print-case',
    name: 'Print Case',
    tooltip: 'Displays the Case Print dialog (Argus Quick Launch)',
    shortcut: 'CTRL+ALT+P',
    shortcutKey: 'p',
    icon: Printer,
    stage: 'Stage 5',
    enabled: false
  },
  {
    id: 'save-case',
    name: 'Save Case',
    tooltip: 'Saves the case with any changes made (Argus Quick Launch)',
    shortcut: 'CTRL+ALT+S',
    shortcutKey: 's',
    icon: Save,
    stage: 'Stage 4',
    enabled: true
  },
  {
    id: 'forward-case',
    name: 'Forward Case',
    tooltip: 'Displays the Case Routing dialog to forward the case (Argus Quick Launch)',
    shortcut: 'CTRL+ALT+>',
    shortcutKey: '.',
    icon: CornerUpRight,
    stage: 'Stage 5',
    enabled: false
  },
  {
    id: 'return-case',
    name: 'Return Case',
    tooltip: 'Displays the Case Routing dialog to return the case (Argus Quick Launch)',
    shortcut: 'CTRL+ALT+<',
    shortcutKey: ',',
    icon: CornerUpLeft,
    stage: 'Stage 5',
    enabled: false
  },
  {
    id: 'worklist',
    name: 'Worklist',
    tooltip: 'Displays the Worklist dialog (Argus Quick Launch)',
    shortcut: 'CTRL+ALT+W',
    shortcutKey: 'w',
    icon: ListChecks,
    stage: 'Stage 3 Active',
    enabled: true
  },
  {
    id: 'lock-case',
    name: 'Lock Case',
    tooltip: 'Displays the Case Lock or Unlock dialog (Argus Quick Launch)',
    shortcut: 'CTRL+ALT+L',
    shortcutKey: 'l',
    icon: Lock,
    stage: 'Stage 5',
    enabled: false
  },
  {
    id: 'medical-review',
    name: 'Medical Review',
    tooltip: 'Displays the Medical Review dialog (Argus Quick Launch)',
    shortcut: 'CTRL+ALT+M',
    shortcutKey: 'm',
    icon: Stethoscope,
    stage: 'Stage 5',
    enabled: false
  },
  {
    id: 'coding-review',
    name: 'Coding Review',
    tooltip: 'Displays the MedDRA Coding Review dialog (Argus Quick Launch)',
    shortcut: 'CTRL+ALT+Q',
    shortcutKey: 'q',
    icon: Binary,
    stage: 'Stage 5',
    enabled: false
  },
  {
    id: 'draft-report',
    name: 'Draft Report',
    tooltip: 'Displays the Report List of Expedited Reports (Argus Quick Launch)',
    shortcut: 'CTRL+ALT+K',
    shortcutKey: 'k',
    icon: FileText,
    stage: 'Stage 6',
    enabled: false
  },
  {
    id: 'e2b-check',
    name: 'E2B Check',
    tooltip: 'Performs E2B DTD Length & Validation Check (Argus Quick Launch)',
    shortcut: 'CTRL+ALT+E',
    shortcutKey: 'e',
    icon: FileCheck2,
    stage: 'Stage 6',
    enabled: false
  },
  {
    id: 'validation-check',
    name: 'Validation Check',
    tooltip: 'Performs full case business logic validation (Argus Quick Launch)',
    shortcut: 'CTRL+ALT+V',
    shortcutKey: 'v',
    icon: CheckSquare,
    stage: 'Stage 5',
    enabled: false
  }
];

interface QuickLaunchToolbarProps {
  onSelectAction?: (action: QuickAction) => void;
}

export const QuickLaunchToolbar: React.FC<QuickLaunchToolbarProps> = ({ onSelectAction }) => {
  const [activeNotification, setActiveNotification] = useState<string | null>(null);

  const handleActionClick = (action: QuickAction) => {
    if (onSelectAction) {
      onSelectAction(action);
    }
    if (!action.enabled) {
      setActiveNotification(
        `[${action.name}] (${action.shortcut}): Scheduled for ${action.stage}. Action is currently disabled.`
      );
    } else {
      setActiveNotification(`[${action.name}]: Activated.`);
    }
  };

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.ctrlKey && e.altKey) {
        const key = e.key.toLowerCase();
        const matched = QUICK_ACTIONS.find(
          (a) => a.shortcutKey.toLowerCase() === key || a.shortcut.toLowerCase().endsWith(key)
        );
        if (matched) {
          e.preventDefault();
          handleActionClick(matched);
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  return (
    <div className="flex flex-col space-y-1.5 w-full max-w-full min-w-0">
      <div className="flex items-center space-x-1.5 overflow-x-auto py-1.5 px-2 bg-white rounded-lg border border-[#DCE4EE] shadow-2xs w-full max-w-full">
        <span className="text-xs font-semibold text-[#102A43] uppercase tracking-wider pr-2 pl-1 border-r border-[#DCE4EE] mr-1 hidden sm:inline select-none shrink-0">
          Quick Launch
        </span>

        {QUICK_ACTIONS.map((action, idx) => {
          const Icon = action.icon;
          const isNearLeftEdge = idx < 2;
          return (
            <button
              key={action.id}
              data-testid={`quick-launch-${action.id}`}
              onClick={() => handleActionClick(action)}
              className="argus-toolbar-btn group relative p-2 rounded-md shrink-0 focus-visible:ring-2 focus-visible:ring-[#2563EB]"
              title={`${action.name} (${action.shortcut}) - ${action.tooltip} [${action.stage}]`}
              aria-label={action.name}
            >
              <Icon className={`w-4 h-4 ${action.enabled ? 'text-[#102A43] group-hover:text-[#2563EB]' : 'text-[#94A3B8]'}`} />

              {/* Modern Tooltip Popup */}
              <div
                className={`absolute top-full mt-2 hidden group-hover:flex flex-col items-center z-50 pointer-events-none whitespace-nowrap ${
                  isNearLeftEdge ? 'left-0' : 'left-1/2 -translate-x-1/2'
                }`}
              >
                <div className="bg-[#0B1F33] text-white text-xs rounded-lg px-2.5 py-1.5 shadow-lg border border-[#1E3A5F] flex flex-col items-center space-y-0.5">
                  <div className="font-semibold flex items-center space-x-1.5">
                    <span>{action.name}</span>
                    <span className="text-[#93C5FD] font-mono text-[10px]">({action.shortcut})</span>
                  </div>
                  <span className="text-[11px] text-[#CBD5E1]">{action.tooltip}</span>
                  {!action.enabled && (
                    <span className="mt-1 text-[10px] bg-[#D97706]/20 text-[#FDE68A] border border-[#D97706]/40 px-1.5 py-0.2 rounded font-medium">
                      Planned: {action.stage}
                    </span>
                  )}
                </div>
              </div>
            </button>
          );
        })}
      </div>

      {/* Ephemeral Feedback Strip */}
      {activeNotification && (
        <div className="text-xs bg-[#FFFBEB] border border-[#FDE68A] text-[#92400E] px-3 py-1.5 rounded-lg flex items-center justify-between animate-fadeIn shadow-2xs">
          <div className="flex items-center space-x-2">
            <Info className="w-4 h-4 text-[#D97706] flex-shrink-0" />
            <span className="font-medium">{activeNotification}</span>
          </div>
          <button
            onClick={() => setActiveNotification(null)}
            className="text-xs text-[#92400E] hover:text-[#78350F] font-bold ml-3 px-1.5 py-0.5 rounded cursor-pointer"
            aria-label="Dismiss message"
          >
            &times;
          </button>
        </div>
      )}
    </div>
  );
};

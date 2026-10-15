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
  shortcutKey: string; // Key code for keyboard listener
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

  // Keyboard shortcut listener (CTRL + ALT + Key)
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
    <div className="flex flex-col space-y-1 w-full max-w-full min-w-0">
      <div className="flex items-center space-x-1 overflow-x-auto py-1 px-1.5 bg-[#DCE7F5] rounded border border-[#B0C7E2] shadow-inner w-full max-w-full">
        <span className="text-[10px] font-bold text-[#0E3D6B] uppercase tracking-wider pr-1 pl-0.5 border-r border-[#B8CCE4] mr-1 hidden sm:inline select-none shrink-0">
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
              className="argus-toolbar-btn group relative p-1.5 shrink-0"
              title={`${action.name} (${action.shortcut}) - ${action.tooltip} [${action.stage}]`}
              aria-label={action.name}
            >
              <Icon className={`w-3.5 h-3.5 ${action.enabled ? 'text-[#0E3D6B]' : 'text-slate-500'}`} />

              {/* Hover Tooltip Popup - positioned safely away from left edge */}
              <div
                className={`absolute top-full mt-1.5 hidden group-hover:flex flex-col items-center z-50 pointer-events-none whitespace-nowrap ${
                  isNearLeftEdge ? 'left-0' : 'left-1/2 -translate-x-1/2'
                }`}
              >
                <div className="bg-[#0E3D6B] text-white text-[10px] rounded px-2 py-1 shadow-md border border-[#19589D] flex flex-col items-center">
                  <div className="font-bold flex items-center space-x-1">
                    <span>{action.name}</span>
                    <span className="text-sky-300 font-mono text-[9px]">({action.shortcut})</span>
                  </div>
                  <span className="text-[9px] text-sky-100">{action.tooltip}</span>
                  {!action.enabled && (
                    <span className="mt-0.5 text-[8px] bg-amber-500/30 text-amber-200 border border-amber-400/30 px-1 rounded">
                      Planned: {action.stage}
                    </span>
                  )}
                </div>
              </div>
            </button>
          );
        })}
      </div>

      {/* Ephemeral Feedback Strip if an action is clicked */}
      {activeNotification && (
        <div className="text-[11px] bg-amber-50 border border-amber-200 text-amber-900 px-2 py-1 rounded flex items-center justify-between animate-fadeIn">
          <div className="flex items-center space-x-1.5">
            <Info className="w-3.5 h-3.5 text-amber-600 flex-shrink-0" />
            <span>{activeNotification}</span>
          </div>
          <button
            onClick={() => setActiveNotification(null)}
            className="text-[10px] text-amber-700 hover:text-amber-900 font-bold ml-2 cursor-pointer"
          >
            &times;
          </button>
        </div>
      )}
    </div>
  );
};

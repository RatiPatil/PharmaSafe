import React, { useState, useEffect, useMemo } from 'react';
import type { SafetyCaseDetails, CaseFormInput } from '../types/case';
import { getXsrfToken, useAuth } from '../context/AuthContext';
import { getApiUrl } from '../config/api';
import {
  Save,
  FileCheck,
  XCircle,
  Plus,
  Trash2,
  AlertTriangle,
  Loader2,
  User,
  Pill,
  Activity,
  FileText,
  Bookmark,
  Paperclip,
  Stethoscope,
  Info
} from 'lucide-react';

const getTodayDateString = (): string => new Date().toISOString().split('T')[0];

interface CaseEntryFormProps {
  initialCase?: SafetyCaseDetails | null;
  onSaveSuccess: (savedCase: SafetyCaseDetails) => void;
  onCancel: () => void;
  saveTrigger?: number; // External save signal from QuickLaunch toolbar
}

export const CaseEntryForm: React.FC<CaseEntryFormProps> = ({
  initialCase,
  onSaveSuccess,
  onCancel,
  saveTrigger
}) => {
  const { user } = useAuth();

  const [activeSubTab, setActiveSubTab] = useState<
    'general' | 'patient' | 'reporters' | 'products' | 'events' | 'history' | 'assessments' | 'narrative' | 'attachments'
  >('general');

  const todayStr = useMemo(() => getTodayDateString(), []);

  // Form State
  const [formData, setFormData] = useState<CaseFormInput>({
    countryCode: 'USA',
    reportType: 'Spontaneous',
    initialReceiptDate: todayStr,
    latestReceivedDate: todayStr,
    workflowState: 'Data Entry',
    priority: 'Routine',
    draft: false,
    narrative: '',
    companyComments: '',
    assignedToUsername: user?.username || '',
    patient: {
      patientIdentifier: '',
      ageValue: undefined,
      ageUnit: 'Years',
      dateOfBirth: '',
      gender: 'Unknown',
      weightKg: undefined,
      heightCm: undefined,
      pregnant: false,
      medicalHistory: ''
    },
    reporters: [
      {
        primary: true,
        qualification: 'Physician',
        title: 'Dr.',
        firstName: '',
        lastName: '',
        institution: '',
        department: '',
        city: '',
        stateProvince: '',
        countryCode: 'USA',
        postalCode: '',
        email: '',
        phone: ''
      }
    ],
    products: [
      {
        productType: 'Suspect',
        productName: '',
        activeSubstance: '',
        indication: '',
        dosageText: '',
        formulation: 'Tablet',
        routeOfAdministration: 'Oral',
        batchLotNumber: '',
        startDate: '',
        stopDate: '',
        ongoing: false,
        dechallengeResult: 'Not Applicable',
        rechallengeResult: 'Not Applicable'
      }
    ],
    events: [
      {
        eventVerbatim: '',
        meddraPt: '',
        meddraLlt: '',
        meddraSoc: '',
        onsetDate: todayStr,
        stopDate: '',
        outcome: 'Unknown',
        serious: false,
        resultsInDeath: false,
        lifeThreatening: false,
        requiresHospitalization: false,
        disablingIncapacitating: false,
        congenitalAnomaly: false,
        otherMedicallyImportant: false
      }
    ],
    assessments: [],
    medicalHistoryList: [],
    references: [],
    attachments: []
  });

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  // Populate existing case when in edit mode
  useEffect(() => {
    if (initialCase) {
      setFormData({
        countryCode: initialCase.countryCode || 'USA',
        reportType: initialCase.reportType || 'Spontaneous',
        initialReceiptDate: initialCase.initialReceiptDate || todayStr,
        latestReceivedDate: initialCase.latestReceivedDate || todayStr,
        workflowState: initialCase.workflowState || 'Data Entry',
        priority: initialCase.priority || 'Routine',
        draft: initialCase.workflowState === 'Draft',
        narrative: initialCase.narrative || '',
        companyComments: initialCase.companyComments || '',
        assignedToUsername: initialCase.assignedToUsername || user?.username || '',
        patient: initialCase.patient || {
          patientIdentifier: '',
          ageValue: undefined,
          ageUnit: 'Years',
          gender: 'Unknown',
          pregnant: false
        },
        reporters: initialCase.reporters?.length ? initialCase.reporters : [
          { primary: true, qualification: 'Physician', countryCode: 'USA' }
        ],
        products: initialCase.products?.length ? initialCase.products : [
          { productType: 'Suspect', productName: '', formulation: 'Tablet', routeOfAdministration: 'Oral' }
        ],
        events: initialCase.events?.length ? initialCase.events : [
          {
            eventVerbatim: '',
            onsetDate: todayStr,
            outcome: 'Unknown',
            serious: false,
            resultsInDeath: false,
            lifeThreatening: false,
            requiresHospitalization: false,
            disablingIncapacitating: false,
            congenitalAnomaly: false,
            otherMedicallyImportant: false
          }
        ],
        assessments: initialCase.assessments || [],
        medicalHistoryList: initialCase.medicalHistoryList || [],
        references: initialCase.references || [],
        attachments: initialCase.attachments || []
      });
    }
  }, [initialCase]);

  // Recalculate seriousness flag dynamically
  const isCaseSerious = formData.events.some(
    (ev) => ev.serious || ev.resultsInDeath || ev.lifeThreatening || ev.requiresHospitalization ||
            ev.disablingIncapacitating || ev.congenitalAnomaly || ev.otherMedicallyImportant
  );

  const isCaseFatal = formData.events.some(
    (ev) => ev.resultsInDeath || ev.outcome === 'Fatal'
  );

  // Dynamic Item Add/Remove Handlers
  const addReporter = () => {
    setFormData((prev) => ({
      ...prev,
      reporters: [
        ...prev.reporters,
        {
          primary: false,
          qualification: 'Physician',
          countryCode: prev.countryCode,
          institution: '',
          lastName: '',
          firstName: ''
        }
      ]
    }));
  };

  const removeReporter = (index: number) => {
    if (formData.reporters.length <= 1) return;
    setFormData((prev) => ({
      ...prev,
      reporters: prev.reporters.filter((_, i) => i !== index)
    }));
  };

  const addProduct = () => {
    setFormData((prev) => ({
      ...prev,
      products: [
        ...prev.products,
        {
          productType: 'Concomitant',
          productName: '',
          activeSubstance: '',
          indication: '',
          dosageText: '',
          formulation: 'Tablet',
          routeOfAdministration: 'Oral',
          ongoing: false,
          dechallengeResult: 'Not Applicable',
          rechallengeResult: 'Not Applicable'
        }
      ]
    }));
  };

  const removeProduct = (index: number) => {
    if (formData.products.length <= 1) return;
    setFormData((prev) => ({
      ...prev,
      products: prev.products.filter((_, i) => i !== index)
    }));
  };

  const addEvent = () => {
    setFormData((prev) => ({
      ...prev,
      events: [
        ...prev.events,
        {
          eventVerbatim: '',
          onsetDate: prev.latestReceivedDate || todayStr,
          outcome: 'Unknown',
          serious: false,
          resultsInDeath: false,
          lifeThreatening: false,
          requiresHospitalization: false,
          disablingIncapacitating: false,
          congenitalAnomaly: false,
          otherMedicallyImportant: false
        }
      ]
    }));
  };

  const removeEvent = (index: number) => {
    if (formData.events.length <= 1) return;
    setFormData((prev) => ({
      ...prev,
      events: prev.events.filter((_, i) => i !== index)
    }));
  };

  const addMedicalHistory = () => {
    setFormData((prev) => ({
      ...prev,
      medicalHistoryList: [
        ...prev.medicalHistoryList,
        {
          conditionName: '',
          startDate: '',
          endDate: '',
          continuing: false,
          notes: ''
        }
      ]
    }));
  };

  const removeMedicalHistory = (index: number) => {
    setFormData((prev) => ({
      ...prev,
      medicalHistoryList: prev.medicalHistoryList.filter((_, i) => i !== index)
    }));
  };

  const addReference = () => {
    setFormData((prev) => ({
      ...prev,
      references: [
        ...prev.references,
        {
          referenceType: 'Source Reference',
          referenceId: '',
          description: ''
        }
      ]
    }));
  };

  const removeReference = (index: number) => {
    setFormData((prev) => ({
      ...prev,
      references: prev.references.filter((_, i) => i !== index)
    }));
  };

  // Form Submission
  const handleSave = async (isDraftMode: boolean) => {
    setErrorMessage(null);
    setFieldErrors({});

    // Client-side validation
    const errors: Record<string, string> = {};

    if (!formData.countryCode) errors.countryCode = 'Country code is required';
    if (!formData.reportType) errors.reportType = 'Report type is required';
    if (!formData.initialReceiptDate) errors.initialReceiptDate = 'Initial receipt date is required';
    if (!formData.latestReceivedDate) errors.latestReceivedDate = 'Latest receipt date is required';

    if (formData.initialReceiptDate && formData.latestReceivedDate &&
        formData.latestReceivedDate < formData.initialReceiptDate) {
      errors.latestReceivedDate = 'Latest received date cannot be before initial receipt date';
    }

    if (!isDraftMode) {
      if (!formData.reporters || formData.reporters.length === 0) {
        errors.reporters = 'At least one reporter is required';
      } else {
        const hasValidReporter = formData.reporters.some((r) => r.lastName || r.qualification);
        if (!hasValidReporter) errors.reporters = 'Reporter must have a qualification or last name';
      }

      if (!formData.products || formData.products.length === 0) {
        errors.products = 'At least one product is required';
      } else {
        const hasSuspect = formData.products.some((p) => p.productType === 'Suspect' && p.productName.trim() !== '');
        if (!hasSuspect) errors.products = 'At least one suspect product with a name is required';
      }

      if (!formData.events || formData.events.length === 0) {
        errors.events = 'At least one adverse event is required';
      } else {
        formData.events.forEach((ev, idx) => {
          if (!ev.eventVerbatim.trim()) {
            errors[`event_${idx}`] = `Event #${idx + 1} verbatim description is required`;
          }
          if (!ev.onsetDate) {
            errors[`event_date_${idx}`] = `Event #${idx + 1} onset date is required`;
          }
        });
      }
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      setErrorMessage('Please resolve the highlighted validation errors before submitting.');
      return;
    }

    setIsSubmitting(true);

    try {
      const xsrf = getXsrfToken();
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      };
      if (xsrf) {
        headers['X-XSRF-TOKEN'] = xsrf;
      }

      const payload = {
        ...formData,
        draft: isDraftMode,
        workflowState: isDraftMode ? 'Draft' : (formData.workflowState === 'Draft' ? 'Data Entry' : formData.workflowState)
      };

      const url = getApiUrl(initialCase ? `/api/cases/${initialCase.id}` : '/api/cases');
      const method = initialCase ? 'PUT' : 'POST';

      const response = await fetch(url, {
        method,
        headers,
        body: JSON.stringify(payload),
        credentials: 'include'
      });

      if (!response.ok) {
        const errData = await response.json().catch(() => null);
        throw new Error(errData?.message || `Failed to save safety case (HTTP ${response.status})`);
      }

      const savedCase: SafetyCaseDetails = await response.json();
      onSaveSuccess(savedCase);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage('An unexpected network error occurred while communicating with the server.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  // Handle external save trigger from Quick Launch toolbar
  useEffect(() => {
    if (saveTrigger && saveTrigger > 0 && !isSubmitting) {
      handleSave(false);
    }
  }, [saveTrigger, isSubmitting]);

  return (
    <div className="bg-white rounded border border-[#B8CCE4] shadow-sm overflow-hidden flex flex-col w-full max-w-full">
      {/* Top Enterprise Case Header */}
      <div className="bg-gradient-to-r from-[#0E3D6B] to-[#19589D] text-white p-3 flex flex-wrap items-center justify-between gap-3 border-b border-[#0A2D50]">
        <div className="flex items-center space-x-3">
          <Stethoscope className="w-5 h-5 text-blue-200" />
          <div>
            <div className="flex items-center space-x-2">
              <span className="text-sm font-bold tracking-wide">
                {initialCase ? `Case ${initialCase.caseNumber}` : 'Initial Case Entry (Argus Safety Format)'}
              </span>
              <span className="font-mono text-[10px] bg-blue-900/60 px-2 py-0.5 rounded border border-blue-400/40 text-blue-100">
                v{initialCase ? initialCase.versionNumber : '1.0'}
              </span>
              <span className={`text-[10px] font-bold px-2 py-0.5 rounded uppercase tracking-wider ${
                formData.workflowState === 'Draft'
                  ? 'bg-amber-100 text-amber-900 border border-amber-300'
                  : 'bg-emerald-100 text-emerald-900 border border-emerald-300'
              }`}>
                {formData.workflowState}
              </span>
              {isCaseSerious && (
                <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-rose-700 text-white uppercase tracking-wider animate-pulse">
                  SERIOUS{isCaseFatal ? ' (FATAL)' : ''}
                </span>
              )}
            </div>
            <p className="text-[11px] text-blue-200 mt-0.5">
              ICH E2B(R3) Pharmacovigilance Case &bull; Server-Side Number Generation &bull; PostgreSQL 18.4
            </p>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center space-x-2">
          <button
            type="button"
            onClick={() => handleSave(true)}
            disabled={isSubmitting}
            className="px-3 py-1.5 text-xs font-semibold rounded bg-white/10 hover:bg-white/20 text-white border border-white/30 flex items-center space-x-1.5 transition-colors cursor-pointer disabled:opacity-50"
            title="Save as Draft allows saving partial entries without minimum criteria"
          >
            <Bookmark className="w-3.5 h-3.5" />
            <span>Save Draft</span>
          </button>

          <button
            type="button"
            onClick={() => handleSave(false)}
            disabled={isSubmitting}
            className="px-4 py-1.5 text-xs font-bold rounded bg-gradient-to-b from-emerald-500 to-emerald-600 hover:from-emerald-600 hover:to-emerald-700 text-white shadow-sm flex items-center space-x-1.5 transition-colors cursor-pointer disabled:opacity-50"
          >
            {isSubmitting ? (
              <>
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                <span>Committing...</span>
              </>
            ) : (
              <>
                <Save className="w-3.5 h-3.5" />
                <span>{initialCase ? 'Update Case' : 'Save Case'}</span>
              </>
            )}
          </button>

          <button
            type="button"
            onClick={onCancel}
            disabled={isSubmitting}
            className="px-3 py-1.5 text-xs font-semibold rounded bg-rose-800/80 hover:bg-rose-900 text-white border border-rose-600/50 flex items-center space-x-1 transition-colors cursor-pointer"
          >
            <XCircle className="w-3.5 h-3.5" />
            <span>Close</span>
          </button>
        </div>
      </div>

      {/* Validation / Error Banner */}
      {errorMessage && (
        <div className="bg-rose-50 border-b border-rose-200 p-3 text-xs text-rose-800 flex items-start space-x-2">
          <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
          <div className="flex-1">
            <span className="font-bold block">Validation Failure:</span>
            <span>{errorMessage}</span>
            {Object.keys(fieldErrors).length > 0 && (
              <ul className="list-disc list-inside mt-1 text-[11px] text-rose-700">
                {Object.values(fieldErrors).map((msg, idx) => (
                  <li key={idx}>{msg}</li>
                ))}
              </ul>
            )}
          </div>
          <button
            onClick={() => setErrorMessage(null)}
            className="text-rose-700 hover:text-rose-900 font-bold cursor-pointer text-xs"
          >
            &times;
          </button>
        </div>
      )}

      {/* Sub-Tab Navigation Bar */}
      <div className="flex items-center space-x-1 px-3 pt-2 bg-[#E9EEF4] border-b border-[#B8CCE4] overflow-x-auto select-none">
        {[
          { id: 'general', label: '1. General Info', icon: Info },
          { id: 'patient', label: '2. Patient', icon: User },
          { id: 'reporters', label: `3. Reporters (${formData.reporters.length})`, icon: User },
          { id: 'products', label: `4. Products (${formData.products.length})`, icon: Pill },
          { id: 'events', label: `5. Adverse Events (${formData.events.length})`, icon: Activity },
          { id: 'history', label: `6. Medical History (${formData.medicalHistoryList.length})`, icon: Stethoscope },
          { id: 'assessments', label: '7. Assessments', icon: FileCheck },
          { id: 'narrative', label: '8. Narrative & Refs', icon: FileText },
          { id: 'attachments', label: '9. Attachments', icon: Paperclip }
        ].map((tab) => {
          const Icon = tab.icon;
          const isActive = activeSubTab === tab.id;
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveSubTab(tab.id as typeof activeSubTab)}
              className={`px-3 py-1.5 text-xs font-semibold rounded-t flex items-center space-x-1.5 whitespace-nowrap transition-colors cursor-pointer ${
                isActive
                  ? 'bg-white text-[#0E3D6B] border-t-2 border-t-[#19589D] border-x border-[#B8CCE4] font-bold shadow-xs'
                  : 'bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/60'
              }`}
            >
              <Icon className="w-3.5 h-3.5" />
              <span>{tab.label}</span>
            </button>
          );
        })}
      </div>

      {/* Main Tab Content */}
      <div className="p-4 bg-white flex-1 overflow-y-auto space-y-4">
        {/* TAB 1: GENERAL INFO */}
        {activeSubTab === 'general' && (
          <div className="space-y-4 text-xs">
            <div className="bg-sky-50 border border-sky-200 rounded p-2.5 text-sky-900 text-[11px] flex items-center space-x-2">
              <Info className="w-4 h-4 text-sky-700 shrink-0" />
              <span>
                <strong>ICH E2B Section A:</strong> Administrative Information, country of incident, and sponsor receipt dates. Case numbers are automatically allocated sequentially by the PostgreSQL backend.
              </span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div>
                <label className="font-semibold text-slate-700 block mb-1">
                  Country of Incident <span className="text-rose-600">*</span>
                </label>
                <select
                  value={formData.countryCode}
                  onChange={(e) => setFormData({ ...formData, countryCode: e.target.value })}
                  className="w-full px-2.5 py-1.5 border border-[#9BB5D6] rounded text-xs bg-[#FDFEFF]"
                >
                  <option value="USA">USA - United States</option>
                  <option value="GBR">GBR - United Kingdom</option>
                  <option value="DEU">DEU - Germany</option>
                  <option value="FRA">FRA - France</option>
                  <option value="JPN">JPN - Japan</option>
                  <option value="CAN">CAN - Canada</option>
                  <option value="AUS">AUS - Australia</option>
                  <option value="IND">IND - India</option>
                </select>
              </div>

              <div>
                <label className="font-semibold text-slate-700 block mb-1">
                  Report Type <span className="text-rose-600">*</span>
                </label>
                <select
                  value={formData.reportType}
                  onChange={(e) => setFormData({ ...formData, reportType: e.target.value })}
                  className="w-full px-2.5 py-1.5 border border-[#9BB5D6] rounded text-xs bg-[#FDFEFF]"
                >
                  <option value="Spontaneous">Spontaneous (Health Professional / Consumer)</option>
                  <option value="Clinical Trial">Clinical Trial (Interventional Study)</option>
                  <option value="Literature">Literature Report (Published Article)</option>
                  <option value="Other">Other Post-Marketing Surveillance</option>
                </select>
              </div>

              <div>
                <label className="font-semibold text-slate-700 block mb-1">Priority</label>
                <select
                  value={formData.priority}
                  onChange={(e) => setFormData({ ...formData, priority: e.target.value })}
                  className="w-full px-2.5 py-1.5 border border-[#9BB5D6] rounded text-xs bg-[#FDFEFF]"
                >
                  <option value="Routine">Routine</option>
                  <option value="Priority">Priority</option>
                  <option value="Expedited">Expedited (7-day / 15-day Regulatory)</option>
                </select>
              </div>

              <div>
                <label className="font-semibold text-slate-700 block mb-1">
                  Initial Sponsor Receipt Date <span className="text-rose-600">*</span>
                </label>
                <input
                  type="date"
                  value={formData.initialReceiptDate}
                  onChange={(e) => setFormData({ ...formData, initialReceiptDate: e.target.value })}
                  className={`w-full px-2.5 py-1.5 border rounded text-xs bg-[#FDFEFF] ${
                    fieldErrors.initialReceiptDate ? 'border-rose-500' : 'border-[#9BB5D6]'
                  }`}
                />
              </div>

              <div>
                <label className="font-semibold text-slate-700 block mb-1">
                  Latest Received Date <span className="text-rose-600">*</span>
                </label>
                <input
                  type="date"
                  value={formData.latestReceivedDate}
                  onChange={(e) => setFormData({ ...formData, latestReceivedDate: e.target.value })}
                  className={`w-full px-2.5 py-1.5 border rounded text-xs bg-[#FDFEFF] ${
                    fieldErrors.latestReceivedDate ? 'border-rose-500' : 'border-[#9BB5D6]'
                  }`}
                />
              </div>

              <div>
                <label className="font-semibold text-slate-700 block mb-1">Workflow State</label>
                <select
                  value={formData.workflowState}
                  onChange={(e) => setFormData({ ...formData, workflowState: e.target.value })}
                  className="w-full px-2.5 py-1.5 border border-[#9BB5D6] rounded text-xs bg-[#FDFEFF]"
                >
                  <option value="Data Entry">Data Entry</option>
                  <option value="Quality Review">Quality Review</option>
                  <option value="Medical Review">Medical Review</option>
                  <option value="Draft">Draft</option>
                  <option value="Closed">Closed</option>
                </select>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: PATIENT DEMOGRAPHICS */}
        {activeSubTab === 'patient' && (
          <div className="space-y-4 text-xs">
            <div className="bg-amber-50 border border-amber-200 rounded p-2.5 text-amber-900 text-[11px] flex items-center space-x-2">
              <Info className="w-4 h-4 text-amber-700 shrink-0" />
              <span>
                <strong>PII Protection:</strong> In compliance with GDPR and HIPAA safe-harbor standards, do not enter full patient names or national identity numbers. Use anonymized patient initials or trial subject identifiers.
              </span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
              <div>
                <label className="font-semibold text-slate-700 block mb-1">Patient Initials / ID</label>
                <input
                  type="text"
                  placeholder="e.g. J.D. or PT-4091"
                  value={formData.patient.patientIdentifier || ''}
                  onChange={(e) => setFormData({
                    ...formData,
                    patient: { ...formData.patient, patientIdentifier: e.target.value }
                  })}
                  className="w-full px-2.5 py-1.5 border border-[#9BB5D6] rounded text-xs bg-[#FDFEFF]"
                />
              </div>

              <div>
                <label className="font-semibold text-slate-700 block mb-1">Gender</label>
                <select
                  value={formData.patient.gender || 'Unknown'}
                  onChange={(e) => setFormData({
                    ...formData,
                    patient: { ...formData.patient, gender: e.target.value }
                  })}
                  className="w-full px-2.5 py-1.5 border border-[#9BB5D6] rounded text-xs bg-[#FDFEFF]"
                >
                  <option value="Unknown">Unknown</option>
                  <option value="Female">Female</option>
                  <option value="Male">Male</option>
                  <option value="Undifferentiated">Undifferentiated</option>
                </select>
              </div>

              <div>
                <label className="font-semibold text-slate-700 block mb-1">Age Value &amp; Unit</label>
                <div className="flex space-x-1">
                  <input
                    type="number"
                    step="0.1"
                    placeholder="45"
                    value={formData.patient.ageValue ?? ''}
                    onChange={(e) => setFormData({
                      ...formData,
                      patient: { ...formData.patient, ageValue: e.target.value ? parseFloat(e.target.value) : undefined }
                    })}
                    className="w-1/2 px-2.5 py-1.5 border border-[#9BB5D6] rounded text-xs bg-[#FDFEFF]"
                  />
                  <select
                    value={formData.patient.ageUnit || 'Years'}
                    onChange={(e) => setFormData({
                      ...formData,
                      patient: { ...formData.patient, ageUnit: e.target.value }
                    })}
                    className="w-1/2 px-2.5 py-1.5 border border-[#9BB5D6] rounded text-xs bg-[#FDFEFF]"
                  >
                    <option value="Years">Years</option>
                    <option value="Months">Months</option>
                    <option value="Weeks">Weeks</option>
                    <option value="Days">Days</option>
                    <option value="Decades">Decades</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="font-semibold text-slate-700 block mb-1">Date of Birth</label>
                <input
                  type="date"
                  value={formData.patient.dateOfBirth || ''}
                  onChange={(e) => setFormData({
                    ...formData,
                    patient: { ...formData.patient, dateOfBirth: e.target.value }
                  })}
                  className="w-full px-2.5 py-1.5 border border-[#9BB5D6] rounded text-xs bg-[#FDFEFF]"
                />
              </div>

              <div>
                <label className="font-semibold text-slate-700 block mb-1">Weight (kg)</label>
                <input
                  type="number"
                  step="0.1"
                  placeholder="70.5"
                  value={formData.patient.weightKg ?? ''}
                  onChange={(e) => setFormData({
                    ...formData,
                    patient: { ...formData.patient, weightKg: e.target.value ? parseFloat(e.target.value) : undefined }
                  })}
                  className="w-full px-2.5 py-1.5 border border-[#9BB5D6] rounded text-xs bg-[#FDFEFF]"
                />
              </div>

              <div>
                <label className="font-semibold text-slate-700 block mb-1">Height (cm)</label>
                <input
                  type="number"
                  step="0.1"
                  placeholder="175"
                  value={formData.patient.heightCm ?? ''}
                  onChange={(e) => setFormData({
                    ...formData,
                    patient: { ...formData.patient, heightCm: e.target.value ? parseFloat(e.target.value) : undefined }
                  })}
                  className="w-full px-2.5 py-1.5 border border-[#9BB5D6] rounded text-xs bg-[#FDFEFF]"
                />
              </div>

              <div className="flex items-center space-x-2 pt-6">
                <input
                  type="checkbox"
                  id="pregnantCheck"
                  checked={formData.patient.pregnant || false}
                  onChange={(e) => setFormData({
                    ...formData,
                    patient: { ...formData.patient, pregnant: e.target.checked }
                  })}
                  className="w-4 h-4 text-blue-600 rounded"
                />
                <label htmlFor="pregnantCheck" className="font-semibold text-slate-700">
                  Patient is Pregnant
                </label>
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: REPORTERS */}
        {activeSubTab === 'reporters' && (
          <div className="space-y-4 text-xs">
            <div className="flex items-center justify-between">
              <span className="font-bold text-slate-800 text-sm">Primary &amp; Secondary Reporters</span>
              <button
                type="button"
                onClick={addReporter}
                className="px-2.5 py-1 bg-[#19589D] text-white rounded font-semibold text-xs flex items-center space-x-1 cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add Reporter</span>
              </button>
            </div>

            {formData.reporters.map((rep, idx) => (
              <div key={idx} className="p-3 bg-slate-50 border border-slate-200 rounded space-y-3 relative">
                <div className="flex items-center justify-between border-b border-slate-200 pb-1.5">
                  <span className="font-bold text-[#0E3D6B]">
                    Reporter #{idx + 1} {rep.primary ? '(Primary Contact)' : ''}
                  </span>
                  {formData.reporters.length > 1 && (
                    <button
                      type="button"
                      onClick={() => removeReporter(idx)}
                      className="text-rose-600 hover:text-rose-800 text-xs flex items-center space-x-1 cursor-pointer"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      <span>Remove</span>
                    </button>
                  )}
                </div>

                <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">Qualification</label>
                    <select
                      value={rep.qualification}
                      onChange={(e) => {
                        const updated = [...formData.reporters];
                        updated[idx].qualification = e.target.value;
                        setFormData({ ...formData, reporters: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    >
                      <option value="Physician">Physician</option>
                      <option value="Pharmacist">Pharmacist</option>
                      <option value="Other Health Professional">Other Health Professional</option>
                      <option value="Lawyer">Lawyer</option>
                      <option value="Consumer">Consumer / Patient</option>
                    </select>
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">First Name</label>
                    <input
                      type="text"
                      value={rep.firstName || ''}
                      onChange={(e) => {
                        const updated = [...formData.reporters];
                        updated[idx].firstName = e.target.value;
                        setFormData({ ...formData, reporters: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">Last Name</label>
                    <input
                      type="text"
                      value={rep.lastName || ''}
                      onChange={(e) => {
                        const updated = [...formData.reporters];
                        updated[idx].lastName = e.target.value;
                        setFormData({ ...formData, reporters: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">Institution / Hospital</label>
                    <input
                      type="text"
                      value={rep.institution || ''}
                      onChange={(e) => {
                        const updated = [...formData.reporters];
                        updated[idx].institution = e.target.value;
                        setFormData({ ...formData, reporters: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">City</label>
                    <input
                      type="text"
                      value={rep.city || ''}
                      onChange={(e) => {
                        const updated = [...formData.reporters];
                        updated[idx].city = e.target.value;
                        setFormData({ ...formData, reporters: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">Country</label>
                    <input
                      type="text"
                      value={rep.countryCode || 'USA'}
                      onChange={(e) => {
                        const updated = [...formData.reporters];
                        updated[idx].countryCode = e.target.value;
                        setFormData({ ...formData, reporters: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">Email</label>
                    <input
                      type="email"
                      value={rep.email || ''}
                      onChange={(e) => {
                        const updated = [...formData.reporters];
                        updated[idx].email = e.target.value;
                        setFormData({ ...formData, reporters: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">Phone</label>
                    <input
                      type="text"
                      value={rep.phone || ''}
                      onChange={(e) => {
                        const updated = [...formData.reporters];
                        updated[idx].phone = e.target.value;
                        setFormData({ ...formData, reporters: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    />
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* TAB 4: MEDICINAL PRODUCTS */}
        {activeSubTab === 'products' && (
          <div className="space-y-4 text-xs">
            <div className="flex items-center justify-between">
              <span className="font-bold text-slate-800 text-sm">Suspect &amp; Concomitant Medicinal Products</span>
              <button
                type="button"
                onClick={addProduct}
                className="px-2.5 py-1 bg-[#19589D] text-white rounded font-semibold text-xs flex items-center space-x-1 cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add Product</span>
              </button>
            </div>

            {formData.products.map((prod, idx) => (
              <div key={idx} className="p-3 bg-slate-50 border border-slate-200 rounded space-y-3">
                <div className="flex items-center justify-between border-b border-slate-200 pb-1.5">
                  <div className="flex items-center space-x-2">
                    <span className="font-bold text-[#0E3D6B]">Product #{idx + 1}</span>
                    <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                      prod.productType === 'Suspect' ? 'bg-rose-100 text-rose-800 border border-rose-300' : 'bg-slate-200 text-slate-800'
                    }`}>
                      {prod.productType}
                    </span>
                  </div>
                  {formData.products.length > 1 && (
                    <button
                      type="button"
                      onClick={() => removeProduct(idx)}
                      className="text-rose-600 hover:text-rose-800 text-xs flex items-center space-x-1 cursor-pointer"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      <span>Remove</span>
                    </button>
                  )}
                </div>

                <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">
                      Product Type <span className="text-rose-600">*</span>
                    </label>
                    <select
                      value={prod.productType}
                      onChange={(e) => {
                        const updated = [...formData.products];
                        updated[idx].productType = e.target.value;
                        setFormData({ ...formData, products: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    >
                      <option value="Suspect">Suspect (Investigational / Drug under study)</option>
                      <option value="Concomitant">Concomitant (Co-administered Medication)</option>
                      <option value="Interacting">Interacting</option>
                      <option value="Treatment">Treatment for Adverse Event</option>
                    </select>
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">
                      Product Name <span className="text-rose-600">*</span>
                    </label>
                    <input
                      type="text"
                      placeholder="e.g. CardioGuard 50mg"
                      value={prod.productName}
                      onChange={(e) => {
                        const updated = [...formData.products];
                        updated[idx].productName = e.target.value;
                        setFormData({ ...formData, products: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">Active Substance</label>
                    <input
                      type="text"
                      placeholder="e.g. Cardiolol Hydrochloride"
                      value={prod.activeSubstance || ''}
                      onChange={(e) => {
                        const updated = [...formData.products];
                        updated[idx].activeSubstance = e.target.value;
                        setFormData({ ...formData, products: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">Reported Indication</label>
                    <input
                      type="text"
                      placeholder="e.g. Essential Hypertension"
                      value={prod.indication || ''}
                      onChange={(e) => {
                        const updated = [...formData.products];
                        updated[idx].indication = e.target.value;
                        setFormData({ ...formData, products: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">Dosage Regimen</label>
                    <input
                      type="text"
                      placeholder="e.g. 50mg Once daily"
                      value={prod.dosageText || ''}
                      onChange={(e) => {
                        const updated = [...formData.products];
                        updated[idx].dosageText = e.target.value;
                        setFormData({ ...formData, products: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">Formulation &amp; Route</label>
                    <div className="flex space-x-1">
                      <input
                        type="text"
                        placeholder="Tablet"
                        value={prod.formulation || ''}
                        onChange={(e) => {
                          const updated = [...formData.products];
                          updated[idx].formulation = e.target.value;
                          setFormData({ ...formData, products: updated });
                        }}
                        className="w-1/2 px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                      />
                      <input
                        type="text"
                        placeholder="Oral"
                        value={prod.routeOfAdministration || ''}
                        onChange={(e) => {
                          const updated = [...formData.products];
                          updated[idx].routeOfAdministration = e.target.value;
                          setFormData({ ...formData, products: updated });
                        }}
                        className="w-1/2 px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">Start Date</label>
                    <input
                      type="date"
                      value={prod.startDate || ''}
                      onChange={(e) => {
                        const updated = [...formData.products];
                        updated[idx].startDate = e.target.value;
                        setFormData({ ...formData, products: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">Stop Date</label>
                    <input
                      type="date"
                      value={prod.stopDate || ''}
                      onChange={(e) => {
                        const updated = [...formData.products];
                        updated[idx].stopDate = e.target.value;
                        setFormData({ ...formData, products: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    />
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* TAB 5: ADVERSE EVENTS */}
        {activeSubTab === 'events' && (
          <div className="space-y-4 text-xs">
            <div className="flex items-center justify-between">
              <span className="font-bold text-slate-800 text-sm">Adverse Events &amp; Seriousness Criteria</span>
              <button
                type="button"
                onClick={addEvent}
                className="px-2.5 py-1 bg-[#19589D] text-white rounded font-semibold text-xs flex items-center space-x-1 cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add Event</span>
              </button>
            </div>

            {formData.events.map((ev, idx) => (
              <div key={idx} className="p-3 bg-slate-50 border border-slate-200 rounded space-y-3">
                <div className="flex items-center justify-between border-b border-slate-200 pb-1.5">
                  <div className="flex items-center space-x-2">
                    <span className="font-bold text-[#0E3D6B]">Event #{idx + 1}</span>
                    {ev.serious && (
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-rose-100 text-rose-800 border border-rose-300">
                        Serious Criteria Met
                      </span>
                    )}
                  </div>
                  {formData.events.length > 1 && (
                    <button
                      type="button"
                      onClick={() => removeEvent(idx)}
                      className="text-rose-600 hover:text-rose-800 text-xs flex items-center space-x-1 cursor-pointer"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      <span>Remove</span>
                    </button>
                  )}
                </div>

                <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
                  <div className="md:col-span-2">
                    <label className="font-semibold text-slate-700 block mb-1">
                      Event Verbatim (Reported Term) <span className="text-rose-600">*</span>
                    </label>
                    <input
                      type="text"
                      placeholder="e.g. Acute severe angioedema with respiratory distress"
                      value={ev.eventVerbatim}
                      onChange={(e) => {
                        const updated = [...formData.events];
                        updated[idx].eventVerbatim = e.target.value;
                        setFormData({ ...formData, events: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">MedDRA Preferred Term (PT)</label>
                    <input
                      type="text"
                      placeholder="e.g. Angioedema"
                      value={ev.meddraPt || ''}
                      onChange={(e) => {
                        const updated = [...formData.events];
                        updated[idx].meddraPt = e.target.value;
                        setFormData({ ...formData, events: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">Outcome</label>
                    <select
                      value={ev.outcome}
                      onChange={(e) => {
                        const updated = [...formData.events];
                        updated[idx].outcome = e.target.value;
                        if (e.target.value === 'Fatal') {
                          updated[idx].resultsInDeath = true;
                          updated[idx].serious = true;
                        }
                        setFormData({ ...formData, events: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    >
                      <option value="Recovered/Resolved">Recovered / Resolved</option>
                      <option value="Recovering/Resolving">Recovering / Resolving</option>
                      <option value="Not Recovered">Not Recovered</option>
                      <option value="Fatal">Fatal</option>
                      <option value="Recovered with Sequelae">Recovered with Sequelae</option>
                      <option value="Unknown">Unknown</option>
                    </select>
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">
                      Onset Date <span className="text-rose-600">*</span>
                    </label>
                    <input
                      type="date"
                      value={ev.onsetDate}
                      onChange={(e) => {
                        const updated = [...formData.events];
                        updated[idx].onsetDate = e.target.value;
                        setFormData({ ...formData, events: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-slate-700 block mb-1">Stop / Resolution Date</label>
                    <input
                      type="date"
                      value={ev.stopDate || ''}
                      onChange={(e) => {
                        const updated = [...formData.events];
                        updated[idx].stopDate = e.target.value;
                        setFormData({ ...formData, events: updated });
                      }}
                      className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                    />
                  </div>
                </div>

                {/* Seriousness Criteria Checkboxes */}
                <div className="bg-white p-2.5 rounded border border-slate-200 mt-2">
                  <span className="font-bold text-[#0E3D6B] block mb-1 text-[11px] uppercase tracking-wider">
                    ICH E2B Seriousness Criteria (Check all that apply)
                  </span>
                  <div className="grid grid-cols-2 md:grid-cols-3 gap-2 text-[11px]">
                    <label className="flex items-center space-x-1.5 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={ev.resultsInDeath}
                        onChange={(e) => {
                          const updated = [...formData.events];
                          updated[idx].resultsInDeath = e.target.checked;
                          if (e.target.checked) {
                            updated[idx].serious = true;
                            updated[idx].outcome = 'Fatal';
                          }
                          setFormData({ ...formData, events: updated });
                        }}
                        className="rounded text-rose-600"
                      />
                      <span className="font-semibold text-rose-800">Results in Death</span>
                    </label>

                    <label className="flex items-center space-x-1.5 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={ev.lifeThreatening}
                        onChange={(e) => {
                          const updated = [...formData.events];
                          updated[idx].lifeThreatening = e.target.checked;
                          if (e.target.checked) updated[idx].serious = true;
                          setFormData({ ...formData, events: updated });
                        }}
                        className="rounded text-blue-600"
                      />
                      <span>Life Threatening</span>
                    </label>

                    <label className="flex items-center space-x-1.5 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={ev.requiresHospitalization}
                        onChange={(e) => {
                          const updated = [...formData.events];
                          updated[idx].requiresHospitalization = e.target.checked;
                          if (e.target.checked) updated[idx].serious = true;
                          setFormData({ ...formData, events: updated });
                        }}
                        className="rounded text-blue-600"
                      />
                      <span>Requires Hospitalization</span>
                    </label>

                    <label className="flex items-center space-x-1.5 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={ev.disablingIncapacitating}
                        onChange={(e) => {
                          const updated = [...formData.events];
                          updated[idx].disablingIncapacitating = e.target.checked;
                          if (e.target.checked) updated[idx].serious = true;
                          setFormData({ ...formData, events: updated });
                        }}
                        className="rounded text-blue-600"
                      />
                      <span>Disabling / Incapacitating</span>
                    </label>

                    <label className="flex items-center space-x-1.5 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={ev.congenitalAnomaly}
                        onChange={(e) => {
                          const updated = [...formData.events];
                          updated[idx].congenitalAnomaly = e.target.checked;
                          if (e.target.checked) updated[idx].serious = true;
                          setFormData({ ...formData, events: updated });
                        }}
                        className="rounded text-blue-600"
                      />
                      <span>Congenital Anomaly</span>
                    </label>

                    <label className="flex items-center space-x-1.5 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={ev.otherMedicallyImportant}
                        onChange={(e) => {
                          const updated = [...formData.events];
                          updated[idx].otherMedicallyImportant = e.target.checked;
                          if (e.target.checked) updated[idx].serious = true;
                          setFormData({ ...formData, events: updated });
                        }}
                        className="rounded text-blue-600"
                      />
                      <span>Other Medically Important</span>
                    </label>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* TAB 6: MEDICAL HISTORY */}
        {activeSubTab === 'history' && (
          <div className="space-y-4 text-xs">
            <div className="flex items-center justify-between">
              <span className="font-bold text-slate-800 text-sm">Patient Medical History &amp; Concurrent Conditions</span>
              <button
                type="button"
                onClick={addMedicalHistory}
                className="px-2.5 py-1 bg-[#19589D] text-white rounded font-semibold text-xs flex items-center space-x-1 cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add History Item</span>
              </button>
            </div>

            {formData.medicalHistoryList.length === 0 ? (
              <div className="p-6 text-center text-slate-500 bg-slate-50 border border-dashed border-slate-300 rounded">
                No prior medical conditions entered for this patient. Click "Add History Item" to document relevant history.
              </div>
            ) : (
              formData.medicalHistoryList.map((mh, idx) => (
                <div key={idx} className="p-3 bg-slate-50 border border-slate-200 rounded space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-[#0E3D6B]">Condition #{idx + 1}</span>
                    <button
                      type="button"
                      onClick={() => removeMedicalHistory(idx)}
                      className="text-rose-600 hover:text-rose-800 text-xs flex items-center space-x-1 cursor-pointer"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      <span>Remove</span>
                    </button>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
                    <div className="md:col-span-2">
                      <label className="font-semibold text-slate-700 block mb-1">Disease / Surgical History</label>
                      <input
                        type="text"
                        placeholder="e.g. Type 2 Diabetes Mellitus"
                        value={mh.conditionName}
                        onChange={(e) => {
                          const updated = [...formData.medicalHistoryList];
                          updated[idx].conditionName = e.target.value;
                          setFormData({ ...formData, medicalHistoryList: updated });
                        }}
                        className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                      />
                    </div>

                    <div>
                      <label className="font-semibold text-slate-700 block mb-1">Start Date</label>
                      <input
                        type="date"
                        value={mh.startDate || ''}
                        onChange={(e) => {
                          const updated = [...formData.medicalHistoryList];
                          updated[idx].startDate = e.target.value;
                          setFormData({ ...formData, medicalHistoryList: updated });
                        }}
                        className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                      />
                    </div>

                    <div className="flex items-center space-x-2 pt-5">
                      <input
                        type="checkbox"
                        id={`cont_${idx}`}
                        checked={mh.continuing || false}
                        onChange={(e) => {
                          const updated = [...formData.medicalHistoryList];
                          updated[idx].continuing = e.target.checked;
                          setFormData({ ...formData, medicalHistoryList: updated });
                        }}
                        className="rounded text-blue-600"
                      />
                      <label htmlFor={`cont_${idx}`} className="font-semibold text-slate-700">
                        Condition Continuing
                      </label>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {/* TAB 7: ASSESSMENTS & CAUSALITY */}
        {activeSubTab === 'assessments' && (
          <div className="space-y-4 text-xs">
            <div className="bg-sky-50 border border-sky-200 rounded p-2.5 text-sky-900 text-[11px]">
              <strong>Causality Assessment:</strong> Evaluate the causal relationship between suspect drug(s) and observed adverse event(s). Company causality determination governs regulatory 7-day and 15-day expedited reporting obligations.
            </div>

            <div className="p-3 bg-slate-50 border border-slate-200 rounded space-y-3">
              <span className="font-bold text-[#0E3D6B]">Medical Causality Assessment</span>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                <div>
                  <label className="font-semibold text-slate-700 block mb-1">Reporter Causality</label>
                  <select
                    value={formData.assessments[0]?.causalityReporter || 'Unassessable'}
                    onChange={(e) => {
                      const updated = [...formData.assessments];
                      if (!updated[0]) updated[0] = { causalityReporter: e.target.value };
                      else updated[0].causalityReporter = e.target.value;
                      setFormData({ ...formData, assessments: updated });
                    }}
                    className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                  >
                    <option value="Related">Related</option>
                    <option value="Not Related">Not Related</option>
                    <option value="Unassessable">Unassessable / Missing</option>
                  </select>
                </div>

                <div>
                  <label className="font-semibold text-slate-700 block mb-1">Company Causality</label>
                  <select
                    value={formData.assessments[0]?.causalityCompany || 'Reasonable Possibility'}
                    onChange={(e) => {
                      const updated = [...formData.assessments];
                      if (!updated[0]) updated[0] = { causalityCompany: e.target.value };
                      else updated[0].causalityCompany = e.target.value;
                      setFormData({ ...formData, assessments: updated });
                    }}
                    className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                  >
                    <option value="Reasonable Possibility">Reasonable Possibility (Related)</option>
                    <option value="No Reasonable Possibility">No Reasonable Possibility (Unrelated)</option>
                    <option value="Pending Review">Pending Medical Review</option>
                  </select>
                </div>

                <div>
                  <label className="font-semibold text-slate-700 block mb-1">Expectedness / Listedness</label>
                  <select
                    value={formData.assessments[0]?.expectedness || 'Unexpected'}
                    onChange={(e) => {
                      const updated = [...formData.assessments];
                      if (!updated[0]) updated[0] = { expectedness: e.target.value };
                      else updated[0].expectedness = e.target.value;
                      setFormData({ ...formData, assessments: updated });
                    }}
                    className="w-full px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                  >
                    <option value="Unexpected">Unexpected (Unlisted in Core Data Sheet)</option>
                    <option value="Expected">Expected (Listed in Core Data Sheet)</option>
                  </select>
                </div>

                <div className="md:col-span-3">
                  <label className="font-semibold text-slate-700 block mb-1">Assessment Comment / Medical Evaluation</label>
                  <textarea
                    rows={3}
                    placeholder="Provide medical assessment rationale regarding temporal relationship, dechallenge, and biological plausibility..."
                    value={formData.assessments[0]?.assessmentComment || ''}
                    onChange={(e) => {
                      const updated = [...formData.assessments];
                      if (!updated[0]) updated[0] = { assessmentComment: e.target.value };
                      else updated[0].assessmentComment = e.target.value;
                      setFormData({ ...formData, assessments: updated });
                    }}
                    className="w-full px-2.5 py-1.5 border border-[#9BB5D6] rounded text-xs bg-white"
                  />
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 8: NARRATIVE & REFERENCES */}
        {activeSubTab === 'narrative' && (
          <div className="space-y-4 text-xs">
            <div>
              <label className="font-semibold text-slate-700 block mb-1">
                Case Narrative (Clinical Summary)
              </label>
              <textarea
                rows={5}
                placeholder="Enter complete clinical case narrative describing timeline of drug exposure, onset of symptoms, treatment administered, and outcome..."
                value={formData.narrative}
                onChange={(e) => setFormData({ ...formData, narrative: e.target.value })}
                className="w-full px-2.5 py-2 border border-[#9BB5D6] rounded text-xs bg-[#FDFEFF]"
              />
            </div>

            <div>
              <label className="font-semibold text-slate-700 block mb-1">
                Company Comments (Internal Safety Review Notes)
              </label>
              <textarea
                rows={3}
                placeholder="Internal pharmacovigilance safety evaluation notes..."
                value={formData.companyComments}
                onChange={(e) => setFormData({ ...formData, companyComments: e.target.value })}
                className="w-full px-2.5 py-2 border border-[#9BB5D6] rounded text-xs bg-[#FDFEFF]"
              />
            </div>

            {/* Source References */}
            <div className="pt-2">
              <div className="flex items-center justify-between mb-2">
                <span className="font-bold text-slate-800">Source References &amp; Literature Identifiers</span>
                <button
                  type="button"
                  onClick={addReference}
                  className="px-2 py-1 bg-[#19589D] text-white rounded font-semibold text-xs flex items-center space-x-1 cursor-pointer"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>Add Reference</span>
                </button>
              </div>

              {formData.references.map((ref, idx) => (
                <div key={idx} className="flex items-center space-x-2 mb-2 p-2 bg-slate-50 border border-slate-200 rounded">
                  <select
                    value={ref.referenceType}
                    onChange={(e) => {
                      const updated = [...formData.references];
                      updated[idx].referenceType = e.target.value;
                      setFormData({ ...formData, references: updated });
                    }}
                    className="px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                  >
                    <option value="Source Reference">Source Document ID</option>
                    <option value="Literature">Literature / PubMed PMID</option>
                    <option value="Health Authority Reference">Health Authority Ref</option>
                  </select>

                  <input
                    type="text"
                    placeholder="Reference Identifier (e.g. PMID-384912)"
                    value={ref.referenceId}
                    onChange={(e) => {
                      const updated = [...formData.references];
                      updated[idx].referenceId = e.target.value;
                      setFormData({ ...formData, references: updated });
                    }}
                    className="flex-1 px-2 py-1 border border-[#9BB5D6] rounded text-xs bg-white"
                  />

                  <button
                    type="button"
                    onClick={() => removeReference(idx)}
                    className="text-rose-600 hover:text-rose-800 p-1 cursor-pointer"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* TAB 9: ATTACHMENTS */}
        {activeSubTab === 'attachments' && (
          <div className="space-y-4 text-xs">
            <div className="bg-sky-50 border border-sky-200 rounded p-3 text-sky-900 text-[11px]">
              <strong className="block mb-1">Attachment Storage &amp; Access Controls:</strong>
              Safety attachments (lab reports, discharge summaries, electrocardiograms, and CIOMS forms) are recorded in the <code>case_attachments</code> metadata registry with SHA-256 cryptographic hashes for tamper-evidence. Secure file transfer pipeline is scheduled for Stage 6.
            </div>

            <div className="p-6 text-center text-slate-500 bg-slate-50 border border-dashed border-slate-300 rounded">
              <Paperclip className="w-8 h-8 text-slate-400 mx-auto mb-2" />
              <p className="font-semibold text-slate-700">No attachments currently associated with this case.</p>
              <p className="text-slate-500 text-[11px] mt-1">
                Attachment metadata records will link to secure S3 / local vault storage in Stage 6.
              </p>
            </div>
          </div>
        )}
      </div>

      {/* Footer Controls */}
      <div className="bg-[#E9EEF4] p-3 border-t border-[#B8CCE4] flex items-center justify-between">
        <div className="text-[11px] text-slate-500 flex items-center space-x-2">
          <span>Assigned Specialist: <strong className="text-slate-800">{user?.username}</strong></span>
          <span>&bull;</span>
          <span>Database: <strong className="text-slate-800">PostgreSQL (Live)</strong></span>
        </div>

        <div className="flex items-center space-x-2">
          <button
            type="button"
            onClick={onCancel}
            disabled={isSubmitting}
            className="px-3 py-1 text-xs font-semibold rounded bg-white hover:bg-slate-100 text-slate-700 border border-slate-300 transition-colors cursor-pointer"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => handleSave(true)}
            disabled={isSubmitting}
            className="px-3 py-1 text-xs font-semibold rounded bg-sky-600 hover:bg-sky-700 text-white transition-colors cursor-pointer"
          >
            Save Draft
          </button>
          <button
            type="button"
            onClick={() => handleSave(false)}
            disabled={isSubmitting}
            className="px-4 py-1 text-xs font-bold rounded bg-[#19589D] hover:bg-[#124B8A] text-white shadow-xs transition-colors cursor-pointer flex items-center space-x-1"
          >
            {isSubmitting && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
            <span>{initialCase ? 'Update Case' : 'Save Case'}</span>
          </button>
        </div>
      </div>
    </div>
  );
};

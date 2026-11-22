import { useEffect, useRef, useState } from "react";
import {
  ShieldCheck,
  GraduationCap,
  CheckCircle2,
  ArrowRight,
  Copy,
  Clock3,
} from "lucide-react";
import { AccessDialog } from "../workspaces/AccessDialog";
import { apiRequest } from "../config/api";
import { passwordPolicy } from "../config/inputValidation";
import type { Catalog } from "./types";
import "../components/PublicAccess.css";
import "./education.css";
type RegistrationResult = {
  studentCode: string;
  fullName: string;
  collegeName: string;
  registrationStatus: string;
  message?: string;
};
export function StudentRegistration() {
  const [catalog, setCatalog] = useState<Catalog | null>(null),
    [collegeId, setCollegeId] = useState(""),
    [selected, setSelected] = useState<string[]>([]),
    [values, setValues] = useState({
      fullName: "",
      username: "",
      email: "",
      password: "",
      confirmPassword: "",
    }),
    [show, setShow] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [success, setSuccess] = useState<RegistrationResult | null>(null),
    [receiptOpen, setReceiptOpen] = useState(false),
    [copyMessage, setCopyMessage] = useState(""),
    [attempt, setAttempt] = useState(0);
  const dirty = useRef(false);
  const confirmationButton = useRef<HTMLButtonElement>(null);
  function closeReceipt() {
    setReceiptOpen(false);
    requestAnimationFrame(() =>
      confirmationButton.current?.focus({ preventScroll: true }),
    );
  }
  useEffect(() => {
    const c = new AbortController();
    apiRequest<Catalog>("/api/education/catalog", "GET", undefined, c.signal)
      .then(setCatalog)
      .catch((e) => {
        if (!c.signal.aborted) setError(e.message);
      });
    return () => c.abort();
  }, [attempt]);
  useEffect(() => {
    dirty.current = Object.values(values).some(Boolean) || selected.length > 0;
  }, [values, selected]);
  useEffect(() => {
    const prior = window.location.hash;
    const before = (e: BeforeUnloadEvent) => {
      if (dirty.current) {
        e.preventDefault();
        e.returnValue = "";
      }
    };
    const hash = () => {
      if (
        dirty.current &&
        !window.confirm("Discard unsaved student registration?")
      )
        window.history.pushState(null, "", prior);
    };
    window.addEventListener("beforeunload", before);
    window.addEventListener("hashchange", hash);
    return () => {
      window.removeEventListener("beforeunload", before);
      window.removeEventListener("hashchange", hash);
    };
  }, []);
  return (
    <div className="pa-site edu-registration">
      <header className="pa-header">
        <a className="pa-brand" href="#/home">
          <span>
            <ShieldCheck />
          </span>
          <strong>
            PharmaSafe<small>COLLEGE LEARNING WORKSPACE</small>
          </strong>
        </a>
        <nav>
          <a href="#/home">Home</a>
          <a className="pa-nav-login" href="#/login">
            Common sign in <ArrowRight size={16} />
          </a>
        </nav>
      </header>
      <main className="pa-shell" data-validation-context="education">
        <div className="pa-page-intro">
          <p className="pa-eyebrow">COLLEGE & DOMAIN ACCESS</p>
          <h1>Build your safety expertise.</h1>
          <p>
            Register with your college, complete your details and select all
            seven domains. Your college reviews each domain request.
          </p>
        </div>
        {error && (
          <p className="pa-error" role="alert">
            {error}
          </p>
        )}
        {success ? (
          <section className="pa-card pa-success">
            <CheckCircle2 size={48} />
            <h2>Registration submitted</h2>
            <p>
              Your college will review your access. Keep your Student ID for the
              common sign-in page.
            </p>
            <button
              ref={confirmationButton}
              type="button"
              onClick={() => {
                setCopyMessage("");
                setReceiptOpen(true);
              }}
            >
              View registration confirmation
            </button>
            <a className="pa-primary-link" href="#/login">
              Continue to sign in <ArrowRight size={16} />
            </a>
          </section>
        ) : !catalog ? (
          <section className="pa-card">
            <p role="status">
              {error
                ? "Unable to load registration options. Retry the connection to continue."
                : "Loading active colleges and domains…"}
            </p>
            <button
              onClick={() => {
                setError("");
                setAttempt((v) => v + 1);
              }}
            >
              Retry connection
            </button>
          </section>
        ) : (
          <>
            {!catalog.colleges.length && (
              <section className="pa-card" role="status">
                <GraduationCap size={30} />
                <h2>Your college must be added before registration</h2>
                <p>
                  The registration form is below. Ask your college coordinator
                  to have the Main Super Admin create an active college, then
                  refresh the list.
                </p>
                <button type="button" onClick={() => setAttempt((v) => v + 1)}>
                  Refresh colleges
                </button>
              </section>
            )}
            <form
              className="pa-card pa-registration"
              aria-label="Student registration"
              inert={busy || undefined}
              onSubmit={async (e) => {
                e.preventDefault();
                setError("");
                if (!collegeId || selected.length !== 7) {
                  setError("Select all seven domains before registering.");
                  return;
                }
                const policy = passwordPolicy(values.password);
                if (policy) {
                  setError(policy);
                  return;
                }
                if (values.password !== values.confirmPassword) {
                  setError("Passwords do not match.");
                  return;
                }
                setBusy(true);
                try {
                  const result = await apiRequest<RegistrationResult>(
                    "/api/education/register",
                    "POST",
                    {
                      ...values,
                      collegeId: Number(collegeId),
                      domains: selected,
                    },
                  );
                  if (
                    !result.studentCode ||
                    !result.fullName ||
                    !result.collegeName
                  )
                    throw new Error(
                      "Registration confirmation is incomplete. Contact your college before trying again.",
                    );
                  dirty.current = false;
                  setValues({
                    fullName: "",
                    username: "",
                    email: "",
                    password: "",
                    confirmPassword: "",
                  });
                  setSelected([]);
                  setSuccess(result);
                  setReceiptOpen(true);
                  setCopyMessage("");
                } catch (e) {
                  const message =
                    e instanceof Error ? e.message : "Registration failed";
                  setError(message);
                } finally {
                  setBusy(false);
                }
              }}
            >
              <fieldset>
                <legend>
                  <span>01</span>Your college and identity
                </legend>
                <div className="pa-form">
                  <label>
                    College
                    <select
                      aria-label="College"
                      required
                      value={collegeId}
                      disabled={!catalog.colleges.length}
                      onChange={(e) => {
                        setCollegeId(e.target.value);
                        setError("");
                      }}
                    >
                      <option value="">
                        {catalog.colleges.length
                          ? "Select your college"
                          : "No active colleges available"}
                      </option>
                      {catalog.colleges.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name} · {c.code}
                        </option>
                      ))}
                    </select>
                  </label>

                  {(["fullName", "username", "email"] as const).map((key) => (
                    <label key={key}>
                      {
                        {
                          fullName: "Student full name",
                          username: "Username",
                          email: "Official email address",
                        }[key]
                      }
                      <input
                        name={key}
                        aria-label={
                          {
                            fullName: "Student full name",
                            username: "Username",
                            email: "Official email address",
                          }[key]
                        }
                        required
                        type={key === "email" ? "email" : "text"}
                        maxLength={key === "username" ? 64 : 128}
                        minLength={key === "username" ? 3 : undefined}
                        autoComplete={
                          key === "fullName"
                            ? "name"
                            : key === "email"
                              ? "email"
                              : "username"
                        }
                        value={values[key]}
                        onChange={(e) =>
                          setValues((v) => ({ ...v, [key]: e.target.value }))
                        }
                      />
                    </label>
                  ))}
                </div>
              </fieldset>
              <fieldset>
                <legend>
                  <span>02</span>Select your domains
                </legend>
                <p>
                  Select all seven domains. Every checkbox is required. Each
                  selection creates a separate college approval request.
                </p>
                {selected.length !== 7 && (
                  <p className="pa-field-help" role="status">
                    Select all seven domains to continue ({selected.length}/7
                    selected).
                  </p>
                )}
                <div className="edu-domain-options">
                  {catalog.domains.map((d, i) => (
                    <label
                      className={`edu-domain-option edu-accent-${i}`}
                      key={d.code}
                    >
                      <input
                        type="checkbox"
                        required
                        checked={selected.includes(d.code)}
                        onChange={(e) =>
                          setSelected((v) =>
                            e.target.checked
                              ? [...v, d.code]
                              : v.filter((c) => c !== d.code),
                          )
                        }
                      />
                      <span>
                        <strong>{d.name}</strong>
                        <small>{d.description}</small>
                      </span>
                    </label>
                  ))}
                </div>
              </fieldset>
              <fieldset>
                <legend>
                  <span>03</span>Secure your account
                </legend>
                <div className="pa-form">
                  {(["password", "confirmPassword"] as const).map((key) => (
                    <label key={key}>
                      {key === "password" ? "Password" : "Confirm password"}
                      <input
                        name={key}
                        required
                        minLength={12}
                        maxLength={72}
                        type={show ? "text" : "password"}
                        autoComplete="new-password"
                        value={values[key]}
                        onChange={(e) =>
                          setValues((v) => ({ ...v, [key]: e.target.value }))
                        }
                      />
                    </label>
                  ))}
                </div>
                <label className="edu-checkbox">
                  <input
                    type="checkbox"
                    checked={show}
                    onChange={(e) => setShow(e.target.checked)}
                  />
                  Show passwords
                </label>
                <p>
                  Use at least 12 characters with letters and a number or
                  symbol, up to 72 UTF-8 bytes.
                </p>
              </fieldset>
              <div className="pa-form-actions">
                <p>
                  <ShieldCheck size={16} />
                  Your domain work stays private to you and authorized
                  administrators.
                </p>
                <button
                  type="submit"
                  disabled={busy || !collegeId || selected.length !== 7}
                >
                  {busy ? "Saving registration…" : "Register student"}
                  <ArrowRight size={16} />
                </button>
              </div>
            </form>
          </>
        )}
      </main>
      {success && receiptOpen && (
        <AccessDialog
          title="Registration Successful"
          eyebrow="STUDENT REGISTRATION"
          className="edu-success-dialog"
          onClose={closeReceipt}
        >
          <div className="edu-registration-receipt">
            <div className="edu-receipt-intro">
              <span className="edu-success-icon">
                <CheckCircle2 size={30} />
              </span>
              <div>
                <h3>Keep your Student ID.</h3>
                <p>
                  Your registration is saved. Use this ID to sign in and follow
                  your college’s approval.
                </p>
              </div>
            </div>
            <dl>
              <div>
                <dt>Student name</dt>
                <dd>{success.fullName}</dd>
              </div>
              <div>
                <dt>College</dt>
                <dd>{success.collegeName}</dd>
              </div>
              <div className="edu-receipt-id">
                <dt>Your Student ID</dt>
                <dd className="edu-receipt-id-value">
                  <code tabIndex={0}>{success.studentCode}</code>
                  <button
                    type="button"
                    className="edu-copy-id"
                    onClick={async () => {
                      try {
                        await navigator.clipboard.writeText(
                          success.studentCode,
                        );
                        setCopyMessage("Student ID copied.");
                      } catch {
                        setCopyMessage(
                          "Copy is unavailable. Select the Student ID above to copy it manually.",
                        );
                      }
                    }}
                  >
                    <Copy size={15} aria-hidden="true" />
                    Copy Student ID
                  </button>
                </dd>
              </div>
              <div className="edu-receipt-status">
                <dt>Registration status</dt>
                <dd>
                  <Clock3 size={15} aria-hidden="true" />
                  Awaiting College Administration approval
                </dd>
              </div>
            </dl>
            <p className="edu-copy-feedback" role="status" aria-live="polite">
              {copyMessage ||
                "Your ID is unique to you. Save a copy for future sign-ins."}
            </p>
            <div className="edu-receipt-next">
              <strong>What happens next?</strong>
              <p>
                Your college reviews your access. Sign in to follow your
                approval status; approved domains will open your assigned work.
              </p>
            </div>
            <div className="edu-receipt-actions">
              <button type="button" onClick={closeReceipt}>
                Close confirmation
              </button>
              <a className="pa-primary-link" href="#/login">
                Continue to sign in <ArrowRight size={16} />
              </a>
            </div>
          </div>
        </AccessDialog>
      )}
    </div>
  );
}

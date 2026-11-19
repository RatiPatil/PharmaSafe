import { useEffect, useRef, useState } from "react";
import {
  ShieldCheck,
  GraduationCap,
  CheckCircle2,
  ArrowRight,
} from "lucide-react";
import { apiRequest } from "../config/api";
import { passwordPolicy } from "../config/inputValidation";
import type { Catalog } from "./types";
import "../components/PublicAccess.css";
import "./education.css";
type Reservation = {
  studentCode: string;
  reservationToken: string;
  expiresAt: string;
};
export function StudentRegistration() {
  const [catalog, setCatalog] = useState<Catalog | null>(null),
    [collegeId, setCollegeId] = useState(""),
    [selected, setSelected] = useState<string[]>([]),
    [reservation, setReservation] = useState<Reservation | null>(null),
    [values, setValues] = useState({
      fullName: "",
      username: "",
      email: "",
      password: "",
      confirmPassword: "",
    }),
    [show, setShow] = useState(false),
    [busy, setBusy] = useState(false),
    [reserving, setReserving] = useState(false),
    [error, setError] = useState(""),
    [success, setSuccess] = useState(""),
    [attempt, setAttempt] = useState(0);
  const generation = useRef(0),
    dirty = useRef(false);
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
  async function reserve(id: string) {
    const current = ++generation.current;
    setCollegeId(id);
    setReservation(null);
    setError("");
    if (!id) {
      setReserving(false);
      return;
    }
    setReserving(true);
    try {
      const r = await apiRequest<Reservation>(
        "/api/education/student-id",
        "POST",
        { collegeId: Number(id) },
      );
      if (current === generation.current) setReservation(r);
    } catch (e) {
      if (current === generation.current)
        setError(
          e instanceof Error ? e.message : "Student ID generation failed",
        );
    } finally {
      if (current === generation.current) setReserving(false);
    }
  }
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
            Register with your college, reserve your Student ID and select all
            seven domains. Your college reviews each domain request.
          </p>
        </div>
        {error && (
          <p className="pa-error" role="alert">
            {error}
          </p>
        )}
        {success ? (
          <section className="pa-card pa-success" role="status">
            <CheckCircle2 size={40} />
            <h2>Student registration saved</h2>
            <p>Your Student ID</p>
            <strong className="edu-student-id">{success}</strong>
            <p>
              Sign in to track approvals. Domain work opens after your College
              Admin approves access.
            </p>
            <a className="pa-primary-link" href="#/login">
              Sign in <ArrowRight size={17} />
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
                if (!reservation || selected.length !== 7) {
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
                  const result = await apiRequest<{ studentCode: string }>(
                    "/api/education/register",
                    "POST",
                    {
                      ...values,
                      collegeId: Number(collegeId),
                      reservationToken: reservation.reservationToken,
                      domains: selected,
                    },
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
                  setSuccess(result.studentCode);
                } catch (e) {
                  const message =
                    e instanceof Error ? e.message : "Registration failed";
                  setError(message);
                  if (message.toLowerCase().includes("reservation"))
                    setReservation(null);
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
                      disabled={reserving || !catalog.colleges.length}
                      onChange={(e) => void reserve(e.target.value)}
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
                  <div className="edu-id-reservation" role="status">
                    <span>Automatically generated Student ID</span>
                    <strong>
                      {reserving
                        ? "Generating…"
                        : reservation?.studentCode || "Choose a college first"}
                    </strong>
                    {reservation && (
                      <small>
                        Reserved for 20 minutes. This becomes your permanent ID
                        when registration is saved.
                      </small>
                    )}
                    {collegeId && !reserving && (
                      <button
                        type="button"
                        onClick={() => void reserve(collegeId)}
                      >
                        Generate a fresh Student ID
                      </button>
                    )}
                  </div>
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
                  disabled={
                    busy || reserving || !reservation || selected.length !== 7
                  }
                >
                  {busy ? "Saving registration…" : "Register student"}
                  <ArrowRight size={16} />
                </button>
              </div>
            </form>
          </>
        )}
      </main>
    </div>
  );
}

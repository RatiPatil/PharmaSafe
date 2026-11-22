import {
  ArrowUpRight,
  CheckCircle2,
  ClipboardList,
  FileCheck2,
  GraduationCap,
  LockKeyhole,
  ShieldCheck,
} from "lucide-react";
import "./HomeContent.css";

const steps = [
  {
    number: "01",
    title: "Capture the details",
    text: "Bring source information, case details and supporting evidence into one organised workspace.",
  },
  {
    number: "02",
    title: "Work together",
    text: "Follow assignments, document progress and keep the next step clear for your team.",
  },
  {
    number: "03",
    title: "Review with context",
    text: "Share structured evidence, receive feedback and keep a record of corrections.",
  },
  {
    number: "04",
    title: "Keep the evidence",
    text: "Access completed work, supporting documents and generated PDF reports.",
  },
];

export function HomeContent() {
  return (
    <>
      <section className="ph-home-hero" aria-labelledby="home-title">
        <div className="ph-home-copy">
          <p className="ph-kicker">
            <span /> CONNECTED PHARMACOVIGILANCE
          </p>
          <h1 id="home-title">
            Safer decisions.
            <br />
            <em>Stronger together.</em>
          </h1>
          <p className="ph-home-lead">
            A clearer way to work with medicine safety.
          </p>
          <p className="ph-home-description">
            From clinical casework to college learning, PharmaSafe brings
            people, assignments and evidence together in a workspace built
            around your role.
          </p>
          <div className="ph-home-access">
            <span>
              <LockKeyhole size={18} />
            </span>
            <div>
              <strong>Choose the right login.</strong>
              <p>
                User Login is for students, domain administrators and safety teams.
                College administrators use College Administration Login.
              </p>
            </div>
          </div>
          <div className="ph-home-tags">
            <span>
              <CheckCircle2 size={15} /> Role-based access
            </span>
            <span>
              <CheckCircle2 size={15} /> Connected learning
            </span>
          </div>
        </div>
        <figure className="ph-home-photo">
          <img
            src="/images/safety-laboratory.webp"
            alt="Healthcare professional working among diagnostic instruments in a clinical laboratory"
            width="1400"
            height="933"
            fetchPriority="high"
          />
          <div className="ph-photo-label">
            <ShieldCheck size={16} /> PHARMASAFE SAFETY SUITE
          </div>
          <figcaption>
            <span>CARE STARTS WITH CLARITY</span>
            <p>
              Better connected.
              <br />
              Better informed.
            </p>
            <div>
              <span /> People. Process. Evidence.
            </div>
          </figcaption>
          <span className="ph-photo-corner" aria-hidden="true">
            <ArrowUpRight size={24} />
          </span>
        </figure>
      </section>
      <section className="ph-workflow" aria-labelledby="home-workflow-title">
        <div className="ph-section-heading">
          <div>
            <p className="ph-kicker">A MORE CONNECTED WAY TO WORK</p>
            <h2 id="home-workflow-title">Every step has a purpose.</h2>
          </div>
          <p>
            Keep the work moving, with the context
            <br className="ph-desktop-break" /> you need at every handoff.
          </p>
        </div>
        <ol className="ph-workflow-steps">
          {steps.map((step) => (
            <li key={step.number}>
              <span>{step.number}</span>
              <h3>{step.title}</h3>
              <p>{step.text}</p>
            </li>
          ))}
        </ol>
      </section>
      <section className="ph-purpose" aria-labelledby="home-purpose-title">
        <figure>
          <img
            src="/images/clinical-team.webp"
            alt="Healthcare professional reviewing information on a mobile device"
            width="1400"
            height="933"
            loading="lazy"
          />
          <figcaption>
            <GraduationCap size={18} /> Knowledge becomes meaningful through
            practice.
          </figcaption>
        </figure>
        <div className="ph-purpose-copy">
          <p className="ph-kicker">BUILT AROUND PEOPLE</p>
          <h2 id="home-purpose-title">
            Real work.
            <br />
            Meaningful learning.
          </h2>
          <p>
            A shared platform with a clear place for each person. Your account
            connects you to your own authorised work and college environment.
          </p>
          <ul>
            <li>
              <ClipboardList size={21} />
              <div>
                <h3>For safety teams</h3>
                <p>
                  Manage assigned casework, track progress and review the
                  evidence behind each response.
                </p>
              </div>
            </li>
            <li>
              <GraduationCap size={21} />
              <div>
                <h3>For colleges and students</h3>
                <p>
                  Learn through domain-specific assignments, structured
                  submissions and feedback from your college.
                </p>
              </div>
            </li>
            <li>
              <FileCheck2 size={21} />
              <div>
                <h3>A record you can return to</h3>
                <p>
                  Keep submissions, review history and PDF reports connected to
                  the work they describe.
                </p>
              </div>
            </li>
          </ul>
        </div>
      </section>
      <section className="ph-start" aria-labelledby="home-start-title">
        <div>
          <p className="ph-kicker">BEFORE YOU BEGIN</p>
          <h2 id="home-start-title">
            Your next step,
            <br /> made clear.
          </h2>
          <p>Everything you need to find your way into the right workspace.</p>
        </div>
        <div className="ph-home-faq">
          <details>
            <summary>I already have an account. How do I sign in?</summary>
            <p>
              Use User Login at the top of this page. College administrators use the separate College Administration Login. Enter your username or official
              email and password. Students can also use their Student ID. Your
              account opens its authorised workspace automatically.
            </p>
          </details>
          <details>
            <summary>How do I register as a student?</summary>
            <p>
              Choose Student registration, select your active college and
              complete the form for all seven learning domains. Your unique
              college-based Student ID appears after successful registration.
              Copy it and keep it for signing in.
            </p>
          </details>
          <details>
            <summary>When can I start my college assignments?</summary>
            <p>
              Your College Administration reviews your registration before granting
              domain access. Sign in to track your approval status. After
              approval, open an available domain to see work assigned to you.
            </p>
          </details>
          <details>
            <summary>What if I cannot find my college?</summary>
            <p>
              Your college must be set up and active before students can
              register. Contact your college coordinator to arrange access. If
              you already have an account and forgot your password, use Forgot
              password on the login page.
            </p>
          </details>
        </div>
      </section>
    </>
  );
}

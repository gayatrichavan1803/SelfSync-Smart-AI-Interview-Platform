# SelfSync — Technical Architecture & Product Document

**Product:** SelfSync — AI-powered smart interview practice platform  
**Audience:** Developers, reviewers, teammates, interviewers of the codebase  
**Last updated:** August 2026  

---

## 1. Executive summary

SelfSync lets candidates practice interviews (Technical, HR, Aptitude, Coding, System Design), answer via **text / voice / video**, and receive **multi-dimensional AI scoring**, **correct/model answers** when wrong, **analytics**, and **personalized learning recommendations**.

The system is a **two-tier web app**:

| Layer | Technology | Role |
|-------|------------|------|
| Frontend | React 19 + Vite + TypeScript | UI, Firebase Auth client, media capture, TTS |
| Backend | Java 21 + Spring Boot 3.3 | REST API, JWT security, persistence, AI orchestration |
| Database | Embedded H2 (file) | Users, sessions, answers, scores — no install |
| AI | Groq (Llama 3.3 + Whisper) | Question generation, evaluation, transcription |
| Auth (optional) | Firebase Auth | Email/password + Google → mapped to local users |

An older **ASP.NET Core** backend remains under `backend-dotnet/` as an archive; the active API is Java.

---

## 2. Tech stack (detailed)

### 2.1 Frontend

| Piece | Choice | Why |
|-------|--------|-----|
| Runtime UI | React 19 | Component model, large ecosystem, team familiarity |
| Language | TypeScript | Safer contracts with API DTOs |
| Bundler / Dev server | Vite 8 | Fast HMR, simple env (`VITE_*`) |
| Routing | React Router 7 | SPA routes for auth + app shell |
| Auth SDK | Firebase JS SDK | Google + email without building OAuth UI |
| Styling | Custom CSS (“liquid glass”) | No heavy UI kit; brandable, lightweight |
| Speech | Browser `speechSynthesis` | Read questions aloud with zero backend cost |
| Media | MediaRecorder + `getUserMedia` | Voice/video answers in-browser |

**Ports:** `http://localhost:5173` (dev)

### 2.2 Backend

| Piece | Choice | Why |
|-------|--------|-----|
| Language | Java 21 (Temurin) | LTS, strong enterprise fit for campus/industry demos |
| Framework | Spring Boot 3.3 | Security, JPA, validation, REST in one stack |
| Security | Spring Security + JJWT | Stateless JWT for API after login |
| Persistence | Spring Data JPA + Hibernate | Rapid schema evolve (`ddl-auto: update`) for MVP |
| JDBC driver | H2 | Embedded; no external DB server |
| Docs | springdoc-openapi (Swagger UI) | Explore/test APIs at `/swagger` |
| HTTP to AI | RestClient + Java `HttpClient` | Chat via RestClient; Whisper multipart via HttpClient |

**Ports:** `http://localhost:5126`  
**Start script:** `backend/run.cmd` (uses bundled Maven; starts API with H2)

### 2.3 Database

| Piece | Choice | Why |
|-------|--------|-----|
| Engine | **H2** (embedded, file mode) | Zero install; works on any laptop with JDK |
| Location | `backend/data/selfsync.*` | Persists between restarts |
| Console | `/h2-console` | Optional browser UI to inspect tables |
| Auth | `sa` / empty password (local only) | Dev convenience |

> Older setups used SQL Server Express. That dependency was removed so teammates don’t need to install or configure SQL Server.

**Core tables (conceptual):**

- `users` — profile, password hash (nullable for OAuth), `firebase_uid`, `provider`, reset tokens  
- `interview_sessions` — type, domain, difficulty, status  
- `questions` — ordered prompts per session  
- `answers` — text / transcript / media path / modality  
- `score_reports` — aggregate scores + feedback + JSON `question_reviews`

### 2.4 AI (Groq)

| Capability | Model / API | Used for |
|------------|-------------|----------|
| Chat / JSON | `llama-3.3-70b-versatile` | Generate questions; evaluate sessions; per-question reviews |
| Speech-to-text | `whisper-large-v3` | Transcribe voice/video uploads |
| Base URL | `https://api.groq.com/openai/v1` | OpenAI-compatible API shape |

**Why Groq (vs OpenAI):** Lower friction for free/dev keys; OpenAI hit quota limits during earlier development. Groq stays OpenAI-compatible so switching providers later is mostly URL/key/model changes.

**Fallbacks:** If Groq is missing or fails, the API still returns shuffled template questions and heuristic offline scores so demos don’t hard-crash.

### 2.5 Authentication

| Mode | How it works |
|------|----------------|
| **Local (SELF)** | Register/login → BCrypt password hash → issue app JWT |
| **Firebase email** | Firebase creates user → ID token → `POST /api/auth/firebase` → map/upsert SQL user → app JWT |
| **Google** | Firebase Google popup → same mapping path; `provider = GOOGLE` |
| **Forgot/reset** | Firebase email reset **or** local reset-token flow for SELF accounts |

**App JWT** is what protects `/api/interviews`, `/api/analytics`, `/api/learning`, etc. Firebase is an **identity front door**, not the authorization system for business APIs.

---

## 3. How the system works (end-to-end)

### 3.1 High-level flow

```text
┌─────────────┐     REST + JWT      ┌──────────────────┐   embedded    ┌────────────┐
│  React SPA  │ ◄─────────────────► │  Spring Boot API │ ◄────────────► │ H2 file DB │
│  Vite :5173 │                     │  :5126           │                │ data/      │
└──────┬──────┘                     └────────┬─────────┘                └────────────┘
       │ Firebase Auth                       │
       ▼                                     │ HTTPS
┌─────────────┐                              ▼
│  Firebase   │                     ┌──────────────────┐
│  Auth       │                     │  Groq (Llama +   │
└─────────────┘                     │  Whisper)        │
                                    └──────────────────┘
```

### 3.2 Interview lifecycle

1. **Select options** — Type drives domain list (no conflicting HR+Java buttons). Difficulty: Easy → Expert.  
2. **Start** — `POST /api/interviews` → validate type/domain pair → Groq generates **5 fresh questions** (variety token + higher temperature) → persist session.  
3. **Practice** — UI shows question text; optional browser TTS reads it. User answers Text   - Text: `POST .../answers`  
   - Voice/Video: multipart `POST .../answers/media` → file under `Uploads/` → Whisper transcript  
4. **Complete** — `POST .../complete` → Groq evaluates all Q&A → overall scores + strengths/weaknesses + **per-question verdict** (`correct` / `partial` / `incorrect` / `unanswered`) and **model answer** when not fully correct.  
5. **Results / Report** — SPA results page + optional HTML report download.  
6. **Analytics / Learning** — Aggregates scores, streak, weekly goal; recommends content from weak skills.

### 3.3 Auth mapping (Firebase → SQL)

```text
Firebase user (uid, email, provider)
        │
        ▼
POST /api/auth/firebase { idToken }
        │
        ▼
Identity Toolkit accounts:lookup (verify token with Web API key)
        │
        ▼
Find/create User row:
  firebaseUid, email, provider ∈ {SELF, GOOGLE, GITHUB, FIREBASE}
        │
        ▼
Issue SelfSync JWT (subject = user UUID)
```

Public health: `GET /api/health/ai` probes Groq (no key leaked) so the dashboard can show “Groq AI online” to everyone.

---

## 4. Feature catalog

| Feature | Status | Notes |
|---------|--------|-------|
| Local register / login / JWT | Done | Works without Firebase |
| Firebase email + Google | Done | Needs Console providers + `.env` / yml |
| Profile (name, phone, avatar URL) | Done | Shows provider + Firebase mapping |
| Type-linked domains + expanded catalogs | Done | Technical / HR / Aptitude / Coding / System Design |
| Fresh questions each session | Done | UUID variety seed + temp 0.95 + shuffle fallbacks |
| Text / voice / video answers | Done | Media stored locally; Whisper transcript |
| Question TTS (browser) | Done | Mute / replay controls |
| Multi-score evaluation | Done | Technical, communication, confidence, problem-solving, overall |
| Correct answer on wrong/partial | Done | Stored in `questionReviewsJson` |
| HTML report | Done | Includes verdicts + model answers |
| Analytics (averages, trends, streak, weekly goal) | Done | |
| Learning recommendations | Done | Rule/content bank from weak areas |
| Password strength + remember-me | Done | Frontend UX |
| Forgot / reset password | Done | Firebase and/or local token |
| Liquid-glass UI | Done | Frosted panels, brand auth split |
| Groq status banner | Done | Public health endpoint |
| Swagger UI | Done | `/swagger` |
| Admin console / multi-tenant orgs | Not built | Future |
| Real-time live interviewer avatar | Not built | Future |
| Production cloud deploy | Not packaged | Needs secrets, HTTPS, migrations |

---

## 5. Why these design choices

### 5.1 Java backend (after .NET)

- Campus / industry alignment for many Java coursework tracks  
- Spring Security + JPA are battle-tested for JWT REST APIs  
- `.NET` version kept as archive to avoid losing earlier work  

### 5.2 H2 embedded database

- Zero install for classmates / other laptops  
- File persistence under `backend/data/`  
- Tradeoff: not ideal for heavy multi-user production (swap to Postgres later if needed)  

### 5.3 Groq for AI

- Fast iteration and free-tier friendliness during development  
- OpenAI-compatible endpoints reduce rewrite cost  
- Whisper handles multimodal answers without a separate ASR stack  

### 5.4 Firebase as optional identity layer

- Google sign-in and managed password reset without custom OAuth  
- Local JWT remains source of truth for API authorization  
- Mapping table keeps analytics tied to one user id across providers  

### 5.5 Type → domain coupling

- Prevents nonsensical combinations (e.g. Aptitude + React)  
- Improves AI prompt quality (domain stays on-type)  

### 5.6 Offline / degraded AI modes

- Demos and grading still work if Groq is down or rate-limited  
- Transparent messaging when offline scoring is used  

---

## 6. Pros and cons

### 6.1 Overall architecture

| Pros | Cons |
|------|------|
| Clear SPA + API separation | Two processes to run locally |
| Stateless JWT scales horizontally later | Token revocation needs extra work (blacklist/short TTL) |
| AI isolated behind `AiService` | Vendor lock-in risk (mitigated by OpenAI-shaped client) |
| Feature-rich MVP for demos | Schema via `ddl-auto=update` is not production-grade migrations |

### 6.2 Frontend (React + Vite)

| Pros | Cons |
|------|------|
| Fast DX, typed UI | Custom CSS must be maintained by hand |
| Firebase client is quick to add | Secrets in `VITE_*` are public by design (restrict API keys in Google Cloud) |
| Browser TTS/MediaRecorder = zero infra | Browser support varies; Safari/permissions quirks |

### 6.3 Backend (Spring Boot)

| Pros | Cons |
|------|------|
| Mature security/validation/JPA | Heavier than a Node BFF for tiny teams |
| Swagger for API exploration | Cold start slower than Node |
| Structured services/controllers | Windows JDBC DLL path is brittle with spaces |

### 6.4 H2 database

| Pros | Cons |
|------|------|
| No install / no ports | Single-file DB; not for huge production scale |
| Works on any OS with JDK | Concurrent multi-process access is limited |
| Easy to reset (delete `data/`) | Team must share `data/` if they want shared local data |

### 6.5 Groq AI

| Pros | Cons |
|------|------|
| Good latency; free key available | Rate limits (429) under load |
| JSON mode for structured eval | Model answers can be imperfect / hallucinated |
| Whisper for voice/video | Media upload size/cost; privacy of audio files |

### 6.6 Firebase Auth

| Pros | Cons |
|------|------|
| Google + email quickly | Extra console configuration |
| Password reset email hosted by Google | Depends on third party uptime |
| Clean UID for mapping | Must keep Web API key + authorized domains correct |

---

## 7. Keynotes (remember these)

1. **JWT after every login path** — Firebase proves identity once; SelfSync JWT authorizes APIs.  
2. **Never expose Groq key to the browser** — only the backend calls Groq.  
3. **Firebase Web API key is expected in the frontend** — protect with HTTP referrer restrictions.  
4. **Domains must match interview type** — enforced in UI and `InterviewCatalog` validation.  
5. **Questions are intentionally non-deterministic** — variety token + temperature; don’t expect bit-identical sets.  
6. **Correct answers appear after completion** — not while answering mid-interview (avoids spoiling practice).  
7. **H2 data lives in `backend/data/`** — delete that folder to reset all users/sessions.  
8. **`backend/run.cmd`** — prefer this; uses the bundled Maven.  
9. **Uploads folder** — voice/video files land on disk under `backend/Uploads/`; not cloud storage yet.  
10. **Health endpoint is public** — `GET /api/health/ai` shows AI status without leaking secrets.  
11. **Hibernate `ddl-auto: update`** — fine for MVP; use Flyway/Liquibase before production.  
12. **Archived `backend-dotnet/`** — do not run both APIs on the same port casually.

---

## 8. Project layout

```text
interview ai/
├── frontend/                 # React + Vite SPA
│   ├── src/
│   │   ├── pages/            # Login, Home, Interview, Results, Analytics, Learning, Profile…
│   │   ├── api.ts            # fetch helpers + DTO types
│   │   ├── auth.tsx          # Auth context (local + Firebase)
│   │   ├── firebase.ts       # Firebase init + helpers
│   │   ├── interviewOptions.ts
│   │   └── index.css         # Liquid-glass theme
│   ├── .env                  # Local secrets (gitignored)
│   └── .env.example
├── backend/                  # Spring Boot API (active)
│   ├── src/main/java/com/selfsync/api/
│   │   ├── controller/       # Auth, Interviews, Analytics, Learning, Health
│   │   ├── service/          # Auth, Interview, Ai, Firebase, Learning…
│   │   ├── model/            # JPA entities
│   │   ├── security/         # JWT filter
│   │   └── config/           # Security, properties, startup checks
│   ├── src/main/resources/application.yml
│   ├── Uploads/              # Media answers
│   └── run.cmd
├── backend-dotnet/           # Archived ASP.NET implementation
├── .tools/                   # Bundled Maven
├── README.md                 # Quick start
├── HOW_TO_RUN.md             # Run guide
└── TECH_STACK.md             # This document
```

---

## 9. Main API map

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/api/auth/register` | Public | Local register |
| POST | `/api/auth/login` | Public | Local login |
| POST | `/api/auth/firebase` | Public | Firebase ID token → JWT |
| POST | `/api/auth/forgot-password` | Public | Local reset kickoff |
| POST | `/api/auth/reset-password` | Public | Local reset complete |
| GET | `/api/auth/me` | JWT | Current user |
| PUT | `/api/auth/profile` | JWT | Update profile |
| GET | `/api/health/ai` | Public | Groq status for UI |
| GET | `/api/interviews/catalog` | JWT | Types / domains / difficulties |
| POST | `/api/interviews` | JWT | Start session |
| GET | `/api/interviews` | JWT | List / filter |
| GET | `/api/interviews/{id}` | JWT | Detail |
| POST | `/api/interviews/{id}/answers` | JWT | Text answer |
| POST | `/api/interviews/{id}/answers/media` | JWT | Voice/video |
| POST | `/api/interviews/{id}/complete` | JWT | Score session |
| GET | `/api/interviews/{id}/report` | JWT | HTML report |
| GET | `/api/analytics/summary` | JWT | Analytics |
| GET | `/api/learning/recommendations` | JWT | Learning pack |

---

## 10. Security notes

- Store **Groq** and production JWT signing secrets outside git when possible (env vars).  
- Rotate keys if they were ever pasted into chat or committed.  
- CORS allowlist is localhost-oriented in `application.yml`.  
- Validate Firebase tokens server-side before trusting email/uid.  
- Treat uploaded media as sensitive PII; add retention policy before production.  
- Replace `ddl-auto: update` and the default JWT secret before any public deploy.

---

## 11. Operational runbook (local)

1. `cd backend` → `.\run.cmd` → wait for “Started SelfSyncApplication”.  
2. Confirm log lines: Groq configured + Firebase configured (if using Firebase).  
3. `cd frontend` → `npm run dev` (restart after any `.env` change).  
4. Open `http://localhost:5173` — Groq banner should show online.  
5. Smoke: register → interview → finish → see scores + correct answers → analytics.

---

## 12. Future improvements (suggested)

| Area | Idea |
|------|------|
| Data | Flyway migrations; backup strategy |
| AI | Cache embeddings; store past questions to explicitly ban repeats per user |
| Media | Azure Blob / S3 instead of local disk |
| Auth | Refresh tokens; optional Firebase Admin SDK verification |
| UX | Live coding editor pane for Coding type; radar charts |
| Quality | Integration tests for auth + complete interview |
| Deploy | Docker Compose (API + DB) or Azure App Service |

---

## 13. One-line architecture thesis

**SelfSync is a Spring Boot interview coach with a React client: Firebase (optional) proves who you are, H2 remembers what you did, and Groq generates, hears, and grades the practice — with graceful offline fallbacks and explicit correct answers when you miss.**

---

*For day-to-day setup and run steps, see [HOW_TO_RUN.md](./HOW_TO_RUN.md).  
For a short overview, see [README.md](./README.md).*

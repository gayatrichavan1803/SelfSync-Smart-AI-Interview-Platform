<<<<<<< HEAD
=======
[README.md](https://github.com/user-attachments/files/30637856/README.md)
>>>>>>> a7c622d28b9808c6547292766d299bebfc859b1b
# SelfSync

AI-powered smart interview platform: simulate HR / Technical / Aptitude interviews, submit text / voice / video answers, and get multi-dimensional AI scoring with analytics.

**Docs**

- [HOW_TO_RUN.md](./HOW_TO_RUN.md) — full setup and run guide  
- [TECH_STACK.md](./TECH_STACK.md) — architecture, features, pros/cons  

## Stack

- **Frontend:** React (Vite) + React Router
- **Backend:** Java 21 + Spring Boot 3 (Web, Security, JPA)
- **Database:** Embedded **H2** (file under `backend/data/` — no SQL Server install)
- **AI:** Groq (Llama chat + Whisper transcription; optional free API key)

## Prerequisites

- [Eclipse Temurin JDK 21](https://adoptium.net/) (or any JDK 21+)
- Node.js 20+
- Free [Groq API key](https://console.groq.com/keys) (optional but recommended)

Maven is bundled under `.tools/apache-maven-3.9.9`.

**Full run guide:** [HOW_TO_RUN.md](./HOW_TO_RUN.md)

## Configure

### Backend (`backend/src/main/resources/application.yml`)

```yaml
selfsync:
  groq:
    api-key: YOUR_GROQ_API_KEY
  firebase:
    api-key: YOUR_FIREBASE_WEB_API_KEY
    project-id: YOUR_FIREBASE_PROJECT_ID
```

### Frontend (`frontend/.env` — copy from `.env.example`)

```bash
VITE_API_URL=http://localhost:5126
VITE_FIREBASE_API_KEY=...
VITE_FIREBASE_AUTH_DOMAIN=...
VITE_FIREBASE_PROJECT_ID=...
VITE_FIREBASE_STORAGE_BUCKET=...
VITE_FIREBASE_MESSAGING_SENDER_ID=...
VITE_FIREBASE_APP_ID=...
```

Firebase Auth (email/password + Google) maps to a local SQL `users` row via `POST /api/auth/firebase` (`firebaseUid` + `provider` mapping: SELF / GOOGLE / GITHUB / FIREBASE).

Without Firebase config, local JWT email/password auth still works.

## Run backend

```powershell
cd backend
.\run.cmd
```

Or manually:

```powershell
..\.\.tools\apache-maven-3.9.9\bin\mvn.cmd spring-boot:run
```

API: http://localhost:5126  
Swagger UI: http://localhost:5126/swagger  
H2 console: http://localhost:5126/h2-console  

On startup Hibernate creates/updates tables in the local H2 file database (`backend/data/selfsync`).

## Run frontend

```powershell
cd frontend
npm install
npm run dev
```

App: http://localhost:5173

## Smoke test path

1. Register a user at `/register`
2. Start a Technical / Python / Medium interview
3. Answer with text (and optionally record voice/video)
4. Finish interview → view scores & feedback
5. Open Analytics for domain trends and history
6. Download the HTML report from Results

## Main API routes

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/register` | Register |
| POST | `/api/auth/login` | Login |
| GET | `/api/auth/me` | Current user |
| PUT | `/api/auth/profile` | Update name |
| POST | `/api/interviews` | Start session + generate questions |
| GET | `/api/interviews` | List sessions (`domain`, `status` filters) |
| GET | `/api/interviews/{id}` | Session detail |
| POST | `/api/interviews/{id}/answers` | Submit text answer |
| POST | `/api/interviews/{id}/answers/media` | Upload voice/video (multipart) |
| POST | `/api/interviews/{id}/complete` | Evaluate + score |
| GET | `/api/interviews/{id}/report` | HTML report |
| GET | `/api/analytics/summary` | Analytics summary |

## Project layout

```
interview ai/
  backend/          Spring Boot API (Java)
  backend-dotnet/   Previous ASP.NET Core implementation (archived)
  frontend/         Vite React TypeScript app
  .tools/           Local Maven + JDBC auth DLL
  README.md         # Quick start
  TECH_STACK.md     # Architecture, features, pros/cons, keynotes
```

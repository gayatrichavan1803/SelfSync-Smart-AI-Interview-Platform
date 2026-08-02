# SelfSync — How to Run

Step-by-step guide to run SelfSync on **Windows** (or any machine with JDK 21 + Node).

**Database:** embedded **H2** (file under `backend/data/`) — **no SQL Server install**.

For architecture details, see [TECH_STACK.md](./TECH_STACK.md).  
For a short overview, see [README.md](./README.md).

---

## 1. What you need installed

| Requirement | Version / notes | Check |
|-------------|-----------------|-------|
| **JDK** | 21+ (Eclipse Temurin recommended) | `java -version` |
| **Node.js** | 20+ | `node -v` / `npm -v` |

**Not required:** SQL Server, Docker, or any database server.

**Bundled in this repo:**

- Maven → `.tools/apache-maven-3.9.9`

**Accounts (optional but recommended):**

- [Groq API key](https://console.groq.com/keys) — AI questions, scoring, Whisper  
- [Firebase project](https://console.firebase.google.com/) — Google / Firebase email login  

Without Groq, the app still runs with offline fallback questions/scores.  
Without Firebase, local Register/Login still works.

---

## 2. Database (automatic)

SelfSync uses **H2**, an embedded file database.

- Created automatically on first API start  
- Files live in `backend/data/selfsync.*`  
- No services to start, no TCP ports, no `CREATE DATABASE`

Optional browser console (while API is running):

- URL: http://localhost:5126/h2-console  
- JDBC URL: `jdbc:h2:file:./data/selfsync`  
- User: `sa`  
- Password: *(leave empty)*

---

## 3. One-time project configuration

### 3.1 Backend — `backend/src/main/resources/application.yml`

Set at least Groq (recommended):

```yaml
selfsync:
  groq:
    api-key: "gsk_your_groq_key_here"
  firebase:
    api-key: "AIza_your_firebase_web_api_key"   # optional
    project-id: "your-firebase-project-id"      # optional
```

### 3.2 Frontend — create `frontend/.env`

```powershell
cd frontend
copy .env.example .env
```

Edit `.env` (Firebase values optional):

```env
VITE_API_URL=http://localhost:5126
VITE_FIREBASE_API_KEY=...
VITE_FIREBASE_AUTH_DOMAIN=....firebaseapp.com
VITE_FIREBASE_PROJECT_ID=...
VITE_FIREBASE_STORAGE_BUCKET=...
VITE_FIREBASE_MESSAGING_SENDER_ID=...
VITE_FIREBASE_APP_ID=...
```

**Important:** Restart `npm run dev` whenever you change `.env`.

### 3.3 Frontend packages (first time)

```powershell
cd frontend
npm install
```

---

## 4. Run the app (every day)

You need **two terminals**.

### Terminal A — Backend (API)

```powershell
cd backend
.\run.cmd
```

Wait until you see:

```text
Started SelfSyncApplication
Tomcat started on port 5126
```

Also check logs for:

- `Groq API key is configured …`
- `Firebase is configured …` (only if Firebase is filled in)

**URLs:**

| Service | URL |
|---------|-----|
| API | http://localhost:5126 |
| Swagger | http://localhost:5126/swagger |
| AI health | http://localhost:5126/api/health/ai |
| H2 console | http://localhost:5126/h2-console |

### Terminal B — Frontend (UI)

```powershell
cd frontend
npm run dev
```

Open: **http://localhost:5173**

---

## 5. First-time smoke test

1. Open http://localhost:5173  
2. Confirm **Groq AI online** (if key is set)  
3. **Register** (or Firebase/Google if configured)  
4. Start an interview → answer → **Finish & evaluate**  
5. Check Results (scores + correct answers) → Analytics → Learning  

---

## 6. Firebase (optional)

1. Enable Email/Password + Google in Firebase Console  
2. Authorized domains includes `localhost`  
3. Fill `frontend/.env` → restart Vite  
4. Fill `application.yml` firebase block → **restart API**  

---

## 7. Useful commands

```powershell
# Probe AI
Invoke-RestMethod http://localhost:5126/api/health/ai

# Force-restart API
Get-NetTCPConnection -LocalPort 5126 -State Listen -EA SilentlyContinue |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -EA SilentlyContinue }
cd backend
.\run.cmd

# Force-restart Vite
Get-NetTCPConnection -LocalPort 5173 -State Listen -EA SilentlyContinue |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -EA SilentlyContinue }
cd frontend
npm run dev
```

### Reset all local data

Stop the API, then delete the folder:

```text
backend/data/
```

Next start creates a fresh empty database (you’ll need to register again).

---

## 8. Troubleshooting

| Problem | What to do |
|---------|------------|
| `Connection refused` on 5126 | Start `backend\run.cmd` |
| Blank UI / API errors | Check `VITE_API_URL` and that API is up |
| Port 5126 in use | Kill old Java process, restart `run.cmd` |
| Groq offline / fallback scores | Fix `selfsync.groq.api-key`, restart API |
| `Firebase is not configured` | Set key in yml, **restart API** |
| Firebase buttons missing | Fill `.env`, restart Vite |
| Lost users after copy to another PC | H2 files are in `backend/data/` — copy that folder too, or re-register |

---

## 9. Ports

| Port | Service |
|------|---------|
| **5126** | Spring Boot API (+ H2 console) |
| **5173** | Vite React app |

---

## 10. Minimal happy path

```text
1. Install JDK 21 + Node 20+
2. Put Groq key in application.yml
3. cd backend  → .\run.cmd       → http://localhost:5126
4. cd frontend → npm install && npm run dev → http://localhost:5173
5. Register → Interview → Finish → Results
```

No SQL Server. No Docker. Just Java + Node.

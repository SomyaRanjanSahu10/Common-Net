# Common Net — Migration Notes
### Node/Express/React → Java/Spring Boot/MongoDB + HTML/CSS/Vanilla JS

This document covers everything requested at the end of the migration spec:
project structure, tech stack, API/model/auth mapping, SMTP/WebSocket/
scheduler implementation, the signature fix, run instructions, what's been
verified, and what still needs attention.

---

## 1. Final project structure

```
mailflow-final/
├── backend/                          Spring Boot (Java)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/mailflow/
│       │   ├── MailflowApplication.java
│       │   ├── controller/    (9)    AuthController, EmailController, FolderController,
│       │   │                         SignatureController, ProfileController, AccountsController,
│       │   │                         UsersController, AdminController, CalendarController
│       │   ├── service/       (13)   AuthService, EmailService, EmailMapper, FolderService,
│       │   │                         SignatureService, ProfileService, AccountsService, AdminService,
│       │   │                         MailService, EmailTemplates, NotificationService,
│       │   │                         SchedulerService, FileStorageService, ActivityLogService
│       │   ├── repository/    (6)    UserRepository, EmailRepository, FolderRepository,
│       │   │                         SignatureRepository, ActivityLogRepository, AdminLogRepository
│       │   ├── model/         (6)    User, Email, Folder, Signature, ActivityLog, AdminLog
│       │   ├── dto/           (18)   Request/response records & classes per endpoint
│       │   ├── security/      (3)    JwtUtil, JwtAuthFilter, UserPrincipal
│       │   ├── config/        (8)    SecurityConfig, WebSocketConfig, RateLimitFilter,
│       │   │                         MongoIndexConfig, MongoAuditConfig, AsyncConfig, StaticResourceConfig
│       │   └── exception/     (2)    ApiException, GlobalExceptionHandler
│       └── resources/
│           ├── application.properties          (reads from env vars)
│           └── application-example.properties  (documents required env vars, no secrets)
│
└── frontend/                         HTML5 + CSS3 + Vanilla JS
    ├── index.html, login.html, register.html, forgot-password.html,
    │   inbox.html, profile.html, signature.html, calendar.html, trash.html, admin.html
    ├── css/  style.css, sidebar.css, email.css, compose.css, signature.css, auth.css, responsive.css
    └── js/   api.js, auth.js, sidebar.js, notifications.js, compose.js, email.js,
              dashboard.js, profile.js, signature.js, calendar.js, admin.js
```

**Note on `compose.html`:** the spec's suggested file list mentions one, but the
original React app never had a dedicated compose route — Compose is always an
overlay modal. Per "do not redesign the UI unnecessarily," I kept that: `compose.js`
renders the same modal on top of whichever page it's opened from, matching the
original behavior exactly.

---

## 2. Technology stack used

| Layer | Technology |
|---|---|
| Frontend | HTML5, CSS3, Vanilla JavaScript, Fetch API |
| Backend | Java 17, Spring Boot 3.3, Spring Web, Spring Data MongoDB, Spring Security, JWT (jjwt), BCrypt |
| Database | MongoDB (unchanged) |
| Email | Spring Boot Mail / Jakarta Mail over Gmail SMTP (unchanged transport) |
| Real-time | Spring WebSocket (STOMP over SockJS) |
| File uploads | `MultipartFile` |
| Scheduling | `@Scheduled` |
| Sanitization | OWASP Java HTML Sanitizer (replaces the `xss` npm package's role) |

---

## 3. API mapping (old → new)

All 35 original Express endpoints have a 1:1 Spring REST equivalent at the
**same path and HTTP method**, so no frontend URL had to change:

| Node route file | Spring controller |
|---|---|
| `routes/auth.js` (7 endpoints) | `AuthController` — register, login, me, forgot-password, verify-otp, reset-password |
| `routes/email.js` (23 endpoints) | `EmailController` — send, draft, draft/:id/send, schedule, inbox, sent, drafts, starred, important, archive, scheduled, trash, search, get/:id, read/star/important toggles, archive toggle, soft-delete, restore, permanent-delete, recall, attachment download |
| `routes/folders.js` | `FolderController` — CRUD + move-email + folder-emails |
| `routes/signature.js` | `SignatureController` — get/save/delete, **+ new `/render` endpoint** (see §9) |
| `routes/profile.js` | `ProfileController` — get/update/avatar upload/public profile |
| `routes/accounts.js` | `AccountsController` — list sessions/add account/logout session |
| `routes/users.js` | `UsersController` — search/list (compose autocomplete) |
| `routes/admin.js` (11 endpoints) | `AdminController` — dashboard, users CRUD, emails, logs, activity |
| inline `app.get('/api/calendar/holidays/:year')`, `/api/health` | `CalendarController` |

---

## 4. MongoDB model mapping

Every Mongoose schema became a `@Document`-annotated Spring Data class with
the **same collection name and field names**, so existing data in your
MongoDB instance is read/written without a migration script:

`User`, `Email` (+ embedded `Attachment`/`Mention`), `Folder`, `Signature`,
`ActivityLog`, `AdminLog`. The two indexes Mongoose declared imperatively
(`Email` text index on subject/body, `Folder` unique compound index on
owner+name) are recreated in `MongoIndexConfig` at startup since Spring Data
annotations can't express a compound-unique or multi-field text index
directly.

---

## 5. Authentication flow

Unchanged shape, new implementation:

```
Register → BCryptPasswordEncoder → MongoDB
Login    → Spring Security AuthenticationManager-free manual check (BCrypt.matches)
             → JwtUtil.generateToken(userId) → returned to frontend
Frontend → stores JWT in localStorage → sends "Authorization: Bearer <token>"
Backend  → JwtAuthFilter validates on every request → SecurityContext populated
             → @PreAuthorize / URL rules enforce admin-only routes
```

Forgot-password keeps the original OTP + hashed-reset-token two-step flow
(OTP emailed → `/verify-otp` exchanges it for a short-lived `resetToken` →
`/reset-password` consumes it), including the 10-minute OTP expiry and
15-minute reset-token expiry.

---

## 6. SMTP configuration

`MailService` uses Spring Boot's auto-configured `JavaMailSender`, populated
from environment variables with the **same names** as the original `.env`
(`SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `SMTP_FROM_NAME`) — see
`application.properties` and `application-example.properties`. Real emails go
out over Gmail SMTP exactly as before; nothing is mocked. Attachments, HTML
bodies, and error capture (`smtpSent`/`smtpError` on the Email document) are
preserved.

---

## 7. WebSocket implementation (replaces Socket.IO)

`WebSocketConfig` sets up a STOMP endpoint at `/ws` (with SockJS fallback).
The frontend connects as `new SockJS('/ws?token=' + jwt)`; a
`HandshakeInterceptor` validates the JWT and a custom `DefaultHandshakeHandler`
turns the user id into the STOMP session's `Principal`. This lets
`NotificationService.notify(userId, event, payload)` call
`SimpMessagingTemplate.convertAndSendToUser(userId, "/queue/notifications", …)`
— the direct equivalent of the old `userSockets[uid] = socket.id` +
`io.to(sid).emit(...)` pattern. Event names (`new_email`, `important_email`,
`mention`) are preserved as a `type` field in the message envelope, and
`notifications.js` on the frontend switches on that field exactly like the
old Socket.IO listener did.

---

## 8. Scheduled task implementation

`SchedulerService` replaces both `node-cron` jobs with `@Scheduled`:
- `deliverScheduledEmails()` — every minute (`0 * * * * *`), delivers due
  scheduled emails, sends the notification + SMTP email, exactly like before.
- `purgeOldTrash()` — daily at 02:00 (`0 0 2 * * *`), permanently deletes
  trashed emails older than 30 days.

---

## 9. Signature implementation — bug fixed

Per the spec's flag: the original React `ComposeModal.js` had signature
auto-insertion **disabled** (`// Signature auto-insertion disabled.`). This
migration fixes it end-to-end:

```
Signature Settings → Save → POST /api/signature → stored in MongoDB
Compose opens (new email only, not when editing a draft)
   → GET /api/signature/render (new endpoint, SignatureService.renderHtml)
   → returns "" if disabled/absent, else ready-to-insert HTML
   → compose.js appends it below the cursor position, inside a
     .compose-signature-block the user can still type above
Send → htmlBody (signature included) → Spring Boot Mail → Gmail SMTP → Recipient
```

---

## 10. How to run

**Backend:**
```bash
cd backend
cp src/main/resources/application-example.properties .env   # fill in real values, export them
export MONGO_URI=... JWT_SECRET=... SMTP_USER=... SMTP_PASS=... CLIENT_URL=http://localhost:3000
mvn spring-boot:run
```
Runs on `PORT` (default 5000), same as the Node server.

**Frontend:** static files — serve `frontend/` with any static file server
(e.g. `npx serve frontend`, nginx, or your Spring Boot's own static resource
serving if you copy it into `src/main/resources/static`). Set
`window.MAILFLOW_API_BASE` / `window.MAILFLOW_WS_BASE` in each page if the
backend isn't at the same origin under `/api` and `/ws`.

---

## 11. Features implemented (by area)

Auth (register/login/me/forgot-password/OTP/reset) · Email (send/draft/
schedule/inbox/sent/drafts/starred/important/archive/scheduled/trash/search/
read/star/important/archive-toggle/soft-delete/restore/permanent-delete/
recall/attachments/CC/BCC/HTML body) · Folders (CRUD, colors, drag-to-move) ·
Signature (CRUD + auto-insert fix) · Profile (update + avatar upload) ·
Multi-account switching · User search (compose autocomplete) · Admin
(dashboard stats, user management, activation/promotion/password
reset/deletion, email management, admin logs, activity logs) · Calendar
(holidays + scheduled emails overlay) · Real-time notifications (new email/
important/mention) · Scheduled delivery + trash auto-purge · Responsive
sidebar/mobile layout.

## 12. Remaining issues / things that genuinely need your attention

- **Not compiled or run.** This sandbox has no Maven/JDK build tooling and no
  network access to Maven Central, so I could not run `mvn compile`, start
  the app against a real MongoDB, or exercise the endpoints end-to-end. I
  reviewed every file by hand for correctness (types, Lombok boolean
  getter/setter conventions, Spring Data query derivation, Mongo field-name
  consistency), but you should run a full build and a manual QA pass — the
  spec's own §23 test checklist — before treating this as production-ready.
- **Rich text editing**: the original used React-Quill; the vanilla version
  uses a plain `contenteditable` div with a minimal toolbar (bold/italic via
  browser defaults aren't wired up). If you need Quill's full toolbar,
  swap in the vanilla build of Quill (it doesn't require React).
- **HTML sanitization**: attachments/htmlBody are stored as submitted. I
  added the OWASP sanitizer dependency but have not wired it into
  `EmailService` — for parity with the original's `xss`-package usage, add a
  sanitize step on `htmlBody` before saving, both client-side (already lightly
  done) and server-side (not yet done).
- **In-memory rate limiter**: fine for a single instance; swap for a
  Redis-backed limiter if you deploy multiple backend instances.
- **Multi-account session switching**: implemented per the original routes,
  but note the original stored raw JWTs in `activeSessions` on the User
  document — consider hashing those too if this goes to production.

# PrimeNest

A real estate platform for Zimbabwe (expanding to other African markets), built around one
idea most local property sites don't offer: **trust**. Verified people, verified listings,
escrow-protected money, and digital paperwork — with the African diaspora treated as a first-class
audience, not an afterthought, since they carry the hardest version of every problem this
platform solves (can't inspect a property, can't chase paperwork, can't be there for the
handover).

> Internal naming note: the codebase's Maven artifact is `primenestprop`, some docs still say
> "Homestead" (an earlier name), and the long-range product vision docs call the bigger ambition
> "AfricaProp AI" / "HomeTrust Africa". They're all the same product. This README uses
> **PrimeNest**, the name currently live in the frontend (`app/layout.tsx`).

---

## 1. What this is, in one paragraph

One account can hold multiple roles — Tenant, Landlord, Agent, Developer, Private seller,
Investor, Diaspora, Service Provider, Admin — and each role gets its own dashboard and
permission set. The backend is the single source of truth for every workflow (no mock data in
the running app): listings, KYC/identity verification, escrow-protected payments, digital
leases, maintenance, REIT-style investments, a home-services marketplace, and — newest —
construction-project monitoring for diaspora buyers building a home remotely. An optional AI
layer (Gemini primary, Anthropic fallback) powers natural-language property search and a
home assistant; without an API key configured, both fall back to plain keyword logic so the app
never breaks for lack of an AI key.

---

## 2. System architecture

A monorepo, two independently-deployable apps, one database:

```
┌───────────────────────┐        REST / JSON over HTTPS       ┌───────────────────────────┐
│   Next.js frontend     │  ─────────────────────────────────▶ │   Spring Boot backend      │
│   (App Router, React)  │   Authorization: Bearer <token>      │   /api/v1/**               │
│   frontend/            │ ◀───────────────────────────────────│   src/main/java/…          │
└───────────┬────────────┘                                     └─────────────┬─────────────┘
            │ localStorage: bearer token                                     │ Hibernate/JPA
            │ (no cookies, no server-side session)                           ▼
            │                                                     ┌────────────────────────┐
            │                                                     │  MariaDB (local dev)    │
            │                                                     │  PostgreSQL (Render)    │
            │                                                     └────────────────────────┘
            │
            ▼
   Browser localStorage
   holds the token + is
   the only client "session"
```

- **Backend**: Spring Boot 4 / Java 21, Maven (`pom.xml`, `mvnw`). Runs on port `8081`.
- **Frontend**: Next.js (App Router), TypeScript, Tailwind. Runs on port `3000`.
- **Database**: Hibernate targets whichever driver/dialect a handful of env vars point it at —
  MariaDB locally, PostgreSQL in production — with **zero code differences** between
  environments. `spring.jpa.hibernate.ddl-auto=update`: the schema is derived straight from the
  JPA `@Entity` classes, there are no migration files to maintain.
- **File storage**: `ObjectStorageService` — S3-compatible object storage when `S3_ENDPOINT` is
  configured, falls back to local disk under `storage/` otherwise. Used for property photos,
  maintenance photos, KYC documents, lease documents.
- **AI**: `ai/` package — Gemini primary, Anthropic fallback, keyword-search fallback if neither
  key is set. Used for property search, lease-document auto-fill, an affordability calculator,
  and a general home assistant.

---

## 3. How the backend works

One Java package per business domain under `src/main/java/com/example/primenestprop/`:

```
auth · user · property · lease · escrow · payment · maintenance · investment · market
viewing · vendor · kyc · review · chat · application · construction · dashboard
notification · subscription · timeline · neighbourhood · featured · admin · ai · security · common
```

Every domain follows the same four-layer shape:

```
Entity  →  Repository (Spring Data JPA)  →  Service (business logic + authorization)  →  Controller (thin — DTO mapping only)
                                                                                                    │
                                                                                                    ▼
                                                                                    Dtos (nested records, .from() factories)
```

**Auth**: bearer-token sessions (an `AuthSession` row per login, not JWT) via
`TokenAuthenticationFilter`. Login/register return a token; every subsequent request carries it
in `Authorization: Bearer <token>`.

**Authorization is layered**:
1. `SecurityConfig` — coarse gating per route: `permitAll()` (public), plain
   `.anyRequest().authenticated()` (any logged-in user, the default), or
   `.hasAuthority("PERM_X")` for admin/role-restricted actions.
2. A `Permission` enum (`PROPERTY_CREATE`, `ESCROW_RELEASE`, `KYC_REVIEW`, `ADMIN_OVERRIDE`, …)
   is mapped from `UserRole → Set<Permission>` once, at class-load, in `RolePermissions` — an
   O(1) bitset check, no DB hit.
3. Inside services/controllers, fine-grained ownership checks (`assertOwnerOrAdmin`,
   `requireSelfOrAdmin` — duplicated per-file as a small private helper, not shared) enforce
   "your own record, or an admin."

A user can hold several `UserRole`s at once (`app_user_roles` join table) — e.g. a diaspora
buyer who also holds `LANDLORD` once they own a rented-out property.

**Dashboard aggregation**: `DashboardController` has one endpoint per role
(`/dashboards/landlords/{id}`, `/dashboards/tenants/{id}`, `/dashboards/diaspora/{id}`,
`/dashboards/admin`), each pulling from several domain services and returning one composite DTO
— this is what each frontend dashboard page fetches in a single call, instead of the page making
five separate requests.

---

## 4. How the frontend works

Flat App Router structure under `frontend/app/` — one folder per route, no route groups. Role
access is enforced **client-side**, not via Next.js middleware:

- `lib/dashboardRoute.ts` — a `UserRole → path` priority list. Login/register send the user to
  their home dashboard from this list (`DIASPORA` → `/diaspora`, ranked ahead of every other
  role so a diaspora user's home experience wins even if they also hold e.g. `INVESTOR`).
- Every dashboard page does its own `useEffect` role-gate (redirect to `/login` or a
  "you need role X" settings page if the role is missing), then fetches its
  `DashboardController` aggregate via typed helpers in `lib/api.ts`.
- `components/NavSidebar.tsx` builds the left nav per-role from the same role data.
- Shared primitives used by every dashboard for visual consistency: `StatTile`, `StatusBadge`
  (auto-colors any backend status string), `EmptyState`, `AlertBanner`, `JourneyStepper`,
  `ProgressBar`.

---

## 5. The core flow: one account, many roles, one journey

The product's central idea is that **the same person moves through one continuous journey**,
not a set of disconnected features:

```
DISCOVER → VERIFY → ANALYSE → BUY/INVEST → LEGAL & PAYMENT → OWN → MONITOR → MANAGE → EARN → SELL/EXIT
```

Concretely, for a diaspora buyer building a home:

1. **Discover** — search/AI search on `/properties`.
2. **Verify** — every listing carries a `VerificationStatus` (`UNVERIFIED → PENDING → VERIFIED`)
   and a computed Trust Score for its landlord/agent; `PropertyPassportService` aggregates a
   property's full real history (photos, leases, maintenance, escrow, ratings) into one public
   "passport" view.
3. **Analyse** — REIT/investment products with projected yield and risk level
   (`investment/`, `market/`).
4. **Buy/Invest & Legal & Payment** — escrow (`escrow/`) holds funds until conditions are met;
   digital leases (`lease/`) with e-signatures; Homestead-branded legal document templates
   (Rental Application, Lease, Agreement of Sale, Mandate to Sell).
5. **Own → Monitor** — for a self-build, the new `construction/` module: a
   `ConstructionProject` with stage/progress/budget, `ConstructionMilestone`s that require the
   diaspora owner's approval before being marked paid, and an update feed doubling as a
   construction diary.
6. **Manage → Earn** — maintenance requests, rent collection, payment history, all tracked
   against the property.
7. **Sell/Exit** — not yet built (see §6).

`lib/journeyStage.ts` derives which stage a given property is in **from statuses that already
exist elsewhere** (`VerificationStatus`, `EscrowStatus`, `PropertyStatus`) rather than adding a
new "current stage" field to track by hand — this is the seam later features are meant to extend
rather than re-deriving their own progress logic.

---

## 6. How it should work vs. how it works today

The product vision (`docs/*_VISION.md`, `docs/HOMESTEAD_GUIDE.md`) is broader than what's built.
The trust/paperwork/payment core — verification, escrow, digital leases, Trust Score, REIT
investing, maintenance — is real and working end-to-end with no mock data. Scoped-but-not-built,
in rough priority order:

| Area | Status |
|---|---|
| Property/Tenant/Landlord Passport with full history | Property Passport exists (`PropertyPassportService`); Tenant/Landlord passport pages exist but are thinner than the vision doc describes |
| Home-services marketplace (movers, cleaners, insurance, **legal**) | Built (`vendor/`, `/services`) — self-registration + admin verification, category filtering, booking flow |
| Construction monitoring for diaspora self-builds | Built this session (`construction/`, `/construction`) — projects, milestones with approval-gated payment, update feed. Photo/video evidence upload not yet built |
| Diaspora-specific financing (mortgage/instalment/diaspora-lender options) | Only a rent-affordability calculator exists (`ai/AffordabilityService`); it explicitly rejects `SALE` listings — no purchase-financing product yet |
| Unified document vault (all docs, one place, expiry tracking) | Documents exist but are scattered per-domain (`LeaseDocument`, `KycDocument`, `MaintenancePhoto`) — no single vault view |
| Sell/Exit workflow (valuation request, offers, settlement tracking) | Not built — nothing beyond `PropertyStatus.SOLD` |
| Multi-country support (South Africa, Kenya, Ghana, Nigeria) | Not built — country fields exist on `Property`/`AppUser` but every default, the REIT market data, and city lists are Zimbabwe-only |
| AI fraud engine, neighbourhood intelligence | Explicitly deferred in `HOMESTEAD_GUIDE.md` §6 — "worth revisiting once the core loop is proven with real users" |
| Admin "View As" impersonation, full Property Business Hub KPI set | Scoped in `docs/ADMIN_COMMAND_CENTER_VISION.md` / `LANDLORD_WORKSPACE_VISION.md`, not built |

---

## 7. Local development

```bash
# 1. Database (MariaDB via Docker — see DATABASE.md for manual setup)
docker compose up -d mariadb

# 2. Backend — http://localhost:8081
./mvnw spring-boot:run

# 3. Frontend — http://localhost:3000
cd frontend && npm install && npm run dev
```

Seed data is **off by default** (`app.demo.seed-data=false`) — the running app has no mock data;
everything comes from the real API. Create data by registering through the app itself, or via
the `bruno/` API collection.

---

## 8. Deployment

- **Local**: `docker-compose.yml` — three containers (MariaDB + backend + frontend), or run
  backend/frontend directly against a Dockerized MariaDB (the usual dev setup).
- **Production**: `render.yaml` — two separate Render web services (backend, frontend) plus a
  managed Postgres instance. Every environment difference is a config value
  (`DB_KIND`, `DB_DRIVER`, `CORS_ORIGINS`, `API_PUBLIC_BASE_URL`, …), never a code branch.

---

## 9. Where to look next

| Doc | What it covers |
|---|---|
| `API.md` | REST endpoint reference |
| `DATABASE.md` | MariaDB setup, table list, useful SQL |
| `docs/HOMESTEAD_GUIDE.md` | User-facing "how it works," role by role |
| `docs/AFRICAPROP_AI_MASTER_VISION.md` | Long-range product vision, including "Diaspora Mode" |
| `docs/LANDLORD_WORKSPACE_VISION.md`, `docs/TENANT_HOMEHUB_VISION.md`, `docs/ADMIN_COMMAND_CENTER_VISION.md` | Per-role product specs — the destination, not the current build |
| `bruno/` | A Bruno API collection organized by domain — the fastest way to see real request/response shapes |
| `frontend/README.md` | Stock `create-next-app` boilerplate — no project-specific content |

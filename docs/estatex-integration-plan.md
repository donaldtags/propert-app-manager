# ESTATEX Integration Plan

Status: **APPROVED 2026-11-17.** Phase 1 (1a, 1b, 1c) done (branch `feature/estatex`), awaiting review before Phase 2.

### Decisions (answers to §9)
- **D1 (Q1).** The target is the Next.js web app, made into an installable, offline-capable PWA. There is no Flutter app.
- **D2 (Q2).** The product is now named **EstateX**. The platform brand becomes EstateX, with the navy/ivory/silver palette, Montserrat/Inter fonts and no gold, replacing PrimeNest green/gold. The organisation-workspace architecture stays so other agencies can be onboarded later. *ESTATEX Group* is the first seeded organisation, and for now its theme is identical to the platform theme.
- **D3 (Q3).** Add Flyway. See §4, "Schema change strategy", for how it's introduced.
- **D4 (Q4).** Compliance gates apply to **organisation listings only**.
- **D5 (Q5–Q7).** New dependencies approved: Flyway, `idb`, a service worker, a Java PDF library (OpenHTMLtoPDF) and Vitest.
- **D6 (Q9).** The pre-existing uncommitted work was committed as `e803b01` on the new branch `feature/estatex`. Nothing was committed to `main`.
- **D7 (Q10).** Sale commission defaults to **5%**. Other fees use common Zimbabwe agency practice as editable org settings: letting fee = 1 month's rent, management fee = 10% of rent collected. The ESTATEX PREA should confirm them.
- **D9 (Phase 1b questions).** Expired mandates are **auto-unpublished** just after midnight Harare. **Price, address and title type** are the material fields whose change withdraws PREA approval.
- **D10 (Phase 1c).** The offline-first scope is **listing intake and enquiry capture**. Approvals, title verification, mandate sign-off/cancel and publishing stay online-only.
- **D8 (Q8, Q11, defaults applied).** The yield calculator is staff-only for now. Logos live in `frontend/public/orgs/estatex/`.

### Baseline before any EstateX code (commit `e803b01`)
- Backend: `./mvnw test` passes 7/7.
- Frontend: `tsc --noEmit` is clean. `npm run lint` already reports **62 problems (39 errors, 23 warnings)** in existing code. The bar for each phase is that new files are lint-clean and that the total doesn't increase.
Author: Claude Code, 2026-11-17
Scope: add ESTATEX Group as the first organisation workspace on the platform (Properties, Management, Intelligence, Developments, Investments), plus a nationwide Zimbabwe location reference dataset.

---

## 0. Read this first: the brief doesn't match this repo

The brief assumes an **offline-first Flutter mobile app**. This repository is something else:

| Brief says | Repo actually is |
|---|---|
| Flutter mobile app, "analyzer" | **Next.js 16 web app** (`frontend/`, App Router, React 19, Tailwind v4, TypeScript). There is no Flutter or Dart code anywhere in the repo. |
| Offline sync layer exists | **No offline layer at all.** There is no service worker, no IndexedDB and no outbox. The bearer token in `localStorage` is the only client state (`frontend/lib/auth.tsx`). |
| "Migrations" | **No migration tool.** `spring.jpa.hibernate.ddl-auto=update` derives the schema from the entities. Production runs on Postgres (Render) and dev on MariaDB. One-off fixes are done with startup runners (`PropertyLocationBackfill`, `RoleCheckConstraintFixer`). |
| Money in integer cents | Every existing money field is `BigDecimal` plus a `currency` string (`Property.price`, `Payment.amount`, `EscrowTransaction.amount`, `Lease.monthlyRent`, `RentInvoice.amount`). |
| Homestead brand, blue `#1259A8` | The frontend is live as **PrimeNest**, with a Forest Green and gold palette (`app/globals.css`) and the Geist font. Blue `#1259A8` doesn't appear anywhere. The README says Homestead, PrimeNest and AfricaProp AI are the same product. |
| Tests in the app | Backend: one `@SpringBootTest` integration class (`ApiIntegrationTests`, 6 tests, H2). Frontend: **no test runner** (only `eslint`). |
| Per-organisation roles | Roles are **global** per user (`AppUser.roles` → `app_user_roles`). The `Organisation` concept doesn't exist. |
| Listing publication gate | **No gate.** `PropertyService.create` sets `status = AVAILABLE` immediately (line 80), and `update` accepts any `status` from the client (line 157). `verify()` only flips `verificationStatus` and doesn't write an audit entry. |

So in this plan:
- "Mobile app" means the **Next.js web app, made installable and offline-capable as a PWA**. This is open question Q1.
- "Analyzer clean" means `npm run lint` + `npx tsc --noEmit` (frontend) and `./mvnw -q verify` (backend).
- "Migrations" means either introducing Flyway (Q3) or the existing pattern: nullable columns, defaults and an idempotent startup backfill runner.

**Working tree warning:** `main` has about 39 modified and several untracked files that predate this work (payments gateway, geocoding, location picker and others). I won't stage or commit any of them. Each phase commit stages only the files that phase touched. You may want to commit or stash your in-progress work first (Q9).

---

## 1. What already exists, and how we'll reuse it

| Existing (package / file) | What it gives us | ESTATEX reuse |
|---|---|---|
| `property/Property` + `PropertyPhoto`, `PropertyMedia`, `GeocodingService` | Listing core: type, status, suburb/city, beds/baths, price, lat/lng, photos (S3 or local disk) | **Is** the listing. ESTATEX adds org ownership, title type, compliance fields and the mandate link. No parallel listing table. |
| `property/VerificationStatus`, `PropertyService.verify` | Verification flag | Becomes one input to the publication gate (title verification). |
| `property/PropertyInquiry` | Anonymous enquiry (name/email/phone/message) | Kept for the public site. ESTATEX enquiries are a new CRM entity that can be **auto-created from** a `PropertyInquiry` on an org listing. |
| `viewing/ViewingRequest` (modes, check-in QR, feedback) | Viewing scheduling + feedback | Reused directly for CRM viewings. Add nullable `organisationId`, `contactId` and `negotiatorId`. Feedback feeds the weekly seller report. |
| `escrow/EscrowTransaction` + release approvals | Held client money | Reused for deposits paid through the platform. Tagged to the org trust account. **Touches escrow, see §7.** |
| `payment/Payment`, `RentInvoice`, `RentInvoiceService`, gateways (Paynow, Stripe) | Payments + monthly rent invoices | Rent ledger charges are built on `RentInvoice`. Receipts link to `Payment` when paid online, or are manual (cash/bank) entries. **Touches payments, see §7.** |
| `lease/Lease`, `LeaseDocument`, e-signature, `LeaseExtractionService` | Digital leases | Reused unchanged for Management. Add nullable `unitId` and `organisationId`. **Touches leases, see §7.** |
| `maintenance/MaintenanceRequest`, `MaintenancePhoto`, `MaintenanceTriageService` | Tenant request → assign vendor → resolve | Extended with quote → owner approval → completion photos → cost-to-owner. Existing statuses are kept and new ones are appended. |
| `vendor/Vendor`, `VendorCategory` | Service-provider directory | Org vendor directory = vendors filtered and flagged per org. Add `GARDENING` to `VendorCategory`. |
| `common/AuditLog` | Audit table (actor, action, entity, json) | Only `KycService` uses it today. We'll add a shared `AuditService` and `organisationId`, and call it from every approval, status change and money movement. |
| `user/Permission`, `RolePermissions`, `AppUser.hasPermission` | O(1) role → permission bundles | Pattern copied for org roles (`OrgRole → Set<OrgPermission>`). |
| `construction/` milestones with owner approval | Approval-gated payments | Pattern reference for the JV/Developments pipeline and maintenance quote approvals. |
| `investment/`, `market/`, `ai/RentPricingService` | REIT products, rent pricing heuristics | Investor CRM is new (people, not REIT units). `RentPricingService` is reviewed as an input to pricing recommendations. |
| `neighbourhood/NeighbourhoodProfile` (city+suburb, admin-curated) | Suburb notes | Linked to the new `Location` reference table. |
| `frontend/lib/zimbabweLocations.ts` | 28 cities, partial suburb lists, hard-coded in the frontend | **Replaced** by the backend `locations` reference dataset (§4). |
| `notification/`, `auth/EmailService` | In-app + email notifications | Reminders (arrears, viewings, campaign tasks) and statement emails. |
| Frontend: `StatTile`, `StatusBadge`, `EmptyState`, `AlertBanner`, `JourneyStepper`, `ProgressBar`, `NavSidebar`, `lib/api.ts` typed helpers | Dashboard primitives + API client | All new screens use them. They'll be refactored to semantic theme tokens (§3.2). |

Conventions we'll follow: one backend package per domain; Entity → Repository → Service (logic + authorization) → thin Controller → nested-record `Dtos` with `.from()`; `ApiException` for errors; flat App Router folders; per-page `useEffect` role gate; typed helpers in `lib/api.ts` and types in `lib/types.ts`. No new state-management library (React state + context only, as today).

---

## 2. Assumptions (correct me where wrong)

- A1. The deliverable is the **existing Next.js web app**, made into an installable PWA for offline use. No Flutter app gets built.
- A2. **ESTATEX is a workspace inside the same app and backend**, not a fork or separate deployment. One user can belong to several organisations and switch between them.
- A3. The platform brand stays as currently shipped (PrimeNest/Homestead tokens). The "blue #1259A8" platform rebrand is **out of scope** unless you confirm it (Q2).
- A4. Compliance gates apply to **organisation listings** (listings owned by an organisation workspace). Independent landlord or private-seller listings keep today's behaviour so existing flows don't break. A platform-wide gate is a separate decision (Q4).
- A5. New ESTATEX money fields use `long ...Cents` + `currency` (`USD` default, `ZWG` for ZiG) + optional `zigCents` secondary. Existing `BigDecimal` fields are **not** converted. Conversion happens at the service boundary where new code reads old entities.
- A6. ZiG ISO code is `ZWG`. No FX feed exists, so ZiG amounts are entered, never auto-converted.
- A7. Timestamps are stored as UTC `Instant` (existing convention). Business dates ("rent due", "week starting", "21-day day N") are computed in `Africa/Harare` through a single injected `Clock`/`ZoneId` bean.
- A8. External roles (Owner/Landlord, Tenant, Buyer, Investor, Landowner) are mainly **CRM contacts**. A contact only becomes an `AppUser` when invited to a portal (owner portal, tenant portal). Org staff roles are per-organisation memberships.
- A9. The Propertyzone Q2 2026 table is seeded as **asking/indicative** market data and is never mixed into achieved data.
- A10. PDFs are generated server-side (one new Java dependency, Q6). Instagram square images are rendered client-side on a `<canvas>` (no dependency).
- A11. Org logo assets live in `frontend/public/orgs/estatex/`, because Next.js serves static files from `public/`.

---

## 3. Cross-cutting design

### 3.1 Organisations, membership and per-org roles (backend: new `organisation/` package)

```
Organisation
  id, slug ("estatex"), name, slogan, logoLightKey, logoDarkKey,
  themeKey ("estatex"), enabledDivisions (Set<Division>),
  contactPhone, whatsappNumber ("263784542297"), instagramHandle, email, address,
  eacPreaUserId, eacRegistrationNumber, trustAccountReference, trustAccountBank,
  defaultCurrency="USD", timezone="Africa/Harare", createdAt, version

Division enum: PROPERTIES, MANAGEMENT, INTELLIGENCE, DEVELOPMENTS, INVESTMENTS

OrganisationMembership
  id, organisationId, userId, roles (Set<OrgRole>), divisions (Set<Division>), active, invitedAt, joinedAt
  unique (organisationId, userId)

OrgRole enum: ORG_ADMIN, PREA, NEGOTIATOR, PROPERTY_MANAGER, ANALYST,
              OWNER, TENANT, BUYER, INVESTOR, LANDOWNER   // external portal roles
OrgPermission enum: LISTING_CREATE, LISTING_APPROVE_PUBLISH, MANDATE_SIGN_OFF, OFFER_RECORD,
                    TRUST_LEDGER_WRITE, COMMISSION_CONFIRM, MAINTENANCE_MANAGE, MARKET_DATA_WRITE,
                    TARGETS_EDIT, ORG_SETTINGS_EDIT, OWNER_PORTAL_VIEW, ...
OrgRolePermissions: static EnumMap, same shape as RolePermissions.
```

- `LISTING_APPROVE_PUBLISH` and `MANDATE_SIGN_OFF` belong **only** to `PREA`. `ORG_ADMIN` doesn't get them. Platform `ADMIN` with `ADMIN_OVERRIDE` also doesn't get them, because EAC accountability has to sit with the registered agent. Test-enforced.
- `OrgAccessService.require(user, orgId, OrgPermission)` is the single check used by every org service.
- API: org-scoped routes under `/api/v1/orgs/{orgSlug}/...`. `SecurityConfig` keeps them at `.authenticated()`, and the service layer does the org checks (same layering as today).
- `Property` gets nullable `organisationId`, `division`, `negotiatorId`. When null, it's an independent listing and behaves exactly as today.

### 3.2 Theming (frontend)

Today every component hard-codes `forest-*` / `gold-*` Tailwind classes, and there is no dark mode.

1. Introduce **semantic tokens** in `globals.css`: `--color-brand`, `--color-brand-contrast`, `--color-surface`, `--color-surface-raised`, `--color-text`, `--color-text-muted`, `--color-accent`, `--color-border`, plus `--font-heading` / `--font-body`. Expose them to Tailwind v4 via `@theme inline` (`bg-brand`, `text-muted`, `font-heading`, …).
2. Theme sets live under `[data-theme="platform"]`, `[data-theme="estatex"]`, each with a light set and a `[data-mode="dark"]` set (plus `prefers-color-scheme` default).
3. ESTATEX tokens: light theme is brand `#0B1D32`, surface `#F5F2EA`, muted text `#788391`, accent `#C9CED4`. Dark theme is surface `#0B1D32`, raised surface a lighter navy (≈`#13294A`), text `#F5F2EA`, accent `#C9CED4`. **No gold.** Contrast is checked against WCAG AA in a unit test.
4. Fonts via `next/font/google`: Montserrat (headings) and Inter (body), loaded once and applied through CSS variables only inside the ESTATEX workspace.
5. `OrgWorkspaceProvider` (React context) sets `data-theme` on the workspace layout root based on the active org. Platform pages are unaffected.
6. Shared primitives (`StatTile`, `StatusBadge`, `EmptyState`, `NavSidebar`, …) are migrated to semantic tokens in Phase 1, with platform token values equal to today's colours, so platform pages show **no visual change**.
7. Logo: `frontend/public/orgs/estatex/` gets a placeholder SVG wordmark (silver-on-dark, navy-on-light) until you drop in the real assets. Next.js serves static assets from `public/`, so the brief's `assets/orgs/estatex/` maps to `frontend/public/orgs/estatex/` (A11).

### 3.3 Compliance gates (backend-enforced)

New fields on `Property` (nullable, so existing rows are unaffected):
`titleType` (`TITLE_DEED | SECTIONAL_TITLE | CESSION`), `titleVerificationStatus`, `ownerConsentPhotos`, `ownerConsentAddress`, `consentRecordedAt`, `consentRecordedBy`, `preaApprovedBy`, `preaApprovedAt`, `activeMandateId`.

New `PublicationGuard.assertPublishable(property)` throws `ApiException(409, reasons[])` listing **every** unmet condition:
1. `titleVerificationStatus == VERIFIED`, title type recorded, and the verification checklist complete
2. an attached `Mandate` with `status = SIGNED`, `preaSignedOffAt != null`, `startDate <= today(Harare) <= expiryDate`
3. PREA approval present, and given **after** the latest material edit (price, address or title change resets approval)
4. owner consent to photos **and** address

Enforcement points:
- `PropertyService.create`: org listings start as `DRAFT`, never `AVAILABLE`.
- `PropertyService.update`: for org listings, the client can no longer set `status` to `AVAILABLE`. Publication goes only through `POST /orgs/{org}/listings/{id}/publish`, which runs the guard.
- Public search excludes org listings that aren't `AVAILABLE`.
- A nightly job (Harare midnight) unpublishes org listings whose mandate has expired, notifies the negotiator and PREA, and writes an audit entry.
- Cession listings carry `riskFlags: ["CESSION"]` in DTOs, and the UI shows a red "Cession" badge on cards, detail pages and PDFs.

### 3.4 Client money and commission

- `TrustLedgerEntry` (new, append-only): `organisationId, entryType (DEPOSIT_RECEIVED, RENT_RECEIVED, DISBURSEMENT_TO_OWNER, REFUND, TRANSFER_TO_OPERATING_FEE…), amountCents, currency, contactId, propertyId, leaseId?, escrowTransactionId?, paymentId?, reference, recordedBy, recordedAt, clientId (UUID)`. Balances are always computed from entries and never stored.
- Operating income (management fees, commission) is recorded **only** as a `TRANSFER_TO_OPERATING_FEE` that debits trust against a specific owner's statement. That makes mixing structurally impossible: no API writes client money anywhere except the trust ledger.
- `Commission`: `dealId, negotiatorId, amountCents, status (EXPECTED → EARNED → RECEIVED)`. `EARNED` can only be set by the transition triggered by `ConveyancerConfirmation` (`transferRegisteredAt`, `conveyancerName`, `deedsReference`, document upload) with `COMMISSION_CONFIRM` permission. Test-enforced.

### 3.5 Audit log

Extend `AuditLog` with `organisationId`, `fromState`, `toState`, `amountCents`, `currency`, `ip`. `AuditService.record(...)` is called from: PREA approvals, mandate sign-off, publish/unpublish, every pipeline stage change, offer events, trust ledger entries, escrow and payment status changes on org records, commission transitions, role/membership changes. Read-only UI: `/orgs/{slug}/audit` (ORG_ADMIN, PREA).

### 3.6 Offline-first (PWA): the biggest new piece of infrastructure

Nothing exists today, so this is built in Phase 1 and every later phase plugs into it.

- **App shell**: a service worker (hand-written, or Serwist; Q5) precaches the app shell and caches GET API responses for org data (stale-while-revalidate). `manifest.webmanifest` makes it installable on Android and iOS.
- **Local store**: IndexedDB via the tiny `idb` wrapper (≈1 KB, Q5), holding `entities/<type>` caches, `outbox`, `uploads` and `locations`.
- **Outbox**: every offline-capable write is `{clientId (UUIDv7), entityType, op, payload, baseVersion, createdAt}`. It replays FIFO per entity when online, with exponential backoff. The server endpoints are **idempotent on `clientId`**: a unique `client_id` column on every new entity means a retried create never duplicates.
- **Conflict-resolution rule** (applies to every new entity):
  1. **Append-only records** (notes, enquiries, viewings feedback, inspection photos, ledger entries, audit) never conflict. They're de-duplicated by `clientId`.
  2. **Editable records** carry JPA `@Version`. An update is sent with `baseVersion`. If the server version is the same, the update applies. If it differs, it's merged **field by field**: fields the client changed that the server didn't change apply; fields **both** changed resolve to **server wins**, and the client's value is kept in a "Sync conflicts" tray for the user to re-apply.
  3. **State transitions, approvals and money** (publish, PREA approve, mandate sign-off, stage moves, offers, receipts, commission) are **server-authoritative commands, never last-write-wins**. Offline they're queued as *intent*. On replay the server validates against current state. If invalid (for example, the mandate expired or someone else already moved the stage), the command is rejected and shown in the conflicts tray. Offline-recorded receipts show as "pending sync" and aren't counted in balances until the server accepts them.
  - Each rule gets a unit test (frontend outbox merge) and an integration test (backend version/idempotency).
- **Background media upload**: images are compressed client-side (canvas → WebP/JPEG, max 2048 px, ~0.8 quality) before queueing. Video isn't transcoded in the browser (too heavy). It's uploaded as-is, in chunks (5 MB, resumable) to a new `/uploads` chunk endpoint backed by `ObjectStorageService` (S3 multipart). Retries use backoff. Background Sync API is used where supported (Chrome/Android). **iOS Safari doesn't support Background Sync**, so there uploads resume whenever the app is open (risk R3).
- **Auth offline**: the cached token allows read and queue. Writes replay only after a successful token check, and a 401 pauses the outbox and prompts re-login without dropping it.

### 3.7 Location reference data: all Zimbabwe cities, towns and suburbs (your addendum)

Today: 28 cities and partial suburbs, hard-coded in `frontend/lib/zimbabweLocations.ts`. Proposal:

- Backend `location/` package. `Location { id, type (PROVINCE | CITY | TOWN | GROWTH_POINT | SUBURB), name, parentId, province, aliases[], densityTier (HIGH | MEDIUM | LOW/NORTHERN | null), lat, lng, source, verified }`, unique `(parentId, name)`.
- A **market area** concept (`MarketArea`, many-to-many with suburbs) handles combined rows like "Vainona / Pomona" and "Warren Park 1" without polluting the administrative list.
- Seeded idempotently on startup from `src/main/resources/reference/zimbabwe-locations.json`, the same pattern as `ReitCatalogSeeder`.
- Coverage target: all **10 provinces**, every **city, municipality, town and town board** (≈ 60–70 urban centres plus main growth points), and suburbs for every urban centre. Harare, Bulawayo, Chitungwiza, Mutare, Gweru, Kwekwe, Kadoma, Masvingo, Marondera, Chinhoyi, Bindura, Norton, Ruwa, Epworth and Victoria Falls get full suburb/township lists. Smaller towns get their main suburbs and high-density townships.
- **Honesty caveat:** there's no single authoritative open dataset of every Zimbabwean suburb. I'll compile it from general knowledge and mark every row `source = "compiled; unverified"`. Please spot-check it with the ESTATEX team and against council/ZIMSTAT ward lists. Admins can add or rename suburbs in-app, and a user typing an unknown suburb creates a `verified=false` entry for admin review instead of failing.
- `GET /api/v1/locations?parent=…` (public, cacheable, cached in IndexedDB for offline). The frontend picker replaces `zimbabweLocations.ts` (kept as fallback until removed). Existing `Property.city/suburb` strings stay as they are. A backfill maps them to `locationId` where they match.
- Appendix A lists the provinces and cities/towns I plan to seed, for your review.

### 3.8 Money and dates

- New entities use `amountCents` (long) + `currency` (`USD` | `ZWG`), and optionally `secondaryAmountCents` + `secondaryCurrency` for a ZiG value.
- Formatting happens in a shared `lib/money.ts` (frontend) and `common/Money` (backend record), using `Intl.NumberFormat("en-ZW")`.
- `spring.jackson.time-zone` stays UTC on the wire. A `ZoneId HARARE` bean plus an injected `Clock` drive all business-date logic, which keeps it testable.

---

## 4. Data model additions (summary)

All new tables have `id`, `organisation_id`, `client_id` (unique, nullable for server-created rows), `version` (`@Version`), `created_at`, `updated_at`, `created_by`.

| Phase | New entities | Changes to existing entities (all nullable, additive) |
|---|---|---|
| 1 | `Organisation`, `OrganisationMembership`, `Location`, `MarketArea`, `TrustLedgerEntry` (table only), `UploadSession` | `Property` (+org, division, negotiator, titleType, titleVerificationStatus, consent fields, PREA approval fields, activeMandateId, locationId, version); `AuditLog` (+org, from/to, amount); `VendorCategory` (+GARDENING) |
| 2 | `Contact` (type set, source, tags, linked userId?), `ContactNote`, `Mandate` (type sole/open, authority: min price, offer authority, commission %, start/expiry, signed doc, PREA sign-off), `PipelineCard` (stage, contact, property, negotiator, lostReason), `InspectionChecklist` + `InspectionItem` + media, `VerificationChecklist`, `PricingRecommendation` + `Comparable`, `MarketingCampaign` (startDate, 21 `CampaignTask`s, `ChannelEnquiryCount`), `Enquiry` (channel, contactId, propertyId), `BuyerQualification`, `Offer` + `OfferEvent`, `SellerReport` (generated PDF key), `Deal`, `ConveyancerConfirmation`, `Commission` | `ViewingRequest` (+org, contactId, negotiatorId); `PropertyInquiry` → auto-creates `Enquiry` for org listings |
| 3 | `Unit`, `ManagementAgreement` (owner, fee %, start), `LedgerCharge`, `LedgerReceipt` (links `RentInvoice`/`Payment`/`TrustLedgerEntry`), `ArrearsReminder`, `MaintenanceQuote`, `InspectionReport` + media, `OwnerStatement` (monthly/annual, PDF key) | `Lease` (+unitId, orgId); `MaintenanceRequest` (+orgId, quote/approval/completion fields, new statuses `TRIAGED`, `QUOTED`, `OWNER_APPROVED`, `OWNER_DECLINED` appended); `Vendor` (+orgId for org-private directory) |
| 4 | `MarketObservation` (ASKING; suburb/locationId, type, beds, standSizeM2, titleType, price/rent cents, date, source, sourceListingId?), `AchievedTransaction` (separate table, **not** a flag, plus `clientConsent`, dealId/leaseId), `MarketReferenceRange` (the Propertyzone seed table), `PulseReport` | none |
| 5 | `LandownerProject` (stage pipeline), `FeasibilitySnapshot` (inputs/outputs JSON + PDF key), `InvestorProfile` (goal, budget cents, preferred suburbs), `BriefingEvent`, `EventRsvp`, `InvestorMatch` (computed, cached) | none |
| 6 | `ScorecardTarget` (org, negotiator?, metric, weekly target), `ScorecardSnapshot` (weekly materialised metrics) | none |

Achieved vs asking separation: two tables, two services, and two distinct API paths (`/market/asking`, `/market/achieved`). Achieved data requires `ANALYST`/`PREA`/`ORG_ADMIN`, and only rows with `clientConsent = true` feed aggregate reports. The Pulse PDF labels every figure "asking" or "achieved".

### Schema change strategy (Q3)
- **Option A (recommended): introduce Flyway** with a baseline of the current schema, then versioned SQL for every change. That's safer on Render Postgres, and gives proper unique and check constraints. It's a new dependency, so I'm asking first.
- **Option B: stay on `ddl-auto=update`**. Only nullable columns and new tables, and every non-null default is applied by an idempotent `*Backfill` runner. That's acceptable but weaker (no dropped or renamed columns, no data migrations, enum check-constraint drift; see the existing `RoleCheckConstraintFixer`).

---

## 5. New screens (frontend routes)

All under `app/orgs/[slug]/…` with a shared `layout.tsx` that loads the org, sets the theme, and renders an org-aware `NavSidebar`. Mobile-first (tested at 360×640), with bottom nav on small screens.

- **Phase 1**: `/orgs` (workspace switcher), `/orgs/[slug]` (division home), `/orgs/[slug]/settings` (org profile, EAC fields, divisions, logo, members & roles), `/orgs/[slug]/audit`, `/orgs/[slug]/sync` (outbox + conflicts tray), a theme/mode toggle, `/admin/locations` (location curation).
- **Phase 2**: `contacts` (list, detail, quick-add), `listings/new` (intake wizard: details → title & verification → inspection checklist + photos → consents → pricing & comparables), `listings/[id]` (compliance checklist panel showing exactly which gates are unmet, publish button), `pipeline` (Kanban + list toggle; drag on desktop, stage menu on mobile), `listings/[id]/campaign` (21-day tracker), `enquiries` (one-tap capture FAB on every org page), `viewings`, `offers`, `listings/[id]/reports` (weekly seller report, share via `wa.me` / `mailto`), listing share sheet (WhatsApp deep link + Instagram square PNG).
- **Phase 3**: `management/portfolio` (landlords → properties → units → tenants), `management/ledger` (charges, receipts, arrears ageing 0-30/31-60/61-90/90+), `management/maintenance` (Kanban), `management/vendors`, `management/inspections`, `management/statements`; **owner portal** `/owner/...` (properties, tenants, statements, inspection media, approve/decline quotes), designed for diaspora owners with no jargon and USD-first.
- **Phase 4**: `intelligence/capture`, `intelligence/achieved`, `intelligence/suburbs` (dashboard with filters + trend chart), `intelligence/yield` (net yield calculator, also usable logged-out as a lead magnet? Q8), `intelligence/pulse`.
- **Phase 5**: `developments` (landowner pipeline Kanban), `developments/[id]/feasibility`, `investors`, `investors/events`, `investors/matches`.
- **Phase 6**: `dashboard` (org scorecard, per-negotiator scorecard, WoW trend, admin targets editor).

---

## 6. Phases (with definition of done)

Phase 1 is much larger than the others, so I propose splitting it into three commits, each with a review pause:

- **1a: Organisations, roles and theming.** Organisation + membership + OrgRole permissions; ESTATEX seed (5 divisions, contact details, placeholder logo); semantic theme tokens + ESTATEX light/dark; Montserrat/Inter; workspace layout + switcher; shared `AuditService`; Harare clock; `Location` dataset + API + picker.
- **1b: Compliance gates.** Property compliance fields, `Mandate` (minimal, enough to gate; full pipeline in Phase 2), `PublicationGuard`, publish endpoint, PREA-only approval, expiry job, Cession badge, `TrustLedgerEntry` + `Commission` skeleton with the conveyancer-confirmation rule.
- **1c: Offline foundation.** PWA manifest + service worker, IndexedDB store, outbox with the conflict rules, idempotent `clientId` handling server-side, chunked media upload with compression and retry, sync/conflicts screen.

Phases 2–6 as in the brief, with the screens and entities above.

**Definition of done per phase:**
- Backend: `./mvnw -q verify` green; new integration tests in feature-scoped test classes (for example `OrganisationComplianceTests`) beside `ApiIntegrationTests`.
- Frontend: `npm run lint` and `npx tsc --noEmit` clean; `next build` succeeds. Unit tests for outbox/conflict logic, yield and feasibility calculators, and theme contrast need a test runner, so I propose **Vitest** as a dev dependency (Q7).
- Required tests from the brief: an unverified listing can't be published (409); an unmandated or expired-mandate listing can't be published; a listing without owner consent can't be published; a non-PREA (including ORG_ADMIN and platform ADMIN) can't approve or sign off; commission can't become EARNED without conveyancer confirmation; trust-ledger entries can't be written to operating income; idempotent replay; version-conflict field merge; server-authoritative command rejection.
- No regressions: the existing `ApiIntegrationTests` pass unchanged. An independent (non-org) landlord can still create a property that's immediately `AVAILABLE`.
- Each new screen is checked in light and dark mode at 360 px wide.
- This file is updated with what was built, deviations and open questions, then committed with only that phase's files staged.

---

## 7. Changes to escrow, payments and leases (flagged as required)

| Area | Change | Risk to existing flows | Mitigation |
|---|---|---|---|
| Escrow | Nullable `organisationId` + `trustAccountReference` on `EscrowTransaction`. When an org-listing deposit escrow is funded or released, a mirror `TrustLedgerEntry` is written through a listener. | Low: additive columns. Existing release logic is untouched. | The listener is a no-op when `organisationId` is null; existing escrow tests are rerun. |
| Payments | `PaymentPurpose` gains `TRUST_DEPOSIT` and `TRUST_RENT`. Rent paid online for an org-managed lease is tagged to the trust ledger via the existing `PaymentSucceededEvent`. | Medium: a new enum value in a `@Enumerated(STRING)` column can hit DB check-constraint drift (as already happened with roles). | Extend the existing constraint-fixer pattern or use Flyway (Q3). Non-org payments are unaffected. |
| Leases | Nullable `unitId`, `organisationId` on `Lease`. Org-managed leases are created by a Property Manager acting for the owner. The existing agent-acts-for-landlord path is reused. | Low | No change to signing or status logic. |
| Rent invoices | `RentInvoiceService` is reused for charges. Arrears ageing is a read model over invoices. | Low | Read-only addition. |
| Property create/update | **Behaviour change for org listings only**: start as `DRAFT`, and `status` can't be set to `AVAILABLE` via `update`. | Medium: affects any client that relied on setting `status` directly. | Scoped to `organisationId != null`, and covered by tests in both directions. |

---

## 8. Risks

- **R1. Scope.** This is effectively a full agency ERP. Realistically every phase is multiple sessions of work, and Phase 1c (offline) and Phase 3 (management + owner portal) are the largest.
- **R2. Offline on the web is weaker than native**: browser storage can be evicted (mitigated with `navigator.storage.persist()`), and iOS limits PWAs.
- **R3. Background upload on iOS**: there's no Background Sync, so uploads only progress while the app is open. Large inspection videos on a mobile connection will be slow, so video quality is capped (720p capture hint).
- **R4. Location data accuracy**: the compiled suburb lists will have gaps and spelling variants (Mabelreign/Malbereign is already duplicated in the current file). Mitigated with aliases plus admin curation.
- **R5. Uncommitted work on `main`**: mixing it with these commits would be messy (Q9).
- **R6. Schema drift without migrations** if Option B is chosen.
- **R7. Brand ambiguity** (PrimeNest vs Homestead, green vs blue) affects the "platform" theme tokens (Q2).
- **R8. Legal/regulatory**: EAC trust-account rules and the cession risk wording should be reviewed by ESTATEX's PREA. I'm encoding the rules from the brief, not from the Estate Agents Act itself.

---

## 9. Open questions (need answers before Phase 1a)

- **Q1. Platform.** Is the Next.js web app (as an installable PWA) the target, or is there a separate Flutter repo I should be working in instead?
- **Q2. Platform brand.** Should the platform theme stay as today's PrimeNest green, or switch to Homestead blue `#1259A8` as part of Phase 1?
- **Q3. Schema changes.** Introduce Flyway (recommended) or stay on `ddl-auto=update` + backfill runners?
- **Q4. Compliance gate scope.** Org listings only (recommended, A4), or every listing on the platform?
- **Q5. Offline deps.** OK to add `idb` (IndexedDB wrapper) and either Serwist or a hand-written service worker?
- **Q6. PDF generation.** OK to add a Java PDF library (OpenHTMLtoPDF, HTML/CSS templates in org branding)?
- **Q7. Frontend tests.** OK to add Vitest as a dev dependency?
- **Q8. Public yield calculator.** Should it be public on the ESTATEX site as a lead magnet, or staff-only?
- **Q9. Existing uncommitted changes.** Commit or stash them before I start, or should I leave them and stage only my files?
- **Q10. Commission & fees.** Default ESTATEX commission % (sale), letting fee and management fee %? I'll make them editable org settings, but need sensible defaults.
- **Q11. Logo path.** OK to use `frontend/public/orgs/estatex/` instead of `assets/orgs/estatex/`?

---

## Appendix A: Location seed outline (for review)

Every province, city and town listed below gets suburbs/townships in the seed JSON. Coverage is fullest for the larger centres, as described in §3.7.

**Provinces (10):** Harare (metropolitan), Bulawayo (metropolitan), Manicaland, Mashonaland Central, Mashonaland East, Mashonaland West, Masvingo, Matabeleland North, Matabeleland South, Midlands.

**Cities and towns by province** (cities in bold; draft, please correct):
- **Harare Metropolitan:** **Harare**, **Chitungwiza**, Epworth, Ruwa
- **Bulawayo Metropolitan:** **Bulawayo**
- **Manicaland:** **Mutare**, Rusape, Chipinge, Nyanga, Chimanimani, Penhalonga, Odzi, Headlands, Birchenough Bridge, Buhera, Murambinda, Checheche, Hauna
- **Mashonaland Central:** Bindura, Mvurwi, Shamva, Glendale, Concession, Mount Darwin, Guruve, Centenary, Mazowe, Rushinga, Muzarabani
- **Mashonaland East:** Marondera, Murehwa, Mutoko, Macheke, Hwedza, Chivhu, Goromonzi, Juru, Mutawatawa, Kotwa, Nyamapanda, Beatrice, Seke/Dema
- **Mashonaland West:** **Chinhoyi**, **Kadoma**, Norton, Chegutu, Karoi, Kariba, Banket, Mhangura, Raffingora, Chirundu, Magunje, Sanyati, Chakari, Murombedzi, Selous
- **Masvingo:** **Masvingo**, Chiredzi, Triangle, Mashava, Gutu/Mupandawana, Zaka/Jerera, Mwenezi/Rutenga, Ngundu, Nyika, Bikita
- **Matabeleland North:** **Victoria Falls**, Hwange, Lupane, Binga, Dete, Kamativi, Nkayi, Tsholotsho, Inyathi
- **Matabeleland South:** Gwanda, Beitbridge, Plumtree, Esigodini, Filabusi, West Nicholson, Kezi, Maphisa, Colleen Bawn
- **Midlands:** **Gweru**, **Kwekwe**, Redcliff, Zvishavane, Shurugwi, Gokwe, Mvuma, Lalapanzi, Mberengwa, Shangani, Silobela

---

## Progress log

### Phase 1a: Organisations, roles, theming, locations (DONE 2026-11-17)

**Built: backend**
- Flyway (`spring-boot-starter-flyway` + Postgres/MySQL modules). Migrations live in `db/migration/common` (portable SQL) and `db/migration/{postgresql,mariadb,h2}`. `V1__flyway_baseline_marker.sql`.
- `organisation/`: `Organisation` (brand theme tokens, contact, EAC fields, fee defaults in basis points), `OrganisationMembership` (per-org `OrgRole` set + divisions), `OrgPermission`, `OrgRolePermissions`, `OrgAccessService` (the single org authorization check), `OrganisationService`/`Controller`, `EstatexOrganisationSeeder` (ESTATEX Group, 5 divisions, brand, +263 78 454 2297, @estatexgroup, 5% / 10% / 1 month fees).
- API: `GET /orgs/mine`, `POST /orgs` (platform admin), `GET /orgs/{slug}` (public profile; internal fields for staff only), `PATCH /orgs/{slug}` (optimistic `version` check), `GET /orgs/{slug}/me`, `GET|POST /orgs/{slug}/members`, `PATCH /orgs/{slug}/members/{id}`, `GET /orgs/{slug}/audit`.
- Rules: only PREA holds `LISTING_APPROVE_PUBLISH` / `MANDATE_SIGN_OFF`. ORG_ADMIN doesn't, and platform ADMIN_OVERRIDE grants neither them nor the client portals. One active PREA per org, mirrored into `Organisation.preaUserId/preaName`. The last ORG_ADMIN can't be removed. Divisions must be enabled on the org.
- `common/AuditService` (joins the caller's transaction). `AuditLog` gains `organisationId`, `fromState`, `toState`, `amountCents`, `currency`. Membership, PREA and settings changes are audited.
- `common/BusinessTime`: injected `Clock` + `Africa/Harare` zone for all business dates (`app.timezone`).
- `location/`: `Location` hierarchy (province → city/town/growth point → suburb, density tier, aliases), `LocationSeeder` from `reference/zimbabwe-locations.json`, `GET /locations` (public, cacheable), `POST /locations/suggestions`, `GET /locations/pending` + `PATCH|DELETE /locations/{id}` (new platform permission `LOCATION_MANAGE`).
- Seed data: **10 provinces, 97 cities/towns/growth points, 436 suburbs** (543 rows). Every suburb in the Propertyzone Q2 2026 table resolves. All rows are marked `source = "EstateX compiled list 2026-11 (unverified ...)"`.

**Built: frontend**
- Rebrand PrimeNest → **EstateX** across the UI: navy/ivory/silver palette (`forest-*` → `brand-*`, `gold-*` → `silver-*` codemod over 58 files), Montserrat headings + Inter body via `next/font`, page title and slogan, placeholder logo (`components/EstateXLogo.tsx`, `public/orgs/estatex/logo-on-{light,dark}.svg`).
- Semantic workspace tokens (`bg-primary`, `text-on-primary`, `bg-surface`, `bg-raised`, `text-ink`, `text-muted`, `border-line`, `text-accent`, ...) driven by each organisation's theme tokens (`lib/orgTheme.ts` → CSS variables), with light / dark / system mode.
- Screens: `/orgs` (my workspaces), `/orgs/[slug]` (home: divisions, contact with WhatsApp deep link, EAC panel, missing-PREA warning), `/orgs/[slug]/settings` (profile, compliance, fees, brand with live light/dark preview + WCAG contrast warnings, members & roles), `/orgs/[slug]/audit`, `/admin/locations` (suburb suggestion review). "Workspaces" added to the sidebar.
- `lib/locations.ts`: location store backed by `/locations`, cached in localStorage, falling back to the old static lists. Property search, create and edit now use it (97 places instead of 28).
- Vitest (`npm test`) with tests for theme contrast/EstateX tokens and the location index/resolver.

**Verification**
- Backend `./mvnw test`: **17/17** (7 existing unchanged + 10 new: `OrgRolePermissionsTest`, `OrganisationWorkspaceTests`, `LocationApiTests`).
- Frontend: `tsc` clean; `npm test` 12/12; `next build` OK; `npm run lint` = 62 problems, **same as baseline, 0 in new files**.
- Manual smoke run (isolated H2 backend on :8082 + Next on :3001, headless Chrome at 375 px): workspace home/settings/members/audit in light and dark mode, no horizontal overflow, no console errors. The PREA has approve permission, the negotiator gets 403 on settings, the public profile hides the trust account, and the property form lists 97 places from the API.

**Deviations and decisions made during the build**
1. **Flyway runs *after* Hibernate** (`common/FlywayAfterHibernateConfig`). The ~40 legacy tables are still created by `ddl-auto=update`, so a pre-JPA Flyway run can't reference them on a fresh DB. Flyway owns versioned data fixes, constraint repairs and indexes. **Follow-up:** take a baseline from production Postgres, then switch to `ddl-auto=validate` with Flyway owning all DDL.
2. **Enum columns in new tables are plain strings with typed accessors** (see `Location.getType()`). Hibernate 7.4 generates a CHECK constraint for an enum even behind an `AttributeConverter`, and `ddl-auto=update` never widens it, which is the same failure `RoleCheckConstraintFixer` patches for roles. Enum *sets* use `EnumSetConverter` (no CHECK). Guarded by `newEstatexTablesHaveNoEnumCheckConstraints`. Every later phase follows this convention.
3. **Muted text uses `#5E6873`, not brand Slate `#788391`**, on ivory. #788391 is 3.6:1 on #F5F2EA and fails WCAG AA for body text. Slate stays for decorative/large text. A Vitest test documents this.
4. **Dark mode covers the new workspace screens only.** Existing platform pages got the EstateX palette but stay light-only, because they hard-code `bg-white`/`text-gray-*` everywhere and converting them is a separate project.
5. `MarketArea` (for combined rows like "Vainona / Pomona") is moved to Phase 4, where it's used.
6. The org-listing fields on `Property` and the publication gate are in Phase 1b, as planned.

**Not renamed yet (follow-ups)**
- Backend identifiers and server-side text still say PrimeNest: Maven artifact `PrimeNest-prop`, package `primenestprop`, email templates, `@primenest.africa` seed accounts, the localStorage token key `pn_token`, and the `PrimeNest_*.docx` templates (the files themselves contain the old name).
- The real ESTATEX logo assets are still to come (placeholders in `frontend/public/orgs/estatex/`).

**Found, not caused by this work**
- `DemoDataSeeder` never runs: it exits when `users.count() > 0`, and `ReitCatalogSeeder` creates its issuer user first, so `app.demo.seed-data=true` has no effect. Left alone; flagging it.
- npm 12 install-script gating: `esbuild`, `sharp` and `unrs-resolver` postinstalls weren't approved. Build and tests work anyway. Run `npm install-scripts approve <pkg>` if anything misbehaves.

**Open questions for Phase 1b**
- Should a listing with an **expired** mandate be unpublished automatically at Harare midnight (planned), or only flagged for the PREA?
- Should "material edit resets PREA approval" cover exactly price, address and title type? Any other fields?

### Phase 1b: Compliance gates, mandates, trust ledger, commission (DONE 2026-11-17)

**Built: backend**
- `Property` gains two nullable columns: `organisation_id` and `title_type` (`TITLE_DEED | SECTIONAL_TITLE | CESSION`, string-backed). `PropertyResponse` adds `organisationId`, `titleType` and `riskFlags` (`["CESSION"]`).
- `listing/` package:
  - `ListingCompliance` (one row per org listing, `@Version`): title verification, owner consent, PREA approval, `materialChangedAt`, active mandate, publish history.
  - `Mandate` (sole/open, start and expiry dates, owner, asking/minimum price in cents, commission bps defaulting to the org's 5% for sales, offer authority, signed document, PREA sign-off). Statuses: DRAFT → AWAITING_SIGN_OFF → ACTIVE → EXPIRED / CANCELLED / SUPERSEDED.
  - `PublicationGuard`: a pure function evaluating the 5 gates (title type, title verified, in-date PREA-signed mandate, owner consent to photos **and** address, PREA approval after the last material change).
  - `OrgListingService`, `MandateService`, `OrgListingController`.
  - `MandateExpiryJob`: cron `0 5 0 * * *` Africa/Harare, plus a catch-up run on startup.
- **Enforced server-side:**
  - Org listings are created as DRAFT and aren't billed per listing.
  - `PATCH /properties/{id}` can't set an org listing to AVAILABLE (409); the only route is `POST /orgs/{slug}/listings/{id}/publish`, which runs the guard.
  - Only the PREA can approve a listing or sign off a mandate; ORG_ADMIN and platform ADMIN get 403.
  - A price or address edit (via any endpoint) or a title-type change withdraws approval. Any change that breaks a gate on a live listing (consent withdrawn, mandate cancelled or expired, title changed) **unpublishes it immediately** and notifies the negotiator and the PREA.
  - Unpublished org listings return 404 to the public and to non-members.
- **Fixed in passing:** `POST /properties/{id}/photos` had no ownership check, so any signed-in user could add photos to any listing. It now requires the listing owner/agent, an org member with LISTING_EDIT, or an admin.
- `trust/`: append-only `TrustLedgerEntry` (cents, USD/ZWG, per-property buckets, client name, links to escrow/payment/commission, `clientId` idempotency). Outgoing entries can't overdraw their bucket; writes are serialised per org with a row lock. `TRANSFER_TO_OPERATING` must cite the fee it pays. There's no update or delete API.
- `commission/`: `Commission` EXPECTED → **EARNED only via `POST .../conveyancer-confirmation`** (conveyancer, Deeds reference, registration date not in the future; `COMMISSION_CONFIRM`) → RECEIVED; EXPECTED → CANCELLED. The amount can be computed from the sale price × mandate bps.
- New org permission `TITLE_VERIFY` (ORG_ADMIN, PREA). New `NotificationType.LISTING_COMPLIANCE` plus a public `NotificationService.notify`.
- Flyway **V2** (vendor-specific): drops the `notifications_type_check` constraint on Postgres and turns the MariaDB `ENUM` into `VARCHAR(40)`, so the new notification type can be inserted on existing databases.
- Every action is audited: LISTING_CREATED / TITLE_CHANGED / TITLE_VERIFICATION / OWNER_CONSENT / PREA_APPROVED / PUBLISHED / UNPUBLISHED (with reason and whether it was automatic) / APPROVAL_WITHDRAWN; MANDATE_CREATED / OWNER_SIGNED / SIGNED_OFF / SUPERSEDED / CANCELLED / EXPIRED; TRUST_*; COMMISSION_*.

**Built: frontend**
- Workspace "Listings" tab: list with live/draft status, outstanding-check count and Cession badge; a new-listing form (draft); and the listing page with the publication checklist, PREA approve, publish/unpublish, title type/reference and verification, mandate create / attach signed copy (camera on mobile) / PREA sign-off / cancel / view document, and owner consent.
- `CessionBadge` on public property cards and the property page, which also shows a plain-language risk explanation.
- `lib/money.ts` (cents ↔ text, exact parsing) and `lib/dates.ts` (Harare calendar day), both with Vitest tests.

**Verification**
- Backend **34/34** (+17 since 1a). `PublicationGuardTest` breaks each gate in isolation. `ListingComplianceTests` covers: an unverified, unmandated or unapproved listing can't be published; each gate in order; PREA-only approve/sign-off (ORG_ADMIN and platform admin get 403); drafts hidden from the public; the status bypass blocked; price, address and title-type changes unpublish; a non-material edit doesn't; mandate expiry the morning after its last day unpublishes and notifies; consent withdrawal and mandate cancellation unpublish; independent listings unchanged; the photo-upload authorization; trust overdraw/fee-reference/idempotency; commission EARNED only via conveyancer confirmation. The CHECK-constraint guard now covers all 7 new tables.
- Frontend: tsc clean, Vitest 18/18, `next build` OK, lint 62 (= baseline, 0 in new files).
- Manual smoke (isolated H2 + headless Chrome, 375 px light/dark and 1280 px): the PREA clicked *Approve as PREA* and the negotiator clicked *Publish* in the UI. Both were audited, the listing went live, the draft stayed out of search, and the Cession badge appeared on the public card. Two display issues found and fixed: a raw enum in the checklist and the `en-ZW` date format ("17 Nov,2026").

**Decisions made during the build**
1. Compliance state lives in `listing_compliance`, not on `properties`; the legacy table only gains two nullable columns.
2. Agency title verification also sets the platform `verificationStatus` (VERIFIED/REJECTED/PENDING). For org listings, "Verified" means the agency checked title documents.
3. Org listings aren't charged the platform per-listing fee (`PropertyBillingService`). How agencies are billed is a commercial question still to decide.
4. Until the Contacts work in Phase 2, the creating negotiator is stored as the property's `landlord` (the legacy field can't be null) and the real owner's details live on the mandate.
5. Trust ledger and commission are **API-only** this phase. Their screens come with Deals (Phase 2) and the Management ledger (Phase 3).

**Found, not caused by this work**
- `GlobalExceptionHandler` returns **500** for an unknown URL (`NoResourceFoundException`) instead of 404.

**Open questions for Phase 1c (offline)**
- Which screens must work fully offline first? I'd suggest listing intake (create, title, consent, mandate photo) and enquiry capture. Approvals and publishing are deliberately online-only server commands.

### Phase 1c: Offline foundation, intake and enquiries offline (DONE 2026-11-17)

**Built: backend**
- `enquiry/`: `Enquiry` (channel: WhatsApp, call, walk-in, Instagram, Facebook, portal, website, email, referral, board, other; intent; status; contact; budget in cents; device `capturedAt`, where a future time is treated as clock skew and replaced with server time). Captures are idempotent on `clientId`. Endpoints: `GET|POST /orgs/{slug}/enquiries`, `PATCH .../{id}/status`. New org permission `ENQUIRY_CAPTURE` for all staff roles.
- Public website enquiries on an org listing (`POST /properties/{id}/inquiries`) also land in the agency's enquiry inbox (channel WEBSITE) via `PropertyInquirySubmittedEvent`. Enquiring about an unpublished org listing now returns 404.
- Listing create is idempotent on `clientId` (new `listing_compliance.client_id`). Mandate create already was.
- **`MigrationGuard`**: at startup the backend refuses to run any classpath migration that isn't named `V<n>__what_it_does.sql` or that contains DROP TABLE / DROP COLUMN / TRUNCATE / DELETE FROM without `-- reviewed-destructive: <reason>` on the line above. This also catches stale copies left in `target/classes`. `MigrationSafetyTest` checks src, the classpath, and the exact generated file described below.

**Built: frontend (offline layer, `lib/offline/`)**
- **Outbox** (IndexedDB via `idb`): every offline-capable write is queued and replayed FIFO per user. Client ids map to server ids, so a listing created offline can have its title, consent, mandate and signed copy queued behind it.
  - Network failure: stop and retry later.
  - 401: pause; nothing is dropped.
  - 5xx: back off (30 s, 2 m, 10 m, then hourly).
  - Other 4xx: moved to the "Needs attention" tray with the server's reason; unrelated work keeps syncing.
  - Web Locks stop two tabs replaying at once, and a user's queue never replays under another user's session.
- **Conflict rule, as implemented** (`merge.ts`, unit-tested): three-way, field by field. If only I changed a field, mine applies. If the server already has my value, nothing happens. If both changed it, **the server wins** and my value goes to the tray (shown as *yours / kept on server*). Creates are idempotent by `clientId`. Approvals and publishing are online-only commands and are never queued.
- **Reads offline**: workspace context, listings, listing detail and enquiries are read network-first with the last copy cached in IndexedDB. Auth now keeps the session when the API is unreachable (cached profile) and only signs out on 401/403. Previously **any** failure of `/auth/me`, including the server restarting, logged the user out.
- **Service worker** (`public/sw.js`, production builds only): cache-first for hashed static assets, network-first navigations with cached-page then `/offline.html` fallback, and warming of the workspace pages after sign-in. Plus the **web manifest** (installable, `start_url /orgs`, navy theme) and icons.
- **Screens**:
  - The intake form can save a whole intake offline as one ordered batch: listing, title type/reference, owner consent, optional mandate, and a **photo/PDF of the signed mandate**, compressed client-side (≤2048 px JPEG).
  - On the listing page, title, consent, mandate and signed copy work offline; verify, approve, sign-off, cancel and publish are disabled offline with an explanation.
  - **Enquiries** inbox (call/WhatsApp/email actions, status) and a one-tap **Enquiry** button on every workspace page.
  - **Sync** page (waiting items with discard; needs-attention with dismiss; sync now) and a header indicator (Synced / N waiting / Offline / N need attention) with an offline banner.
- `next.config.ts` reads `NEXT_DIST_DIR`, so test builds can't touch the `.next` folder a running `next dev` uses.

**Verification**
- Backend **42/42** (+8: `OfflineReplayTests` for replayed enquiry/listing creates landing once, clock-skew clamp, contact required, external roles blocked, drafts not enquirable; `MigrationSafetyTest` ×4). The CHECK-constraint guard now also covers `enquiries`.
- Frontend: tsc clean; Vitest **30/30** (+12: merge rule ×5, outbox processor ×7 covering ordered intake replay, resume without duplicates, 401, backoff, refused write plus dependants, server-wins conflict, per-user isolation); `next build` OK; lint 62 (= baseline).
- **End-to-end in Chrome against a production build** (isolated H2 backend :8082, `next start` :3001, 375 px):
  - Online first, so the service worker took control.
  - Offline: the intake page opened from cache. A full intake was saved, including a photographed signed mandate (5 queued writes), plus an enquiry. Reloading offline showed the cached listings.
  - Reconnected: synced automatically. The server has **exactly one** listing with title reference, both consents and the mandate awaiting PREA sign-off with the photo attached, and exactly one enquiry with its device capture time.
  - A deliberate same-field conflict kept the server's value and showed both values on the Sync page.
  - `/`, `/properties`, `/login`, a property page, `/orgs`, the workspace home, `/landlord` and `/tenant` all returned 200 with no console errors.
- Against the developer's **local MariaDB**, a temporary instance on :8083 validated 3 migrations ("up to date"), served 543 locations, ESTATEX with 5 divisions and 15 public listings, and was then stopped.

**Incidents this phase (recorded so they aren't repeated)**
1. During the 1b smoke test I deleted `frontend/.next/dev` while the developer's `next dev` (:3000) was using it, which corrupted its Turbopack cache, so every page hung or returned 500. Fixed by restarting it with a clean `.next`. Prevention: `NEXT_DIST_DIR` and `.next-*` ignores; test builds now use `.next-agent`.
2. IDE-generated `V3__.sql`/`V4__.sql` (unnamed, re-adding Hibernate columns and ending in `DROP TABLE app_user_roles`) were staged. A restart at 11:54 ran Flyway against local MariaDB: V3 failed (recorded `success=0`). **No data was lost**: all 6 role rows and the foreign key are intact. With the developer's approval both files were removed and the single failed history row deleted (the `flyway repair` equivalent). Prevention: `MigrationGuard`.
3. A stale `target/classes/.../V3__.sql` (from my own guard demonstration) survived the source deletion and ran on a throwaway smoke database. Removed; `MigrationGuard` now blocks this class of problem at startup.

**Deviations / notes**
- Chunked, resumable video upload (planned in §3.6) moves to Phase 3 with inspection video. Phase 1c uploads are photos/PDFs of signed mandates, compressed on the device.
- iOS Safari has no Background Sync: queued work syncs when the app is open and online, on reconnect, or every 30 s while there's work.
- `next build` with `NEXT_DIST_DIR` rewrites `tsconfig.json` to add `.next-*/types` includes. Revert it after such builds; it isn't committed.
- `next/font/google` downloads fonts at build time; one build failed on a transient network error and the rebuild passed.

**Open questions for Phase 2**
- Should captured enquiries create or merge into **Contacts** automatically, matched on phone number? I'd suggest yes, matching on the normalised +263 number.

---

## Changelog
- 2026-11-17: Initial draft (exploration only, no code).
- 2026-11-17: Decisions D1–D8 recorded; Phase 1a built and verified.
- 2026-11-17: D9 recorded; Phase 1b built and verified.
- 2026-11-17: D10 recorded; Phase 1c built and verified; V3/V4 incident resolved.

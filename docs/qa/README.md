# EstateX QA platform: assessment and plan

Status: **Phase Q0 done; findings F-1 to F-9 fixed (2026-11-27).** Latest run: backend 269/269, frontend 59/59, QA suite in Edge 60/60 on MariaDB and 60/60 on Postgres 16. Started 2026-11-27. Browser for end-to-end runs: **Microsoft Edge** (Playwright `channel: "msedge"`).

Every number below comes from a run that actually executed, not from reading test names. Re-measure with the commands in [How to measure](#how-to-measure).

## 1. Application inventory (from the source code)

| Layer | Count | Source |
|---|---|---|
| REST controllers | 58 (59 `@RestController` classes, one with no routes) | `src/main/java/**` |
| REST endpoints | **328** (144 GET, 110 POST, 46 PATCH, 21 PUT, 7 DELETE) | `qa-automation/tools/inventory.mjs` |
| Public endpoints (no sign-in) | 49 | `SecurityConfig` URL rules |
| JPA entities | 99 | `@Entity` |
| Flyway migrations | 11 applied locally (V1–V10 + repeatables) | `src/main/resources/db/migration` |
| Frontend routes (pages) | 60 | `frontend/app/**/page.tsx` |
| Frontend API client groups | 49 | `frontend/lib/api.ts` |
| User roles | TENANT, LANDLORD, AGENT, DIASPORA, INVESTOR, DEVELOPER, PRIVATE, SERVICE_PROVIDER; staff: ADMIN, FINANCE_ADMIN, MARKET_ANALYST; agency roles ORG_ADMIN, PREA, NEGOTIATOR | `UserRole`, `Permission`, org roles |
| Authorisation | URL rules (`SecurityConfig`, 30+ permission-gated routes) **plus** in-code permission/ownership checks in services | |
| External integrations | Paynow + Stripe (payments), Didit + Dojah (ID checks), Africa's Talking / Twilio (SMS), SMTP, S3 storage, ArcGIS/Esri (maps, geocoding), Gemini / Anthropic (AI) | `application.properties` |

The per-endpoint matrix (module, method, path, handler, access rule, and whether each test layer called it successfully) is generated: **[endpoint-coverage.md](endpoint-coverage.md)**.

### Feature coverage matrix

Status of tests that **actually ran successfully** on 2026-11-27. "API exercised" = endpoints of that feature that answered 2xx in the backend test run or the Edge QA run, out of the feature's total (computed by `inventory.mjs`). Overall: **236 / 328 (72 %)**; 92 endpoints have never succeeded in any test.

| Feature | Frontend pages | Backend modules | Roles | API exercised | Existing tests | Main gaps | Priority |
|---|---|---|---|---|---|---|---|
| Sign-up, login, 2-step, password reset, sessions | /login /register /forgot-password /settings/security | auth | all | 15/17 | AccountSecurityTests, login.spec, trust-wallet-security.spec | SMS 2-step send/disable (needs SMS stub), session expiry | **P1** |
| Users, roles, staff access, admin requests | /profile /admin (Users, Staff, Requests) | user, admin | admin, staff | 13/18 | AdminBootstrapTests, ApiIntegrationTests | admin creating users, admin-request approval, business verification | **P1** |
| Property listing (private landlords) | /properties/new /properties/[id]/edit | property | landlord, agent | 13/15 | ApiIntegrationTests, ListingVerificationSecurityTests | billing view; photo file serving crashes on bad paths | **P1** |
| Agency listing workflow (mandate, title, PREA approval, publish) | /orgs/[slug]/listings/** | listing, intake, campaign, report | ORG_ADMIN, PREA, NEGOTIATOR | 29/34 (incl. commissions) | PhaseTwoTests, ListingComplianceTests, publish-journey.spec | unpublish, signed mandate download | **P1** |
| Search, filters, map, detail | /properties /properties/[id] /properties/compare | property, location, neighbourhood | public | property 6/7 public; location + neighbourhood 3/7 | public.smoke.spec, LocationApiTests | neighbourhood data, sorting/pagination assertions, map markers | **P1** |
| Enquiries, contacts, pipeline, viewings, offers | /orgs/[slug]/{enquiries,contacts,pipeline,viewings} | enquiry, contact, pipeline, showing, offer, viewing | agency staff, buyers | 20/28 | offers.spec, offers-and-pipeline.spec, OrganisationWorkspaceTests | **public viewings (6/7 never succeed)**, enquiry status change | **P1** |
| Short stays (BnB) | /hosting /trips /properties/[id] | stay | guest, host | 14/16 | StayBookingTests, stays.spec, stays API spec | host decline, hosting overview | P2 |
| Rentals: applications, leases, maintenance | /applications /leases /maintenance /tenant /landlord | application, lease, maintenance | tenant, landlord | 13/24 | ApiIntegrationTests (partial) | **applications almost untested**, lease actions, maintenance photos/vendor | **P1** |
| Payments, escrow, wallet, subscriptions, featured listings | /payments /escrow /wallet /landlord/subscription /landlord/featured-listings | payment, escrow, wallet, subscription, featured | all, finance admin | 18/29 | WalletTests, PricingMathTest, trust-wallet-security.spec | **checkout/confirm/receipts, escrow release & dispute**, subscribe | **P1** |
| KYC / ID verification, agent licences, agency approval | /verification /agencies/register /admin | kyc, agent, organisation, trust | all, reviewers | 26/30 | IdReviewTests, ExternalIdChecksTests, TrustAndAgenciesTests | registry re-check, licence certificate | P2 |
| Investments / REITs | /investments | investment, market | investor, admin | 7/8 | ApiIntegrationTests | assigning properties to a REIT | P2 |
| Construction / diaspora projects | /construction /construction/[id] /diaspora | construction | developer, diaspora | **0/11** | none | **whole module untested** | P2 |
| Service vendors, moving, bookings | /services /provider /moving | vendor | providers, tenants | **2/13** | none | **whole module almost untested** | P2 |
| Messages, notifications, timeline | /messages /timeline | chat, notification, timeline | all | 13/16 (incl. dashboards) | ApiIntegrationTests | admin conversation view, mark one read | P3 |
| AI assistant (search, affordability, rent advice, lease explainer) | /ai /properties/[id] | ai | public, signed in | 1/5 | ApiIntegrationTests (search, added today) | affordability, rent suggestion, home assistant, lease explanation | P2 |
| Admin analytics (Module 21) | /admin (11 analytics tabs) | analytics/** | admin, finance, analyst | 38/44 | 9 analytics test classes (correctness recalculated from controlled data) | engine rebuild endpoint, metrics API, report runs list | P2 |
| Referrals | /referrals /r/[code] | referral | all, admin | 5/5 | ReferralTests | none | P3 |

## 2. Test layers today

| Layer | Tool | Size | Runs in CI | Result 2026-11-27 |
|---|---|---|---|---|
| Backend unit + integration | JUnit 5, Spring Boot test, MockMvc, H2 | 40 classes, 233 tests | ✅ every push/PR | 232 pass, 1 fail (race in the job lock, see finding F-4) |
| Backend code coverage | JaCoCo (added today) | | report only | **77.4 % of lines, 57.0 % of branches** |
| Frontend unit | Vitest | 11 files, 54 tests | ✅ | 54 pass |
| API (live backend) | Playwright request | 6 specs (+ access-matrix added today) | ❌ type-check only | Edge run: all API tests pass; access sweep: 401 ✅, 403 ✅, robustness ❌ (F-3) |
| End-to-end (browser) | Playwright, Edge desktop + phone | 11 specs | ❌ type-check only | Edge run: **53/55 pass**; the 2 failures were my own interference (a parallel run cleared their trace folder) and pass on their own |
| Accessibility | axe-core in Playwright | 2 tests | ❌ | passing |
| Security | access-control specs + sweep | 4 + 3 tests | ❌ | sweep finds 8 crashing endpoints |
| Performance | none | | | not tested |
| Visual regression | none | | | not tested |
| Database integrity | indirectly, via backend integration tests on H2 | | | MariaDB/Postgres-specific behaviour not tested |

Lowest Java line coverage: admin 8 %, viewing 12 %, timeline 15 %, construction 15 %, neighbourhood 16 %, application 16 %, vendor 18 %, ai 20 %, featured 25 %, maintenance 45 %.

## 3. Findings (status 2026-11-27)

| ID | Severity | Module | Finding | Evidence | Status |
|---|---|---|---|---|---|
| F-1 | High | ai | AI Search answered 500 whenever it found listings (photos loaded after the DB session closed; same path used by AI rent pricing). | Edge tour; `LazyInitializationException` in `PropertyService.searchPage` | **Fixed** (transaction on `search()`), regression test in `ApiIntegrationTests` fails without the fix |
| F-2 | High | auth, user | Two requests for the same user at the same moment can fail with 500 "Record has changed since last read" (MariaDB). Seen on `POST /auth/login` and on `GET /users/{id}/verification-level`, a read that writes to `users` and `known_devices`. | 8090 backend log, 08:56:41 and 08:57:02 | **Fixed** 2026-11-27: connections use READ COMMITTED (Postgres's default). MariaDB 11.8+ snapshot isolation made every simultaneous login of one account fail. `tests/api/concurrency.spec.ts` (20 simultaneous requests × 3 rounds) failed 3/3 before, passes after |
| F-3 | Low | common | Wrong content type on upload endpoints answers 500 instead of 415 (7 endpoints), and an unknown URL for a signed-in caller answers 500 instead of 404. The global error handler doesn't map `HttpMediaTypeNotSupportedException` / `NoResourceFoundException`. | `access-matrix.spec.ts` robustness sweep | **Fixed**: framework 4xx errors keep their status; `ApiIntegrationTests.malformedRequestsGetA4xxNotA500` fails without the fix; the sweep now finds 0 crashes |
| F-4 | Medium | analytics | The analytics job lock can let two runs of the same job overlap under load: a run's id is allocated before its row is visible, so two runs inserting at the same moment can each miss the other in the "older run?" check. | `AnalyticsEngineTests.runsOfAJobNeverOverlap` failed 1/5 under load (2.6 s overlap) | **Fixed**: per-job lock row (`analytics_job_locks`, `SELECT … FOR UPDATE`). `simultaneousStartsOfAJobNeverOverlap` (12 runs × 30) overlapped 1/30 before, 30/30 clean after |
| F-5 | Info | kyc, auth | Didit, document check and SMS 2-step answer 503 when not configured: deliberate. | sweep | Not a bug |
| F-6 | High | property | `properties.latitude/longitude` were `DECIMAL(38,2)` (Hibernate default): every map pin rounded to ~1 km (all 15 local listings). | found via a GeoDemand test after the billing change | **Fixed**: `@Column(precision=10, scale=7)` + V12 migration (MariaDB, Postgres). Already-rounded values can't be recovered |
| F-7 | High | frontend | `/properties/[id]` layout looked the listing up server-side without the visitor's sign-in and called `notFound()` on 404, so owners couldn't open (or pay for) their own hidden listings: drafts, withdrawn, and every new unpaid listing. | Edge walkthrough | **Fixed**: layout no longer 404s; hidden listings get `noindex`; the page decides with the visitor's token |
| F-8 | Critical (deploy) | analytics | Postgres V9 migration failed on any fresh Postgres database ("cannot drop index uq_metric_value because constraint … requires it"): the backend would not start on Render with this branch. | first run on Postgres 16 (Docker) | **Fixed** before V9 ever ran on Postgres: drop the constraint, recreate it. Fresh DB and a second boot (redeploy) both start cleanly |
| F-9 | Medium | billing | Listing fees and plan renewals were marked paid without collecting money (found in analytics Phase F). | code review | **Fixed** (owner decision 2026-11-27): real bills paid by checkout/wallet; new listings publish on payment; renewals PAST_DUE with 3-day grace; `BillingTests` |

## 4. Plan

Each phase ends with a run, recorded results and a pause for review.

| Phase | Scope | Exit criteria |
|---|---|---|
| **Q0** (done) | Inventory, measured coverage, JaCoCo, Edge projects, access-control sweep over all 328 endpoints | this document |
| **Q1** CI + environments | One command (`npm run stack`) starts an isolated backend + DB + frontend; GitHub Actions job runs API + Edge E2E on every PR against MariaDB **and** Postgres service containers; nightly full regression; post-deploy read-only smoke against Render; traces/screenshots/videos kept for failures | QA suite green in CI on both databases |
| **Q2** Critical journeys | Buyer (search → filters → detail → map → enquiry → admin sees it), seller/landlord (list → photos → verify → visible), agency (mandate → approve → publish → offer), rental (application → lease → payment → escrow release/dispute), admin (approve, manage user, analytics), each checked against the database | P1 rows in the matrix covered |
| **Q3** Gaps by module | Construction, vendors/bookings, viewings, applications, maintenance, AI helpers, payments checkout/receipts with gateway stubs | ≥ 95 % of endpoints succeed in some test; Java lines ≥ 85 % |
| **Q4** Security + data integrity | IDOR sweep (user A's ids with user B's token for every owner-scoped endpoint), JWT tampering/expiry, XSS/SQL-injection payloads in every text field, upload validation (type, size, extension), CORS, rate limits; DB checks for constraints, cascades, rollback | no High findings open |
| **Q5** Performance, visual, accessibility | p50/p95/p99 for key APIs and pages with safe load levels; screenshot baselines for key pages in Edge; axe on every public page | budgets agreed and enforced |
| **Q6** QA dashboard + bug reports | Run history (JSON) → HTML dashboard with pass rate, coverage by module, findings, perf, a11y; one structured bug report per failure with request/response, logs, trace; PDF export | dashboard published from CI |

## How to measure

```bash
# Isolated stack (test DB, never your real one): backend :8090, frontend :3001
#   (Q1 replaces this with `npm run stack`)
# Backend tests + coverage (writes target/site/jacoco/)
./mvnw test > /tmp/mvn-test.log
# QA suite in Edge
cd qa-automation
BASE_URL=http://localhost:3001 API_URL=http://127.0.0.1:8090/api/v1 PW_CHANNEL=msedge npx playwright test
# Endpoint inventory + which endpoints each layer exercised
node tools/inventory.mjs --backend-log /tmp/mvn-test.log --qa-log <backend log> --md ../docs/qa/endpoint-coverage.md
```

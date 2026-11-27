# Module 21: Admin Analytics, Market Intelligence and Hourly Engine (plan)

Status: **Phases A–H and R done (2026-11-26); open items completed 2026-11-27 (see the last entry).** Branch: `feature/admin-analytics`.
The brief calls the product "AfricaProp AI". The codebase and the UI call it **EstateX**, so this
plan uses EstateX.

This plan follows the same rules as `docs/estatex-integration-plan.md`. Each phase is committed
separately, and work stops after each phase so you can review it. Nothing is committed to `main`.

---

## 1. Audit: what exists today

### 1.1 Admin dashboard
- **Backend.** `GET /api/v1/dashboards/admin` lives in `dashboard/DashboardController.java:176`.
  It returns the revenue for today and for this month, the escrow balance, property counts
  (listed, sold, rented), leases and occupancy, maintenance, the verification queue and an
  activity feed. Every figure is computed live on each request. There are no stored figures,
  no comparison with an earlier period and no history.
- **Frontend.** `frontend/app/admin/page.tsx` is a single 1,368-line page with 16 tabs:
  overview, users, requests, kyc, properties, agents, companies, reports, escrows, withdrawals,
  messages, fraud, neighbourhoods, vendors, pricing and subscriptions. Charts are drawn by hand:
  `HorizontalBarChart.tsx` and `StatTile.tsx`. There is no charting library.
- **Problem with the current revenue figure.** It counts *every* successful payment. That
  includes rent, escrow funding, wallet top-ups and short-stay bookings, which are client money
  passing through EstateX and are not EstateX revenue. Section 4.4 fixes this.

### 1.2 Scheduled jobs
`@EnableScheduling` is switched on in `EstateXApplication`. The existing jobs are:

| Job | Schedule | Notes |
|---|---|---|
| `listing/MandateExpiryJob` | Daily at 00:05 Harare time, and once at startup | Catches up after downtime by running at startup |
| `property/PropertyBillingScheduler` | Cron | Bills properties |
| `subscription/SubscriptionBillingScheduler` | Cron | Renews subscriptions and marks them past due |
| `stay/StayHousekeepingJob` | Cron | Short-stay housekeeping |
| `market/ZimbabweReitMarketService` | `@Scheduled` | Refreshes REIT quotes |

None of these jobs takes a lock or keeps a run history. That is safe today because Render runs a
single instance, but it is not safe if a second instance is ever added. The analytics engine
needs its own lock and run history (see §3).

### 1.3 Schema management
Hibernate `ddl-auto=update` creates the tables. Flyway runs *after* Hibernate and only handles
indexes, constraints and data fixes. Every migration has three versions (`h2`, `mariadb` and
`postgresql`) and the latest is V4. New analytics tables follow the same pattern: JPA entities
define the tables, and Flyway migrations add the indexes, unique keys and backfills.

### 1.4 Data that already exists

| Need | Source | Usable for |
|---|---|---|
| Listings | `properties`: listingType (RENT, SALE or SHORT_STAY), status, verificationStatus, city, suburb, country, bedrooms, bathrooms, price, currency, latitude/longitude, createdAt, verifiedAt, organisationId, landlord, agent | Supply, asking prices, verification |
| Location hierarchy | `locations` with parent, province, city, density tier and centroid coordinates | Drilling from city to suburb and placing points on the map |
| Rental agreements | `leases`: monthlyRent, deposit, start/end dates, status, signedAt | **Contracted rent** (as opposed to asking rent) and time to let |
| Rental applications | `rental_applications` and `ApplicationStatus` | Application conversion |
| Enquiries | `enquiries` (channel, intent, status) and `property_inquiries` | Buyer and rental enquiry volumes |
| Viewings | `viewing_requests` and `ViewingStatus` | Viewing requests |
| Offers | `offers` and `offer_events`, with status RECEIVED → ACCEPTED/REJECTED | Offers recorded |
| Payments | `payments` (purposeType, status, amount, paidAt) | Cash collected by product |
| Subscriptions | `landlord_subscriptions` (plan, status, currentPeriodEnd, pastDueSince) | MRR, past due, churn (partly) |
| Featured listings | `featured_listing_settings` and payments with purpose FEATURED_LISTING | Featured listing revenue |
| Fraud signals | `admin/FraudDetectionService` | A starting point for duplicate detection |
| Audit trail | `audit_logs` (actor, entity, from/to state, amount) through `AuditService` | Audit of sensitive access |
| AI | `anthropic-java` is already a dependency and `AiAnthropicConfig` is set up | Interpretations written by AI |
| PDF | `openhtmltopdf` is already a dependency | PDF reports |
| Map | Leaflet with marker clustering (`PropertyMap.tsx`) | 2D map. **There is no 3D engine in the repo.** |

### 1.5 Missing data (needed before the related metrics can be reported honestly)

| Gap | Effect | Fix (phase) |
|---|---|---|
| **No price history.** `PropertyService` overwrites `price` in place (line 182). | No price trends and no record of price reductions | `property_events` table (append-only) plus daily listing snapshots (A) |
| **No status history** | Cannot count "new listings this week" correctly, time to let, or days on market | Same (A) |
| **No property category** (house, flat, townhouse, stand and so on) | No "by property type" breakdown | Add `Property.category` (A). Existing rows are `UNSPECIFIED` until edited. |
| **No floor area on Property.** It exists only on `ListingInspection`. | No price per m² | Add `Property.floorAreaM2`, prefilled from the inspection where one exists (A) |
| **No WITHDRAWN, EXPIRED, UNDER_OFFER or REPORTED_SOLD statuses.** Today `SOLD` means "someone clicked sold". | Cannot tell reported sales from verified ones | Extend the statuses and add a `sale_transactions` table that records how each sale was verified (A, see §4.1) |
| **No tracking of views, searches or saves**, and no "saved properties" feature | No demand or funnel analytics | `marketplace_events` table plus a saved-properties feature (A) |
| **No marketing spend** | Customer acquisition cost cannot be calculated | Admins enter spend by month (F). Until then CAC shows "Not tracked". |
| **No suburb boundary polygons** | No true choropleth map | Use proportional circles on suburb centroids now. Choropleth once boundary GeoJSON is supplied (open question Q4). |
| **No developer advertising, lead generation or property management fee products** | These revenue lines are 0 because they are not tracked, not because they earned nothing | Show them as "No product yet" instead of $0 |
| **No refund amounts or invoices for platform revenue** | Refunds can only be counted from `status=REFUNDED` | Enough for now. Proper invoices come later. |
| **No referral system** | — | New phase R (§7) |

**History cannot be backfilled.** Price and status history start on the day Phase A is deployed.
Earlier periods will show "No history before \<date\>" instead of a fabricated trend. Where the
audit log already holds price changes for organisation listings, those are imported and marked
with their source.

---

## 2. Architecture

```
 raw source tables (unchanged)          capture layer (new, append-only)
 properties, leases, offers,     ──►   property_events (price/status/verification/characteristic changes)
 enquiries, viewings, payments,        marketplace_events (view/search/save; no personal data, deduplicated)
 subscriptions                         sale_transactions (reported → verified, with source)
                                                │
                                                ▼  hourly job (incremental, watermark-based)
                                       listing_daily_snapshot (one row per listing per day)
                                       metric_values (metric, grain, period, dimensions → value, n, median, p25, p75, mean)
                                       market_observations (statistical findings, deterministic)
                                       market_insights (AI interpretation of observations, labelled)
                                       analytics_job_runs (lock, history, counts, errors)
                                                │
                                                ▼
                                       /api/v1/admin/analytics/**  (read-only, permission-checked, cached)
                                                │
                                                ▼
                                       /admin (executive overview) + /admin/analytics/{rentals,sales,map,demand,revenue,engine}
```

- **New package `analytics`.** Its subpackages are `capture`, `engine`, `metrics`, `insight`,
  `alert`, `report` and `api`. Source tables are never written by analytics code, apart from the
  new capture hooks in the existing services.
- **Why we keep snapshots rather than only aggregates.** Medians cannot be combined: the median
  of several suburb medians is not the city median. Filters such as an arbitrary price range or
  bedroom count are also unbounded. So `listing_daily_snapshot` stores one row per listing per
  day, and any filtered median can be calculated exactly from it. At 10,000 listings that is
  about 3.7 million rows a year, which is fine for indexed Postgres. `metric_values`
  pre-computes the standard cuts (country, city, suburb, listing type, category and bedroom
  band, by day, week, month, quarter and year) so the dashboards load quickly.

---

## 3. Hourly engine

- **Trigger.** Spring `@Scheduled(cron = "0 5 * * * *", zone = "Africa/Harare")`, which runs at
  5 minutes past every hour on the server. It does not depend on anyone having a browser open.
- **Lock and idempotency.** `analytics_job_runs` has a unique key on
  `(job_name, window_start)`. A run begins by inserting its row. If that insert fails because of
  the unique key, another worker owns the window and this run exits. A lock older than
  45 minutes is taken over, so a crashed run cannot block later ones. Every write is an upsert
  keyed on `(metric, grain, period, dimensions)`, so running the same window twice gives the
  same result.
- **Incremental processing.** Each run processes the window from `last_successful.window_end`
  to the current hour, reading only rows whose `created_at` or `updated_at`, or capture events,
  fall inside the window. Only the periods affected by those rows are recalculated (for
  example, the current day, week, month, quarter and year for the suburbs that changed).
- **Recovery.**
  - On startup, the engine finds hours that were never processed and catches up, oldest first,
    up to 72 hours back. Anything older is left to the nightly rebuild.
  - A failed step is retried 3 times with backoff (30 s, 2 min, 5 min). After that, the run is
    marked `FAILED` and the next hour includes the failed window.
- **Reconciliation.** At 02:00 each night the engine fully recalculates the previous day and
  compares the result with the incremental figures. Any difference is logged and raised as an
  alert, and the recalculated figures are kept.
- **Protecting the database.** Reads happen in batches of 1,000 by id range, each in its own
  read-only transaction. The engine uses at most 2 database connections out of the pool of 10.
  Each step has a 10-minute time budget.
- **When a job fails, the last good figures stay.** The dashboard shows when the data was last
  updated successfully, the time of the next run, the status, records processed, records that
  failed and data-quality warnings. If the last success is more than 2 hours old, a **Stale**
  banner appears.
- **Workflow steps (each is recorded per run).**
  1. Collect the changes in the window.
  2. Validate them. Rows with no price, an impossible price, no suburb or the wrong currency are
     counted as rejected, with a reason.
  3. Detect duplicates: same agent or landlord, suburb, bedrooms, a price within 3% and a title
     or photo that looks similar.
  4. Check verification status.
  5. Write the daily snapshots.
  6. Calculate the metrics.
  7. Aggregate by location.
  8. Run significance tests.
  9. Generate observations, then AI interpretations.
  10. Evaluate alerts.
  11. Publish the figures (switch the "current" pointer).
  12. Record the run.

---

## 4. Metric definitions and governance

Every metric has an entry in `MetricDefinition`: a key, a version, a plain-English description,
the source, the filter that decides which records count, the calculation, how missing values
are handled, how outliers are handled and the minimum number of observations. Each stored value
carries the definition version, the number of observations (`n`), the time it was calculated and
the id of the run that calculated it. Drill-down lists the source record ids behind each value.

### 4.1 Listing states (kept separate and never added together)

| State | How it is represented |
|---|---|
| Active listing | `status=AVAILABLE` |
| Pending verification | `verificationStatus=PENDING` |
| Verified listing | `verificationStatus=VERIFIED` |
| Withdrawn | **new** `status=WITHDRAWN`, set by the owner or an agent |
| Expired | **new** `status=EXPIRED`, set when a mandate expires or a listing goes 90 days without being refreshed |
| Under offer | **new** `status=UNDER_OFFER` (the existing `RESERVED` status is migrated to it, Q2) |
| Reported sold | **new** `status=REPORTED_SOLD`. Existing `SOLD` rows are migrated to it, because none of them was ever verified. |
| Verified completed sale | A `sale_transactions` row with `verification=VERIFIED`. It also records `source` (AGENT_REPORTED, OWNER_REPORTED, CONVEYANCER_CONFIRMED or DEEDS_REGISTRY), the document id, the verifier and the time it was verified. **An agent's report alone can never make a sale VERIFIED.** |
| Rental agreement signed | A lease with `signedAt` set and status SIGNED or ACTIVE |
| Rental agreement terminated | A lease with status ENDED or CANCELLED |

### 4.2 Market metrics (summary; the full list goes into the definitions table)

| Metric | Definition | Rules |
|---|---|---|
| Median asking rent | Median of `price` across RENT listings that were AVAILABLE at any point in the period, using the latest price in the period, in USD only | Below n=5, the value is shown but marked "low sample". Listings in other currencies are left out and counted. Outliers are never removed. Instead, the p25–p75 range is shown alongside the median. |
| Average asking rent | Mean of the same set | Always shown next to the median, with a note that extremes distort it |
| Median contracted rent | Median `monthlyRent` of leases whose `signedAt` falls in the period | Always labelled "Agreed rent" and kept separate from asking rent |
| Gross rental yield (estimated) | (median asking rent × 12) ÷ median asking sale price, for the same suburb, category and bedroom band | Shown only when n ≥ 5 on both sides. Always labelled "estimate, based on asking prices". |
| Net yield (estimated) | Gross yield × (1 − cost ratio) | The cost ratio is an admin setting, default 25% and shown on screen. It stays hidden until an admin has confirmed it. |
| Median verified sale price | Median of `sale_transactions.price` where `verification=VERIFIED` and the completion date falls in the period | Will be empty at first. The empty state explains why. |
| Price per m² | price ÷ floorAreaM2 | Only listings that have a floor area count. The coverage percentage is shown. |
| Price reduction | A PRICE_CHANGED event with new price < old price | Count, and the median percentage reduction |
| Days on market / time to let | From the first time a listing became AVAILABLE to UNDER_OFFER, REPORTED_SOLD or a signed lease | The median is used. Listings still on the market are reported separately as "still listed, age X". |
| New listings | Listings that first became AVAILABLE during the period | Based on events, not on createdAt, so drafts are not counted |
| Conversion rates | The next step ÷ the previous step, for the same listings in the same period | Search → view → enquiry → viewing → offer or application |

### 4.3 Comparing periods
- Supported comparisons: hour, day, week, month, quarter and year, each compared with the
  previous period of the same type.
- **A period that is still in progress is compared like for like.** For example, 1–26 November
  is compared with 1–26 October, and the label says so ("Month to date vs same days last
  month"). Comparing with the full previous period is available as an option and is labelled.
- Percentage change is not shown when the previous value is 0 or the sample is below the
  minimum. In those cases the screen shows "New" or "Not enough data".

### 4.4 What counts as revenue (fixes the current dashboard)

| Payment purpose | Treated as |
|---|---|
| SUBSCRIPTION, FEATURED_LISTING (and, once these products exist, lead generation, developer ads and property management fees) | **Platform revenue** |
| Commission (the agency trust ledger's `commission`) | Platform revenue *only if* EstateX earns it. Organisation commission belongs to the agency. (Q5) |
| RENT, ESCROW_FUND, STAY_BOOKING, WALLET_TOP_UP, INVESTMENT | **Client money passing through EstateX. Never revenue.** Shown separately as "Payment volume". |

The revenue screen shows four figures and never mixes them:

- **Cash collected**: successful payments, by `paidAt`.
- **Recognised revenue**: subscription payments spread across their service period.
- **Outstanding**: subscriptions that are past due.
- **Estimated**: MRR projected forward, always labelled as an estimate.

---

## 5. Roles and permissions

- **New permissions:**
  - `ANALYTICS_MARKET_VIEW`
  - `ANALYTICS_DEMAND_VIEW`
  - `ANALYTICS_FINANCE_VIEW`
  - `ANALYTICS_OPS_VIEW`
  - `ANALYTICS_CONFIGURE` (thresholds, the yield cost ratio and re-running jobs)
  - `REPORTS_EXPORT`
  - `REPORTS_SCHEDULE`
- **New staff roles.** These go in `UserRole` and `RolePermissions`. The existing `ADMIN` role
  stays as super administrator.

  | Role | Permissions |
  |---|---|
  | `FINANCE_ADMIN` | finance, export |
  | `MARKET_ANALYST` | market, demand, export |
  | `OPERATIONS_ADMIN` | ops, market, demand |
  | `VERIFICATION_OFFICER` | `PROPERTY_VERIFY`, ops, and verifying sale transactions |

- **Audit.** Viewing finance data, exporting anything, changing thresholds and re-running jobs
  are all written to `audit_logs` through `AuditService`.

## 6. Dashboard layout

`/admin` opens on the new **Executive overview**. The existing tabs move into a "Manage" group in
the sidebar, so nothing is removed. Analytics pages: Overview, Rentals, Sales, Market map,
Demand, Revenue, Insights & alerts, Reports and Engine status.

Each page follows the same layout:

- **Top bar:** title, global date range, comparison mode, location picker (country › city ›
  suburb), freshness indicator (for example "Updated 14:05 · next 15:05"), export and the
  notification bell.
- **KPI row:** active listings, rental listings, sale listings, verified transactions, rental
  demand and platform revenue. Each tile shows the value, the change against the comparison
  period with an arrow and a +/− sign (never colour alone), a sparkline, `n`, the period and a
  link to drill down.
- **Trends:** rent (asking and agreed drawn as two clearly labelled lines), sale prices (asking
  vs verified), and platform activity.
- **Map:** suburb circles sized by supply and coloured by the selected measure (yield, demand or
  median price). Clicking a suburb opens a side panel with `n`, the median and mean, a
  12-month trend, the confidence level and the time it was last updated.
- **Insights:** a "What changed" summary. Statistical observations are marked
  **Observed**; AI interpretations are marked **AI interpretation** and shown in a different
  style.
- **Lower section:** recent transactions (each labelled with its verification source),
  top-performing suburbs, popular categories, agent performance and revenue by product.

Design: a restrained neutral palette with one accent colour, positive, negative and warning
colours each paired with an icon, light and dark themes, charts drawn as hand-built SVG to match
the existing `HorizontalBarChart` (no new dependency, see Q3), and a subtitle on every chart
explaining in plain English what it shows. When there is not enough data, a chart shows an
empty state explaining what data is missing. Charts are never filled with sample data.

## 7. Referral discount (your addendum)

**Proposal (needs your answers in Q1):**

- Every user gets a referral code and a link of the form `estatex…/r/CODE`.
- The code is recorded when a new account is created.
- The person who signs up gets **X% off their first subscription payment**.
- Once that person's first payment succeeds, the person who referred them gets a **wallet
  credit**. The wallet already exists.
- Abuse protections:
  - Referring yourself is blocked: the same phone number, email, national ID or device cannot
    be both referrer and new user.
  - Each referrer can earn at most N rewards a month.
  - A reward is cancelled if the payment is refunded.
  - Every step is written to the audit log.
- Discounts reduce *recognised revenue*. They are reported as a separate "Referral discounts"
  line and as the cost of each acquired customer, which is the only CAC figure we can measure
  without marketing spend data.

## 8. Phases (each one is committed, tested and then paused for your review)

| Phase | Contents | Definition of done |
|---|---|---|
| **A. Data foundation** | `property_events`; new statuses and migration; `sale_transactions` and its verification flow; `category` and `floorAreaM2`; `marketplace_events` (view, search and save tracking endpoint, deduplicated and free of personal data); saved properties | Price and status changes are recorded on every path. Tests cover history, duplicate events and status migration on all three databases. |
| **B. Hourly engine** | `analytics_job_runs`, lock, watermark, startup catch-up, retries, snapshots, `metric_values`, nightly reconciliation, engine status API and page | Tests cover the lock under two concurrent workers, idempotent re-runs, retries, catch-up and known datasets (medians, means, p25/p75, period boundaries, partial periods). A load test with 50,000 listings stays under budget. |
| **C. Executive overview** | KPI API with comparisons, freshness, drill-down, roles and permissions, audit; redesigned `/admin` landing page | Each KPI matches a fixture calculated by hand. Access-control tests pass for every role. |
| **D. Rentals and sales** | Both dashboards with every filter, the yield calculation and the verified/reported split | Filter tests check that every chart on a page reacts to the same filters |
| **E. Map and demand** | Suburb map with a side panel; funnels; analysis of under-supplied segments | Locations are aggregated only from rows with valid coordinates or a suburb, and the coverage is shown |
| **R. Referral discount** | As in §7 | Tests cover the discount, the reward, self-referral blocking and refund reversal |
| **F. Revenue** | The revenue classification in §4.4; MRR, ARR and churn; marketing spend entry; LTV; referral costs | Tests reconcile the revenue figures with the payments in a fixture |
| **G. Insights and alerts** (done) | Deterministic observations (Poisson or z-tests with minimum n); Claude interpretations limited to those observations; alert rules, thresholds, cooldowns and preferences | No insight is produced when n is below the minimum. If AI is turned off, observations are still shown. |
| **H. Reports** (done) | CSV, Excel and PDF reports with dates, coverage and method notes; scheduled email delivery; saved views | Tests cover report generation and the coverage notes |

## 9. Decisions (2026-11-26)
- **D1 Referral:** subscriptions, both sides. The new user gets a percentage off their first
  subscription payment. Once that payment succeeds, the person who referred them gets wallet
  credit. Any user can refer. The amounts and the monthly cap are admin settings.
- **D2 Statuses:** SOLD is migrated to REPORTED_SOLD and RESERVED to UNDER_OFFER (V5).
  WITHDRAWN and EXPIRED are added. Older clients that still send SOLD or RESERVED are mapped to
  the new names.
- **D3 Charts:** hand-built SVG, with no new dependency.
- **D4 Commission** ("do what's best to generate revenue"):
  - A configurable **platform share of agency commission** is added in Phase F. Only that share
    counts as EstateX revenue.
  - It is **off (0%) until an admin sets a rate**, because charging agencies is a commercial
    change they must be told about first. Suggested starting point: 10% of the commission
    EstateX helped earn on listings it brought the lead for.
  - Featured listings, subscriptions and the referral programme (Phase R) are the other revenue
    levers.
- **Still open:** Q4, suburb boundary GeoJSON. Until we have it, the map uses centroid circles.

## Progress log

### Phase A: Data foundation (DONE 2026-11-26)

**What was built:**
- **Listing history.** `analytics/capture/PropertyHistoryRecorder` is a Hibernate
  post-insert/post-update listener. It writes `property_events` rows (CREATED, PRICE_CHANGED,
  STATUS_CHANGED, VERIFICATION_CHANGED, LISTING_TYPE_CHANGED, LOCATION_CHANGED,
  CHARACTERISTICS_CHANGED) in the same transaction as the change, through a before-commit
  callback. Every row holds the full state after the change, plus the previous price or status
  and the person who made the change. It catches all ~10 code paths that change a listing,
  without editing any of them. A price is no longer lost when it changes.
- **Statuses.** `PropertyStatus` is now DRAFT, AVAILABLE, UNDER_OFFER, OCCUPIED, REPORTED_SOLD,
  WITHDRAWN, EXPIRED and INACTIVE.
  - A mandate expiring now sets EXPIRED instead of DRAFT.
  - Owners can't set EXPIRED themselves.
  - WITHDRAWN and EXPIRED listings are hidden from the public like drafts
    (`isHiddenFromPublic`) and can be published again.
  - The JSON reader still accepts SOLD and RESERVED from older clients and the offline queue.
- **Verified sales.** The new `transaction` package holds `SaleTransaction`, which records
  REPORTED, VERIFIED or REJECTED, the source, an evidence reference, the verifier, and a copy of
  the location and characteristics.
  - Reporting a sale sets the listing to REPORTED_SOLD.
  - Verifying it needs `PROPERTY_VERIFY`, a CONVEYANCER_CONFIRMED or DEEDS_REGISTRY source, an
    evidence reference, and a verifier who is not the person who reported it.
  - Every step is audited.
- **Property type and size.** `Property.category` (`PropertyCategory`) and
  `Property.floorAreaM2` are added to the API, to the new listing form and to the edit form.
- **Demand capture.**
  - `marketplace_events` records views and searches from the browser through the public
    `POST /api/v1/marketplace-events`, and saves and unsaves from the server.
  - Stored data: a salted visitor hash only. No IP addresses or names.
  - Not counted: crawlers, and an owner or agent viewing their own listing.
  - Deduplication: a view counts once per visitor per listing per hour, and a search once per
    10 minutes. There is also a limit per IP address.
  - `saved_properties` and `/api/v1/saved-properties`. The existing local "save" heart now
    syncs to the account when the person is signed in.

**Migrations:**
- V5: renames the statuses and drops the Postgres CHECK constraint (MariaDB: changes ENUM to
  VARCHAR; H2: no-op).
- V6: copies floor area from listing inspections, then writes one BASELINE `property_events`
  row per listing.

**Endpoints:**
- `POST/GET /api/v1/properties/{id}/sale-transactions`
- `GET /api/v1/admin/sale-transactions`
- `POST /api/v1/admin/sale-transactions/{id}/verify`
- `POST /api/v1/marketplace-events`
- `GET /api/v1/saved-properties`, `GET /api/v1/saved-properties/ids`,
  `PUT/DELETE /api/v1/saved-properties/{id}`

**Frontend:** `lib/marketplaceEvents.ts`; view tracking on the listing page; search tracking on
the first results page; the save heart syncs to the account; category and floor area on the new
and edit forms; status labels (for example "Sold (reported)" and "Expired (mandate ended)").

**Tests:** `analytics/ListingHistoryAndSalesTests` (6 tests) covers:
- history on price, status and characteristic changes, with no event for a price that didn't
  really change;
- old status names still accepted, and EXPIRED blocked for owners;
- each rule for verifying a sale;
- rentals can't be reported as sold;
- view and search deduplication, crawlers ignored, own views ignored;
- saving twice.

The two existing tests that expected DRAFT or RESERVED were updated to the new statuses.

**Verification:** backend 196/196 passing (H2). Frontend: typecheck clean, lint 0 errors,
vitest 50/50. The QA specs were updated for UNDER_OFFER but not run here.

**Not yet verified:** V5 and V6 against real Postgres and MariaDB. Tests use H2, where V5 is a
no-op. Check this at the first deploy: Render logs should show `Migrating schema ... to version
"6"`.

**Known limits:**
- History starts at deploy. The audit log stores no old or new prices, so nothing earlier can be
  imported.
- Existing listings have no category until someone edits them.
- View and search counts measure interest only and can be inflated by a determined script
  (mitigated by the per-IP limit).

### Phase B: Hourly analytics engine (DONE 2026-11-26)

**Scheduling** (`analytics/engine/AnalyticsScheduler`). The engine runs on the server itself;
no browser or signed-in admin is needed:
- at 5 past every hour, Harare time (`0 5 * * * *`);
- at 02:00 for the nightly run;
- once just after startup, to catch up on anything missed while the server was down. This runs
  on a background virtual thread, so startup is never delayed.

`spring.task.scheduling.pool.size=4`, so a slow job can't hold up the mandate and billing jobs.
Tests turn scheduling off with `app.analytics.scheduling-enabled=false`.

**Lock and idempotency** (`JobLock`, table `analytics_job_runs`).
- The unique key on (job, window start, attempt) means only one worker can take a window.
- A window that has already succeeded is never processed again.
- If a fresh RUNNING row exists, other workers skip.
- A row with no heartbeat for 45 minutes is marked ABANDONED by exactly one worker, which then
  takes over.

**Windows.** Each hourly run covers the time from the end of the last successful run to the top
of the current hour. A manual run goes up to the current moment. After long downtime, only the
last 72 hours are caught up, and the run records a `CATCH_UP_GAP` warning. Older periods can be
recalculated from the admin API's rebuild action.

**Run steps.** Each step is timed and recorded on the run. Each is retried 3 times, after 30 s,
2 min and 5 min:
1. collect changes
2. validate and find duplicates
3. check verification
4. calculate figures (this includes the breakdowns by city and suburb)
5. significance tests (skipped until Phase G)
6. market intelligence (skipped until Phase G)
7. alerts (skipped until Phase G)
8. publish

Publishing writes the snapshots and replaces the figures for every recalculated period in **one
transaction**. If any step fails, nothing is published: the previous figures stay, and the
status shows FAILED or STALE.

**Figures** (`analytics/metrics`):
- **Catalogue:** 22 metrics in `MetricDefinition`. Each has a version, a plain-English
  definition and its source table.
- **Grains:** HOUR, DAY, WEEK, MONTH, QUARTER and YEAR, following the Harare calendar, with
  weeks starting on Monday.
- **Which periods each run recalculates:**
  - hourly runs: the hours in their window, plus the current day, week and month;
  - the nightly run: day through year;
  - a rebuild: every period in its range.
- **Breakdowns.** Each figure is stored for all listings, by listing type, by city, by city and
  suburb, by category, by bedroom band, by category and bedroom band, and by city and category.
  Hourly figures are only broken down to city level.
- **Each stored figure carries:** n, median, p25, p75, mean, min, max, sum, the excluded count,
  coverage, a low-sample flag (n < 5), whether the period is complete, the time it covers up
  to, the definition version and the id of the run that produced it.
- **Source.** Listing state at any moment is rebuilt from `property_events` (`StateReader`), and
  figures are always recalculated from source data. That is why running the same window twice
  gives the same answer.

**Snapshots** (`listing_daily_snapshots`). One row per listing per day. A day with no rows yet
gets every listing; after that, only changed listings are updated. Every night, yesterday is
rebuilt from scratch and compared with what the hourly runs wrote. Any difference is reported as
a `RECONCILIATION_MISMATCH` warning and the rebuilt version replaces it.

**Data quality** (`DataQualityChecker`):
- Live listings with no price, or priced in another currency, are left out of price figures and
  counted.
- Unusual prices, missing suburb, missing map location and missing category are flagged, never
  dropped.
- Possible duplicates go into `listing_duplicate_candidates`. A pair is flagged when either:
  - the same lister has two listings in the same suburb with the same bedrooms and prices within
    3%; or
  - different listers have the same title and rooms with prices within 1%.

**Endpoints:**

| Endpoint | Permission |
|---|---|
| `GET /api/v1/admin/analytics/engine/status` | any analytics permission |
| `GET /api/v1/admin/analytics/engine/runs` | `ANALYTICS_OPS_VIEW` |
| `POST /api/v1/admin/analytics/engine/run` | `ANALYTICS_CONFIGURE`, audited |
| `POST /api/v1/admin/analytics/engine/rebuild?from=&to=` | `ANALYTICS_CONFIGURE`, audited, ≤ 400 days |
| `GET /api/v1/admin/analytics/definitions` | analytics view |
| `GET /api/v1/admin/analytics/metrics?metric=&grain=&dims=&from=&to=` | analytics view |

The 7 new `Permission` values are held by ADMIN. The staff roles come in Phase C.

**Frontend.** A new "Analytics Engine" tab in `/admin` (`components/admin/AnalyticsEnginePanel.tsx`)
shows:
- the state (up to date, updating, stale, failed or not run yet), each with a plain-English
  explanation;
- the last successful update, the next scheduled update, records processed, and records left out
  of price figures;
- when history begins;
- data-quality warnings;
- run history, with the steps of each run;
- an "Update now" button.

**Tests** (25 new):
- `StatsTest`: datasets with known answers, interpolated percentiles, and an outlier that is
  kept.
- `PeriodTest`: days follow Harare midnight, weeks start Monday, quarter and year boundaries,
  incomplete periods.
- `DimsTest`: text normalisation and slice breakdowns.
- `DuplicateRulesTest`.
- `AnalyticsEngineTests`:
  - exact figures for a known dataset (median 700, mean 1,508, p25 540, p75 800, per-m² median
    10 with 80% coverage, one 10% price cut, one withdrawn listing);
  - running the same window twice gives the same result;
  - two concurrent workers: exactly one succeeds;
  - a step that fails twice and is retried;
  - a failed publish keeps the previous figures and shows FAILED;
  - a stale lock is taken over;
  - catch-up is capped at 72 hours;
  - reconciliation repairs a snapshot that drifted;
  - access control;
  - a load test.

**Verification:**
- Backend: 162/162 tests pass.
- **Load test:** 50,000 listings were processed in ~6.8 s on H2. The run covered 7 periods with
  250 suburbs.
- Frontend: typecheck clean, lint 0 errors, vitest 50/50.
- The engine panel was **not** viewed in a browser. The backend running locally was started
  before Phase B and doesn't have the new endpoints.

**Found along the way:**
- H2 returns `OffsetDateTime` for timestamp-with-zone columns, where Postgres returns
  `Timestamp`. `JobLock.instant()` now handles both.
- A stray edit in `PropertyController.java` (`statieasc`) stopped the backend compiling. It is
  fixed; it was the cause of "the APIs return nothing on localhost".

**Not yet verified:** the engine against real Postgres and MariaDB. The first deploy should show
an hourly run in the engine tab.

### Phase C: Executive overview, staff roles and drill-down (DONE 2026-11-26)

**Staff roles.**
- `UserRole` has four new staff roles: FINANCE_ADMIN, MARKET_ANALYST, OPERATIONS_ADMIN and
  VERIFICATION_OFFICER. `isStaff()` marks them and ADMIN.
- Permissions are in `RolePermissions` (now built with `Map.ofEntries`, because there are more
  than 10 entries). The mapping is in §5.
- **Security fix:** sign-up and self-service "add role" used to block only ADMIN. They now block
  every staff role.
- Staff roles are granted only through `PUT /api/v1/admin/staff/{userId}`. That needs
  `USER_MANAGE`, is audited as `STAFF_ROLES_CHANGED`, and won't let an admin remove their own
  ADMIN role. `GET /api/v1/admin/staff` lists staff.
- V7 drops the Postgres CHECK constraint on `app_user_roles`.

**New capture.**
- `user_activity_days` records each user once per day, from the token filter. Sessions couldn't
  be used for active users because they're deleted at sign-out.
- `analytics_capture_starts` (V8) records when each capture table began.

**Metric changes:**
- **Domains.** Each metric now has a domain (MARKET, DEMAND, PLATFORM or FINANCE), and each
  domain has its own permission. That permission is checked on the overview, drill-down and
  series endpoints.
- **14 new metrics** (36 in total): VERIFIED_LISTINGS, UPDATED_LISTINGS, TOTAL_USERS,
  NEW_REGISTRATIONS, NEW_AGENTS, NEW_LANDLORDS, NEW_DEVELOPERS, ACTIVE_USERS, PLATFORM_REVENUE,
  REVENUE_SUBSCRIPTIONS, REVENUE_FEATURED_LISTINGS, PAYMENT_VOLUME (client money, never revenue),
  PAYMENTS_SUCCESSFUL and PAYMENTS_FAILED.
- **`history_complete`.** False when the period starts before the source began recording. The
  overview then says the change isn't comparable instead of showing a fake "+400%".
- **`variant` = TO_DATE.** For every unfinished period, the engine also stores the previous
  period cut to the same length (for example 1–26 Oct alongside 1–26 Nov). That makes like-for-like
  comparison possible. If the previous FULL period is missing, it is calculated once.
- **Quarter and year** are now recalculated every hour.

**Overview** (`GET /api/v1/admin/analytics/overview?grain=&compare=TO_DATE|FULL&city=&suburb=`).
- It reads only published figures. Each KPI has:
  - its value, the previous value and the absolute and percentage change;
  - a direction (UP, DOWN, FLAT, NEW or NONE) and whether it is comparable, with a reason when
    it isn't;
  - n, a low-sample flag and a 12-period trend;
  - whether the location filter applies, and whether drill-down is available.
- Sections a role can't see are left out and marked restricted.
- **Live KPIs** are read directly from the database and labelled live: approval queues, active
  agencies, MRR and past-due subscriptions.
- **Unavailable KPIs** state why rather than showing a zero: lead revenue, developer ads,
  property management fees and agent responses.
- Finance views are audited (`ANALYTICS_FINANCE_VIEWED`).

**Drill-down** (`GET /api/v1/admin/analytics/records`).
- It recalculates the period with tracing on and returns the source records (listings, leases,
  sales, enquiries, viewings, applications, offers, users or payments), up to 500.
- Views and searches are anonymous, so their drill-down explains that there are no individual
  records to show.
- Opening finance records is audited.

**Frontend.**
- `/admin` now opens on **Executive overview**
  (`components/admin/analytics/ExecutiveOverview.tsx`). It has:
  - a period switch (day to year) and a comparison mode (same point vs whole previous period);
  - city and suburb filters;
  - a freshness chip that links to the engine tab, and CSV export with period, comparison,
    location and method notes;
  - headline tiles followed by the Platform, Marketplace, Customer activity and Business sections;
  - KPI tiles that show change with an arrow, a +/− sign and a word, not colour alone, plus the
    previous value, a hover-able sparkline, n, a small-sample badge and a records drawer.
- The old overview moved to an **Operations** tab, and a new **Staff Access** tab was added.
- Staff without ADMIN see only the overview, plus the engine tab for operations and verification
  staff. Admin-only data isn't requested for them.

**Fixes found while checking against real MariaDB:**
- `analytics_job_runs` failed to create on MariaDB because two 8,000-character VARCHAR columns
  exceeded the row size limit. They are now long text (`LONG32VARCHAR`).
- **The `/api/v1/dashboards/admin` 500 existed before this work.** The activity feed read the
  payer, tenant and property outside the database session, so it failed as soon as any
  successful payment existed. Fixed with entity graphs.
- A calculated period with no rows for the chosen location now shows 0 instead of "no figure".

**Tests** (10 new):
- `ExecutiveOverviewTests`:
  - staff roles can't be self-assigned and are granted only by an admin, with an audit entry;
  - each role sees only its own sections;
  - KPIs match published figures, the location filter and drill-down work, and a not-comparable
    period shows no percentage;
  - like-for-like vs whole-period change maths (+50%, −40%, NEW);
  - revenue includes only EstateX products, reconciles with the payments table, and opening its
    records is audited;
  - the old dashboard works when payments exist;
  - activity is recorded once per day.
- `ToDatePeriodTest`.
- `DrillDownMatchTest`.

**Verification:**
- Backend 172/172. Frontend: typecheck clean, lint 0 errors, vitest 50/50.
- **On real MariaDB** (a scratch database on port 8090):
  - all 8 migrations applied to a fresh database;
  - the startup run succeeded;
  - the overview, filters, drill-down, revenue, staff, run-now and old dashboard endpoints all
    returned 200.
- **In a browser** (`localhost:3000`, with its API calls routed to the scratch backend), I
  clicked through the overview, the Harare filter, the records drawer and Staff Access. There
  were no console or API errors.
- **Not verified:** Postgres. Check it after the first deploy.

**Fixes after Phase C (2026-11-26), found while running on the local MariaDB:**
- **V9.** The unique key on `metric_values` must include `variant`. A database that ran Phase B
  kept the old key, because ddl-auto never changes an existing index, so it rejected the
  like-for-like rows. V9 rebuilds the index, and is safe on a fresh database.
- **Lock race.** The concurrency test caught two workers processing the same window. A worker
  that saw the other's RUNNING attempt treated it as a retry. Now a RUNNING attempt blocks the
  window, and after inserting, a worker steps aside if an older run of the same job is running.
  Both concurrency tests now run 5 times each.
- **Release on shutdown.** On a normal shutdown (deploy or restart), the server marks its own
  unfinished runs ABANDONED (`@PreDestroy`). Before this, every restart blocked the engine for 45
  minutes until the stale-lock timeout. A crash still relies on that timeout.
- **Verification:**
  - Backend 183/183.
  - The local backend was restarted on the new code. On the real local data, the startup
    catch-up succeeded (1,524 figures) and every public endpoint returned 200.

### Phase D: Rental and sales market dashboards (DONE 2026-11-26)

**How it works.**
- The dashboards accept any combination of filters: country, city, suburb, property type, exact
  bedrooms (5 = 5+), price range and dates. Exact bedrooms and price ranges aren't among the
  pre-calculated figures, so `analytics/market/MarketDashboardService` calculates on request:
  - **stock** (live listings, asking prices with p25/p75, price per m², vacancy) from the
    engine's daily listing snapshots, using the latest snapshot in each period, so it matches
    the last successful run;
  - **flows** (new listings, withdrawals, price cuts, time to let or days on market, leases,
    reported and verified sales, enquiries, viewings, applications, offers) from listing history
    and the transaction tables.
- **One filter for everything.** Every figure, chart and table goes through one `MarketFilter`.
  The price range applies to the asking price for listings and to the agreed amount for leases
  and sales.
- **Caching.** Results are cached per filter set, per engine run, for 10 minutes.

**Rules that keep it honest:**
- **Asking vs agreed.** Asking and agreed rent are separate series and KPIs. Verified sales
  (from conveyancer or Deeds Registry evidence) are separate from reported sales.
- **Blank vs zero.**
  - Listing figures before listing history began are left blank.
  - Counts from tables with full history (leases, sales, enquiries) show a real 0 for an empty
    period.
  - Listing flows show 0 only after history began.
- **Small samples.** Medians resting on fewer than 5 listings are flagged as a small sample.
- **Yield.**
  - Gross yield = median asking rent × 12 ÷ median asking sale price, for the same place and
    kind of home, only where at least 5 rentals and 5 sales are listed.
  - Net yield appears only once an admin has set the cost ratio, through
    `PUT /market/settings/yield-cost-ratio`. That needs `ANALYTICS_CONFIGURE`, is audited, and
    accepts 0–90%.

**Endpoints:**
- `GET /api/v1/admin/analytics/market/{rent|sale}`. Needs `ANALYTICS_MARKET_VIEW`; demand
  figures are included only with `ANALYTICS_DEMAND_VIEW`.
- `GET /api/v1/admin/analytics/market/{type}/listings`: the live listings behind a figure.
- `GET` and `PUT /api/v1/admin/analytics/market/settings/yield-cost-ratio`.
- New table: `analytics_settings`.

**Frontend.** New **Rental Market** and **Sales Market** tabs, for admins, market analysts and
operations staff (`components/admin/analytics/MarketDashboard.tsx`). Each has:
- **Filters:** one row with date presets, grouping, city and suburb, type, bedrooms and price.
- **KPI tiles** comparing with the first period that has data.
- **Charts:**
  - asking vs agreed rent, or asking vs verified sale price, with a middle-50% band;
  - supply, new listings, and leases signed or reported vs verified sales;
  - enquiries per listing, applications vs approvals, and price cuts;
  - sales vs rental activity.
- **Tables** by suburb (click a suburb to focus on it), type, bedrooms and price band, each with a
  "Listings" drawer.
- **Yield table**, with the cost-ratio setting for admins.
- **CSV export.**
- **Chart components** are hand-built SVG (`charts/LineChart.tsx`, `charts/ColumnChart.tsx`):
  - validated palette slots 1 and 2, 2 px lines and columns no wider than 24 px;
  - one y-axis, with a legend plus end labels when there are two series;
  - a hover crosshair and tooltip, gaps for missing data, and hidden data tables for screen
    readers.

**Tests** (`MarketDashboardTests`, 5 tests; all values worked out by hand):
- **Rentals:** median asking 600, mean 584, agreed 650, new 6, withdrawn 1, a lease signed and
  time to let. Blank vs zero is checked before history began.
- **Filters:** category + bedrooms + max price gives 3 homes on every KPI, chart point and
  breakdown table, and in the listings drill-down.
- **Sales:** reported vs verified (85,000), per m² 1,071.43, price bands.
- **Yield:** suppressed below 5 listings; 9.00% gross; net 6.30% once a 30% cost ratio is set.
  The setting is audited and analysts can't change it.
- **Permissions:** finance and verification staff get 403; analysts see demand figures. Bad
  type, dates and bedrooms are rejected.

**Engine test fix.** One run of the "different windows" concurrency test failed. Under load, the
second thread started after the first had finished, which is valid sequential behaviour. The test
now checks the real rule: two runs of the same job never overlap in time. A deterministic latch
test checks that a run started during another is skipped.

**Verification:**
- Backend 188/188. Frontend: typecheck clean, lint 0 errors, vitest 50/50.
- **On real MariaDB** (a scratch database on port 8090, with 78 listings and 15 leases backdated
  over 7 months and one day rebuilt per month), both dashboards were checked in a browser. That
  covered the charts, the hover tooltip, the 3-bedroom filter, tables, yields and drill-down,
  with no API or console errors.
- I fixed what that check found: the KPI comparison now names the actual period, empty count
  periods show 0 instead of a gap, axis labels no longer collide, count axes use whole numbers,
  and "Sept" now reads "Sep".

### Phase E: Market map and customer demand (DONE 2026-11-26)

**Map** (`GET /api/v1/admin/analytics/geo/{rent|sale}`, `analytics/market/GeoService`). Needs
market permission; the demand layers need demand permission.

**What each suburb gets.** From the latest snapshot, only listings with a suburb are aggregated.
Each suburb gets:
- live listings, median and mean asking price, n, and a confidence level (HIGH 20+, MEDIUM 5–19,
  LOW under 5);
- a 90-day price change, only where at least 5 listings existed at both dates;
- estimated gross yield, with at least 5 rentals and 5 sales;
- new listings, leases or verified sales, enquiries + viewings, and searches, all over 90 days.

**Placement:**
- A suburb is placed at its centre from the location data or, failing that, at the median of its
  listings' own pins. The median is calculated at full precision: `Stats` rounds to cents, which
  is about 1 km for a coordinate.
- If neither exists, the suburb is counted but not drawn.
- `coverage` reports live listings, how many are placed on the map, how many have no suburb,
  how many are in suburbs that can't be placed, and how many have their own pin.

**Gap found in the data.** None of the 436 suburbs in the location reference data has
coordinates; only cities and towns do. So suburbs are placed only through their listings' pins
for now. Adding suburb centres to the location data would place the rest. The map says this
whenever it applies.

**Demand** (`GET /api/v1/admin/analytics/demand?from&to&city`, `DemandService`). Needs demand
permission. It provides:
- searches, the share that found nothing, and abandonment (no listing opened by the same
  anonymous browser within 30 minutes);
- views and saves;
- funnels for rent (searches → views → enquiries → viewings → applications → approved) and sale
  (… → offers → accepted). These are volumes in the period, not the same people moving through,
  and the page says so;
- the most searched places, property types, bedrooms and price brackets, with live supply beside
  each;
- the most viewed and most saved listings;
- response times for viewings confirmed, applications reviewed and offers decided. Enquiry
  replies have no timestamp, so that one is shown as unavailable, with the reason.

**Supply gaps.** A segment (type + place + category + bedrooms) is flagged only with at least 10
searches, and only when nothing matching is live or its searches per live listing are at least
twice the market's. The wording always says "interest, not confirmed demand".

**Frontend.** Two new tabs, Market Map and Customer Demand.

- **Market Map:**
  - Leaflet with Esri tiles; circles sized by live listings and coloured by the chosen measure.
  - Magnitudes use quantile classes on the validated blue ramp. Tied breaks are merged
    (`lib/mapClasses.ts`, with a unit test).
  - Price change uses a red ↔ grey ↔ blue diverging scale.
  - "Not enough data" is a dashed hollow circle.
  - A legend, hover labels, an optional clustered listing-pin layer, the coverage line, a suburb
    side panel (figures, confidence, 12-month trend) and a full table, so the map is never the
    only way to read the figures.
- **Customer Demand:**
  - KPI tiles and funnels with ordinal blue steps.
  - Supply-gap list, ranked tables, most viewed and saved listings, and response times.

**Tests:**
- `GeoDemandTests` (4):
  - placement, coverage, LOW confidence and median 600;
  - a known centre is used when present;
  - 13 searches with 92.3% finding nothing and 7.7% leading to a view;
  - funnel conversion, the top suburb and the most viewed listing with its enquiry;
  - a gap flagged at 12 searches with nothing live, and not flagged at 9;
  - enquiry replies unavailable;
  - permissions.
- Frontend: `mapClasses.test.ts` (3).

**Verification:**
- Backend 192/192. Frontend: typecheck clean, lint 0 errors, vitest 53/53.
- **On real MariaDB** (a scratch database, 112 listings over 7 suburbs with backdated history,
  seeded searches, views and enquiries), checked in a browser:
  - the map with tiles and circles, the price-change colouring, and the Belgravia panel (+14.7%,
    7.93%);
  - the demand page, with both gaps flagged;
  - no console or API errors.
- **Fixed during that check:**
  - the map container showed no height on first render; it now uses an inline height;
  - the legend repeated ranges when values tied;
  - the table repeated a column.

### Phase R: Referral discount (DONE 2026-11-26)

**How it works** (`referral` package):
- Every user has a code. Codes are 8 characters from an alphabet without 0/O or 1/I, and are
  created the first time the user opens Invite friends. The link is `/r/CODE`.
- Sign-up accepts `referralCode`. A code that doesn't exist is rejected with 400 before the
  account is created, so it can be fixed.
- The referee's first subscription checkout is discounted by the programme's percentage. The
  discount is recorded on the payment's metadata (`referralId`, `discountPercent`,
  `discountCents`), so revenue figures are net cash.
- When the gateway confirms that payment, the referrer gets wallet credit from the
  `MARKETING_EXPENSE` system account (wallet entry `REFERRAL_REWARD`). The credit is paid once
  (idempotency key) and up to `monthlyCap` rewards per calendar month; above the cap the status
  is CAPPED.
- Only the first ever successful subscription payment is discounted. Renewals and later upgrades
  are full price.

**Anti-abuse:**
- A self-referral is marked BLOCKED (no discount, no reward), checked at sign-up and again at
  the first payment. It's caught by any of:
  - the same browser (a salted device-id hash kept only for this check, in
    `referral_device_marks`);
  - the same national ID number;
  - the same email address, ignoring dots and +tags for Gmail and +tags elsewhere.
- **Reversals.** EstateX has no in-app refund flow; nothing sets REFUNDED. So
  `PaymentRefundedEvent` is ready for when one is built, and staff can reverse a reward from the
  admin panel. The reversal debits the wallet if it can cover it. Otherwise the status is
  REVERSAL_PENDING, for follow-up, without blocking the refund.
- Every step is audited: REFERRAL_REGISTERED, BLOCKED, REWARDED, CAPPED, REVERSED and
  SETTINGS_CHANGED.

**Settings.**
- On/off, discount 0–90%, reward $0–$1,000, and monthly cap.
- **The programme is OFF by default**, with suggested values of 20%, $10 and 10 a month, until an
  administrator switches it on.
- Codes and attribution work even while the programme is off.

**Endpoints:**

| Endpoint | Access |
|---|---|
| `GET /api/v1/referrals/code/{code}` | Public; reveals nothing about the referrer |
| `GET /api/v1/referrals/mine` | Signed-in user |
| `GET /api/v1/admin/referrals` | Finance or admin |
| `PUT /api/v1/admin/referrals/settings` | `ANALYTICS_CONFIGURE` |
| `POST /api/v1/admin/referrals/{id}/reverse` | `ANALYTICS_CONFIGURE` |

**Analytics.** New metrics REFERRAL_SIGNUPS (platform), REFERRAL_DISCOUNTS and REFERRAL_REWARDS
(finance), shown on the overview with drill-down to the users and payments behind them.

**Frontend:**
- `/r/[code]` redirects to sign-up with the code filled in, and the sign-up form shows the offer.
- `/referrals` (Invite friends, in the profile menu): the link, copy and WhatsApp share, and the
  user's stats.
- The subscription page shows a discount note.
- An admin **Referrals** tab with settings, totals, and a table of referrals with reasons and a
  Reverse action.

**Tests** (`ReferralTests`, 9):
- the discount (GROWTH $15 → $12) and the referrer credited exactly once, even when the webhook
  repeats;
- the second payment at full price;
- the costs appear in the overview;
- a mistyped code is rejected;
- self-referral from the same browser is blocked;
- the monthly cap;
- refund reversal, and the pending case when the wallet can't cover it;
- nothing is discounted while the programme is off;
- admin-only settings;
- email aliases are treated as the same person;
- 4 simultaneous first requests get the same code.

Frontend: `referralCode.test.ts`.

**Verification:**
- Backend 201/201. Frontend: typecheck clean, lint 0 errors, vitest 54/54.
- In a browser on a scratch MariaDB database: link → sign-up with the offer shown → subscription
  page discount note → Invite friends page → admin panel.
- **Bug found and fixed.** The subscription page asked for `/referrals/mine` twice at once (React
  dev mode). Both requests tried to create the same code; one failed with 409, and the page then
  hid the discount. Code creation now runs in its own transaction and reads back the winner's
  code, and the page no longer clears a result it already has. Covered by the concurrency test.
- **Not verified:** a real Paynow or Stripe payment completing a discounted checkout. Tests use a
  fake gateway.

### Phase F: Revenue analytics (DONE 2026-11-26)

**Important finding: two charges are recorded without collecting money.** Both use provider
`platform` and are tagged purpose OTHER:
- **Listing fees.** Every new listing's $7 monthly fee (`PropertyBillingService.chargeForNewListing`)
  and its monthly renewal.
- **Subscription renewals** (`SubscriptionService.renewDuePaidSubscriptions`).

The code creates the payment and immediately marks it SUCCESSFUL through the payee
self-confirmation path, with no gateway and no stored card. So listings stay live and paid plans
renew without anyone paying.

The analytics now classify these as **"charges recorded without payment"**. They're excluded
from cash, revenue and "client money", counted separately (metric `UNCOLLECTED_CHARGES`), and
explained on the overview and the Revenue tab.

**The billing behaviour itself is unchanged.** It's a product and pricing decision for the owner:
for example, require checkout and mark the charge PAST_DUE until paid, or add stored cards.

**Capture.**
- `subscription_events` is an append-only plan and status history with the plan's price at the
  time. It's written by `SubscriptionHistoryRecorder`, a Hibernate listener like listing
  history; period-date changes aren't recorded as events.
- V10 adds a BASELINE row per subscription and a capture start.
- Featured-listing checkouts now record `durationDays` in the payment metadata.

**Revenue service** (`GET /api/v1/admin/analytics/revenue?from&to&grain`). Needs
`ANALYTICS_FINANCE_VIEW`; every view is audited (`ANALYTICS_REVENUE_VIEWED`). Four groups, never
added together:
- **Cash:** successful subscription and featured-listing payments by payment date, plus refunds
  (none exist; there's no refund flow) and failed payments.
- **Recognised:** each payment spread pro rata over what it pays for (subscription 30 days,
  featured listing its duration), deferred revenue at the end, and referral discounts given.
- **Outstanding:** what past-due subscribers owe (an estimate: there are no invoices) and charges
  recorded without payment.
- **Estimated:**
  - MRR, ARR and ARPA, from subscription history; MRR counts ACTIVE paid subscriptions only;
  - the commission share: an admin-set % (`PUT /revenue/commission-share`, `ANALYTICS_CONFIGURE`,
    0–50%, audited) of agency commission RECEIVED. **It is off (0%) by default and never added to
    cash.**

**Unit economics:**
- subscriber churn (churned ÷ paying at the start + new);
- LTV = ARPA ÷ monthly churn, which needs at least 60 days of history and some churn;
- CAC = (marketing spend entered + referral rewards) ÷ new paying subscribers;
- payback months.

**Series per period:**
- cash, recognised, and their split by product;
- MRR, paying subscribers, and new, expansion, contraction and churned MRR;
- churn %, failed payments, uncollected charges and marketing spend (spread over its month).

**Breakdowns:** by product (each subscription plan, and featured listings), by customer category
(the payer's main role) and by location (the listing's city for featured listings, the
subscriber's city for subscriptions).

**Marketing spend.** `POST` and `DELETE /api/v1/admin/analytics/marketing-spend`. Finance staff
enter it by month and channel; it's audited. Table `marketing_spend`; its column is
`spend_month`, because "month" is reserved in H2.

**Engine and overview.**
- New metrics: MRR, PAYING_SUBSCRIBERS, SUBSCRIPTION_CANCELLATIONS and UNCOLLECTED_CHARGES.
- "Platform" charges are no longer counted as successful payments or as client money.
- The overview's MRR now comes from history and can be compared with earlier periods (it was a
  live count). Drill-down resolves subscriptions.

**Frontend.** A **Revenue** tab for admins and finance staff, with:
- the four groups side by side, plus unit economics;
- a warning banner for uncollected charges;
- cash vs recognised, and MRR charts;
- an MRR movements table;
- the three breakdowns;
- marketing spend entry and deletion;
- the commission-share setting (admin only);
- notes and CSV export.

**Tests** (`RevenueTests`, 6; a 2025 fixture no other test touches):
- **Cash $45:** rent, the renewal and the listing fee are excluded.
- **Recognised:** March $30.50, April $14.50 (Harare month boundaries); deferred at the end of
  March $14.50, and recognised + deferred = cash.
- **Subscriptions:** MRR $64, then $49; new MRR $49; churned $15; churn 50%.
- **Unit economics:** CAC $100, payback 2.0 months.
- **Breakdowns and checks:** by product and category, uncollected $22 (1 listing fee and 1
  renewal), and cash reconciled against the payments table.
- **Commission share:** off until set; $300 at 10%; finance staff can't change it; it's never
  added to cash.
- **Capture:** the subscription listener records CREATED and CHANGED with the price, and ignores
  period-date-only changes.
- **Access:** finance only, every view audited, and the range is validated.

**Verification:** in a browser on a scratch MariaDB database seeded with 12 subscribers, 6 months
of payments, featured listings and marketing spend, the Revenue tab showed no errors. That check
found that listing fees, not only renewals, were self-confirmed (14 of the 16 uncollected
charges), which led to the rename above.

### Phase G: Insights and alerts (DONE 2026-11-26)

**Engine.** The three steps skipped until now run after publish, so a failure in them never
withdraws published figures. Each records its outcome, turns a failure into a run warning, and
is retried on the next run (no backoff: they are cheap or, for AI, rate-sensitive).
1. `significance_tests`: `ObservationService` regenerates the observations.
2. `market_intelligence`: `MarketInsightService` asks Claude to interpret them.
3. `alerts`: `AlertService` evaluates the alert rules.

A failed run raises a JOB_FAILED alert, and a 15-minute scheduler check raises DATA_STALE, so
staleness is noticed even when the engine itself has stopped.

**Observations (deterministic, "Observed").** For both week and month, the latest completed
period is compared with the one before. Table `market_observations`:
- **Counts:** enquiries, viewing requests, listing views, saves, new listings, rental
  applications and offers by slice; failed payments and subscription cancellations platform-wide.
  - The test is a Poisson rate test, conditional binomial and exact up to 2,000 events, adjusted
    for period length.
  - Needs at least 20 events across the two periods, and a change of at least 10%.
- **Medians:** asking price and agreed rent, per listing type (rents and sale prices are never
  mixed).
  - Approximate z-test using SE = 1.2533 × (IQR / 1.349) / √n.
  - Needs at least 10 prices in each period and a change of at least 5%.
  - Caveat: a listing can appear in both periods, so the samples overlap.
- **Category demand vs supply,** within the latest period: a category's share of enquiries
  against its share of listings (two-proportion z-test).
  - Needs 30 enquiries and 20 listings for the listing type.
  - Reported when the ratio is at least 1.5× (under-supplied) or at most 0.67× (over-supplied).
- **Not significance tests, and labelled as such** (methods THRESHOLD and DESCRIPTIVE, with LOW
  to MEDIUM confidence):
  - supply (a stock): active listings, at least 20, with a change of at least 10%;
  - indicative gross yield: median asking rent × 12 ÷ median asking sale price, at least 10 of
    each, with a change of at least 0.5 points;
  - the largest category by supply.
- **One correction per run.** A single Benjamini–Hochberg correction covers every tested
  comparison in the run, and only q ≤ 0.05 is kept. This is what keeps hundreds of suburb tests
  from producing a steady stream of false findings.
- **Never test across a gap in recording.** A comparison whose earlier period started before
  its capture table did is not tested: a half-recorded period would look like growth.
- **Coverage.** `observation_coverage` keeps, per grain, the number tested, reported, below the
  minimum, and with incomplete history. It drives the explicit "No reliable conclusion can be
  drawn: …" answers.
- **Stable ids.** A fingerprint keeps each row's id across hourly reruns, and findings that stop
  holding after a recalculation are deleted.
- **Fields of each observation:** the statement, location, period and comparison period, metric,
  both sample sizes, confidence, test, p and q, and its domain. The domain decides who may see it.

**AI interpretation ("AI interpretation").** `MarketInsightService` calls Claude through the
existing `AiAnthropicConfig` key:
- **Model:** `app.analytics.ai-model`, default `claude-opus-5-5`.
- **Request:** structured output (`Draft`: headline plus up to 8 items of CHANGE, OPPORTUNITY or
  RISK, each with an interpretation, an implication and the observation ids it rests on).
- **What it sees:** only market and demand observations (finance findings are never sent), up to
  40, strongest first.
- **Guard rails:**
  - Items that cite no observation it was given are dropped server-side.
  - The read API hides any item citing an observation the viewer's role can't see.
  - The prompt forbids figures, places or periods not in the observations, and treats causes as
    possibilities.
- **Cost:** Claude is only called when the set of observations changes (a SHA-256 fingerprint);
  a failure is retried after an hour.
- **Status:** each attempt is recorded in `market_insights` as OK, REFUSED (Claude declined:
  `stop_reason = refusal`) or FAILED, with token counts.
- **Admin switch:** `PUT /insights/ai` (audited). When off, unavailable (no key), refused or
  failed, observations are still served and the UI says why.

**Alerts.** The 9 kinds in `AlertKind`, with editable defaults stored in `alert_rules` (a missing
row means the defaults):
- demand surge, supply drop, price jump, category under-supply;
- possible duplicate listings (from the data-quality step);
- cancellations up, payment failures up;
- job failed, data stale.

Noise control:
- Market rules only see observations that passed their test, then apply their own threshold
  (% change), minimum sample and maximum q.
- A dedupe key (rule, place, period) means a finding is never raised twice.
- A cooldown per rule and place, in hours.
- At most 5 per rule per run, strongest first.

Delivery and preferences:
- **Recipients:** staff whose role can see the alert's domain. The notification is in-app (new
  `NotificationType.MARKET_ALERT`; `notifications.type` is already a VARCHAR since V2) and/or
  email.
- **Preferences:** per user and rule, in `alert_preferences`. No row means in-app on, email off.

**API** (all under `/api/v1/admin/analytics`; any analytics permission, filtered by domain):
- `GET /insights?grain=WEEK|MONTH`: the observations, coverage, AI view, and the brief's ten
  analyst questions, each answered with observation ids or with "No reliable conclusion…".
- `GET /alerts?open=`, and `POST /alerts/{id}/acknowledge` (audited).
- `GET /alert-rules`, and `PUT /alert-rules/{key}` (ANALYTICS_CONFIGURE; validated; audited with
  the before and after values).
- `GET /alert-preferences` and `PUT /alert-preferences/{key}`: only the viewer's own, and only
  for domains they can see.

**Frontend.** A new **Insights & Alerts** tab for all analytics staff. It contains:
- **Header:** a week-on-week or month-on-month toggle and a coverage line.
- **AI interpretation:** a card with a dashed indigo border and an "AI interpretation" label,
  visibly unlike the observations. Its items are grouped as what changed, opportunities and
  risks; each has "Based on observation #…" links that scroll to and highlight the evidence. It
  shows the model and the time written, and an on/off switch for admins.
- **Analyst questions:** each answered with evidence or an explicit "no reliable conclusion".
- **Observation cards:** each labelled "Observed", with a signed change and an arrow (never colour
  alone), location, period, comparison, n, confidence, and the test with q.
- **Alert centre:** severity icon and label, an open-only filter, and acknowledge.
- **Rules table:** editable by admins, read-only for other staff.
- **Personal preferences:** in-app and email, per rule.

**Fix along the way.** `NotificationBell` only knew 4 of the 8 existing notification types.
Wallet, stay-booking, compliance and verification notifications had no icon, which crashed the
dropdown. All 9 types now have an icon with a fallback, routes are optional, and MARKET_ALERT
links to `/admin?tab=insights`.

**Tests.**
- **`SignificanceTest` (7):** exact binomial 0.002221; period-length adjustment; the normal
  approximation with continuity correction; median z; two proportions; BH q-values; numeric
  helpers.
- **`InsightsTests` (8):** a fixture over the two latest completed weeks.
  - Avondale rental enquiries 10 → 40 are reported, with the statement, q and domain;
    Borrowdale 3 → 6 is below the minimum and not reported; 50 → 60 overall is not significant.
  - Asking rent $500 → $600 (median test); cottages under-supplied (50% of demand vs 10% of
    supply); houses are the top supply category.
  - Ids are stable across reruns, and a finding is dropped when recalculated away.
  - Per-role visibility: the analyst gets no finance findings, finance gets only finance
    findings, a tenant gets 403. Questions are answered, or say "No reliable conclusion".
  - The AI: an uncited item is dropped; finance findings are never sent; it isn't called again
    for the same input; when off, it isn't called and observations remain; refused and failed
    attempts are recorded; the real request builds with a structured-output schema.
  - Alerts: recipients by role; dedupe; cooldown; threshold change (admin only, audited);
    preference opt-out; finance preferences forbidden to an analyst.
  - Operational: job failed (once per run), duplicates, staleness, acknowledgement, and alerts
    hidden from roles without the domain.

### Phase H: Reports, scheduled delivery and saved views (DONE 2026-11-26)

**Reports** (package `analytics/report`). There are ten types in `ReportType`, one for each item
in brief §11:
- daily platform;
- weekly rental market; monthly sales; annual market (rentals and sales by month);
- quarterly market intelligence;
- geographic;
- revenue;
- agent performance; developer performance;
- investment intelligence.

**Where the figures come from.** `ReportBuilder` builds each report from the same services as
the dashboards, so a report matches the screen for the same period and filters:
- `OverviewService`, which now takes a reference date so the daily report covers yesterday, a
  complete day;
- `MarketDashboardService`, `GeoService` and `RevenueService`;
- Phase G's observations, AI interpretations and alerts.

Only three reports read source tables directly, because no dashboard covers them yet:
- **Agent performance:** a listing counts for its agent; if it has none, for its owner when the
  owner is an agent.
- **Developer performance:** listing owners with the developer role, plus their construction
  projects.
- **Investment intelligence:** REIT offerings and their take-up, alongside indicative rental
  yields by suburb.

**Every report carries its context:**
- when it was generated and by whom (or by which schedule);
- the period, as dates and a label; the default is the last completed day, week, month, quarter
  or year for that report;
- the filters applied (city, suburb, category);
- data coverage, for example snapshot dates, when history starts, map coverage, and sections
  left out because of the viewer's role;
- methodology (definitions, tests, thresholds) and notes.

A blank cell always means "no data", never zero. Names appear in reports; contact details don't.

**Formats** (`ReportRenderer`), with no new dependencies:
- **PDF:** XHTML rendered with the existing openhtmltopdf. Page numbers, repeating table headers,
  and landscape for wide tables. Symbols the built-in Helvetica lacks are swapped for plain text.
- **Excel (.xlsx):** OOXML written directly. A Summary sheet, then one sheet per table with typed
  numeric cells (money, whole numbers and one decimal), a frozen header row and column widths.
- **CSV:** UTF-8 with a BOM. Cells beginning with = + - @ are defused against formula injection;
  numbers are written as numbers, including negatives.

**Permissions:**
- **Downloading** needs `REPORTS_EXPORT` (admins, finance admins and market analysts) plus each
  report's domains. Revenue needs finance; investment needs market and finance; the daily report
  filters its own sections by role.
- **Recording:** every download is audited (`REPORT_EXPORTED`) and recorded in `report_runs`.

**Scheduled delivery** (`report_schedules`, `ReportScheduler` every 10 minutes):
- Managing schedules needs `REPORTS_SCHEDULE` (admins).
- Each delivery covers the period that has just ended and is sent at 06:00 Harare after it.
- A schedule runs with its owner's permissions; whoever last edited it becomes the owner.
- **Recipients must be staff accounts allowed to see that report.** Arbitrary email addresses
  are not accepted. Both the owner and the recipients are checked again at every delivery, so a
  revoked role stops the email (the run is recorded as SKIPPED).
- Due schedules are claimed with a conditional update, so two servers can't send the same report.
- Attachments go out through the new `EmailService.sendWithAttachment`. Over 10 MB, the email
  links to the dashboard instead.
- "Send now", and a run history of outcome, recipient count and errors.
- Create, change and delete are audited.

**Saved views** (`analytics_saved_views`):
- Named filter sets per dashboard page, personal by default and optionally shared with other
  staff. Shared views can be applied by others, but only changed by their owner.
- Filters must be flat values (at most 30 keys, 2,000 characters) and the page must be a known
  one. At most 100 views per person.
- A **Saved views** menu is wired into the rental and sales dashboards (all eight filters).

**API** (under `/api/v1/admin/analytics`):
- `GET /reports` (the catalogue for this role, with default periods);
- `GET /reports/{type}?format=PDF|XLSX|CSV&from&to&city&suburb&category`;
- `GET /reports/runs`;
- `GET|POST /report-schedules`, `PUT|DELETE /report-schedules/{id}`,
  `POST /report-schedules/{id}/send-now`;
- `GET /report-recipients?type=`;
- `GET|POST /saved-views?page=`, `PUT|DELETE /saved-views/{id}`.

**Frontend.** A **Reports** tab with:
- the report picker;
- dates (single day for the daily report), city, suburb and category;
- PDF, Excel and CSV download buttons;
- a scheduled-delivery manager: report, frequency with plain-English timing, format, filters,
  and recipients chosen only from eligible staff; pause, send now, edit and delete;
- the recent reports list.

**Tests.**
- **`ReportRendererTest` (4):**
  - CSV context, the formula guard, and negatives not defused;
  - the .xlsx parts are well-formed XML with numeric cells, escaping and a frozen header;
  - the PDF text extracts back with money formatting, notes and page numbers;
  - cell references, display formats and file names.
- **`ReportTests` (7):**
  - the catalogue per role: admin 10; analyst without finance reports; finance gets daily and
    revenue; operations and tenants get 403;
  - every report as CSV, and four as PDF and Excel, all carrying coverage and methodology;
    downloads audited and recorded;
  - validation of format, dates, range, category and unknown types;
  - agent attribution, with 3 enquiries counted and no contact details;
  - schedules: recipients restricted; next run on Monday at 06:00; one delivery when two servers
    race; a PDF attached; nothing sent after the recipient loses the role;
  - the delivery period maths;
  - saved views: personal, shared, owner-only changes, flat filters only.

**Verification** (2026-11-26, Phases G and H together).
- **Setup:** a scratch MariaDB database with 38 rental listings across 7 suburbs and 538 real
  enquiries over September to November, rebuilt by the engine from source.
- **End-to-end result:** Avondale rental enquiries 10 → 38 (+280%, q = 0.0014) and Hatfield
  28 → 9 (weekly) and 70 → 35 (monthly) were reported. A DEMAND_SURGE alert reached the 3
  eligible staff, and the bell showed it. Of the 23 comparisons tested, the rest were correctly
  held back.
- **UI check:** with no API key locally, one interpretation was inserted by hand (labelled as
  such) to check the AI card.
- **Browser check** (Insights, Reports and saved views): no console or API errors. PDF, Excel
  and CSV downloads worked, and a saved view was created.
- **Two bugs found and fixed:**
  - CORS didn't expose `Content-Disposition`, so every download was named `estatex-report.*`.
  - With no API key, the AI card wrongly said "the latest attempt failed".
- **Not verified:** a live Claude call (no key locally; the request is unit-checked to build
  with its structured-output schema), real SMTP delivery (tests capture the email), and Postgres.

**Open items for the owner:**
- Set `ANTHROPIC_API_KEY` (and optionally `ANALYTICS_AI_MODEL`) for AI interpretations.
- Configure SMTP (`spring.mail.*`) for alert and report emails.
- Decide on the self-confirmed billing charges found in Phase F.


### Completion of open items (2026-11-27)

Done after Phase H, at the owner's request ("finish all these"):
- **Billing (owner decision: charge properly).** Listing fees and plan renewals are real bills
  (`PaymentPurpose.LISTING_FEE`, renewal `SUBSCRIPTION` payments) paid through Paynow, card or the
  wallet. A new private listing stays a draft (`AWAITING_PAYMENT`) until its first month is paid;
  renewals go `PAST_DUE` with 3 days' grace, then the listing is deactivated or the plan drops to
  Starter, and paying restores it. Owners can't switch an unpaid listing on (402). "Pay now" on the
  new-listing screen, the edit page, Payments and Subscription. Older self-confirmed charges stay
  reported as "charges recorded without payment". V11 widens `property_billings.status`.
- **Enquiry response time.** `enquiries.first_responded_at` is set when an agency first moves an
  enquiry on from NEW; Customer Demand shows "Enquiries first answered" (from 2026-11-27 on).
- **Floor area.** Agency listings take an optional floor area, and a saved inspection's floor area
  fills in the listing's when it has none, so more listings count towards price per m².
- **Map.** Market Map has Circles / Areas / 3D views. Areas are approximate (Voronoi cells around
  suburb centres, capped at 2 km) and labelled so; a GeoJSON file at
  `frontend/public/geo/suburb-boundaries.geojson` (features with `name` or `suburb`) replaces them
  with real boundaries automatically. 3D uses MapLibre GL 4.7.1 (height + colour = the measure).
- **Map pins were rounded to ~1 km** (QA finding F-6): `properties.latitude/longitude` now keep 7
  decimals (V12). Values saved before can't be recovered.
- **Postgres.** First run on Postgres 16 found V9 failing on a fresh database (F-8); fixed before
  it ever ran on Render. Full QA suite: 60/60 on Postgres and on MariaDB.
- **Email.** Checked end to end with Mailpit (local SMTP catcher): scheduled report with its PDF
  and sign-in alerts arrive. A real SMTP server is still to be configured on Render.
- **AI interpretation** is still only tested with a stub (no API key locally).

**Still open for the owner:** set `ANTHROPIC_API_KEY` and SMTP (`SMTP_HOST`, …) on Render; real
Paynow/Stripe keys so listing fees can be paid online; optionally a suburb boundary GeoJSON.

# Module 21: Admin Analytics, Market Intelligence and Hourly Engine (plan)

Status: **Phases A and B done (2026-11-26); Phase C (executive overview) next, after your review.** Branch: `feature/admin-analytics`.
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
| **G. Insights and alerts** | Deterministic observations (Poisson or z-tests with minimum n); Claude interpretations limited to those observations; alert rules, thresholds, cooldowns and preferences | No insight is produced when n is below the minimum. If AI is turned off, observations are still shown. |
| **H. Reports** | CSV, Excel and PDF reports with dates, coverage and method notes; scheduled email delivery; saved views | Tests cover report generation and the coverage notes |

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


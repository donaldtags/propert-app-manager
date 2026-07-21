# PrimeNest Admin Portal — Command Center Vision

This is the product/technical north star for PrimeNest's Admin Portal, captured verbatim from
the founding master prompt so it persists across sessions and can be referenced when planning
or reviewing work. It is intentionally aspirational and far larger than any single build — treat
it as the destination, not a sprint backlog. See `docs/ADMIN_COMMAND_CENTER_ROADMAP.md` (once
created) for the phased, buildable plan against the current codebase.

## Premise

PrimeNest is not a property listing website. It is the operating system for the real estate
industry in Zimbabwe first, and later Africa. The Admin Portal is a **Real Estate Command
Center** — an airport-control-tower view from which administrators can monitor, control,
automate, and improve every activity on the platform, across every participant in the real
estate ecosystem (landlords, tenants, agents, property managers, investors, developers, lawyers,
maintenance companies, banks, and PrimeNest ops).

Product philosophy: not a CRUD dashboard, not an admin panel that only edits data. Every screen
must answer "what problem does this solve?", every feature must reduce friction, every workflow
must save time, every page should encourage daily return. Focus areas: Trust, Automation,
Transparency, Analytics, Security, AI, Scalability.

## Admin role

The administrator is the super user with access to every workspace, and must be able to switch
between: Admin, Tenant, Landlord, Agent, Property Manager, Diaspora User, Investor, Customer
Support, Vendor, Lawyer, Developer. Support a "View As" mode for troubleshooting another user's
experience, with confirmation + audit logging on any privileged action taken while impersonating.

## Command centers

- **Global Command Center** — executive dashboard: today's/monthly revenue, escrow balance and
  active escrows, properties listed/sold/rented, active leases, occupancy rate, maintenance jobs,
  support tickets, investment portfolio, platform health, fraud alerts, system notifications,
  pending tasks, verification queue, disputes, KYC queue, activity maps, charts, live analytics,
  recent activity feed, calendar/upcoming events, AI recommendations, quick actions — real-time.
- **Property Command Center** — verification, duplicate detection, property passport, ownership
  history/timeline, inspection & maintenance history, market valuation, analytics, documents,
  media library, nearby amenities, trust score, occupancy history, rental yield, energy/water/
  solar/insurance/legal status, future development potential.
- **User Command Center** — every user type, each profile showing verification, trust score,
  activity timeline, payments, escrows, applications, documents, properties, leases, maintenance,
  messages, reviews, notes, AI risk score, relationship history.
- **Escrow Command Center** — full lifecycle (pending/funded/released/refunded/disputed/
  cancelled) with timeline, payment history, lease/property links, evidence, documents, photos,
  videos, messages, decision log, AI risk analysis.
- **Lease Command Center** — generate, digitally sign, renew, terminate, archive; document vault;
  timeline; reminders; renewal suggestions; compliance status.
- **Payment Command Center** — rent, deposits, escrow, refunds, service payments, investment
  payments, commissions, statements, invoices, receipts, exports, reconciliation.
- **Property Management Command Center** — buildings, units, owners, tenants, vacancies,
  inspections, maintenance, expenses, income, reports, asset register, inventory, vendor
  contracts.
- **Maintenance Command Center** — a maintenance marketplace (contractors, electricians,
  plumbers, painters, gardeners, movers, security, ISPs, cleaners, furniture suppliers), each
  vendor rated with availability, pricing, completed jobs, trust score, insurance, performance.
- **Investment Command Center** — portfolio overview, marketplace (REITs, fractional, commercial,
  residential, development projects), certificates, statements, dividend history, projected
  returns, risk analysis, market insights, AI investment advisor, performance charts, tax
  reports.
- **Diaspora Command Center** — remote buying/renting/management, family property monitoring,
  escrow, video inspections, lawyer coordination, utility setup, furniture installation,
  investment tracking, document vault.
- **Fraud & Trust Center** — a trust engine monitoring duplicate listings, fake users/IDs,
  multiple accounts, payment anomalies, document fraud, AI fraud detection, risk alerts,
  suspicious behavior, trust scores.
- **AI Command Center** — continuous monitoring: missing photos, duplicate listings, leases
  expiring soon, high-risk tenants, late rent, declining property performance, market trends,
  pricing suggestions, investment opportunities, maintenance predictions, churn prediction,
  revenue forecasting.
- **Business Intelligence** — revenue, growth, occupancy/vacancy, average rent/sale price,
  demand, investment performance, maintenance costs, satisfaction, conversion, retention, LTV,
  revenue by city/agent/property/landlord, forecasts.
- **Automation Center** — admin-authored workflows, e.g. lease expiry → notify tenant → notify
  landlord → generate renewal → book inspection → update dashboard; maintenance request → assign
  vendor → notify tenant → track progress → collect feedback; payment overdue → notify tenant →
  notify landlord → create reminder → escalate if unpaid.
- **Communication Center** — unified messages, email, SMS, push, announcements, support tickets,
  broadcast messages, (future) video meetings and voice calls.
- **Document Vault** — IDs, passports, leases, receipts, contracts, ownership papers, inspection
  reports, insurance, valuations, investment certificates, tax documents — every document
  versioned with an audit trail.

## Identity Verification & Trust Center (KYC)

Multi-step KYC required before high-trust actions (listing a property, applying for a property,
receiving escrow funds, acting as an agent, participating in investments). Public browsing stays
open to unverified users.

Workflow:
1. **Personal information** — full legal name, DOB, nationality, residential address, mobile,
   email.
2. **Government ID** — National ID front/back, or passport, or driver's licence. Auto-detect
   blurry/cropped/expired/duplicate uploads with real-time quality guidance.
3. **Selfie verification** — live selfie, plus selfie holding ID beside face. Auto-check face
   match, eyes visible, unobstructed, no sunglasses/masks, good lighting.
4. **Liveness detection** — random prompted actions (blink, turn head left/right, smile) to
   defeat photo/screen spoofing.
5. **Address verification (future)** — utility bill / bank statement / government letter.
6. **Property ownership verification** — title deed, lease agreement, power of attorney, or
   management authorization for landlords; if ownership can't be fully confirmed, show the
   partial verification level rather than a false "fully verified" badge.

Verification levels (visible trust badges): 0 Guest (browse only) → 1 Email Verified → 2 Phone
Verified → 3 Identity Verified (govt ID approved) → 4 Face Verified (identity + selfie) → 5
Trusted User (platform history).

Trust Score (e.g. 94/100) factors: identity verification completion, payment reliability, lease
history, escrow history, dispute history, review quality, account age, profile completeness,
document verification, platform activity. Always explain the score and give a path to improve it
— never use it to silently exclude someone.

Property Trust Score factors: verified owner, inspection completed, accurate location,
maintenance history, complete documentation, escrow enabled, positive tenant feedback.

Ecosystem-level verification badges (people, businesses, *and* properties):
- Verified Individual — identity checked.
- Verified Landlord — identity + ownership/management authority reviewed.
- Verified Agent — identity + agency affiliation + licence (where applicable) confirmed.
- Verified Property Manager — identity + company verification.
- Verified Vendor — identity + business registration + service verification.
- Verified Property — ownership/management authority, location, required documentation reviewed.

**Admin Verification Center**: reviewers see user profile, ID front/back, selfie, selfie+ID,
verification history, device info (where appropriate), submission timestamps, notes, AI quality
assessment. Actions: approve, reject, request more info, escalate, suspend (with due process).
Every decision is recorded in an immutable audit log.

**Fraud detection** should flag: duplicate identity documents, multiple accounts on the same ID,
reused selfies, frequent failed verification attempts, shared device fingerprints (where
permitted), rapid identity-info changes, repeated payment disputes, duplicate listings.

**Privacy & security**: encrypt sensitive documents at rest and in transit, role-based access to
verification documents, log all access to identity records, support user-requested deletion
where legally permissible, and show clear consent notices on data usage/retention.

## Product goal

Identity verification — and the Command Center as a whole — should feel simple, secure, and
trustworthy. The goal isn't just verifying identity; it's building confidence that every
participant on PrimeNest is a real person or business that has completed an appropriate level of
verification. Trust is meant to be one of PrimeNest's strongest competitive advantages.
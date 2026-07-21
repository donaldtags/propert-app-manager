# PrimeNest Tenant HomeHub — Product Specification

Product specification for the tenant-facing side of PrimeNest, captured from the founding
master prompt so it persists across sessions. This is the destination, not a sprint backlog —
see the relevant phase plan (created per build) for what's actually buildable against the
current codebase at any given time.

## Positioning

PrimeNest is not a listing app — it is the operating system for real estate. Most rental
platforms stop at "search property → contact landlord → rent → leave." PrimeNest must own the
**complete tenant lifecycle**: finding a home → applying → verification → signing lease →
moving in → paying rent → reporting issues → building rental reputation → renewing lease →
moving → eventually becoming a homeowner or investor. Every feature must solve a real tenant
problem, not just mirror a competitor's feature list. The experience should feel modern, safe,
personalized, intelligent, transparent, simple, and trustworthy — a personal housing operating
system a tenant opens daily, not a CRUD dashboard.

## Tenant HomeHub Dashboard

On login: a personalized command center. **Home Overview** — current property (photo, address),
landlord, property manager, lease status, rent amount, next payment date, deposit status, trust
score. **Quick actions**: pay rent, view lease, report maintenance, message landlord, find a new
home, request inspection, download documents.

## Personal Tenant Profile — "Tenant Passport"

A portable rental identity: profile picture, identity/phone/email verification status, trust
score, rental history, payment reliability, previous properties, references, reviews, documents,
lease history, dispute history, maintenance history.

## Tenant Trust Score

A transparent reputation system computed from: identity verification, payment history, lease
completion, communication behavior, maintenance responsibility, reviews, disputes, account
security, profile completeness. Always explain *why* the score is what it is (e.g. "Excellent
payment history, verified identity, no disputes, completed previous leases") and give the tenant
a path to improve it.

## Property Discovery Experience

Not just listings — help tenants find the *right* home. AI natural-language search (e.g. "I need
a 2 bedroom apartment in Harare close to CBD with solar and parking under $700") that recommends
suitable properties. Advanced filters: location, price, bedrooms, bathrooms, property type,
furnished, solar, backup power, borehole, internet/fibre, security, parking, pets, school
distance, workplace distance, public transport, verified-only, escrow-available.

## Property Comparison

Side-by-side comparison table: price, location, amenities, trust score, landlord rating,
security, power/water availability, transport access, distance, reviews.

## Property Details Experience

Images, videos, virtual tour, location, amenities, nearby services, property trust score,
landlord verification, inspection status, escrow availability, reviews, maintenance history,
property timeline, available documents. Actions: apply, book viewing, save, share, message.

## Application Management

Tenants track: submitted applications, status, required documents, verification progress,
landlord response, next steps. Statuses: Draft, Submitted, Verification Required, Under Review,
Approved, Rejected, Lease Preparation.

## Digital Lease Center

View/download lease, digital signature, an AI lease explainer, renewal/termination requests,
lease timeline, important dates, tenant/landlord responsibilities, automatic reminders (rent
dates, inspection dates, lease expiry, renewal dates).

## Rent Payment Center

Current rent, next payment, payment history, receipts, statements, late-payment history, escrow
deposits, payment methods. Support bank payments, mobile money, cards, and future providers.

## Escrow Deposit Protection

Deposit amount, escrow status, transaction history, protection status, release conditions,
dispute process. Timeline: Deposit Paid → Escrow Protected → Inspection Completed → Deposit
Released (or Dispute Started).

## Maintenance Center

Tenant creates a request (photos, videos, description, category: electrical/plumbing/security/
appliance/structural/other). Workflow: tenant submits → AI categorizes urgency → landlord
notified → vendor assigned → repair tracked → tenant confirms completion → feedback submitted.

## Landlord Relationship Profile

Transparency in both directions: landlord name, verification status, trust score, response time,
rating, number of properties, communication history.

## Messaging Center

Secure tenant↔landlord and tenant↔property-manager chat, maintenance conversations, document
sharing, notifications, read receipts, attachments.

## Home Timeline

A history of the tenant's home: moved in → deposit protected → first rent paid → maintenance
completed → inspection completed → lease renewed. Stores payments, maintenance, inspections,
documents, messages.

## AI Home Assistant

Available everywhere, aware of the tenant's profile, lease, property, payments, maintenance, and
documents. Examples: "When is my rent due?" → "The 5th of every month." "Explain my lease" → a
plain-language summary. "My landlord hasn't fixed my issue" → reviews maintenance history and
suggests next steps.

## Moving Assistant

Don't lose tenants when they leave — help them find another property, arrange moving services,
transfer documents and rental history, set up utilities, update their address.

## Future Home Ownership Journey

Turn tenants into future buyers/investors: show rental history, trust score, estimated
affordability, investment opportunities, a homeownership pathway. Connect to REITs, fractional
ownership, mortgage partners, and property investments.

## Tenant Services Marketplace

Access to moving companies, cleaning services, ISPs, security services, furniture providers,
insurance providers, home improvement services — PrimeNest can earn referral commissions.

## Tenant Notification System

Intelligent notifications: rent reminders, lease expiry, maintenance updates, new matching
properties, investment opportunities, market changes, security alerts.

## Sequencing note

The author's own recommendation: after the tenant module, build **Property Manager** next, since
it's the piece that connects tenants and landlords operationally and turns PrimeNest from an app
into a real estate company.

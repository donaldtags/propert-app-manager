# Homestead — Features, How It Works, and User Guide

Homestead is a property platform for Zimbabwe (with diaspora buyers/tenants as a core
audience) that connects tenants, landlords, agents, developers, and investors around one
thing most local property sites don't offer: **trust**. Verified people, verified listings,
escrow-protected money, and digital paperwork you don't have to chase down.

---

## 1. What problems Homestead solves

| Problem | How Homestead solves it |
|---|---|
| Rental/sale scams — fake listings, fake landlords, disappearing deposits | Identity verification (KYC), property verification, a computed **Trust Score** per user, and escrow so no deposit or rent goes directly into a stranger's pocket |
| Diaspora buyers/landlords can't manage property from abroad | "Diaspora Friendly" listings, remote-manageable properties, in-app messaging, an AI property assistant, and everything else (leases, payments, maintenance) usable without being in the country |
| Paper-based, error-prone lease and sale paperwork | Digital leases with e-signatures from both sides, official Homestead-branded legal templates (Rental Application, Agreement of Sale, Mandate to Sell), and AI that reads an existing lease document and pre-fills the form for you |
| No way to judge who you're dealing with | Trust Score (see §6) built from real activity — verification, escrow history, completed leases, ratings — shown on profiles and on every listing's contact card |
| Rent/deposit payment friction and disputes | Escrow-backed payments, a rent/payment history per lease, and maintenance requests tracked against the property |
| Small investors can't access property-backed investment | A REIT/property investment marketplace with unit purchases and portfolio tracking |
| Agents/property managers juggling many owners and properties | One account, multiple roles, role-specific dashboards, and the ability for an agent to act on a property (create/sign leases, list for sale) without owning it themselves |

---

## 2. How it works, in brief

- **Frontend**: Next.js app (`/frontend`) — public property search/browse needs no login;
  an account is only required to apply, message, pay, sign, upload documents, list a
  property, or invest.
- **Backend**: Spring Boot API (`/src`) backed by MariaDB — the source of truth for every
  workflow below. No mock data in the running app.
- **One account, many roles**: a single login can hold TENANT, LANDLORD, AGENT, DEVELOPER,
  PRIVATE, DIASPORA, INVESTOR, and/or ADMIN roles at once. Adding a role happens from the
  dashboard (password-confirmed); admin access is never self-service — it's requested, then
  approved by an existing admin.
- **AI assistant**: an optional Claude-powered layer (falls back to plain keyword search if
  no API key is configured) used for natural-language property search and for reading
  uploaded lease documents to pre-fill a new lease.

---

## 3. User manual, by role

### Anyone (no account needed)
- Browse and search every verified listing — filter by listing type (Rent / Sale / Short
  Stay), city, suburb, price range, minimum bedrooms/bathrooms, and diaspora-friendly.
- Open a listing to see the full photo gallery, price, description, map, and the
  landlord/agent's Trust Score.
- Contact the landlord/agent directly from the listing (name/email/message) — no login
  required.
- Use the AI Search page to describe what you want in plain language ("2 bed in Avondale
  under $500") instead of using the filter panel.

### Tenant
1. Register (or add the TENANT role to an existing account).
2. Search and shortlist properties; save listings with the heart icon.
3. Fill out the **Rental Application Form** for the property you want (see §5) — a landlord
   or agent reviews and decides.
4. Once approved, the landlord/agent creates a digital **Lease** for you. Sign it in the
   Leases tab.
5. Pay rent through the platform, linked to your lease, protected by escrow.
6. Raise maintenance requests against your leased property (with photos).
7. Rate your landlord once you have an active/ended lease with them — this feeds their
   Trust Score.

### Landlord / Private seller / Developer
1. Register (or add the LANDLORD/PRIVATE/DEVELOPER role).
2. List a property: fill in the details, upload photos (real upload button — device photos
   or paste an image URL), set escrow/diaspora-friendly flags.
3. An agent or admin verifies the listing, giving it a "Verified" badge.
4. Review applications and messages; create a lease for an approved tenant, optionally
   auto-filling it from an existing lease document (§5).
5. Track rent payments, escrow balance, and maintenance requests from your Landlord
   Dashboard.
6. For a **sale** listing, download the **Agreement of Sale** template from the property
   page to use with your purchaser and conveyancer.

### Agent
Everything a landlord can do, plus:
- Can be attached to a property as its **representing agent** (`agentId`) without being the
  registered owner — the owner stays the legal landlord on the lease, but the agent can
  **create the lease, upload/review its documents, and sign on the landlord's behalf**.
- Download the **Mandate to Sell** template to get formal authorization from an owner
  before marketing their property for sale.
- Shows up in "My Leases" for every property they represent, not just properties they
  personally own.

### Investor
- Browse available REITs/property investment products (`/investments`), see projected
  yield and risk level, and purchase units.
- Track your portfolio and exit positions from your dashboard.

### Diaspora
- Use the "Diaspora Friendly" filter to find remotely-manageable properties.
- Everything else (leases, payments, escrow, maintenance, AI assistant) works the same —
  designed so you never need to be physically present.

### Admin
- Verify user identities and properties.
- Approve or reject admin-access requests (admin is never self-granted).
- Review KYC submissions, escrow disputes, and flagged messages.
- See platform-wide dashboards (active leases, escrow balances, occupancy, etc.).

---

## 4. Trust Score

Every user has a **Trust Score (0–100)**, computed from real activity — not a fixed
number. It factors in:

- Identity verified (+20), face verified (+15), phone on file (+5)
- Approved KYC submission (+10)
- Account age (+5 at 90 days, +5 more at 1 year)
- At least one escrow transaction released successfully (+10); an escrow that went to
  **dispute** is a penalty (-20)
- At least one completed lease — as tenant, landlord, *or* representing agent (+10, +5 more
  at 3+)
- Average landlord rating, if any (up to +15)

The score is recomputed whenever a profile is viewed and immediately after a lease is
signed, so it reflects what actually happened on the platform, not just what someone
typed into their profile. It's shown on the user's own profile page and on every
listing's contact card (for the landlord or agent shown).

---

## 5. Leasing tools

Homestead ships three official, Homestead-branded legal document templates (based on
Zimbabwean property law and referencing Homestead's own escrow/verification/dispute
processes) — all reviewed by a legal practitioner before use is still required, since
these are templates, not legal advice.

| Template | Used for | Where to get it |
|---|---|---|
| **Rental Application Form** | A prospective tenant applies for a rental listing (identity, employment/income, rental history, references, consent to verification) | Leases page → Attachments → "Download blank Rental Application Form" |
| **Lease Form** (digital) | The actual tenancy agreement — created in-app, not a static document | Leases page → "Create New Lease" |
| **Agreement of Sale** | The contract between a Seller and Purchaser for a property sale, with escrow-held deposit, suspensive conditions, and conveyancer handoff | Property detail page (Sale listings only) → "Legal Documents" |
| **Mandate to Sell** | Authorizes an agent to market/sell a property on behalf of the owner, before a buyer is found | Property detail page (Sale listings only) → "Legal Documents" |

### Creating a lease
Landlords and the property's representing agent can create a lease for an approved
tenant: pick the property and tenant, set the term, rent, and deposit. Both sides sign
digitally in-app (the agent can sign for the landlord's side).

### AI auto-fill from an existing document
Already have a signed lease as a PDF, a phone-camera scan (PNG/JPEG), or a Word document?
On the "Create New Lease" form, upload it under **"Auto-fill from an existing lease
document"** — Homestead's AI reads the file and pre-fills the start/end date, monthly
rent, and deposit, and shows you the tenant name/address it found in the document (for
you to confirm — it never invents a value, and it never auto-selects a platform user or
property for you). This needs the AI assistant to be configured on the server
(`ANTHROPIC_API_KEY`); without it, the form works exactly the same, just manually.

### Document review
Every document attached to a lease (application, lease form, payslip, proof of
employment, bank statement) can be approved or rejected by the landlord, the property's
representing agent, or an admin — with the decision and reviewer recorded.

---

## 6. What's next (not yet built)

The current build focuses on the trust/paperwork/payment core above. Longer-range ideas
that were scoped but not built in this pass — a Property/Tenant/Landlord "Passport" with
full history, a home-services marketplace (movers, insurance, utilities), an AI fraud
engine, and neighbourhood intelligence — are worth revisiting once the core loop above is
proven out with real users, rather than building all of them shallowly at once.

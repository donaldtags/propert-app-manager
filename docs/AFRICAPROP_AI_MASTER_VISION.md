# AfricaProp AI / "HomeTrust Africa" — Master Product Vision

Overarching north-star vision for the platform, captured verbatim from the founding pitch so it
persists across sessions. This sits above the module-specific specs already saved in this
directory (`ADMIN_COMMAND_CENTER_VISION.md`, `LANDLORD_WORKSPACE_VISION.md`,
`TENANT_HOMEHUB_VISION.md`) — those describe individual workspaces; this describes the company.

## Vision

The first AI-powered African real estate super app, combining property rentals, property sales,
escrow payments, diaspora property management, REIT investing, verified agents, smart contracts,
tenant screening, maintenance management, an AI property assistant, VEX/tokenized property
investments, credit scoring, a mortgage marketplace, utility payments, and Airbnb-style short
stays — starting in Zimbabwe, then scaling across Africa.

## Problem

African real estate is fragmented and high-trust-cost: fake listings, scams, disappearing agents,
lost deposits, poor tenant screening, no trusted escrow, diaspora victims, no transparent property
history, cash-only systems, poor maintenance communication, no digital leases, no centralized
investment access, no AI assistance, hard mortgage access, no verified-ownership visibility. The
platform's job is to solve trust, payments, verification, and management.

## Core concept

Tenants find verified rentals; landlords manage properties; diaspora users safely rent/buy
remotely; payments run through escrow; investors buy into REITs and property fractions; AI helps
search, negotiate, and manage; agents operate professionally; maintenance is automated. Airbnb +
Property24 + Zillow + PayPal Escrow + ChatGPT + EasyEquities, African-first.

## User types and capabilities

- **Tenants** — search rentals, verify legitimacy, pay deposits safely, sign leases online, report
  maintenance, pay rent online, track lease status, rate landlords, use an AI assistant.
- **Landlords** — list properties, receive rent, screen tenants, automate reminders, manage
  maintenance, track finances, manage leases, use AI analytics.
- **Diaspora users** — rent for relatives, buy remotely, escrow protection, monitor property
  progress, pay utilities remotely, invest in REITs, manage family housing.
- **Agents** — manage listings, verify properties, earn commissions, onboard clients, manage
  tours, chat with leads, advertise premium listings.
- **Investors** — invest into REITs, buy property fractions, view ROI analytics, invest in
  developments, track dividends.

## The core differentiator: Escrow + Verification + AI — "the trust layer"

**Escrow**: the platform holds funds until the tenant confirms the property, the landlord
confirms the agreement, and the lease is signed — only then are funds released. Especially
valuable for diaspora users who can't inspect in person.

**AI features**:
- AI Property Assistant — natural-language search ("find me a 2-bedroom apartment in Borrowdale
  under $600 near schools") with intelligent responses.
- AI Scam Detection — suspicious pricing, duplicate listings, fake images, risky landlords,
  unusual payment patterns.
- AI Pricing Engine — fair rental prices, valuations, investment potential.
- AI Tenant Scoring — payment history, employment, rental behavior, references.
- AI Investment Advisor — REITs, high-yield locations, risk, projected ROI.

## REIT + VEX integration

A REIT marketplace where users buy shares in property portfolios and receive dividends. Target:
diaspora investors, young professionals, SMEs. Longer-term: integrate Zimbabwe Stock Exchange
products, Victoria Falls Exchange opportunities, and tokenized real estate — fractional ownership
where e.g. $50 buys a portion of a commercial property, democratizing African real estate
investing.

## Virality features

Verified Listings Badge (physically verified by agents/lawyers/inspectors), Property History
(prices, occupancy, disputes, maintenance), Smart Digital Lease (legally structured, generated
digitally), Diaspora Mode (USD payments, remote inspections, trusted verification, local
representative access), Maintenance Marketplace (plumbers/electricians/cleaners/painters,
instant landlord requests), Utility Payments (ZESA, water, internet, levies inside the app), AI
WhatsApp Assistant (property search over WhatsApp).

## Revenue model

Monthly subscriptions (landlords/agencies/property managers), escrow fees (1–3% per transaction),
premium/boosted listings, REIT commissions, property-management SaaS pricing, mortgage/referral
commissions (bank partnerships), maintenance-marketplace commissions.

## Proposed tech stack (as pitched — see current-state note below)

Backend: Spring Boot, PostgreSQL, Redis, Elasticsearch, Docker, Kafka later. Frontend: Next.js.
Mobile: Flutter. AI: Python FastAPI, OpenAI APIs, local AI later, vector database for property
search. Payments: Paynow, Stripe, EcoCash, InnBucks, Mukuru later. Cloud: AWS, Azure, Cloudflare.

## Suggested tables

users, properties, landlords, agents, tenants, leases, escrow_transactions,
maintenance_requests, utility_payments, reits, investments, property_history, property_media,
chats, ai_requests, payments, disputes, reviews.

## MVP phasing (as pitched)

- **Phase 1**: verified listings, escrow, rent payments, landlord dashboard, tenant dashboard.
- **Phase 2**: AI property assistant, WhatsApp integration, digital leases, maintenance system.
- **Phase 3**: diaspora mode, REIT investing, VEX integration, AI pricing, tokenization.

## Naming

Considered: CasaAfrica, PropFlow Africa, Nyumbani AI, Homeland Africa, TerraVest AI, EstateFlow,
DiasporaHomes, AfriNest, HomeTrust Africa, ZimNest, Vesta Africa. Pitched recommendation:
**HomeTrust Africa** (the product is fundamentally about trust) — no rename has been made; the
codebase and all deployed artifacts are still named PrimeNest as of this writing.

## Positioning

"The trusted real estate operating system for Africa" / "Africa's AI-powered real estate super
app." Framed as building trust/payments/investment infrastructure for African real estate, not
just another listing app.

## Long-term vision

Mortgages, insurance, smart homes, legal verification, blockchain land records, AI-powered urban
analytics, cross-border African investments, construction financing, digital title systems.

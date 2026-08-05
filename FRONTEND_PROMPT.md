# AfricaProp AI Frontend Prompt

Build a Next.js TypeScript web app for AfricaProp AI that connects directly to the existing Spring Boot backend.

Use:

```text
NEXT_PUBLIC_API_BASE_URL=http://localhost:8081/api/v1
```

Allow switching to `8081` or `8082` from `.env.local` without code changes.

## Must-Have Product Feel

This is a web app first. Mobile will come later. Make the web experience polished, easy to use, dashboard-first, and trust-focused.

AfricaProp AI is the trust layer for African real estate:

- verified property listings
- escrow-protected deposits
- digital leases
- role-based dashboards
- diaspora-safe property management
- REIT/property investment marketplace
- AI property assistant
- maintenance and rent management

Do not build only a landing page. Build a working app experience.

## How The App Works Now

The backend is a Spring Boot API with real MariaDB persistence. The frontend should treat the backend as the source of truth for all data. Do not use seed data, hardcoded IDs, or mock API state for core workflows.

Core product logic:

- Anyone can browse and search public property listings without login.
- Users register or log in only when they need to apply, message, pay, sign, upload documents, list properties, manage records, or invest.
- One account can hold multiple roles. A tenant can later add landlord, diaspora, agent, or investor access from the dashboard.
- Role switching is not a separate login. It is one authenticated user with multiple roles and multiple workspaces.
- Adding a non-admin role requires password confirmation through the backend.
- Admin access is never self-service. Users can request it; an admin must approve it later.
- Monthly rent payments must pass through the platform and be linked to a lease.
- Tenants can rate landlords only in a lease-linked context.
- Users can contact each other in-app for general messages, references, maintenance, and payment queries.
- Lease documents are uploaded and reviewed through the lease document endpoints.
- The backend logs every API request. The frontend can also send user/event logs to `/logs/frontend`.

## Access Model

Use public browsing first and account creation later. Do not force login for basic discovery.

Public users can:

- browse listings
- search and filter properties
- view property details, photos, prices, locations, badges, and reviews
- use the AI property assistant
- view public agent profiles
- click WhatsApp/contact calls to action

Require login for:

- applying for a property
- booking or tracking a viewing
- paying deposits or rent
- creating escrow transactions
- signing leases
- direct in-app messaging
- saving permanent favorites
- submitting maintenance requests
- listing or managing properties
- managing tenants
- investing
- admin verification and dispute handling
- adding roles
- rating landlords
- uploading lease documents

When a public user clicks `Apply Now`, `Book Viewing`, or another protected action, show a quick signup/login step and then return them to the same workflow.

The main MVP flow is:

```text
Search -> Property Details -> Apply / Book Viewing -> Quick Signup -> Escrow Payment -> Digital Lease -> Tenant Dashboard
```

Every property-facing screen should answer:

- Can I trust this property?
- Can I safely pay?
- What happens next?

## Backend Communication

Create:

- `lib/api.ts`
- `lib/types.ts`
- `lib/auth.tsx`

The API client must:

- read `NEXT_PUBLIC_API_BASE_URL`
- support `GET`, `POST`, `PATCH`
- show proper loading, empty, and error states
- reuse the same backend endpoints exactly
- never hardcode API data or rely on seeded IDs
- create records through API forms/actions instead of assuming data already exists
- send `Authorization: Bearer ${token}` on all protected endpoints
- post important frontend events/errors to `POST /logs/frontend`

## Role-Based Profiles

Every logged-in user must have a clear profile.

Use:

- `GET /users/{id}`
- `PATCH /users/{id}/profile`
- `GET /users`
- `PATCH /users/{id}/verify`
- `POST /users/{id}/roles`
- `POST /users/{id}/admin-request`

User responses include:

- `id`
- `fullName`
- `email`
- `phone`
- `country`
- `city`
- `preferredCurrency`
- `avatarUrl`
- `bio`
- `diasporaLocation`
- `occupation`
- `companyName`
- `emergencyContactName`
- `emergencyContactPhone`
- `emailNotifications`
- `smsNotifications`
- `twoFactorEnabled`
- `identityVerified`
- `verified`
- `trustScore`
- `primaryProfile`
- `profileCompletion`
- `roles`

Create profile screens for:

- Tenant Profile
- Landlord Profile
- Diaspora Profile
- Agent Profile
- Investor Profile
- Admin Profile

Each profile should reuse the same backend user object but render role-specific sections.

Tenant profile:
- lease status
- rent payments
- maintenance requests
- escrow deposits
- trust score

Landlord profile:
- listed properties
- rent income
- active leases
- maintenance requests
- escrow releases

Diaspora profile:
- country abroad
- remote family/property management
- escrow-protected activity
- diaspora-ready properties
- USD payments
- investment shortcuts

Agent profile:
- verification status
- managed listings
- property verification tools
- tours/leads placeholders
- commission placeholder

Investor profile:
- REIT investments
- projected yield
- VEX eligibility
- portfolio summary

Admin profile:
- user verification tools
- property verification tools
- dispute and safety placeholders

## Role Transitions

Build the dashboard so users can move between roles easily without creating a new account.

Rules:

- Show current workspaces for every role in `user.roles`.
- Show an `Add Role` area for roles the user does not have.
- For `TENANT`, `LANDLORD`, `AGENT`, `DIASPORA`, and `INVESTOR`, ask the user to confirm their current password, then call:

```http
POST /users/{id}/roles
Authorization: Bearer {token}
```

```json
{
  "role": "LANDLORD",
  "password": "CurrentPassword123!"
}
```

- After success, call `GET /auth/me` or refresh the user object and show the new workspace immediately.
- For `ADMIN`, do not call the add-role endpoint. Call:

```http
POST /users/{id}/admin-request
Authorization: Bearer {token}
```

- Show admin requests as pending approval, not granted access.

## Login And Safety

Build UI for:

- `/login`
- `/register`
- `/forgot-password`
- `/profile`
- `/settings/security`

The backend auth endpoints exist now. Isolate auth calls in `lib/auth.tsx` so JWT/session behavior can be improved later without touching screens.

Use these auth endpoints:

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/logout`
- `GET /auth/me`
- `POST /auth/forgot-password`
- `POST /auth/reset-password`

`POST /auth/login` and `POST /auth/register` return:

```ts
{
  token: string;
  user: User;
}
```

Store the token in the auth provider, send it as `Authorization: Bearer ${token}` for `/auth/me` and `/auth/logout`, and keep all protected pages behind the auth state.

Forgot password flow:

- `/forgot-password` should first ask for email and call `POST /auth/forgot-password`.
- Always show a neutral success message: `If this email exists, a reset link has been sent.`
- The reset link uses a raw token. Submit it with a new password to `POST /auth/reset-password`.
- Password reset policy: 10+ characters, uppercase letter, number, and symbol.

Security UI must include:

- password strength validation
- confirm password
- logout
- protected routes
- profile menu
- role badges
- trust score
- verified status
- identity verification status
- two-factor placeholder
- account safety tips

Do not use seeded demo users. For development and demos, register users through `POST /auth/register`, then create properties, leases, escrows, payments, maintenance requests, and investments through their API endpoints.

## Required Routes

- `/login`
- `/register`
- `/forgot-password`
- `/`
- `/properties`
- `/properties/[id]`
- `/ai`
- `/tenant`
- `/landlord`
- `/diaspora`
- `/agent`
- `/investments`
- `/escrow`
- `/payments`
- `/leases`
- `/maintenance`
- `/messages`
- `/users`
- `/profile`
- `/settings/security`

## Existing Backend Endpoints

Properties:
- `GET /properties`
- `GET /properties/{id}`
- `POST /properties`
- `POST /properties/{id}/photos`
- `PATCH /properties/{id}/verify`

When landlords add pictures, send image URL arrays as `photoUrls`, `imageUrls`, or `photos` on `POST /properties`, or upload actual files afterward with multipart `POST /properties/{id}/photos` using form field `files`. Read images from `photoUrls` first, then fall back to `photos` or `imageUrls`.

Users and profiles:
- `GET /users`
- `GET /users/{id}`
- `POST /users`
- `PATCH /users/{id}/profile`
- `PATCH /users/{id}/verify`
- `POST /users/{id}/roles`
- `POST /users/{id}/admin-request`

Leases:
- `POST /leases`
- `PATCH /leases/{id}/sign`
- `GET /leases?tenantId={id}`
- `GET /leases?landlordId={id}`
- `POST /leases/{leaseId}/documents`
- `GET /leases/{leaseId}/documents`
- `GET /leases/{leaseId}/documents/{documentId}/download`
- `PATCH /leases/{leaseId}/documents/{documentId}/review`

Lease document upload:
- consumes `multipart/form-data`
- `files`: repeated file field
- `documentTypes`: repeated values matching file order
- allowed document types: `PAYSLIP`, `PROOF_OF_EMPLOYMENT`, `BANK_STATEMENT`
- allowed file types: PDF, PNG, JPEG
- max file size: 8MB per file
- requires bearer token

Escrow:
- `POST /escrows`
- `GET /escrows?userId={id}`
- `PATCH /escrows/{id}/fund`
- `PATCH /escrows/{id}/release`
- `PATCH /escrows/{id}/dispute`

Payments:
- `POST /payments`
- `GET /payments?userId={id}`
- `PATCH /payments/{id}/success`

Rent payment rule:
- For monthly rent linked to a lease, `payerId` must be the lease tenant.
- `payeeId` must be the lease landlord.
- `amount` must match `monthlyRent`.
- The frontend should not let tenants manually choose another payee for monthly rent.

Recommended rent flow:

```text
Tenant Dashboard -> Active Lease -> Pay Rent -> Confirm Amount -> Select Provider -> POST /payments
```

Maintenance:
- `POST /maintenance`
- `GET /maintenance?propertyId={id}`
- `PATCH /maintenance/{id}/status?status=RESOLVED`

Investments:
- `GET /investments/reits`
- `POST /investments/reits`
- `POST /investments`
- `GET /investments?investorId={id}`

Dashboards:
- `GET /dashboards/landlords/{landlordId}`
- `GET /dashboards/tenants/{tenantId}`

AI:
- `POST /ai/property-search`

Auth:
- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/logout`
- `GET /auth/me`
- `POST /auth/forgot-password`
- `POST /auth/reset-password`

Ratings:
- `POST /ratings`
- `GET /ratings?landlordId={id}`
- `GET /ratings/landlords/{landlordId}`

Messages:
- `GET /messages/conversations?userId={id}`
- `POST /messages/conversations`
- `GET /messages/conversations/{conversationId}`
- `POST /messages`
- `PATCH /messages/conversations/{conversationId}/read`

Frontend logs:
- `POST /logs/frontend`

## Landlord Ratings

Tenants should be able to rate landlords from a lease context, not as a random public action.

Rating payload:

```json
{
  "landlordId": 1,
  "tenantId": 2,
  "propertyId": 3,
  "leaseId": 4,
  "rating": 5,
  "comment": "Responsive landlord."
}
```

Frontend behavior:

- Show landlord ratings on landlord profile and property detail pages.
- Show a `Rate Landlord` action on tenant leases that are active, signed, completed, or ended.
- Use stars for rating input and display.
- Make clear ratings are tied to rental history and platform trust.

## In-App Messaging

Build `/messages` as a real protected workspace.

Message types:

- `GENERAL`
- `REFERENCE_REQUEST`
- `MAINTENANCE`
- `PAYMENT_QUERY`

Start conversation:

```json
{
  "recipientId": 1,
  "subject": "Reference request",
  "content": "Please confirm my rental history.",
  "messageType": "REFERENCE_REQUEST",
  "propertyId": 3
}
```

Frontend behavior:

- Let tenants, landlords, agents, and diaspora users contact each other in-app.
- Keep phone/email private unless users type it themselves.
- Reference requests should be clearly labeled.
- Maintenance and payment messages should be visually tagged.
- Use conversation list + thread UI.
- Mark messages read when the thread opens.

## Lease Documents

Build lease document upload and review into the lease flow.

Tenant side:

- Upload payslip, proof of employment, and bank statement.
- Show document status: `SUBMITTED`, `APPROVED`, `REJECTED`.
- Allow download of uploaded files.

Landlord/admin side:

- Review documents.
- Approve or reject with a review note.
- Do not expose document content publicly.

## Frontend Logging

Send useful frontend events to the backend:

```http
POST /logs/frontend
```

```json
{
  "level": "info",
  "event": "property_viewed",
  "route": "/properties/3",
  "userId": 12,
  "message": "User opened property details",
  "metadata": {
    "propertyId": 3
  }
}
```

Log:

- API errors
- failed form submissions
- successful payments
- lease signing
- document uploads
- role transitions
- important property actions

## UI Requirements

Use reusable components:

- `AppShell`
- `SidebarNav`
- `TopBar`
- `UserMenu`
- `ProtectedRoute`
- `ProfileCard`
- `ProfileForm`
- `SecurityPanel`
- `PropertyCard`
- `VerifiedBadge`
- `EscrowStatusBadge`
- `DiasporaBadge`
- `RoleBadge`
- `TrustScore`
- `ProfileCompletion`
- `StatCard`
- `EmptyState`
- `LoadingState`
- `ErrorState`
- `MoneyDisplay`
- `StatusBadge`
- `FormInput`
- `SelectInput`
- `Modal`
- `DashboardSection`
- `StarRating`
- `RatingForm`
- `ConversationList`
- `MessageThread`
- `RoleSwitcher`
- `LeaseDocumentUploader`

The final app must let me demo:

- account registration and login using records created through the API
- profile viewing/editing
- tenant, landlord, diaspora, agent, investor profiles
- property search
- AI property assistant
- property details
- lease creation/signing
- lease document upload and review
- escrow creation/funding/release
- rent payment
- landlord rating
- in-app reference request messaging
- secure role transition from tenant to landlord/diaspora/investor
- maintenance request
- REIT investing
- role-based dashboards

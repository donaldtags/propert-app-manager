# PrimeNest Prop API

Base URL: `http://localhost:8081/api/v1`

MariaDB is the default application database. For setup and verification commands, see `DATABASE.md`.

## Auth

- `POST /auth/register` create an account and return a session token plus user profile.
- `POST /auth/login` log in and return a session token plus user profile.
- `POST /auth/logout` invalidate the current bearer token.
- `GET /auth/me` fetch the current user from `Authorization: Bearer <token>`.
- `POST /auth/forgot-password` create a single-use reset token if the email exists.
- `POST /auth/reset-password` reset password with a valid raw token.

Auth sessions are stored in the database table `auth_sessions`. Registering or logging in creates a session row. Calling `/auth/me` looks up the bearer token in `auth_sessions`, then returns the linked user from `users`. Calling `/auth/logout` deletes the session row.

Password reset tokens are stored hashed in `password_reset_tokens`, expire after 30 minutes, and are marked used after reset. In development, the backend logs the reset URL.

Register example:

```json
{
  "fullName": "Nadia Ncube",
  "email": "nadia@example.com",
  "phone": "+263771000002",
  "password": "AfricaProp123!",
  "country": "Zimbabwe",
  "roles": ["TENANT"]
}
```

Login example:

```json
{
  "email": "nadia@example.com",
  "password": "AfricaProp123!"
}
```

## Users

- `POST /users` create a user.
- `GET /users?role=LANDLORD` list users, optionally filtered by role.
- `GET /users/{id}` fetch one user profile.
- `PATCH /users/{id}/profile` update profile fields.
- `PATCH /users/{id}/verify` mark a user as verified.
- `POST /users/{id}/roles` add a non-admin role to your own account. Requires bearer token and password confirmation.
- `POST /users/{id}/admin-request` request admin access. Requires bearer token. Does not grant admin automatically.

Example:

```json
{
  "fullName": "Tariro Moyo",
  "email": "tariro@example.com",
  "phone": "+263771234567",
  "country": "Zimbabwe",
  "roles": ["LANDLORD"]
}
```

Profile update example:

```json
{
  "fullName": "Nadia Ncube",
  "phone": "+263771000002",
  "country": "Zimbabwe",
  "city": "Harare",
  "preferredCurrency": "USD",
  "bio": "Tenant looking for verified rentals with escrow protection.",
  "occupation": "Software developer",
  "diasporaLocation": "United Kingdom",
  "emergencyContactName": "Tariro Ncube",
  "emergencyContactPhone": "+263772000000",
  "emailNotifications": true,
  "smsNotifications": true,
  "twoFactorEnabled": false
}
```

User responses include `primaryProfile`, `profileCompletion`, role badges, trust score, identity/verification status, and profile fields for tenant, landlord, agent, diaspora, and investor experiences.

Role add example:

```json
{
  "role": "LANDLORD",
  "password": "AfricaProp123!"
}
```

Allowed self-service roles: `TENANT`, `LANDLORD`, `AGENT`, `DIASPORA`, `INVESTOR`. `ADMIN` must go through admin request and approval.

## Properties

- `POST /properties` create a rental, sale, or short-stay listing.
- `GET /properties?listingType=RENT&city=Harare&suburb=Borrowdale&maxPrice=600&bedrooms=2` search available listings.
- `GET /properties/{id}` fetch one listing.
- `POST /properties/{id}/photos` upload one or more image files using multipart form field `files`.
- `PATCH /properties/{id}/verify` verify a listing using an agent or admin user.

Property create accepts `photoUrls`, `imageUrls`, or `photos` as arrays of image URL strings. Property responses include `photoUrls`, `imageUrls`, and `photos` with the saved image URLs so older and newer frontends can display the same pictures. Uploaded files are served from `/uploads/property-photos/...`.

Property responses include optional `latitude` and `longitude` for maps. If not supplied on create, common Zimbabwe suburb coordinates are filled automatically when known.

## Leases

- `POST /leases` create a digital lease.
- `PATCH /leases/{id}/sign` sign as tenant or landlord.
- `GET /leases?tenantId=2` or `GET /leases?landlordId=1` list leases.
- `POST /leases/{leaseId}/documents` upload tenant documents as multipart form data. Requires bearer token.
- `GET /leases/{leaseId}/documents` list document metadata. Requires bearer token.
- `GET /leases/{leaseId}/documents/{documentId}/download` download the stored file. Requires bearer token.
- `PATCH /leases/{leaseId}/documents/{documentId}/review` approve or reject a document as landlord/admin. Requires bearer token.

Lease document upload fields:

- `files`: repeated files.
- `documentTypes`: repeated values matching file order. Allowed values: `PAYSLIP`, `PROOF_OF_EMPLOYMENT`, `BANK_STATEMENT`.

Allowed uploads: `application/pdf`, `image/png`, `image/jpeg`, up to 8MB per file.

## Escrow

- `POST /escrows` create an escrow transaction.
- `PATCH /escrows/{id}/fund` mark funds as held.
- `PATCH /escrows/{id}/release` release funds after the linked lease is signed.
- `PATCH /escrows/{id}/dispute` dispute a funded escrow.
- `GET /escrows?userId=2` list escrows for a payer or beneficiary.

## Payments

- `POST /payments` create rent, utility, or manual payment record.
- `PATCH /payments/{id}/success` mark payment successful.
- `GET /payments?userId=2` list sent and received payments.

For monthly rent payments linked to a lease, the backend enforces:

- `payerId` must be the lease tenant.
- `payeeId` must be the lease landlord.
- `amount` must match the lease `monthlyRent`.

This keeps monthly rent passing through the platform record instead of being routed outside the system.

## Ratings

- `POST /ratings` create a landlord rating. Requires bearer token.
- `GET /ratings?landlordId=1` list ratings for a landlord.
- `GET /ratings/landlords/{landlordId}` alias for landlord ratings.

Rating example:

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

Ratings tied to a lease must match that lease's tenant and landlord.

## Messages

- `GET /messages/conversations?userId=2` list current user's conversations. Requires bearer token.
- `POST /messages/conversations` start a conversation. Requires bearer token.
- `GET /messages/conversations/{conversationId}` list messages in a conversation. Requires bearer token.
- `POST /messages` send a message in a conversation. Requires bearer token.
- `PATCH /messages/conversations/{conversationId}/read` mark messages as read. Requires bearer token.

Start conversation example:

```json
{
  "recipientId": 1,
  "subject": "Reference request",
  "content": "Please confirm my rental history.",
  "messageType": "REFERENCE_REQUEST",
  "propertyId": 3
}
```

Message types: `GENERAL`, `REFERENCE_REQUEST`, `MAINTENANCE`, `PAYMENT_QUERY`.

## Maintenance

- `POST /maintenance` open a maintenance request.
- `GET /maintenance?propertyId=1` list property maintenance.
- `PATCH /maintenance/{id}/status?status=RESOLVED` update status.

## Investments

- `POST /investments/reits` create a REIT product.
- `GET /investments/reits` list active REIT products.
- `POST /investments` buy REIT units.
- `GET /investments?investorId=4` list investor positions.

## Dashboards

- `GET /dashboards/landlords/{landlordId}`
- `GET /dashboards/tenants/{tenantId}`

## AI Assistant

- `POST /ai/property-search`

## Frontend Logs

- `POST /logs/frontend` records frontend events in backend logs.

Example:

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

Example:

```json
{
  "query": "Find me a 2-bedroom apartment in Borrowdale under $600 near schools"
}
```

Seed data is disabled by default. Create users, properties, leases, escrows, payments, maintenance requests, and investments through the API endpoints above. Do not make the frontend depend on seeded IDs or seeded emails.

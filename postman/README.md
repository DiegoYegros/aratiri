# Aratiri Postman Collection

This folder uses a single Postman collection with folders inside it.

That is the best fit here because it keeps import and sharing simple while still separating requests by domain:

- `00 Demo Flow`: quickest end-to-end path using seeded demo users.
- `Auth`: token lifecycle and current-user requests.
- `Accounts`: account lookups, balances, QR-bearing account payloads, and transactions.
- `Public & Decoder`: public LNURL endpoints plus authenticated decoder calls.
- `Invoices & LNURL Payments`: invoice generation and payment initiation flows.

## Files

- `Aratiri.local.postman_collection.json`
- `Aratiri.local.postman_environment.json`

## Assumptions

The collection is prefilled for the local setup used in this repo:

- API base URL: `http://localhost:2100`
- Seeded users from Flyway:
  - `alice@example.com` / `password123`
  - `bob@example.com` / `password123`
- Seeded account identities:
  - Alice account: `123e4567-e89b-12d3-a456-426614174000`
  - Bob account: `123e4567-e89b-12d3-a456-426614174001`

## Recommended Run Order

Run the `00 Demo Flow` folder from top to bottom.

It will:

1. log in Alice and Bob
2. fetch Alice's real account payload
3. read Alice's public LNURL metadata
4. generate a real Lightning invoice as Alice
5. decode and pay that invoice as Bob
6. list Bob's transactions

## Important Notes

- `GET /v1/accounts/account` includes the QR-bearing fields already, so there is no separate QR generation endpoint to call for the user flow.
- `POST /v1/transactions/{id}/confirm` is included, but on the default local seed it is expected to fail until the paying account is funded.
- `POST /v1/auth/logout` only succeeds when both of these are sent:
  - bearer access token
  - refresh token in the request body
- The decoder endpoints are also exercised with authenticated calls because that is how the current local app behaves.

## Import

Import both JSON files into Postman, select the `Aratiri Local` environment, then run the collection or just the `00 Demo Flow` folder.

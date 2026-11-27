# Payment setup

**No live payment provider is integrated.** What exists: the full client/back-end contract for a *manual* wallet-transfer flow, and a `GatewayPaymentProvider` stub that deliberately fails. Nothing is ever marked paid by the app.

Flow: user picks Plus/Pro → sees operator instructions → pays out-of-band → submits transaction ID → backend stores `PENDING` → admin verifies → backend grants 30 days → app refreshes entitlement (signed device token + server record).

## Operator steps
1. Deploy `backend/` over HTTPS (see backend/README.md); set `JWT_SECRET`, `ADMIN_TOKEN`, prices.
2. Build the app with `-Pechora.backendUrl=https://…` and `-Pechora.paymentInstructions="…{AMOUNT}…"` (your real merchant/personal wallet number — never invented by the software).
3. Verify payments: `curl -H "Authorization: Bearer $ADMIN_TOKEN" https://host/admin/payments?status=PENDING`, compare with your wallet statement, then `POST /admin/payments/<id>/verify` (or `/reject`, `/refund`).

## bKash / other gateways
Requires a merchant agreement, sandbox credentials and the provider's current API docs, none of which were available. Implement `PaymentProvider` server-side (create/execute/query payment, webhook with signature verification) and keep entitlement granting in the backend. Do not call gateway APIs from the app with secrets.

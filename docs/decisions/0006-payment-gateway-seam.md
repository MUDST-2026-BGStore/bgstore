# ADR-0006: Payment gateway seam

Status: Accepted — 2026-09-23

## Decision

Session checkout settles money through a `billing` module seam, not a concrete
provider. `PaymentGateway` is the port: an adapter declares which
`PaymentMethod` values it can charge and reports what the provider did.
`BillingService` picks the first adapter that supports the requested method,
charges before the session closes, and records only successful charges in the
`payment` table. The built-in `BogusPaymentGateway` is the default adapter for
every charged method so the reservation → play → settle flow runs before a real
PromptPay or bank adapter exists.

Settlement semantics:

- `WAIVED` closes the session with no charge: no gateway call and no payment
  record. The API rejects a waived checkout whose amount is not 0.
- A charged checkout requires a confirmed amount greater than 0.
- A declined charge fails the checkout request with `502` and the transaction
  rolls back, so the session stays open and staff can retry. The UI keeps the
  dialog open with the amount preserved.
- A succeeded charge carries the gateway name and the provider's transaction
  reference; both are returned in `CheckoutReceiptResponse.payment` and shown
  on the staff receipt.
- The browser only reads the receipt. No card data, QR payload, or provider
  redirect ever reaches the client; the charge is a server-side call.

> 2026-09-23: card simulation (ADR-0007) refines the card case — the card
> fields reach the client at charge time so staff can enter a PAN, CVV, and
> expiry for the bogus gateway to judge; the receipt itself still stays
> minimal (network brand and last four only).

## Rationale

Checkout is the first place BGStore moves money. Keeping the provider behind a
port lets the store operate end to end today (with a deterministic stand-in)
while real adapters (PromptPay, bank transfer confirmation) land later as new
beans, without touching `PlaySessionService`, the contract, or the staff UI.
Charging inside the checkout transaction gives one invariant — a session never
closes unless its confirmed fee was charged or waived — and makes a gateway
outage a retryable condition instead of a lost session.

## Consequences

- Adding a real gateway means adding a `PaymentGateway` adapter bean; the first
  adapter that `supports` the method wins, so adapter order is the precedence
  rule when two adapters overlap.
- The `payment` table is the settlement ledger. Only charged sessions get rows;
  waived sessions are identifiable by the closed session's method instead.
- Checkout decline responses are `502` with a reason; callers retry with the
  same session. There is no partial settlement state to reconcile.
- When a real adapter exists, the bogus adapter should be removed or demoted to
  test configuration so production never double-supports a method.
- The staff receipt must keep displaying the gateway and reference so staff can
  reconcile against the provider's own dashboard.

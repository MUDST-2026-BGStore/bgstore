# ADR-0007: Card simulation in the bogus gateway

Status: Accepted — 2026-09-23

## Decision

The bogus gateway simulates card payments well enough to exercise the staff
checkout end to end, without a real acquirer:

- **Card number acceptance.** A chargeable card is a 13–19 digit PAN that
  passes the Luhn check. Anything else is rejected locally as a validation
  error before the gateway is called.
- **Network detection.** The network comes from the leading digits (IIN):
  `4` Visa, `51`–`55`/`2221`–`2720` Mastercard, `34`/`37` Amex, `35` JCB,
  `300`–`305`/`3095`/`36`/`38`–`39` Diners, `6011`/`65`/`644`–`649` Discover,
  `62` UnionPay, else Unknown. The UI renders a logo per network (assets in
  `apps/web/src/assets/card-networks/`) as the staff types, so the logo always
  reflects the entered digits.
- **CVV is the outcome knob.** On a valid, unexpired card the security code
  picks the gateway result: `111` → insufficient funds, `222` → stolen card,
  `333` → CVV mismatch, `999` → gateway unavailable (`502`); any other value
  approves. An expired `MM/YY` beats the knob and declines. The UI shows the
  knob table as a demo hint under the CVV field.
- **What is persisted.** Only the network brand and the last four digits reach
  the payment record (`V24__payment_card_records.sql`) and the receipt
  (`Visa •••• 4242`). The full PAN, CVV, and expiry are charged and dropped;
  the browser only ever sends them for the charge call.

PromptPay stays a static demo QR asset: the gateway returns a fake QR image
and the receipt closes with no acquirer involved.

## Rationale

The store needs staff to practise the full card checkout — decline, read the
reason, retry, and settle — before a real acquirer exists. A Luhn-valid
simulator with network logos exercises the real client-side formatting and
validation paths (including Amex's 4-digit CVV) without inventing fake data
entry rules, and the knob makes every decline reason reproducible on demand.
Recording only brand and last four keeps the ledger useful while keeping the
demo safely out of PCI scope.

## Consequences

- The card fields are client-visible at charge time, refining ADR-0006's
  "no card data reaches the client" stance: that holds for the receipt, while
  the request necessarily carries the PAN/CVV/expiry to the BFF.
- Decline reasons surface verbatim ("The card network declined the charge:
  insufficient funds") so tests and staff see the same wording.
- Swapping in a real acquirer is still only a new `PaymentGateway` bean;
  nothing in the UI or contract encodes the knob behaviour.
- The knob is demo configuration; a production adapter would ignore it by
  construction, not by removing UI code.

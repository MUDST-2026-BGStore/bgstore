# Handoff — remaining TODOs

- Date: 2026-11-13
- Checked against `main` at `f41f971` (same tree as `feat/storefront-reservation-entry` at `9cd568d`)
- Scope of this file: every `TODO` marker left in the repository, with what exists today, the owner's decisions, what to build, and what is out of scope. Nothing else.
- Source of truth for the list: `make todos`. At HEAD it reported 20 entries in 17 files. The tool also counts the word in this file, so ignore the `HANDOFF.md` group in its output. The 6 entries under `tools/backend-tools/` are generator template text and a test asserting on it. They are intentional. **Do not touch them.**

## How to use this file

1. Pick one work item (T1–T12). Do one item per change/PR unless the item says otherwise.
2. Read the files named under "Read first" before editing. Every fact here was checked against the code at `main` `f41f971`. If the code now differs, trust the code, and stop to re-plan if the difference changes the design.
3. Every product decision has already been made by the project owner on 2026-11-13 and is written under **Decisions** in each item. Do not change a decision and do not add behavior that is not listed. If you hit a question this file does not answer, stop and ask the owner; do not guess.
4. Where a step says "verify in code", open the named file and confirm the name/signature before writing code against it.
5. When an item is finished, delete its `TODO` comment, run `make todos` to confirm that file's entry is gone, and delete the item's section from this file.

## Rules that apply to every item

These come from `AGENTS.md` and `apps/web/AGENTS.md`. Read both files once.

- API contract: edit YAML under `openapi/`, then run `pnpm generate:api`. Never hand-edit generated Java (the `contracts` package) or `libs/web/api-client/src/generated/`. New path files must be registered in `openapi/openapi.yaml` (copy how an existing path is registered). Every operation needs `x-access-policy` and, for writes, the `CsrfToken` parameter (copy from `openapi/paths/branches/collection.yaml`). An unquoted `word: text` inside a YAML description breaks parsing; use `>-` block text.
- Database: add a new Flyway file in `apps/api/src/main/resources/db/migration/`. V001–V009 exist. Use the next free number at the time you write it (the first item to land uses `V010__<name>.sql`, the next `V011`, and so on). Never edit an existing migration.
- Backend modules are Spring Modulith modules. Each `apps/api/src/main/java/com/chanakanlabs/theparlor/<module>/package-info.java` lists `allowedDependencies`. A module may only use another module's top-level public types, or its `spi` package when listed as `<module>::spi`. Never import another module's `internal` package. `ModularityTests` enforces this.
- Modules reference each other by scalar IDs (`UUID`) and subjects (`String`), never by JPA relations across modules.
- The "SPI pattern" mentioned below means: the lower module declares an interface in `<module>/spi/` (with a `package-info.java` annotated `@org.springframework.modulith.NamedInterface("spi")`, copy `branches/spi/package-info.java`); higher modules implement it as a Spring bean and add `"<module>::spi"` to their `allowedDependencies`; the lower module injects `List<TheInterface>`. Working example: `branches/spi/TableCommitments.java`, injected in `branches/internal/application/BranchesService.java`, implemented by `reservations/internal/application/ReservationTableCommitments.java`.
- Branches are not tenants. Admin = business-wide; manager = all branches; staff = only branches with a row in `branch_staff_assignments`. The server check is `BranchAccess.requireOperator(branchId)`.
- Money is integer THB minor units (satang) on the server (`*_minor` columns, `Money.amountMinor` in the contract). ฿50 = `5000`.
- Web: every write waits for the server and shows the server's error; never update the screen optimistically. Read problems with the helpers in `apps/web/src/shared/lib/api-errors.ts`. Generated client functions take headers through the options argument, e.g. `{ headers: { 'Idempotency-Key': key } }`.
- Management UI copy is terse and must not mention samples, demos, local storage or backend wiring. Customer pages use the shared controls in `apps/web/src/shared/ui`.
- Scaffold new backend services with `pnpm nx g @theparlor/backend-tools:backend-service --module=<module> --name=<service> --dry-run --no-interactive`, review, then rerun without `--dry-run`.
- Never commit secrets or `.env`. Do not create extra documentation files.

## Verification commands

Run the ones matching what you changed. All must pass before an item is done.

```bash
pnpm generate:api
```

```bash
make modulith-check
```

```bash
pnpm nx run api:test
```

```bash
CI=true pnpm nx run-many -t test --exclude=api
```

```bash
pnpm nx run-many -t lint typecheck
```

`api:test` needs Docker (Testcontainers). Use the Nx target, not plain Gradle. Java formatting: `pnpm nx run api:test --args='spotlessApply'` reflows comments, so re-read a Java file before a second exact-text edit. Backend integration tests live in `apps/api/src/test/java/com/chanakanlabs/theparlor/TheparlorApiApplicationTests.java`; add cases there in the style of the existing ones.

## Work items at a glance

| ID  | Marker                                                                                                       | Area      | Depends on |
| --- | ------------------------------------------------------------------------------------------------------------ | --------- | ---------- |
| T1  | `MeController.java:24` (no tag)                                                                              | API       | —          |
| T2  | `MeController.java:48` (no tag)                                                                              | API + Web | T1         |
| T3  | `GamesService.java:160` `catalog-offline`                                                                    | API       | —          |
| T4  | `BranchSelect.vue:22` `branch-api`                                                                           | Web       | —          |
| T5  | `CheckoutPage.vue:17` `checkout-payments`                                                                    | API + Web | T6 first   |
| T6  | `CheckoutPage.vue:18` `checkout-shipping`                                                                    | API + Web | —          |
| T7  | `InPlayOrderModal.vue:2` `in-play-orders-api`; `useWorkspacePantry.ts:1`, `pantry.samples.ts:1` `pantry-api` | API + Web | —          |
| T8  | `reservations-store.ts:1` `reservation-api`                                                                  | Web       | T7, T10    |
| T9  | `WorkspaceAnalytics.vue:2` `analytics-api`                                                                   | API + Web | T7         |
| T10 | `WorkspacePolicies.vue:2` `policies-api`                                                                     | Web       | —          |
| T11 | `WorkspaceSettings.vue:2` `settings-api`                                                                     | Web       | —          |
| T12 | `WorkspaceStaff.vue:2` `staff-admin-api`; `WorkspaceAdministration.vue:2` `administration-api`               | API + Web | —          |

Suggested order: T1, T11, T4, T10, T3, T2, T12, T6, T7, T8, T9, T5. T5 is last because it needs Omise credentials from the owner and the largest design.

Path prefixes used below:

- `API/` = `apps/api/src/main/java/com/chanakanlabs/theparlor/`
- `APITEST/` = `apps/api/src/test/java/com/chanakanlabs/theparlor/`
- `WEB/` = `apps/web/src/`
- `MGMT/` = `apps/web/src/features/reservations/management/`

---

## T1 — Make `MeController` use one dependency style

**Marker:** `API/account/internal/web/MeController.java:24` — `// TODO: make this consistent, either use interface or direct class`

**Today:** The controller injects both the concrete `AccountService` and the interface `AccountProvisioning`. `AccountService` implements `AccountProvisioning`, so both are the same bean. Every other controller injects its concrete service only (see `API/branches/internal/web/BranchesController.java`).

**Decision:** use the concrete class only.

**Do:**

1. In `MeController.java`: remove the `AccountProvisioning` field, constructor parameter and import. Constructor becomes `MeController(AccountService accountService)`.
2. In `createMe`, replace `accountProvisioning.provisionForCurrentIdentity(registration);` with `accountService.provisionForCurrentIdentity(registration);`. Keep the next line (`return accountService.findCurrentProfile().orElseThrow();`) exactly as it is.
3. Delete the TODO comment.
4. In `APITEST/account/internal/web/MeControllerTests.java`: every `new MeController(service, mock(AccountProvisioning.class))` becomes `new MeController(service)`. Two tests (around lines 137 and 180) create a separate `provisioning` mock and stub/verify `provisionForCurrentIdentity` on it; move those stubs and verifications onto the `AccountService` mock. Remove the unused import.

**Out of scope:** Do not delete or change the `AccountProvisioning` interface. Do not change endpoint behavior.

**Done when:** `pnpm nx run api:test` passes and the TODO is gone.

---

## T2 — Implement account deletion (`DELETE /api/v1/me`)

**Marker:** `API/account/internal/web/MeController.java:48`. The method throws `UnsupportedOperationException`.

**Read first:** `openapi/paths/account/me.yaml` (`delete:` block), `API/account/internal/application/AccountService.java`, `apps/api/src/main/resources/db/migration/V001__initial_schema.sql` lines 5–46 and 547–567, `API/orders/internal/domain/Order.java`, `API/reservations/internal/domain/Reservation.java`, `WEB/features/auth/pages/AuthProfile.vue`, `WEB/shared/stores/auth.ts`.

**Facts:**

- The contract exists: `operationId: deleteMe`, responses `204`, `401`, `403`, `404` (not provisioned), `409` (cannot currently be deleted). It says the Keycloak identity is not deleted.
- Deleting a row from `accounts` cascades in the database: `account_identities` and `carts` rows are deleted (`ON DELETE CASCADE`), `customers.account_id` becomes NULL (`ON DELETE SET NULL`). The `customers` row stays, so past reservations and orders keep their history.
- Orders and reservations also store the customer's subject (`Order.getCustomerSubject()` exists; `Reservation` has a subject in its `Party` — verify the field/column name in code).
- The `account` module may depend only on `identity`, `contracts`, `shared`.
- The generated client already has `deleteMe`. No page calls it. No test covers it.
- Web logout exists: `useAuthStore().logout()` in `WEB/shared/stores/auth.ts` drops account data and posts to `/api/auth/logout`, which clears the server session and redirects through Keycloak logout.

**Decisions:**

1. Refuse with `409` while the account has an open commitment: a reservation in status `CONFIRMED` or `CHECKED_IN`, or an order in status `PLACED`. (An active rental always belongs to a `CHECKED_IN` reservation, so the reservation check covers it.) Finished, cancelled and no-show history never blocks.
2. Only the application account is deleted. The Keycloak identity is kept. No Keycloak integration.
3. After a successful delete the user is logged out.
4. Build the UI now, on the profile page.

**Do — server:**

1. Create `API/account/spi/package-info.java` (named interface `spi`) and `API/account/spi/AccountCommitments.java`:
   ```java
   /** Implemented by modules holding a customer's open commitments; asked before an account is deleted. */
   public interface AccountCommitments {
     /** subject is the customer's OIDC subject. */
     boolean hasOpenCommitments(String subject);
   }
   ```
2. `AccountService`: add a constructor parameter `List<AccountCommitments> commitments` and a method:
   - `@Transactional public void deleteCurrent()`
   - `var user = currentUser.require();`
   - find the account with the existing private `lookupAccount(user)`; if empty throw `new AccountNotProvisionedException()` (already mapped to 404).
   - if any `commitments` bean returns true for `user.subject()`, throw `new ResponseStatusException(HttpStatus.CONFLICT, "Account has an open reservation or order")`.
   - `accounts.delete(account); accounts.flush();`
3. Implement `AccountCommitments` in `reservations` (new class in `API/reservations/internal/application/`, a repository query "exists reservation with this customer subject and status in CONFIRMED, CHECKED_IN") and in `orders` (new class in `API/orders/internal/application/`, "exists order with this customer subject and status PLACED"). Add `"account::spi"` to `allowedDependencies` in `API/reservations/package-info.java` and `API/orders/package-info.java`.
4. `MeController.deleteMe()`: replace the body with `accountService.deleteCurrent();`. Delete the TODO. Verify in the generated `AccountApi` interface that the method answers 204.
5. Fix `AccountServiceTests` construction for the new constructor parameter.

**Do — web:**

6. In `AuthProfile.vue` add a "Delete account" section at the bottom of the page: a danger button that opens a confirmation dialog (use `AppConfirmDialog` from `WEB/shared/ui`), text stating that the account, saved cart and preferences are removed, order and visit history is kept by the store, and the sign-in itself remains.
7. On confirm call `deleteMe()` from `@theparlor/api-client`. On success call `useAuthStore().logout()`. On `409` show the server's message (open reservation or order) and keep the dialog closed and the page unchanged. On any other failure show a generic retry message. Disable the button while pending.
8. Add both Thai and English copy the same way the rest of `AuthProfile.vue` does (verify whether it uses `useStoreText`/`t()` or inline locale checks, and follow it).

**Tests:**

- `AccountServiceTests`: deletes when no commitments; 404 when not provisioned; 409 when a commitments bean returns true and the account still exists.
- `TheparlorApiApplicationTests`: delete with a confirmed reservation → 409; after cancelling it → 204, `GET /api/v1/me` → 404, the identity link and carts are gone, and the cancelled reservation is still readable by staff.
- `AuthProfile.spec.ts`: confirm calls `deleteMe` then logout; 409 shows the message and does not log out.

**Out of scope:** Deleting or disabling the Keycloak user. Anonymising `customers` contact columns. Auto-cancelling anything.

---

## T3 — Refuse taking a game offline while it is committed

**Marker:** `API/catalog/internal/application/GamesService.java:160` — `TODO(catalog-offline)`. In `updateGame`, the `active: false` branch calls `entity.deactivate()` with no checks.

**Read first:** `GamesService.updateGame`, `API/branches/internal/application/BranchesService.java` method `update` (the model to copy), `API/reservations/ReservationCommitments.java`, `API/rentals/RentalCommitments.java`, `API/orders/OrderCommitments.java`, `API/reservations/internal/persistence/ReservationRepository.java`, `API/orders/internal/persistence/OrderRepository.java`.

**Facts:**

- Three classes exist "for catalog retirement checks" and are **not called from anywhere**: `ReservationCommitments`, `RentalCommitments`, `OrderCommitments`.
- `catalog` may depend only on `contracts` and `shared`, so it cannot call them. `reservations` and `orders` do not list `catalog` as a dependency.
- `PATCH /api/v1/games/{gameId}` (`openapi/paths/games/item.yaml`, `updateGame`) already declares `409`.
- `order_items.game_id` exists (V008) and is nullable for old rows.
- `ReservationRepository.hasConfirmedGame(gameId)` exists but checks `CONFIRMED` only.

**Decision:** refuse with `409`, the same rule as deactivating a branch. Staff must cancel or finish the commitments first.

**Do:**

1. Create `API/catalog/spi/package-info.java` (named interface `spi`) and `API/catalog/spi/GameCommitments.java` with one method `boolean hasOpenCommitments(UUID gameId)` and a Javadoc: implemented by modules that commit a game to customers; catalog asks before taking a game offline.
2. `GamesService`: add constructor parameter `List<GameCommitments> commitments`. In `updateGame`, in the `else` branch (request `active` is false), before `entity.deactivate()`:
   ```java
   if (entity.isActive() && commitments.stream().anyMatch(c -> c.hasOpenCommitments(id)))
     throw new ResponseStatusException(
         HttpStatus.CONFLICT, "Game has a confirmed reservation, active visit or placed order");
   ```
   (Verify the getter name on `catalog/internal/domain/Game.java`.) Replace the TODO comment with a one-line comment stating the rule.
3. `reservations`: new bean implementing `GameCommitments`. True when a reservation in status `CONFIRMED` or `CHECKED_IN` has a `ReservationGame` row for the game. Add a repository query modelled on `hasConfirmedGame` with both statuses. Add `"catalog::spi"` to `API/reservations/package-info.java`.
4. `orders`: new bean implementing `GameCommitments`. True when an order in status `PLACED` has an item with that `game_id` (verify the field name on `OrderItem`). Add `"catalog::spi"` to `API/orders/package-info.java`.
5. Delete `ReservationCommitments`, `RentalCommitments`, `OrderCommitments` and the repository methods only they used (`hasConfirmedGame`, `reservationsUsingGame`, `hasActiveReservation`, `hasPlacedEdition`) — but first search the whole repo for each name and keep anything that has another caller.
6. Web: find where the workspace toggles a game's active flag (search `updateGame` in `MGMT/useWorkspaceGames.ts` and `MGMT/components/WorkspaceLibrary.vue`). If such an action exists, make sure a `409` shows the server's message. If none exists, make no web change.

**Tests:** `TheparlorApiApplicationTests`: deactivating is 409 with a confirmed reservation selecting the game; 409 with a placed order for one of its editions; 200 after those are cancelled; reactivating is always allowed. Fix any unit test that constructs `GamesService`.

**Out of scope:** Edition `active` flags (`EditionsService`). Removing a game from a branch library. Notifications.

---

## T4 — Branch search in the store selector

**Marker:** `WEB/features/home/components/BranchSelect.vue:22` — `TODO(branch-api)`.

**Read first:** `BranchSelect.vue`, its callers `WEB/features/home/components/CartFulfillment.vue:52` and `WEB/features/home/components/StoreContext.vue:133`, `WEB/shared/stores/branch-directory.ts`, `WEB/features/home/components/BranchSelect.spec.ts`.

**Facts:** `branch-directory.ts` loads every active branch once and caches it; each entry has `address` (one free-text string; no city field exists anywhere). `BranchSelect` receives `options: { id, name }[]` and filters on the translated name only. The selected option always stays visible while filtering, and typing never changes the saved branch.

**Decision:** client-side search only. No server search parameter will be added.

**Do:**

1. Change the `options` prop type to `{ id: string; name: string; address?: string }[]`.
2. In `matches`, also match `option.address` (case-insensitive, same trimming as the name).
3. In both callers, pass `address` from the directory entries they already have.
4. Add a case to `BranchSelect.spec.ts`: typing part of an address keeps that branch and the selected branch visible, and hides the others.
5. Replace the TODO with a one-line comment: search is client-side over the cached directory.

**Out of scope:** Changing option labels or layout (`AppSelect` options are `{ value, label }` only). Any API change.

---

## T5 — PromptPay and cash on delivery, with Omise as the payment provider

**Marker:** `WEB/features/home/pages/CheckoutPage.vue:17` — `TODO(checkout-payments)`.

**Do T6 first** (the order total changes there).

**Read first:** `API/payments/Payments.java`, `API/payments/internal/application/PaymentsService.java`, `API/payments/internal/application/PaymentTokensService.java`, `API/payments/internal/gateway/PaymentGateway.java`, `UnconfiguredGateway.java`, `bogus/BogusGateway.java`, `API/payments/internal/domain/Payment.java`, V006 lines 22–62 and V009 lines 1–6, `API/orders/internal/application/OrderPlacementService.java`, `OrdersService.java`, `OrderViews.java`, `API/carts/internal/application/CartsService.java` (checkout), `openapi/components/schemas/CheckoutCartRequest.yaml`, `OrderPayment.yaml`, `apps/api/src/main/resources/application.yaml` (the `theparlor.payments` block), `WEB/features/home/pages/CheckoutPage.vue`, `WEB/features/home/components/MyOrdersModal.vue`, `WEB/features/reservations/components/inventory/FulfillmentQueue.vue`.

**Facts:**

- Checkout offers card only. The web tokenizes the card through `POST /api/v1/payments/tokens` (card data passes through our server to the gateway), then checkout authorizes the token. Capture happens at fulfilment, void at cancellation.
- Payment methods are `CARD` and `COUNTER`; statuses `PENDING`, `AUTHORIZED`, `CAPTURED`, `VOIDED`. Database checks in V006: `chk_payments_method`, `chk_payments_card` (card fields present exactly when method is CARD), `chk_payments_method_status` (CARD is never PENDING; COUNTER is never AUTHORIZED).
- One gateway adapter is active, chosen by `theparlor.payments.gateway`: `none` (refuses) or `bogus` (development, moves no money). Each payment stores the gateway `name()` so later captures reach the same provider.
- `OrdersService.fulfillOrder` always calls `payments.capture`; `cancelOrder` always calls `payments.cancel`.
- Order statuses are `PLACED`, `CANCELLED`, `FULFILLED`. `OrdersService.listMyOrders` reads by customer subject; guests cannot re-read an order.
- There is no scheduled job anywhere in the API (`@EnableScheduling` is not used).
- Bank transfer was considered and **rejected**; do not build it.

**Decisions:**

1. Provider: **Omise (Opn Payments)**, for cards and PromptPay. `bogus` stays for development and tests; `none` stays the default.
2. New methods: **PromptPay QR** and **cash on delivery (COD)**. No bank transfer.
3. PromptPay and COD are for **signed-in customers only**, and only on retail checkout. Guests keep card only. Reservation food pre-orders stay card only.
4. PromptPay: the order is placed with stock held and the payment `PENDING`. If it is not paid within **15 minutes** the server cancels the order and releases the stock.
5. COD: allowed only when the cart ships (`SHIPPING`). The payment is `PENDING` until staff confirm the cash was received; dispatching the parcel does not capture it.
6. Omise API details are **not** written here on purpose. Before coding any provider call, read the current Omise documentation (charges, tokens, PromptPay source, webhooks, authorize-then-capture, reversing an authorization). Do not write endpoint paths or field names from memory. The owner supplies test keys through `.env`; never commit keys, and never log card data, tokens or keys.

**Design (our side; build exactly this):**

_Schema (one new migration):_

- Replace `chk_payments_method` to allow `'CARD','COUNTER','PROMPTPAY','COD'`.
- Replace `chk_payments_card` with: CARD rows have gateway, gateway_reference, card_brand, card_last4 all non-null; PROMPTPAY rows have gateway and gateway_reference non-null and card fields null; COUNTER and COD rows have all four null.
- Replace `chk_payments_method_status` with: CARD is never PENDING; COUNTER, PROMPTPAY and COD are never AUTHORIZED.
- Add nullable `expires_at TIMESTAMPTZ` on `payments`, non-null exactly for PROMPTPAY rows.
- Use `ALTER TABLE … DROP CONSTRAINT … , ADD CONSTRAINT …`. Check `EnumStorageLengths` / the `method` column width (20) still fits.

_Payments module:_

- `Payments.Method`: add `PROMPTPAY`, `COD`.
- `PaymentGateway` port: add `PromptPayCharge createPromptPay(long amountMinor, String payableReference)` returning the provider reference, the QR image/payload the provider gives, and its expiry; and `boolean isPaid(String reference)` which asks the provider for the charge's current state. `UnconfiguredGateway` throws `GatewayFailure` for both. `BogusGateway` implements both for development (verify how it stores state in `bogus_payment_tokens` and add the minimum needed so tests can mark a bogus PromptPay charge paid).
- `Payments` interface: add
  - `PaymentSummary startPromptPay(Payable payable, long amountMinor)` — creates the provider charge, stores a `PROMPTPAY` / `PENDING` payment with `expires_at = now + 15 minutes`.
  - `PaymentSummary payOnDelivery(Payable payable, long amountMinor)` — stores a `COD` / `PENDING` payment.
  - `PaymentSummary confirmReceived(Payable payable)` — `PENDING` → `CAPTURED` for COD (and for PromptPay once the provider says paid); repeating it changes nothing.
  - `List<UUID> expiredUnpaid(Kind kind, Instant now)` — payables whose PROMPTPAY payment is still PENDING and past `expires_at`.
- `capture(payable)`: for `PROMPTPAY`, if still `PENDING` throw a 409 `ProblemException` ("Payment has not been received"); if `CAPTURED` do nothing. For `COD`, do nothing (it stays `PENDING`). CARD and COUNTER behavior is unchanged.
- `cancel(payable)`: `PENDING` PROMPTPAY/COD → `VOIDED`. A `CAPTURED` PromptPay or COD payment cannot be cancelled here; throw 409 (refunds are out of scope).
- `PaymentSummary`: add nullable `promptPayQr` (what the web renders) and `expiresAt`. Only filled for PENDING PromptPay.
- New adapter `API/payments/internal/gateway/omise/OmiseGateway.java`, active when `theparlor.payments.gateway=omise`, implementing every port method through Omise's HTTP API with keys from configuration properties (`theparlor.payments.omise.public-key`, `…secret-key`, bound from environment variables; add empty defaults to `application.yaml` and placeholders wherever `.env` values are generated by `make configure` — find that script from the `Makefile`). Map issuer refusals to `GatewayDeclined` and everything else to `GatewayFailure`, as the port's Javadoc requires. Use Spring's `RestClient` with timeouts. Unit-test it against a local stub HTTP server; never call Omise in automated tests.
- Webhook: new public endpoint `POST /api/v1/payments/omise/webhook` (contract file under `openapi/paths/payments/`, `x-access-policy: public`, permit it in `API/identity/internal/security/SecurityConfig.java` next to the existing `/api/v1/payments/tokens` rule, and exempt it from CSRF the way that config handles other non-browser posts — verify how). **Do not trust the webhook body.** Use it only to learn which charge changed, then call `gateway.isPaid(reference)` and, if paid, mark the payment `CAPTURED`. Unknown references answer 200 and do nothing.

_Orders module:_

- `OrderPlacement.Placement`: add `paymentMethod` (`CARD`, `PROMPTPAY`, `COD`); `paymentToken` is required only for CARD.
- `OrderPlacementService.place`: after holding stock and writing the order, call `authorizeCard`, `startPromptPay` or `payOnDelivery` accordingly. Reject with 400: PROMPTPAY or COD from a guest (no `accountId`); COD when `shippingAddress` is null.
- `OrdersService.fulfillOrder`: unchanged call order; the new `capture` rules make an unpaid PromptPay order un-fulfillable (409) and leave COD pending.
- New endpoint `POST /api/v1/branches/{branchId}/orders/{orderId}/payment/confirm` (`operationId: confirmOrderPayment`, policy `branch-operator`, CSRF): only for a COD order in status `FULFILLED` with payment `PENDING`; calls `payments.confirmReceived`; otherwise 409. Repeating after success returns the order unchanged.
- Expiry: add `@EnableScheduling` (on a new small configuration class in `shared/internal`, next to `TimeConfiguration.java`) and a scheduled method in `orders` running every minute: for each order ID from `payments.expiredUnpaid(ORDER, now)`, first call `gateway`-backed re-check through a new `Payments` method or reuse the webhook path so a payment that arrived late is captured instead of cancelled; otherwise cancel the order exactly as `cancelOrder` does (release stock, void payment) with actor `system` and reason `Payment not received in time`. Each order in its own transaction with the order row locked (`lockByIdAndBranchId`), so it cannot race a webhook.

_Contract:_

- `CheckoutCartRequest.yaml`: add `paymentMethod` (enum `CARD`, `PROMPTPAY`, `COD`, default `CARD`); remove `paymentToken` from `required` and document that it is required for CARD. Apply the same to `CreateOrderRequest.yaml` if it carries `paymentToken` (verify).
- `OrderPayment.yaml`: add the two enum values, `promptPayQr` and `expiresAt`, and update the description.
- Add the confirm and webhook paths. Run `pnpm generate:api`.

_Web:_

- `CheckoutPage.vue`: in the payment methods card, show three options for signed-in customers (card, PromptPay, cash on delivery) and card only for guests. COD is shown only when `cart.method === 'shipping'`. Card fields render only for card. Submit sends `paymentMethod` and, for card, the token as today.
- After a PromptPay checkout succeeds, show the QR from `placedOrder.payment.promptPayQr`, the amount and a countdown to `expiresAt`. Poll the order (through the orders store / `listMyOrders` or `getOrder` — verify which the customer may call) every 3 seconds until payment is `CAPTURED` (show the normal success state) or the order is `CANCELLED` (show "payment was not received, the order was cancelled" and a link back to the cart). Stop polling when the component unmounts.
- `MyOrdersModal.vue`: show the method and state ("Awaiting payment", "Pay on delivery", "Paid"); for a pending PromptPay order show the QR again.
- `FulfillmentQueue.vue`: show the payment state; disable Fulfil for unpaid PromptPay; add a "Cash received" action for fulfilled COD orders calling `confirmOrderPayment`.
- Add Thai and English copy alongside existing strings. Remove the `checkout-payments` half of the TODO comment and update the sentence in `apps/web/AGENTS.md` that says PromptPay, bank transfer and cash on delivery have no backend (bank transfer still has none).
- Delete dead CSS in `CheckoutPage.vue` that the new UI does not use. At HEAD these selectors had zero uses in the template: `.bank-total-card`, `.bank-accounts-box`, `.bank-card-luxury`, `.qr-code-frame`, `.tracking-row`, `.carrier-tag`, `.btn-copy-tracking`, `.dl-code` and their sibling rules. Search the file for each class before deleting; reuse instead of deleting where the new QR view fits.

**Tests:** `PaymentsServiceTests` for every new transition and refusal; `TheparlorApiApplicationTests` with the bogus gateway: PromptPay order paid → fulfillable; unpaid past expiry → cancelled and stock back; fulfil while unpaid → 409; COD pickup → 400; guest PromptPay → 400; COD fulfil then confirm → CAPTURED; webhook with unknown reference → 200. `SchemaIntegrityTests` if it enumerates constraints. Web specs for method visibility (guest vs signed-in, pickup vs shipping) and the polling outcomes.

**Out of scope:** Refunds of captured payments. Bank transfer. PromptPay for reservation pre-orders. Saving cards. Sending raw card data anywhere new. Going live (live keys and switching production to `omise` are the owner's step).

---

## T6 — Shipping fee, carriers and tracking numbers

**Marker:** `WEB/features/home/pages/CheckoutPage.vue:18` — `TODO(checkout-shipping)`.

**Read first:** `API/orders/internal/domain/Order.java`, `OrderFulfillmentMethod.java`, `API/orders/internal/application/OrderPlacementService.java`, `OrdersService.java` (`fulfillOrder`), `OrderViews.java`, `API/carts/internal/application/CartsService.java`, `openapi/components/schemas/Cart.yaml`, `Order.yaml`, `openapi/paths/orders/fulfill.yaml`, `WEB/features/home/pages/CartPage.vue`, `CheckoutPage.vue`, `WEB/features/home/components/MyOrdersModal.vue`, `WEB/features/reservations/components/inventory/FulfillmentQueue.vue`.

**Facts:** Orders are `PICKUP` or `SHIPPING`, with an optional `shippingAddress`. One branch ships. `Order.total` is the sum of line totals; `Cart` has `subtotal`. There is no fee, carrier or tracking column. `fulfillOrder` takes no request body. `carts` may depend on `orders`.

**Decisions:**

1. Fee: **flat ฿50 (`5000` minor) per shipping order, free when the items total is ฿1,500 (`150000` minor) or more.** Pickup orders have no fee.
2. The carrier is chosen by **staff at dispatch**, from a fixed list: **Thailand Post, Kerry Express (KEX), Flash Express, J&T Express**.
3. The tracking number is **entered by staff when fulfilling a shipping order, is required then, and is shown to the customer** in My Orders.

**Do — server:**

1. New public final class `API/orders/ShippingPolicy.java` (top-level so `carts` can use it) with constants `FLAT_FEE_MINOR = 5_000`, `FREE_FROM_MINOR = 150_000` and `static long feeMinor(boolean shipping, long itemsTotalMinor)` returning `0` for pickup or when `itemsTotalMinor >= FREE_FROM_MINOR`, else the flat fee. Unit-test the boundaries (149 999 → 5 000; 150 000 → 0).
2. Migration: on `orders` add `shipping_fee_minor BIGINT NOT NULL DEFAULT 0`, `carrier VARCHAR(20)`, `tracking_number VARCHAR(60)`; checks: fee ≥ 0; carrier in `('THAILAND_POST','KERRY','FLASH','JT')` or null; carrier and tracking are both null or both non-null; tracking is not blank. Verify the name of the fulfilment-method column on `orders` and add a check that pickup rows have fee 0 and null carrier.
3. `Order` entity: store the fee at creation (computed by the service from `ShippingPolicy`); add `ship(carrier, trackingNumber)` used during fulfilment, which throws `IllegalStateException` for a pickup order. Keep mutations inside domain methods as the existing ones do.
4. `OrderPlacementService.place`: compute the fee from the items total, store it, and authorize **items total + fee**. Verify `OrderViews` then reports `total` = items + fee and that the payment amount equals it.
5. Contract:
   - `Order.yaml`: add `subtotal` (items only), `shippingFee` (both `Money`, required), `carrier` (enum above) and `trackingNumber` (string, max 60); update the description: total = subtotal + shippingFee.
   - `Cart.yaml`: add `shippingFee` and `total` (`Money`, required).
   - `openapi/paths/orders/fulfill.yaml`: add an optional request body schema `FulfillOrderRequest` with `carrier` and `trackingNumber`. Server rule: both required when the order is `SHIPPING` (400 if missing), rejected when it is `PICKUP` (400).
   - `pnpm generate:api`.
6. `OrdersService.fulfillOrder`: take the request, validate as above, call `order.ship(...)` for shipping orders before the existing fulfil logic. The controller passes the body through (`API/orders/internal/web/OrdersController.java`).
7. `CartsService`: fill `shippingFee` and `total` on the cart view using `ShippingPolicy` and the cart's fulfilment method.

**Do — web:**

8. `CartPage.vue` and the `CheckoutPage.vue` summary: show Subtotal, Shipping (the server's `shippingFee`, or "Free" when zero on a shipping cart; hide the row for pickup) and Total (the server's `total`). Under the shipping row, when the fee is not zero, show how much more is needed for free shipping, computed as `150000 - subtotal` from a constant exported next to the cart store (`WEB/features/home/stores/cart.ts`) with a comment pointing at `ShippingPolicy`.
9. `MyOrdersModal.vue` and the checkout confirmation: show the fee line, and for shipped orders the carrier name and tracking number with a copy button.
10. `FulfillmentQueue.vue`: fulfilling a shipping order opens a small dialog with a carrier `AppSelect` (four options, display names as in decision 2) and a tracking number field, both required; pickup orders fulfil as today. Wait for the server and show its errors.
11. Remove the `checkout-shipping` half of the TODO comment (and the sentence "delivery orders ship … at no extra fee"). Update the sentence in `apps/web/AGENTS.md` that says carriers and shipping fees have no backend.

**Tests:** `ShippingPolicy` unit test; `OrderTests` for `ship`; integration: shipping order below threshold authorizes items + 5 000; at threshold authorizes items only; fulfil shipping without tracking → 400; pickup with tracking → 400; fulfilled shipping order returns carrier and tracking to its customer. Update `apps/web-e2e/src/storefront.spec.ts` fixtures for the new required fields. Web specs for the summary rows.

**Out of scope:** Customer-chosen carriers, per-province rates, carrier API integration, delivery status tracking, changing tracking after fulfilment.

---

## T7 — Food and drink orders during a visit (staff tab), settled at checkout

**Markers (one feature):**

- `WEB/features/reservations/components/InPlayOrderModal.vue:2` — `TODO(in-play-orders-api)`
- `MGMT/useWorkspacePantry.ts:1` — `TODO(pantry-api)`
- `MGMT/pantry.samples.ts:1` — `TODO(pantry-api)` (its comment "There is no pantry endpoint" is outdated)

**Read first:** `API/pantry/Pantry.java`, `API/pantry/internal/application/PantryService.java`, `API/pantry/internal/domain/BranchMenuStock.java`, V009, `API/payments/Payments.java`, `API/reservations/Visits.java`, `API/reservations/internal/application/VisitsService.java`, `ReservationViews.java`, `ReservationsService.java`, `API/rentals/internal/application/RentalsService.java` (`checkOut`), `openapi/components/schemas/Reservation.yaml`, `Rental.yaml`, `CheckOutResult.yaml`, `MGMT/useWorkspacePantry.ts`, `MGMT/components/RefreshmentsDialog.vue`, `MGMT/components/WorkspacePantryStock.vue` (already live; pattern for reading the menu and for mutations), `MGMT/useWorkspaceReservations.ts` (`openCheckBill`), `WEB/features/reservations/components/TableBillModal.vue`.

**Facts:**

- The server has the menu, per-branch stock, `GET /api/v1/branches/{branchId}/menu`, stock adjustments, and food pre-orders made at booking. It has nothing for ordering after check-in. `Pantry` offers only `hold`, `release`, `fulfill`.
- One payment per payable; payables are `ORDER` and `RESERVATION` (`payments.reservation_id` is `UNIQUE`, used by the pre-order).
- `RentalsService.checkOut` bills table and game hours and records no payment. `rentals` does not depend on `payments`.
- Staff web: the item list comes from `createPantrySamples()` and "submitting" edits browser state. The add-snacks buttons are hidden for live sessions (`WorkspaceSessions.vue:189` `v-if="!res.live"`; check `FloorTableCard.vue:114-117` and `WorkspaceDashboard.vue:256` for equivalent gating). For live sessions `openCheckBill` passes `inPlayOrders: []`.
- `InPlayOrderModal.vue` is mounted only by `MGMT/useWorkspaceReservations.spec.ts`.

**Decisions:**

1. **Staff only.** Customers do not order from their own device. Delete `InPlayOrderModal.vue`.
2. The tab is paid **together with the table bill at checkout**, and the server **records one counter settlement** covering table time, game rental and in-visit food.
3. No kitchen status. An order is final when placed; stock is taken immediately.
4. In-visit food is **full price**. The 10% discount remains pre-order only.

**Do — server:**

1. Migration:
   - `visit_orders` (`id UUID PK DEFAULT uuidv7()`, `reservation_id UUID NOT NULL REFERENCES reservations(id) ON DELETE RESTRICT`, `subtotal_minor BIGINT NOT NULL CHECK (> 0)`, `notes VARCHAR(1000)`, `created_by_subject TEXT NOT NULL CHECK (<> '')`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`), index on `reservation_id`.
   - `visit_order_items` copied from `reservation_preorder_items` in V009 but keyed `(visit_order_id, menu_item_id)`.
   - `payments`: add `rental_id UUID UNIQUE REFERENCES rentals(id) ON DELETE RESTRICT`; replace `chk_payments_one_payable` with `num_nonnulls(order_id, reservation_id, rental_id) = 1`.
2. `Pantry`: add `List<PricedItem> sell(UUID branchId, List<Selection> selections)` — all or nothing, 409 when any quantity exceeds what is available (on hand minus held), decreases on-hand immediately, returns name/price snapshots like `hold`. Add the matching domain method on `BranchMenuStock` (verify how `available()` and `fulfill` are written and follow them). Reuse `PantryService.validate` and the lock-in-ID-order pattern. Update the module comment in `API/pantry/package-info.java` (pre-orders are no longer the only consumers). Unit tests in `PantryServiceTests` / `BranchMenuStockTests`.
3. `reservations`: entities `VisitOrder`, `VisitOrderItem` + repositories under `internal`, and a service method `addVisitOrder(branchId, reservationId, idempotencyKey, request)`:
   - `branches.requireOperator(branchId)`; lock the reservation (`lockByIdAndBranchId`); 409 unless status is `CHECKED_IN`.
   - wrap creation in `Idempotency.once(...)` exactly as `createStaffReservation` does (verify its scope/fingerprint construction and copy it).
   - `pantry.sell(...)`, store order + items with snapshots, subtotal = sum of line totals.
4. Contract:
   - `POST /api/v1/branches/{branchId}/reservations/{reservationId}/food-orders` (`operationId: addVisitFoodOrder`, policy `branch-operator`, CSRF + `Idempotency-Key` parameters, body `{ items: PreOrderItemRequest[] (1..50), notes? }`, `201` with the `Reservation`; `409` not checked in or not enough stock).
   - `Reservation.yaml`: add `foodOrders` (array of `{ id, createdAt, items: ReservationPreOrderItem[], subtotal: Money, notes? }`), filled by `ReservationViews` for staff and owner reads.
   - `Rental.yaml` / `CheckOutResult.yaml`: add `foodTotal` (`Money`), `amountDue` (`Money`) and `payment` (method, status, amount) — see step 6.
5. `Visits` interface: add `long visitFoodTotalMinor(UUID reservationId)` implemented in `VisitsService`.
6. Checkout settlement, in `rentals`:
   - Add `"payments"` to `API/rentals/package-info.java`. Add `RENTAL` to `Payments.Kind`, `Payable.rental(UUID)`, and make `PaymentsService`/`Payment`/`PaymentRepository` store and look up `rental_id` (follow how `RESERVATION` is handled).
   - In `RentalsService.checkOut`, after the rental total is computed: `amountDue = rental total + visits.visitFoodTotalMinor(reservationId)`; call `payments.settleAtCounter(Payable.rental(rentalId), amountDue)` then `payments.capture(Payable.rental(rentalId))` in the same transaction (read `PaymentsService` first: confirm `settleAtCounter` creates COUNTER/PENDING and `capture` moves it to CAPTURED). Repeating checkout must not create a second payment (the method is already idempotent — keep that; verify with a test).
   - The pre-order is not part of `amountDue`; it was captured by card at check-in.
   - Rental views return `foodTotal`, `amountDue` and the payment when it exists.
7. `pnpm generate:api`.

**Do — web:**

8. `MGMT/useWorkspacePantry.ts`: replace `createPantrySamples()` with the live branch menu for `selectedBranchId` (same query and key as `WorkspacePantryStock.vue`; take `selectedBranchId` as an argument the way `useWorkspaceSaleStock` does in `MGMT/context.ts`). Items offered = menu items with availability > 0; cap each quantity at availability and 50. `submitStaffSnackOrder` becomes async: call `addVisitFoodOrder` with one `Idempotency-Key` per attempt (copy the `walkInAttempt` key-reuse pattern in `useWorkspaceReservations.ts`), wait, on success close the dialog and invalidate the menu query and `['listBranchReservations']`; on failure keep the dialog open and show the server's message. Remove the local stock decrement and the `reservationsStore` usage. Type the reservation instead of `any`.
9. `RefreshmentsDialog.vue`: adapt to the live item shape, show pending and error states.
10. Remove the `!res.live` gating on add-snacks so it is offered for live checked-in sessions (and only for those).
11. `openCheckBill`: for live reservations pass the server's `foodOrders` mapped into the bill's order shape instead of `[]`. `TableBillModal.vue` live path: show the food orders, and after checkout show the server's `amountDue` and recorded payment. Do not compute totals in the browser when the server returned them.
12. Delete `MGMT/pantry.samples.ts` and `WEB/features/reservations/components/InPlayOrderModal.vue`; remove the `InPlayOrderModal` cases from `MGMT/useWorkspaceReservations.spec.ts`. Remove all three TODO comments. Update the sentences in `apps/web/AGENTS.md` about in-play ordering (it now exists for staff; it stays off customer pages).

**Tests:** integration: order on a checked-in visit reduces stock and appears on the reservation; order on a confirmed (not checked-in) reservation → 409; more than available → 409 and nothing changes; same idempotency key twice → one order; checkout records a CAPTURED COUNTER payment of rental total + food; second checkout changes nothing. `MGMT` spec for the composable: success closes and invalidates, failure keeps the dialog. Update `apps/web-e2e/src/management.spec.ts` if it clicks the old sample refreshments flow.

**Out of scope:** Customer self-ordering, kitchen statuses, discounts, card/PromptPay at the table (the settlement is always COUNTER), the QR simulation in the non-live bill path (removed in T8), editing or voiding a placed food order.

---

## T8 — Remove the local sample reservations store

**Marker:** `WEB/features/reservations/data/reservations-store.ts:1` — `TODO(reservation-api)`.

**Do after T7 and T10.**

**Read first:** `reservations-store.ts`, `MGMT/useWorkspaceReservations.ts`, `MGMT/useWorkspaceReservations.spec.ts`, `WEB/features/reservations/components/TableBillModal.vue`, `MGMT/components/WorkspaceBillDialog.vue`, `WEB/features/reservations/types.ts`, `apps/web-e2e/src/management.spec.ts`.

**Facts:**

- Customer pages do not use this store. After T7 its remaining users are `useWorkspaceReservations.ts`, `TableBillModal.vue` and the spec.
- It supplies sample visits for a branch with **no tables on the server** (`isLiveFloor` false), local walk-ins, a local "settle bill" with a QR simulation, and the types `CustomerReservation`, `CustomerReservationPreOrder`, `InPlayOrder`, `PreOrderItemRecord` that the bill uses as its view model.
- `handleCheckIn` and `submitCancel` also call the local store for live reservations; `submitCancel` does so before the server answers.

**Decision:** remove the sample floor and sample visits entirely. A branch without server tables shows an empty state.

**Do:**

1. Move the four exported types into `WEB/features/reservations/types.ts`; update every import. Rename `InPlayOrder` only if T7 already replaced it.
2. `useWorkspaceReservations.ts`: delete `sampleFloorTables`, the `!isLiveFloor` merge in `allReservations` (it returns the API items only), the local branch in `handleStaffWalkInSubmit`, the `found` and fallback branches in `openCheckBill`, the `reservationsStore` calls in `handleCheckIn` and `submitCancel`, and the `reservationsStore` export. `branchFloorTables` holds server tables only. Keep `isLiveFloor` only if something still reads it; otherwise delete it and its uses.
3. Where the floor, sessions and bookings views render, show an `AppEmptyState` ("No tables set up for this branch.") when the branch has no tables. Do not fall back to sample tables anywhere (also check `MGMT/components/WorkspaceFloor.vue`, `OverviewFloorTable.vue`, `WorkspaceDashboard.vue`).
4. `TableBillModal.vue`: remove everything that only served non-live sessions — the local settle (`reservationsStore.settleSessionBill`), the customer self-payment QR block and its `qrcode` import, the staff payment-method picker that recorded nothing, and their CSS. The modal always requires a `rentalId`. If `qrcode` has no other importer after this (and after T5), remove the dependency from `package.json`.
5. Delete `reservations-store.ts`. Search for its storage key suffix `workspace-sample-reservations` and for `registerProtectedState` usages tied to it; remove leftovers.
6. Rewrite the affected cases in `useWorkspaceReservations.spec.ts` against mocked API responses (the file already mocks `fetch` for live cases; follow those).
7. `apps/web-e2e/src/management.spec.ts` exercises sample-backed UI: give its API fixtures tables, reservations and rentals so the same UI behaviors are covered from server data.
8. `apps/web/AGENTS.md`: change the sentence "Sample records appear only for a branch with no server data of that kind…" so it no longer covers tables, reservations and visits. `management-inventory.samples.ts` (retail stock, rental copies, orders fallback) is **not** part of this item; leave it and its sentence intact.

**Done when:** no file imports `reservations-store`, unit and e2e suites pass, and the workspace shows only server data for floor, sessions, bookings and bills.

---

## T9 — Revenue and analytics from the server

**Marker:** `MGMT/components/WorkspaceAnalytics.vue:2` — `TODO(analytics-api)`.

**Do after T7** (food revenue includes in-visit orders and the checkout settlement).

**Read first:** `WorkspaceAnalytics.vue`, `MGMT/useWorkspaceNavigation.ts`, V001 `rentals` / `rental_charges` / `reservations` / `orders` tables, V006 `payments`, V009 `reservation_preorders`, `API/branches/BranchFloor.java`, `API/branches/spi/WeeklyHours.java`, `API/shared/internal/TimeConfiguration.java`, `WEB/features/reservations/data/booking.ts` (`BRANCH_TIME_ZONE`, `branchDate`).

**Facts:** The tab is visible to managers and admins. "Refreshments" is a hard-coded `ref(2450)`; the rest is computed in the browser from the current list. There is no reporting endpoint or module. No module may read another module's tables.

**Decisions:**

1. Show **everything useful the server can derive**: revenue by category, visit counts, no-shows and cancellations, table utilization.
2. Period: a **date range** (`from`, `until`, Bangkok calendar days, both inclusive, default today, at most 92 days). Access: **managers and admins only**.
3. The endpoint lives in a **new `reporting` module** that combines public read interfaces of the owning modules.

**Report definition (build exactly these fields):**

Range = `[from 00:00, until+1 day 00:00)` in `Asia/Bangkok`. All money is `Money`.

| Field                  | Meaning                                                                                                  | Owner module                 |
| ---------------------- | -------------------------------------------------------------------------------------------------------- | ---------------------------- |
| `tableRevenue`         | Sum of `TABLE` charge line totals of rentals COMPLETED with `actual_ends_at` in range                    | rentals                      |
| `gameRentalRevenue`    | Sum of `GAME` charge line totals of the same rentals                                                     | rentals                      |
| `completedVisits`      | Count of those rentals                                                                                   | rentals                      |
| `occupiedTableMinutes` | Sum of minutes between `started_at` and `actual_ends_at` of those rentals                                | rentals                      |
| `preOrderRevenue`      | Sum of pre-order totals of reservations checked in within range (`checked_in_at`)                        | reservations                 |
| `visitFoodRevenue`     | Sum of in-visit food order subtotals created in range (T7)                                               | reservations                 |
| `reservationsBooked`   | Reservations with `starts_at` in range, any status                                                       | reservations                 |
| `walkIns`              | Of those, source `WALK_IN`                                                                               | reservations                 |
| `noShows`              | Of those, status `NO_SHOW`                                                                               | reservations                 |
| `cancellations`        | Of those, status `CANCELLED`                                                                             | reservations                 |
| `guestsServed`         | Sum of `party_size` of those with status `CHECKED_IN` or `COMPLETED`                                     | reservations                 |
| `retailRevenue`        | Sum of totals of orders FULFILLED with `fulfilled_at` in range                                           | orders                       |
| `retailOrders`         | Count of those orders                                                                                    | orders                       |
| `openTableMinutes`     | Number of tables currently in service × open minutes in the range from the branch's weekly opening hours | reporting, via `BranchFloor` |
| `totalRevenue`         | Sum of the five revenue fields                                                                           | reporting                    |
| `utilizationPercent`   | `round(100 × occupiedTableMinutes / openTableMinutes)`, `0` when the divisor is 0, capped at 100         | reporting                    |

**Do — server:**

1. In each owning module add a small public top-level read interface with one method taking `(UUID branchId, Instant from, Instant until)` and returning a record of that module's fields above: `rentals/RentalReport.java`, `reservations/ReservationReport.java`, `orders/OrderReport.java`. Implement each in the module's `internal/application` with aggregate JPQL/native queries in its own repositories. Verify column/field names (`fulfilled_at`, `checked_in_at`, …) in the entities before writing queries.
2. New module `API/reporting/` with `package-info.java` (`@ApplicationModule(allowedDependencies = {"contracts::http","contracts::models","branches","branches::spi","rentals","reservations","orders","shared"})`), `internal/application/BranchReportService.java` and `internal/web/ReportsController.java`.
3. `BranchReportService.report(branchId, from, until)`:
   - `@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")`, then `branches.requireOperator(branchId)` (managers and admins always pass it; it also gives the 404/409 for a missing/inactive branch).
   - 400 when `until` is before `from` or the range exceeds 92 days.
   - Convert dates with `ZoneId.of("Asia/Bangkok")`.
   - `openTableMinutes`: for each date in range, sum the minutes of that weekday's periods from `BranchFloor.openingHours(branchId)` (read `WeeklyHours` for the accessor; `24:00` closes count as end of day), times the count of `BranchFloor.tables(branchId)` with `inService`.
4. Contract: `openapi/paths/reports/branch.yaml` → `GET /api/v1/branches/{branchId}/reports/summary?from=YYYY-MM-DD&until=YYYY-MM-DD` (`operationId: getBranchReport`, add a new access-policy value only if the existing ones do not fit — `catalog-manager` is the existing manager+admin policy name; verify how `x-access-policy` values are consumed (search the repo for `x-access-policy`) before adding one), schema `BranchReport` with the fields above plus `from`, `until`. Register the path, `pnpm generate:api`.
5. `make modulith-check` must pass; `ModuleDocumentation` may regenerate module docs — commit what the existing workflow commits.

**Do — web:**

6. `WorkspaceAnalytics.vue`: add from/until date inputs (default both to today's Bangkok date via `branchDate(new Date())`), query `getBranchReport` keyed by branch and range, with loading, error (`AppMessageBar`) and empty states.
7. Replace the four metric cards with: Total revenue; Table time; Game rental; Food and drink (pre-orders + in-visit); Retail; Utilization (`utilizationPercent`, caption "x of y table-hours"). Add a compact counts row/table: completed visits, guests served, reservations, walk-ins, no-shows, cancellations, retail orders. Format money with the existing baht formatter (`formatBaht` in `data/booking.ts`). Follow the dense layout rules in `apps/web/AGENTS.md`; no charts are required.
8. Keep the existing "Open-session fee breakdown" table (it shows live open sessions) below the report. Delete `todayFoodDrinkRevenue`, `todayTotalGross` and the TODO.

**Tests:** per-module repository/service tests for each aggregate; one integration case that books, checks in, orders food, checks out and fulfils a retail order, then asserts every field; staff role → 403; bad range → 400. Web spec with a mocked report.

**Out of scope:** Charts, exports, cross-branch totals, cost/profit, tax (the server has no such data), caching.

---

## T10 — Venue policies show the real rules; no-show becomes manual

**Marker:** `MGMT/components/WorkspacePolicies.vue:2` — `TODO(policies-api)`.

**Read first:** `WorkspacePolicies.vue`, `MGMT/useWorkspaceReservations.ts` (search `autoNoShowMutation`, `Auto-marked`, `Auto-released`), `MGMT/components/WorkspaceBookings.vue`, `MGMT/components/CancelBookingDialog.vue`, `API/reservations/internal/application/ReservationsService.java` (`markNoShow`), `API/reservations/internal/domain/Reservation.java` (`markNoShow` — read which states and times it accepts), `openapi/paths/reservations/no-show.yaml`, `WEB/features/reservations/data/booking.ts`.

**Facts:**

- The screen (admin tab, read-only) shows hard-coded values: no-show release 15 minutes; rates ฿150/฿200/฿300 by seat count; unused deposit fields.
- Real rules: table rate is per table (`hourlyRate` on each table, already loaded as `branchFloorTables` with `label`, `seats`, `rate`); billing is every started hour with a one-hour minimum; pre-orders get 10% off and are allowed for visits within 6 days (`PRE_ORDER_DISCOUNT_PERCENT`, `PRE_ORDER_WINDOW_DAYS` exported by `booking.ts`); deposits do not exist.
- The server says no-shows are "Never automatic". But the web auto-marks: the `allReservations` computed calls `markReservationNoShow` for any confirmed booking more than 15 minutes late, and local sample items are flipped to `NO_SHOW` the same way. **There is no manual No-show button anywhere in the web.**

**Decisions:**

1. Policies are **not** editable. The screen displays the real fixed rules.
2. No-show is **manual only**. Remove the browser auto-mark; staff press a No-show action.

**Do:**

1. `useWorkspaceReservations.ts`:
   - Delete `autoNoShowMutation` and its export.
   - In `allReservations`, delete the block that fires the mutation and rewrites status/notes for late confirmed bookings (API items), and the equivalent status flip for local items (if T8 has not removed that code yet). Late bookings stay `CONFIRMED`.
   - In `needsYouItems`, change the LATE alert subtitle from `… · Auto-released if 15m late` to `Scheduled HH:MM`. Keep the alert.
   - Add a manual flow mirroring cancel: `showNoShowModal`, `noShowTargetRes`, `noShowReason` (default `Customer did not arrive`), `openNoShow(res)`, a `noShowMutation` calling `markReservationNoShow(selectedBranchId, id, { reason })`, and `submitNoShow()`. On success close and invalidate `['listBranchReservations']`; on error set `actionError` to `No-show failed. Refresh and retry.` Export them.
2. New `MGMT/components/NoShowDialog.vue`, copied from `CancelBookingDialog.vue` with the no-show state and wording. Mount it where `CancelBookingDialog` is mounted (`WEB/features/reservations/pages/StaffReservationsPage.vue`).
3. Add a "No-show" action next to Cancel for `CONFIRMED` bookings whose start time has passed, in `WorkspaceBookings.vue` and in the dashboard's late-arrival item (`WorkspaceDashboard.vue`). If the server rejects (read `Reservation.markNoShow` for its rule), the error shows through `actionError`.
4. `WorkspacePolicies.vue`: delete `venuePolicies`. Take `branchFloorTables` from `useManagementWorkspace()`.
   - "Reservation & billing rules" rows: **Table time** — "Every started hour, one-hour minimum" / "Billed from check-in to checkout at the rate locked when booked."; **Food pre-orders** — `${PRE_ORDER_DISCOUNT_PERCENT}% off` / `Visits within ${PRE_ORDER_WINDOW_DAYS} days; card charged at check-in.`; **No-shows** — "Marked by staff" / "Releases the table, held games and pre-ordered food; the card hold is voided."
   - "Table hourly rates" table: one row per table from `branchFloorTables` (Table label, Seats, Hourly rate), with an `AppEmptyState` when there are none.
   - Remove the TODO. Keep the Read-only badge.
5. Tests: extend `useWorkspaceReservations.spec.ts` — a late confirmed booking is not marked automatically and no request is sent; `submitNoShow` sends the reason and refreshes. Update `apps/web-e2e` cases that relied on auto no-show, if any (search for `No-Show`/`Auto-marked`).

**Out of scope:** Server-side settings, deposits, a scheduled no-show job, changing any server rule.

---

## T11 — Venue settings: save branch details through the existing API

**Marker:** `MGMT/components/WorkspaceSettings.vue:2` — `TODO(settings-api)`. The comment "no backend" is outdated.

**Read first:** `WorkspaceSettings.vue`, `MGMT/useWorkspaceSettings.ts`, `MGMT/context.ts`, `MGMT/useWorkspaceReservations.ts` (`branches` query, `selectedBranch`), `openapi/components/schemas/UpdateBranchRequest.yaml`, `OpeningPeriod.yaml`, `MGMT/components/WorkspacePantryStock.vue` (mutation pattern).

**Facts:**

- Admin-only tab. It binds to a hard-coded object in `useWorkspaceSettings.ts` and its Save button is permanently disabled.
- The endpoint exists: `PATCH /api/v1/branches/{branchId}`, generated as `updateBranch(branchId, body, options?)`, admin only. Body fields, all optional, at least one required: `name` (1–120), `address` (1–500), `phone` (1–40), `openingHours`, `active`, `fulfillsDelivery`.
- `openingHours` is a full replacement list of `{ dayOfWeek: 'MONDAY'…'SUNDAY', opensAt: 'HH:MM', closesAt: 'HH:MM' or '24:00' }`, at most 28 entries, several non-overlapping periods per day allowed, omitted days are closed, `closesAt` later than `opensAt`.
- The server answers `409` when new hours would exclude a confirmed reservation or active rental, `400` for invalid input.
- The workspace context exposes `selectedBranch` (a `Branch`) and `selectedBranchId`. The storefront caches branches under `BRANCHES_QUERY_KEY` exported from `WEB/shared/stores/branch-directory.ts`; the workspace uses `['branches']`.

**Do:**

1. In `WorkspaceSettings.vue`, stop using `venueSettings`. Take `selectedBranch`, `selectedBranchId` and `queryClient` from `useManagementWorkspace()`.
2. Keep a local editable copy (`name`, `address`, `phone`, list of periods) reset from `selectedBranch` whenever the selected branch changes or a save succeeds.
3. Fields: Branch name, Physical address, Phone (new, same `AppField` pattern). Replace the single open/close pair with one row per period: day (`AppSelect` of the seven days), opens (`type="time"`), closes (`type="time"`), an "Until midnight" checkbox that sends `'24:00'` and disables the closes input, and Remove. An "Add period" button appends a row. Sort rows by day then opening time for display.
4. Enable Save only when something changed and every row is complete with closes later than opens. On save send only changed fields (`openingHours` as the whole list when any period changed; omit `phone` when it is empty, since the server requires 1–40 characters). Disable the button while pending.
5. On success invalidate `['branches']` and `BRANCHES_QUERY_KEY`. On failure show the server's message in an `AppMessageBar` and keep the edits.
6. Delete `MGMT/useWorkspaceSettings.ts` and its spread in `MGMT/context.ts` after confirming nothing else reads `venueSettings` (search the repo).
7. Remove the TODO and the HTML comment about not suggesting persistence.
8. Tests: a component spec `MGMT/components/WorkspaceSettings.spec.ts` (mount with a provided workspace and mocked `fetch`, as other specs in `MGMT/` do): fields prefilled from the branch; saving a changed name sends only `name`; a 409 shows the message and keeps edits; "Until midnight" sends `24:00`.

**Out of scope:** `active` and `fulfillsDelivery` toggles, creating branches, table management, fields the server does not have.

---

## T12 — Staff branch assignments (admin manages, managers read)

**Markers:**

- `MGMT/components/WorkspaceStaff.vue:2` — `TODO(staff-admin-api)`
- `WEB/features/workspace/pages/WorkspaceAdministration.vue:2` — `TODO(administration-api)`

**Read first:** `API/branches/BranchAccess.java`, `API/branches/internal/application/BranchAccessService.java`, `API/branches/internal/domain/BranchStaffAssignment.java`, `BranchStaffAssignmentId.java`, `API/branches/internal/persistence/BranchStaffAssignmentRepository.java`, V001 lines 20–34 and 83–95, `API/account/AccountDirectory.java`, `API/account/internal/application/AccountService.java`, `API/account/internal/persistence/AccountIdentityRepository.java`, `API/identity/internal/security/SecurityConfig.java` lines 140–151, `WorkspaceStaff.vue`, `WorkspaceAdministration.vue`, `WEB/features/workspace/routes.ts`.

**Facts:**

- `branch_staff_assignments (branch_id, staff_subject, granted_by, granted_at)`, primary key `(branch_id, staff_subject)`. `staff_subject` is the Keycloak user ID (OIDC `sub`).
- **Nothing in the repository writes that table.** The entity has no public constructor or factory, the repository is only read (`existsById`). Today a row can only be added by SQL.
- Roles (`staff`, `manager`, `admin`) are Keycloak realm roles read from the session. The API has no Keycloak admin client.
- An assignment only has an effect for a user who has the `staff` role; managers and admins operate every branch without one (`BranchAccessService.requireOperator`).
- `AccountDirectory.findBySubject(subject)` returns `AccountContact(accountId, name, phoneNumber)` for a subject that completed onboarding. An account can have several identities (`account_identities`, key `(issuer, subject)`).
- `branches` depends on `identity`, `shared`, contracts. `account` does not depend on `branches`.
- `SecurityConfig` already restricts `/api/v1/admin/**` to `ADMIN`. Nothing lives there yet.
- Shifts, on-duty status and zones (in the sample table) do not exist on the server.

**Decisions:**

1. No shift, on-duty or zone tracking. The staff screen shows **who is assigned to the branch**.
2. The admin finds a person by **searching onboarded accounts by name or phone**. No Keycloak admin integration.
3. **Roles stay in the Keycloak console.** The app only manages branch assignments.
4. **Managers and admins can read** a branch's assignment list; **only admins** grant or revoke.
5. Wiring: **`branches` depends on `account`**, and `AccountDirectory` gains a search method.

**Do — server:**

1. `account`:
   - `AccountDirectory`: add `List<AccountMatch> search(String query, int limit)` and `record AccountMatch(String subject, String name, String phoneNumber)`. One result per identity. Matches when the query (trimmed, at least 2 characters, otherwise an empty list) is contained case-insensitively in "first last" name, or contained in the phone number. Order by name then subject; `limit` capped at 20.
   - Implement in `AccountService` with a JPQL query on `AccountIdentityRepository` joining its `account` (the entity already has that relation). Escape `%` and `_` in the query text.
   - Tests in `AccountServiceTests` / `AccountPersistenceTests`.
2. `branches`:
   - Add `"account"` to `API/branches/package-info.java`.
   - `BranchStaffAssignment`: add `public static BranchStaffAssignment grant(UUID branchId, String staffSubject, String grantedBy, Instant grantedAt)` (verify `BranchStaffAssignmentId`'s constructor/field order first). Keep the no-arg constructor protected.
   - `BranchStaffAssignmentRepository`: add a finder for all rows of a branch ordered by `grantedAt` (verify the embedded-id property path, e.g. `findByIdBranchIdOrderByGrantedAt`).
   - New `API/branches/internal/application/StaffAssignmentsService.java` (scaffold with the generator, `--module=branches --name=StaffAssignments`):
     - `list(branchId)` — `@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")`, 404 for an unknown branch (inactive branches are allowed here); returns each row with name/phone from `AccountDirectory.findBySubject` when present.
     - `grant(branchId, staffSubject)` — `@PreAuthorize("hasRole('ADMIN')")`, `@Transactional`; 404 unknown branch; 400 blank subject or longer than 255; if the row exists return it unchanged; else save `grant(branchId, staffSubject, currentUser.require().subject(), clock.instant())` (inject `Clock` the way `ReservationsService` does).
     - `revoke(branchId, staffSubject)` — admin, `@Transactional`; deletes if present; always succeeds.
     - `searchPeople(query)` — admin; delegates to `AccountDirectory.search(query, 20)`.
   - New `API/branches/internal/web/StaffAssignmentsController.java` implementing the generated API interface.
3. Contract (new files under `openapi/paths/branches/`, registered in `openapi/openapi.yaml`):
   - `GET /api/v1/branches/{branchId}/staff-assignments` — `operationId: listStaffAssignments`; manager+admin (see the note in T9 step 4 about `x-access-policy` values; reuse an existing value if one means manager+admin, otherwise add one consistently). Response `{ items: StaffAssignment[] }`, `StaffAssignment = { staffSubject, name?, phone?, grantedBy, grantedAt }`.
   - `PUT /api/v1/admin/branches/{branchId}/staff-assignments/{staffSubject}` — `operationId: grantStaffAssignment`, `administrator`, CSRF, no body, `200` with the `StaffAssignment`.
   - `DELETE` same path — `operationId: revokeStaffAssignment`, `administrator`, CSRF, `204`.
   - `GET /api/v1/admin/accounts?q=` — `operationId: searchAccounts`, `administrator`, response `{ items: { subject, name, phone }[] }`.
   - `pnpm generate:api`.
4. No migration: the table exists.

**Do — web:**

5. `WorkspaceAdministration.vue` (route `/admin`): keep the identity status line. Add a "Branch staff" section: a branch `AppSelect` (from `listBranches`, all pages via `collectPages` in `WEB/shared/lib/pagination.ts`), the assignment table (Name, Phone, Granted by, Granted, a Remove action with `AppConfirmDialog`), and an "Assign staff" control: a search field calling `searchAccounts` (debounced, min 2 characters) listing matches with an Assign button. After grant or revoke, wait for the server, refetch the list, show server errors. Add a short note under the control: "Branch access applies to accounts with the staff role." Remove the placeholder paragraph and the TODO. This page uses the customer layout today (`routes.ts`); keep its routing and role meta unchanged.
6. `WorkspaceStaff.vue` (manager tab): delete the sample roster, the `StaffMember` type and the shift/zone/status columns. Show the read-only assignment list for the workspace's `selectedBranchId` from `listStaffAssignments` (columns Name, Phone, Granted), with loading, error and empty (`No staff assigned to this branch.`) states. Rename the page title to "Branch staff" and update the label `'staff-roster': 'Staff & Shifts'` in `MGMT/useWorkspaceNavigation.ts` and the matching sidebar text in `MGMT/components/WorkspaceSidebar.vue` to "Branch staff". Remove the TODO.

**Tests:** service tests (grant is idempotent, revoke of a missing row succeeds, unknown branch 404, role checks via `MethodSecurityTests` style); integration: a staff user without assignment gets 403 on a branch operation, 200 after an admin grants, 403 again after revoke; manager can list but gets 403 on grant; search returns onboarded accounts only. Web specs for both screens; update `apps/web-e2e/src/workspace-access.spec.ts` / `management.spec.ts` if they assert the old staff table or the placeholder text.

**Out of scope:** Keycloak admin integration, role editing, creating users, shifts, zones, on-duty status, assigning people who have not completed onboarding.

---

## Not TODOs (leave alone)

- `tools/backend-tools/**` and `tools/list-todos.mjs`: the word TODO there is generator output text, test assertions and the listing tool itself.
- `WEB/features/reservations/components/management-inventory.samples.ts`: sample records for retail stock, rental copies and orders on branches with no server data of that kind. It has no TODO marker and is an intended fallback per `apps/web/AGENTS.md`.

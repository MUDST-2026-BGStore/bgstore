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
- Database: add a new Flyway file in `apps/api/src/main/resources/db/migration/`. V001–V011 exist. Use the next free number at the time you write it (the next item to land uses `V012__<name>.sql`, the one after `V013`, and so on). Never edit an existing migration.
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
| T7  | `InPlayOrderModal.vue:2` `in-play-orders-api`; `useWorkspacePantry.ts:1`, `pantry.samples.ts:1` `pantry-api` | API + Web | —          |
| T8  | `reservations-store.ts:1` `reservation-api`                                                                  | Web       | T7         |
| T9  | `WorkspaceAnalytics.vue:2` `analytics-api`                                                                   | API + Web | T7         |

Suggested order: T7, T8, T9.

Path prefixes used below:

- `API/` = `apps/api/src/main/java/com/chanakanlabs/theparlor/`
- `APITEST/` = `apps/api/src/test/java/com/chanakanlabs/theparlor/`
- `WEB/` = `apps/web/src/`
- `MGMT/` = `apps/web/src/features/reservations/management/`

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
4. `TableBillModal.vue`: remove everything that only served non-live sessions — the local settle (`reservationsStore.settleSessionBill`), the customer self-payment QR block and its `qrcode` import, the staff payment-method picker that recorded nothing, and their CSS. The modal always requires a `rentalId`. If `qrcode` has no other importer after this, remove the dependency from `package.json`.
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

## Not TODOs (leave alone)

- `tools/backend-tools/**` and `tools/list-todos.mjs`: the word TODO there is generator output text, test assertions and the listing tool itself.
- `WEB/features/reservations/components/management-inventory.samples.ts`: sample records for retail stock, rental copies and orders on branches with no server data of that kind. It has no TODO marker and is an intended fallback per `apps/web/AGENTS.md`.

# Handoff — backend/storefront checkpoint and customer reservation wiring

- Date: 2026-11-13
- Repository: `/Users/chanakan5591/Developments/bgstore`
- Branch: `feat/management-workspace-ui`
- Implementation checkpoint: `40b1fa9`

## Read this first

The previous session checked the accumulated uncommitted implementation, fixed failures and two backend correctness bugs, ran the full verification suite, and saved the work in seven commits. All executed checks passed at the final checkpoint. **This is a development checkpoint; customer reservation go-live and legacy-data upgrade compatibility remain unfinished.**

The next likely task is completing the customer reservation wizard and My Reservations against the implemented backend. Resolve the migration-data question before deploying to any environment whose existing business data must survive.

Read `AGENTS.md` and `apps/web/AGENTS.md` before editing. The code and commits are authoritative for implementation details. This file records context, verification limits and continuation guidance.

## User intent and operating constraints

The user asked to check the earlier handoff, account for newer migration/pantry/pre-order/seed work, get checks green, and split the checkpoint into commits. They subsequently explicitly requested this detailed handoff **inside the repository**.

- Prefer complete implementations; preserve existing API/database semantics and module boundaries.
- Research unfamiliar external APIs against installed versions before changing code.
- Ask for consequential architecture, public-contract or migration-policy decisions.
- Keep explanations in code where practical. Additional documentation requires an explicit user request; this handoff is authorized.
- Use Nx with pnpm for project tasks. Discover targets through the `nx-workspace` skill and `pnpm nx show project <name> --json`.
- Edit contracts in `openapi/`, then run `pnpm generate:api`. Never edit generated Java or TypeScript directly.
- Preserve scalar-FK Spring Modulith boundaries, named SPIs and catalog aggregate ownership.
- Add migrations for future schema changes. Existing migration files were preserved during this session.
- Never commit credentials or `.env`. The earlier handoff describes the local development data as disposable; reconfirm the target before destructive operations.
- Do not publish, push or deploy without authorization.

The original handoff was read from `/var/folders/52/c2gtcnvs7wd3s0jl2jp9prd80000gn/T/bgstore-handoff-2026-11-13.md`. That file is temporary and describes an earlier state. In particular, its backend pantry/pre-order/seed TODOs have since been implemented.

## Git checkpoint

Seven commits were created, in this order:

| Commit    | Subject / scope                                                                                                                                                                          |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `9fab90e` | `feat(contract): define card checkout and reservation pantry pre-orders` — OpenAPI sources and generated web bindings                                                                    |
| `c8c12d6` | `feat(seed): populate shared menu and branch pantry stock` — maintenance seed and repeatability assertions                                                                               |
| `ff00ac9` | `feat(web): connect storefront inventory cart checkout and order history` — storefront services/stores/pages, shared branch directory, removal of obsolete cart panel/sample retail data |
| `48c8b0e` | `refactor(web): prepare reservation food picker for branch menus` — explicit picker input, booking/menu helpers and tests, unfinished-work annotations                                   |
| `a2e688e` | `fix(keycloak): configure English and Thai login locales` — realm locale settings and local theme synchronization                                                                        |
| `c8f8564` | `test(e2e): align browser fixtures with live storefront and workspace` — storefront/map fixtures, management interception, authentication tests                                          |
| `40b1fa9` | `feat(api): implement commerce booking rental and pantry workflows` — integrated backend services/SPIs, migrations V004–V009, application tests and correctness fixes                    |

Nothing was pushed. The working tree was clean after these commits, before this handoff was written. The backend commit message explicitly records the legacy-upgrade limitation. Treat the seven-commit tip as the verified integrated checkpoint; intermediate commits were not independently verified.

## Implemented behavior

### Backend

The backend now wires branches/tables, inventory, carts, orders, payments, reservations, availability, rentals, pantry and food pre-orders. Inspect the application services and their tests rather than inferring completeness from an endpoint contract.

Important entry points under `apps/api/src/main/java/com/chanakanlabs/theparlor/`:

- `branches/internal/application/BranchesService.java`, `TablesService.java`, `BranchFloorService.java`.
- `carts/internal/application/CartsService.java`.
- `orders/internal/application/OrderPlacementService.java`, `OrdersService.java`, `OrderViews.java`.
- `payments/Payments.java`, `payments/internal/application/PaymentsService.java` and `PaymentTokensService.java`.
- `payments/internal/gateway/PaymentGateway.java`, `UnconfiguredGateway.java`, and `bogus/BogusGateway.java`.
- `pantry/Pantry.java`, `pantry/internal/application/PantryService.java` and `MenuService.java`.
- `reservations/internal/application/BookingPlanner.java`, `ReservationsService.java`, `ReservationViews.java`, `PreOrderDesk.java`.
- `rentals/internal/application/RentalsService.java`.
- `shared/internal/ApiExceptionHandler.java`, `shared/ProblemException.java`, `shared/KeysetCursor.java`.

Module collaboration uses public interfaces and lower-module named SPIs, including `branches.spi.TableCommitments`, `inventory.spi.CopyCommitments` and `reservations.spi.RentalDesk`. `ModularityTests` verifies dependency direction and access to internals.

Food pre-order behavior:

- Business-wide menu, per-branch stock and append-only stock adjustments.
- Public branch menu: `GET /api/v1/branches/{branchId}/menu`.
- Branch-operator stock adjustment: `POST /api/v1/branches/{branchId}/menu/{menuItemId}/stock-adjustments`.
- Optional `CreateReservationRequest.preOrder`; reservation responses include the priced snapshot and payment summary.
- Booking holds pantry units and authorizes the card. A decline rolls back the booking/holds.
- The server calculates and stores a 10% discount using integer minor units and half-up rounding.
- Check-in fulfills held units and captures payment; cancellation/no-show releases stock and voids authorization.
- Creation and rescheduling enforce the implemented six-day pre-order window. Rescheduling is anchored to the original pre-order creation timestamp.
- Payments reference exactly one order or reservation, enforced by V009.

The configured card gateway remains a development stand-in. `application-dev.yaml` defaults to `bogus`; the normal configuration defaults to `none`. Bogus transactions move no money. Never use bogus for real orders. Actual provider-specific capture deadlines still need to be represented when a real gateway is integrated; a fixed six-day policy is not a universal card-network guarantee.

### Seed

See:

- `apps/api/src/maintenance/java/com/chanakanlabs/theparlor/tools/ReferenceDataSeed.java`
- `apps/api/src/maintenance/resources/seed/reference-data.json`
- `apps/api/src/maintenanceTest/java/com/chanakanlabs/theparlor/tools/ReferenceDataSeedTests.java`

The seed includes the 16 menu items and per-branch pantry stock. Seed repeatability passed against isolated infrastructure. `make seed-dev` resets development business data while retaining accounts; inspect its current behavior before invoking it. This session did not need to rerun the destructive seed against the live development database.

### Storefront

Live API wiring covers branch selection, branch price/stock, server carts, card checkout and My Orders. Server values remain authoritative for price, availability and totals.

Relevant areas:

- `apps/web/src/features/home/api/`
- `apps/web/src/features/home/stores/cart.ts`, `orders.ts`
- `apps/web/src/features/home/pages/GameProduct.vue`, `CartPage.vue`, `CheckoutPage.vue`
- `apps/web/src/shared/stores/branch-directory.ts`, `store-context.ts`
- `apps/web/src/shared/lib/api-errors.ts`, `pagination.ts`

Card details go only to tokenization and must never be persisted or logged. PromptPay, transfers, COD, carrier selection and shipping fees have no working backend flow.

## Corrections made during verification

1. **Authorization-window extension:** `PreOrderDesk.requireReschedulable` previously checked against reschedule time. It now checks against persisted `ReservationPreOrder.createdAt`. The integration regression ages the pre-order, rejects a move beyond the original deadline, and verifies unchanged booking/holds. It failed before the fix and passed afterward.
2. **Branch-edit/booking race:** `BranchFloorService.lockTable` now obtains the branch write lock before the table lock, refreshes cached branch state and rejects an inactive branch. The transaction retains the lock while the booking is committed. The regression coordinates a concurrent branch deactivation and booking; it failed before the fix and passed afterward. Opening-hours edits share the same branch lock, although the concurrency regression specifically exercises deactivation.
3. **Food picker call:** `FoodMenuPicker.items` is now required. The unfinished wizard explicitly supplies `FOOD_MENU_ITEMS` to preserve its documented sample behavior and restore typechecking.
4. **Map tests:** updated obsolete slug selectors to fixture UUIDs. Viewport assertions allow a small floating-point intersection tolerance; the observed fully visible transformed marker had ratio approximately `0.9999993`. Anchoring and overlap assertions remain.
5. **Management fixtures:** the broad `**/api/**` route matcher also matched Vite modules under `features/home/api/`. It now falls through for paths outside the actual `/api/` prefix. All 20 management browser tests passed afterward.
6. **Authentication fixtures:** provision a missing local application profile through the real CSRF-protected API after sign-in, then assert current workspace markup. Cookie, CSRF, role-denial and logout assertions remain.
7. **Keycloak locale setup:** the theme expected English/Thai locale options but the realm had no internationalization settings. Added them to the realm source and local apply script. Ran the existing local theme setup; theme browser tests passed afterward.

Independent read-only reviewers checked backend standards/correctness and handoff/spec coverage. A focused follow-up review accepted the two backend fixes and regression coverage. The legacy-migration findings below remain open.

## Verification performed

Final successful results:

| Check                                      | Result                                                                           |
| ------------------------------------------ | -------------------------------------------------------------------------------- |
| Application backend tests                  | 170 passed, 0 skipped/failures/errors                                            |
| Maintenance tests                          | 9 passed, 0 skipped/failures/errors                                              |
| Web unit tests                             | 102 passed across 20 files                                                       |
| Other workspace unit targets               | Passed for API client, account, auth, UI, backend tooling and AWS infrastructure |
| Full Chromium browser suite                | 48 passed                                                                        |
| Workspace lint/typecheck                   | All applicable targets passed across 8 projects                                  |
| API/web builds                             | Passed                                                                           |
| Contract lint/validation/generation checks | Passed                                                                           |
| Whitespace check                           | Passed                                                                           |

Commands executed successfully included:

```sh
# Initial forced fresh backend verification, including Testcontainers.
pnpm nx run api:test --args='test maintenanceTest --rerun-tasks'

# Final backend verification after regression fixes and Java formatting.
pnpm nx run api:test --args='spotlessApply test maintenanceTest'

CI=true pnpm nx run-many -t test --exclude=api --skipNxCache --output-style=static
pnpm nx run-many -t lint typecheck --skipNxCache --output-style=static
pnpm nx run-many -t build -p api,web --skipNxCache
pnpm nx run web-e2e:e2e
pnpm generate:api
git diff --check
```

Targeted red/green runs used the existing integration test:

```sh
pnpm nx run api:test --args='test --tests "*TheparlorApiApplicationTests.foodPreOrdersHoldPantryStockAndAreChargedOnlyWhenThePartyArrives"'
```

The final full backend test run actually executed the application tests. Maintenance tasks were up-to-date at that final run and had passed in the earlier forced fresh run. Contract validation executed successfully; generation tasks were up-to-date against current inputs. No dependency versions were upgraded.

Gradle reports deprecated features that will be incompatible with Gradle 10. This warning did not fail the current pinned build.

Temporary evidence is under `/tmp/bgstore-checks/`, especially `api-fresh.log`, `api-final.log`, `unit-final.log`, `static-final.log`, `build-final.log`, `e2e-final.log` and `contract-final.log`. These files may disappear. Durable backend reports are generated under `apps/api/build/reports/` and test XML under `apps/api/build/test-results/`.

## Important unresolved migration decision

**Do not interpret fresh-database migration success as a verified populated-database upgrade.**

Review found these concrete compatibility risks:

- `V006__payments_delivery_and_order_snapshots.sql` creates an empty payments table. Existing V003 orders do not acquire payment records. `OrderViews.payment` throws when a payment is absent, and lifecycle operations also expect one. Old rows can therefore make order reads/pages fail.
- `V007__reservation_lifecycle_and_booking_guard.sql` immediately validates required completion/check-in audit fields and a nonempty no-show reason. Valid older completed/no-show reservations can lack those newly required values and prevent migration/startup.
- V007 also introduces a confirmed-table overlap exclusion constraint; inspect existing confirmed reservations before attempting a populated upgrade.

The user was asked whether these migrations have been applied anywhere with data that must be retained. No answer was received before this handoff. Existing migration SQL was deliberately left unchanged, and the risk was disclosed in the checkpoint commit and final response.

Next steps depend on the answer:

1. Establish deployed Flyway versions, checksums and data-retention requirements.
2. Agree on a truthful compatibility policy for legacy payment state and missing audit history. Do not invent successful card authorizations or historical timestamps/reasons.
3. Add an upgrade fixture starting at V003 with existing orders and completed/no-show reservations.
4. Prove startup, reads and allowed lifecycle behavior after upgrade.
5. If an applied migration fails before a later corrective migration could run, plan an explicit operational remediation with the user. A later migration alone cannot fix an earlier migration that never completes.

## Customer reservation implementation still required

The approved product requirements from the earlier session were:

- Live branch/time/table availability and game selection.
- Optional food pre-order from the selected branch's live menu.
- Card authorization at booking, capture at check-in, void on cancellation/no-show.
- Server-applied/stored 10% pre-order discount.
- My Reservations with cancellation, rescheduling and read-only rental charges.
- Hide in-play ordering and QR bill/payment from that customer flow.

The backend substantially supports these requirements. The customer pages do not yet complete them.

### `CustomerReservePage.vue`

Path: `apps/web/src/features/reservations/pages/CustomerReservePage.vue`.

This remains the old large wizard. It still has sample branch/table fallback, browser-local timestamp construction, sample food, QR-payment UI, empty game submission and local optimistic success. The current create call omits the required idempotency header and does not submit a pre-order. Errors can still lead to a misleading local receipt. Do not present this flow as working booking/payment.

Recommended vertical slices:

1. Use the shared branch directory and real availability. Surface loading/error/empty states with no sample fallback.
2. Use branch-local Bangkok date/time helpers and revalidate availability on confirmation.
3. Offer games from time-aware live rental availability and submit selections.
4. Read `getBranchMenu`, map rows through `data/menu.ts`, and pass live items to the food picker. Reset/reconcile selections when branch or menu context changes.
5. Enforce the server's pre-order window in the preview. Treat preview totals as informational; display the returned server snapshot after success.
6. Reuse checkout's tokenization pattern, never store/log PAN/CVC, and submit `preOrder` with the token.
7. Submit with a stable `Idempotency-Key` for a logical attempt; retain it across retries and avoid reusing it for changed request contents. Respect the gateway token lifecycle when designing retry behavior.
8. Advance to receipt only after server success. Preserve useful input and render actionable errors when a request fails.
9. Remove QR payment and local fake-success behavior from the live customer booking flow.

Available preparation:

- `apps/web/src/features/reservations/data/booking.ts` and `.spec.ts`: Bangkok helpers, window/discount preview and baht formatting.
- `apps/web/src/features/reservations/data/menu.ts`: API menu row mapping.
- `FoodMenuPicker.vue`: explicit menu input and per-item availability limits.

### `CustomerReservationsList.vue`

Path: `apps/web/src/features/reservations/pages/CustomerReservationsList.vue`.

It remains local/sample-backed. Wire `listMyReservations`, real cancellation, a reschedule dialog using `rescheduleReservation`, and read-only rental charges through `getRental`. Hide `InPlayOrderModal` and `TableBillModal`. Refresh authoritative state after mutations and enforce owner/operator lifecycle restrictions using server results.

## Other unfinished areas

- Staff workspace backend wiring: floor, bookings, check-in/out, retail stock, rental copies and fulfillment queue. Existing TODOs identify intended endpoint seams.
- UI with no completed backend flow: in-play ordering, QR table bills, analytics, policies, settings and staff administration.
- Existing backend TODOs include account deletion/consistency handling in `MeController` and catalog-offline policy in `GamesService`.
- The earlier handoff noted dead checkout CSS for removed payment methods; inspect before cleanup.
- Sample-data management browser tests establish UI behavior only. They do not prove live staff workflows.

## Local environment and pitfalls

- At verification time, the web server on `http://localhost:5173` and API on `http://localhost:8080` were reachable. They were pre-existing host processes, and may no longer be running.
- The live browser run used those processes. Backend integration tests compiled current source in isolated containers. **Restart the host API before manually verifying the newest backend fixes**; this session did not explicitly restart it after those edits.
- Start source apps with `node tools/run-api.mjs` and `pnpm nx serve web`, or consult `make help` for complete workflows.
- Use `pnpm nx run api:test`, whose wrapper loads local environment settings needed for Docker/Testcontainers on this machine's Lima setup. Plain Gradle may lack the required environment.
- `make keycloak-theme` was run successfully. It syncs local theme/signup/password-reset/mail settings; the updated apply script also syncs locale configuration. Inspect the tool before repeating it.
- Browser authentication tests require a usable local staff account through `E2E_USERNAME` and `E2E_PASSWORD`. Use `make e2e` to load `.env` through `tools/run-e2e.mjs`. Do not print credentials or commit them.
- The live local staff application profile may now exist because authentication tests provision a missing profile. Do not depend on its generated ID.
- Do not assume the live schema version from old handoff notes. Inspect actual Flyway history when version matters.
- Generated client functions omit header parameters from their positional arguments. Supply `Idempotency-Key` and `X-Cart-Token` through the options argument, e.g. `{ headers: { ... } }`.
- Shared frontend code must not import `features/*`; shared stores belong under `apps/web/src/shared/stores`.
- Branches belong to one business; they are not tenants. Preserve explicit staff branch assignments and business-wide administrator/manager semantics.
- Spotless may reflow Java comments. Re-read files before exact text edits after formatting.
- A YAML description containing an unquoted `word: text` can break contract parsing.

## Suggested skills and next-session sequence

Read/invoke available skills through the runtime rather than claiming invocation without execution.

1. `ask-matt` — route the next scoped engineering task.
2. `nx-workspace` — inspect projects/targets before navigation and task execution; `nx-run-tasks` for verification commands.
3. `tdd` — agree on behavioral seams and cover customer booking/API interactions and populated upgrade behavior.
4. `diagnosing-bugs` — use a reproducible failing check for any new regression.
5. `codebase-design` — only if a real interface/state seam needs design; preserve existing module boundaries.
6. `code-review` — review Standards and Spec independently against the checkpoint and the approved reservation requirements.
7. `handoff` — update continuation context when transitioning sessions; honor explicit user direction about file location.

Begin by checking git status and reading this checkpoint's relevant source. Confirm the migration retention question. Then implement one customer reservation vertical slice at a time, test it, and save coherent commits without implying unfinished workflows are live.

## Primary references consulted in the verification session

- Vue typed component props: https://vuejs.org/guide/typescript/composition-api
- Spring Data JPA locking: https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html
- Card authorization/capture windows: https://docs.stripe.com/payments/place-a-hold-on-a-payment-method
- Playwright viewport assertions: https://playwright.dev/docs/api/class-locatorassertions#locator-assertions-to-be-in-viewport
- Keycloak localization: https://www.keycloak.org/ui-customization/localization

Installed versions remain the baseline. Relevant inspected versions included Spring Boot 4.1.1, Spring Modulith 2.1.1, OpenAPI Generator 7.26.0, Nx 23.3.0, pnpm 10.33.0, Orval 8.43.0 and Keycloak 26.8.1. Inspect current manifests/lockfiles before relying on any external API details.

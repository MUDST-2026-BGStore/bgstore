# Pull Request Title

`feat(reservations): add client play history and booking management`

---

## 📌 Summary

This pull request implements the **Client Play History** feature (Story: _View my play history_). Clients can now view their past and upcoming table reservations with status filtering (`All`, `Reserved`, `Completed`, `Cancelled`), view a comprehensive breakdown of each reservation, and cancel eligible upcoming reservations.

The feature includes full end-to-end integration:

1. OpenAPI HTTP contract specification.
2. Java 21 / Spring Boot modular backend with Flyway migration and automated test coverage.
3. Vue 3 frontend matching the design guidelines, complete with Thai/English i18n, responsive design, and Vitest test coverage.
4. Clean reconciliation and merge with the latest `main` branch.

---

## 🚀 Key Changes

### 1. HTTP Contract (`packages/contracts/openapi.yaml`)

- Added `Reservations` tag with endpoints:
  - `GET /reservations`: Paginated list of reservations for authenticated client with optional `status` filter and `page`/`pageSize` parameters.
  - `GET /reservations/{reservationId}`: Detailed reservation record by ID.
  - `POST /reservations/{reservationId}/cancel`: Cancels an upcoming reservation (`Reserved` -> `Cancelled`).
- Added schema definitions for `ReservationStatus`, `ReservationResponse`, and `ReservationListResponse`.
- Regenerated TypeScript SDK & types in `apps/web/src/generated/api`.

### 2. Backend Service (`apps/api`)

- **New Spring Modulith Module `reservations`**:
  - [`ReservationEntity.java`](file:///c:/Users/Prem/Documents/GitHub/bgstore/apps/api/src/main/java/com/chanakanlabs/bgstore/reservations/ReservationEntity.java): JPA Entity tracking booking window, status (`Reserved`, `Completed`, `Cancelled`), party size, associated table ID, pricing, check-in/out times, and cancellation policy.
  - [`JpaReservationRepository.java`](file:///c:/Users/Prem/Documents/GitHub/bgstore/apps/api/src/main/java/com/chanakanlabs/bgstore/reservations/JpaReservationRepository.java): Spring Data JPA repository with client and status-filtered pagination queries.
  - [`ReservationService.java`](file:///c:/Users/Prem/Documents/GitHub/bgstore/apps/api/src/main/java/com/chanakanlabs/bgstore/reservations/ReservationService.java): Business logic enforcing that only upcoming `Reserved` bookings can be cancelled, plus mock seed data initialization for development.
  - [`ReservationController.java`](file:///c:/Users/Prem/Documents/GitHub/bgstore/apps/api/src/main/java/com/chanakanlabs/bgstore/reservations/ReservationController.java): REST endpoints adhering to the OpenAPI contract.
  - [`package-info.java`](file:///c:/Users/Prem/Documents/GitHub/bgstore/apps/api/src/main/java/com/chanakanlabs/bgstore/reservations/package-info.java): Modulith boundary configuration with `@NamedInterface`.
- **Flyway Database Migration**:
  - [`V9__reservations.sql`](file:///c:/Users/Prem/Documents/GitHub/bgstore/apps/api/src/main/resources/db/migration/V9__reservations.sql): Creates `reservations` table and seeds realistic reservation records across all statuses. (Sequenced after `V8__branch_directory_details.sql` from `main`).
- **Tests**:
  - [`ReservationControllerTest.java`](file:///c:/Users/Prem/Documents/GitHub/bgstore/apps/api/src/test/java/com/chanakanlabs/bgstore/reservations/ReservationControllerTest.java): WebMvc tests verifying HTTP serialization, 404 handling, and cancellation endpoints.
  - [`ReservationServiceTest.java`](file:///c:/Users/Prem/Documents/GitHub/bgstore/apps/api/src/test/java/com/chanakanlabs/bgstore/reservations/ReservationServiceTest.java): Unit tests validating business rules and state transitions.

### 3. Frontend Application (`apps/web`)

- **Views & Components**:
  - [`ClientHistoryListPage.vue`](file:///c:/Users/Prem/Documents/GitHub/bgstore/apps/web/src/pages/history/ClientHistoryListPage.vue):
    - Tab bar for quick status filtering (`All`, `Reserved`, `Completed`, `Cancelled`).
    - Responsive card layout displaying booking date, time window, table number, party size, and status badges (`Reserved`: Blue, `Completed`: Green, `Cancelled`: Rose/Pink).
    - Client-side and paginated navigation (`<`, `1`, `2`, `...`, `>`).
    - Direct "View" action button linking to the reservation detail.
  - [`ClientHistoryDetailPage.vue`](file:///c:/Users/Prem/Documents/GitHub/bgstore/apps/web/src/pages/history/ClientHistoryDetailPage.vue):
    - Table thumbnail banner, table metadata (seats, hourly rate), and booking details.
    - Status badge and dynamic action buttons:
      - Only `Reserved` status displays the "Cancel" action.
      - Completed and Cancelled reservations hide the cancel button.
    - Accessible modal for cancellation confirmation.
- **Routing & Auth Guard**:
  - Added `/history` and `/history/:id` routes in [`router.ts`](file:///c:/Users/Prem/Documents/GitHub/bgstore/apps/web/src/router.ts) protected by `meta: { requiresAuth: true }`.
  - Added a fallback [`LoginView.vue`](file:///c:/Users/Prem/Documents/GitHub/bgstore/apps/web/src/views/LoginView.vue) with redirect support.
- **Localization (i18n)**:
  - Added comprehensive English and Thai strings in [`i18n.ts`](file:///c:/Users/Prem/Documents/GitHub/bgstore/apps/web/src/i18n.ts) under the `history` namespace.
- **Frontend Automated Tests**:
  - [`history-screens.spec.ts`](file:///c:/Users/Prem/Documents/GitHub/bgstore/apps/web/src/pages/history/history-screens.spec.ts): 100% passing Vitest test suite covering:
    - Rendering reservation cards with proper badges and metadata.
    - Tab filtering by status (`Reserved`, `Completed`, `Cancelled`).
    - Pagination next/previous controls.
    - Navigating to detail page and verifying details layout.
    - Confirming and dismissing the cancellation modal.
    - Authentication redirection when unauthenticated.

---

## 🧪 Verification & Quality Checks

All monorepo quality gates and Lefthook commit/push hooks have been run and passed:

| Check                              | Command                                                  | Status                                      |
| ---------------------------------- | -------------------------------------------------------- | ------------------------------------------- |
| **Linter & Typecheck**             | `pnpm exec nx run-many -t lint,typecheck`                | ✅ Passed (0 errors, 0 warnings)            |
| **Frontend Unit Tests**            | `pnpm exec vitest run --config apps/web/vite.config.mts` | ✅ Passed (17 test files, 134 tests passed) |
| **Full Workspace Gate**            | `pnpm check`                                             | ✅ Passed                                   |
| **Backend Tests & Modulith Check** | `./apps/api/gradlew -p apps/api test`                    | ✅ Passed                                   |
| **Code Formatting**                | `pnpm format:check` / Spotless                           | ✅ Clean                                    |

---

## 🔍 How to Test Locally

1. **Start the dev servers**:

   ```bash
   pnpm dev:web
   ./apps/api/gradlew -p apps/api bootRun
   ```

2. **Navigate to the Client History view**:
   - Open [http://localhost:4200/history](http://localhost:4200/history) in your browser.
   - Test clicking between tabs: **All**, **Reserved**, **Completed**, and **Cancelled**.
   - Verify pagination buttons when items span multiple pages.

3. **Test Reservation Details & Cancellation**:
   - Click **View** on an upcoming reservation (e.g. `RES-1001` or `RES-1004`).
   - Notice the status badge and click **Cancel**.
   - Confirm cancellation in the modal: verify that the status changes to `Cancelled` and the cancel button disappears.
   - Switch language between English (EN) and Thai (TH) via the header language switcher to verify localization.

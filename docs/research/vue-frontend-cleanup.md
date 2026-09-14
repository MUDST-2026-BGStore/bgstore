# Vue frontend cleanup research

<!-- markdownlint-disable MD013 -->

Date: 2026-09-14
Scope: `apps/web` only
Method: source/configuration inspection plus current official Vue, Vue Router, TanStack Query, Pinia, TypeScript, and eslint-plugin-vue documentation. The findings were then implemented where they were behaviorally safe; the implementation status is recorded below.

## Executive summary

The frontend is already substantially modern: every application SFC uses `<script setup lang="ts">`, Vue Query is on v5 and uses `queryOptions`/`infiniteQueryOptions`, forms use Vue 3.5 `defineModel`, routes use Vue Router 4, and the repository has strict TypeScript, Vite, `vue-tsc`, Vitest, and flat ESLint configuration.

The main concrete cleanup items were:

1. Replace the deprecated Vue Router `next()` guard style in `apps/web/src/router.ts:253-260`.
2. Resolve the API contract drift in `apps/web/src/views/BranchListView.vue:8-13,53-89`; the view casts generated `Branch` values to fields that the OpenAPI contract does not provide.
3. Connect shared field validation messages to their inputs with `aria-describedby`, and associate the history detail labels with their read-only values.
4. Remove the unused Pinia plugin/dependency unless a real client-state store is planned; server state is already handled by TanStack Query.
5. Consolidate route/auth redirects currently split between the router guard and the `App.vue` watcher.
6. Add teardown for the two search debounce timers and consider a shared debounced-ref composable.

The six concrete cleanup items, plus lazy route imports, removal of the legacy Vue shim, and deprecated lint-rule cleanup, were implemented after this audit. The remaining lower-priority typed-route, indexed-access, and stable-key recommendations remain follow-up hardening work.

The remaining items are lower-risk maintainability or hardening work: consider typed routes, trial `noUncheckedIndexedAccess`, and replace index keys in API-backed lists where stable identity is available.

## Dependency and tooling snapshot

Resolved versions from the workspace installation:

| Package               | Resolved version | Assessment                                                                                                                         |
| --------------------- | ---------------: | ---------------------------------------------------------------------------------------------------------------------------------- |
| `vue`                 |           3.5.41 | Current Vue 3.5 line; the codebase already uses `<script setup>` and `defineModel`.                                                |
| `vue-router`          |            4.6.4 | Current Router 4 line; the deprecated `next` callback is still supported but should not be used for new code.                      |
| `@tanstack/vue-query` |          5.102.3 | v5 object syntax and query-options helpers are in use.                                                                             |
| `pinia`               |                — | Removed; no client-state store is needed because server state is handled by TanStack Query and local UI state is component-scoped. |
| `typescript`          |            6.0.3 | Strict mode is enabled through `tsconfig.base.json`.                                                                               |
| `vue-tsc`             |           2.2.12 | The repository has a dedicated SFC typecheck command.                                                                              |
| `eslint-plugin-vue`   |          10.10.0 | Flat recommended rules are enabled and the current lint run passes.                                                                |

The official Vue tooling guidance recommends Vite, `vue-tsc` for SFC type checking, and `eslint-plugin-vue` for SFC-aware linting. This repository follows that toolchain already: [Vue tooling](https://vuejs.org/guide/scaling-up/tooling.html).

## Findings

### P1 — Deprecated Vue Router guard callback (implemented)

Location: `apps/web/src/router.ts:253-260`

The global guard accepts a third `next` argument and calls it on both paths:

```ts
router.beforeEach((to, _from, next) => {
  if (...) return next({ path: '/login', query: { redirect: to.fullPath } })
  next()
})
```

Vue Router documents `next` as the older callback API and recommends returning `false`, a route location, or nothing from the guard. The callback remains supported, but it is a common source of double-resolution mistakes and is marked for removal in the API documentation.

Implementation:

```ts
router.beforeEach((to) => {
  if (to.meta.requiresAuth && !authResolver()) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }
});
```

This is behavior-preserving and should have a focused router test update. Sources: [Navigation guards](https://router.vuejs.org/guide/advanced/navigation-guards.html), [NavigationGuard API](https://router.vuejs.org/api/interfaces/navigationguard.html).

### P1 — Generated API type is being bypassed in the branch screen (implemented)

Locations: `apps/web/src/views/BranchListView.vue:8-13,53-89`; generated contract type `apps/web/src/generated/api/types.gen.ts:83-98`; source contract `packages/contracts/openapi.yaml` (`Branch` schema).

The generated `Branch` type contains `id`, `name`, `address`, `opensAt`, and `closesAt`. `BranchListView` creates a wider local type with `status`, `latitude`, `longitude`, and `phone`, then casts the query result to that type. A TypeScript cast does not add fields to runtime data:

- `branch.status` is absent from the current API response, so `isBranchBookable()` treats every generated branch as bookable because `undefined !== 'INACTIVE'`.
- coordinates are absent, so distance rendering cannot work from this endpoint.
- `status?: 'ACTIVE' | 'INACTIVE' | string` collapses to `string`, losing the intended status union even if the backend later adds it.

Implementation: the contract now publishes `status`, `phone`, `latitude`, and `longitude`; migration `V21__publish_branch_directory_metadata.sql` persists the fields; the API maps them from the branch module; and the generated Vue `Branch` type is used directly without a cast. Existing branches default to `ACTIVE`, while contact details and coordinates remain nullable until recorded.

This resolves the drift by making the actual API shape contract-owned rather than hiding it behind a frontend cast.

The contract-consistent implementation is:

- If these fields are required, add them to `packages/contracts/openapi.yaml`, regenerate the client, and use the generated type without a cast.
- If they are not required, remove the dead metadata and related behavior from the view.
- If a separate enrichment endpoint is intended, model the enrichment as a distinct adapter type and explicitly map API data into it.

This should be treated as correctness/contract work rather than a cosmetic TypeScript cleanup. The repository instruction that the OpenAPI contract is the source of truth is especially important here.

### P1 — Shared validation errors are not programmatically associated with fields (implemented)

Locations: `apps/web/src/components/ui/UiField.vue:7-20`; `apps/web/src/components/ui/UiTextInput.vue:38-45`; `apps/web/src/components/ui/UiSelect.vue:43-58`.

`UiField` renders an error paragraph and its label points at the supplied input ID, but the input/select does not receive an `aria-describedby` pointing to that error. `UiTextInput` only emits `aria-invalid`; it has no prop for an error or described-by ID. As a result, a screen reader can discover the invalid state without necessarily discovering the corresponding message while focused on the field.

Implementation:

- Have `UiField` derive a stable error ID from `inputId`.
- Pass `aria-describedby` to the slotted control, or make the control accept `describedBy`/`errorId` and bind it to the native element.
- Keep `aria-invalid` on the native control when an error exists.
- Add an assertion in the form component test that the native control references the rendered error.

Vue’s accessibility guide specifically recommends matching labels to controls and using `aria-describedby` to connect instructions. Source: [Vue accessibility guidance](https://vuejs.org/guide/best-practices/accessibility.html).

The same audit found a separate instance in `apps/web/src/pages/history/ClientHistoryDetailPage.vue:207-318`: several labels have no `for` attribute and their read-only inputs have no IDs. These values should either use associated `label`/`id` pairs or semantic `<output>` elements if they are display-only rather than editable form controls.

### P2 — Pinia is installed and registered without any store (implemented)

Locations: `package.json:dependencies.pinia`; `apps/web/src/main.ts:3,34`.

There are no `defineStore`, `use...Store`, `storeToRefs`, or other Pinia usages in application code. Registering an empty Pinia instance adds dependency/runtime surface and suggests a state-management convention that the application does not currently use.

The current split is otherwise sensible: TanStack Query owns remote/server state, while local UI state is component-scoped refs/computed values. The unused dependency and `app.use(createPinia())` registration were removed. Pinia’s official documentation describes stores as shared application state defined with `defineStore`; it does not require installing a store plugin without stores. Source: [Pinia core concepts](https://pinia.vuejs.org/core-concepts/index.html).

### P2 — Authentication admission and redirect policy is split across two layers (implemented)

Locations: `apps/web/src/router.ts:253-260`; `apps/web/src/app/App.vue:49-94`.

The router guard checks `requiresAuth` through a mutable test resolver, while `App.vue` watches the current-user query and independently redirects to login, onboarding, or home. This makes route admission and post-navigation correction separate state machines. A mismatch between the resolver, query state, route metadata, or a future route can produce duplicate redirects or a briefly rendered intermediate state.

Implementation: route metadata remains the declarative source of admission policy, while `App.vue` is now the sole session gate. The router no longer owns a second mutable auth resolver/guard, and screen-level tests no longer install a duplicate auth state machine. Backend authorization remains authoritative.

Implementation:

- Keep route policy in one guard/service: public vs authenticated, onboarding-required, and role/permission admission should have one source of truth.
- Let `App.vue` decide layout/presentation only; it should not be the second route policy engine.
- Preserve testability by injecting a small auth/session reader into the guard, rather than exporting a mutable global resolver as the production mechanism.
- Keep server authorization authoritative; frontend route checks remain UX/navigation checks only.

Vue Router guards are explicitly intended for redirecting or cancelling navigation and can resolve asynchronously. Source: [Navigation guards](https://router.vuejs.org/guide/advanced/navigation-guards.html).

### P2 — Two route-level screens are eagerly imported, reducing code splitting (implemented)

Locations: the top-level imports in `apps/web/src/router.ts:7-17` and the route records below them.

`HomeView`, `OnboardingView`, `BranchDetailView`, `BranchListView`, `UserProfileView`, `AccessDeniedView`, `AuthRedirectView`, and `StaffPermissionsView` are statically imported. The other role-specific page components are lazy-loaded through `defineAsyncComponent`.

All direct route components are now lazy-loaded. The existing `defineAsyncComponent` usage remains for components rendered inside `RoleView`; Vue Router specifically distinguishes route lazy loaders from Vue async components and advises not to use async components as the route component itself. Sources: [Vue performance/code splitting](https://vuejs.org/guide/best-practices/performance.html), [Vue Router lazy loading](https://router.vuejs.org/guide/advanced/lazy-loading.html).

This is a measurable optimization, not a reason to restructure all route definitions immediately. Confirm the resulting chunk graph and initial bundle size after the change.

### P2 — Search debounce timers are not disposed on unmount (implemented)

Locations: `apps/web/src/pages/games/GameCataloguePage.vue:23-31`; `apps/web/src/pages/games/GamesInventoryPage.vue:54-62`.

Both components create a `setTimeout` from a watcher but do not register `onBeforeUnmount` cleanup. The callback is small and the impact is usually transient, but it is an avoidable stale callback and differs from the cleanup already present in `BranchListView`, `CountryCodePicker`, and `useActiveSession`.

Search inputs now use a shared `useDebounced` composable that clears timers on scope disposal. Sources: [Vue composables and side-effect cleanup](https://vuejs.org/guide/reusability/composables.html), [Vue watcher cleanup](https://vuejs.org/guide/essentials/watchers.html).

### P3 — Route names and params are stringly typed

Locations: `apps/web/src/router.ts` and calls such as `String(route.params.gameId)` in the game pages.

The app uses named routes in some places but still passes many raw path strings, and route params are manually converted from `unknown`-shaped route values. Vue Router 4.4+ supports a typed route map. A generated typed-route solution is preferable once the route table stabilizes; manual augmentation is possible but tedious and error-prone.

This is a maintainability improvement, not an urgent bug. It would catch typos in route names/paths and make params such as `gameId` explicit at call sites. Source: [Vue Router typed routes](https://router.vuejs.org/guide/advanced/typed-routes.html).

### P3 — `noUncheckedIndexedAccess` is a worthwhile TypeScript hardening trial

Location: `tsconfig.base.json:8-19`.

Strict mode is enabled, but `noUncheckedIndexedAccess` is not. The frontend contains array indexing in views and pagination/calendar rendering. Enabling this option in a trial config or a dedicated application boundary would force those accesses to acknowledge that an index may be absent, which is particularly useful around route/query data and user-controlled pagination.

Do this as a staged hardening change, because it can expose many legitimate but currently implicit bounds assumptions. TypeScript documents that the option adds `undefined` to undeclared indexed fields. Source: [TypeScript `noUncheckedIndexedAccess`](https://www.typescriptlang.org/tsconfig/#noUncheckedIndexedAccess).

### P3 — Stable identity should be preferred over index keys in API-backed content

Locations: `apps/web/src/pages/games/GameCatalogueDetailPage.vue:115-120,386-388`; `apps/web/src/pages/history/ClientHistoryListPage.vue:290-293`; `apps/web/src/components/ui/UiPagination.vue:36`.

The guide steps use `:key="index"`, and pagination entries include the position in the key. Static skeletons and fixed decorative dots are fine, but API-backed or locale-dependent content should use a stable primitive identity wherever one exists. The guide step mapper currently drops any source identity and returns only title/body. If steps later become editable/reorderable or contain stateful children, index keys can cause Vue to reuse the wrong DOM/component state.

Preserve a stable source key when the contract provides one; otherwise treat the list as intentionally presentational and add a short comment/test documenting that constraint. Source: [Vue list rendering and `key`](https://vuejs.org/guide/essentials/list.html).

### P3 — The hand-written `*.vue` module shim should be validated or removed (implemented)

Location: `apps/web/src/vue-shims.d.ts:1-5`.

The repository has the official Vite Vue plugin and uses `vue-tsc`, both of which provide SFC tooling. The custom declaration is a legacy-style blanket `*.vue` declaration returning `ReturnType<typeof defineComponent>`. It may be redundant and can be less precise than the SFC-aware tooling, especially for prop inference across component imports.

The shim was removed after successful `vue-tsc`, build, lint, unit-test, and E2E verification. The project now relies on `@vitejs/plugin-vue` plus `vue-tsc`. Source: [Vue tooling](https://vuejs.org/guide/scaling-up/tooling.html), [Vue TypeScript with Composition API](https://vuejs.org/guide/typescript/composition-api.html).

## TanStack Vue Query assessment

No v4-style query overloads or deprecated query callbacks were found in application source. The query layer is already using the preferred v5 patterns:

- `queryOptions` and `infiniteQueryOptions` co-locate keys and functions in `apps/web/src/queries/*.ts`.
- Query keys include the filter object used by the request.
- `placeholderData` is used for paginated/infinite transitions.
- Mutations invalidate related queries in `onSuccess`, which is the documented v5 pattern.

Potential follow-up is consistency rather than migration: the pages wrap complete option objects in `computed`, for example `useQuery(computed(() => gameQueryOptions(gameId.value)))`. This is valid reactive usage, but TanStack’s Vue guidance also supports reactive getters for simple derived inputs. Standardizing on one local convention can reduce visual noise; do not change this mechanically without retaining reactivity. Sources: [TanStack Query options](https://tanstack.com/query/latest/docs/framework/vue/guides/query-options), [Vue reactivity in TanStack Query](https://tanstack.com/query/latest/docs/framework/vue/reactivity), [Invalidation from mutations](https://tanstack.com/query/latest/docs/framework/vue/guides/invalidations-from-mutations).

One important rule from the same guidance is already respected by the game form: query results are treated as immutable and copied into local editable form state instead of binding `v-model` directly to cached data.

## ESLint assessment

The repository already enables `eslint-plugin-vue`’s flat recommended configuration, and `pnpm exec eslint apps/web/src --max-warnings=0` passed during this audit. All application SFCs use `<script setup lang="ts">`; no Options API, Vue 2 APIs, `v-html`, or unsafe `v-if`/`v-for` combinations were found in application source.

Recommended next step is not to add arbitrary stylistic rules, but to consider targeted rules for the findings above and their likely regressions:

- `vue/require-explicit-emits` (already part of the recommended Vue 3 guidance; keep it enabled).
- accessibility rules from the project’s chosen a11y plugin, if one is added deliberately.
- a query-specific ESLint plugin only if the team wants automated checks for unstable query dependencies and query option conventions.

The official plugin documents the recommended flat configuration and its Vue 3 essential/strongly-recommended rules. Sources: [eslint-plugin-vue user guide](https://eslint.vuejs.org/user-guide/), [available rules](https://eslint.vuejs.org/rules/), [official release history](https://github.com/vuejs/eslint-plugin-vue/releases).

## Suggested implementation order

1. Fix the branch contract/type drift and add regression coverage for the actual API shape. (done)
2. Replace `next()` with return-style router guards and consolidate auth redirect ownership. (done)
3. Repair shared field error associations and history detail label/value associations. (done)
4. Dispose debounce timers through one tested composable. (done)
5. Remove unused Pinia. (done)
6. Lazy-load route components and measure the bundle. (done)
7. Stage typed routes, `noUncheckedIndexedAccess`, and stable-key cleanup separately.

## Verification baseline

The audit and implementation checks completed successfully:

```text
pnpm exec eslint apps/web/src --max-warnings=0
pnpm exec vue-tsc --noEmit -p apps/web/tsconfig.app.json
```

The broader checks also passed: `pnpm check`, `pnpm build`, and `pnpm e2e` with the documented Lima Docker socket settings. The API suite passed independently with 161 Testcontainers-backed tests. The repository-wide Nx formatter check still reports four unrelated dirty files (`.vscode/settings.json`, `pnpm-lock.yaml`, the Keycloak `bgstore.js`, and the Grafana dashboard JSON); all relevant touched application, contract, and documentation files pass Prettier.

# Web agent guide

This Vue 3/Vite SPA uses TanStack Query for server state and the generated client in `src/generated/api` for HTTP types and calls.

- Treat `src/generated/api` as read-only. Change `packages/contracts/openapi.yaml`, run `pnpm nx run contracts:generate`, and inspect the generated diff.
- Keep OIDC tokens out of browser code. Requests use the same-origin BFF session; preserve CSRF and credential behavior.
- Reuse existing UI primitives and preserve English/Thai content, Bangkok time, THB display, keyboard access, and responsive layouts.
- Co-locate focused Vitest coverage with changed components/features. Browser journeys belong in sibling project `apps/web-e2e` when behavior crosses routing, authentication, or process boundaries.

Fast loop: `pnpm nx test @mudst-2026-bgstore/web --run src/path/file.spec.ts`. Web gate: `.agents/bin/verify web`; add `.agents/bin/verify e2e` for routes or user journeys. Render and inspect affected visual states for appearance changes.

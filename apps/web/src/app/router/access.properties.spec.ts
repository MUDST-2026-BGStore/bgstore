import fc from 'fast-check';
import { expect, it } from 'vitest';
import { pageAccess, safeReturnPath } from './access';

const ORIGIN = 'https://store.example';

it('only ever returns a path on this site, unchanged', () => {
  fc.assert(
    fc.property(
      fc.oneof(
        fc.string(),
        fc.webPath(),
        fc.webUrl(),
        fc.string().map((tail) => `/${tail}`),
        fc.string().map((tail) => `//${tail}`),
        fc.string().map((tail) => `/\\${tail}`),
      ),
      (value) => {
        const result = safeReturnPath(value);
        if (result === null) return;
        expect(result).toBe(value);
        // Wherever a browser resolves it, it stays on this origin.
        expect(new URL(result, ORIGIN).origin).toBe(ORIGIN);
        expect(new URL(result, ORIGIN).pathname).not.toBe('/login');
      },
    ),
    { numRuns: 2000 },
  );
});

it('never lets a signed-out visitor into a page that needs a sign-in or role', () => {
  const role = fc.constantFrom('staff', 'manager', 'admin', 'customer');
  fc.assert(
    fc.property(
      fc.boolean(),
      fc.array(role),
      fc.boolean(),
      fc.array(role),
      (authenticated, roles, requiresAuth, required) => {
        const access = pageAccess(
          { authenticated, roles },
          { requiresAuth, roles: required },
        );
        const restricted = requiresAuth || required.length > 0;
        if (!authenticated && restricted) expect(access).toBe('login');
        if (access === 'allow' && required.length)
          expect(required.some((r) => roles.includes(r))).toBe(true);
        if (!restricted) expect(access).toBe('allow');
      },
    ),
  );
});

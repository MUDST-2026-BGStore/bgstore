import { watch } from 'vue';
import type { Router } from 'vue-router';

import { login, useAuth } from '@theparlor/auth';
import { clearAccount, ensureAccount, useAccount } from '@theparlor/account';

import { claimProtectedState } from '../../shared/lib/protected-state';
import { pageAccess, safeReturnPath, workspaceDestination } from './access';
import { useLocale } from '../../shared/lib/locale';

const returnPathKey = 'theparlor.returnTo';
const onboardingRoute = 'AccountOnboarding';

export function installAuthGuards(router: Router): void {
  router.beforeEach(async (to) => {
    let identity = useAuth().value;

    // 1. Account gate applies to every authenticated user.
    if (identity.authenticated) {
      const subject = identity.subject;
      const account = await ensureAccount().catch((error: unknown) => {
        const current = useAuth().value;
        if (current.authenticated && current.subject === subject) throw error;
        return null;
      });

      // The session may have changed while the request was pending.
      identity = useAuth().value;
      if (identity.authenticated && identity.subject !== subject) {
        return to.fullPath;
      }

      // Missing account: only onboarding is accessible.
      if (identity.authenticated && account?.status === 'missing') {
        if (to.name === onboardingRoute) return true;

        return {
          name: onboardingRoute,
          replace: true,
        };
      }

      // Existing account: onboarding is unnecessary.
      if (
        identity.authenticated &&
        account?.status === 'ready' &&
        to.name === onboardingRoute
      ) {
        return {
          name: workspaceDestination(identity.roles),
          replace: true,
        };
      }
    }

    // 2. Existing login flow.
    if (to.name === 'AuthLogin') {
      const next =
        safeReturnPath(to.query['redirect']) ||
        safeReturnPath(sessionStorage.getItem(returnPathKey));

      sessionStorage.removeItem(returnPathKey);

      if (identity.authenticated) {
        return (
          next || {
            name: 'HomeIndex',
          }
        );
      }

      if (next) {
        sessionStorage.setItem(returnPathKey, next);
      }

      await login(useLocale().locale.value);
      return false;
    }

    // 3. Existing authorization checks.
    const access = pageAccess(identity, to.meta);

    if (access === 'login') {
      return {
        name: 'AuthLogin',
        query: { redirect: to.fullPath },
      };
    }

    if (access === 'forbidden') {
      return { name: 'AuthForbidden' };
    }

    return true;
  });

  // React to session changes.
  watch(
    () => [useAuth().value.authenticated, useAuth().value.subject] as const,
    ([authenticated, subject], [wasAuthenticated]) => {
      clearAccount();
      claimProtectedState(authenticated ? subject : null);
      const route = router.currentRoute.value;

      if (!authenticated && wasAuthenticated) {
        if (route.meta.requiresAuth || route.meta.roles?.length) {
          void router.replace({
            name: 'AuthLogin',
            query: { redirect: route.fullPath },
          });
        }
      } else if (
        authenticated &&
        pageAccess(useAuth().value, route.meta) === 'forbidden'
      ) {
        // Another account took over this tab; it must not keep a page it cannot open.
        void router.replace({ name: 'AuthForbidden' });
      }
    },
    { flush: 'sync' },
  );

  // React to account changes without requiring navigation.
  watch(
    () => useAccount().value.status,
    (status) => {
      const identity = useAuth().value;
      const route = router.currentRoute.value;

      if (!identity.authenticated) return;

      if (status === 'missing' && route.name !== onboardingRoute) {
        void router.replace({ name: onboardingRoute });
      }

      if (status === 'ready' && route.name === onboardingRoute) {
        void router.replace({
          name: workspaceDestination(identity.roles),
        });
      }
    },
  );
}

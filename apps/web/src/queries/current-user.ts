import { queryOptions } from '@tanstack/vue-query';
import { getCurrentUser } from '../generated/api/sdk.gen';
import type { ApplicationRole } from '../generated/api/types.gen';

/**
 * Who is using the app: the signed-in user, or `null` for a guest. A missing
 * session is not a failure, because a guest can still open the public screens.
 */
export const currentUserQueryOptions = () =>
  queryOptions({
    queryKey: ['current-user'] as const,
    // Authentication state drives the application shell; retrying it behind
    // the shell can leave a public page waiting on a stale guest decision.
    retry: false,
    queryFn: async () => {
      const { data, error, response } = await getCurrentUser();
      if (response?.status === 401) {
        return null;
      }
      if (error !== undefined) {
        throw error;
      }
      return data;
    },
  });

/**
 * Whether the user works in the store rather than visits it. This only picks
 * which screens to present; the API enforces what each role may do.
 */
export function hasStaffAccess(roles: readonly ApplicationRole[]): boolean {
  return roles.includes('STAFF') || roles.includes('MANAGER');
}

/**
 * Where the BFF starts a Keycloak round trip that lands back on `returnTo`.
 * `signUp` opens the registration form instead of the sign-in form.
 */
export function signInHref(
  returnTo: string,
  options: { signUp?: boolean } = {},
): string {
  const href = `/oauth2/authorization/keycloak?returnTo=${encodeURIComponent(returnTo)}`;
  return options.signUp ? `${href}&signup` : href;
}

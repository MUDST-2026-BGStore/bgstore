import { queryOptions } from '@tanstack/vue-query';
import {
  getCurrentUser,
  listStaffBranchAssignments,
  replaceStaffBranchAssignments,
} from '../generated/api/sdk.gen';
import type { ReplaceStaffBranchAssignmentsRequest } from '../generated/api/types.gen';
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

export function hasManagerAccess(roles: readonly ApplicationRole[]): boolean {
  return roles.includes('MANAGER');
}

export async function listStaffAssignments() {
  const { data } = await listStaffBranchAssignments({ throwOnError: true });
  return data.items;
}

export async function replaceStaffAssignments(
  staffSubject: string,
  body: ReplaceStaffBranchAssignmentsRequest,
) {
  const { data } = await replaceStaffBranchAssignments({
    path: { staffSubject },
    body,
    throwOnError: true,
  });
  return data;
}

/**
 * Where the BFF starts the app's authentication round trip that lands back on
 * `returnTo`. `signUp` opens the registration form instead of sign-in.
 */
export function authStartHref(
  returnTo: string,
  options: { signUp?: boolean } = {},
): string {
  const path = options.signUp ? '/auth/sign-up' : '/auth/sign-in';
  return `${path}?returnTo=${encodeURIComponent(returnTo)}`;
}

/**
 * Starts a browser-navigation logout so the BFF can redirect through Keycloak's RP-initiated
 * logout endpoint. The BFF returns the redirect target as plain text because a fetch would follow
 * the redirect without moving the browser, leaving the Keycloak SSO cookie alive.
 */
export async function logout(): Promise<void> {
  const csrfToken = document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith('XSRF-TOKEN='))
    ?.split('=')[1];
  const response = await fetch('/logout', {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(csrfToken ? { 'X-XSRF-TOKEN': decodeURIComponent(csrfToken) } : {}),
    },
  });

  if (!response.ok) {
    throw new Error(`Logout failed with status ${response.status}`);
  }

  const redirectUrl = (await response.text()).trim();
  if (!redirectUrl) {
    throw new Error('Logout response did not include a redirect target');
  }

  window.location.assign(redirectUrl);
}

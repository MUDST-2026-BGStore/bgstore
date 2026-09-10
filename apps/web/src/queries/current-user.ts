import { queryOptions } from '@tanstack/vue-query';
import { getCurrentUser } from '../generated/api/sdk.gen';
import type { ApplicationRole } from '../generated/api/types.gen';

export const currentUserQueryOptions = () =>
  queryOptions({
    queryKey: ['current-user'] as const,
    queryFn: async () => {
      const { data } = await getCurrentUser({ throwOnError: true });
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

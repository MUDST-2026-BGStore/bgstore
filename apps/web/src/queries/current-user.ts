import { queryOptions } from '@tanstack/vue-query';
import { getCurrentUser } from '../generated/api/sdk.gen';

export const currentUserQueryOptions = () =>
  queryOptions({
    queryKey: ['current-user'] as const,
    queryFn: async () => {
      const { data } = await getCurrentUser({ throwOnError: true });
      return data;
    },
  });

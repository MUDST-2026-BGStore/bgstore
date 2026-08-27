import { queryOptions } from '@tanstack/vue-query';
import { getHello } from '../generated/api/sdk.gen';

export const helloQueryOptions = () =>
  queryOptions({
    queryKey: ['hello'] as const,
    queryFn: async () => {
      const { data } = await getHello({ throwOnError: true });
      return data;
    },
  });

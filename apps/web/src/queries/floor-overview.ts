import { queryOptions } from '@tanstack/vue-query';
import { getFloorOverview } from '../generated/api/sdk.gen';
import type {
  FloorOverviewResponse,
  GetFloorOverviewData,
} from '../generated/api/types.gen';

export type FloorOverviewQuery = NonNullable<GetFloorOverviewData['query']>;

export const floorOverviewQueryOptions = (query: FloorOverviewQuery) =>
  queryOptions({
    queryKey: ['floor-overview', query] as const,
    queryFn: async (): Promise<FloorOverviewResponse> => {
      const { data } = await getFloorOverview({ query, throwOnError: true });
      return data;
    },
    // Keeps the current page on screen while the next one loads.
    placeholderData: (previous) => previous,
  });

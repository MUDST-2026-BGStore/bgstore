import { queryOptions } from '@tanstack/vue-query';
import {
  cancelReservation,
  getReservation,
  listReservations,
} from '../generated/api/sdk.gen';
import type {
  CancelReservationResponse,
  GetReservationResponse,
  ListReservationsData,
  ReservationListResponse,
} from '../generated/api/types.gen';

export type ReservationListQuery = NonNullable<ListReservationsData['query']>;

/** The history design shows four records per page. */
export const reservationsPageSize = 4;

export const reservationsQueryOptions = (query: ReservationListQuery) =>
  queryOptions({
    queryKey: ['reservations', 'list', query] as const,
    queryFn: async (): Promise<ReservationListResponse> => {
      const { data } = await listReservations({ query, throwOnError: true });
      return data;
    },
    placeholderData: (previous) => previous,
  });

export const reservationQueryOptions = (reservationId: string) =>
  queryOptions({
    queryKey: ['reservations', 'detail', reservationId] as const,
    enabled: reservationId.length > 0,
    queryFn: async (): Promise<GetReservationResponse> => {
      const { data } = await getReservation({
        path: { reservationId },
        throwOnError: true,
      });
      return data;
    },
    retry: false,
  });

export async function cancelReservationRequest(
  reservationId: string,
): Promise<CancelReservationResponse> {
  const { data } = await cancelReservation({
    path: { reservationId },
    throwOnError: true,
  });
  return data;
}

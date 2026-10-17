import { queryOptions } from '@tanstack/vue-query';
import {
  cancelReservation,
  createReservation,
  getReservationAvailability,
  getReservation,
  listReservations,
  listClients,
  listStaffReservations,
} from '../generated/api/sdk.gen';
import type {
  CancelReservationResponse,
  GetReservationResponse,
  ListReservationsData,
  ListStaffReservationsData,
  ReservationListResponse,
  StaffReservationListResponse,
  CreateReservationRequest,
} from '../generated/api/types.gen';

export type ReservationListQuery = NonNullable<ListReservationsData['query']>;
export type StaffReservationsQuery = NonNullable<
  ListStaffReservationsData['query']
>;

/** The history design shows four records per page. */
export const reservationsPageSize = 4;

/** The staff list design shows twenty records per page. */
export const staffReservationsPageSize = 20;

export const reservationAvailabilityQueryOptions = (query: {
  branch: string;
  date: string;
  startTime: string;
  endTime: string;
  partySize: number;
}) =>
  queryOptions({
    queryKey: ['reservations', 'availability', query] as const,
    enabled: query.branch.length > 0 && query.date.length > 0,
    queryFn: async () => {
      const { data } = await getReservationAvailability({
        query,
        throwOnError: true,
      });
      return data;
    },
    staleTime: 15_000,
  });

export const reservationClientsQueryOptions = (enabled = true) =>
  queryOptions({
    queryKey: ['clients', 'reservation-lookup'] as const,
    enabled,
    queryFn: async () => {
      const { data } = await listClients({ throwOnError: true });
      return data;
    },
    staleTime: 60_000,
  });

export async function createReservationRequest(body: CreateReservationRequest) {
  const { data } = await createReservation({ body, throwOnError: true });
  return data;
}

export const reservationsQueryOptions = (query: ReservationListQuery) =>
  queryOptions({
    queryKey: ['reservations', 'list', query] as const,
    queryFn: async (): Promise<ReservationListResponse> => {
      const { data } = await listReservations({ query, throwOnError: true });
      return data;
    },
    placeholderData: (previous) => previous,
  });

/** Staff or manager only: reservations across the caller's accessible branches. */
export const staffReservationsQueryOptions = (query: StaffReservationsQuery) =>
  queryOptions({
    queryKey: ['reservations', 'staff-list', query] as const,
    queryFn: async (): Promise<StaffReservationListResponse> => {
      const { data } = await listStaffReservations({
        query,
        throwOnError: true,
      });
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

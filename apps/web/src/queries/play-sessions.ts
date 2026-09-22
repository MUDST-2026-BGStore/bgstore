import { queryOptions } from '@tanstack/vue-query';
import {
  getActiveSession,
  requestSessionAssistance as requestSessionAssistanceCall,
} from '../generated/api/sdk.gen';
import type {
  ActiveSessionResponse,
  SessionAssistanceKind,
} from '../generated/api/types.gen';

/**
 * The client's checked-in session, or `null` when there is none. A missing
 * session is a normal state, not a failure, so it is not retried.
 */
export const activeSessionQueryOptions = () =>
  queryOptions({
    queryKey: ['play-sessions', 'active'] as const,
    retry: false,
    queryFn: async (): Promise<ActiveSessionResponse | null> => {
      const { data, error, response } = await getActiveSession();
      if (response?.status === 404) {
        return null;
      }
      if (error !== undefined) {
        throw error;
      }
      return data ?? null;
    },
    // Staff confirm the final charge out of band, so the screen re-reads the
    // session rather than trusting a client-side estimate.
    refetchInterval: 30_000,
  });

/**
 * Asks staff for help or to settle the session. The `requestId` makes a retry
 * resolve to the original request instead of recording a second one.
 */
export async function requestSessionAssistance(
  reservationId: string,
  kind: SessionAssistanceKind,
  requestId: string = crypto.randomUUID(),
) {
  const { data } = await requestSessionAssistanceCall({
    path: { reservationId },
    body: { kind, requestId },
    throwOnError: true,
  });
  return data;
}

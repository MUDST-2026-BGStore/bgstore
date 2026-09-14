import type { ActiveSessionSnapshot } from './active-session-types';

/** Temporary client-session data until the active-session endpoint is available. */
export const createActiveSessionFixture = (
  now = new Date(),
): ActiveSessionSnapshot => ({
  id: 'session-local-preview',
  locationName: 'Central — Rama I Road, Pathum Wan',
  tableName: 'Table 12',
  startedAt: new Date(now.getTime() - 105 * 60 * 1000).toISOString(),
  partySize: 2,
  hourlyRatePerPerson: 50,
  accumulatedFee: 250,
  currency: 'THB',
});

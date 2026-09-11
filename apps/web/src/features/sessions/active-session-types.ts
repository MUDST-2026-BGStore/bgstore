export interface ActiveSessionSnapshot {
  id: string;
  locationName: string;
  tableName: string;
  startedAt: string;
  partySize: number;
  hourlyRatePerPerson: number;
  accumulatedFee: number;
  currency: 'THB';
}

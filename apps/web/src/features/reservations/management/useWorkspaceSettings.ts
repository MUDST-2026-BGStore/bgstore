import { ref } from 'vue';

export function useWorkspaceSettings() {
  // Existing local configuration scaffold; persistence requires a backend endpoint.
  const venueSettings = ref({
    branchName: 'The Parlor · Siam Paragon',
    address: '991 Rama I Rd, Pathum Wan, Bangkok 10330',
    phone: '+66 2 123 4567',
    openTime: '10:00',
    closeTime: '23:00',
    totalTables: 20,
    peakHourSurcharge: false,
  });
  return { venueSettings };
}

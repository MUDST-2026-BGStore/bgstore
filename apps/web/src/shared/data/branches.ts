/**
 * Approximate map positions for the seeded branches, keyed by branch name. The API
 * has no coordinates, so these stay a frontend-only approximation of the seed
 * addresses in apps/api/src/maintenance/resources/seed/reference-data.json; a
 * branch missing here is simply listed without a map pin.
 */
const coordinates: Record<string, { lat: number; lng: number }> = {
  siam: { lat: 13.7449, lng: 100.5338 },
  ari: { lat: 13.7796, lng: 100.5446 },
  riverside: { lat: 13.7226, lng: 100.5136 },
};
export const branchCoordinates = (name: string) =>
  coordinates[name.trim().toLocaleLowerCase()];

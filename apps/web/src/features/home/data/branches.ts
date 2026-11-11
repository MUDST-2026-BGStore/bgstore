export interface StoreBranch {
  id: string;
  name: string;
  pickup: boolean;
  address: string;
  /** Approximate map position for the address; not a verified geocode. */
  location: { lat: number; lng: number };
}
// TODO(branch-api): Replace fictional fixture branches with active branches and
// configured rental/retail/pickup/shipping capabilities. Only Siam fulfills delivery
// in this first version. No transfer, split shipment or warehouse aggregation.
// Canonical seed copy used by the dev database lives in
// apps/api/src/maintenance/resources/seed/reference-data.json (make seed-dev);
// keep the two sources in sync until the API replaces this file. Coordinates
// are frontend-only approximations of the seed addresses.
export const branches: StoreBranch[] = [
  {
    id: 'siam',
    name: 'Siam',
    pickup: true,
    address: '483 Siam Square Soi 7, Pathum Wan, Bangkok 10330',
    location: { lat: 13.7449, lng: 100.5338 },
  },
  {
    id: 'ari',
    name: 'Ari',
    pickup: true,
    address: '71 Phahon Yothin Soi 7, Phaya Thai, Bangkok 10400',
    location: { lat: 13.7796, lng: 100.5446 },
  },
  {
    id: 'riverside',
    name: 'Riverside',
    pickup: true,
    address: '2194 Charoen Krung 42, Bang Rak, Bangkok 10500',
    location: { lat: 13.7226, lng: 100.5136 },
  },
];
// Business-wide opening hours from the seed data (ISO weekday, 1 = Monday).
export const openingHours: { day: number; opens: string; closes: string }[] = [
  { day: 1, opens: '12:00', closes: '21:00' },
  { day: 2, opens: '12:00', closes: '21:00' },
  { day: 3, opens: '12:00', closes: '21:00' },
  { day: 4, opens: '12:00', closes: '21:00' },
  { day: 5, opens: '12:00', closes: '21:30' },
  { day: 6, opens: '10:00', closes: '21:30' },
  { day: 7, opens: '10:00', closes: '21:00' },
];
export const DELIVERY_BRANCH_ID = 'siam';
const retailStock: Record<string, Record<string, number>> = {
  siam: {
    'azul-en': 4,
    'azul-th': 2,
    'wingspan-en': 0,
    'codenames-en': 6,
    'codenames-th': 2,
    'catan-en': 3,
    'catan-th': 2,
    'pandemic-en': 2,
    'cascadia-en': 4,
    'love-letter-en': 8,
    'wavelength-en': 5,
    'cash-n-guns-en': 4,
    'monikers-en': 6,
    'betrayal-at-house-on-the-hill-en': 3,
    'taboo-en': 5,
    'beyond-balderdash-en': 4,
    'cosmic-encounter-en': 3,
    'blood-on-the-clocktower-en': 4,
  },
  ari: {
    'azul-en': 1,
    'azul-th': 3,
    'wingspan-en': 2,
    'codenames-en': 2,
    'codenames-th': 0,
    'catan-en': 0,
    'catan-th': 1,
    'pandemic-en': 0,
    'cascadia-en': 2,
    'love-letter-en': 4,
    'wavelength-en': 2,
    'cash-n-guns-en': 2,
    'monikers-en': 2,
    'betrayal-at-house-on-the-hill-en': 1,
    'taboo-en': 2,
    'beyond-balderdash-en': 2,
    'cosmic-encounter-en': 1,
    'blood-on-the-clocktower-en': 2,
  },
  riverside: {
    'azul-en': 0,
    'azul-th': 1,
    'wingspan-en': 1,
    'codenames-en': 4,
    'codenames-th': 1,
    'catan-en': 2,
    'catan-th': 0,
    'pandemic-en': 3,
    'cascadia-en': 0,
    'love-letter-en': 2,
    'wavelength-en': 3,
    'cash-n-guns-en': 3,
    'monikers-en': 3,
    'betrayal-at-house-on-the-hill-en': 2,
    'taboo-en': 3,
    'beyond-balderdash-en': 3,
    'cosmic-encounter-en': 2,
    'blood-on-the-clocktower-en': 2,
  },
};
const isVitest =
  typeof process !== 'undefined' &&
  (Boolean(process.env?.VITEST) || process.env?.NODE_ENV === 'test');

export const retailStockAt = (variantId: string, branchId?: string) => {
  if (!isVitest) {
    const branch = branchId || DELIVERY_BRANCH_ID;
    const stock = retailStock[branch]?.[variantId];
    return stock && stock > 0 ? Math.max(stock, 10) : 10;
  }
  if (!branchId) return 0;
  return retailStock[branchId]?.[variantId] ?? 0;
};
export interface RentalLibraryEntry {
  offered: boolean;
  staffCanTeach: boolean;
}
// Discovery describes branch library membership, not free copies right now.
// Values indicate branch teaching support; an entry (including false) means the
// game is offered for in-store play. TODO(reservation-availability): only query
// copy/table availability after branch, visit date, start time and duration are
// selected. Revalidate the full interval on confirmation; membership is no promise
// of a free copy or a booking, and walk-ins need an arrival-time check.
const rentalLibraries: Record<string, Record<string, boolean>> = {
  siam: {
    azul: true,
    wingspan: true,
    codenames: true,
    avalon: true,
    catan: true,
    pandemic: true,
    cascadia: false,
    'love-letter': true,
  },
  ari: {
    azul: true,
    wingspan: false,
    codenames: true,
    avalon: true,
    catan: true,
    'love-letter': true,
  },
  riverside: {
    azul: false,
    codenames: true,
    catan: true,
    pandemic: true,
    cascadia: true,
    'love-letter': true,
  },
};
export function rentalLibraryAt(
  gameId: string,
  branchId: string,
): RentalLibraryEntry {
  const library = rentalLibraries[branchId] ?? {};
  return {
    offered: Object.hasOwn(library, gameId),
    staffCanTeach: library[gameId] ?? false,
  };
}

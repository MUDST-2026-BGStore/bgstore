export interface StoreBranch {
  id: string;
  name: string;
  pickup: boolean;
}
// TODO(branch-api): Replace fictional fixture branches with active branches and
// configured rental/retail/pickup/shipping capabilities. Only Siam fulfills delivery
// in this first version. No transfer, split shipment or warehouse aggregation.
// Canonical seed copy used by the dev database lives in
// apps/api/src/maintenance/resources/seed/reference-data.json (make seed-dev);
// keep the two sources in sync until the API replaces this file.
export const branches: StoreBranch[] = [
  { id: 'siam', name: 'Siam', pickup: true },
  { id: 'ari', name: 'Ari', pickup: true },
  { id: 'riverside', name: 'Riverside', pickup: true },
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
  },
};
export const retailStockAt = (variantId: string, branchId: string) =>
  retailStock[branchId]?.[variantId] ?? 0;
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

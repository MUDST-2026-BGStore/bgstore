import type {
  Branch,
  GameDetail,
  GameListResponse,
  GameSummary,
} from '../generated/api/types.gen';

/** Ids match the branches `V2__games.sql` seeds. */
export const branches: Branch[] = [
  { id: '3f0d7d5a-9a2b-4a71-8f0e-000000000002', name: 'Big C Rama I' },
  { id: '3f0d7d5a-9a2b-4a71-8f0e-000000000001', name: 'Central Rama II' },
  { id: '3f0d7d5a-9a2b-4a71-8f0e-000000000004', name: 'Sukhumvit' },
];

export const ticketToRideId = '9b1f0e4c-1d3a-4a0b-8f21-4c6f5a0d7e11';

export const explodingKittens: GameSummary = {
  id: '1a1f0e4c-1d3a-4a0b-8f21-4c6f5a0d7e12',
  title: 'Exploding Kittens',
  category: 'card',
  minPlayers: 2,
  maxPlayers: 5,
  branchName: 'Central Rama II',
  branchCount: 1,
  copies: 3,
  available: 3,
  status: 'available',
};

export const splendor: GameSummary = {
  id: '2a1f0e4c-1d3a-4a0b-8f21-4c6f5a0d7e13',
  title: 'Splendor',
  category: 'strategy',
  minPlayers: 2,
  maxPlayers: 4,
  branchCount: 2,
  copies: 2,
  available: 0,
  status: 'allCopiesOut',
};

export function gameList(
  items: GameSummary[] = [explodingKittens, splendor],
  overrides: Partial<GameListResponse> = {},
): GameListResponse {
  return {
    items,
    page: { number: 0, size: 20, totalElements: items.length, totalPages: 1 },
    stats: { titles: items.length, availableNow: 46, inUse: 12 },
    ...overrides,
  };
}

export const ticketToRide: GameDetail = {
  id: ticketToRideId,
  title: 'Ticket to Ride',
  description: 'Build routes across the map — easy to teach.',
  category: 'family',
  minPlayers: 2,
  maxPlayers: 5,
  playTimeMinutes: 60,
  difficulty: 'Easy to teach',
  tags: ['beginner friendly', '30–60 min'],
  lifecycle: 'active',
  status: 'available',
  addedAt: '2025-01-12T09:00:00Z',
  lastPlayedAt: null,
  totalCopies: 3,
  branchCount: 2,
  stock: [
    {
      branchId: branches[0].id,
      branchName: 'Big C Rama I',
      copies: 1,
      available: 0,
      inUse: 1,
      status: 'allCopiesOut',
    },
    {
      branchId: branches[1].id,
      branchName: 'Central Rama II',
      copies: 2,
      available: 1,
      inUse: 1,
      status: 'available',
    },
    {
      branchId: branches[2].id,
      branchName: 'Sukhumvit',
      copies: 0,
      available: 0,
      inUse: 0,
      status: 'notStocked',
    },
  ],
};

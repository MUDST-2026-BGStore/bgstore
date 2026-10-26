export const categories = [
  'Party & Casual',
  'Social Deduction',
  'Strategy',
  'Cooperative',
  'Mystery & Puzzle',
  'Quick & Light',
  'Adventure & Immersive',
] as const;

export type Category = (typeof categories)[number];
export interface CatalogGame {
  id: string;
  name: string;
  designer: string;
  categories: Category[];
  mechanics: string[];
  players: [number, number];
  playMinutes: [number, number];
  teachMinutes: number;
  setupMinutes: number;
  difficulty: 'Easy' | 'Medium' | 'Advanced';
  language: string;
  description: string;
  retailPrice: number | null;
}

// TODO(cart-catalog-api): These legacy fixtures still supply cart display data;
// HomeIndex and GameProduct already use live catalog reads in api/catalog.ts.
// Replace cart fixture joins with real game/edition IDs when server carts work,
// then remove these fixtures (retain shared formatting helpers).
// TODO(branch-inventory): Resolve play-copy availability and retail stock/prices
// independently for the selected branch. Never derive one from the other. Staff
// teaching and teach/setup estimates must also come from branch-owned records.
// All prices, availability and teaching estimates below are sample data.
export const games: CatalogGame[] = [
  {
    id: 'azul',
    name: 'Azul',
    designer: 'Michael Kiesling',
    categories: ['Strategy', 'Quick & Light'],
    mechanics: ['Tile placement', 'Drafting'],
    players: [2, 4],
    playMinutes: [30, 45],
    teachMinutes: 5,
    setupMinutes: 5,
    difficulty: 'Easy',
    language: 'English · Language independent',
    description:
      'Draft tiles from shared supplies and arrange them on your board to score patterns and complete rows.',
    retailPrice: 1490,
  },
  {
    id: 'wingspan',
    name: 'Wingspan',
    designer: 'Elizabeth Hargrave',
    categories: ['Strategy'],
    mechanics: ['Engine building', 'Card drafting'],
    players: [1, 5],
    playMinutes: [40, 70],
    teachMinutes: 15,
    setupMinutes: 10,
    difficulty: 'Medium',
    language: 'English',
    description:
      'Build a bird habitat by playing cards with abilities that produce food, eggs and points.',
    retailPrice: 2490,
  },
  {
    id: 'codenames',
    name: 'Codenames',
    designer: 'Vlaada Chvátil',
    categories: ['Party & Casual', 'Quick & Light'],
    mechanics: ['Word association', 'Team-based'],
    players: [2, 8],
    playMinutes: [15, 20],
    teachMinutes: 5,
    setupMinutes: 3,
    difficulty: 'Easy',
    language: 'English',
    description:
      'Give one-word clues to help your team identify its agents on a shared grid of words. Avoid the assassin.',
    retailPrice: 990,
  },
  {
    id: 'avalon',
    name: 'The Resistance: Avalon',
    designer: 'Don Eskridge',
    categories: ['Social Deduction', 'Party & Casual'],
    mechanics: ['Hidden roles', 'Bluffing', 'Voting'],
    players: [5, 10],
    playMinutes: [30, 30],
    teachMinutes: 10,
    setupMinutes: 5,
    difficulty: 'Easy',
    language: 'English',
    description:
      'Vote on quest teams while deducing hidden loyalties. Loyal players must complete three quests without revealing Merlin.',
    retailPrice: null,
  },
  {
    id: 'catan',
    name: 'Catan',
    designer: 'Klaus Teuber',
    categories: ['Strategy'],
    mechanics: ['Trading', 'Network building', 'Dice rolling'],
    players: [3, 4],
    playMinutes: [60, 120],
    teachMinutes: 15,
    setupMinutes: 10,
    difficulty: 'Medium',
    language: 'English',
    description:
      'Collect and trade resources to build roads, settlements and cities on a modular island board.',
    retailPrice: 1890,
  },
  {
    id: 'pandemic',
    name: 'Pandemic',
    designer: 'Matt Leacock',
    categories: ['Cooperative'],
    mechanics: ['Team-based', 'Hand management'],
    players: [2, 4],
    playMinutes: [45, 60],
    teachMinutes: 10,
    setupMinutes: 10,
    difficulty: 'Medium',
    language: 'English',
    description:
      'Coordinate specialist roles to treat outbreaks and discover four cures before time runs out.',
    retailPrice: 1690,
  },
  {
    id: 'cascadia',
    name: 'Cascadia',
    designer: 'Randy Flynn',
    categories: ['Strategy', 'Mystery & Puzzle'],
    mechanics: ['Tile placement', 'Pattern building'],
    players: [1, 4],
    playMinutes: [30, 45],
    teachMinutes: 5,
    setupMinutes: 5,
    difficulty: 'Easy',
    language: 'English · Language independent',
    description:
      'Draft habitat tiles and wildlife tokens. Score by connecting landscapes and arranging animals in specific patterns.',
    retailPrice: 1790,
  },
  {
    id: 'love-letter',
    name: 'Love Letter',
    designer: 'Seiji Kanai',
    categories: ['Quick & Light', 'Party & Casual'],
    mechanics: ['Deduction', 'Bluffing', 'Hand management'],
    players: [2, 6],
    playMinutes: [15, 20],
    teachMinutes: 5,
    setupMinutes: 2,
    difficulty: 'Easy',
    language: 'English',
    description:
      'Draw and play character cards to deduce other players’ hands and eliminate rivals from each round.',
    retailPrice: 690,
  },
];

export const formatPrice = (price: number) =>
  new Intl.NumberFormat('th-TH', {
    style: 'currency',
    currency: 'THB',
    maximumFractionDigits: 0,
  }).format(price);
export const range = ([min, max]: [number, number]) =>
  min === max ? String(min) : `${min}–${max}`;

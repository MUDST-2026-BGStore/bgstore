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

// TODO(workspace-inventory-api): These fixture games are used only by the staff
// workspace samples (useWorkspaceGames). The storefront, cart and orders read the
// catalog, branch prices and stock from the API. Remove this array once the
// workspace is wired, keeping the formatting helpers below.
// All prices, availability and teaching estimates in it are sample data.
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
  {
    id: 'wavelength',
    name: 'Wavelength',
    designer: 'Alex Hague, Justin Vickers, Wolfgang Warsch',
    categories: ['Party & Casual'],
    mechanics: ['Team-based', 'Deduction'],
    players: [2, 12],
    playMinutes: [30, 45],
    teachMinutes: 5,
    setupMinutes: 3,
    difficulty: 'Easy',
    language: 'English',
    description:
      'A telepathic party game of guessing where your target falls on a binary spectrum.',
    retailPrice: 1190,
  },
  {
    id: 'cash-n-guns',
    name: 'Cash ’n Guns',
    designer: 'Ludovic Maublanc',
    categories: ['Party & Casual'],
    mechanics: ['Bluffing', 'Voting'],
    players: [4, 8],
    playMinutes: [30, 30],
    teachMinutes: 5,
    setupMinutes: 5,
    difficulty: 'Easy',
    language: 'English',
    description:
      'Point foam pistols at your rivals to claim the biggest share of gangster loot.',
    retailPrice: 1390,
  },
  {
    id: 'monikers',
    name: 'Monikers',
    designer: 'Alex Hague, Justin Vickers',
    categories: ['Party & Casual'],
    mechanics: ['Word association', 'Team-based'],
    players: [4, 16],
    playMinutes: [30, 60],
    teachMinutes: 5,
    setupMinutes: 2,
    difficulty: 'Easy',
    language: 'English',
    description:
      'A party game based on the public domain game Celebrity where players guess cards across rounds.',
    retailPrice: 890,
  },
  {
    id: 'betrayal-at-house-on-the-hill',
    name: 'Betrayal at House on the Hill',
    designer: 'Bruce Glassco',
    categories: ['Adventure & Immersive'],
    mechanics: ['Tile placement', 'Dice rolling'],
    players: [3, 6],
    playMinutes: [60, 90],
    teachMinutes: 15,
    setupMinutes: 10,
    difficulty: 'Medium',
    language: 'English',
    description:
      'Tile by tile explore a haunted mansion until one explorer betrays the rest.',
    retailPrice: 2190,
  },
  {
    id: 'taboo',
    name: 'Taboo',
    designer: 'Brian Hersch',
    categories: ['Party & Casual'],
    mechanics: ['Word association', 'Team-based'],
    players: [4, 10],
    playMinutes: [20, 30],
    teachMinutes: 3,
    setupMinutes: 2,
    difficulty: 'Easy',
    language: 'English',
    description:
      'Get your team to guess the secret word without uttering forbidden clues.',
    retailPrice: 1090,
  },
  {
    id: 'beyond-balderdash',
    name: 'Beyond Balderdash',
    designer: 'Laura Robinson, Paul Toyne',
    categories: ['Party & Casual'],
    mechanics: ['Bluffing', 'Voting'],
    players: [2, 8],
    playMinutes: [45, 60],
    teachMinutes: 5,
    setupMinutes: 5,
    difficulty: 'Easy',
    language: 'English',
    description:
      'Bluff hilarious definitions, movie plots and acronyms to fool your opponents.',
    retailPrice: 990,
  },
  {
    id: 'cosmic-encounter',
    name: 'Cosmic Encounter',
    designer: 'Peter Olotka, Jack Kittredge, Bill Eberle',
    categories: ['Strategy'],
    mechanics: ['Hand management', 'Bluffing'],
    players: [3, 5],
    playMinutes: [60, 120],
    teachMinutes: 15,
    setupMinutes: 10,
    difficulty: 'Medium',
    language: 'English',
    description:
      'Lead an alien race with game-breaking powers into galactic conflict and shifting alliances.',
    retailPrice: 2390,
  },
  {
    id: 'blood-on-the-clocktower',
    name: 'Blood on the Clocktower',
    designer: 'Steven Medway',
    categories: ['Social Deduction'],
    mechanics: ['Hidden roles', 'Bluffing', 'Voting'],
    players: [5, 20],
    playMinutes: [30, 90],
    teachMinutes: 10,
    setupMinutes: 5,
    difficulty: 'Advanced',
    language: 'English',
    description:
      'A bluffing game of murder and mystery, deceit and deduction for 5 to 20 players.',
    retailPrice: 3890,
  },
];

export const formatPrice = (price: number) =>
  new Intl.NumberFormat('th-TH', {
    style: 'currency',
    currency: 'THB',
    maximumFractionDigits: 0,
  }).format(price);
/** Formats THB minor units (satang) from the API; satang show only when present. */
export const formatMinor = (amountMinor: number) =>
  new Intl.NumberFormat('th-TH', {
    style: 'currency',
    currency: 'THB',
    minimumFractionDigits: amountMinor % 100 === 0 ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(amountMinor / 100);
export const range = ([min, max]: [number, number]) =>
  min === max ? String(min) : `${min}–${max}`;

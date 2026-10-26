import type { Game } from '@theparlor/api-client';

export const apiGames: [Game, Game] = [
  {
    id: '00000000-0000-4000-8000-000000000001',
    name: 'Azul',
    designer: 'Michael Kiesling',
    categories: ['Strategy', 'Quick & Light'],
    mechanics: ['Tile placement', 'Drafting'],
    minPlayers: 2,
    maxPlayers: 4,
    playMinutesMin: 30,
    playMinutesMax: 45,
    teachMinutes: 5,
    setupMinutes: 5,
    difficulty: 'EASY',
    active: true,
    descriptions: {
      'en-US': 'Arrange tiles on your board.',
      'th-TH': 'จัดเรียงกระเบื้องบนกระดานของคุณ',
    },
    images: [
      {
        id: 'cover',
        url: '/images/games/azul.jpg',
        altText: 'Azul game box',
        sortOrder: 0,
      },
    ],
  },
  {
    id: '00000000-0000-4000-8000-000000000002',
    name: 'Blood on the Clocktower',
    categories: ['Social Deduction', 'Mystery & Puzzle'],
    mechanics: ['Hidden roles', 'Voting'],
    minPlayers: 5,
    maxPlayers: 20,
    playMinutesMin: 30,
    playMinutesMax: 120,
    active: true,
    descriptions: { 'en-US': 'Find the demon in your town.' },
    images: [],
  },
];
export const apiTaxonomy = apiGames
  .flatMap((game) => game.categories)
  .map((name, index) => ({
    id: index + 1,
    name,
    names: { 'en-US': name },
  }));

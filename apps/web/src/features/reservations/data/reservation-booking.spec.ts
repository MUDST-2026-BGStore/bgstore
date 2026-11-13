import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { BranchAvailability, BranchGame } from '@theparlor/api-client';
import type { FoodMenuItem } from '../types';
import {
  BookingError,
  TOKEN_EXPIRY_MARGIN_MS,
  availabilityProblems,
  bookableGames,
  cardRequest,
  createBookingSubmitter,
  intentFingerprint,
  preOrderShortfalls,
  reconcileGames,
  reconcilePreOrder,
  reservationRequest,
  type BookingIntent,
} from './reservation-booking';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('../../../../../../libs/web/api-client/src/lib/transport.ts', () => ({
  apiRequest: request,
  configureApiTransport: vi.fn(),
}));

const BRANCH = '00000000-0000-4000-8000-0000000000a1';
const TABLE = '00000000-0000-4000-8000-0000000000b1';
const CATAN = '00000000-0000-4000-8000-0000000000c1';
const AZUL = '00000000-0000-4000-8000-0000000000c2';
const COLA = '00000000-0000-4000-8000-0000000000d1';
const CHIPS = '00000000-0000-4000-8000-0000000000d2';

const fail = (status?: number, code?: string) =>
  Object.assign(new Error(status ? `HTTP ${status}` : 'Network Error'), {
    response: status ? { status, data: code ? { code } : {} } : undefined,
  });

const intent = (overrides: Partial<BookingIntent> = {}): BookingIntent => ({
  branchId: BRANCH,
  tableId: TABLE,
  startsAt: '2026-11-20T07:00:00.000Z',
  endsAt: '2026-11-20T09:00:00.000Z',
  partySize: 4,
  games: [{ gameId: CATAN, quantity: 1 }],
  notes: '  Birthday  ',
  preOrder: [{ menuItemId: COLA, quantity: 2 }],
  ...overrides,
});

const card = {
  number: '4242424242424242',
  expMonth: 12,
  expYear: 2030,
  cvc: '123',
  holderName: 'Pat',
};

describe('the reservation request', () => {
  it('carries the chosen games and pre-order with the payment token', () => {
    expect(reservationRequest(intent(), 'tok_1234567890abcdef')).toEqual({
      tableId: TABLE,
      startsAt: '2026-11-20T07:00:00.000Z',
      endsAt: '2026-11-20T09:00:00.000Z',
      partySize: 4,
      games: [{ gameId: CATAN, quantity: 1 }],
      notes: 'Birthday',
      preOrder: {
        items: [{ menuItemId: COLA, quantity: 2 }],
        paymentToken: 'tok_1234567890abcdef',
      },
    });
  });

  it('is table-only without games or food, and never sends food without a token', () => {
    const plain = intent({ games: [], preOrder: [], notes: ' ' });
    expect(reservationRequest(plain)).toEqual({
      tableId: TABLE,
      startsAt: plain.startsAt,
      endsAt: plain.endsAt,
      partySize: 4,
      games: [],
    });
    expect(() => reservationRequest(intent())).toThrow(/payment token/);
  });

  it('identifies the booking regardless of selection order or blank lines', () => {
    const a = intent({
      games: [
        { gameId: CATAN, quantity: 1 },
        { gameId: AZUL, quantity: 2 },
      ],
    });
    const b = intent({
      games: [
        { gameId: AZUL, quantity: 2 },
        { gameId: CATAN, quantity: 1 },
        { gameId: '00000000-0000-4000-8000-0000000000ff', quantity: 0 },
      ],
      notes: 'Birthday',
    });
    expect(intentFingerprint(a)).toBe(intentFingerprint(b));
    expect(intentFingerprint(a)).not.toBe(
      intentFingerprint({ ...a, partySize: 5 }),
    );
    expect(intentFingerprint(a)).not.toBe(
      intentFingerprint({
        ...a,
        preOrder: [{ menuItemId: COLA, quantity: 3 }],
      }),
    );
  });
});

const availability = (
  overrides: Partial<BranchAvailability> = {},
): BranchAvailability => ({
  branchId: BRANCH,
  startsAt: '2026-11-20T07:00:00Z',
  endsAt: '2026-11-20T09:00:00Z',
  partySize: 4,
  withinOpeningHours: true,
  asOf: '2026-11-13T03:00:00Z',
  tables: [
    {
      tableId: TABLE,
      label: 'Window 1',
      capacity: 4,
      hourlyRate: { amountMinor: 15000 },
      available: true,
    },
  ],
  games: [
    {
      gameId: CATAN,
      rentalEnabled: true,
      usableQuantity: 2,
      availableQuantity: 1,
      hourlyRate: { amountMinor: 4000 },
    },
    {
      gameId: AZUL,
      rentalEnabled: true,
      usableQuantity: 1,
      availableQuantity: 0,
      hourlyRate: { amountMinor: 3000 },
    },
  ],
  ...overrides,
});

const shelf = (id: string, name: string): BranchGame =>
  ({
    branchId: BRANCH,
    game: {
      id,
      name,
      categories: ['Strategy'],
      mechanics: [],
      minPlayers: 2,
      maxPlayers: 4,
      playMinutesMin: 30,
      playMinutesMax: 60,
      active: true,
    },
    saleEnabled: false,
    rentalEnabled: true,
    staffCanTeach: false,
    rentalHourlyRate: { amountMinor: 4000 },
    editions: [],
    rentalTotal: 2,
    rentalUsable: 2,
    rentalAvailableNow: 2,
  }) as BranchGame;

describe('games for a visit', () => {
  const library = [shelf(CATAN, 'Catan'), shelf(AZUL, 'Azul')];

  it('offers only games with copies free for the whole interval', () => {
    expect(bookableGames(availability(), library)).toEqual([
      {
        gameId: CATAN,
        name: 'Catan',
        imageUrl: undefined,
        minPlayers: 2,
        maxPlayers: 4,
        available: 1,
        hourlyRateMinor: 4000,
      },
    ]);
  });

  it('offers nothing outside opening hours, when rental is off or the game is unknown', () => {
    expect(
      bookableGames(availability({ withinOpeningHours: false }), library),
    ).toEqual([]);
    expect(
      bookableGames(
        availability({
          games: [
            {
              gameId: CATAN,
              rentalEnabled: false,
              usableQuantity: 2,
              availableQuantity: 2,
            },
          ],
        }),
        library,
      ),
    ).toEqual([]);
    expect(bookableGames(availability(), [])).toEqual([]);
    expect(bookableGames(undefined, library)).toEqual([]);
  });

  it('limits selections to the copies still free', () => {
    const offered = bookableGames(availability(), library);
    expect(reconcileGames({ [CATAN]: 3, [AZUL]: 1 }, offered)).toEqual({
      selections: { [CATAN]: 1 },
      changed: [CATAN, AZUL],
    });
    expect(reconcileGames({ [CATAN]: 1 }, offered)).toEqual({
      selections: { [CATAN]: 1 },
      changed: [],
    });
  });
});

describe('pre-order lines against a branch menu', () => {
  const item = (id: string, available?: number, price = 50): FoodMenuItem => ({
    id,
    name: id === COLA ? 'Cola' : 'Chips',
    description: '',
    category: 'beverages',
    price,
    available,
  });

  it('keeps served items at the menu price and removes or reduces the rest', () => {
    const result = reconcilePreOrder(
      [
        { item: item(COLA, 10, 40), quantity: 4 },
        { item: item(CHIPS, 10), quantity: 2 },
      ],
      [item(COLA, 3, 45)],
    );
    expect(result.lines).toEqual([{ item: item(COLA, 3, 45), quantity: 3 }]);
    expect(result.reduced.map((line) => line.item.id)).toEqual([COLA]);
    expect(result.removed.map((line) => line.item.id)).toEqual([CHIPS]);
  });

  it('finds requested lines the latest menu cannot serve in full', () => {
    const request = intent({
      preOrder: [
        { menuItemId: COLA, quantity: 2 },
        { menuItemId: CHIPS, quantity: 1 },
      ],
    });
    expect(
      preOrderShortfalls([item(COLA, 2), item(CHIPS, 5)], request),
    ).toEqual([]);
    expect(preOrderShortfalls([item(COLA, 1)], request)).toEqual([COLA, CHIPS]);
  });

  it('removes items the branch has run out of', () => {
    const result = reconcilePreOrder(
      [{ item: item(COLA, 5), quantity: 1 }],
      [item(COLA, 0)],
    );
    expect(result.lines).toEqual([]);
    expect(result.removed).toHaveLength(1);
  });
});

describe('revalidating before confirmation', () => {
  it('passes while the table and games still fit', () => {
    expect(availabilityProblems(availability(), intent())).toEqual([]);
  });

  it('reports a taken table, short games and a closed branch', () => {
    const taken = availability({
      tables: [{ ...availability().tables[0], available: false }],
    });
    expect(
      availabilityProblems(
        taken,
        intent({
          games: [
            { gameId: CATAN, quantity: 2 },
            { gameId: AZUL, quantity: 1 },
          ],
        }),
      ),
    ).toEqual([
      { kind: 'table' },
      { kind: 'game', gameId: CATAN, available: 1 },
      { kind: 'game', gameId: AZUL, available: 0 },
    ]);
    expect(
      availabilityProblems(
        availability({ withinOpeningHours: false }),
        intent(),
      ),
    ).toEqual([{ kind: 'closed' }]);
  });
});

describe('card details', () => {
  const now = new Date('2026-11-13T03:00:00Z');

  it('become the tokenization request', () => {
    expect(
      cardRequest(
        {
          number: '4242 4242 4242 4242',
          expiry: '12/30',
          cvc: '123',
          holder: ' Pat ',
        },
        now,
      ),
    ).toEqual({ request: card });
  });

  it('report plainly wrong fields, including an expired month', () => {
    expect(
      cardRequest(
        { number: '4242', expiry: '10/26', cvc: '12', holder: '' },
        now,
      ),
    ).toEqual({ invalid: ['number', 'expiry', 'cvc', 'holder'] });
    expect(
      cardRequest(
        {
          number: '4242424242424242',
          expiry: '11/26',
          cvc: '123',
          holder: 'P',
        },
        now,
      ),
    ).toHaveProperty('request');
  });
});

describe('submitting a booking', () => {
  let clock = 0;
  let keys = 0;
  let tokens = 0;
  let createResult: () => Promise<unknown>;
  const reservation = { id: 'res-1', status: 'CONFIRMED' };

  const sent = (url: RegExp) =>
    request.mock.calls
      .filter(([config]) => url.test(config.url))
      .map(([config, options]) => ({
        data: config.data,
        headers: { ...config.headers, ...options?.headers },
      }));
  const bookings = () => sent(/\/reservations$/);
  const tokenizations = () => sent(/\/payments\/tokens$/);

  const submitter = () =>
    createBookingSubmitter({
      newKey: () =>
        `00000000-0000-4000-8000-${String(++keys).padStart(12, '0')}`,
      now: () => clock,
    });

  beforeEach(() => {
    clock = Date.parse('2026-11-13T03:00:00Z');
    keys = 0;
    tokens = 0;
    createResult = () => Promise.resolve(reservation);
    request.mockReset().mockImplementation((config) => {
      if (config.url === '/api/v1/payments/tokens')
        return Promise.resolve({
          token: `tok_${String(++tokens).padStart(16, '0')}`,
          cardBrand: 'VISA',
          cardLast4: '4242',
          expiresAt: new Date(clock + 15 * 60_000).toISOString(),
        });
      if (config.url === `/api/v1/branches/${BRANCH}/reservations`)
        return createResult();
      return Promise.reject(fail(404));
    });
  });

  it('sends the card only to tokenization and books with the token and a key', async () => {
    await expect(submitter().submit(intent(), card)).resolves.toBe(reservation);
    expect(tokenizations()).toHaveLength(1);
    expect(tokenizations()[0].data).toEqual(card);
    const [booking] = bookings();
    expect(booking.data.preOrder).toEqual({
      items: [{ menuItemId: COLA, quantity: 2 }],
      paymentToken: 'tok_0000000000000001',
    });
    expect(booking.data.games).toEqual([{ gameId: CATAN, quantity: 1 }]);
    expect(booking.headers['Idempotency-Key']).toMatch(/^[0-9a-f-]{36}$/);
    expect(JSON.stringify(booking.data)).not.toContain('4242');
    expect(JSON.stringify(booking.data)).not.toContain('"123"');
  });

  it('books a table without food without a card or token', async () => {
    await submitter().submit(intent({ preOrder: [] }));
    expect(tokenizations()).toHaveLength(0);
    expect(bookings()[0].data).not.toHaveProperty('preOrder');
  });

  it('asks for the card when food needs paying for', async () => {
    await expect(submitter().submit(intent())).rejects.toMatchObject({
      failure: 'card-required',
    });
    expect(bookings()).toHaveLength(0);
  });

  it('replays a lost request exactly: same key, same token', async () => {
    const booking = submitter();
    createResult = () => Promise.reject(fail());
    await expect(booking.submit(intent(), card)).rejects.toMatchObject({
      failure: 'unknown-outcome',
    });
    expect(booking.outcomeUnknown).toBe(true);

    createResult = () => Promise.resolve(reservation);
    clock += 5 * 60_000;
    await expect(booking.submit(intent(), card)).resolves.toBe(reservation);
    const [first, retry] = bookings();
    expect(retry.headers['Idempotency-Key']).toBe(
      first.headers['Idempotency-Key'],
    );
    expect(retry.data).toEqual(first.data);
    expect(tokenizations()).toHaveLength(1);
  });

  it('treats proxy timeouts and bare gateway errors as lost responses', async () => {
    for (const error of [fail(504), fail(408), fail(502)]) {
      const booking = submitter();
      createResult = () => Promise.reject(error);
      await expect(booking.submit(intent(), card)).rejects.toMatchObject({
        failure: 'unknown-outcome',
      });
    }
  });

  it('keeps the key but tokenizes again once the token is nearly expired', async () => {
    const booking = submitter();
    createResult = () => Promise.reject(fail());
    await expect(booking.submit(intent(), card)).rejects.toThrow(BookingError);

    clock += 15 * 60_000 - TOKEN_EXPIRY_MARGIN_MS;
    createResult = () => Promise.resolve(reservation);
    await booking.submit(intent(), card);
    const [first, retry] = bookings();
    expect(retry.headers['Idempotency-Key']).toBe(
      first.headers['Idempotency-Key'],
    );
    expect(retry.data.preOrder.paymentToken).toBe('tok_0000000000000002');
    expect(tokenizations()).toHaveLength(2);
  });

  it('keeps the key after a lost response even if the booking changes, so it cannot book twice', async () => {
    const booking = submitter();
    createResult = () => Promise.reject(fail());
    await expect(booking.submit(intent(), card)).rejects.toThrow(BookingError);

    // The server holds the first request under this key and refuses a different one.
    createResult = () => Promise.reject(fail(409, 'CONFLICT'));
    await expect(
      booking.submit(intent({ partySize: 3 }), card),
    ).rejects.toMatchObject({ failure: 'possibly-booked' });
    const [first, changed] = bookings();
    expect(changed.headers['Idempotency-Key']).toBe(
      first.headers['Idempotency-Key'],
    );
    expect(booking.outcomeUnknown).toBe(true);

    booking.reset();
    createResult = () => Promise.resolve(reservation);
    await booking.submit(intent({ partySize: 3 }), card);
    expect(bookings()[2].headers['Idempotency-Key']).not.toBe(
      first.headers['Idempotency-Key'],
    );
  });

  it('after a decline, retries the same booking under the same key with a fresh token', async () => {
    const booking = submitter();
    createResult = () => Promise.reject(fail(402, 'PAYMENT_DECLINED'));
    await expect(booking.submit(intent(), card)).rejects.toMatchObject({
      failure: 'declined',
    });
    expect(booking.outcomeUnknown).toBe(false);

    createResult = () => Promise.resolve(reservation);
    await booking.submit(intent(), card);
    const [declined, retry] = bookings();
    expect(retry.headers['Idempotency-Key']).toBe(
      declined.headers['Idempotency-Key'],
    );
    expect(retry.data.preOrder.paymentToken).not.toBe(
      declined.data.preOrder.paymentToken,
    );
  });

  it('uses a new key when the booking changes after a definite answer', async () => {
    const booking = submitter();
    createResult = () => Promise.reject(fail(409, 'TABLE_UNAVAILABLE'));
    await expect(booking.submit(intent(), card)).rejects.toMatchObject({
      failure: 'unavailable',
    });
    createResult = () => Promise.resolve(reservation);
    await booking.submit(
      intent({ tableId: '00000000-0000-4000-8000-0000000000b2' }),
      card,
    );
    const [refused, changed] = bookings();
    expect(changed.headers['Idempotency-Key']).not.toBe(
      refused.headers['Idempotency-Key'],
    );
  });

  it('starts a new attempt with a new key after success', async () => {
    const booking = submitter();
    await booking.submit(intent(), card);
    await booking.submit(intent(), card);
    const [first, second] = bookings();
    expect(second.headers['Idempotency-Key']).not.toBe(
      first.headers['Idempotency-Key'],
    );
  });

  it('classifies definite failures without claiming success', async () => {
    const cases: [Error, string][] = [
      [fail(400), 'invalid'],
      [fail(401), 'signed-out'],
      [fail(404), 'unavailable'],
      [fail(409), 'unavailable'],
      [fail(502, 'PAYMENT_GATEWAY_ERROR'), 'gateway'],
      [fail(500), 'failed'],
    ];
    for (const [error, failure] of cases) {
      createResult = () => Promise.reject(error);
      await expect(submitter().submit(intent(), card)).rejects.toMatchObject({
        failure,
      });
    }
  });

  it('does not book when the card cannot be tokenized', async () => {
    request.mockImplementation((config) =>
      config.url === '/api/v1/payments/tokens'
        ? Promise.reject(fail(400))
        : Promise.resolve(reservation),
    );
    await expect(submitter().submit(intent(), card)).rejects.toMatchObject({
      failure: 'invalid',
    });
    expect(bookings()).toHaveLength(0);
  });

  it('refuses a second submission while one is in flight', async () => {
    const booking = submitter();
    let finish!: (value: unknown) => void;
    createResult = () => new Promise((resolve) => (finish = resolve));
    const first = booking.submit(intent({ preOrder: [] }));
    await expect(booking.submit(intent({ preOrder: [] }))).rejects.toThrow(
      /already being submitted/,
    );
    await vi.waitFor(() => expect(bookings()).toHaveLength(1));
    finish(reservation);
    await expect(first).resolves.toBe(reservation);
  });
});

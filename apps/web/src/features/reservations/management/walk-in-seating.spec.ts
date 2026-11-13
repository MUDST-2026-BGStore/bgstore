import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { defineComponent, nextTick, ref } from 'vue';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('../../../../../../libs/web/api-client/src/lib/transport.ts', () => ({
  apiRequest: request,
  configureApiTransport: vi.fn(),
}));

const SIAM = '00000000-0000-4000-8000-0000000000a1';
const T1 = '00000000-0000-4000-8000-0000000000b1';
const T2 = '00000000-0000-4000-8000-0000000000b2';
const T3 = '00000000-0000-4000-8000-0000000000b3';
const T4 = '00000000-0000-4000-8000-0000000000b4';
const HOUR = 3_600_000;
const STORAGE_KEY = 'theparlor_customer_reservations_v3';

/** The value, failing the test when it is missing. */
function must<T>(value: T | null | undefined): T {
  if (value == null) throw new Error('Expected a value');
  return value;
}

interface Visit {
  id: string;
  tableId: string;
  status: 'CONFIRMED' | 'CHECKED_IN';
  startsAt: string;
  endsAt: string;
  partySize: number;
  customer: { kind: 'GUEST'; displayName: string };
}

/**
 * Stands in for the branch API with the server's walk-in rules: a table must be
 * in service, big enough, open until the end, and not occupied or booked during
 * the stay. Seating is idempotent per key and checks the party in.
 */
function fakeServer() {
  const tables = [
    { id: T1, label: 'Table 1', capacity: 2, outOfService: false },
    { id: T2, label: 'Table 2', capacity: 4, outOfService: false },
    { id: T3, label: 'Table 3', capacity: 4, outOfService: true },
    { id: T4, label: 'Table 4', capacity: 6, outOfService: false },
  ];
  const visits: Visit[] = [
    {
      id: 'visit-original',
      tableId: T1,
      status: 'CHECKED_IN',
      startsAt: new Date(Date.now() - HOUR).toISOString(),
      endsAt: new Date(Date.now() + HOUR).toISOString(),
      partySize: 2,
      customer: { kind: 'GUEST', displayName: 'Original guest' },
    },
  ];
  let closesAt = Date.now() + 10 * HOUR;
  const keys = new Map<string, { body: string; id: string }>();
  const posts: {
    headers: Record<string, string>;
    data: Record<string, unknown>;
  }[] = [];
  /** Makes the next seating fail: 'network' before or after the server commits. */
  let nextFailure:
    | { kind: 'status'; status: number; message?: string }
    | { kind: 'network'; committed: boolean }
    | null = null;
  let seq = 0;

  const fail = (status: number, message?: string) =>
    Promise.reject(
      Object.assign(new Error(`HTTP ${status}`), {
        response: { status, data: { status, code: 'X', message } },
      }),
    );
  const lost = () => Promise.reject(new Error('Network Error'));

  function reasons(
    tableId: string,
    from: number,
    until: number,
    party: number,
  ) {
    const table = must(tables.find((t) => t.id === tableId));
    const out: string[] = [];
    if (until > closesAt) out.push('OUTSIDE_OPENING_HOURS');
    if (table.outOfService) out.push('OUT_OF_SERVICE');
    if (party > table.capacity) out.push('CAPACITY_EXCEEDED');
    const mine = visits.filter((v) => v.tableId === tableId);
    if (
      mine.some(
        (v) =>
          v.status === 'CONFIRMED' &&
          Date.parse(v.startsAt) < until &&
          Date.parse(v.endsAt) > from,
      )
    )
      out.push('RESERVED');
    if (mine.some((v) => v.status === 'CHECKED_IN')) out.push('OCCUPIED');
    return out;
  }

  request.mockReset().mockImplementation((config, options) => {
    const method = (config.method ?? 'GET').toUpperCase();
    const url: string = config.url;
    const base = `/api/v1/branches/${SIAM}`;
    if (url === '/api/v1/branches')
      return Promise.resolve({ items: [{ id: SIAM, name: 'Siam' }] });
    if (url === `${base}/tables`)
      return Promise.resolve({
        items: tables.map((t) => ({
          id: t.id,
          branchId: SIAM,
          label: t.label,
          capacity: t.capacity,
          hourlyRate: { amountMinor: 20000 },
          operationalStatus: t.outOfService ? 'OUT_OF_SERVICE' : 'IN_SERVICE',
          state: 'AVAILABLE',
        })),
      });
    if (url === `${base}/reservations`)
      return Promise.resolve({ items: visits.map((v) => ({ ...v })) });
    if (url === `${base}/availability`) {
      const { startsAt, endsAt, partySize } = config.params;
      return Promise.resolve({
        branchId: SIAM,
        startsAt,
        endsAt,
        partySize,
        withinOpeningHours: Date.parse(endsAt) <= closesAt,
        asOf: new Date().toISOString(),
        games: [],
        tables: tables.map((t) => {
          const why = reasons(
            t.id,
            Date.parse(startsAt),
            Date.parse(endsAt),
            partySize,
          );
          return {
            tableId: t.id,
            label: t.label,
            capacity: t.capacity,
            hourlyRate: { amountMinor: 20000 },
            available: why.length === 0,
            unavailableReasons: why,
          };
        }),
      });
    }
    if (url === `${base}/staff/reservations` && method === 'POST') {
      const headers = { ...(options?.headers ?? {}) } as Record<string, string>;
      const data = config.data;
      posts.push({ headers, data });
      const failure = nextFailure;
      nextFailure = null;
      if (failure?.kind === 'status')
        return fail(failure.status, failure.message);
      if (failure?.kind === 'network' && !failure.committed) return lost();
      const key = headers['Idempotency-Key'];
      const body = JSON.stringify(data);
      const seen = keys.get(key);
      if (seen) {
        if (seen.body !== body) return fail(409, 'Idempotency key reused');
        return Promise.resolve({
          ...must(visits.find((v) => v.id === seen.id)),
        });
      }
      if ('startsAt' in data) return fail(400, 'startsAt is not allowed');
      if (!tables.some((t) => t.id === data.tableId))
        return fail(404, 'Table not found');
      const why = reasons(
        data.tableId,
        Date.now(),
        Date.parse(data.endsAt),
        data.partySize,
      );
      if (why.length)
        return fail(409, 'Table is not free for the whole booking');
      const visit: Visit = {
        id: `visit-${++seq}`,
        tableId: data.tableId,
        status: data.checkIn ? 'CHECKED_IN' : 'CONFIRMED',
        startsAt: new Date().toISOString(),
        endsAt: data.endsAt,
        partySize: data.partySize,
        customer: { kind: 'GUEST', displayName: data.customer.displayName },
      };
      visits.push(visit);
      keys.set(key, { body, id: visit.id });
      if (failure?.kind === 'network') return lost();
      return Promise.resolve({ ...visit });
    }
    if (url.startsWith(base) && method === 'GET')
      return Promise.resolve({ items: [] });
    return fail(500);
  });

  return {
    tables,
    visits,
    posts,
    seat(
      tableId: string,
      status: Visit['status'],
      from: number,
      until: number,
    ) {
      visits.push({
        id: `visit-${++seq}`,
        tableId,
        status,
        startsAt: new Date(from).toISOString(),
        endsAt: new Date(until).toISOString(),
        partySize: 2,
        customer: { kind: 'GUEST', displayName: 'Someone else' },
      });
    },
    closeAt(time: number) {
      closesAt = time;
    },
    failNext(failure: NonNullable<typeof nextFailure>) {
      nextFailure = failure;
    },
    availabilityCalls: () =>
      request.mock.calls.filter(([config]) =>
        String(config.url).endsWith('/availability'),
      ).length,
  };
}

let server: ReturnType<typeof fakeServer>;
let wrapper: VueWrapper | undefined;

beforeEach(() => {
  localStorage.clear();
  vi.resetModules();
  server = fakeServer();
});
afterEach(() => {
  wrapper?.unmount();
  wrapper = undefined;
});

/** Mounts a composable as a page would, with a fresh query cache ("a reload"). */
async function mountWith<T>(setup: () => T): Promise<T> {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  let exposed!: T;
  wrapper = mount(
    defineComponent({
      setup() {
        exposed = setup();
        return () => null;
      },
    }),
    { global: { plugins: [[VueQueryPlugin, { queryClient }]] } },
  );
  await flushPromises();
  return exposed;
}

async function walkInDialog() {
  const { useWalkInSeating } = await import('./useWalkInSeating');
  const walkIn = await mountWith(() => useWalkInSeating(ref(SIAM)));
  walkIn.openFor();
  await flushPromises();
  return walkIn;
}

async function workspace() {
  const { useWorkspaceReservations } = await import(
    './useWorkspaceReservations'
  );
  const ws = await mountWith(() => useWorkspaceReservations());
  await flushPromises();
  return ws;
}

function option(walkIn: Awaited<ReturnType<typeof walkInDialog>>, id: string) {
  return must(walkIn.tableOptions.value.find((o) => o.id === id));
}

it('offers only the tables the server says can take the walk-in, with reasons', async () => {
  const walkIn = await walkInDialog();
  walkIn.partySize.value = 3;
  await flushPromises();

  expect(option(walkIn, T1)).toMatchObject({ disabled: true });
  expect(option(walkIn, T1).description).toContain('In play');
  expect(option(walkIn, T1).description).toContain('Too small for the party');
  expect(option(walkIn, T2)).toMatchObject({ disabled: false });
  expect(option(walkIn, T2).description).toContain('Free');
  expect(option(walkIn, T3).description).toContain('Out of service');
  expect(option(walkIn, T3).disabled).toBe(true);
});

it('refuses an occupied or unready table without sending a seating', async () => {
  const walkIn = await walkInDialog();
  for (const table of [T1, T3]) {
    walkIn.selectTable(table);
    await nextTick();
    expect(walkIn.canSubmit.value).toBe(false);
    expect(walkIn.blockedReason.value).not.toBe('');
    expect(await walkIn.submit()).toBeNull();
  }
  expect(server.posts).toHaveLength(0);
  expect(server.visits).toHaveLength(1);
});

it('rechecks a table that became occupied after the dialog opened', async () => {
  const walkIn = await walkInDialog();
  walkIn.selectTable(T2);
  walkIn.guestName.value = 'QA Capacity Test';
  await nextTick();
  expect(walkIn.canSubmit.value).toBe(true);

  server.seat(T2, 'CHECKED_IN', Date.now(), Date.now() + HOUR);
  expect(await walkIn.submit()).toBeNull();

  expect(server.posts).toHaveLength(0);
  expect(walkIn.error.value).toContain('Table 2 cannot be seated: In play');
  expect(walkIn.open.value).toBe(true);
  expect(option(walkIn, T2).disabled).toBe(true);
});

it('disables tables smaller than the party', async () => {
  const walkIn = await walkInDialog();
  walkIn.partySize.value = 5;
  await flushPromises();
  expect(option(walkIn, T2).description).toContain('Too small for the party');
  expect(option(walkIn, T2).disabled).toBe(true);
  expect(option(walkIn, T4).disabled).toBe(false);
});

it('disables tables whose stay runs into a later booking or past closing', async () => {
  server.seat(T2, 'CONFIRMED', Date.now() + 2.5 * HOUR, Date.now() + 4 * HOUR);
  server.closeAt(Date.now() + 2.5 * HOUR);
  const walkIn = await walkInDialog();
  walkIn.durationHours.value = 2;
  await flushPromises();
  expect(option(walkIn, T2).disabled).toBe(false);

  walkIn.durationHours.value = 3;
  await flushPromises();
  expect(option(walkIn, T2).description).toContain('Booked during this time');
  expect(option(walkIn, T4).description).toContain(
    'Branch closes before this ends',
  );
  expect(option(walkIn, T2).disabled).toBe(true);
  expect(option(walkIn, T4).disabled).toBe(true);
});

it.each([
  [
    'a rejection',
    {
      kind: 'status',
      status: 409,
      message: 'Table is not free for the whole booking',
    },
    'Table is not free for the whole booking. Choose another table.',
  ],
  ['a server failure', { kind: 'status', status: 500 }, 'Nothing was saved'],
  [
    'a network failure',
    { kind: 'network', committed: false },
    'No answer from the server',
  ],
] as const)(
  'keeps every table, guest and session as they were after %s',
  async (_, failure, message) => {
    const before = JSON.stringify(server.visits);
    const ws = await workspace();
    const floorBefore = ws.branchFloorTables.map((t) => [
      t.id,
      ws.getTableStatus(t).state,
      ws.getTableStatus(t).reservation?.id,
    ]);
    const storedBefore = localStorage.getItem(STORAGE_KEY);

    ws.openWalkInForTable(ws.branchFloorTables.find((t) => t.id === T2));
    await flushPromises();
    ws.walkIn.guestName.value = 'QA Capacity Test';
    server.failNext(failure);
    expect(await ws.walkIn.submit()).toBeNull();
    await flushPromises();

    expect(ws.walkIn.error.value).toContain(message);
    expect(ws.walkIn.open.value).toBe(true);
    expect(JSON.stringify(server.visits)).toBe(before);
    expect(localStorage.getItem(STORAGE_KEY)).toBe(storedBefore);
    expect(
      ws.branchFloorTables.map((t) => [
        t.id,
        ws.getTableStatus(t).state,
        ws.getTableStatus(t).reservation?.id,
      ]),
    ).toEqual(floorBefore);

    // A reload shows the same, untouched floor.
    wrapper?.unmount();
    const reloaded = await workspace();
    expect(reloaded.getTableStatus(reloaded.branchFloorTables[1]).state).toBe(
      'FREE',
    );
    expect(
      reloaded.getTableStatus(reloaded.branchFloorTables[0]).reservation?.id,
    ).toBe('visit-original');
  },
);

it('seats a valid walk-in in one checked-in request and shows it after a reload', async () => {
  const ws = await workspace();
  ws.openWalkInForTable(ws.branchFloorTables.find((t) => t.id === T2));
  await flushPromises();
  ws.walkIn.partySize.value = 3;
  ws.walkIn.guestName.value = 'QA Capacity Test';
  await flushPromises();

  const seated = await ws.walkIn.submit();
  await flushPromises();

  expect(seated).toMatchObject({ status: 'CHECKED_IN', tableId: T2 });
  const [post] = server.posts;
  expect(post.data).toMatchObject({
    source: 'WALK_IN',
    checkIn: true,
    tableId: T2,
    partySize: 3,
    games: [],
    customer: { kind: 'GUEST', displayName: 'QA Capacity Test' },
  });
  expect(post.data).not.toHaveProperty('startsAt');
  expect(post.headers['Idempotency-Key']).toMatch(/^[0-9a-f-]{36}$/);
  expect(ws.walkIn.open.value).toBe(false);
  const table2 = must(ws.branchFloorTables.find((t) => t.id === T2));
  expect(ws.getTableStatus(table2)).toMatchObject({
    state: 'OCCUPIED',
    reservation: { id: must(seated).id },
  });
  // The original guest keeps Table 1.
  const table1 = must(ws.branchFloorTables.find((t) => t.id === T1));
  expect(ws.getTableStatus(table1).reservation?.id).toBe('visit-original');
  expect(ws.tableLabel(must(seated).tableId)).toBe('Table 2');
  expect(ws.tableLabel('visit-original')).toBe('—');

  wrapper?.unmount();
  const reloaded = await workspace();
  const reloadedTable2 = must(
    reloaded.branchFloorTables.find((t) => t.id === T2),
  );
  expect(reloaded.getTableStatus(reloadedTable2).reservation?.id).toBe(
    must(seated).id,
  );
});

it('sends one seating when confirm is clicked twice', async () => {
  const walkIn = await walkInDialog();
  walkIn.selectTable(T2);
  await nextTick();
  const [first, second] = await Promise.all([walkIn.submit(), walkIn.submit()]);
  expect(first).toMatchObject({ status: 'CHECKED_IN' });
  expect(second).toBeNull();
  expect(server.posts).toHaveLength(1);
  expect(server.visits.filter((v) => v.tableId === T2)).toHaveLength(1);
});

it('retries a seating whose answer was lost with the same key and body, once', async () => {
  const walkIn = await walkInDialog();
  walkIn.selectTable(T2);
  await nextTick();
  server.failNext({ kind: 'network', committed: true });
  expect(await walkIn.submit()).toBeNull();
  // The server did seat them, so the table now reads as occupied; the retry
  // replays the same request rather than being blocked or seating twice.
  await flushPromises();
  expect(walkIn.canSubmit.value).toBe(true);
  const retried = await walkIn.submit();

  expect(retried).toMatchObject({ status: 'CHECKED_IN', tableId: T2 });
  expect(server.posts).toHaveLength(2);
  expect(server.posts[1].headers['Idempotency-Key']).toBe(
    server.posts[0].headers['Idempotency-Key'],
  );
  expect(server.posts[1].data).toEqual(server.posts[0].data);
  expect(server.visits.filter((v) => v.tableId === T2)).toHaveLength(1);
});

it('starts a new attempt after a definite failure or changed details', async () => {
  const walkIn = await walkInDialog();
  walkIn.selectTable(T2);
  await nextTick();
  server.failNext({ kind: 'status', status: 500 });
  await walkIn.submit();
  server.failNext({ kind: 'network', committed: false });
  await walkIn.submit();
  walkIn.guestName.value = 'Changed';
  await walkIn.submit();

  const keys = server.posts.map((p) => p.headers['Idempotency-Key']);
  expect(new Set(keys).size).toBe(3);
  expect(server.visits.filter((v) => v.tableId === T2)).toHaveLength(1);
});

it('lets two counters race for one table without overlapping occupancy', async () => {
  const first = await walkInDialog();
  const firstWrapper = wrapper;
  wrapper = undefined;
  const second = await walkInDialog();
  for (const walkIn of [first, second]) {
    walkIn.selectTable(T2);
    await nextTick();
  }
  const results = await Promise.all([first.submit(), second.submit()]);

  expect(results.filter(Boolean)).toHaveLength(1);
  expect(
    server.visits.filter((v) => v.tableId === T2 && v.status === 'CHECKED_IN'),
  ).toHaveLength(1);
  const loser = results[0] ? second : first;
  expect(loser.error.value).toMatch(/Choose another table|cannot be seated/);
  firstWrapper?.unmount();
});

it('drops walk-ins the server never accepted from stored state, keeping real sessions', async () => {
  // Seed the sample sessions, then add what the old counter fallback stored.
  await import('../data/reservations-store');
  const stored = JSON.parse(must(localStorage.getItem(STORAGE_KEY)));
  const legitimate = stored.find(
    (r: { id: string }) => r.id === 'res-thm-live',
  );
  expect(legitimate).toMatchObject({
    status: 'CHECKED_IN',
    tableId: 't-siam-1',
  });
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify([
      {
        ...legitimate,
        id: 'walkin-mf1abc2',
        tableId: '1',
        tableLabel: 'Table 1',
        customerName: 'QA Capacity Test',
        clientType: 'WALK_IN',
        notes: 'Counter Walk-in Session',
      },
      ...stored,
    ]),
  );
  vi.resetModules();
  const { useCustomerReservationsStore } = await import(
    '../data/reservations-store'
  );
  const reloaded = useCustomerReservationsStore().reservations.value;

  expect(reloaded.map((r) => r.id)).not.toContain('walkin-mf1abc2');
  expect(reloaded.map((r) => r.id)).toContain('walkin-siam-live');
  expect(reloaded.find((r) => r.id === 'res-thm-live')).toMatchObject({
    status: 'CHECKED_IN',
    tableId: 't-siam-1',
  });
  expect(JSON.parse(must(localStorage.getItem(STORAGE_KEY)))).toHaveLength(
    stored.length,
  );
});

it('keeps sample sessions on their own sample tables, matched by ID only', async () => {
  // A branch with no server tables shows the sample floor and sample sessions.
  server.tables.splice(0);
  server.visits.splice(0);
  // A stored session that names its table by display label must not claim it.
  await import('../data/reservations-store');
  const stored = JSON.parse(must(localStorage.getItem(STORAGE_KEY)));
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify([
      { ...stored[0], id: 'labelled-session', tableId: 'Table 1' },
      ...stored,
    ]),
  );
  vi.resetModules();
  const ws = await workspace();
  expect(ws.usingSampleFloor.value).toBe(true);
  const status = (id: string) =>
    ws.getTableStatus(must(ws.branchFloorTables.find((t) => t.id === id)));
  expect(status('t-siam-1').reservation?.id).toBe('res-thm-live');
  expect(status('t-siam-2').reservation?.id).toBe('walkin-siam-live');
  expect(ws.tableLabel('t-siam-1')).toBe('Table 1');
  expect(ws.tableLabel('Table 1')).toBe('—');
  // The walk-in dialog still asks the server, which has no tables to offer.
  ws.openWalkInForTable(ws.branchFloorTables[3]);
  await flushPromises();
  expect(ws.walkIn.tableOptions.value).toEqual([]);
  expect(ws.walkIn.blockedReason.value).toBe(
    'No tables are set up for this branch.',
  );
  expect(await ws.walkIn.submit()).toBeNull();
  expect(server.posts).toHaveLength(0);
});

it('never mixes sample sessions into a floor of server tables', async () => {
  const ws = await workspace();
  expect(ws.usingSampleFloor.value).toBe(false);
  expect(ws.branchFloorTables.map((t) => t.id)).toEqual([T1, T2, T3, T4]);
  expect(ws.allReservations.value.map((r) => r.id)).toEqual(['visit-original']);
  expect(ws.activeFloorReservations.value).toHaveLength(1);
});

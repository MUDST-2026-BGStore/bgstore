import { beforeEach, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { clearAuth, initAuth } from '@theparlor/auth';
import { queryClient } from '../../../shared/lib/api';
import { CART_SESSION_KEY, useCartStore } from './cart';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('../../../../../../libs/web/api-client/src/lib/transport.ts', () => ({
  apiRequest: request,
  configureApiTransport: vi.fn(),
}));

const SIAM = '00000000-0000-4000-8000-0000000000a1';
const ARI = '00000000-0000-4000-8000-0000000000a2';
const AZUL = '00000000-0000-4000-8000-0000000000e1';
const CATAN = '00000000-0000-4000-8000-0000000000e2';
const GUEST_CART = '00000000-0000-4000-8000-0000000000c1';
const ACCOUNT_CART = '00000000-0000-4000-8000-0000000000c2';
const TOKEN = 'guest-token-0123456789abcdef0123456789abcdef';

const fail = (status: number) =>
  Promise.reject(
    Object.assign(new Error(`HTTP ${status}`), { response: { status } }),
  );

/** A minimal stand-in for the cart API: prices and stock live here, not in the store. */
function fakeServer() {
  const stock: Record<string, number> = { [AZUL]: 4, [CATAN]: 0 };
  const carts = new Map<
    string,
    { branchId?: string; lines: Map<string, number>; token?: string }
  >();
  let authenticated = false;
  const view = (id: string) => {
    const cart = carts.get(id)!;
    const items = [...cart.lines].map(([editionId, quantity]) => ({
      id: editionId,
      editionId,
      gameId: `game-${editionId}`,
      editionKind: 'BASE_GAME',
      editionLanguage: 'en',
      gameName: editionId === AZUL ? 'Azul' : 'Catan',
      quantity,
      unitPrice: { amountMinor: 149000 },
      lineTotal: { amountMinor: 149000 * quantity },
      available: stock[editionId] ?? 0,
    }));
    const issues = items
      .filter((item) => item.quantity > item.available)
      .map((item) => ({
        lineId: item.id,
        code: 'INSUFFICIENT_STOCK',
        available: item.available,
      }));
    const subtotal = items.reduce(
      (sum, item) => sum + item.lineTotal.amountMinor,
      0,
    );
    const shippingFee = cart.branchId || subtotal >= 150000 ? 0 : 5000;
    return {
      id,
      fulfillment: cart.branchId
        ? { method: 'PICKUP', branchId: cart.branchId }
        : { method: 'SHIPPING' },
      items,
      issues,
      subtotal,
      shippingFee: { amountMinor: shippingFee },
      total: { amountMinor: subtotal + shippingFee },
      checkoutReady: items.length > 0 && issues.length === 0,
      createdAt: '2026-11-12T00:00:00Z',
      updatedAt: '2026-11-12T00:00:00Z',
    };
  };
  const calls: {
    method: string;
    url: string;
    headers: Record<string, string>;
    data?: any;
  }[] = [];
  let checkoutResult: () => Promise<unknown> = () =>
    Promise.resolve({ id: 'order-1', status: 'PLACED' });

  request.mockReset().mockImplementation((config, options) => {
    const method = (config.method ?? 'GET').toUpperCase();
    const url: string = config.url;
    const headers = { ...(config.headers ?? {}), ...(options?.headers ?? {}) };
    const data = config.data;
    calls.push({ method, url, headers, data });
    if (url === '/api/auth/session')
      return Promise.resolve(
        authenticated
          ? {
              authenticated: true,
              profile: {
                subject: 'sub-1',
                username: 'user',
                emailVerified: true,
                roles: [],
              },
            }
          : { authenticated: false },
      );
    if (url === '/api/v1/branches')
      return Promise.resolve({
        items: [
          {
            id: SIAM,
            name: 'Siam',
            address: 'A',
            openingHours: [],
            active: true,
            fulfillsDelivery: true,
          },
          {
            id: ARI,
            name: 'Ari',
            address: 'B',
            openingHours: [],
            active: true,
            fulfillsDelivery: false,
          },
        ],
      });
    if (url === '/api/v1/payments/tokens')
      return Promise.resolve({ token: 'pay-token' });
    if (url === '/api/v1/me/cart') {
      if (!authenticated) return fail(401);
      if (!carts.has(ACCOUNT_CART))
        carts.set(ACCOUNT_CART, { lines: new Map() });
      return Promise.resolve(view(ACCOUNT_CART));
    }
    if (url === '/api/v1/me/cart/merge') {
      const guest = carts.get(data.cartId);
      if (!guest || guest.token !== data.cartToken) return fail(404);
      if (!carts.has(ACCOUNT_CART))
        carts.set(ACCOUNT_CART, { lines: new Map() });
      const mine = carts.get(ACCOUNT_CART)!;
      for (const [edition, quantity] of guest.lines)
        mine.lines.set(edition, (mine.lines.get(edition) ?? 0) + quantity);
      carts.delete(data.cartId);
      return Promise.resolve(view(ACCOUNT_CART));
    }
    if (url === '/api/v1/carts' && method === 'POST') {
      carts.set(GUEST_CART, { lines: new Map(), token: TOKEN });
      return Promise.resolve({ ...view(GUEST_CART), token: TOKEN });
    }
    const match =
      /^\/api\/v1\/carts\/([^/]+)(?:\/(items|checkout))?(?:\/([^/]+))?$/.exec(
        url,
      );
    if (!match) return fail(500);
    const [, id, section, lineId] = match;
    const cart = carts.get(id);
    if (!cart || (cart.token && headers['X-Cart-Token'] !== cart.token))
      return fail(404);
    if (section === 'checkout') return checkoutResult();
    if (section === 'items' && method === 'POST') {
      const quantity = (cart.lines.get(data.editionId) ?? 0) + data.quantity;
      if (quantity > (stock[data.editionId] ?? 0)) return fail(409);
      cart.lines.set(data.editionId, quantity);
    } else if (section === 'items' && method === 'PATCH') {
      if (!cart.lines.has(lineId)) return fail(404);
      cart.lines.set(lineId, data.quantity);
    } else if (section === 'items' && method === 'DELETE') {
      cart.lines.delete(lineId);
      return Promise.resolve(undefined);
    } else if (method === 'PATCH') {
      cart.branchId =
        data.fulfillment.method === 'PICKUP'
          ? data.fulfillment.pickupBranchId
          : undefined;
    }
    return Promise.resolve(view(id));
  });

  return {
    calls,
    carts,
    stock,
    signIn: async () => {
      authenticated = true;
      await initAuth();
    },
    failCheckoutWith: (result: () => Promise<unknown>) =>
      (checkoutResult = result),
    sent: (method: string, pattern: RegExp) =>
      calls.filter((call) => call.method === method && pattern.test(call.url)),
  };
}

let server: ReturnType<typeof fakeServer>;
beforeEach(() => {
  localStorage.clear();
  queryClient.clear();
  clearAuth();
  setActivePinia(createPinia());
  server = fakeServer();
});

it('opens a guest cart on the first add and keeps only its ID and token in the browser', async () => {
  const cart = useCartStore();
  await cart.load();
  expect(server.sent('POST', /\/carts$/)).toHaveLength(0);

  expect(await cart.add(AZUL, 2)).toBe(true);
  expect(server.sent('POST', /\/carts$/)[0].headers['Idempotency-Key']).toMatch(
    /^[0-9a-f-]{36}$/,
  );
  expect(server.sent('POST', /\/items$/)[0].headers['X-Cart-Token']).toBe(
    TOKEN,
  );
  expect(cart.lines.map((line) => [line.variantId, line.quantity])).toEqual([
    [AZUL, 2],
  ]);
  expect(cart.count).toBe(2);
  // Prices come from the server in minor units and are never stored locally.
  expect(cart.subtotal).toBe(298000);
  expect(JSON.parse(localStorage.getItem(CART_SESSION_KEY)!)).toEqual({
    id: GUEST_CART,
    token: TOKEN,
  });

  setActivePinia(createPinia());
  const restored = useCartStore();
  await restored.load();
  expect(restored.count).toBe(2);
});

it('adopts where the shopper was browsing for the first line only', async () => {
  const cart = useCartStore();
  expect(await cart.add(AZUL, 1, { method: 'pickup', branchId: ARI })).toBe(
    true,
  );
  expect(cart.method).toBe('pickup');
  expect(cart.fulfillmentBranch?.name).toBe('Ari');

  expect(await cart.add(AZUL, 1, { method: 'shipping', branchId: SIAM })).toBe(
    true,
  );
  expect(cart.method).toBe('pickup');
  expect(server.sent('PATCH', /\/carts\/[^/]+$/)).toHaveLength(1);
});

it('uses the server shipping fee and total without recalculating them', async () => {
  const cart = useCartStore();
  await cart.add(AZUL);
  expect(cart.subtotal).toBe(149000);
  expect(cart.shippingFee).toBe(5000);
  expect(cart.total).toBe(154000);

  await cart.add(AZUL);
  expect(cart.subtotal).toBe(298000);
  expect(cart.shippingFee).toBe(0);
  expect(cart.total).toBe(298000);

  await cart.setFulfillment('pickup', ARI);
  expect(cart.shippingFee).toBe(0);
  expect(cart.total).toBe(298000);
});

it('reports a stock conflict from the server and never guesses stock itself', async () => {
  const cart = useCartStore();
  expect(await cart.add(CATAN)).toBe(false);
  expect(cart.lastFailure).toBe('stock');
  expect(await cart.add(AZUL, 4)).toBe(true);
  expect(await cart.add(AZUL)).toBe(false);
  expect(cart.count).toBe(4);

  // An explicit quantity is kept and flagged by the server, not clamped here.
  server.stock[AZUL] = 1;
  expect(await cart.update(AZUL, 3)).toBe(true);
  expect(cart.lines[0]).toMatchObject({
    quantity: 3,
    available: 1,
    problem: true,
    issue: 'INSUFFICIENT_STOCK',
  });
  expect(cart.canCheckout).toBe(false);
});

it('rejects invalid quantities without calling the API and removes a line at zero', async () => {
  const cart = useCartStore();
  await cart.add(AZUL, 2);
  const before = server.calls.length;
  for (const quantity of [NaN, Infinity, -1, 1.5, 100])
    expect(await cart.update(AZUL, quantity)).toBe(false);
  for (const quantity of [NaN, 0, 1.5, 100])
    expect(await cart.add(AZUL, quantity)).toBe(false);
  expect(server.calls.length).toBe(before);

  expect(await cart.update(AZUL, 0)).toBe(true);
  expect(cart.lines).toEqual([]);
  expect(cart.subtotal).toBe(0);
});

it('sends a pickup choice only once a branch is chosen', async () => {
  const cart = useCartStore();
  await cart.add(AZUL);
  expect(cart.method).toBe('shipping');
  expect(cart.fulfillmentBranch?.name).toBe('Siam');

  expect(await cart.setFulfillment('pickup')).toBe(false);
  expect(cart.method).toBe('pickup');
  expect(cart.pickupBranchId).toBe('');
  expect(cart.canCheckout).toBe(false);
  expect(server.sent('PATCH', /\/carts\/[^/]+$/)).toHaveLength(0);

  expect(await cart.setFulfillment('pickup', ARI)).toBe(true);
  expect(cart.pickupBranchId).toBe(ARI);
  expect(cart.canCheckout).toBe(true);
  // Changing fulfillment never removes lines.
  expect(cart.count).toBe(1);
});

it('forgets a guest cart the server no longer knows and ignores corrupt storage', async () => {
  localStorage.setItem(
    CART_SESSION_KEY,
    JSON.stringify({ id: GUEST_CART, token: TOKEN }),
  );
  const cart = useCartStore();
  await cart.load();
  expect(cart.lines).toEqual([]);
  expect(localStorage.getItem(CART_SESSION_KEY)).toBeNull();

  localStorage.setItem(CART_SESSION_KEY, '{');
  setActivePinia(createPinia());
  const fresh = useCartStore();
  await fresh.load();
  expect(fresh.status).toBe('ready');
  expect(fresh.lines).toEqual([]);
});

it('merges the guest cart into the account cart on sign-in and drops the token', async () => {
  const cart = useCartStore();
  await cart.add(AZUL, 2);
  await server.signIn();
  await cart.load();
  expect(server.sent('POST', /\/me\/cart\/merge$/)[0].data).toEqual({
    cartId: GUEST_CART,
    cartToken: TOKEN,
  });
  expect(cart.count).toBe(2);
  expect(localStorage.getItem(CART_SESSION_KEY)).toBeNull();

  await cart.add(AZUL, 1);
  expect(
    server.sent('POST', /\/items$/).at(-1)!.headers['X-Cart-Token'],
  ).toBeUndefined();
  expect(server.sent('POST', /\/carts$/)).toHaveLength(1);
});

const card = {
  number: '4242 4242 4242 4242',
  expMonth: 12,
  expYear: 2030,
  cvc: '123',
  holderName: 'Test',
};
const details = {
  contact: {
    name: 'Guest',
    email: 'guest@example.invalid',
    phone: '0812345678',
  },
};

it('pays with a card token and clears the spent cart after the order is placed', async () => {
  const cart = useCartStore();
  await cart.add(AZUL);
  const order = await cart.checkout(card, details);
  expect(order.id).toBe('order-1');
  const checkout = server.sent('POST', /\/checkout$/)[0];
  expect(checkout.data).toEqual({ ...details, paymentToken: 'pay-token' });
  expect(checkout.headers['X-Cart-Token']).toBe(TOKEN);
  expect(checkout.headers['Idempotency-Key']).toMatch(/^[0-9a-f-]{36}$/);
  // The card itself only ever goes to tokenization.
  expect(JSON.stringify(checkout.data)).not.toContain('4242');
  expect(cart.lines).toEqual([]);
  expect(localStorage.getItem(CART_SESSION_KEY)).toBeNull();
});

it('reuses the idempotency key after a lost response but not after a definite answer', async () => {
  const cart = useCartStore();
  await cart.add(AZUL);
  const key = () =>
    server.sent('POST', /\/checkout$/).at(-1)!.headers['Idempotency-Key'];

  server.failCheckoutWith(() => Promise.reject(new Error('network')));
  await expect(cart.checkout(card, details)).rejects.toThrow('network');
  const lost = key();
  await expect(cart.checkout(card, details)).rejects.toThrow('network');
  expect(key()).toBe(lost);

  server.failCheckoutWith(() => fail(402));
  await expect(cart.checkout(card, details)).rejects.toThrow();
  expect(key()).toBe(lost);
  await expect(cart.checkout(card, details)).rejects.toThrow();
  expect(key()).not.toBe(lost);
  // A declined payment leaves the cart as it was.
  expect(cart.count).toBe(1);
  expect(localStorage.getItem(CART_SESSION_KEY)).not.toBeNull();
});

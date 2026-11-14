import { expect, test } from '@playwright/test';
import {
  ASSIGNED_BRANCH,
  OTHER_BRANCH,
  PERSONAS,
  type PersonaKey,
} from './support/personas';
import { idempotencyKey, signIn, type Session } from './support/session';

/**
 * The whole business on the live stack, one person at a time: a customer, branch staff,
 * staff with no branch, a manager and an administrator, each signed in through Keycloak with
 * the users `make seed-users` creates. Writes go through each person's own session, so every
 * role check is the server's. Nothing is mocked.
 */
test.describe.configure({ mode: 'serial' });

const ALL = Object.keys(PERSONAS) as PersonaKey[];
const sessions = {} as Record<PersonaKey, Session>;
const subjects = {} as Record<PersonaKey, string>;
const branchIds = {} as Record<string, string>;
let edition: { id: string; name: string; price: number };
let startingOnHand = 0;

interface Order {
  id: string;
  branchId: string;
  status: string;
  fulfillmentMethod: string;
  carrier?: string;
  trackingNumber?: string;
  total: { amountMinor: number };
  payment: { method: string; status: string };
}

const SHIPPING_ADDRESS = {
  recipientName: 'Dev Customer',
  line1: '1 Test Road',
  district: 'Pathum Wan',
  province: 'Bangkok',
  postalCode: '10330',
};

test.beforeAll(async ({ browser, baseURL }) => {
  const url = baseURL as string;
  await Promise.all(
    ALL.map(async (persona) => {
      sessions[persona] = await signIn(browser, url, persona);
    }),
  );
  for (const persona of ALL) {
    const session = await sessions[persona].json<{
      profile: { subject: string };
    }>('GET', '/api/auth/session', 200);
    subjects[persona] = session.profile.subject;
  }
  const { items } = await sessions.customer.json<{
    items: { id: string; name: string }[];
  }>('GET', '/api/v1/branches', 200);
  for (const branch of items) branchIds[branch.name] = branch.id;
  expect(
    branchIds[ASSIGNED_BRANCH],
    `seeded branch ${ASSIGNED_BRANCH}`,
  ).toBeTruthy();
  expect(branchIds[OTHER_BRANCH], `seeded branch ${OTHER_BRANCH}`).toBeTruthy();

  // Any edition the delivery branch can sell several times over.
  const { items: games } = await sessions.customer.json<{
    items: {
      game: { name: string };
      saleEnabled: boolean;
      editions: {
        editionId: string;
        active: boolean;
        saleAvailable: number;
        salePrice: { amountMinor: number };
      }[];
    }[];
  }>('GET', `/api/v1/branches/${branchIds[ASSIGNED_BRANCH]}/games`, 200, {
    params: { limit: 100 },
  });
  for (const game of games) {
    const found = game.saleEnabled
      ? game.editions.find((item) => item.active && item.saleAvailable >= 5)
      : undefined;
    if (found) {
      edition = {
        id: found.editionId,
        name: game.game.name,
        price: found.salePrice.amountMinor,
      };
      break;
    }
  }
  expect(edition, 'an edition with stock at the delivery branch').toBeTruthy();
  startingOnHand = (await stock()).onHand;
});

test.afterAll(async () => {
  // Sold copies leave the shelf for good; put the shelf back so repeated runs and the
  // other specs keep the stock they found.
  try {
    const delta = startingOnHand - (await stock()).onHand;
    if (delta !== 0)
      await sessions.manager.json(
        'POST',
        `/api/v1/branches/${siam()}/editions/${edition.id}/stock-adjustments`,
        201,
        { data: { delta, reason: 'E2E: restore stock sold during the run' } },
      );
  } finally {
    await Promise.all(ALL.map((persona) => sessions[persona]?.context.close()));
  }
});

const siam = () => branchIds[ASSIGNED_BRANCH];
const ari = () => branchIds[OTHER_BRANCH];

/** Sale stock of the shared edition at the delivery branch, read as the public sees it. */
async function stock() {
  const { items } = await sessions.customer.json<{
    items: {
      editions: { editionId: string; saleOnHand: number; saleHeld: number }[];
    }[];
  }>('GET', `/api/v1/branches/${siam()}/games`, 200, {
    params: { limit: 100 },
  });
  const row = items
    .flatMap((game) => game.editions)
    .find((item) => item.editionId === edition.id);
  if (!row) throw new Error('The edition is no longer on sale at the branch');
  return { onHand: row.saleOnHand, held: row.saleHeld };
}

/** Puts the customer's open cart back to an empty delivery cart, then adds one edition. */
async function fillDeliveryCart(quantity = 1) {
  const customer = sessions.customer;
  const cart = await customer.json<{ id: string; items: { id: string }[] }>(
    'GET',
    '/api/v1/me/cart',
    200,
  );
  for (const line of cart.items)
    await customer.json(
      'DELETE',
      `/api/v1/carts/${cart.id}/items/${line.id}`,
      200,
    );
  await customer.json('PATCH', `/api/v1/carts/${cart.id}`, 200, {
    data: { fulfillment: { method: 'SHIPPING' } },
  });
  const filled = await customer.json<{
    checkoutReady: boolean;
    fulfillment: { method: string };
  }>('POST', `/api/v1/carts/${cart.id}/items`, 200, {
    data: { editionId: edition.id, quantity },
  });
  expect(filled.fulfillment.method).toBe('SHIPPING');
  expect(filled.checkoutReady).toBe(true);
  return cart.id;
}

/** Places the cart's contents as a cash-on-delivery order, as the checkout page would. */
async function placeCashOnDeliveryOrder() {
  const cartId = await fillDeliveryCart();
  const order = await sessions.customer.json<Order>(
    'POST',
    `/api/v1/carts/${cartId}/checkout`,
    201,
    {
      headers: idempotencyKey(),
      data: { shippingAddress: SHIPPING_ADDRESS, paymentMethod: 'COD' },
    },
  );
  expect(order.status).toBe('PLACED');
  return order;
}

const fulfil = (who: PersonaKey, order: Order, data?: unknown) =>
  sessions[who].call(
    'POST',
    `/api/v1/branches/${order.branchId}/orders/${order.id}/fulfill`,
    {
      data,
    },
  );

test('every sign-in carries exactly its own roles and a ready account', async () => {
  const privileged = ['staff', 'manager', 'admin'];
  for (const persona of ALL) {
    const { profile } = await sessions[persona].json<{
      profile: { roles: string[]; username: string };
    }>('GET', '/api/auth/session', 200);
    expect(profile.username).toBe(PERSONAS[persona].username);
    for (const role of privileged)
      expect(profile.roles.includes(role), `${persona} has role ${role}`).toBe(
        PERSONAS[persona].roles.some((owned) => owned === role),
      );
    // Seeded accounts skip onboarding.
    await sessions[persona].json('GET', '/api/v1/me', 200);
  }
});

test('branch operations open only to assigned staff, managers and administrators', async ({
  request,
}) => {
  const expected: Record<PersonaKey, [number, number]> = {
    customer: [403, 403],
    staff: [200, 403],
    staffUnassigned: [403, 403],
    manager: [200, 200],
    admin: [200, 200],
  };
  for (const persona of ALL) {
    const [atAssigned, atOther] = expected[persona];
    for (const [branch, status] of [
      [siam(), atAssigned],
      [ari(), atOther],
    ] as const) {
      for (const path of ['orders', 'reservations']) {
        const response = await sessions[persona].call(
          'GET',
          `/api/v1/branches/${branch}/${path}`,
        );
        expect(
          response.status(),
          `${persona} lists ${path} of ${branch === siam() ? ASSIGNED_BRANCH : OTHER_BRANCH}`,
        ).toBe(status);
      }
    }
  }
  const anonymous = await request.get(`/api/v1/branches/${siam()}/orders`);
  expect(anonymous.status()).toBe(401);
});

test('only administrators assign staff to branches', async () => {
  const target = subjects.staffUnassigned;
  const grant = `/api/v1/admin/branches/${siam()}/staff-assignments/${target}`;
  const listing = `/api/v1/branches/${siam()}/staff-assignments`;

  for (const persona of ['customer', 'staff', 'staffUnassigned'] as const)
    expect(
      (await sessions[persona].call('GET', listing)).status(),
      persona,
    ).toBe(403);
  expect((await sessions.manager.call('GET', listing)).status()).toBe(200);
  for (const persona of ['customer', 'staff', 'manager'] as const) {
    expect(
      (await sessions[persona].call('PUT', grant)).status(),
      `${persona} grants`,
    ).toBe(403);
    expect(
      (
        await sessions[persona].call('GET', '/api/v1/admin/accounts', {
          params: { q: 'Dev' },
        })
      ).status(),
      `${persona} searches people`,
    ).toBe(403);
  }
  const found = await sessions.admin.json<{ items: { subject: string }[] }>(
    'GET',
    '/api/v1/admin/accounts',
    200,
    { params: { q: 'Unassigned' } },
  );
  expect(found.items.map((match) => match.subject)).toContain(target);

  try {
    await sessions.admin.json('PUT', grant, 200);
    const assigned = await sessions.manager.json<{
      items: { staffSubject: string }[];
    }>('GET', listing, 200);
    expect(assigned.items.map((item) => item.staffSubject)).toContain(target);
    // The grant opens that branch and no other.
    expect(
      (
        await sessions.staffUnassigned.call(
          'GET',
          `/api/v1/branches/${siam()}/orders`,
        )
      ).status(),
    ).toBe(200);
    expect(
      (
        await sessions.staffUnassigned.call(
          'GET',
          `/api/v1/branches/${ari()}/orders`,
        )
      ).status(),
    ).toBe(403);
  } finally {
    expect((await sessions.admin.call('DELETE', grant)).status()).toBe(204);
  }
  expect(
    (
      await sessions.staffUnassigned.call(
        'GET',
        `/api/v1/branches/${siam()}/orders`,
      )
    ).status(),
  ).toBe(403);
});

test('a customer pays cash on delivery and staff dispatch the parcel and collect the cash', async () => {
  const before = await stock();
  const order = await placeCashOnDeliveryOrder();
  expect(order.fulfillmentMethod).toBe('SHIPPING');
  expect(order.branchId).toBe(siam());
  expect(order.payment).toMatchObject({ method: 'COD', status: 'PENDING' });
  expect(order.total.amountMinor).toBeGreaterThanOrEqual(edition.price);
  expect(await stock()).toEqual({
    onHand: before.onHand,
    held: before.held + 1,
  });

  const here = `/api/v1/branches/${order.branchId}/orders/${order.id}`;
  // Only the owner and operators of this branch can read it.
  expect((await sessions.staffUnassigned.call('GET', here)).status()).toBe(403);
  expect((await sessions.staff.call('GET', here)).status()).toBe(200);

  // The customer, staff with no branch and staff of another branch cannot dispatch it.
  const shipment = { carrier: 'KERRY', trackingNumber: 'E2E-TRACK-0001' };
  for (const persona of ['customer', 'staffUnassigned'] as const)
    expect((await fulfil(persona, order, shipment)).status(), persona).toBe(
      403,
    );
  // A parcel needs its carrier and tracking number.
  expect((await fulfil('staff', order)).status()).toBe(400);
  expect((await fulfil('staff', order, { carrier: 'KERRY' })).status()).toBe(
    400,
  );

  const shipped = await (await fulfil('staff', order, shipment)).json();
  expect(shipped).toMatchObject({ status: 'FULFILLED', ...shipment });
  // Cash on delivery stays unpaid until the courier hands the cash over.
  expect(shipped.payment).toMatchObject({ method: 'COD', status: 'PENDING' });
  expect(await stock()).toEqual({
    onHand: before.onHand - 1,
    held: before.held,
  });

  const confirm = `${here}/payment/confirm`;
  for (const persona of ['customer', 'staffUnassigned'] as const)
    expect(
      (await sessions[persona].call('POST', confirm)).status(),
      persona,
    ).toBe(403);
  const paid = await sessions.staff.json<Order>('POST', confirm, 200);
  expect(paid.payment.status).toBe('CAPTURED');
  expect(
    (await sessions.staff.json<Order>('POST', confirm, 200)).payment.status,
  ).toBe('CAPTURED');

  const mine = await sessions.customer.json<{ items: Order[] }>(
    'GET',
    '/api/v1/me/orders',
    200,
    {
      params: { limit: 50 },
    },
  );
  expect(mine.items.find((item) => item.id === order.id)).toMatchObject({
    status: 'FULFILLED',
    ...shipment,
    payment: { method: 'COD', status: 'CAPTURED' },
  });
});

test('a customer cancels before dispatch and the stock returns to the shelf', async () => {
  const before = await stock();
  const order = await placeCashOnDeliveryOrder();
  expect((await stock()).held).toBe(before.held + 1);

  const cancel = `/api/v1/branches/${order.branchId}/orders/${order.id}/cancel`;
  expect(
    (
      await sessions.staffUnassigned.call('POST', cancel, {
        data: { reason: 'Not mine' },
      })
    ).status(),
  ).toBe(403);
  const cancelled = await sessions.customer.json<
    Order & { cancellationReason: string }
  >('POST', cancel, 200, { data: { reason: 'Changed my mind' } });
  expect(cancelled).toMatchObject({
    status: 'CANCELLED',
    cancellationReason: 'Changed my mind',
  });
  expect(await stock()).toEqual(before);
  // A cancelled order cannot be dispatched.
  expect(
    (
      await fulfil('staff', order, {
        carrier: 'FLASH',
        trackingNumber: 'E2E-TRACK-0002',
      })
    ).status(),
  ).toBe(409);
});

test('staff record a counter sale for a walk-in and hand it over', async () => {
  const before = await stock();
  const sale = {
    customer: { kind: 'GUEST', displayName: 'Walk-in E2E' },
    items: [{ editionId: edition.id, quantity: 1 }],
  };
  const counter = (branch: string) => `/api/v1/branches/${branch}/staff/orders`;
  for (const persona of ['customer', 'staffUnassigned'] as const)
    expect(
      (
        await sessions[persona].call('POST', counter(siam()), {
          headers: idempotencyKey(),
          data: sale,
        })
      ).status(),
      persona,
    ).toBe(403);
  // Staff operate only their own branch, even at the counter.
  expect(
    (
      await sessions.staff.call('POST', counter(ari()), {
        headers: idempotencyKey(),
        data: sale,
      })
    ).status(),
  ).toBe(403);

  const order = await sessions.staff.json<Order & { source: string }>(
    'POST',
    counter(siam()),
    201,
    { headers: idempotencyKey(), data: sale },
  );
  expect(order).toMatchObject({ status: 'PLACED', source: 'STAFF_COUNTER' });
  expect(order.payment.method).toBe('COUNTER');
  expect((await stock()).held).toBe(before.held + 1);

  const handed = await (await fulfil('staff', order)).json();
  expect(handed).toMatchObject({ status: 'FULFILLED' });
  expect(handed.payment.status).toBe('CAPTURED');
  expect(await stock()).toEqual({
    onHand: before.onHand - 1,
    held: before.held,
  });
  // The manager sees it in the branch list too.
  const listed = await sessions.manager.json<{ items: Order[] }>(
    'GET',
    `/api/v1/branches/${siam()}/orders`,
    200,
    { params: { limit: 100 } },
  );
  expect(listed.items.map((item) => item.id)).toContain(order.id);
});

test('only branch operators receive stock', async () => {
  const receipt = (branch: string) =>
    `/api/v1/branches/${branch}/editions/${edition.id}/stock-adjustments`;
  const data = { delta: 1, reason: 'E2E: receive one copy' };
  const before = await stock();
  for (const persona of ['customer', 'staffUnassigned'] as const)
    expect(
      (
        await sessions[persona].call('POST', receipt(siam()), { data })
      ).status(),
      persona,
    ).toBe(403);
  expect(
    (await sessions.staff.call('POST', receipt(ari()), { data })).status(),
  ).toBe(403);
  await sessions.staff.json('POST', receipt(siam()), 201, { data });
  expect((await stock()).onHand).toBe(before.onHand + 1);
});

test('a customer pays by card for pickup and a declined card holds nothing', async () => {
  const configuration = await sessions.customer.json<{
    cardTokenization: string;
  }>('GET', '/api/v1/payments/configuration', 200);
  test.skip(
    configuration.cardTokenization !== 'SERVER',
    'Cards are tokenized by the hosted provider; only the development gateway can be driven here.',
  );
  const card = (number: string) => ({
    number,
    expMonth: 12,
    expYear: new Date().getFullYear() + 2,
    cvc: '123',
    holderName: 'Dev Customer',
  });
  const tokenFor = async (number: string) =>
    (
      await sessions.customer.json<{ token: string }>(
        'POST',
        '/api/v1/payments/tokens',
        201,
        {
          data: card(number),
        },
      )
    ).token;
  const place = async (number: string, expected: number) =>
    sessions.customer
      .call('POST', `/api/v1/branches/${siam()}/orders`, {
        headers: idempotencyKey(),
        data: {
          items: [{ editionId: edition.id, quantity: 1 }],
          paymentMethod: 'CARD',
          paymentToken: await tokenFor(number),
        },
      })
      .then((response) => {
        expect(response.status()).toBe(expected);
        return response;
      });

  const before = await stock();
  await place('4000000000000002', 402);
  expect(await stock()).toEqual(before);

  const order = (await (await place('4242424242424242', 201)).json()) as Order;
  expect(order.payment.status).toBe('AUTHORIZED');
  expect((await stock()).held).toBe(before.held + 1);
  const handed = await (await fulfil('staff', order)).json();
  expect(handed).toMatchObject({
    status: 'FULFILLED',
    payment: { status: 'CAPTURED' },
  });
  expect(await stock()).toEqual({
    onHand: before.onHand - 1,
    held: before.held,
  });
});

test('a customer books a table, staff see it, and the customer cancels', async () => {
  const { items: tables } = await sessions.customer.json<{
    items: { id: string; capacity: number; operationalStatus: string }[];
  }>('GET', `/api/v1/branches/${siam()}/tables`, 200);
  const table = tables.find(
    (item) => item.operationalStatus === 'IN_SERVICE' && item.capacity >= 2,
  );
  if (!table) throw new Error('The branch has no table in service');
  // A distant afternoon inside every branch's opening hours (Bangkok time), varied so a
  // booking an interrupted run left behind cannot collide with this one.
  const day = new Date(
    Date.now() + (14 + Math.floor(Math.random() * 300)) * 86_400_000,
  );
  const date = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Bangkok',
  }).format(day);
  const request = {
    tableId: table.id,
    startsAt: `${date}T14:00:00+07:00`,
    endsAt: `${date}T16:00:00+07:00`,
    partySize: 2,
    games: [],
  };
  const reservations = `/api/v1/branches/${siam()}/reservations`;

  const booked = await sessions.customer.json<{
    id: string;
    status: string;
    source: string;
  }>('POST', reservations, 201, { headers: idempotencyKey(), data: request });
  expect(booked).toMatchObject({ status: 'CONFIRMED', source: 'CUSTOMER' });
  const cancel = `${reservations}/${booked.id}/cancel`;
  try {
    const mine = await sessions.customer.json<{ items: { id: string }[] }>(
      'GET',
      '/api/v1/me/reservations',
      200,
      { params: { limit: 100 } },
    );
    expect(mine.items.map((item) => item.id)).toContain(booked.id);

    // Staff of the branch see it; the customer and staff with no branch do not get the list.
    const window = {
      from: `${date}T00:00:00+07:00`,
      until: `${date}T23:59:59+07:00`,
    };
    const seen = await sessions.staff.json<{ items: { id: string }[] }>(
      'GET',
      reservations,
      200,
      {
        params: window,
      },
    );
    expect(seen.items.map((item) => item.id)).toContain(booked.id);
    for (const persona of ['customer', 'staffUnassigned'] as const)
      expect(
        (
          await sessions[persona].call('GET', reservations, { params: window })
        ).status(),
        persona,
      ).toBe(403);
    expect(
      (
        await sessions.staffUnassigned.call('POST', cancel, {
          data: { reason: 'Not allowed' },
        })
      ).status(),
    ).toBe(403);
  } finally {
    const cancelled = await sessions.customer.json<{ status: string }>(
      'POST',
      cancel,
      200,
      {
        data: { reason: 'E2E cleanup' },
      },
    );
    expect(cancelled.status).toBe('CANCELLED');
  }
});

test('the workspace shows each person only what their role allows', async () => {
  // A customer's workspace is their profile; every branch page sends them to "Access denied".
  const customer = sessions.customer.page;
  await customer.goto('/workspace');
  await expect(
    customer.getByRole('link', { name: /^Your profile\b/ }),
  ).toBeVisible();
  await expect(
    customer.getByRole('link', { name: /Branch Management/ }),
  ).toHaveCount(0);
  for (const path of ['/workspace/management', '/management', '/admin']) {
    await customer.goto(path);
    await expect(customer).toHaveURL(/\/forbidden$/);
    await expect(
      customer.getByRole('heading', { name: 'Access denied' }),
    ).toBeVisible();
  }

  // Branch staff and managers reach branch management but not administration.
  for (const persona of ['staff', 'manager'] as const) {
    const page = sessions[persona].page;
    await page.goto('/workspace');
    await expect(
      page.getByRole('link', { name: /Branch Management/ }),
    ).toBeVisible();
    await page.goto('/workspace/management');
    await expect(page).toHaveURL(/\/workspace\/management$/);
    await page.goto('/admin');
    await expect(page).toHaveURL(/\/forbidden$/);
  }

  // Administrators reach both.
  const admin = sessions.admin.page;
  await admin.goto('/workspace/management');
  await expect(admin).toHaveURL(/\/workspace\/management$/);
  await admin.goto('/admin');
  await expect(admin).toHaveURL(/\/admin$/);
  await expect(
    admin.getByRole('heading', { name: 'Administration' }),
  ).toBeVisible();
});

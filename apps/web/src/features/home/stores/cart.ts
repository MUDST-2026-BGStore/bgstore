import { computed, ref, watch } from 'vue';
import { defineStore } from 'pinia';
import {
  addCartItem,
  checkoutCart,
  createCart,
  createPaymentToken,
  getCart,
  getMyCart,
  mergeCart,
  removeCartItem,
  updateCart,
  updateCartItem,
  type Cart,
  type CartIssueCode,
  type CheckoutCartRequest,
  type CreatePaymentTokenRequest,
  type Order,
} from '@theparlor/api-client';
import { useAuthStore } from '../../../shared/stores/auth';
import { queryClient } from '../../../shared/lib/api';
import { httpStatus } from '../../../shared/lib/api-errors';
import { useBranchDirectoryStore } from './branch-directory';

/** The guest cart's ID and bearer token: the only cart data kept in the browser. */
export const CART_SESSION_KEY = 'theparlor.cart.session.v1';

/** Must stay aligned with the backend ShippingPolicy free-shipping threshold. */
export const FREE_SHIPPING_FROM_MINOR = 150_000;

export type CartFailure =
  | 'stock' // not enough units, or not sold at the cart's location
  | 'invalid' // rejected input
  | 'gone' // the cart or line no longer exists
  | 'error'; // network or server failure

export interface CartLine {
  /** The edition ID; kept as variantId because every cart view keys lines by it. */
  variantId: string;
  quantity: number;
  /** Units that can be bought at the cart's fulfillment location right now. */
  available: number;
  /** Why the line blocks checkout, if it does. */
  issue?: CartIssueCode;
  problem: boolean;
  /** Line total in THB minor units. */
  total: number;
  variant: { id: string; language: string; kind: string; price: number };
  game: { id: string; name: string };
}

interface Session {
  id: string;
  token: string;
}

function restoreSession(): Session | null {
  try {
    const value: unknown = JSON.parse(
      localStorage.getItem(CART_SESSION_KEY) || 'null',
    );
    const session = value as Partial<Session> | null;
    return typeof session?.id === 'string' && typeof session.token === 'string'
      ? { id: session.id, token: session.token }
      : null;
  } catch {
    return null;
  }
}

/**
 * The shopper's cart, held on the server. This store keeps only the last server
 * response and, for guests, the cart's ID and token. Prices, stock and issues are
 * never computed here: every change is sent to the API and its answer replaces
 * the state, so the page always shows what checkout will charge.
 *
 * Signed-in shoppers use their account cart; a guest cart is merged into it on
 * sign-in. All amounts are THB minor units.
 */
export const useCartStore = defineStore('shopping-cart', () => {
  const auth = useAuthStore();
  const directory = useBranchDirectoryStore();

  const server = ref<Cart | null>(null);
  const session = ref<Session | null>(restoreSession());
  const status = ref<'idle' | 'loading' | 'ready' | 'error'>('idle');
  const busy = ref(false);
  const lastFailure = ref<CartFailure | null>(null);
  /** Pickup was chosen but no branch yet; the server cannot store that state. */
  const choosingPickupBranch = ref(false);
  let loading: Promise<void> | undefined;

  function saveSession(next: Session | null) {
    session.value = next;
    try {
      if (next) localStorage.setItem(CART_SESSION_KEY, JSON.stringify(next));
      else localStorage.removeItem(CART_SESSION_KEY);
    } catch {
      /* The cart still works for this visit. */
    }
  }

  /** Guests prove ownership with the token; an account cart needs only the session. */
  const options = () =>
    session.value
      ? { headers: { 'X-Cart-Token': session.value.token } }
      : undefined;

  function failure(error: unknown): CartFailure {
    const code = httpStatus(error);
    return code === 409
      ? 'stock'
      : code === 404
        ? 'gone'
        : code === 400
          ? 'invalid'
          : 'error';
  }

  async function fetchCart(): Promise<void> {
    if (auth.authenticated) {
      const guest = session.value;
      let guestCartGone = false;
      if (guest) {
        // Signing in moves the guest cart into the account cart, which consumes it.
        try {
          server.value = await mergeCart({
            cartId: guest.id,
            cartToken: guest.token,
          });
          saveSession(null);
          return;
        } catch (error) {
          const code = httpStatus(error);
          if (code === 409) saveSession(null);
          // 404 is either a guest cart that no longer exists or an account without
          // a profile yet; reading the account cart below tells them apart.
          else if (code === 404) guestCartGone = true;
          else throw error;
        }
      }
      try {
        server.value = await getMyCart();
        if (guestCartGone) saveSession(null);
        return;
      } catch (error) {
        if (httpStatus(error) !== 404) throw error;
        // Signed in without an account profile: keep shopping with a guest cart.
      }
    }
    await fetchGuestCart();
  }

  async function fetchGuestCart(): Promise<void> {
    if (!session.value) {
      server.value = null;
      return;
    }
    try {
      server.value = await getCart(session.value.id, options());
    } catch (error) {
      // 404: unknown cart or token. 409: the cart was checked out or abandoned.
      if (![404, 409].includes(httpStatus(error) ?? 0)) throw error;
      saveSession(null);
      server.value = null;
    }
  }

  /** Reads the cart from the server; concurrent callers share one request. */
  function load(): Promise<void> {
    if (loading) return loading;
    status.value = server.value ? status.value : 'loading';
    loading = Promise.all([fetchCart(), directory.load()])
      .then(() => {
        status.value = 'ready';
      })
      .catch(() => {
        status.value = 'error';
      })
      .finally(() => {
        loading = undefined;
      });
    return loading;
  }

  /** The cart to change, opening a guest cart on the shopper's first change. */
  async function ensureCart(): Promise<Cart> {
    if (status.value !== 'ready') await load();
    if (server.value) return server.value;
    const created = await createCart({
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    });
    const { token, ...cart } = created;
    saveSession({ id: created.id, token });
    server.value = cart;
    return cart;
  }

  /** Runs one change against the server and adopts its answer. */
  async function change(action: (cart: Cart) => Promise<Cart | void>) {
    busy.value = true;
    lastFailure.value = null;
    try {
      const cart = await ensureCart();
      const next = await action(cart);
      if (next) server.value = next;
      else await fetchCart();
      return true;
    } catch (error) {
      lastFailure.value = failure(error);
      // The cart may have changed underneath us; show what the server has now.
      if (lastFailure.value !== 'error')
        await fetchCart().catch(() => undefined);
      return false;
    } finally {
      busy.value = false;
    }
  }

  const method = computed<'shipping' | 'pickup'>(() =>
    choosingPickupBranch.value || server.value?.fulfillment.method === 'PICKUP'
      ? 'pickup'
      : 'shipping',
  );
  const pickupBranchId = computed(() =>
    choosingPickupBranch.value
      ? ''
      : (server.value?.fulfillment.branchId ?? ''),
  );
  const fulfillmentBranch = computed(() =>
    method.value === 'shipping'
      ? directory.deliveryBranch
      : directory.byId(pickupBranchId.value),
  );
  const fulfillmentBranchId = computed(() => fulfillmentBranch.value?.id ?? '');

  const lines = computed<CartLine[]>(() => {
    const issues = new Map(
      (server.value?.issues ?? []).map((issue) => [issue.lineId, issue.code]),
    );
    return (server.value?.items ?? []).map((item) => ({
      variantId: item.editionId,
      quantity: item.quantity,
      available: item.available,
      issue: issues.get(item.id),
      problem: issues.has(item.id),
      total: item.lineTotal.amountMinor,
      variant: {
        id: item.editionId,
        language: item.editionLanguage,
        kind: item.editionKind,
        price: item.unitPrice.amountMinor,
      },
      game: { id: item.gameId, name: item.gameName },
    }));
  });
  const count = computed(() =>
    lines.value.reduce((sum, line) => sum + line.quantity, 0),
  );
  const subtotal = computed(() => server.value?.subtotal ?? 0);
  const shippingFee = computed(
    () => server.value?.shippingFee.amountMinor ?? 0,
  );
  const total = computed(() => server.value?.total.amountMinor ?? 0);
  const issues = computed(() => lines.value.filter((line) => line.problem));
  const canCheckout = computed(
    () => Boolean(server.value?.checkoutReady) && !choosingPickupBranch.value,
  );

  /**
   * Chooses how the whole cart is fulfilled. Pickup needs a branch; until one is
   * chosen nothing is sent, because the server only stores a complete choice.
   */
  async function setFulfillment(
    nextMethod: 'shipping' | 'pickup',
    branchId = '',
  ) {
    if (nextMethod === 'pickup' && !directory.byId(branchId)) {
      choosingPickupBranch.value = true;
      return false;
    }
    const done = await change((cart) =>
      updateCart(
        cart.id,
        {
          fulfillment:
            nextMethod === 'pickup'
              ? { method: 'PICKUP', pickupBranchId: branchId }
              : { method: 'SHIPPING' },
        },
        options(),
      ),
    );
    if (done) choosingPickupBranch.value = false;
    return done;
  }

  /**
   * Adds units of one edition. An empty cart first adopts where the shopper was
   * browsing (source), so the line is checked against the store they saw.
   */
  async function add(
    editionId: string,
    quantity = 1,
    source?: { method: 'shipping' | 'pickup'; branchId: string },
  ) {
    if (!Number.isSafeInteger(quantity) || quantity < 1 || quantity > 99) {
      lastFailure.value = 'invalid';
      return false;
    }
    return change(async (cart) => {
      let current = cart;
      if (!current.items.length && source) {
        current = await updateCart(
          current.id,
          {
            fulfillment:
              source.method === 'pickup'
                ? { method: 'PICKUP', pickupBranchId: source.branchId }
                : { method: 'SHIPPING' },
          },
          options(),
        );
        server.value = current;
        choosingPickupBranch.value = false;
      }
      return addCartItem(current.id, { editionId, quantity }, options());
    });
  }

  /** Sets a line's quantity; zero removes it. The server flags shortages. */
  async function update(editionId: string, quantity: number) {
    if (!Number.isSafeInteger(quantity) || quantity < 0 || quantity > 99) {
      lastFailure.value = 'invalid';
      return false;
    }
    return change((cart) =>
      quantity === 0
        ? removeCartItem(cart.id, editionId, options())
        : updateCartItem(cart.id, editionId, { quantity }, options()),
    );
  }

  /**
   * Pays by card and places the order. The card goes to the payment gateway for a
   * single-use token and is never kept. The idempotency key is reused until the
   * server gives a definite answer, so a retry after a lost response cannot place
   * a second order.
   */
  let checkoutKey: string | undefined;
  async function checkout(
    card: CreatePaymentTokenRequest,
    details: Omit<CheckoutCartRequest, 'paymentToken'>,
  ): Promise<Order> {
    if (!server.value) throw new Error('There is no cart to check out');
    busy.value = true;
    try {
      const cartId = server.value.id;
      const { token } = await createPaymentToken(card);
      checkoutKey ??= crypto.randomUUID();
      let order: Order;
      try {
        order = await checkoutCart(
          cartId,
          { ...details, paymentToken: token },
          {
            headers: {
              ...options()?.headers,
              'Idempotency-Key': checkoutKey,
            },
          },
        );
      } catch (error) {
        // Any HTTP answer is definite; only a lost response keeps the key.
        if (httpStatus(error) !== undefined) checkoutKey = undefined;
        if (httpStatus(error) === 409) await fetchCart().catch(() => undefined);
        throw error;
      }
      checkoutKey = undefined;
      // The cart became the order. A guest's token is spent; an account gets a
      // fresh cart on the next read.
      saveSession(null);
      server.value = null;
      void queryClient.invalidateQueries({
        queryKey: ['storefront', 'offering'],
      });
      void queryClient.invalidateQueries({
        queryKey: ['storefront', 'orders'],
      });
      if (auth.authenticated) void load();
      return order;
    } finally {
      busy.value = false;
    }
  }

  // Signing in merges the guest cart; signing out leaves the account cart behind.
  watch(
    () => auth.authenticated,
    () => {
      server.value = null;
      status.value = 'idle';
      void load();
    },
  );

  return {
    status,
    busy,
    lastFailure,
    lines,
    count,
    subtotal,
    shippingFee,
    total,
    method,
    pickupBranchId,
    fulfillmentBranch,
    fulfillmentBranchId,
    issues,
    canCheckout,
    load,
    setFulfillment,
    add,
    update,
    checkout,
  };
});

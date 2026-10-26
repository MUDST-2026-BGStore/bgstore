import { computed, ref, watch } from 'vue';
import { defineStore } from 'pinia';
import { games } from '../data/catalog';
import { variants } from '../data/retail';
import { branches, DELIVERY_BRANCH_ID, retailStockAt } from '../data/branches';
// TODO(cart-api): All lines, prices and stock in this store are fixture-backed.
// Wire create/get/add/update/remove cart and sign-in merge after CartsController
// is implemented; generated client methods currently target throwing stubs.
export const CART_STORAGE_KEY = 'theparlor.cart.preview.v1';
export const FULFILLMENT_STORAGE_KEY = 'theparlor.cart.fulfillment.v1';
interface CartItem {
  variantId: string;
  quantity: number;
}
function restore(): CartItem[] {
  try {
    const saved: unknown = JSON.parse(
      localStorage.getItem(CART_STORAGE_KEY) || '[]',
    );
    if (!Array.isArray(saved)) return [];
    const restored = new Map<string, number>();
    for (const item of saved) {
      if (
        !item ||
        typeof item !== 'object' ||
        typeof item.variantId !== 'string' ||
        !Number.isSafeInteger(item.quantity) ||
        item.quantity < 1 ||
        !variants.some((v) => v.id === item.variantId)
      )
        continue;
      // Retain recognized lines even if this location cannot fulfill them. A stock
      // conflict must be shown, not silently removed/clamped on restore or change.
      restored.set(
        item.variantId,
        Math.min(99, item.quantity + (restored.get(item.variantId) || 0)),
      );
    }
    return [...restored].map(([variantId, quantity]) => ({
      variantId,
      quantity,
    }));
  } catch {
    return [];
  }
}
function restoreFulfillment(): {
  method: 'shipping' | 'pickup';
  branchId: string;
} {
  try {
    const value = JSON.parse(
      localStorage.getItem(FULFILLMENT_STORAGE_KEY) || 'null',
    );
    if (value?.method === 'pickup')
      return {
        method: 'pickup',
        branchId: branches.some(
          (branch) => branch.id === value.branchId && branch.pickup,
        )
          ? value.branchId
          : '',
      };
  } catch {
    /* Ignore corrupt browser preferences. */
  }
  return { method: 'shipping', branchId: '' };
}
export const useCartStore = defineStore('shopping-cart', () => {
  const items = ref<CartItem[]>(restore());
  const savedFulfillment = restoreFulfillment();
  const method = ref<'shipping' | 'pickup'>(savedFulfillment.method);
  const pickupBranchId = ref(savedFulfillment.branchId);
  const fulfillmentBranchId = computed(() =>
    method.value === 'shipping' ? DELIVERY_BRANCH_ID : pickupBranchId.value,
  );
  const fulfillmentBranch = computed(() =>
    branches.find((branch) => branch.id === fulfillmentBranchId.value),
  );
  const isOpen = ref(false);
  const stage = ref<'cart' | 'details' | 'review'>('cart');
  function stockFor(variantId: string) {
    return retailStockAt(variantId, fulfillmentBranchId.value);
  }
  const lines = computed(() =>
    items.value.flatMap((item) => {
      const variant = variants.find((v) => v.id === item.variantId);
      const game = games.find((g) => g.id === variant?.gameId);
      const available = stockFor(item.variantId);
      return variant && game
        ? [
            {
              ...item,
              variant,
              game,
              available,
              problem:
                Boolean(fulfillmentBranch.value) && item.quantity > available,
              total: variant.price * item.quantity,
            },
          ]
        : [];
    }),
  );
  const count = computed(() =>
    lines.value.reduce((sum, item) => sum + item.quantity, 0),
  );
  const subtotal = computed(() =>
    lines.value.reduce((sum, item) => sum + item.total, 0),
  );
  const issues = computed(() => lines.value.filter((line) => line.problem));
  // TODO(checkout-api): This flag permits prototype navigation only, not placing
  // an order. Require a server quote and stock validation before real checkout.
  const canCheckout = computed(
    () =>
      count.value > 0 &&
      Boolean(fulfillmentBranch.value) &&
      !issues.value.length,
  );
  // TODO(cart-api): Use a server/guest cart and sign-in merge. Reprice/revalidate
  // every variant against ONE fulfillment branch; browser totals and stock never
  // reserve inventory. Do not aggregate stock, transfer or split the first release.
  watch(
    items,
    (value) => {
      try {
        localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(value));
      } catch {
        /* Session-only fallback. */
      }
    },
    { deep: true, flush: 'sync' },
  );
  watch(
    [method, pickupBranchId],
    () => {
      try {
        localStorage.setItem(
          FULFILLMENT_STORAGE_KEY,
          JSON.stringify({
            method: method.value,
            branchId: pickupBranchId.value,
          }),
        );
      } catch {
        /* Session-only fallback. */
      }
      if (stage.value === 'review') stage.value = 'details';
    },
    { flush: 'sync' },
  );
  function setFulfillment(
    nextMethod: 'shipping' | 'pickup',
    branchId = pickupBranchId.value,
  ) {
    method.value = nextMethod;
    pickupBranchId.value = branches.some(
      (branch) => branch.id === branchId && branch.pickup,
    )
      ? branchId
      : '';
  }
  function add(
    variantId: string,
    quantity = 1,
    source?: { method: 'shipping' | 'pickup'; branchId: string },
  ) {
    const variant = variants.find((v) => v.id === variantId);
    const current = items.value.find((item) => item.variantId === variantId);
    const sourceId = source
      ? source.method === 'shipping'
        ? DELIVERY_BRANCH_ID
        : source.branchId
      : fulfillmentBranchId.value;
    if (
      !variant ||
      !Number.isSafeInteger(quantity) ||
      quantity < 1 ||
      quantity > 99 ||
      (current?.quantity || 0) + quantity > 99 ||
      (current?.quantity || 0) + quantity > retailStockAt(variantId, sourceId)
    )
      return false;
    // The first item may initialize an empty order from the viewed store.
    // Existing orders keep their own fulfillment choice. Never synchronize it
    // back to browsing; shortages are resolved inside the cart only.
    if (!items.value.length && source) {
      if (source.method === 'pickup') setFulfillment('pickup', source.branchId);
      else setFulfillment('shipping');
    }
    if (current) current.quantity += quantity;
    else items.value.push({ variantId, quantity });
    return true;
  }
  function update(variantId: string, quantity: number) {
    const item = items.value.find((item) => item.variantId === variantId);
    if (
      !item ||
      !Number.isSafeInteger(quantity) ||
      quantity < 0 ||
      quantity > 99 ||
      (quantity > stockFor(variantId) && quantity >= item.quantity)
    )
      return false;
    if (!quantity)
      items.value = items.value.filter((line) => line.variantId !== variantId);
    else item.quantity = quantity;
    return true;
  }
  function open() {
    stage.value = 'cart';
    isOpen.value = true;
  }
  return {
    items,
    lines,
    count,
    subtotal,
    isOpen,
    stage,
    method,
    pickupBranchId,
    fulfillmentBranch,
    fulfillmentBranchId,
    issues,
    canCheckout,
    stockFor,
    setFulfillment,
    add,
    update,
    open,
  };
});

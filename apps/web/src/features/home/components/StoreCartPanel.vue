<script setup lang="ts">
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue';
import QRCode from 'qrcode';
import { useCartStore } from '../stores/cart';
import { useOrdersStore } from '../stores/orders';
import { formatPrice } from '../data/catalog';
import { useStoreText } from '../data/store-text';
import { useAuthStore } from '../../../shared/stores/auth';
import CartFulfillment from './CartFulfillment.vue';

const cart = useCartStore();
const ordersStore = useOrdersStore();
const auth = useAuthStore();
const { t, locale } = useStoreText();

const dialog = ref<HTMLDialogElement | null>(null);
const error = ref('');

const contact = reactive({
  email: '',
  name: '',
  phone: '',
  address: '',
  extra: '',
  city: '',
  region: '',
  postal: '',
  country: 'Thailand',
  note: '',
});

// Auto pre-fill contact details from logged-in user if available
watch(
  () => auth.authenticated,
  (isAuth) => {
    if (isAuth) {
      if (auth.displayName && !contact.name) contact.name = auth.displayName;
      if (auth.username && !contact.name) contact.name = auth.username;
      if (auth.email && !contact.email) contact.email = auth.email;
    }
  },
  { immediate: true },
);

// Payment state
const qrDataUrl = ref<string>('');
const qrReference = ref<string>('');
const isGeneratingQr = ref(false);
const isProcessingPayment = ref(false);
const secondsLeft = ref(15 * 60);
let timer: ReturnType<typeof setInterval> | null = null;
const copiedTracking = ref(false);

const formattedTimer = computed(() => {
  const m = Math.floor(secondsLeft.value / 60);
  const s = secondsLeft.value % 60;
  return `${m}:${s < 10 ? '0' : ''}${s}`;
});

const title = computed(() => {
  switch (cart.stage) {
    case 'cart':
      return t('Your cart');
    case 'details':
      return t('Contact & delivery');
    case 'review':
      return t('Order review');
    case 'payment':
      return t('PromptPay QR payment');
    case 'confirmation':
      return t('Order confirmed');
    default:
      return t('Your cart');
  }
});

watch(
  () => cart.isOpen,
  async (value) => {
    await nextTick();
    if (value && !dialog.value?.open) {
      error.value = '';
      dialog.value?.showModal();
    } else if (!value && dialog.value?.open) dialog.value.close();
  },
);

watch(
  () => cart.items,
  () => {
    if (!cart.count && cart.stage !== 'confirmation') {
      cart.stage = 'cart';
    } else if (cart.stage === 'review') {
      cart.stage = 'cart';
      error.value = t('The cart was updated. Please review your items again.');
    }
  },
  { deep: true },
);

onUnmounted(() => {
  cart.isOpen = false;
  if (timer) clearInterval(timer);
});

// Shopee-style Item Selection in Cart
const selectedVariantIds = ref<string[]>([]);
const initializedSelection = ref(false);

watch(
  () => cart.lines.map((l) => l.variantId),
  (currentIds) => {
    if (!initializedSelection.value) {
      selectedVariantIds.value = [...currentIds];
      initializedSelection.value = true;
    } else {
      const existing = new Set(selectedVariantIds.value);
      const prevIds = new Set(selectedVariantIds.value);
      for (const id of currentIds) {
        if (!prevIds.has(id)) {
          existing.add(id);
        }
      }
      selectedVariantIds.value = currentIds.filter((id) => existing.has(id));
    }
  },
  { immediate: true },
);

const isAllSelected = computed(
  () =>
    cart.lines.length > 0 &&
    cart.lines.every((l) => selectedVariantIds.value.includes(l.variantId)),
);

const isPartiallySelected = computed(
  () => selectedVariantIds.value.length > 0 && !isAllSelected.value,
);

function toggleSelectAll(event: Event) {
  const target = event.target as HTMLInputElement;
  if (target.checked) {
    selectedVariantIds.value = cart.lines.map((l) => l.variantId);
  } else {
    selectedVariantIds.value = [];
  }
}

function toggleSelectItem(variantId: string) {
  const idx = selectedVariantIds.value.indexOf(variantId);
  if (idx >= 0) {
    selectedVariantIds.value.splice(idx, 1);
  } else {
    selectedVariantIds.value.push(variantId);
  }
}

function removeSelectedItems() {
  for (const id of [...selectedVariantIds.value]) {
    cart.update(id, 0);
  }
  selectedVariantIds.value = [];
}

const selectedLines = computed(() =>
  cart.lines.filter((l) => selectedVariantIds.value.includes(l.variantId)),
);

const selectedCount = computed(() =>
  selectedLines.value.reduce((sum, l) => sum + l.quantity, 0),
);

const selectedSubtotal = computed(() =>
  selectedLines.value.reduce((sum, l) => sum + l.total, 0),
);

const selectedShippingFee = computed(() => {
  if (cart.method !== 'shipping') return 0;
  return selectedLines.value.length > 0 ? cart.shippingFee : 0;
});

const selectedTotalPayable = computed(
  () => selectedSubtotal.value + selectedShippingFee.value,
);

function proceedToDetails() {
  if (selectedCount.value === 0) return;
  cart.stage = 'details';
}

const showQuickPicker = ref(false);
const quickCatalogGames = [
  {
    variantId: 'azul-th',
    id: 'azul',
    name: 'Azul',
    edition: 'Thai edition',
    price: 1490,
  },
  {
    variantId: 'catan-th',
    id: 'catan',
    name: 'Catan',
    edition: 'Thai edition',
    price: 1890,
  },
  {
    variantId: 'codenames-th',
    id: 'codenames',
    name: 'Codenames',
    edition: 'Thai edition',
    price: 990,
  },
  {
    variantId: 'love-letter-en',
    id: 'love-letter',
    name: 'Love Letter',
    edition: 'English edition',
    price: 690,
  },
  {
    variantId: 'wingspan-en',
    id: 'wingspan',
    name: 'Wingspan',
    edition: 'English edition',
    price: 2490,
  },
  {
    variantId: 'cascadia-en',
    id: 'cascadia',
    name: 'Cascadia',
    edition: 'English edition',
    price: 1790,
  },
];

function quickAddGame(variantId: string) {
  cart.add(variantId, 1);
}

function updateQuantity(id: string, quantity: number, event?: Event) {
  error.value = '';
  if (
    !Number.isSafeInteger(quantity) ||
    quantity < 1 ||
    !cart.update(id, quantity)
  ) {
    const line = cart.lines.find((item) => item.variantId === id);
    error.value = t('Enter a quantity between 1 and {count}.', {
      count: line?.available || 0,
    });
    if (event)
      (event.target as HTMLInputElement).value = String(line?.quantity || 1);
  }
}

async function remove(id: string) {
  cart.update(id, 0);
  await nextTick();
  dialog.value
    ?.querySelector<HTMLElement>('.remove-line, .continue-shopping')
    ?.focus();
}

function stockIssue(available: number) {
  if (cart.method === 'shipping')
    return available
      ? t('Only {count} available for delivery.', { count: available })
      : t('Not available for delivery');
  const branch = t(cart.fulfillmentBranch?.name || '');
  return available
    ? t('Only {count} available at {branch}.', { count: available, branch })
    : t('Not available at {branch}', { branch });
}

function review() {
  if (cart.canCheckout) cart.stage = 'review';
}

async function proceedToPayment() {
  if (!cart.canCheckout) return;
  isGeneratingQr.value = true;
  error.value = '';

  const randRef = `PP-ORD-${Math.floor(100000 + Math.random() * 900000)}`;
  qrReference.value = randRef;
  secondsLeft.value = 15 * 60;

  const qrPayload = JSON.stringify({
    service: 'PromptPay-QR',
    merchant: 'The Parlor Board Game Store',
    ref: randRef,
    amount: cart.totalPayable.toFixed(2),
    currency: 'THB',
    created: new Date().toISOString(),
  });

  try {
    qrDataUrl.value = await QRCode.toDataURL(qrPayload, {
      width: 240,
      margin: 1,
      color: { dark: '#142a1f', light: '#ffffff' },
      errorCorrectionLevel: 'M',
    });
  } catch (err) {
    console.error('Failed to generate PromptPay QR:', err);
  } finally {
    isGeneratingQr.value = false;
  }

  cart.stage = 'payment';

  if (timer) clearInterval(timer);
  timer = setInterval(() => {
    if (secondsLeft.value > 0) {
      secondsLeft.value--;
    } else {
      if (timer) clearInterval(timer);
    }
  }, 1000);
}

function confirmPayment() {
  isProcessingPayment.value = true;
  if (timer) clearInterval(timer);

  setTimeout(() => {
    isProcessingPayment.value = false;

    // Convert selected lines into OrderItems
    const orderItems = selectedLines.value.map((line) => ({
      variantId: line.variantId,
      gameId: line.game.id,
      gameName: line.game.name,
      edition:
        line.variant.language === 'th' ? 'Thai edition' : 'English edition',
      quantity: line.quantity,
      unitPrice: line.variant.price,
      totalPrice: line.total,
      imageUrl: `/images/games/${line.game.id}.jpg`,
    }));

    const newOrder = ordersStore.createOrder({
      items: orderItems,
      itemSubtotal: selectedSubtotal.value,
      fulfillmentMethod: cart.method,
      pickupBranchName: cart.fulfillmentBranch?.name,
      carrier: cart.carrier,
      shippingFee: selectedShippingFee.value,
      shippingAddress: { ...contact },
      paymentReference: qrReference.value,
    });

    cart.placedOrder = newOrder;
    // Remove only the purchased items from cart, keeping unselected items for next time!
    for (const item of selectedLines.value) {
      cart.update(item.variantId, 0);
    }
    cart.stage = 'confirmation';
  }, 800);
}

function copyTracking(trackingNo: string) {
  void navigator.clipboard.writeText(trackingNo);
  copiedTracking.value = true;
  setTimeout(() => {
    copiedTracking.value = false;
  }, 2000);
}

function viewAllOrders() {
  dialog.value?.close();
  ordersStore.openOrdersModal(cart.placedOrder ?? undefined);
}
</script>

<template>
  <dialog
    ref="dialog"
    class="cart-panel"
    aria-labelledby="cart-title"
    @click="$event.target === dialog && dialog?.close()"
    @close="cart.isOpen = false"
  >
    <div class="cart-content">
      <header class="cart-header-main">
        <div class="cart-header-title">
          <h2 id="cart-title">
            {{ locale === 'th-TH' ? 'ตะกร้าสินค้า' : title }}
            <span v-if="cart.stage === 'cart'" class="header-count-num">
              ({{ cart.count }})</span
            >
          </h2>
        </div>
        <button
          class="close-cart"
          type="button"
          :aria-label="t('Close cart')"
          autofocus
          @click="dialog?.close()"
        >
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            aria-hidden="true"
          >
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </header>

      <!-- 4-Step Progress Indicator -->
      <nav class="cart-step-bar" aria-label="ขั้นตอนการสั่งซื้อ">
        <div
          class="step-node"
          :class="{
            active: cart.stage === 'cart',
            done: ['details', 'review', 'payment', 'confirmation'].includes(
              cart.stage,
            ),
          }"
        >
          <span class="step-num">1</span>
          <span class="step-txt">{{
            locale === 'th-TH' ? 'ตะกร้าสินค้า' : 'Cart'
          }}</span>
        </div>
        <div
          class="step-connector"
          :class="{
            filled: ['details', 'review', 'payment', 'confirmation'].includes(
              cart.stage,
            ),
          }"
        ></div>
        <div
          class="step-node"
          :class="{
            active: cart.stage === 'details' || cart.stage === 'review',
            done: ['payment', 'confirmation'].includes(cart.stage),
          }"
        >
          <span class="step-num">2</span>
          <span class="step-txt">{{
            locale === 'th-TH' ? 'ข้อมูลจัดส่ง' : 'Delivery'
          }}</span>
        </div>
        <div
          class="step-connector"
          :class="{ filled: ['payment', 'confirmation'].includes(cart.stage) }"
        ></div>
        <div
          class="step-node"
          :class="{
            active: cart.stage === 'payment',
            done: cart.stage === 'confirmation',
          }"
        >
          <span class="step-num">3</span>
          <span class="step-txt">{{
            locale === 'th-TH' ? 'ชำระเงิน' : 'Payment'
          }}</span>
        </div>
        <div
          class="step-connector"
          :class="{ filled: cart.stage === 'confirmation' }"
        ></div>
        <div
          class="step-node"
          :class="{
            active: cart.stage === 'confirmation',
            done: cart.stage === 'confirmation',
          }"
        >
          <span class="step-num">4</span>
          <span class="step-txt">{{
            locale === 'th-TH' ? 'สำเร็จ' : 'Done'
          }}</span>
        </div>
      </nav>

      <!-- Cart fulfillment selector only visible on cart stage -->
      <CartFulfillment
        v-if="cart.stage === 'cart' || cart.stage === 'details'"
      />

      <p
        v-if="cart.count && !cart.fulfillmentBranch"
        class="cart-error"
        role="status"
      >
        {{ t('Choose a pickup branch to continue.') }}
      </p>

      <div v-if="cart.issues.length" class="location-conflict" role="alert">
        <strong>{{ t('Some items need changes for this location.') }}</strong>
        <p>{{ t('Change branch, choose delivery, or edit your cart.') }}</p>
        <button
          v-if="cart.stage !== 'cart'"
          type="button"
          class="back-link"
          @click="cart.stage = 'cart'"
        >
          {{ t('Back to cart') }}
        </button>
      </div>

      <p v-if="error" role="alert" class="cart-error">{{ error }}</p>

      <!-- STAGE 1: CART ITEMS LIST -->
      <template v-if="cart.stage === 'cart'">
        <div v-if="!cart.count" class="empty-cart">
          <h3>{{ t('Your cart is empty') }}</h3>
          <p class="empty-sub">
            {{ t('Choose a popular board game below to order:') }}
          </p>

          <!-- Quick Game Selection Shelf -->
          <div class="cart-quick-shelf">
            <div
              v-for="g in quickCatalogGames"
              :key="g.variantId"
              class="quick-game-card"
            >
              <img
                :src="`/images/games/${g.id}.jpg`"
                :alt="g.name"
                class="quick-thumb"
              />
              <div class="quick-details">
                <strong>{{ g.name }}</strong>
                <span class="quick-meta"
                  >{{ g.edition }} · {{ formatPrice(g.price) }}</span
                >
              </div>
              <button
                type="button"
                class="btn-quick-add"
                @click="quickAddGame(g.variantId)"
              >
                + {{ t('Add') }}
              </button>
            </div>
          </div>

          <button
            class="primary continue-shopping"
            type="button"
            @click="dialog?.close()"
          >
            {{ t('Continue shopping') }}
          </button>
          <button
            v-if="ordersStore.orders.length > 0"
            type="button"
            class="secondary view-orders-cta"
            @click="viewAllOrders"
          >
            <svg
              width="15"
              height="15"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              aria-hidden="true"
            >
              <path
                d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"
              />
              <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
              <line x1="12" y1="22.08" x2="12" y2="12" />
            </svg>
            <span>{{ t('View past orders & tracking') }}</span>
          </button>
        </div>

        <template v-else>
          <!-- Shopee-style Select All Bar -->
          <div class="cart-select-bar">
            <label class="shopee-chk-wrap">
              <input
                type="checkbox"
                class="shopee-chk-input"
                :checked="isAllSelected"
                @change="toggleSelectAll"
              />
              <span class="shopee-chk-box">
                <svg
                  v-if="isAllSelected"
                  width="12"
                  height="12"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="3"
                >
                  <polyline points="20 6 9 17 4 12" />
                </svg>
                <span v-else-if="isPartiallySelected" class="indeterminate-bar"
                  >−</span
                >
              </span>
              <span class="chk-label-text">
                {{
                  locale === 'th-TH'
                    ? `เลือกทั้งหมด (${cart.lines.length} รายการ)`
                    : t('Select all')
                }}
              </span>
            </label>
            <button
              v-if="selectedVariantIds.length > 0"
              type="button"
              class="btn-clear-selected"
              @click="removeSelectedItems"
            >
              {{
                locale === 'th-TH'
                  ? `ลบที่เลือก (${selectedVariantIds.length})`
                  : t('Delete selected')
              }}
            </button>
          </div>

          <ul class="cart-lines">
            <li
              v-for="line in cart.lines"
              :key="line.variantId"
              :data-variant="line.variantId"
              class="cart-line-item"
              :class="{
                'is-selected': selectedVariantIds.includes(line.variantId),
              }"
            >
              <!-- Shopee-style Row Checkbox -->
              <label class="item-chk-wrap">
                <input
                  type="checkbox"
                  class="shopee-chk-input"
                  :checked="selectedVariantIds.includes(line.variantId)"
                  @change="toggleSelectItem(line.variantId)"
                />
                <span class="shopee-chk-box">
                  <svg
                    v-if="selectedVariantIds.includes(line.variantId)"
                    width="12"
                    height="12"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="3"
                  >
                    <polyline points="20 6 9 17 4 12" />
                  </svg>
                </span>
              </label>

              <img
                :src="`/images/games/${line.game.id}.jpg`"
                :alt="line.game.name"
                width="76"
                height="90"
                class="cart-item-img"
              />
              <div class="line-info">
                <div class="line-heading">
                  <h3>{{ line.game.name }}</h3>
                  <strong class="line-total">{{
                    formatPrice(line.total)
                  }}</strong>
                </div>
                <p class="edition-label">
                  {{
                    t(
                      line.variant.language === 'th'
                        ? 'Thai edition'
                        : 'English edition',
                    )
                  }}
                </p>
                <p v-if="line.problem" class="line-conflict">
                  {{ stockIssue(line.available) }}
                </p>
                <div class="line-actions">
                  <div class="quantity-control">
                    <button
                      type="button"
                      :disabled="line.quantity <= 1"
                      :aria-label="
                        t('Decrease quantity for {name}', {
                          name: line.game.name,
                        })
                      "
                      @click="updateQuantity(line.variantId, line.quantity - 1)"
                    >
                      −
                    </button>
                    <input
                      type="number"
                      inputmode="numeric"
                      min="1"
                      :max="Math.max(line.available, line.quantity)"
                      step="1"
                      :value="line.quantity"
                      :aria-label="
                        t('Quantity for {name}', { name: line.game.name })
                      "
                      @change="
                        updateQuantity(
                          line.variantId,
                          ($event.target as HTMLInputElement).value.trim()
                            ? Number(($event.target as HTMLInputElement).value)
                            : NaN,
                          $event,
                        )
                      "
                    />
                    <button
                      type="button"
                      :disabled="line.quantity >= line.available"
                      :aria-label="
                        t('Increase quantity for {name}', {
                          name: line.game.name,
                        })
                      "
                      @click="updateQuantity(line.variantId, line.quantity + 1)"
                    >
                      +
                    </button>
                  </div>
                  <button
                    type="button"
                    class="remove-line"
                    :aria-label="t('Remove {name}', { name: line.game.name })"
                    @click="remove(line.variantId)"
                  >
                    {{ t('Remove') }}
                  </button>
                </div>
              </div>
            </li>
          </ul>

          <div class="add-more-games-section">
            <button
              type="button"
              class="toggle-more-games-btn"
              @click="showQuickPicker = !showQuickPicker"
            >
              <span>{{
                showQuickPicker
                  ? '▲ ' + t('Hide extra games')
                  : '+ ' + t('Add more board games to cart')
              }}</span>
            </button>

            <div v-if="showQuickPicker" class="cart-quick-shelf in-cart">
              <div
                v-for="g in quickCatalogGames"
                :key="g.variantId"
                class="quick-game-card"
              >
                <img
                  :src="`/images/games/${g.id}.jpg`"
                  :alt="g.name"
                  class="quick-thumb"
                />
                <div class="quick-details">
                  <strong>{{ g.name }}</strong>
                  <span class="quick-meta"
                    >{{ g.edition }} · {{ formatPrice(g.price) }}</span
                  >
                </div>
                <button
                  type="button"
                  class="btn-quick-add"
                  @click="quickAddGame(g.variantId)"
                >
                  + {{ t('Add') }}
                </button>
              </div>
            </div>
          </div>

          <div class="cart-summary">
            <div>
              <span>{{
                locale === 'th-TH'
                  ? `ยอดรวมค่าสินค้า (${selectedCount} ชิ้นที่เลือก)`
                  : t('Item subtotal')
              }}</span>
              <strong>{{ formatPrice(selectedSubtotal) }}</strong>
            </div>
            <div v-if="cart.method === 'shipping'">
              <span>{{
                locale === 'th-TH'
                  ? `ค่าจัดส่งพัสดุ (${cart.carrierInfo.name})`
                  : t('Shipping')
              }}</span>
              <strong>{{ formatPrice(selectedShippingFee) }}</strong>
            </div>
            <div class="total-row">
              <span
                ><strong>{{
                  locale === 'th-TH' ? 'ยอดชำระสุทธิ' : t('Estimated total')
                }}</strong></span
              >
              <strong class="grand-total">{{
                formatPrice(selectedTotalPayable)
              }}</strong>
            </div>
            <p class="summary-note">
              {{
                locale === 'th-TH'
                  ? 'ชำระเงินสะดวกและปลอดภัยผ่าน พร้อมเพย์ QR Code เมื่อยืนยันคำสั่งซื้อ'
                  : t('Instant payment via PromptPay QR code upon checkout.')
              }}
            </p>
            <button
              type="button"
              class="primary btn-checkout-main"
              :disabled="!cart.canCheckout || selectedCount === 0"
              @click="proceedToDetails"
            >
              {{
                locale === 'th-TH'
                  ? `ดำเนินการสั่งซื้อ (${selectedCount} ชิ้น) →`
                  : t('Proceed to Delivery Details')
              }}
            </button>
            <button
              type="button"
              class="secondary continue-shopping"
              @click="dialog?.close()"
            >
              {{
                locale === 'th-TH'
                  ? 'เลือกดูบอร์ดเกมเพิ่มเติม'
                  : t('Continue shopping')
              }}
            </button>
            <button
              v-if="ordersStore.orders.length > 0"
              type="button"
              class="footer-orders-link"
              @click="viewAllOrders"
            >
              <svg
                width="14"
                height="14"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                aria-hidden="true"
              >
                <path
                  d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"
                />
                <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
                <line x1="12" y1="22.08" x2="12" y2="12" />
              </svg>
              <span>{{
                locale === 'th-TH'
                  ? 'ดูประวัติคำสั่งซื้อและการจัดส่ง'
                  : t('View past orders & tracking')
              }}</span>
            </button>
          </div>
        </template>
      </template>

      <!-- STAGE 2: CONTACT & SHIPPING ADDRESS -->
      <form
        v-else-if="cart.stage === 'details'"
        class="checkout-form"
        @submit.prevent="review"
      >
        <button class="back-link" type="button" @click="cart.stage = 'cart'">
          ← {{ t('Back to cart') }}
        </button>

        <h3>{{ t('Recipient Contact') }}</h3>
        <label>
          {{ t('Full name') }}
          <input
            v-model.trim="contact.name"
            name="name"
            autocomplete="name"
            required
            maxlength="150"
            placeholder="e.g. Somchai Prasert"
          />
        </label>
        <label>
          {{ t('Phone number') }}
          <input
            v-model.trim="contact.phone"
            type="tel"
            name="phone"
            autocomplete="tel"
            required
            maxlength="30"
            placeholder="e.g. 081-234-5678"
            pattern="[+0-9 .\-]{6,30}"
          />
        </label>
        <label>
          {{ t('Email address') }}
          <input
            v-model.trim="contact.email"
            type="email"
            name="email"
            autocomplete="email"
            required
            maxlength="254"
            placeholder="e.g. somchai@example.com"
          />
        </label>

        <template v-if="cart.method === 'shipping'">
          <h3>{{ t('Shipping Address') }}</h3>
          <label>
            {{ t('Street address') }}
            <input
              v-model.trim="contact.address"
              name="address"
              autocomplete="address-line1"
              required
              maxlength="200"
              placeholder="e.g. 128/4 Sukhumvit Soi 21"
            />
          </label>
          <label>
            {{ t('Building / Apartment / Suite (optional)') }}
            <input
              v-model.trim="contact.extra"
              name="address2"
              autocomplete="address-line2"
              maxlength="200"
              placeholder="e.g. Tower B, Room 402"
            />
          </label>
          <div class="form-pair">
            <label>
              {{ t('District / Sub-district') }}
              <input
                v-model.trim="contact.city"
                name="city"
                autocomplete="address-level2"
                required
                maxlength="100"
                placeholder="e.g. Watthana"
              />
            </label>
            <label>
              {{ t('Province') }}
              <input
                v-model.trim="contact.region"
                name="region"
                autocomplete="address-level1"
                required
                maxlength="100"
                placeholder="e.g. Bangkok"
              />
            </label>
          </div>
          <div class="form-pair">
            <label>
              {{ t('Postal code') }}
              <input
                v-model.trim="contact.postal"
                name="postal"
                autocomplete="postal-code"
                required
                maxlength="20"
                placeholder="e.g. 10110"
              />
            </label>
            <label>
              {{ t('Country') }}
              <input
                v-model.trim="contact.country"
                name="country"
                autocomplete="country-name"
                required
                maxlength="80"
                readonly
              />
            </label>
          </div>
        </template>

        <label>
          {{ t('Delivery instructions / Notes (optional)') }}
          <textarea
            v-model.trim="contact.note"
            name="note"
            rows="2"
            maxlength="500"
            placeholder="e.g. Leave package with condo security guard"
          />
        </label>

        <div class="cart-summary">
          <div>
            <span>{{ t('Item subtotal') }}</span>
            <strong>{{ formatPrice(cart.subtotal) }}</strong>
          </div>
          <div v-if="cart.method === 'shipping'">
            <span>{{ t('Shipping') }} ({{ cart.carrierInfo.name }})</span>
            <strong>{{ formatPrice(cart.shippingFee) }}</strong>
          </div>
          <div class="total-row">
            <span
              ><strong>{{ t('Total payable') }}</strong></span
            >
            <strong class="grand-total">{{
              formatPrice(cart.totalPayable)
            }}</strong>
          </div>
          <button class="primary" type="submit" :disabled="!cart.canCheckout">
            {{ t('Review Order') }} →
          </button>
        </div>
      </form>

      <!-- STAGE 3: ORDER REVIEW -->
      <div v-else-if="cart.stage === 'review'" class="order-review">
        <button class="back-link" type="button" @click="cart.stage = 'details'">
          ← {{ t('Edit details') }}
        </button>

        <h3>{{ t('Recipient & Delivery') }}</h3>
        <div class="review-box">
          <p>
            <strong>{{ contact.name }}</strong> ({{ contact.phone }})
          </p>
          <p class="review-sub">{{ contact.email }}</p>
          <div class="review-divider"></div>
          <p v-if="cart.method === 'shipping'">
            <strong>{{ t('Ship via') }}:</strong> {{ cart.carrierInfo.name }}
            <span class="delivery-time"
              >({{
                locale === 'th-TH'
                  ? cart.carrierInfo.deliveryDaysTh
                  : cart.carrierInfo.deliveryDays
              }})</span
            ><br />
            {{ contact.address }} {{ contact.extra }}<br />
            {{ contact.city }}, {{ contact.region }} {{ contact.postal }},
            {{ contact.country }}
          </p>
          <p v-else-if="cart.fulfillmentBranch">
            <strong>{{ t('Collect at') }}:</strong>
            {{ t(cart.fulfillmentBranch.name) }}
          </p>
          <p v-if="contact.note" class="review-note">
            <em>"{{ contact.note }}"</em>
          </p>
        </div>

        <h3>{{ t('Ordered Games ({count})', { count: cart.count }) }}</h3>
        <ul class="review-items">
          <li v-for="line in cart.lines" :key="line.variantId">
            <div class="review-item-main">
              <strong>{{ line.game.name }}</strong> × {{ line.quantity }}
              <small>{{
                line.variant.language === 'th'
                  ? t('Thai edition')
                  : t('English edition')
              }}</small>
            </div>
            <strong>{{ formatPrice(line.total) }}</strong>
          </li>
        </ul>

        <div class="cart-summary">
          <div>
            <span>{{ t('Item subtotal') }}</span>
            <strong>{{ formatPrice(cart.subtotal) }}</strong>
          </div>
          <div>
            <span>{{ t('Shipping fee') }} ({{ cart.carrierInfo.name }})</span>
            <strong v-if="cart.isFreeShipping" class="free-shipping-text">
              {{ t('FREE (Order > ฿1,500)') }}
            </strong>
            <strong v-else>{{ formatPrice(cart.shippingFee) }}</strong>
          </div>
          <div class="total-row">
            <span
              ><strong>{{ t('Grand total to pay') }}</strong></span
            >
            <strong class="grand-total highlight">{{
              formatPrice(cart.totalPayable)
            }}</strong>
          </div>
          <button
            class="primary place-order-btn"
            type="button"
            :disabled="!cart.canCheckout || isGeneratingQr"
            @click="proceedToPayment"
          >
            <svg
              width="15"
              height="15"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              aria-hidden="true"
            >
              <rect x="5" y="2" width="14" height="20" rx="2" ry="2" />
              <line x1="12" y1="18" x2="12.01" y2="18" />
            </svg>
            <span>{{ t('Pay with PromptPay QR') }}</span> ({{
              formatPrice(cart.totalPayable)
            }}) →
          </button>
          <button type="button" class="secondary" @click="cart.stage = 'cart'">
            {{ t('Back to cart') }}
          </button>
        </div>
      </div>

      <!-- STAGE 4: PROMPTPAY QR PAYMENT -->
      <div v-else-if="cart.stage === 'payment'" class="qr-payment-screen">
        <button class="back-link" type="button" @click="cart.stage = 'review'">
          ← {{ t('Cancel payment') }}
        </button>

        <div class="qr-payment-card">
          <div class="qr-branding">
            <span class="gold-sparkle">✦</span>
            <span class="qr-shop-name">THE PARLOR · BOARD GAME STORE</span>
            <span class="gold-sparkle">✦</span>
          </div>
          <h3 class="qr-title">{{ t('Scan PromptPay QR') }}</h3>
          <p class="qr-subtitle">
            {{ t('Compatible with all Thai Mobile Banking apps') }}
          </p>

          <div class="qr-display-box">
            <img
              v-if="qrDataUrl"
              :src="qrDataUrl"
              alt="PromptPay QR Code"
              width="220"
              height="220"
              class="qr-code-img"
            />
            <div v-else class="qr-loading-placeholder">
              <span>Generating Secure QR…</span>
            </div>
            <div class="qr-timer">
              <span>{{ t('Expires in') }}:</span>
              <strong>{{ formattedTimer }}</strong>
            </div>
          </div>

          <div class="qr-amount-pill">
            <span class="lbl">{{ t('Total amount due') }}</span>
            <strong class="amt">{{ formatPrice(cart.totalPayable) }}</strong>
          </div>

          <p class="qr-ref-code">
            {{ t('Ref No.') }}: <code>{{ qrReference }}</code>
          </p>

          <button
            type="button"
            class="primary confirm-paid-btn"
            :disabled="isProcessingPayment"
            @click="confirmPayment"
          >
            <span v-if="isProcessingPayment"
              ><svg
                width="15"
                height="15"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2.5"
                class="spin-icon"
                aria-hidden="true"
              >
                <path d="M21 12a9 9 0 1 1-6.219-8.56" />
              </svg>
              <span>{{ t('Verifying bank transfer…') }}</span></span
            >
            <span v-else>{{ t('I Have Paid / Simulate Transfer') }}</span>
          </button>
          <p class="qr-demo-hint">
            {{
              t(
                'For testing: Click above to simulate instant payment verification.',
              )
            }}
          </p>
        </div>
      </div>

      <!-- STAGE 5: ORDER CONFIRMATION & TRACKING NUMBER -->
      <div
        v-else-if="cart.stage === 'confirmation'"
        class="order-confirmation-screen"
      >
        <div class="success-banner">
          <div class="success-check-circle">
            <svg
              width="24"
              height="24"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="3"
              aria-hidden="true"
            >
              <polyline points="20 6 9 17 4 12" />
            </svg>
          </div>
          <h3 class="success-title">{{ t('Order Placed Successfully!') }}</h3>
          <p class="success-desc">
            {{
              t(
                'Thank you! Your payment was received and we are packing your games.',
              )
            }}
          </p>
        </div>

        <div v-if="cart.placedOrder" class="confirmation-card">
          <div class="conf-row">
            <span>{{ t('Order ID') }}</span>
            <strong class="order-id-badge">{{ cart.placedOrder.id }}</strong>
          </div>
          <div class="conf-row">
            <span>{{ t('Delivery status') }}</span>
            <span class="status-pill-green"
              >● {{ cart.placedOrder.fulfillmentStatus }}</span
            >
          </div>
          <div v-if="cart.placedOrder.carrierName" class="conf-row">
            <span>{{ t('Carrier') }}</span>
            <strong>{{ cart.placedOrder.carrierName }}</strong>
          </div>
          <div
            v-if="cart.placedOrder.trackingNumber"
            class="tracking-highlight-box"
          >
            <div class="tracking-lbl">{{ t('Tracking Number') }}</div>
            <div class="tracking-val-row">
              <code class="tracking-code">{{
                cart.placedOrder.trackingNumber
              }}</code>
              <button
                type="button"
                class="copy-btn"
                @click="copyTracking(cart.placedOrder.trackingNumber!)"
              >
                {{ copiedTracking ? t('Copied!') : t('Copy') }}
              </button>
            </div>
            <div class="tracking-est">
              <span>{{ cart.placedOrder.estimatedDeliveryDate }}</span>
            </div>
          </div>

          <div class="conf-address-block">
            <strong>{{ t('Delivering to:') }}</strong>
            <p>
              {{ cart.placedOrder.shippingAddress.name }} ({{
                cart.placedOrder.shippingAddress.phone
              }})<br />
              {{ cart.placedOrder.shippingAddress.address }}
              {{ cart.placedOrder.shippingAddress.extra || '' }}<br />
              {{ cart.placedOrder.shippingAddress.city }},
              {{ cart.placedOrder.shippingAddress.region }}
              {{ cart.placedOrder.shippingAddress.postal }}
            </p>
          </div>
        </div>

        <div class="confirmation-actions">
          <button type="button" class="primary" @click="viewAllOrders">
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              aria-hidden="true"
            >
              <path
                d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"
              />
              <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
              <line x1="12" y1="22.08" x2="12" y2="12" />
            </svg>
            <span>{{ t('View My Orders & Track Delivery') }}</span>
          </button>
          <button
            type="button"
            class="secondary"
            @click="
              dialog?.close();
              cart.stage = 'cart';
            "
          >
            {{ t('Done & Return to Store') }}
          </button>
        </div>
      </div>
    </div>
  </dialog>
</template>

<style scoped>
.cart-panel {
  --ink: #202e27;
  --muted: #4e5b53;
  --line: #d3d9d4;
  --accent: #244c38;
  --gold: #c6ad7a;
  position: fixed;
  inset: 0 0 0 auto;
  width: min(440px, 100%);
  height: 100svh;
  max-height: 100svh;
  max-width: 100%;
  margin: 0 0 0 auto;
  padding: 0;
  border: 0;
  border-left: 1px solid #829087;
  background: #fafbf9;
  color: var(--ink);
  font:
    14px/1.5 Arial,
    Helvetica,
    sans-serif;
  opacity: 1;
  transform: translateX(100%);
  transition:
    transform 400ms cubic-bezier(0.4, 0, 0.2, 1),
    display 400ms allow-discrete,
    overlay 400ms allow-discrete;
}
.cart-panel[open] {
  opacity: 1;
  transform: translateX(0);
}
.cart-panel::backdrop {
  background: #14221d00;
  transition:
    background-color 180ms ease,
    display 400ms allow-discrete,
    overlay 400ms allow-discrete;
}
.cart-panel[open]::backdrop {
  background: #14221d99;
}
@starting-style {
  .cart-panel[open] {
    opacity: 1;
    transform: translateX(100%);
  }
  .cart-panel[open]::backdrop {
    background: #14221d00;
  }
}
.cart-content {
  padding: 20px;
  min-height: 100%;
  overflow-y: auto;
  box-sizing: border-box;
}
header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.cart-header-title {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
h2 {
  font-size: 20px;
  font-weight: 700;
  color: #172b21;
}
h2 span {
  font-size: 16px;
  font-weight: 400;
  color: var(--muted);
}
.header-history-link {
  font-size: 11px;
  font-weight: 700;
  color: #244c38;
  background: #eaf1ed;
  padding: 3px 8px;
  border-radius: 4px;
  border: 1px solid #c2d6ca;
  cursor: pointer;
  text-decoration: none;
}
.header-history-link:hover {
  background: #d6e8de;
}
h3 {
  font-size: 15px;
  font-weight: 700;
  color: #172b21;
}
.close-cart {
  padding: 7px 10px;
  font-size: 18px;
  background: transparent;
  border: 0;
  color: #55665c;
  cursor: pointer;
}
.close-cart:hover {
  color: #172b21;
}

button {
  cursor: pointer;
  transition:
    background-color 140ms ease,
    color 140ms ease,
    border-color 140ms ease;
}
button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
button:focus-visible,
input:focus-visible,
textarea:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.cart-error {
  color: #8a3020;
  margin-block: 14px;
}
.empty-cart {
  padding: 55px 0;
  text-align: center;
}
.empty-cart p {
  margin-top: 10px;
  color: var(--muted);
}
.empty-cart .primary {
  margin-top: 24px;
}
.view-orders-cta {
  margin-top: 10px;
  font-weight: 600;
}
.cart-lines {
  list-style: none;
  padding: 0;
  margin: 0;
}
.cart-lines li {
  display: grid;
  grid-template-columns: 60px minmax(0, 1fr);
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--line);
}
.cart-lines img {
  width: 60px;
  height: 78px;
  object-fit: contain;
  mix-blend-mode: multiply;
  background: #f0f0f0;
  border-radius: 4px;
}
.line-info h3 {
  line-height: 1.4;
  margin: 0;
}
.line-info p {
  font-size: 12px;
  color: var(--muted);
  margin-top: 4px;
}
.line-total {
  font-size: 14px;
  white-space: nowrap;
}
.line-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 9px;
}
.quantity-control {
  display: flex;
  border: 1px solid #829087;
  border-radius: 3px;
  overflow: hidden;
}
.quantity-control button {
  width: 29px;
  min-height: 33px;
  font-size: 17px;
  background: #f7f9f7;
  border: 0;
}
.quantity-control button:hover:enabled {
  background: #e6ece7;
}
.quantity-control input {
  width: 36px;
  padding: 5px 1px;
  text-align: center;
  appearance: textfield;
  -moz-appearance: textfield;
  font-size: 13px;
  border: 0;
  border-left: 1px solid #829087;
  border-right: 1px solid #829087;
}
.quantity-control input::-webkit-inner-spin-button {
  appearance: none;
}
.remove-line,
.back-link {
  font-size: 12px;
  text-decoration: underline;
  text-underline-offset: 3px;
  color: var(--muted);
  background: transparent;
  border: 0;
  padding: 0;
  cursor: pointer;
}
.back-link {
  display: inline-block;
  margin-bottom: 14px;
  color: var(--accent);
  font-weight: 600;
}
.cart-summary {
  border-top: 1px solid var(--line);
  padding-top: 16px;
  margin-top: 12px;
}
.cart-summary > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  font-size: 13px;
}
.free-shipping-text {
  color: #1b5e20;
}
.total-row {
  border-top: 1px dashed var(--line);
  padding-top: 10px;
  margin-top: 6px;
}
.grand-total {
  font-size: 17px;
  color: #172b21;
}
.grand-total.highlight {
  color: #1b5e20;
  font-size: 19px;
}
.summary-note {
  font-size: 11px;
  color: var(--muted);
  margin: 6px 0 14px;
  line-height: 1.5;
}
.primary,
.secondary {
  display: block;
  width: 100%;
  padding: 11px 14px;
  border: 1px solid var(--accent);
  border-radius: 4px;
  font-size: 14px;
  font-weight: 600;
  text-align: center;
}
.primary {
  background: var(--accent);
  color: white;
}
.primary:hover:enabled {
  background: #173c29;
}
.place-order-btn {
  background: #142a1f;
  border-color: #c6ad7a;
  color: #f7eed7;
  font-size: 15px;
}
.place-order-btn:hover:enabled {
  background: #1f3f2f;
  color: #ffffff;
}
.secondary {
  margin-top: 6px;
  border: 0;
  background: transparent;
  color: var(--accent);
  font-size: 13px;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.secondary:hover {
  background: #e6ece7;
}

/* Forms */
.checkout-form {
  display: grid;
  gap: 14px;
}
.checkout-form h3 {
  margin: 10px 0 2px;
  font-size: 13px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #4b5e52;
}
.checkout-form label {
  display: grid;
  gap: 5px;
  font-size: 12px;
  font-weight: 600;
  color: #2b3b32;
}
.checkout-form input:not([type='radio']),
.checkout-form select,
textarea {
  width: 100%;
  min-width: 0;
  border: 1px solid #829087;
  padding: 9px 11px;
  border-radius: 4px;
  background: white;
  font-size: 13px;
  box-sizing: border-box;
}
.form-pair {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 10px;
}

/* Review stage */
.review-box {
  background: #f4f7f4;
  border: 1px solid #d3d9d4;
  border-radius: 6px;
  padding: 12px;
  font-size: 13px;
  margin-bottom: 16px;
  line-height: 1.6;
}
.review-divider {
  height: 1px;
  background: #d3d9d4;
  margin: 8px 0;
}
.review-sub {
  font-size: 12px;
  color: var(--muted);
}
.delivery-time {
  color: #1b5e20;
  font-weight: 600;
}
.review-note {
  margin-top: 6px;
  color: #55665c;
  font-size: 12px;
}
.review-items {
  list-style: none;
  padding: 0;
  margin: 0;
}
.review-items li {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
}
.review-item-main small {
  display: block;
  color: var(--muted);
  font-size: 11px;
  margin-top: 2px;
}

/* PromptPay QR Payment Screen */
.qr-payment-card {
  background: #ffffff;
  border: 1.5px solid #c6ad7a;
  border-radius: 8px;
  padding: 20px 16px;
  text-align: center;
  box-shadow: 0 4px 16px rgba(20, 42, 31, 0.08);
}
.qr-branding {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: #8c7649;
  margin-bottom: 6px;
}
.gold-sparkle {
  color: #c6ad7a;
}
.qr-title {
  font-size: 18px;
  margin: 0 0 4px;
  color: #142a1f;
}
.qr-subtitle {
  font-size: 12px;
  color: var(--muted);
  margin: 0 0 16px;
}
.qr-display-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #fafbf9;
  border: 1px dashed #c6ad7a;
  border-radius: 8px;
  padding: 14px;
  margin-bottom: 14px;
}
.qr-code-img {
  border-radius: 4px;
  background: white;
  padding: 6px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.qr-timer {
  margin-top: 10px;
  font-size: 12px;
  color: #8a3020;
}
.qr-amount-pill {
  background: #f0f7f2;
  border: 1px solid #a3cfb1;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 12px;
}
.qr-amount-pill .lbl {
  display: block;
  font-size: 11px;
  color: #3b5947;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}
.qr-amount-pill .amt {
  font-size: 22px;
  color: #142a1f;
}
.qr-ref-code {
  font-size: 12px;
  color: #637369;
  margin: 0 0 16px;
}
.qr-ref-code code {
  background: #eef2ef;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 700;
  color: #172b21;
}
.confirm-paid-btn {
  background: #1b5e20;
  border-color: #1b5e20;
  font-size: 15px;
}
.confirm-paid-btn:hover:enabled {
  background: #124116;
}
.qr-demo-hint {
  font-size: 11px;
  color: #7a8a80;
  margin-top: 10px;
  line-height: 1.4;
}

/* Order Confirmation Screen */
.order-confirmation-screen {
  padding-top: 8px;
}
.success-banner {
  text-align: center;
  padding: 16px 0 20px;
}
.success-check-circle {
  width: 52px;
  height: 52px;
  background: #2e7d32;
  color: white;
  border-radius: 50%;
  font-size: 28px;
  line-height: 52px;
  margin: 0 auto 12px;
  font-weight: bold;
}
.success-title {
  font-size: 20px;
  color: #142a1f;
  margin: 0 0 6px;
}
.success-desc {
  font-size: 13px;
  color: var(--muted);
  line-height: 1.5;
  margin: 0;
}
.confirmation-card {
  background: #ffffff;
  border: 1px solid #d3d9d4;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.conf-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #eef2ee;
  font-size: 13px;
}
.order-id-badge {
  background: #e8f0eb;
  padding: 2px 8px;
  border-radius: 4px;
  color: #172b21;
  font-family: monospace;
  font-size: 13px;
}
.status-pill-green {
  font-size: 11px;
  font-weight: 700;
  color: #2e7d32;
  background: #e8f5e9;
  padding: 3px 8px;
  border-radius: 4px;
}
.tracking-highlight-box {
  background: #f7f9f7;
  border: 1.5px dashed #244c38;
  border-radius: 6px;
  padding: 12px;
  margin: 12px 0;
  text-align: center;
}
.tracking-lbl {
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #55665c;
  margin-bottom: 6px;
}
.tracking-val-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.tracking-code {
  font-size: 17px;
  font-weight: 800;
  color: #142a1f;
  letter-spacing: 0.05em;
}
.copy-btn {
  background: #244c38;
  color: white;
  border: 0;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  padding: 4px 8px;
  cursor: pointer;
}
.copy-btn:hover {
  background: #173827;
}
.tracking-est {
  font-size: 12px;
  color: #1b5e20;
  font-weight: 600;
  margin-top: 6px;
}
.conf-address-block {
  font-size: 12px;
  color: #44534a;
  line-height: 1.6;
  margin-top: 10px;
}
.conf-address-block strong {
  display: block;
  margin-bottom: 2px;
  color: #172b21;
}
.confirmation-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.line-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}
.location-conflict {
  border-left: 2px solid #9b5a38;
  padding: 8px 12px;
  margin: 12px 0;
  color: #783a25;
  font-size: 12px;
  line-height: 1.7;
}
.line-info .line-conflict {
  color: #8a3020;
  font-size: 12px;
  margin-top: 6px;
}

/* Step Progress Bar */
.cart-header-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.cart-header-title h2 {
  font-size: 20px;
  font-weight: 800;
  color: #142a1f;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.header-count-num {
  font-size: 16px;
  font-weight: 600;
  color: #829087;
}

.cart-step-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #f4efe6;
  border: 1px solid #e5dcce;
  border-radius: 6px;
  margin-bottom: 16px;
}

.step-node {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #7d8b82;
  transition: all 180ms ease;
}

.step-node .step-num {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #e2dac9;
  color: #556259;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
}

.step-node.active {
  color: #142a1f;
  font-weight: 700;
}

.step-node.active .step-num {
  background: #142a1f;
  color: #ffd98a;
  box-shadow: 0 0 0 2px #c6ad7a;
}

.step-node.done {
  color: #1f5e3d;
}

.step-node.done .step-num {
  background: #1f5e3d;
  color: #ffffff;
}

.step-connector {
  flex: 1;
  height: 2px;
  background: #e2dac9;
  margin: 0 6px;
}

.step-connector.filled {
  background: #c6ad7a;
}

/* Shopee-style Selection Bar */
.cart-select-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #ffffff;
  border: 1px solid #ebe6dc;
  border-radius: 6px;
  margin-bottom: 14px;
}

.shopee-chk-wrap {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #142a1f;
  cursor: pointer;
  user-select: none;
}

.shopee-chk-input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.shopee-chk-box {
  width: 18px;
  height: 18px;
  border: 1.5px solid #a39478;
  border-radius: 4px;
  background: #ffffff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #142a1f;
  transition: all 140ms ease;
}

.shopee-chk-input:checked + .shopee-chk-box {
  background: #142a1f;
  border-color: #142a1f;
  color: #ffd98a;
}

.indeterminate-bar {
  font-size: 14px;
  font-weight: bold;
  line-height: 1;
  color: #142a1f;
}

.btn-clear-selected {
  background: none;
  border: none;
  color: #a43e2e;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 140ms ease;
}

.btn-clear-selected:hover {
  background: #fdf2f2;
}

/* Cart Line Row with Checkbox */
.cart-line-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid #ebe6dc;
  transition: opacity 160ms ease;
}

.cart-line-item:not(.is-selected) {
  opacity: 0.55;
}

.item-chk-wrap {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  padding: 4px;
}

.footer-orders-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  margin-top: 10px;
  padding: 8px 12px;
  background: transparent;
  border: 1px dashed #c6ad7a;
  border-radius: 4px;
  color: #142a1f;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 140ms ease;
}

.footer-orders-link:hover {
  background: #fffdf8;
  border-color: #142a1f;
}
</style>

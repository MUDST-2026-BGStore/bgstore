<script setup lang="ts">
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue';
import { useCartStore } from '../stores/cart';
import { formatPrice } from '../data/catalog';
import { useStoreText } from '../data/store-text';
import CartFulfillment from './CartFulfillment.vue';
const cart = useCartStore();
const { t } = useStoreText();
const dialog = ref<HTMLDialogElement | null>(null);
const error = ref('');
// TODO(checkout-contact-api): Submit contact/address through server validation
// when checkout exists; native form checks alone are insufficient.
// Deliberately memory-only: do not save contact/address data in browser storage.
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
const title = computed(() =>
  t(
    {
      cart: 'Your cart',
      details: 'Contact & delivery',
      review: 'Order review',
    }[cart.stage],
  ),
);
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
    if (!cart.count) cart.stage = 'cart';
    else if (cart.stage === 'review') {
      cart.stage = 'cart';
      error.value = t('The cart was updated. Please review your items again.');
    }
  },
  { deep: true },
);
onUnmounted(() => {
  cart.isOpen = false;
});
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
  // TODO(checkout-api): Revalidate cart/stock, obtain branch-specific pickup slots,
  // shipping methods/rates, address validation, tax and discounts from the server.
  // Create checkout with an idempotency key; payment must use a hosted/tokenized
  // provider. Only a verified backend result/webhook may confirm a real order.
  // Do not add card-number inputs or imply an order succeeded in this preview.
  if (cart.canCheckout) cart.stage = 'review';
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
      <header>
        <h2 id="cart-title">
          {{ title
          }}<span v-if="cart.stage === 'cart'"> ({{ cart.count }})</span>
        </h2>
        <button
          class="close-cart"
          type="button"
          :aria-label="t('Close cart')"
          autofocus
          @click="dialog?.close()"
        >
          ✕
        </button>
      </header>
      <CartFulfillment v-if="cart.stage !== 'review'" />
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
      <template v-if="cart.stage === 'cart'">
        <div v-if="!cart.count" class="empty-cart">
          <h3>{{ t('Your cart is empty') }}</h3>
          <p>{{ t('Add a game from the collection to get started.') }}</p>
          <button
            class="primary continue-shopping"
            type="button"
            @click="dialog?.close()"
          >
            {{ t('Continue shopping') }}
          </button>
        </div>
        <template v-else>
          <ul class="cart-lines">
            <li
              v-for="line in cart.lines"
              :key="line.variantId"
              :data-variant="line.variantId"
            >
              <img
                :src="`/images/games/${line.game.id}.jpg`"
                :alt="line.game.name"
                width="76"
                height="90"
              />
              <div class="line-info">
                <div class="line-heading">
                  <h3>{{ line.game.name }}</h3>
                  <strong class="line-total">{{
                    formatPrice(line.total)
                  }}</strong>
                </div>
                <p>
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
                      −</button
                    ><input
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
                    /><button
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
          <div class="cart-summary">
            <div>
              <span>{{ t('Item subtotal') }}</span
              ><strong>{{ formatPrice(cart.subtotal) }}</strong>
            </div>
            <p>
              {{ t('Shipping and taxes are calculated at checkout.') }}
            </p>
            <button
              type="button"
              class="primary"
              :disabled="!cart.canCheckout"
              @click="cart.stage = 'details'"
            >
              {{ t('Checkout') }}</button
            ><button
              type="button"
              class="secondary continue-shopping"
              @click="dialog?.close()"
            >
              {{ t('Continue shopping') }}
            </button>
          </div>
        </template>
      </template>
      <form
        v-else-if="cart.stage === 'details'"
        class="checkout-form"
        @submit.prevent="review"
      >
        <button class="back-link" type="button" @click="cart.stage = 'cart'">
          ← {{ t('Back to cart') }}
        </button>
        <h3>{{ t('Contact') }}</h3>
        <label
          >{{ t('Email')
          }}<input
            v-model.trim="contact.email"
            type="email"
            name="email"
            autocomplete="email"
            required
            maxlength="254"
        /></label>
        <label
          >{{ t('Full name')
          }}<input
            v-model.trim="contact.name"
            name="name"
            autocomplete="name"
            required
            maxlength="150"
            pattern=".*\S.*"
        /></label>
        <label
          >{{ t('Phone')
          }}<input
            v-model.trim="contact.phone"
            type="tel"
            name="phone"
            autocomplete="tel"
            required
            maxlength="30"
            pattern="[+0-9 .\-]{6,30}"
        /></label>
        <template v-if="cart.method === 'shipping'">
          <label
            >{{ t('Address')
            }}<input
              v-model.trim="contact.address"
              name="address"
              autocomplete="address-line1"
              required
              maxlength="200"
              pattern=".*\S.*"
          /></label>
          <label
            >{{ t('Apartment, suite, etc. (optional)')
            }}<input
              v-model.trim="contact.extra"
              name="address2"
              autocomplete="address-line2"
              maxlength="200"
          /></label>
          <div class="form-pair">
            <label
              >{{ t('City / district')
              }}<input
                v-model.trim="contact.city"
                name="city"
                autocomplete="address-level2"
                required
                maxlength="100"
                pattern=".*\S.*" /></label
            ><label
              >{{ t('Province / region')
              }}<input
                v-model.trim="contact.region"
                name="region"
                autocomplete="address-level1"
                required
                maxlength="100"
                pattern=".*\S.*"
            /></label>
          </div>
          <div class="form-pair">
            <label
              >{{ t('Postal code')
              }}<input
                v-model.trim="contact.postal"
                name="postal"
                autocomplete="postal-code"
                required
                maxlength="20"
                pattern=".*\S.*" /></label
            ><label
              >{{ t('Country')
              }}<input
                v-model.trim="contact.country"
                name="country"
                autocomplete="country-name"
                required
                maxlength="80"
                pattern=".*\S.*"
            /></label>
          </div>
        </template>
        <label
          >{{ t('Order note (optional)')
          }}<textarea
            v-model.trim="contact.note"
            name="note"
            rows="3"
            maxlength="1000"
          />
        </label>
        <div class="cart-summary">
          <div>
            <span>{{ t('Item subtotal') }}</span
            ><strong>{{ formatPrice(cart.subtotal) }}</strong>
          </div>
          <button class="primary" type="submit" :disabled="!cart.canCheckout">
            {{ t('Review order') }}
          </button>
        </div>
      </form>
      <div v-else class="order-review">
        <button class="back-link" type="button" @click="cart.stage = 'details'">
          ← {{ t('Edit details') }}
        </button>
        <h3>{{ t('Contact') }}</h3>
        <p>
          {{ contact.name }}<br />{{ contact.email }}<br />{{ contact.phone }}
        </p>
        <h3>{{ t('Delivery') }}</h3>
        <p>
          {{
            t(
              cart.method === 'pickup'
                ? 'Collect in store'
                : 'Ship to an address',
            )
          }}
        </p>
        <p v-if="cart.method === 'shipping'">
          {{ contact.address }} {{ contact.extra }}<br />{{ contact.city }},
          {{ contact.region }} {{ contact.postal }}<br />{{ contact.country }}
        </p>
        <p v-if="cart.method === 'pickup' && cart.fulfillmentBranch">
          {{ t(cart.fulfillmentBranch.name) }}
        </p>
        <p v-if="contact.note">{{ contact.note }}</p>
        <h3>{{ t('Items') }}</h3>
        <ul class="review-items">
          <li v-for="line in cart.lines" :key="line.variantId">
            <span
              >{{ line.game.name }} × {{ line.quantity
              }}<small>{{
                t(
                  line.variant.language === 'th'
                    ? 'Thai edition'
                    : 'English edition',
                )
              }}</small></span
            ><strong>{{ formatPrice(line.total) }}</strong>
          </li>
        </ul>
        <div class="cart-summary">
          <div>
            <span>{{ t('Item subtotal') }}</span
            ><strong>{{ formatPrice(cart.subtotal) }}</strong>
          </div>
          <div>
            <span>{{ t('Shipping') }}</span
            ><span>—</span>
          </div>
          <div>
            <span>{{ t('Taxes') }}</span
            ><span>—</span>
          </div>
          <!-- TODO(checkout-api): Keep disabled until server checkout, atomic stock
               holds, idempotency and verified payment/order results are implemented.
               Shipping/tax above require a server quote before showing a payable total. -->
          <button class="primary" type="button" disabled>
            {{ t('Place order') }}</button
          ><button type="button" class="secondary" @click="cart.stage = 'cart'">
            {{ t('Back to cart') }}
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
  position: fixed;
  inset: 0 0 0 auto;
  width: min(400px, 100%);
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
}
header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}
h2 {
  font-size: 20px;
  font-weight: 600;
}
h2 span {
  font-size: 17px;
  font-weight: 400;
}
h3 {
  font-size: 15px;
  font-weight: 600;
}
.close-cart {
  padding: 7px 10px;
  font-size: 18px;
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
  outline-offset: 3px;
}
.cart-error {
  color: #8a3020;
  margin-block: 16px;
}
.empty-cart {
  padding: 55px 0;
}
.empty-cart p {
  margin-top: 10px;
  color: var(--muted);
}
.empty-cart .primary {
  margin-top: 24px;
}
.cart-lines {
  list-style: none;
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
}
.line-info h3 {
  line-height: 1.4;
}
.line-info p {
  font-size: 12px;
  color: var(--muted);
  margin-top: 5px;
}
.line-total {
  font-size: 13px;
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
}
.cart-summary {
  border-top: 1px solid var(--line);
  padding-top: 16px;
  margin-top: 8px;
}
.cart-summary > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.cart-summary > p {
  font-size: 12px;
  color: var(--muted);
  margin: 8px 0 14px;
  line-height: 1.8;
}
.primary,
.secondary {
  display: block;
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--accent);
  border-radius: 3px;
  font-size: 14px;
  text-align: center;
}
.primary {
  background: var(--accent);
  color: white;
}
.primary:hover:enabled {
  background: #173c29;
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
.checkout-form {
  display: grid;
  gap: 16px;
}
.checkout-form label {
  display: grid;
  gap: 6px;
  font-size: 13px;
}
.checkout-form input:not([type='radio']),
.checkout-form select,
textarea {
  width: 100%;
  min-width: 0;
  border: 1px solid #829087;
  padding: 10px 12px;
  border-radius: 3px;
  background: white;
  font-size: 14px;
}
fieldset {
  border: 1px solid var(--line);
  padding: 15px;
  display: grid;
  gap: 12px;
}
legend {
  padding: 0 6px;
  font-size: 14px;
  font-weight: 600;
}
.checkout-form .radio-option {
  display: flex;
  align-items: center;
  gap: 9px;
}
input[type='radio'] {
  accent-color: var(--accent);
}
.field-note {
  font-size: 12px;
  color: var(--muted);
}
.form-pair {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
}
.order-review h3 {
  margin: 22px 0 8px;
}
.order-review p {
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.review-items li {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 0;
  border-bottom: 1px solid var(--line);
}
.review-items small {
  display: block;
  color: var(--muted);
  font-size: 12px;
  margin-top: 3px;
}
@media (max-width: 420px) {
  .cart-content {
    padding: 20px 16px;
  }
  .cart-lines li {
    grid-template-columns: 56px minmax(0, 1fr);
    gap: 12px;
  }
  .cart-lines img {
    width: 56px;
    height: 76px;
  }

  .form-pair {
    grid-template-columns: 1fr;
  }
  h2 {
    font-size: 21px;
  }
}
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    transition: none !important;
    animation: none !important;
  }
}

.line-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}
.line-heading h3 {
  min-width: 0;
}
.cart-lines li:last-child {
  border-bottom: 0;
}
.review-items li:last-child {
  border-bottom: 0;
}
.primary:disabled {
  opacity: 1;
  background: #e2e6e2;
  border-color: #b5bfb6;
  color: #4e5b53;
}

.location-conflict {
  border-left: 2px solid #9b5a38;
  padding: 8px 12px;
  margin: 12px 0;
  color: #783a25;
  font-size: 12px;
  line-height: 1.7;
}
.location-conflict p {
  margin-top: 4px;
}
.line-info .line-conflict {
  color: #8a3020;
  font-size: 12px;
  margin-top: 6px;
}
</style>

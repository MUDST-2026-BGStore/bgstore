<script setup lang="ts">
import { useCartStore } from '../stores/cart';
import { useStoreText } from '../data/store-text';
const cart = useCartStore();
const { t } = useStoreText();
</script>
<template>
  <button
    class="cart-trigger"
    type="button"
    :aria-label="t('Cart ({count})', { count: cart.count })"
    @click="cart.open()"
  >
    <svg
      viewBox="0 0 24 24"
      width="19"
      height="19"
      fill="none"
      stroke="currentColor"
      stroke-width="1.5"
      aria-hidden="true"
    >
      <path d="M5 7h14l1 14H4L5 7Z" />
      <path d="M8 8V6a4 4 0 0 1 8 0v2" /></svg
    ><span>{{ cart.count }}</span>
  </button>
</template>
<style scoped>
.cart-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-parlor-cream);
  min-height: 36px;
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 140ms ease;
  white-space: nowrap;
}
.cart-trigger:hover {
  background: var(--color-parlor-green-600);
}
.cart-trigger:focus-visible {
  outline: 2px solid var(--color-parlor-gold-300);
  outline-offset: 2px;
}
@media (prefers-reduced-motion: reduce) {
  .cart-trigger {
    transition: none;
  }
}
</style>

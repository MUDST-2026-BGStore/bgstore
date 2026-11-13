import { defineStore } from 'pinia';
import { ref } from 'vue';

/**
 * Whether the My Orders dialog is open, and which order to bring into view. Order
 * data itself is server state and lives in the query cache (api/orders.ts).
 */
export const useOrdersStore = defineStore('retail-orders', () => {
  const isOrdersModalOpen = ref(false);
  const activeOrderId = ref<string | null>(null);

  function openOrdersModal(orderId?: string) {
    activeOrderId.value = orderId ?? null;
    isOrdersModalOpen.value = true;
  }

  function closeOrdersModal() {
    isOrdersModalOpen.value = false;
    activeOrderId.value = null;
  }

  return {
    isOrdersModalOpen,
    activeOrderId,
    openOrdersModal,
    closeOrdersModal,
  };
});

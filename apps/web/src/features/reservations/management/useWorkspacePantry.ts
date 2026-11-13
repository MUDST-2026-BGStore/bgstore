// TODO(pantry-api): Counter refreshment orders have no backend. Their item list and
// stock deductions are local sample state; branch pantry stock is read and adjusted
// through the menu API in WorkspacePantryStock.
import { computed, ref } from 'vue';
import { createPantrySamples, type FoodDrinkStockItem } from './pantry.samples';
import { useCustomerReservationsStore } from '../data/reservations-store';

export function useWorkspacePantry() {
  const reservationsStore = useCustomerReservationsStore();
  const branchStockItems = ref(createPantrySamples());
  // Staff Snack Tab Ordering
  const staffSnackReservation = ref<any>(null);
  const staffSnackQuantities = ref<Record<string, number>>({});

  function openAddSnacks(res: any) {
    staffSnackReservation.value = res;
    staffSnackQuantities.value = {};
  }

  function getStaffSnackQty(itemId: string): number {
    return staffSnackQuantities.value[itemId] || 0;
  }

  function adjustStaffSnackQty(item: FoodDrinkStockItem, delta: number) {
    const current = getStaffSnackQty(item.id);
    const next = Math.max(0, current + delta);
    if (next > item.currentStock) return; // Prevent ordering more than current stock
    if (next === 0) {
      delete staffSnackQuantities.value[item.id];
    } else {
      staffSnackQuantities.value[item.id] = next;
    }
  }

  const staffSnackSelectedItems = computed(() => {
    return Object.entries(staffSnackQuantities.value)
      .filter(([, qty]) => qty > 0)
      .map(([id, qty]) => {
        const item = branchStockItems.value.find((f) => f.id === id);
        return {
          item: {
            id,
            name: item?.name || id,
            nameTh: item?.nameTh,
            price: item?.price || 0,
          },
          quantity: qty,
        };
      });
  });

  const staffSnackSubtotal = computed(() => {
    return staffSnackSelectedItems.value.reduce(
      (sum, it) => sum + it.item.price * it.quantity,
      0,
    );
  });

  function submitStaffSnackOrder() {
    if (
      !staffSnackReservation.value ||
      staffSnackSelectedItems.value.length === 0
    )
      return;

    // Deduct inventory stock in real time
    for (const it of staffSnackSelectedItems.value) {
      const stockItem = branchStockItems.value.find((s) => s.id === it.item.id);
      if (stockItem) {
        stockItem.currentStock = Math.max(
          0,
          stockItem.currentStock - it.quantity,
        );
      }
    }

    reservationsStore.addStaffSnackOrder(
      staffSnackReservation.value.id,
      staffSnackSelectedItems.value,
      'Added by staff at counter',
    );
    staffSnackReservation.value = null;
  }
  return {
    reservationsStore,
    branchStockItems,
    staffSnackReservation,
    staffSnackQuantities,
    openAddSnacks,
    getStaffSnackQty,
    adjustStaffSnackQty,
    staffSnackSelectedItems,
    staffSnackSubtotal,
    submitStaffSnackOrder,
  };
}

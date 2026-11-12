import { computed, ref } from 'vue';
import { createPantrySamples, type FoodDrinkStockItem } from './pantry.samples';
import { useCustomerReservationsStore } from '../data/reservations-store';

export function useWorkspacePantry() {
  const reservationsStore = useCustomerReservationsStore();
  const branchStockItems = ref(createPantrySamples());
  const stockCategoryFilter = ref<string>('ALL');
  const stockStatusFilter = ref<string>('ALL');

  const filteredStockItems = computed(() => {
    return branchStockItems.value.filter((item) => {
      const matchesCat =
        stockCategoryFilter.value === 'ALL' ||
        item.category === stockCategoryFilter.value;
      let matchesStatus = true;
      if (stockStatusFilter.value === 'NEED_REORDER') {
        matchesStatus = item.currentStock <= item.lowStockThreshold;
      } else if (stockStatusFilter.value === 'OUT') {
        matchesStatus = item.currentStock === 0;
      } else if (stockStatusFilter.value === 'HEALTHY') {
        matchesStatus = item.currentStock > item.lowStockThreshold;
      }
      return matchesCat && matchesStatus;
    });
  });

  const totalStockUnits = computed(() =>
    branchStockItems.value.reduce((s, it) => s + it.currentStock, 0),
  );
  const healthyStockCount = computed(
    () =>
      branchStockItems.value.filter(
        (it) => it.currentStock > it.lowStockThreshold,
      ).length,
  );
  const lowStockCount = computed(
    () =>
      branchStockItems.value.filter(
        (it) => it.currentStock <= it.lowStockThreshold && it.currentStock > 0,
      ).length,
  );
  const outOfStockCount = computed(
    () => branchStockItems.value.filter((it) => it.currentStock === 0).length,
  );

  // Staff Quick +/- Adjustment
  function adjustStockQty(item: FoodDrinkStockItem, delta: number) {
    item.currentStock = Math.max(0, item.currentStock + delta);
  }

  // Restock Modal
  const showRestockModal = ref(false);
  const restockTargetItem = ref<FoodDrinkStockItem | null>(null);
  const restockBatchQty = ref(12);

  function openRestockModal(item: FoodDrinkStockItem) {
    restockTargetItem.value = item;
    restockBatchQty.value = 12;
    showRestockModal.value = true;
  }

  function submitRestock() {
    if (restockTargetItem.value && restockBatchQty.value > 0) {
      restockTargetItem.value.currentStock += restockBatchQty.value;
    }
    showRestockModal.value = false;
    restockTargetItem.value = null;
  }

  // Higher Role Exclusive: Add New Food/Drink Type
  const showAddFoodModal = ref(false);
  const newFoodForm = ref({
    name: '',
    nameTh: '',
    category: 'beverages' as
      | 'beverages'
      | 'snacks'
      | 'tea_coffee'
      | 'bakery_desserts',
    price: 50,
    costPrice: 25,
    unit: 'cans',
    initialStock: 24,
    lowStockThreshold: 10,
  });

  function openAddFoodModal() {
    newFoodForm.value = {
      name: '',
      nameTh: '',
      category: 'beverages',
      price: 50,
      costPrice: 25,
      unit: 'cans',
      initialStock: 24,
      lowStockThreshold: 10,
    };
    showAddFoodModal.value = true;
  }

  function submitAddNewFood() {
    if (!newFoodForm.value.name.trim()) return;
    const newId = `custom-${Date.now()}`;
    branchStockItems.value.unshift({
      id: newId,
      name: newFoodForm.value.name.trim(),
      nameTh: newFoodForm.value.nameTh.trim() || newFoodForm.value.name.trim(),
      category: newFoodForm.value.category,
      price: Number(newFoodForm.value.price) || 0,
      costPrice: Number(newFoodForm.value.costPrice) || 0,
      unit: newFoodForm.value.unit || 'units',
      currentStock: Number(newFoodForm.value.initialStock) || 0,
      lowStockThreshold: Number(newFoodForm.value.lowStockThreshold) || 10,
    });
    showAddFoodModal.value = false;
  }

  function removeStockItem(itemId: string) {
    branchStockItems.value = branchStockItems.value.filter(
      (i) => i.id !== itemId,
    );
  }

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
    stockCategoryFilter,
    stockStatusFilter,
    filteredStockItems,
    totalStockUnits,
    healthyStockCount,
    lowStockCount,
    outOfStockCount,
    adjustStockQty,
    showRestockModal,
    restockTargetItem,
    restockBatchQty,
    openRestockModal,
    submitRestock,
    showAddFoodModal,
    newFoodForm,
    openAddFoodModal,
    submitAddNewFood,
    removeStockItem,
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

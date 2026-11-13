// TODO(workspace-inventory-api): Sale stock here is sample data. Branch edition
// prices, stock adjustments and order fulfillment now have endpoints; wire this
// composable and the fulfillment queue to them.
import { computed, ref, type Ref } from 'vue';
import { retailStockSamples } from '../components/management-inventory.samples';
import { saleStockItems } from '../components/inventory/sale-stock';
import type { FoodDrinkStockItem } from './pantry.samples';

export interface StockReceiptEntry {
  id: string;
  quantity: number;
}

// Workspace-owned sample balances, matching the existing pantry state lifetime.
// Replace these commands with inventory transactions when those APIs are wired.
export function useWorkspaceSaleStock(foodItems: Ref<FoodDrinkStockItem[]>) {
  const retailItems = ref(retailStockSamples.map((item) => ({ ...item })));
  const items = computed(() =>
    saleStockItems(retailItems.value, foodItems.value),
  );

  function setOnHand(id: string, quantity: number): boolean {
    const item = items.value.find((row) => row.id === id);
    if (!item || !Number.isSafeInteger(quantity) || quantity < item.reserved)
      return false;
    if (item.type === 'retail') item.source.onHand = quantity;
    else item.source.currentStock = quantity;
    return true;
  }

  function receive(entries: readonly StockReceiptEntry[]): boolean {
    if (
      !entries.length ||
      new Set(entries.map((entry) => entry.id)).size !== entries.length
    )
      return false;
    const updates = entries.map((entry) => {
      const item = items.value.find((row) => row.id === entry.id);
      if (!item || !Number.isSafeInteger(entry.quantity) || entry.quantity <= 0)
        return null;
      const quantity = item.onHand + entry.quantity;
      return Number.isSafeInteger(quantity) ? { id: item.id, quantity } : null;
    });
    // Validate the whole batch before changing any balance.
    if (updates.some((update) => update === null)) return false;
    for (const update of updates)
      if (update) setOnHand(update.id, update.quantity);
    return true;
  }

  return { items, setOnHand, receive };
}

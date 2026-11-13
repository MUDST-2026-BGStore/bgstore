import { computed, ref, watch, type Ref } from 'vue';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import {
  adjustMenuStock,
  adjustSaleStock,
  listBranchGames,
} from '@theparlor/api-client';
import { collectPages } from '../../../shared/lib/pagination';
import {
  retailStockSamples,
  toRetailStockSamples,
} from '../components/management-inventory.samples';
import { saleStockItems } from '../components/inventory/sale-stock';
import type { FoodDrinkStockItem } from './pantry.samples';

export interface StockReceiptEntry {
  id: string;
  quantity: number;
}

export function useWorkspaceSaleStock(
  foodItems: Ref<FoodDrinkStockItem[]>,
  selectedBranchId?: Ref<string>,
) {
  const queryClient = selectedBranchId ? useQueryClient() : undefined;
  const retailItems = ref(retailStockSamples.map((item) => ({ ...item })));

  const branchGamesQuery = selectedBranchId
    ? useQuery({
        queryKey: ['workspace', 'branchGames', selectedBranchId],
        enabled: computed(() => Boolean(selectedBranchId.value)),
        queryFn: ({ signal }) =>
          collectPages((cursor) =>
            listBranchGames(
              selectedBranchId.value,
              { limit: 100, cursor },
              undefined,
              signal,
            ),
          ),
      })
    : undefined;

  if (branchGamesQuery && selectedBranchId) {
    watch(
      () => branchGamesQuery.data.value,
      (branchGames) => {
        if (!branchGames?.length) return;
        const mapped = toRetailStockSamples(branchGames);
        if (mapped.length > 0) {
          retailItems.value = mapped;
        }
      },
      { immediate: true },
    );
    watch(selectedBranchId, () => {
      const branchGames = branchGamesQuery.data.value;
      const mapped = branchGames?.length
        ? toRetailStockSamples(branchGames)
        : [];
      retailItems.value =
        mapped.length > 0
          ? mapped
          : retailStockSamples.map((item) => ({ ...item }));
    });
  }

  const items = computed(() =>
    saleStockItems(retailItems.value, foodItems.value),
  );

  function syncAdjustment(
    item: (typeof items.value)[number],
    delta: number,
    reason: string,
  ) {
    const branchId = selectedBranchId?.value;
    if (!branchId || delta === 0) return;
    if (item.type === 'retail' && item.source.editionId) {
      const source = item.source;
      void adjustSaleStock(branchId, source.editionId!, { delta, reason })
        .then((res) => {
          if (typeof res?.saleOnHand === 'number') {
            source.onHand = res.saleOnHand;
          }
          void queryClient?.invalidateQueries({
            queryKey: ['workspace', 'branchGames', branchId],
          });
        })
        .catch(() => {
          // Keep optimistic local balance when offline or unauthenticated
        });
    } else if (item.type === 'food' && item.source.menuItemId) {
      const source = item.source;
      void adjustMenuStock(branchId, source.menuItemId!, { delta, reason })
        .then((res) => {
          const nextStock =
            typeof res?.available === 'number' ? res.available : res?.onHand;
          if (typeof nextStock === 'number') {
            source.currentStock = nextStock;
          }
          void queryClient?.invalidateQueries({
            queryKey: ['workspace', 'branchMenu', branchId],
          });
          void queryClient?.invalidateQueries({
            queryKey: ['branch-menu', branchId],
          });
        })
        .catch(() => {
          // Keep optimistic local balance when offline or unauthenticated
        });
    }
  }

  function setOnHand(id: string, quantity: number): boolean {
    const item = items.value.find((row) => row.id === id);
    if (!item || !Number.isSafeInteger(quantity) || quantity < item.reserved)
      return false;
    const delta = quantity - item.onHand;
    if (item.type === 'retail') item.source.onHand = quantity;
    else item.source.currentStock = quantity;
    syncAdjustment(
      item,
      delta,
      delta > 0 ? 'Stock receipt' : 'Inventory count adjustment',
    );
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

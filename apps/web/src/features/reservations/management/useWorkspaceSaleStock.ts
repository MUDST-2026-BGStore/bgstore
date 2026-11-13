import { computed, ref, type Ref } from 'vue';
import { useQueryClient } from '@tanstack/vue-query';
import { adjustSaleStock } from '@theparlor/api-client';
import {
  retailStockSamples,
  toRetailStockRows,
  type RetailStockSample,
} from '../components/management-inventory.samples';
import { saleStockItems } from '../components/inventory/sale-stock';
import { branchGamesKey, useBranchGamesQuery } from './branch-games';

export interface StockReceiptEntry {
  id: string;
  quantity: number;
}

/**
 * Retail sale stock for the selected branch. Live editions come from the branch
 * games API and every change is a server stock adjustment; the screen shows the
 * server's balance after each one. Sample rows remain only while the branch
 * offers no editions.
 */
export function useWorkspaceSaleStock(selectedBranchId: Ref<string>) {
  const queryClient = useQueryClient();
  const branchGames = useBranchGamesQuery(selectedBranchId);
  const samples = ref<RetailStockSample[]>(
    retailStockSamples.map((item) => ({ ...item })),
  );
  const liveRows = computed(() =>
    toRetailStockRows(branchGames.data.value ?? []),
  );
  const isLive = computed(() => liveRows.value.length > 0);
  const items = computed(() =>
    saleStockItems(isLive.value ? liveRows.value : samples.value),
  );
  const saving = ref(false);
  const error = ref('');

  async function adjust(
    changes: readonly { id: string; delta: number }[],
    reason: string,
  ): Promise<boolean> {
    const branchId = selectedBranchId.value;
    const rows = changes.map((change) => ({
      ...change,
      item: items.value.find((row) => row.id === change.id),
    }));
    if (rows.some((row) => !row.item)) return false;
    if (!isLive.value) {
      for (const { item, delta } of rows) item!.source.onHand += delta;
      return true;
    }
    saving.value = true;
    error.value = '';
    let saved = 0;
    try {
      for (const { item, delta } of rows) {
        await adjustSaleStock(branchId, item!.source.editionId!, {
          delta,
          reason,
        });
        saved += 1;
      }
      return true;
    } catch {
      error.value =
        saved > 0
          ? `Only ${saved} of ${rows.length} stock changes were saved. Review the balances and try again.`
          : 'Stock could not be updated. Refresh and try again.';
      return false;
    } finally {
      saving.value = false;
      // Show the server's balances, including any changes made before a failure.
      await queryClient.invalidateQueries({
        queryKey: branchGamesKey(branchId),
      });
    }
  }

  function setOnHand(id: string, quantity: number): Promise<boolean> {
    const item = items.value.find((row) => row.id === id);
    if (
      !item ||
      saving.value ||
      !Number.isSafeInteger(quantity) ||
      quantity < item.reserved
    )
      return Promise.resolve(false);
    const delta = quantity - item.onHand;
    if (delta === 0) return Promise.resolve(true);
    return adjust([{ id, delta }], 'Inventory count adjustment');
  }

  function receive(entries: readonly StockReceiptEntry[]): Promise<boolean> {
    const valid =
      !saving.value &&
      entries.length > 0 &&
      new Set(entries.map((entry) => entry.id)).size === entries.length &&
      entries.every((entry) => {
        const item = items.value.find((row) => row.id === entry.id);
        return (
          item &&
          Number.isSafeInteger(entry.quantity) &&
          entry.quantity > 0 &&
          Number.isSafeInteger(item.onHand + entry.quantity)
        );
      });
    // Validate the whole batch before changing any balance.
    if (!valid) return Promise.resolve(false);
    return adjust(
      entries.map((entry) => ({ id: entry.id, delta: entry.quantity })),
      'Stock receipt',
    );
  }

  return { items, isLive, saving, error, setOnHand, receive };
}

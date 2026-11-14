import { computed, ref } from 'vue';
import { useAuth } from '@theparlor/auth';
import { ACCOUNT_STORAGE_PREFIX } from '../../../shared/lib/protected-state';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import {
  addRentalRefreshments,
  getBranchMenu,
  type CustomerReference,
  type PreOrderItemRequest,
} from '@theparlor/api-client';
import { httpStatus, problemMessage } from '../../../shared/lib/api-errors';
import { toFoodMenuItem } from '../data/menu';
import { createPantrySamples, type FoodDrinkStockItem } from './pantry.samples';
import { useCustomerReservationsStore } from '../data/reservations-store';

interface SnackReservation {
  id: string;
  tableId: string;
  customer?: CustomerReference;
  live?: boolean;
  branchId?: string;
  rentalId?: string;
}
interface Attempt {
  key: string;
  items: PreOrderItemRequest[];
}

function isAttempt(value: unknown): value is Attempt {
  if (
    !value ||
    typeof value !== 'object' ||
    !('key' in value) ||
    !('items' in value)
  )
    return false;
  if (
    typeof value.key !== 'string' ||
    !/^[0-9a-f-]{36}$/i.test(value.key) ||
    !Array.isArray(value.items)
  )
    return false;
  const ids = new Set<string>();
  return (
    value.items.length > 0 &&
    value.items.length <= 50 &&
    value.items.every((item: unknown) => {
      if (
        !item ||
        typeof item !== 'object' ||
        !('menuItemId' in item) ||
        !('quantity' in item)
      )
        return false;
      if (
        typeof item.menuItemId !== 'string' ||
        !item.menuItemId ||
        ids.has(item.menuItemId) ||
        typeof item.quantity !== 'number' ||
        !Number.isInteger(item.quantity) ||
        item.quantity < 1 ||
        item.quantity > 50
      )
        return false;
      ids.add(item.menuItemId);
      return true;
    })
  );
}

export function useWorkspacePantry() {
  const reservationsStore = useCustomerReservationsStore();
  const auth = useAuth();
  const queryClient = useQueryClient();
  const samples = ref(createPantrySamples());
  const staffSnackReservation = ref<SnackReservation | null>(null);
  const staffSnackQuantities = ref<Record<string, number>>({});
  const staffSnackError = ref('');
  const staffSnackSubmitting = ref(false);
  // Persist before sending so a lost response remains retry-safe after reload or navigation.
  const attempts = ref(new Map<string, Attempt>());
  const isLive = computed(() =>
    Boolean(
      staffSnackReservation.value?.live ||
      staffSnackReservation.value?.rentalId,
    ),
  );
  const branchId = computed(() => staffSnackReservation.value?.branchId ?? '');
  const rentalId = computed(() => staffSnackReservation.value?.rentalId ?? '');
  const scope = computed(
    () =>
      `${ACCOUNT_STORAGE_PREFIX}refreshments:${encodeURIComponent(auth.value.subject ?? '')}:${branchId.value}:${rentalId.value}`,
  );
  const staffSnackRecoveryBlocked = ref(false);
  const staffSnackUncertain = computed(() => attempts.value.has(scope.value));
  const menu = useQuery({
    queryKey: ['branch-menu', branchId],
    queryFn: ({ signal }) => getBranchMenu(branchId.value, undefined, signal),
    enabled: computed(() => isLive.value && Boolean(branchId.value)),
    staleTime: 0,
  });
  const staffSnackLoading = computed(
    () => isLive.value && menu.isPending.value,
  );
  const staffSnackMenuError = computed(
    () => isLive.value && menu.isError.value,
  );
  const branchStockItems = computed<FoodDrinkStockItem[]>(() => {
    if (!isLive.value) return samples.value;
    return (menu.data.value?.items ?? []).map(toFoodMenuItem).map((item) => ({
      id: item.id,
      name: item.name,
      nameTh: item.nameTh ?? '',
      category: item.category,
      price: item.price,
      costPrice: 0,
      unit: 'units',
      currentStock: item.available ?? 0,
      lowStockThreshold: 0,
    }));
  });

  function openAddSnacks(res: SnackReservation) {
    if (staffSnackSubmitting.value) return;
    staffSnackReservation.value = res;
    staffSnackRecoveryBlocked.value = false;
    staffSnackError.value = '';
    staffSnackQuantities.value = {};
    if (!isLive.value) return;
    try {
      if (!auth.value.subject) throw new Error('No operator session');
      const saved = localStorage.getItem(scope.value);
      if (saved) {
        const parsed: unknown = JSON.parse(saved);
        if (!isAttempt(parsed)) throw new Error('Invalid saved order');
        attempts.value.set(scope.value, parsed);
      }
      const attempt = attempts.value.get(scope.value);
      staffSnackQuantities.value = Object.fromEntries(
        (attempt?.items ?? []).map((item) => [item.menuItemId, item.quantity]),
      );
      if (attempt)
        staffSnackError.value =
          'The previous order has no confirmed answer. Retry to confirm it without adding it twice.';
    } catch {
      staffSnackRecoveryBlocked.value = true;
      staffSnackError.value =
        'Could not recover pending orders. Check the table bill and allow site storage before ordering.';
    }
  }

  function getStaffSnackQty(itemId: string): number {
    return staffSnackQuantities.value[itemId] || 0;
  }

  function adjustStaffSnackQty(item: FoodDrinkStockItem, delta: number) {
    if (
      staffSnackSubmitting.value ||
      staffSnackUncertain.value ||
      staffSnackRecoveryBlocked.value
    )
      return;
    const next = Math.max(0, getStaffSnackQty(item.id) + delta);
    if (!Number.isInteger(next) || next > Math.min(50, item.currentStock))
      return;
    if (next === 0) delete staffSnackQuantities.value[item.id];
    else staffSnackQuantities.value[item.id] = next;
  }

  const staffSnackSelectedItems = computed(() =>
    Object.entries(staffSnackQuantities.value)
      .filter(([, qty]) => qty > 0)
      .map(([id, qty]) => {
        const item = branchStockItems.value.find((item) => item.id === id);
        return {
          item: {
            id,
            name: item?.name ?? id,
            nameTh: item?.nameTh,
            price: item?.price ?? 0,
          },
          quantity: qty,
        };
      }),
  );
  const staffSnackSubtotal = computed(
    () =>
      staffSnackSelectedItems.value.reduce(
        (sum, line) => sum + Math.round(line.item.price * 100) * line.quantity,
        0,
      ) / 100,
  );

  async function submitStaffSnackOrder() {
    const reservation = staffSnackReservation.value;
    if (
      !reservation ||
      staffSnackSelectedItems.value.length === 0 ||
      staffSnackSubmitting.value ||
      staffSnackRecoveryBlocked.value
    )
      return;
    staffSnackError.value = '';
    if (!isLive.value) {
      for (const line of staffSnackSelectedItems.value) {
        const item = samples.value.find((item) => item.id === line.item.id);
        if (item) item.currentStock -= line.quantity;
      }
      reservationsStore.addStaffSnackOrder(
        reservation.id,
        staffSnackSelectedItems.value,
        'Added by staff at counter',
      );
      staffSnackReservation.value = null;
      return;
    }
    if (!reservation.branchId || !reservation.rentalId) {
      staffSnackError.value = 'Refresh the session before adding refreshments.';
      return;
    }
    const requestScope = scope.value;
    const attempt = attempts.value.get(requestScope) ?? {
      key: crypto.randomUUID(),
      items: staffSnackSelectedItems.value.map((line) => ({
        menuItemId: line.item.id,
        quantity: line.quantity,
      })),
    };
    try {
      localStorage.setItem(requestScope, JSON.stringify(attempt));
    } catch {
      staffSnackError.value =
        'Allow site storage before adding refreshments so an interrupted order can be retried safely.';
      return;
    }
    attempts.value.set(requestScope, attempt);
    staffSnackSubmitting.value = true;
    try {
      const rental = await addRentalRefreshments(
        reservation.branchId,
        reservation.rentalId,
        { items: attempt.items },
        {
          headers: { 'Idempotency-Key': attempt.key },
        },
      );
      localStorage.removeItem(requestScope);
      attempts.value.delete(requestScope);
      queryClient.setQueryData(
        ['rental', reservation.branchId, reservation.rentalId],
        rental,
      );
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ['branch-menu', reservation.branchId],
        }),
        queryClient.invalidateQueries({
          queryKey: ['listBranchRentals', reservation.branchId],
        }),
      ]);
      staffSnackReservation.value = null;
    } catch (error) {
      const status = httpStatus(error);
      if (status !== undefined && status >= 400 && status < 500) {
        try {
          localStorage.removeItem(requestScope);
          attempts.value.delete(requestScope);
        } catch {
          // Keep the same rejected body/key until storage recovers; no new request is invented.
        }
        staffSnackError.value =
          problemMessage(error) ??
          'The order was rejected. Check the session and stock, then retry.';
        void menu.refetch();
      } else {
        staffSnackError.value =
          'No confirmed answer from the server. Retry this order; it will not be added twice.';
      }
    } finally {
      staffSnackSubmitting.value = false;
    }
  }
  return {
    reservationsStore,
    branchStockItems,
    staffSnackReservation,
    staffSnackQuantities,
    staffSnackError,
    staffSnackSubmitting,
    staffSnackLoading,
    staffSnackMenuError,
    staffSnackUncertain,
    staffSnackRecoveryBlocked,
    openAddSnacks,
    getStaffSnackQty,
    adjustStaffSnackQty,
    staffSnackSelectedItems,
    staffSnackSubtotal,
    submitStaffSnackOrder,
  };
}

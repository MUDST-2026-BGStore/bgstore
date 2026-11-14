import {
  computed,
  getCurrentScope,
  onScopeDispose,
  reactive,
  ref,
  watch,
} from 'vue';
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query';
import {
  listBranches,
  listBranchReservations,
  listBranchRentals,
  listTables,
  checkInReservation,
  cancelReservation,
  markReservationNoShow,
  type Branch,
  type Reservation,
  type ReservationPreOrder,
  type Table,
} from '@theparlor/api-client';
import {
  useCustomerReservationsStore,
  type CustomerReservationPreOrder,
} from '../data/reservations-store';
import {
  billableHoursFor,
  computeSessionTiming,
  formatStoreTime,
  serverClockOffsetMs,
} from '../data/session-timing';
import { httpStatus, problemMessage } from '../../../shared/lib/api-errors';
import { useWalkInSeating } from './useWalkInSeating';

const baht = (amountMinor: number) => Math.round(amountMinor / 100);

/** A server pre-order in the bill's shape; it is paid once its card is captured. */
function toBillPreOrder(
  preOrder: ReservationPreOrder | undefined,
): CustomerReservationPreOrder | undefined {
  if (!preOrder) return undefined;
  return {
    items: preOrder.items.map((line) => ({
      item: {
        id: line.menuItemId,
        name: line.name,
        price: baht(line.unitPrice.amountMinor),
      },
      quantity: line.quantity,
    })),
    subtotal: baht(preOrder.subtotal.amountMinor),
    discountAmount: baht(preOrder.discount.amountMinor),
    discountedTotal: baht(preOrder.total.amountMinor),
    paid: preOrder.payment.status === 'CAPTURED',
    paymentMethod: preOrder.payment.method.toLowerCase(),
  };
}

export interface FloorTable {
  /** The table's canonical ID: the API's UUID, or a sample table's ID. */
  id: string;
  /** Display name, e.g. "Table 3". Never used to identify a table. */
  label: string;
  /** Short form of the label for compact columns. */
  number: number;
  seats: number;
  rate: number;
}

// TODO(workspace-floor-api): The sample floor stands in only for branches without
// tables on the server. Its IDs are the ones the local sample sessions reference.
const SAMPLE_FLOOR: readonly FloorTable[] = [
  { id: 't-siam-1', label: 'Table 1', number: 1, seats: 2, rate: 150 },
  { id: 't-siam-2', label: 'Table 2', number: 2, seats: 4, rate: 200 },
  { id: 't-siam-3', label: 'Table 3', number: 3, seats: 4, rate: 200 },
  { id: 't-siam-4', label: 'Table 4', number: 4, seats: 4, rate: 200 },
  { id: 't-siam-5', label: 'Table 5', number: 5, seats: 6, rate: 300 },
  { id: 't-siam-6', label: 'Table 6', number: 6, seats: 6, rate: 300 },
];

export function floorTableOf(table: Table, index: number): FloorTable {
  const number = Number(/\d+/.exec(table.label)?.[0] ?? index + 1);
  return {
    id: table.id,
    label: table.label,
    number: number > 0 ? number : index + 1,
    seats: table.capacity,
    rate: baht(table.hourlyRate.amountMinor),
  };
}

export function useWorkspaceReservations() {
  const queryClient = useQueryClient();
  const reservationsStore = useCustomerReservationsStore();
  // Branch
  const selectedBranchId = ref<string>('');

  // Query branches
  const branchesQuery = useQuery({
    queryKey: ['branches'],
    queryFn: ({ signal }) => listBranches({}, undefined, signal),
  });
  const branches = computed<Branch[]>(
    () => branchesQuery.data.value?.items ?? [],
  );

  watch(
    branches,
    (list) => {
      if (list.length > 0 && !selectedBranchId.value) {
        selectedBranchId.value = list[0].id;
      }
    },
    { immediate: true },
  );

  const selectedBranch = computed(() =>
    branches.value.find((b) => b.id === selectedBranchId.value),
  );

  // Query branch reservations
  const reservationsQuery = useQuery({
    queryKey: ['listBranchReservations', selectedBranchId],
    queryFn: ({ signal }) =>
      listBranchReservations(selectedBranchId.value, {}, undefined, signal),
    enabled: computed(() => !!selectedBranchId.value),
  });

  const tablesQuery = useQuery({
    queryKey: ['listBranchTables', selectedBranchId],
    queryFn: ({ signal }) =>
      listTables(selectedBranchId.value, { limit: 100 }, undefined, signal),
    enabled: computed(() => !!selectedBranchId.value),
  });

  const rentalsQuery = useQuery({
    queryKey: ['listBranchRentals', selectedBranchId],
    queryFn: ({ signal }) =>
      listBranchRentals(
        selectedBranchId.value,
        { limit: 100 },
        undefined,
        signal,
      ),
    enabled: computed(() => !!selectedBranchId.value),
  });

  // A branch without server tables keeps the sample floor and its sample visits.
  // Sample visits never share a floor with real tables: their IDs would not match,
  // and their made-up occupancy must not shadow real guests.
  const usingSampleFloor = computed(
    () =>
      tablesQuery.isSuccess.value &&
      (tablesQuery.data.value?.items ?? []).length === 0,
  );
  // One array instance, so consumers keep plain array access as the source changes.
  const branchFloorTables = reactive<FloorTable[]>([]);
  watch(
    [() => tablesQuery.data.value, usingSampleFloor],
    ([page, sample]) => {
      const next = sample
        ? SAMPLE_FLOOR.map((table) => ({ ...table }))
        : (page?.items ?? []).map(floorTableOf);
      branchFloorTables.splice(0, branchFloorTables.length, ...next);
    },
    { immediate: true },
  );

  /** The display label of a table by canonical ID, or a dash when it is not on this floor. */
  function tableLabel(tableId: string | null | undefined): string {
    return branchFloorTables.find((t) => t.id === tableId)?.label ?? '—';
  }

  // The server decides who operates a branch. Until it has answered this branch's
  // reservation list successfully, the workspace shows no records at all, including
  // the local sample visits merged below.
  const branchAccessVerified = computed(
    () => !!selectedBranchId.value && reservationsQuery.isSuccess.value,
  );
  const branchAccessDenied = computed(() => {
    const status = httpStatus(reservationsQuery.error.value);
    return status === 401 || status === 403;
  });

  const currentTimeStr = computed(() => {
    const updatedAt = reservationsQuery.dataUpdatedAt.value;
    return updatedAt
      ? new Date(updatedAt).toLocaleTimeString([], {
          hour: '2-digit',
          minute: '2-digit',
        })
      : '';
  });

  const allReservations = computed<any[]>(() => {
    if (!branchAccessVerified.value) return [];
    const rentals = rentalsQuery.data.value?.items ?? [];
    const apiItems = (reservationsQuery.data.value?.items ?? []).map(
      (item) => ({
        ...item,
        live: true,
        rentalId:
          item.rentalId ??
          rentals.find((rental) => rental.reservationId === item.id)?.id,
        clientType: item.source === 'WALK_IN' ? 'WALK_IN' : 'RESERVED',
        hourlyRate: item.tableHourlyRate
          ? baht(item.tableHourlyRate.amountMinor)
          : undefined,
      }),
    );

    if (!usingSampleFloor.value) return apiItems;

    const localItems = reservationsStore.reservations.value.filter(
      (r) =>
        !selectedBranchId.value ||
        r.branchId === selectedBranchId.value ||
        r.branchName.toLowerCase() === selectedBranch.value?.name.toLowerCase(),
    );

    const combined = [...apiItems];
    for (const loc of localItems) {
      if (!combined.some((c) => c.id === loc.id)) {
        combined.push({
          id: loc.id,
          branchId: loc.branchId,
          tableId: loc.tableId,
          startsAt: loc.startsAt,
          endsAt: loc.endsAt,
          checkedInAt: loc.checkedInAt,
          completedAt: loc.completedAt,
          partySize: loc.partySize,
          status: loc.status,
          customer: {
            displayName: loc.customerName,
            phone: loc.customerPhone,
          } as any,
          notes: loc.notes,
          clientType: loc.clientType,
          source: (loc.clientType === 'WALK_IN' ? 'WALK_IN' : 'ONLINE') as any,
          hourlyRate: loc.hourlyRate,
        } as any);
      }
    }

    return combined;
  });

  // Category splits
  const activeFloorReservations = computed(() => {
    return allReservations.value.filter((r) => r.status === 'CHECKED_IN');
  });

  const upcomingReservations = computed(() => {
    return allReservations.value
      .filter((r) => r.status === 'CONFIRMED' || r.status === 'PENDING')
      .sort(
        (a, b) =>
          new Date(a.startsAt).getTime() - new Date(b.startsAt).getTime(),
      );
  });

  // Visits are matched to tables by canonical ID only. Labels and ID fragments
  // are not identities: "t-siam-1" is not table "1", and a label is display text.
  function getTableStatus(table: FloorTable) {
    const occupied = activeFloorReservations.value.find(
      (r) => r.tableId === table.id,
    );
    if (occupied) {
      return {
        state: 'OCCUPIED' as const,
        reservation: occupied,
      };
    }

    const reserved = upcomingReservations.value.find(
      (r) => r.tableId === table.id,
    );
    if (reserved) {
      return {
        state: 'RESERVED' as const,
        reservation: reserved,
      };
    }

    return {
      state: 'FREE' as const,
      reservation: null,
    };
  }

  const occupiedTablesCount = computed(() => {
    return branchFloorTables.filter(
      (t) => getTableStatus(t).state === 'OCCUPIED',
    ).length;
  });

  const freeTablesCount = computed(() => {
    return branchFloorTables.filter(
      (table) => getTableStatus(table).state === 'FREE',
    ).length;
  });

  // Floor occupancy breakdown (FortiSIEM-style counter rail)
  const floorOccupancy = computed(() => {
    const reserved = branchFloorTables.filter(
      (t) => getTableStatus(t).state === 'RESERVED',
    ).length;
    const occupied = occupiedTablesCount.value;
    const free = freeTablesCount.value;
    const total = branchFloorTables.length || 1;
    return [
      {
        state: 'In play',
        count: occupied,
        cls: 'occupied',
        pct: Math.round((occupied / total) * 100),
      },
      {
        state: 'Reserved',
        count: reserved,
        cls: 'reserved',
        pct: Math.round((reserved / total) * 100),
      },
      {
        state: 'Free',
        count: free,
        cls: 'free',
        pct: Math.round((free / total) * 100),
      },
    ];
  });

  // "Needs You Now" alerts (Most urgent first)
  const needsYouItems = computed(() => {
    const alerts: any[] = [];
    for (const r of activeFloorReservations.value) {
      const elapsed = getElapsedMinutes(r);
      alerts.push({
        id: r.id,
        type: 'IN_PLAY',
        sev: 'med',
        title: `${tableLabel(r.tableId)} in play for ${elapsed} min`,
        subtitle: `${r.partySize} guests · ${getCustomerName(r.customer)} · Table charge: ฿${calculateCurrentPlayFee(r)}`,
        reservation: r,
      });
    }

    for (const r of upcomingReservations.value) {
      const startMs = new Date(r.startsAt).getTime();
      const diffMins = Math.round((Date.now() - startMs) / 60000);
      if (diffMins > 0) {
        alerts.push({
          id: r.id,
          type: 'LATE',
          sev: 'high',
          title: `Customer late ${diffMins}m: ${getCustomerName(r.customer)} (${tableLabel(r.tableId)})`,
          subtitle: `Scheduled ${formatTime(r.startsAt)}`,
          reservation: r,
        });
      }
    }

    return alerts;
  });

  // Helper functions
  function formatTime(iso: string): string {
    return formatStoreTime(iso);
  }

  // One-second display clock on the server's time: the offset is measured when the
  // rentals were last fetched, so a wrong device clock does not skew play time.
  const clockTick = ref(Date.now());
  if (getCurrentScope()) {
    const timer = setInterval(() => (clockTick.value = Date.now()), 1000);
    onScopeDispose(() => clearInterval(timer));
  }
  const serverNowMs = computed(() => {
    const serverNow = rentalsQuery.data.value?.items[0]?.serverNow;
    const offset = serverNow
      ? serverClockOffsetMs(serverNow, rentalsQuery.dataUpdatedAt.value)
      : 0;
    return clockTick.value + offset;
  });

  /** Timing for a live session; null for sample data, which keeps its local estimate. */
  function getSessionTiming(res: any) {
    if (!res.live || !res.checkedInAt) return null;
    return computeSessionTiming({
      checkedInAt: res.checkedInAt,
      scheduledEnd: res.endsAt,
      completedAt: res.completedAt,
      serverNowMs: serverNowMs.value,
    });
  }

  function getElapsedMinutes(res: any): number {
    const timing = getSessionTiming(res);
    if (timing) return Math.floor(timing.elapsedSeconds / 60);
    const start = new Date(res.checkedInAt || res.startsAt).getTime();
    const end = res.completedAt
      ? new Date(res.completedAt).getTime()
      : Date.now();
    return Math.max(1, Math.round((end - start) / 60000));
  }

  function calculateCurrentPlayFee(res: any): number {
    const mins = getElapsedMinutes(res);
    const rate = res.hourlyRate ?? 0;
    // The server bills started hours, minimum one; samples keep a running estimate.
    const timing = getSessionTiming(res);
    if (timing) return timing.billableHours * rate;
    if (res.live) return billableHoursFor(mins * 60) * rate;
    return Math.round((mins / 60) * rate);
  }

  function getCustomerName(c: any): string {
    if (!c) return 'Guest';
    return c.displayName || c.name || c.subject || 'Guest';
  }

  function getCustomerPhone(c: any): string {
    return c?.phone || '';
  }

  // Check in
  const actionInProgress = ref<string | null>(null);
  const actionError = ref<string>('');

  const checkInMutation = useMutation({
    mutationFn: async (resId: string) => {
      return checkInReservation(selectedBranchId.value, resId);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ['listBranchReservations'],
      });
      void queryClient.invalidateQueries({ queryKey: ['listBranchRentals'] });
    },
    onError: (error) => {
      if (httpStatus(error) === 409) {
        const reason = problemMessage(error);
        actionError.value = reason
          ? `Check-in conflict: ${reason}`
          : 'Check-in conflict. Refresh the reservation and check table and game availability.';
      } else {
        actionError.value = 'Check-in failed. Refresh and retry.';
      }
    },
  });

  // A visit lives either on the server or in the local sample store, never both.
  // Server visits change only through the API; sample visits only locally.
  function isServerVisit(id: string): boolean {
    return (reservationsQuery.data.value?.items ?? []).some((r) => r.id === id);
  }

  async function handleCheckIn(res: Reservation) {
    actionError.value = '';
    actionInProgress.value = res.id;
    try {
      if (isServerVisit(res.id)) await checkInMutation.mutateAsync(res.id);
      else reservationsStore.checkInReservation(res.id);
    } catch {
      // onError reports it; the floor keeps the server's state.
    } finally {
      actionInProgress.value = null;
    }
  }

  // Cancel
  const showCancelModal = ref(false);
  const cancelTargetRes = ref<any>(null);
  const cancelReason = ref('Customer called to cancel');

  function openCancel(res: any) {
    cancelTargetRes.value = res;
    cancelReason.value = 'Customer called to cancel';
    showCancelModal.value = true;
  }

  const cancelMutation = useMutation({
    mutationFn: async ({
      resId,
      reason,
    }: {
      resId: string;
      reason: string;
    }) => {
      return cancelReservation(selectedBranchId.value, resId, { reason });
    },
    onSuccess: () => {
      showCancelModal.value = false;
      cancelTargetRes.value = null;
      void queryClient.invalidateQueries({
        queryKey: ['listBranchReservations'],
      });
    },
    onError: () => {
      actionError.value = 'Cancellation failed. Refresh and retry.';
    },
  });

  async function submitCancel() {
    if (!cancelTargetRes.value) return;
    actionError.value = '';
    const target = cancelTargetRes.value.id;
    if (!isServerVisit(target)) {
      reservationsStore.cancelReservation(target, cancelReason.value);
      showCancelModal.value = false;
      cancelTargetRes.value = null;
      return;
    }
    await cancelMutation
      .mutateAsync({ resId: target, reason: cancelReason.value })
      .catch(() => undefined);
  }

  // No-show
  const showNoShowModal = ref(false);
  const noShowTargetRes = ref<any>(null);
  const noShowReason = ref('Customer did not arrive');

  function openNoShow(res: any) {
    noShowTargetRes.value = res;
    noShowReason.value = 'Customer did not arrive';
    showNoShowModal.value = true;
  }

  const noShowMutation = useMutation({
    mutationFn: async ({
      resId,
      reason,
    }: {
      resId: string;
      reason: string;
    }) => {
      return markReservationNoShow(selectedBranchId.value, resId, { reason });
    },
    onSuccess: () => {
      showNoShowModal.value = false;
      noShowTargetRes.value = null;
      void queryClient.invalidateQueries({
        queryKey: ['listBranchReservations'],
      });
    },
    onError: () => {
      actionError.value = 'No-show failed. Refresh and retry.';
    },
  });

  async function submitNoShow() {
    if (!noShowTargetRes.value) return;
    actionError.value = '';
    await noShowMutation.mutateAsync({
      resId: noShowTargetRes.value.id,
      reason: noShowReason.value,
    });
  }

  // Walk-ins are seated by the server only; see useWalkInSeating.
  const walkIn = useWalkInSeating(selectedBranchId);

  function openWalkInForTable(table?: FloorTable) {
    walkIn.openFor(table ? { id: table.id, seats: table.seats } : undefined);
  }

  // Staff Table Bill Modal
  const staffBillReservation = ref<any>(null);

  function openCheckBill(res: any) {
    const found = reservationsStore.reservations.value.find(
      (r) => r.id === res.id,
    );
    if (res.live) {
      staffBillReservation.value = {
        id: res.id,
        branchId: res.branchId,
        branchName: selectedBranch.value?.name ?? '',
        tableId: res.tableId,
        tableLabel:
          branchFloorTables.find((table) => table.id === res.tableId)?.label ??
          'Table',
        startsAt: res.startsAt,
        endsAt: res.endsAt,
        checkedInAt: res.checkedInAt,
        completedAt: res.completedAt,
        rentalId: res.rentalId,
        partySize: res.partySize,
        status: res.status,
        customerName: getCustomerName(res.customer),
        customerPhone: getCustomerPhone(res.customer) || undefined,
        clientType: res.clientType,
        hourlyRate: res.hourlyRate,
        preOrder: toBillPreOrder(res.preOrder),
        // TableBillModal reads persisted refreshments from this rental.
        inPlayOrders: [],
      };
    } else if (found) {
      staffBillReservation.value = found;
    } else {
      staffBillReservation.value = {
        id: res.id,
        branchId: selectedBranchId.value,
        branchName: selectedBranch.value?.name ?? 'Siam',
        tableId: res.tableId,
        tableLabel: tableLabel(res.tableId),
        startsAt: res.startsAt,
        endsAt: res.endsAt,
        checkedInAt: res.checkedInAt || res.startsAt,
        partySize: res.partySize,
        status: res.status,
        customerName: getCustomerName(res.customer),
        customerPhone: getCustomerPhone(res.customer) || undefined,
        hourlyRate: res.hourlyRate || 200,
        inPlayOrders: [],
      };
    }
  }

  function refreshAll() {
    for (const key of [
      'listBranchReservations',
      'listBranchRentals',
      'listBranchTables',
      'workspace',
    ])
      void queryClient.invalidateQueries({ queryKey: [key] });
  }
  return {
    queryClient,
    reservationsStore,
    selectedBranchId,
    currentTimeStr,
    branchesQuery,
    branches,
    selectedBranch,
    reservationsQuery,
    tablesQuery,
    rentalsQuery,
    usingSampleFloor,
    tableLabel,
    branchAccessVerified,
    branchAccessDenied,
    allReservations,
    activeFloorReservations,
    upcomingReservations,
    branchFloorTables,
    getTableStatus,
    occupiedTablesCount,
    freeTablesCount,
    floorOccupancy,
    needsYouItems,
    formatTime,
    getElapsedMinutes,
    getSessionTiming,
    calculateCurrentPlayFee,
    getCustomerName,
    getCustomerPhone,
    actionInProgress,
    actionError,
    checkInMutation,
    handleCheckIn,
    showCancelModal,
    cancelTargetRes,
    cancelReason,
    openCancel,
    cancelMutation,
    submitCancel,
    showNoShowModal,
    noShowTargetRes,
    noShowReason,
    openNoShow,
    noShowMutation,
    submitNoShow,
    walkIn,
    showWalkInModal: walkIn.open,
    openWalkInForTable,
    staffBillReservation,
    openCheckBill,
    refreshAll,
  };
}

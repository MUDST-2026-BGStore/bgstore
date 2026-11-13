import { computed, reactive, ref, watch } from 'vue';
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query';
import {
  listBranches,
  listBranchReservations,
  listTables,
  checkInReservation,
  cancelReservation,
  markReservationNoShow,
  type Branch,
  type Reservation,
  type Table,
} from '@theparlor/api-client';
import { useCustomerReservationsStore } from '../data/reservations-store';
import { useWalkInSeating } from './useWalkInSeating';

export interface FloorTable {
  /** The table's canonical ID: the API's UUID, or a sample table's ID. */
  id: string;
  /** Display name, e.g. "Table 3". Never used to identify a table. */
  label: string;
  /** Short form of the label for compact columns. */
  number: string;
  seats: number;
  rate: number;
}

// TODO(workspace-floor-api): The sample floor stands in only for branches without
// tables on the server. Its IDs are the ones the local sample sessions reference.
const SAMPLE_FLOOR: readonly FloorTable[] = [
  { id: 't-siam-1', label: 'Table 1', number: '1', seats: 2, rate: 150 },
  { id: 't-siam-2', label: 'Table 2', number: '2', seats: 4, rate: 200 },
  { id: 't-siam-3', label: 'Table 3', number: '3', seats: 4, rate: 200 },
  { id: 't-siam-4', label: 'Table 4', number: '4', seats: 4, rate: 200 },
  { id: 't-siam-5', label: 'Table 5', number: '5', seats: 6, rate: 300 },
  { id: 't-siam-6', label: 'Table 6', number: '6', seats: 6, rate: 300 },
];

export function floorTableOf(table: Table): FloorTable {
  return {
    id: table.id,
    label: table.label,
    number: table.label.replace(/^table\s+/i, ''),
    seats: table.capacity,
    rate: table.hourlyRate.amountMinor / 100,
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
    queryKey: ['branchTables', selectedBranchId],
    queryFn: ({ signal }) =>
      listTables(selectedBranchId.value, { limit: 100 }, undefined, signal),
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

  const currentTimeStr = computed(() => {
    const updatedAt = reservationsQuery.dataUpdatedAt.value;
    return updatedAt
      ? new Date(updatedAt).toLocaleTimeString([], {
          hour: '2-digit',
          minute: '2-digit',
        })
      : '';
  });

  // Auto 15-min No-Show Mutation
  const autoNoShowMutation = useMutation({
    mutationFn: async ({ resId }: { resId: string }) => {
      return markReservationNoShow(selectedBranchId.value, resId, {
        reason:
          'Automatic No-Show: Customer did not arrive within 15 minutes of reservation start time.',
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ['listBranchReservations'],
      });
    },
  });

  const allReservations = computed<any[]>(() => {
    const now = Date.now();
    const apiItems = (reservationsQuery.data.value?.items ?? []).map((r) => {
      if (r.status === 'CONFIRMED' && !r.checkedInAt) {
        const startMs = new Date(r.startsAt).getTime();
        if (now - startMs > 15 * 60 * 1000) {
          void autoNoShowMutation.mutateAsync({ resId: r.id });
          return {
            ...r,
            status: 'NO_SHOW',
            notes: r.notes
              ? `${r.notes}\n[System]: Auto-marked No-Show after 15 min.`
              : '[System]: Auto-marked No-Show after 15 min.',
          };
        }
      }
      return r;
    });

    const localItems = usingSampleFloor.value
      ? reservationsStore.reservations.value.filter(
          (r) =>
            !selectedBranchId.value ||
            r.branchId === selectedBranchId.value ||
            r.branchName.toLowerCase() ===
              selectedBranch.value?.name.toLowerCase(),
        )
      : [];

    const combined = [...apiItems];
    for (const loc of localItems) {
      if (!combined.some((c) => c.id === loc.id)) {
        let status = loc.status;
        if (status === 'CONFIRMED' && !loc.checkedInAt) {
          const startMs = new Date(loc.startsAt).getTime();
          if (now - startMs > 15 * 60 * 1000) {
            status = 'NO_SHOW';
          }
        }

        combined.push({
          id: loc.id,
          branchId: loc.branchId,
          tableId: loc.tableId,
          startsAt: loc.startsAt,
          endsAt: loc.endsAt,
          checkedInAt: loc.checkedInAt,
          completedAt: loc.completedAt,
          partySize: loc.partySize,
          status,
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
          subtitle: `Scheduled ${formatTime(r.startsAt)} · Auto-released if 15m late`,
          reservation: r,
        });
      }
    }

    return alerts;
  });

  // Helper functions
  function formatTime(iso: string): string {
    if (!iso) return '';
    const d = new Date(iso);
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  function getElapsedMinutes(res: any): number {
    const start = new Date(res.checkedInAt || res.startsAt).getTime();
    const end = res.completedAt
      ? new Date(res.completedAt).getTime()
      : Date.now();
    return Math.max(1, Math.round((end - start) / 60000));
  }

  function calculateCurrentPlayFee(res: any): number {
    const mins = getElapsedMinutes(res);
    const rate = res.hourlyRate || 200;
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
    },
    onError: () => {
      actionError.value = 'Check-in failed. Refresh and retry.';
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
    if (found) {
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
    void queryClient.invalidateQueries({
      queryKey: ['listBranchReservations'],
    });
    void queryClient.invalidateQueries({ queryKey: ['branchTables'] });
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
    autoNoShowMutation,
    allReservations,
    activeFloorReservations,
    upcomingReservations,
    branchFloorTables,
    tablesQuery,
    usingSampleFloor,
    tableLabel,
    getTableStatus,
    occupiedTablesCount,
    freeTablesCount,
    floorOccupancy,
    needsYouItems,
    formatTime,
    getElapsedMinutes,
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
    walkIn,
    showWalkInModal: walkIn.open,
    openWalkInForTable,
    staffBillReservation,
    openCheckBill,
    refreshAll,
  };
}

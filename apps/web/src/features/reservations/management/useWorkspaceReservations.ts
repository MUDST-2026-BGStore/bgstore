import { computed, ref, watch } from 'vue';
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query';
import {
  listBranches,
  listBranchReservations,
  checkInReservation,
  cancelReservation,
  markReservationNoShow,
  createStaffReservation,
  type Branch,
  type Reservation,
} from '@theparlor/api-client';
import { useCustomerReservationsStore } from '../data/reservations-store';

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

    const localItems = reservationsStore.reservations.value.filter(
      (r) =>
        !selectedBranchId.value ||
        r.branchId === selectedBranchId.value ||
        r.branchName.toLowerCase() === selectedBranch.value?.name.toLowerCase(),
    );

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

  // Tables Floor Plan Roster
  interface FloorTable {
    id: string;
    number: number;
    seats: number;
    rate: number;
  }

  const branchFloorTables: FloorTable[] = [
    { id: '1', number: 1, seats: 2, rate: 150 },
    { id: '2', number: 2, seats: 4, rate: 200 },
    { id: '3', number: 3, seats: 4, rate: 200 },
    { id: '4', number: 4, seats: 4, rate: 200 },
    { id: '5', number: 5, seats: 6, rate: 300 },
    { id: '6', number: 6, seats: 6, rate: 300 },
  ];

  function getTableStatus(table: FloorTable) {
    const occupied = activeFloorReservations.value.find((r) => {
      const tId = String(r.tableId || '');
      return (
        tId === table.id ||
        tId.endsWith(`-${table.id}`) ||
        tId.includes(`Table ${table.number}`)
      );
    });
    if (occupied) {
      return {
        state: 'OCCUPIED' as const,
        reservation: occupied,
      };
    }

    const reserved = upcomingReservations.value.find((r) => {
      const tId = String(r.tableId || '');
      return (
        tId === table.id ||
        tId.endsWith(`-${table.id}`) ||
        tId.includes(`Table ${table.number}`)
      );
    });
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
        title: `Table ${r.tableId} in play for ${elapsed} min`,
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
          title: `Customer late ${diffMins}m: ${getCustomerName(r.customer)} (Table ${r.tableId})`,
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

  async function handleCheckIn(res: Reservation) {
    actionError.value = '';
    actionInProgress.value = res.id;
    try {
      reservationsStore.checkInReservation(res.id);
      await checkInMutation.mutateAsync(res.id);
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
    reservationsStore.cancelReservation(
      cancelTargetRes.value.id,
      cancelReason.value,
    );
    await cancelMutation.mutateAsync({
      resId: cancelTargetRes.value.id,
      reason: cancelReason.value,
    });
  }

  // Walk-in Modal (Instant seating - NO pre-order snacks!)
  const showWalkInModal = ref(false);
  const walkInTableId = ref('1');
  const walkInPartySize = ref(2);
  const walkInDuration = ref(2);
  const walkInGuestName = ref('');
  const walkInPhone = ref('');
  const walkInError = ref('');

  function openWalkInForTable(table?: FloorTable) {
    if (table) {
      walkInTableId.value = table.id;
      walkInPartySize.value = table.seats;
    }
    walkInGuestName.value = '';
    walkInPhone.value = '';
    walkInError.value = '';
    showWalkInModal.value = true;
  }

  const staffCreateMutation = useMutation({
    mutationFn: async (payload: any) => {
      return createStaffReservation(selectedBranchId.value, payload);
    },
    onSuccess: () => {
      showWalkInModal.value = false;
      void queryClient.invalidateQueries({
        queryKey: ['listBranchReservations'],
      });
    },
    onError: () => {
      walkInError.value = 'Seating failed. Refresh and retry.';
    },
  });

  async function handleStaffWalkInSubmit() {
    walkInError.value = '';
    const matchedTable =
      branchFloorTables.find((t) => t.id === walkInTableId.value) ||
      branchFloorTables[0];
    const startsAtIso = new Date().toISOString();
    const endsAtIso = new Date(
      Date.now() + walkInDuration.value * 3600000,
    ).toISOString();

    reservationsStore.startWalkInSession({
      branchId: selectedBranchId.value,
      branchName: selectedBranch.value?.name ?? 'Siam',
      tableId: matchedTable.id,
      tableLabel: `Table ${matchedTable.number}`,
      partySize: walkInPartySize.value,
      customerName: walkInGuestName.value || 'Walk-in Guests',
      customerPhone: walkInPhone.value || undefined,
      hourlyRate: matchedTable.rate,
      durationHours: walkInDuration.value,
    });

    try {
      const payload = {
        source: 'WALK_IN',
        tableId: matchedTable.id,
        startsAt: startsAtIso,
        endsAt: endsAtIso,
        partySize: walkInPartySize.value,
        customer: {
          type: 'GUEST',
          name: walkInGuestName.value || 'Walk-in Guests',
          phone: walkInPhone.value || undefined,
        },
        games: [],
        notes: 'Counter Walk-in Session',
      };
      await staffCreateMutation.mutateAsync(payload);
    } catch {
      // local store maintains the session
    }

    showWalkInModal.value = false;
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
        tableLabel: `Table ${res.tableId}`,
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
    void queryClient.invalidateQueries({
      queryKey: ['workspace'],
    });
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
    showWalkInModal,
    walkInTableId,
    walkInPartySize,
    walkInDuration,
    walkInGuestName,
    walkInPhone,
    walkInError,
    openWalkInForTable,
    staffCreateMutation,
    handleStaffWalkInSubmit,
    staffBillReservation,
    openCheckBill,
    refreshAll,
  };
}

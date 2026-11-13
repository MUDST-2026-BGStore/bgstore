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
  createStaffReservation,
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
import { httpStatus } from '../../../shared/lib/api-errors';

const SESSION_POLL_MS = 15_000;
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

interface FloorTable {
  id: string;
  number: number;
  seats: number;
  rate: number;
  label?: string;
}

// Sample floor for branches whose tables are not set up on the server yet.
const sampleFloorTables = (): FloorTable[] => [
  { id: '1', number: 1, seats: 2, rate: 150 },
  { id: '2', number: 2, seats: 4, rate: 200 },
  { id: '3', number: 3, seats: 4, rate: 200 },
  { id: '4', number: 4, seats: 4, rate: 200 },
  { id: '5', number: 5, seats: 6, rate: 300 },
  { id: '6', number: 6, seats: 6, rate: 300 },
];

function toFloorTable(table: Table, index: number): FloorTable {
  const number = Number(/\d+/.exec(table.label)?.[0] ?? index + 1);
  return {
    id: table.id,
    number: number > 0 ? number : index + 1,
    seats: table.capacity,
    rate: baht(table.hourlyRate.amountMinor),
    label: table.label,
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
    // The server closes sessions at their booked end; poll so the floor follows it.
    refetchInterval: SESSION_POLL_MS,
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
    // The server closes sessions at their booked end; poll so the floor follows it.
    refetchInterval: SESSION_POLL_MS,
  });

  // Tables Floor Plan Roster. A branch with server tables runs entirely on server
  // records; otherwise the sample floor and local sample visits stand in.
  const branchFloorTables = reactive<FloorTable[]>(sampleFloorTables());
  const isLiveFloor = computed(
    () => (tablesQuery.data.value?.items.length ?? 0) > 0,
  );
  watch(
    () => tablesQuery.data.value,
    (page) => {
      const live = page?.items ?? [];
      branchFloorTables.splice(
        0,
        branchFloorTables.length,
        ...(live.length ? live.map(toFloorTable) : sampleFloorTables()),
      );
    },
    { immediate: true },
  );

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
    if (!branchAccessVerified.value) return [];
    const now = Date.now();
    const rentals = rentalsQuery.data.value?.items ?? [];
    const apiItems = (reservationsQuery.data.value?.items ?? []).map((item) => {
      const r = {
        ...item,
        live: true,
        rentalId:
          item.rentalId ??
          rentals.find((rental) => rental.reservationId === item.id)?.id,
        clientType: item.source === 'WALK_IN' ? 'WALK_IN' : 'RESERVED',
        hourlyRate: item.tableHourlyRate
          ? baht(item.tableHourlyRate.amountMinor)
          : undefined,
      };
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

    if (isLiveFloor.value) return apiItems;

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
    onError: () => {
      actionError.value = 'Check-in failed. Refresh and retry.';
    },
  });

  async function handleCheckIn(res: Reservation) {
    actionError.value = '';
    actionInProgress.value = res.id;
    try {
      await checkInMutation.mutateAsync(res.id);
      reservationsStore.checkInReservation(res.id);
    } catch {
      // The API rejected the check-in; keep the saved reservation unchanged.
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
    mutationFn: ({
      branchId,
      key,
      payload,
    }: {
      branchId: string;
      key: string;
      payload: Parameters<typeof createStaffReservation>[1];
    }) =>
      createStaffReservation(branchId, payload, {
        headers: { 'Idempotency-Key': key },
      }),
  });

  // One key per walk-in attempt, reused when a lost response is retried.
  let walkInAttempt: { key: string; body: string } | null = null;

  async function handleStaffWalkInSubmit() {
    walkInError.value = '';
    const matchedTable =
      branchFloorTables.find((t) => t.id === walkInTableId.value) ||
      branchFloorTables[0];
    if (!matchedTable) return;
    const customerName = walkInGuestName.value.trim() || 'Walk-in Guests';

    if (!isLiveFloor.value) {
      reservationsStore.startWalkInSession({
        branchId: selectedBranchId.value,
        branchName: selectedBranch.value?.name ?? '',
        tableId: matchedTable.id,
        tableLabel: `Table ${matchedTable.number}`,
        partySize: walkInPartySize.value,
        customerName,
        customerPhone: walkInPhone.value || undefined,
        hourlyRate: matchedTable.rate,
        durationHours: walkInDuration.value,
      });
      showWalkInModal.value = false;
      return;
    }

    const branchId = selectedBranchId.value;
    const payload = {
      source: 'WALK_IN' as const,
      tableId: matchedTable.id,
      endsAt: new Date(
        Date.now() + walkInDuration.value * 3_600_000,
      ).toISOString(),
      partySize: walkInPartySize.value,
      customer: {
        kind: 'GUEST' as const,
        displayName: customerName,
        ...(walkInPhone.value.trim()
          ? { phone: walkInPhone.value.trim() }
          : {}),
      },
      games: [],
    };
    // endsAt moves with the clock, so the retry key follows table, party and guest.
    const body = JSON.stringify({ ...payload, endsAt: undefined, branchId });
    if (walkInAttempt?.body !== body)
      walkInAttempt = { key: crypto.randomUUID(), body };
    let created: Reservation;
    try {
      created = await staffCreateMutation.mutateAsync({
        branchId,
        key: walkInAttempt.key,
        payload,
      });
    } catch (error) {
      // A definite rejection ends this attempt; a lost response keeps its key.
      if (httpStatus(error) !== undefined) walkInAttempt = null;
      walkInError.value = 'Seating failed. Refresh and retry.';
      return;
    }
    walkInAttempt = null;
    void queryClient.invalidateQueries({
      queryKey: ['listBranchReservations'],
    });
    try {
      await checkInMutation.mutateAsync(created.id);
    } catch {
      walkInError.value =
        'The walk-in is booked but not checked in. Check it in from Bookings.';
      return;
    }
    showWalkInModal.value = false;
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
    isLiveFloor,
    branchAccessVerified,
    branchAccessDenied,
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

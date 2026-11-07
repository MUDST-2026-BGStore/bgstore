<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { RouterLink } from 'vue-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query';
import {
  listBranches,
  listBranchReservations,
  checkInReservation,
  cancelReservation,
  markReservationNoShow,
  createStaffReservation,
  getBranchAvailability,
  type Branch,
  type Reservation,
  type TableAvailability,
} from '@theparlor/api-client';
import FoodMenuPicker from '../components/FoodMenuPicker.vue';
import type { FoodOrderItem } from '../types';
import { calculatePreOrderDiscount } from '../data/payment';
import TableBillModal from '../components/TableBillModal.vue';
import { useCustomerReservationsStore } from '../data/reservations-store';
import { FOOD_MENU_ITEMS } from '../data/food-menu';

const queryClient = useQueryClient();

const selectedBranchId = ref<string>('');
const activeTab = ref<'FLOOR' | 'UPCOMING' | 'HISTORY'>('FLOOR');
const historyFilterStatus = ref<string>('ALL');
const historySearchQuery = ref<string>('');

// Query branches
const branchesQuery = useQuery({
  queryKey: ['branches'],
  queryFn: ({ signal }) => listBranches({}, undefined, signal),
});
const branches = computed<Branch[]>(() => branchesQuery.data.value?.items ?? []);

watch(branches, (list) => {
  if (list.length > 0 && !selectedBranchId.value) {
    selectedBranchId.value = list[0].id;
  }
});

const selectedBranch = computed(() =>
  branches.value.find((b) => b.id === selectedBranchId.value)
);

// Query branch reservations
const reservationsQuery = useQuery({
  queryKey: ['listBranchReservations', selectedBranchId],
  queryFn: ({ signal }) =>
    listBranchReservations(
      selectedBranchId.value,
      {},
      undefined,
      signal
    ),
  enabled: computed(() => !!selectedBranchId.value),
});

const reservationsStore = useCustomerReservationsStore();

const allReservations = computed<any[]>(() => {
  const apiItems = reservationsQuery.data.value?.items ?? [];
  const localItems = reservationsStore.reservations.value.filter(
    (r) =>
      !selectedBranchId.value ||
      r.branchId === selectedBranchId.value ||
      r.branchName.toLowerCase() === selectedBranch.value?.name.toLowerCase()
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
  return allReservations.value.filter((r) => r.status === 'CONFIRMED' || r.status === 'PENDING');
});

const completedReservations = computed(() => {
  return allReservations.value.filter((r) => r.status === 'COMPLETED');
});

const filteredHistoryReservations = computed(() => {
  let list = allReservations.value;
  if (historyFilterStatus.value !== 'ALL') {
    list = list.filter((r) => r.status === historyFilterStatus.value);
  }
  if (historySearchQuery.value.trim()) {
    const q = historySearchQuery.value.toLowerCase().trim();
    list = list.filter((r) => {
      const name = getCustomerName(r.customer).toLowerCase();
      const phone = getCustomerPhone(r.customer).toLowerCase();
      const tbl = String(r.tableId || '').toLowerCase();
      return name.includes(q) || phone.includes(q) || tbl.includes(q);
    });
  }
  return list;
});

// Format Timestamps
function formatInterval(startsAt: string, endsAt: string): string {
  if (!startsAt || !endsAt) return '';
  const s = new Date(startsAt);
  const e = new Date(endsAt);
  const timeStr = `${s.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} – ${e.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
  return timeStr;
}

function formatTime(iso: string): string {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function getElapsedMinutes(res: any): number {
  const start = new Date(res.checkedInAt || res.startsAt).getTime();
  const end = res.completedAt ? new Date(res.completedAt).getTime() : Date.now();
  return Math.max(1, Math.round((end - start) / 60000));
}

function formatPlayedDuration(mins: number): string {
  if (mins < 60) return `${mins}m`;
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return m > 0 ? `${h}h ${m}m` : `${h}h`;
}

function calculateCurrentPlayFee(res: any): number {
  const mins = getElapsedMinutes(res);
  const rate = res.hourlyRate || 200;
  return Math.round((mins / 60) * rate);
}

function getTableSnackSummary(res: any) {
  const storeRes = reservationsStore.reservations.value.find((r) => r.id === res.id);
  const orders = storeRes?.inPlayOrders || [];
  const allItems: Array<{ name: string; quantity: number; price: number }> = [];
  let total = 0;
  for (const o of orders) {
    total += o.subtotal;
    for (const it of o.items) {
      const existing = allItems.find((x) => x.name === it.item.name);
      if (existing) {
        existing.quantity += it.quantity;
      } else {
        allItems.push({
          name: it.item.name,
          quantity: it.quantity,
          price: it.item.price,
        });
      }
    }
  }
  return { items: allItems, total };
}

function isWalkIn(res: any): boolean {
  return (
    res.source === 'WALK_IN' ||
    res.clientType === 'WALK_IN' ||
    (res.id && String(res.id).startsWith('walkin')) ||
    (res.notes && String(res.notes).toLowerCase().includes('walk-in'))
  );
}

// Staff Snack Tab Ordering
const staffSnackReservation = ref<any>(null);
const staffSnackQuantities = ref<Record<string, number>>({});

function openStaffAddSnacks(res: any) {
  staffSnackReservation.value = res;
  staffSnackQuantities.value = {};
}

function getStaffSnackQty(itemId: string): number {
  return staffSnackQuantities.value[itemId] || 0;
}

function adjustStaffSnackQty(item: any, delta: number) {
  const current = getStaffSnackQty(item.id);
  const next = Math.max(0, current + delta);
  if (next === 0) {
    delete staffSnackQuantities.value[item.id];
  } else {
    staffSnackQuantities.value[item.id] = next;
  }
}

const staffSnackSelectedItems = computed(() => {
  return Object.entries(staffSnackQuantities.value)
    .filter(([_, qty]) => qty > 0)
    .map(([id, qty]) => {
      const item = FOOD_MENU_ITEMS.find((f) => f.id === id);
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
    0
  );
});

function submitStaffSnackOrder() {
  if (!staffSnackReservation.value || staffSnackSelectedItems.value.length === 0) return;
  reservationsStore.addStaffSnackOrder(
    staffSnackReservation.value.id,
    staffSnackSelectedItems.value,
    'Added by counter staff'
  );
  staffSnackReservation.value = null;
}

// Staff Table Bill Modal
const staffBillReservation = ref<any>(null);

function openStaffBill(res: any) {
  const found = reservationsStore.reservations.value.find((r) => r.id === res.id);
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

// Mutations
const actionInProgress = ref<string | null>(null);
const actionError = ref<string>('');

const checkInMutation = useMutation({
  mutationFn: async (resId: string) => {
    return checkInReservation(selectedBranchId.value, resId);
  },
  onSuccess: () => {
    void queryClient.invalidateQueries({ queryKey: ['listBranchReservations'] });
  },
  onError: (err: any) => {
    actionError.value =
      err?.response?.data?.message || err?.message || 'Check-in failed.';
  },
});

async function handleCheckIn(res: Reservation) {
  actionError.value = '';
  actionInProgress.value = res.id;
  try {
    reservationsStore.checkInReservation(res.id);
    await checkInMutation.mutateAsync(res.id);
    // Switch smoothly to the floor view so staff sees their active table
    activeTab.value = 'FLOOR';
  } finally {
    actionInProgress.value = null;
  }
}

// Reason Modal (for Cancel or No-Show)
const modalAction = ref<'CANCEL' | 'NO_SHOW' | null>(null);
const targetReservation = ref<Reservation | null>(null);
const modalReason = ref<string>('');

function openReasonModal(res: Reservation, action: 'CANCEL' | 'NO_SHOW') {
  targetReservation.value = res;
  modalAction.value = action;
  modalReason.value = action === 'CANCEL' ? 'Customer requested' : 'Customer did not arrive within 30 minutes';
  actionError.value = '';
}

const cancelMutation = useMutation({
  mutationFn: async ({ resId, reason }: { resId: string; reason: string }) => {
    return cancelReservation(selectedBranchId.value, resId, { reason });
  },
  onSuccess: () => {
    modalAction.value = null;
    targetReservation.value = null;
    void queryClient.invalidateQueries({ queryKey: ['listBranchReservations'] });
  },
  onError: (err: any) => {
    actionError.value =
      err?.response?.data?.message || err?.message || 'Action failed.';
  },
});

const noShowMutation = useMutation({
  mutationFn: async ({ resId, reason }: { resId: string; reason: string }) => {
    return markReservationNoShow(selectedBranchId.value, resId, { reason });
  },
  onSuccess: () => {
    modalAction.value = null;
    targetReservation.value = null;
    void queryClient.invalidateQueries({ queryKey: ['listBranchReservations'] });
  },
  onError: (err: any) => {
    actionError.value =
      err?.response?.data?.message || err?.message || 'Action failed.';
  },
});

async function submitModalAction() {
  if (!targetReservation.value || !modalAction.value) return;
  actionError.value = '';
  if (modalAction.value === 'CANCEL') {
    await cancelMutation.mutateAsync({
      resId: targetReservation.value.id,
      reason: modalReason.value,
    });
  } else {
    await noShowMutation.mutateAsync({
      resId: targetReservation.value.id,
      reason: modalReason.value,
    });
  }
}

// Staff Walk-in Modal
const showWalkInModal = ref(false);
const walkInDate = ref(new Date().toISOString().split('T')[0]);
const walkInTime = ref('15:00');
const walkInDuration = ref(2);
const walkInPartySize = ref(4);
const walkInCustomerName = ref('');
const walkInCountryCode = ref('+66');
const walkInPhone = ref('');
const walkInSelectedTable = ref<TableAvailability | null>(null);
const walkInFood = ref<FoodOrderItem[]>([]);
const walkInNotes = ref('');
const walkInStartNow = ref(true);
const walkInError = ref('');

const walkInStartTimestamp = computed(() => {
  if (!walkInDate.value || !walkInTime.value) return '';
  const dt = new Date(`${walkInDate.value}T${walkInTime.value}:00`);
  return isNaN(dt.getTime()) ? '' : dt.toISOString();
});

const walkInEndTimestamp = computed(() => {
  if (!walkInStartTimestamp.value) return '';
  const dt = new Date(walkInStartTimestamp.value);
  dt.setHours(dt.getHours() + walkInDuration.value);
  return dt.toISOString();
});

const walkInAvailabilityQuery = useQuery({
  queryKey: [
    'walkInAvailability',
    selectedBranchId,
    walkInStartTimestamp,
    walkInEndTimestamp,
    walkInPartySize,
  ],
  queryFn: ({ signal }) =>
    getBranchAvailability(
      selectedBranchId.value,
      {
        startsAt: walkInStartTimestamp.value,
        endsAt: walkInEndTimestamp.value,
        partySize: walkInPartySize.value,
      },
      undefined,
      signal
    ),
  enabled: computed(() => showWalkInModal.value && !!selectedBranchId.value && !!walkInStartTimestamp.value),
});

const walkInTables = computed(() =>
  (walkInAvailabilityQuery.data.value?.tables ?? []).filter((t) => t.available)
);

watch(walkInTables, (avail) => {
  if (avail.length > 0 && !walkInSelectedTable.value) {
    walkInSelectedTable.value = avail[0];
  }
});

const staffCreateMutation = useMutation({
  mutationFn: async (payload: any) => {
    return createStaffReservation(selectedBranchId.value, payload);
  },
  onSuccess: () => {
    showWalkInModal.value = false;
    void queryClient.invalidateQueries({ queryKey: ['listBranchReservations'] });
  },
  onError: (err: any) => {
    walkInError.value =
      err?.response?.data?.message || err?.message || 'Staff booking failed.';
  },
});

async function handleStaffBooking() {
  if (!walkInSelectedTable.value) return;
  walkInError.value = '';

  const startsNow = walkInStartNow.value;
  const startsAtIso = startsNow ? new Date().toISOString() : walkInStartTimestamp.value;
  const endsAtIso = startsNow
    ? new Date(Date.now() + walkInDuration.value * 3600000).toISOString()
    : walkInEndTimestamp.value;

  const rateMinor = (walkInSelectedTable.value.hourlyRate as any)?.amountMinor;
  const rate = rateMinor ? Number(rateMinor) / 100 : 200;

  const newSession = reservationsStore.startWalkInSession({
    branchId: selectedBranchId.value,
    branchName: selectedBranch.value?.name ?? 'Siam',
    tableId: walkInSelectedTable.value.tableId,
    tableLabel: walkInSelectedTable.value.label || `Table ${walkInSelectedTable.value.tableId}`,
    partySize: walkInPartySize.value,
    customerName: walkInCustomerName.value || 'Walk-in Guests',
    customerPhone: `${walkInCountryCode.value} ${walkInPhone.value}`.trim() || undefined,
    hourlyRate: rate,
    durationHours: walkInDuration.value,
  });

  if (walkInFood.value.length > 0) {
    reservationsStore.addStaffSnackOrder(
      newSession.id,
      walkInFood.value.map((f) => ({
        item: {
          id: f.item.id,
          name: f.item.name,
          nameTh: f.item.nameTh,
          price: f.item.price,
        },
        quantity: f.quantity,
      })),
      'Added during walk-in check-in'
    );
  }

  try {
    let notes = walkInNotes.value ? `${walkInNotes.value}\n` : '';
    if (walkInFood.value.length > 0) {
      const foodSummary = walkInFood.value
        .map((f) => `${f.quantity}x ${f.item.name}`)
        .join(', ');
      const discountCalc = calculatePreOrderDiscount(
        walkInFood.value.reduce((a, b) => a + b.item.price * b.quantity, 0)
      );
      notes += `[Staff Pre-order (-10%)]: ${foodSummary} (฿${discountCalc.discountedTotal})`;
    }

    const payload = {
      source: 'WALK_IN',
      tableId: walkInSelectedTable.value.tableId,
      startsAt: startsAtIso,
      endsAt: endsAtIso,
      partySize: walkInPartySize.value,
      customer: {
        type: 'GUEST',
        name: walkInCustomerName.value || 'Walk-in Guests',
        phone: `${walkInCountryCode.value} ${walkInPhone.value}`.trim() || undefined,
      },
      games: [],
      notes: notes.trim() || undefined,
    };
    await staffCreateMutation.mutateAsync(payload);
  } catch {
    // Local store holds the active session
  }

  showWalkInModal.value = false;
  activeTab.value = 'FLOOR';
}

function getCustomerName(c: any): string {
  if (!c) return 'Customer';
  return c.displayName || c.name || c.subject || 'Customer';
}
function getCustomerPhone(c: any): string {
  return c?.phone || '';
}

function formatStatus(status: string): string {
  switch (status) {
    case 'CONFIRMED':
      return 'Confirmed';
    case 'CHECKED_IN':
      return 'Playing Now';
    case 'COMPLETED':
      return 'Completed';
    case 'CANCELLED':
      return 'Cancelled';
    case 'NO_SHOW':
      return 'No-Show';
    case 'PENDING':
      return 'Pending';
    default:
      return status;
  }
}
</script>

<template>
  <main class="branch-console-page">
    <div class="console-wrapper">
      <!-- Top Brand & Branch Bar -->
      <header class="console-header">
        <div class="console-branding">
          <div class="brand-pill">
            <span class="gold-suit">♠</span>
            <span class="brand-text">THE PARLOR</span>
            <span class="console-role-tag">Staff Console</span>
          </div>
          <h1 class="console-title">Branch Operations & Floor</h1>
          <p class="console-subtitle">
            Manage live table sessions, seat walk-in groups, add snack tabs, and check guest bills.
          </p>
        </div>

        <div class="console-top-controls">
          <div class="branch-selector-pill">
            <label for="branch-select" class="selector-icon">📍</label>
            <select id="branch-select" v-model="selectedBranchId" class="branch-select-native">
              <option v-for="b in branches" :key="b.id" :value="b.id">
                {{ b.name }} Branch ({{ b.address || 'Bangkok' }})
              </option>
            </select>
          </div>

          <button
            type="button"
            class="walkin-hero-btn"
            @click="showWalkInModal = true"
          >
            <span class="btn-icon">+</span>
            <span>Walk-in Table</span>
          </button>
        </div>
      </header>

      <!-- Shift Metrics Ribbon -->
      <section class="metrics-ribbon" aria-label="Shift overview">
        <div
          class="metric-chip"
          :class="{ active: activeTab === 'FLOOR' }"
          @click="activeTab = 'FLOOR'"
        >
          <div class="chip-dot dot-green"></div>
          <div class="chip-content">
            <span class="chip-val">{{ activeFloorReservations.length }}</span>
            <span class="chip-lbl">Active Tables</span>
          </div>
        </div>

        <div
          class="metric-chip"
          :class="{ active: activeTab === 'UPCOMING' }"
          @click="activeTab = 'UPCOMING'"
        >
          <div class="chip-dot dot-amber"></div>
          <div class="chip-content">
            <span class="chip-val">{{ upcomingReservations.length }}</span>
            <span class="chip-lbl">Upcoming Arrivals</span>
          </div>
        </div>

        <div
          class="metric-chip"
          :class="{ active: activeTab === 'HISTORY' }"
          @click="activeTab = 'HISTORY'"
        >
          <div class="chip-dot dot-slate"></div>
          <div class="chip-content">
            <span class="chip-val">{{ completedReservations.length }}</span>
            <span class="chip-lbl">Completed Today</span>
          </div>
        </div>

        <div class="metric-quick-links">
          <RouterLink :to="{ name: 'WorkspaceManagement' }" class="quick-link">
            ← Branch Hub
          </RouterLink>
          <span class="quick-sep">·</span>
          <RouterLink :to="{ name: 'HomeIndex' }" class="quick-link">
            Public Store
          </RouterLink>
        </div>
      </section>

      <!-- Main Navigation Tabs -->
      <nav class="console-tabs" aria-label="Operational views">
        <button
          type="button"
          class="tab-btn"
          :class="{ active: activeTab === 'FLOOR' }"
          @click="activeTab = 'FLOOR'"
        >
          <span class="tab-icon">🎲</span>
          <span>Live Floor & Active Tables</span>
          <span v-if="activeFloorReservations.length > 0" class="tab-badge">
            {{ activeFloorReservations.length }}
          </span>
        </button>

        <button
          type="button"
          class="tab-btn"
          :class="{ active: activeTab === 'UPCOMING' }"
          @click="activeTab = 'UPCOMING'"
        >
          <span class="tab-icon">📅</span>
          <span>Upcoming Bookings</span>
          <span v-if="upcomingReservations.length > 0" class="tab-badge amber">
            {{ upcomingReservations.length }}
          </span>
        </button>

        <button
          type="button"
          class="tab-btn"
          :class="{ active: activeTab === 'HISTORY' }"
          @click="activeTab = 'HISTORY'"
        >
          <span class="tab-icon">📋</span>
          <span>All Records & History</span>
        </button>
      </nav>

      <!-- Error Alert -->
      <div v-if="actionError" class="alert-banner" role="alert">
        {{ actionError }}
      </div>

      <!-- VIEW 1: LIVE FLOOR & ACTIVE TABLES -->
      <section v-if="activeTab === 'FLOOR'" class="view-panel">
        <div v-if="activeFloorReservations.length === 0" class="empty-state-card">
          <div class="empty-illustration">🎲</div>
          <h2 class="empty-title">All Tables Are Open</h2>
          <p class="empty-desc">
            No active gaming sessions running right now at {{ selectedBranch?.name || 'this branch' }}.
            Ready to seat incoming guests or check in bookings!
          </p>
          <button
            type="button"
            class="walkin-hero-btn"
            @click="showWalkInModal = true"
          >
            + Seat Walk-in Group Now
          </button>
        </div>

        <div v-else class="floor-grid">
          <article
            v-for="res in activeFloorReservations"
            :key="res.id"
            class="table-card"
          >
            <!-- Card Header: Table Number & Client Badge -->
            <header class="table-card-top">
              <div class="table-id-group">
                <span class="table-label">Table {{ res.tableId }}</span>
                <span class="party-chip">👥 {{ res.partySize }} Players</span>
              </div>
              <span v-if="isWalkIn(res)" class="client-pill walkin">
                ● Walk-in
              </span>
              <span v-else class="client-pill reserved">
                ● Reserved
              </span>
            </header>

            <!-- Play Timer & Elapsed Time -->
            <div class="timer-box">
              <div class="timer-main">
                <span class="timer-icon">⏱️</span>
                <span class="timer-val">{{ formatPlayedDuration(getElapsedMinutes(res)) }} played</span>
              </div>
              <span class="timer-sub">
                Checked in at {{ formatTime(res.checkedInAt || res.startsAt) }}
              </span>
            </div>

            <!-- Customer Details -->
            <div class="guest-info-row">
              <div class="guest-name">{{ getCustomerName(res.customer) }}</div>
              <div v-if="getCustomerPhone(res.customer)" class="guest-phone">
                📞 {{ getCustomerPhone(res.customer) }}
              </div>
            </div>

            <!-- Running Snacks / Tab Preview -->
            <div class="snack-tab-preview">
              <div class="snack-tab-header">
                <span class="snack-tab-title">🥤 Snacks & Drinks Tab</span>
                <span v-if="getTableSnackSummary(res).total > 0" class="snack-tab-total">
                  ฿{{ getTableSnackSummary(res).total }}
                </span>
              </div>

              <div v-if="getTableSnackSummary(res).items.length > 0" class="snack-items-mini">
                <span
                  v-for="(it, idx) in getTableSnackSummary(res).items"
                  :key="idx"
                  class="snack-mini-chip"
                >
                  {{ it.quantity }}x {{ it.name }}
                </span>
              </div>
              <p v-else class="no-snacks-text">
                No packaged snacks ordered yet.
              </p>
            </div>

            <!-- Estimated Table Fee -->
            <div class="fee-estimate-row">
              <span class="fee-lbl">Table Rate:</span>
              <span class="fee-val">
                ฿{{ res.hourlyRate || 200 }}/hr · <span class="play-fee">~฿{{ calculateCurrentPlayFee(res) }}</span>
              </span>
            </div>

            <!-- Actions Footer -->
            <footer class="table-card-footer">
              <button
                type="button"
                class="btn-card-snack"
                @click="openStaffAddSnacks(res)"
              >
                + Add Snacks
              </button>
              <button
                type="button"
                class="btn-card-bill"
                @click="openStaffBill(res)"
              >
                Check Bill ✓
              </button>
            </footer>
          </article>
        </div>
      </section>

      <!-- VIEW 2: UPCOMING BOOKINGS -->
      <section v-if="activeTab === 'UPCOMING'" class="view-panel">
        <div v-if="upcomingReservations.length === 0" class="empty-state-card">
          <div class="empty-illustration">📅</div>
          <h2 class="empty-title">No Upcoming Reservations</h2>
          <p class="empty-desc">
            No further bookings scheduled for today at {{ selectedBranch?.name || 'this branch' }}.
          </p>
        </div>

        <div v-else class="upcoming-list">
          <article
            v-for="res in upcomingReservations"
            :key="res.id"
            class="upcoming-card"
          >
            <div class="upcoming-time-col">
              <span class="time-slot">{{ formatInterval(res.startsAt, res.endsAt) }}</span>
              <span class="res-ref">Ref: {{ res.id }}</span>
            </div>

            <div class="upcoming-main-col">
              <div class="upcoming-title-row">
                <span class="tbl-badge">Table {{ res.tableId }}</span>
                <span class="party-badge">👥 {{ res.partySize }} Guests</span>
                <span class="client-pill reserved">Reserved Online</span>
              </div>

              <div class="guest-details">
                <strong class="guest-name">{{ getCustomerName(res.customer) }}</strong>
                <span v-if="getCustomerPhone(res.customer)" class="guest-phone">
                  · {{ getCustomerPhone(res.customer) }}
                </span>
              </div>

              <div v-if="res.notes" class="upcoming-notes">
                📝 {{ res.notes }}
              </div>
            </div>

            <div class="upcoming-actions-col">
              <button
                type="button"
                class="btn-checkin-hero"
                :disabled="actionInProgress === res.id"
                @click="handleCheckIn(res)"
              >
                Check In Guest ✓
              </button>
              <div class="secondary-actions">
                <button
                  type="button"
                  class="btn-subtle"
                  @click="openReasonModal(res, 'CANCEL')"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  class="btn-subtle"
                  @click="openReasonModal(res, 'NO_SHOW')"
                >
                  No-Show
                </button>
              </div>
            </div>
          </article>
        </div>
      </section>

      <!-- VIEW 3: ALL RECORDS & HISTORY -->
      <section v-if="activeTab === 'HISTORY'" class="view-panel">
        <div class="history-controls">
          <div class="history-filter-pills">
            <button
              v-for="st in ['ALL', 'CONFIRMED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'NO_SHOW']"
              :key="st"
              type="button"
              class="hist-pill"
              :class="{ active: historyFilterStatus === st }"
              @click="historyFilterStatus = st"
            >
              {{ st }}
            </button>
          </div>

          <div class="history-search">
            <input
              v-model="historySearchQuery"
              type="search"
              placeholder="Search guest or table..."
              class="search-input"
            />
          </div>
        </div>

        <div v-if="filteredHistoryReservations.length === 0" class="empty-state-card small">
          <p>No records match the selected status or query.</p>
        </div>

        <div v-else class="history-table-card">
          <table class="history-table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Status</th>
                <th>Table</th>
                <th>Guest</th>
                <th>Notes / Snacks</th>
                <th class="th-actions">Action</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="res in filteredHistoryReservations" :key="res.id">
                <td>
                  <span class="hist-time">{{ formatInterval(res.startsAt, res.endsAt) }}</span>
                  <span class="hist-ref">#{{ res.id }}</span>
                </td>
                <td>
                  <span class="status-chip" :class="`chip-${res.status.toLowerCase()}`">
                    {{ formatStatus(res.status) }}
                  </span>
                </td>
                <td>
                  <strong>Table {{ res.tableId }}</strong>
                  <div class="hist-sub">{{ res.partySize }} guests</div>
                </td>
                <td>
                  <div class="hist-guest-name">{{ getCustomerName(res.customer) }}</div>
                  <div class="hist-sub">{{ getCustomerPhone(res.customer) }}</div>
                </td>
                <td>
                  <span v-if="res.notes" class="hist-notes">{{ res.notes }}</span>
                  <span v-else class="hist-sub">—</span>
                </td>
                <td class="td-actions">
                  <button
                    v-if="res.status === 'CHECKED_IN'"
                    type="button"
                    class="btn-table-action"
                    @click="openStaffBill(res)"
                  >
                    Check Bill
                  </button>
                  <button
                    v-else-if="res.status === 'CONFIRMED'"
                    type="button"
                    class="btn-table-action green"
                    @click="handleCheckIn(res)"
                  >
                    Check In
                  </button>
                  <button
                    v-else
                    type="button"
                    class="btn-table-action view"
                    @click="openStaffBill(res)"
                  >
                    View Bill
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- Staff Table Bill & Settlement Modal -->
      <TableBillModal
        v-if="staffBillReservation"
        :reservation="staffBillReservation"
        :is-staff="true"
        @close="staffBillReservation = null"
        @settled="staffBillReservation = null; queryClient.invalidateQueries({ queryKey: ['listBranchReservations'] })"
      />

      <!-- Action Reason Modal (Cancel / No Show) -->
      <div v-if="modalAction" class="modal-backdrop" @click.self="modalAction = null">
        <div class="modal-card">
          <h3 class="modal-title">
            {{ modalAction === 'CANCEL' ? 'Cancel Reservation' : 'Mark as No-Show' }}
          </h3>
          <p class="modal-sub">
            Update status for Reservation Ref <strong>{{ targetReservation?.id }}</strong> at
            {{ selectedBranch?.name }}.
          </p>

          <div class="form-group">
            <label for="action-reason" class="modal-label">Reason for record:</label>
            <input
              id="action-reason"
              v-model="modalReason"
              type="text"
              class="modal-input"
              required
            />
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="modalAction = null">
              Close
            </button>
            <button
              type="button"
              class="btn"
              :class="modalAction === 'CANCEL' ? 'btn-danger' : 'btn-warning'"
              :disabled="cancelMutation.isPending.value || noShowMutation.isPending.value"
              @click="submitModalAction"
            >
              Confirm {{ modalAction === 'CANCEL' ? 'Cancellation' : 'No-Show' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Quick Walk-in Modal -->
      <div v-if="showWalkInModal" class="modal-backdrop" @click.self="showWalkInModal = false">
        <div class="walkin-modal">
          <div class="walkin-header">
            <div>
              <div class="modal-eyebrow">
                <span class="gold-suit">♠</span>
                <span>QUICK WALK-IN SEATING</span>
              </div>
              <h2 class="walkin-title">Seat Walk-in Table</h2>
            </div>
            <button type="button" class="close-x" @click="showWalkInModal = false">✕</button>
          </div>

          <div class="walkin-body">
            <!-- Table Selection -->
            <div class="form-section">
              <label class="form-label">1. Choose Table (at {{ selectedBranch?.name }}):</label>
              <div v-if="walkInTables.length === 0" class="no-tables">
                Checking table availability…
              </div>
              <div v-else class="tables-grid">
                <button
                  v-for="tbl in walkInTables"
                  :key="tbl.tableId"
                  type="button"
                  class="tbl-select-card"
                  :class="{ active: walkInSelectedTable?.tableId === tbl.tableId }"
                  @click="walkInSelectedTable = tbl"
                >
                  <div class="tbl-card-name">{{ tbl.label }}</div>
                  <div class="tbl-card-cap">Up to {{ tbl.capacity }} players</div>
                  <div class="tbl-card-rate">
                    ฿{{ (tbl.hourlyRate as any)?.amountMinor ? ((tbl.hourlyRate as any).amountMinor / 100) : 200 }}/hr
                  </div>
                </button>
              </div>
            </div>

            <!-- Party Size & Duration -->
            <div class="form-row-2">
              <div class="form-field">
                <label class="form-label">2. Players in Group:</label>
                <div class="stepper-wrap">
                  <button
                    type="button"
                    class="step-btn"
                    :disabled="walkInPartySize <= 1"
                    @click="walkInPartySize = Math.max(1, walkInPartySize - 1)"
                  >
                    -
                  </button>
                  <span class="step-val">{{ walkInPartySize }}</span>
                  <button
                    type="button"
                    class="step-btn"
                    @click="walkInPartySize++"
                  >
                    +
                  </button>
                </div>
              </div>

              <div class="form-field">
                <label class="form-label">3. Expected Duration:</label>
                <select v-model.number="walkInDuration" class="modal-select">
                  <option :value="1">1 Hour</option>
                  <option :value="2">2 Hours</option>
                  <option :value="3">3 Hours</option>
                  <option :value="4">4 Hours</option>
                </select>
              </div>
            </div>

            <!-- Optional Customer Info -->
            <div class="form-section">
              <label class="form-label">4. Guest Name (Optional):</label>
              <input
                v-model="walkInCustomerName"
                type="text"
                placeholder="Walk-in Guests"
                class="modal-input"
              />
            </div>

            <!-- Pre-order snacks -->
            <div class="form-section">
              <details class="food-details">
                <summary class="food-summary-btn">
                  + Pre-Order Refreshments (-10% Pre-order Discount)
                </summary>
                <div class="picker-wrap">
                  <FoodMenuPicker v-model="walkInFood" />
                </div>
              </details>
            </div>

            <div v-if="walkInError" class="alert-banner">{{ walkInError }}</div>
          </div>

          <div class="walkin-footer">
            <button type="button" class="btn btn-secondary" @click="showWalkInModal = false">
              Cancel
            </button>
            <button
              type="button"
              class="btn btn-primary"
              :disabled="!walkInSelectedTable || staffCreateMutation.isPending.value"
              @click="handleStaffBooking"
            >
              <span v-if="staffCreateMutation.isPending.value">Starting Table…</span>
              <span v-else>Seat & Start Playing Now →</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Staff Quick Add Snacks Modal -->
      <div v-if="staffSnackReservation" class="modal-backdrop" @click.self="staffSnackReservation = null">
        <div class="modal-card snack-modal-card">
          <div class="snack-modal-header">
            <div>
              <div class="snack-eyebrow">
                <span class="gold-suit">♠</span>
                <span>TABLE TAB / SNACK SERVICE</span>
              </div>
              <h2 class="snack-modal-title">Add Snacks & Drinks to Table</h2>
              <p class="snack-modal-sub">
                Table {{ staffSnackReservation.tableId }} · {{ getCustomerName(staffSnackReservation.customer) }}
              </p>
            </div>
            <button type="button" class="close-x" @click="staffSnackReservation = null">✕</button>
          </div>

          <div class="snack-picker-body">
            <div class="snack-items-list">
              <div
                v-for="item in FOOD_MENU_ITEMS"
                :key="item.id"
                class="snack-row-item"
              >
                <div class="snack-info">
                  <span class="snack-name">{{ item.name }}</span>
                  <span class="snack-price">฿{{ item.price }}</span>
                </div>
                <div class="snack-stepper">
                  <button
                    type="button"
                    class="snack-step-btn"
                    :disabled="getStaffSnackQty(item.id) <= 0"
                    @click="adjustStaffSnackQty(item, -1)"
                  >
                    -
                  </button>
                  <span class="snack-qty-num">{{ getStaffSnackQty(item.id) }}</span>
                  <button
                    type="button"
                    class="snack-step-btn plus"
                    @click="adjustStaffSnackQty(item, 1)"
                  >
                    +
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div class="snack-modal-footer">
            <div class="snack-order-summary">
              <span>Selected Total:</span>
              <strong class="snack-total-val">฿{{ staffSnackSubtotal }}</strong>
            </div>
            <div class="snack-footer-actions">
              <button type="button" class="btn btn-secondary" @click="staffSnackReservation = null">
                Cancel
              </button>
              <button
                type="button"
                class="btn btn-primary"
                :disabled="staffSnackSelectedItems.length === 0"
                @click="submitStaffSnackOrder"
              >
                Add to Table Tab (฿{{ staffSnackSubtotal }})
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </main>
</template>

<style scoped>
.branch-console-page {
  background: #fbf9f5;
  min-height: calc(100vh - 80px);
  padding: 24px 20px 60px;
  color: #1a2f24;
  font-family: Arial, Helvetica, sans-serif;
}

.console-wrapper {
  max-width: 1240px;
  margin: 0 auto;
}

/* Header */
.console-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 20px;
}

.brand-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: #203c30;
  background: #f0e9dc;
  padding: 3px 10px;
  border-radius: 12px;
  margin-bottom: 8px;
}

.gold-suit {
  color: #c99a4e;
}

.console-role-tag {
  background: #203c30;
  color: #fffaf0;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
}

.console-title {
  margin: 0 0 6px;
  font-size: 28px;
  font-weight: 800;
  color: #173224;
}

.console-subtitle {
  margin: 0;
  font-size: 14px;
  color: #635a4b;
}

.console-top-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.branch-selector-pill {
  display: flex;
  align-items: center;
  background: #ffffff;
  border: 1px solid #d5c8b2;
  border-radius: 10px;
  padding: 6px 14px;
  box-shadow: 0 1px 4px rgba(20, 43, 32, 0.04);
}

.selector-icon {
  margin-right: 6px;
  font-size: 16px;
}

.branch-select-native {
  border: none;
  background: transparent;
  font-size: 14px;
  font-weight: 700;
  color: #203c30;
  outline: none;
  cursor: pointer;
}

.walkin-hero-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: #203c30;
  color: #ffffff;
  border: 1px solid #162a22;
  padding: 10px 20px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 3px 8px rgba(32, 60, 48, 0.2);
  transition: all 120ms ease;
}

.walkin-hero-btn:hover {
  background: #2c5242;
  transform: translateY(-1px);
}

.btn-icon {
  font-size: 18px;
  line-height: 1;
}

/* Metrics Ribbon */
.metrics-ribbon {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #ffffff;
  border: 1px solid #e5dcce;
  border-radius: 14px;
  padding: 12px 20px;
  margin-bottom: 24px;
  flex-wrap: wrap;
  box-shadow: 0 2px 8px rgba(20, 43, 32, 0.03);
}

.metric-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 14px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 120ms ease;
}

.metric-chip:hover {
  background: #f7f3eb;
}

.metric-chip.active {
  background: #ede6d8;
}

.chip-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.dot-green {
  background: #16a34a;
  box-shadow: 0 0 0 3px rgba(22, 163, 74, 0.2);
}

.dot-amber {
  background: #d97706;
  box-shadow: 0 0 0 3px rgba(217, 119, 6, 0.2);
}

.dot-slate {
  background: #64748b;
}

.chip-content {
  display: flex;
  flex-direction: column;
}

.chip-val {
  font-size: 17px;
  font-weight: 800;
  color: #173224;
  line-height: 1.1;
}

.chip-lbl {
  font-size: 11px;
  color: #6a6253;
  font-weight: 600;
}

.metric-quick-links {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
}

.quick-link {
  color: #203c30;
  text-decoration: none;
}

.quick-link:hover {
  text-decoration: underline;
}

.quick-sep {
  color: #b0a592;
}

/* Tabs */
.console-tabs {
  display: flex;
  gap: 8px;
  border-bottom: 2px solid #e6decb;
  margin-bottom: 24px;
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  background: transparent;
  border: none;
  border-bottom: 3px solid transparent;
  margin-bottom: -2px;
  font-size: 15px;
  font-weight: 700;
  color: #696051;
  cursor: pointer;
  transition: all 120ms ease;
}

.tab-btn:hover {
  color: #203c30;
}

.tab-btn.active {
  color: #203c30;
  border-bottom-color: #203c30;
}

.tab-badge {
  background: #203c30;
  color: #ffffff;
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 12px;
  font-weight: 800;
}

.tab-badge.amber {
  background: #d97706;
}

/* Alerts */
.alert-banner {
  background: #fee2e2;
  border: 1px solid #f87171;
  color: #991b1b;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 20px;
  font-size: 13px;
}

/* Empty States */
.empty-state-card {
  background: #ffffff;
  border: 1px dashed #d5c8b2;
  border-radius: 16px;
  padding: 60px 24px;
  text-align: center;
  max-width: 580px;
  margin: 40px auto;
}

.empty-illustration {
  font-size: 48px;
  margin-bottom: 12px;
}

.empty-title {
  font-size: 20px;
  font-weight: 800;
  color: #173224;
  margin: 0 0 8px;
}

.empty-desc {
  font-size: 14px;
  color: #635b4c;
  margin: 0 auto 20px;
  line-height: 1.5;
}

/* Floor Grid */
.floor-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px;
}

.table-card {
  background: #ffffff;
  border: 1px solid #ded6c8;
  border-radius: 14px;
  padding: 18px;
  box-shadow: 0 3px 10px rgba(20, 43, 32, 0.04);
  display: flex;
  flex-direction: column;
  gap: 14px;
  border-top: 4px solid #16a34a;
}

.table-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-id-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.table-label {
  font-size: 18px;
  font-weight: 800;
  color: #173224;
}

.party-chip {
  background: #f2eee6;
  font-size: 12px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 6px;
  color: #4f4739;
}

.client-pill {
  font-size: 11px;
  font-weight: 800;
  padding: 3px 8px;
  border-radius: 10px;
}

.client-pill.walkin {
  background: #f3e8ff;
  color: #7e22ce;
  border: 1px solid #e9d5ff;
}

.client-pill.reserved {
  background: #ecfdf5;
  color: #047857;
  border: 1px solid #a7f3d0;
}

/* Timer */
.timer-box {
  background: #f7faf7;
  border: 1px solid #d4edd7;
  border-radius: 8px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.timer-main {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 800;
  color: #166534;
}

.timer-sub {
  font-size: 11px;
  color: #4b6352;
}

/* Guest */
.guest-info-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.guest-name {
  font-size: 15px;
  font-weight: 700;
  color: #203c30;
}

.guest-phone {
  font-size: 12px;
  color: #635a4a;
}

/* Snack Tab Preview */
.snack-tab-preview {
  background: #fffcf5;
  border: 1px solid #eedfc9;
  border-radius: 8px;
  padding: 10px 12px;
}

.snack-tab-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 700;
  color: #453b2d;
  margin-bottom: 6px;
}

.snack-tab-total {
  color: #b45309;
  font-weight: 800;
}

.snack-items-mini {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.snack-mini-chip {
  background: #fde8ca;
  color: #854d0e;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 4px;
}

.no-snacks-text {
  margin: 0;
  font-size: 11px;
  color: #8c8270;
  font-style: italic;
}

/* Fee estimate */
.fee-estimate-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #6b6353;
  padding-top: 4px;
  border-top: 1px solid #f0eae0;
}

.fee-lbl {
  font-weight: 600;
}

.fee-val {
  font-weight: 700;
  color: #203c30;
}

.play-fee {
  color: #15803d;
  font-weight: 800;
}

/* Footer buttons */
.table-card-footer {
  display: flex;
  gap: 8px;
  margin-top: auto;
}

.btn-card-snack {
  flex: 1;
  background: #f4efe4;
  border: 1px solid #dcd2bf;
  color: #3b3325;
  font-size: 13px;
  font-weight: 700;
  padding: 9px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 120ms ease;
}

.btn-card-snack:hover {
  background: #ede4d3;
}

.btn-card-bill {
  flex: 1.3;
  background: #203c30;
  border: 1px solid #142820;
  color: #ffffff;
  font-size: 13px;
  font-weight: 800;
  padding: 9px 14px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 120ms ease;
}

.btn-card-bill:hover {
  background: #2b5040;
}

/* Upcoming View */
.upcoming-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.upcoming-card {
  background: #ffffff;
  border: 1px solid #d5c8b2;
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  box-shadow: 0 2px 6px rgba(20, 43, 32, 0.03);
}

.upcoming-time-col {
  display: flex;
  flex-direction: column;
  min-width: 140px;
}

.time-slot {
  font-size: 16px;
  font-weight: 800;
  color: #203c30;
}

.res-ref {
  font-size: 11px;
  color: #8c8270;
}

.upcoming-main-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 240px;
}

.upcoming-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tbl-badge {
  font-size: 13px;
  font-weight: 800;
  color: #203c30;
}

.party-badge {
  font-size: 12px;
  color: #635b4c;
  font-weight: 600;
}

.upcoming-notes {
  font-size: 12px;
  color: #b45309;
  background: #fef3c7;
  padding: 4px 8px;
  border-radius: 4px;
  display: inline-block;
  margin-top: 4px;
}

.upcoming-actions-col {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.btn-checkin-hero {
  background: #16a34a;
  border: 1px solid #15803d;
  color: #ffffff;
  font-size: 14px;
  font-weight: 800;
  padding: 9px 18px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 120ms ease;
}

.btn-checkin-hero:hover {
  background: #15803d;
}

.secondary-actions {
  display: flex;
  gap: 8px;
}

.btn-subtle {
  background: transparent;
  border: none;
  font-size: 11px;
  color: #7a705e;
  cursor: pointer;
  text-decoration: underline;
}

.btn-subtle:hover {
  color: #dc2626;
}

/* History View */
.history-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.history-filter-pills {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.hist-pill {
  padding: 5px 12px;
  border: 1px solid #d5c8b2;
  background: #ffffff;
  color: #554d3e;
  border-radius: 14px;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}

.hist-pill.active {
  background: #203c30;
  color: #ffffff;
  border-color: #203c30;
}

.search-input {
  padding: 7px 12px;
  border: 1px solid #d5c8b2;
  border-radius: 8px;
  font-size: 13px;
  width: 220px;
}

.history-table-card {
  background: #ffffff;
  border: 1px solid #d5c8b2;
  border-radius: 10px;
  overflow-x: auto;
}

.history-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 13px;
}

.history-table th {
  background: #f7f3ea;
  padding: 10px 14px;
  font-weight: 700;
  color: #203c30;
  border-bottom: 1px solid #dfd5c2;
}

.history-table td {
  padding: 12px 14px;
  border-bottom: 1px solid #f0e9dc;
}

.hist-time {
  font-weight: 700;
  color: #203c30;
  display: block;
}

.hist-ref {
  font-size: 10px;
  color: #8c8270;
}

.hist-sub {
  font-size: 11px;
  color: #7a705e;
}

.status-chip {
  font-size: 11px;
  font-weight: 800;
  padding: 3px 8px;
  border-radius: 6px;
}

.chip-confirmed {
  background: #fef3c7;
  color: #92400e;
}

.chip-checked_in {
  background: #dcfce7;
  color: #166534;
}

.chip-completed {
  background: #f1f5f9;
  color: #475569;
}

.chip-cancelled {
  background: #fee2e2;
  color: #991b1b;
}

.chip-no_show {
  background: #ffedd5;
  color: #9a3412;
}

.btn-table-action {
  padding: 5px 12px;
  border: 1px solid #203c30;
  background: #203c30;
  color: #ffffff;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.btn-table-action.green {
  background: #16a34a;
  border-color: #15803d;
}

.btn-table-action.view {
  background: transparent;
  color: #203c30;
  border-color: #c9bca6;
}

/* Modals */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(18, 36, 28, 0.65);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  z-index: 1000;
}

.modal-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 24px;
  max-width: 480px;
  width: 100%;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
}

.modal-title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 800;
  color: #1a2f24;
}

.modal-sub {
  margin: 0 0 16px;
  font-size: 13px;
  color: #6a6253;
}

.modal-label {
  display: block;
  font-size: 12px;
  font-weight: 700;
  color: #3b3427;
  margin-bottom: 4px;
}

.modal-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d5c8b2;
  border-radius: 6px;
  font-size: 13px;
  box-sizing: border-box;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

.btn {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.btn-secondary {
  background: #f2eee6;
  border: 1px solid #d5c8b2;
  color: #4b4436;
}

.btn-primary {
  background: #203c30;
  border: 1px solid #142820;
  color: #ffffff;
}

.btn-danger {
  background: #dc2626;
  border: 1px solid #b91c1c;
  color: #ffffff;
}

.btn-warning {
  background: #ea580c;
  border: 1px solid #c2410c;
  color: #ffffff;
}

/* Walkin Modal */
.walkin-modal {
  background: #ffffff;
  border-radius: 16px;
  width: 100%;
  max-width: 640px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.25);
}

.walkin-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 24px;
  border-bottom: 1px solid #eee4d1;
}

.modal-eyebrow {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.06em;
  color: #c99a4e;
  margin-bottom: 2px;
}

.walkin-title {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  color: #173224;
}

.close-x {
  background: transparent;
  border: none;
  font-size: 20px;
  cursor: pointer;
  color: #8c8270;
}

.walkin-body {
  padding: 20px 24px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-label {
  display: block;
  font-size: 13px;
  font-weight: 700;
  color: #203c30;
  margin-bottom: 6px;
}

.tables-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 10px;
}

.tbl-select-card {
  background: #fbf9f4;
  border: 2px solid #e2d8c7;
  border-radius: 10px;
  padding: 10px;
  text-align: left;
  cursor: pointer;
  transition: all 120ms ease;
}

.tbl-select-card:hover {
  background: #f5eedf;
}

.tbl-select-card.active {
  background: #effaf2;
  border-color: #16a34a;
}

.tbl-card-name {
  font-size: 14px;
  font-weight: 800;
  color: #1a2f24;
}

.tbl-card-cap {
  font-size: 11px;
  color: #6a6253;
}

.tbl-card-rate {
  font-size: 12px;
  font-weight: 700;
  color: #15803d;
  margin-top: 4px;
}

.form-row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.stepper-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
}

.step-btn {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: 1px solid #d5c8b2;
  background: #fdfaf5;
  font-size: 18px;
  font-weight: 700;
  cursor: pointer;
}

.step-val {
  font-size: 16px;
  font-weight: 800;
  color: #173224;
  min-width: 24px;
  text-align: center;
}

.modal-select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d5c8b2;
  border-radius: 8px;
  font-size: 13px;
  background: #ffffff;
  color: #173224;
}

.walkin-footer {
  padding: 16px 24px;
  border-top: 1px solid #eee4d1;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  background: #faf7f2;
}

/* Snack Modal */
.snack-modal-card {
  max-width: 520px;
}

.snack-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.snack-eyebrow {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 800;
  color: #c99a4e;
  letter-spacing: 0.06em;
  margin-bottom: 2px;
}

.snack-modal-title {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: #173224;
}

.snack-modal-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: #635b4c;
}

.snack-items-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 340px;
  overflow-y: auto;
  padding-right: 4px;
}

.snack-row-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #faf7f2;
  border-radius: 8px;
  border: 1px solid #eee3d1;
}

.snack-info {
  display: flex;
  flex-direction: column;
}

.snack-name {
  font-size: 13px;
  font-weight: 700;
  color: #203c30;
}

.snack-price {
  font-size: 12px;
  color: #b45309;
  font-weight: 800;
}

.snack-stepper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.snack-step-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid #d5c8b2;
  background: #ffffff;
  font-weight: 700;
  cursor: pointer;
}

.snack-step-btn.plus {
  background: #203c30;
  border-color: #203c30;
  color: #ffffff;
}

.snack-qty-num {
  font-size: 13px;
  font-weight: 800;
  min-width: 18px;
  text-align: center;
}

.snack-modal-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px solid #eee4d1;
}

.snack-order-summary {
  font-size: 13px;
  color: #4b4435;
}

.snack-total-val {
  font-size: 18px;
  color: #15803d;
  margin-left: 6px;
}

.snack-footer-actions {
  display: flex;
  gap: 8px;
}

.food-details {
  border: 1px dashed #d5c8b2;
  border-radius: 8px;
  padding: 8px 12px;
  background: #fffdf9;
}

.food-summary-btn {
  font-size: 13px;
  font-weight: 700;
  color: #203c30;
  cursor: pointer;
}

.picker-wrap {
  margin-top: 12px;
}
</style>

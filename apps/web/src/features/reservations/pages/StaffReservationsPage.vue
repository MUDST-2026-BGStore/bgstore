<script setup lang="ts">
import { ref, computed, watch } from 'vue';
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
import TableBillModal from '../components/TableBillModal.vue';
import { useCustomerReservationsStore } from '../data/reservations-store';
import { FOOD_MENU_ITEMS } from '../data/food-menu';
import { useStoreText } from '../../home/data/store-text';

const { t, locale } = useStoreText();
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

// Auto 15-min No-Show Mutation
const autoNoShowMutation = useMutation({
  mutationFn: async ({ resId }: { resId: string }) => {
    return markReservationNoShow(selectedBranchId.value, resId, {
      reason: 'Automatic No-Show: Customer did not arrive within 15 minutes of reservation start time.',
    });
  },
  onSuccess: () => {
    void queryClient.invalidateQueries({ queryKey: ['listBranchReservations'] });
  },
});

const allReservations = computed<any[]>(() => {
  const now = Date.now();
  const apiItems = (reservationsQuery.data.value?.items ?? []).map((r) => {
    // 15-minute automatic No-Show rule for API reservations
    if (r.status === 'CONFIRMED' && !r.checkedInAt) {
      const startMs = new Date(r.startsAt).getTime();
      if (now - startMs > 15 * 60 * 1000) {
        void autoNoShowMutation.mutateAsync({ resId: r.id });
        return {
          ...r,
          status: 'NO_SHOW',
          notes: r.notes ? `${r.notes}\n[System]: Auto-marked No-Show after 15 min.` : '[System]: Auto-marked No-Show after 15 min.',
        };
      }
    }
    return r;
  });

  const localItems = reservationsStore.reservations.value.filter(
    (r) =>
      !selectedBranchId.value ||
      r.branchId === selectedBranchId.value ||
      r.branchName.toLowerCase() === selectedBranch.value?.name.toLowerCase()
  );

  const combined = [...apiItems];
  for (const loc of localItems) {
    if (!combined.some((c) => c.id === loc.id)) {
      // 15-minute auto No-Show rule for local items
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

function formatFriendlyDateTime(isoStr: string): string {
  if (!isoStr) return '';
  const d = new Date(isoStr);
  return `${d.toLocaleDateString(locale.value === 'th-TH' ? 'th-TH' : 'en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  })} • ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
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
  if (mins < 60) return `${mins} ${t('mins')}`;
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return m > 0 ? `${h} ${t('hr')} ${m} ${t('mins')}` : `${h} ${t('Hours')}`;
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

// Staff Snack Tab Ordering (for counter items added during session)
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
    'Added by staff at counter'
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

// Check In Mutation
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
    activeTab.value = 'FLOOR';
  } finally {
    actionInProgress.value = null;
  }
}

// Cancel Modal (Customer requested cancel)
const modalAction = ref<'CANCEL' | null>(null);
const targetReservation = ref<Reservation | null>(null);
const modalReason = ref<string>('');

function openCancelModal(res: Reservation) {
  targetReservation.value = res;
  modalAction.value = 'CANCEL';
  modalReason.value = 'Customer called to cancel';
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
      err?.response?.data?.message || err?.message || 'Cancel failed.';
  },
});

async function submitCancelAction() {
  if (!targetReservation.value || !modalAction.value) return;
  actionError.value = '';
  reservationsStore.cancelReservation(targetReservation.value.id, modalReason.value);
  await cancelMutation.mutateAsync({
    resId: targetReservation.value.id,
    reason: modalReason.value,
  });
}

// Staff Walk-in Modal (Instant Seating - NO Pre-Order Snacks)
const showWalkInModal = ref(false);
const walkInDuration = ref(2);
const walkInPartySize = ref(4);
const walkInCustomerName = ref('');
const walkInCountryCode = ref('+66');
const walkInPhone = ref('');
const walkInSelectedTable = ref<TableAvailability | null>(null);
const walkInError = ref('');

const walkInStartTimestamp = computed(() => {
  return new Date().toISOString();
});

const walkInEndTimestamp = computed(() => {
  const dt = new Date();
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
  enabled: computed(() => showWalkInModal.value && !!selectedBranchId.value),
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

  const startsAtIso = new Date().toISOString();
  const endsAtIso = new Date(Date.now() + walkInDuration.value * 3600000).toISOString();

  const rateMinor = (walkInSelectedTable.value.hourlyRate as any)?.amountMinor;
  const rate = rateMinor ? Number(rateMinor) / 100 : 200;

  // Immediate walk-in session seated at the counter
  reservationsStore.startWalkInSession({
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

  try {
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
      notes: 'Counter Walk-in Session',
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
  if (locale.value === 'th-TH') {
    switch (status) {
      case 'CONFIRMED':
        return 'ยืนยันแล้ว';
      case 'CHECKED_IN':
        return 'กำลังเล่นอยู่';
      case 'COMPLETED':
        return 'เสร็จสิ้น';
      case 'CANCELLED':
        return 'ยกเลิกแล้ว';
      case 'NO_SHOW':
        return 'ไม่มาตามนัด (อัตโนมัติ)';
      case 'PENDING':
        return 'รอยืนยัน';
      default:
        return t(status);
    }
  }
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
      return 'Auto No-Show';
    case 'PENDING':
      return 'Pending';
    default:
      return status
        .replace(/_/g, ' ')
        .toLowerCase()
        .replace(/\b\w/g, (c) => c.toUpperCase());
  }
}
</script>

<template>
  <div class="staff-console-page">
    <div class="container">
      <!-- Elevated Header Block on Green Canvas -->
      <header class="console-header-block">
        <div class="header-titles">
          <div class="header-eyebrow">
            <span class="gold-sparkle">✦</span>
            <span>THE PARLOR · BRANCH OPERATIONS CONSOLE</span>
          </div>
          <h1 class="page-title">Branch Floor & Reservations</h1>
          <p class="page-subtitle">
            Manage live table sessions, seat walk-in guests, log refreshments, and check out bills.
          </p>
        </div>

        <div class="header-controls">
          <div class="branch-picker">
            <label for="branch-selector" class="picker-label">{{ t('Branch') }}:</label>
            <select id="branch-selector" v-model="selectedBranchId" class="branch-select-input">
              <option v-for="b in branches" :key="b.id" :value="b.id">
                {{ b.name }} Branch
              </option>
            </select>
          </div>

          <button
            type="button"
            class="btn btn-primary add-res-btn"
            @click="showWalkInModal = true"
          >
            + Seat Walk-in Table
          </button>
        </div>
      </header>

      <!-- Operational View Switcher Block -->
      <div class="filters-card">
        <div class="filter-group">
          <span class="filter-label">View:</span>
          <div class="status-pills" role="tablist">
            <button
              type="button"
              role="tab"
              :aria-selected="activeTab === 'FLOOR'"
              class="pill-btn"
              :class="{ active: activeTab === 'FLOOR' }"
              @click="activeTab = 'FLOOR'"
            >
              Active Tables & Floor
              <span v-if="activeFloorReservations.length > 0" class="count-badge">
                {{ activeFloorReservations.length }}
              </span>
            </button>

            <button
              type="button"
              role="tab"
              :aria-selected="activeTab === 'UPCOMING'"
              class="pill-btn"
              :class="{ active: activeTab === 'UPCOMING' }"
              @click="activeTab = 'UPCOMING'"
            >
              Upcoming Bookings
              <span v-if="upcomingReservations.length > 0" class="count-badge">
                {{ upcomingReservations.length }}
              </span>
            </button>

            <button
              type="button"
              role="tab"
              :aria-selected="activeTab === 'HISTORY'"
              class="pill-btn"
              :class="{ active: activeTab === 'HISTORY' }"
              @click="activeTab = 'HISTORY'"
            >
              All Records & History
            </button>
          </div>
        </div>

        <div class="shift-stats-box">
          <span class="stat-item">
            <strong>{{ activeFloorReservations.length }}</strong> Playing
          </span>
          <span class="stat-sep">·</span>
          <span class="stat-item">
            <strong>{{ upcomingReservations.length }}</strong> Booked
          </span>
          <span class="stat-sep">·</span>
          <span class="stat-item">
            <strong>{{ completedReservations.length }}</strong> Finished
          </span>
        </div>
      </div>

      <!-- Error Alert -->
      <div v-if="actionError" class="alert-box" role="alert">
        {{ actionError }}
      </div>

      <!-- VIEW 1: ACTIVE TABLES & LIVE FLOOR -->
      <section v-if="activeTab === 'FLOOR'" class="content-section">
        <div v-if="activeFloorReservations.length === 0" class="empty-reservations-box">
          <div class="empty-icon-wrap">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <rect x="3" y="4" width="18" height="16" rx="2" />
              <line x1="3" y1="10" x2="21" y2="10" />
              <line x1="9" y1="4" x2="9" y2="20" />
            </svg>
          </div>
          <h3 class="empty-title">All Tables Are Available</h3>
          <p class="empty-text">
            No active gaming sessions currently running at {{ selectedBranch?.name || 'this branch' }}. Ready to seat walk-in guests or check in bookings!
          </p>
          <button
            type="button"
            class="btn btn-primary"
            @click="showWalkInModal = true"
          >
            + Seat Walk-in Table
          </button>
        </div>

        <div v-else class="reservations-grid">
          <article
            v-for="res in activeFloorReservations"
            :key="res.id"
            class="reservation-card status-checked_in"
          >
            <!-- Header: Table, Client Type, Status -->
            <div class="card-header">
              <div class="branch-meta">
                <span class="branch-name">
                  <span class="gold-bullet">◆</span>
                  Table {{ res.tableId }}
                </span>
                <span class="ref-badge">{{ res.partySize }} Players</span>
                <span v-if="isWalkIn(res)" class="client-type-tag walkin">
                  <span class="gold-bullet">◆</span> Walk-in Session
                </span>
                <span v-else class="client-type-tag reserved">
                  <span class="gold-bullet">◆</span> Reserved Client
                </span>
              </div>
              <span class="status-badge badge-checked_in">
                {{ formatStatus(res.status) }}
              </span>
            </div>

            <!-- Body: Play Time, Guest info, Snacks, Billing -->
            <div class="card-body">
              <!-- Live Played Timer Schedule Block -->
              <div class="schedule-block">
                <div class="date-main">
                  <svg class="schedule-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10" />
                    <polyline points="12 6 12 12 16 14" />
                  </svg>
                  <span class="schedule-text">
                    {{ formatPlayedDuration(getElapsedMinutes(res)) }} played
                  </span>
                  <span class="schedule-sub">
                    (Checked in at {{ formatTime(res.checkedInAt || res.startsAt) }})
                  </span>
                </div>

                <div class="iso-timestamps-box">
                  <span class="iso-label">Hourly Table Fee:</span>
                  <span class="fee-text">
                    ฿{{ res.hourlyRate || 200 }}/hr · Current play charge: <strong>฿{{ calculateCurrentPlayFee(res) }}</strong>
                  </span>
                </div>
              </div>

              <!-- Guest details row -->
              <div class="details-row">
                <div class="detail-item">
                  <span class="detail-lbl">Customer:</span>
                  <span class="detail-val table-highlight">{{ getCustomerName(res.customer) }}</span>
                </div>
                <div v-if="getCustomerPhone(res.customer)" class="detail-item">
                  <span class="detail-lbl">Phone:</span>
                  <span class="detail-val">{{ getCustomerPhone(res.customer) }}</span>
                </div>
              </div>

              <!-- Running Snack & Refreshment Tab -->
              <div class="preorder-section">
                <div class="preorder-header">
                  <div class="preorder-title">
                    <span class="gold-bullet">◆</span>
                    <strong>Table Refreshment & Snack Tab</strong>
                  </div>
                  <span v-if="getTableSnackSummary(res).total > 0" class="discount-pill">
                    Tab: ฿{{ getTableSnackSummary(res).total }}
                  </span>
                </div>

                <div v-if="getTableSnackSummary(res).items.length > 0" class="preorder-items-list">
                  <div
                    v-for="(it, idx) in getTableSnackSummary(res).items"
                    :key="idx"
                    class="preorder-item-row"
                  >
                    <span class="item-name-qty">
                      <span class="qty-badge">{{ it.quantity }}x</span>
                      {{ it.name }}
                    </span>
                    <span class="item-calc-price">
                      ฿{{ it.price * it.quantity }}
                    </span>
                  </div>
                </div>
                <p v-else class="no-snacks-text">
                  No packaged refreshments added to this table tab yet.
                </p>
              </div>

              <div v-if="res.notes" class="notes-box">
                <span class="notes-tag">Notes:</span>
                <p class="notes-text">{{ res.notes }}</p>
              </div>
            </div>

            <!-- Card Footer: Operational Actions -->
            <div class="card-footer">
              <div class="inplay-live-actions">
                <button
                  type="button"
                  class="btn btn-secondary"
                  @click="openStaffAddSnacks(res)"
                >
                  + Add Snacks & Drinks
                </button>
                <button
                  type="button"
                  class="btn btn-primary"
                  @click="openStaffBill(res)"
                >
                  Check Bill & Settle ✓
                </button>
              </div>
            </div>
          </article>
        </div>
      </section>

      <!-- VIEW 2: UPCOMING BOOKINGS -->
      <section v-if="activeTab === 'UPCOMING'" class="content-section">
        <div v-if="upcomingReservations.length === 0" class="empty-reservations-box">
          <div class="empty-icon-wrap">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <rect x="3" y="4" width="18" height="18" rx="2" />
              <line x1="16" y1="2" x2="16" y2="6" />
              <line x1="8" y1="2" x2="8" y2="6" />
              <line x1="3" y1="10" x2="21" y2="10" />
            </svg>
          </div>
          <h3 class="empty-title">No Upcoming Reservations</h3>
          <p class="empty-text">
            No further reservations are scheduled for today at {{ selectedBranch?.name || 'this branch' }}.
          </p>
        </div>

        <div v-else class="reservations-grid">
          <article
            v-for="res in upcomingReservations"
            :key="res.id"
            class="reservation-card status-confirmed"
          >
            <div class="card-header">
              <div class="branch-meta">
                <span class="branch-name">
                  <span class="gold-bullet">◆</span>
                  Table {{ res.tableId }}
                </span>
                <span class="ref-badge">Ref: {{ res.id }}</span>
                <span class="client-type-tag reserved">
                  <span class="gold-bullet">◆</span> Reserved Client
                </span>
                <span class="auto-noshow-pill">
                  Auto No-Show after 15m
                </span>
              </div>
              <span class="status-badge badge-confirmed">
                {{ formatStatus(res.status) }}
              </span>
            </div>

            <div class="card-body">
              <div class="schedule-block">
                <div class="date-main">
                  <svg class="schedule-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10" />
                    <polyline points="12 6 12 12 16 14" />
                  </svg>
                  <span class="schedule-text">
                    {{ formatFriendlyDateTime(res.startsAt) }} – {{ formatTime(res.endsAt) }}
                  </span>
                </div>
              </div>

              <div class="details-row">
                <div class="detail-item">
                  <span class="detail-lbl">Customer:</span>
                  <span class="detail-val table-highlight">{{ getCustomerName(res.customer) }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-lbl">Party Size:</span>
                  <span class="detail-val">{{ res.partySize }} guests</span>
                </div>
                <div v-if="getCustomerPhone(res.customer)" class="detail-item">
                  <span class="detail-lbl">Phone:</span>
                  <span class="detail-val">{{ getCustomerPhone(res.customer) }}</span>
                </div>
              </div>

              <div v-if="res.notes" class="notes-box">
                <span class="notes-tag">Pre-order / Notes:</span>
                <p class="notes-text">{{ res.notes }}</p>
              </div>
            </div>

            <div class="card-footer">
              <div class="inplay-live-actions">
                <button
                  type="button"
                  class="btn btn-primary"
                  :disabled="actionInProgress === res.id"
                  @click="handleCheckIn(res)"
                >
                  Check In Guest ✓
                </button>
                <button
                  type="button"
                  class="btn btn-secondary"
                  @click="openCancelModal(res)"
                >
                  Cancel
                </button>
              </div>
            </div>
          </article>
        </div>
      </section>

      <!-- VIEW 3: ALL RECORDS & HISTORY -->
      <section v-if="activeTab === 'HISTORY'" class="content-section">
        <div class="history-filter-bar">
          <div class="status-pills">
            <button
              v-for="st in ['ALL', 'CONFIRMED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'NO_SHOW']"
              :key="st"
              type="button"
              class="pill-btn"
              :class="{ active: historyFilterStatus === st }"
              @click="historyFilterStatus = st"
            >
              {{ st === 'ALL' ? 'All Statuses' : formatStatus(st) }}
            </button>
          </div>

          <input
            v-model="historySearchQuery"
            type="search"
            placeholder="Search by customer or table..."
            class="branch-select-input search-input"
          />
        </div>

        <div v-if="filteredHistoryReservations.length === 0" class="empty-reservations-box">
          <p class="empty-text">No records match the current status filter or query.</p>
        </div>

        <div v-else class="reservations-grid">
          <article
            v-for="res in filteredHistoryReservations"
            :key="res.id"
            class="reservation-card"
            :class="`status-${res.status.toLowerCase()}`"
          >
            <div class="card-header">
              <div class="branch-meta">
                <span class="branch-name">
                  <span class="gold-bullet">◆</span>
                  Table {{ res.tableId }}
                </span>
                <span class="ref-badge">Ref: {{ res.id }}</span>
                <span v-if="isWalkIn(res)" class="client-type-tag walkin">Walk-in</span>
                <span v-else class="client-type-tag reserved">Reserved</span>
              </div>
              <span class="status-badge" :class="`badge-${res.status.toLowerCase()}`">
                {{ formatStatus(res.status) }}
              </span>
            </div>

            <div class="card-body">
              <div class="schedule-block">
                <div class="date-main">
                  <span class="schedule-text">
                    {{ formatFriendlyDateTime(res.startsAt) }} – {{ formatTime(res.endsAt) }}
                  </span>
                </div>
              </div>

              <div class="details-row">
                <div class="detail-item">
                  <span class="detail-lbl">Customer:</span>
                  <span class="detail-val">{{ getCustomerName(res.customer) }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-lbl">Party:</span>
                  <span class="detail-val">{{ res.partySize }} guests</span>
                </div>
                <div v-if="getCustomerPhone(res.customer)" class="detail-item">
                  <span class="detail-lbl">Phone:</span>
                  <span class="detail-val">{{ getCustomerPhone(res.customer) }}</span>
                </div>
              </div>

              <div v-if="res.notes" class="notes-box">
                <span class="notes-tag">Notes:</span>
                <p class="notes-text">{{ res.notes }}</p>
              </div>
            </div>

            <div class="card-footer">
              <div class="inplay-live-actions">
                <button
                  v-if="res.status === 'CHECKED_IN'"
                  type="button"
                  class="btn btn-primary"
                  @click="openStaffBill(res)"
                >
                  Check Bill & Settle ✓
                </button>
                <button
                  v-else
                  type="button"
                  class="btn btn-secondary"
                  @click="openStaffBill(res)"
                >
                  View Bill Summary
                </button>
              </div>
            </div>
          </article>
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

      <!-- Cancel Confirmation Modal -->
      <Transition name="fade">
        <div v-if="modalAction" class="modal-overlay" @click.self="modalAction = null">
          <div class="modal-card">
            <h3 class="modal-heading">Cancel Reservation</h3>
            <p class="modal-desc">
              Cancel Reservation Ref <strong>{{ targetReservation?.id }}</strong> at
              {{ selectedBranch?.name }}. Table will be made available immediately.
            </p>

            <div class="form-group">
              <label for="action-reason" class="modal-label">Cancellation reason:</label>
              <input
                id="action-reason"
                v-model="modalReason"
                type="text"
                class="modal-input"
                required
              />
            </div>

            <div class="modal-buttons">
              <button type="button" class="btn btn-secondary" @click="modalAction = null">
                Keep Reservation
              </button>
              <button
                type="button"
                class="btn btn-danger"
                :disabled="cancelMutation.isPending.value"
                @click="submitCancelAction"
              >
                Confirm Cancellation
              </button>
            </div>
          </div>
        </div>
      </Transition>

      <!-- Staff Walk-in Seating Modal (NO PRE-ORDER SNACKS) -->
      <Transition name="fade">
        <div v-if="showWalkInModal" class="modal-overlay" @click.self="showWalkInModal = false">
          <div class="modal-card walkin-card">
            <div class="walkin-header">
              <div class="header-eyebrow">
                <span class="gold-sparkle">✦</span>
                <span>INSTANT WALK-IN SEATING</span>
              </div>
              <h3 class="modal-heading">Seat Walk-in Table</h3>
              <p class="modal-desc">
                Select an available table to begin playing immediately. Drinks & snacks can be ordered at the counter during the session.
              </p>
            </div>

            <div class="walkin-body">
              <div class="form-section">
                <label class="modal-label">Select Available Table at {{ selectedBranch?.name }}:</label>
                <div v-if="walkInTables.length === 0" class="no-tables">
                  Checking table availability...
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
                    <div class="tbl-name">{{ tbl.label }}</div>
                    <div class="tbl-cap">Up to {{ tbl.capacity }} players</div>
                    <div class="tbl-rate">
                      ฿{{ (tbl.hourlyRate as any)?.amountMinor ? ((tbl.hourlyRate as any).amountMinor / 100) : 200 }}/hr
                    </div>
                  </button>
                </div>
              </div>

              <div class="form-row-2">
                <div class="form-field">
                  <label class="modal-label">Players in Group:</label>
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
                  <label class="modal-label">Expected Duration:</label>
                  <select v-model.number="walkInDuration" class="branch-select-input full-width">
                    <option :value="1">1 Hour</option>
                    <option :value="2">2 Hours</option>
                    <option :value="3">3 Hours</option>
                    <option :value="4">4 Hours</option>
                  </select>
                </div>
              </div>

              <div class="form-section">
                <label class="modal-label">Guest Name (Optional):</label>
                <input
                  v-model="walkInCustomerName"
                  type="text"
                  placeholder="Walk-in Guests"
                  class="modal-input"
                />
              </div>

              <div v-if="walkInError" class="alert-box">{{ walkInError }}</div>
            </div>

            <div class="modal-buttons">
              <button type="button" class="btn btn-secondary" @click="showWalkInModal = false">
                Cancel
              </button>
              <button
                type="button"
                class="btn btn-primary"
                :disabled="!walkInSelectedTable || staffCreateMutation.isPending.value"
                @click="handleStaffBooking"
              >
                <span v-if="staffCreateMutation.isPending.value">Starting Table...</span>
                <span v-else>Seat & Start Playing Now →</span>
              </button>
            </div>
          </div>
        </div>
      </Transition>

      <!-- Staff Quick Add Snacks Modal -->
      <Transition name="fade">
        <div v-if="staffSnackReservation" class="modal-overlay" @click.self="staffSnackReservation = null">
          <div class="modal-card snack-card">
            <div class="walkin-header">
              <div class="header-eyebrow">
                <span class="gold-sparkle">✦</span>
                <span>TABLE TAB SERVICE</span>
              </div>
              <h3 class="modal-heading">Add Snacks & Drinks</h3>
              <p class="modal-desc">
                Table {{ staffSnackReservation.tableId }} · {{ getCustomerName(staffSnackReservation.customer) }}
              </p>
            </div>

            <div class="snack-list-body">
              <div
                v-for="item in FOOD_MENU_ITEMS"
                :key="item.id"
                class="snack-row"
              >
                <div class="snack-meta">
                  <span class="snack-name">{{ item.name }}</span>
                  <span class="snack-price">฿{{ item.price }}</span>
                </div>
                <div class="stepper-wrap compact">
                  <button
                    type="button"
                    class="step-btn small"
                    :disabled="getStaffSnackQty(item.id) <= 0"
                    @click="adjustStaffSnackQty(item, -1)"
                  >
                    -
                  </button>
                  <span class="step-val small">{{ getStaffSnackQty(item.id) }}</span>
                  <button
                    type="button"
                    class="step-btn small"
                    @click="adjustStaffSnackQty(item, 1)"
                  >
                    +
                  </button>
                </div>
              </div>
            </div>

            <div class="snack-footer-row">
              <div class="snack-total">
                <span>Selected:</span>
                <strong class="total-value">฿{{ staffSnackSubtotal }}</strong>
              </div>
              <div class="modal-buttons">
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
      </Transition>
    </div>
  </div>
</template>

<style scoped>
/* Signature Deep Parlor Forest Green Background */
.staff-console-page {
  background: #142a1f;
  min-height: calc(100vh - 80px);
  padding: 36px 16px 80px;
  color: #172b21;
  font-family: 'FC Mee Sakul', 'Plus Jakarta Sans', system-ui, Arial, sans-serif;
}

.container {
  max-width: 960px;
  margin: 0 auto;
}

/* Elevated White Blocks on Green Background */
.console-header-block {
  background: #ffffff;
  border: 1.5px solid #c6ad7a;
  border-radius: 12px;
  padding: 24px 28px;
  margin-bottom: 24px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.28);
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}

.header-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: #8c7649;
  margin-bottom: 6px;
}

.gold-sparkle {
  color: #c6ad7a;
  font-size: 14px;
}

.page-title {
  margin: 0 0 6px;
  font-size: 28px;
  font-weight: 800;
  color: #142b20;
  letter-spacing: -0.02em;
}

.page-subtitle {
  margin: 0;
  font-size: 14.5px;
  color: #554e42;
  line-height: 1.5;
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.branch-picker {
  display: flex;
  align-items: center;
  gap: 8px;
}

.picker-label {
  font-size: 13px;
  font-weight: 700;
  color: #142b20;
}

.branch-select-input {
  padding: 8px 12px;
  border: 1px solid #dcd1be;
  border-radius: 6px;
  background: #ffffff;
  font-size: 13.5px;
  color: #142b20;
  font-weight: 700;
  outline: none;
}

.branch-select-input.full-width {
  width: 100%;
}

.add-res-btn {
  white-space: nowrap;
}

/* Buttons */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 9px 18px;
  border-radius: 8px;
  font-size: 13.5px;
  font-weight: 800;
  cursor: pointer;
  text-decoration: none;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.btn-primary {
  background: #142b20;
  border: 1px solid #c6ad7a;
  color: #ffd98a;
  box-shadow: 0 2px 8px rgba(20, 43, 32, 0.2);
}

.btn-primary:hover {
  background: #1c3d2e;
  border-color: #dfc18c;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: #f7f3ea;
  border: 1px solid #ded5c3;
  color: #4b4435;
}

.btn-secondary:hover {
  background: #ece4d2;
}

.btn-danger {
  background: #dc2626;
  border: 1px solid #b91c1c;
  color: #ffffff;
}

/* Filter Card Block */
.filters-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border: 1.5px solid #ded5c3;
  border-radius: 12px;
  padding: 14px 20px;
  margin-bottom: 24px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.22);
  gap: 16px;
  flex-wrap: wrap;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-label {
  font-size: 13px;
  font-weight: 700;
  color: #142b20;
}

.status-pills {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.pill-btn {
  background: #f7f3ea;
  border: 1px solid #dcd1be;
  border-radius: 20px;
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  color: #4b4435;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.pill-btn:hover {
  background: #eee4d1;
  border-color: #baa67c;
}

.pill-btn.active {
  background: #142b20;
  border-color: #c6ad7a;
  color: #ffd98a;
  box-shadow: 0 2px 8px rgba(20, 43, 32, 0.25);
}

.count-badge {
  background: #c6ad7a;
  color: #142b20;
  font-size: 10px;
  font-weight: 800;
  padding: 1px 6px;
  border-radius: 10px;
}

.shift-stats-box {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #60594a;
}

.stat-item strong {
  color: #142b20;
}

.stat-sep {
  color: #c6ad7a;
}

/* Alert Box */
.alert-box {
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #b91c1c;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 20px;
  font-size: 13.5px;
}

/* Empty Box */
.empty-reservations-box {
  background: #ffffff;
  border: 1.5px dashed #c6ad7a;
  border-radius: 12px;
  padding: 52px 20px;
  text-align: center;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.25);
}

.empty-icon-wrap {
  color: #c6ad7a;
  margin-bottom: 12px;
}

.empty-title {
  margin: 0 0 8px;
  font-size: 19px;
  font-weight: 800;
  color: #142b20;
}

.empty-text {
  margin: 0 0 20px;
  font-size: 14px;
  color: #655e4e;
}

/* Reservations Grid & Cards */
.reservations-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.reservation-card {
  background: #ffffff;
  border: 1.5px solid #ded5c3;
  border-radius: 12px;
  padding: 22px 24px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.22);
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

.reservation-card:hover {
  border-color: #c6ad7a;
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.3), 0 0 12px rgba(198, 173, 122, 0.16);
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0e8db;
  padding-bottom: 14px;
  gap: 12px;
  flex-wrap: wrap;
}

.branch-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.branch-name {
  font-size: 17px;
  font-weight: 800;
  color: #142b20;
  display: flex;
  align-items: center;
  gap: 6px;
}

.gold-bullet {
  color: #c6ad7a;
  font-size: 12px;
}

.ref-badge {
  font-size: 11px;
  color: #6a6252;
  font-family: monospace;
  background: #f4ecdc;
  border: 1px solid #dcd1be;
  padding: 3px 8px;
  border-radius: 4px;
  font-weight: 600;
}

.client-type-tag {
  font-size: 11.5px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.client-type-tag.walkin {
  background: #f5f0fb;
  border: 1px solid #dcd0f0;
  color: #6932a8;
}

.client-type-tag.reserved {
  background: #f0f7f2;
  border: 1px solid #c8e6d2;
  color: #145e31;
}

.auto-noshow-pill {
  font-size: 11px;
  color: #b45309;
  background: #fef3c7;
  border: 1px solid #fde68a;
  padding: 2px 7px;
  border-radius: 4px;
  font-weight: 700;
}

/* Status Badges */
.status-badge {
  font-size: 12px;
  font-weight: 700;
  padding: 4px 12px;
  border-radius: 14px;
  letter-spacing: 0.02em;
  text-transform: none;
}

.badge-confirmed {
  background: #142b20;
  color: #ffd98a;
  border: 1px solid #c6ad7a;
  box-shadow: 0 0 8px rgba(198, 173, 122, 0.3);
}

.badge-checked_in {
  background: #e0f2fe;
  color: #0369a1;
  border: 1px solid #bae6fd;
}

.badge-completed {
  background: #f1f5f9;
  color: #475569;
  border: 1px solid #cbd5e1;
}

.badge-cancelled {
  background: #fef2f2;
  color: #b91c1c;
  border: 1px solid #fecaca;
}

.badge-no_show {
  background: #ffedd5;
  color: #9a3412;
  border: 1px solid #fed7aa;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* Schedule Block */
.schedule-block {
  background: #fcfaf6;
  border: 1px solid #ded5c3;
  border-radius: 8px;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.date-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.schedule-icon {
  color: #c6ad7a;
}

.schedule-text {
  font-size: 15px;
  font-weight: 800;
  color: #142b20;
}

.schedule-sub {
  font-size: 12.5px;
  color: #6a6252;
}

.iso-timestamps-box {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  border-top: 1px dashed #e5dccb;
  padding-top: 6px;
  margin-top: 2px;
}

.iso-label {
  font-size: 11.5px;
  font-weight: 700;
  color: #7a705e;
}

.fee-text {
  font-size: 12.5px;
  color: #142b20;
}

/* Details Row */
.details-row {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
  padding: 0 4px;
}

.detail-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 13.5px;
}

.detail-lbl {
  color: #6a6252;
  font-weight: 600;
}

.detail-val {
  font-weight: 700;
  color: #142b20;
}

.table-highlight {
  color: #142b20;
  font-weight: 800;
}

/* Preorder / Tab Section */
.preorder-section {
  background: #fdfcf9;
  border: 1px solid #e0d5c1;
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.preorder-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border-bottom: 1px solid #f0e8dc;
  padding-bottom: 8px;
}

.preorder-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #142b20;
}

.discount-pill {
  font-size: 11px;
  font-weight: 800;
  background: #c6ad7a;
  color: #142b20;
  padding: 2px 8px;
  border-radius: 10px;
}

.preorder-items-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.preorder-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}

.item-name-qty {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #172b21;
}

.qty-badge {
  font-size: 11px;
  font-weight: 800;
  background: #142b20;
  color: #ffd98a;
  padding: 1px 6px;
  border-radius: 4px;
}

.item-calc-price {
  font-weight: 700;
  color: #142b20;
}

.no-snacks-text {
  margin: 0;
  font-size: 12px;
  color: #8c8270;
  font-style: italic;
}

.notes-box {
  background: #fcf9f2;
  border-left: 3px solid #c6ad7a;
  padding: 10px 14px;
  border-radius: 4px;
}

.notes-tag {
  font-size: 11px;
  font-weight: 800;
  color: #8c7649;
  display: block;
  margin-bottom: 2px;
}

.notes-text {
  margin: 0;
  font-size: 13px;
  color: #383226;
}

/* Card Footer */
.card-footer {
  border-top: 1px solid #f0e8db;
  padding-top: 14px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.inplay-live-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

/* History search bar */
.history-filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.search-input {
  min-width: 240px;
}

/* Modals */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(10, 24, 17, 0.75);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  z-index: 1000;
}

.modal-card {
  background: #ffffff;
  border: 1.5px solid #c6ad7a;
  border-radius: 12px;
  padding: 28px 24px;
  max-width: 480px;
  width: 100%;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.35);
}

.walkin-card {
  max-width: 580px;
}

.snack-card {
  max-width: 480px;
}

.walkin-header {
  margin-bottom: 16px;
}

.modal-heading {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 800;
  color: #142b20;
}

.modal-desc {
  margin: 0 0 16px;
  font-size: 13.5px;
  color: #60594a;
}

.modal-label {
  display: block;
  font-size: 12.5px;
  font-weight: 700;
  color: #142b20;
  margin-bottom: 6px;
}

.modal-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #dcd1be;
  border-radius: 6px;
  font-size: 13.5px;
  box-sizing: border-box;
}

.modal-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

/* Walkin Table Selector Grid */
.tables-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
}

.tbl-select-card {
  background: #fbf9f4;
  border: 1.5px solid #dcd1be;
  border-radius: 8px;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.tbl-select-card:hover {
  background: #f5eedf;
  border-color: #baa67c;
}

.tbl-select-card.active {
  background: #142b20;
  border-color: #c6ad7a;
}

.tbl-select-card.active .tbl-name {
  color: #ffd98a;
}

.tbl-select-card.active .tbl-cap {
  color: #e5dccb;
}

.tbl-select-card.active .tbl-rate {
  color: #ffd98a;
}

.tbl-name {
  font-size: 14px;
  font-weight: 800;
  color: #142b20;
}

.tbl-cap {
  font-size: 11.5px;
  color: #6a6252;
}

.tbl-rate {
  font-size: 12px;
  font-weight: 700;
  color: #145e31;
  margin-top: 2px;
}

.form-row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin: 16px 0;
}

.stepper-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
}

.step-btn {
  width: 36px;
  height: 36px;
  border-radius: 6px;
  border: 1px solid #dcd1be;
  background: #f7f3ea;
  font-size: 18px;
  font-weight: 700;
  cursor: pointer;
}

.step-btn.small {
  width: 28px;
  height: 28px;
  font-size: 14px;
}

.step-val {
  font-size: 16px;
  font-weight: 800;
  color: #142b20;
  min-width: 20px;
  text-align: center;
}

.step-val.small {
  font-size: 13.5px;
}

/* Snack Modal List */
.snack-list-body {
  max-height: 320px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.snack-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #fdfcf9;
  border: 1px solid #eee4d3;
  border-radius: 6px;
}

.snack-name {
  font-size: 13.5px;
  font-weight: 700;
  color: #142b20;
  display: block;
}

.snack-price {
  font-size: 12px;
  font-weight: 700;
  color: #8c7649;
}

.snack-footer-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #f0e8db;
  padding-top: 14px;
}

.snack-total {
  font-size: 13.5px;
  color: #60594a;
}

.total-value {
  color: #142b20;
  font-size: 18px;
  margin-left: 6px;
}
</style>

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
  type Branch,
  type Reservation,
} from '@theparlor/api-client';
import TableBillModal from '../components/TableBillModal.vue';
import { useCustomerReservationsStore } from '../data/reservations-store';
import { FOOD_MENU_ITEMS } from '../data/food-menu';
import { useAuthStore } from '../../../shared/stores/auth';

const queryClient = useQueryClient();
const auth = useAuthStore();
const reservationsStore = useCustomerReservationsStore();

// Sidebar state
const isSidebarCollapsed = ref(false);
const activeSidebarItem = ref<'dashboard' | 'floor' | 'sessions' | 'history'>('dashboard');

// Branch & Search
const selectedBranchId = ref<string>('');
const searchQuery = ref<string>('');
const currentTimeStr = ref(new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));

// Time of day greeting
const greeting = computed(() => {
  const hr = new Date().getHours();
  if (hr < 12) return 'Good morning';
  if (hr < 17) return 'Good afternoon';
  return 'Good evening';
});

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
  // Check if occupied right now
  const occupied = activeFloorReservations.value.find((r) => {
    const tId = String(r.tableId || '');
    return tId === table.id || tId.endsWith(`-${table.id}`) || tId.includes(`Table ${table.number}`);
  });
  if (occupied) {
    return {
      state: 'OCCUPIED' as const,
      reservation: occupied,
    };
  }

  // Check if reserved upcoming
  const reserved = upcomingReservations.value.find((r) => {
    const tId = String(r.tableId || '');
    return tId === table.id || tId.endsWith(`-${table.id}`) || tId.includes(`Table ${table.number}`);
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
  return branchFloorTables.filter((t) => getTableStatus(t).state === 'OCCUPIED').length;
});

const freeTablesCount = computed(() => {
  return branchFloorTables.length - occupiedTablesCount.value;
});

// "Needs You Now" alerts (Most urgent first)
const needsYouItems = computed(() => {
  const alerts: any[] = [];
  // 1. Long playing sessions without snacks
  for (const r of activeFloorReservations.value) {
    const elapsed = getElapsedMinutes(r);
    alerts.push({
      id: r.id,
      type: 'IN_PLAY',
      title: `Table ${r.tableId} in play for ${elapsed} min`,
      subtitle: `${r.partySize} guests · ${getCustomerName(r.customer)} · Table charge: ฿${calculateCurrentPlayFee(r)}`,
      reservation: r,
    });
  }

  // 2. Upcoming arrivals due soon or late
  for (const r of upcomingReservations.value) {
    const startMs = new Date(r.startsAt).getTime();
    const diffMins = Math.round((Date.now() - startMs) / 60000);
    if (diffMins > 0) {
      alerts.push({
        id: r.id,
        type: 'LATE',
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
  const end = res.completedAt ? new Date(res.completedAt).getTime() : Date.now();
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
  mutationFn: async ({ resId, reason }: { resId: string; reason: string }) => {
    return cancelReservation(selectedBranchId.value, resId, { reason });
  },
  onSuccess: () => {
    showCancelModal.value = false;
    cancelTargetRes.value = null;
    void queryClient.invalidateQueries({ queryKey: ['listBranchReservations'] });
  },
  onError: (err: any) => {
    actionError.value =
      err?.response?.data?.message || err?.message || 'Cancel failed.';
  },
});

async function submitCancel() {
  if (!cancelTargetRes.value) return;
  actionError.value = '';
  reservationsStore.cancelReservation(cancelTargetRes.value.id, cancelReason.value);
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
    void queryClient.invalidateQueries({ queryKey: ['listBranchReservations'] });
  },
  onError: (err: any) => {
    walkInError.value =
      err?.response?.data?.message || err?.message || 'Staff booking failed.';
  },
});

async function handleStaffWalkInSubmit() {
  walkInError.value = '';
  const matchedTable = branchFloorTables.find((t) => t.id === walkInTableId.value) || branchFloorTables[0];
  const startsAtIso = new Date().toISOString();
  const endsAtIso = new Date(Date.now() + walkInDuration.value * 3600000).toISOString();

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

// Staff Snack Tab Ordering
const staffSnackReservation = ref<any>(null);
const staffSnackQuantities = ref<Record<string, number>>({});

function openAddSnacks(res: any) {
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

function openCheckBill(res: any) {
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

function refreshAll() {
  currentTimeStr.value = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  void queryClient.invalidateQueries({ queryKey: ['listBranchReservations'] });
}
</script>

<template>
  <div class="gl-admin-layout">
    <!-- Left Navigation Sidebar -->
    <aside class="gl-sidebar" :class="{ collapsed: isSidebarCollapsed }">
      <div class="gl-sidebar-brand">
        <div class="brand-dice-icon">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
            <rect x="2" y="2" width="20" height="20" rx="5" fill="#111827"/>
            <circle cx="7" cy="7" r="1.8" fill="#ffffff"/>
            <circle cx="17" cy="7" r="1.8" fill="#ffffff"/>
            <circle cx="12" cy="12" r="1.8" fill="#ffffff"/>
            <circle cx="7" cy="17" r="1.8" fill="#ffffff"/>
            <circle cx="17" cy="17" r="1.8" fill="#ffffff"/>
          </svg>
        </div>
        <div v-if="!isSidebarCollapsed" class="brand-text-col">
          <span class="brand-sub">ADMIN CONSOLE</span>
          <span class="brand-name">The Parlor</span>
        </div>
      </div>

      <button
        type="button"
        class="sidebar-collapse-btn"
        @click="isSidebarCollapsed = !isSidebarCollapsed"
      >
        <span>{{ isSidebarCollapsed ? '»' : '« Collapse' }}</span>
      </button>

      <!-- Nav Menu Items -->
      <nav class="sidebar-nav">
        <a
          href="#dashboard"
          class="nav-item active"
          @click.prevent="activeSidebarItem = 'dashboard'"
        >
          <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="7" height="7"/>
            <rect x="14" y="3" width="7" height="7"/>
            <rect x="14" y="14" width="7" height="7"/>
            <rect x="3" y="14" width="7" height="7"/>
          </svg>
          <span v-if="!isSidebarCollapsed">Dashboard</span>
        </a>

        <a
          href="#floor"
          class="nav-item"
          :class="{ active: activeSidebarItem === 'floor' }"
          @click.prevent="activeSidebarItem = 'floor'"
        >
          <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 3h18v18H3z"/>
            <path d="M3 9h18M9 21V9"/>
          </svg>
          <span v-if="!isSidebarCollapsed">Tables & Floor Plan</span>
        </a>

        <a
          href="#sessions"
          class="nav-item"
          :class="{ active: activeSidebarItem === 'sessions' }"
          @click.prevent="activeSidebarItem = 'sessions'"
        >
          <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <polyline points="12 6 12 12 16 14"/>
          </svg>
          <span v-if="!isSidebarCollapsed">Live Sessions</span>
        </a>

        <RouterLink :to="{ name: 'WorkspaceManagement' }" class="nav-item">
          <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="3"/>
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
          </svg>
          <span v-if="!isSidebarCollapsed">Branch Hub</span>
        </RouterLink>

        <RouterLink :to="{ name: 'HomeIndex' }" class="nav-item">
          <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
            <polyline points="9 22 9 12 15 12 15 22"/>
          </svg>
          <span v-if="!isSidebarCollapsed">Public Store</span>
        </RouterLink>
      </nav>

      <div v-if="!isSidebarCollapsed" class="sidebar-footer">
        <span class="help-lbl">Need help?</span>
        <a href="mailto:support@theparlor.com" class="help-link">support@theparlor.com</a>
      </div>
    </aside>

    <!-- Main Content Area -->
    <div class="gl-main-wrapper">
      <!-- Top Header Navigation Bar -->
      <header class="gl-topbar">
        <div class="branch-selector-capsule">
          <span class="pin-icon">✦</span>
          <select v-model="selectedBranchId" class="branch-select">
            <option v-for="b in branches" :key="b.id" :value="b.id">
              The Parlor · {{ b.name }} Branch
            </option>
          </select>
        </div>

        <div class="topbar-search">
          <svg class="search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/>
            <line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input
            v-model="searchQuery"
            type="search"
            placeholder="Search games or bookings..."
            class="topbar-search-input"
          />
        </div>

        <div class="topbar-actions">
          <button type="button" class="icon-circle-btn" title="Notifications">
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
              <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
            </svg>
          </button>

          <RouterLink :to="{ name: 'HomeIndex' }" class="icon-circle-btn" title="View Store">
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
              <line x1="8" y1="21" x2="16" y2="21"/>
              <line x1="12" y1="17" x2="12" y2="21"/>
            </svg>
          </RouterLink>

          <div class="user-avatar-badge" title="Logged in as Staff">
            {{ (auth.username || 'Staff').slice(0, 2).toUpperCase() }}
          </div>
        </div>
      </header>

      <!-- Dashboard Service Screen Content -->
      <main class="gl-content">
        <!-- Service Greeting Section -->
        <section class="service-hero">
          <div class="eyebrow-row">
            <span class="hero-eyebrow">
              TONIGHT AT THE PARLOR · {{ selectedBranch?.name.toUpperCase() || 'SIAM' }}
            </span>
          </div>

          <div class="title-row">
            <h1 class="hero-title">{{ greeting }}</h1>
            <span class="live-pill">
              <span class="live-pulse"></span>
              Bookings: Live
            </span>
          </div>

          <p class="hero-subtitle">
            {{ allReservations.length }} bookings tonight — first arrival {{ formatTime(upcomingReservations[0]?.startsAt) || '7:30 PM' }}, largest party of {{ Math.max(...branchFloorTables.map(t => t.seats)) }}, {{ needsYouItems.length }} item{{ needsYouItems.length === 1 ? '' : 's' }} need attention.
          </p>

          <!-- Hero Action Buttons Bar -->
          <div class="hero-actions-bar">
            <button
              type="button"
              class="btn-seat-walkin"
              @click="openWalkInForTable()"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="8.5" cy="7" r="4"/>
                <line x1="20" y1="8" x2="20" y2="14"/>
                <line x1="23" y1="11" x2="17" y2="11"/>
              </svg>
              <span>Seat walk-in</span>
            </button>

            <button
              type="button"
              class="btn-hero-pill"
              @click="openWalkInForTable()"
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
                <line x1="16" y1="2" x2="16" y2="6"/>
                <line x1="8" y1="2" x2="8" y2="6"/>
                <line x1="3" y1="10" x2="21" y2="10"/>
              </svg>
              <span>New booking</span>
            </button>

            <div class="actions-right">
              <span class="updated-time">Updated at {{ currentTimeStr }}</span>
              <button
                type="button"
                class="btn-refresh"
                @click="refreshAll"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M23 4v6h-6"/>
                  <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
                </svg>
                <span>Refresh</span>
              </button>
            </div>
          </div>
        </section>

        <!-- Numbers Metric Ribbon (4 Column Card) -->
        <section class="numbers-card-ribbon">
          <div class="num-col">
            <span class="col-label">TONIGHT</span>
            <div class="col-value">{{ allReservations.length }}</div>
            <div class="col-sub">party · {{ allReservations.reduce((s, r) => s + (r.partySize || 2), 0) }} guests</div>
          </div>

          <div class="num-col">
            <span class="col-label">TABLES IN USE</span>
            <div class="col-value">{{ occupiedTablesCount }} / {{ branchFloorTables.length }}</div>
            <div class="segmented-progress">
              <span
                v-for="(_, i) in branchFloorTables"
                :key="i"
                class="progress-seg"
                :class="{ active: i < occupiedTablesCount }"
              ></span>
            </div>
            <div class="col-sub">{{ freeTablesCount }} free now</div>
          </div>

          <div class="num-col">
            <span class="col-label">ARRIVING</span>
            <div class="col-value">{{ upcomingReservations.length }}</div>
            <div class="col-sub">in the next hour</div>
          </div>

          <div class="num-col">
            <span class="col-label">ACTIVE TABS</span>
            <div class="col-value">{{ activeFloorReservations.length }}</div>
            <div class="col-sub">running refreshments on tab</div>
          </div>
        </section>

        <!-- Needs You Now Alert Section -->
        <section v-if="needsYouItems.length > 0" class="needs-you-section">
          <div class="needs-you-header">
            <div class="needs-you-title">
              <span>Needs you now</span>
              <span class="needs-count-pill">{{ needsYouItems.length }}</span>
            </div>
            <span class="most-urgent">Most urgent first</span>
          </div>

          <div class="needs-you-card">
            <div
              v-for="item in needsYouItems"
              :key="item.id"
              class="needs-item-row"
            >
              <div class="needs-item-left">
                <div class="needs-icon-circle">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10"/>
                    <line x1="12" y1="8" x2="12" y2="12"/>
                    <line x1="12" y1="16" x2="12.01" y2="16"/>
                  </svg>
                </div>
                <div>
                  <div class="needs-item-title">{{ item.title }}</div>
                  <div class="needs-item-sub">{{ item.subtitle }}</div>
                </div>
              </div>

              <div class="needs-item-actions">
                <button
                  type="button"
                  class="btn-action-dark"
                  @click="openAddSnacks(item.reservation)"
                >
                  + Add Snacks
                </button>
                <button
                  type="button"
                  class="btn-action-light"
                  @click="openCheckBill(item.reservation)"
                >
                  Check Bill
                </button>
              </div>
            </div>
          </div>
        </section>

        <!-- Lower Two-Column Section: Floor Plan Grid (Left) & Coming Up Timeline (Right) -->
        <div class="service-lower-grid">
          <!-- Left Column: Floor · Live -->
          <section class="floor-section">
            <div class="section-top-row">
              <div class="section-title">
                <span>Floor</span>
                <span class="live-dot-green">● Live</span>
              </div>
              <span class="occupancy-stat">{{ occupiedTablesCount }} of {{ branchFloorTables.length }} in use</span>
            </div>

            <div class="floor-tables-grid">
              <div
                v-for="table in branchFloorTables"
                :key="table.id"
                class="floor-table-tile"
                :class="getTableStatus(table).state.toLowerCase()"
              >
                <div class="tile-header">
                  <span class="table-num">{{ table.number }}</span>
                  <span class="table-seats">{{ table.seats }} seats</span>
                </div>

                <!-- STATE 1: FREE -->
                <template v-if="getTableStatus(table).state === 'FREE'">
                  <div class="tile-status-tag free">FREE</div>
                  <div class="tile-timing">Rest of night</div>
                  <button
                    type="button"
                    class="btn-seat-here"
                    @click="openWalkInForTable(table)"
                  >
                    Seat here →
                  </button>
                </template>

                <!-- STATE 2: OCCUPIED (IN USE) -->
                <template v-else-if="getTableStatus(table).state === 'OCCUPIED'">
                  <div class="tile-status-tag occupied">OCCUPIED</div>
                  <div class="tile-guest-name">
                    {{ getCustomerName(getTableStatus(table).reservation?.customer) }}
                  </div>
                  <div class="tile-timing">
                    {{ getElapsedMinutes(getTableStatus(table).reservation) }}m played · ฿{{ calculateCurrentPlayFee(getTableStatus(table).reservation) }}
                  </div>
                  <div class="tile-actions-row">
                    <button
                      type="button"
                      class="btn-tile-sm"
                      @click="openAddSnacks(getTableStatus(table).reservation)"
                    >
                      + Snacks
                    </button>
                    <button
                      type="button"
                      class="btn-tile-sm primary"
                      @click="openCheckBill(getTableStatus(table).reservation)"
                    >
                      Bill
                    </button>
                  </div>
                </template>

                <!-- STATE 3: RESERVED UPCOMING -->
                <template v-else>
                  <div class="tile-status-tag reserved">RESERVED</div>
                  <div class="tile-guest-name">
                    {{ getCustomerName(getTableStatus(table).reservation?.customer) }}
                  </div>
                  <div class="tile-timing">
                    Due {{ formatTime(getTableStatus(table).reservation?.startsAt) }}
                  </div>
                  <button
                    type="button"
                    class="btn-seat-here arrive-btn"
                    @click="handleCheckIn(getTableStatus(table).reservation)"
                  >
                    Check In ✓
                  </button>
                </template>
              </div>
            </div>
          </section>

          <!-- Right Column: Coming up · Next 2 hours -->
          <section class="coming-up-section">
            <div class="section-top-row">
              <div class="section-title">
                <span>Coming up</span>
                <span class="time-window">Next 2 hours</span>
                <span class="count-tag">{{ upcomingReservations.length }} party</span>
              </div>
              <div class="policy-note">Auto No-Show if 15m late</div>
            </div>

            <div v-if="upcomingReservations.length === 0" class="empty-coming-up">
              <p>No upcoming arrivals scheduled in the next 2 hours.</p>
            </div>

            <div v-else class="upcoming-cards-stack">
              <div
                v-for="res in upcomingReservations"
                :key="res.id"
                class="coming-card"
              >
                <div class="coming-card-header">
                  <span class="arrival-time">{{ formatTime(res.startsAt) }}</span>
                  <div class="customer-info-line">
                    <strong class="cust-title">{{ getCustomerName(res.customer) }}</strong>
                    <span class="cust-meta">· {{ res.partySize }} guests · Table {{ res.tableId }}</span>
                  </div>
                </div>

                <div v-if="res.notes" class="coming-notes-pill">
                  📝 {{ res.notes }}
                </div>

                <div class="coming-card-actions">
                  <button
                    type="button"
                    class="btn-arrived"
                    :disabled="actionInProgress === res.id"
                    @click="handleCheckIn(res)"
                  >
                    ✓ Arrived
                  </button>
                  <button
                    type="button"
                    class="btn-seat-dark"
                    :disabled="actionInProgress === res.id"
                    @click="handleCheckIn(res)"
                  >
                    Seat Table
                  </button>
                  <button
                    type="button"
                    class="btn-cancel-link"
                    @click="openCancel(res)"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            </div>
          </section>
        </div>
      </main>

      <!-- Staff Table Bill & Settlement Modal -->
      <TableBillModal
        v-if="staffBillReservation"
        :reservation="staffBillReservation"
        :is-staff="true"
        @close="staffBillReservation = null"
        @settled="staffBillReservation = null; queryClient.invalidateQueries({ queryKey: ['listBranchReservations'] })"
      />

      <!-- Cancel Confirmation Modal -->
      <div v-if="showCancelModal" class="modal-backdrop" @click.self="showCancelModal = false">
        <div class="modal-card">
          <h3 class="modal-title">Cancel Reservation</h3>
          <p class="modal-sub">
            Cancel reservation for <strong>{{ getCustomerName(cancelTargetRes?.customer) }}</strong> at Table {{ cancelTargetRes?.tableId }}.
            Table will be made available immediately.
          </p>

          <div class="form-group">
            <label class="modal-label">Reason:</label>
            <input
              v-model="cancelReason"
              type="text"
              class="modal-input"
              required
            />
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="showCancelModal = false">
              Keep Booking
            </button>
            <button
              type="button"
              class="btn btn-danger"
              :disabled="cancelMutation.isPending.value"
              @click="submitCancel"
            >
              Confirm Cancellation
            </button>
          </div>
        </div>
      </div>

      <!-- Quick Walk-in Seating Modal (INSTANT - NO PRE-ORDER SNACKS) -->
      <div v-if="showWalkInModal" class="modal-backdrop" @click.self="showWalkInModal = false">
        <div class="modal-card walkin-card">
          <div class="walkin-header">
            <div>
              <div class="eyebrow-sub">SERVICE COUNTER SEATING</div>
              <h3 class="modal-title">Seat Walk-in Table</h3>
            </div>
            <button type="button" class="close-x" @click="showWalkInModal = false">✕</button>
          </div>

          <div class="walkin-body">
            <!-- Table Selection -->
            <div class="form-section">
              <label class="modal-label">Select Table:</label>
              <div class="tables-choice-grid">
                <button
                  v-for="tbl in branchFloorTables"
                  :key="tbl.id"
                  type="button"
                  class="tbl-choice-pill"
                  :class="{
                    active: walkInTableId === tbl.id,
                    occupied: getTableStatus(tbl).state === 'OCCUPIED'
                  }"
                  @click="walkInTableId = tbl.id; walkInPartySize = tbl.seats"
                >
                  <strong>Table {{ tbl.number }}</strong>
                  <span>({{ tbl.seats }}p · ฿{{ tbl.rate }}/hr)</span>
                </button>
              </div>
            </div>

            <!-- Party Size & Duration -->
            <div class="form-row-2">
              <div class="form-group">
                <label class="modal-label">Guests in Party:</label>
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

              <div class="form-group">
                <label class="modal-label">Duration:</label>
                <select v-model.number="walkInDuration" class="modal-select">
                  <option :value="1">1 Hour</option>
                  <option :value="2">2 Hours</option>
                  <option :value="3">3 Hours</option>
                  <option :value="4">4 Hours</option>
                </select>
              </div>
            </div>

            <!-- Guest Name -->
            <div class="form-group">
              <label class="modal-label">Guest Name (Optional):</label>
              <input
                v-model="walkInGuestName"
                type="text"
                placeholder="Walk-in Guests"
                class="modal-input"
              />
            </div>

            <div v-if="walkInError" class="alert-banner">{{ walkInError }}</div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="showWalkInModal = false">
              Cancel
            </button>
            <button
              type="button"
              class="btn btn-seat-confirm"
              :disabled="staffCreateMutation.isPending.value"
              @click="handleStaffWalkInSubmit"
            >
              <span v-if="staffCreateMutation.isPending.value">Seating Table…</span>
              <span v-else>Seat & Start Playing Now →</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Quick Add Snacks Modal -->
      <div v-if="staffSnackReservation" class="modal-backdrop" @click.self="staffSnackReservation = null">
        <div class="modal-card snack-card">
          <div class="walkin-header">
            <div>
              <div class="eyebrow-sub">TABLE TAB SERVICE</div>
              <h3 class="modal-title">Add Refreshments</h3>
              <p class="modal-sub">
                Table {{ staffSnackReservation.tableId }} · {{ getCustomerName(staffSnackReservation.customer) }}
              </p>
            </div>
            <button type="button" class="close-x" @click="staffSnackReservation = null">✕</button>
          </div>

          <div class="snack-list-body">
            <div
              v-for="item in FOOD_MENU_ITEMS"
              :key="item.id"
              class="snack-row"
            >
              <div class="snack-info">
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

          <div class="snack-modal-footer">
            <div class="snack-total">
              <span>Selected Total:</span>
              <strong class="total-val">฿{{ staffSnackSubtotal }}</strong>
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
    </div>
  </div>
</template>

<style scoped>
/* Full Page Layout inspired by GameLedger */
.gl-admin-layout {
  display: flex;
  min-height: 100vh;
  background: #f4f5f7;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
  color: #111827;
}

/* Sidebar */
.gl-sidebar {
  width: 210px;
  background: #ffffff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  padding: 18px 12px;
  flex-shrink: 0;
  transition: width 0.2s ease;
}

.gl-sidebar.collapsed {
  width: 64px;
}

.gl-sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  padding: 0 4px;
}

.brand-dice-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-text-col {
  display: flex;
  flex-direction: column;
}

.brand-sub {
  font-size: 9.5px;
  font-weight: 800;
  letter-spacing: 0.1em;
  color: #6b7280;
}

.brand-name {
  font-size: 15px;
  font-weight: 800;
  color: #111827;
}

.sidebar-collapse-btn {
  background: transparent;
  border: none;
  font-size: 11px;
  color: #6b7280;
  text-align: left;
  padding: 4px 6px;
  margin-bottom: 16px;
  cursor: pointer;
}

.sidebar-collapse-btn:hover {
  color: #111827;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #4b5563;
  text-decoration: none;
  transition: all 0.12s ease;
}

.nav-item:hover {
  background: #f3f4f6;
  color: #111827;
}

.nav-item.active {
  background: #fff7ed;
  color: #ea580c;
  font-weight: 700;
}

.nav-icon {
  flex-shrink: 0;
}

.sidebar-footer {
  border-top: 1px solid #f3f4f6;
  padding-top: 12px;
  display: flex;
  flex-direction: column;
  font-size: 11px;
}

.help-lbl {
  color: #9ca3af;
}

.help-link {
  color: #4b5563;
  text-decoration: none;
  font-weight: 500;
}

/* Main Content Wrapper */
.gl-main-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

/* Topbar */
.gl-topbar {
  height: 60px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.branch-selector-capsule {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #fef3c7;
  border: 1px solid #fde68a;
  border-radius: 20px;
  padding: 4px 12px;
}

.pin-icon {
  font-size: 11px;
  color: #d97706;
}

.branch-select {
  border: none;
  background: transparent;
  font-size: 12.5px;
  font-weight: 700;
  color: #92400e;
  outline: none;
  cursor: pointer;
}

.topbar-search {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 20px;
  padding: 6px 14px;
  width: 320px;
}

.search-icon {
  color: #9ca3af;
}

.topbar-search-input {
  border: none;
  background: transparent;
  font-size: 13px;
  outline: none;
  width: 100%;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.icon-circle-btn {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1px solid #e5e7eb;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #4b5563;
  cursor: pointer;
  text-decoration: none;
}

.icon-circle-btn:hover {
  background: #f9fafb;
  color: #111827;
}

.user-avatar-badge {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: #ea580c;
  color: #ffffff;
  font-size: 12px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Service Content Area */
.gl-content {
  padding: 24px 28px 60px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1320px;
}

/* Service Hero Greeting */
.service-hero {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hero-eyebrow {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: #6b7280;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hero-title {
  margin: 0;
  font-family: 'Playfair Display', Georgia, serif;
  font-size: 32px;
  font-weight: 700;
  color: #111827;
}

.live-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 11px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 12px;
}

.live-pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #4f46e5;
}

.hero-subtitle {
  margin: 0;
  font-size: 13.5px;
  color: #6b7280;
}

/* Hero Actions Bar */
.hero-actions-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.btn-seat-walkin {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #ea580c;
  color: #ffffff;
  border: 1px solid #c2410c;
  border-radius: 20px;
  padding: 7px 18px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 2px 5px rgba(234, 88, 12, 0.25);
  transition: all 0.12s ease;
}

.btn-seat-walkin:hover {
  background: #c2410c;
}

.btn-hero-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #ffffff;
  border: 1px solid #d1d5db;
  color: #374151;
  border-radius: 20px;
  padding: 7px 16px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.btn-hero-pill:hover {
  background: #f9fafb;
}

.actions-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}

.updated-time {
  font-size: 12px;
  color: #9ca3af;
}

.btn-refresh {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #ffffff;
  border: 1px solid #d1d5db;
  color: #4b5563;
  border-radius: 20px;
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.btn-refresh:hover {
  background: #f3f4f6;
}

/* Metric Ribbon */
.numbers-card-ribbon {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.num-col {
  display: flex;
  flex-direction: column;
  padding: 0 16px;
  border-right: 1px solid #f3f4f6;
}

.num-col:last-child {
  border-right: none;
}

.col-label {
  font-size: 10.5px;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: #6b7280;
  margin-bottom: 2px;
}

.col-value {
  font-size: 26px;
  font-weight: 800;
  color: #111827;
  line-height: 1.1;
}

.segmented-progress {
  display: flex;
  gap: 3px;
  margin: 6px 0 4px;
}

.progress-seg {
  height: 4px;
  flex: 1;
  background: #e5e7eb;
  border-radius: 2px;
}

.progress-seg.active {
  background: #0d9488;
}

.col-sub {
  font-size: 11.5px;
  color: #6b7280;
}

/* Needs You Now Section */
.needs-you-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.needs-you-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.needs-you-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: 'Playfair Display', Georgia, serif;
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}

.needs-count-pill {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #ea580c;
  color: #ffffff;
  font-size: 11px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: sans-serif;
}

.most-urgent {
  font-size: 11.5px;
  color: #9ca3af;
}

.needs-you-card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 12px 18px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.needs-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.needs-item-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.needs-icon-circle {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #fff7ed;
  color: #ea580c;
  display: flex;
  align-items: center;
  justify-content: center;
}

.needs-item-title {
  font-size: 13.5px;
  font-weight: 700;
  color: #111827;
}

.needs-item-sub {
  font-size: 12px;
  color: #6b7280;
}

.needs-item-actions {
  display: flex;
  gap: 8px;
}

.btn-action-dark {
  background: #111827;
  color: #ffffff;
  border: none;
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.btn-action-light {
  background: #ffffff;
  border: 1px solid #d1d5db;
  color: #374151;
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

/* Lower Two-Column Section */
.service-lower-grid {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 20px;
}

/* Floor Plan Grid Section */
.floor-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-top-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.section-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-family: 'Playfair Display', Georgia, serif;
  font-size: 19px;
  font-weight: 700;
  color: #111827;
}

.live-dot-green {
  font-family: sans-serif;
  font-size: 11px;
  font-weight: 700;
  color: #0d9488;
}

.occupancy-stat {
  font-size: 12px;
  color: #6b7280;
}

.floor-tables-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.floor-table-tile {
  background: #ffffff;
  border-radius: 10px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 120px;
}

.floor-table-tile.free {
  border: 1.5px solid #0d9488;
}

.floor-table-tile.occupied {
  border: 1.5px solid #d1d5db;
  background: #fafafa;
}

.floor-table-tile.reserved {
  border: 1.5px dashed #9ca3af;
  background: #ffffff;
}

.tile-header {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.table-num {
  font-size: 15px;
  font-weight: 800;
  color: #111827;
}

.table-seats {
  font-size: 11px;
  color: #6b7280;
}

.tile-status-tag {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.tile-status-tag.free {
  color: #0d9488;
}

.tile-status-tag.occupied {
  color: #374151;
}

.tile-status-tag.reserved {
  color: #6b7280;
}

.tile-guest-name {
  font-size: 13px;
  font-weight: 700;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tile-timing {
  font-size: 11.5px;
  color: #6b7280;
}

.btn-seat-here {
  margin-top: auto;
  background: transparent;
  border: none;
  color: #0d9488;
  font-size: 11.5px;
  font-weight: 700;
  text-align: left;
  padding: 0;
  cursor: pointer;
}

.btn-seat-here.arrive-btn {
  color: #4f46e5;
}

.tile-actions-row {
  margin-top: auto;
  display: flex;
  gap: 6px;
}

.btn-tile-sm {
  flex: 1;
  background: #ffffff;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  padding: 3px 6px;
  cursor: pointer;
}

.btn-tile-sm.primary {
  background: #111827;
  color: #ffffff;
  border-color: #111827;
}

/* Coming Up Timeline Section */
.coming-up-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.time-window {
  font-size: 12px;
  font-family: sans-serif;
  color: #6b7280;
  font-weight: 500;
}

.count-tag {
  background: #f3f4f6;
  font-family: sans-serif;
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 10px;
  font-weight: 600;
  color: #4b5563;
}

.policy-note {
  font-size: 11px;
  color: #dc2626;
  font-weight: 600;
}

.empty-coming-up {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 30px 16px;
  text-align: center;
  font-size: 13px;
  color: #6b7280;
}

.upcoming-cards-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.coming-card {
  background: #fffbeb;
  border: 1px solid #fef3c7;
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.coming-card-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.arrival-time {
  font-size: 14.5px;
  font-weight: 800;
  color: #92400e;
}

.customer-info-line {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.cust-title {
  font-size: 13.5px;
  font-weight: 700;
  color: #111827;
}

.cust-meta {
  font-size: 12px;
  color: #6b7280;
}

.coming-notes-pill {
  font-size: 11.5px;
  color: #92400e;
  background: #fef3c7;
  padding: 3px 8px;
  border-radius: 6px;
  width: fit-content;
}

.coming-card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.btn-arrived {
  background: #ffffff;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.btn-seat-dark {
  background: #111827;
  color: #ffffff;
  border: none;
  border-radius: 6px;
  padding: 5px 14px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.btn-cancel-link {
  background: transparent;
  border: none;
  color: #dc2626;
  font-size: 11px;
  cursor: pointer;
  text-decoration: underline;
  margin-left: auto;
}

/* Modals */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(17, 24, 39, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  z-index: 1000;
}

.modal-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 24px;
  max-width: 480px;
  width: 100%;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
}

.walkin-card {
  max-width: 520px;
}

.snack-card {
  max-width: 460px;
}

.walkin-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.eyebrow-sub {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: #ea580c;
}

.modal-title {
  margin: 2px 0 0;
  font-size: 19px;
  font-weight: 800;
  color: #111827;
}

.modal-sub {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: #6b7280;
}

.close-x {
  background: transparent;
  border: none;
  font-size: 18px;
  cursor: pointer;
  color: #9ca3af;
}

.modal-label {
  display: block;
  font-size: 12px;
  font-weight: 700;
  color: #374151;
  margin-bottom: 6px;
}

.tables-choice-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 14px;
}

.tbl-choice-pill {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px;
  text-align: center;
  cursor: pointer;
  display: flex;
  flex-direction: column;
}

.tbl-choice-pill.active {
  background: #fff7ed;
  border-color: #ea580c;
  color: #ea580c;
}

.tbl-choice-pill.occupied {
  opacity: 0.4;
  cursor: not-allowed;
}

.form-row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}

.form-group {
  margin-bottom: 12px;
}

.stepper-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.step-btn {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: 1px solid #d1d5db;
  background: #f9fafb;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}

.step-btn.small {
  width: 26px;
  height: 26px;
  font-size: 13px;
}

.step-val {
  font-size: 14px;
  font-weight: 800;
  min-width: 20px;
  text-align: center;
}

.modal-select {
  width: 100%;
  padding: 7px 10px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 13px;
  background: #ffffff;
}

.modal-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 13px;
  box-sizing: border-box;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

.btn {
  padding: 7px 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.btn-secondary {
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  color: #374151;
}

.btn-primary {
  background: #111827;
  border: 1px solid #111827;
  color: #ffffff;
}

.btn-danger {
  background: #dc2626;
  border: 1px solid #dc2626;
  color: #ffffff;
}

.btn-seat-confirm {
  background: #ea580c;
  border: 1px solid #c2410c;
  color: #ffffff;
  font-weight: 700;
}

/* Snacks Modal List */
.snack-list-body {
  max-height: 280px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}

.snack-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f9fafb;
  border-radius: 6px;
}

.snack-name {
  font-size: 13px;
  font-weight: 600;
  display: block;
}

.snack-price {
  font-size: 12px;
  color: #ea580c;
  font-weight: 700;
}

.snack-modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #f3f4f6;
  padding-top: 12px;
}

.snack-total {
  font-size: 13px;
  color: #4b5563;
}

.total-val {
  font-size: 16px;
  color: #111827;
  margin-left: 6px;
}

.alert-banner {
  background: #fee2e2;
  border: 1px solid #fecaca;
  color: #b91c1c;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 12px;
  margin-top: 10px;
}
</style>

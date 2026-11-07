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
  type ReservationStatus,
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
const selectedStatus = ref<string>('ALL');

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
  queryKey: ['listBranchReservations', selectedBranchId, selectedStatus],
  queryFn: ({ signal }) =>
    listBranchReservations(
      selectedBranchId.value,
      selectedStatus.value !== 'ALL'
        ? { status: selectedStatus.value as ReservationStatus }
        : {},
      undefined,
      signal
    ),
  enabled: computed(() => !!selectedBranchId.value),
});

const reservations = computed<any[]>(() => {
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

  if (selectedStatus.value !== 'ALL') {
    return combined.filter((r) => r.status === selectedStatus.value);
  }
  return combined;
});

// Format Timestamps
function formatInterval(startsAt: string, endsAt: string): string {
  if (!startsAt || !endsAt) return '';
  const s = new Date(startsAt);
  const e = new Date(endsAt);
  const dateStr = s.toLocaleDateString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  });
  const timeStr = `${s.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} – ${e.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
  return `${dateStr}, ${timeStr}`;
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
    'Added by staff at counter'
  );
  staffSnackReservation.value = null;
}

function isWalkIn(res: any): boolean {
  return (
    res.source === 'WALK_IN' ||
    res.clientType === 'WALK_IN' ||
    (res.id && String(res.id).startsWith('walkin')) ||
    (res.notes && String(res.notes).toLowerCase().includes('walk-in'))
  );
}

// Staff Actions: Check-in, Cancel, No-Show
const actionInProgress = ref<string | null>(null);
const actionError = ref<string>('');
const reservationsStore = useCustomerReservationsStore();
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
      hourlyRate: 200,
      inPlayOrders: [],
    };
  }
}

// Check In Mutation
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

// Cancel Mutation
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

// No Show Mutation
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

// Staff Walk-in / Phone Reservation Modal
const showWalkInModal = ref(false);
const walkInDate = ref(new Date().toISOString().split('T')[0]);
const walkInTime = ref('15:00');
const walkInDuration = ref(2);
const walkInPartySize = ref(4);
const walkInCustomerName = ref('');
const walkInCountryCode = ref('+66'); // Flexible, does NOT have to be +66!
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

  // Add to local store immediately as active walk-in session
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
}

function getCustomerName(c: any): string {
  if (!c) return 'Customer';
  return c.displayName || c.name || c.subject || 'Customer';
}
function getCustomerPhone(c: any): string {
  return c?.phone || '';
}
function getCustomerEmail(c: any): string {
  return c?.email || '';
}

function formatStatus(status: string): string {
  switch (status) {
    case 'CONFIRMED':
      return 'Confirmed';
    case 'CHECKED_IN':
      return 'Checked In';
    case 'COMPLETED':
      return 'Completed';
    case 'CANCELLED':
      return 'Cancelled';
    case 'NO_SHOW':
      return 'No-Show';
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
  <main class="staff-reservations-workspace">
    <div class="workspace-container">
      <!-- Breadcrumb navigation -->
      <nav class="breadcrumb-bar" aria-label="Breadcrumb">
        <RouterLink :to="{ name: 'WorkspaceIndex' }">Workspace</RouterLink>
        <span class="sep">/</span>
        <span class="current">Reservation Operations</span>
      </nav>

      <!-- Dashboard Header -->
      <div class="dashboard-header">
        <div>
          <h1 class="header-title">Staff Reservation Operations</h1>
          <p class="header-sub">
            Real-time branch bookings, check-in arrivals, pre-ordered food & refreshments, and walk-ins.
          </p>
        </div>

        <button type="button" class="btn btn-primary" @click="showWalkInModal = true">
          + New Walk-in / Phone Booking
        </button>
      </div>

      <!-- Control Toolbar -->
      <div class="toolbar-card">
        <!-- Branch Selector (Cross-branch for managers, single for staff) -->
        <div class="tool-group">
          <label for="branch-select" class="tool-label">Active Branch:</label>
          <select id="branch-select" v-model="selectedBranchId" class="tool-select">
            <option v-for="b in branches" :key="b.id" :value="b.id">
              {{ b.name }} ({{ b.address || 'Bangkok' }})
            </option>
          </select>
        </div>

        <!-- Status Filter -->
        <div class="tool-group">
          <label class="tool-label">Filter Status:</label>
          <div class="status-filters">
            <button
              v-for="st in ['ALL', 'CONFIRMED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'NO_SHOW']"
              :key="st"
              type="button"
              class="filter-pill"
              :class="{ active: selectedStatus === st }"
              @click="selectedStatus = st"
            >
              {{ st }}
            </button>
          </div>
        </div>
      </div>

      <!-- Error Alert -->
      <div v-if="actionError" class="alert-banner" role="alert">
        {{ actionError }}
      </div>

      <!-- Reservations Data Table -->
      <div class="table-container">
        <div v-if="reservationsQuery.isLoading.value" class="table-state">
          Loading reservations for {{ selectedBranch?.name }}…
        </div>

        <div v-else-if="reservations.length === 0" class="table-state">
          No reservations found for {{ selectedBranch?.name }} matching "{{ selectedStatus }}".
        </div>

        <table v-else class="data-table" aria-label="Branch Reservations">
          <thead>
            <tr>
              <th scope="col">Time Interval</th>
              <th scope="col">Status</th>
              <th scope="col">Table & Party</th>
              <th scope="col">Customer / Contact</th>
              <th scope="col">Pre-Order Food & Notes</th>
              <th scope="col" class="th-actions">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="res in reservations"
              :key="res.id"
              :class="{ 'row-active': res.status === 'CHECKED_IN' }"
            >
              <!-- Time & Timestamps -->
              <td>
                <div class="time-cell">
                  <span class="interval-bold">{{ formatInterval(res.startsAt, res.endsAt) }}</span>
                  <span class="ts-sub">Ref: {{ res.id }}</span>
                </div>
              </td>

              <!-- Status Badge -->
              <td>
                <span class="status-pill" :class="`status-${res.status.toLowerCase()}`">
                  {{ formatStatus(res.status) }}
                </span>
                <span v-if="res.checkedInAt" class="checkin-time">
                  In: {{ new Date(res.checkedInAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}
                </span>
              </td>

              <!-- Table & Party -->
              <td>
                <div class="table-cell">
                  <span class="tbl-name">Table {{ res.tableId }}</span>
                  <span class="party-tag">{{ res.partySize }} Guests</span>
                </div>
              </td>

              <!-- Customer Details (International phone support + Client Type Badge) -->
              <td>
                <div class="customer-cell">
                  <div class="cust-name-row">
                    <span class="cust-name">{{ getCustomerName(res.customer) }}</span>
                    <span v-if="isWalkIn(res)" class="client-type-badge walkin">Walk-in</span>
                    <span v-else class="client-type-badge reserved">Reserved</span>
                  </div>
                  <span v-if="getCustomerPhone(res.customer)" class="cust-phone">
                    <span class="gold-bullet">◆</span> {{ getCustomerPhone(res.customer) }}
                  </span>
                  <span v-if="getCustomerEmail(res.customer)" class="cust-email">
                    {{ getCustomerEmail(res.customer) }}
                  </span>
                </div>
              </td>

              <!-- Pre-Order Food & Notes -->
              <td>
                <div class="notes-cell">
                  <div v-if="res.notes" class="notes-badge">
                    {{ res.notes }}
                  </div>
                  <span v-else class="no-notes">—</span>
                </div>
              </td>

              <!-- Operational Staff Actions -->
              <td class="td-actions">
                <div class="btn-action-group">
                  <!-- Check-in Action -->
                  <button
                    v-if="res.status === 'CONFIRMED'"
                    type="button"
                    class="action-btn checkin-btn"
                    :disabled="actionInProgress === res.id"
                    @click="handleCheckIn(res)"
                  >
                    Check In ✓
                  </button>

                  <!-- Cancel Action -->
                  <button
                    v-if="res.status === 'CONFIRMED'"
                    type="button"
                    class="action-btn cancel-btn"
                    @click="openReasonModal(res, 'CANCEL')"
                  >
                    Cancel
                  </button>

                  <!-- No-Show Action -->
                  <button
                    v-if="res.status === 'CONFIRMED'"
                    type="button"
                    class="action-btn noshow-btn"
                    @click="openReasonModal(res, 'NO_SHOW')"
                  >
                    No-Show
                  </button>

                  <!-- In-Progress active tag & Staff Check Bill Button -->
                  <div v-if="res.status === 'CHECKED_IN'" class="playing-action-wrap">
                    <span class="playing-tag">
                      <span class="gold-bullet">◆</span> Currently Playing
                    </span>
                    <button
                      type="button"
                      class="action-btn add-snack-btn"
                      @click="openStaffAddSnacks(res)"
                    >
                      + Add Snacks/Drinks
                    </button>
                    <button
                      type="button"
                      class="action-btn bill-btn"
                      @click="openStaffBill(res)"
                    >
                      Check Bill
                    </button>
                  </div>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Staff Table Bill & In-Play Orders Modal -->
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

      <!-- Staff Walk-in / Phone Booking Modal -->
      <div v-if="showWalkInModal" class="modal-backdrop" @click.self="showWalkInModal = false">
        <div class="walkin-modal">
          <div class="walkin-header">
            <h3>New Walk-in / Phone Reservation</h3>
            <button type="button" class="close-x" @click="showWalkInModal = false">✕</button>
          </div>

          <div class="walkin-body">
            <div class="form-grid">
              <div class="form-field">
                <label class="form-label">Branch</label>
                <input type="text" :value="selectedBranch?.name" disabled class="modal-input disabled" />
              </div>
              <div class="form-field">
                <label class="form-label">Date</label>
                <input v-model="walkInDate" type="date" class="modal-input" />
              </div>
              <div class="form-field">
                <label class="form-label">Start Time</label>
                <input v-model="walkInTime" type="time" class="modal-input" />
              </div>
              <div class="form-field">
                <label class="form-label">Duration (Hours)</label>
                <select v-model.number="walkInDuration" class="modal-select">
                  <option :value="1">1 Hour</option>
                  <option :value="2">2 Hours</option>
                  <option :value="3">3 Hours</option>
                  <option :value="4">4 Hours</option>
                </select>
              </div>
              <div class="form-field">
                <label class="form-label">Party Size</label>
                <input v-model.number="walkInPartySize" type="number" min="1" max="20" class="modal-input" />
              </div>
              <div class="form-field">
                <label class="form-label">Customer Name</label>
                <input v-model="walkInCustomerName" type="text" placeholder="Guest Name" class="modal-input" />
              </div>

              <!-- International Phone Field -->
              <div class="form-field full-width">
                <label class="form-label">Phone Number (Any Country)</label>
                <div class="phone-row">
                  <select v-model="walkInCountryCode" class="modal-select country-select">
                    <option value="+66">TH (+66)</option>
                    <option value="+1">US/CA (+1)</option>
                    <option value="+44">UK (+44)</option>
                    <option value="+65">SG (+65)</option>
                    <option value="+81">JP (+81)</option>
                    <option value="+61">AU (+61)</option>
                    <option value="+49">DE (+49)</option>
                  </select>
                  <input
                    v-model="walkInPhone"
                    type="tel"
                    placeholder="Phone number"
                    class="modal-input phone-input"
                  />
                </div>
              </div>
            </div>

            <!-- Immediate Session Checkbox -->
            <div class="form-field full-width">
              <label class="walkin-now-toggle">
                <input type="checkbox" v-model="walkInStartNow" />
                <span><strong>Start session now</strong> (Guest is at the counter, table starts immediately)</span>
              </label>
            </div>

            <!-- Table Selection -->
            <div class="walkin-tables">
              <label class="form-label">Select Available Table:</label>
              <div v-if="walkInTables.length === 0" class="no-tables">
                No tables available for this time slot.
              </div>
              <div v-else class="tables-capsules">
                <button
                  v-for="tbl in walkInTables"
                  :key="tbl.tableId"
                  type="button"
                  class="tbl-capsule"
                  :class="{ active: walkInSelectedTable?.tableId === tbl.tableId }"
                  @click="walkInSelectedTable = tbl"
                >
                  {{ tbl.label }} (Capacity {{ tbl.capacity }}) — ฿{{ (tbl.hourlyRate as any)?.amountMinor ? ((tbl.hourlyRate as any).amountMinor / 100) : 150 }}/hr
                </button>
              </div>
            </div>

            <!-- Optional Food Pre-Order -->
            <div class="walkin-food-section">
              <details class="food-details">
                <summary class="food-summary-btn">
                  + Pre-Order Refreshments (10% Discount Applied)
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
              <span v-if="staffCreateMutation.isPending.value">Saving Booking…</span>
              <span v-else>Confirm & Create Reservation</span>
            </button>
          </div>
        </div>
      </div>
      <!-- Staff Quick Add Snacks/Drinks Modal -->
      <div v-if="staffSnackReservation" class="modal-backdrop" @click.self="staffSnackReservation = null">
        <div class="modal-card snack-modal-card">
          <div class="snack-modal-header">
            <div>
              <div class="snack-eyebrow">
                <span class="gold-sparkle">✦</span>
                <span>TABLE TAB / SNACK SERVICE</span>
              </div>
              <h3 class="snack-modal-title">Add Snacks & Drinks to Table</h3>
              <p class="snack-modal-sub">
                Table {{ staffSnackReservation.tableId }} • {{ getCustomerName(staffSnackReservation.customer) }}
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
.staff-reservations-workspace {
  background: #f6f8f6;
  min-height: calc(100vh - 120px);
  padding: 24px 20px 60px;
  color: #1a2f24;
  font-family: Arial, Helvetica, sans-serif;
}

.workspace-container {
  max-width: 1200px;
  margin: 0 auto;
}

.breadcrumb-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  margin-bottom: 16px;
  color: #635c4d;
}

.breadcrumb-bar a {
  color: #203c30;
  text-decoration: underline;
  font-weight: 600;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 22px;
  border-bottom: 2px solid #c6ad7a;
  padding-bottom: 16px;
  flex-wrap: wrap;
  gap: 16px;
}

.header-title {
  margin: 0 0 6px;
  font-size: 26px;
  font-weight: 800;
  color: #173224;
}

.header-sub {
  margin: 0;
  font-size: 14px;
  color: #5c5546;
}

/* Toolbar */
.toolbar-card {
  background: #ffffff;
  border: 1px solid #d5c8b2;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  box-shadow: 0 2px 6px rgba(20, 43, 32, 0.04);
}

.tool-group {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.tool-label {
  font-size: 13px;
  font-weight: 700;
  color: #3b362c;
}

.tool-select {
  padding: 8px 14px;
  border: 1px solid #c9bda7;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  background: #fffaf1;
  color: #203c30;
}

.status-filters {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.filter-pill {
  padding: 6px 12px;
  border: 1px solid #d5c8b2;
  background: #fffaf1;
  color: #4b4538;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: all 120ms ease;
}

.filter-pill:hover {
  background: #eee3ce;
}

.filter-pill.active {
  background: #203c30;
  border-color: #203c30;
  color: #fffaf1;
}

.alert-banner {
  background: #fee2e2;
  border: 1px solid #f87171;
  color: #991b1b;
  padding: 12px 16px;
  border-radius: 6px;
  margin-bottom: 16px;
  font-size: 13px;
}

/* Data Table */
.table-container {
  background: #ffffff;
  border: 1px solid #d5c8b2;
  border-radius: 8px;
  overflow-x: auto;
  box-shadow: 0 4px 14px rgba(20, 43, 32, 0.05);
}

.table-state {
  padding: 40px;
  text-align: center;
  font-size: 14px;
  color: #6a6252;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 13px;
}

.data-table th {
  background: #f8f6f0;
  border-bottom: 2px solid #c6ad7a;
  padding: 12px 16px;
  font-weight: 700;
  color: #203c30;
}

.data-table td {
  padding: 14px 16px;
  border-bottom: 1px solid #eee4d1;
  vertical-align: middle;
}

.data-table tbody tr:hover {
  background: #faf8f3;
}

.row-active {
  background: #f0fdf4 !important;
}

.time-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.interval-bold {
  font-weight: 700;
  color: #173224;
}

.ts-sub {
  font-size: 11px;
  color: #787162;
  font-family: monospace;
}

.status-pill {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 700;
  text-transform: none;
}

.status-confirmed {
  background: #e2f0d9;
  color: #1e5214;
}

.status-checked_in {
  background: #dbeafe;
  color: #1e40af;
}

.status-completed {
  background: #f1f5f9;
  color: #475569;
}

.status-cancelled {
  background: #fee2e2;
  color: #991b1b;
}

.status-no_show {
  background: #fef3c7;
  color: #92400e;
}

.checkin-time {
  display: block;
  font-size: 11px;
  color: #1e40af;
  margin-top: 3px;
}

.table-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.tbl-name {
  font-weight: 700;
  color: #1b2f25;
}

.party-tag {
  font-size: 11px;
  color: #555044;
}

.customer-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cust-name {
  font-weight: 700;
  color: #173224;
}

.cust-phone,
.cust-email {
  font-size: 11px;
  color: #60594a;
}

.notes-cell {
  max-width: 280px;
}

.notes-badge {
  background: #fff8eb;
  border: 1px solid #f1ddbe;
  border-radius: 4px;
  padding: 6px 8px;
  font-size: 11px;
  color: #3f392d;
  line-height: 1.4;
}

.no-notes {
  color: #a39c8d;
}

.btn-action-group {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.action-btn {
  padding: 6px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: all 120ms ease;
}

.checkin-btn {
  background: #203c30;
  border: 1px solid #203c30;
  color: #fffaf1;
}

.checkin-btn:hover {
  background: #152a21;
}

.cancel-btn {
  background: #ffffff;
  border: 1px solid #f87171;
  color: #dc2626;
}

.cancel-btn:hover {
  background: #fee2e2;
}

.noshow-btn {
  background: #ffffff;
  border: 1px solid #f59e0b;
  color: #d97706;
}

.noshow-btn:hover {
  background: #fef3c7;
}

.playing-tag {
  font-size: 11px;
  font-weight: 700;
  color: #15803d;
  background: #dcfce7;
  padding: 4px 8px;
  border-radius: 4px;
}

/* Modal styles */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(16, 32, 24, 0.65);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  padding: 16px;
}

.modal-card {
  background: #ffffff;
  border: 1px solid #c6ad7a;
  border-radius: 8px;
  padding: 24px;
  max-width: 440px;
  width: 100%;
}

.modal-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 800;
  color: #173224;
}

.modal-sub {
  margin: 0 0 16px;
  font-size: 13px;
  color: #60594a;
}

.form-group {
  margin-bottom: 16px;
}

.modal-label {
  display: block;
  font-size: 12px;
  font-weight: 700;
  color: #3b362c;
  margin-bottom: 6px;
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
}

/* Walkin Modal */
.walkin-modal {
  background: #ffffff;
  border: 2px solid #c6ad7a;
  border-radius: 10px;
  max-width: 640px;
  width: 100%;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
}

.walkin-header {
  padding: 16px 20px;
  border-bottom: 1px solid #eee4d1;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.walkin-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: #173224;
}

.close-x {
  background: transparent;
  border: none;
  font-size: 18px;
  cursor: pointer;
  color: #6a6252;
}

.walkin-body {
  padding: 20px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.full-width {
  grid-column: 1 / -1;
}

.phone-row {
  display: flex;
  gap: 8px;
}

.country-select {
  width: 120px;
}

.phone-input {
  flex: 1;
}

.modal-select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d5c8b2;
  border-radius: 6px;
  font-size: 13px;
  background: #ffffff;
}

.disabled {
  background: #f4ecdc;
}

.walkin-tables {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tables-capsules {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tbl-capsule {
  padding: 8px 12px;
  border: 1px solid #d5c8b2;
  background: #fffaf1;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #203c30;
  cursor: pointer;
}

.tbl-capsule.active {
  background: #203c30;
  border-color: #203c30;
  color: #fffaf1;
}

.food-details {
  border: 1px solid #e0d5c1;
  border-radius: 6px;
  padding: 10px 14px;
  background: #faf8f3;
}

.food-summary-btn {
  font-size: 13px;
  font-weight: 700;
  color: #1a4231;
  cursor: pointer;
}

.picker-wrap {
  margin-top: 14px;
}

.walkin-footer {
  padding: 16px 20px;
  border-top: 1px solid #eee4d1;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 9px 18px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.btn-primary {
  background: #203c30;
  color: #fffaf1;
  border: none;
}

.btn-secondary {
  background: #ffffff;
  border: 1px solid #d5c8b2;
  color: #3b362c;
}

.btn-danger {
  background: #dc2626;
  color: #ffffff;
  border: none;
}

.btn-warning {
  background: #d97706;
  color: #ffffff;
  border: none;
}

.playing-action-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.action-btn.bill-btn {
  background: #142b20;
  color: #ffd98a;
  border: 1px solid #c6ad7a;
  font-weight: 800;
  padding: 4px 10px;
}

.action-btn.bill-btn:hover {
  background: #1e3f2f;
  transform: translateY(-1px);
}


.cust-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.client-type-badge {
  font-size: 10.5px;
  font-weight: 800;
  padding: 2px 7px;
  border-radius: 8px;
  letter-spacing: 0.03em;
}

.client-type-badge.walkin {
  background: #fef3c7;
  color: #92400e;
  border: 1px solid #fde68a;
}

.client-type-badge.reserved {
  background: #e0f2fe;
  color: #0369a1;
  border: 1px solid #bae6fd;
}

.action-btn.add-snack-btn {
  background: #142b20;
  color: #ffd98a;
  border: 1px solid #c6ad7a;
  white-space: nowrap;
}

.action-btn.add-snack-btn:hover {
  background: #1f3f30;
}

.walkin-now-toggle {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  padding: 10px 14px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13.5px;
  color: #92400e;
}

.walkin-now-toggle input {
  width: 18px;
  height: 18px;
  accent-color: #142b20;
  cursor: pointer;
}

/* Snack Modal */
.snack-modal-card {
  max-width: 520px;
  width: 100%;
}

.snack-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 18px 22px 14px;
  border-bottom: 1.5px solid #e7ded0;
  background: #fdfbf7;
}

.snack-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 10.5px;
  font-weight: 800;
  letter-spacing: 0.12em;
  color: #8c7649;
  margin-bottom: 4px;
}

.gold-sparkle {
  color: #c6ad7a;
}

.snack-modal-title {
  margin: 0 0 2px;
  font-size: 20px;
  font-weight: 800;
  color: #142b20;
}

.snack-modal-sub {
  margin: 0;
  font-size: 13px;
  color: #6a6252;
}

.snack-picker-body {
  padding: 14px 22px;
  max-height: 52vh;
  overflow-y: auto;
}

.snack-items-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.snack-row-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: #fcfaf6;
  border: 1px solid #e7ded0;
  border-radius: 8px;
}

.snack-info {
  display: flex;
  flex-direction: column;
}

.snack-name {
  font-size: 13.5px;
  font-weight: 700;
  color: #142b20;
}

.snack-price {
  font-size: 13px;
  font-weight: 800;
  color: #8c7649;
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
  border: 1px solid #c9bda7;
  background: #ffffff;
  font-size: 15px;
  font-weight: 700;
  color: #142b20;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;
}

.snack-step-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.snack-step-btn.plus {
  background: #142b20;
  color: #ffd98a;
  border-color: #c6ad7a;
}

.snack-qty-num {
  min-width: 20px;
  text-align: center;
  font-size: 14px;
  font-weight: 800;
  color: #142b20;
}

.snack-modal-footer {
  padding: 14px 22px 18px;
  border-top: 1.5px solid #c6ad7a;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #ffffff;
  gap: 12px;
}

.snack-order-summary {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
  color: #554e42;
}

.snack-total-val {
  font-size: 20px;
  font-weight: 800;
  color: #142b20;
}

.snack-footer-actions {
  display: flex;
  gap: 8px;
}
</style>

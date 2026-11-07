<script setup lang="ts">
import { ref, computed } from 'vue';
import { RouterLink } from 'vue-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query';
import {
  listMyReservations,
  cancelReservation,
  listBranches,
  type Reservation,
  type Branch,
} from '@theparlor/api-client';
import { useAuthStore } from '../../../shared/stores/auth';

const auth = useAuthStore();
const queryClient = useQueryClient();

const selectedStatusFilter = ref<string>('ALL');
const cancellingReservation = ref<Reservation | null>(null);
const cancellationReason = ref<string>('Schedule conflict');
const cancelError = ref<string>('');

// Query branches for branch name lookup
const branchesQuery = useQuery({
  queryKey: ['branches'],
  queryFn: ({ signal }) => listBranches({}, undefined, signal),
});
const branches = computed<Branch[]>(() => branchesQuery.data.value?.items ?? []);

function getBranchName(branchId: string): string {
  const b = branches.value.find((item) => item.id === branchId);
  return b ? b.name : branchId;
}

// Query current customer reservations
const reservationsQuery = useQuery({
  queryKey: ['listMyReservations'],
  queryFn: ({ signal }) => listMyReservations({}, undefined, signal),
  enabled: computed(() => auth.authenticated),
});

const reservations = computed<Reservation[]>(
  () => reservationsQuery.data.value?.items ?? []
);

const filteredReservations = computed(() => {
  if (selectedStatusFilter.value === 'ALL') return reservations.value;
  return reservations.value.filter((r) => r.status === selectedStatusFilter.value);
});

// Format timestamps
function formatTimestamp(isoStr: string): string {
  if (!isoStr) return '';
  const d = new Date(isoStr);
  return `${d.toLocaleDateString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })} at ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
}

// Cancel mutation
const cancelMutation = useMutation({
  mutationFn: async ({
    branchId,
    reservationId,
    reason,
  }: {
    branchId: string;
    reservationId: string;
    reason: string;
  }) => {
    return cancelReservation(branchId, reservationId, { reason });
  },
  onSuccess: () => {
    cancellingReservation.value = null;
    void queryClient.invalidateQueries({ queryKey: ['listMyReservations'] });
  },
  onError: (err: any) => {
    cancelError.value =
      err?.response?.data?.message || err?.message || 'Failed to cancel reservation.';
  },
});

function openCancelModal(res: Reservation) {
  cancelError.value = '';
  cancellationReason.value = 'Change of plans';
  cancellingReservation.value = res;
}

async function confirmCancel() {
  if (!cancellingReservation.value) return;
  cancelError.value = '';
  await cancelMutation.mutateAsync({
    branchId: cancellingReservation.value.branchId,
    reservationId: cancellingReservation.value.id,
    reason: cancellationReason.value || 'Cancelled by customer',
  });
}
</script>

<template>
  <div class="my-reservations-page">
    <div class="container">
      <div class="page-header">
        <div>
          <h1 class="page-title">My Table Reservations</h1>
          <p class="page-subtitle">
            Manage your booked gaming tables and pre-ordered food & refreshments.
          </p>
        </div>
        <RouterLink :to="{ name: 'CustomerReservePage' }" class="btn btn-primary">
          + Book a Table
        </RouterLink>
      </div>

      <!-- Filter Bar -->
      <div class="filters-bar">
        <span class="filter-label">Filter Status:</span>
        <div class="status-pills">
          <button
            v-for="st in ['ALL', 'CONFIRMED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED']"
            :key="st"
            type="button"
            class="pill-btn"
            :class="{ active: selectedStatusFilter === st }"
            @click="selectedStatusFilter = st"
          >
            {{ st === 'ALL' ? 'All Bookings' : st }}
          </button>
        </div>
      </div>

      <!-- State Handling -->
      <div v-if="!auth.authenticated" class="empty-box">
        <p class="empty-title">Please Sign In</p>
        <p class="empty-text">Sign in to your Parlor account to view your reservations.</p>
        <RouterLink :to="{ name: 'AuthLogin' }" class="btn btn-primary">
          Sign In
        </RouterLink>
      </div>

      <div v-else-if="reservationsQuery.isLoading.value" class="empty-box">
        <p class="empty-text">Loading your reservations…</p>
      </div>

      <div v-else-if="filteredReservations.length === 0" class="empty-box">
        <p class="empty-title">No Reservations Found</p>
        <p class="empty-text">You have no reservations matching this filter.</p>
        <RouterLink :to="{ name: 'CustomerReservePage' }" class="btn btn-primary">
          Reserve a Table Now
        </RouterLink>
      </div>

      <!-- Reservations List -->
      <div v-else class="reservations-list">
        <div
          v-for="res in filteredReservations"
          :key="res.id"
          class="res-card"
          :class="`status-${res.status.toLowerCase()}`"
        >
          <div class="res-card-top">
            <div class="branch-meta">
              <span class="branch-badge">{{ getBranchName(res.branchId) }}</span>
              <span class="res-id">Ref: {{ res.id }}</span>
            </div>
            <span class="status-badge" :class="`badge-${res.status.toLowerCase()}`">
              {{ res.status }}
            </span>
          </div>

          <div class="res-body">
            <div class="body-row">
              <span class="row-label">Time Interval:</span>
              <span class="row-val">
                {{ formatTimestamp(res.startsAt) }} – {{ formatTimestamp(res.endsAt) }}
              </span>
            </div>
            <div class="body-row">
              <span class="row-label">Table:</span>
              <span class="row-val">Table {{ res.tableId }} (Party of {{ res.partySize }})</span>
            </div>
            <div v-if="res.notes" class="notes-callout">
              <span class="notes-lbl">Notes & Pre-Orders:</span>
              <p class="notes-content">{{ res.notes }}</p>
            </div>
          </div>

          <!-- Actions -->
          <div class="res-footer">
            <div class="inplay-prompt">
              💡 Food & drinks can also be ordered at your table while playing!
            </div>
            <div v-if="res.status === 'CONFIRMED'" class="actions-group">
              <button
                type="button"
                class="btn-text danger"
                @click="openCancelModal(res)"
              >
                Cancel Reservation
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Cancel Confirmation Modal -->
      <div v-if="cancellingReservation" class="modal-backdrop" @click.self="cancellingReservation = null">
        <div class="modal-box">
          <h3 class="modal-title">Cancel Table Reservation?</h3>
          <p class="modal-sub">
            Are you sure you want to cancel your table at
            <strong>{{ getBranchName(cancellingReservation.branchId) }}</strong> for
            {{ formatTimestamp(cancellingReservation.startsAt) }}?
          </p>

          <div class="reason-field">
            <label for="cancel-reason" class="reason-lbl">Cancellation Reason:</label>
            <input
              id="cancel-reason"
              v-model="cancellationReason"
              type="text"
              class="form-input"
              placeholder="e.g. Schedule conflict"
            />
          </div>

          <div v-if="cancelError" class="error-msg">{{ cancelError }}</div>

          <div class="modal-actions">
            <button
              type="button"
              class="btn btn-secondary"
              @click="cancellingReservation = null"
            >
              Keep Reservation
            </button>
            <button
              type="button"
              class="btn btn-danger"
              :disabled="cancelMutation.isPending.value"
              @click="confirmCancel"
            >
              <span v-if="cancelMutation.isPending.value">Cancelling…</span>
              <span v-else>Confirm Cancellation</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.my-reservations-page {
  background: #fafbf9;
  min-height: calc(100vh - 120px);
  padding: 32px 16px 64px;
  color: #202e27;
}

.container {
  max-width: 900px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  border-bottom: 2px solid #c6ad7a;
  padding-bottom: 16px;
  flex-wrap: wrap;
  gap: 16px;
}

.page-title {
  margin: 0 0 6px;
  font-size: 26px;
  font-weight: 800;
  color: #173224;
}

.page-subtitle {
  margin: 0;
  font-size: 14px;
  color: #5c5546;
}

.filters-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.filter-label {
  font-size: 13px;
  font-weight: 700;
  color: #4b4435;
}

.status-pills {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.pill-btn {
  background: #ffffff;
  border: 1px solid #d5c8b2;
  border-radius: 20px;
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  color: #443e32;
  transition: all 120ms ease;
}

.pill-btn:hover {
  background: #eee3ce;
}

.pill-btn.active {
  background: #203c30;
  border-color: #203c30;
  color: #fffaf1;
}

.empty-box {
  background: #ffffff;
  border: 1px dashed #c6ad7a;
  border-radius: 10px;
  padding: 40px 20px;
  text-align: center;
}

.empty-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  color: #1a382a;
}

.empty-text {
  margin: 0 0 20px;
  font-size: 14px;
  color: #60594a;
}

.reservations-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.res-card {
  background: #ffffff;
  border: 1px solid #d5c8b2;
  border-radius: 10px;
  padding: 20px;
  box-shadow: 0 3px 10px rgba(20, 43, 32, 0.04);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.res-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0e7d8;
  padding-bottom: 12px;
}

.branch-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.branch-badge {
  font-size: 15px;
  font-weight: 800;
  color: #1a382a;
}

.res-id {
  font-size: 11px;
  color: #6e6757;
  font-family: monospace;
  background: #f4ecdc;
  padding: 2px 6px;
  border-radius: 4px;
}

.status-badge {
  font-size: 11px;
  font-weight: 800;
  padding: 4px 10px;
  border-radius: 12px;
  text-transform: uppercase;
}

.badge-confirmed {
  background: #e2f0d9;
  color: #1e5214;
}

.badge-checked_in {
  background: #dbeafe;
  color: #1e40af;
}

.badge-completed {
  background: #f1f5f9;
  color: #475569;
}

.badge-cancelled {
  background: #fee2e2;
  color: #991b1b;
}

.res-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.body-row {
  display: flex;
  font-size: 13px;
  gap: 10px;
}

.row-label {
  width: 120px;
  color: #6a6252;
  font-weight: 600;
}

.row-val {
  font-weight: 600;
  color: #1a382a;
}

.notes-callout {
  background: #fbf9f4;
  border-left: 3px solid #c6ad7a;
  padding: 8px 12px;
  border-radius: 0 4px 4px 0;
  margin-top: 6px;
}

.notes-lbl {
  font-size: 11px;
  font-weight: 700;
  color: #6a6252;
  display: block;
}

.notes-content {
  margin: 3px 0 0;
  font-size: 12px;
  color: #27382f;
  line-height: 1.4;
}

.res-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #f0e7d8;
  padding-top: 12px;
  flex-wrap: wrap;
  gap: 10px;
}

.inplay-prompt {
  font-size: 12px;
  color: #2e6022;
  font-weight: 600;
}

.btn-text {
  background: transparent;
  border: none;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  padding: 6px 10px;
}

.btn-text.danger {
  color: #b91c1c;
}

.btn-text.danger:hover {
  text-decoration: underline;
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
  text-decoration: none;
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
  background: #b91c1c;
  color: #ffffff;
  border: none;
}

/* Modal */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(16, 32, 24, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  padding: 16px;
}

.modal-box {
  background: #ffffff;
  border: 1px solid #c6ad7a;
  border-radius: 10px;
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
  color: #5c5546;
  line-height: 1.5;
}

.reason-field {
  margin-bottom: 16px;
}

.reason-lbl {
  font-size: 12px;
  font-weight: 700;
  color: #3b362c;
  display: block;
  margin-bottom: 6px;
}

.form-input {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid #d5c8b2;
  border-radius: 6px;
  font-size: 13px;
  box-sizing: border-box;
}

.error-msg {
  color: #b91c1c;
  font-size: 12px;
  margin-bottom: 12px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>

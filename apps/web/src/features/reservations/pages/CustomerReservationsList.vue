<script setup lang="ts">
import { ref, computed } from 'vue';
import { RouterLink } from 'vue-router';
import { useQuery } from '@tanstack/vue-query';
import { listBranches, type Branch } from '@theparlor/api-client';
import { useAuthStore } from '../../../shared/stores/auth';
import { useStoreText } from '../../home/data/store-text';
import {
  useCustomerReservationsStore,
  type CustomerReservation,
} from '../data/reservations-store';
import InPlayOrderModal from '../components/InPlayOrderModal.vue';
import TableBillModal from '../components/TableBillModal.vue';

const auth = useAuthStore();
const { t, locale } = useStoreText();
const reservationsStore = useCustomerReservationsStore();

const selectedStatusFilter = ref<string>('ALL');
const selectedBranchFilter = ref<string>('ALL');
const cancellingReservation = ref<CustomerReservation | null>(null);
const isCancelling = ref(false);
const orderingReservation = ref<CustomerReservation | null>(null);
const billingReservation = ref<CustomerReservation | null>(null);
const activeSession = computed(() => reservationsStore.activeSession.value);

// Query branches for lookup
const branchesQuery = useQuery({
  queryKey: ['branches'],
  queryFn: ({ signal }) => listBranches({}, undefined, signal),
});
const branches = computed<Branch[]>(() => branchesQuery.data.value?.items ?? []);

function getLocalizedBranchName(name: string): string {
  return t(name);
}

function formatStatus(status: string): string {
  if (locale.value === 'th-TH') {
    switch (status) {
      case 'CONFIRMED':
        return 'ยืนยันแล้ว';
      case 'CHECKED_IN':
        return 'เช็คอินแล้ว';
      case 'COMPLETED':
        return 'เสร็จสิ้น';
      case 'CANCELLED':
        return 'ยกเลิกแล้ว';
      case 'NO_SHOW':
        return 'ไม่มาตามนัด';
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

// Filtered reservations from the store
const allReservations = computed(() => reservationsStore.reservations.value);

const filteredReservations = computed(() => {
  return allReservations.value.filter((r) => {
    const matchesStatus =
      selectedStatusFilter.value === 'ALL' || r.status === selectedStatusFilter.value;
    const matchesBranch =
      selectedBranchFilter.value === 'ALL' ||
      r.branchId === selectedBranchFilter.value ||
      r.branchName.toLowerCase() === selectedBranchFilter.value.toLowerCase();
    return matchesStatus && matchesBranch;
  });
});

// Format user-friendly date and time
function formatFriendlyDateTime(isoStr: string): string {
  if (!isoStr) return '';
  const d = new Date(isoStr);
  return `${d.toLocaleDateString(locale.value === 'th-TH' ? 'th-TH' : 'en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })} • ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
}

function formatTimeOnly(isoStr: string): string {
  if (!isoStr) return '';
  const d = new Date(isoStr);
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function openCancelModal(res: CustomerReservation) {
  cancellingReservation.value = res;
}

function confirmCancel() {
  if (!cancellingReservation.value) return;
  isCancelling.value = true;
  try {
    reservationsStore.cancelReservation(cancellingReservation.value.id);
    cancellingReservation.value = null;
  } finally {
    isCancelling.value = false;
  }
}
</script>

<template>
  <div class="my-reservations-page">
    <div class="container">
      <!-- Page Header -->
      <header class="page-header">
        <div class="header-titles">
          <p class="header-eyebrow">THE PARLOR RESERVATION HISTORY</p>
          <h1 class="page-title">{{ t('My Reservations') }}</h1>
          <p class="page-subtitle">
            {{ t('Manage your booked gaming tables and pre-ordered food & refreshments.') }}
          </p>
        </div>
        <div class="header-actions">
          <RouterLink to="/reservations/new" class="btn btn-primary add-res-btn">
            + {{ t('Reserve Table') }}
          </RouterLink>
        </div>
      </header>

      <!-- Auth Gate Check -->
      <section v-if="!auth.authenticated" class="auth-required-box" aria-live="polite">
        <div class="auth-icon-circle">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
            <path d="M7 11V7a5 5 0 0 1 10 0v4" />
          </svg>
        </div>
        <h2 class="auth-title">{{ t('Please Sign In') }}</h2>
        <p class="auth-text">
          {{ t('Sign in to your Parlor account to view your reservations.') }}
        </p>
        <RouterLink :to="{ name: 'AuthLogin', query: { redirect: '/reservations' } }" class="btn btn-primary">
          {{ t('Sign In / Register') }} →
        </RouterLink>
      </section>

      <section v-else class="reservations-content">
        <!-- Active Ongoing Session Spotlight Card -->
        <div v-if="activeSession" class="active-session-banner">
          <div class="active-session-left">
            <p class="session-eyebrow">
              <span class="live-pulse-dot" aria-hidden="true"></span>
              <strong>{{ t('ACTIVE ON-GOING SESSION') }}</strong>
            </p>
            <h2 class="active-table-name">
              {{ t(activeSession.tableLabel) }}
              <span class="active-branch">{{ t(activeSession.branchName) }}</span>
            </h2>
            <ul class="active-meta-chips">
              <li class="meta-chip">{{ t('Playing for') }}: {{ formatTimeOnly(activeSession.checkedInAt || activeSession.startsAt) }}</li>
              <li class="meta-chip">{{ activeSession.partySize }} {{ t('players') }}</li>
              <li class="meta-chip">฿{{ activeSession.hourlyRate || 200 }}/{{ t('hr') }}</li>
              <li v-if="activeSession.inPlayOrders && activeSession.inPlayOrders.length > 0" class="meta-chip chip-orders">
                {{ activeSession.inPlayOrders.length }} {{ t('In-Play Orders') }}
              </li>
            </ul>
          </div>

          <div class="active-session-actions">
            <button
              type="button"
              class="btn btn-order-table"
              @click="orderingReservation = activeSession"
            >
              + {{ t('Order Food & Drinks to Table') }}
            </button>
            <button
              type="button"
              class="btn btn-view-bill"
              @click="billingReservation = activeSession"
            >
              {{ t('Live Table Bill & Tab') }}
            </button>
          </div>
        </div>

        <!-- Filter Bar -->
        <div class="filters-card">
          <div class="filter-group">
            <span class="filter-label">{{ t('Status') }}:</span>
            <div class="status-pills" role="tablist">
              <button
                v-for="st in ['ALL', 'CONFIRMED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED']"
                :key="st"
                type="button"
                role="tab"
                :aria-selected="selectedStatusFilter === st"
                class="pill-btn"
                :class="{ active: selectedStatusFilter === st }"
                @click="selectedStatusFilter = st"
              >
                {{ st === 'ALL' ? t('All Statuses') : formatStatus(st) }}
              </button>
            </div>
          </div>

          <div class="filter-group branch-filter-group">
            <label for="branch-filter" class="filter-label">{{ t('Branch') }}:</label>
            <select id="branch-filter" v-model="selectedBranchFilter" class="branch-select-input">
              <option value="ALL">{{ t('All Branches') }}</option>
              <option v-for="b in branches" :key="b.id" :value="b.name">{{ t(b.name) }}</option>
            </select>
          </div>
        </div>

        <!-- Empty State -->
        <div v-if="filteredReservations.length === 0" class="empty-reservations-box">
          <div class="empty-icon-wrap">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
              <line x1="16" y1="2" x2="16" y2="6" />
              <line x1="8" y1="2" x2="8" y2="6" />
              <line x1="3" y1="10" x2="21" y2="10" />
            </svg>
          </div>
          <h3 class="empty-title">{{ t('No Reservations Found') }}</h3>
          <p class="empty-text">
            {{ t('You have no reservations matching this filter.') }}
          </p>
          <RouterLink to="/reservations/new" class="btn btn-primary">
            + {{ t('Reserve a Table Now') }}
          </RouterLink>
        </div>

        <!-- Reservations List -->
        <div v-else class="reservations-grid">
          <article
            v-for="res in filteredReservations"
            :key="res.id"
            class="reservation-card"
            :class="`status-${res.status.toLowerCase()}`"
          >
            <!-- Card Header: Branch, Ref, Status -->
            <div class="card-header">
              <div class="branch-meta">
                <span class="branch-name">
                  {{ getLocalizedBranchName(res.branchName) }}
                </span>
                <span class="ref-badge">{{ t('Ref:') }} {{ res.id }}</span>
              </div>
              <span class="status-badge" :class="`badge-${res.status.toLowerCase()}`">
                {{ formatStatus(res.status) }}
              </span>
            </div>

            <!-- Card Body: Date, Times, ISO Timestamps, Table, Pre-orders -->
            <div class="card-body">
              <!-- Primary Schedule Display -->
              <div class="schedule-block">
                <div class="date-main">
                  <svg class="schedule-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                    <circle cx="12" cy="12" r="10" />
                    <polyline points="12 6 12 12 16 14" />
                  </svg>
                  <span class="schedule-text">
                    {{ formatFriendlyDateTime(res.startsAt) }} – {{ formatTimeOnly(res.endsAt) }}
                  </span>
                </div>

                <!-- Dedicated ISO Timestamps Callout (Requirement: ISO timestamp in reservation history) -->
                <div class="iso-timestamps-box">
                  <span class="iso-label">{{ t('ISO Timestamps (System Record):') }}</span>
                  <div class="iso-codes">
                    <code>{{ res.startsAt }}</code>
                    <span class="iso-arrow">→</span>
                    <code>{{ res.endsAt }}</code>
                  </div>
                </div>
              </div>

              <!-- Table and Party Size -->
              <dl class="details-row">
                <div class="detail-item">
                  <dt class="detail-lbl">{{ t('Table') }}:</dt>
                  <dd class="detail-val table-highlight">{{ t(res.tableLabel) }}</dd>
                </div>
                <div class="detail-item">
                  <dt class="detail-lbl">{{ t('Party size') }}:</dt>
                  <dd class="detail-val">{{ res.partySize }} {{ t('players') }}</dd>
                </div>
                <div v-if="res.customerName" class="detail-item">
                  <dt class="detail-lbl">{{ t('Reserved by') }}:</dt>
                  <dd class="detail-val">{{ res.customerName }}</dd>
                </div>
              </dl>

              <!-- Pre-ordered Food & Drinks Section (with 10% Discount) -->
              <div v-if="res.preOrder && res.preOrder.items && res.preOrder.items.length > 0" class="preorder-section">
                <div class="preorder-header">
                  <div class="preorder-title">
                    <span class="gold-bullet">◆</span>
                    <strong>{{ t('Pre-ordered Food & Drinks') }}</strong>
                  </div>
                  <span class="discount-pill">-10% {{ t('Pre-order Discount') }}</span>
                </div>

                <div class="preorder-items-list">
                  <div
                    v-for="itemRecord in res.preOrder.items"
                    :key="itemRecord.item.id"
                    class="preorder-item-row"
                  >
                    <span class="item-name-qty">
                      <span class="qty-badge">{{ itemRecord.quantity }}x</span>
                      {{ locale === 'th-TH' && itemRecord.item.nameTh ? itemRecord.item.nameTh : itemRecord.item.name }}
                    </span>
                    <span class="item-calc-price">
                      ฿{{ Math.round(itemRecord.item.price * 0.9 * itemRecord.quantity) }}
                    </span>
                  </div>
                </div>

                <div class="preorder-footer-bar">
                  <div class="payment-confirmed-tag">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                      <polyline points="20 6 9 17 4 12" />
                    </svg>
                    <span>{{ t('Paid via PromptPay QR') }} (Ref: {{ res.preOrder.paymentReference }})</span>
                  </div>
                  <div class="preorder-sum">
                    <span class="original-sum">฿{{ res.preOrder.subtotal }}</span>
                    <span class="discounted-sum">฿{{ res.preOrder.discountedTotal }}</span>
                  </div>
                </div>
              </div>

              <!-- In-Play Orders Section -->
              <div v-if="res.inPlayOrders && res.inPlayOrders.length > 0" class="inplay-history-section">
                <div class="inplay-history-header">
                  <span class="gold-bullet">◆</span>
                  <strong>{{ t('In-Play Table Orders') }} ({{ res.inPlayOrders.length }})</strong>
                </div>

                <div class="inplay-orders-grid">
                  <div
                    v-for="ord in res.inPlayOrders"
                    :key="ord.id"
                    class="inplay-mini-card"
                  >
                    <div class="mini-card-top">
                      <span class="ord-ref">{{ ord.orderNumber }}</span>
                      <span class="ord-total">฿{{ ord.subtotal }}</span>
                    </div>
                    <div class="mini-items">
                      <span v-for="it in ord.items" :key="it.item.id">
                        {{ it.quantity }}x {{ locale === 'th-TH' && it.item.nameTh ? it.item.nameTh : it.item.name }}
                      </span>
                    </div>
                    <div v-if="ord.notes" class="mini-card-bottom">
                      <span class="ord-note">{{ ord.notes }}</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Special Notes -->
              <div v-if="res.notes && (!res.preOrder || !res.notes.startsWith('[Pre-order'))" class="notes-box">
                <span class="notes-tag">{{ t('Special Requests or Notes (Optional)') }}:</span>
                <p class="notes-text">{{ res.notes }}</p>
              </div>
            </div>

            <!-- Card Footer: In-Play Prompt & Cancel Action -->
            <div class="card-footer">
              <div v-if="res.status === 'CHECKED_IN'" class="inplay-live-actions">
                <button
                  type="button"
                  class="btn-live-order"
                  @click="orderingReservation = res"
                >
                  + {{ t('Order Food to Table') }}
                </button>
                <button
                  type="button"
                  class="btn-live-bill"
                  @click="billingReservation = res"
                >
                  {{ t('Live Table Bill') }}
                </button>
              </div>
              <div v-else class="inplay-prompt">
                <span>{{ t('Need more snacks or refills while playing? You can scan your table’s menu code to order food and drinks directly to your table at any time during your visit.') }}</span>
              </div>

              <div v-if="res.status === 'CONFIRMED'" class="card-actions">
                <button
                  type="button"
                  class="btn-cancel"
                  @click="openCancelModal(res)"
                >
                  {{ t('Cancel Reservation') }}
                </button>
              </div>
            </div>
          </article>
        </div>
      </section>

      <!-- Cancel Confirmation Modal -->
      <Transition name="fade">
        <div
          v-if="cancellingReservation"
          class="modal-overlay"
          role="dialog"
          aria-modal="true"
          @click.self="cancellingReservation = null"
        >
          <div class="modal-card">
            <h3 class="modal-heading">{{ t('Cancel Table Reservation?') }}</h3>
            <p class="modal-desc">
              {{ t('Cancel Reservation') }}:
              <strong>{{ getLocalizedBranchName(cancellingReservation.branchName) }}</strong> —
              {{ formatFriendlyDateTime(cancellingReservation.startsAt) }}
            </p>

            <div class="modal-buttons">
              <button
                type="button"
                class="btn btn-secondary"
                @click="cancellingReservation = null"
              >
                {{ t('Keep Reservation') }}
              </button>
              <button
                type="button"
                class="btn btn-danger"
                :disabled="isCancelling"
                @click="confirmCancel"
              >
                <span v-if="isCancelling">{{ t('Cancelling…') }}</span>
                <span v-else>{{ t('Cancel Reservation') }}</span>
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </div>

    <!-- In-Play Ordering Modal -->
    <InPlayOrderModal
      v-if="orderingReservation"
      :reservation="orderingReservation"
      @close="orderingReservation = null"
    />

    <!-- Live Table Bill Modal -->
    <TableBillModal
      v-if="billingReservation"
      :reservation="billingReservation"
      @close="billingReservation = null"
    />
  </div>
</template>

<style scoped>
/*
 * Reservation surfaces: ivory page, ivory panels, deep green for highlights,
 * gold only for rules, icons and the primary action. One scale for radii
 * (12 card / 8 block & control / 999 pill), 1px borders, 40px controls.
 */
.my-reservations-page {
  --surface: var(--color-parlor-ivory-50);
  --sunken: var(--color-parlor-ivory-200);
  --line: var(--color-parlor-ivory-300);
  --control-border: var(--color-parlor-ivory-400);
  --ink: var(--color-parlor-green-900);
  --muted: var(--color-parlor-neutral-600);
  --radius-card: 12px;
  --radius: 8px;
  --control-h: 40px;
  --shadow: 0 1px 2px color-mix(in srgb, var(--color-parlor-green-950) 6%, transparent);

  background: var(--color-parlor-ivory-100);
  min-height: calc(100svh - var(--parlor-header-height, 64px));
  padding: 40px 24px 80px;
  color: var(--ink);
  font-family: 'FC Mee Sakul', 'Plus Jakarta Sans', system-ui, Arial, sans-serif;
}

.container {
  max-width: 960px;
  margin: 0 auto;
}

/* ---------- Page header ---------- */
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px 24px;
  flex-wrap: wrap;
  margin-bottom: 28px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-parlor-gold-500);
}

.header-titles {
  min-width: 0;
  flex: 1 1 360px;
}

.header-eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--color-parlor-gold-700);
}

.page-title {
  margin: 0 0 6px;
  font-size: 30px;
  line-height: 1.15;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--ink);
}

.page-subtitle {
  margin: 0;
  font-size: 14.5px;
  line-height: 1.5;
  color: var(--muted);
}

.header-actions {
  flex: none;
}

.add-res-btn {
  white-space: nowrap;
}

/* ---------- Buttons (one height, one radius) ---------- */
.btn,
.btn-cancel,
.btn-live-order,
.btn-live-bill,
.btn-order-table,
.btn-view-bill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: var(--control-h);
  padding: 0 18px;
  border: 1px solid transparent;
  border-radius: var(--radius);
  font: inherit;
  font-size: 13.5px;
  font-weight: 700;
  line-height: 1.2;
  text-align: center;
  text-decoration: none;
  cursor: pointer;
  transition:
    background-color 150ms ease,
    border-color 150ms ease,
    color 150ms ease;
}

.btn-primary,
.btn-order-table {
  background: var(--color-parlor-gold-500);
  border-color: var(--color-parlor-gold-500);
  color: var(--color-parlor-green-950);
}
.btn-primary:hover,
.btn-order-table:hover {
  background: var(--color-parlor-gold-300);
  border-color: var(--color-parlor-gold-300);
}

.btn-secondary,
.btn-live-bill {
  background: var(--surface);
  border-color: var(--control-border);
  color: var(--ink);
}
.btn-secondary:hover,
.btn-live-bill:hover {
  background: var(--sunken);
  border-color: var(--color-parlor-gold-500);
}

.btn-live-order {
  background: var(--color-parlor-green-900);
  border-color: var(--color-parlor-green-900);
  color: var(--color-parlor-cream);
}
.btn-live-order:hover {
  background: var(--color-parlor-green-800);
}

.btn-view-bill {
  background: transparent;
  border-color: color-mix(in srgb, var(--color-parlor-gold-500) 70%, transparent);
  color: var(--color-parlor-cream);
}
.btn-view-bill:hover {
  background: color-mix(in srgb, var(--color-parlor-gold-500) 14%, transparent);
  border-color: var(--color-parlor-gold-500);
}

.btn-cancel {
  min-height: 36px;
  padding: 0 14px;
  background: transparent;
  border-color: var(--color-parlor-danger-300);
  color: var(--color-parlor-danger-700);
  font-size: 13px;
}
.btn-cancel:hover {
  background: var(--color-parlor-danger-100);
  border-color: var(--color-parlor-danger-700);
}

.btn-danger {
  background: var(--color-parlor-danger-700);
  border-color: var(--color-parlor-danger-700);
  color: var(--color-parlor-white);
}
.btn-danger:hover {
  background: var(--color-parlor-danger-600);
  border-color: var(--color-parlor-danger-600);
}
.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn:focus-visible,
.btn-cancel:focus-visible,
.btn-live-order:focus-visible,
.btn-live-bill:focus-visible,
.btn-order-table:focus-visible,
.btn-view-bill:focus-visible,
.pill-btn:focus-visible,
.branch-select-input:focus-visible {
  outline: 2px solid var(--color-parlor-gold-500);
  outline-offset: 2px;
}

/* ---------- Shared panel ---------- */
.auth-required-box,
.filters-card,
.empty-reservations-box,
.reservation-card,
.modal-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow);
}

/* ---------- Auth gate ---------- */
.auth-required-box {
  max-width: 520px;
  margin: 40px auto;
  padding: 44px 28px;
  text-align: center;
}

.auth-icon-circle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  margin: 0 auto 16px;
  border-radius: 50%;
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
}

.auth-title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 800;
  color: var(--ink);
}

.auth-text {
  margin: 0 0 24px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--muted);
}

/* ---------- Active session ---------- */
.active-session-banner {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-bottom: 24px;
  padding: 24px;
  background: var(--color-parlor-green-900);
  border: 1px solid color-mix(in srgb, var(--color-parlor-gold-500) 45%, transparent);
  border-radius: var(--radius-card);
  color: var(--color-parlor-cream);
  box-shadow: var(--shadow);
}

.session-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--color-parlor-gold-300);
}

.live-pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-parlor-success-400);
  animation: pulseLive 2s ease-in-out infinite;
}

@keyframes pulseLive {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}

.active-table-name {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin: 0 0 14px;
  font-size: 24px;
  line-height: 1.2;
  font-weight: 800;
  color: var(--color-parlor-white);
}

.active-branch {
  font-size: 15px;
  font-weight: 600;
  color: color-mix(in srgb, var(--color-parlor-cream) 80%, transparent);
}

.active-meta-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.meta-chip {
  padding: 5px 12px;
  border: 1px solid color-mix(in srgb, var(--color-parlor-cream) 22%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-parlor-cream) 8%, transparent);
  color: var(--color-parlor-cream);
  font-size: 12.5px;
  font-weight: 600;
}

.meta-chip.chip-orders {
  border-color: color-mix(in srgb, var(--color-parlor-success-400) 50%, transparent);
  color: var(--color-parlor-success-300);
}

.active-session-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 18px;
  border-top: 1px solid color-mix(in srgb, var(--color-parlor-gold-500) 25%, transparent);
}

/* ---------- Filters ---------- */
.filters-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 14px 24px;
  margin-bottom: 24px;
  padding: 14px 18px;
}

.filter-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  min-width: 0;
}

.filter-label {
  font-size: 13px;
  font-weight: 700;
  color: var(--ink);
}

.status-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.pill-btn {
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid var(--control-border);
  border-radius: 999px;
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background-color 150ms ease,
    border-color 150ms ease,
    color 150ms ease;
}

.pill-btn:hover {
  background: var(--sunken);
}

.pill-btn.active {
  background: var(--color-parlor-green-900);
  border-color: var(--color-parlor-green-900);
  color: var(--color-parlor-cream);
}

.branch-select-input {
  min-height: 34px;
  padding: 0 32px 0 12px;
  border: 1px solid var(--control-border);
  border-radius: var(--radius);
  background-color: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
}

/* ---------- Empty ---------- */
.empty-reservations-box {
  padding: 52px 24px;
  text-align: center;
}

.empty-icon-wrap {
  margin-bottom: 12px;
  color: var(--color-parlor-gold-700);
}

.empty-title {
  margin: 0 0 8px;
  font-size: 19px;
  font-weight: 800;
  color: var(--ink);
}

.empty-text {
  margin: 0 0 20px;
  font-size: 14px;
  color: var(--muted);
}

/* ---------- Reservation history cards ---------- */
.reservations-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.reservation-card {
  display: flex;
  flex-direction: column;
  transition: border-color 150ms ease;
}

.reservation-card:hover {
  border-color: var(--control-border);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px 12px;
  padding: 18px 24px;
  border-bottom: 1px solid var(--line);
}

.branch-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 12px;
  min-width: 0;
}

.branch-name {
  font-size: 17px;
  font-weight: 800;
  color: var(--ink);
}

.gold-bullet {
  color: var(--color-parlor-gold-700);
  font-size: 10px;
}

.ref-badge {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: var(--muted);
}

.status-badge {
  padding: 4px 12px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 700;
  white-space: nowrap;
}

.badge-confirmed {
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-cream);
}

.badge-checked_in {
  background: var(--color-parlor-info-100);
  border-color: var(--color-parlor-info-300);
  color: var(--color-parlor-info-700);
}

.badge-completed {
  background: var(--sunken);
  border-color: var(--line);
  color: var(--muted);
}

.badge-cancelled {
  background: var(--color-parlor-danger-100);
  border-color: var(--color-parlor-danger-300);
  color: var(--color-parlor-danger-700);
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 20px 24px;
}

/* Date first, system timestamps quietly underneath */
.schedule-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.date-main {
  display: flex;
  align-items: center;
  gap: 10px;
}

.schedule-icon {
  flex: none;
  color: var(--color-parlor-gold-700);
}

.schedule-text {
  font-size: 16px;
  font-weight: 800;
  color: var(--ink);
}

.iso-timestamps-box {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 2px 8px;
  padding-left: 28px;
  font-size: 11.5px;
  color: var(--muted);
}

.iso-label {
  font-weight: 600;
}

.iso-codes {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 6px;
  min-width: 0;
}

.iso-codes code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11.5px;
  color: var(--muted);
  overflow-wrap: anywhere;
}

.iso-arrow {
  color: var(--muted);
}

/* Table / party / reserved by */
.details-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 10px 24px;
  margin: 0;
  padding: 14px 0 0;
  border-top: 1px solid var(--line);
}

.detail-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
  font-size: 14px;
}

.detail-lbl {
  font-weight: 600;
  color: var(--muted);
}

.detail-val {
  margin: 0;
  font-weight: 700;
  color: var(--ink);
  overflow-wrap: anywhere;
}

.table-highlight {
  font-weight: 800;
}

/* Sunken sub-blocks */
.preorder-section,
.inplay-history-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  background: var(--sunken);
  border-radius: var(--radius);
}

.preorder-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.preorder-title,
.inplay-history-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
  color: var(--ink);
}

.discount-pill {
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-cream);
  font-size: 11.5px;
  font-weight: 700;
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
  gap: 12px;
  font-size: 13.5px;
  color: var(--ink);
}

.item-name-qty {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.qty-badge {
  font-weight: 800;
  color: var(--muted);
}

.item-calc-price {
  font-weight: 700;
  white-space: nowrap;
}

.preorder-footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 12px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
}

.payment-confirmed-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 12px;
  font-weight: 700;
  color: var(--color-parlor-success-700);
  overflow-wrap: anywhere;
}

.preorder-sum {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.original-sum {
  font-size: 12.5px;
  color: var(--muted);
  text-decoration: line-through;
}

.discounted-sum {
  font-size: 15px;
  font-weight: 800;
  color: var(--ink);
}

.inplay-orders-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}

.inplay-mini-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.mini-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.ord-ref {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--muted);
}

.ord-total {
  font-weight: 800;
  color: var(--ink);
}

.mini-items {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12.5px;
  color: var(--ink);
}

.mini-card-bottom {
  padding-top: 6px;
  border-top: 1px solid var(--line);
  font-size: 12px;
}

.ord-note {
  color: var(--muted);
  font-style: italic;
}

.kitchen-pill,
.pay-pill {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}
.kitchen-pill.status-received {
  background: var(--color-parlor-warning-100);
  color: var(--color-parlor-warning-700);
}
.kitchen-pill.status-preparing {
  background: var(--color-parlor-warning-100);
  color: var(--color-parlor-warning-600);
}
.kitchen-pill.status-served,
.pay-pill.paid {
  background: var(--color-parlor-success-100);
  color: var(--color-parlor-success-700);
}
.pay-pill.tab {
  background: var(--sunken);
  color: var(--color-parlor-gold-700);
}

/* Notes */
.notes-box {
  padding: 10px 14px;
  border-left: 2px solid var(--color-parlor-gold-500);
  background: var(--sunken);
  border-radius: 0 var(--radius) var(--radius) 0;
}

.notes-tag {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 700;
  color: var(--muted);
}

.notes-text {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.5;
  color: var(--ink);
  overflow-wrap: anywhere;
}

.cancelled-box {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 10px 14px;
  border-left: 2px solid var(--color-parlor-danger-700);
  background: var(--color-parlor-danger-100);
  border-radius: 0 var(--radius) var(--radius) 0;
  font-size: 13px;
}
.cancel-lbl {
  font-weight: 700;
  color: var(--color-parlor-danger-700);
}
.cancel-text {
  color: var(--color-parlor-danger-700);
}

/* Footer */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px 16px;
  padding: 14px 24px;
  border-top: 1px solid var(--line);
  background: color-mix(in srgb, var(--sunken) 45%, var(--surface));
  border-radius: 0 0 var(--radius-card) var(--radius-card);
}

.inplay-prompt {
  flex: 1 1 320px;
  max-width: 640px;
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--muted);
}

.inplay-live-actions,
.card-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.inplay-live-actions .btn-live-order,
.inplay-live-actions .btn-live-bill {
  min-height: 36px;
  padding: 0 14px;
  font-size: 13px;
}

/* ---------- Cancel modal ---------- */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: color-mix(in srgb, var(--color-parlor-green-950) 55%, transparent);
}

.modal-card {
  width: 100%;
  max-width: 460px;
  padding: 26px 28px;
  box-shadow: 0 12px 32px color-mix(in srgb, var(--color-parlor-green-950) 22%, transparent);
}

.modal-heading {
  margin: 0 0 8px;
  font-size: 19px;
  font-weight: 800;
  color: var(--ink);
}

.modal-desc {
  margin: 0 0 20px;
  font-size: 14px;
  line-height: 1.5;
  color: var(--muted);
}

.modal-form-group {
  margin-bottom: 20px;
}

.modal-label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 700;
  color: var(--ink);
}

.form-select {
  box-sizing: border-box;
  width: 100%;
  min-height: var(--control-h);
  padding: 0 12px;
  border: 1px solid var(--control-border);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 14px;
}

.modal-buttons {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 160ms ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ---------- Responsive ---------- */
@media (max-width: 700px) {
  .my-reservations-page {
    padding: 28px 16px 64px;
  }
  .page-title {
    font-size: 26px;
  }
  .header-actions,
  .add-res-btn {
    width: 100%;
  }
  .active-session-banner {
    padding: 20px 18px;
  }
  .active-table-name {
    font-size: 21px;
  }
  .active-session-actions > .btn {
    flex: 1 1 100%;
  }
  .filters-card {
    padding: 14px 16px;
  }
  .branch-filter-group {
    width: 100%;
  }
  .branch-select-input {
    flex: 1;
  }
  .card-header,
  .card-body,
  .card-footer {
    padding-left: 16px;
    padding-right: 16px;
  }
  .iso-timestamps-box {
    padding-left: 0;
  }
  .card-actions,
  .card-actions .btn-cancel,
  .inplay-live-actions {
    width: 100%;
  }
  .inplay-live-actions > * {
    flex: 1;
  }
  .modal-buttons > .btn {
    flex: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .live-pulse-dot {
    animation: none;
  }
  .btn,
  .btn-cancel,
  .btn-live-order,
  .btn-live-bill,
  .btn-order-table,
  .btn-view-bill,
  .pill-btn,
  .reservation-card,
  .fade-enter-active,
  .fade-leave-active {
    transition: none;
  }
}
</style>

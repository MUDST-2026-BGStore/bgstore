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
          <div class="header-eyebrow">
            <span class="gold-sparkle">✦</span>
            <span>THE PARLOR RESERVATION HISTORY</span>
          </div>
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
            <div class="session-eyebrow">
              <span class="live-pulse-dot"></span>
              <strong>{{ t('ACTIVE ON-GOING SESSION') }}</strong>
            </div>
            <h2 class="active-table-name">
              {{ t(activeSession.tableLabel) }} • {{ t(activeSession.branchName) }}
            </h2>
            <div class="active-meta-chips">
              <span class="meta-chip"><span class="gold-bullet">◆</span> {{ t('Playing for') }}: {{ formatTimeOnly(activeSession.checkedInAt || activeSession.startsAt) }}</span>
              <span class="meta-chip">{{ activeSession.partySize }} {{ t('players') }}</span>
              <span class="meta-chip">฿{{ activeSession.hourlyRate || 200 }}/{{ t('hr') }}</span>
              <span v-if="activeSession.inPlayOrders && activeSession.inPlayOrders.length > 0" class="meta-chip chip-orders">
                <span class="gold-bullet">◆</span> {{ activeSession.inPlayOrders.length }} {{ t('In-Play Orders') }}
              </span>
            </div>
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
                  <span class="gold-bullet">◆</span>
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
                  <svg class="schedule-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
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
              <div class="details-row">
                <div class="detail-item">
                  <span class="detail-lbl">{{ t('Table') }}:</span>
                  <span class="detail-val table-highlight">{{ t(res.tableLabel) }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-lbl">{{ t('Party size') }}:</span>
                  <span class="detail-val">{{ res.partySize }} {{ t('players') }}</span>
                </div>
                <div v-if="res.customerName" class="detail-item">
                  <span class="detail-lbl">{{ t('Reserved by') }}:</span>
                  <span class="detail-val">{{ res.customerName }}</span>
                </div>
              </div>

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
                <span class="sparkle-gold">✦</span>
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
.my-reservations-page {
  background: var(--color-parlor-page);
  min-height: calc(100vh - 120px);
  padding: 36px 16px 80px;
  color: var(--color-parlor-green-900);
  font-family: 'FC Mee Sakul', 'Plus Jakarta Sans', system-ui, Arial, sans-serif;
}

.container {
  max-width: 960px;
  margin: 0 auto;
}

/* Page Header */
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 28px;
  border-bottom: 2px solid var(--color-parlor-gold-500);
  padding-bottom: 20px;
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
  color: var(--color-parlor-gold-700);
  margin-bottom: 6px;
}

.gold-sparkle {
  color: var(--color-parlor-gold-500);
  font-size: 14px;
}

.page-title {
  margin: 0 0 6px;
  font-size: 30px;
  font-weight: 800;
  color: var(--color-parlor-green-900);
  letter-spacing: -0.02em;
}

.page-subtitle {
  margin: 0;
  font-size: 14.5px;
  color: var(--color-parlor-neutral-600);
  line-height: 1.5;
}

.add-res-btn {
  white-space: nowrap;
}

/* Auth Required Box */
.auth-required-box {
  background: var(--color-parlor-white);
  border: 1.5px solid var(--color-parlor-gold-500);
  border-radius: 12px;
  padding: 48px 24px;
  text-align: center;
  max-width: 520px;
  margin: 40px auto;
  box-shadow: 0 8px 24px color-mix(in srgb, var(--color-parlor-green-900) 8%, transparent);
}

.auth-icon-circle {
  width: 56px;
  height: 56px;
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
}

.auth-title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 800;
  color: var(--color-parlor-green-900);
}

.auth-text {
  margin: 0 0 24px;
  font-size: 14px;
  color: var(--color-parlor-neutral-600);
  line-height: 1.6;
}

/* Filter Card */
.filters-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--color-parlor-white);
  border: 1px solid var(--color-parlor-neutral-300);
  border-radius: 10px;
  padding: 14px 18px;
  margin-bottom: 24px;
  box-shadow: 0 2px 8px color-mix(in srgb, var(--color-parlor-green-800) 4%, transparent);
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
  color: var(--color-parlor-green-900);
}

.status-pills {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.pill-btn {
  background: var(--color-parlor-neutral-100);
  border: 1px solid var(--color-parlor-neutral-300);
  border-radius: 20px;
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  color: var(--color-parlor-neutral-800);
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.pill-btn:hover {
  background: var(--color-parlor-neutral-150);
  border-color: var(--color-parlor-gold-500);
}

.pill-btn.active {
  background: var(--color-parlor-green-900);
  border-color: var(--color-parlor-gold-500);
  color: var(--color-parlor-gold-300);
  box-shadow: 0 2px 8px color-mix(in srgb, var(--color-parlor-green-900) 25%, transparent);
}

.branch-select-input {
  padding: 6px 12px;
  border: 1px solid var(--color-parlor-neutral-300);
  border-radius: 6px;
  background: var(--color-parlor-white);
  font-size: 13px;
  color: var(--color-parlor-green-900);
  font-weight: 600;
}

/* Empty Box */
.empty-reservations-box {
  background: var(--color-parlor-white);
  border: 1.5px dashed var(--color-parlor-gold-500);
  border-radius: 12px;
  padding: 52px 20px;
  text-align: center;
  margin-top: 10px;
}

.empty-icon-wrap {
  color: var(--color-parlor-gold-500);
  margin-bottom: 12px;
}

.empty-title {
  margin: 0 0 8px;
  font-size: 19px;
  font-weight: 800;
  color: var(--color-parlor-green-900);
}

.empty-text {
  margin: 0 0 20px;
  font-size: 14px;
  color: var(--color-parlor-neutral-600);
}

/* Reservations Grid & Cards */
.reservations-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.reservation-card {
  background: var(--color-parlor-white);
  border: 1.5px solid var(--color-parlor-neutral-300);
  border-radius: 12px;
  padding: 22px 24px;
  box-shadow: 0 4px 16px color-mix(in srgb, var(--color-parlor-green-900) 5%, transparent);
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

.reservation-card:hover {
  border-color: var(--color-parlor-gold-500);
  box-shadow: 0 8px 24px color-mix(in srgb, var(--color-parlor-green-900) 10%, transparent), 0 0 12px color-mix(in srgb, var(--color-parlor-gold-500) 12%, transparent);
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--color-parlor-neutral-150);
  padding-bottom: 14px;
  gap: 12px;
  flex-wrap: wrap;
}

.branch-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.branch-name {
  font-size: 17px;
  font-weight: 800;
  color: var(--color-parlor-green-900);
  display: flex;
  align-items: center;
  gap: 6px;
}

.gold-bullet {
  color: var(--color-parlor-gold-500);
  font-size: 12px;
}

.ref-badge {
  font-size: 11px;
  color: var(--color-parlor-neutral-600);
  font-family: monospace;
  background: var(--color-parlor-neutral-100);
  border: 1px solid var(--color-parlor-neutral-300);
  padding: 3px 8px;
  border-radius: 4px;
  font-weight: 600;
}

.status-badge {
  font-size: 12px;
  font-weight: 700;
  padding: 4px 12px;
  border-radius: 14px;
  letter-spacing: 0.02em;
  text-transform: none;
}

.badge-confirmed {
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  border: 1px solid var(--color-parlor-gold-500);
  box-shadow: 0 0 8px color-mix(in srgb, var(--color-parlor-gold-500) 30%, transparent);
}

.badge-checked_in {
  background: var(--color-parlor-info-100);
  color: var(--color-parlor-info-700);
  border: 1px solid var(--color-parlor-info-300);
}

.badge-completed {
  background: var(--color-parlor-neutral-100);
  color: var(--color-parlor-neutral-600);
  border: 1px solid var(--color-parlor-neutral-300);
}

.badge-cancelled {
  background: var(--color-parlor-danger-100);
  color: var(--color-parlor-danger-700);
  border: 1px solid var(--color-parlor-danger-300);
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* Schedule and ISO Timestamps */
.schedule-block {
  background: var(--color-parlor-page);
  border: 1px solid var(--color-parlor-neutral-300);
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
}

.schedule-icon {
  color: var(--color-parlor-gold-500);
}

.schedule-text {
  font-size: 15px;
  font-weight: 800;
  color: var(--color-parlor-green-900);
}

.iso-timestamps-box {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  border-top: 1px dashed var(--color-parlor-neutral-300);
  padding-top: 6px;
  margin-top: 2px;
}

.iso-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--color-parlor-neutral-600);
}

.iso-codes {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.iso-codes code {
  font-size: 11px;
  background: var(--color-parlor-neutral-150);
  color: var(--color-parlor-neutral-800);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid var(--color-parlor-neutral-300);
  font-family: monospace;
}

.iso-arrow {
  color: var(--color-parlor-gold-500);
  font-weight: 800;
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
  color: var(--color-parlor-neutral-600);
  font-weight: 600;
}

.detail-val {
  font-weight: 700;
  color: var(--color-parlor-green-900);
}

.table-highlight {
  color: var(--color-parlor-green-900);
  font-weight: 800;
}

/* Pre-order Breakdown */
.preorder-section {
  background: var(--color-parlor-page);
  border: 1px solid var(--color-parlor-neutral-300);
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
  border-bottom: 1px solid var(--color-parlor-neutral-150);
  padding-bottom: 8px;
}

.preorder-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
  color: var(--color-parlor-green-900);
}

.discount-pill {
  font-size: 10.5px;
  font-weight: 800;
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  border: 1px solid var(--color-parlor-gold-500);
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
  color: var(--color-parlor-green-800);
}

.item-name-qty {
  display: flex;
  align-items: center;
  gap: 8px;
}

.qty-badge {
  font-weight: 800;
  color: var(--color-parlor-green-900);
  background: var(--color-parlor-neutral-150);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11.5px;
}

.item-calc-price {
  font-weight: 700;
  color: var(--color-parlor-green-900);
}

.preorder-footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px dashed var(--color-parlor-neutral-300);
  padding-top: 10px;
  margin-top: 2px;
  gap: 12px;
  flex-wrap: wrap;
}

.payment-confirmed-tag {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--color-parlor-success-700);
}

.preorder-sum {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.original-sum {
  font-size: 12px;
  color: var(--color-parlor-neutral-600);
  text-decoration: line-through;
}

.discounted-sum {
  font-size: 15px;
  font-weight: 800;
  color: var(--color-parlor-green-900);
}

/* Notes & Cancelled Reason */
.notes-box {
  background: var(--color-parlor-page);
  border-left: 3px solid var(--color-parlor-gold-500);
  padding: 8px 12px;
  border-radius: 0 6px 6px 0;
}

.notes-tag {
  font-size: 11.5px;
  font-weight: 700;
  color: var(--color-parlor-neutral-600);
  display: block;
  margin-bottom: 2px;
}

.notes-text {
  margin: 0;
  font-size: 12.5px;
  color: var(--color-parlor-neutral-800);
  line-height: 1.4;
}

.cancelled-box {
  background: var(--color-parlor-danger-100);
  border-left: 3px solid var(--color-parlor-danger-700);
  padding: 8px 12px;
  border-radius: 0 6px 6px 0;
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 12.5px;
}

.cancel-lbl {
  font-weight: 700;
  color: var(--color-parlor-danger-700);
}

.cancel-text {
  color: var(--color-parlor-danger-700);
}

/* Card Footer */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--color-parlor-neutral-150);
  padding-top: 14px;
  gap: 12px;
  flex-wrap: wrap;
}

.inplay-prompt {
  font-size: 12px;
  color: var(--color-parlor-green-600);
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 650px;
  line-height: 1.4;
}

.sparkle-gold {
  color: var(--color-parlor-gold-500);
}

.btn-cancel {
  background: transparent;
  border: 1px solid var(--color-parlor-danger-300);
  color: var(--color-parlor-danger-700);
  font-size: 12.5px;
  font-weight: 700;
  padding: 6px 14px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-cancel:hover {
  background: var(--color-parlor-danger-100);
  border-color: var(--color-parlor-danger-700);
}

/* General Buttons */
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
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.btn-primary {
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  border: 1px solid var(--color-parlor-gold-500);
  box-shadow: 0 2px 8px color-mix(in srgb, var(--color-parlor-green-900) 20%, transparent);
}

.btn-primary:hover {
  background: var(--color-parlor-green-800);
  box-shadow: 0 4px 14px color-mix(in srgb, var(--color-parlor-green-900) 30%, transparent), 0 0 10px color-mix(in srgb, var(--color-parlor-gold-500) 40%, transparent);
  transform: translateY(-1px);
}

.btn-secondary {
  background: var(--color-parlor-white);
  border: 1px solid var(--color-parlor-neutral-300);
  color: var(--color-parlor-neutral-800);
}

.btn-secondary:hover {
  background: var(--color-parlor-neutral-100);
  border-color: var(--color-parlor-gold-500);
}

.btn-danger {
  background: var(--color-parlor-danger-700);
  color: var(--color-parlor-white);
  border: none;
}

.btn-danger:hover {
  background: var(--color-parlor-danger-700);
}

/* Modal Overlay */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: color-mix(in srgb, var(--color-parlor-green-950) 65%, transparent);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  padding: 16px;
}

.modal-card {
  background: var(--color-parlor-white);
  border: 1.5px solid var(--color-parlor-gold-500);
  border-radius: 12px;
  padding: 26px 28px;
  max-width: 460px;
  width: 100%;
  box-shadow: 0 12px 36px color-mix(in srgb, var(--color-parlor-green-950) 40%, transparent);
}

.modal-heading {
  margin: 0 0 8px;
  font-size: 19px;
  font-weight: 800;
  color: var(--color-parlor-green-900);
}

.modal-desc {
  margin: 0 0 18px;
  font-size: 13.5px;
  color: var(--color-parlor-neutral-600);
  line-height: 1.5;
}

.modal-form-group {
  margin-bottom: 20px;
}

.modal-label {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--color-parlor-green-900);
  display: block;
  margin-bottom: 6px;
}

.form-select {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--color-parlor-neutral-300);
  border-radius: 6px;
  font-size: 13.5px;
  color: var(--color-parlor-green-900);
  background: var(--color-parlor-white);
  box-sizing: border-box;
}

.modal-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* Active Ongoing Session Banner */
.active-session-banner {
  background: radial-gradient(ellipse at 50% 20%, var(--color-parlor-green-800) 0%, var(--color-parlor-green-900) 70%, var(--color-parlor-green-950) 100%);
  border: 2px solid var(--color-parlor-gold-500);
  border-radius: 14px;
  padding: 24px 28px;
  margin-bottom: 24px;
  color: var(--color-parlor-page);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  box-shadow: 0 8px 28px color-mix(in srgb, var(--color-parlor-green-950) 35%, transparent), 0 0 20px color-mix(in srgb, var(--color-parlor-gold-500) 25%, transparent);
  flex-wrap: wrap;
}

.session-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  color: var(--color-parlor-gold-300);
  margin-bottom: 6px;
}

.live-pulse-dot {
  width: 8px;
  height: 8px;
  background: var(--color-parlor-success-500);
  border-radius: 50%;
  box-shadow: 0 0 8px var(--color-parlor-success-500);
  animation: pulseLive 1.8s infinite;
}

@keyframes pulseLive {
  0% { transform: scale(0.95); opacity: 0.8; }
  50% { transform: scale(1.3); opacity: 1; }
  100% { transform: scale(0.95); opacity: 0.8; }
}

.active-table-name {
  margin: 0 0 10px;
  font-size: 24px;
  font-weight: 800;
  color: var(--color-parlor-white);
  letter-spacing: -0.01em;
}

.active-meta-chips {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.meta-chip {
  background: color-mix(in srgb, var(--color-parlor-gold-500) 20%, transparent);
  border: 1px solid color-mix(in srgb, var(--color-parlor-gold-500) 40%, transparent);
  color: var(--color-parlor-page);
  font-size: 11.5px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 12px;
}

.meta-chip.chip-orders {
  background: color-mix(in srgb, var(--color-parlor-success-500) 20%, transparent);
  border-color: var(--color-parlor-success-500);
  color: var(--color-parlor-success-300);
}

.active-session-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.btn-order-table {
  background: var(--color-parlor-gold-500);
  color: var(--color-parlor-green-900);
  font-size: 13.5px;
  font-weight: 800;
  padding: 11px 20px;
  border-radius: 8px;
  border: none;
  box-shadow: 0 4px 14px color-mix(in srgb, var(--color-parlor-gold-500) 40%, transparent);
}

.btn-order-table:hover {
  background: var(--color-parlor-gold-300);
  transform: translateY(-2px);
}

.btn-view-bill {
  background: transparent;
  color: var(--color-parlor-page);
  border: 1.5px solid var(--color-parlor-gold-500);
  font-size: 13.5px;
  font-weight: 800;
  padding: 10px 18px;
  border-radius: 8px;
}

.btn-view-bill:hover {
  background: color-mix(in srgb, var(--color-parlor-gold-500) 20%, transparent);
  transform: translateY(-2px);
}

/* In-Play Orders within Card */
.inplay-history-section {
  background: var(--color-parlor-page);
  border: 1px solid var(--color-parlor-neutral-300);
  border-radius: 8px;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.inplay-history-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-parlor-green-900);
}

.inplay-orders-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 10px;
}

.inplay-mini-card {
  background: var(--color-parlor-white);
  border: 1px solid var(--color-parlor-neutral-300);
  border-radius: 6px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mini-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.ord-ref {
  font-size: 11px;
  font-family: monospace;
  font-weight: 700;
  color: var(--color-parlor-green-900);
}

.kitchen-pill {
  font-size: 10px;
  font-weight: 800;
  padding: 2px 6px;
  border-radius: 4px;
}

.kitchen-pill.status-received {
  background: var(--color-parlor-warning-100);
  color: var(--color-parlor-warning-700);
}

.kitchen-pill.status-preparing {
  background: var(--color-parlor-warning-300);
  color: var(--color-parlor-warning-600);
}

.kitchen-pill.status-served {
  background: var(--color-parlor-success-100);
  color: var(--color-parlor-success-700);
}

.pay-pill {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
}

.pay-pill.tab {
  background: var(--color-parlor-neutral-100);
  color: var(--color-parlor-gold-700);
}

.pay-pill.paid {
  background: var(--color-parlor-success-100);
  color: var(--color-parlor-success-700);
}

.mini-items {
  font-size: 12px;
  color: var(--color-parlor-neutral-800);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mini-card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px dashed var(--color-parlor-neutral-150);
  padding-top: 4px;
  font-size: 11px;
}

.ord-total {
  font-weight: 800;
  color: var(--color-parlor-green-900);
}

.ord-note {
  color: var(--color-parlor-gold-700);
  font-style: italic;
}

.inplay-live-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-live-order {
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  border: 1px solid var(--color-parlor-gold-500);
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 12.5px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-live-order:hover {
  background: var(--color-parlor-green-800);
}

.btn-live-bill {
  background: var(--color-parlor-white);
  border: 1px solid var(--color-parlor-gold-500);
  color: var(--color-parlor-green-900);
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 12.5px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-live-bill:hover {
  background: var(--color-parlor-neutral-100);
}

</style>

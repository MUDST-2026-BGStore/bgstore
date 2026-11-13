<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue';
import { RouterLink } from 'vue-router';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import {
  cancelReservation,
  getBranchAvailability,
  getRental,
  listBranchGames,
  listMyReservations,
  listTables,
  rescheduleReservation,
  type Rental,
  type Reservation,
} from '@theparlor/api-client';
import { useAuthStore } from '../../../shared/stores/auth';
import { useBranchDirectoryStore } from '../../../shared/stores/branch-directory';
import { useStoreText } from '../../../shared/lib/store-text';
import { collectPages } from '../../../shared/lib/pagination';
import { httpStatus } from '../../../shared/lib/api-errors';
import {
  BRANCH_TIME_ZONE,
  branchDate,
  branchInstant,
  branchTime,
  canPreOrder,
  formatBaht,
  plusHours,
} from '../data/booking';

const auth = useAuthStore();
const directory = useBranchDirectoryStore();
const queryClient = useQueryClient();
const { t, locale } = useStoreText();

onMounted(() => void directory.load());

const STATUSES = [
  'ALL',
  'CONFIRMED',
  'CHECKED_IN',
  'COMPLETED',
  'CANCELLED',
  'NO_SHOW',
] as const;
const selectedStatusFilter = ref<string>('ALL');
const selectedBranchFilter = ref<string>('ALL');

const reservationsQuery = useQuery({
  queryKey: ['reservations', 'mine'],
  queryFn: ({ signal }) =>
    collectPages((cursor) =>
      listMyReservations({ limit: 100, cursor }, undefined, signal),
    ),
  enabled: computed(() => auth.authenticated),
});

const allReservations = computed<Reservation[]>(() =>
  [...(reservationsQuery.data.value ?? [])].sort((a, b) =>
    b.startsAt.localeCompare(a.startsAt),
  ),
);

const branchIds = computed(() =>
  [...new Set(allReservations.value.map((r) => r.branchId))].sort(),
);

/** Table labels and game names of every branch the customer has booked at. */
const labelsQuery = useQuery({
  queryKey: ['reservations', 'labels', branchIds],
  meta: { public: true },
  staleTime: 300_000,
  enabled: computed(() => branchIds.value.length > 0),
  queryFn: async ({ signal }) => {
    const tables = new Map<string, string>();
    const games = new Map<string, string>();
    await Promise.all(
      branchIds.value.map(async (branchId) => {
        const [branchTables, branchGames] = await Promise.all([
          collectPages((cursor) =>
            listTables(branchId, { limit: 100, cursor }, undefined, signal),
          ),
          collectPages((cursor) =>
            listBranchGames(
              branchId,
              { limit: 100, cursor },
              undefined,
              signal,
            ),
          ),
        ]);
        for (const table of branchTables) tables.set(table.id, table.label);
        for (const entry of branchGames)
          games.set(entry.game.id, entry.game.name);
      }),
    );
    return { tables, games };
  },
});

function tableLabel(r: Reservation): string {
  return labelsQuery.data.value?.tables.get(r.tableId) ?? t('Table');
}

function gameName(gameId: string | undefined): string {
  return (gameId && labelsQuery.data.value?.games.get(gameId)) || t('Game');
}

function branchName(branchId: string): string {
  return directory.byId(branchId)?.name ?? '';
}

const filteredReservations = computed(() =>
  allReservations.value.filter(
    (r) =>
      (selectedStatusFilter.value === 'ALL' ||
        r.status === selectedStatusFilter.value) &&
      (selectedBranchFilter.value === 'ALL' ||
        r.branchId === selectedBranchFilter.value),
  ),
);

const STATUS_LABELS: Record<string, string> = {
  CONFIRMED: 'Confirmed',
  CHECKED_IN: 'Checked In',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  NO_SHOW: 'No-Show',
};

function formatStatus(status: string): string {
  return t(STATUS_LABELS[status] ?? status);
}

const dateLocale = computed(() =>
  locale.value === 'th-TH' ? 'th-TH' : 'en-US',
);

function formatFriendlyDateTime(iso: string): string {
  const d = new Date(iso);
  return `${d.toLocaleDateString(dateLocale.value, {
    timeZone: BRANCH_TIME_ZONE,
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })} • ${branchTime(d)}`;
}

function formatTimeOnly(iso: string): string {
  return branchTime(new Date(iso));
}

function paymentLabel(r: Reservation): string {
  const payment = r.preOrder?.payment;
  if (!payment) return '';
  const card = payment.cardLast4
    ? ` (${payment.cardBrand ?? ''} •••• ${payment.cardLast4})`
    : '';
  const state: Record<string, string> = {
    AUTHORIZED: t('Authorized; charged at check-in'),
    CAPTURED: t('Charged'),
    VOIDED: t('Released; not charged'),
    PENDING: t('Pending'),
  };
  return `${state[payment.status] ?? payment.status}${card}`;
}

const isUpcoming = (r: Reservation) =>
  r.status === 'CONFIRMED' && Date.parse(r.startsAt) > Date.now();

async function refresh() {
  await queryClient.invalidateQueries({ queryKey: ['reservations'] });
}

function actionError(error: unknown, fallback: string): string {
  const status = httpStatus(error);
  if (status === 409)
    return t(
      'This change is no longer possible. The reservation may have started or the time is taken.',
    );
  if (status === 402 || status === 502)
    return t(
      'The card payment could not be updated. Please try again shortly.',
    );
  return fallback;
}

// Cancel: releases the table, games and food, and voids any pre-order card hold.
const cancellingReservation = ref<Reservation | null>(null);
const cancelReason = ref('');
const isCancelling = ref(false);
const cancelError = ref('');

function openCancelModal(r: Reservation) {
  cancellingReservation.value = r;
  cancelReason.value = '';
  cancelError.value = '';
}

async function confirmCancel() {
  const r = cancellingReservation.value;
  if (!r || isCancelling.value) return;
  isCancelling.value = true;
  cancelError.value = '';
  try {
    await cancelReservation(r.branchId, r.id, {
      reason: cancelReason.value.trim() || 'Cancelled by customer',
    });
    cancellingReservation.value = null;
    await refresh();
  } catch (error) {
    cancelError.value = actionError(
      error,
      t('The reservation could not be cancelled. Please try again.'),
    );
  } finally {
    isCancelling.value = false;
  }
}

// Reschedule: same table and games when they are free at the new time, otherwise
// another free table that seats the party.
const reschedulingReservation = ref<Reservation | null>(null);
const reschedule = reactive({ date: '', time: '', hours: 2, partySize: 2 });
const isRescheduling = ref(false);
const rescheduleError = ref('');
const todayDate = computed(() => branchDate(new Date()));

function openReschedule(r: Reservation) {
  const start = new Date(r.startsAt);
  reschedulingReservation.value = r;
  reschedule.date = branchDate(start);
  reschedule.time = branchTime(start);
  reschedule.hours = Math.max(
    1,
    Math.round((Date.parse(r.endsAt) - Date.parse(r.startsAt)) / 3_600_000),
  );
  reschedule.partySize = r.partySize;
  rescheduleError.value = '';
}

async function confirmReschedule() {
  const r = reschedulingReservation.value;
  if (!r || isRescheduling.value) return;
  const startsAt = branchInstant(reschedule.date, reschedule.time);
  if (!startsAt || Date.parse(startsAt) < Date.now() + 30 * 60_000) {
    rescheduleError.value = t('Choose a time at least 30 minutes from now.');
    return;
  }
  // The card hold ages from booking, when the pre-order was authorized, so the
  // server measures the window from then, not from now.
  if (r.preOrder && !canPreOrder(startsAt, new Date(r.createdAt))) {
    rescheduleError.value = t(
      'A reservation with pre-ordered food can only move to a time within 6 days of when it was booked.',
    );
    return;
  }
  const endsAt = plusHours(startsAt, reschedule.hours);
  isRescheduling.value = true;
  rescheduleError.value = '';
  try {
    const availability = await getBranchAvailability(r.branchId, {
      startsAt,
      endsAt,
      partySize: reschedule.partySize,
    });
    // The booking's own holds count against it until the move replaces them.
    const sameWindow =
      Date.parse(startsAt) < Date.parse(r.endsAt) &&
      Date.parse(endsAt) > Date.parse(r.startsAt);
    const fits = (tableId: string, capacity: number, available: boolean) =>
      capacity >= reschedule.partySize &&
      (available || (sameWindow && tableId === r.tableId));
    const table =
      availability.tables.find(
        (tbl) =>
          tbl.tableId === r.tableId &&
          fits(tbl.tableId, tbl.capacity, tbl.available),
      ) ??
      availability.tables.find((tbl) =>
        fits(tbl.tableId, tbl.capacity, tbl.available),
      );
    if (!availability.withinOpeningHours || !table) {
      rescheduleError.value = t(
        'No table is free for that time and party size.',
      );
      return;
    }
    const free = new Map(
      availability.games.map((game) => [game.gameId, game.availableQuantity]),
    );
    const short = r.games.find(
      (game) => !sameWindow && (free.get(game.gameId) ?? 0) < game.quantity,
    );
    if (short) {
      rescheduleError.value = t('{game} is not free at that time.', {
        game: gameName(short.gameId),
      });
      return;
    }
    await rescheduleReservation(r.branchId, r.id, {
      tableId: table.tableId,
      startsAt,
      endsAt,
      partySize: reschedule.partySize,
      games: r.games.map(({ gameId, quantity }) => ({ gameId, quantity })),
    });
    reschedulingReservation.value = null;
    await refresh();
  } catch (error) {
    rescheduleError.value = actionError(
      error,
      t('The reservation could not be moved. Please try again.'),
    );
  } finally {
    isRescheduling.value = false;
  }
}

// Read-only charges of a visit that has started, loaded when asked for.
const rentals = ref<Record<string, Rental | 'error' | 'loading'>>({});

async function toggleCharges(r: Reservation) {
  if (!r.rentalId) return;
  if (rentals.value[r.id] && rentals.value[r.id] !== 'error') {
    const { [r.id]: _, ...rest } = rentals.value;
    rentals.value = rest;
    return;
  }
  rentals.value = { ...rentals.value, [r.id]: 'loading' };
  try {
    const rental = await getRental(r.branchId, r.rentalId);
    rentals.value = { ...rentals.value, [r.id]: rental };
  } catch {
    rentals.value = { ...rentals.value, [r.id]: 'error' };
  }
}

function rentalOf(r: Reservation): Rental | undefined {
  const value = rentals.value[r.id];
  return typeof value === 'object' ? value : undefined;
}
</script>

<template>
  <div class="my-reservations-page">
    <div class="container">
      <header class="page-header">
        <div class="header-titles">
          <h1 class="page-title">{{ t('My Reservations') }}</h1>
          <p class="page-subtitle">
            {{
              t(
                'Manage your booked gaming tables and pre-ordered food & refreshments.',
              )
            }}
          </p>
        </div>
        <div class="header-actions">
          <RouterLink
            to="/reservations/new"
            class="btn btn-primary add-res-btn"
          >
            + {{ t('Reserve Table') }}
          </RouterLink>
        </div>
      </header>

      <section
        v-if="!auth.authenticated"
        class="auth-required-box"
        aria-live="polite"
      >
        <h2 class="auth-title">{{ t('Please Sign In') }}</h2>
        <p class="auth-text">
          {{ t('Sign in to your Parlor account to view your reservations.') }}
        </p>
        <RouterLink
          :to="{ name: 'AuthLogin', query: { redirect: '/reservations' } }"
          class="btn btn-primary"
        >
          {{ t('Sign In / Register') }} →
        </RouterLink>
      </section>

      <section v-else class="reservations-content">
        <div class="filters-card">
          <div class="filter-group">
            <span class="filter-label">{{ t('Status') }}:</span>
            <div class="status-pills">
              <button
                v-for="st in STATUSES"
                :key="st"
                type="button"
                :aria-pressed="selectedStatusFilter === st"
                class="pill-btn"
                :class="{ active: selectedStatusFilter === st }"
                @click="selectedStatusFilter = st"
              >
                {{ st === 'ALL' ? t('All Statuses') : formatStatus(st) }}
              </button>
            </div>
          </div>

          <div class="filter-group branch-filter-group">
            <label for="branch-filter" class="filter-label"
              >{{ t('Branch') }}:</label
            >
            <select
              id="branch-filter"
              v-model="selectedBranchFilter"
              class="branch-select-input"
            >
              <option value="ALL">{{ t('All Branches') }}</option>
              <option v-for="b in directory.branches" :key="b.id" :value="b.id">
                {{ b.name }}
              </option>
            </select>
          </div>
        </div>

        <div
          v-if="reservationsQuery.isLoading.value"
          class="empty-reservations-box"
          role="status"
        >
          <p class="empty-text">{{ t('Loading your reservations…') }}</p>
        </div>

        <div
          v-else-if="reservationsQuery.isError.value"
          class="empty-reservations-box"
          role="alert"
        >
          <h3 class="empty-title">
            {{ t('Your reservations could not be loaded.') }}
          </h3>
          <button
            type="button"
            class="btn btn-secondary"
            @click="reservationsQuery.refetch()"
          >
            {{ t('Try again') }}
          </button>
        </div>

        <div
          v-else-if="filteredReservations.length === 0"
          class="empty-reservations-box"
        >
          <h3 class="empty-title">{{ t('No Reservations Found') }}</h3>
          <p class="empty-text">
            {{ t('You have no reservations matching this filter.') }}
          </p>
          <RouterLink to="/reservations/new" class="btn btn-primary">
            + {{ t('Reserve a Table Now') }}
          </RouterLink>
        </div>

        <div v-else class="reservations-grid">
          <article
            v-for="res in filteredReservations"
            :key="res.id"
            class="reservation-card"
            :class="`status-${res.status.toLowerCase()}`"
          >
            <div class="card-header">
              <div class="branch-meta">
                <span class="branch-name">{{ branchName(res.branchId) }}</span>
                <span class="ref-badge"
                  >{{ t('Ref:') }} {{ res.id.slice(-8).toUpperCase() }}</span
                >
              </div>
              <span
                class="status-badge"
                :class="`badge-${res.status.toLowerCase()}`"
              >
                {{ formatStatus(res.status) }}
              </span>
            </div>

            <div class="card-body">
              <div class="schedule-block">
                <div class="date-main">
                  <span class="schedule-text">
                    {{ formatFriendlyDateTime(res.startsAt) }} –
                    {{ formatTimeOnly(res.endsAt) }}
                  </span>
                </div>
              </div>

              <div class="details-row">
                <div class="detail-item">
                  <span class="detail-lbl">{{ t('Table') }}:</span>
                  <span class="detail-val table-highlight">{{
                    tableLabel(res)
                  }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-lbl">{{ t('Party size') }}:</span>
                  <span class="detail-val"
                    >{{ res.partySize }} {{ t('players') }}</span
                  >
                </div>
                <div class="detail-item">
                  <span class="detail-lbl">{{ t('Table rate') }}:</span>
                  <span class="detail-val"
                    >{{ formatBaht(res.tableHourlyRate.amountMinor) }}
                    {{ t('/ hour') }}</span
                  >
                </div>
              </div>

              <div v-if="res.games.length" class="details-row">
                <div class="detail-item">
                  <span class="detail-lbl">{{ t('Games') }}:</span>
                  <span class="detail-val">{{
                    res.games
                      .map((g) => `${g.quantity}× ${gameName(g.gameId)}`)
                      .join(', ')
                  }}</span>
                </div>
              </div>

              <div v-if="res.preOrder" class="preorder-section">
                <div class="preorder-header">
                  <div class="preorder-title">
                    <strong>{{ t('Pre-ordered Food & Drinks') }}</strong>
                  </div>
                  <span class="discount-pill"
                    >-10% {{ t('Pre-order Discount') }}</span
                  >
                </div>

                <div class="preorder-items-list">
                  <div
                    v-for="line in res.preOrder.items"
                    :key="line.menuItemId"
                    class="preorder-item-row"
                  >
                    <span class="item-name-qty">
                      <span class="qty-badge">{{ line.quantity }}×</span>
                      {{ line.name }}
                    </span>
                    <span class="item-calc-price">{{
                      formatBaht(line.lineTotal.amountMinor)
                    }}</span>
                  </div>
                </div>

                <div class="preorder-footer-bar">
                  <div class="payment-confirmed-tag">
                    <span>{{ paymentLabel(res) }}</span>
                  </div>
                  <div class="preorder-sum">
                    <span class="original-sum">{{
                      formatBaht(res.preOrder.subtotal.amountMinor)
                    }}</span>
                    <span class="discounted-sum">{{
                      formatBaht(res.preOrder.total.amountMinor)
                    }}</span>
                  </div>
                </div>
              </div>

              <div v-if="res.notes" class="notes-box">
                <span class="notes-tag">{{ t('Notes') }}:</span>
                <p class="notes-text">{{ res.notes }}</p>
              </div>

              <div v-if="res.rentalId && rentals[res.id]" class="charges-box">
                <p v-if="rentals[res.id] === 'loading'" role="status">
                  {{ t('Loading charges…') }}
                </p>
                <p v-else-if="rentals[res.id] === 'error'" role="alert">
                  {{ t('Charges could not be loaded.') }}
                </p>
                <template v-else-if="rentalOf(res)">
                  <table class="charges-table">
                    <caption class="charges-caption">
                      {{
                        rentalOf(res)?.status === 'COMPLETED'
                          ? t('Visit charges')
                          : t('Charges so far, billed at check-out')
                      }}
                    </caption>
                    <thead>
                      <tr>
                        <th scope="col">{{ t('Item') }}</th>
                        <th scope="col">{{ t('Rate') }}</th>
                        <th scope="col">{{ t('Amount') }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr
                        v-for="(charge, index) in rentalOf(res)?.charges"
                        :key="index"
                      >
                        <td>
                          {{
                            charge.kind === 'TABLE'
                              ? t('Table')
                              : `${charge.quantity}× ${gameName(charge.gameId)}`
                          }}
                        </td>
                        <td>
                          {{ formatBaht(charge.unitHourlyRate.amountMinor) }}
                          {{ t('/ hour') }}
                        </td>
                        <td>
                          {{
                            charge.lineTotal
                              ? formatBaht(charge.lineTotal.amountMinor)
                              : '—'
                          }}
                        </td>
                      </tr>
                    </tbody>
                    <tfoot v-if="rentalOf(res)?.total">
                      <tr>
                        <th scope="row" colspan="2">
                          {{
                            t('Total for {hours} hours', {
                              hours: rentalOf(res)?.billableHours ?? 0,
                            })
                          }}
                        </th>
                        <td>
                          {{
                            formatBaht(rentalOf(res)?.total?.amountMinor ?? 0)
                          }}
                        </td>
                      </tr>
                    </tfoot>
                  </table>
                </template>
              </div>
            </div>

            <div class="card-footer">
              <div class="card-actions">
                <button
                  v-if="res.rentalId"
                  type="button"
                  class="btn btn-secondary btn-small"
                  :aria-expanded="!!rentals[res.id]"
                  @click="toggleCharges(res)"
                >
                  {{
                    rentals[res.id] && rentals[res.id] !== 'error'
                      ? t('Hide charges')
                      : t('View charges')
                  }}
                </button>
                <template v-if="isUpcoming(res)">
                  <button
                    type="button"
                    class="btn btn-secondary btn-small"
                    @click="openReschedule(res)"
                  >
                    {{ t('Reschedule') }}
                  </button>
                  <button
                    type="button"
                    class="btn-cancel"
                    @click="openCancelModal(res)"
                  >
                    {{ t('Cancel Reservation') }}
                  </button>
                </template>
              </div>
            </div>
          </article>
        </div>
      </section>

      <Transition name="fade">
        <div
          v-if="cancellingReservation"
          class="modal-overlay"
          @click.self="cancellingReservation = null"
        >
          <div
            class="modal-card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="cancel-heading"
          >
            <h3 id="cancel-heading" class="modal-heading">
              {{ t('Cancel Table Reservation?') }}
            </h3>
            <p class="modal-desc">
              <strong>{{ branchName(cancellingReservation.branchId) }}</strong>
              — {{ formatFriendlyDateTime(cancellingReservation.startsAt) }}
              <template v-if="cancellingReservation.preOrder">
                <br />
                {{
                  t(
                    'The {amount} hold on your card for pre-ordered food will be released.',
                    {
                      amount: formatBaht(
                        cancellingReservation.preOrder.total.amountMinor,
                      ),
                    },
                  )
                }}
              </template>
            </p>
            <div class="modal-form-group">
              <label for="cancel-reason" class="modal-label">{{
                t('Reason (optional)')
              }}</label>
              <input
                id="cancel-reason"
                v-model="cancelReason"
                type="text"
                maxlength="500"
                class="form-select"
              />
            </div>
            <p v-if="cancelError" class="modal-error" role="alert">
              {{ cancelError }}
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
                {{ isCancelling ? t('Cancelling…') : t('Cancel Reservation') }}
              </button>
            </div>
          </div>
        </div>
      </Transition>

      <Transition name="fade">
        <div
          v-if="reschedulingReservation"
          class="modal-overlay"
          @click.self="reschedulingReservation = null"
        >
          <form
            class="modal-card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="reschedule-heading"
            @submit.prevent="confirmReschedule"
          >
            <h3 id="reschedule-heading" class="modal-heading">
              {{ t('Reschedule Reservation') }}
            </h3>
            <p class="modal-desc">
              {{
                t(
                  'Your table and games move with you when they are free; otherwise another table that seats your party is chosen.',
                )
              }}
            </p>
            <div class="modal-grid">
              <div class="modal-form-group">
                <label for="rs-date" class="modal-label">{{ t('Date') }}</label>
                <input
                  id="rs-date"
                  v-model="reschedule.date"
                  type="date"
                  :min="todayDate"
                  required
                  class="form-select"
                />
              </div>
              <div class="modal-form-group">
                <label for="rs-time" class="modal-label">{{
                  t('Start Time')
                }}</label>
                <input
                  id="rs-time"
                  v-model="reschedule.time"
                  type="time"
                  required
                  class="form-select"
                />
              </div>
              <div class="modal-form-group">
                <label for="rs-hours" class="modal-label">{{
                  t('Duration')
                }}</label>
                <select
                  id="rs-hours"
                  v-model.number="reschedule.hours"
                  class="form-select"
                >
                  <option v-for="h in [1, 2, 3, 4, 6]" :key="h" :value="h">
                    {{ t(h === 1 ? '1 Hour' : '{count} Hours', { count: h }) }}
                  </option>
                </select>
              </div>
              <div class="modal-form-group">
                <label for="rs-party" class="modal-label">{{
                  t('Party Size')
                }}</label>
                <input
                  id="rs-party"
                  v-model.number="reschedule.partySize"
                  type="number"
                  min="1"
                  max="20"
                  required
                  class="form-select"
                />
              </div>
            </div>
            <p v-if="rescheduleError" class="modal-error" role="alert">
              {{ rescheduleError }}
            </p>
            <div class="modal-buttons">
              <button
                type="button"
                class="btn btn-secondary"
                @click="reschedulingReservation = null"
              >
                {{ t('Keep Current Time') }}
              </button>
              <button
                type="submit"
                class="btn btn-primary"
                :disabled="isRescheduling"
              >
                {{ isRescheduling ? t('Moving…') : t('Move Reservation') }}
              </button>
            </div>
          </form>
        </div>
      </Transition>
    </div>
  </div>
</template>

<style scoped>
.my-reservations-page {
  background: #fbf9f4;
  min-height: calc(100vh - 120px);
  padding: 36px 16px 80px;
  color: #172b21;
  font-family:
    'FC Mee Sakul', 'Plus Jakarta Sans', system-ui, Arial, sans-serif;
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
  border-bottom: 2px solid #c6ad7a;
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
  color: #8c7649;
  margin-bottom: 6px;
}

.gold-sparkle {
  color: #c6ad7a;
  font-size: 14px;
}

.page-title {
  margin: 0 0 6px;
  font-size: 30px;
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

.add-res-btn {
  white-space: nowrap;
}

/* Auth Required Box */
.auth-required-box {
  background: #ffffff;
  border: 1.5px solid #c6ad7a;
  border-radius: 12px;
  padding: 48px 24px;
  text-align: center;
  max-width: 520px;
  margin: 40px auto;
  box-shadow: 0 8px 24px rgba(20, 43, 32, 0.08);
}

.auth-icon-circle {
  width: 56px;
  height: 56px;
  background: #142b20;
  color: #ffd98a;
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
  color: #142b20;
}

.auth-text {
  margin: 0 0 24px;
  font-size: 14px;
  color: #60594a;
  line-height: 1.6;
}

/* Filter Card */
.filters-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border: 1px solid #ded5c3;
  border-radius: 10px;
  padding: 14px 18px;
  margin-bottom: 24px;
  box-shadow: 0 2px 8px rgba(32, 60, 48, 0.04);
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
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  color: #4b4435;
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

.branch-select-input {
  padding: 6px 12px;
  border: 1px solid #dcd1be;
  border-radius: 6px;
  background: #ffffff;
  font-size: 13px;
  color: #142b20;
  font-weight: 600;
}

/* Empty Box */
.empty-reservations-box {
  background: #ffffff;
  border: 1.5px dashed #c6ad7a;
  border-radius: 12px;
  padding: 52px 20px;
  text-align: center;
  margin-top: 10px;
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
  box-shadow: 0 4px 16px rgba(20, 43, 32, 0.05);
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

.reservation-card:hover {
  border-color: #c6ad7a;
  box-shadow:
    0 8px 24px rgba(20, 43, 32, 0.1),
    0 0 12px rgba(198, 173, 122, 0.12);
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

.card-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* Schedule and ISO Timestamps */
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
}

.schedule-icon {
  color: #c6ad7a;
}

.schedule-text {
  font-size: 15px;
  font-weight: 800;
  color: #142b20;
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
  font-size: 11px;
  font-weight: 700;
  color: #7a705e;
}

.iso-codes {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.iso-codes code {
  font-size: 11px;
  background: #f0e8db;
  color: #383226;
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid #ded5c3;
  font-family: monospace;
}

.iso-arrow {
  color: #c6ad7a;
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

/* Pre-order Breakdown */
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
  font-size: 13.5px;
  color: #142b20;
}

.discount-pill {
  font-size: 10.5px;
  font-weight: 800;
  background: #142b20;
  color: #ffd98a;
  border: 1px solid #c6ad7a;
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
  color: #2b3a31;
}

.item-name-qty {
  display: flex;
  align-items: center;
  gap: 8px;
}

.qty-badge {
  font-weight: 800;
  color: #142b20;
  background: #f0e7d5;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11.5px;
}

.item-calc-price {
  font-weight: 700;
  color: #142b20;
}

.preorder-footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px dashed #e0d5c1;
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
  color: #1e5214;
}

.preorder-sum {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.original-sum {
  font-size: 12px;
  color: #8c8371;
  text-decoration: line-through;
}

.discounted-sum {
  font-size: 15px;
  font-weight: 800;
  color: #142b20;
}

/* Notes & Cancelled Reason */
.notes-box {
  background: #fcf9f2;
  border-left: 3px solid #c6ad7a;
  padding: 8px 12px;
  border-radius: 0 6px 6px 0;
}

.notes-tag {
  font-size: 11.5px;
  font-weight: 700;
  color: #6a6252;
  display: block;
  margin-bottom: 2px;
}

.notes-text {
  margin: 0;
  font-size: 12.5px;
  color: #27382f;
  line-height: 1.4;
}

.cancelled-box {
  background: #fff5f5;
  border-left: 3px solid #b91c1c;
  padding: 8px 12px;
  border-radius: 0 6px 6px 0;
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 12.5px;
}

.cancel-lbl {
  font-weight: 700;
  color: #991b1b;
}

.cancel-text {
  color: #7f1d1d;
}

/* Card Footer */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #f0e8db;
  padding-top: 14px;
  gap: 12px;
  flex-wrap: wrap;
}

.inplay-prompt {
  font-size: 12px;
  color: #3b5a45;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 650px;
  line-height: 1.4;
}

.sparkle-gold {
  color: #c6ad7a;
}

.btn-cancel {
  background: transparent;
  border: 1px solid #e2b4b4;
  color: #b91c1c;
  font-size: 12.5px;
  font-weight: 700;
  padding: 6px 14px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-cancel:hover {
  background: #fee2e2;
  border-color: #b91c1c;
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
  background: #142b20;
  color: #ffd98a;
  border: 1px solid #c6ad7a;
  box-shadow: 0 2px 8px rgba(20, 43, 32, 0.2);
}

.btn-primary:hover {
  background: #1e3f2f;
  box-shadow:
    0 4px 14px rgba(20, 43, 32, 0.3),
    0 0 10px rgba(198, 173, 122, 0.4);
  transform: translateY(-1px);
}

.btn-secondary {
  background: #ffffff;
  border: 1px solid #ded5c3;
  color: #383226;
}

.btn-secondary:hover {
  background: #f7f3eb;
  border-color: #baa67c;
}

.btn-danger {
  background: #b91c1c;
  color: #ffffff;
  border: none;
}

.btn-danger:hover {
  background: #991b1b;
}

/* Modal Overlay */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(16, 32, 24, 0.65);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  padding: 16px;
}

.modal-card {
  background: #ffffff;
  border: 1.5px solid #c6ad7a;
  border-radius: 12px;
  padding: 26px 28px;
  max-width: 460px;
  width: 100%;
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.4);
}

.modal-heading {
  margin: 0 0 8px;
  font-size: 19px;
  font-weight: 800;
  color: #142b20;
}

.modal-desc {
  margin: 0 0 18px;
  font-size: 13.5px;
  color: #554e42;
  line-height: 1.5;
}

.modal-form-group {
  margin-bottom: 20px;
}

.modal-label {
  font-size: 12.5px;
  font-weight: 700;
  color: #142b20;
  display: block;
  margin-bottom: 6px;
}

.form-select {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid #ded5c3;
  border-radius: 6px;
  font-size: 13.5px;
  color: #142b20;
  background: #ffffff;
  box-sizing: border-box;
}

.modal-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* Active Ongoing Session Banner */
.active-session-banner {
  background: radial-gradient(
    ellipse at 50% 20%,
    #1f4232 0%,
    #142b20 70%,
    #0d1e16 100%
  );
  border: 2px solid #c6ad7a;
  border-radius: 14px;
  padding: 24px 28px;
  margin-bottom: 24px;
  color: #fffaf0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  box-shadow:
    0 8px 28px rgba(0, 0, 0, 0.35),
    0 0 20px rgba(198, 173, 122, 0.25);
  flex-wrap: wrap;
}

.session-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  color: #ffd98a;
  margin-bottom: 6px;
}

.live-pulse-dot {
  width: 8px;
  height: 8px;
  background: #22c55e;
  border-radius: 50%;
  box-shadow: 0 0 8px #22c55e;
  animation: pulseLive 1.8s infinite;
}

@keyframes pulseLive {
  0% {
    transform: scale(0.95);
    opacity: 0.8;
  }
  50% {
    transform: scale(1.3);
    opacity: 1;
  }
  100% {
    transform: scale(0.95);
    opacity: 0.8;
  }
}

.active-table-name {
  margin: 0 0 10px;
  font-size: 24px;
  font-weight: 800;
  color: #ffffff;
  letter-spacing: -0.01em;
}

.active-meta-chips {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.meta-chip {
  background: rgba(198, 173, 122, 0.2);
  border: 1px solid rgba(198, 173, 122, 0.4);
  color: #fff8e7;
  font-size: 11.5px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 12px;
}

.meta-chip.chip-orders {
  background: rgba(34, 197, 94, 0.2);
  border-color: #22c55e;
  color: #bbf7d0;
}

.active-session-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.btn-order-table {
  background: #c6ad7a;
  color: #14281f;
  font-size: 13.5px;
  font-weight: 800;
  padding: 11px 20px;
  border-radius: 8px;
  border: none;
  box-shadow: 0 4px 14px rgba(198, 173, 122, 0.4);
}

.btn-order-table:hover {
  background: #e0d0ac;
  transform: translateY(-2px);
}

.btn-view-bill {
  background: transparent;
  color: #fff8e7;
  border: 1.5px solid #c6ad7a;
  font-size: 13.5px;
  font-weight: 800;
  padding: 10px 18px;
  border-radius: 8px;
}

.btn-view-bill:hover {
  background: rgba(198, 173, 122, 0.2);
  transform: translateY(-2px);
}

/* In-Play Orders within Card */
.inplay-history-section {
  background: #fdfaf4;
  border: 1px solid #ded5c3;
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
  color: #142b20;
}

.inplay-orders-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 10px;
}

.inplay-mini-card {
  background: #ffffff;
  border: 1px solid #e2d7c5;
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
  color: #142b20;
}

.kitchen-pill {
  font-size: 10px;
  font-weight: 800;
  padding: 2px 6px;
  border-radius: 4px;
}

.kitchen-pill.status-received {
  background: #fef3c7;
  color: #92400e;
}

.kitchen-pill.status-preparing {
  background: #fed7aa;
  color: #c2410c;
}

.kitchen-pill.status-served {
  background: #dcfce7;
  color: #15803d;
}

.pay-pill {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
}

.pay-pill.tab {
  background: #f4ecdc;
  color: #8c7649;
}

.pay-pill.paid {
  background: #e2f0d9;
  color: #1e5214;
}

.mini-items {
  font-size: 12px;
  color: #383226;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mini-card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px dashed #efe8db;
  padding-top: 4px;
  font-size: 11px;
}

.ord-total {
  font-weight: 800;
  color: #142b20;
}

.ord-note {
  color: #8c7649;
  font-style: italic;
}

.inplay-live-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-live-order {
  background: #142b20;
  color: #ffd98a;
  border: 1px solid #c6ad7a;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 12.5px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-live-order:hover {
  background: #1e3f2f;
}

.btn-live-bill {
  background: #ffffff;
  border: 1px solid #c6ad7a;
  color: #142b20;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 12.5px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-live-bill:hover {
  background: #f7f3ea;
}

.btn-small {
  padding: 6px 12px;
  font-size: 12.5px;
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.modal-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 12px;
}

.modal-error {
  margin: 0 0 14px;
  font-size: 13px;
  color: #b91c1c;
}

.charges-box {
  border-top: 1px solid #ece4d4;
  padding-top: 10px;
  font-size: 13px;
  color: #383226;
}

.charges-table {
  width: 100%;
  border-collapse: collapse;
}

.charges-caption {
  text-align: left;
  font-weight: 700;
  padding-bottom: 6px;
  color: #142b20;
}

.charges-table th,
.charges-table td {
  text-align: left;
  padding: 5px 6px;
  border-bottom: 1px solid #f0e9dc;
}

.charges-table td:last-child,
.charges-table th:last-child {
  text-align: right;
}

@media (max-width: 480px) {
  .modal-grid {
    grid-template-columns: 1fr;
  }
}
</style>

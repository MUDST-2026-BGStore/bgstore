<script setup lang="ts">
// Customer table booking against the live API: branches, interval availability,
// games and the branch menu. An optional food pre-order is paid by card token. The
// server owns availability, prices, the pre-order discount and the booking itself;
// the receipt shows only what it returned.
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import {
  getBranchAvailability,
  getBranchMenu,
  listBranchGames,
  type BranchAvailability,
  type OrderPayment,
  type Reservation,
} from '@theparlor/api-client';
import { useAuthStore } from '../../../shared/stores/auth';
import { useBranchDirectoryStore } from '../../../shared/stores/branch-directory';
import { useStoreContextStore } from '../../../shared/stores/store-context';
import { useStoreText } from '../../../shared/lib/store-text';
import { collectPages } from '../../../shared/lib/pagination';
import FoodMenuPicker from '../components/FoodMenuPicker.vue';
import type { FoodOrderItem } from '../types';
import { toFoodMenuItem } from '../data/menu';
import {
  PRE_ORDER_WINDOW_DAYS,
  addBranchDays,
  branchDate,
  branchInstant,
  canPreOrder,
  formatBaht,
  formatBranchDay,
  formatBranchTimeRange,
  plusHours,
  preOrderPreview,
} from '../data/booking';
import {
  BookingError,
  availabilityProblems,
  bookableGames,
  cardRequest,
  createBookingSubmitter,
  preOrderShortfalls,
  reconcileGames,
  reconcilePreOrder,
  type BookingFailure,
  type BookingIntent,
  type CardField,
  type CardForm,
} from '../data/reservation-booking';

const route = useRoute();
const auth = useAuthStore();
const directory = useBranchDirectoryStore();
const storeContext = useStoreContextStore();
const queryClient = useQueryClient();
const { t, locale } = useStoreText();

type Step = 1 | 2 | 3 | 4 | 5;
const step = ref<Step>(1);
const STEPS: { step: Step; label: string }[] = [
  { step: 1, label: 'When & Where' },
  { step: 2, label: 'Table & Games' },
  { step: 3, label: 'Food & Drinks' },
  { step: 4, label: 'Review & Confirm' },
];

// The branch clock decides "today" and whether a start time has passed.
const clock = ref(Date.now());
const ticker = setInterval(() => (clock.value = Date.now()), 30_000);
onBeforeUnmount(() => clearInterval(ticker));
const today = computed(() => branchDate(new Date(clock.value)));

// ── Step 1: branch, date, time, duration and party ─────────────────────────────
const branchId = ref('');
const visitDate = ref(addBranchDays(today.value, 1));
const startTime = ref('14:00');
const durationHours = ref(2);
const partySize = ref(4);
const DURATIONS = [1, 2, 3, 4, 6];

void directory.load();
const branches = computed(() => directory.branches);
const branch = computed(() => directory.byId(branchId.value));

// Default to the requested branch, then the browsing branch, then the first. The
// booking branch is local to this page; it never changes the site-wide choice.
watch(
  branches,
  (list) => {
    if (directory.byId(branchId.value)) return;
    const requested =
      typeof route.query.branch === 'string' ? route.query.branch : '';
    branchId.value =
      directory.byId(requested)?.id ??
      storeContext.branch?.id ??
      list[0]?.id ??
      '';
  },
  { immediate: true },
);

const startsAt = computed(() =>
  branchInstant(visitDate.value, startTime.value),
);
const endsAt = computed(() => plusHours(startsAt.value, durationHours.value));
const startsInPast = computed(
  () => !!startsAt.value && Date.parse(startsAt.value) <= clock.value,
);
const partySizeValid = computed(
  () =>
    Number.isInteger(partySize.value) &&
    partySize.value >= 1 &&
    partySize.value <= 20,
);
const whenReady = computed(
  () =>
    auth.authenticated &&
    !!branch.value &&
    !!startsAt.value &&
    !startsInPast.value &&
    partySizeValid.value,
);

function visitLabel(from: string, until: string): string {
  const day = formatBranchDay(from, locale.value);
  return day ? `${day} · ${formatBranchTimeRange(from, until)}` : '';
}
const visitSummary = computed(() => visitLabel(startsAt.value, endsAt.value));

// ── Step 2: tables and games for the interval ─────────────────────────────────
const availabilityKey = computed(
  () =>
    [
      'reservations',
      'availability',
      branchId.value,
      startsAt.value,
      endsAt.value,
      partySize.value,
    ] as const,
);

function availabilityQueryOptions(force = false) {
  return {
    queryKey: availabilityKey.value,
    meta: { public: true },
    staleTime: force ? 0 : 30_000,
    queryFn: ({
      queryKey: [, , id, from, until, size],
      signal,
    }: {
      queryKey: typeof availabilityKey.value;
      signal: AbortSignal;
    }) =>
      getBranchAvailability(
        id,
        { startsAt: from, endsAt: until, partySize: size },
        undefined,
        signal,
      ),
  };
}

const availabilityQuery = useQuery(
  computed(() => ({
    ...availabilityQueryOptions(),
    enabled: step.value >= 2 && step.value <= 4 && whenReady.value,
  })),
);
const availability = computed<BranchAvailability | undefined>(
  () => availabilityQuery.data.value,
);
const tables = computed(() =>
  (availability.value?.tables ?? []).filter(
    (table) => table.available && table.capacity >= partySize.value,
  ),
);

const libraryQuery = useQuery(
  computed(() => ({
    queryKey: ['reservations', 'branch-games', branchId.value] as const,
    meta: { public: true },
    staleTime: 300_000,
    enabled: step.value >= 2 && step.value <= 4 && !!branchId.value,
    queryFn: ({ signal }: { signal: AbortSignal }) =>
      collectPages((cursor) =>
        listBranchGames(
          branchId.value,
          { limit: 100, cursor },
          undefined,
          signal,
        ),
      ),
  })),
);
const offeredGames = computed(() =>
  bookableGames(availability.value, libraryQuery.data.value ?? []),
);

const tableId = ref('');
const selectedTable = computed(() =>
  tables.value.find((table) => table.tableId === tableId.value),
);
const tableNotice = ref('');
watch(tables, (list) => {
  if (!availability.value || !tableId.value) return;
  if (!list.some((table) => table.tableId === tableId.value)) {
    tableId.value = '';
    tableNotice.value = t(
      'Your table is not free for this time. Choose another table.',
    );
  }
});

const gameSelections = ref<Record<string, number>>({});
const gamesNotice = ref('');
let requestedGameApplied = false;
watch(offeredGames, (offered) => {
  // Only a loaded snapshot can say a game is gone.
  if (!availability.value || !libraryQuery.data.value) return;
  const requested = route.query.game;
  if (!requestedGameApplied && typeof requested === 'string') {
    requestedGameApplied = true;
    if (offered.some((game) => game.gameId === requested))
      gameSelections.value = { ...gameSelections.value, [requested]: 1 };
  }
  const { selections, changed } = reconcileGames(gameSelections.value, offered);
  if (changed.length) {
    gameSelections.value = selections;
    gamesNotice.value = t(
      'Some games you chose are no longer free for this time. Your game list was updated.',
    );
  }
});

function gameQuantity(gameId: string): number {
  return gameSelections.value[gameId] ?? 0;
}
function setGameQuantity(gameId: string, quantity: number) {
  const game = offeredGames.value.find((g) => g.gameId === gameId);
  const next = Math.max(0, Math.min(quantity, game?.available ?? 0));
  const selections = { ...gameSelections.value };
  if (next > 0) selections[gameId] = next;
  else delete selections[gameId];
  gameSelections.value = selections;
  gamesNotice.value = '';
}
const chosenGames = computed(() =>
  offeredGames.value.filter((game) => gameQuantity(game.gameId) > 0),
);

const notes = ref('');

// ── Step 3: food pre-order from the branch menu ───────────────────────────────
const preOrderAllowed = computed(
  () => !!startsAt.value && canPreOrder(startsAt.value, new Date(clock.value)),
);
const menuQueryKey = computed(
  () => ['reservations', 'menu', branchId.value] as const,
);
function menuQueryOptions(force = false) {
  return {
    queryKey: menuQueryKey.value,
    meta: { public: true },
    staleTime: force ? 0 : 30_000,
    queryFn: ({
      queryKey: [, , id],
      signal,
    }: {
      queryKey: typeof menuQueryKey.value;
      signal: AbortSignal;
    }) => getBranchMenu(id, undefined, signal),
  };
}
const menuQuery = useQuery(
  computed(() => ({
    ...menuQueryOptions(),
    enabled:
      step.value >= 3 &&
      step.value <= 4 &&
      !!branchId.value &&
      preOrderAllowed.value,
  })),
);
const menuItems = computed(() =>
  (menuQuery.data.value?.items ?? []).map(toFoodMenuItem),
);

const foodLines = ref<FoodOrderItem[]>([]);
const foodNotice = ref('');

/** Applies a menu to the selection and tells the customer what it changed. */
function applyMenu(menu: ReturnType<typeof toFoodMenuItem>[]) {
  const { lines, removed, reduced } = reconcilePreOrder(foodLines.value, menu);
  foodLines.value = lines;
  const names = [...removed, ...reduced].map((line) => line.item.name);
  if (names.length)
    foodNotice.value = t(
      'The branch menu changed. Updated: {items}. Check your food order.',
      { items: names.join(', ') },
    );
}
watch(menuItems, (menu) => {
  if (menuQuery.data.value) applyMenu(menu);
});

/** The food that will be sent: nothing outside the pre-order window. */
const preOrderLines = computed(() =>
  preOrderAllowed.value ? foodLines.value : [],
);
const foodEstimate = computed(() =>
  preOrderPreview(
    preOrderLines.value.reduce(
      (sum, line) => sum + Math.round(line.item.price * 100) * line.quantity,
      0,
    ),
  ),
);
const needsCard = computed(() => preOrderLines.value.length > 0);

// ── Step 4: card and confirmation ─────────────────────────────────────────────
// Card details stay in this form only until tokenization; they are never stored,
// logged or sent anywhere except the payment token operation.
const card = reactive<CardForm>({
  number: '',
  expiry: '',
  cvc: '',
  holder: '',
});
const cardErrors = ref<CardField[]>([]);
function formatCardNumber() {
  card.number = card.number
    .replace(/\D/g, '')
    .slice(0, 19)
    .replace(/(\d{4})(?=\d)/g, '$1 ');
}
function formatExpiry() {
  const digits = card.expiry.replace(/\D/g, '').slice(0, 4);
  card.expiry =
    digits.length > 2 ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits;
}
function clearCard() {
  Object.assign(card, { number: '', expiry: '', cvc: '', holder: '' });
  cardErrors.value = [];
}
onBeforeUnmount(clearCard);

const submitter = createBookingSubmitter();
const submitting = ref(false);
type PageFailure = BookingFailure | 'revalidation-failed' | 'changed';
const failure = ref<PageFailure | null>(null);
const changeMessages = ref<string[]>([]);

const failureText = computed(() => {
  switch (failure.value) {
    case 'declined':
      return t(
        'The card was declined. Nothing was booked or charged. Check the card details or use another card.',
      );
    case 'unavailable':
      return t(
        'Part of this booking was just taken: the table, a game or a menu item. Nothing was booked or charged. Review your choices and confirm again.',
      );
    case 'gateway':
      return t(
        'Card payment is unavailable right now. Nothing was booked or charged. Try again shortly, or book without food.',
      );
    case 'invalid':
      return t(
        'The booking or card details were not accepted. Check them and try again.',
      );
    case 'signed-out':
      return t('Your session has ended. Sign in again to book.');
    case 'unknown-outcome':
      return t(
        'We could not confirm whether your booking went through. Check your connection and select Confirm again: retrying from this page will not book twice. You can also check My Reservations.',
      );
    case 'possibly-booked':
      return t(
        'An earlier attempt may already have booked this visit. Check My Reservations before booking again.',
      );
    case 'card-required':
      return t('Enter your card details again to retry.');
    case 'revalidation-failed':
      return t(
        'Availability could not be checked. Check your connection and try again.',
      );
    case 'changed':
      return t(
        'Availability changed since you chose. Review the changes below, then confirm again.',
      );
    case 'failed':
      return t(
        'The booking could not be completed and nothing was confirmed. Try again.',
      );
    default:
      return '';
  }
});

function currentIntent(): BookingIntent | null {
  if (!branch.value || !selectedTable.value) return null;
  return {
    branchId: branch.value.id,
    tableId: selectedTable.value.tableId,
    startsAt: startsAt.value,
    endsAt: endsAt.value,
    partySize: partySize.value,
    games: Object.entries(gameSelections.value).map(([gameId, quantity]) => ({
      gameId,
      quantity,
    })),
    notes: notes.value,
    preOrder: preOrderLines.value.map((line) => ({
      menuItemId: line.item.id,
      quantity: line.quantity,
    })),
  };
}

const receipt = ref<Reservation | null>(null);
const receiptNames = ref<{
  branch: string;
  table: string;
  games: Record<string, string>;
}>({ branch: '', table: '', games: {} });

/**
 * Rechecks the booking against fresh availability and menu stock before sending it.
 * Returns false, with the customer's input intact, when something changed.
 */
async function revalidate(intent: BookingIntent): Promise<boolean> {
  const messages: string[] = [];
  const latest = await queryClient.fetchQuery(availabilityQueryOptions(true));
  for (const problem of availabilityProblems(latest, intent)) {
    if (problem.kind === 'closed')
      messages.push(t('The branch is not open for this whole visit.'));
    else if (problem.kind === 'table')
      messages.push(t('Your table is no longer free for this time.'));
    else {
      const name =
        offeredGames.value.find((g) => g.gameId === problem.gameId)?.name ??
        receiptNames.value.games[problem.gameId] ??
        t('A game');
      messages.push(
        problem.available
          ? t('{game}: only {count} free for this time.', {
              game: name,
              count: problem.available,
            })
          : t('{game} is no longer free for this time.', { game: name }),
      );
    }
  }
  if (intent.preOrder.length) {
    // Judge the request that would be sent, not the live selection: the menu
    // watcher may already have reconciled the selection with this fresh menu.
    const names = new Map(foodLines.value.map((l) => [l.item.id, l.item.name]));
    const menu = (
      await queryClient.fetchQuery(menuQueryOptions(true))
    ).items.map(toFoodMenuItem);
    const short = preOrderShortfalls(menu, intent);
    applyMenu(menu);
    if (short.length)
      messages.push(
        t('The branch menu changed. Updated: {items}. Check your food order.', {
          items: short.map((id) => names.get(id) ?? id).join(', '),
        }),
      );
  }
  changeMessages.value = messages;
  return messages.length === 0;
}

async function confirmBooking() {
  if (submitting.value) return;
  failure.value = null;
  changeMessages.value = [];
  const intent = currentIntent();
  if (!intent) {
    step.value = 2;
    return;
  }

  let cardDetails;
  if (intent.preOrder.length) {
    const parsed = cardRequest(card, new Date(clock.value));
    if ('invalid' in parsed) {
      cardErrors.value = parsed.invalid;
      return;
    }
    cardErrors.value = [];
    cardDetails = parsed.request;
  }

  submitting.value = true;
  try {
    // An attempt whose response was lost may already hold this table, so it is
    // retried as it was; the server replays it or refuses a conflicting change.
    if (!submitter.outcomeUnknown) {
      let current: boolean;
      try {
        current = await revalidate(intent);
      } catch {
        failure.value = 'revalidation-failed';
        return;
      }
      if (!current) {
        failure.value = 'changed';
        return;
      }
    }
    // Names for the receipt come from the snapshot the customer booked against.
    const names = {
      branch: branch.value?.name ?? '',
      table: selectedTable.value?.label ?? '',
      games: Object.fromEntries(
        offeredGames.value.map((game) => [game.gameId, game.name]),
      ),
    };
    const reservation = await submitter.submit(intent, cardDetails);
    receiptNames.value = names;
    receipt.value = reservation;
    clearCard();
    step.value = 5;
    void queryClient.invalidateQueries({ queryKey: ['reservations'] });
    void queryClient.invalidateQueries({
      queryKey: ['api', 'v1', 'me', 'reservations'],
    });
  } catch (error) {
    failure.value = error instanceof BookingError ? error.failure : 'failed';
    if (failure.value === 'unavailable') {
      void queryClient.invalidateQueries({ queryKey: availabilityKey.value });
      void queryClient.invalidateQueries({ queryKey: menuQueryKey.value });
    }
  } finally {
    submitting.value = false;
  }
}

function startNewAttempt() {
  submitter.reset();
  failure.value = null;
}

function bookAnother() {
  submitter.reset();
  receipt.value = null;
  failure.value = null;
  changeMessages.value = [];
  tableId.value = '';
  gameSelections.value = {};
  foodLines.value = [];
  notes.value = '';
  foodNotice.value = '';
  gamesNotice.value = '';
  tableNotice.value = '';
  step.value = 1;
}

function goTo(next: Step) {
  failure.value = null;
  changeMessages.value = [];
  step.value = next;
}

// ── Receipt: the server's booking ─────────────────────────────────────────────
function paymentText(payment: OrderPayment): string {
  const cardText =
    payment.cardBrand && payment.cardLast4
      ? `${payment.cardBrand} •••• ${payment.cardLast4}`
      : t('Card');
  switch (payment.status) {
    case 'AUTHORIZED':
      return t('{card}: authorized, charged when you check in', {
        card: cardText,
      });
    case 'CAPTURED':
      return t('{card}: charged', { card: cardText });
    case 'VOIDED':
      return t('{card}: released, not charged', { card: cardText });
    default:
      return t('{card}: pending', { card: cardText });
  }
}
const receiptVisit = computed(() =>
  receipt.value ? visitLabel(receipt.value.startsAt, receipt.value.endsAt) : '',
);
</script>

<template>
  <div class="reservation-page">
    <div class="container">
      <nav
        v-if="step < 5"
        class="stepper-nav"
        :aria-label="t('Reservation progress')"
      >
        <template v-for="(item, index) in STEPS" :key="item.step">
          <div v-if="index > 0" class="step-divider" />
          <div
            class="step-indicator"
            :class="{ active: step === item.step, completed: step > item.step }"
            :aria-current="step === item.step ? 'step' : undefined"
          >
            <span class="step-num">{{ item.step }}</span>
            <span class="step-text">{{ t(item.label) }}</span>
          </div>
        </template>
      </nav>

      <Transition name="glam-step" mode="out-in">
        <!-- STEP 1: branch and time -->
        <section
          v-if="step === 1"
          key="step1"
          class="wizard-step"
          aria-labelledby="step1-heading"
        >
          <div class="step-header">
            <h2 id="step1-heading" class="step-title">
              {{ t('Choose Your Branch & Time') }}
            </h2>
            <p class="step-subtitle">
              {{ t('Times are in Bangkok time at the branch.') }}
            </p>
          </div>

          <div v-if="auth.authenticated" class="member-status-bar">
            <div class="member-meta">
              <span class="member-text">{{ t('Booking as') }}</span>
              <strong class="member-name">{{
                auth.displayName || auth.username
              }}</strong>
            </div>
          </div>
          <div v-else class="auth-gate-callout" role="alert">
            <div class="auth-gate-info">
              <div>
                <strong class="auth-gate-heading">{{
                  t('Sign in Required')
                }}</strong>
                <p class="auth-gate-sub">
                  {{ t('Sign in to reserve a table.') }}
                </p>
              </div>
            </div>
            <RouterLink
              :to="{
                name: 'AuthLogin',
                query: { redirect: '/reservations/new' },
              }"
              class="btn btn-primary auth-signin-btn"
            >
              {{ t('Sign In / Register') }}
            </RouterLink>
          </div>

          <p
            v-if="directory.status === 'loading' && !branches.length"
            class="state-message"
            role="status"
          >
            {{ t('Loading branches…') }}
          </p>
          <div
            v-else-if="directory.status === 'error' && !branches.length"
            class="validation-alert"
            role="alert"
          >
            <span>{{ t('Branches could not be loaded.') }}</span>
            <button
              type="button"
              class="btn btn-secondary"
              @click="directory.load(true)"
            >
              {{ t('Try again') }}
            </button>
          </div>
          <p
            v-else-if="directory.status === 'ready' && !branches.length"
            class="state-message"
            role="status"
          >
            {{ t('No branches are taking reservations right now.') }}
          </p>

          <div v-else class="form-grid">
            <div class="form-field full-width">
              <label for="branch-select" class="form-label">{{
                t('Branch Location')
              }}</label>
              <select id="branch-select" v-model="branchId" class="form-select">
                <option v-for="b in branches" :key="b.id" :value="b.id">
                  {{ b.name }} — {{ b.address }}
                </option>
              </select>
            </div>

            <div class="form-field">
              <label for="res-date" class="form-label">{{
                t('Reservation Date')
              }}</label>
              <input
                id="res-date"
                v-model="visitDate"
                :min="today"
                type="date"
                class="form-input"
                required
              />
            </div>

            <div class="form-field">
              <label for="res-time" class="form-label">{{
                t('Start Time')
              }}</label>
              <input
                id="res-time"
                v-model="startTime"
                type="time"
                step="900"
                class="form-input"
                required
              />
            </div>

            <div class="form-field">
              <label for="res-duration" class="form-label">{{
                t('Duration')
              }}</label>
              <select
                id="res-duration"
                v-model.number="durationHours"
                class="form-select"
              >
                <option v-for="hours in DURATIONS" :key="hours" :value="hours">
                  {{
                    t(hours === 1 ? '1 Hour' : '{count} Hours', {
                      count: hours,
                    })
                  }}
                </option>
              </select>
            </div>

            <div class="form-field">
              <label for="res-party" class="form-label">{{
                t('Party Size')
              }}</label>
              <input
                id="res-party"
                v-model.number="partySize"
                type="number"
                min="1"
                max="20"
                class="form-input"
                required
              />
            </div>
          </div>

          <div v-if="startsInPast" class="validation-alert" role="alert">
            <span>{{ t('Choose a start time that has not passed.') }}</span>
          </div>
          <div
            v-else-if="!partySizeValid"
            class="validation-alert"
            role="alert"
          >
            <span>{{ t('Party size must be between 1 and 20.') }}</span>
          </div>

          <p v-if="whenReady" class="visit-line">
            <strong>{{ branch?.name }}</strong> · {{ visitSummary }} ·
            {{
              t(partySize === 1 ? '{count} player' : '{count} players', {
                count: partySize,
              })
            }}
          </p>

          <div class="wizard-actions">
            <RouterLink :to="{ name: 'HomeIndex' }" class="btn btn-secondary">{{
              t('Cancel')
            }}</RouterLink>
            <button
              type="button"
              class="btn btn-primary"
              :disabled="!whenReady"
              @click="goTo(2)"
            >
              {{ t('Check Available Tables →') }}
            </button>
          </div>
        </section>

        <!-- STEP 2: table and games -->
        <section
          v-else-if="step === 2"
          key="step2"
          class="wizard-step"
          aria-labelledby="step2-heading"
        >
          <div class="step-header">
            <h2 id="step2-heading" class="step-title">
              {{ t('Choose a Table & Games') }}
            </h2>
            <p class="step-subtitle">
              <strong>{{ branch?.name }}</strong> · {{ visitSummary }} ·
              {{
                t(partySize === 1 ? '{count} player' : '{count} players', {
                  count: partySize,
                })
              }}
            </p>
          </div>

          <p
            v-if="availabilityQuery.isPending.value"
            class="state-message"
            role="status"
          >
            {{ t('Checking availability…') }}
          </p>
          <div
            v-else-if="availabilityQuery.isError.value"
            class="validation-alert"
            role="alert"
          >
            <span>{{ t('Availability could not be loaded.') }}</span>
            <button
              type="button"
              class="btn btn-secondary"
              @click="availabilityQuery.refetch()"
            >
              {{ t('Try again') }}
            </button>
          </div>
          <div v-else-if="!tables.length" class="empty-tables-box">
            <p class="empty-title">
              {{
                availability?.withinOpeningHours === false
                  ? t('The branch is not open for this whole visit')
                  : t('No tables available for this time slot')
              }}
            </p>
            <p class="empty-sub">
              {{ t('Try another time, a shorter visit or another branch.') }}
            </p>
            <button type="button" class="btn btn-secondary" @click="goTo(1)">
              {{ t('← Change Time or Branch') }}
            </button>
          </div>

          <template v-else>
            <p v-if="tableNotice" class="notice" role="status">
              {{ tableNotice }}
            </p>
            <fieldset class="plain-fieldset">
              <legend class="section-title">{{ t('Table') }}</legend>
              <div class="tables-grid">
                <label
                  v-for="table in tables"
                  :key="table.tableId"
                  class="table-card"
                  :class="{ 'is-selected': tableId === table.tableId }"
                >
                  <div class="table-top">
                    <span class="table-label">{{ table.label }}</span>
                    <span class="capacity-pill">{{
                      t('Up to {count} seats', { count: table.capacity })
                    }}</span>
                  </div>
                  <div class="table-rate">
                    <span class="rate-num">{{
                      formatBaht(table.hourlyRate.amountMinor)
                    }}</span>
                    <span class="rate-unit">{{ t('/ hour') }}</span>
                  </div>
                  <div class="select-radio">
                    <input
                      v-model="tableId"
                      type="radio"
                      name="table"
                      :value="table.tableId"
                      @change="tableNotice = ''"
                    />
                    <span>{{
                      tableId === table.tableId
                        ? t('Selected')
                        : t('Select Table')
                    }}</span>
                  </div>
                </label>
              </div>
            </fieldset>

            <section class="games-section" aria-labelledby="games-heading">
              <h3 id="games-heading" class="section-title">
                {{ t('Games (optional)') }}
              </h3>
              <p class="field-hint">
                {{
                  t(
                    'Copies are held for your whole visit. Only games free for this time are shown.',
                  )
                }}
              </p>
              <p v-if="gamesNotice" class="notice" role="status">
                {{ gamesNotice }}
              </p>
              <p
                v-if="libraryQuery.isPending.value"
                class="state-message"
                role="status"
              >
                {{ t('Loading games…') }}
              </p>
              <div
                v-else-if="libraryQuery.isError.value"
                class="validation-alert"
                role="alert"
              >
                <span>{{ t('Games could not be loaded.') }}</span>
                <button
                  type="button"
                  class="btn btn-secondary"
                  @click="libraryQuery.refetch()"
                >
                  {{ t('Try again') }}
                </button>
              </div>
              <p v-else-if="!offeredGames.length" class="state-message">
                {{ t('No games are free at this branch for this time.') }}
              </p>
              <ul v-else class="games-list">
                <li
                  v-for="game in offeredGames"
                  :key="game.gameId"
                  class="game-row"
                >
                  <div class="game-info">
                    <strong>{{ game.name }}</strong>
                    <span class="field-hint">
                      {{
                        t('{min}–{max} players · {count} free', {
                          min: game.minPlayers,
                          max: game.maxPlayers,
                          count: game.available,
                        })
                      }}
                      <template v-if="game.hourlyRateMinor !== undefined">
                        · {{ formatBaht(game.hourlyRateMinor) }}
                        {{ t('/ hour') }}
                      </template>
                    </span>
                  </div>
                  <div class="qty-stepper">
                    <button
                      type="button"
                      :aria-label="
                        t('Fewer copies of {game}', { game: game.name })
                      "
                      :disabled="gameQuantity(game.gameId) === 0"
                      @click="
                        setGameQuantity(
                          game.gameId,
                          gameQuantity(game.gameId) - 1,
                        )
                      "
                    >
                      −
                    </button>
                    <span aria-live="polite">{{
                      gameQuantity(game.gameId)
                    }}</span>
                    <button
                      type="button"
                      :aria-label="
                        t('More copies of {game}', { game: game.name })
                      "
                      :disabled="gameQuantity(game.gameId) >= game.available"
                      @click="
                        setGameQuantity(
                          game.gameId,
                          gameQuantity(game.gameId) + 1,
                        )
                      "
                    >
                      +
                    </button>
                  </div>
                </li>
              </ul>
            </section>

            <div class="form-field full-width">
              <label for="res-notes" class="form-label">{{
                t('Notes for the branch (optional)')
              }}</label>
              <textarea
                id="res-notes"
                v-model="notes"
                rows="2"
                maxlength="1000"
                class="form-input"
              />
            </div>
          </template>

          <div class="wizard-actions">
            <button type="button" class="btn btn-secondary" @click="goTo(1)">
              {{ t('← Back') }}
            </button>
            <button
              type="button"
              class="btn btn-primary"
              :disabled="!selectedTable"
              @click="goTo(3)"
            >
              {{ t('Continue →') }}
            </button>
          </div>
        </section>

        <!-- STEP 3: food pre-order -->
        <section
          v-else-if="step === 3"
          key="step3"
          class="wizard-step"
          aria-labelledby="step3-heading"
        >
          <div class="step-header">
            <h2 id="step3-heading" class="step-title">
              {{ t('Pre-Order Food & Drinks (optional)') }}
            </h2>
            <p class="step-subtitle">
              {{
                t(
                  'Pre-orders get 10% off. Your card is authorized when you book and charged when you check in.',
                )
              }}
            </p>
          </div>

          <p v-if="!preOrderAllowed" class="notice" role="status">
            {{
              t(
                'Food can be pre-ordered only for visits starting within {days} days.',
                { days: PRE_ORDER_WINDOW_DAYS },
              )
            }}
          </p>
          <template v-else>
            <p
              v-if="menuQuery.isPending.value"
              class="state-message"
              role="status"
            >
              {{ t('Loading the menu…') }}
            </p>
            <div
              v-else-if="menuQuery.isError.value"
              class="validation-alert"
              role="alert"
            >
              <span>{{ t('The menu could not be loaded.') }}</span>
              <button
                type="button"
                class="btn btn-secondary"
                @click="menuQuery.refetch()"
              >
                {{ t('Try again') }}
              </button>
            </div>
            <p v-else-if="!menuItems.length" class="state-message">
              {{ t('This branch has no menu items to pre-order.') }}
            </p>
            <template v-else>
              <p v-if="foodNotice" class="notice" role="status">
                {{ foodNotice }}
              </p>
              <FoodMenuPicker
                v-model="foodLines"
                :items="menuItems"
                @update:model-value="foodNotice = ''"
              />
              <p class="field-hint estimate-note">
                {{
                  t(
                    'Prices and totals are estimates. The branch confirms the final total when you book.',
                  )
                }}
              </p>
            </template>
          </template>

          <div class="wizard-actions">
            <button type="button" class="btn btn-secondary" @click="goTo(2)">
              {{ t('← Back to Table') }}
            </button>
            <button type="button" class="btn btn-primary" @click="goTo(4)">
              {{
                preOrderLines.length
                  ? t('Review & Pay →')
                  : t('Continue without food →')
              }}
            </button>
          </div>
        </section>

        <!-- STEP 4: review, card and confirmation -->
        <section
          v-else-if="step === 4"
          key="step4"
          class="wizard-step"
          aria-labelledby="step4-heading"
        >
          <div class="step-header">
            <h2 id="step4-heading" class="step-title">
              {{ t('Review & Confirm Reservation') }}
            </h2>
          </div>

          <div class="review-grid">
            <div class="review-card">
              <h3 class="card-heading">{{ t('Reservation Summary') }}</h3>
              <div class="review-row">
                <span class="label">{{ t('Branch') }}</span>
                <span class="val">{{ branch?.name }}</span>
              </div>
              <div class="review-row">
                <span class="label">{{ t('When') }}</span>
                <span class="val">{{ visitSummary }}</span>
              </div>
              <div class="review-row">
                <span class="label">{{ t('Table') }}</span>
                <span class="val"
                  >{{ selectedTable?.label }} ·
                  {{
                    selectedTable
                      ? formatBaht(selectedTable.hourlyRate.amountMinor)
                      : ''
                  }}
                  {{ t('/ hour') }}</span
                >
              </div>
              <div class="review-row">
                <span class="label">{{ t('Party Size') }}</span>
                <span class="val">{{
                  t(partySize === 1 ? '{count} player' : '{count} players', {
                    count: partySize,
                  })
                }}</span>
              </div>
              <div class="review-row">
                <span class="label">{{ t('Games') }}</span>
                <span class="val">{{
                  chosenGames.length
                    ? chosenGames
                        .map(
                          (game) =>
                            `${game.name} × ${gameQuantity(game.gameId)}`,
                        )
                        .join(', ')
                    : t('None')
                }}</span>
              </div>
              <div v-if="notes.trim()" class="review-row">
                <span class="label">{{ t('Notes') }}</span>
                <span class="val">{{ notes.trim() }}</span>
              </div>
              <p class="field-hint">
                {{
                  t(
                    'Table and game time is charged at the branch at the hourly rates shown.',
                  )
                }}
              </p>
            </div>

            <div class="pricing-card">
              <h3 class="card-heading">
                {{ needsCard ? t('Food Pre-Order') : t('Payment') }}
              </h3>
              <template v-if="needsCard">
                <ul class="food-list">
                  <li
                    v-for="line in preOrderLines"
                    :key="line.item.id"
                    class="food-item-line"
                  >
                    <span>{{ line.quantity }}× {{ line.item.name }}</span>
                    <span>{{
                      formatBaht(
                        Math.round(line.item.price * 100) * line.quantity,
                      )
                    }}</span>
                  </li>
                </ul>
                <div class="cost-row">
                  <span>{{ t('Subtotal') }}</span>
                  <span>{{ formatBaht(foodEstimate.subtotalMinor) }}</span>
                </div>
                <div class="cost-row discount-row">
                  <span>{{ t('Pre-Order Discount (-10%)') }}</span>
                  <span>−{{ formatBaht(foodEstimate.discountMinor) }}</span>
                </div>
                <div class="grand-total-row">
                  <div>
                    <span>{{ t('Estimated total') }}</span>
                    <small class="due-sub-text">{{
                      t('Final total is confirmed by the branch when you book.')
                    }}</small>
                  </div>
                  <span class="total-amount">{{
                    formatBaht(foodEstimate.totalMinor)
                  }}</span>
                </div>

                <fieldset class="card-form plain-fieldset">
                  <legend class="section-title">{{ t('Card') }}</legend>
                  <p class="field-hint">
                    {{
                      t(
                        'Authorized now and charged when you check in. Released if you cancel or miss the visit.',
                      )
                    }}
                  </p>
                  <div class="form-field">
                    <label for="card-number" class="form-label">{{
                      t('Card number')
                    }}</label>
                    <input
                      id="card-number"
                      v-model="card.number"
                      class="form-input"
                      inputmode="numeric"
                      autocomplete="cc-number"
                      :aria-invalid="cardErrors.includes('number')"
                      @input="formatCardNumber"
                    />
                    <small
                      v-if="cardErrors.includes('number')"
                      class="field-error"
                      >{{ t('Enter a valid card number.') }}</small
                    >
                  </div>
                  <div class="card-row">
                    <div class="form-field">
                      <label for="card-expiry" class="form-label">{{
                        t('Expiry (MM/YY)')
                      }}</label>
                      <input
                        id="card-expiry"
                        v-model="card.expiry"
                        class="form-input"
                        inputmode="numeric"
                        autocomplete="cc-exp"
                        placeholder="MM/YY"
                        :aria-invalid="cardErrors.includes('expiry')"
                        @input="formatExpiry"
                      />
                      <small
                        v-if="cardErrors.includes('expiry')"
                        class="field-error"
                        >{{ t('Enter a valid, unexpired date.') }}</small
                      >
                    </div>
                    <div class="form-field">
                      <label for="card-cvc" class="form-label">{{
                        t('Security code')
                      }}</label>
                      <input
                        id="card-cvc"
                        v-model="card.cvc"
                        class="form-input"
                        inputmode="numeric"
                        autocomplete="cc-csc"
                        maxlength="4"
                        :aria-invalid="cardErrors.includes('cvc')"
                      />
                      <small
                        v-if="cardErrors.includes('cvc')"
                        class="field-error"
                        >{{ t('Enter the 3 or 4 digit code.') }}</small
                      >
                    </div>
                  </div>
                  <div class="form-field">
                    <label for="card-holder" class="form-label">{{
                      t('Name on card')
                    }}</label>
                    <input
                      id="card-holder"
                      v-model="card.holder"
                      class="form-input"
                      autocomplete="cc-name"
                      :aria-invalid="cardErrors.includes('holder')"
                    />
                    <small
                      v-if="cardErrors.includes('holder')"
                      class="field-error"
                      >{{ t('Enter the name on the card.') }}</small
                    >
                  </div>
                </fieldset>
              </template>
              <p v-else class="pay-note">
                {{ t('Nothing is charged now.') }}
              </p>

              <div v-if="failure" class="error-banner" role="alert">
                <p>{{ failureText }}</p>
                <ul v-if="changeMessages.length">
                  <li v-for="message in changeMessages" :key="message">
                    {{ message }}
                  </li>
                </ul>
                <div class="error-actions">
                  <RouterLink
                    v-if="
                      failure === 'unknown-outcome' ||
                      failure === 'possibly-booked'
                    "
                    :to="{ name: 'CustomerReservationsList' }"
                    class="btn btn-secondary"
                    >{{ t('View My Reservations') }}</RouterLink
                  >
                  <button
                    v-if="failure === 'possibly-booked'"
                    type="button"
                    class="btn btn-secondary"
                    @click="startNewAttempt"
                  >
                    {{ t('It is not there: book again') }}
                  </button>
                  <button
                    v-if="failure === 'changed' || failure === 'unavailable'"
                    type="button"
                    class="btn btn-secondary"
                    @click="goTo(2)"
                  >
                    {{ t('Review table & games') }}
                  </button>
                  <button
                    v-if="
                      (failure === 'changed' || failure === 'unavailable') &&
                      needsCard
                    "
                    type="button"
                    class="btn btn-secondary"
                    @click="goTo(3)"
                  >
                    {{ t('Review food') }}
                  </button>
                  <RouterLink
                    v-if="failure === 'signed-out'"
                    :to="{
                      name: 'AuthLogin',
                      query: { redirect: '/reservations/new' },
                    }"
                    class="btn btn-secondary"
                    >{{ t('Sign In / Register') }}</RouterLink
                  >
                </div>
              </div>

              <button
                type="button"
                class="btn btn-primary pay-btn"
                :disabled="submitting || failure === 'possibly-booked'"
                @click="confirmBooking"
              >
                {{
                  submitting
                    ? t('Confirming…')
                    : needsCard
                      ? t('Confirm & Authorize Card')
                      : t('Confirm Reservation')
                }}
              </button>
            </div>
          </div>

          <div class="wizard-actions">
            <button
              type="button"
              class="btn btn-secondary"
              :disabled="submitting"
              @click="goTo(3)"
            >
              {{ t('← Back to Food & Drinks') }}
            </button>
          </div>
        </section>

        <!-- STEP 5: the server's confirmed booking -->
        <section
          v-else-if="step === 5 && receipt"
          key="step5"
          class="wizard-step receipt-step"
          aria-labelledby="receipt-heading"
        >
          <div class="receipt-card">
            <div class="receipt-badge">
              <span>{{ t('Reservation Confirmed') }}</span>
            </div>
            <h2 id="receipt-heading" class="receipt-title">
              {{ t('See You at The Parlor!') }}
            </h2>

            <div class="receipt-meta-box">
              <div class="meta-line">
                <span class="lbl">{{ t('Reservation Reference') }}</span>
                <span class="v code">{{ receipt.id }}</span>
              </div>
              <div class="meta-line">
                <span class="lbl">{{ t('Branch') }}</span>
                <span class="v">{{ receiptNames.branch }}</span>
              </div>
              <div class="meta-line">
                <span class="lbl">{{ t('When') }}</span>
                <span class="v">{{ receiptVisit }}</span>
              </div>
              <div class="meta-line">
                <span class="lbl">{{ t('Table') }}</span>
                <span class="v"
                  >{{ receiptNames.table || t('Table') }} ·
                  {{ formatBaht(receipt.tableHourlyRate.amountMinor) }}
                  {{ t('/ hour') }}</span
                >
              </div>
              <div class="meta-line">
                <span class="lbl">{{ t('Party Size') }}</span>
                <span class="v">{{
                  t(
                    receipt.partySize === 1
                      ? '{count} player'
                      : '{count} players',
                    { count: receipt.partySize },
                  )
                }}</span>
              </div>
              <div
                v-for="game in receipt.games"
                :key="game.gameId"
                class="meta-line"
              >
                <span class="lbl">{{ t('Game') }}</span>
                <span class="v"
                  >{{ receiptNames.games[game.gameId] ?? t('Game') }} ×
                  {{ game.quantity }} ·
                  {{ formatBaht(game.unitHourlyRate.amountMinor) }}
                  {{ t('/ hour each') }}</span
                >
              </div>
              <div v-if="receipt.notes" class="meta-line">
                <span class="lbl">{{ t('Notes') }}</span>
                <span class="v">{{ receipt.notes }}</span>
              </div>
            </div>

            <div v-if="receipt.preOrder" class="receipt-food-box">
              <h3 class="food-h">{{ t('Food Pre-Order') }}</h3>
              <div
                v-for="line in receipt.preOrder.items"
                :key="line.menuItemId"
                class="receipt-food-item"
              >
                <span
                  >{{ line.quantity }}× {{ line.name }} ({{
                    formatBaht(line.unitPrice.amountMinor)
                  }})</span
                >
                <span>{{ formatBaht(line.lineTotal.amountMinor) }}</span>
              </div>
              <div class="receipt-food-item">
                <span>{{ t('Subtotal') }}</span>
                <span>{{
                  formatBaht(receipt.preOrder.subtotal.amountMinor)
                }}</span>
              </div>
              <div class="receipt-food-item">
                <span>{{ t('Pre-Order Discount') }}</span>
                <span
                  >−{{
                    formatBaht(receipt.preOrder.discount.amountMinor)
                  }}</span
                >
              </div>
              <div class="receipt-food-item receipt-total">
                <span>{{ t('Total') }}</span>
                <span>{{
                  formatBaht(receipt.preOrder.total.amountMinor)
                }}</span>
              </div>
              <p class="field-hint">
                {{ paymentText(receipt.preOrder.payment) }}
              </p>
            </div>

            <div class="receipt-actions">
              <RouterLink
                :to="{ name: 'CustomerReservationsList' }"
                class="btn btn-primary"
              >
                {{ t('View My Reservations') }}
              </RouterLink>
              <button
                type="button"
                class="btn btn-secondary"
                @click="bookAnother"
              >
                {{ t('Book Another Visit') }}
              </button>
            </div>
          </div>
        </section>
      </Transition>
    </div>
  </div>
</template>

<style scoped>
.reservation-page {
  background: #fafbf9;
  min-height: calc(100vh - 120px);
  padding: 32px 16px 64px;
  color: #202e27;
  font-family:
    'FC Mee Sakul', 'Plus Jakarta Sans', system-ui, Arial, sans-serif;
}

.container {
  max-width: 980px;
  margin: 0 auto;
}

/* Stepper Bar */
.stepper-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border: 1px solid #c6ad7a;
  border-radius: 8px;
  padding: 14px 20px;
  margin-bottom: 28px;
  box-shadow: 0 2px 8px rgba(32, 60, 48, 0.05);
  overflow-x: auto;
}

.step-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  opacity: 0.55;
  white-space: nowrap;
}

.step-indicator.active {
  opacity: 1;
  font-weight: 700;
  color: #203c30;
}

.step-indicator.completed {
  opacity: 0.9;
  color: #1a4231;
}

.step-num {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #e9dec9;
  color: #203c30;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
}

.step-indicator.active .step-num {
  background: #203c30;
  color: #fffaf1;
}

.step-indicator.completed .step-num {
  background: #2e6022;
  color: #ffffff;
}

.step-text {
  font-size: 13px;
}

.step-divider {
  flex: 1;
  height: 1px;
  background: #d5c8b2;
  margin: 0 12px;
  min-width: 16px;
}

/* Wizard Step Layout */
.wizard-step {
  background: #ffffff;
  border: 1px solid #d5c8b2;
  border-radius: 10px;
  padding: 28px 24px;
  box-shadow: 0 4px 16px rgba(20, 43, 32, 0.04);
}

.step-header {
  margin-bottom: 22px;
  border-bottom: 1px solid #eee4d1;
  padding-bottom: 16px;
}

.step-title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 800;
  color: #1b3528;
}

.step-subtitle {
  margin: 0;
  font-size: 14px;
  color: #60594a;
  line-height: 1.5;
}

/* Form Grid */
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-field.full-width {
  grid-column: 1 / -1;
}

.form-label {
  font-size: 13px;
  font-weight: 700;
  color: #27382f;
}

.form-input,
.form-select {
  padding: 10px 14px;
  border: 1px solid #c9bda7;
  border-radius: 6px;
  background: #ffffff;
  color: #1e2e25;
  font-size: 14px;
  outline: none;
}

.form-input:focus,
.form-select:focus {
  border-color: #203c30;
  box-shadow: 0 0 0 2px rgba(32, 60, 48, 0.15);
}

.field-hint {
  font-size: 11px;
  color: #6a6252;
}

/* Glamorous Brief Reservation Summary with Running Gold Shine Border */

/* Base static gold outline */

/* Continuous running gold shine animation around the perimeter */

/* Inner deep emerald parlor core to reveal only the 2px running gold border */

@keyframes goldBorderSpin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

.validation-alert {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff3f0;
  border: 1px solid #d45b45;
  color: #9c2715;
  border-radius: 6px;
  padding: 12px 14px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 18px;
}

/* Tables grid */
.tables-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
  margin-bottom: 28px;
}

.table-card {
  background: #ffffff;
  border: 1.5px solid #ded5c3;
  border-radius: 12px;
  padding: 16px 18px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: all 0.28s cubic-bezier(0.16, 1, 0.3, 1);
  box-shadow: 0 2px 8px rgba(32, 60, 48, 0.04);
}

.table-card:hover {
  border-color: #baa67c;
  box-shadow:
    0 8px 20px rgba(20, 43, 32, 0.08),
    0 0 12px rgba(198, 173, 122, 0.15);
  transform: translateY(-2px);
}

.table-card.is-selected {
  transform: translateY(-3px);
  border-color: #f7e7c4;
  background: radial-gradient(
    ellipse at 50% 15%,
    #244b39 0%,
    #173224 70%,
    #0f2219 100%
  );
  color: #fffaf0;
  box-shadow:
    0 0 0 2px #c6ad7a,
    0 12px 28px rgba(0, 0, 0, 0.35),
    0 0 20px rgba(198, 173, 122, 0.3);
}

.table-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 2px;
}

.table-label {
  font-size: 15.5px;
  font-weight: 700;
  color: #142b20;
  letter-spacing: -0.01em;
}

.capacity-pill {
  font-size: 11.5px;
  background: #f4ecdc;
  color: #142b20;
  border: 1px solid rgba(198, 173, 122, 0.4);
  padding: 4px 10px;
  border-radius: 14px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
  flex-shrink: 0;
}

.table-rate {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.rate-num {
  font-size: 19px;
  font-weight: 800;
  color: #142b20;
}

.rate-unit {
  font-size: 12px;
  color: #6a6252;
}

.select-radio {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #142b20;
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid rgba(198, 173, 122, 0.25);
}

/* HIGH-CONTRAST GOLD & CREAM WHEN SELECTED - ENSURES TEXT NEVER DISAPPEARS */
.table-card.is-selected .table-label {
  color: #fffaf0 !important;
}

.table-card.is-selected .rate-num {
  color: #ffd98a !important;
}

.table-card.is-selected .rate-unit {
  color: #e5d8b8 !important;
}

.table-card.is-selected .select-radio {
  color: #fff8e7 !important;
  border-top-color: rgba(198, 173, 122, 0.4);
}

.table-card.is-selected .capacity-pill {
  background: rgba(198, 173, 122, 0.25) !important;
  border-color: #c6ad7a !important;
  color: #fff8e7 !important;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: #1a382a;
  margin: 0 0 14px;
}

/* Review step */
.review-grid {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 20px;
  margin-bottom: 22px;
}

.review-card,
.pricing-card {
  background: #fffaf1;
  border: 1px solid #d5c8b2;
  border-radius: 8px;
  padding: 18px;
}

.card-heading {
  margin: 0 0 14px;
  font-size: 16px;
  font-weight: 700;
  color: #1a382a;
  border-bottom: 1px solid #e5dac6;
  padding-bottom: 8px;
}

.review-row,
.cost-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 10px;
  color: #4b4435;
}

.review-row .val {
  font-weight: 600;
  color: #1b2f25;
  text-align: right;
}

.discount-row {
  color: #b54708;
  font-weight: 700;
}

.grand-total-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 2px solid #c6ad7a;
  padding-top: 12px;
  margin-top: 14px;
  font-size: 16px;
  font-weight: 800;
  color: #162f23;
}

.total-amount {
  font-size: 24px;
  color: #184330;
}

.food-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}

.food-item-line {
  display: flex;
  justify-content: space-between;
  color: #443e32;
}

.pay-btn {
  width: 100%;
  margin-top: 16px;
  padding: 13px;
  font-size: 15px;
}

.pay-note {
  margin: 8px 0 0;
  font-size: 11px;
  color: #6a6252;
  text-align: center;
}

/* Receipt step */
.receipt-step {
  display: flex;
  justify-content: center;
}

.receipt-card {
  max-width: 580px;
  width: 100%;
  background: #ffffff;
  border: 2px solid #c6ad7a;
  border-radius: 12px;
  padding: 32px 24px;
  text-align: center;
  box-shadow: 0 10px 30px rgba(18, 39, 29, 0.08);
}

.receipt-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: #e2f0d9;
  color: #275e1b;
  font-size: 13px;
  font-weight: 800;
  padding: 6px 14px;
  border-radius: 20px;
  margin-bottom: 14px;
}

.receipt-title {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 800;
  color: #173224;
}

.receipt-meta-box {
  background: #fffaf1;
  border: 1px solid #d5c8b2;
  border-radius: 8px;
  padding: 16px;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 20px;
}

.meta-line {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}

.meta-line .lbl {
  color: #60594a;
}

.meta-line .v {
  font-weight: 600;
  color: #1b2f25;
}

.meta-line .code {
  font-family: monospace;
  background: #e9dec9;
  padding: 1px 6px;
  border-radius: 3px;
}

.receipt-food-box {
  background: #fbf9f4;
  border: 1px dashed #c6ad7a;
  border-radius: 8px;
  padding: 14px;
  margin-bottom: 24px;
  text-align: left;
}

.food-h {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 700;
  color: #1a4231;
}

.receipt-food-item {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 6px;
  color: #4b4435;
}

.receipt-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

/* Actions buttons */
.wizard-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  border-top: 1px solid #eee4d1;
  padding-top: 20px;
  margin-top: 20px;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px 18px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  text-decoration: none;
  transition: all 140ms ease;
}

.btn-primary {
  background: #203c30;
  color: #fffaf1;
  border: none;
}

.btn-primary:hover:not(:disabled) {
  background: #142820;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: #fffaf1;
  border: 1px solid #c9bda7;
  color: #3b362c;
}

.btn-secondary:hover {
  background: #eee4d1;
}

/* Modal Backdrop */

.state-message,
.empty-tables-box {
  padding: 32px 16px;
  text-align: center;
  background: #fffaf1;
  border: 1px dashed #c6ad7a;
  border-radius: 8px;
  margin-bottom: 24px;
}

.empty-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 700;
  color: #8c392b;
}

.empty-sub {
  margin: 0 0 16px;
  font-size: 13px;
  color: #6a6252;
}

@media (max-width: 768px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
  .review-grid {
    grid-template-columns: 1fr;
  }
}

/* Smooth Glam Transition between Wizard Steps */
.glam-step-enter-active,
.glam-step-leave-active {
  transition:
    opacity 0.3s cubic-bezier(0.16, 1, 0.3, 1),
    transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
.glam-step-enter-from {
  opacity: 0;
  transform: translateY(14px) scale(0.99);
}
.glam-step-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.99);
}

/* Gold Layout Icons styling */

/* Smooth Button Hover Elevation */
.btn-primary {
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  box-shadow: 0 4px 14px rgba(198, 173, 122, 0.25);
}
.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(198, 173, 122, 0.45);
  filter: brightness(1.08);
}
.btn-secondary {
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}
.btn-secondary:hover:not(:disabled) {
  transform: translateY(-2px);
  border-color: #c6ad7a;
  color: #ddc18c;
}

/* Member Status Bar & Auth Gate Banner */
.member-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #142b20;
  border: 1px solid #c6ad7a;
  border-radius: 8px;
  padding: 10px 16px;
  margin-bottom: 20px;
  gap: 12px;
  box-shadow: 0 4px 14px rgba(20, 43, 32, 0.15);
  flex-wrap: wrap;
}

.member-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  color: #fffaf0;
}

.member-text {
  color: #ddc18c;
}

.member-name {
  color: #ffffff;
  font-weight: 800;
}

.auth-gate-callout {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff8f0;
  border: 1.5px solid #d4a373;
  border-radius: 10px;
  padding: 14px 18px;
  margin-bottom: 22px;
  gap: 16px;
  flex-wrap: wrap;
}

.auth-gate-info {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.auth-gate-heading {
  font-size: 14.5px;
  color: #8c4a00;
  display: block;
  margin-bottom: 2px;
}

.auth-gate-sub {
  margin: 0;
  font-size: 12.5px;
  color: #6a4c28;
  line-height: 1.4;
}

.auth-signin-btn {
  white-space: nowrap;
}
.due-sub-text {
  display: block;
  font-size: 10.5px;
  color: #8c7649;
  margin-top: 2px;
}

.visit-line {
  margin: 4px 0 0;
  font-size: 14px;
  color: #203c30;
}

.notice {
  background: #fbf6ea;
  border: 1px solid #c6ad7a;
  color: #5b4520;
  border-radius: 6px;
  padding: 10px 14px;
  font-size: 13px;
  margin: 0 0 16px;
}

.plain-fieldset {
  border: 0;
  margin: 0;
  padding: 0;
  min-width: 0;
}

.games-section {
  margin: 24px 0;
}

.games-list {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
  border: 1px solid #e3d7c0;
  border-radius: 8px;
}

.game-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
}

.game-row + .game-row {
  border-top: 1px solid #eee4d1;
}

.game-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.qty-stepper {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.qty-stepper button {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: 1px solid #c6ad7a;
  background: #ffffff;
  color: #203c30;
  font-size: 16px;
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.qty-stepper button:hover:not(:disabled) {
  background: #f4eddd;
}

.qty-stepper button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.qty-stepper span {
  min-width: 1.5em;
  text-align: center;
  font-weight: 700;
}

.estimate-note {
  margin-top: 12px;
}

.card-form {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.field-error {
  color: #9c2715;
  font-size: 12px;
}

.error-banner {
  background: #fff3f0;
  border: 1px solid #d45b45;
  color: #7d1f10;
  border-radius: 6px;
  padding: 12px 14px;
  font-size: 13px;
  margin: 16px 0 12px;
}

.error-banner p {
  margin: 0;
}

.error-banner ul {
  margin: 8px 0 0;
  padding-left: 18px;
}

.error-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.error-actions:empty {
  display: none;
}

.receipt-total {
  font-weight: 700;
  border-top: 1px solid #e3d7c0;
  padding-top: 6px;
}

@media (prefers-reduced-motion: reduce) {
  .qty-stepper button {
    transition: none;
  }
}
</style>

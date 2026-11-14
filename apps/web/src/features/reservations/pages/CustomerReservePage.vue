<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { useRoute, RouterLink } from 'vue-router';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import {
  createMyReservation,
  getBranchAvailability,
  getBranchMenu,
  listBranchGames,
  type CreateReservationRequest,
  type Reservation,
  type TableAvailability,
} from '@theparlor/api-client';
import { useAuthStore } from '../../../shared/stores/auth';
import { useBranchDirectoryStore } from '../../../shared/stores/branch-directory';
import { useStoreContextStore } from '../../../shared/stores/store-context';
import { useStoreText } from '../../../shared/lib/store-text';
import { collectPages } from '../../../shared/lib/pagination';
import { httpStatus } from '../../../shared/lib/api-errors';
import { tokenizeCard } from '../../../shared/lib/card-tokens';
import { AppNumberStepper, AppSelect } from '../../../shared/ui';
import FoodMenuPicker from '../components/FoodMenuPicker.vue';
import CardPaymentFields from '../components/CardPaymentFields.vue';
import ReservationPromptPay from '../components/ReservationPromptPay.vue';
import type { FoodOrderItem } from '../types';
import { toFoodMenuItem } from '../data/menu';
import {
  BRANCH_TIME_ZONE,
  PRE_ORDER_WINDOW_DAYS,
  branchDate,
  branchInstant,
  branchTime,
  canPreOrder,
  formatBaht,
  plusHours,
  preOrderPreview,
} from '../data/booking';

const route = useRoute();
const auth = useAuthStore();
const queryClient = useQueryClient();
const { t, locale } = useStoreText();
const storeContext = useStoreContextStore();
const directory = useBranchDirectoryStore();

// 1 When & where, 2 Table, 3 Games, 4 Food & drinks, 5 Review & pay, 6 Confirmation
const currentStep = ref<number>(1);
const steps = [
  'When & Where',
  'Table',
  'Games',
  'Food & Drinks (-10%)',
  'Review & Pay',
];

// Step 1: when and where, on the branch clock.
const selectedBranchId = ref<string>('');
const reservationDate = ref<string>('');
const reservationStartTime = ref<string>('14:00');
// Open the native picker from anywhere in the date and time fields.
function openNativePicker(event: MouseEvent) {
  (event.currentTarget as HTMLInputElement).showPicker?.();
}
const reservationDurationHours = ref<number>(2);
const partySize = ref<number>(4);
const specialNotes = ref<string>('');

/** A game the customer arrived from, preselected once it is known to be free. */
const requestedGameId = ref<string>('');

const todayDateString = computed(() => branchDate(new Date()));

onMounted(() => {
  void directory.load();
  reservationDate.value = branchDate(new Date(Date.now() + 86_400_000));
  if (typeof route.query.game === 'string')
    requestedGameId.value = route.query.game;
});

const branches = computed(() => directory.branches);

let userSelectedBranch = false;

const branchOptions = computed(() =>
  branches.value.map((b) => ({
    value: b.id,
    label: `${b.name} — ${b.address}`,
  })),
);
const durationOptions = computed(() => [
  { value: '1', label: t('1 Hour') },
  { value: '2', label: t('2 Hours (Recommended)') },
  { value: '3', label: t('3 Hours') },
  { value: '4', label: t('4 Hours (Half Day)') },
  { value: '6', label: t('6 Hours (Full Session)') },
]);
const durationValue = computed({
  get: () => String(reservationDurationHours.value),
  set: (value: string) => {
    reservationDurationHours.value = Number(value);
  },
});

function onBranchChange(id: string) {
  userSelectedBranch = true;
  selectedBranchId.value = id;
  if (branches.value.some((b) => b.id === id)) storeContext.select(id);
}

// Preselect the branch from the link, else the shopper's chosen store, else the first.
watch(
  [branches, () => storeContext.choice],
  ([list, contextChoice]) => {
    if (list.length === 0) return;
    if (userSelectedBranch && list.some((b) => b.id === selectedBranchId.value))
      return;
    const wanted =
      typeof route.query.branch === 'string' ? route.query.branch : undefined;
    const fromLink = list.find(
      (b) => b.id === wanted || b.name.toLowerCase() === wanted?.toLowerCase(),
    );
    const fromContext =
      contextChoice && contextChoice !== 'browse' && contextChoice !== 'online'
        ? list.find((b) => b.id === contextChoice)
        : undefined;
    const pick = fromLink ?? fromContext;
    if (pick) selectedBranchId.value = pick.id;
    else if (!list.some((b) => b.id === selectedBranchId.value))
      selectedBranchId.value = list[0].id;
  },
  { immediate: true },
);

const selectedBranch = computed(() => directory.byId(selectedBranchId.value));

const startTimestamp = computed(() =>
  branchInstant(reservationDate.value, reservationStartTime.value),
);
const endTimestamp = computed(() =>
  plusHours(startTimestamp.value, reservationDurationHours.value),
);

const isDateInPast = computed(
  () =>
    !!reservationDate.value && reservationDate.value < todayDateString.value,
);

// Same-day bookings start at least 30 minutes from now.
const isTimeInPast = computed(() => {
  if (!startTimestamp.value || isDateInPast.value) return false;
  return Date.parse(startTimestamp.value) < Date.now() + 30 * 60_000;
});

const isDateTimeInvalid = computed(
  () => isDateInPast.value || isTimeInPast.value,
);

const dateLocale = computed(() =>
  locale.value === 'th-TH' ? 'th-TH' : 'en-US',
);

const glamorousDateFormatted = computed(() =>
  startTimestamp.value
    ? new Date(startTimestamp.value).toLocaleDateString(dateLocale.value, {
        timeZone: BRANCH_TIME_ZONE,
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric',
      })
    : '',
);

const glamorousTimeFormatted = computed(() =>
  startTimestamp.value
    ? `${branchTime(new Date(startTimestamp.value))} – ${branchTime(new Date(endTimestamp.value))}`
    : '',
);

const durationLabel = computed(() =>
  t(reservationDurationHours.value === 1 ? '1 Hour' : '{count} Hours', {
    count: reservationDurationHours.value,
  }),
);

const formattedDateTimeInterval = computed(() =>
  startTimestamp.value
    ? `${glamorousDateFormatted.value}, ${glamorousTimeFormatted.value} (${durationLabel.value})`
    : '',
);

// Live availability for the chosen window; nothing is assumed when it fails.
const availabilityQuery = useQuery({
  queryKey: [
    'reservations',
    'availability',
    selectedBranchId,
    startTimestamp,
    endTimestamp,
    partySize,
  ],
  meta: { public: true },
  queryFn: ({ signal }) =>
    getBranchAvailability(
      selectedBranchId.value,
      {
        startsAt: startTimestamp.value,
        endsAt: endTimestamp.value,
        partySize: partySize.value,
      },
      undefined,
      signal,
    ),
  enabled: computed(
    () =>
      currentStep.value >= 2 &&
      currentStep.value <= 5 &&
      !!selectedBranchId.value &&
      !!startTimestamp.value &&
      !isDateTimeInvalid.value,
  ),
});

const availability = computed(() => availabilityQuery.data.value);

// Step 2: table.
const selectedTable = ref<TableAvailability | null>(null);

const availableTables = computed<TableAvailability[]>(() =>
  (availability.value?.tables ?? []).filter(
    (table) => table.available && table.capacity >= partySize.value,
  ),
);

watch(availableTables, (tables) => {
  if (!tables.some((table) => table.tableId === selectedTable.value?.tableId))
    selectedTable.value = tables[0] ?? null;
});

// Step 3: games from the branch rental library that are free for the whole window.
const branchGamesQuery = useQuery({
  queryKey: ['reservations', 'branch-games', selectedBranchId],
  meta: { public: true },
  staleTime: 300_000,
  queryFn: ({ signal }) =>
    collectPages((cursor) =>
      listBranchGames(
        selectedBranchId.value,
        { limit: 100, cursor },
        undefined,
        signal,
      ),
    ),
  enabled: computed(
    () =>
      !!selectedBranchId.value &&
      (currentStep.value >= 3 || !!requestedGameId.value),
  ),
});

const gameNames = computed(
  () =>
    new Map(
      (branchGamesQuery.data.value ?? []).map((entry) => [
        entry.game.id,
        entry.game.name,
      ]),
    ),
);

interface GameChoice {
  gameId: string;
  name: string;
  available: number;
  hourlyRateMinor: number;
}

const gameChoices = computed<GameChoice[]>(() =>
  (availability.value?.games ?? [])
    .filter((game) => game.rentalEnabled && game.availableQuantity > 0)
    .map((game) => ({
      gameId: game.gameId,
      name: gameNames.value.get(game.gameId) ?? t('Game'),
      available: game.availableQuantity,
      hourlyRateMinor: game.hourlyRate?.amountMinor ?? 0,
    }))
    .sort((a, b) => a.name.localeCompare(b.name)),
);

/** Copies wanted per game ID; absent means not wanted. */
const selectedGames = ref<Record<string, number>>({});

function gameQuantity(gameId: string): number {
  return selectedGames.value[gameId] ?? 0;
}

function setGameQuantity(game: GameChoice, quantity: number) {
  const next = Math.max(0, Math.min(game.available, quantity));
  const updated = { ...selectedGames.value };
  if (next === 0) delete updated[game.gameId];
  else updated[game.gameId] = next;
  selectedGames.value = updated;
}

// Drop or trim games that are no longer free, and preselect a linked game once.
watch(gameChoices, (choices) => {
  const byId = new Map(choices.map((game) => [game.gameId, game]));
  const kept: Record<string, number> = {};
  for (const [gameId, quantity] of Object.entries(selectedGames.value)) {
    const game = byId.get(gameId);
    if (game) kept[gameId] = Math.min(quantity, game.available);
  }
  if (requestedGameId.value && byId.has(requestedGameId.value)) {
    kept[requestedGameId.value] ??= 1;
    requestedGameId.value = '';
  }
  selectedGames.value = kept;
});

const requestedGameName = computed(() =>
  requestedGameId.value
    ? gameNames.value.get(requestedGameId.value)
    : undefined,
);

const chosenGames = computed(() =>
  gameChoices.value
    .filter((game) => gameQuantity(game.gameId) > 0)
    .map((game) => ({ ...game, quantity: gameQuantity(game.gameId) })),
);

// Step 4: food and drinks, held now and served at check-in.
const preOrderAllowed = computed(() => canPreOrder(startTimestamp.value));

const menuQuery = useQuery({
  queryKey: ['reservations', 'menu', selectedBranchId],
  meta: { public: true },
  queryFn: ({ signal }) =>
    getBranchMenu(selectedBranchId.value, undefined, signal),
  enabled: computed(
    () =>
      !!selectedBranchId.value &&
      preOrderAllowed.value &&
      currentStep.value >= 4 &&
      currentStep.value <= 5,
  ),
});

const menuItems = computed(() =>
  (menuQuery.data.value?.items ?? []).map(toFoodMenuItem),
);

const preOrderedFood = ref<FoodOrderItem[]>([]);

watch(selectedBranchId, () => {
  preOrderedFood.value = [];
  selectedGames.value = {};
});

watch(preOrderAllowed, (allowed) => {
  if (!allowed) preOrderedFood.value = [];
});

// Keep the pre-order within what the branch has left after a menu refresh.
watch(menuItems, (items) => {
  const byId = new Map(items.map((item) => [item.id, item]));
  preOrderedFood.value = preOrderedFood.value
    .map((line) => {
      const item = byId.get(line.item.id);
      return item
        ? { item, quantity: Math.min(line.quantity, item.available ?? 0) }
        : null;
    })
    .filter((line): line is FoodOrderItem => !!line && line.quantity > 0);
});

const hasFood = computed(() => preOrderedFood.value.length > 0);

const foodPreview = computed(() =>
  preOrderPreview(
    preOrderedFood.value.reduce(
      (sum, line) => sum + Math.round(line.item.price * 100) * line.quantity,
      0,
    ),
  ),
);

// PromptPay prepays the selected duration; the card path keeps counter billing.
const tableHourlyMinor = computed(
  () => selectedTable.value?.hourlyRate.amountMinor ?? 0,
);

const visitEstimateMinor = computed(
  () =>
    (tableHourlyMinor.value +
      chosenGames.value.reduce(
        (sum, game) => sum + game.hourlyRateMinor * game.quantity,
        0,
      )) *
    reservationDurationHours.value,
);

// Step 5: create a full-visit QR, or authorize the food card on the legacy path.
const paymentMethod = ref<'PROMPTPAY' | 'CARD'>('PROMPTPAY');
const upfrontMinor = computed(
  () => visitEstimateMinor.value + foodPreview.value.totalMinor,
);
const cardFields = ref<InstanceType<typeof CardPaymentFields> | null>(null);
const isSubmitting = ref(false);
const submissionError = ref('');
const createdReservation = ref<Reservation | null>(null);

/**
 * One idempotency key, with the token it was sent with, per booking attempt. Both
 * survive only a lost response, so a retry replays instead of booking twice; any
 * answer from the server, or changing the booking, starts a fresh attempt.
 */
let attempt: { key: string; paymentToken?: string } | undefined;
watch(paymentMethod, () => {
  attempt = undefined;
  submissionError.value = '';
});

watch(currentStep, (step) => {
  if (step < 5) attempt = undefined;
  if (step !== 5) submissionError.value = '';
});

function failureMessage(error: unknown): string {
  const status = httpStatus(error);
  if (status === 402)
    return t(
      'The card was declined and nothing was booked. Check the details or use another card.',
    );
  if (status === 409)
    return t(
      'The table, a game or some food was just taken, or the visit is too far ahead for a pre-order. Review your choices and try again.',
    );
  if (status === 502)
    return t('Payment is unavailable right now. Please try again shortly.');
  if (status === 400)
    return t('Some booking details are not valid. Please check them.');
  return t('The reservation could not be made. Please try again.');
}

async function confirmReservation() {
  if (isSubmitting.value || !selectedTable.value) return;
  submissionError.value = '';
  const needsCard = paymentMethod.value === 'CARD' && hasFood.value;
  const card =
    needsCard && !attempt?.paymentToken ? cardFields.value?.read() : undefined;
  if (needsCard && !attempt?.paymentToken && !card) return;
  isSubmitting.value = true;
  try {
    if (!attempt) attempt = { key: crypto.randomUUID() };
    if (card && !attempt.paymentToken) {
      attempt.paymentToken = await tokenizeCard(card);
      cardFields.value?.clear();
    }
    const request: CreateReservationRequest = {
      tableId: selectedTable.value.tableId,
      startsAt: startTimestamp.value,
      endsAt: endTimestamp.value,
      partySize: partySize.value,
      games: chosenGames.value.map(({ gameId, quantity }) => ({
        gameId,
        quantity,
      })),
      notes: specialNotes.value.trim() || undefined,
      paymentMethod: paymentMethod.value,
      preOrder: hasFood.value
        ? {
            items: preOrderedFood.value.map((line) => ({
              menuItemId: line.item.id,
              quantity: line.quantity,
            })),
            paymentToken: attempt.paymentToken,
          }
        : undefined,
    };
    createdReservation.value = await createMyReservation(
      selectedBranchId.value,
      request,
      { headers: { 'Idempotency-Key': attempt.key } },
    );
    attempt = undefined;
    currentStep.value = 6;
    void queryClient.invalidateQueries({ queryKey: ['reservations'] });
  } catch (error) {
    const status = httpStatus(error);
    // Any answer is definite: the token is spent or refused, and the key is done.
    if (status !== undefined) attempt = undefined;
    submissionError.value = failureMessage(error);
    if (status === 409) {
      void availabilityQuery.refetch();
      void menuQuery.refetch();
    }
  } finally {
    isSubmitting.value = false;
  }
}

function startOver() {
  createdReservation.value = null;
  preOrderedFood.value = [];
  selectedGames.value = {};
  specialNotes.value = '';
  currentStep.value = 1;
}

const receiptGames = computed(() =>
  (createdReservation.value?.games ?? []).map((game) => ({
    name: gameNames.value.get(game.gameId) ?? t('Game'),
    quantity: game.quantity,
  })),
);

const receiptPaymentNote = computed(() => {
  const status = createdReservation.value?.preOrder?.payment.status;
  if (status === 'AUTHORIZED')
    return t('Authorized on your card; charged when you check in.');
  if (status === 'CAPTURED') return t('Charged');
  return '';
});
</script>

<template>
  <div class="reservation-page">
    <div class="container">
      <nav class="stepper-nav" :aria-label="t('Reservation progress')">
        <template v-for="(label, index) in steps" :key="label">
          <div v-if="index > 0" class="step-divider" />
          <div
            class="step-indicator"
            :class="{
              active: currentStep === index + 1,
              completed: currentStep > index + 1,
            }"
            :aria-current="currentStep === index + 1 ? 'step' : undefined"
          >
            <span class="step-num"
              ><span>{{ index + 1 }}</span></span
            >
            <span class="step-text">{{ t(label) }}</span>
          </div>
        </template>
      </nav>

      <Transition name="glam-step" mode="out-in">
        <!-- STEP 1: Date, Time & Branch (Timestamp driven) -->
        <section
          v-if="currentStep === 1"
          key="step1"
          class="wizard-step"
          aria-labelledby="step1-heading"
        >
          <div class="step-header">
            <h2 id="step1-heading" class="step-title">
              {{ t('Choose Your Branch & Time') }}
            </h2>
            <p class="step-subtitle">
              {{
                t('Select your preferred branch location, date, and time slot.')
              }}
            </p>
          </div>

          <!-- Registered Member Verification Callout -->
          <div v-if="auth.authenticated" class="member-status-bar">
            <div class="member-meta">
              <svg class="gold-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path
                  fill="currentColor"
                  d="M12 3 15 9 21 12 15 15 12 21 9 15 3 12 9 9Z"
                />
              </svg>
              <span class="member-text">{{
                t('Reserving as Registered Member:')
              }}</span>
              <strong class="member-name">{{
                auth.displayName || auth.username
              }}</strong>
            </div>
            <span class="member-badge-perk">{{
              t('10% Pre-order Discount Active')
            }}</span>
          </div>
          <div v-else class="auth-gate-callout" role="alert">
            <div class="auth-gate-info">
              <svg
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                class="gold-icon"
              >
                <path
                  d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"
                />
                <line x1="12" y1="9" x2="12" y2="13" />
                <line x1="12" y1="17" x2="12.01" y2="17" />
              </svg>
              <div>
                <strong class="auth-gate-heading">{{
                  t('Sign in Required')
                }}</strong>
                <p class="auth-gate-sub">
                  {{
                    t(
                      'Table reservations and food pre-orders are exclusively available for registered and signed-in members.',
                    )
                  }}
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
              {{ t('Sign In / Register') }} →
            </RouterLink>
          </div>

          <div class="form-grid">
            <!-- Branch Picker -->
            <div class="form-field full-width">
              <label for="branch-select" class="form-label">{{
                t('Branch Location')
              }}</label>
              <AppSelect
                input-id="branch-select"
                block
                :model-value="selectedBranchId"
                :label="t('Branch Location')"
                :options="branchOptions"
                @update:model-value="onBranchChange"
              />
            </div>

            <!-- Date Picker -->
            <div class="form-field">
              <label for="res-date" class="form-label">{{
                t('Reservation Date')
              }}</label>
              <input
                id="res-date"
                v-model="reservationDate"
                :min="todayDateString"
                type="date"
                class="form-input"
                required
                @click="openNativePicker"
              />
            </div>

            <!-- Start Time -->
            <div class="form-field">
              <label for="res-time" class="form-label">{{
                t('Start Time')
              }}</label>
              <input
                id="res-time"
                v-model="reservationStartTime"
                type="time"
                class="form-input"
                required
                @click="openNativePicker"
              />
            </div>

            <!-- Duration -->
            <div class="form-field">
              <label for="res-duration" class="form-label">{{
                t('Duration')
              }}</label>
              <AppSelect
                v-model="durationValue"
                input-id="res-duration"
                block
                :label="t('Duration')"
                :options="durationOptions"
              />
            </div>

            <!-- Party Size -->
            <div class="form-field">
              <label for="res-party" class="form-label">{{
                t('Party Size')
              }}</label>
              <AppNumberStepper
                v-model="partySize"
                input-id="res-party"
                :label="t('Party Size')"
                :min="1"
                :max="20"
              />
            </div>
          </div>

          <!-- Date & Time validation alerts -->
          <div v-if="isDateInPast" class="validation-alert" role="alert">
            <svg
              class="gold-icon warning-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path
                d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"
              ></path>
              <line x1="12" y1="9" x2="12" y2="13"></line>
              <line x1="12" y1="17" x2="12.01" y2="17"></line>
            </svg>
            <span>{{
              t('Reservation date and time cannot be in the past.')
            }}</span>
          </div>
          <div v-else-if="isTimeInPast" class="validation-alert" role="alert">
            <svg
              class="gold-icon warning-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path
                d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"
              ></path>
              <line x1="12" y1="9" x2="12" y2="13"></line>
              <line x1="12" y1="17" x2="12.01" y2="17"></line>
            </svg>
            <span>{{
              t(
                'For same-day bookings, please select a time slot at least 30 minutes in advance.',
              )
            }}</span>
          </div>

          <!-- Glamorous Brief Reservation Summary Card (No raw timestamps here) -->
          <div
            v-if="startTimestamp && endTimestamp && !isDateTimeInvalid"
            class="glamorous-summary-card"
          >
            <div class="glamorous-summary-border-base" aria-hidden="true"></div>
            <div class="summary-card-header">
              <span class="parlor-crest">
                <svg
                  class="gold-icon mini-crest"
                  viewBox="0 0 24 24"
                  fill="currentColor"
                >
                  <path
                    d="M12 2l2.4 6.6L21 11l-6.6 2.4L12 20l-2.4-6.6L3 11l6.6-2.4L12 2z"
                  ></path>
                </svg>
                <span>The Parlor · {{ selectedBranch?.name }}</span>
                <svg
                  class="gold-icon mini-crest"
                  viewBox="0 0 24 24"
                  fill="currentColor"
                >
                  <path
                    d="M12 2l2.4 6.6L21 11l-6.6 2.4L12 20l-2.4-6.6L3 11l6.6-2.4L12 2z"
                  ></path>
                </svg>
              </span>
              <span class="party-badge">
                <svg
                  class="gold-icon micro"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                  <circle cx="9" cy="7" r="4"></circle>
                </svg>
                <span>{{
                  t(partySize === 1 ? '{count} player' : '{count} players', {
                    count: partySize,
                  })
                }}</span>
              </span>
            </div>

            <div class="summary-card-body">
              <div class="summary-item">
                <span class="summary-icon">
                  <svg
                    class="gold-icon"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <rect
                      x="3"
                      y="4"
                      width="18"
                      height="18"
                      rx="2"
                      ry="2"
                    ></rect>
                    <line x1="16" y1="2" x2="16" y2="6"></line>
                    <line x1="8" y1="2" x2="8" y2="6"></line>
                    <line x1="3" y1="10" x2="21" y2="10"></line>
                  </svg>
                </span>
                <div>
                  <span class="summary-label">{{ t('Reservation Date') }}</span>
                  <strong class="summary-value">{{
                    glamorousDateFormatted
                  }}</strong>
                </div>
              </div>

              <div class="summary-item">
                <span class="summary-icon">
                  <svg
                    class="gold-icon"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <circle cx="12" cy="12" r="10"></circle>
                    <polyline points="12 6 12 12 16 14"></polyline>
                  </svg>
                </span>
                <div>
                  <span class="summary-label">{{ t('Duration') }}</span>
                  <strong class="summary-value"
                    >{{ glamorousTimeFormatted }} ({{
                      t(
                        reservationDurationHours === 1
                          ? '1 Hour'
                          : '{count} Hours',
                        { count: reservationDurationHours },
                      )
                    }})</strong
                  >
                </div>
              </div>

              <div v-if="requestedGameName" class="summary-item game-item">
                <span class="summary-icon">
                  <svg
                    class="gold-icon"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path
                      d="M8 20h8M9 20v-4h6v4M10 16l1-7h2l1 7M9 9h6M12 4a2 2 0 1 0 0-4 2 2 0 0 0 0 4z"
                    ></path>
                  </svg>
                </span>
                <div>
                  <span class="summary-label">{{ t('Playing') }}</span>
                  <strong class="summary-value">{{ requestedGameName }}</strong>
                </div>
              </div>
            </div>
          </div>

          <div class="wizard-actions">
            <RouterLink :to="{ name: 'HomeIndex' }" class="btn btn-secondary">{{
              t('Cancel')
            }}</RouterLink>
            <button
              type="button"
              class="btn btn-primary"
              :disabled="
                !startTimestamp ||
                !endTimestamp ||
                !selectedBranchId ||
                isDateTimeInvalid ||
                !auth.authenticated
              "
              @click="currentStep = 2"
            >
              <span v-if="!auth.authenticated">{{
                t('Sign in Required to Reserve')
              }}</span>
              <span v-else>{{ t('Check Available Tables →') }}</span>
            </button>
          </div>
        </section>

        <!-- STEP 2: Table -->
        <section
          v-else-if="currentStep === 2"
          key="step2"
          class="wizard-step"
          aria-labelledby="step2-heading"
        >
          <div class="step-header">
            <h2 id="step2-heading" class="step-title">
              {{ t('Choose Your Table') }}
            </h2>
            <p class="step-subtitle">
              {{
                t('Tables free at {branch} for {count} players, {when}.', {
                  branch: selectedBranch?.name ?? '',
                  count: partySize,
                  when: formattedDateTimeInterval,
                })
              }}
            </p>
          </div>

          <div
            v-if="availabilityQuery.isLoading.value"
            class="state-message"
            role="status"
          >
            {{ t('Checking table availability…') }}
          </div>

          <div
            v-else-if="availabilityQuery.isError.value"
            class="empty-tables-box"
            role="alert"
          >
            <p class="empty-title">
              {{ t('Availability could not be loaded.') }}
            </p>
            <button
              type="button"
              class="btn btn-secondary"
              @click="availabilityQuery.refetch()"
            >
              {{ t('Try again') }}
            </button>
          </div>

          <div
            v-else-if="availableTables.length === 0"
            class="empty-tables-box"
          >
            <p class="empty-title">
              {{
                availability?.withinOpeningHours === false
                  ? t('The branch is closed for part of this time.')
                  : t('No tables available for this time slot')
              }}
            </p>
            <p class="empty-sub">
              {{
                t('Try another time, a shorter visit or a different branch.')
              }}
            </p>
            <button
              type="button"
              class="btn btn-secondary"
              @click="currentStep = 1"
            >
              {{ t('← Change Time or Branch') }}
            </button>
          </div>

          <div v-else class="tables-grid" role="radiogroup">
            <label
              v-for="table in availableTables"
              :key="table.tableId"
              class="table-card"
              :class="{
                'is-selected': selectedTable?.tableId === table.tableId,
              }"
            >
              <div class="table-top">
                <div class="table-label-row">
                  <span class="table-label">{{ table.label }}</span>
                </div>
                <span class="capacity-pill">
                  {{ t('Up to {count} seats', { count: table.capacity }) }}
                </span>
              </div>
              <div class="table-rate">
                <span class="rate-num">{{
                  formatBaht(table.hourlyRate.amountMinor)
                }}</span>
                <span class="rate-unit">{{ t('/ hour') }}</span>
              </div>
              <div class="select-radio">
                <input
                  v-model="selectedTable"
                  type="radio"
                  name="selected_table"
                  :value="table"
                />
                <span>{{
                  selectedTable?.tableId === table.tableId
                    ? t('Selected')
                    : t('Select Table')
                }}</span>
              </div>
            </label>
          </div>

          <div class="contact-section">
            <div class="form-field full-width">
              <label for="cust-notes" class="form-label">{{
                t('Special requests (optional)')
              }}</label>
              <textarea
                id="cust-notes"
                v-model="specialNotes"
                rows="2"
                maxlength="1000"
                class="form-input"
              />
            </div>
          </div>

          <div class="wizard-actions">
            <button
              type="button"
              class="btn btn-secondary"
              @click="currentStep = 1"
            >
              {{ t('← Back') }}
            </button>
            <button
              type="button"
              class="btn btn-primary"
              :disabled="!selectedTable"
              @click="currentStep = 3"
            >
              {{ t('Choose Games →') }}
            </button>
          </div>
        </section>

        <!-- STEP 3: Games free for the whole visit -->
        <section
          v-else-if="currentStep === 3"
          key="step3"
          class="wizard-step"
          aria-labelledby="step3-heading"
        >
          <div class="step-header">
            <h2 id="step3-heading" class="step-title">
              {{ t('Reserve Games') }}
            </h2>
            <p class="step-subtitle">
              {{
                t(
                  'Copies free for your whole visit. Reserved copies wait at your table; you can also pick games when you arrive.',
                )
              }}
            </p>
          </div>

          <div
            v-if="
              availabilityQuery.isLoading.value ||
              branchGamesQuery.isLoading.value
            "
            class="state-message"
            role="status"
          >
            {{ t('Checking game availability…') }}
          </div>
          <div v-else-if="gameChoices.length === 0" class="state-message">
            {{ t('No games can be reserved for this time.') }}
          </div>
          <ul v-else class="games-list">
            <li
              v-for="game in gameChoices"
              :key="game.gameId"
              class="game-row"
              :class="{ 'is-selected': gameQuantity(game.gameId) > 0 }"
            >
              <div class="game-info">
                <span class="game-name">{{ game.name }}</span>
                <span class="game-meta">
                  {{
                    t('{count} free · {rate} per copy per hour', {
                      count: game.available,
                      rate: formatBaht(game.hourlyRateMinor),
                    })
                  }}
                </span>
              </div>
              <div class="game-stepper">
                <button
                  v-if="gameQuantity(game.gameId) > 0"
                  type="button"
                  class="step-btn"
                  :aria-label="t('Fewer copies of {game}', { game: game.name })"
                  @click="setGameQuantity(game, gameQuantity(game.gameId) - 1)"
                >
                  −
                </button>
                <span
                  v-if="gameQuantity(game.gameId) > 0"
                  class="qty-count"
                  aria-live="polite"
                  >{{ gameQuantity(game.gameId) }}</span
                >
                <button
                  type="button"
                  class="step-btn"
                  :disabled="gameQuantity(game.gameId) >= game.available"
                  :aria-label="t('Reserve {game}', { game: game.name })"
                  @click="setGameQuantity(game, gameQuantity(game.gameId) + 1)"
                >
                  {{ gameQuantity(game.gameId) > 0 ? '+' : t('+ Reserve') }}
                </button>
              </div>
            </li>
          </ul>

          <div class="wizard-actions">
            <button
              type="button"
              class="btn btn-secondary"
              @click="currentStep = 2"
            >
              {{ t('← Back to Table') }}
            </button>
            <button
              type="button"
              class="btn btn-primary"
              @click="currentStep = 4"
            >
              {{ t('Add Food & Drinks (-10%) →') }}
            </button>
          </div>
        </section>

        <!-- STEP 4: Food & drinks -->
        <section
          v-else-if="currentStep === 4"
          key="step4"
          class="wizard-step"
          aria-labelledby="step4-heading"
        >
          <div class="step-header">
            <h2 id="step4-heading" class="step-title">
              {{ t('Pre-Order Food & Drinks (10% Discount)') }}
            </h2>
            <p class="step-subtitle">
              {{
                t(
                  'Pre-ordered food and drinks are 10% off. Your card is authorized when you book and charged when you check in; cancelling releases it.',
                )
              }}
            </p>
          </div>

          <div v-if="!preOrderAllowed" class="state-message">
            {{
              t(
                'Food can be pre-ordered for visits within {days} days. You can order at the counter during this visit.',
                { days: PRE_ORDER_WINDOW_DAYS },
              )
            }}
          </div>
          <div
            v-else-if="menuQuery.isLoading.value"
            class="state-message"
            role="status"
          >
            {{ t('Loading the menu…') }}
          </div>
          <div
            v-else-if="menuQuery.isError.value"
            class="state-message"
            role="alert"
          >
            {{ t('The menu could not be loaded.') }}
            <button
              type="button"
              class="btn btn-secondary"
              @click="menuQuery.refetch()"
            >
              {{ t('Try again') }}
            </button>
          </div>
          <FoodMenuPicker v-else v-model="preOrderedFood" :items="menuItems" />

          <div class="wizard-actions">
            <button
              type="button"
              class="btn btn-secondary"
              @click="currentStep = 3"
            >
              {{ t('← Back to Games') }}
            </button>
            <button
              type="button"
              class="btn btn-primary"
              @click="currentStep = 5"
            >
              {{ t('Review →') }}
            </button>
          </div>
        </section>

        <!-- STEP 5: Review and book -->
        <section
          v-else-if="currentStep === 5"
          key="step5"
          class="wizard-step"
          aria-labelledby="step5-heading"
        >
          <div class="step-header">
            <h2 id="step5-heading" class="step-title">
              {{ t('Review & Confirm Reservation') }}
            </h2>
          </div>

          <div class="review-grid">
            <div class="review-card">
              <h3 class="card-heading">{{ t('Reservation Summary') }}</h3>
              <div class="review-row">
                <span class="label">{{ t('Branch') }}:</span>
                <span class="val">{{ selectedBranch?.name }}</span>
              </div>
              <div class="review-row">
                <span class="label">{{ t('Table') }}:</span>
                <span class="val"
                  >{{ selectedTable?.label }} ({{
                    t('Up to {count} seats', {
                      count: selectedTable?.capacity ?? 0,
                    })
                  }})</span
                >
              </div>
              <div class="review-row">
                <span class="label">{{ t('Reservation Date') }}:</span>
                <span class="val">{{ formattedDateTimeInterval }}</span>
              </div>
              <div class="review-row">
                <span class="label">{{ t('Party Size') }}:</span>
                <span class="val">{{
                  t(partySize === 1 ? '{count} player' : '{count} players', {
                    count: partySize,
                  })
                }}</span>
              </div>
              <div class="review-row">
                <span class="label">{{ t('Games') }}:</span>
                <span class="val">{{
                  chosenGames.length
                    ? chosenGames
                        .map((game) => `${game.quantity}× ${game.name}`)
                        .join(', ')
                    : t('Choose at the table')
                }}</span>
              </div>
              <div v-if="specialNotes.trim()" class="review-row">
                <span class="label">{{ t('Notes') }}:</span>
                <span class="val">{{ specialNotes.trim() }}</span>
              </div>

              <div v-if="hasFood" class="food-summary-block">
                <h4 class="food-summary-title">
                  {{ t('Pre-Ordered Food & Drinks (10% Discount)') }}
                </h4>
                <ul class="food-list">
                  <li
                    v-for="line in preOrderedFood"
                    :key="line.item.id"
                    class="food-item-line"
                  >
                    <span
                      >{{ line.quantity }}×
                      {{
                        locale === 'th-TH' && line.item.nameTh
                          ? line.item.nameTh
                          : line.item.name
                      }}</span
                    >
                    <span>{{
                      formatBaht(
                        Math.round(line.item.price * 100) * line.quantity,
                      )
                    }}</span>
                  </li>
                </ul>
              </div>
            </div>

            <div class="pricing-card">
              <h3 class="card-heading">{{ t('Payment Summary') }}</h3>
              <fieldset
                class="reservation-payment-methods"
                :disabled="isSubmitting"
              >
                <legend>{{ t('Payment method') }}</legend>
                <label
                  ><input
                    v-model="paymentMethod"
                    type="radio"
                    value="PROMPTPAY"
                  />
                  {{ t('PromptPay QR') }}</label
                >
                <label
                  ><input v-model="paymentMethod" type="radio" value="CARD" />
                  {{ t('Card for food; play paid at counter') }}</label
                >
              </fieldset>
              <div class="cost-row">
                <div>
                  <span>{{
                    t('Table and games for {hours}', { hours: durationLabel })
                  }}</span>
                  <span
                    v-if="paymentMethod === 'CARD'"
                    class="pay-counter-pill"
                    >{{ t('Pay at counter after session') }}</span
                  >
                </div>
                <span class="muted-val">{{
                  formatBaht(visitEstimateMinor)
                }}</span>
              </div>
              <template v-if="hasFood">
                <div class="cost-row">
                  <span>{{ t('Pre-ordered Food & Drinks') }}</span>
                  <span>{{ formatBaht(foodPreview.subtotalMinor) }}</span>
                </div>
                <div class="cost-row discount-row">
                  <span>{{ t('Pre-Order Discount (-10%)') }}</span>
                  <span class="discount-num"
                    >-{{ formatBaht(foodPreview.discountMinor) }}</span
                  >
                </div>
              </template>
              <div class="grand-total-row">
                <div>
                  <span>{{
                    paymentMethod === 'PROMPTPAY'
                      ? t('Pay now')
                      : t('On your card now')
                  }}</span>
                  <small class="due-sub-text">{{
                    paymentMethod === 'PROMPTPAY'
                      ? t(
                          'Booked hours and food paid upfront; extras at the counter',
                        )
                      : hasFood
                        ? t('Authorized now, charged at check-in')
                        : t('Table and games are paid at the counter')
                  }}</small>
                </div>
                <span class="total-amount">{{
                  formatBaht(
                    paymentMethod === 'PROMPTPAY'
                      ? upfrontMinor
                      : hasFood
                        ? foodPreview.totalMinor
                        : 0,
                  )
                }}</span>
              </div>

              <CardPaymentFields
                v-if="hasFood && paymentMethod === 'CARD'"
                ref="cardFields"
              />
              <p v-if="paymentMethod === 'PROMPTPAY'" class="pay-note">
                {{
                  t(
                    'Pay the QR within 15 minutes. Paid bookings cannot be cancelled or rescheduled; refunds are unavailable.',
                  )
                }}
              </p>

              <div v-if="submissionError" class="error-banner" role="alert">
                {{ submissionError }}
              </div>

              <button
                type="button"
                class="btn btn-primary pay-btn"
                :disabled="isSubmitting || !selectedTable"
                @click="confirmReservation"
              >
                {{
                  isSubmitting
                    ? t('Booking…')
                    : paymentMethod === 'PROMPTPAY'
                      ? t('Pay {total} with PromptPay QR', {
                          total: formatBaht(upfrontMinor),
                        })
                      : hasFood
                        ? t('Authorize {total} and Book', {
                            total: formatBaht(foodPreview.totalMinor),
                          })
                        : t('Confirm Reservation')
                }}
              </button>
              <p class="pay-note">
                {{
                  t(
                    'Prices, availability and the discount are confirmed by the branch when you book.',
                  )
                }}
              </p>
            </div>
          </div>

          <div class="wizard-actions">
            <button
              type="button"
              class="btn btn-secondary"
              :disabled="isSubmitting"
              @click="currentStep = 4"
            >
              {{ t('← Back to Food & Drinks') }}
            </button>
          </div>
        </section>

        <!-- STEP 6: Confirmation, from the server's record -->
        <section
          v-else-if="currentStep === 6 && createdReservation"
          key="step6"
          class="wizard-step receipt-step"
          aria-labelledby="receipt-heading"
        >
          <div v-if="createdReservation" class="receipt-card">
            <div class="receipt-badge" role="status">
              <span>{{
                createdReservation.status === 'CANCELLED'
                  ? t('Reservation cancelled')
                  : createdReservation.payment?.status === 'PENDING'
                    ? t('Awaiting PromptPay payment')
                    : t('Reservation Confirmed!')
              }}</span>
            </div>

            <h2 id="receipt-heading" class="receipt-title">
              {{
                createdReservation.status === 'CANCELLED'
                  ? t('Payment window closed')
                  : createdReservation.payment?.status === 'PENDING'
                    ? t('Scan to complete your reservation')
                    : t('See You at The Parlor!')
              }}
            </h2>

            <ReservationPromptPay
              :reservation="createdReservation"
              @updated="createdReservation = $event"
            />

            <div class="receipt-meta-box">
              <div class="meta-line">
                <span class="lbl">{{ t('Reservation Reference') }}:</span>
                <span class="v code">{{
                  createdReservation.id.slice(-8).toUpperCase()
                }}</span>
              </div>
              <div class="meta-line">
                <span class="lbl">{{ t('Branch') }}:</span>
                <span class="v">{{
                  directory.byId(createdReservation.branchId)?.name
                }}</span>
              </div>
              <div class="meta-line">
                <span class="lbl">{{ t('Table') }}:</span>
                <span class="v">{{ selectedTable?.label }}</span>
              </div>
              <div class="meta-line">
                <span class="lbl">{{ t('Reservation Date') }}:</span>
                <span class="v">{{ formattedDateTimeInterval }}</span>
              </div>
              <div class="meta-line">
                <span class="lbl">{{ t('Party Size') }}:</span>
                <span class="v">{{ createdReservation.partySize }}</span>
              </div>
              <div v-if="receiptGames.length" class="meta-line">
                <span class="lbl">{{ t('Games') }}:</span>
                <span class="v">{{
                  receiptGames
                    .map((game) => `${game.quantity}× ${game.name}`)
                    .join(', ')
                }}</span>
              </div>
            </div>

            <div v-if="createdReservation.preOrder" class="receipt-food-box">
              <h4 class="food-h">
                {{ t('Pre-Ordered Refreshments (10% Discount Applied)') }}
              </h4>
              <div
                v-for="line in createdReservation.preOrder.items"
                :key="line.menuItemId"
                class="receipt-food-item"
              >
                <span>{{ line.quantity }}× {{ line.name }}</span>
                <span>{{ formatBaht(line.lineTotal.amountMinor) }}</span>
              </div>
              <div class="receipt-food-item">
                <span>{{ t('Pre-Order Discount (-10%)') }}</span>
                <span
                  >-{{
                    formatBaht(createdReservation.preOrder.discount.amountMinor)
                  }}</span
                >
              </div>
              <div class="receipt-food-item receipt-food-total">
                <span
                  >{{ t('Total') }}
                  <small v-if="createdReservation.preOrder.payment.cardLast4"
                    >({{ createdReservation.preOrder.payment.cardBrand }} ••••
                    {{ createdReservation.preOrder.payment.cardLast4 }})</small
                  ></span
                >
                <span>{{
                  formatBaht(createdReservation.preOrder.total.amountMinor)
                }}</span>
              </div>
              <p class="pay-note">{{ receiptPaymentNote }}</p>
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
                @click="startOver"
              >
                + {{ t('Reserve a Table Now') }}
              </button>
              <RouterLink :to="{ name: 'HomeIndex' }" class="btn btn-secondary">
                {{ t('Return to Catalog') }}
              </RouterLink>
            </div>
          </div>
        </section>
      </Transition>
    </div>
  </div>
</template>

<style scoped>
.reservation-payment-methods {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 0 0 20px;
  padding: 12px;
  border: 1px solid #c9bda7;
}
.reservation-payment-methods label {
  display: flex;
  gap: 8px;
  align-items: center;
}
/* Shared selects and steppers match this page's cream-bordered fields. */
.reservation-page {
  --ui-brand: #203c30;
  --ui-focus: #203c30;
  --ui-stroke: #c9bda7;
  --ui-stroke-strong: #203c30;
  --ui-surface-hover: #fffaf1;
  --ui-selected: #f3ead7;
  --ui-radius: 6px;
  --ui-control-height: 42px;
}

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
  flex-shrink: 0;
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

/* Trim font leading on labels, while flex centers their visible text boxes. */
.step-num > span,
.step-text,
.member-text,
.member-name,
.member-badge-perk,
.parlor-crest > span,
.party-badge > span {
  text-box: trim-both cap alphabetic;
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

.form-input {
  padding: 10px 14px;
  border: 1px solid #c9bda7;
  border-radius: 6px;
  background: #ffffff;
  color: #1e2e25;
  font-size: 14px;
  outline: none;
}

.form-input:focus {
  border-color: #203c30;
  box-shadow: 0 0 0 2px rgba(32, 60, 48, 0.15);
}

.phone-input-group {
  display: flex;
  gap: 8px;
}

.country-code-select {
  padding: 10px 12px;
  border: 1px solid #c9bda7;
  border-radius: 6px;
  background: #fffaf1;
  color: #203c30;
  font-size: 13px;
  font-weight: 600;
  max-width: 220px;
}

.phone-number-field {
  flex: 1;
}

.field-hint {
  font-size: 11px;
  color: #6a6252;
}

/* Reservation summary with a static gold border. */
.glamorous-summary-card {
  position: relative;
  background: #142b20;
  border-radius: 14px;
  padding: 22px 24px;
  margin-bottom: 24px;
  color: #fffaf0;
  box-shadow:
    0 8px 30px rgba(0, 0, 0, 0.45),
    0 0 20px rgba(198, 173, 122, 0.2);
  overflow: hidden;
  z-index: 1;
}

/* Base static gold outline */
.glamorous-summary-border-base {
  position: absolute;
  inset: 0;
  border-radius: 14px;
  border: 1.5px solid rgba(198, 173, 122, 0.4);
  pointer-events: none;
  z-index: 0;
}

/* Keep the emerald background inside the static border. */
.glamorous-summary-card::after {
  content: '';
  position: absolute;
  inset: 2px;
  background: radial-gradient(
    ellipse at 50% 25%,
    #1d3e2f 0%,
    #142b20 70%,
    #0d1e16 100%
  );
  border-radius: 12px;
  z-index: -1;
  pointer-events: none;
  box-shadow: inset 0 0 16px rgba(10, 22, 16, 0.7);
}

.glamorous-summary-card > *:not(.glamorous-summary-border-base) {
  position: relative;
  z-index: 1;
}

.summary-card-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: space-between;
  border-bottom: 1px solid rgba(198, 173, 122, 0.3);
  padding-bottom: 12px;
  margin-bottom: 16px;
}

.parlor-crest {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  font-family: 'Plus Jakarta Sans', Arial, sans-serif;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #ddc18c;
}

.party-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  white-space: nowrap;
  background: rgba(198, 173, 122, 0.2);
  border: 1px solid #c6ad7a;
  color: #fff3dc;
  font-size: 11px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 12px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.summary-card-body {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.summary-icon {
  display: flex;
  flex-shrink: 0;
  font-size: 18px;
  line-height: 1.2;
}

.summary-label {
  display: block;
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #baa785;
  margin-bottom: 3px;
}

.summary-value {
  display: block;
  font-size: 14.5px;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.4;
}

.game-item {
  grid-column: 1 / -1;
  background: rgba(198, 173, 122, 0.12);
  border: 1px dashed rgba(198, 173, 122, 0.5);
  border-radius: 6px;
  padding: 8px 12px;
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

.table-subtotal {
  font-size: 12.5px;
  color: #555044;
  font-weight: 500;
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

.table-card.is-selected .table-subtotal {
  color: #ddc18c !important;
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

.table-card.is-selected .gold-icon {
  color: #ffd98a !important;
  filter: drop-shadow(0 0 6px rgba(255, 217, 138, 0.6));
}

.phone-error-hint {
  font-size: 11.5px;
  color: #c0392b;
  margin-top: 4px;
  display: block;
  font-weight: 600;
}

.form-input.phone-number-field.is-invalid {
  border-color: #c0392b;
  background: #fff8f8;
}

.contact-section {
  border-top: 1px solid #eee4d1;
  padding-top: 20px;
  margin-top: 10px;
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

.food-summary-block {
  margin-top: 14px;
  border-top: 1px dashed #d5c8b2;
  padding-top: 12px;
}

.food-summary-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 700;
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

.inplay-callout {
  background: #eef6f1;
  border: 1px solid #b7dcce;
  border-radius: 6px;
  padding: 12px 14px;
  margin-top: 16px;
  display: flex;
  gap: 10px;
}

.callout-icon {
  font-size: 16px;
}

.callout-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: #1c4533;
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

.receipt-sub {
  margin: 0 0 24px;
  font-size: 14px;
  color: #60594a;
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
  align-items: stretch;
  flex-wrap: wrap;
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
  gap: 8px;
  min-height: 44px;
  line-height: 1.5;
  text-align: center;
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
.gold-icon {
  width: 18px;
  height: 18px;
  color: #ddc18c;
  display: inline-block;
  vertical-align: middle;
  flex-shrink: 0;
  filter: drop-shadow(0 0 3px rgba(198, 173, 122, 0.35));
}
.gold-icon.mini-crest {
  width: 14px;
  height: 14px;
  margin: 0 4px;
}
.gold-icon.micro {
  width: 13px;
  height: 13px;
  margin-right: 3px;
}
.gold-icon.table-icon {
  width: 22px;
  height: 22px;
  color: #ddc18c;
}
.gold-icon.warning-icon {
  width: 20px;
  height: 20px;
  color: #e5b869;
}
.gold-icon.empty-icon {
  width: 32px;
  height: 32px;
  margin-bottom: 8px;
  color: #c6ad7a;
}
.gold-icon.check-icon {
  width: 20px;
  height: 20px;
  color: #2ecc71;
  filter: drop-shadow(0 0 6px rgba(46, 204, 113, 0.4));
}

.table-label-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

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
  background: radial-gradient(
    ellipse at 50% 25%,
    #1d3e2f 0%,
    #142b20 70%,
    #0d1e16 100%
  );
  border: 1px solid #c6ad7a;
  border-radius: 8px;
  padding: 10px 16px;
  margin-bottom: 20px;
  gap: 12px;
  box-shadow:
    inset 0 0 16px rgba(10, 22, 16, 0.7),
    0 4px 14px rgba(20, 43, 32, 0.15);
  flex-wrap: wrap;
}

.member-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  min-width: 0;
  gap: 8px;
  font-size: 13.5px;
  color: #fffaf0;
}

.member-text {
  color: #ddc18c;
}

.member-name {
  overflow-wrap: anywhere;
  color: #ffffff;
  font-weight: 800;
}

.member-badge-perk {
  display: block;
  line-height: 1.5;
  padding-block: 7px;
  font-size: 11px;
  font-weight: 800;
  background: rgba(198, 173, 122, 0.2);
  border: 1px solid #ffd98a;
  color: #ffd98a;
  padding-inline: 10px;
  border-radius: 12px;
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

.auth-gate-icon {
  font-size: 20px;
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

.pay-counter-pill {
  display: block;
  font-size: 10.5px;
  color: #8c7649;
  font-weight: 700;
  margin-top: 2px;
}
.due-sub-text {
  display: block;
  font-size: 10.5px;
  color: #8c7649;
  margin-top: 2px;
}
.muted-val {
  color: #7a705e;
  font-weight: 700;
}

.games-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.game-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #d8cfbf;
  border-radius: 6px;
  background: #fffdf8;
  transition: border-color 0.15s ease;
}

.game-row.is-selected {
  border-color: #203c30;
}

.game-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.game-name {
  font-weight: 700;
  color: #1f1a14;
}

.game-meta {
  font-size: 13px;
  color: #5b5145;
}

.game-stepper {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.game-stepper .step-btn {
  min-width: 36px;
  min-height: 36px;
  padding: 0 10px;
  border: 1px solid #203c30;
  border-radius: 4px;
  background: #fff;
  color: #203c30;
  font-weight: 700;
  cursor: pointer;
}

.game-stepper .step-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.receipt-food-total {
  font-weight: 700;
}

@media (prefers-reduced-motion: reduce) {
  .game-row {
    transition: none;
  }
}
</style>

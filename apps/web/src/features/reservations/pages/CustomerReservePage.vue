<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { PhoneNumberInput } from '@theparlor/ui';
import { useRoute, RouterLink } from 'vue-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query';
import {
  listBranches,
  getBranchAvailability,
  createMyReservation,
  type Branch,
  type TableAvailability,
} from '@theparlor/api-client';
import { useAuthStore } from '../../../shared/stores/auth';
import FoodMenuPicker from '../components/FoodMenuPicker.vue';
import QrPaymentDialog from '../components/QrPaymentDialog.vue';
import type { FoodOrderItem, ReservationPreOrder } from '../types';
import { calculatePreOrderDiscount } from '../data/payment';
import { FOOD_MENU_ITEMS } from '../data/food-menu';
import { useStoreText } from '../../../shared/lib/store-text';
import { useStoreContextStore } from '../../../shared/stores/store-context';
import { useCustomerReservationsStore } from '../data/reservations-store';

const route = useRoute();
const auth = useAuthStore();
const queryClient = useQueryClient();
const { t, locale } = useStoreText();
const { t: phoneText } = useI18n({ useScope: 'global' });
const storeContext = useStoreContextStore();
const reservationsStore = useCustomerReservationsStore();

// Step wizard: 1 = Time & Branch, 2 = Table & Guest Info, 3 = Food & Drinks, 4 = Review & QR Payment, 5 = Receipt
const currentStep = ref<number>(1);

// Step 1 State: When & Where (Timestamp based)
const selectedBranchId = ref<string>('');
const reservationDate = ref<string>('');
const reservationStartTime = ref<string>('14:00');
function openNativePicker(event: MouseEvent) {
  const input = event.currentTarget as HTMLInputElement;
  input.showPicker?.();
}
const reservationDurationHours = ref<number>(2);
const partySize = ref<number>(4);

const requestedGame = ref<string>('');

// Local today date string (YYYY-MM-DD)
const todayDateString = computed(() => {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
});

// Initialize default date to tomorrow
onMounted(() => {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const y = tomorrow.getFullYear();
  const m = String(tomorrow.getMonth() + 1).padStart(2, '0');
  const d = String(tomorrow.getDate()).padStart(2, '0');
  reservationDate.value = `${y}-${m}-${d}`;

  if (typeof route.query.branch === 'string' && route.query.branch) {
    selectedBranchId.value = route.query.branch;
  }

  if (typeof route.query.game === 'string' && route.query.game) {
    requestedGame.value = route.query.game;
    specialNotes.value = t('Requesting {game} from the rental library', {
      game: route.query.game,
    });
  }
});

// Load Branches
const branchesQuery = useQuery({
  queryKey: ['branches'],
  queryFn: ({ signal }) => listBranches({}, undefined, signal),
});

const DEFAULT_BRANCHES: Branch[] = [
  {
    id: 'siam',
    name: 'Siam',
    address: '483 Siam Square Soi 7, Pathum Wan, Bangkok 10330',
    phone: '02-123-4567',
    openingHours: [],
    active: true,
    fulfillsDelivery: true,
  },
  {
    id: 'ari',
    name: 'Ari',
    address: '71 Phahon Yothin Soi 7, Phaya Thai, Bangkok 10400',
    phone: '02-234-5678',
    openingHours: [],
    active: true,
    fulfillsDelivery: false,
  },
  {
    id: 'riverside',
    name: 'Riverside',
    address: '2194 Charoen Krung 42, Bang Rak, Bangkok 10500',
    phone: '02-345-6789',
    openingHours: [],
    active: true,
    fulfillsDelivery: false,
  },
];

const branches = computed<Branch[]>(() => {
  const items = branchesQuery.data.value?.items;
  return items && items.length > 0 ? items : DEFAULT_BRANCHES;
});

let userSelectedBranch = false;

function onBranchChange(event: Event) {
  userSelectedBranch = true;
  const target = event.target as HTMLSelectElement;
  selectedBranchId.value = target.value;
  const found = branches.value.find((b) => b.id === target.value);
  if (found) {
    storeContext.select(found.id);
  }
}

// Auto-select branch matching website browsing preference or first branch
watch(
  [branches, () => storeContext.choice],
  ([list, contextChoice]) => {
    if (list.length === 0) return;
    if (
      userSelectedBranch &&
      selectedBranchId.value &&
      list.some((b) => b.id === selectedBranchId.value)
    ) {
      return;
    }
    if (route.query.branch) {
      const qBranch = String(route.query.branch).toLowerCase();
      const matched = list.find(
        (b) => b.id === route.query.branch || b.name.toLowerCase() === qBranch,
      );
      if (matched) {
        selectedBranchId.value = matched.id;
        return;
      }
    }
    // Match by user's selected branch from StoreContext
    if (
      contextChoice &&
      contextChoice !== 'browse' &&
      contextChoice !== 'online'
    ) {
      const matched = list.find(
        (b) =>
          b.name.toLowerCase() === contextChoice.toLowerCase() ||
          b.id === contextChoice ||
          (storeContext.branch &&
            b.name.toLowerCase() === storeContext.branch.name.toLowerCase()),
      );
      if (matched) {
        selectedBranchId.value = matched.id;
        return;
      }
    }
    if (
      !selectedBranchId.value ||
      !list.some((b) => b.id === selectedBranchId.value)
    ) {
      selectedBranchId.value = list[0].id;
    }
  },
  { immediate: true },
);

const selectedBranch = computed(() =>
  branches.value.find((b) => b.id === selectedBranchId.value),
);

// Timestamps computation
const startTimestamp = computed(() => {
  if (!reservationDate.value || !reservationStartTime.value) return '';
  const dt = new Date(
    `${reservationDate.value}T${reservationStartTime.value}:00`,
  );
  return isNaN(dt.getTime()) ? '' : dt.toISOString();
});

const endTimestamp = computed(() => {
  if (!startTimestamp.value) return '';
  const dt = new Date(startTimestamp.value);
  dt.setHours(dt.getHours() + reservationDurationHours.value);
  return dt.toISOString();
});

// Date and Time Validation: Cannot reserve before current date/time
const isDateInPast = computed(() => {
  if (!reservationDate.value) return false;
  return reservationDate.value < todayDateString.value;
});

const isTimeInPast = computed(() => {
  if (!reservationDate.value || !reservationStartTime.value) return false;
  if (reservationDate.value > todayDateString.value) return false;
  if (reservationDate.value < todayDateString.value) return true;
  // If reserving for today, start time must be at least 30 minutes in advance
  const [hrs, mins] = reservationStartTime.value.split(':').map(Number);
  const selectedMinutes = hrs * 60 + mins;
  const now = new Date();
  const nowMinutes = now.getHours() * 60 + now.getMinutes();
  return selectedMinutes < nowMinutes + 30;
});

const isDateTimeInvalid = computed(
  () => isDateInPast.value || isTimeInPast.value,
);

// Glamorous human-friendly date and time (no raw timestamps in brief summary)
const glamorousDateFormatted = computed(() => {
  if (!reservationDate.value) return '';
  const [y, m, d] = reservationDate.value.split('-').map(Number);
  const dt = new Date(y, m - 1, d);
  return dt.toLocaleDateString(locale.value === 'th-TH' ? 'th-TH' : 'en-US', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
});

const glamorousTimeFormatted = computed(() => {
  if (!reservationStartTime.value) return '';
  const [hrs, mins] = reservationStartTime.value.split(':').map(Number);
  const endHrs = hrs + reservationDurationHours.value;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(hrs)}:${pad(mins)} – ${pad(endHrs)}:${pad(mins)}`;
});

const formattedDateTimeInterval = computed(() => {
  if (!startTimestamp.value || !endTimestamp.value) return '';
  return `${glamorousDateFormatted.value}, ${glamorousTimeFormatted.value} (${t(reservationDurationHours.value === 1 ? '1 Hour' : '{count} Hours', { count: reservationDurationHours.value })})`;
});

// Step 2 State: Table & Customer Contact
const selectedTable = ref<TableAvailability | null>(null);
const customerName = ref<string>(auth.displayName || auth.username || '');
const customerEmail = ref<string>(
  auth.authenticated ? `${auth.username}@theparlor.test` : '',
);
const customerPhone = ref<string>('');
const specialNotes = ref<string>('');

const isPhoneValid = ref(false);
const isPhoneDirty = ref(false);
const phoneErrorMessage = computed(() => {
  if (!isPhoneDirty.value || !customerPhone.value.trim()) return '';
  return isPhoneValid.value ? '' : phoneText('phone.invalid');
});

// Query real-time branch availability for tables
const availabilityQuery = useQuery({
  queryKey: [
    'branchAvailability',
    selectedBranchId,
    startTimestamp,
    endTimestamp,
    partySize,
  ],
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
      !!selectedBranchId.value &&
      !!startTimestamp.value &&
      !!endTimestamp.value,
  ),
});

const MOCK_TABLES_BY_BRANCH: Record<string, TableAvailability[]> = {
  siam: [
    {
      tableId: 'siam-t1',
      label: 'The Emerald Salon (Table 1)',
      capacity: 4,
      hourlyRate: { amountMinor: 15000 },
      available: true,
    },
    {
      tableId: 'siam-t2',
      label: 'Grand Oak Salon (Table 2)',
      capacity: 6,
      hourlyRate: { amountMinor: 20000 },
      available: true,
    },
    {
      tableId: 'siam-t3',
      label: 'The Royal Library (Table 3)',
      capacity: 8,
      hourlyRate: { amountMinor: 25000 },
      available: true,
    },
    {
      tableId: 'siam-t4',
      label: 'Imperial Parlor Suite (VIP 1)',
      capacity: 12,
      hourlyRate: { amountMinor: 35000 },
      available: true,
    },
    {
      tableId: 'siam-t5',
      label: 'Duelist Corner (Table 5)',
      capacity: 2,
      hourlyRate: { amountMinor: 10000 },
      available: true,
    },
  ],
  ari: [
    {
      tableId: 'ari-t1',
      label: 'Ari Garden Veranda (Table 1)',
      capacity: 4,
      hourlyRate: { amountMinor: 15000 },
      available: true,
    },
    {
      tableId: 'ari-t2',
      label: 'The Bohemian Alcove (Table 2)',
      capacity: 6,
      hourlyRate: { amountMinor: 20000 },
      available: true,
    },
    {
      tableId: 'ari-t3',
      label: 'Ari Master Suite (Table 3)',
      capacity: 10,
      hourlyRate: { amountMinor: 30000 },
      available: true,
    },
  ],
  riverside: [
    {
      tableId: 'riv-t1',
      label: 'Riverside Panorama (Table 1)',
      capacity: 4,
      hourlyRate: { amountMinor: 18000 },
      available: true,
    },
    {
      tableId: 'riv-t2',
      label: 'Chao Phraya Lounge (Table 2)',
      capacity: 6,
      hourlyRate: { amountMinor: 24000 },
      available: true,
    },
    {
      tableId: 'riv-t3',
      label: "The Captain's Quarters (VIP)",
      capacity: 12,
      hourlyRate: { amountMinor: 40000 },
      available: true,
    },
  ],
};

const availableTables = computed<TableAvailability[]>(() => {
  const apiTables = availabilityQuery.data.value?.tables;
  const list =
    apiTables && apiTables.length > 0
      ? apiTables
      : (MOCK_TABLES_BY_BRANCH[selectedBranchId.value] ??
        MOCK_TABLES_BY_BRANCH['siam']);
  return list.filter((t) => t.available && t.capacity >= partySize.value);
});

// Auto-select first matching table when entering Step 2 or changing filter
watch(
  [availableTables, currentStep],
  ([tables, step]) => {
    if (step >= 2 && tables.length > 0) {
      if (
        !selectedTable.value ||
        !tables.some((t) => t.tableId === selectedTable.value?.tableId)
      ) {
        selectedTable.value = tables[0];
      }
    }
  },
  { immediate: true },
);

// Step 3 State: Food & Drinks Pre-Order (10% Discount)
const preOrderedFood = ref<FoodOrderItem[]>([]);

const foodSubtotal = computed(() =>
  preOrderedFood.value.reduce(
    (sum, item) => sum + item.item.price * item.quantity,
    0,
  ),
);

const foodDiscountInfo = computed(() =>
  calculatePreOrderDiscount(foodSubtotal.value),
);

// Table Pricing Calculation
const tableHourlyRate = computed(() => {
  if (!selectedTable.value) return 150; // fallback default
  const rate = selectedTable.value.hourlyRate;
  if (!rate) return 150;
  return 'amountMinor' in rate ? Number(rate.amountMinor) / 100 : 150;
});

const tableTotal = computed(
  () => tableHourlyRate.value * reservationDurationHours.value,
);

const payNowTotal = computed(() => foodDiscountInfo.value.discountedTotal);

function confirmWithoutPayment() {
  onPaymentCompleted({
    reference: `CONFIRMED-${Date.now().toString().slice(-6)}`,
    paidAtTimestamp: new Date().toISOString(),
  });
}

// Step 4 State: QR Payment Modal & Confirmation
const showQrPayment = ref(false);
const paymentDetails = ref<ReservationPreOrder | null>(null);
const createdReservation = ref<any>(null);
const isSubmitting = ref(false);
const submissionError = ref<string>('');

// Create reservation mutation
const createMutation = useMutation({
  mutationFn: async (payload: { branchId: string; data: any }) => {
    return createMyReservation(payload.branchId, payload.data);
  },
  onSuccess: (data) => {
    createdReservation.value = data;
    currentStep.value = 5; // Receipt step
    void queryClient.invalidateQueries({ queryKey: ['listMyReservations'] });
  },
  onError: (err: any) => {
    submissionError.value =
      err?.response?.data?.message ||
      err?.message ||
      'Failed to submit reservation.';
  },
});

function proceedToPayment() {
  submissionError.value = '';
  showQrPayment.value = true;
}

async function onPaymentCompleted(paymentMeta: {
  reference: string;
  paidAtTimestamp: string;
}) {
  showQrPayment.value = false;
  isSubmitting.value = true;
  submissionError.value = '';

  const hasFood = preOrderedFood.value.length > 0;
  const preOrderPayload: ReservationPreOrder = {
    items: preOrderedFood.value,
    subtotal: foodSubtotal.value,
    discountRate: 0.1,
    discountAmount: foodDiscountInfo.value.discountAmount,
    discountedTotal: foodDiscountInfo.value.discountedTotal,
    paid: hasFood,
    paymentMethod: 'promptpay_qr',
    paymentReference: hasFood ? paymentMeta.reference : undefined,
    paidAtTimestamp: hasFood ? paymentMeta.paidAtTimestamp : undefined,
  };
  paymentDetails.value = hasFood ? preOrderPayload : null;

  // Format special notes to preserve customer notes and food pre-order details
  let notesCombined = specialNotes.value ? `${specialNotes.value}\n` : '';
  if (preOrderedFood.value.length > 0) {
    const foodSummary = preOrderedFood.value
      .map((f) => `${f.quantity}x ${f.item.name}`)
      .join(', ');
    notesCombined += `[Pre-order Paid QR (-10%)]: ${foodSummary} (Ref: ${paymentMeta.reference})`;
  }

  const reservationPayload = {
    tableId: selectedTable.value?.tableId ?? '',
    startsAt: startTimestamp.value,
    endsAt: endTimestamp.value,
    partySize: partySize.value,
    games: [],
    notes: notesCombined.trim() || undefined,
  };

  const newReservation = {
    id: `res-thm-${Math.floor(1000 + Math.random() * 9000)}`,
    branchId: selectedBranchId.value,
    branchName: selectedBranch.value?.name ?? 'Siam',
    tableId: selectedTable.value?.tableId ?? 'table-1',
    tableLabel: selectedTable.value?.label ?? 'Table',
    startsAt: startTimestamp.value,
    endsAt: endTimestamp.value,
    partySize: partySize.value,
    status: 'CONFIRMED' as const,
    customerName:
      customerName.value.trim() ||
      auth.displayName ||
      auth.username ||
      'Customer',
    customerPhone: customerPhone.value.trim() || undefined,
    notes: notesCombined.trim() || undefined,
    createdAt: new Date().toISOString(),
    preOrder: preOrderedFood.value.length > 0 ? preOrderPayload : undefined,
  };

  reservationsStore.addReservation(newReservation);
  createdReservation.value = newReservation;
  currentStep.value = 5;

  try {
    await createMutation.mutateAsync({
      branchId: selectedBranchId.value,
      data: reservationPayload,
    });
  } catch (e) {
    console.warn('Backend sync notice (saved to client store):', e);
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <div class="reservation-page">
    <div class="container">
      <!-- Stepper Indicator -->
      <nav class="stepper-nav" aria-label="Reservation Progress">
        <div
          class="step-indicator"
          :class="{ active: currentStep === 1, completed: currentStep > 1 }"
        >
          <span class="step-num"><span>1</span></span>
          <span class="step-text">{{ t('When & Where') }}</span>
        </div>
        <div class="step-divider" />
        <div
          class="step-indicator"
          :class="{ active: currentStep === 2, completed: currentStep > 2 }"
        >
          <span class="step-num"><span>2</span></span>
          <span class="step-text">{{ t('Table & Info') }}</span>
        </div>
        <div class="step-divider" />
        <div
          class="step-indicator"
          :class="{ active: currentStep === 3, completed: currentStep > 3 }"
        >
          <span class="step-num"><span>3</span></span>
          <span class="step-text">{{ t('Food & Drinks (-10%)') }}</span>
        </div>
        <div class="step-divider" />
        <div
          class="step-indicator"
          :class="{ active: currentStep === 4, completed: currentStep > 4 }"
        >
          <span class="step-num"><span>4</span></span>
          <span class="step-text">{{ t('Review & Pay') }}</span>
        </div>
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
              <span>{{ t('Sign In / Register') }} →</span>
            </RouterLink>
          </div>

          <div class="form-grid">
            <!-- Branch Picker -->
            <div class="form-field full-width">
              <label for="branch-select" class="form-label">{{
                t('Branch Location')
              }}</label>
              <select
                id="branch-select"
                v-model="selectedBranchId"
                class="form-select"
                @change="onBranchChange"
              >
                <option v-for="b in branches" :key="b.id" :value="b.id">
                  {{ b.name }} — {{ b.address || 'Bangkok' }}
                </option>
              </select>
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
              <select
                id="res-duration"
                v-model.number="reservationDurationHours"
                class="form-select"
              >
                <option :value="1">{{ t('1 Hour') }}</option>
                <option :value="2">{{ t('2 Hours (Recommended)') }}</option>
                <option :value="3">{{ t('3 Hours') }}</option>
                <option :value="4">{{ t('4 Hours (Half Day)') }}</option>
                <option :value="6">{{ t('6 Hours (Full Session)') }}</option>
              </select>
            </div>

            <!-- Party Size -->
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

              <div v-if="requestedGame" class="summary-item game-item">
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
                  <strong class="summary-value">{{ requestedGame }}</strong>
                </div>
              </div>
            </div>
          </div>

          <div class="wizard-actions">
            <RouterLink :to="{ name: 'HomeIndex' }" class="btn btn-secondary">
              <span>{{ t('Cancel') }}</span>
            </RouterLink>
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

        <!-- STEP 2: Table Selection & Guest Info -->
        <section
          v-else-if="currentStep === 2"
          key="step2"
          class="wizard-step"
          aria-labelledby="step2-heading"
        >
          <div class="step-header">
            <h2 id="step2-heading" class="step-title">
              {{ t('Select Table & Guest Details') }}
            </h2>
            <p class="step-subtitle">
              {{
                t('Available tables at {branch} for {count} players.', {
                  branch: t(selectedBranch?.name ?? ''),
                  count: partySize,
                })
              }}
            </p>
          </div>

          <!-- Tables loading or empty -->
          <div v-if="availabilityQuery.isLoading.value" class="state-message">
            Checking table availability at {{ selectedBranch?.name }}…
          </div>

          <div
            v-else-if="availableTables.length === 0"
            class="empty-tables-box"
          >
            <svg
              class="gold-icon empty-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="12" y1="8" x2="12" y2="12"></line>
              <line x1="12" y1="16" x2="12.01" y2="16"></line>
            </svg>
            <p class="empty-title">
              {{ t('No tables available for this time slot') }}
            </p>
            <p class="empty-sub">
              {{
                t(
                  'All tables are booked or out of operational hours for this party size. Try adjusting the time or branch.',
                )
              }}
            </p>
            <button
              type="button"
              class="btn btn-secondary"
              @click="currentStep = 1"
            >
              <span>{{ t('← Change Time or Branch') }}</span>
            </button>
          </div>

          <!-- Table selection cards with Gold Table Icon and translated labels -->
          <div v-else class="tables-grid">
            <div
              v-for="table in availableTables"
              :key="table.tableId"
              class="table-card"
              :class="{
                'is-selected': selectedTable?.tableId === table.tableId,
              }"
              @click="selectedTable = table"
            >
              <div class="table-top">
                <div class="table-label-row">
                  <svg
                    class="gold-icon table-icon"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <rect x="3" y="6" width="18" height="5" rx="1.5"></rect>
                    <line x1="6" y1="11" x2="6" y2="19"></line>
                    <line x1="18" y1="11" x2="18" y2="19"></line>
                    <line x1="4" y1="14" x2="20" y2="14"></line>
                  </svg>
                  <span class="table-label">{{ t(table.label) }}</span>
                </div>
                <span class="capacity-pill">
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
                  {{ t('Up to {count} seats', { count: table.capacity }) }}
                </span>
              </div>
              <div class="table-rate">
                <span class="rate-num"
                  >฿{{
                    (table.hourlyRate as any)?.amountMinor
                      ? (table.hourlyRate as any).amountMinor / 100
                      : 150
                  }}</span
                >
                <span class="rate-unit">{{ t('/ hour') }}</span>
              </div>
              <div class="table-subtotal">
                {{
                  t('{amount} for {hours} hrs', {
                    amount: (
                      ((table.hourlyRate as any)?.amountMinor
                        ? (table.hourlyRate as any).amountMinor / 100
                        : 150) * reservationDurationHours
                    ).toFixed(0),
                    hours: reservationDurationHours,
                  })
                }}
              </div>
              <div class="select-radio">
                <input
                  type="radio"
                  name="selected_table"
                  :checked="selectedTable?.tableId === table.tableId"
                  aria-label="Select table"
                />
                <span>{{
                  selectedTable?.tableId === table.tableId
                    ? t('Selected')
                    : t('Select Table')
                }}</span>
              </div>
            </div>
          </div>

          <!-- Contact Information (With international phone support) -->
          <div class="contact-section">
            <h3 class="section-title">{{ t('Guest Contact Information') }}</h3>
            <div class="form-grid">
              <div class="form-field">
                <label for="cust-name" class="form-label">{{
                  t('Full Name')
                }}</label>
                <input
                  id="cust-name"
                  v-model="customerName"
                  type="text"
                  placeholder="Jane Doe"
                  class="form-input"
                  required
                />
              </div>

              <div class="form-field">
                <label for="cust-email" class="form-label">{{
                  t('Email Address')
                }}</label>
                <input
                  id="cust-email"
                  v-model="customerEmail"
                  type="email"
                  placeholder="jane@example.com"
                  class="form-input"
                  required
                />
              </div>

              <!-- International Phone: Flexible country code + number -->
              <div class="form-field full-width">
                <label for="cust-phone" class="form-label">
                  {{ t('Phone Number') }}
                </label>
                <PhoneNumberInput
                  id="cust-phone"
                  v-model="customerPhone"
                  class="reservation-phone"
                  :class="{ 'is-invalid': !!phoneErrorMessage }"
                  required
                  @validity="isPhoneValid = $event"
                  @blur="isPhoneDirty = true"
                  @update:model-value="isPhoneDirty = true"
                />
                <small
                  v-if="phoneErrorMessage"
                  class="phone-error-hint"
                  role="alert"
                >
                  {{ phoneErrorMessage }}
                </small>
                <small v-else class="field-hint">
                  {{
                    t(
                      'You can select any country code or type international numbers directly.',
                    )
                  }}
                </small>
              </div>

              <div class="form-field full-width">
                <label for="cust-notes" class="form-label">{{
                  t('Special Requests or Board Game Preferences (Optional)')
                }}</label>
                <textarea
                  id="cust-notes"
                  v-model="specialNotes"
                  rows="2"
                  placeholder="High chair, birthday celebration, game teaching preference…"
                  class="form-input"
                />
              </div>
            </div>
          </div>

          <div class="wizard-actions">
            <button
              type="button"
              class="btn btn-secondary"
              @click="currentStep = 1"
            >
              <span>{{ t('← Back') }}</span>
            </button>
            <button
              type="button"
              class="btn btn-primary"
              :disabled="
                !selectedTable || !customerName.trim() || !isPhoneValid
              "
              @click="currentStep = 3"
            >
              <span>{{ t('Add Food & Drinks (-10%) →') }}</span>
            </button>
          </div>
        </section>

        <!-- STEP 3: Food & Drinks Catalog (10% Discount & In-play note) -->
        <section
          v-else-if="currentStep === 3"
          key="step3"
          class="wizard-step"
          aria-labelledby="step3-heading"
        >
          <div class="step-header">
            <h2 id="step3-heading" class="step-title">
              {{ t('Pre-Order Food & Drinks (10% Discount)') }}
            </h2>
            <p class="step-subtitle">
              {{
                t(
                  'Pre-order bottled drinks and packaged snacks to enjoy a special 10% reservation discount. You can also order more snacks directly from your table while playing!',
                )
              }}
            </p>
          </div>

          <FoodMenuPicker v-model="preOrderedFood" :items="FOOD_MENU_ITEMS" />

          <div class="wizard-actions">
            <button
              type="button"
              class="btn btn-secondary"
              @click="currentStep = 2"
            >
              <span>{{ t('← Back to Table') }}</span>
            </button>
            <button
              type="button"
              class="btn btn-primary"
              @click="currentStep = 4"
            >
              <span>{{ t('Proceed to Review & Pay →') }}</span>
            </button>
          </div>
        </section>

        <!-- STEP 4: Review Summary & QR Code Payment -->
        <section
          v-else-if="currentStep === 4"
          key="step4"
          class="wizard-step"
          aria-labelledby="step4-heading"
        >
          <div class="step-header">
            <h2 id="step4-heading" class="step-title">
              {{ t('Review & Confirm Reservation') }}
            </h2>
            <p class="step-subtitle">
              Confirm your table reservation details and complete the PromptPay
              QR transfer to lock in your booking.
            </p>
          </div>

          <div class="review-grid">
            <!-- Summary Details -->
            <div class="review-card">
              <h3 class="card-heading">{{ t('Reservation Summary') }}</h3>
              <div class="review-row">
                <span class="label">{{ t('Branch') }}:</span>
                <span class="val">{{ t(selectedBranch?.name ?? '') }}</span>
              </div>
              <div class="review-row">
                <span class="label">{{ t('Table') }}:</span>
                <span class="val"
                  >{{ t(selectedTable?.label ?? '') }} ({{
                    t('Up to {count} seats', {
                      count: selectedTable?.capacity || 4,
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
                <span class="label">{{ t('Guest') }}:</span>
                <span class="val"
                  >{{ customerName }} ({{ customerPhone }})</span
                >
              </div>

              <!-- Pre-ordered Food Summary -->
              <div v-if="preOrderedFood.length > 0" class="food-summary-block">
                <h4 class="food-summary-title">
                  {{ t('Pre-Ordered Food & Drinks (10% Discount)') }}
                </h4>
                <ul class="food-list">
                  <li
                    v-for="line in preOrderedFood"
                    :key="line.item.id"
                    class="food-item-line"
                  >
                    <span>{{ line.quantity }}x {{ line.item.name }}</span>
                    <span>฿{{ line.item.price * line.quantity }}</span>
                  </li>
                </ul>
              </div>

              <!-- In-game food purchase note with Gold Layout Icon -->
              <div class="inplay-callout">
                <span class="callout-icon">
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
                      d="M12 2v1M12 21v1M4.22 4.22l.71.71M18.36 18.36l.71.71M1 12h1M21 12h1M4.22 19.78l.71-.71M18.36 5.64l.71-.71"
                    ></path>
                    <circle cx="12" cy="12" r="5"></circle>
                  </svg>
                </span>
                <p class="callout-text">
                  <strong>{{ t('In-Play Ordering:') }}</strong>
                  {{
                    t(
                      "Need more snacks or refills while playing? You can scan your table's menu code to order food and drinks directly to your table at any time during your visit.",
                    )
                  }}
                </p>
              </div>
            </div>

            <!-- Price & Pay Action (Pay only pre-order food; table fee is paid at counter with staff) -->
            <div class="pricing-card">
              <h3 class="card-heading">{{ t('Payment Summary') }}</h3>
              <div class="cost-row">
                <div>
                  <span
                    >{{ t('Table Fee') }} ({{ reservationDurationHours }} hrs @
                    ฿{{ tableHourlyRate }}/hr)</span
                  >
                  <span class="pay-counter-pill">{{
                    t('Pay at counter after session')
                  }}</span>
                </div>
                <span class="muted-val">฿{{ tableTotal }}</span>
              </div>
              <div v-if="preOrderedFood.length > 0" class="cost-row">
                <span>{{ t('Pre-ordered Food & Drinks') }}</span>
                <span>฿{{ foodSubtotal }}</span>
              </div>
              <div
                v-if="preOrderedFood.length > 0"
                class="cost-row discount-row"
              >
                <span>{{ t('Pre-Order Discount (-10%)') }}</span>
                <span class="discount-num"
                  >-฿{{ foodDiscountInfo.discountAmount }}</span
                >
              </div>
              <div class="grand-total-row">
                <div>
                  <span>{{ t('Total Due Now') }}</span>
                  <small class="due-sub-text">{{
                    preOrderedFood.length > 0
                      ? t('Pre-ordered food with 10% discount')
                      : t('Table fee paid at counter with staff')
                  }}</small>
                </div>
                <span class="total-amount">฿{{ payNowTotal }}</span>
              </div>

              <div v-if="submissionError" class="error-banner" role="alert">
                {{ submissionError }}
              </div>

              <button
                v-if="payNowTotal > 0"
                type="button"
                class="btn btn-primary pay-btn"
                :disabled="isSubmitting"
                @click="proceedToPayment"
              >
                <span>{{
                  t('Pay Pre-order ฿{total} with PromptPay QR →', {
                    total: payNowTotal,
                  })
                }}</span>
              </button>
              <button
                v-else
                type="button"
                class="btn btn-primary pay-btn"
                :disabled="isSubmitting"
                @click="confirmWithoutPayment"
              >
                <span>{{
                  t('Confirm Table Reservation (Pay at Counter)')
                }}</span>
              </button>
              <p class="pay-note">
                {{
                  payNowTotal > 0
                    ? t(
                        'Instant verification via PromptPay / Thai QR Code. Table is reserved immediately.',
                      )
                    : t(
                        'Table is reserved immediately. Settle table fee at the counter with staff.',
                      )
                }}
              </p>
            </div>
          </div>

          <div class="wizard-actions">
            <button
              type="button"
              class="btn btn-secondary"
              @click="currentStep = 3"
            >
              <span>{{ t('← Back to Food & Drinks') }}</span>
            </button>
          </div>
        </section>

        <!-- STEP 5: Receipt & Booking Confirmation -->
        <section
          v-else-if="currentStep === 5"
          key="step5"
          class="wizard-step receipt-step"
          aria-labelledby="receipt-heading"
        >
          <div class="receipt-card">
            <div class="receipt-badge">
              <svg
                class="gold-icon check-icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <polyline points="20 6 9 17 4 12"></polyline>
              </svg>
              <span>{{ t('Reservation Confirmed!') }}</span>
            </div>

            <h2 id="receipt-heading" class="receipt-title">
              {{ t('See You at The Parlor!') }}
            </h2>
            <p class="receipt-sub">
              {{
                t(
                  'Your table is reserved. A confirmation has been sent to your email.',
                )
              }}
            </p>

            <div class="receipt-meta-box">
              <div class="meta-line">
                <span class="lbl">{{ t('Reservation Reference') }}:</span>
                <span class="v code">{{
                  createdReservation?.id || 'CONFIRMED'
                }}</span>
              </div>
              <div class="meta-line">
                <span class="lbl">{{ t('Branch') }}:</span>
                <span class="v">{{ t(selectedBranch?.name ?? '') }}</span>
              </div>
              <div class="meta-line">
                <span class="lbl">{{ t('Table') }}:</span>
                <span class="v">{{ t(selectedTable?.label ?? '') }}</span>
              </div>
              <div class="meta-line">
                <span class="lbl">{{ t('Interval (Timestamps)') }}:</span>
                <span class="v">{{ formattedDateTimeInterval }}</span>
              </div>
              <div class="meta-line">
                <span class="lbl">{{ t('Guest') }}:</span>
                <span class="v">{{ customerName }} ({{ customerPhone }})</span>
              </div>
              <div class="meta-line">
                <span class="lbl">{{ t('Table Fee') }}:</span>
                <span class="v"
                  >฿{{ tableTotal }} ({{
                    t('Pay at counter with staff')
                  }})</span
                >
              </div>
              <div class="meta-line">
                <span class="lbl">{{ t('Pre-order Paid (QR)') }}:</span>
                <span class="v bold">฿{{ payNowTotal }}</span>
              </div>
            </div>

            <div
              v-if="paymentDetails && paymentDetails.items.length > 0"
              class="receipt-food-box"
            >
              <h4 class="food-h">
                {{ t('Pre-Ordered Refreshments (10% Discount Applied)') }}
              </h4>
              <div
                v-for="f in paymentDetails.items"
                :key="f.item.id"
                class="receipt-food-item"
              >
                <span>{{ f.quantity }}x {{ f.item.name }}</span>
                <span>฿{{ Math.round(f.item.price * 0.9 * f.quantity) }}</span>
              </div>
            </div>

            <div class="receipt-actions">
              <RouterLink
                :to="{ name: 'CustomerReservationsList' }"
                class="btn btn-primary"
              >
                <span>{{ t('View My Reservations') }}</span>
              </RouterLink>
              <button
                type="button"
                class="btn btn-secondary"
                @click="currentStep = 1"
              >
                <span>+ {{ t('Reserve a Table Now') }}</span>
              </button>
              <RouterLink :to="{ name: 'HomeIndex' }" class="btn btn-secondary">
                <span>{{ t('Return to Catalog') }}</span>
              </RouterLink>
            </div>
          </div>
        </section>
      </Transition>

      <!-- Dynamic QR Payment Modal -->
      <div
        v-if="showQrPayment"
        class="modal-backdrop"
        @click.self="showQrPayment = false"
      >
        <QrPaymentDialog
          :total-amount="payNowTotal"
          :table-total="0"
          :food-items="preOrderedFood"
          :food-subtotal="foodSubtotal"
          :food-discount="foodDiscountInfo.discountAmount"
          :food-discounted-total="foodDiscountInfo.discountedTotal"
          :reservation-ref="`RES-${Date.now().toString().slice(-6)}`"
          @payment-completed="onPaymentCompleted"
          @cancel="showQrPayment = false"
        />
      </div>
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
.party-badge > span,
.btn > span {
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

.reservation-phone.is-invalid :deep(.phone-control) {
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
  border: 1px solid transparent;
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
</style>

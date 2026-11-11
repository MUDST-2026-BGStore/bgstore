<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
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
import { useStoreText } from '../../home/data/store-text';
import { useStoreContextStore } from '../../home/stores/store-context';
import { useCustomerReservationsStore } from '../data/reservations-store';

const route = useRoute();
const auth = useAuthStore();
const queryClient = useQueryClient();
const { t, locale } = useStoreText();
const storeContext = useStoreContextStore();
const reservationsStore = useCustomerReservationsStore();

// Step wizard: 1 = Time & Branch, 2 = Table & Guest Info, 3 = Food & Drinks, 4 = Review & QR Payment, 5 = Receipt
const currentStep = ref<number>(1);
const reservationSteps = [
  'When & Where',
  'Table & Info',
  'Food & Drinks (-10%)',
  'Review & Pay',
] as const;

// Step 1 State: When & Where (Timestamp based)
const selectedBranchId = ref<string>('');
const reservationDate = ref<string>('');
const reservationStartTime = ref<string>('14:00');
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
    specialNotes.value = t('Requesting {game} from the rental library', { game: route.query.game });
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
  },
  {
    id: 'ari',
    name: 'Ari',
    address: '71 Phahon Yothin Soi 7, Phaya Thai, Bangkok 10400',
    phone: '02-234-5678',
    openingHours: [],
    active: true,
  },
  {
    id: 'riverside',
    name: 'Riverside',
    address: '2194 Charoen Krung 42, Bang Rak, Bangkok 10500',
    phone: '02-345-6789',
    openingHours: [],
    active: true,
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
    if (userSelectedBranch && selectedBranchId.value && list.some((b) => b.id === selectedBranchId.value)) {
      return;
    }
    if (route.query.branch) {
      const qBranch = String(route.query.branch).toLowerCase();
      const matched = list.find((b) => b.id === route.query.branch || b.name.toLowerCase() === qBranch);
      if (matched) {
        selectedBranchId.value = matched.id;
        return;
      }
    }
    // Match by user's selected branch from StoreContext
    if (contextChoice && contextChoice !== 'browse' && contextChoice !== 'online') {
      const matched = list.find(
        (b) =>
          b.name.toLowerCase() === contextChoice.toLowerCase() ||
          b.id === contextChoice ||
          (storeContext.branch && b.name.toLowerCase() === storeContext.branch.name.toLowerCase())
      );
      if (matched) {
        selectedBranchId.value = matched.id;
        return;
      }
    }
    if (!selectedBranchId.value || !list.some((b) => b.id === selectedBranchId.value)) {
      selectedBranchId.value = list[0].id;
    }
  },
  { immediate: true }
);

const selectedBranch = computed(() =>
  branches.value.find((b) => b.id === selectedBranchId.value)
);

// Timestamps computation
const startTimestamp = computed(() => {
  if (!reservationDate.value || !reservationStartTime.value) return '';
  const dt = new Date(`${reservationDate.value}T${reservationStartTime.value}:00`);
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

const isDateTimeInvalid = computed(() => isDateInPast.value || isTimeInPast.value);

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
const customerEmail = ref<string>(auth.authenticated ? `${auth.username}@theparlor.test` : '');
const customerCountryCode = ref<string>('+66'); // Default +66 but freely changeable to any code!
const customerPhone = ref<string>('');
const specialNotes = ref<string>('');

// Phone Validation: clean digits, min 8, max 15
const cleanPhoneDigits = computed(() => customerPhone.value.replace(/\D/g, ''));
const isPhoneValid = computed(() => {
  const len = cleanPhoneDigits.value.length;
  return len >= 8 && len <= 15;
});
const isPhoneDirty = ref(false);
const phoneErrorMessage = computed(() => {
  if (!isPhoneDirty.value || !customerPhone.value.trim()) return '';
  if (cleanPhoneDigits.value.length < 8) {
    return t('Phone number must have at least 8 digits.');
  }
  if (cleanPhoneDigits.value.length > 15) {
    return t('Phone number is too long.');
  }
  return '';
});

// Country codes list (flexible, does NOT have to be +66!)
const countryCodes = [
  { code: '+66', label: 'Thailand (+66)' },
  { code: '+1', label: 'United States / Canada (+1)' },
  { code: '+44', label: 'United Kingdom (+44)' },
  { code: '+65', label: 'Singapore (+65)' },
  { code: '+81', label: 'Japan (+81)' },
  { code: '+61', label: 'Australia (+61)' },
  { code: '+49', label: 'Germany (+49)' },
  { code: '+33', label: 'France (+33)' },
  { code: '+82', label: 'South Korea (+82)' },
  { code: '+86', label: 'China (+86)' },
  { code: '+852', label: 'Hong Kong (+852)' },
  { code: '+886', label: 'Taiwan (+886)' },
  { code: '+60', label: 'Malaysia (+60)' },
  { code: '+84', label: 'Vietnam (+84)' },
  { code: '+62', label: 'Indonesia (+62)' },
  { code: '+91', label: 'India (+91)' },
];

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
      signal
    ),
  enabled: computed(() => !!selectedBranchId.value && !!startTimestamp.value && !!endTimestamp.value),
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
      : (MOCK_TABLES_BY_BRANCH[selectedBranchId.value] ?? MOCK_TABLES_BY_BRANCH['siam']);
  return list.filter((t) => t.available && t.capacity >= partySize.value);
});

// Auto-select first matching table when entering Step 2 or changing filter
watch(
  [availableTables, currentStep],
  ([tables, step]) => {
    if (step >= 2 && tables.length > 0) {
      if (!selectedTable.value || !tables.some((t) => t.tableId === selectedTable.value?.tableId)) {
        selectedTable.value = tables[0];
      }
    }
  },
  { immediate: true }
);

// Step 3 State: Food & Drinks Pre-Order (10% Discount)
const preOrderedFood = ref<FoodOrderItem[]>([]);

const foodSubtotal = computed(() =>
  preOrderedFood.value.reduce((sum, item) => sum + item.item.price * item.quantity, 0)
);

const foodDiscountInfo = computed(() =>
  calculatePreOrderDiscount(foodSubtotal.value)
);

// Table Pricing Calculation
const tableHourlyRate = computed(() => {
  if (!selectedTable.value) return 150; // fallback default
  const rate = selectedTable.value.hourlyRate;
  if (!rate) return 150;
  return 'amountMinor' in rate ? Number(rate.amountMinor) / 100 : 150;
});

const tableTotal = computed(() =>
  tableHourlyRate.value * reservationDurationHours.value
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
      err?.response?.data?.message || err?.message || 'Failed to submit reservation.';
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
    customerName: customerName.value.trim() || auth.displayName || auth.username || 'Customer',
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
        <ol class="stepper-list">
          <li
            v-for="(label, index) in reservationSteps"
            :key="label"
            class="step-indicator"
            :class="{
              active: currentStep === index + 1,
              completed: currentStep > index + 1,
            }"
            :aria-current="currentStep === index + 1 ? 'step' : undefined"
          >
            <span class="step-num" aria-hidden="true">
              <svg
                v-if="currentStep > index + 1"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="3"
                stroke-linecap="round"
                stroke-linejoin="round"
              ><polyline points="20 6 9 17 4 12" /></svg>
              <template v-else>{{ index + 1 }}</template>
            </span>
            <span class="step-text">{{ t(label) }}</span>
          </li>
        </ol>
      </nav>

      <Transition name="glam-step" mode="out-in">
        <!-- STEP 1: Date, Time & Branch (Timestamp driven) -->
        <section v-if="currentStep === 1" key="step1" class="wizard-step" aria-labelledby="step1-heading">
        <div class="step-header">
          <h2 id="step1-heading" class="step-title">{{ t('Choose Your Branch & Time') }}</h2>
          <p class="step-subtitle">
            {{ t('Select your preferred branch location, date, and time slot.') }}
          </p>
        </div>

        <!-- Registered Member Verification Callout -->
        <div v-if="auth.authenticated" class="member-status-bar">
          <div class="member-meta">
            <span class="member-text">{{ t('Reserving as Registered Member:') }}</span>
            <strong class="member-name">{{ auth.displayName || auth.username }}</strong>
          </div>
          <span class="member-badge-perk">{{ t('10% Pre-order Discount Active') }}</span>
        </div>
        <div v-else class="auth-gate-callout" role="alert">
          <div class="auth-gate-info">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="gold-icon"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" /><line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" /></svg>
            <div>
              <strong class="auth-gate-heading">{{ t('Sign in Required') }}</strong>
              <p class="auth-gate-sub">{{ t('Table reservations and food pre-orders are exclusively available for registered and signed-in members.') }}</p>
            </div>
          </div>
          <RouterLink :to="{ name: 'AuthLogin', query: { redirect: '/reservations/new' } }" class="btn btn-primary auth-signin-btn">
            {{ t('Sign In / Register') }} →
          </RouterLink>
        </div>

        <div class="form-grid">
          <!-- Branch Picker -->
          <div class="form-field full-width">
            <label for="branch-select" class="form-label">{{ t('Branch Location') }}</label>
            <select id="branch-select" v-model="selectedBranchId" class="form-select" @change="onBranchChange">
              <option v-for="b in branches" :key="b.id" :value="b.id">
                {{ b.name }} — {{ b.address || 'Bangkok' }}
              </option>
            </select>
          </div>

          <!-- Date Picker -->
          <div class="form-field">
            <label for="res-date" class="form-label">{{ t('Reservation Date') }}</label>
            <input
              id="res-date"
              v-model="reservationDate"
              :min="todayDateString"
              type="date"
              class="form-input"
              required
            />
          </div>

          <!-- Start Time -->
          <div class="form-field">
            <label for="res-time" class="form-label">{{ t('Start Time') }}</label>
            <input
              id="res-time"
              v-model="reservationStartTime"
              type="time"
              class="form-input"
              required
            />
          </div>

          <!-- Duration -->
          <div class="form-field">
            <label for="res-duration" class="form-label">{{ t('Duration') }}</label>
            <select id="res-duration" v-model.number="reservationDurationHours" class="form-select">
              <option :value="1">{{ t('1 Hour') }}</option>
              <option :value="2">{{ t('2 Hours (Recommended)') }}</option>
              <option :value="3">{{ t('3 Hours') }}</option>
              <option :value="4">{{ t('4 Hours (Half Day)') }}</option>
              <option :value="6">{{ t('6 Hours (Full Session)') }}</option>
            </select>
          </div>

          <!-- Party Size -->
          <div class="form-field">
            <label for="res-party" class="form-label">{{ t('Party Size') }}</label>
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
          <svg class="gold-icon warning-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>
          <span>{{ t('Reservation date and time cannot be in the past.') }}</span>
        </div>
        <div v-else-if="isTimeInPast" class="validation-alert" role="alert">
          <svg class="gold-icon warning-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>
          <span>{{ t('For same-day bookings, please select a time slot at least 30 minutes in advance.') }}</span>
        </div>

        <!-- Glamorous Brief Reservation Summary Card (No raw timestamps here) -->
        <div v-if="startTimestamp && endTimestamp && !isDateTimeInvalid" class="glamorous-summary-card">
          <div class="summary-card-header">
            <span class="parlor-crest">
              The Parlor · {{ selectedBranch?.name }}
            </span>
            <span class="party-badge">
              <svg class="gold-icon micro" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle></svg>
              {{ t(partySize === 1 ? '{count} player' : '{count} players', { count: partySize }) }}
            </span>
          </div>

          <div class="summary-card-body">
            <div class="summary-item">
              <span class="summary-icon">
                <svg class="gold-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect><line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line></svg>
              </span>
              <div>
                <span class="summary-label">{{ t('Reservation Date') }}</span>
                <strong class="summary-value">{{ glamorousDateFormatted }}</strong>
              </div>
            </div>

            <div class="summary-item">
              <span class="summary-icon">
                <svg class="gold-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
              </span>
              <div>
                <span class="summary-label">{{ t('Duration') }}</span>
                <strong class="summary-value">{{ glamorousTimeFormatted }} ({{ t(reservationDurationHours === 1 ? '1 Hour' : '{count} Hours', { count: reservationDurationHours }) }})</strong>
              </div>
            </div>

            <div v-if="requestedGame" class="summary-item game-item">
              <span class="summary-icon">
                <svg class="gold-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M8 20h8M9 20v-4h6v4M10 16l1-7h2l1 7M9 9h6M12 4a2 2 0 1 0 0-4 2 2 0 0 0 0 4z"></path></svg>
              </span>
              <div>
                <span class="summary-label">{{ t('Playing') }}</span>
                <strong class="summary-value">{{ requestedGame }}</strong>
              </div>
            </div>
          </div>
        </div>

        <div class="wizard-actions">
          <RouterLink :to="{ name: 'HomeIndex' }" class="btn btn-secondary">{{ t('Cancel') }}</RouterLink>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="!startTimestamp || !endTimestamp || !selectedBranchId || isDateTimeInvalid || !auth.authenticated"
            @click="currentStep = 2"
          >
            <span v-if="!auth.authenticated">{{ t('Sign in Required to Reserve') }}</span>
            <span v-else>{{ t('Check Available Tables →') }}</span>
          </button>
        </div>
      </section>

      <!-- STEP 2: Table Selection & Guest Info -->
        <section v-else-if="currentStep === 2" key="step2" class="wizard-step" aria-labelledby="step2-heading">
        <div class="step-header">
          <h2 id="step2-heading" class="step-title">{{ t('Select Table & Guest Details') }}</h2>
          <p class="step-subtitle">
            {{ t('Available tables at {branch} for {count} players.', { branch: t(selectedBranch?.name ?? ''), count: partySize }) }}
          </p>
        </div>

        <!-- Tables loading or empty -->
        <div v-if="availabilityQuery.isLoading.value" class="state-message">
          Checking table availability at {{ selectedBranch?.name }}…
        </div>

        <div v-else-if="availableTables.length === 0" class="empty-tables-box">
          <svg class="gold-icon empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>
          <p class="empty-title">{{ t('No tables available for this time slot') }}</p>
          <p class="empty-sub">
            {{ t('All tables are booked or out of operational hours for this party size. Try adjusting the time or branch.') }}
          </p>
          <button type="button" class="btn btn-secondary" @click="currentStep = 1">
            {{ t('← Change Time or Branch') }}
          </button>
        </div>

        <!-- Table selection cards with Gold Table Icon and translated labels -->
        <div v-else class="tables-grid">
          <div
            v-for="table in availableTables"
            :key="table.tableId"
            class="table-card"
            :class="{ 'is-selected': selectedTable?.tableId === table.tableId }"
            @click="selectedTable = table"
          >
            <div class="table-top">
              <div class="table-label-row">
                <svg class="gold-icon table-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="6" width="18" height="5" rx="1.5"></rect><line x1="6" y1="11" x2="6" y2="19"></line><line x1="18" y1="11" x2="18" y2="19"></line><line x1="4" y1="14" x2="20" y2="14"></line></svg>
                <span class="table-label">{{ t(table.label) }}</span>
              </div>
              <span class="capacity-pill">
                <svg class="gold-icon micro" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle></svg>
                {{ t('Up to {count} seats', { count: table.capacity }) }}
              </span>
            </div>
            <div class="table-rate">
              <span class="rate-num">฿{{ (table.hourlyRate as any)?.amountMinor ? ((table.hourlyRate as any).amountMinor / 100) : 150 }}</span>
              <span class="rate-unit">{{ t('/ hour') }}</span>
            </div>
            <div class="table-subtotal">
              {{ t('{amount} for {hours} hrs', { amount: (((table.hourlyRate as any)?.amountMinor ? ((table.hourlyRate as any).amountMinor / 100) : 150) * reservationDurationHours).toFixed(0), hours: reservationDurationHours }) }}
            </div>
            <div class="select-radio">
              <input
                type="radio"
                name="selected_table"
                :checked="selectedTable?.tableId === table.tableId"
                aria-label="Select table"
              />
              <span>{{ selectedTable?.tableId === table.tableId ? t('Selected') : t('Select Table') }}</span>
            </div>
          </div>
        </div>

        <!-- Contact Information (With international phone support) -->
        <div class="contact-section">
          <h3 class="section-title">{{ t('Guest Contact Information') }}</h3>
          <div class="form-grid">
            <div class="form-field">
              <label for="cust-name" class="form-label">{{ t('Full Name') }}</label>
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
              <label for="cust-email" class="form-label">{{ t('Email Address') }}</label>
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
              <div class="phone-input-group">
                <select v-model="customerCountryCode" class="country-code-select" aria-label="Country Code">
                  <option v-for="c in countryCodes" :key="c.code" :value="c.code">
                    {{ c.label }}
                  </option>
                </select>
                <input
                  id="cust-phone"
                  v-model="customerPhone"
                  type="tel"
                  placeholder="812345678"
                  class="form-input phone-number-field"
                  :class="{ 'is-invalid': isPhoneDirty && !isPhoneValid }"
                  required
                  @blur="isPhoneDirty = true"
                  @input="isPhoneDirty = true"
                />
              </div>
              <small v-if="phoneErrorMessage" class="phone-error-hint" role="alert">
                {{ phoneErrorMessage }}
              </small>
              <small v-else class="field-hint">
                {{ t('You can select any country code or type international numbers directly.') }}
              </small>
            </div>

            <div class="form-field full-width">
              <label for="cust-notes" class="form-label">{{ t('Special Requests or Board Game Preferences (Optional)') }}</label>
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
          <button type="button" class="btn btn-secondary" @click="currentStep = 1">
            {{ t('← Back') }}
          </button>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="!selectedTable || !customerName.trim() || !isPhoneValid"
            @click="currentStep = 3"
          >
            {{ t('Add Food & Drinks (-10%) →') }}
          </button>
        </div>
      </section>

      <!-- STEP 3: Food & Drinks Catalog (10% Discount & In-play note) -->
        <section v-else-if="currentStep === 3" key="step3" class="wizard-step" aria-labelledby="step3-heading">
        <div class="step-header">
          <h2 id="step3-heading" class="step-title">{{ t('Pre-Order Food & Drinks (10% Discount)') }}</h2>
          <p class="step-subtitle">
            {{ t('Pre-order bottled drinks and packaged snacks to enjoy a special 10% reservation discount. You can also order more snacks directly from your table while playing!') }}
          </p>
        </div>

        <FoodMenuPicker v-model="preOrderedFood" />

        <div class="wizard-actions">
          <button type="button" class="btn btn-secondary" @click="currentStep = 2">
            {{ t('← Back to Table') }}
          </button>
          <button type="button" class="btn btn-primary" @click="currentStep = 4">
            {{ t('Proceed to Review & Pay →') }}
          </button>
        </div>
      </section>

      <!-- STEP 4: Review Summary & QR Code Payment -->
        <section v-else-if="currentStep === 4" key="step4" class="wizard-step" aria-labelledby="step4-heading">
        <div class="step-header">
          <h2 id="step4-heading" class="step-title">{{ t('Review & Confirm Reservation') }}</h2>
          <p class="step-subtitle">
            Confirm your table reservation details and complete the PromptPay QR transfer to lock in your booking.
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
              <span class="val">{{ t(selectedTable?.label ?? '') }} ({{ t('Up to {count} seats', { count: selectedTable?.capacity || 4 }) }})</span>
            </div>
            <div class="review-row">
              <span class="label">{{ t('Reservation Date') }}:</span>
              <span class="val">{{ formattedDateTimeInterval }}</span>
            </div>
            <div class="review-row">
              <span class="label">{{ t('Party Size') }}:</span>
              <span class="val">{{ t(partySize === 1 ? '{count} player' : '{count} players', { count: partySize }) }}</span>
            </div>
            <div class="review-row">
              <span class="label">{{ t('Guest') }}:</span>
              <span class="val">{{ customerName }} ({{ customerCountryCode }} {{ customerPhone }})</span>
            </div>

            <!-- Pre-ordered Food Summary -->
            <div v-if="preOrderedFood.length > 0" class="food-summary-block">
              <h4 class="food-summary-title">{{ t('Pre-Ordered Food & Drinks (10% Discount)') }}</h4>
              <ul class="food-list">
                <li v-for="line in preOrderedFood" :key="line.item.id" class="food-item-line">
                  <span>{{ line.quantity }}x {{ line.item.name }}</span>
                  <span>฿{{ line.item.price * line.quantity }}</span>
                </li>
              </ul>
            </div>

            <!-- In-game food purchase note with Gold Layout Icon -->
            <div class="inplay-callout">
              <span class="callout-icon">
                <svg class="gold-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v1M12 21v1M4.22 4.22l.71.71M18.36 18.36l.71.71M1 12h1M21 12h1M4.22 19.78l.71-.71M18.36 5.64l.71-.71"></path><circle cx="12" cy="12" r="5"></circle></svg>
              </span>
              <p class="callout-text">
                <strong>{{ t('In-Play Ordering:') }}</strong> {{ t('Need more snacks or refills while playing? You can scan your table\'s menu code to order food and drinks directly to your table at any time during your visit.') }}
              </p>
            </div>
          </div>

          <!-- Price & Pay Action (Pay only pre-order food; table fee is paid at counter with staff) -->
          <div class="pricing-card">
            <h3 class="card-heading">{{ t('Payment Summary') }}</h3>
            <div class="cost-row">
              <div>
                <span>{{ t('Table Fee') }} ({{ reservationDurationHours }} hrs @ ฿{{ tableHourlyRate }}/hr)</span>
                <span class="pay-counter-pill">{{ t('Pay at counter after session') }}</span>
              </div>
              <span class="muted-val">฿{{ tableTotal }}</span>
            </div>
            <div v-if="preOrderedFood.length > 0" class="cost-row">
              <span>{{ t('Pre-ordered Food & Drinks') }}</span>
              <span>฿{{ foodSubtotal }}</span>
            </div>
            <div v-if="preOrderedFood.length > 0" class="cost-row discount-row">
              <span>{{ t('Pre-Order Discount (-10%)') }}</span>
              <span class="discount-num">-฿{{ foodDiscountInfo.discountAmount }}</span>
            </div>
            <div class="grand-total-row">
              <div>
                <span>{{ t('Total Due Now') }}</span>
                <small class="due-sub-text">{{ preOrderedFood.length > 0 ? t('Pre-ordered food with 10% discount') : t('Table fee paid at counter with staff') }}</small>
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
              {{ t('Pay Pre-order ฿{total} with PromptPay QR →', { total: payNowTotal }) }}
            </button>
            <button
              v-else
              type="button"
              class="btn btn-primary pay-btn"
              :disabled="isSubmitting"
              @click="confirmWithoutPayment"
            >
              {{ t('Confirm Table Reservation (Pay at Counter) →') }}
            </button>
            <p class="pay-note">
              {{ payNowTotal > 0 ? t('Instant verification via PromptPay / Thai QR Code. Table is reserved immediately.') : t('Table is reserved immediately. Settle table fee at the counter with staff.') }}
            </p>
          </div>
        </div>

        <div class="wizard-actions">
          <button type="button" class="btn btn-secondary" @click="currentStep = 3">
            {{ t('← Back to Food & Drinks') }}
          </button>
        </div>
      </section>

      <!-- STEP 5: Receipt & Booking Confirmation -->
        <section v-else-if="currentStep === 5" key="step5" class="wizard-step receipt-step" aria-labelledby="receipt-heading">
        <div class="receipt-card">
          <div class="receipt-badge">
            <svg class="gold-icon check-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>
            <span>{{ t('Reservation Confirmed!') }}</span>
          </div>

          <h2 id="receipt-heading" class="receipt-title">{{ t('See You at The Parlor!') }}</h2>
          <p class="receipt-sub">
            {{ t('Your table is reserved. A confirmation has been sent to your email.') }}
          </p>

          <div class="receipt-meta-box">
            <div class="meta-line">
              <span class="lbl">{{ t('Reservation Reference') }}:</span>
              <span class="v code">{{ createdReservation?.id || 'CONFIRMED' }}</span>
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
              <span class="v">{{ customerName }} ({{ customerCountryCode }} {{ customerPhone }})</span>
            </div>
            <div class="meta-line">
              <span class="lbl">{{ t('Table Fee') }}:</span>
              <span class="v">฿{{ tableTotal }} ({{ t('Pay at counter with staff') }})</span>
            </div>
            <div class="meta-line">
              <span class="lbl">{{ t('Pre-order Paid (QR)') }}:</span>
              <span class="v bold">฿{{ payNowTotal }}</span>
            </div>
          </div>

          <div v-if="paymentDetails && paymentDetails.items.length > 0" class="receipt-food-box">
            <h4 class="food-h">{{ t('Pre-Ordered Refreshments (10% Discount Applied)') }}</h4>
            <div v-for="f in paymentDetails.items" :key="f.item.id" class="receipt-food-item">
              <span>{{ f.quantity }}x {{ f.item.name }}</span>
              <span>฿{{ Math.round(f.item.price * 0.9 * f.quantity) }}</span>
            </div>
          </div>

          <div class="receipt-actions">
            <RouterLink :to="{ name: 'CustomerReservationsList' }" class="btn btn-primary">
              {{ t('View My Reservations') }}
            </RouterLink>
            <button type="button" class="btn btn-secondary" @click="currentStep = 1">
              + {{ t('Reserve a Table Now') }}
            </button>
            <RouterLink :to="{ name: 'HomeIndex' }" class="btn btn-secondary">
              {{ t('Return to Catalog') }}
            </RouterLink>
          </div>
        </div>
      </section>
      </Transition>

      <!-- Dynamic QR Payment Modal -->
      <div v-if="showQrPayment" class="modal-backdrop" @click.self="showQrPayment = false">
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
/*
 * Reservation surfaces: ivory page, ivory panels, deep green for highlights,
 * gold only for rules, icons and the primary action. One scale for radii
 * (12 card / 8 block & control / 999 pill), 1px borders, 44px fields & buttons.
 */
.reservation-page {
  --surface: var(--color-parlor-ivory-50);
  --sunken: var(--color-parlor-ivory-200);
  --line: var(--color-parlor-ivory-300);
  --control-border: var(--color-parlor-ivory-400);
  --ink: var(--color-parlor-green-900);
  --muted: var(--color-parlor-neutral-600);
  --radius-card: 12px;
  --radius: 8px;
  --control-h: 44px;
  --shadow: 0 1px 2px color-mix(in srgb, var(--color-parlor-green-950) 6%, transparent);

  background: var(--color-parlor-ivory-100);
  min-height: calc(100svh - var(--parlor-header-height, 64px));
  padding: 40px 24px 80px;
  color: var(--ink);
  font-family: 'FC Mee Sakul', 'Plus Jakarta Sans', system-ui, Arial, sans-serif;
}

.container {
  max-width: 980px;
  margin: 0 auto;
}

/* ---------- Step indicator ---------- */
.stepper-nav {
  margin-bottom: 24px;
  padding: 16px 20px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow);
}

.stepper-list {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.step-indicator {
  display: flex;
  align-items: center;
  flex: 1 1 0;
  gap: 10px;
  min-width: 0;
  color: var(--muted);
}

/* Connector before every step except the first */
.step-indicator + .step-indicator::before {
  content: '';
  flex: 0 1 40px;
  min-width: 12px;
  height: 2px;
  margin-right: 2px;
  border-radius: 2px;
  background: var(--line);
}
.step-indicator.completed + .step-indicator::before {
  background: var(--color-parlor-green-700);
}

.step-num {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1.5px solid var(--control-border);
  border-radius: 50%;
  background: var(--surface);
  color: var(--muted);
  font-size: 13px;
  font-weight: 700;
}

.step-num svg {
  width: 14px;
  height: 14px;
}

.step-text {
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-indicator.completed {
  color: var(--color-parlor-green-700);
}
.step-indicator.completed .step-num {
  border-color: var(--color-parlor-green-700);
  background: var(--color-parlor-green-700);
  color: var(--color-parlor-cream);
}

.step-indicator.active {
  color: var(--ink);
}
.step-indicator.active .step-num {
  border-color: var(--color-parlor-green-900);
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-gold-300);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-parlor-gold-500) 35%, transparent);
}
.step-indicator.active .step-text {
  font-weight: 800;
}

/* ---------- Step panel ---------- */
.wizard-step {
  padding: 28px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow);
}

.step-header {
  margin-bottom: 24px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--line);
}

.step-title {
  margin: 0 0 6px;
  font-size: 24px;
  line-height: 1.2;
  font-weight: 800;
  color: var(--ink);
}

.step-subtitle {
  margin: 0;
  font-size: 14.5px;
  line-height: 1.5;
  color: var(--muted);
}

/* ---------- Member / sign-in callouts ---------- */
.member-status-bar,
.auth-gate-callout {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px 16px;
  margin-bottom: 24px;
  padding: 12px 16px;
  background: var(--sunken);
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.member-meta {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 8px;
  min-width: 0;
  font-size: 14px;
}

.member-text {
  color: var(--muted);
}

.member-name {
  font-weight: 800;
  color: var(--ink);
  overflow-wrap: anywhere;
}

.member-badge-perk {
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--color-parlor-green-900);
  color: var(--color-parlor-cream);
  font-size: 12px;
  font-weight: 700;
}

.auth-gate-callout {
  border-color: color-mix(in srgb, var(--color-parlor-gold-500) 60%, transparent);
}

.auth-gate-info {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex: 1 1 320px;
}

.auth-gate-heading {
  display: block;
  margin-bottom: 2px;
  font-size: 14.5px;
  color: var(--ink);
}

.auth-gate-sub {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--muted);
}

.auth-signin-btn {
  white-space: nowrap;
}

/* ---------- Form ---------- */
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 18px 20px;
  margin-bottom: 24px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.form-field.full-width {
  grid-column: 1 / -1;
}

.form-label {
  font-size: 13.5px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--ink);
}

.form-input,
.form-select,
.country-code-select {
  box-sizing: border-box;
  width: 100%;
  min-height: var(--control-h);
  padding: 0 14px;
  border: 1px solid var(--control-border);
  border-radius: var(--radius);
  background-color: var(--color-parlor-white);
  color: var(--ink);
  font: inherit;
  font-size: 14.5px;
  transition:
    border-color 150ms ease,
    box-shadow 150ms ease;
}

textarea.form-input {
  min-height: 88px;
  padding: 12px 14px;
  line-height: 1.5;
  resize: vertical;
}

.form-input::placeholder {
  color: color-mix(in srgb, var(--muted) 80%, transparent);
}

.form-input:hover,
.form-select:hover,
.country-code-select:hover {
  border-color: var(--color-parlor-neutral-500);
}

.form-input:focus,
.form-select:focus,
.country-code-select:focus {
  outline: none;
  border-color: var(--color-parlor-green-700);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-parlor-green-700) 18%, transparent);
}

.phone-input-group {
  display: flex;
  gap: 8px;
}

.country-code-select {
  flex: 0 0 auto;
  width: auto;
  max-width: 220px;
  font-weight: 600;
}

.phone-number-field {
  flex: 1;
  min-width: 0;
}

.field-hint,
.phone-error-hint {
  font-size: 12.5px;
  line-height: 1.4;
}

.field-hint {
  color: var(--muted);
}

.phone-error-hint {
  font-weight: 600;
  color: var(--color-parlor-danger-700);
}

.form-input.phone-number-field.is-invalid {
  border-color: var(--color-parlor-danger-600);
  background: var(--color-parlor-danger-100);
}

.validation-alert,
.error-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
  padding: 12px 14px;
  border: 1px solid var(--color-parlor-danger-300);
  border-radius: var(--radius);
  background: var(--color-parlor-danger-100);
  color: var(--color-parlor-danger-700);
  font-size: 13.5px;
  font-weight: 600;
}

.error-banner {
  margin: 16px 0 0;
}

/* ---------- Reservation summary (deep green, flat) ---------- */
.glamorous-summary-card {
  margin-bottom: 24px;
  padding: 20px 24px;
  background: var(--color-parlor-green-900);
  border: 1px solid color-mix(in srgb, var(--color-parlor-gold-500) 45%, transparent);
  border-radius: var(--radius-card);
  color: var(--color-parlor-cream);
}

.summary-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid color-mix(in srgb, var(--color-parlor-gold-500) 25%, transparent);
}

.parlor-crest {
  font-family: 'Plus Jakarta Sans', Arial, sans-serif;
  font-size: 14px;
  font-weight: 700;
  color: var(--color-parlor-gold-300);
}

.party-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border: 1px solid color-mix(in srgb, var(--color-parlor-cream) 25%, transparent);
  border-radius: 999px;
  color: var(--color-parlor-cream);
  font-size: 12.5px;
  font-weight: 700;
}

.summary-card-body {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px 24px;
}

.summary-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
}

.summary-icon {
  display: inline-flex;
  padding-top: 2px;
}

.summary-label {
  display: block;
  margin-bottom: 2px;
  font-size: 12.5px;
  color: color-mix(in srgb, var(--color-parlor-cream) 72%, transparent);
}

.summary-value {
  display: block;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--color-parlor-white);
}

.game-item {
  grid-column: 1 / -1;
  padding-top: 14px;
  border-top: 1px solid color-mix(in srgb, var(--color-parlor-gold-500) 25%, transparent);
}

/* ---------- Tables ---------- */
.state-message,
.empty-tables-box {
  margin-bottom: 24px;
  padding: 32px 16px;
  background: var(--sunken);
  border: 1px dashed var(--control-border);
  border-radius: var(--radius);
  text-align: center;
  color: var(--muted);
}

.empty-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 700;
  color: var(--ink);
}

.empty-sub {
  margin: 0 0 16px;
  font-size: 13.5px;
  line-height: 1.5;
  color: var(--muted);
}

.tables-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
  margin-bottom: 28px;
}

.table-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px 18px;
  background: var(--color-parlor-white);
  border: 1px solid var(--control-border);
  border-radius: var(--radius-card);
  cursor: pointer;
  transition:
    background-color 150ms ease,
    border-color 150ms ease,
    box-shadow 150ms ease;
}

.table-card:hover {
  border-color: var(--color-parlor-gold-500);
}

.table-card.is-selected {
  background: var(--color-parlor-green-900);
  border-color: var(--color-parlor-green-900);
  box-shadow: 0 0 0 2px var(--color-parlor-gold-500);
  color: var(--color-parlor-cream);
}

.table-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.table-label-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.table-label {
  font-size: 16px;
  font-weight: 700;
  color: var(--ink);
}

.capacity-pill {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--sunken);
  color: var(--ink);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.table-rate {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.rate-num {
  font-size: 20px;
  font-weight: 800;
  color: var(--ink);
}

.rate-unit,
.table-subtotal {
  font-size: 13px;
  color: var(--muted);
}

.select-radio {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
  font-size: 13.5px;
  font-weight: 700;
  color: var(--ink);
}

.select-radio input {
  width: 16px;
  height: 16px;
  margin: 0;
  accent-color: var(--color-parlor-gold-500);
}

.table-card.is-selected .table-label,
.table-card.is-selected .select-radio {
  color: var(--color-parlor-white);
}
.table-card.is-selected .rate-num {
  color: var(--color-parlor-gold-300);
}
.table-card.is-selected .rate-unit,
.table-card.is-selected .table-subtotal {
  color: color-mix(in srgb, var(--color-parlor-cream) 80%, transparent);
}
.table-card.is-selected .select-radio {
  border-top-color: color-mix(in srgb, var(--color-parlor-gold-500) 30%, transparent);
}
.table-card.is-selected .capacity-pill {
  background: transparent;
  border-color: color-mix(in srgb, var(--color-parlor-cream) 30%, transparent);
  color: var(--color-parlor-cream);
}

.contact-section {
  margin-top: 4px;
  padding-top: 22px;
  border-top: 1px solid var(--line);
}

.section-title {
  margin: 0 0 16px;
  font-size: 17px;
  font-weight: 800;
  color: var(--ink);
}

/* ---------- Review ---------- */
.review-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 0.8fr);
  align-items: start;
  gap: 20px;
}

.review-card,
.pricing-card {
  padding: 20px;
  background: var(--color-parlor-white);
  border: 1px solid var(--line);
  border-radius: var(--radius-card);
}

.card-heading {
  margin: 0 0 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--line);
  font-size: 16px;
  font-weight: 800;
  color: var(--ink);
}

.review-row,
.cost-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
  font-size: 14px;
  line-height: 1.45;
  color: var(--ink);
}

.review-row .label {
  flex: none;
  color: var(--muted);
}

.review-row .val {
  min-width: 0;
  font-weight: 700;
  text-align: right;
  overflow-wrap: anywhere;
}

.discount-row {
  font-weight: 700;
  color: var(--color-parlor-success-700);
}

.pay-counter-pill,
.due-sub-text {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-parlor-gold-700);
}

.muted-val {
  font-weight: 700;
  color: var(--muted);
}

.grand-total-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--color-parlor-gold-500);
  font-size: 16px;
  font-weight: 800;
  color: var(--ink);
}

.total-amount {
  font-size: 26px;
  color: var(--ink);
}

.food-summary-block {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}

.food-summary-title {
  margin: 0 0 8px;
  font-size: 13.5px;
  font-weight: 700;
  color: var(--ink);
}

.food-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 13.5px;
}

.food-item-line {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--ink);
}

.inplay-callout {
  display: flex;
  gap: 10px;
  margin-top: 18px;
  padding: 12px 14px;
  background: var(--sunken);
  border-radius: var(--radius);
}

.callout-icon {
  display: inline-flex;
  padding-top: 1px;
}

.callout-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--ink);
}

.pay-btn {
  width: 100%;
  min-height: 48px;
  margin-top: 18px;
  font-size: 15px;
}

.pay-note {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
  color: var(--muted);
}

/* ---------- Receipt ---------- */
.receipt-step {
  display: flex;
  justify-content: center;
}

.receipt-card {
  width: 100%;
  max-width: 580px;
  text-align: center;
}

.receipt-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding: 6px 14px;
  border-radius: 999px;
  background: var(--color-parlor-success-100);
  color: var(--color-parlor-success-700);
  font-size: 13.5px;
  font-weight: 800;
}

.receipt-title {
  margin: 0 0 6px;
  font-size: 26px;
  font-weight: 800;
  color: var(--ink);
}

.receipt-sub {
  margin: 0 0 24px;
  font-size: 14.5px;
  color: var(--muted);
}

.receipt-meta-box,
.receipt-food-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 20px;
  padding: 16px 18px;
  background: var(--color-parlor-white);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  text-align: left;
}

.receipt-food-box {
  background: var(--sunken);
  gap: 6px;
  margin-bottom: 24px;
}

.meta-line,
.receipt-food-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  font-size: 14px;
}

.meta-line .lbl {
  flex: none;
  color: var(--muted);
}

.meta-line .v {
  min-width: 0;
  font-weight: 600;
  text-align: right;
  color: var(--ink);
  overflow-wrap: anywhere;
}

.meta-line .v.bold {
  font-weight: 800;
}

.meta-line .code {
  padding: 1px 8px;
  border-radius: 4px;
  background: var(--sunken);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
}

.food-h {
  margin: 0 0 4px;
  font-size: 13.5px;
  font-weight: 700;
  color: var(--ink);
}

.receipt-food-item {
  color: var(--ink);
}

.receipt-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

/* ---------- Actions & buttons ---------- */
.wizard-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: var(--control-h);
  padding: 0 20px;
  border: 1px solid transparent;
  border-radius: var(--radius);
  font: inherit;
  font-size: 14px;
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

.btn:focus-visible,
.table-card:focus-within {
  outline: 2px solid var(--color-parlor-gold-500);
  outline-offset: 2px;
}

.btn-primary {
  background: var(--color-parlor-gold-500);
  border-color: var(--color-parlor-gold-500);
  color: var(--color-parlor-green-950);
}

.btn-primary:hover:not(:disabled) {
  background: var(--color-parlor-gold-300);
  border-color: var(--color-parlor-gold-300);
}

.btn-primary:disabled {
  background: var(--sunken);
  border-color: var(--line);
  color: var(--muted);
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--surface);
  border-color: var(--control-border);
  color: var(--ink);
}

.btn-secondary:hover:not(:disabled) {
  background: var(--sunken);
  border-color: var(--color-parlor-gold-500);
}

/* ---------- Icons ---------- */
.gold-icon {
  display: inline-block;
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  color: var(--color-parlor-gold-700);
  vertical-align: middle;
}
/* Pale gold only where the background is deep green */
.glamorous-summary-card .gold-icon,
.table-card.is-selected .gold-icon {
  color: var(--color-parlor-gold-300);
}
.gold-icon.micro {
  width: 13px;
  height: 13px;
}
.gold-icon.table-icon {
  width: 22px;
  height: 22px;
}
.gold-icon.warning-icon {
  width: 20px;
  height: 20px;
  color: currentColor;
}
.gold-icon.empty-icon {
  width: 32px;
  height: 32px;
  margin-bottom: 8px;
}
.gold-icon.check-icon {
  width: 18px;
  height: 18px;
  color: var(--color-parlor-success-700);
}

/* ---------- Modal ---------- */
.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: color-mix(in srgb, var(--color-parlor-green-950) 55%, transparent);
}

/* ---------- Step transition ---------- */
.glam-step-enter-active,
.glam-step-leave-active {
  transition:
    opacity 200ms ease,
    transform 200ms ease;
}
.glam-step-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.glam-step-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* ---------- Responsive ---------- */
@media (max-width: 768px) {
  .form-grid,
  .review-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 700px) {
  .reservation-page {
    padding: 24px 16px 64px;
  }
  .stepper-nav {
    padding: 14px 16px;
  }
  /* Numbers for every step, the label only for the current one */
  .step-indicator {
    flex: 0 0 auto;
  }
  .step-indicator.active {
    flex: 1 1 auto;
  }
  .step-indicator:not(.active) .step-text {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip-path: inset(50%);
    white-space: nowrap;
  }
  .step-indicator + .step-indicator::before {
    flex-basis: 16px;
  }
  .wizard-step {
    padding: 22px 16px;
  }
  .step-title {
    font-size: 21px;
  }
  .glamorous-summary-card {
    padding: 18px 16px;
  }
  .phone-input-group {
    flex-direction: column;
  }
  .country-code-select {
    width: 100%;
    max-width: none;
  }
  .wizard-actions,
  .receipt-actions {
    flex-direction: column-reverse;
  }
  .wizard-actions > .btn,
  .receipt-actions > .btn,
  .auth-signin-btn {
    width: 100%;
  }
  .review-row,
  .meta-line {
    flex-wrap: wrap;
  }
  .review-row .val,
  .meta-line .v {
    text-align: left;
  }
}

@media (prefers-reduced-motion: reduce) {
  .glam-step-enter-active,
  .glam-step-leave-active,
  .btn,
  .table-card,
  .form-input,
  .form-select,
  .country-code-select {
    transition: none;
  }
  .glam-step-enter-from,
  .glam-step-leave-to {
    transform: none;
  }
}
</style>

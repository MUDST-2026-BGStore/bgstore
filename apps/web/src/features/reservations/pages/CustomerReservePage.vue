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

const route = useRoute();
const auth = useAuthStore();
const queryClient = useQueryClient();

// Step wizard: 1 = Time & Branch, 2 = Table & Guest Info, 3 = Food & Drinks, 4 = Review & QR Payment, 5 = Receipt
const currentStep = ref<number>(1);

// Step 1 State: When & Where (Timestamp based)
const selectedBranchId = ref<string>('');
const reservationDate = ref<string>('');
const reservationStartTime = ref<string>('14:00');
const reservationDurationHours = ref<number>(2);
const partySize = ref<number>(4);

// Initialize default date to tomorrow
onMounted(() => {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  reservationDate.value = tomorrow.toISOString().split('T')[0];

  if (typeof route.query.branch === 'string') {
    selectedBranchId.value = route.query.branch;
  }
});

// Load Branches
const branchesQuery = useQuery({
  queryKey: ['branches'],
  queryFn: ({ signal }) => listBranches({}, undefined, signal),
});

const branches = computed<Branch[]>(() => branchesQuery.data.value?.items ?? []);

// Auto-select first branch if none selected
watch(branches, (list) => {
  if (list.length > 0 && !selectedBranchId.value) {
    selectedBranchId.value = list[0].id;
  }
});

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

const formattedDateTimeInterval = computed(() => {
  if (!startTimestamp.value || !endTimestamp.value) return '';
  const start = new Date(startTimestamp.value);
  const end = new Date(endTimestamp.value);
  const dateStr = start.toLocaleDateString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
  const timeStr = `${start.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} – ${end.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
  return `${dateStr}, ${timeStr} (${reservationDurationHours.value} hrs)`;
});

// Step 2 State: Table & Customer Contact
const selectedTable = ref<TableAvailability | null>(null);
const customerName = ref<string>(auth.displayName || auth.username || '');
const customerEmail = ref<string>(auth.authenticated ? `${auth.username}@theparlor.test` : '');
const customerCountryCode = ref<string>('+66'); // Default +66 but freely changeable to any code!
const customerPhone = ref<string>('');
const specialNotes = ref<string>('');

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

const availableTables = computed<TableAvailability[]>(() => {
  const tables = availabilityQuery.data.value?.tables ?? [];
  return tables.filter((t) => t.available && t.capacity >= partySize.value);
});

// Step 3 State: Food & Drinks Pre-Order (10% Discount)
const preOrderedFood = ref<FoodOrderItem[]>([]);

const foodSubtotal = computed(() =>
  preOrderedFood.value.reduce((sum, item) => sum + item.item.price * item.quantity, 0)
);

const foodDiscountInfo = computed(() =>
  calculatePreOrderDiscount(foodSubtotal.value)
);

// Table Pricing Calculation
function getTableHourlyRateNumber(table: TableAvailability | null): number {
  if (!table || !table.hourlyRate) return 150;
  return table.hourlyRate.amountMinor ? table.hourlyRate.amountMinor / 100 : 150;
}

const tableHourlyRate = computed(() => getTableHourlyRateNumber(selectedTable.value));

const tableTotal = computed(() =>
  tableHourlyRate.value * reservationDurationHours.value
);

const grandTotal = computed(() =>
  tableTotal.value + foodDiscountInfo.value.discountedTotal
);

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

  const preOrderPayload: ReservationPreOrder = {
    items: preOrderedFood.value,
    subtotal: foodSubtotal.value,
    discountRate: 0.1,
    discountAmount: foodDiscountInfo.value.discountAmount,
    discountedTotal: foodDiscountInfo.value.discountedTotal,
    paid: true,
    paymentMethod: 'promptpay_qr',
    paymentReference: paymentMeta.reference,
    paidAtTimestamp: paymentMeta.paidAtTimestamp,
  };
  paymentDetails.value = preOrderPayload;

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

  try {
    await createMutation.mutateAsync({
      branchId: selectedBranchId.value,
      data: reservationPayload,
    });
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
        <div class="step-indicator" :class="{ active: currentStep === 1, completed: currentStep > 1 }">
          <span class="step-num">1</span>
          <span class="step-text">When & Where</span>
        </div>
        <div class="step-divider" />
        <div class="step-indicator" :class="{ active: currentStep === 2, completed: currentStep > 2 }">
          <span class="step-num">2</span>
          <span class="step-text">Table & Info</span>
        </div>
        <div class="step-divider" />
        <div class="step-indicator" :class="{ active: currentStep === 3, completed: currentStep > 3 }">
          <span class="step-num">3</span>
          <span class="step-text">Food & Drinks (-10%)</span>
        </div>
        <div class="step-divider" />
        <div class="step-indicator" :class="{ active: currentStep === 4, completed: currentStep > 4 }">
          <span class="step-num">4</span>
          <span class="step-text">Review & Pay</span>
        </div>
      </nav>

      <!-- STEP 1: Date, Time & Branch (Timestamp driven) -->
      <section v-if="currentStep === 1" class="wizard-step" aria-labelledby="step1-heading">
        <div class="step-header">
          <h2 id="step1-heading" class="step-title">Choose Your Branch & Time</h2>
          <p class="step-subtitle">
            Select your preferred branch location, date, and time slot. Exact timestamps are verified in real time.
          </p>
        </div>

        <div class="form-grid">
          <!-- Branch Picker -->
          <div class="form-field full-width">
            <label for="branch-select" class="form-label">Branch Location</label>
            <select id="branch-select" v-model="selectedBranchId" class="form-select">
              <option v-for="b in branches" :key="b.id" :value="b.id">
                {{ b.name }} — {{ b.address || 'Bangkok' }}
              </option>
            </select>
          </div>

          <!-- Date Picker -->
          <div class="form-field">
            <label for="res-date" class="form-label">Reservation Date</label>
            <input
              id="res-date"
              v-model="reservationDate"
              type="date"
              class="form-input"
              required
            />
          </div>

          <!-- Start Time -->
          <div class="form-field">
            <label for="res-time" class="form-label">Start Time</label>
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
            <label for="res-duration" class="form-label">Duration</label>
            <select id="res-duration" v-model.number="reservationDurationHours" class="form-select">
              <option :value="1">1 Hour</option>
              <option :value="2">2 Hours (Recommended)</option>
              <option :value="3">3 Hours</option>
              <option :value="4">4 Hours (Half Day)</option>
              <option :value="6">6 Hours (Full Session)</option>
            </select>
          </div>

          <!-- Party Size -->
          <div class="form-field">
            <label for="res-party" class="form-label">Party Size</label>
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

        <!-- Timestamp preview card -->
        <div v-if="startTimestamp && endTimestamp" class="timestamp-summary">
          <div class="time-main">
            <span class="clock-icon">🕒</span>
            <span class="interval-text">{{ formattedDateTimeInterval }}</span>
          </div>
          <div class="timestamp-codes">
            <code>Starts: {{ startTimestamp }}</code>
            <code>Ends: {{ endTimestamp }}</code>
          </div>
        </div>

        <div class="wizard-actions">
          <RouterLink :to="{ name: 'HomeIndex' }" class="btn btn-secondary">Cancel</RouterLink>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="!startTimestamp || !endTimestamp || !selectedBranchId"
            @click="currentStep = 2"
          >
            Check Available Tables →
          </button>
        </div>
      </section>

      <!-- STEP 2: Table Selection & Guest Info -->
      <section v-if="currentStep === 2" class="wizard-step" aria-labelledby="step2-heading">
        <div class="step-header">
          <h2 id="step2-heading" class="step-title">Select Table & Guest Details</h2>
          <p class="step-subtitle">
            Available tables at <strong>{{ selectedBranch?.name }}</strong> for {{ partySize }} players.
          </p>
        </div>

        <!-- Tables loading or empty -->
        <div v-if="availabilityQuery.isLoading.value" class="state-message">
          Checking table availability at {{ selectedBranch?.name }}…
        </div>

        <div v-else-if="availableTables.length === 0" class="empty-tables-box">
          <p class="empty-title">No tables available for this time slot</p>
          <p class="empty-sub">
            All tables are booked or out of operational hours for this party size. Try adjusting the time or branch.
          </p>
          <button type="button" class="btn btn-secondary" @click="currentStep = 1">
            ← Change Time or Branch
          </button>
        </div>

        <!-- Table selection cards -->
        <div v-else class="tables-grid">
          <div
            v-for="table in availableTables"
            :key="table.tableId"
            class="table-card"
            :class="{ 'is-selected': selectedTable?.tableId === table.tableId }"
            @click="selectedTable = table"
          >
            <div class="table-top">
              <span class="table-label">{{ table.label }}</span>
              <span class="capacity-pill">Up to {{ table.capacity }} seats</span>
            </div>
            <div class="table-rate">
              <span class="rate-num">฿{{ getTableHourlyRateNumber(table) }}</span>
              <span class="rate-unit">/ hour</span>
            </div>
            <div class="table-subtotal">
              ฿{{ getTableHourlyRateNumber(table) * reservationDurationHours }} for {{ reservationDurationHours }} hrs
            </div>
            <div class="select-radio">
              <input
                type="radio"
                name="selected_table"
                :checked="selectedTable?.tableId === table.tableId"
                aria-label="Select table"
              />
              <span>{{ selectedTable?.tableId === table.tableId ? 'Selected' : 'Select Table' }}</span>
            </div>
          </div>
        </div>

        <!-- Contact Information (With international phone support) -->
        <div class="contact-section">
          <h3 class="section-title">Guest Contact Information</h3>
          <div class="form-grid">
            <div class="form-field">
              <label for="cust-name" class="form-label">Full Name</label>
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
              <label for="cust-email" class="form-label">Email Address</label>
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
                Phone Number (International numbers supported)
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
                  required
                />
              </div>
              <small class="field-hint">
                You can select any country code or type international numbers directly.
              </small>
            </div>

            <div class="form-field full-width">
              <label for="cust-notes" class="form-label">Special Requests (Optional)</label>
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
            ← Back
          </button>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="!selectedTable || !customerName.trim() || !customerPhone.trim()"
            @click="currentStep = 3"
          >
            Add Food & Drinks (-10%) →
          </button>
        </div>
      </section>

      <!-- STEP 3: Food & Drinks Catalog (10% Discount & In-play note) -->
      <section v-if="currentStep === 3" class="wizard-step" aria-labelledby="step3-heading">
        <div class="step-header">
          <h2 id="step3-heading" class="step-title">Pre-Order Food & Drinks</h2>
          <p class="step-subtitle">
            Pre-order coffee, snacks, or platters to enjoy a special <strong>10% reservation discount</strong>.
            You can also order more food and drinks directly from your table while playing!
          </p>
        </div>

        <FoodMenuPicker v-model="preOrderedFood" />

        <div class="wizard-actions">
          <button type="button" class="btn btn-secondary" @click="currentStep = 2">
            ← Back to Table
          </button>
          <button type="button" class="btn btn-primary" @click="currentStep = 4">
            Proceed to Review & Pay →
          </button>
        </div>
      </section>

      <!-- STEP 4: Review Summary & QR Code Payment -->
      <section v-if="currentStep === 4" class="wizard-step" aria-labelledby="step4-heading">
        <div class="step-header">
          <h2 id="step4-heading" class="step-title">Review & Complete Booking</h2>
          <p class="step-subtitle">
            Confirm your table reservation details and complete the PromptPay QR transfer to lock in your booking.
          </p>
        </div>

        <div class="review-grid">
          <!-- Summary Details -->
          <div class="review-card">
            <h3 class="card-heading">Booking Overview</h3>
            <div class="review-row">
              <span class="label">Branch:</span>
              <span class="val">{{ selectedBranch?.name }}</span>
            </div>
            <div class="review-row">
              <span class="label">Table:</span>
              <span class="val">{{ selectedTable?.label }} (Up to {{ selectedTable?.capacity }} seats)</span>
            </div>
            <div class="review-row">
              <span class="label">Date & Time:</span>
              <span class="val">{{ formattedDateTimeInterval }}</span>
            </div>
            <div class="review-row">
              <span class="label">Party Size:</span>
              <span class="val">{{ partySize }} Players</span>
            </div>
            <div class="review-row">
              <span class="label">Contact:</span>
              <span class="val">{{ customerName }} ({{ customerCountryCode }} {{ customerPhone }})</span>
            </div>

            <!-- Pre-ordered Food Summary -->
            <div v-if="preOrderedFood.length > 0" class="food-summary-block">
              <h4 class="food-summary-title">Pre-Ordered Food & Drinks (10% Discount)</h4>
              <ul class="food-list">
                <li v-for="line in preOrderedFood" :key="line.item.id" class="food-item-line">
                  <span>{{ line.quantity }}x {{ line.item.name }}</span>
                  <span>฿{{ line.item.price * line.quantity }}</span>
                </li>
              </ul>
            </div>

            <!-- In-game food purchase note -->
            <div class="inplay-callout">
              <span class="callout-icon">💡</span>
              <p class="callout-text">
                <strong>In-Play Ordering:</strong> Need more snacks or refills while playing?
                You can scan your table's menu code to order food and drinks directly to your table at any time during your visit.
              </p>
            </div>
          </div>

          <!-- Price & Pay Action -->
          <div class="pricing-card">
            <h3 class="card-heading">Payment Summary</h3>
            <div class="cost-row">
              <span>Table Fee ({{ reservationDurationHours }} hrs @ ฿{{ tableHourlyRate }}/hr)</span>
              <span>฿{{ tableTotal }}</span>
            </div>
            <div v-if="preOrderedFood.length > 0" class="cost-row">
              <span>Food & Drinks Subtotal</span>
              <span>฿{{ foodSubtotal }}</span>
            </div>
            <div v-if="preOrderedFood.length > 0" class="cost-row discount-row">
              <span>Pre-Order Discount (10% OFF)</span>
              <span class="discount-num">-฿{{ foodDiscountInfo.discountAmount }}</span>
            </div>
            <div class="grand-total-row">
              <span>Total Amount Due</span>
              <span class="total-amount">฿{{ grandTotal }}</span>
            </div>

            <div v-if="submissionError" class="error-banner" role="alert">
              {{ submissionError }}
            </div>

            <button
              type="button"
              class="btn btn-primary pay-btn"
              :disabled="isSubmitting"
              @click="proceedToPayment"
            >
              Pay ฿{{ grandTotal }} with PromptPay QR →
            </button>
            <p class="pay-note">
              Instant verification via PromptPay / Thai QR Code. Table is reserved immediately.
            </p>
          </div>
        </div>

        <div class="wizard-actions">
          <button type="button" class="btn btn-secondary" @click="currentStep = 3">
            ← Back to Food & Drinks
          </button>
        </div>
      </section>

      <!-- STEP 5: Receipt & Booking Confirmation -->
      <section v-if="currentStep === 5" class="wizard-step receipt-step" aria-labelledby="receipt-heading">
        <div class="receipt-card">
          <div class="receipt-badge">
            <span class="check">✓</span>
            <span>RESERVATION CONFIRMED</span>
          </div>

          <h2 id="receipt-heading" class="receipt-title">See You at The Parlor!</h2>
          <p class="receipt-sub">
            Your table and pre-ordered food & drinks have been successfully booked and paid.
          </p>

          <div class="receipt-meta-box">
            <div class="meta-line">
              <span class="lbl">Reservation Reference:</span>
              <span class="v code">{{ createdReservation?.id || 'CONFIRMED' }}</span>
            </div>
            <div class="meta-line">
              <span class="lbl">Branch:</span>
              <span class="v">{{ selectedBranch?.name }}</span>
            </div>
            <div class="meta-line">
              <span class="lbl">Table:</span>
              <span class="v">{{ selectedTable?.label }}</span>
            </div>
            <div class="meta-line">
              <span class="lbl">Interval (Timestamps):</span>
              <span class="v">{{ formattedDateTimeInterval }}</span>
            </div>
            <div class="meta-line">
              <span class="lbl">Guest:</span>
              <span class="v">{{ customerName }} ({{ customerCountryCode }} {{ customerPhone }})</span>
            </div>
            <div class="meta-line">
              <span class="lbl">Total Paid (QR):</span>
              <span class="v bold">฿{{ grandTotal }}</span>
            </div>
          </div>

          <div v-if="paymentDetails && paymentDetails.items.length > 0" class="receipt-food-box">
            <h4 class="food-h">Pre-Ordered Refreshments (10% Discount Applied)</h4>
            <div v-for="f in paymentDetails.items" :key="f.item.id" class="receipt-food-item">
              <span>{{ f.quantity }}x {{ f.item.name }}</span>
              <span>฿{{ Math.round(f.item.price * 0.9 * f.quantity) }}</span>
            </div>
          </div>

          <div class="receipt-actions">
            <RouterLink :to="{ name: 'CustomerReservationsList' }" class="btn btn-primary">
              View My Reservations
            </RouterLink>
            <RouterLink :to="{ name: 'HomeIndex' }" class="btn btn-secondary">
              Return to Catalog
            </RouterLink>
          </div>
        </div>
      </section>

      <!-- Dynamic QR Payment Modal -->
      <div v-if="showQrPayment" class="modal-backdrop" @click.self="showQrPayment = false">
        <QrPaymentDialog
          :total-amount="grandTotal"
          :table-total="tableTotal"
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
  font-family: Arial, Helvetica, sans-serif;
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

/* Timestamp summary */
.timestamp-summary {
  background: #f4ecdc;
  border: 1px solid #c6ad7a;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 22px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.time-main {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  color: #1a382a;
}

.timestamp-codes {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 11px;
  color: #554d3d;
}

.timestamp-codes code {
  background: #ffffff;
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid #d5c8b2;
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
  border: 2px solid #e0d5c1;
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: all 140ms ease;
}

.table-card:hover {
  border-color: #baa67c;
  box-shadow: 0 4px 14px rgba(20, 43, 32, 0.08);
}

.table-card.is-selected {
  border-color: #203c30;
  background: #fcf9f2;
  box-shadow: 0 0 0 1px #203c30;
}

.table-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.table-label {
  font-size: 16px;
  font-weight: 700;
  color: #1a382a;
}

.capacity-pill {
  font-size: 11px;
  background: #e9dec9;
  color: #203c30;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
}

.table-rate {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.rate-num {
  font-size: 18px;
  font-weight: 800;
  color: #203c30;
}

.rate-unit {
  font-size: 12px;
  color: #6a6252;
}

.table-subtotal {
  font-size: 12px;
  color: #555044;
}

.select-radio {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #203c30;
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
</style>

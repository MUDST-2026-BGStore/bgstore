<script setup lang="ts">
// Uses live rental charges and checkout (getRental, checkOutRental) when the
// reservation has a server rentalId, while keeping local in-play food orders
// and PromptPay QR simulation as client-side overlays.
import { computed, ref } from 'vue';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import {
  checkOutRental,
  getRental,
  type Rental,
} from '@theparlor/api-client';
import {
  useCustomerReservationsStore,
  type CustomerReservation,
  type InPlayOrder,
} from '../data/reservations-store';
import { useStoreText } from '../../../shared/lib/store-text';
import QRCode from 'qrcode';

const props = withDefaults(
  defineProps<{
    reservation: CustomerReservation;
    isStaff?: boolean;
  }>(),
  {
    isStaff: false,
  },
);

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'settled'): void;
}>();

const { t, locale } = useStoreText();
const queryClient = useQueryClient();
const reservationsStore = useCustomerReservationsStore();
const settlementSuccess = ref(false);
const isSettling = ref(false);
const selectedStaffPaymentMethod = ref<'cash' | 'promptpay_qr' | 'card'>(
  'cash',
);

const rentalBranchId = computed(() => props.reservation.branchId);
const rentalId = computed(() => props.reservation.rentalId ?? '');
const rentalQuery = useQuery({
  queryKey: ['rental', rentalBranchId, rentalId],
  queryFn: ({ signal }) =>
    getRental(rentalBranchId.value, rentalId.value, undefined, signal),
  enabled: computed(() =>
    Boolean(props.reservation.branchId && props.reservation.rentalId),
  ),
});

const checkedOutRental = ref<Rental | null>(null);
const activeRental = computed<Rental | null>(() => {
  const candidate = checkedOutRental.value ?? rentalQuery.data.value ?? null;
  if (!candidate || typeof candidate.startedAt !== 'string') {
    return null;
  }
  return candidate;
});

// Calculate actual playtime duration from checkedInAt (or rental.startedAt) to now (or completedAt / rental.endedAt)
const checkInTime = computed(() => {
  return new Date(
    activeRental.value?.startedAt ||
      props.reservation.checkedInAt ||
      props.reservation.startsAt,
  ).getTime();
});

const finishTime = computed(() => {
  const endedAt = activeRental.value?.endedAt || props.reservation.completedAt;
  return endedAt ? new Date(endedAt).getTime() : Date.now();
});

const playDurationMinutes = computed(() => {
  const diffMs = finishTime.value - checkInTime.value;
  return Math.max(1, Math.round(diffMs / 60000));
});

const formattedPlayedDuration = computed(() => {
  const mins = playDurationMinutes.value;
  if (mins < 60) {
    return `${mins} ${t('mins')}`;
  }
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return m > 0 ? `${h} ${t('hr')} ${m} ${t('mins')}` : `${h} ${t('Hours')}`;
});

const effectiveHourlyRate = computed(() => {
  const tableCharge = activeRental.value?.charges?.find(
    (c) => c.kind === 'TABLE',
  );
  if (tableCharge?.unitHourlyRate?.amountMinor) {
    return Math.round(tableCharge.unitHourlyRate.amountMinor / 100);
  }
  return props.reservation.hourlyRate || 200;
});

const gameRentalCost = computed(() => {
  const gameCharges =
    activeRental.value?.charges?.filter((c) => c.kind === 'GAME') ?? [];
  return gameCharges.reduce((sum, charge) => {
    if (charge.lineTotal?.amountMinor != null) {
      return sum + Math.round(charge.lineTotal.amountMinor / 100);
    }
    const unitRate = Math.round((charge.unitHourlyRate?.amountMinor ?? 0) / 100);
    return (
      sum +
      Math.round((playDurationMinutes.value / 60) * unitRate * charge.quantity)
    );
  }, 0);
});

// Table Play Cost based on completed rental total or actual played duration: (minutes / 60) * rate
const tablePlayCost = computed(() => {
  if (
    activeRental.value?.status === 'COMPLETED' &&
    activeRental.value.total?.amountMinor != null
  ) {
    return Math.round(activeRental.value.total.amountMinor / 100);
  }
  const rate = effectiveHourlyRate.value;
  return (
    Math.round((playDurationMinutes.value / 60) * rate) + gameRentalCost.value
  );
});

// Late arrival info (e.g. booked 9-10, arrived 9:15)
const lateArrivalInfo = computed(() => {
  const actualStartIso =
    activeRental.value?.startedAt || props.reservation.checkedInAt;
  if (!actualStartIso || !props.reservation.startsAt) return null;
  const scheduledStart = new Date(props.reservation.startsAt).getTime();
  const actualCheckIn = new Date(actualStartIso).getTime();
  const lateMinutes = Math.round((actualCheckIn - scheduledStart) / 60000);
  if (lateMinutes >= 3) {
    return {
      lateMinutes,
      scheduledTime: `${new Date(props.reservation.startsAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} – ${new Date(props.reservation.endsAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`,
      actualCheckIn: new Date(actualStartIso).toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
      }),
    };
  }
  return null;
});

// Pre-order totals (paid in advance via online QR)
const preOrderPaid = computed(() => {
  return props.reservation.preOrder?.paid
    ? props.reservation.preOrder.discountedTotal
    : 0;
});

// In-play orders breakdown (paid at counter)
const inPlayOrders = computed<InPlayOrder[]>(() => {
  const storeOrders = reservationsStore.getInPlayOrders(props.reservation.id);
  return storeOrders.length > 0
    ? storeOrders
    : props.reservation.inPlayOrders || [];
});

const inPlayOrdersTotal = computed(() => {
  return inPlayOrders.value.reduce((sum, o) => sum + o.subtotal, 0);
});

// Total Bill and Outstanding Balance to pay at counter
const totalGrossBill = computed(() => {
  return (
    tablePlayCost.value +
    (props.reservation.preOrder?.discountedTotal || 0) +
    inPlayOrdersTotal.value
  );
});

const outstandingBalanceDue = computed(() => {
  return Math.max(0, totalGrossBill.value - preOrderPaid.value);
});

async function completeRentalOnServerIfNeeded() {
  if (
    !props.isStaff ||
    !props.reservation.branchId ||
    !props.reservation.rentalId ||
    activeRental.value?.status === 'COMPLETED'
  ) {
    return;
  }
  try {
    const completed = await checkOutRental(
      props.reservation.branchId,
      props.reservation.rentalId,
    );
    if (completed?.rental && typeof completed.rental.startedAt === 'string') {
      checkedOutRental.value = completed.rental;
    }
    void queryClient.invalidateQueries({
      queryKey: ['rental', props.reservation.branchId, props.reservation.rentalId],
    });
    void queryClient.invalidateQueries({
      queryKey: ['listBranchRentals'],
    });
    void queryClient.invalidateQueries({
      queryKey: ['listBranchReservations'],
    });
    void queryClient.invalidateQueries({
      queryKey: ['listBranchTables'],
    });
  } catch {
    // Fall back to local settlement when offline or unseeded
  }
}

// Settle bill (staff action at counter)
async function handleSettleBill() {
  if (isSettling.value) return;
  isSettling.value = true;
  try {
    await completeRentalOnServerIfNeeded();
    reservationsStore.settleSessionBill(
      props.reservation.id,
      selectedStaffPaymentMethod.value,
    );
    settlementSuccess.value = true;
    setTimeout(() => {
      emit('settled');
      emit('close');
    }, 1400);
  } finally {
    isSettling.value = false;
  }
}

// Customer Self-Payment QR
const showCustomerQr = ref(false);
const customerQrDataUrl = ref('');
const isVerifyingCustomerPayment = ref(false);

async function openCustomerQr() {
  const qrPayload = JSON.stringify({
    service: 'PromptPay-QR',
    merchant: 'The Parlor Session Bill',
    reservationId: props.reservation.id,
    table: props.reservation.tableLabel,
    amount: outstandingBalanceDue.value,
    currency: 'THB',
  });
  try {
    customerQrDataUrl.value = await QRCode.toDataURL(qrPayload, {
      width: 220,
      margin: 1,
      color: { dark: '#142a1f', light: '#ffffff' },
    });
  } catch (err) {
    console.error(err);
  }
  showCustomerQr.value = true;
}

function confirmCustomerQrPayment() {
  isVerifyingCustomerPayment.value = true;
  setTimeout(() => {
    isVerifyingCustomerPayment.value = false;
    showCustomerQr.value = false;
    reservationsStore.settleSessionBill(props.reservation.id, 'promptpay_qr');
    settlementSuccess.value = true;
    setTimeout(() => {
      emit('settled');
      emit('close');
    }, 1400);
  }, 800);
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal-card bill-dialog">
      <!-- Header -->
      <div class="dialog-header">
        <div>
          <div class="header-tags">
            <span class="live-pill">
              <span class="pulse-dot"></span>
              {{
                isStaff
                  ? t('Staff Bill Inspection')
                  : t('Live Table Bill & Tab')
              }}
            </span>
            <span
              v-if="reservation.clientType === 'WALK_IN'"
              class="client-badge walkin-badge"
            >
              {{ t('Walk-in Session') }}
            </span>
            <span v-else class="client-badge reserved-badge">
              {{ t('Reserved Client') }}
            </span>
            <span class="table-ref"
              >{{ t(reservation.tableLabel) }} •
              {{ t(reservation.branchName) }}</span
            >
          </div>
          <h2 :id="`table-bill-title-${reservation.id}`" class="dialog-title">
            {{ t('Table Session Bill') }}
          </h2>
          <p class="dialog-sub">
            {{
              t('Party of {count} players • Session Ref: {ref}', {
                count: reservation.partySize,
                ref: reservation.id,
              })
            }}
          </p>
        </div>
        <button
          type="button"
          class="btn-close"
          :aria-label="t('Cancel')"
          @click="emit('close')"
        >
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>

      <!-- Settlement Success Screen -->
      <div v-if="settlementSuccess" class="success-screen">
        <div class="success-ring">
          <svg
            width="28"
            height="28"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="3"
          >
            <polyline points="20 6 9 17 4 12" />
          </svg>
        </div>
        <h3 class="success-title">{{ t('Bill Settled & Session Closed!') }}</h3>
        <p class="success-sub">
          {{
            t(
              'All table play fees and in-play food orders have been settled successfully. Thank you for playing at The Parlor!',
            )
          }}
        </p>
      </div>

      <!-- Bill Content -->
      <div v-else class="dialog-content">
        <div class="bill-scroll">
          <!-- 1. Table Play Session Section (Dynamic played time computation) -->
          <div class="bill-section">
            <div class="section-top">
              <div class="sec-title-wrap">
                <span class="gold-bullet">◆</span>
                <span class="section-title">{{ t('Table Play Session') }}</span>
              </div>
              <span class="section-cost">฿{{ tablePlayCost }}</span>
            </div>
            <div class="section-meta">
              <span
                >{{ t('Played Time:') }}
                <strong>{{ formattedPlayedDuration }}</strong> (@ ฿{{
                  effectiveHourlyRate
                }}/{{ t('hr') }})</span
              >
              <span
                class="meta-tag"
                :class="
                  reservation.status === 'COMPLETED'
                    ? 'tag-completed'
                    : 'tag-active'
                "
              >
                {{
                  reservation.status === 'COMPLETED'
                    ? t('Completed')
                    : t('Playing Now')
                }}
              </span>
            </div>

            <!-- Late arrival notice (e.g. booked 9-10, arrived 9:15 -> billed for 45 mins) -->
            <div v-if="lateArrivalInfo" class="late-arrival-callout">
              <span class="callout-icon">✦</span>
              <div class="callout-text">
                <strong>{{ t('Actual Played Time Billed:') }}</strong>
                <span>
                  {{
                    t(
                      'Scheduled {sched}, checked in at {checkin} ({late} min late). Billed for actual {mins} min played.',
                      {
                        sched: lateArrivalInfo.scheduledTime,
                        checkin: lateArrivalInfo.actualCheckIn,
                        late: lateArrivalInfo.lateMinutes,
                        mins: playDurationMinutes,
                      },
                    )
                  }}
                </span>
              </div>
            </div>
          </div>

          <!-- 2. Pre-ordered Food & Drinks Section (Pre-paid online) -->
          <div
            v-if="reservation.preOrder && reservation.preOrder.items.length > 0"
            class="bill-section"
          >
            <div class="section-top">
              <div class="sec-title-wrap">
                <span class="gold-bullet">◆</span>
                <span class="section-title">{{
                  t('Pre-ordered Refreshments (-10% Discount)')
                }}</span>
              </div>
              <span class="section-cost"
                >฿{{ reservation.preOrder.discountedTotal }}</span
              >
            </div>
            <div class="itemized-list">
              <div
                v-for="itemRecord in reservation.preOrder.items"
                :key="itemRecord.item.id"
                class="item-line"
              >
                <span class="line-name">
                  {{ itemRecord.quantity }}x
                  {{
                    locale === 'th-TH' && itemRecord.item.nameTh
                      ? itemRecord.item.nameTh
                      : itemRecord.item.name
                  }}
                </span>
                <span class="line-price">
                  ฿{{
                    Math.round(
                      itemRecord.item.price * 0.9 * itemRecord.quantity,
                    )
                  }}
                </span>
              </div>
            </div>
            <div class="prepaid-badge">
              <svg
                width="12"
                height="12"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2.5"
              >
                <polyline points="20 6 9 17 4 12" />
              </svg>
              <span>{{ t('Pre-paid via PromptPay QR') }}</span>
            </div>
          </div>

          <!-- 3. In-Play Food & Drinks Orders (No food status pipeline, clean text list) -->
          <div class="bill-section">
            <div class="section-top">
              <div class="sec-title-wrap">
                <span class="gold-bullet">◆</span>
                <span class="section-title"
                  >{{ t('In-Play Table Orders') }} ({{
                    inPlayOrders.length
                  }})</span
                >
              </div>
              <span class="section-cost">฿{{ inPlayOrdersTotal }}</span>
            </div>

            <div v-if="inPlayOrders.length === 0" class="empty-inplay-note">
              {{
                t(
                  'No in-play food or drinks ordered yet. You can order snacks anytime while playing!',
                )
              }}
            </div>

            <div v-else class="inplay-orders-list">
              <div
                v-for="order in inPlayOrders"
                :key="order.id"
                class="inplay-order-card"
              >
                <div class="order-card-header">
                  <span class="order-ref-num">{{ order.orderNumber }}</span>
                  <span class="order-time">
                    {{
                      new Date(order.createdAt).toLocaleTimeString([], {
                        hour: '2-digit',
                        minute: '2-digit',
                      })
                    }}
                  </span>
                  <span class="tab-pill">{{ t('On Tab') }}</span>
                </div>

                <!-- Clean simple item list without kitchen status badges -->
                <div class="order-items">
                  <div
                    v-for="it in order.items"
                    :key="it.item.id"
                    class="order-sub-item"
                  >
                    <span
                      >{{ it.quantity }}x
                      {{
                        locale === 'th-TH' && it.item.nameTh
                          ? it.item.nameTh
                          : it.item.name
                      }}</span
                    >
                    <span>฿{{ it.item.price * it.quantity }}</span>
                  </div>
                </div>

                <div v-if="order.notes" class="order-note-line">
                  {{ t('Note:') }} {{ order.notes }}
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 4. Total Calculation Summary -->
        <div class="bill-summary-footer">
          <div class="summary-line">
            <span>{{ t('Total Session Bill (Play + Food):') }}</span>
            <strong class="val">฿{{ totalGrossBill }}</strong>
          </div>
          <div v-if="preOrderPaid > 0" class="summary-line text-muted">
            <span>{{ t('Pre-paid Online (PromptPay QR):') }}</span>
            <span class="val text-green">- ฿{{ preOrderPaid }}</span>
          </div>

          <div class="summary-divider"></div>

          <div class="summary-line total-due-line">
            <div>
              <span class="due-label">{{
                t('Outstanding Balance Due at Counter:')
              }}</span>
              <small class="due-hint">
                {{
                  outstandingBalanceDue === 0
                    ? t('All charges settled ✓')
                    : t('Includes hourly fee & on-tab refreshments')
                }}
              </small>
            </div>
            <strong
              class="due-amount"
              :class="{ 'is-zero': outstandingBalanceDue === 0 }"
            >
              ฿{{ outstandingBalanceDue }}
            </strong>
          </div>

          <!-- Counter Notice / Actions -->
          <div class="bill-actions">
            <!-- Staff Settle Action -->
            <template v-if="isStaff">
              <div class="staff-payment-picker">
                <span class="staff-pm-label">{{
                  t('Counter Payment Method:')
                }}</span>
                <div class="staff-pm-options">
                  <label
                    class="pm-radio-btn"
                    :class="{ active: selectedStaffPaymentMethod === 'cash' }"
                  >
                    <input
                      v-model="selectedStaffPaymentMethod"
                      type="radio"
                      value="cash"
                      class="sr-only"
                    />
                    <span>{{ t('Cash') }}</span>
                  </label>
                  <label
                    class="pm-radio-btn"
                    :class="{
                      active: selectedStaffPaymentMethod === 'promptpay_qr',
                    }"
                  >
                    <input
                      v-model="selectedStaffPaymentMethod"
                      type="radio"
                      value="promptpay_qr"
                      class="sr-only"
                    />
                    <span>{{ t('PromptPay QR') }}</span>
                  </label>
                  <label
                    class="pm-radio-btn"
                    :class="{ active: selectedStaffPaymentMethod === 'card' }"
                  >
                    <input
                      v-model="selectedStaffPaymentMethod"
                      type="radio"
                      value="card"
                      class="sr-only"
                    />
                    <span>{{ t('Card') }}</span>
                  </label>
                </div>
              </div>

              <button
                type="button"
                class="btn btn-primary settle-btn"
                @click="handleSettleBill"
              >
                {{ t('Settle Bill & Complete Session ✓') }}
              </button>
            </template>

            <!-- Customer Counter Notice & Self QR Option -->
            <template v-else>
              <div
                v-if="outstandingBalanceDue > 0"
                class="customer-pay-actions"
              >
                <button
                  type="button"
                  class="btn-customer-qr"
                  @click="openCustomerQr"
                >
                  <svg
                    width="18"
                    height="18"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    aria-hidden="true"
                  >
                    <rect x="3" y="3" width="7" height="7" />
                    <rect x="14" y="3" width="7" height="7" />
                    <rect x="14" y="14" width="7" height="7" />
                    <rect x="3" y="14" width="7" height="7" />
                  </svg>
                  <span>{{
                    locale === 'th-TH'
                      ? 'สแกนชำระผ่าน พร้อมเพย์ (PromptPay QR) ทันที'
                      : 'Scan & Pay via PromptPay QR'
                  }}</span>
                </button>
                <div class="pay-method-sep">
                  <span>{{
                    locale === 'th-TH'
                      ? 'หรือเลือกชำระที่เคาน์เตอร์'
                      : 'Or pay with staff at counter'
                  }}</span>
                </div>
              </div>

              <div class="counter-pay-box">
                <svg
                  class="gold-icon"
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <circle cx="12" cy="12" r="10" />
                  <line x1="12" y1="16" x2="12" y2="12" />
                  <line x1="12" y1="8" x2="12.01" y2="8" />
                </svg>
                <div>
                  <strong>{{ t('Pay at Counter with Staff') }}</strong>
                  <p>
                    {{
                      locale === 'th-TH'
                        ? 'สามารถชำระด้วย เงินสด, พร้อมเพย์ QR หรือ บัตรเครดิต/เดบิต ได้ที่เคาน์เตอร์'
                        : t(
                            'Please show this bill or quote your table number at the counter when you finish playing.',
                          )
                    }}
                  </p>
                </div>
              </div>

              <!-- Customer PromptPay QR Modal (Centered) -->
              <div
                v-if="showCustomerQr"
                class="bill-qr-backdrop"
                @click.self="showCustomerQr = false"
              >
                <div class="bill-qr-card">
                  <div class="qr-head">
                    <div class="brand-eyebrow">
                      ✦ THE PARLOR · TABLE SETTLEMENT ✦
                    </div>
                    <h3>
                      {{
                        locale === 'th-TH'
                          ? 'สแกนชำระบิลค่าเล่นและอาหาร'
                          : 'Scan PromptPay QR to Settle Bill'
                      }}
                    </h3>
                    <p class="qr-desc">
                      {{
                        locale === 'th-TH'
                          ? 'สแกนจ่ายได้ด้วยแอปพลิเคชันทุกธนาคารในไทย'
                          : 'Pay with any Thai mobile banking app'
                      }}
                    </p>
                  </div>

                  <div class="qr-img-wrap">
                    <img
                      v-if="customerQrDataUrl"
                      :src="customerQrDataUrl"
                      alt="PromptPay QR"
                      width="220"
                      height="220"
                    />
                  </div>

                  <div class="qr-amount-pill">
                    <span>{{
                      locale === 'th-TH' ? 'ยอดที่ต้องชำระ' : 'Amount Due'
                    }}</span>
                    <strong>฿{{ outstandingBalanceDue }}</strong>
                  </div>

                  <p class="qr-ref-tag">
                    {{ locale === 'th-TH' ? 'โต๊ะ:' : 'Table:' }}
                    <strong>{{ reservation.tableLabel }}</strong> · Ref:
                    <code>{{ reservation.id }}</code>
                  </p>

                  <button
                    type="button"
                    class="btn btn-primary btn-confirm-table-paid"
                    :disabled="isVerifyingCustomerPayment"
                    @click="confirmCustomerQrPayment"
                  >
                    <span v-if="isVerifyingCustomerPayment">{{
                      locale === 'th-TH' ? 'กำลังตรวจสอบยอดเงิน…' : 'Verifying…'
                    }}</span>
                    <span v-else>{{
                      locale === 'th-TH'
                        ? 'ฉันได้โอนเงินเรียบร้อยแล้ว'
                        : 'I Have Paid / Simulate Transfer'
                    }}</span>
                  </button>

                  <button
                    type="button"
                    class="btn-close-qr"
                    @click="showCustomerQr = false"
                  >
                    {{ locale === 'th-TH' ? 'ย้อนกลับ' : 'Back' }}
                  </button>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(14, 30, 22, 0.72);
  backdrop-filter: blur(5px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 250;
  padding: 16px;
}

.modal-card {
  background: #ffffff;
  border: 1.5px solid #c6ad7a;
  border-radius: 14px;
  max-width: 580px;
  width: 100%;
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.45);
  overflow: hidden;
  font-family:
    'FC Mee Sakul', 'Plus Jakarta Sans', system-ui, Arial, sans-serif;
}

/* Header */
.dialog-header {
  padding: 20px 24px 16px;
  border-bottom: 1.5px solid #e7ded0;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  background: #fdfbf7;
}

.header-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.live-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #142b20;
  color: #ffd98a;
  font-size: 11px;
  font-weight: 800;
  padding: 3px 10px;
  border-radius: 12px;
  letter-spacing: 0.04em;
}

.client-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 10px;
}

.walkin-badge {
  background: #fef3c7;
  color: #92400e;
  border: 1px solid #fde68a;
}

.reserved-badge {
  background: #e0f2fe;
  color: #0369a1;
  border: 1px solid #bae6fd;
}

.pulse-dot {
  width: 7px;
  height: 7px;
  background: #4ade80;
  border-radius: 50%;
  box-shadow: 0 0 6px #4ade80;
  animation: pulse-green 1.6s infinite ease-in-out;
}

@keyframes pulse-green {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.4;
    transform: scale(0.85);
  }
}

.table-ref {
  font-size: 12px;
  color: #736959;
  font-weight: 600;
}

.dialog-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 800;
  color: #142b20;
}

.dialog-sub {
  margin: 0;
  font-size: 13px;
  color: #6a6252;
}

.btn-close {
  background: none;
  border: none;
  color: #736959;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.btn-close:hover {
  background: #f0e6d6;
  color: #142b20;
}

/* Success Screen */
.success-screen {
  padding: 60px 24px;
  text-align: center;
}

.success-ring {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: #142b20;
  color: #ffd98a;
  border: 2px solid #c6ad7a;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 18px;
}

.success-title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 800;
  color: #142b20;
}

.success-sub {
  margin: 0 auto;
  max-width: 420px;
  font-size: 14px;
  color: #554e42;
  line-height: 1.5;
}

/* Scroll Content */
.dialog-content {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex: 1;
}

.bill-scroll {
  padding: 20px 24px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Bill Sections */
.bill-section {
  background: #fcfaf6;
  border: 1px solid #ded5c3;
  border-radius: 10px;
  padding: 14px 18px;
}

.section-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.sec-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.gold-bullet {
  color: #c6ad7a;
  font-size: 11px;
}

.section-title {
  font-size: 15px;
  font-weight: 800;
  color: #142b20;
}

.section-cost {
  font-size: 16px;
  font-weight: 800;
  color: #142b20;
}

.section-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #635b4c;
}

.meta-tag {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 8px;
}

.tag-active {
  background: #dcfce7;
  color: #166534;
}

.tag-completed {
  background: #f1f5f9;
  color: #475569;
}

/* Late Arrival Callout */
.late-arrival-callout {
  margin-top: 10px;
  background: #fffbeb;
  border: 1px solid #fef3c7;
  border-radius: 6px;
  padding: 8px 12px;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  color: #92400e;
  line-height: 1.4;
}

.callout-icon {
  color: #d97706;
  font-size: 13px;
  flex-shrink: 0;
  margin-top: 1px;
}

.callout-text strong {
  display: block;
  font-weight: 700;
  margin-bottom: 2px;
}

/* Itemized Lists */
.itemized-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 10px 0;
}

.item-line {
  display: flex;
  justify-content: space-between;
  font-size: 13.5px;
  color: #3f392f;
}

.line-name {
  font-weight: 600;
}

.line-price {
  font-weight: 700;
}

.prepaid-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  font-weight: 700;
  color: #15803d;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  padding: 3px 8px;
  border-radius: 6px;
  margin-top: 6px;
}

/* In-Play Orders */
.empty-inplay-note {
  font-size: 13px;
  color: #8c8270;
  font-style: italic;
  padding: 8px 0;
}

.inplay-orders-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 8px;
}

.inplay-order-card {
  background: #ffffff;
  border: 1px solid #e7ded0;
  border-radius: 8px;
  padding: 10px 14px;
}

.order-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 12px;
}

.order-ref-num {
  font-weight: 800;
  color: #142b20;
}

.order-time {
  color: #7d7363;
}

.tab-pill {
  background: #fef3c7;
  color: #92400e;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 8px;
}

.order-items {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: #3f392f;
}

.order-sub-item {
  display: flex;
  justify-content: space-between;
}

.order-note-line {
  margin-top: 6px;
  font-size: 12px;
  color: #736959;
  font-style: italic;
}

/* Bill Summary Footer */
.bill-summary-footer {
  background: #ffffff;
  border-top: 1.5px solid #c6ad7a;
  padding: 18px 24px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.summary-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #4b4435;
}

.summary-line.text-muted {
  font-size: 13px;
  color: #6b6353;
}

.text-green {
  color: #15803d;
  font-weight: 700;
}

.summary-divider {
  height: 1px;
  background: #e7ded0;
  margin: 4px 0;
}

.total-due-line {
  align-items: flex-start;
}

.due-label {
  font-size: 15px;
  font-weight: 800;
  color: #142b20;
  display: block;
}

.due-hint {
  font-size: 12px;
  color: #736959;
}

.due-amount {
  font-size: 26px;
  font-weight: 800;
  color: #142b20;
}

.due-amount.is-zero {
  color: #15803d;
}

/* Actions */
.bill-actions {
  margin-top: 6px;
}

.staff-payment-picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 10px;
  flex-wrap: wrap;
}

.staff-pm-label {
  font-size: 13px;
  font-weight: 700;
  color: #142b20;
}

.staff-pm-options {
  display: flex;
  gap: 6px;
}

.pm-radio-btn {
  border: 1px solid #d4c8b4;
  border-radius: 6px;
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  background: #faf6ee;
  color: #554e42;
  transition: all 0.15s;
}

.pm-radio-btn.active {
  background: #142b20;
  color: #ffd98a;
  border-color: #c6ad7a;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  border: 0;
}

.settle-btn {
  width: 100%;
  padding: 12px;
  font-size: 15px;
  font-weight: 800;
  border-radius: 8px;
  background: #142b20;
  color: #ffd98a;
  border: 1.5px solid #c6ad7a;
  cursor: pointer;
  transition: all 0.15s;
}

.settle-btn:hover {
  background: #1f3f30;
  box-shadow: 0 4px 14px rgba(20, 43, 32, 0.25);
}

.counter-pay-box {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fdf8ed;
  border: 1px solid #ecd8ab;
  border-radius: 8px;
  padding: 12px 16px;
  font-size: 13px;
  color: #5d4a1b;
}

.counter-pay-box p {
  margin: 2px 0 0;
  font-size: 12px;
  color: #7a6631;
}

.gold-icon {
  color: #c6ad7a;
  flex-shrink: 0;
}

.customer-pay-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 14px;
}

.btn-customer-qr {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 13px 20px;
  background: #142a1f;
  border: 1px solid #c6ad7a;
  border-radius: 6px;
  color: #f6eedb;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: all 140ms ease;
  box-shadow: 0 2px 8px rgba(20, 42, 31, 0.2);
}

.btn-customer-qr:hover {
  background: #244c38;
  border-color: #dfc999;
  color: #ffffff;
}

.pay-method-sep {
  text-align: center;
  position: relative;
  font-size: 11px;
  color: #8c7e63;
}

.pay-method-sep::before,
.pay-method-sep::after {
  content: '';
  position: absolute;
  top: 50%;
  width: 25%;
  height: 1px;
  background: #e3d9c5;
}

.pay-method-sep::before {
  left: 0;
}
.pay-method-sep::after {
  right: 0;
}

.bill-qr-backdrop {
  position: fixed;
  inset: 0;
  z-index: 99999;
  background: rgba(12, 28, 20, 0.75);
  backdrop-filter: blur(5px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  overflow-y: auto;
}

.bill-qr-card {
  position: relative;
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #c6ad7a;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.45);
  padding: 28px 24px;
  max-width: 440px;
  width: 100%;
  text-align: center;
  margin: auto;
  animation: modalCenterPop 0.22s cubic-bezier(0.16, 1, 0.3, 1);
}

.qr-head h3 {
  font-size: 18px;
  font-weight: 800;
  color: #142a1f;
  margin: 6px 0;
}

.brand-eyebrow {
  font-size: 11px;
  font-weight: 800;
  color: #c6ad7a;
  letter-spacing: 0.1em;
}

.qr-desc {
  font-size: 13px;
  color: #556b60;
  margin: 0;
}

.qr-img-wrap {
  display: inline-block;
  padding: 12px;
  background: #ffffff;
  border: 1px solid #d4c5a6;
  border-radius: 8px;
  margin: 16px 0 12px;
  box-shadow: 0 4px 12px rgba(20, 42, 31, 0.08);
}

.qr-amount-pill {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #fffaf0;
  border: 1px solid #e8dbbe;
  border-radius: 6px;
  margin-bottom: 10px;
}

.qr-amount-pill span {
  font-size: 13px;
  font-weight: 600;
  color: #5a4b2c;
}

.qr-amount-pill strong {
  font-size: 20px;
  font-weight: 800;
  color: #142a1f;
}

.qr-ref-tag {
  font-size: 12px;
  color: #7d6e52;
  margin-bottom: 16px;
}

.btn-confirm-table-paid {
  width: 100%;
  height: 44px;
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 8px;
}

.btn-close-qr {
  background: none;
  border: none;
  color: #777;
  font-size: 13px;
  cursor: pointer;
  padding: 6px 12px;
}

.btn-close-qr:hover {
  color: #142a1f;
  text-decoration: underline;
}

@keyframes modalCenterPop {
  0% {
    opacity: 0;
    transform: scale(0.92) translateY(12px);
  }
  100% {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}
</style>

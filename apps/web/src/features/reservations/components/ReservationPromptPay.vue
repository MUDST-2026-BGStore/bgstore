<script setup lang="ts">
import { computed, watch } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import { getReservation, type Reservation } from '@theparlor/api-client';
import { useStoreText } from '../../../shared/lib/store-text';
import { formatBaht } from '../data/booking';

const props = defineProps<{ reservation: Reservation }>();
const emit = defineEmits<{ updated: [reservation: Reservation] }>();
const { locale } = useStoreText();
const th = computed(() => locale.value === 'th-TH');
const pending = computed(
  () =>
    props.reservation.status === 'CONFIRMED' &&
    props.reservation.payment?.method === 'PROMPTPAY' &&
    props.reservation.payment.status === 'PENDING',
);
const paymentQuery = useQuery({
  queryKey: computed(() => [
    'reservations',
    'payment',
    props.reservation.branchId,
    props.reservation.id,
  ]),
  queryFn: ({ signal }) =>
    getReservation(
      props.reservation.branchId,
      props.reservation.id,
      undefined,
      signal,
    ),
  enabled: pending,
  refetchInterval: (query) =>
    query.state.data?.status === 'CANCELLED' ||
    query.state.data?.payment?.status === 'CAPTURED'
      ? false
      : 3000,
});
watch(paymentQuery.data, (reservation) => {
  if (reservation) emit('updated', reservation);
});
const payment = computed(() => props.reservation.payment);
const deadline = computed(() =>
  payment.value?.expiresAt
    ? new Date(payment.value.expiresAt).toLocaleString(
        th.value ? 'th-TH' : 'en-US',
        { timeZone: 'Asia/Bangkok' },
      )
    : '',
);
</script>

<template>
  <section
    v-if="payment?.method === 'PROMPTPAY'"
    class="reservation-payment"
    aria-live="polite"
  >
    <h3>{{ th ? 'ชำระเงินผ่านพร้อมเพย์' : 'PromptPay payment' }}</h3>
    <p>
      <strong>{{ formatBaht(payment.amount.amountMinor) }}</strong> ·
      {{
        payment.status === 'CAPTURED'
          ? th
            ? 'ชำระแล้ว'
            : 'Paid'
          : payment.status === 'VOIDED'
            ? th
              ? 'ยกเลิกแล้ว'
              : 'Cancelled'
            : th
              ? 'รอชำระเงิน'
              : 'Awaiting payment'
      }}
    </p>
    <template v-if="pending">
      <img
        v-if="payment.promptPayQr"
        :src="payment.promptPayQr"
        :alt="
          th
            ? 'คิวอาร์โค้ดพร้อมเพย์สำหรับการจองนี้'
            : 'PromptPay QR code for this reservation'
        "
        width="240"
        height="240"
      />
      <p>
        {{ th ? 'สแกนชำระเงินภายใน' : 'Scan and pay by' }} {{ deadline }} ({{
          th ? 'เวลาไทย' : 'Thailand time'
        }}).
      </p>
      <p>
        {{
          th
            ? 'ระบบยืนยันอัตโนมัติหลังได้รับเงิน หากไม่ชำระภายในกำหนด การจองจะถูกยกเลิก'
            : 'Payment is confirmed automatically. Unpaid reservations are cancelled after the deadline.'
        }}
      </p>
      <p>
        {{
          th
            ? 'การชำระเงินนี้รวมค่าโต๊ะ เกม และอาหารที่สั่งล่วงหน้า เวลาหรืออาหารที่เพิ่มภายหลังชำระที่เคาน์เตอร์ ไม่รองรับการคืนเงินหรือเปลี่ยนเวลาหลังชำระ'
            : 'Includes booked table and game hours and food pre-orders. Extra time and later food are paid at the counter. Paid bookings cannot be cancelled or rescheduled; refunds are unavailable.'
        }}
      </p>
      <p v-if="paymentQuery.isError.value" role="alert">
        {{
          th
            ? 'ตรวจสอบสถานะไม่สำเร็จ กำลังลองอีกครั้ง'
            : 'Payment status could not be checked. Retrying…'
        }}
      </p>
    </template>
    <p v-else-if="reservation.status === 'CANCELLED'">
      {{
        th
          ? 'การจองถูกยกเลิกแล้ว กรุณาจองใหม่หากต้องการมาใช้บริการ'
          : 'This reservation was cancelled. Make a new booking to visit.'
      }}
    </p>
  </section>
</template>

<style scoped>
.reservation-payment {
  padding: 16px;
  border: 1px solid var(--ui-stroke, #c9bda7);
  border-radius: 8px;
  margin-block: 16px;
}
h3 {
  margin: 0 0 8px;
  font-size: 1.1rem;
}
p {
  margin: 8px 0;
  line-height: 1.5;
}
img {
  display: block;
  max-width: 100%;
  height: auto;
  background: white;
  margin-block: 12px;
}
</style>

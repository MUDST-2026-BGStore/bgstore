<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query';
import StaffLayout from '../../layouts/StaffLayout.vue';
import UiBadge from '../../components/ui/UiBadge.vue';
import UiButton from '../../components/ui/UiButton.vue';
import UiSelect from '../../components/ui/UiSelect.vue';
import UiTextInput from '../../components/ui/UiTextInput.vue';
import {
  checkInReservationRequest,
  checkOutReservationRequest,
  sessionsQueryOptions,
} from '../../queries/play-sessions';
import type {
  PaymentMethod,
  ReservationResponse,
} from '../../generated/api/types.gen';

const { t, locale } = useI18n();
const queryClient = useQueryClient();
const sessions = useQuery(sessionsQueryOptions());
const rows = computed(() => sessions.data.value?.items ?? []);

/** The booking date is an ISO string for new records and free text for old ones. */
const bookingDate = (value: string) => {
  const parsed = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(parsed.getTime())
    ? value
    : new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium' }).format(
        parsed,
      );
};

const isCheckedIn = (session: ReservationResponse) =>
  session.status === 'CheckedIn';

const actionError = ref<string | null>(null);
const checkIn = useMutation({
  mutationFn: (reservationId: string) =>
    checkInReservationRequest(reservationId),
  onSuccess: () => {
    actionError.value = null;
    void queryClient.invalidateQueries({ queryKey: ['play-sessions'] });
  },
  onError: () => {
    actionError.value = t('sessions.checkInFailed');
  },
});

const checkoutTarget = ref<ReservationResponse | null>(null);
const finalAmount = ref('');
const paymentMethod = ref<PaymentMethod>('Cash');
const amountError = ref<string | null>(null);

const paymentOptions = computed(() =>
  (['Cash', 'PromptPay', 'BankTransfer', 'Waived'] as PaymentMethod[]).map(
    (value) => ({ value, label: t(`sessions.payment.${value}`) }),
  ),
);

const openCheckout = (session: ReservationResponse) => {
  checkoutTarget.value = session;
  finalAmount.value = session.totalPrice > 0 ? String(session.totalPrice) : '';
  paymentMethod.value = 'Cash';
  amountError.value = null;
  actionError.value = null;
};

const closeCheckout = () => {
  checkoutTarget.value = null;
  amountError.value = null;
};

const isWaived = computed(() => paymentMethod.value === 'Waived');

const checkOut = useMutation({
  mutationFn: () => {
    const target = checkoutTarget.value;
    if (!target) {
      throw new Error('No session selected.');
    }
    return checkOutReservationRequest(
      target.id,
      isWaived.value ? 0 : Number(finalAmount.value),
      paymentMethod.value,
    );
  },
  onSuccess: () => {
    closeCheckout();
    void queryClient.invalidateQueries({ queryKey: ['play-sessions'] });
  },
});

const confirmCheckout = () => {
  const amount = isWaived.value ? 0 : Number(finalAmount.value);
  if (!isWaived.value && finalAmount.value.trim() === '') {
    amountError.value = t('sessions.amountRequired');
    return;
  }
  if (!Number.isInteger(amount) || amount < 0) {
    amountError.value = t('sessions.amountRequired');
    return;
  }
  if (!isWaived.value && amount <= 0) {
    amountError.value = t('sessions.amountPositive');
    return;
  }
  amountError.value = null;
  checkOut.mutate();
};
</script>

<template>
  <StaffLayout active="sessions">
    <div
      class="staff-page-content flex w-full flex-col items-start gap-6 px-10 pt-8 pb-10"
      data-page="session-console"
    >
      <div>
        <h1
          class="staff-page-title text-[28px] leading-[34px] font-semibold tracking-[-0.084px] text-ink"
        >
          {{ t('sessions.title') }}
        </h1>
        <p class="mt-1 text-[14px] leading-[22px] text-ink-muted">
          {{ t('sessions.description') }}
        </p>
      </div>

      <div
        v-if="actionError"
        class="w-full rounded-lg border border-danger-border bg-surface p-4 text-danger-fg"
        role="alert"
      >
        {{ actionError }}
      </div>

      <div
        v-if="sessions.isError.value"
        class="w-full rounded-lg border border-danger-border bg-surface p-6 text-danger-fg"
        role="alert"
      >
        <p>{{ t('sessions.loadError') }}</p>
        <UiButton
          class="mt-3"
          variant="outline"
          size="sm"
          @click="sessions.refetch()"
        >
          {{ t('sessions.retry') }}
        </UiButton>
      </div>
      <div
        v-else-if="sessions.isPending.value"
        class="w-full rounded-lg border border-line bg-surface p-8 text-ink-muted"
        aria-live="polite"
      >
        {{ t('sessions.loading') }}
      </div>
      <div
        v-else-if="rows.length === 0"
        class="w-full rounded-lg border border-line bg-surface p-8 text-ink-muted"
      >
        {{ t('sessions.empty') }}
      </div>
      <div
        v-else
        class="staff-table-shell w-full overflow-x-auto rounded-lg border border-line bg-surface"
      >
        <table class="w-full min-w-[860px] table-fixed border-collapse">
          <thead
            class="border-b border-line bg-surface-sunken text-[13px] leading-5 font-medium text-ink-secondary"
          >
            <tr class="h-11">
              <th scope="col" class="w-[160px] px-4 text-left font-medium">
                {{ t('sessions.columns.table') }}
              </th>
              <th scope="col" class="px-4 text-left font-medium">
                {{ t('sessions.columns.client') }}
              </th>
              <th scope="col" class="w-[110px] px-4 text-center font-medium">
                {{ t('sessions.columns.party') }}
              </th>
              <th scope="col" class="w-[210px] px-4 text-left font-medium">
                {{ t('sessions.columns.booking') }}
              </th>
              <th scope="col" class="w-[150px] px-4 text-center font-medium">
                {{ t('sessions.columns.status') }}
              </th>
              <th scope="col" class="w-[150px] px-4 text-right font-medium">
                {{ t('sessions.columns.actions') }}
              </th>
            </tr>
          </thead>
          <tbody class="text-[14px] leading-[22px] text-ink">
            <tr
              v-for="session in rows"
              :key="session.id"
              class="h-[52px] border-b border-line last:border-0"
            >
              <td class="truncate px-4">{{ session.tableName }}</td>
              <td class="truncate px-4">{{ session.customerName }}</td>
              <td class="px-4 text-center">
                {{ t('sessions.seats', { count: session.partySize }) }}
              </td>
              <td class="px-4">
                {{ bookingDate(session.date) }} · {{ session.timeSlot }}
              </td>
              <td class="px-4 text-center">
                <UiBadge :tone="isCheckedIn(session) ? 'info' : 'warning'">
                  {{ t(`sessions.status.${session.status}`) }}
                </UiBadge>
              </td>
              <td class="px-4 text-right whitespace-nowrap">
                <UiButton
                  v-if="isCheckedIn(session)"
                  variant="outline"
                  size="sm"
                  @click="openCheckout(session)"
                >
                  {{ t('sessions.checkOut') }}
                </UiButton>
                <UiButton
                  v-else
                  size="sm"
                  :disabled="checkIn.isPending.value"
                  @click="checkIn.mutate(session.id)"
                >
                  {{ t('sessions.checkIn') }}
                </UiButton>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div
      v-if="checkoutTarget"
      class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="checkout-dialog-title"
    >
      <form
        class="w-full max-w-[440px] rounded-lg border border-line bg-surface p-6"
        @submit.prevent="confirmCheckout"
      >
        <h2
          id="checkout-dialog-title"
          class="text-[18px] leading-6 font-semibold text-ink"
        >
          {{ t('sessions.dialogTitle') }}
        </h2>
        <p class="mt-1 text-[13px] leading-5 text-ink-muted">
          {{ t('sessions.dialogDescription') }}
        </p>

        <p class="mt-4 text-[13px] text-ink-secondary">
          {{ checkoutTarget.tableName }} ·
          {{ t('sessions.seats', { count: checkoutTarget.partySize }) }}
        </p>

        <div class="mt-4 flex flex-col gap-1.5">
          <label
            for="checkout-amount"
            class="text-[13px] font-medium text-ink-secondary"
          >
            {{ t('sessions.finalAmount') }}
          </label>
          <UiTextInput
            id="checkout-amount"
            v-model="finalAmount"
            inputmode="numeric"
            :invalid="amountError !== null"
            :disabled="isWaived"
          />
        </div>

        <div class="mt-4 flex flex-col gap-1.5">
          <label
            for="checkout-method"
            class="text-[13px] font-medium text-ink-secondary"
          >
            {{ t('sessions.paymentMethod') }}
          </label>
          <UiSelect
            id="checkout-method"
            v-model="paymentMethod"
            :options="paymentOptions"
          />
        </div>

        <p v-if="isWaived" class="mt-2 text-[13px] leading-5 text-ink-muted">
          {{ t('sessions.waivedNote') }}
        </p>

        <p
          v-if="amountError"
          class="mt-2 text-[13px] leading-5 text-danger-fg"
          role="alert"
        >
          {{ amountError }}
        </p>
        <p
          v-else-if="checkOut.isError.value"
          class="mt-2 text-[13px] leading-5 text-danger-fg"
          role="alert"
        >
          {{ t('sessions.checkOutFailed') }}
        </p>

        <div class="mt-6 flex justify-end gap-2">
          <UiButton variant="outline" @click="closeCheckout">
            {{ t('sessions.cancel') }}
          </UiButton>
          <UiButton type="submit" :disabled="checkOut.isPending.value">
            {{ t('sessions.confirm') }}
          </UiButton>
        </div>
      </form>
    </div>
  </StaffLayout>
</template>

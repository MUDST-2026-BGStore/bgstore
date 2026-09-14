<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import type {
  ReservationBranchOption,
  ReservationClientOption,
} from '../../features/reservations/reservation-types';

const props = defineProps<{
  clientSearch: string;
  branchId: string;
  firstName: string;
  lastName: string;
  nickname: string;
  phone: string;
  clients: ReservationClientOption[];
  branches: ReservationBranchOption[];
  clientMode?: boolean;
  clientsPending?: boolean;
  clientsError?: unknown;
}>();

const emit = defineEmits<{
  next: [];
  'select-client': [client: ReservationClientOption];
  'update:clientSearch': [value: string];
  'update:branchId': [value: string];
  'update:firstName': [value: string];
  'update:lastName': [value: string];
  'update:nickname': [value: string];
  'update:phone': [value: string];
}>();

const { t } = useI18n();
const canContinue = computed(
  () =>
    props.branchId.length > 0 &&
    props.firstName.trim().length > 0 &&
    props.lastName.trim().length > 0 &&
    props.phone.trim().length > 0,
);

const updateClientSearch = (value: string) => {
  emit('update:clientSearch', value);
  const normalizedValue = value.trim().toLocaleLowerCase();
  const selectedClient = props.clients.find(
    (client) => client.displayName.toLocaleLowerCase() === normalizedValue,
  );
  if (selectedClient) {
    emit('select-client', selectedClient);
  }
};
</script>

<template>
  <form class="client-step-form" @submit.prevent="emit('next')">
    <label v-if="!clientMode" class="field field--wide">
      <span>{{ t('reservation.clientSearch') }}</span>
      <input
        id="reservation-client-search"
        :value="clientSearch"
        type="search"
        list="reservation-client-options"
        :placeholder="t('reservation.clientSearchPlaceholder')"
        autocomplete="off"
        @input="updateClientSearch(($event.target as HTMLInputElement).value)"
      />
      <small v-if="clientsPending" role="status">{{
        t('reservation.loadingClients')
      }}</small>
      <small v-else-if="clientsError" role="alert">{{
        t('reservation.clientsError')
      }}</small>
      <datalist id="reservation-client-options">
        <option
          v-for="client in clients"
          :key="client.id"
          :value="client.displayName"
        />
      </datalist>
    </label>

    <label class="field field--wide">
      <span>{{ t('reservation.branch') }} <b aria-hidden="true">*</b></span>
      <select
        id="reservation-branch"
        :value="branchId"
        required
        @change="
          emit('update:branchId', ($event.target as HTMLSelectElement).value)
        "
      >
        <option v-for="branch in branches" :key="branch.id" :value="branch.id">
          {{ branch.name }}
        </option>
      </select>
    </label>

    <label class="field">
      <span>{{ t('reservation.firstName') }} <b aria-hidden="true">*</b></span>
      <input
        id="reservation-first-name"
        :value="firstName"
        type="text"
        :placeholder="t('reservation.firstNamePlaceholder')"
        autocomplete="given-name"
        required
        @input="
          emit('update:firstName', ($event.target as HTMLInputElement).value)
        "
      />
    </label>

    <label class="field">
      <span>{{ t('reservation.lastName') }} <b aria-hidden="true">*</b></span>
      <input
        id="reservation-last-name"
        :value="lastName"
        type="text"
        :placeholder="t('reservation.lastNamePlaceholder')"
        autocomplete="family-name"
        required
        @input="
          emit('update:lastName', ($event.target as HTMLInputElement).value)
        "
      />
    </label>

    <label class="field">
      <span>{{ t('reservation.nickname') }}</span>
      <input
        id="reservation-nickname"
        :value="nickname"
        type="text"
        :placeholder="t('reservation.nicknamePlaceholder')"
        autocomplete="off"
        @input="
          emit('update:nickname', ($event.target as HTMLInputElement).value)
        "
      />
    </label>

    <label class="field">
      <span
        >{{ t('reservation.phoneNumber') }} <b aria-hidden="true">*</b></span
      >
      <input
        id="reservation-phone"
        :value="phone"
        type="tel"
        :placeholder="t('reservation.phonePlaceholder')"
        autocomplete="tel"
        required
        @input="emit('update:phone', ($event.target as HTMLInputElement).value)"
      />
    </label>

    <div class="step-actions field--wide">
      <button class="primary-button" type="submit" :disabled="!canContinue">
        {{ t('reservation.next') }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.client-step-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1.45rem 2rem;
  padding: 2rem 2.6rem 2.1rem;
}

.field--wide {
  grid-column: 1 / -1;
}

.field {
  display: grid;
  gap: 0.55rem;
  min-width: 0;
  color: #20252d;
  font-size: 0.9rem;
}

.field b {
  color: #8d1d24;
}

input,
select {
  width: 100%;
  min-height: 2.9rem;
  box-sizing: border-box;
  padding: 0.65rem 0.85rem;
  border: 1px solid #dce2e6;
  border-radius: 0.55rem;
  color: #20252d;
  background: #fff;
  font: inherit;
}

input::placeholder {
  color: #718096;
}

input:focus,
select:focus {
  border-color: #497883;
  outline: none;
  box-shadow: 0 0 0 3px rgb(73 120 131 / 16%);
}

.step-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 0.25rem;
}

.primary-button {
  min-width: 8.7rem;
  min-height: 2.8rem;
  padding: 0.65rem 1.15rem;
  border: 1px solid #497883;
  border-radius: 0.55rem;
  color: #fff;
  background: #497883;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.primary-button:focus-visible {
  outline: 3px solid #20252d;
  outline-offset: 2px;
}

.primary-button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

@media (max-width: 640px) {
  .client-step-form {
    grid-template-columns: 1fr;
    padding: 1.4rem 1.15rem 1.6rem;
  }

  .field--wide {
    grid-column: auto;
  }

  .primary-button {
    width: 100%;
  }
}
</style>

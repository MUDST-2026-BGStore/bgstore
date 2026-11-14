<script setup lang="ts">
import { computed, ref } from 'vue';
import { useManagementWorkspace } from '../context';
import {
  AppBadge,
  AppButton,
  AppDataTable,
  AppEmptyState,
  AppField,
  AppPage,
  AppPanel,
  AppToolbar,
  AppSearchField,
  AppTableActions,
} from '../../../../shared/ui';
const columns = [
  { label: 'Arrival' },
  { label: 'Table' },
  { label: 'Guest' },
  { label: 'Status' },
  { label: 'Contact' },
  { label: 'Actions', action: true },
];
const {
  allReservations,
  formatTime,
  getCustomerName,
  getCustomerPhone,
  handleCheckIn,
  openCancel,
  openNoShow,
  openCheckBill,
  actionInProgress,
  tableLabel,
} = useManagementWorkspace();
const search = ref('');
const status = ref('ALL');
const statuses = [
  { id: 'CONFIRMED', label: 'Confirmed' },
  { id: 'PENDING', label: 'Pending' },
  { id: 'CHECKED_IN', label: 'Checked in' },
  { id: 'COMPLETED', label: 'Completed' },
  { id: 'CANCELLED', label: 'Cancelled' },
  { id: 'NO_SHOW', label: 'No show' },
];
const visibleBookings = computed(() =>
  allReservations.value.filter(
    (res) =>
      (status.value === 'ALL' || res.status === status.value) &&
      `${getCustomerName(res.customer)} ${tableLabel(res.tableId)} ${getCustomerPhone(res.customer)}`
        .toLowerCase()
        .includes(search.value.trim().toLowerCase()),
  ),
);
</script>

<template>
  <AppPage title="Bookings">
    <AppPanel aria-label="Branch bookings">
      <AppToolbar>
        <div class="workspace-filters">
          <AppSearchField
            v-model="search"
            label="Search bookings"
            placeholder="Guest, table, or phone"
          /><AppField label="Status" input-id="booking-status" layout="inline"
            ><select
              id="booking-status"
              v-model="status"
              aria-label="Booking status"
            >
              <option value="ALL">All statuses</option>
              <option v-for="item in statuses" :key="item.id" :value="item.id">
                {{ item.label }}
              </option>
            </select></AppField
          >
        </div>
        <span class="workspace-results" role="status"
          >{{ visibleBookings.length }} of
          {{ allReservations.length }} bookings</span
        >
      </AppToolbar>
      <AppEmptyState
        v-if="!visibleBookings.length"
        :title="
          allReservations.length
            ? 'No bookings match these filters'
            : 'No bookings for this branch'
        "
        ><AppButton
          v-if="search || status !== 'ALL'"
          @click="
            search = '';
            status = 'ALL';
          "
          >Clear filters</AppButton
        ></AppEmptyState
      >
      <AppDataTable
        v-else
        class="workspace-records"
        label="Bookings table"
        caption="Reservations and available check-in or cancellation actions"
        :columns="columns"
      >
        <tr v-for="res in visibleBookings" :key="res.id">
          <td>
            <strong class="record-primary">{{
              formatTime(res.startsAt)
            }}</strong
            ><span class="record-secondary">{{
              new Date(res.startsAt).toLocaleDateString([], {
                month: 'short',
                day: 'numeric',
                year: 'numeric',
              })
            }}</span>
          </td>
          <td>{{ tableLabel(res.tableId) }}</td>
          <td>
            <strong class="record-primary">{{
              getCustomerName(res.customer)
            }}</strong
            ><span class="record-secondary">{{ res.partySize }} guests</span>
          </td>
          <td>
            <AppBadge
              :tone="
                ['CANCELLED', 'NO_SHOW'].includes(res.status)
                  ? 'danger'
                  : res.status === 'CHECKED_IN'
                    ? 'success'
                    : 'neutral'
              "
              >{{
                statuses.find((item) => item.id === res.status)?.label ||
                res.status
              }}</AppBadge
            >
          </td>
          <td>{{ getCustomerPhone(res.customer) || '—' }}</td>
          <AppTableActions>
            <AppButton
              v-if="res.status === 'CONFIRMED'"
              appearance="primary"
              :disabled="actionInProgress === res.id"
              @click="handleCheckIn(res)"
              ><i class="fa-solid fa-check" aria-hidden="true"></i>Check in &
              seat</AppButton
            >
            <AppButton
              v-if="res.status === 'CHECKED_IN'"
              appearance="primary"
              @click="openCheckBill(res)"
              ><i class="fa-solid fa-receipt" aria-hidden="true"></i>Check
              bill</AppButton
            >
            <AppButton
              v-if="
                res.status === 'CONFIRMED' &&
                Date.parse(res.startsAt) <= Date.now()
              "
              intent="danger"
              @click="openNoShow(res)"
              >No-show</AppButton
            >
            <AppButton
              v-if="res.status === 'CONFIRMED'"
              intent="danger"
              @click="openCancel(res)"
              >Cancel booking</AppButton
            >
            <span
              v-if="!['CONFIRMED', 'CHECKED_IN'].includes(res.status)"
              class="record-secondary"
              >—</span
            >
          </AppTableActions>
        </tr>
      </AppDataTable>
    </AppPanel>
  </AppPage>
</template>

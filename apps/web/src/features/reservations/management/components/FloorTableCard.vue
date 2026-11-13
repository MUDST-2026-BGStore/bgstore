<script setup lang="ts">
import { computed } from 'vue';
import { AppBadge, AppButton } from '../../../../shared/ui';
import { useManagementWorkspace } from '../context';
import type { useWorkspaceReservations } from '../useWorkspaceReservations';

const {
  getTableStatus,
  getTableAssignedGames,
  getElapsedMinutes,
  getCustomerName,
  calculateCurrentPlayFee,
  formatTime,
  openWalkInForTable,
  openAddSnacks,
  openAssignGameForTable,
  unassignGameFromTable,
  openCheckBill,
  handleCheckIn,
  actionInProgress,
} = useManagementWorkspace();
const props = defineProps<{
  table: ReturnType<
    typeof useWorkspaceReservations
  >['branchFloorTables'][number];
}>();
const status = computed(() => getTableStatus(props.table));
const assignedGames = computed(() => getTableAssignedGames(props.table.id));
</script>

<template>
  <article class="floor-service-table" :aria-label="`Table ${table.number}`">
    <header class="floor-service-header">
      <h2>Table {{ table.number }}</h2>
      <AppBadge
        :tone="
          status.state === 'OCCUPIED'
            ? 'success'
            : status.state === 'RESERVED'
              ? 'warning'
              : 'neutral'
        "
        >{{
          status.state === 'OCCUPIED'
            ? 'In play'
            : status.state === 'RESERVED'
              ? 'Reserved'
              : 'Free'
        }}</AppBadge
      >
    </header>
    <p class="floor-service-capacity">
      {{ table.seats }} seats <span aria-hidden="true">·</span> ฿{{
        table.rate
      }}/hr
    </p>
    <div class="floor-service-body">
      <template v-if="status.state === 'OCCUPIED'">
        <strong class="floor-service-guest">{{
          getCustomerName(status.reservation?.customer)
        }}</strong>
        <p class="floor-service-context">
          {{ status.reservation?.partySize }} guests ·
          {{ getElapsedMinutes(status.reservation) }} min played
        </p>
        <dl class="floor-service-fee">
          <dt>Est. table fee</dt>
          <dd>฿{{ calculateCurrentPlayFee(status.reservation) }}</dd>
        </dl>
        <div class="floor-service-game">
          <span class="floor-service-label">Game</span
          ><template v-if="assignedGames.length"
            ><div
              v-for="game in assignedGames"
              :key="game"
              class="floor-game-line"
            >
              <strong>{{ game }}</strong
              ><AppButton
                appearance="subtle"
                :aria-label="`Return ${game} from Table ${table.number}`"
                @click="unassignGameFromTable(table.id, game)"
                >Return to shelf</AppButton
              >
            </div></template
          >
          <div v-else class="floor-game-line">
            <span class="floor-service-context">No game assigned</span
            ><AppButton
              :aria-label="`Assign game to Table ${table.number}`"
              @click="openAssignGameForTable(table.id)"
              >Assign game</AppButton
            >
          </div>
        </div>
      </template>
      <template v-else-if="status.state === 'RESERVED'">
        <strong class="floor-service-guest">{{
          getCustomerName(status.reservation?.customer)
        }}</strong>
        <p class="floor-service-context">
          Party of {{ status.reservation?.partySize }}
        </p>
        <dl class="floor-service-fee">
          <dt>Arrival</dt>
          <dd>{{ formatTime(status.reservation?.startsAt) }}</dd>
        </dl>
      </template>
      <template v-else
        ><strong class="floor-service-guest">Available</strong></template
      >
    </div>
    <footer class="floor-service-actions">
      <template v-if="status.state === 'OCCUPIED'"
        ><AppButton
          :aria-label="`Add refreshments to Table ${table.number}`"
          @click="openAddSnacks(status.reservation)"
          >Refreshments</AppButton
        ><AppButton
          appearance="primary"
          :aria-label="`Check bill for Table ${table.number}`"
          @click="openCheckBill(status.reservation)"
          ><i class="fa-solid fa-receipt" aria-hidden="true"></i>Check
          bill</AppButton
        ></template
      >
      <AppButton
        v-else-if="status.state === 'RESERVED'"
        appearance="primary"
        :disabled="actionInProgress === status.reservation?.id"
        :aria-label="`Check in and seat at Table ${table.number}`"
        @click="handleCheckIn(status.reservation)"
        >Check in & seat</AppButton
      >
      <AppButton
        v-else
        appearance="primary"
        :aria-label="`Seat walk-in at Table ${table.number}`"
        @click="openWalkInForTable(table)"
        ><i class="fa-solid fa-plus" aria-hidden="true"></i>Seat
        walk-in</AppButton
      >
    </footer>
  </article>
</template>

<style scoped>
.floor-service-table {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border: 1px solid var(--ui-stroke-subtle);
  border-radius: var(--ui-radius-lg);
  background: var(--ui-surface);
}
.floor-service-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 16px 0;
}
.floor-service-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--ui-text);
}
.floor-service-capacity {
  margin: 4px 16px 0;
  font-size: 12px;
  color: var(--ui-muted);
}
.floor-service-body {
  flex: 1;
  padding: 18px 16px;
}
.floor-service-guest {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--ui-text);
}
.floor-service-context {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--ui-muted);
}
.floor-service-fee {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 12px;
  margin: 14px 0 0;
}
.floor-service-fee dt,
.floor-service-label {
  font-size: 12px;
  color: var(--ui-muted);
}
.floor-service-fee dd {
  margin: 0;
  font-size: 17px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  color: var(--ui-text);
}
.floor-service-game {
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px solid var(--ui-divider);
}
.floor-game-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 4px 8px;
}
.floor-game-line strong {
  font-size: 13px;
  font-weight: 500;
}
.floor-service-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--ui-divider);
  background: var(--ui-surface-subtle);
}
</style>

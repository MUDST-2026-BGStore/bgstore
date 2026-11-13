<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import {
  adjustMenuStock,
  getBranchMenu,
  type BranchMenuItem,
} from '@theparlor/api-client';
import {
  AppButton,
  AppDialog,
  AppDialogPanel,
  AppField,
} from '../../../../shared/ui';
import { useManagementWorkspace } from '../context';

const { selectedBranchId } = useManagementWorkspace();
const queryClient = useQueryClient();
const menu = useQuery({
  queryKey: ['branch-menu', selectedBranchId],
  queryFn: ({ signal }) =>
    getBranchMenu(selectedBranchId.value, undefined, signal),
  enabled: computed(() => !!selectedBranchId.value),
});
const items = computed(() => menu.data.value?.items ?? []);
const target = ref<BranchMenuItem | null>(null);
const delta = ref(1);
const reason = ref('');
const saving = ref(false);
const error = ref('');
let adjustmentTrigger: HTMLElement | null = null;
const canSubmit = computed(
  () =>
    !!target.value &&
    Number.isSafeInteger(delta.value) &&
    delta.value >= -2147483648 &&
    delta.value <= 2147483647 &&
    delta.value !== 0 &&
    reason.value.trim().length > 0 &&
    reason.value.trim().length <= 500 &&
    !saving.value,
);

function openAdjustment(item: BranchMenuItem, event: MouseEvent) {
  adjustmentTrigger = event.currentTarget as HTMLElement;
  target.value = item;
  delta.value = 1;
  reason.value = '';
  error.value = '';
}
async function closeAdjustment() {
  if (saving.value) return;
  target.value = null;
  await nextTick();
  adjustmentTrigger?.focus({ preventScroll: true });
}
watch(selectedBranchId, () => {
  target.value = null;
  error.value = '';
});
async function submit() {
  const item = target.value;
  const branchId = selectedBranchId.value;
  if (!item || !branchId || !canSubmit.value) return;
  saving.value = true;
  error.value = '';
  try {
    await adjustMenuStock(branchId, item.item.id, {
      delta: delta.value,
      reason: reason.value.trim(),
    });
    await queryClient.invalidateQueries({
      queryKey: ['branch-menu', branchId],
    });
    if (selectedBranchId.value === branchId) {
      saving.value = false;
      await closeAdjustment();
    }
  } catch {
    error.value =
      'Stock could not be updated. Check the quantity and try again.';
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <section class="pantry-stock" aria-labelledby="pantry-stock-title">
    <div class="section-heading">
      <div>
        <h2 id="pantry-stock-title">Food & drinks</h2>
        <p>Available to serve at this branch, after reserved pre-orders.</p>
      </div>
      <button
        class="pantry-button"
        type="button"
        :disabled="!selectedBranchId || menu.isFetching.value"
        @click="menu.refetch()"
      >
        Refresh
      </button>
    </div>
    <p v-if="!selectedBranchId">
      Select a branch to view food and drink stock.
    </p>
    <p v-else-if="menu.isPending.value">Loading food and drink stock…</p>
    <div v-else-if="menu.isError.value" role="alert">
      <p>Food and drink stock could not be loaded.</p>
      <button class="pantry-button" type="button" @click="menu.refetch()">
        Try again
      </button>
    </div>
    <p v-else-if="!items.length">
      No food or drink items are available for this branch.
    </p>
    <div v-else class="stock-table-wrap">
      <table>
        <thead>
          <tr>
            <th scope="col">Item</th>
            <th scope="col">SKU</th>
            <th scope="col">Available</th>
            <th scope="col">Action</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.item.id">
            <th scope="row">{{ row.item.names['en-US'] }}</th>
            <td>{{ row.item.sku }}</td>
            <td>{{ row.available }}</td>
            <td>
              <button
                class="pantry-button"
                type="button"
                @click="openAdjustment(row, $event)"
              >
                Adjust stock
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <AppDialog
      v-if="target"
      title-id="pantry-adjust-title"
      size="compact"
      @close="closeAdjustment"
    >
      <AppDialogPanel
        title-id="pantry-adjust-title"
        :title="`Adjust ${target.item.names['en-US']}`"
        as="form"
        @submit.prevent="submit"
        @close="closeAdjustment"
      >
        <p>
          {{ target.available }} available to serve. Enter a positive receipt or
          negative correction.
        </p>
        <AppField label="Change in units" input-id="pantry-delta">
          <input
            id="pantry-delta"
            v-model.number="delta"
            type="number"
            step="1"
            required
          />
        </AppField>
        <AppField label="Reason" input-id="pantry-reason">
          <input id="pantry-reason" v-model="reason" maxlength="500" required />
        </AppField>
        <p v-if="error" role="alert">{{ error }}</p>
        <template #footer>
          <AppButton :disabled="saving" @click="closeAdjustment"
            >Cancel</AppButton
          >
          <AppButton type="submit" appearance="primary" :disabled="!canSubmit">
            {{ saving ? 'Saving…' : 'Save adjustment' }}
          </AppButton>
        </template>
      </AppDialogPanel>
    </AppDialog>
  </section>
</template>

<style scoped>
.pantry-stock {
  margin-top: 20px;
  padding: 20px;
  border: 1px solid var(--ui-divider);
  border-radius: var(--ui-radius);
  background: var(--ui-surface);
  color: var(--ui-text);
}
.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
h2 {
  margin: 0;
  font-size: 18px;
}
.section-heading p {
  margin: 4px 0 16px;
  color: var(--ui-muted);
}
.pantry-button {
  padding: 7px 12px;
  border: 1px solid var(--ui-divider);
  border-radius: var(--ui-radius);
  background: var(--ui-surface);
  color: var(--ui-text);
  font: inherit;
  cursor: pointer;
}
.pantry-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.pantry-button:focus-visible,
input:focus-visible {
  outline: 2px solid var(--ui-brand);
  outline-offset: 2px;
}
.stock-table-wrap {
  overflow-x: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}
th,
td {
  padding: 10px;
  border-bottom: 1px solid var(--ui-divider);
}
td:nth-child(3) {
  font-variant-numeric: tabular-nums;
}
input {
  padding: 9px;
  border: 1px solid var(--ui-divider);
  border-radius: var(--ui-radius);
  background: var(--ui-surface);
  color: var(--ui-text);
  font: inherit;
}
</style>

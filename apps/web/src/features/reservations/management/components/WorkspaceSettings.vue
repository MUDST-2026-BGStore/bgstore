<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  updateBranch,
  type Branch,
  type OpeningPeriod,
  type OpeningPeriodDayOfWeek,
  type UpdateBranchRequest,
} from '@theparlor/api-client';
import {
  AppBadge,
  AppButton,
  AppCheckbox,
  AppField,
  AppFormActions,
  AppFormGrid,
  AppFormSection,
  AppMessageBar,
  AppPage,
  AppPanel,
  AppSelect,
} from '../../../../shared/ui';
import { queryClient as sharedQueryClient } from '../../../../shared/lib/api';
import { problemMessage } from '../../../../shared/lib/api-errors';
import { BRANCHES_QUERY_KEY } from '../../../../shared/stores/branch-directory';
import { useManagementWorkspace } from '../context';

interface EditablePeriod {
  id: number;
  dayOfWeek: OpeningPeriodDayOfWeek;
  opensAt: string;
  closesAt: string;
  untilMidnight: boolean;
}

const DAY_OPTIONS: { value: OpeningPeriodDayOfWeek; label: string }[] = [
  { value: 'MONDAY', label: 'Monday' },
  { value: 'TUESDAY', label: 'Tuesday' },
  { value: 'WEDNESDAY', label: 'Wednesday' },
  { value: 'THURSDAY', label: 'Thursday' },
  { value: 'FRIDAY', label: 'Friday' },
  { value: 'SATURDAY', label: 'Saturday' },
  { value: 'SUNDAY', label: 'Sunday' },
];

const DAY_ORDER: Record<OpeningPeriodDayOfWeek, number> = {
  MONDAY: 0,
  TUESDAY: 1,
  WEDNESDAY: 2,
  THURSDAY: 3,
  FRIDAY: 4,
  SATURDAY: 5,
  SUNDAY: 6,
};

const TIME_PATTERN = /^([01][0-9]|2[0-3]):[0-5][0-9]$/;

const { selectedBranch, selectedBranchId, queryClient } =
  useManagementWorkspace();

let nextRowId = 1;
const baseline = ref<Branch | undefined>(undefined);
const name = ref('');
const address = ref('');
const phone = ref('');
const periods = ref<EditablePeriod[]>([]);
const saving = ref(false);
const errorMessage = ref('');

function toEditablePeriod(period: OpeningPeriod): EditablePeriod {
  const untilMidnight = period.closesAt === '24:00';
  return {
    id: nextRowId++,
    dayOfWeek: period.dayOfWeek,
    opensAt: period.opensAt,
    closesAt: untilMidnight ? '23:00' : period.closesAt,
    untilMidnight,
  };
}

function resetFromBranch(branch: Branch | undefined) {
  baseline.value = branch;
  name.value = branch?.name ?? '';
  address.value = branch?.address ?? '';
  phone.value = branch?.phone ?? '';
  periods.value = (branch?.openingHours ?? []).map(toEditablePeriod);
}

watch(
  selectedBranch,
  (branch, previous) => {
    if (branch?.id !== previous?.id || !baseline.value) {
      errorMessage.value = '';
      resetFromBranch(branch);
    } else if (branch) {
      baseline.value = branch;
    }
  },
  { immediate: true },
);

const sortedPeriods = computed(() =>
  [...periods.value].sort(
    (a, b) =>
      DAY_ORDER[a.dayOfWeek] - DAY_ORDER[b.dayOfWeek] ||
      a.opensAt.localeCompare(b.opensAt) ||
      a.id - b.id,
  ),
);

function effectiveClosesAt(row: EditablePeriod): string {
  return row.untilMidnight ? '24:00' : row.closesAt;
}

function toOpeningPeriod(row: EditablePeriod): OpeningPeriod {
  return {
    dayOfWeek: row.dayOfWeek,
    opensAt: row.opensAt,
    closesAt: effectiveClosesAt(row),
  };
}

function sortOpeningPeriods(list: OpeningPeriod[]): OpeningPeriod[] {
  return [...list].sort(
    (a, b) =>
      DAY_ORDER[a.dayOfWeek] - DAY_ORDER[b.dayOfWeek] ||
      a.opensAt.localeCompare(b.opensAt) ||
      a.closesAt.localeCompare(b.closesAt),
  );
}

const periodsValid = computed(
  () =>
    periods.value.length <= 28 &&
    periods.value.every((row) => {
      const closes = effectiveClosesAt(row);
      return (
        Boolean(row.dayOfWeek) &&
        TIME_PATTERN.test(row.opensAt) &&
        (closes === '24:00' || TIME_PATTERN.test(closes)) &&
        closes > row.opensAt
      );
    }),
);

const normalizedPeriods = computed(() =>
  sortedPeriods.value.map(toOpeningPeriod),
);

const hoursChanged = computed(() => {
  const current = normalizedPeriods.value;
  const original = sortOpeningPeriods(baseline.value?.openingHours ?? []);
  if (current.length !== original.length) return true;
  return current.some(
    (period, index) =>
      period.dayOfWeek !== original[index].dayOfWeek ||
      period.opensAt !== original[index].opensAt ||
      period.closesAt !== original[index].closesAt,
  );
});

const changedPayload = computed<UpdateBranchRequest>(() => {
  const current = baseline.value;
  if (!current) return {};
  const payload: UpdateBranchRequest = {};
  const trimmedName = name.value.trim();
  const trimmedAddress = address.value.trim();
  const trimmedPhone = phone.value.trim();
  if (trimmedName !== current.name) payload.name = trimmedName;
  if (trimmedAddress !== current.address) payload.address = trimmedAddress;
  if (trimmedPhone.length > 0 && trimmedPhone !== (current.phone ?? '')) {
    payload.phone = trimmedPhone;
  }
  if (hoursChanged.value) {
    payload.openingHours = normalizedPeriods.value;
  }
  return payload;
});

const canSave = computed(() => {
  const trimmedName = name.value.trim();
  const trimmedAddress = address.value.trim();
  const trimmedPhone = phone.value.trim();
  return (
    Boolean(baseline.value) &&
    !saving.value &&
    trimmedName.length >= 1 &&
    trimmedName.length <= 120 &&
    trimmedAddress.length >= 1 &&
    trimmedAddress.length <= 500 &&
    trimmedPhone.length <= 40 &&
    periodsValid.value &&
    Object.keys(changedPayload.value).length > 0
  );
});

function addPeriod() {
  periods.value.push({
    id: nextRowId++,
    dayOfWeek: 'MONDAY',
    opensAt: '10:00',
    closesAt: '22:00',
    untilMidnight: false,
  });
}

function removePeriod(id: number) {
  periods.value = periods.value.filter((row) => row.id !== id);
}

async function save() {
  const branchId = selectedBranchId.value;
  if (!branchId || !canSave.value) return;
  const payload = changedPayload.value;
  saving.value = true;
  errorMessage.value = '';
  try {
    const updated = await updateBranch(branchId, payload);
    resetFromBranch(updated);
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['branches'] }),
      queryClient.invalidateQueries({ queryKey: BRANCHES_QUERY_KEY }),
      sharedQueryClient.invalidateQueries({ queryKey: BRANCHES_QUERY_KEY }),
    ]);
  } catch (error) {
    errorMessage.value =
      problemMessage(error) ?? 'Branch settings could not be saved.';
  } finally {
    saving.value = false;
  }
}
</script>
<template>
  <AppPage title="Venue settings">
    <template #actions><AppBadge>Administrator</AppBadge></template>
    <AppMessageBar v-if="errorMessage" tone="error">{{
      errorMessage
    }}</AppMessageBar>
    <AppPanel aria-label="Venue configuration">
      <form @submit.prevent="save">
        <AppFormSection title="Branch identity" title-id="venue-identity">
          <AppFormGrid :columns="1">
            <AppField label="Branch name" input-id="venue-name">
              <input
                id="venue-name"
                v-model="name"
                type="text"
                maxlength="120"
                required
              />
            </AppField>
            <AppField label="Physical address" input-id="venue-address">
              <input
                id="venue-address"
                v-model="address"
                type="text"
                maxlength="500"
                required
              />
            </AppField>
            <AppField label="Phone" input-id="venue-phone">
              <input
                id="venue-phone"
                v-model="phone"
                type="tel"
                maxlength="40"
              />
            </AppField>
          </AppFormGrid>
        </AppFormSection>
        <AppFormSection title="Operating hours" title-id="venue-hours">
          <div class="periods-list">
            <div
              v-for="row in sortedPeriods"
              :key="row.id"
              class="period-row"
              data-testid="opening-period-row"
            >
              <AppField label="Day" :input-id="`period-day-${row.id}`">
                <AppSelect
                  :model-value="row.dayOfWeek"
                  :name="`period-day-${row.id}`"
                  label="Day"
                  :options="DAY_OPTIONS"
                  @update:model-value="
                    row.dayOfWeek = $event as OpeningPeriodDayOfWeek
                  "
                />
              </AppField>
              <AppField label="Opens" :input-id="`period-opens-${row.id}`">
                <input
                  :id="`period-opens-${row.id}`"
                  v-model="row.opensAt"
                  type="time"
                  required
                />
              </AppField>
              <AppField label="Closes" :input-id="`period-closes-${row.id}`">
                <input
                  :id="`period-closes-${row.id}`"
                  v-model="row.closesAt"
                  type="time"
                  :disabled="row.untilMidnight"
                  required
                />
              </AppField>
              <label class="until-midnight">
                <AppCheckbox
                  :model-value="row.untilMidnight"
                  label="Until midnight"
                  @update:model-value="row.untilMidnight = $event"
                />
                <span>Until midnight</span>
              </label>
              <AppButton
                type="button"
                size="compact"
                @click="removePeriod(row.id)"
              >
                Remove
              </AppButton>
            </div>
            <div>
              <AppButton type="button" @click="addPeriod">Add period</AppButton>
            </div>
          </div>
        </AppFormSection>
        <AppFormActions>
          <AppButton type="submit" appearance="primary" :disabled="!canSave">
            {{ saving ? 'Saving…' : 'Save changes' }}
          </AppButton>
        </AppFormActions>
      </form>
    </AppPanel>
  </AppPage>
</template>
<style scoped>
.periods-list {
  display: grid;
  gap: 12px;
}
.period-row {
  display: grid;
  grid-template-columns:
    minmax(140px, 1fr) repeat(2, minmax(120px, 1fr))
    auto auto;
  align-items: end;
  gap: 12px;
}
.until-midnight {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  font-size: 13px;
  cursor: pointer;
}
</style>

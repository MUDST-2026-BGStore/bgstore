<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query';
import { computed, reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import OwnerLayout from '../../layouts/OwnerLayout.vue';
import UiButton from '../../components/ui/UiButton.vue';
import UiSelect from '../../components/ui/UiSelect.vue';
import UiTextInput from '../../components/ui/UiTextInput.vue';
import { branchesQueryOptions } from '../../queries/games';
import {
  createTableRequest,
  deleteTableRequest,
  tablesQueryOptions,
  updateTableRequest,
} from '../../queries/tables';
import type {
  CreateTableRequest,
  TableResponse,
  TableShape,
  TableStatus,
} from '../../generated/api/types.gen';

type ViewMode = 'list' | 'create' | 'edit' | 'view';

const { t } = useI18n();
const queryClient = useQueryClient();
const pageSize = 5;

const branchDirectory = useQuery(branchesQueryOptions());
const branches = computed(() => branchDirectory.data.value ?? []);
const selectedBranch = ref('');
const search = ref('');
const zoneFilter = ref('');
const statusFilter = ref<TableStatus | ''>('');
// The API contract is one-based; keep the screen state one-based too so every
// request is valid without translating at multiple call sites.
const currentPage = ref(1);
const currentView = ref<ViewMode>('list');
const viewedTable = ref<TableResponse | null>(null);
const editingId = ref<number | null>(null);
const formError = ref('');

const defaultForm: CreateTableRequest = {
  name: '',
  branch: '',
  capacity: 4,
  status: 'Available',
  active: true,
  shape: 'Round',
  zone: '',
};
const form = reactive<CreateTableRequest>({ ...defaultForm });

watch(
  branches,
  (availableBranches) => {
    if (!selectedBranch.value && availableBranches.length > 0) {
      selectedBranch.value = availableBranches[0].name;
    }
  },
  { immediate: true },
);

const allTablesQuery = computed(() =>
  tablesQueryOptions({
    branch: selectedBranch.value || undefined,
    page: 1,
    pageSize: 100,
  }),
);
const tablesQuery = computed(() =>
  tablesQueryOptions({
    branch: selectedBranch.value || undefined,
    zone: zoneFilter.value || undefined,
    status: statusFilter.value || undefined,
    search: search.value.trim() || undefined,
    page: currentPage.value,
    pageSize,
  }),
);

const allTables = useQuery(allTablesQuery);
const tables = useQuery(tablesQuery);
const rows = computed(() => tables.data.value?.items ?? []);
const summaryRows = computed(() => allTables.data.value?.items ?? []);
const total = computed(() => tables.data.value?.total ?? 0);
const totalPages = computed(() => tables.data.value?.totalPages ?? 0);
const isLoading = computed(
  () =>
    branchDirectory.isPending.value ||
    allTables.isPending.value ||
    tables.isPending.value,
);
const loadError = computed(
  () =>
    branchDirectory.isError.value ||
    allTables.isError.value ||
    tables.isError.value,
);

const zoneOptions = computed(() => [
  ...new Set(summaryRows.value.map((table) => table.zone)),
]);
const statusOptions: TableStatus[] = [
  'Available',
  'Reserved',
  'Occupied',
  'Unavailable',
];
const shapeOptions: TableShape[] = ['Round', 'Square', 'Oval', 'Rectangle'];
const capacityOptions = [1, 2, 4, 6, 8, 10, 12, 15, 20, 25, 50, 100];

const summary = computed(() => ({
  total: allTables.data.value?.total ?? summaryRows.value.length,
  available: summaryRows.value.filter((table) => table.status === 'Available')
    .length,
  reserved: summaryRows.value.filter((table) => table.status === 'Reserved')
    .length,
  occupied: summaryRows.value.filter((table) => table.status === 'Occupied')
    .length,
}));

const statusClasses: Record<TableStatus, string> = {
  Available: 'bg-[#e9f5ee] text-[#237847] border-[#cbeed6]',
  Reserved: 'bg-[#edf3fb] text-[#2563eb] border-[#bfdbfe]',
  Occupied: 'bg-[#fff4e9] text-[#d97706] border-[#fde68a]',
  Unavailable: 'bg-[#fdeeed] text-[#dc2626] border-[#fecaca]',
};

const pageNumbers = computed(() =>
  Array.from({ length: totalPages.value }, (_, index) => index + 1),
);
const range = computed(() => {
  if (total.value === 0) return t('tables.showingNone');
  const from = (currentPage.value - 1) * pageSize + 1;
  const to = Math.min(currentPage.value * pageSize, total.value);
  return t('tables.showing', { from, to, total: total.value });
});

const save = useMutation({
  mutationFn: ({
    id,
    body,
  }: {
    id: number | null;
    body: CreateTableRequest;
  }) => (id === null ? createTableRequest(body) : updateTableRequest(id, body)),
  onSuccess: async () => {
    formError.value = '';
    await queryClient.invalidateQueries({ queryKey: ['tables'] });
    closeForm();
  },
  onError: (error: unknown) => {
    formError.value = errorMessage(error);
  },
});

const remove = useMutation({
  mutationFn: deleteTableRequest,
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['tables'] });
    if (currentPage.value > totalPages.value && currentPage.value > 1) {
      currentPage.value -= 1;
    }
  },
  onError: (error: unknown) => {
    formError.value = errorMessage(error);
  },
});

watch([selectedBranch, search, zoneFilter, statusFilter], () => {
  currentPage.value = 1;
});

function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  if (typeof error === 'object' && error !== null && 'detail' in error) {
    const detail = error.detail;
    if (typeof detail === 'string') return detail;
  }
  return t('tables.saveError');
}

function resetForm(branch = selectedBranch.value) {
  Object.assign(form, {
    ...defaultForm,
    branch,
    zone: zoneOptions.value[0] ?? '',
  });
  formError.value = '';
}

function openCreateForm() {
  editingId.value = null;
  resetForm();
  currentView.value = 'create';
}

function openEditForm(table: TableResponse) {
  editingId.value = table.id;
  Object.assign(form, {
    name: table.name,
    branch: table.branch,
    capacity: table.capacity,
    status: table.status,
    active: table.active,
    shape: table.shape,
    zone: table.zone,
  });
  formError.value = '';
  currentView.value = 'edit';
}

function openView(table: TableResponse) {
  viewedTable.value = table;
  currentView.value = 'view';
}

function closeForm() {
  currentView.value = 'list';
  editingId.value = null;
  viewedTable.value = null;
  resetForm();
}

function validateForm(): boolean {
  if (!form.name.trim()) {
    formError.value = t('tables.nameRequired');
    return false;
  }
  if (form.name.trim().length > 100) {
    formError.value = t('tables.nameTooLong');
    return false;
  }
  if (!form.branch || !form.zone || form.capacity < 1) {
    formError.value = t('tables.invalidDetails');
    return false;
  }
  const duplicate = summaryRows.value.some(
    (table) =>
      table.id !== editingId.value &&
      table.branch.toLowerCase() === form.branch.toLowerCase() &&
      table.name.trim().toLowerCase() === form.name.trim().toLowerCase(),
  );
  if (duplicate) {
    formError.value = t('tables.duplicate');
    return false;
  }
  formError.value = '';
  return true;
}

function saveTable() {
  if (!validateForm() || save.isPending.value) return;
  save.mutate({
    id: editingId.value,
    body: { ...form, name: form.name.trim(), capacity: Number(form.capacity) },
  });
}

function confirmDelete(table: TableResponse) {
  if (remove.isPending.value || !window.confirm(t('tables.confirmDelete'))) {
    return;
  }
  remove.mutate(table.id);
}

function formatUpdated(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}
</script>

<template>
  <OwnerLayout active="tables">
    <div
      class="staff-page-content flex w-full flex-col items-start bg-canvas"
      data-page="tables"
    >
      <header
        class="staff-page-toolbar flex w-full items-center gap-3 border-b border-line bg-surface px-8 py-4"
      >
        <h1 class="text-[18px] font-semibold text-ink">
          {{ t('tables.title') }}
        </h1>
        <div class="h-px min-w-0 flex-1" />
        <UiButton v-if="currentView === 'list'" @click="openCreateForm">
          {{ t('tables.add') }}
        </UiButton>
      </header>

      <div
        v-if="currentView === 'list'"
        class="mx-auto w-full max-w-6xl px-8 py-7"
      >
        <div class="staff-stat-grid mb-6 grid grid-cols-1 gap-4 md:grid-cols-4">
          <article
            v-for="card in [
              { label: t('tables.total'), value: summary.total },
              { label: t('tables.available'), value: summary.available },
              { label: t('tables.reserved'), value: summary.reserved },
              { label: t('tables.occupied'), value: summary.occupied },
            ]"
            :key="card.label"
            class="rounded-md border border-line bg-surface p-4"
          >
            <p class="text-[12px] text-ink-muted">{{ card.label }}</p>
            <p class="mt-1 text-[24px] font-semibold text-ink">
              {{ card.value }}
            </p>
          </article>
        </div>

        <div class="mb-5 grid grid-cols-1 gap-3 md:grid-cols-4">
          <UiSelect
            id="panel-branch"
            v-model="selectedBranch"
            :options="
              branches.map((branch) => ({
                value: branch.name,
                label: branch.name,
              }))
            "
          />
          <UiTextInput
            id="table-search"
            v-model="search"
            :placeholder="t('tables.search')"
            search
          />
          <UiSelect
            id="table-zone"
            v-model="zoneFilter"
            :placeholder="t('tables.allZones')"
            placeholder-selectable
            :options="zoneOptions.map((zone) => ({ value: zone, label: zone }))"
          />
          <UiSelect
            id="table-status"
            v-model="statusFilter"
            :placeholder="t('tables.allStatuses')"
            placeholder-selectable
            :options="
              statusOptions.map((status) => ({
                value: status,
                label: t(`tables.status.${status}`),
              }))
            "
          />
        </div>

        <div
          v-if="loadError"
          class="rounded-md border border-danger-border bg-surface p-6 text-danger-fg"
          role="alert"
        >
          <p>{{ t('tables.loadError') }}</p>
          <UiButton
            class="mt-3"
            variant="outline"
            size="sm"
            @click="
              branchDirectory.refetch();
              allTables.refetch();
              tables.refetch();
            "
          >
            {{ t('tables.retry') }}
          </UiButton>
        </div>
        <div
          v-else-if="isLoading"
          class="rounded-md border border-line bg-surface p-8 text-ink-muted"
          data-testid="tables-loading"
        >
          {{ t('tables.loading') }}
        </div>
        <div
          v-else-if="rows.length === 0"
          class="rounded-md border border-line bg-surface p-8 text-ink-muted"
        >
          {{ t('tables.empty') }}
        </div>
        <div
          v-else
          class="overflow-hidden rounded-md border border-line bg-surface"
        >
          <table class="w-full text-left text-[13px]">
            <thead class="border-b border-line bg-canvas text-ink-muted">
              <tr>
                <th class="px-4 py-3">{{ t('tables.name') }}</th>
                <th class="px-4 py-3">{{ t('tables.branch') }}</th>
                <th class="px-4 py-3">{{ t('tables.capacity') }}</th>
                <th class="px-4 py-3">{{ t('tables.statusLabel') }}</th>
                <th class="px-4 py-3 text-right">{{ t('tables.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="table in rows"
                :key="table.id"
                class="border-b border-line last:border-0"
              >
                <td class="px-4 py-3 font-medium text-ink">{{ table.name }}</td>
                <td class="px-4 py-3 text-ink-secondary">{{ table.branch }}</td>
                <td class="px-4 py-3 text-ink-secondary">
                  {{ t('tables.seats', { count: table.capacity }) }}
                </td>
                <td class="px-4 py-3">
                  <span
                    class="inline-flex rounded-full border px-3 py-0.5 text-xs font-semibold"
                    :class="statusClasses[table.status]"
                  >
                    {{ t(`tables.status.${table.status}`) }}
                  </span>
                </td>
                <td class="px-4 py-3 text-right">
                  <button class="mr-3 text-primary" @click="openView(table)">
                    {{ t('tables.view') }}
                  </button>
                  <button
                    class="mr-3 text-primary"
                    @click="openEditForm(table)"
                  >
                    {{ t('tables.edit') }}
                  </button>
                  <button class="text-danger-fg" @click="confirmDelete(table)">
                    {{ t('tables.delete') }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div
          v-if="!isLoading && rows.length > 0"
          class="mt-4 flex items-center justify-between text-[12px] text-ink-muted"
        >
          <span>{{ range }}</span>
          <div class="flex items-center gap-1" aria-label="Table pagination">
            <button
              :disabled="currentPage === 1"
              aria-label="Previous page"
              class="rounded border border-line px-2 py-1 disabled:opacity-40"
              @click="currentPage -= 1"
            >
              ‹
            </button>
            <button
              v-for="page in pageNumbers"
              :key="page"
              :aria-current="page === currentPage ? 'page' : undefined"
              class="rounded px-2 py-1"
              :class="
                page === currentPage
                  ? 'bg-primary text-primary-fg'
                  : 'border border-line'
              "
              @click="currentPage = page"
            >
              {{ page + 1 }}
            </button>
            <button
              :disabled="currentPage >= totalPages"
              aria-label="Next page"
              class="rounded border border-line px-2 py-1 disabled:opacity-40"
              @click="currentPage += 1"
            >
              ›
            </button>
          </div>
        </div>
      </div>

      <form
        v-else-if="currentView === 'create' || currentView === 'edit'"
        id="table-management-form"
        class="mx-auto w-full max-w-2xl px-8 py-8"
        @submit.prevent="saveTable"
      >
        <h2 class="mb-6 text-[22px] font-semibold text-ink">
          {{
            currentView === 'edit'
              ? t('tables.editTitle')
              : t('tables.addTitle')
          }}
        </h2>
        <div class="grid gap-4 rounded-md border border-line bg-surface p-6">
          <label class="grid gap-1 text-[13px] text-ink-secondary"
            >{{ t('tables.name') }}
            <input
              id="form-name"
              v-model="form.name"
              class="h-10 rounded-md border border-line bg-surface px-3 text-ink"
              :placeholder="t('tables.namePlaceholder')"
            />
          </label>
          <label class="grid gap-1 text-[13px] text-ink-secondary"
            >{{ t('tables.branch') }}
            <select
              id="form-branch"
              v-model="form.branch"
              class="h-10 rounded-md border border-line bg-surface px-3 text-ink"
            >
              <option
                v-for="branch in branches"
                :key="branch.id"
                :value="branch.name"
              >
                {{ branch.name }}
              </option>
            </select>
          </label>
          <div class="grid grid-cols-2 gap-4">
            <label class="grid gap-1 text-[13px] text-ink-secondary"
              >{{ t('tables.capacity') }}
              <select
                id="form-capacity"
                v-model.number="form.capacity"
                class="h-10 rounded-md border border-line bg-surface px-3 text-ink"
              >
                <option
                  v-for="capacity in capacityOptions"
                  :key="capacity"
                  :value="capacity"
                >
                  {{ capacity }}
                </option>
              </select>
            </label>
            <label class="grid gap-1 text-[13px] text-ink-secondary"
              >{{ t('tables.statusLabel') }}
              <select
                v-model="form.status"
                class="h-10 rounded-md border border-line bg-surface px-3 text-ink"
              >
                <option
                  v-for="status in statusOptions"
                  :key="status"
                  :value="status"
                >
                  {{ t(`tables.status.${status}`) }}
                </option>
              </select>
            </label>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <label class="grid gap-1 text-[13px] text-ink-secondary"
              >{{ t('tables.shapeLabel') }}
              <select
                v-model="form.shape"
                class="h-10 rounded-md border border-line bg-surface px-3 text-ink"
              >
                <option
                  v-for="shape in shapeOptions"
                  :key="shape"
                  :value="shape"
                >
                  {{ t(`tables.shape.${shape}`) }}
                </option>
              </select>
            </label>
            <label class="grid gap-1 text-[13px] text-ink-secondary"
              >{{ t('tables.zone') }}
              <select
                v-model="form.zone"
                class="h-10 rounded-md border border-line bg-surface px-3 text-ink"
              >
                <option v-for="zone in zoneOptions" :key="zone" :value="zone">
                  {{ zone }}
                </option>
              </select>
            </label>
          </div>
          <label class="flex items-center gap-2 text-[13px] text-ink-secondary"
            ><input v-model="form.active" type="checkbox" />
            {{ t('tables.active') }}</label
          >
          <p v-if="formError" class="text-[13px] text-danger-fg" role="alert">
            {{ formError }}
          </p>
          <div class="flex justify-end gap-3">
            <UiButton variant="outline" type="button" @click="closeForm">{{
              t('tables.cancel')
            }}</UiButton>
            <UiButton type="submit" :disabled="save.isPending.value">{{
              save.isPending.value ? t('tables.saving') : t('tables.save')
            }}</UiButton>
          </div>
        </div>
      </form>

      <section
        v-else-if="currentView === 'view' && viewedTable"
        class="mx-auto w-full max-w-2xl px-8 py-8"
      >
        <div class="mb-5 flex items-center justify-between">
          <h2 class="text-[22px] font-semibold text-ink">
            {{ t('tables.details') }}
          </h2>
          <UiButton variant="outline" @click="closeForm">{{
            t('tables.done')
          }}</UiButton>
        </div>
        <dl
          class="grid gap-3 rounded-md border border-line bg-surface p-6 text-[14px]"
        >
          <div>
            <dt class="text-ink-muted">{{ t('tables.name') }}</dt>
            <dd class="font-medium text-ink">{{ viewedTable.name }}</dd>
          </div>
          <div>
            <dt class="text-ink-muted">{{ t('tables.branch') }}</dt>
            <dd class="text-ink">{{ viewedTable.branch }}</dd>
          </div>
          <div>
            <dt class="text-ink-muted">{{ t('tables.capacity') }}</dt>
            <dd class="text-ink">
              {{ t('tables.seats', { count: viewedTable.capacity }) }}
            </dd>
          </div>
          <div>
            <dt class="text-ink-muted">{{ t('tables.shapeLabel') }}</dt>
            <dd class="text-ink">
              {{ t(`tables.shape.${viewedTable.shape}`) }}
            </dd>
          </div>
          <div>
            <dt class="text-ink-muted">{{ t('tables.zone') }}</dt>
            <dd class="text-ink">{{ viewedTable.zone }}</dd>
          </div>
          <div>
            <dt class="text-ink-muted">{{ t('tables.lastUpdated') }}</dt>
            <dd class="text-ink">
              {{ formatUpdated(viewedTable.lastUpdated) }}
            </dd>
          </div>
        </dl>
      </section>
    </div>
  </OwnerLayout>
</template>

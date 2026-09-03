<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';

type TableStatus = 'Available' | 'Reserved' | 'Occupied' | 'Unavailable';
type TableShape = 'Round' | 'Square' | 'Oval';

type TableRecord = {
  id: number;
  name: string;
  branch: string;
  capacity: number;
  shape: TableShape;
  status: TableStatus;
  active: boolean;
  zone: string;
  lastUpdated: string;
};

const branches = ['Sukhumvit', 'Silom', 'Bangkok'];
const zoneOptions = ['All zones', 'Main Hall', 'Private Room', 'Rooftop'];
const statusOptions = [
  'All statuses',
  'Available',
  'Reserved',
  'Occupied',
  'Unavailable',
];

const tableSeed: TableRecord[] = [
  {
    id: 1,
    name: 'Table 1',
    branch: 'Sukhumvit',
    capacity: 4,
    shape: 'Round',
    status: 'Available',
    active: true,
    zone: 'Main Hall',
    lastUpdated: '2025-09-25T20:12:00',
  },
  {
    id: 2,
    name: 'Table 3',
    branch: 'Sukhumvit',
    capacity: 2,
    shape: 'Square',
    status: 'Reserved',
    active: true,
    zone: 'Private Room',
    lastUpdated: '2025-09-25T20:04:00',
  },
  {
    id: 3,
    name: 'Table 5',
    branch: 'Sukhumvit',
    capacity: 4,
    shape: 'Round',
    status: 'Occupied',
    active: true,
    zone: 'Main Hall',
    lastUpdated: '2025-09-25T21:38:00',
  },
  {
    id: 4,
    name: 'Table 9',
    branch: 'Sukhumvit',
    capacity: 8,
    shape: 'Square',
    status: 'Unavailable',
    active: true,
    zone: 'Rooftop',
    lastUpdated: '2025-09-26T15:20:00',
  },
  {
    id: 5,
    name: 'Table 12',
    branch: 'Silom',
    capacity: 6,
    shape: 'Oval',
    status: 'Available',
    active: true,
    zone: 'Main Hall',
    lastUpdated: '2025-09-24T10:10:00',
  },
  {
    id: 6,
    name: 'Table 14',
    branch: 'Silom',
    capacity: 8,
    shape: 'Round',
    status: 'Reserved',
    active: true,
    zone: 'Private Room',
    lastUpdated: '2025-09-25T22:32:00',
  },
  {
    id: 7,
    name: 'Table 18',
    branch: 'Bangkok',
    capacity: 10,
    shape: 'Round',
    status: 'Available',
    active: false,
    zone: 'Main Hall',
    lastUpdated: '2025-09-22T09:00:00',
  },
  {
    id: 8,
    name: 'Table 19',
    branch: 'Bangkok',
    capacity: 2,
    shape: 'Square',
    status: 'Occupied',
    active: true,
    zone: 'Private Room',
    lastUpdated: '2025-09-27T12:05:00',
  },
  {
    id: 9,
    name: 'Table 22',
    branch: 'Bangkok',
    capacity: 6,
    shape: 'Oval',
    status: 'Unavailable',
    active: false,
    zone: 'Rooftop',
    lastUpdated: '2025-09-20T18:42:00',
  },
];

const tables = ref<TableRecord[]>(tableSeed);
const selectedBranch = ref('Sukhumvit');
const search = ref('');
const zoneFilter = ref('All zones');
const statusFilter = ref('All statuses');
const currentPage = ref(1);
const pageSize = 5;
const isLoading = ref(true);
const isFormOpen = ref(false);
const isViewOpen = ref(false);
const isEditing = ref(false);
const editingId = ref<number | null>(null);
const viewedTable = ref<TableRecord | null>(null);

const defaultForm = {
  name: '',
  branch: 'Sukhumvit',
  capacity: '',
  status: 'Available',
  active: true,
  shape: 'Round',
  zone: 'Main Hall',
};

const form = reactive({ ...defaultForm });
const formErrors = reactive({
  name: '',
  branch: '',
  capacity: '',
  status: '',
});

const selectedBranchTables = computed(() =>
  tables.value.filter((table) => table.branch === selectedBranch.value),
);

const summary = computed(() => {
  const list = selectedBranchTables.value;
  return {
    total: list.length,
    available: list.filter((table) => table.status === 'Available').length,
    reserved: list.filter((table) => table.status === 'Reserved').length,
    occupied: list.filter((table) => table.status === 'Occupied').length,
  };
});

const filteredTables = computed(() => {
  const query = search.value.trim().toLowerCase();

  return selectedBranchTables.value.filter((table) => {
    const matchesName = !query || table.name.toLowerCase().includes(query);
    const matchesZone =
      zoneFilter.value === 'All zones' || table.zone === zoneFilter.value;
    const matchesStatus =
      statusFilter.value === 'All statuses' ||
      table.status === statusFilter.value;

    return matchesName && matchesZone && matchesStatus;
  });
});

const totalPages = computed(() =>
  Math.max(1, Math.ceil(filteredTables.value.length / pageSize)),
);

const pageNumbers = computed(() => {
  const total = totalPages.value;

  if (total <= 7) {
    return Array.from({ length: total }, (_, index) => index + 1);
  }

  if (currentPage.value <= 3) {
    return [1, 2, 3, 'ellipsis', total];
  }

  if (currentPage.value >= total - 2) {
    return [1, 'ellipsis', total - 2, total - 1, total];
  }

  return [
    1,
    'ellipsis',
    currentPage.value - 1,
    currentPage.value,
    currentPage.value + 1,
    'ellipsis',
    total,
  ];
});

const paginatedTables = computed(() => {
  const start = (currentPage.value - 1) * pageSize;
  return filteredTables.value.slice(start, start + pageSize);
});

const statusClasses: Record<TableStatus, string> = {
  Available:
    'inline-flex items-center rounded-full bg-[#e9f5ee] px-2.5 py-1 text-[0.72rem] font-bold text-[#2b7a61]',
  Reserved:
    'inline-flex items-center rounded-full bg-[#edf3fb] px-2.5 py-1 text-[0.72rem] font-bold text-[#3a6f9c]',
  Occupied:
    'inline-flex items-center rounded-full bg-[#fdf2e8] px-2.5 py-1 text-[0.72rem] font-bold text-[#c77c29]',
  Unavailable:
    'inline-flex items-center rounded-full bg-[#fbe9e8] px-2.5 py-1 text-[0.72rem] font-bold text-[#b85b52]',
};

watch([selectedBranch, search, zoneFilter, statusFilter], () => {
  currentPage.value = 1;
});

watch(
  () => selectedBranch.value,
  () => {
    form.branch = selectedBranch.value;
  },
);

onMounted(() => {
  setTimeout(() => {
    isLoading.value = false;
  }, 350);
});

function openCreateForm() {
  isEditing.value = false;
  editingId.value = null;
  Object.assign(form, { ...defaultForm, branch: selectedBranch.value });
  Object.keys(formErrors).forEach((key) => {
    formErrors[key as keyof typeof formErrors] = '';
  });
  isFormOpen.value = true;
}

function openEditForm(table: TableRecord) {
  isEditing.value = true;
  editingId.value = table.id;
  Object.assign(form, {
    name: table.name,
    branch: table.branch,
    capacity: String(table.capacity),
    status: table.status,
    active: table.active,
    shape: table.shape,
    zone: table.zone,
  });
  Object.keys(formErrors).forEach((key) => {
    formErrors[key as keyof typeof formErrors] = '';
  });
  isFormOpen.value = true;
}

function closeForm() {
  isFormOpen.value = false;
  isEditing.value = false;
  editingId.value = null;
  Object.assign(form, { ...defaultForm, branch: selectedBranch.value });
  Object.keys(formErrors).forEach((key) => {
    formErrors[key as keyof typeof formErrors] = '';
  });
}

function openViewForm(table: TableRecord) {
  viewedTable.value = table;
  isViewOpen.value = true;
}

function closeViewForm() {
  isViewOpen.value = false;
  viewedTable.value = null;
}

function validateForm() {
  const nextErrors = {
    name: '',
    branch: '',
    capacity: '',
    status: '',
  };

  if (!form.name.trim()) nextErrors.name = 'Table name is required';
  if (!form.branch.trim()) nextErrors.branch = 'Branch is required';
  if (!String(form.capacity).trim())
    nextErrors.capacity = 'Capacity is required';
  if (!form.status) nextErrors.status = 'Status is required';

  Object.assign(formErrors, nextErrors);
  return !Object.values(nextErrors).some(Boolean);
}

function saveTable() {
  if (!validateForm()) return;

  const payload: TableRecord = {
    id: editingId.value ?? Date.now(),
    name: form.name.trim(),
    branch: form.branch,
    capacity: Number(form.capacity),
    status: form.status as TableStatus,
    active: form.active,
    shape: form.shape as TableShape,
    zone: form.zone,
    lastUpdated: new Date().toISOString(),
  };

  if (isEditing.value && editingId.value) {
    tables.value = tables.value.map((table) =>
      table.id === editingId.value ? payload : table,
    );
  } else {
    tables.value = [payload, ...tables.value];
  }

  closeForm();
}

function confirmDelete(id: number) {
  const table = tables.value.find((item) => item.id === id);
  if (
    table &&
    window.confirm(`Delete ${table.name}? This action cannot be undone.`)
  ) {
    tables.value = tables.value.filter((item) => item.id !== id);
  }
}

function formatUpdated(value: string) {
  return new Date(value).toLocaleString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
</script>

<template>
  <main class="min-h-screen bg-[#ffffff] p-0 text-[#1d2a2f]">
    <div class="min-h-screen w-full overflow-hidden bg-[#ffffff]">
      <header
        class="flex items-center justify-between gap-4 border-b border-[#d9dfe1] bg-[#f8f8f7] px-5 py-3"
      >
        <div class="flex items-center gap-3">
          <div
            class="grid h-8 w-8 place-items-center rounded-full bg-[#223336] text-[0.58rem] font-bold text-white"
          >
            BG
          </div>
          <div class="leading-tight">
            <div
              class="text-[0.62rem] font-semibold uppercase tracking-[0.12em] text-[#6d7377]"
            >
              Reservation
            </div>
            <div class="text-xs font-bold text-[#1d2a2f]">Pine</div>
          </div>
        </div>

        <nav
          class="hidden items-center gap-2 md:flex"
          aria-label="Primary navigation"
        >
          <button
            type="button"
            class="rounded-full px-3 py-1.5 text-xs font-medium text-[#70777c] transition hover:bg-[#ebeeef] hover:text-[#1d2a2f]"
          >
            Dashboard
          </button>
          <button
            type="button"
            class="rounded-full px-3 py-1.5 text-xs font-medium text-[#70777c] transition hover:bg-[#ebeeef] hover:text-[#1d2a2f]"
          >
            Branches
          </button>
          <button
            type="button"
            class="rounded-full bg-[#e9efef] px-3 py-1.5 text-xs font-medium text-[#1d2a2f]"
          >
            Tables
          </button>
          <button
            type="button"
            class="rounded-full px-3 py-1.5 text-xs font-medium text-[#70777c] transition hover:bg-[#ebeeef] hover:text-[#1d2a2f]"
          >
            Games
          </button>
        </nav>
      </header>

      <section class="px-5 pb-5 pt-4">
        <div
          class="flex items-center justify-between gap-4 border-b border-[#d3d7d9] pb-3"
        >
          <div class="text-sm font-medium text-[#6a7377]">Tables</div>
          <div class="flex items-center gap-3">
            <label class="sr-only" for="panel-branch">Branch filter</label>
            <select
              id="panel-branch"
              v-model="selectedBranch"
              aria-label="Select branch"
              class="h-9 min-w-[160px] rounded-full border border-[#d3d7d9] bg-white px-3 text-xs text-[#1d2a2f] outline-none focus:border-[#1d2a2f]"
            >
              <option v-for="branch in branches" :key="branch" :value="branch">
                {{ branch }}
              </option>
            </select>
            <button
              type="button"
              class="inline-flex items-center justify-center rounded-full bg-[#2c5d5b] px-4 py-2 text-xs font-semibold text-white shadow-sm transition hover:bg-[#244d4c]"
              @click="openCreateForm"
            >
              + Add table
            </button>
          </div>
        </div>

        <div class="pt-4">
          <p
            class="text-[2.05rem] font-semibold tracking-[-0.05em] text-[#1d2a2f]"
          >
            Table management
          </p>
          <p class="mt-1 text-sm text-[#657069]">
            Tables belonging to a branch. Status updates automatically as
            reservations move through the lifecycle.
          </p>
        </div>

        <div class="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <article
            class="rounded-xl border border-[#dfe5e7] bg-[#f2f3f2] p-3 shadow-sm"
          >
            <div class="flex items-center justify-between gap-2">
              <span
                class="text-[0.7rem] font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
                >Total tables</span
              >
              <span
                class="rounded-md bg-white/70 p-1 text-[0.7rem] text-[#6a7377]"
                >◌</span
              >
            </div>
            <strong
              class="mt-4 block text-[2rem] font-semibold leading-none tracking-[-0.06em] text-[#1d2a2f]"
              >{{ summary.total }}</strong
            >
          </article>

          <article
            class="rounded-xl border border-[#dfe5e7] bg-[#edf7f0] p-3 shadow-sm"
          >
            <div class="flex items-center justify-between gap-2">
              <span
                class="text-[0.7rem] font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
                >Available</span
              >
              <span
                class="rounded-md bg-[#daf0e2] p-1 text-[0.7rem] text-[#2d7a60]"
                >✓</span
              >
            </div>
            <strong
              class="mt-4 block text-[2rem] font-semibold leading-none tracking-[-0.06em] text-[#1d2a2f]"
              >{{ summary.available }}</strong
            >
          </article>

          <article
            class="rounded-xl border border-[#dfe5e7] bg-[#eef4fb] p-3 shadow-sm"
          >
            <div class="flex items-center justify-between gap-2">
              <span
                class="text-[0.7rem] font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
                >Reserved</span
              >
              <span
                class="rounded-md bg-[#dfeeff] p-1 text-[0.7rem] text-[#406c96]"
                >◔</span
              >
            </div>
            <strong
              class="mt-4 block text-[2rem] font-semibold leading-none tracking-[-0.06em] text-[#1d2a2f]"
              >{{ summary.reserved }}</strong
            >
          </article>

          <article
            class="rounded-xl border border-[#dfe5e7] bg-[#fff4e9] p-3 shadow-sm"
          >
            <div class="flex items-center justify-between gap-2">
              <span
                class="text-[0.7rem] font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
                >Occupied</span
              >
              <span
                class="rounded-md bg-[#fce7c9] p-1 text-[0.7rem] text-[#c37d2b]"
                >•</span
              >
            </div>
            <strong
              class="mt-4 block text-[2rem] font-semibold leading-none tracking-[-0.06em] text-[#1d2a2f]"
              >{{ summary.occupied }}</strong
            >
          </article>
        </div>

        <div class="mt-5 flex flex-col gap-3 md:flex-row md:items-center">
          <label
            class="flex flex-1 items-center gap-2 rounded-lg border border-[#d3d7d9] bg-white px-3 py-2 shadow-sm"
            aria-label="Search tables"
          >
            <span aria-hidden="true" class="text-[#6a7377]">⌕</span>
            <input
              v-model="search"
              type="search"
              placeholder="Search table name"
              class="w-full border-0 bg-transparent text-sm text-[#1d2a2f] placeholder:text-[#6a7377] focus:outline-none"
            />
          </label>

          <select
            v-model="zoneFilter"
            class="h-9 min-w-[170px] rounded-lg border border-[#d3d7d9] bg-white px-3 text-sm text-[#1d2a2f] shadow-sm outline-none focus:border-[#1d2a2f]"
          >
            <option v-for="zone in zoneOptions" :key="zone" :value="zone">
              {{ zone }}
            </option>
          </select>

          <select
            v-model="statusFilter"
            class="h-9 min-w-[170px] rounded-lg border border-[#d3d7d9] bg-white px-3 text-sm text-[#1d2a2f] shadow-sm outline-none focus:border-[#1d2a2f]"
          >
            <option
              v-for="status in statusOptions"
              :key="status"
              :value="status"
            >
              {{ status }}
            </option>
          </select>
        </div>

        <div
          class="mt-4 overflow-hidden rounded-xl border border-[#d3d7d9] bg-[#fafaf9] shadow-sm"
          aria-live="polite"
        >
          <template v-if="isLoading">
            <div class="grid gap-3 p-6">
              <div class="h-4 animate-pulse rounded bg-[#e4e7e8]" />
              <div class="h-4 w-1/2 animate-pulse rounded bg-[#e4e7e8]" />
              <div class="h-4 animate-pulse rounded bg-[#e4e7e8]" />
            </div>
          </template>

          <template v-else-if="filteredTables.length === 0">
            <div class="grid place-items-center gap-2 px-6 py-10 text-center">
              <h2 class="text-2xl font-semibold text-[#1d2a2f]">
                No tables match your filters
              </h2>
              <p class="text-sm text-[#657069]">
                Try changing the branch, search text, or status filter.
              </p>
            </div>
          </template>

          <template v-else>
            <table class="w-full border-collapse">
              <thead>
                <tr
                  class="bg-[#f2f3f2] text-left text-[0.7rem] font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
                >
                  <th class="px-4 py-3">Table</th>
                  <th class="px-4 py-3">Capacity</th>
                  <th class="px-4 py-3">Shape</th>
                  <th class="px-4 py-3">Status</th>
                  <th class="px-4 py-3">Last updated</th>
                  <th class="px-4 py-3">Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="table in paginatedTables"
                  :key="table.id"
                  class="border-t border-[#dfe3e4] align-middle bg-white/40"
                >
                  <td class="px-4 py-3 text-sm font-medium text-[#1d2a2f]">
                    {{ table.name }}
                  </td>
                  <td class="px-4 py-3 text-sm text-[#1d2a2f]">
                    {{ table.capacity }} seats
                  </td>
                  <td class="px-4 py-3 text-sm text-[#1d2a2f]">
                    {{ table.shape }}
                  </td>
                  <td class="px-4 py-3">
                    <span :class="statusClasses[table.status]">{{
                      table.status
                    }}</span>
                  </td>
                  <td class="px-4 py-3 text-sm text-[#1d2a2f]">
                    {{ formatUpdated(table.lastUpdated) }}
                  </td>
                  <td class="px-4 py-3">
                    <div class="flex items-center gap-2 text-xs font-medium">
                      <button
                        type="button"
                        class="text-[#2d5d62] hover:text-[#1d2a2f]"
                        @click="openViewForm(table)"
                      >
                        View
                      </button>
                      <button
                        type="button"
                        class="text-[#2d5d62] hover:text-[#1d2a2f]"
                        @click="openEditForm(table)"
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        class="text-[#c14f4b] hover:text-[#8a3732]"
                        @click="confirmDelete(table.id)"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </template>
        </div>

        <div
          v-if="!isLoading && filteredTables.length > 0"
          class="mt-5 flex items-center justify-end gap-3 px-1 pb-1"
          aria-label="Table pagination"
        >
          <div class="mr-3 text-sm text-[#1d2a2f]">
            Showing
            {{
              Math.min((currentPage - 1) * pageSize + 1, filteredTables.length)
            }}-{{ Math.min(currentPage * pageSize, filteredTables.length) }} of
            {{ filteredTables.length }}
          </div>

          <button
            type="button"
            aria-label="Previous page"
            class="flex h-9 w-9 items-center justify-center rounded-full bg-[#e7eaeb] text-lg text-[#1d2a2f] shadow-sm transition hover:bg-[#dfe5e7] disabled:cursor-not-allowed disabled:opacity-40"
            :disabled="currentPage === 1"
            @click="currentPage -= 1"
          >
            ←
          </button>

          <div
            class="flex items-center gap-3 text-sm font-medium text-[#5d676d]"
          >
            <template
              v-for="(page, index) in pageNumbers"
              :key="`page-${String(page)}-${index}`"
            >
              <button
                v-if="page !== 'ellipsis'"
                type="button"
                class="flex h-7 w-7 items-center justify-center rounded-full transition"
                @click="currentPage = Number(page)"
              >
                <span
                  :class="
                    page === currentPage
                      ? 'border-b-2 border-[#1d2a2f] pb-0.5 text-[#1d2a2f]'
                      : 'text-[#5d676d]'
                  "
                >
                  {{ page }}
                </span>
              </button>
              <span v-else class="px-1 text-lg leading-none text-[#5d676d]"
                >...</span
              >
            </template>
          </div>

          <button
            type="button"
            aria-label="Next page"
            class="flex h-9 w-9 items-center justify-center rounded-full bg-[#e7eaeb] text-lg text-[#1d2a2f] shadow-sm transition hover:bg-[#dfe5e7] disabled:cursor-not-allowed disabled:opacity-40"
            :disabled="currentPage >= totalPages"
            @click="currentPage += 1"
          >
            →
          </button>
        </div>
      </section>
    </div>

    <div
      v-if="isFormOpen"
      class="fixed inset-0 z-50 grid place-items-center bg-[#1d2a2f]/20 p-4"
      @click.self="closeForm"
    >
      <div
        class="w-full max-w-[700px] rounded-xl border border-[#d3d7d9] bg-[#f4f5f4] shadow-[0_20px_50px_rgba(29,42,47,0.14)]"
        role="dialog"
        aria-modal="true"
        aria-labelledby="table-form-title"
      >
        <div
          class="flex items-center justify-between border-b border-[#d3d7d9] px-5 py-3"
        >
          <div
            class="text-xs font-medium uppercase tracking-[0.12em] text-[#6a7377]"
          >
            Tables
          </div>
          <div class="flex items-center gap-3">
            <button
              type="button"
              class="rounded-full bg-[#dfe7e8] px-3 py-1.5 text-xs font-medium text-[#1d2a2f]"
              @click="closeForm"
            >
              Cancel
            </button>
            <button
              type="submit"
              class="rounded-full bg-[#2d5d62] px-3 py-1.5 text-xs font-medium text-white"
              @click="saveTable"
            >
              Save
            </button>
          </div>
        </div>

        <div class="px-5 py-4">
          <h2
            id="table-form-title"
            class="text-[2rem] font-semibold tracking-[-0.05em] text-[#1d2a2f]"
          >
            {{ isEditing ? 'Edit table' : 'Add table' }}
          </h2>

          <form class="mt-5" @submit.prevent="saveTable">
            <div class="grid gap-4 md:grid-cols-2">
              <label class="flex flex-col gap-2 text-sm text-[#1d2a2f]">
                <span
                  class="text-xs font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
                  >Table name</span
                >
                <input
                  v-model="form.name"
                  type="text"
                  placeholder="Table 5"
                  class="h-10 rounded-md border border-[#d3d7d9] bg-white px-3 text-sm text-[#1d2a2f] outline-none focus:border-[#2d5d62]"
                />
                <small v-if="formErrors.name" class="text-xs text-[#c14f4b]">{{
                  formErrors.name
                }}</small>
              </label>

              <label class="flex flex-col gap-2 text-sm text-[#1d2a2f]">
                <span
                  class="text-xs font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
                  >Branch</span
                >
                <select
                  v-model="form.branch"
                  class="h-10 rounded-md border border-[#d3d7d9] bg-white px-3 text-sm text-[#1d2a2f] outline-none focus:border-[#2d5d62]"
                >
                  <option
                    v-for="branch in branches"
                    :key="branch"
                    :value="branch"
                  >
                    {{ branch }}
                  </option>
                </select>
                <small
                  v-if="formErrors.branch"
                  class="text-xs text-[#c14f4b]"
                  >{{ formErrors.branch }}</small
                >
              </label>

              <label class="flex flex-col gap-2 text-sm text-[#1d2a2f]">
                <span
                  class="text-xs font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
                  >Capacity</span
                >
                <input
                  v-model="form.capacity"
                  type="number"
                  min="1"
                  placeholder="4"
                  class="h-10 rounded-md border border-[#d3d7d9] bg-white px-3 text-sm text-[#1d2a2f] outline-none focus:border-[#2d5d62]"
                />
                <small
                  v-if="formErrors.capacity"
                  class="text-xs text-[#c14f4b]"
                  >{{ formErrors.capacity }}</small
                >
              </label>

              <label class="flex flex-col gap-2 text-sm text-[#1d2a2f]">
                <span
                  class="text-xs font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
                  >Status</span
                >
                <select
                  v-model="form.status"
                  class="h-10 rounded-md border border-[#d3d7d9] bg-white px-3 text-sm text-[#1d2a2f] outline-none focus:border-[#2d5d62]"
                >
                  <option value="Available">Available</option>
                  <option value="Reserved">Reserved</option>
                  <option value="Occupied">Occupied</option>
                  <option value="Unavailable">Unavailable</option>
                </select>
                <small
                  v-if="formErrors.status"
                  class="text-xs text-[#c14f4b]"
                  >{{ formErrors.status }}</small
                >
              </label>

              <label class="flex flex-col gap-2 text-sm text-[#1d2a2f]">
                <span
                  class="text-xs font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
                  >Active</span
                >
                <select
                  v-model="form.active"
                  class="h-10 rounded-md border border-[#d3d7d9] bg-white px-3 text-sm text-[#1d2a2f] outline-none focus:border-[#2d5d62]"
                >
                  <option :value="true">Yes</option>
                  <option :value="false">No</option>
                </select>
              </label>

              <label class="flex flex-col gap-2 text-sm text-[#1d2a2f]">
                <span
                  class="text-xs font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
                  >Shape</span
                >
                <select
                  v-model="form.shape"
                  class="h-10 rounded-md border border-[#d3d7d9] bg-white px-3 text-sm text-[#1d2a2f] outline-none focus:border-[#2d5d62]"
                >
                  <option value="Round">Round</option>
                  <option value="Square">Square</option>
                </select>
              </label>
            </div>
          </form>
        </div>
      </div>
    </div>

    <div
      v-if="isViewOpen && viewedTable"
      class="fixed inset-0 z-50 grid place-items-center bg-[#1d2a2f]/20 p-4"
      @click.self="closeViewForm"
    >
      <div
        class="w-full max-w-[620px] rounded-xl border border-[#d3d7d9] bg-[#f4f5f4] shadow-[0_20px_50px_rgba(29,42,47,0.16)]"
        role="dialog"
        aria-modal="true"
        aria-labelledby="view-table-title"
      >
        <div
          class="flex items-center justify-between border-b border-[#d3d7d9] px-5 py-3"
        >
          <div
            class="text-xs font-medium uppercase tracking-[0.12em] text-[#6a7377]"
          >
            Tables
          </div>
          <button
            type="button"
            class="rounded-full bg-[#dfe7e8] px-3 py-1.5 text-xs font-medium text-[#1d2a2f]"
            @click="closeViewForm"
          >
            Done
          </button>
        </div>

        <div class="px-5 py-4">
          <h2
            id="view-table-title"
            class="text-[2rem] font-semibold tracking-[-0.05em] text-[#1d2a2f]"
          >
            Table details
          </h2>

          <div class="mt-5 grid gap-4 md:grid-cols-2">
            <div class="rounded-lg border border-[#d3d7d9] bg-white p-3">
              <div
                class="text-[0.7rem] font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
              >
                Table name
              </div>
              <div class="mt-2 text-sm font-medium text-[#1d2a2f]">
                {{ viewedTable.name }}
              </div>
            </div>
            <div class="rounded-lg border border-[#d3d7d9] bg-white p-3">
              <div
                class="text-[0.7rem] font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
              >
                Branch
              </div>
              <div class="mt-2 text-sm font-medium text-[#1d2a2f]">
                {{ viewedTable.branch }}
              </div>
            </div>
            <div class="rounded-lg border border-[#d3d7d9] bg-white p-3">
              <div
                class="text-[0.7rem] font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
              >
                Capacity
              </div>
              <div class="mt-2 text-sm font-medium text-[#1d2a2f]">
                {{ viewedTable.capacity }} seats
              </div>
            </div>
            <div class="rounded-lg border border-[#d3d7d9] bg-white p-3">
              <div
                class="text-[0.7rem] font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
              >
                Shape
              </div>
              <div class="mt-2 text-sm font-medium text-[#1d2a2f]">
                {{ viewedTable.shape }}
              </div>
            </div>
            <div class="rounded-lg border border-[#d3d7d9] bg-white p-3">
              <div
                class="text-[0.7rem] font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
              >
                Status
              </div>
              <div class="mt-2 text-sm font-medium text-[#1d2a2f]">
                {{ viewedTable.status }}
              </div>
            </div>
            <div class="rounded-lg border border-[#d3d7d9] bg-white p-3">
              <div
                class="text-[0.7rem] font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
              >
                Zone
              </div>
              <div class="mt-2 text-sm font-medium text-[#1d2a2f]">
                {{ viewedTable.zone }}
              </div>
            </div>
            <div class="rounded-lg border border-[#d3d7d9] bg-white p-3">
              <div
                class="text-[0.7rem] font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
              >
                Active
              </div>
              <div class="mt-2 text-sm font-medium text-[#1d2a2f]">
                {{ viewedTable.active ? 'Yes' : 'No' }}
              </div>
            </div>
            <div class="rounded-lg border border-[#d3d7d9] bg-white p-3">
              <div
                class="text-[0.7rem] font-semibold uppercase tracking-[0.08em] text-[#6a7377]"
              >
                Last updated
              </div>
              <div class="mt-2 text-sm font-medium text-[#1d2a2f]">
                {{ formatUpdated(viewedTable.lastUpdated) }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';

export type TableStatus = 'Available' | 'Reserved' | 'Occupied' | 'Unavailable';
export type TableShape = 'Round' | 'Square' | 'Oval' | 'Rectangle';

export type TableRecord = {
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

type ViewMode = 'list' | 'create' | 'edit' | 'view';

const branches = ['Sukhumvit', 'Silom', 'Bangkok'];
const zoneOptions = [
  'All zones',
  'Main Hall',
  'Private Room',
  'Rooftop',
  'VIP',
];
const statusOptions: (TableStatus | 'All statuses')[] = [
  'All statuses',
  'Available',
  'Reserved',
  'Occupied',
  'Unavailable',
];
const shapeOptions: TableShape[] = ['Round', 'Square', 'Oval', 'Rectangle'];
const capacityOptions = [
  1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 25, 30, 40, 50,
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
    name: 'Table 2',
    branch: 'Sukhumvit',
    capacity: 2,
    shape: 'Square',
    status: 'Available',
    active: true,
    zone: 'Main Hall',
    lastUpdated: '2025-09-25T19:40:00',
  },
  {
    id: 3,
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
    id: 4,
    name: 'Table 4',
    branch: 'Sukhumvit',
    capacity: 6,
    shape: 'Round',
    status: 'Available',
    active: true,
    zone: 'Main Hall',
    lastUpdated: '2025-09-25T18:30:00',
  },
  {
    id: 5,
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
    id: 6,
    name: 'Table 6',
    branch: 'Sukhumvit',
    capacity: 4,
    shape: 'Square',
    status: 'Available',
    active: true,
    zone: 'Main Hall',
    lastUpdated: '2025-09-25T17:15:00',
  },
  {
    id: 7,
    name: 'Table 7',
    branch: 'Sukhumvit',
    capacity: 8,
    shape: 'Rectangle',
    status: 'Reserved',
    active: true,
    zone: 'Private Room',
    lastUpdated: '2025-09-25T21:00:00',
  },
  {
    id: 8,
    name: 'Table 8',
    branch: 'Sukhumvit',
    capacity: 4,
    shape: 'Round',
    status: 'Available',
    active: true,
    zone: 'Main Hall',
    lastUpdated: '2025-09-25T16:20:00',
  },
  {
    id: 9,
    name: 'Table 9',
    branch: 'Sukhumvit',
    capacity: 8,
    shape: 'Square',
    status: 'Occupied',
    active: true,
    zone: 'Rooftop',
    lastUpdated: '2025-09-26T15:20:00',
  },
  {
    id: 10,
    name: 'Table 10',
    branch: 'Sukhumvit',
    capacity: 2,
    shape: 'Square',
    status: 'Available',
    active: true,
    zone: 'VIP',
    lastUpdated: '2025-09-25T14:10:00',
  },
  {
    id: 11,
    name: 'Table 11',
    branch: 'Sukhumvit',
    capacity: 4,
    shape: 'Round',
    status: 'Available',
    active: true,
    zone: 'Main Hall',
    lastUpdated: '2025-09-25T13:00:00',
  },
  {
    id: 13,
    name: 'Table 13',
    branch: 'Sukhumvit',
    capacity: 6,
    shape: 'Oval',
    status: 'Reserved',
    active: true,
    zone: 'Private Room',
    lastUpdated: '2025-09-25T19:15:00',
  },
  {
    id: 15,
    name: 'Table 15',
    branch: 'Sukhumvit',
    capacity: 4,
    shape: 'Square',
    status: 'Available',
    active: true,
    zone: 'Main Hall',
    lastUpdated: '2025-09-25T11:45:00',
  },
  {
    id: 16,
    name: 'Table 16',
    branch: 'Sukhumvit',
    capacity: 4,
    shape: 'Round',
    status: 'Available',
    active: true,
    zone: 'Main Hall',
    lastUpdated: '2025-09-25T10:30:00',
  },
  {
    id: 17,
    name: 'Table 17',
    branch: 'Sukhumvit',
    capacity: 8,
    shape: 'Rectangle',
    status: 'Reserved',
    active: true,
    zone: 'VIP',
    lastUpdated: '2025-09-25T20:45:00',
  },
  {
    id: 20,
    name: 'Table 20',
    branch: 'Sukhumvit',
    capacity: 2,
    shape: 'Round',
    status: 'Available',
    active: true,
    zone: 'Rooftop',
    lastUpdated: '2025-09-25T09:15:00',
  },
  {
    id: 21,
    name: 'Table 21',
    branch: 'Sukhumvit',
    capacity: 6,
    shape: 'Oval',
    status: 'Available',
    active: true,
    zone: 'Main Hall',
    lastUpdated: '2025-09-25T08:50:00',
  },
  {
    id: 23,
    name: 'Table 23',
    branch: 'Sukhumvit',
    capacity: 4,
    shape: 'Square',
    status: 'Occupied',
    active: true,
    zone: 'Main Hall',
    lastUpdated: '2025-09-25T22:10:00',
  },
  {
    id: 24,
    name: 'Table 24',
    branch: 'Sukhumvit',
    capacity: 2,
    shape: 'Round',
    status: 'Reserved',
    active: true,
    zone: 'VIP',
    lastUpdated: '2025-09-25T21:15:00',
  },
  {
    id: 25,
    name: 'Table 25',
    branch: 'Sukhumvit',
    capacity: 4,
    shape: 'Round',
    status: 'Available',
    active: true,
    zone: 'Main Hall',
    lastUpdated: '2025-09-25T08:00:00',
  },
  // Silom
  {
    id: 12,
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
    id: 14,
    name: 'Table 14',
    branch: 'Silom',
    capacity: 8,
    shape: 'Round',
    status: 'Reserved',
    active: true,
    zone: 'Private Room',
    lastUpdated: '2025-09-25T22:32:00',
  },
  // Bangkok
  {
    id: 18,
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
    id: 19,
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
    id: 22,
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
const statusFilter = ref<TableStatus | 'All statuses'>('All statuses');
const currentPage = ref(1);
const pageSize = 5;
const isLoading = ref(true);

const currentView = ref<ViewMode>('list');
const editingId = ref<number | null>(null);
const viewedTable = ref<TableRecord | null>(null);
const activeNav = ref<'Dashboard' | 'Branches' | 'Tables' | 'Games'>('Tables');

const defaultForm = {
  name: '',
  branch: 'Sukhumvit',
  capacity: '4',
  status: 'Available' as TableStatus,
  active: true,
  shape: 'Round' as TableShape,
  zone: 'Main Hall',
};

const form = reactive({ ...defaultForm });
const formErrors = reactive({ name: '', branch: '', capacity: '', status: '' });

const selectedBranchTables = computed(() =>
  tables.value.filter((t) => t.branch === selectedBranch.value),
);

const summary = computed(() => {
  const list = selectedBranchTables.value;
  return {
    total: list.length,
    available: list.filter((t) => t.status === 'Available').length,
    reserved: list.filter((t) => t.status === 'Reserved').length,
    occupied: list.filter((t) => t.status === 'Occupied').length,
  };
});

const filteredTables = computed(() => {
  const query = search.value.trim().toLowerCase();
  return selectedBranchTables.value.filter((t) => {
    const matchesName = !query || t.name.toLowerCase().includes(query);
    const matchesZone =
      zoneFilter.value === 'All zones' || t.zone === zoneFilter.value;
    const matchesStatus =
      statusFilter.value === 'All statuses' || t.status === statusFilter.value;
    return matchesName && matchesZone && matchesStatus;
  });
});

const totalPages = computed(() =>
  Math.max(1, Math.ceil(filteredTables.value.length / pageSize)),
);

const pageNumbers = computed(() => {
  const total = totalPages.value;
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
  if (currentPage.value <= 3) return [1, 2, 3, 'ellipsis', total];
  if (currentPage.value >= total - 2)
    return [1, 'ellipsis', total - 2, total - 1, total];
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
    'inline-flex items-center rounded-full bg-[#e9f5ee] px-3 py-0.5 text-xs font-semibold text-[#237847] border border-[#cbeed6]',
  Reserved:
    'inline-flex items-center rounded-full bg-[#edf3fb] px-3 py-0.5 text-xs font-semibold text-[#2563eb] border border-[#bfdbfe]',
  Occupied:
    'inline-flex items-center rounded-full bg-[#fff4e9] px-3 py-0.5 text-xs font-semibold text-[#d97706] border border-[#fde68a]',
  Unavailable:
    'inline-flex items-center rounded-full bg-[#fdeeed] px-3 py-0.5 text-xs font-semibold text-[#dc2626] border border-[#fecaca]',
};

watch([selectedBranch, search, zoneFilter, statusFilter], () => {
  currentPage.value = 1;
});

watch(
  () => selectedBranch.value,
  (newBranch) => {
    form.branch = newBranch;
  },
);

onMounted(() => {
  document.body.style.background = 'white';
  document.body.style.color = '#1e293b';
  setTimeout(() => {
    isLoading.value = false;
  }, 350);
});

onUnmounted(() => {
  document.body.style.background = '';
  document.body.style.color = '';
});

function handleNavClick(
  section: 'Dashboard' | 'Branches' | 'Tables' | 'Games',
) {
  activeNav.value = section;
  if (section === 'Tables') closeForm();
}

function openCreateForm() {
  editingId.value = null;
  Object.assign(form, { ...defaultForm, branch: selectedBranch.value });
  clearErrors();
  currentView.value = 'create';
}

function openEditForm(table: TableRecord) {
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
  clearErrors();
  currentView.value = 'edit';
}

function openViewForm(table: TableRecord) {
  viewedTable.value = table;
  currentView.value = 'view';
}

function closeViewForm() {
  currentView.value = 'list';
  viewedTable.value = null;
}

function switchToEditFromView() {
  if (viewedTable.value) openEditForm(viewedTable.value);
}

function closeForm() {
  currentView.value = 'list';
  editingId.value = null;
  viewedTable.value = null;
  Object.assign(form, { ...defaultForm, branch: selectedBranch.value });
  clearErrors();
}

function clearErrors() {
  formErrors.name = '';
  formErrors.branch = '';
  formErrors.capacity = '';
  formErrors.status = '';
}

function validateField(field: 'name' | 'branch' | 'capacity' | 'status') {
  const trimmedName = form.name.trim();

  if (field === 'name') {
    if (!trimmedName) {
      formErrors.name = 'Table name is required';
    } else if (trimmedName.length > 50) {
      formErrors.name = 'Table name cannot exceed 50 characters';
    } else {
      const isDuplicate = tables.value.some(
        (t) =>
          t.id !== editingId.value &&
          t.branch === form.branch &&
          t.name.trim().toLowerCase() === trimmedName.toLowerCase(),
      );
      formErrors.name = isDuplicate
        ? 'A table with this name already exists in this branch'
        : '';
    }
  }

  if (field === 'branch') {
    formErrors.branch = !form.branch.trim() ? 'Branch is required' : '';
    if (trimmedName) validateField('name');
  }

  if (field === 'capacity') {
    formErrors.capacity = !String(form.capacity).trim()
      ? 'Capacity is required'
      : '';
  }

  if (field === 'status') {
    formErrors.status = !form.status ? 'Status is required' : '';
  }
}

function validateForm(): boolean {
  validateField('name');
  validateField('branch');
  validateField('capacity');
  validateField('status');
  return (
    !formErrors.name &&
    !formErrors.branch &&
    !formErrors.capacity &&
    !formErrors.status
  );
}

function saveTable() {
  if (!validateForm()) return;
  const payload: TableRecord = {
    id: editingId.value ?? Date.now(),
    name: form.name.trim(),
    branch: form.branch,
    capacity: Number(form.capacity),
    status: form.status,
    active: form.active,
    shape: form.shape,
    zone: form.zone,
    lastUpdated: new Date().toISOString(),
  };
  if (currentView.value === 'edit' && editingId.value) {
    tables.value = tables.value.map((t) =>
      t.id === editingId.value ? payload : t,
    );
  } else {
    tables.value = [payload, ...tables.value];
  }
  closeForm();
}

function confirmDelete(id: number) {
  const table = tables.value.find((t) => t.id === id);
  if (
    table &&
    window.confirm(`Delete ${table.name}? This action cannot be undone.`)
  ) {
    tables.value = tables.value.filter((t) => t.id !== id);
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
  <main
    class="m-0 min-h-screen w-full bg-white p-0 font-[Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,'Segoe_UI',Roboto,Helvetica,Arial,sans-serif] text-[#1e293b] antialiased [box-sizing:border-box]"
  >
    <!-- ─── GLOBAL HEADER ──────────────────────────────────── -->
    <header class="sticky top-0 z-30 border-b border-gray-200 bg-white">
      <div
        class="mx-auto flex max-w-6xl items-center justify-between px-6 py-3"
      >
        <!-- Logo -->
        <div
          class="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-[#1b4d4f]"
          aria-label="BGStore"
        />

        <!-- Nav -->
        <nav class="flex items-center gap-1" aria-label="Primary navigation">
          <!-- Dashboard -->
          <button
            type="button"
            :class="[
              'flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-xs font-medium transition',
              activeNav === 'Dashboard'
                ? 'bg-[#e8efef] font-semibold text-[#1b2b30]'
                : 'text-gray-500 hover:bg-gray-100 hover:text-gray-800',
            ]"
            :aria-current="activeNav === 'Dashboard' ? 'page' : undefined"
            @click="handleNavClick('Dashboard')"
          >
            <svg
              class="h-3.5 w-3.5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z" />
              <polyline points="9 22 9 12 15 12 15 22" />
            </svg>
            Dashboard
          </button>

          <!-- Branches -->
          <button
            type="button"
            :class="[
              'flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-xs font-medium transition',
              activeNav === 'Branches'
                ? 'bg-[#e8efef] font-semibold text-[#1b2b30]'
                : 'text-gray-500 hover:bg-gray-100 hover:text-gray-800',
            ]"
            :aria-current="activeNav === 'Branches' ? 'page' : undefined"
            @click="handleNavClick('Branches')"
          >
            <svg
              class="h-3.5 w-3.5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M21 10c0 7-9 13-9 13S3 17 3 10a9 9 0 0118 0z" />
              <circle cx="12" cy="10" r="3" />
            </svg>
            Branches
          </button>

          <!-- Tables -->
          <button
            type="button"
            :class="[
              'flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-xs font-medium transition',
              activeNav === 'Tables'
                ? 'bg-[#e8efef] font-semibold text-[#1b2b30]'
                : 'text-gray-500 hover:bg-gray-100 hover:text-gray-800',
            ]"
            :aria-current="activeNav === 'Tables' ? 'page' : undefined"
            @click="handleNavClick('Tables')"
          >
            <svg
              class="h-3.5 w-3.5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <rect x="3" y="4" width="18" height="4" rx="1" />
              <line x1="6" y1="8" x2="6" y2="20" />
              <line x1="18" y1="8" x2="18" y2="20" />
              <line x1="4" y1="20" x2="20" y2="20" />
            </svg>
            Tables
          </button>

          <!-- Games -->
          <button
            type="button"
            :class="[
              'flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-xs font-medium transition',
              activeNav === 'Games'
                ? 'bg-[#e8efef] font-semibold text-[#1b2b30]'
                : 'text-gray-500 hover:bg-gray-100 hover:text-gray-800',
            ]"
            :aria-current="activeNav === 'Games' ? 'page' : undefined"
            @click="handleNavClick('Games')"
          >
            <svg
              class="h-3.5 w-3.5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <rect x="2" y="7" width="20" height="12" rx="4" />
              <path d="M7 13h2m-1-1v2" />
              <circle cx="16" cy="12" r="1" fill="currentColor" stroke="none" />
              <circle
                cx="18.5"
                cy="14"
                r="1"
                fill="currentColor"
                stroke="none"
              />
            </svg>
            Games
          </button>
        </nav>

        <!-- Spacer so nav stays centred visually -->
        <div class="h-9 w-9" aria-hidden="true" />
      </div>
    </header>

    <!-- ─── LIST VIEW ──────────────────────────────────────── -->
    <div v-if="currentView === 'list'" class="mx-auto max-w-6xl px-6 py-6">
      <!-- Subheader toolbar -->
      <div
        class="flex items-center justify-between gap-4 border-b border-gray-200 pb-4"
      >
        <span class="text-sm font-semibold text-gray-500">Tables</span>
        <div class="flex items-center gap-3">
          <label class="sr-only" for="panel-branch">Branch</label>
          <select
            id="panel-branch"
            v-model="selectedBranch"
            aria-label="Select branch"
            class="h-9 min-w-[150px] rounded-full border border-gray-300 bg-white px-4 text-xs font-medium text-gray-800 outline-none transition hover:border-gray-400 focus:border-[#1b4d4f]"
          >
            <option v-for="b in branches" :key="b" :value="b">{{ b }}</option>
          </select>
          <button
            type="button"
            class="inline-flex items-center rounded-full bg-[#1b4d4f] px-4 py-2 text-xs font-semibold text-white shadow-sm transition hover:bg-[#153b3d]"
            @click="openCreateForm"
          >
            + Add table
          </button>
        </div>
      </div>

      <!-- Heading -->
      <div class="pt-5">
        <h1 class="text-2xl font-bold tracking-tight text-gray-900">
          Table management
        </h1>
        <p class="mt-1 text-xs text-gray-500">
          Overview of table inventory, current capacity and availability status
          across store branches and zones.
        </p>
      </div>

      <!-- Summary cards -->
      <div class="mt-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <!-- Total tables -->
        <article class="rounded-xl border border-gray-200 bg-[#f8fafc] p-4">
          <div class="flex items-center justify-between gap-2">
            <span
              class="text-[0.68rem] font-semibold uppercase tracking-wider text-gray-500"
              >Total tables</span
            >
            <span
              class="grid h-6 w-6 place-items-center rounded-md bg-gray-100 text-gray-500"
            >
              <svg
                class="h-3.5 w-3.5"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path
                  d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"
                />
                <path
                  d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"
                />
              </svg>
            </span>
          </div>
          <strong
            class="mt-3 block text-3xl font-bold leading-none tracking-tight text-gray-900"
          >
            {{ summary.total }}
          </strong>
        </article>

        <!-- Available -->
        <article class="rounded-xl border border-emerald-200 bg-[#f0fdf4] p-4">
          <div class="flex items-center justify-between gap-2">
            <span
              class="text-[0.68rem] font-semibold uppercase tracking-wider text-emerald-700"
              >Available</span
            >
            <span
              class="grid h-6 w-6 place-items-center rounded-md bg-emerald-100 text-emerald-700"
            >
              <svg
                class="h-3.5 w-3.5"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <rect x="3" y="3" width="18" height="18" rx="3" />
                <polyline points="9 12 11 14 15 10" />
              </svg>
            </span>
          </div>
          <strong
            class="mt-3 block text-3xl font-bold leading-none tracking-tight text-gray-900"
          >
            {{ summary.available }}
          </strong>
        </article>

        <!-- Reserved -->
        <article class="rounded-xl border border-blue-200 bg-[#eff6ff] p-4">
          <div class="flex items-center justify-between gap-2">
            <span
              class="text-[0.68rem] font-semibold uppercase tracking-wider text-blue-700"
              >Reserved</span
            >
            <span
              class="grid h-6 w-6 place-items-center rounded-md bg-blue-100 text-blue-700"
            >
              <svg
                class="h-3.5 w-3.5"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M19 21l-7-5-7 5V5a2 2 0 012-2h10a2 2 0 012 2z" />
              </svg>
            </span>
          </div>
          <strong
            class="mt-3 block text-3xl font-bold leading-none tracking-tight text-gray-900"
          >
            {{ summary.reserved }}
          </strong>
        </article>

        <!-- Occupied -->
        <article class="rounded-xl border border-amber-200 bg-[#fffbeb] p-4">
          <div class="flex items-center justify-between gap-2">
            <span
              class="text-[0.68rem] font-semibold uppercase tracking-wider text-amber-700"
              >Occupied</span
            >
            <span
              class="grid h-6 w-6 place-items-center rounded-md bg-amber-100 text-amber-700"
            >
              <svg
                class="h-3.5 w-3.5"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <circle cx="12" cy="12" r="10" />
                <circle
                  cx="12"
                  cy="12"
                  r="3"
                  fill="currentColor"
                  stroke="none"
                />
              </svg>
            </span>
          </div>
          <strong
            class="mt-3 block text-3xl font-bold leading-none tracking-tight text-gray-900"
          >
            {{ summary.occupied }}
          </strong>
        </article>
      </div>

      <!-- Filter bar -->
      <div class="mt-5 flex flex-col gap-3 md:flex-row md:items-center">
        <label
          class="flex flex-1 items-center gap-2 rounded-lg border border-gray-300 bg-white px-3.5 py-2 transition focus-within:border-[#1b4d4f]"
          aria-label="Search tables"
        >
          <svg
            class="h-3.5 w-3.5 shrink-0 text-gray-400"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            v-model="search"
            type="search"
            placeholder="Search table by name..."
            aria-label="Search tables by name"
            class="w-full border-0 bg-transparent text-xs text-gray-800 placeholder:text-gray-400 focus:outline-none"
          />
        </label>

        <select
          v-model="zoneFilter"
          aria-label="Filter by zone"
          class="h-9 min-w-[150px] rounded-lg border border-gray-300 bg-white px-3 text-xs font-medium text-gray-800 outline-none transition hover:border-gray-400 focus:border-[#1b4d4f]"
        >
          <option v-for="z in zoneOptions" :key="z" :value="z">{{ z }}</option>
        </select>

        <select
          v-model="statusFilter"
          aria-label="Filter by status"
          class="h-9 min-w-[150px] rounded-lg border border-gray-300 bg-white px-3 text-xs font-medium text-gray-800 outline-none transition hover:border-gray-400 focus:border-[#1b4d4f]"
        >
          <option v-for="s in statusOptions" :key="s" :value="s">
            {{ s }}
          </option>
        </select>
      </div>

      <!-- Table container -->
      <div
        class="mt-4 overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm"
        aria-live="polite"
      >
        <template v-if="isLoading">
          <div class="grid gap-3 p-6">
            <div class="h-5 animate-pulse rounded-md bg-gray-100" />
            <div class="h-5 w-2/3 animate-pulse rounded-md bg-gray-100" />
            <div class="h-5 animate-pulse rounded-md bg-gray-100" />
          </div>
        </template>

        <template v-else-if="filteredTables.length === 0">
          <div class="grid place-items-center gap-2 px-6 py-12 text-center">
            <div
              class="grid h-12 w-12 place-items-center rounded-full bg-gray-100 text-gray-400"
            >
              <svg
                class="h-5 w-5"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
              >
                <circle cx="12" cy="12" r="10" />
                <line x1="8" y1="8" x2="16" y2="16" />
                <line x1="16" y1="8" x2="8" y2="16" />
              </svg>
            </div>
            <h2 class="text-base font-bold text-gray-900">
              No tables match your filters
            </h2>
            <p class="max-w-xs text-xs text-gray-500">
              Try changing the branch, search text, or status filter.
            </p>
          </div>
        </template>

        <template v-else>
          <div class="overflow-x-auto">
            <table class="w-full border-collapse text-left">
              <thead>
                <tr
                  class="border-b border-gray-200 bg-gray-50 text-[0.68rem] font-semibold uppercase tracking-wider text-gray-500"
                >
                  <th class="px-5 py-3">Table</th>
                  <th class="px-5 py-3">Capacity</th>
                  <th class="px-5 py-3">Shape</th>
                  <th class="px-5 py-3">Status</th>
                  <th class="px-5 py-3">Last updated</th>
                  <th class="px-5 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100">
                <tr
                  v-for="table in paginatedTables"
                  :key="table.id"
                  class="transition hover:bg-gray-50/60"
                >
                  <td class="px-5 py-3.5 text-xs font-semibold text-gray-900">
                    {{ table.name }}
                  </td>
                  <td class="px-5 py-3.5 text-xs text-gray-600">
                    {{ table.capacity }} seats
                  </td>
                  <td class="px-5 py-3.5 text-xs text-gray-600">
                    {{ table.shape }}
                  </td>
                  <td class="px-5 py-3.5">
                    <span :class="statusClasses[table.status]">{{
                      table.status
                    }}</span>
                  </td>
                  <td class="px-5 py-3.5 text-xs text-gray-500">
                    {{ formatUpdated(table.lastUpdated) }}
                  </td>
                  <td class="px-5 py-3.5 text-right">
                    <div
                      class="inline-flex items-center gap-3 text-xs font-semibold"
                    >
                      <button
                        type="button"
                        class="text-[#1b4d4f] transition hover:text-[#153b3d] hover:underline"
                        @click="openViewForm(table)"
                      >
                        View
                      </button>
                      <button
                        type="button"
                        class="text-[#1b4d4f] transition hover:text-[#153b3d] hover:underline"
                        @click="openEditForm(table)"
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        class="text-red-600 transition hover:text-red-800 hover:underline"
                        @click="confirmDelete(table.id)"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </div>

      <!-- Pagination -->
      <div
        v-if="!isLoading && filteredTables.length > 0"
        class="mt-5 flex items-center justify-end gap-3 pb-2"
        aria-label="Table pagination"
      >
        <div class="mr-2 text-xs font-medium text-gray-500">
          Showing
          {{
            Math.min((currentPage - 1) * pageSize + 1, filteredTables.length)
          }}-{{ Math.min(currentPage * pageSize, filteredTables.length) }} of
          {{ filteredTables.length }}
        </div>
        <button
          type="button"
          aria-label="Previous page"
          class="flex h-8 w-8 items-center justify-center rounded-full border border-gray-300 bg-white text-sm text-gray-700 transition hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40"
          :disabled="currentPage === 1"
          @click="currentPage -= 1"
        >
          ←
        </button>

        <div class="flex items-center gap-1 text-xs font-semibold">
          <template
            v-for="(page, index) in pageNumbers"
            :key="`page-${String(page)}-${index}`"
          >
            <button
              v-if="page !== 'ellipsis'"
              type="button"
              :class="[
                'flex h-7 w-7 items-center justify-center rounded-md transition',
                page === currentPage
                  ? 'bg-[#1b4d4f] text-white'
                  : 'text-gray-600 hover:bg-gray-100',
              ]"
              @click="currentPage = Number(page)"
            >
              {{ page }}
            </button>
            <span v-else class="px-1 text-sm text-gray-400">...</span>
          </template>
        </div>

        <button
          type="button"
          aria-label="Next page"
          class="flex h-8 w-8 items-center justify-center rounded-full border border-gray-300 bg-white text-sm text-gray-700 transition hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40"
          :disabled="currentPage >= totalPages"
          @click="currentPage += 1"
        >
          →
        </button>
      </div>
    </div>

    <!-- ─── ADD / EDIT VIEW ────────────────────────────────── -->
    <div
      v-else-if="currentView === 'create' || currentView === 'edit'"
      class="mx-auto max-w-6xl px-6 py-6"
    >
      <!-- Subheader -->
      <div
        class="flex items-center justify-between gap-4 border-b border-gray-200 pb-4"
      >
        <nav
          aria-label="Breadcrumb"
          class="flex items-center gap-2 text-xs font-semibold text-gray-500"
        >
          <button
            type="button"
            class="transition hover:text-gray-900 hover:underline"
            @click="closeForm"
          >
            Tables
          </button>
          <span class="text-gray-400">›</span>
          <span class="text-gray-900">{{
            currentView === 'edit' ? 'Edit table' : 'Add table'
          }}</span>
        </nav>
        <div class="flex items-center gap-2">
          <button
            type="button"
            class="rounded-lg border border-gray-300 bg-white px-5 py-1.5 text-xs font-semibold text-gray-700 transition hover:bg-gray-50"
            @click="closeForm"
          >
            Cancel
          </button>
          <button
            type="submit"
            form="table-management-form"
            class="rounded-lg bg-[#1b4d4f] px-6 py-1.5 text-xs font-semibold text-white transition hover:bg-[#153b3d]"
            @click="saveTable"
          >
            Save
          </button>
        </div>
      </div>

      <!-- Heading -->
      <div class="pt-5">
        <h1 class="text-2xl font-bold tracking-tight text-gray-900">
          {{
            currentView === 'edit'
              ? `Edit table — ${form.name || 'Table'}`
              : 'Add table'
          }}
        </h1>
      </div>

      <!-- Form card -->
      <div class="mt-6 rounded-xl border border-gray-200 bg-white p-6">
        <h2 class="text-sm font-semibold text-gray-900">Table details</h2>

        <form
          id="table-management-form"
          class="mt-5 grid gap-x-8 gap-y-5 md:grid-cols-2"
          novalidate
          @submit.prevent="saveTable"
        >
          <!-- Table name -->
          <div class="flex flex-col gap-1.5">
            <label for="form-name" class="text-xs font-medium text-gray-700">
              Table name <span class="text-red-500">*</span>
            </label>
            <input
              id="form-name"
              v-model="form.name"
              type="text"
              placeholder="Table 5"
              maxlength="60"
              class="h-10 rounded-lg border border-gray-300 bg-white px-3.5 text-sm text-gray-900 outline-none transition placeholder:text-gray-400 hover:border-gray-400 focus:border-[#1b4d4f]"
              @input="validateField('name')"
              @blur="validateField('name')"
            />
            <small
              v-if="formErrors.name"
              class="text-xs font-medium text-red-600"
              >{{ formErrors.name }}</small
            >
          </div>

          <!-- Branch -->
          <div class="flex flex-col gap-1.5">
            <label for="form-branch" class="text-xs font-medium text-gray-700">
              Branch <span class="text-red-500">*</span>
            </label>
            <select
              id="form-branch"
              v-model="form.branch"
              class="h-10 rounded-lg border border-gray-300 bg-white px-3.5 text-sm text-gray-900 outline-none transition hover:border-gray-400 focus:border-[#1b4d4f]"
              @change="validateField('branch')"
            >
              <option v-for="b in branches" :key="b" :value="b">{{ b }}</option>
            </select>
            <small
              v-if="formErrors.branch"
              class="text-xs font-medium text-red-600"
              >{{ formErrors.branch }}</small
            >
          </div>

          <!-- Capacity -->
          <div class="flex flex-col gap-1.5">
            <label
              for="form-capacity"
              class="text-xs font-medium text-gray-700"
            >
              Capacity <span class="text-red-500">*</span>
            </label>
            <select
              id="form-capacity"
              v-model="form.capacity"
              aria-label="Capacity"
              class="h-10 rounded-lg border border-gray-300 bg-white px-3.5 text-sm text-gray-900 outline-none transition hover:border-gray-400 focus:border-[#1b4d4f]"
              @change="validateField('capacity')"
            >
              <option v-for="n in capacityOptions" :key="n" :value="String(n)">
                {{ n }} seat{{ n === 1 ? '' : 's' }}
              </option>
            </select>
            <small
              v-if="formErrors.capacity"
              class="text-xs font-medium text-red-600"
              >{{ formErrors.capacity }}</small
            >
          </div>

          <!-- Status -->
          <div class="flex flex-col gap-1.5">
            <label for="form-status" class="text-xs font-medium text-gray-700">
              Status <span class="text-red-500">*</span>
            </label>
            <select
              id="form-status"
              v-model="form.status"
              class="h-10 rounded-lg border border-gray-300 bg-white px-3.5 text-sm text-gray-900 outline-none transition hover:border-gray-400 focus:border-[#1b4d4f]"
              @change="validateField('status')"
            >
              <option value="Available">Available</option>
              <option value="Reserved">Reserved</option>
              <option value="Occupied">Occupied</option>
              <option value="Unavailable">Unavailable</option>
            </select>
            <small
              v-if="formErrors.status"
              class="text-xs font-medium text-red-600"
              >{{ formErrors.status }}</small
            >
          </div>

          <!-- Active -->
          <div class="flex flex-col gap-1.5">
            <label for="form-active" class="text-xs font-medium text-gray-700"
              >Active</label
            >
            <select
              id="form-active"
              v-model="form.active"
              class="h-10 rounded-lg border border-gray-300 bg-white px-3.5 text-sm text-gray-900 outline-none transition hover:border-gray-400 focus:border-[#1b4d4f]"
            >
              <option :value="true">Yes</option>
              <option :value="false">No</option>
            </select>
          </div>

          <!-- Shape -->
          <div class="flex flex-col gap-1.5">
            <label for="form-shape" class="text-xs font-medium text-gray-700"
              >Shape</label
            >
            <select
              id="form-shape"
              v-model="form.shape"
              class="h-10 rounded-lg border border-gray-300 bg-white px-3.5 text-sm text-gray-900 outline-none transition hover:border-gray-400 focus:border-[#1b4d4f]"
            >
              <option v-for="s in shapeOptions" :key="s" :value="s">
                {{ s }}
              </option>
            </select>
          </div>
        </form>
      </div>
    </div>

    <!-- ─── VIEW DETAILS ───────────────────────────────────── -->
    <div
      v-else-if="currentView === 'view' && viewedTable"
      class="mx-auto max-w-6xl px-6 py-6"
    >
      <!-- Subheader -->
      <div
        class="flex items-center justify-between gap-4 border-b border-gray-200 pb-4"
      >
        <nav
          aria-label="Breadcrumb"
          class="flex items-center gap-2 text-xs font-semibold text-gray-500"
        >
          <button
            type="button"
            class="transition hover:text-gray-900 hover:underline"
            @click="closeViewForm"
          >
            Tables
          </button>
          <span class="text-gray-400">›</span>
          <span class="text-gray-900">View table</span>
        </nav>
        <div class="flex items-center gap-2">
          <button
            type="button"
            class="rounded-lg border border-gray-300 bg-white px-5 py-1.5 text-xs font-semibold text-gray-700 transition hover:bg-gray-50"
            @click="closeViewForm"
          >
            Done
          </button>
          <button
            type="button"
            class="rounded-lg bg-[#1b4d4f] px-6 py-1.5 text-xs font-semibold text-white transition hover:bg-[#153b3d]"
            @click="switchToEditFromView"
          >
            Edit
          </button>
        </div>
      </div>

      <!-- Heading -->
      <div class="pt-5">
        <h1 class="text-2xl font-bold tracking-tight text-gray-900">
          Table details — {{ viewedTable.name }}
        </h1>
      </div>

      <!-- Details card -->
      <div class="mt-6 rounded-xl border border-gray-200 bg-white p-6">
        <h2 class="text-sm font-semibold text-gray-900">Table details</h2>
        <div class="mt-5 grid gap-4 md:grid-cols-2">
          <div
            v-for="field in [
              { label: 'Table name', value: viewedTable.name },
              { label: 'Branch', value: viewedTable.branch },
              { label: 'Capacity', value: `${viewedTable.capacity} seats` },
              { label: 'Shape', value: viewedTable.shape },
              { label: 'Active', value: viewedTable.active ? 'Yes' : 'No' },
              { label: 'Zone', value: viewedTable.zone },
              {
                label: 'Last updated',
                value: formatUpdated(viewedTable.lastUpdated),
              },
            ]"
            :key="field.label"
            class="flex flex-col gap-1 rounded-lg border border-gray-200 bg-gray-50/50 p-4"
          >
            <span
              class="text-[0.68rem] font-semibold uppercase tracking-wider text-gray-500"
              >{{ field.label }}</span
            >
            <span class="text-sm font-semibold text-gray-900">{{
              field.value
            }}</span>
          </div>
          <div
            class="flex flex-col gap-1 rounded-lg border border-gray-200 bg-gray-50/50 p-4"
          >
            <span
              class="text-[0.68rem] font-semibold uppercase tracking-wider text-gray-500"
              >Status</span
            >
            <span
              :class="statusClasses[viewedTable.status]"
              class="mt-0.5 self-start"
              >{{ viewedTable.status }}</span
            >
          </div>
        </div>
      </div>
    </div>
  </main>
</template>

<style>
/* Reset body background when this view is active */
body:has(main[data-page='tables']) {
  background: white;
}
</style>

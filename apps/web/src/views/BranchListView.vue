<template>
  <div class="w-full bg-white">
    <!-- Sub Header Bar -->
    <div class="w-full border-b border-gray-200 bg-white">
      <div class="w-full px-12 h-16 flex justify-between items-center">
        <span class="text-[17px] font-bold text-gray-900 tracking-tight"
          >Branches</span
        >
        <button
          class="bg-[#386671] hover:bg-[#2c535c] text-white px-5 py-2.5 rounded-lg flex items-center gap-2 text-sm font-medium transition cursor-pointer shadow-xs"
          @click="goToAddBranch"
        >
          <svg
            class="w-4 h-4"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2.5"
              d="M12 4v16m8-8H4"
            />
          </svg>
          Add branch
        </button>
      </div>
    </div>

    <!-- Main Content Area -->
    <main class="w-full px-12 pt-8 pb-14">
      <!-- Title Header -->
      <h1
        class="text-gray-900 font-bold tracking-tight"
        style="
          font-size: 32px !important;
          line-height: 40px !important;
          margin: 0 0 24px 0 !important;
        "
      >
        Branch management
      </h1>

      <!-- Filters & Search Row -->
      <div class="flex items-center gap-4 mb-6">
        <!-- Search Input -->
        <div class="relative w-[340px]">
          <span
            class="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-gray-600"
          >
            <svg
              class="w-4 h-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2.2"
                d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
              />
            </svg>
          </span>
          <input
            v-model="searchQuery"
            class="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm text-gray-900 placeholder-gray-500 focus:outline-none focus:border-[#386671] focus:ring-1 focus:ring-[#386671] bg-white transition"
            placeholder="Search branch name or code"
            type="text"
          />
        </div>

        <!-- Status Dropdown -->
        <div class="relative w-[160px]">
          <select
            v-model="selectedStatus"
            class="appearance-none w-full border border-gray-300 rounded-lg pl-4 pr-9 py-2.5 text-sm text-gray-900 font-medium bg-white focus:outline-none focus:border-[#386671] focus:ring-1 focus:ring-[#386671] transition cursor-pointer"
          >
            <option value="All">All statuses</option>
            <option value="Active">Active</option>
            <option value="Inactive">Inactive</option>
          </select>
          <span
            class="absolute inset-y-0 right-0 pr-3 flex items-center pointer-events-none text-gray-600"
          >
            <svg
              class="w-3.5 h-3.5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2.2"
                d="M19 9l-7 7-7-7"
              />
            </svg>
          </span>
        </div>
      </div>

      <!-- Data Table Container (ใช้ table-fixed และกระจายเปอร์เซ็นต์ทุกคอลัมน์อย่างสมดุล) -->
      <div
        class="w-full border border-gray-200 rounded-2xl overflow-hidden bg-white shadow-xs"
      >
        <table class="w-full text-left text-[15px] text-gray-900 table-fixed">
          <thead
            class="border-b border-gray-200 bg-white text-gray-800 font-semibold text-[14px]"
          >
            <tr>
              <th class="py-4 px-6 w-[16%]">Branch</th>
              <th class="py-4 px-6 w-[26%]">Address</th>
              <th class="py-4 px-6 w-[15%]">Contact</th>
              <th class="py-4 px-6 w-[15%]">Opening hours</th>
              <th class="py-4 px-6 w-[8%]">Tables</th>
              <th class="py-4 px-6 w-[10%]">Status</th>
              <th class="py-4 px-6 w-[10%] text-right"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 text-[15px]">
            <tr
              v-for="branch in filteredBranches"
              :key="branch.id"
              class="hover:bg-gray-50/70 transition-colors"
            >
              <td class="py-5 px-6 text-gray-900 font-medium">
                {{ branch.name }}
              </td>
              <td
                class="py-5 px-6 text-gray-800 truncate"
                :title="branch.address"
              >
                {{ branch.address }}
              </td>
              <td class="py-5 px-6 text-gray-800">{{ branch.phone }}</td>
              <td class="py-5 px-6 text-gray-800">{{ branch.openingHours }}</td>
              <td class="py-5 px-6 text-gray-800 font-medium">
                {{ branch.tables }}
              </td>
              <td class="py-5 px-6">
                <span
                  :class="
                    branch.status === 'Active'
                      ? 'bg-[#edf7ee] text-[#1e6f40] border border-[#bce2c7]'
                      : 'bg-[#f0f2f5] text-[#4b5563] border border-[#d1d5db]'
                  "
                  class="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold"
                >
                  {{ branch.status }}
                </span>
              </td>
              <td
                class="py-5 px-6 text-right space-x-3 whitespace-nowrap text-sm"
              >
                <button
                  class="text-[#2e5d67] hover:text-[#1e3f46] hover:underline font-semibold cursor-pointer"
                  @click="viewBranch(branch.id)"
                >
                  View
                </button>
                <button
                  class="text-[#2e5d67] hover:text-[#1e3f46] hover:underline font-semibold cursor-pointer"
                  @click="editBranch(branch.id)"
                >
                  Edit
                </button>
                <button
                  class="text-[#c22838] hover:text-[#991b28] hover:underline font-semibold cursor-pointer"
                  @click="confirmDelete(branch)"
                >
                  Delete
                </button>
              </td>
            </tr>
            <tr v-if="filteredBranches.length === 0">
              <td
                class="py-14 text-center text-gray-500 font-medium"
                colspan="7"
              >
                No branches found matching your criteria.
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination Controls -->
      <div
        class="flex justify-end items-center gap-3 mt-8 pr-2 text-[15px] text-gray-700"
      >
        <button
          class="w-9 h-9 flex items-center justify-center rounded-full bg-[#f1f5f7] hover:bg-gray-200 text-gray-600 transition cursor-pointer"
          aria-label="Previous page"
        >
          <svg
            class="w-4 h-4"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M10 19l-7-7m0 0l7-7m-7 7h18"
            />
          </svg>
        </button>

        <div class="flex items-center gap-4 px-2">
          <button
            class="font-bold text-gray-900 border-b-2 border-gray-900 pb-0.5 px-0.5 cursor-pointer"
          >
            1
          </button>
          <button
            class="text-gray-700 hover:text-gray-900 font-medium transition px-0.5 cursor-pointer"
          >
            2
          </button>
          <button
            class="text-gray-700 hover:text-gray-900 font-medium transition px-0.5 cursor-pointer"
          >
            3
          </button>
          <span class="text-gray-400 px-0.5">...</span>
          <button
            class="text-gray-700 hover:text-gray-900 font-medium transition px-0.5 cursor-pointer"
          >
            9
          </button>
        </div>

        <button
          class="w-9 h-9 flex items-center justify-center rounded-full bg-[#f1f5f7] hover:bg-gray-200 text-gray-600 transition cursor-pointer"
          aria-label="Next page"
        >
          <svg
            class="w-4 h-4"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M14 5l7 7m0 0l-7 7m7-7H3"
            />
          </svg>
        </button>
      </div>
    </main>

    <!-- Delete Confirmation Modal -->
    <div
      v-if="branchToDelete"
      class="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50"
    >
      <div
        class="bg-white rounded-xl max-w-sm w-full p-6 shadow-2xl text-center"
      >
        <h3 class="text-lg font-bold text-gray-900 mb-2">Delete Branch</h3>
        <p class="text-sm text-gray-700 mb-6">
          Are you sure you want to delete
          <span class="font-bold text-gray-900">{{ branchToDelete.name }}</span
          >? This action cannot be undone.
        </p>
        <div class="flex gap-3 justify-center">
          <button
            class="px-5 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 transition cursor-pointer"
            @click="branchToDelete = null"
          >
            Cancel
          </button>
          <button
            class="px-5 py-2 bg-[#c22838] text-white rounded-lg text-sm font-medium hover:bg-[#991b28] transition cursor-pointer"
            @click="executeDelete"
          >
            Confirm Delete
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';

interface BranchItem {
  id: string;
  name: string;
  code: string;
  address: string;
  phone: string;
  openingHours: string;
  tables: number;
  status: 'Active' | 'Inactive';
}

const router = useRouter();
const searchQuery = ref('');
const selectedStatus = ref('All');
const branchToDelete = ref<BranchItem | null>(null);

const branches = ref<BranchItem[]>([
  {
    id: '1',
    name: 'Sukhumvit',
    code: 'SKV',
    address: '88 Sukhumvit Rd, Watthana',
    phone: '02-111-2233',
    openingHours: '10:00 – 22:00',
    tables: 20,
    status: 'Active',
  },
  {
    id: '2',
    name: 'Silom',
    code: 'SLM',
    address: '15 Silom Rd, Bang Rak',
    phone: '02-222-3344',
    openingHours: '11:00 – 23:00',
    tables: 16,
    status: 'Active',
  },
  {
    id: '3',
    name: 'Thonglor',
    code: 'TGL',
    address: '9 Thonglor Soi 10, Watthana',
    phone: '02-333-4455',
    openingHours: '12:00 – 24:00',
    tables: 12,
    status: 'Inactive',
  },
]);

const filteredBranches = computed(() => {
  return branches.value.filter((branch) => {
    const matchesSearch =
      branch.name.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
      branch.code.toLowerCase().includes(searchQuery.value.toLowerCase());
    const matchesStatus =
      selectedStatus.value === 'All' || branch.status === selectedStatus.value;
    return matchesSearch && matchesStatus;
  });
});

const goToAddBranch = () => router.push('/branches/new');
const viewBranch = (id: string) => router.push(`/branches/${id}`);
const editBranch = (id: string) => router.push(`/branches/${id}/edit`);

const confirmDelete = (branch: BranchItem) => {
  branchToDelete.value = branch;
};

const executeDelete = () => {
  if (branchToDelete.value) {
    branches.value = branches.value.filter(
      (b) => b.id !== branchToDelete.value?.id,
    );
    branchToDelete.value = null;
  }
};
</script>

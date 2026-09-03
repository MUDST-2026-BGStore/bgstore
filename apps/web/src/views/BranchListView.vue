<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useBranches, type Branch } from '../composables/useBranches';

const router = useRouter();
const { branches, deleteBranch } = useBranches();

const searchQuery = ref('');
const statusFilter = ref('All statuses');

// State สำหรับ Delete Confirmation Modal
const showDeleteModal = ref(false);
const branchToDelete = ref<Branch | null>(null);

// กรองข้อมูลตาม Search Query และ Status Filter
const filteredBranches = computed(() => {
  return branches.value.filter((branch) => {
    const query = searchQuery.value.trim().toLowerCase();
    const matchesSearch =
      query === '' ||
      branch.name.toLowerCase().includes(query) ||
      branch.code.toLowerCase().includes(query);

    const matchesStatus =
      statusFilter.value === 'All statuses' ||
      branch.status === statusFilter.value;

    return matchesSearch && matchesStatus;
  });
});

const handleAddBranch = () => {
  void router.push('/branches/new');
};

const handleView = (id: string) => {
  void router.push(`/branches/${id}`);
};

const handleEdit = (id: string) => {
  void router.push(`/branches/${id}/edit`);
};

// เปิดป๊อปอัปยืนยันการลบ
const openDeleteModal = (branch: Branch) => {
  branchToDelete.value = branch;
  showDeleteModal.value = true;
};

// กดยืนยันการลบ
const confirmDelete = () => {
  if (branchToDelete.value) {
    deleteBranch(branchToDelete.value.id);
    branchToDelete.value = null;
    showDeleteModal.value = false;
  }
};

// กดยกเลิกการลบ
const cancelDelete = () => {
  branchToDelete.value = null;
  showDeleteModal.value = false;
};
</script>

<template>
  <div class="w-full bg-white relative">
    <!-- Sub-header Bar (Branches + Add branch) -->
    <div
      class="w-full border-b border-gray-200 px-12 py-3 flex justify-between items-center bg-white"
    >
      <span class="text-sm font-bold text-gray-900">Branches</span>

      <button
        type="button"
        class="flex items-center gap-1.5 px-4 py-2 text-xs font-semibold text-white bg-[#386671] hover:bg-[#2c535c] rounded-lg transition shadow-2xs cursor-pointer"
        @click="handleAddBranch"
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
            stroke-width="2"
            d="M12 4v16m8-8H4"
          />
        </svg>
        <span>Add branch</span>
      </button>
    </div>

    <!-- Main Content Area -->
    <div class="w-full px-12 pt-6 pb-6">
      <!-- Page Title -->
      <h1
        class="font-bold text-gray-900 tracking-tight"
        style="
          font-size: 26px !important;
          line-height: 32px !important;
          margin-top: 0 !important;
          margin-bottom: 24px !important;
        "
      >
        Branch management
      </h1>

      <!-- Search & Filters -->
      <div class="flex items-center gap-3 mb-6">
        <!-- Search Input -->
        <div class="relative w-72">
          <span
            class="absolute inset-y-0 left-0 flex items-center pl-3 text-gray-400 pointer-events-none"
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
                d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
              />
            </svg>
          </span>
          <input
            v-model="searchQuery"
            type="text"
            placeholder="Search branch name or code"
            class="w-full h-10 bg-white border border-gray-200 text-gray-900 placeholder:text-gray-400 text-xs rounded-lg pl-9 pr-3 focus:outline-hidden focus:ring-1 focus:ring-[#386671]"
          />
        </div>

        <!-- Status Filter Dropdown -->
        <div class="relative w-36">
          <select
            v-model="statusFilter"
            class="w-full h-10 appearance-none bg-white border border-gray-200 text-gray-800 text-xs rounded-lg px-3 pr-8 focus:outline-hidden focus:ring-1 focus:ring-[#386671] cursor-pointer"
          >
            <option value="All statuses">All statuses</option>
            <option value="Active">Active</option>
            <option value="Inactive">Inactive</option>
          </select>
          <span
            class="absolute inset-y-0 right-0 flex items-center pr-2.5 pointer-events-none text-gray-400"
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
                stroke-width="2"
                d="M19 9l-7 7-7-7"
              />
            </svg>
          </span>
        </div>
      </div>

      <!-- Branches Table Container -->
      <div
        class="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-2xs mb-5"
      >
        <table class="w-full text-left border-collapse">
          <thead>
            <tr
              class="border-b border-gray-200 bg-gray-50/60 text-[11px] font-semibold text-gray-600"
            >
              <th class="px-6 py-3.5">Branch</th>
              <th class="px-6 py-3.5">Address</th>
              <th class="px-6 py-3.5">Contact</th>
              <th class="px-6 py-3.5">Opening hours</th>
              <th class="px-6 py-3.5">Tables</th>
              <th class="px-6 py-3.5">Status</th>
              <th class="px-6 py-3.5 text-right"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 text-xs">
            <!-- แสดงรายการเมื่อมีข้อมูล -->
            <tr
              v-for="branch in filteredBranches"
              :key="branch.id"
              class="hover:bg-gray-50/70 transition"
            >
              <td class="px-6 py-3.5 font-semibold text-gray-900">
                {{ branch.name }}
              </td>
              <td class="px-6 py-3.5 text-gray-600">
                {{ branch.addressLine }}, {{ branch.district }}
              </td>
              <td class="px-6 py-3.5 text-gray-600">
                {{ branch.phone }}
              </td>
              <td class="px-6 py-3.5 text-gray-600">
                {{ branch.openingHours.monFri.open }} –
                {{ branch.openingHours.monFri.close }}
              </td>
              <td class="px-6 py-3.5 font-semibold text-gray-900">
                {{ branch.tables }}
              </td>
              <td class="px-6 py-3.5">
                <span
                  class="inline-flex items-center px-2.5 py-0.5 rounded-full text-[11px] font-semibold border"
                  :class="
                    branch.status === 'Active'
                      ? 'bg-[#edf7ee] text-[#1e6f40] border-[#bce2c7]'
                      : 'bg-gray-100 text-gray-600 border-gray-200'
                  "
                >
                  {{ branch.status }}
                </span>
              </td>
              <td class="px-6 py-3.5 text-right space-x-3">
                <button
                  type="button"
                  class="text-[#386671] hover:underline font-medium cursor-pointer"
                  @click="handleView(branch.id)"
                >
                  View
                </button>
                <button
                  type="button"
                  class="text-[#386671] hover:underline font-medium cursor-pointer"
                  @click="handleEdit(branch.id)"
                >
                  Edit
                </button>
                <button
                  type="button"
                  class="text-red-600 hover:underline font-medium cursor-pointer"
                  @click="openDeleteModal(branch)"
                >
                  Delete
                </button>
              </td>
            </tr>

            <!-- Empty State (กรณีค้นหาไม่พบข้อมูล) -->
            <tr v-if="filteredBranches.length === 0">
              <td colspan="7" class="px-6 py-12 text-center text-gray-500">
                <div class="flex flex-col items-center justify-center gap-2">
                  <svg
                    class="w-8 h-8 text-gray-400"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="1.5"
                      d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                    />
                  </svg>
                  <p class="text-sm font-semibold text-gray-700">
                    No branches found
                  </p>
                  <p class="text-xs text-gray-400">
                    Try adjusting your search query or filter criteria.
                  </p>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div
        v-if="filteredBranches.length > 0"
        class="flex items-center justify-end gap-2 text-xs text-gray-600"
      >
        <button
          type="button"
          class="w-7 h-7 flex items-center justify-center rounded-lg hover:bg-gray-100 transition cursor-pointer"
          aria-label="Previous page"
        >
          ‹
        </button>
        <button
          type="button"
          class="w-7 h-7 flex items-center justify-center font-bold text-gray-900 border-b-2 border-gray-900"
        >
          1
        </button>
        <button
          type="button"
          class="w-7 h-7 flex items-center justify-center hover:bg-gray-100 rounded-lg transition cursor-pointer"
        >
          2
        </button>
        <button
          type="button"
          class="w-7 h-7 flex items-center justify-center hover:bg-gray-100 rounded-lg transition cursor-pointer"
        >
          3
        </button>
        <span class="px-1 text-gray-400">...</span>
        <button
          type="button"
          class="w-7 h-7 flex items-center justify-center hover:bg-gray-100 rounded-lg transition cursor-pointer"
        >
          9
        </button>
        <button
          type="button"
          class="w-7 h-7 flex items-center justify-center rounded-lg hover:bg-gray-100 transition cursor-pointer"
          aria-label="Next page"
        >
          ›
        </button>
      </div>
    </div>

    <!-- Delete Confirmation Modal (AC 8) -->
    <div
      v-if="showDeleteModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-2xs px-4"
    >
      <div
        class="bg-white rounded-2xl p-6 max-w-sm w-full shadow-xl border border-gray-100 flex flex-col gap-4"
      >
        <div class="flex items-center gap-3">
          <div
            class="w-10 h-10 rounded-full bg-red-50 text-red-600 flex items-center justify-center shrink-0"
          >
            <svg
              class="w-5 h-5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
              />
            </svg>
          </div>
          <div>
            <h3 class="text-sm font-bold text-gray-900">Delete branch</h3>
            <p class="text-xs text-gray-500">
              Are you sure you want to delete
              <span class="font-bold text-gray-800">{{
                branchToDelete?.name
              }}</span
              >?
            </p>
          </div>
        </div>

        <p
          class="text-xs text-gray-500 bg-gray-50 p-2.5 rounded-lg border border-gray-100"
        >
          This action cannot be undone. All associated branch details will be
          permanently removed.
        </p>

        <div class="flex items-center justify-end gap-2.5 pt-1">
          <button
            type="button"
            class="px-4 py-2 text-xs font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg transition cursor-pointer"
            @click="cancelDelete"
          >
            Cancel
          </button>
          <button
            type="button"
            class="px-4 py-2 text-xs font-medium text-white bg-red-600 hover:bg-red-700 rounded-lg transition cursor-pointer"
            @click="confirmDelete"
          >
            Delete
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

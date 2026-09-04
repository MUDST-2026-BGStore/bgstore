<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import { useBranches } from '../composables/useBranches';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const { getBranchById } = useBranches();

const branchId = computed(() => (route?.params?.id as string) || '');

// ค้นหาข้อมูลสาขาตาม ID (ไม่มี fallback ไปสาขาอื่น เพื่อรองรับ Not Found State)
const branch = computed(() => {
  if (!branchId.value) return undefined;
  return getBranchById(branchId.value);
});

const openingHoursList = computed(() => {
  if (!branch.value) return [];
  return [
    {
      days: t('branch.hours.monFri'),
      time: `${branch.value.openingHours.monFri.open} – ${branch.value.openingHours.monFri.close}`,
    },
    {
      days: t('branch.hours.saturday'),
      time: `${branch.value.openingHours.saturday.open} – ${branch.value.openingHours.saturday.close}`,
    },
    {
      days: t('branch.hours.sunday'),
      time: `${branch.value.openingHours.sunday.open} – ${branch.value.openingHours.sunday.close}`,
    },
  ];
});

const handleCancel = () => {
  void router.push('/branches');
};

const handleEdit = () => {
  if (branch.value) {
    void router.push(`/branches/${branch.value.id}/edit`);
  }
};
</script>

<template>
  <!-- กรณีพบข้อมูลสาขา -->
  <div v-if="branch" class="w-full bg-white">
    <!-- Breadcrumb Bar -->
    <div
      class="w-full border-b border-gray-200 px-12 py-3 flex items-center bg-white"
    >
      <nav
        class="flex items-center gap-2 text-sm font-bold text-gray-900"
        aria-label="Breadcrumb"
      >
        <a
          href="/branches"
          class="text-gray-900 hover:underline cursor-pointer"
          @click.prevent="handleCancel"
        >
          {{ t('branch.list') }}
        </a>
        <span class="text-gray-400 font-normal">›</span>
        <span class="text-gray-900">{{ branch.name }}</span>
      </nav>
    </div>

    <!-- Main Content Body -->
    <div class="w-full px-12 pt-6 pb-8">
      <!-- Title Header -->
      <div class="mb-5">
        <div class="flex items-center gap-2.5">
          <h1
            class="font-bold text-gray-900 tracking-tight"
            style="
              font-size: 26px !important;
              line-height: 32px !important;
              margin: 0 !important;
            "
          >
            {{ branch.name }}
          </h1>
          <span
            class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border"
            :class="
              branch.status === 'Active'
                ? 'bg-[#edf7ee] text-[#1e6f40] border-[#bce2c7]'
                : 'bg-gray-100 text-gray-600 border-gray-200'
            "
          >
            {{ branch.status }}
          </span>
        </div>
        <p class="text-xs text-gray-500 mt-1 font-normal">
          {{ branch.code }} · {{ branch.addressLine }}, {{ branch.district }},
          {{ branch.city }} {{ branch.postcode }}
        </p>
      </div>

      <!-- Stat Cards Grid -->
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4 mb-5">
        <!-- Total tables -->
        <div class="bg-white border border-gray-200 rounded-xl p-4 shadow-2xs">
          <div
            class="flex items-center gap-2 text-xs font-medium text-gray-600 mb-2"
          >
            <svg
              class="w-4 h-4 text-gray-500"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"
              />
            </svg>
            <span>{{ t('branch.stats.total') }}</span>
          </div>
          <div class="text-3xl font-bold text-gray-900">
            {{ branch.stats.total }}
          </div>
        </div>

        <!-- Available -->
        <div
          class="bg-[#edf7ee] border border-[#cfe6d5] rounded-xl p-4 shadow-2xs"
        >
          <div
            class="flex items-center gap-2 text-xs font-semibold text-[#1e7e46] mb-2"
          >
            <svg
              class="w-4 h-4 text-[#1e7e46]"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
            <span>{{ t('branch.stats.available') }}</span>
          </div>
          <div class="text-3xl font-bold text-gray-900">
            {{ branch.stats.available }}
          </div>
        </div>

        <!-- Reserved -->
        <div
          class="bg-[#eff6ff] border border-[#dbeafe] rounded-xl p-4 shadow-2xs"
        >
          <div
            class="flex items-center gap-2 text-xs font-semibold text-[#1d4ed8] mb-2"
          >
            <svg
              class="w-4 h-4 text-[#1d4ed8]"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z"
              />
            </svg>
            <span>{{ t('branch.stats.reserved') }}</span>
          </div>
          <div class="text-3xl font-bold text-gray-900">
            {{ branch.stats.reserved }}
          </div>
        </div>

        <!-- Occupied -->
        <div
          class="bg-[#fffbeb] border border-[#ecd9b9] rounded-xl p-4 shadow-2xs"
        >
          <div
            class="flex items-center gap-2 text-xs font-semibold text-[#a3620a] mb-2"
          >
            <svg
              class="w-4 h-4 text-[#a3620a]"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
            <span>{{ t('branch.stats.occupied') }}</span>
          </div>
          <div class="text-3xl font-bold text-gray-900">
            {{ branch.stats.occupied }}
          </div>
        </div>
      </div>

      <!-- Information & Hours Grid -->
      <div class="grid grid-cols-1 lg:grid-cols-4 gap-4 mb-6 items-start">
        <!-- Left: Branch Information -->
        <div
          class="lg:col-span-3 bg-white border border-gray-200 rounded-xl p-5 shadow-2xs"
        >
          <h2 class="text-sm font-bold text-gray-900 mb-3.5">
            {{ t('branch.info.title') }}
          </h2>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-5 gap-y-3">
            <div>
              <label class="block text-xs font-medium text-gray-700 mb-1">
                {{ t('branch.info.code') }}
              </label>
              <input
                type="text"
                readonly
                :value="branch.code"
                class="w-full bg-[#f9fafb] border border-gray-200 text-gray-800 text-xs rounded-lg px-3 py-2 focus:outline-hidden cursor-default"
              />
            </div>

            <div>
              <label class="block text-xs font-medium text-gray-700 mb-1">
                {{ t('branch.table.status') }}
              </label>
              <input
                type="text"
                readonly
                :value="branch.status"
                class="w-full bg-[#f9fafb] border border-gray-200 text-gray-800 text-xs rounded-lg px-3 py-2 focus:outline-hidden cursor-default"
              />
            </div>

            <div>
              <label class="block text-xs font-medium text-gray-700 mb-1">
                {{ t('branch.info.phone') }}
              </label>
              <input
                type="text"
                readonly
                :value="branch.phone"
                class="w-full bg-[#f9fafb] border border-gray-200 text-gray-800 text-xs rounded-lg px-3 py-2 focus:outline-hidden cursor-default"
              />
            </div>

            <div>
              <label class="block text-xs font-medium text-gray-700 mb-1">
                {{ t('branch.info.email') }}
              </label>
              <input
                type="text"
                readonly
                :value="branch.email || '-'"
                class="w-full bg-[#f9fafb] border border-gray-200 text-gray-800 text-xs rounded-lg px-3 py-2 focus:outline-hidden cursor-default"
              />
            </div>

            <div>
              <label class="block text-xs font-medium text-gray-700 mb-1">
                {{ t('branch.info.address') }}
              </label>
              <input
                type="text"
                readonly
                :value="`${branch.addressLine}, ${branch.district}`"
                class="w-full bg-[#f9fafb] border border-gray-200 text-gray-800 text-xs rounded-lg px-3 py-2 focus:outline-hidden cursor-default"
              />
            </div>

            <div>
              <label class="block text-xs font-medium text-gray-700 mb-1">
                {{ t('branch.info.created') }}
              </label>
              <input
                type="text"
                readonly
                :value="branch.createdAt"
                class="w-full bg-[#f9fafb] border border-gray-200 text-gray-800 text-xs rounded-lg px-3 py-2 focus:outline-hidden cursor-default"
              />
            </div>
          </div>
        </div>

        <!-- Right: Opening Hours -->
        <div
          class="lg:col-span-1 bg-white border border-gray-200 rounded-xl p-5 shadow-2xs self-start"
        >
          <h2 class="text-sm font-bold text-gray-900 mb-3.5">
            {{ t('branch.hours.title') }}
          </h2>
          <div class="space-y-3 text-xs">
            <div
              v-for="item in openingHoursList"
              :key="item.days"
              class="flex items-center justify-between text-gray-600"
            >
              <span>{{ item.days }}</span>
              <span class="font-semibold text-gray-900">{{ item.time }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Action Buttons -->
      <div class="flex items-center justify-end gap-3">
        <button
          type="button"
          class="px-5 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg transition shadow-2xs cursor-pointer"
          @click="handleCancel"
        >
          {{ t('branch.cancel') }}
        </button>
        <button
          type="button"
          class="px-5 py-2 text-sm font-medium text-white bg-[#386671] hover:bg-[#2c535c] rounded-lg transition shadow-2xs cursor-pointer"
          @click="handleEdit"
        >
          {{ t('branch.edit') }}
        </button>
      </div>
    </div>
  </div>

  <!-- Not-Found State -->
  <div
    v-else
    class="w-full min-h-[60vh] flex flex-col items-center justify-center px-12 py-16 bg-white text-center"
  >
    <div
      class="w-12 h-12 rounded-full bg-gray-100 flex items-center justify-center text-gray-400 mb-4"
    >
      <svg
        class="w-6 h-6"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="2"
          d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
        />
      </svg>
    </div>
    <h2 class="text-lg font-bold text-gray-900 mb-1">
      {{ t('branch.notFoundTitle') }}
    </h2>
    <p class="text-xs text-gray-500 mb-6 max-w-sm">
      {{ t('branch.notFoundDesc') }}
    </p>
    <a
      href="/branches"
      class="px-4 py-2 text-xs font-medium text-white bg-[#386671] hover:bg-[#2c535c] rounded-lg transition cursor-pointer"
      @click.prevent="handleCancel"
    >
      {{ t('branch.backToBranches') }}
    </a>
  </div>
</template>

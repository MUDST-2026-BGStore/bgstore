<template>
  <div class="min-h-screen bg-gray-50 p-8">
    <div class="max-w-6xl mx-auto">
      <!-- Breadcrumb & Header -->
      <div class="mb-6">
        <div class="text-sm text-gray-500 mb-2">
          <RouterLink to="/branches" class="hover:underline"
            >Branches</RouterLink
          >
          <span class="mx-2">›</span>
          <span class="text-gray-900 font-medium">{{ branch.name }}</span>
        </div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900">{{ branch.name }}</h1>
          <span
            :class="
              branch.status === 'Active'
                ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                : 'bg-gray-100 text-gray-600 border-gray-200'
            "
            class="px-2.5 py-0.5 rounded-full text-xs font-medium border"
          >
            {{ branch.status }}
          </span>
        </div>
        <p class="text-xs text-gray-500 mt-1">
          {{ branch.code }} · {{ branch.address }}
        </p>
      </div>

      <!-- Table Status Counters -->
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
        <div class="bg-white p-5 rounded-xl border border-gray-200 shadow-sm">
          <span
            class="text-xs font-semibold text-gray-500 uppercase tracking-wider block mb-2"
            >Total tables</span
          >
          <p class="text-3xl font-bold text-gray-900">
            {{ branch.tables.total }}
          </p>
        </div>
        <div
          class="bg-emerald-50/60 p-5 rounded-xl border border-emerald-100 shadow-sm"
        >
          <span
            class="text-xs font-semibold text-emerald-700 uppercase tracking-wider block mb-2"
            >Available</span
          >
          <p class="text-3xl font-bold text-emerald-700">
            {{ branch.tables.available }}
          </p>
        </div>
        <div
          class="bg-blue-50/60 p-5 rounded-xl border border-blue-100 shadow-sm"
        >
          <span
            class="text-xs font-semibold text-blue-700 uppercase tracking-wider block mb-2"
            >Reserved</span
          >
          <p class="text-3xl font-bold text-blue-700">
            {{ branch.tables.reserved }}
          </p>
        </div>
        <div
          class="bg-amber-50/60 p-5 rounded-xl border border-amber-100 shadow-sm"
        >
          <span
            class="text-xs font-semibold text-amber-700 uppercase tracking-wider block mb-2"
            >Occupied</span
          >
          <p class="text-3xl font-bold text-amber-700">
            {{ branch.tables.occupied }}
          </p>
        </div>
      </div>

      <!-- Detail Info Panels -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        <!-- Branch Information -->
        <div
          class="md:col-span-2 bg-white p-6 rounded-xl border border-gray-200 shadow-sm space-y-4"
        >
          <h2 class="text-base font-bold text-gray-900 mb-4">
            Branch information
          </h2>
          <div class="grid grid-cols-2 gap-4 text-sm">
            <div>
              <span class="text-gray-400 block text-xs mb-1">Branch code</span>
              <p class="font-medium text-gray-800">{{ branch.code }}</p>
            </div>
            <div>
              <span class="text-gray-400 block text-xs mb-1">Status</span>
              <p class="font-medium text-gray-800">{{ branch.status }}</p>
            </div>
            <div>
              <span class="text-gray-400 block text-xs mb-1">Phone</span>
              <p class="font-medium text-gray-800">{{ branch.phone }}</p>
            </div>
            <div>
              <span class="text-gray-400 block text-xs mb-1">Email</span>
              <p class="font-medium text-gray-800">{{ branch.email || '-' }}</p>
            </div>
            <div class="col-span-2">
              <span class="text-gray-400 block text-xs mb-1">Address</span>
              <p class="font-medium text-gray-800">{{ branch.address }}</p>
            </div>
          </div>
        </div>

        <!-- Opening Hours -->
        <div class="bg-white p-6 rounded-xl border border-gray-200 shadow-sm">
          <h2 class="text-base font-bold text-gray-900 mb-4">Opening hours</h2>
          <div class="space-y-3 text-sm">
            <div class="flex justify-between py-1 border-b border-gray-100">
              <span class="text-gray-500">Mon – Fri</span>
              <span class="font-medium text-gray-800">{{
                branch.openingHours.monFri
              }}</span>
            </div>
            <div class="flex justify-between py-1 border-b border-gray-100">
              <span class="text-gray-500">Saturday</span>
              <span class="font-medium text-gray-800">{{
                branch.openingHours.saturday
              }}</span>
            </div>
            <div class="flex justify-between py-1">
              <span class="text-gray-500">Sunday</span>
              <span class="font-medium text-gray-800">{{
                branch.openingHours.sunday
              }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Action Buttons -->
      <div class="flex justify-end gap-3 mt-8">
        <button
          class="px-5 py-2 border border-gray-300 rounded-lg text-sm font-medium hover:bg-gray-100"
          @click="goBack"
        >
          Back
        </button>
        <button
          class="bg-[#316974] hover:bg-[#28555e] text-white px-5 py-2 rounded-lg text-sm font-medium transition shadow-sm"
          @click="goToEdit"
        >
          Edit branch
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';

const router = useRouter();
const route = useRoute();

const branch = ref({
  id: (route.params.id as string) || '1',
  name: 'Sukhumvit',
  code: 'SKV',
  status: 'Active',
  address: '88 Sukhumvit Rd, Watthana, Bangkok 10110',
  phone: '02-111-2233',
  email: 'sukhumvit@reservation.co.th',
  tables: { total: 20, available: 12, reserved: 5, occupied: 3 },
  openingHours: {
    monFri: '10:00 – 22:00',
    saturday: '11:00 – 23:00',
    sunday: '11:00 – 22:00',
  },
});

const goBack = () => router.push('/branches');
const goToEdit = () => router.push(`/branches/${branch.value.id}/edit`);
</script>

<template>
  <div class="min-h-screen bg-gray-50 p-8">
    <div class="max-w-4xl mx-auto">
      <!-- Breadcrumb & Title -->
      <div class="flex justify-between items-center mb-6">
        <div>
          <div class="text-sm text-gray-500 mb-1">
            <RouterLink to="/branches" class="hover:underline"
              >Branches</RouterLink
            >
            <span class="mx-2">›</span>
            <span>{{ isEdit ? 'Edit branch' : 'Add branch' }}</span>
          </div>
          <h1 class="text-2xl font-bold text-gray-900">
            {{ isEdit ? `Edit branch — ${form.name}` : 'Add branch' }}
          </h1>
        </div>
        <div class="flex gap-3">
          <button
            class="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium hover:bg-gray-100"
            @click="cancel"
          >
            Cancel
          </button>
          <button
            class="bg-[#316974] hover:bg-[#28555e] text-white px-5 py-2 rounded-lg text-sm font-medium transition shadow-sm"
            @click="handleSubmit"
          >
            {{ isEdit ? 'Save' : 'Create' }}
          </button>
        </div>
      </div>

      <!-- Form Body -->
      <div class="space-y-6">
        <!-- Section: Branch Details -->
        <div
          class="bg-white p-6 rounded-xl border border-gray-200 shadow-sm space-y-4"
        >
          <h2 class="text-sm font-bold text-gray-800 uppercase tracking-wide">
            Branch details
          </h2>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label class="block text-xs font-semibold text-gray-700 mb-1"
                >Branch name *</label
              >
              <input
                v-model="form.name"
                type="text"
                placeholder="e.g. Sukhumvit"
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-[#316974] outline-none"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold text-gray-700 mb-1"
                >Branch code *</label
              >
              <input
                v-model="form.code"
                type="text"
                placeholder="e.g. SKV"
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-[#316974] outline-none"
              />
            </div>
            <div class="md:col-span-2">
              <label class="block text-xs font-semibold text-gray-700 mb-1"
                >Status *</label
              >
              <select
                v-model="form.status"
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-[#316974] outline-none bg-white"
              >
                <option value="Active">Active</option>
                <option value="Inactive">Inactive</option>
              </select>
            </div>
          </div>
        </div>

        <!-- Section: Address -->
        <div
          class="bg-white p-6 rounded-xl border border-gray-200 shadow-sm space-y-4"
        >
          <h2 class="text-sm font-bold text-gray-800 uppercase tracking-wide">
            Address
          </h2>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label class="block text-xs font-semibold text-gray-700 mb-1"
                >Address line *</label
              >
              <input
                v-model="form.addressLine"
                type="text"
                placeholder="Street and number"
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-[#316974] outline-none"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold text-gray-700 mb-1"
                >City *</label
              >
              <input
                v-model="form.city"
                type="text"
                placeholder="e.g. Bangkok"
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-[#316974] outline-none"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold text-gray-700 mb-1"
                >Postcode</label
              >
              <input
                v-model="form.postcode"
                type="text"
                placeholder="e.g. 10110"
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-[#316974] outline-none"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold text-gray-700 mb-1"
                >District</label
              >
              <input
                v-model="form.district"
                type="text"
                placeholder="e.g. Watthana"
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-[#316974] outline-none"
              />
            </div>
          </div>
        </div>

        <!-- Section: Contact -->
        <div
          class="bg-white p-6 rounded-xl border border-gray-200 shadow-sm space-y-4"
        >
          <h2 class="text-sm font-bold text-gray-800 uppercase tracking-wide">
            Contact
          </h2>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label class="block text-xs font-semibold text-gray-700 mb-1"
                >Phone *</label
              >
              <input
                v-model="form.phone"
                type="text"
                placeholder="02-000-0000"
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-[#316974] outline-none"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold text-gray-700 mb-1"
                >Email</label
              >
              <input
                v-model="form.email"
                type="email"
                placeholder="branch@reservation.co.th"
                class="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-[#316974] outline-none"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';

const router = useRouter();
const route = useRoute();

const isEdit = computed(() => !!route.params.id);

const form = ref({
  name: isEdit.value ? 'Sukhumvit' : '',
  code: isEdit.value ? 'SKV' : '',
  status: 'Active',
  addressLine: isEdit.value ? '88 Sukhumvit Rd' : '',
  city: isEdit.value ? 'Bangkok' : '',
  postcode: isEdit.value ? '10110' : '',
  district: isEdit.value ? 'Watthana' : '',
  phone: isEdit.value ? '02-111-2233' : '',
  email: isEdit.value ? 'sukhumvit@reservation.co.th' : '',
});

const cancel = () => router.push('/branches');

const handleSubmit = () => {
  if (
    !form.value.name ||
    !form.value.code ||
    !form.value.addressLine ||
    !form.value.city ||
    !form.value.phone
  ) {
    alert('Please fill in all required fields (*)');
    return;
  }
  alert(
    isEdit.value
      ? 'Branch updated successfully!'
      : 'Branch created successfully!',
  );
  router.push('/branches');
};
</script>

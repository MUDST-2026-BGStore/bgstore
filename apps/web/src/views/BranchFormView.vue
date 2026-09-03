<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useBranches } from '../composables/useBranches';

const route = useRoute();
const router = useRouter();
const { getBranchById, addBranch, updateBranch } = useBranches();

const isEdit = computed(() => Boolean(route?.path?.includes('/edit')));
const branchId = computed(() => (route?.params?.id as string) || '');

const form = ref({
  name: '',
  code: '',
  status: 'Active' as 'Active' | 'Inactive',
  addressLine: '',
  city: '',
  postcode: '',
  district: '',
  phone: '',
  email: '',
  openingHours: {
    monFri: { open: '10:00', close: '22:00' },
    saturday: { open: '11:00', close: '23:00' },
    sunday: { open: '11:00', close: '22:00' },
  },
});

type HourKey = keyof typeof form.value.openingHours;

const hourRows: { key: HourKey; label: string }[] = [
  { key: 'monFri', label: 'Mon – Fri' },
  { key: 'saturday', label: 'Saturday' },
  { key: 'sunday', label: 'Sunday' },
];

const timeOptions = [
  '08:00',
  '09:00',
  '10:00',
  '11:00',
  '12:00',
  '13:00',
  '14:00',
  '15:00',
  '16:00',
  '17:00',
  '18:00',
  '19:00',
  '20:00',
  '21:00',
  '22:00',
  '23:00',
  '24:00',
];

// โหลดข้อมูลเดิมกรณีเป็นโหมด Edit
onMounted(() => {
  if (isEdit.value && branchId.value) {
    const existing = getBranchById(branchId.value);
    if (existing) {
      form.value = {
        name: existing.name,
        code: existing.code,
        status: existing.status,
        addressLine: existing.addressLine,
        city: existing.city,
        postcode: existing.postcode || '',
        district: existing.district || '',
        phone: existing.phone,
        email: existing.email || '',
        openingHours: {
          monFri: { ...existing.openingHours.monFri },
          saturday: { ...existing.openingHours.saturday },
          sunday: { ...existing.openingHours.sunday },
        },
      };
    } else {
      void router.push('/branches');
    }
  }
});

// Validation Rules (AC 5)
const isEmailValid = computed(() => {
  if (!form.value.email.trim()) return true; // Optional field
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email.trim());
});

const isPhoneValid = computed(() => {
  if (!form.value.phone.trim()) return false;
  // อนุญาตตัวเลข, ขีด, เว้นวรรค อย่างน้อย 9 ตัว
  const digitsOnly = form.value.phone.replace(/\D/g, '');
  return digitsOnly.length >= 9;
});

const isFormValid = computed(() => {
  return (
    form.value.name.trim() !== '' &&
    form.value.code.trim() !== '' &&
    form.value.status !== null &&
    form.value.addressLine.trim() !== '' &&
    form.value.city.trim() !== '' &&
    isPhoneValid.value &&
    isEmailValid.value
  );
});

const handleCancel = () => {
  void router.push('/branches');
};

const handleSubmit = () => {
  if (!isFormValid.value) return;

  if (isEdit.value && branchId.value) {
    updateBranch(branchId.value, {
      name: form.value.name.trim(),
      code: form.value.code.trim().toUpperCase(),
      status: form.value.status,
      addressLine: form.value.addressLine.trim(),
      city: form.value.city.trim(),
      postcode: form.value.postcode.trim(),
      district: form.value.district.trim(),
      phone: form.value.phone.trim(),
      email: form.value.email.trim(),
      openingHours: form.value.openingHours,
    });
  } else {
    addBranch({
      name: form.value.name.trim(),
      code: form.value.code.trim().toUpperCase(),
      status: form.value.status,
      addressLine: form.value.addressLine.trim(),
      city: form.value.city.trim(),
      postcode: form.value.postcode.trim(),
      district: form.value.district.trim(),
      phone: form.value.phone.trim(),
      email: form.value.email.trim(),
      openingHours: form.value.openingHours,
    });
  }

  void router.push('/branches');
};
</script>

<template>
  <div class="w-full bg-[#f8f9fa]">
    <div
      class="w-full border-b border-gray-200 px-12 py-2.5 flex items-center justify-between bg-white"
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
          Branches
        </a>
        <span class="text-gray-400 font-normal">›</span>
        <span class="text-gray-900">
          {{ isEdit ? 'Edit branch' : 'Add branch' }}
        </span>
      </nav>

      <div class="flex items-center gap-3">
        <button
          type="button"
          class="px-5 py-2 text-xs font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg transition cursor-pointer"
          @click="handleCancel"
        >
          Cancel
        </button>
        <button
          type="button"
          :disabled="!isFormValid"
          class="px-6 py-2 text-xs font-medium text-white rounded-lg transition shadow-2xs"
          :class="
            isFormValid
              ? 'bg-[#386671] hover:bg-[#2c535c] cursor-pointer'
              : 'bg-gray-400 cursor-not-allowed opacity-60'
          "
          @click="handleSubmit"
        >
          {{ isEdit ? 'Save' : 'Create' }}
        </button>
      </div>
    </div>

    <div class="w-full px-12 pt-5 pb-6">
      <h1
        class="font-bold text-gray-900 tracking-tight"
        style="
          font-size: 26px !important;
          line-height: 32px !important;
          margin-top: 0 !important;
          margin-bottom: 18px !important;
        "
      >
        {{ isEdit ? `Edit branch — ${form.name}` : 'Add branch' }}
      </h1>

      <form
        class="bg-white border border-gray-200 rounded-xl p-6 shadow-2xs"
        @submit.prevent="handleSubmit"
      >
        <div>
          <h2 class="text-sm font-bold text-gray-900 mb-3">Branch details</h2>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-3">
            <div>
              <label class="block text-xs font-semibold text-gray-700 mb-1">
                Branch name <span class="text-red-500">*</span>
              </label>
              <input
                v-model="form.name"
                type="text"
                placeholder="e.g. Sukhumvit"
                class="w-full h-9 bg-white border border-gray-200 text-gray-900 placeholder:text-gray-400 text-xs rounded-lg px-3 focus:outline-hidden focus:ring-1 focus:ring-[#386671]"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold text-gray-700 mb-1">
                Branch code <span class="text-red-500">*</span>
              </label>
              <input
                v-model="form.code"
                type="text"
                placeholder="e.g. SKV"
                class="w-full h-9 bg-white border border-gray-200 text-gray-900 placeholder:text-gray-400 text-xs rounded-lg px-3 focus:outline-hidden focus:ring-1 focus:ring-[#386671]"
              />
            </div>
          </div>
          <div>
            <label class="block text-xs font-semibold text-gray-700 mb-1">
              Status <span class="text-red-500">*</span>
            </label>
            <div class="relative">
              <select
                v-model="form.status"
                class="w-full h-9 appearance-none bg-white border border-gray-200 text-gray-900 text-xs rounded-lg px-3 pr-8 focus:outline-hidden focus:ring-1 focus:ring-[#386671] cursor-pointer"
              >
                <option value="Active">Active</option>
                <option value="Inactive">Inactive</option>
              </select>
              <span
                class="absolute inset-y-0 right-0 flex items-center pr-3 pointer-events-none text-gray-400"
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
        </div>

        <hr class="border-gray-100 my-4" />

        <div>
          <h2 class="text-sm font-bold text-gray-900 mb-3">Address</h2>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label class="block text-xs font-semibold text-gray-700 mb-1">
                Address line <span class="text-red-500">*</span>
              </label>
              <input
                v-model="form.addressLine"
                type="text"
                placeholder="Street and number"
                class="w-full h-9 bg-white border border-gray-200 text-gray-900 placeholder:text-gray-400 text-xs rounded-lg px-3 focus:outline-hidden focus:ring-1 focus:ring-[#386671]"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold text-gray-700 mb-1">
                City <span class="text-red-500">*</span>
              </label>
              <input
                v-model="form.city"
                type="text"
                placeholder="e.g. Bangkok"
                class="w-full h-9 bg-white border border-gray-200 text-gray-900 placeholder:text-gray-400 text-xs rounded-lg px-3 focus:outline-hidden focus:ring-1 focus:ring-[#386671]"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold text-gray-700 mb-1">
                Postcode
              </label>
              <input
                v-model="form.postcode"
                type="text"
                placeholder="e.g. 10110"
                class="w-full h-9 bg-white border border-gray-200 text-gray-900 placeholder:text-gray-400 text-xs rounded-lg px-3 focus:outline-hidden focus:ring-1 focus:ring-[#386671]"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold text-gray-700 mb-1">
                District
              </label>
              <input
                v-model="form.district"
                type="text"
                placeholder="e.g. Watthana"
                class="w-full h-9 bg-white border border-gray-200 text-gray-900 placeholder:text-gray-400 text-xs rounded-lg px-3 focus:outline-hidden focus:ring-1 focus:ring-[#386671]"
              />
            </div>
          </div>
        </div>

        <hr class="border-gray-100 my-4" />

        <div>
          <h2 class="text-sm font-bold text-gray-900 mb-3">Contact</h2>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label class="block text-xs font-semibold text-gray-700 mb-1">
                Phone <span class="text-red-500">*</span>
              </label>
              <input
                v-model="form.phone"
                type="text"
                placeholder="02-000-0000"
                class="w-full h-9 bg-white border border-gray-200 text-gray-900 placeholder:text-gray-400 text-xs rounded-lg px-3 focus:outline-hidden focus:ring-1 focus:ring-[#386671]"
                :class="{ 'border-red-400': form.phone && !isPhoneValid }"
              />
              <span
                v-if="form.phone && !isPhoneValid"
                class="text-[11px] text-red-500 mt-0.5 block"
              >
                Please enter a valid phone number (at least 9 digits).
              </span>
            </div>
            <div>
              <label class="block text-xs font-semibold text-gray-700 mb-1">
                Email
              </label>
              <input
                v-model="form.email"
                type="email"
                placeholder="branch@reservation.co.th"
                class="w-full h-9 bg-white border border-gray-200 text-gray-900 placeholder:text-gray-400 text-xs rounded-lg px-3 focus:outline-hidden focus:ring-1 focus:ring-[#386671]"
                :class="{ 'border-red-400': form.email && !isEmailValid }"
              />
              <span
                v-if="form.email && !isEmailValid"
                class="text-[11px] text-red-500 mt-0.5 block"
              >
                Please enter a valid email address.
              </span>
            </div>
          </div>
        </div>

        <hr class="border-gray-100 my-4" />

        <div>
          <h2 class="text-sm font-bold text-gray-900 mb-3">Opening hours</h2>
          <div class="space-y-2.5">
            <div
              v-for="row in hourRows"
              :key="row.key"
              class="flex items-center gap-6"
            >
              <span class="w-20 shrink-0 text-xs font-semibold text-gray-700">
                {{ row.label }}
              </span>

              <div class="flex items-center gap-3">
                <div class="relative w-32">
                  <span
                    class="absolute inset-y-0 left-0 flex items-center pl-2.5 text-gray-400 pointer-events-none"
                  >
                    <svg
                      class="w-3.5 h-3.5"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <circle cx="12" cy="12" r="9" stroke-width="2" />
                      <path
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        stroke-width="2"
                        d="M12 6v6l4 2"
                      />
                    </svg>
                  </span>
                  <select
                    v-model="form.openingHours[row.key].open"
                    class="appearance-none w-full h-8 bg-white border border-gray-200 text-gray-900 text-xs rounded-lg pl-8 pr-7 focus:outline-hidden focus:ring-1 focus:ring-[#386671] cursor-pointer"
                  >
                    <option v-for="t in timeOptions" :key="t" :value="t">
                      {{ t }}
                    </option>
                  </select>
                  <span
                    class="absolute inset-y-0 right-0 flex items-center pr-2 text-gray-400 pointer-events-none"
                  >
                    <svg
                      class="w-3 h-3"
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

                <span class="text-gray-400 text-xs">–</span>

                <div class="relative w-32">
                  <span
                    class="absolute inset-y-0 left-0 flex items-center pl-2.5 text-gray-400 pointer-events-none"
                  >
                    <svg
                      class="w-3.5 h-3.5"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <circle cx="12" cy="12" r="9" stroke-width="2" />
                      <path
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        stroke-width="2"
                        d="M12 6v6l4 2"
                      />
                    </svg>
                  </span>
                  <select
                    v-model="form.openingHours[row.key].close"
                    class="appearance-none w-full h-8 bg-white border border-gray-200 text-gray-900 text-xs rounded-lg pl-8 pr-7 focus:outline-hidden focus:ring-1 focus:ring-[#386671] cursor-pointer"
                  >
                    <option v-for="t in timeOptions" :key="t" :value="t">
                      {{ t }}
                    </option>
                  </select>
                  <span
                    class="absolute inset-y-0 right-0 flex items-center pr-2 text-gray-400 pointer-events-none"
                  >
                    <svg
                      class="w-3 h-3"
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
            </div>
          </div>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import ParlorShell from './ParlorShell.vue';
import BLocaleSwitcher from './BLocaleSwitcher.vue';
import ParlorCard from './ParlorCard.vue';
import PhoneNumberInput from './PhoneInput.vue';

export interface OnboardingPayload {
  firstName: string;
  lastName: string;
  phoneNumber: string;
  preferences: { preferredLocale: 'th-TH' | 'en-US' };
}

const props = defineProps<{ locale: 'th-TH' | 'en-US' }>();
const { t } = useI18n({ useScope: 'global' });

const emit = defineEmits<{
  'update:locale': [locale: 'th-TH' | 'en-US'];
  submit: [payload: OnboardingPayload];
}>();

const form = reactive({
  firstName: '',
  lastName: '',
  phoneNumber: '',
});

const touched = reactive({
  firstName: false,
  lastName: false,
  phoneNumber: false,
});

const submitted = ref(false);

const isPhoneValid = ref(false);

function submitForm() {
  touched.firstName = true;
  touched.lastName = true;
  touched.phoneNumber = true;

  const firstName = form.firstName.trim();
  const lastName = form.lastName.trim();
  if (!firstName || !lastName || !isPhoneValid.value) return;

  emit('submit', {
    firstName,
    lastName,
    phoneNumber: form.phoneNumber,
    preferences: { preferredLocale: props.locale },
  });
}

function showSuccess() {
  submitted.value = true;
}
defineExpose({ showSuccess });
</script>

<template>
  <ParlorShell centered auth-layout>
    <template #header-left>
      <BLocaleSwitcher
        :locale="props.locale"
        :label="t('locale.label')"
        @update:locale="emit('update:locale', $event)"
      />
    </template>

    <template #header-right>
      <span class="onboarding-context">{{ t('onboarding.context') }}</span>
    </template>

    <ParlorCard :aria-label="t('onboarding.context')">
      <template v-if="!submitted">
        <div class="text-center">
          <h1
            class="font-editorial text-[40px] leading-[0.98] text-[#14241c] sm:text-[44px]"
          >
            {{ t('onboarding.title') }}
          </h1>
          <p class="mt-2 text-[13px] text-[#6a6252]">
            {{ t('onboarding.description') }}
          </p>
        </div>

        <form class="mt-8 space-y-6" novalidate @submit.prevent="submitForm">
          <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 sm:gap-5">
            <div>
              <label for="parlor-first" class="field-label">
                <span>{{ t('onboarding.firstName') }}</span>
                <span
                  v-if="touched.firstName && !form.firstName.trim()"
                  class="text-[#9e2f24]"
                  >{{ t('onboarding.required') }}</span
                >
              </label>
              <input
                id="parlor-first"
                v-model="form.firstName"
                class="field-input"
                :placeholder="t('onboarding.firstName')"
                autocomplete="given-name"
                maxlength="100"
                required
                @blur="touched.firstName = true"
              />
            </div>

            <div>
              <label for="parlor-last" class="field-label">
                <span>{{ t('onboarding.lastName') }}</span>
                <span
                  v-if="touched.lastName && !form.lastName.trim()"
                  class="text-[#9e2f24]"
                  >{{ t('onboarding.required') }}</span
                >
              </label>
              <input
                id="parlor-last"
                v-model="form.lastName"
                class="field-input"
                :placeholder="t('onboarding.lastName')"
                autocomplete="family-name"
                maxlength="100"
                required
                @blur="touched.lastName = true"
              />
            </div>
          </div>

          <div>
            <label for="parlor-phone" class="field-label">
              <span>{{ t('onboarding.phone') }}</span>
            </label>
            <PhoneNumberInput
              id="parlor-phone"
              v-model="form.phoneNumber"
              required
              @validity="isPhoneValid = $event"
              @blur="touched.phoneNumber = true"
            />
            <p
              v-if="touched.phoneNumber && !isPhoneValid"
              class="mt-2 text-xs text-[#9e2f24]"
              role="alert"
            >
              {{ t('phone.invalid') }}
            </p>
          </div>

          <button
            type="submit"
            class="group mt-3 flex h-12 w-full items-center justify-between bg-[#152c21] px-5 text-xs font-semibold uppercase tracking-[0.16em] text-[#f3ead8] transition-colors hover:bg-[#0e1f17] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#152c21]"
          >
            <span>{{ t('onboarding.continue') }}</span>
            <span
              class="text-sm transition-transform duration-200 group-hover:translate-x-1"
              aria-hidden="true"
            >
              &rarr;
            </span>
          </button>
        </form>
      </template>

      <div v-else class="py-12 text-center" aria-live="polite">
        <div
          class="mx-auto grid size-12 place-items-center rounded-full border border-[#152c21]/30 bg-[#e6dac3] font-editorial text-2xl italic text-[#152c21]"
        >
          P
        </div>
        <h2 class="mt-4 font-editorial text-4xl text-[#14241c]">
          {{ t('onboarding.welcome', { name: form.firstName }) }}
        </h2>
        <p class="mt-2 text-[13px] text-[#5c5546]">
          {{ t('onboarding.ready') }}
        </p>
      </div>
    </ParlorCard>
  </ParlorShell>
</template>

<style scoped>
.onboarding-context {
  font-size: 13px;
  line-height: 1.2;
  text-align: end;
  overflow-wrap: anywhere;
}

@media (max-width: 380px) {
  .onboarding-context {
    max-width: 6em;
    font-size: 12px;
  }
}

.font-editorial {
  font-family: 'Instrument Serif', Georgia, serif;
}

.field-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: #6a6252;
}

.field-input {
  display: block;
  width: 100%;
  height: 36px;
  padding: 0;
  border: 0;
  border-bottom: 1px solid #bfb093;
  border-radius: 0;
  background: transparent;
  font-family:
    'Plus Jakarta Sans',
    -apple-system,
    BlinkMacSystemFont,
    sans-serif;
  font-size: 15px;
  color: #14241c;
  outline: none;
  transition: border-color 160ms ease;
}

.field-input::placeholder {
  color: #b0a590;
}

.field-input:focus-visible {
  border-bottom-color: #152c21;
}
</style>

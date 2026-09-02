<script setup lang="ts">
import { useMutation, useQueryClient } from '@tanstack/vue-query';
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import { completeClientProfile } from '../generated/api/sdk.gen';

const queryClient = useQueryClient();
const route = useRoute();
const router = useRouter();
const { locale, t } = useI18n();
const countryCode = ref('+66');
const phoneNumber = ref('');
const countryMenuOpen = ref(false);

const countryCodes = [
  { code: '+66', flag: '🇹🇭', nameEn: 'Thailand', nameTh: 'ไทย' },
  {
    code: '+1',
    flag: '🇺🇸',
    nameEn: 'United States / Canada',
    nameTh: 'สหรัฐอเมริกา / แคนาดา',
  },
  {
    code: '+44',
    flag: '🇬🇧',
    nameEn: 'United Kingdom',
    nameTh: 'สหราชอาณาจักร',
  },
  { code: '+61', flag: '🇦🇺', nameEn: 'Australia', nameTh: 'ออสเตรเลีย' },
  { code: '+65', flag: '🇸🇬', nameEn: 'Singapore', nameTh: 'สิงคโปร์' },
  { code: '+60', flag: '🇲🇾', nameEn: 'Malaysia', nameTh: 'มาเลเซีย' },
  { code: '+81', flag: '🇯🇵', nameEn: 'Japan', nameTh: 'ญี่ปุ่น' },
  { code: '+82', flag: '🇰🇷', nameEn: 'South Korea', nameTh: 'เกาหลีใต้' },
  { code: '+86', flag: '🇨🇳', nameEn: 'China', nameTh: 'จีน' },
  { code: '+91', flag: '🇮🇳', nameEn: 'India', nameTh: 'อินเดีย' },
  { code: '+33', flag: '🇫🇷', nameEn: 'France', nameTh: 'ฝรั่งเศส' },
  { code: '+49', flag: '🇩🇪', nameEn: 'Germany', nameTh: 'เยอรมนี' },
] as const;

const selectedCountry = computed(
  () =>
    countryCodes.find((country) => country.code === countryCode.value) ??
    countryCodes[0],
);

const countryName = (country: (typeof countryCodes)[number]) =>
  locale.value === 'th' ? country.nameTh : country.nameEn;

function toggleCountryMenu() {
  countryMenuOpen.value = !countryMenuOpen.value;
}

function chooseCountry(code: string) {
  countryCode.value = code;
  countryMenuOpen.value = false;
}

function handleCountryKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    countryMenuOpen.value = false;
  } else if (
    event.key === 'ArrowDown' ||
    event.key === 'Enter' ||
    event.key === ' '
  ) {
    event.preventDefault();
    countryMenuOpen.value = true;
  }
}

const returnTo = computed(() => {
  const value = route.query.returnTo;
  return typeof value === 'string' &&
    value.startsWith('/') &&
    !value.startsWith('//')
    ? value
    : '/';
});

const completeProfile = useMutation({
  mutationFn: async () => {
    const { data } = await completeClientProfile({
      body: { countryCode: countryCode.value, phoneNumber: phoneNumber.value },
      throwOnError: true,
    });
    return data;
  },
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['current-user'] });
    await queryClient.refetchQueries({ queryKey: ['current-user'] });
    await router.replace(returnTo.value);
  },
});
</script>

<template>
  <section class="onboarding-layout" aria-labelledby="onboarding-title">
    <div class="onboarding-card">
      <p class="eyebrow">{{ t('onboarding.eyebrow') }}</p>
      <h1 id="onboarding-title">{{ t('onboarding.title') }}</h1>
      <p>{{ t('onboarding.description') }}</p>

      <form class="onboarding-form" @submit.prevent="completeProfile.mutate()">
        <span id="country-code-label" class="phone-label">{{
          t('onboarding.phoneNumber')
        }}</span>
        <div class="phone-control" @keydown.esc="countryMenuOpen = false">
          <div class="phone-country-picker">
            <button
              class="phone-country-trigger"
              type="button"
              data-testid="country-trigger"
              aria-haspopup="listbox"
              aria-controls="country-options"
              aria-labelledby="country-code-label"
              :aria-expanded="countryMenuOpen"
              @click="toggleCountryMenu"
              @keydown="handleCountryKeydown"
            >
              <span class="phone-flag" aria-hidden="true">{{
                selectedCountry.flag
              }}</span>
              <span class="phone-country-code">{{ selectedCountry.code }}</span>
              <svg class="phone-chevron" viewBox="0 0 16 16" aria-hidden="true">
                <path d="m4 6 4 4 4-4" />
              </svg>
            </button>
            <div
              v-if="countryMenuOpen"
              id="country-options"
              class="phone-country-menu"
              role="listbox"
            >
              <button
                v-for="country in countryCodes"
                :key="country.code"
                class="phone-country-option"
                type="button"
                role="option"
                :data-testid="`country-option-${country.code}`"
                :aria-selected="country.code === countryCode"
                @click="chooseCountry(country.code)"
              >
                <span class="phone-flag" aria-hidden="true">{{
                  country.flag
                }}</span>
                <span class="phone-country-name">{{
                  countryName(country)
                }}</span>
                <span class="phone-country-option-code">{{
                  country.code
                }}</span>
              </button>
            </div>
          </div>
          <span class="phone-divider" aria-hidden="true"></span>
          <input
            id="countryCode"
            v-model="countryCode"
            type="hidden"
            name="countryCode"
          />
          <label class="sr-only" for="phoneNumber">{{
            t('onboarding.phoneNumber')
          }}</label>
          <input
            id="phoneNumber"
            v-model="phoneNumber"
            class="phone-number-input"
            name="phoneNumber"
            inputmode="tel"
            autocomplete="tel"
            :placeholder="t('onboarding.phoneNumberPlaceholder')"
            required
          />
        </div>
        <p class="phone-hint">{{ t('onboarding.phoneHint') }}</p>
        <p v-if="completeProfile.isError.value" class="form-error" role="alert">
          {{ t('onboarding.error') }}
        </p>
        <button
          class="button"
          type="submit"
          :disabled="completeProfile.isPending.value"
        >
          {{
            completeProfile.isPending.value
              ? t('onboarding.saving')
              : t('onboarding.continue')
          }}
        </button>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useMutation, useQueryClient } from '@tanstack/vue-query';
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import { completeClientProfile } from '../generated/api/sdk.gen';

const queryClient = useQueryClient();
const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const countryCode = ref('+66');
const phoneNumber = ref('');

const countryCodes = [
  { code: '+66', label: 'Thailand (+66)' },
  { code: '+1', label: 'United States / Canada (+1)' },
  { code: '+44', label: 'United Kingdom (+44)' },
  { code: '+61', label: 'Australia (+61)' },
  { code: '+65', label: 'Singapore (+65)' },
  { code: '+60', label: 'Malaysia (+60)' },
  { code: '+81', label: 'Japan (+81)' },
  { code: '+82', label: 'South Korea (+82)' },
  { code: '+86', label: 'China (+86)' },
  { code: '+91', label: 'India (+91)' },
  { code: '+33', label: 'France (+33)' },
  { code: '+49', label: 'Germany (+49)' },
] as const;

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
        <label for="countryCode">{{ t('onboarding.countryCode') }}</label>
        <select id="countryCode" v-model="countryCode" name="countryCode">
          <option
            v-for="country in countryCodes"
            :key="country.code"
            :value="country.code"
          >
            {{ country.label }}
          </option>
        </select>
        <label for="phoneNumber">{{ t('onboarding.phoneNumber') }}</label>
        <input
          id="phoneNumber"
          v-model="phoneNumber"
          name="phoneNumber"
          inputmode="tel"
          autocomplete="tel"
          :placeholder="t('onboarding.phoneNumberPlaceholder')"
          required
        />
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

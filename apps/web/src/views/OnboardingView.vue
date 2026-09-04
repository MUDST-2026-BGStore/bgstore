<script setup lang="ts">
import { useMutation, useQueryClient } from '@tanstack/vue-query';
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import { completeClientProfile } from '../generated/api/sdk.gen';
import CountryCodePicker from '../components/CountryCodePicker.vue';

const queryClient = useQueryClient();
const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const countryCode = ref('+66');
const phoneNumber = ref('');

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
        <div class="phone-control">
          <CountryCodePicker
            v-model="countryCode"
            :label="t('onboarding.countryCode')"
          />
          <span class="phone-divider" aria-hidden="true"></span>
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

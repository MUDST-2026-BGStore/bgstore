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
const phone = ref('');

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
      body: { phone: phone.value },
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
        <label for="phone">{{ t('onboarding.phone') }}</label>
        <input
          id="phone"
          v-model="phone"
          name="phone"
          inputmode="tel"
          autocomplete="tel-national"
          :placeholder="t('onboarding.phonePlaceholder')"
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

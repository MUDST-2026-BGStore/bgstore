<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute } from 'vue-router';

const { t } = useI18n();
const route = useRoute();

const returnTo = computed(() => {
  const redirect = route.query.redirect;
  return typeof redirect === 'string' && redirect.startsWith('/')
    ? redirect
    : '/';
});

const signInHref = computed(
  () =>
    `/oauth2/authorization/keycloak?returnTo=${encodeURIComponent(returnTo.value)}`,
);
</script>

<template>
  <div
    class="flex min-h-screen w-full items-center justify-center bg-canvas p-6"
  >
    <div
      class="flex w-full max-w-md flex-col items-center rounded-xl border border-line bg-surface p-8 text-center shadow-xs"
    >
      <span
        class="mb-4 flex size-12 items-center justify-center rounded-full bg-primary-subtle text-primary font-bold text-lg"
      >
        BG
      </span>
      <h1 class="text-[22px] font-bold text-ink">
        {{ t('auth.signInTitle') }}
      </h1>
      <p class="mt-2 text-[14px] text-ink-secondary">
        {{ t('status.authenticationHint') }}
      </p>
      <a
        :href="signInHref"
        class="mt-6 inline-flex h-10 w-full items-center justify-center rounded-md bg-primary px-4 text-[14px] font-medium text-primary-fg transition-colors hover:opacity-90"
      >
        {{ t('actions.signIn') }}
      </a>
    </div>
  </div>
</template>

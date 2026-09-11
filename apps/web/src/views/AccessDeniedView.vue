<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import UiButton from '../components/ui/UiButton.vue';

const { t } = useI18n();
</script>

<template>
  <section
    class="flex min-h-[calc(100vh-4rem)] w-full items-center justify-center bg-canvas p-6"
  >
    <div
      class="w-full max-w-lg rounded-xl border border-line bg-surface p-8 text-center shadow-xs"
    >
      <h1 class="text-2xl font-bold text-ink">{{ t('accessDenied.title') }}</h1>
      <p class="mt-3 text-sm text-ink-secondary">
        {{ t('accessDenied.description') }}
      </p>
      <UiButton to="/" class="mt-6">{{ t('accessDenied.back') }}</UiButton>
    </div>
  </section>
</template>

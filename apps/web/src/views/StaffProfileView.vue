<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query';
import { useI18n } from 'vue-i18n';
import { currentUserQueryOptions } from '../queries/current-user';

const { t } = useI18n();
const currentUser = useQuery(currentUserQueryOptions());
</script>

<template>
  <section
    class="flex min-h-[calc(100vh-4rem)] w-full justify-center bg-canvas p-6 sm:p-10"
  >
    <div
      class="w-full max-w-2xl rounded-xl border border-line bg-surface p-6 shadow-xs sm:p-8"
    >
      <p class="text-xs font-semibold uppercase tracking-wide text-primary">
        {{ t('navigation.staff') }}
      </p>
      <h1 class="mt-2 text-2xl font-bold text-ink">
        {{ t('userProfile.title') }}
      </h1>
      <p class="mt-2 text-sm text-ink-secondary">
        {{ t('userProfile.staffDescription') }}
      </p>

      <dl v-if="currentUser.data.value" class="mt-8 grid gap-5 sm:grid-cols-2">
        <div>
          <dt class="text-xs font-medium text-ink-secondary">
            {{ t('userProfile.username') }}
          </dt>
          <dd class="mt-1 text-sm text-ink">
            {{ currentUser.data.value.username }}
          </dd>
        </div>
        <div>
          <dt class="text-xs font-medium text-ink-secondary">
            {{ t('userProfile.email') }}
          </dt>
          <dd class="mt-1 text-sm text-ink">
            {{ currentUser.data.value.email }}
          </dd>
        </div>
        <div>
          <dt class="text-xs font-medium text-ink-secondary">
            {{ t('userProfile.firstName') }}
          </dt>
          <dd class="mt-1 text-sm text-ink">
            {{ currentUser.data.value.firstName }}
          </dd>
        </div>
        <div>
          <dt class="text-xs font-medium text-ink-secondary">
            {{ t('userProfile.lastName') }}
          </dt>
          <dd class="mt-1 text-sm text-ink">
            {{ currentUser.data.value.lastName }}
          </dd>
        </div>
      </dl>
    </div>
  </section>
</template>

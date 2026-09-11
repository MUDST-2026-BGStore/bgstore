<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import { useBranches } from '../composables/useBranches';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const { branches, isError, isPending } = useBranches();

const branchId = computed(() => String(route.params.id ?? ''));
const branch = computed(() =>
  branches.value.find((item) => item.id === branchId.value),
);

const goBack = () => {
  void router.push('/branches');
};
</script>

<template>
  <section
    class="w-full bg-white px-6 py-8 md:px-12"
    aria-labelledby="branch-detail-title"
  >
    <div class="mx-auto max-w-4xl">
      <button
        type="button"
        class="mb-6 text-sm font-semibold text-[#386671] hover:underline"
        @click="goBack"
      >
        ← {{ t('branch.backToBranches') }}
      </button>

      <div
        v-if="isPending"
        class="rounded-xl border border-gray-200 p-8 text-center text-gray-500"
        aria-live="polite"
      >
        {{ t('status.connecting') }}
      </div>
      <div
        v-else-if="isError || !branch"
        class="rounded-xl border border-gray-200 p-8 text-center"
      >
        <h1 id="branch-detail-title" class="text-xl font-bold text-gray-900">
          {{ t('branch.notFoundTitle') }}
        </h1>
        <p class="mt-2 text-sm text-gray-500">
          {{ t('branch.notFoundDesc') }}
        </p>
        <button
          type="button"
          class="mt-5 rounded-lg bg-[#386671] px-4 py-2 text-sm font-semibold text-white"
          @click="goBack"
        >
          {{ t('branch.backToBranches') }}
        </button>
      </div>
      <article v-else class="rounded-xl border border-gray-200 p-6">
        <p class="text-xs font-semibold uppercase tracking-wide text-[#386671]">
          {{ t('branch.list') }}
        </p>
        <h1
          id="branch-detail-title"
          class="mt-1 text-2xl font-bold text-gray-900"
        >
          {{ branch.name }}
        </h1>
        <dl class="mt-6 grid gap-4 sm:grid-cols-2">
          <div class="rounded-lg bg-gray-50 p-4">
            <dt
              class="text-xs font-semibold uppercase tracking-wide text-gray-500"
            >
              {{ t('branch.id') }}
            </dt>
            <dd class="mt-1 break-all font-mono text-sm text-gray-900">
              {{ branch.id }}
            </dd>
          </div>
          <div class="rounded-lg bg-gray-50 p-4">
            <dt
              class="text-xs font-semibold uppercase tracking-wide text-gray-500"
            >
              {{ t('branch.table.branch') }}
            </dt>
            <dd class="mt-1 text-sm text-gray-900">{{ branch.name }}</dd>
          </div>
          <div v-if="branch.address" class="rounded-lg bg-gray-50 p-4">
            <dt
              class="text-xs font-semibold uppercase tracking-wide text-gray-500"
            >
              {{ t('branch.address') }}
            </dt>
            <dd class="mt-1 text-sm text-gray-900">{{ branch.address }}</dd>
          </div>
          <div
            v-if="branch.opensAt && branch.closesAt"
            class="rounded-lg bg-gray-50 p-4"
          >
            <dt
              class="text-xs font-semibold uppercase tracking-wide text-gray-500"
            >
              {{ t('branch.openingHours') }}
            </dt>
            <dd class="mt-1 text-sm text-gray-900">
              {{ branch.opensAt }}–{{ branch.closesAt }}
            </dd>
          </div>
        </dl>
      </article>
    </div>
  </section>
</template>

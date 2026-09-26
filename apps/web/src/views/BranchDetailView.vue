<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query';
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import { useBranches } from '../composables/useBranches';
import {
  currentUserQueryOptions,
  hasStaffAccess,
} from '../queries/current-user';
import OwnerPortalLayout from '../layouts/OwnerPortalLayout.vue';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const { branches, isError, isPending } = useBranches();
const currentUser = useQuery(currentUserQueryOptions());

const isStaff = computed(() =>
  currentUser.data.value ? hasStaffAccess(currentUser.data.value.roles) : false,
);
const wrapper = computed(() => (isStaff.value ? OwnerPortalLayout : 'div'));
const wrapperProps = computed(() =>
  isStaff.value ? { active: 'branches' as const } : {},
);

const branchId = computed(() => String(route.params.id ?? ''));
const branch = computed(() =>
  branches.value.find((item) => item.id === branchId.value),
);

const goBack = () => {
  void router.push('/branches');
};
</script>

<template>
  <!-- Staff reach this page from the sidebar-driven branch list, so it keeps
       the same chrome; guests/clients get the plain top-nav page as before. -->
  <component :is="wrapper" v-bind="wrapperProps">
    <section
      class="w-full bg-surface px-6 py-8 md:px-12"
      aria-labelledby="branch-detail-title"
    >
      <div class="mx-auto max-w-4xl">
        <button
          type="button"
          class="mb-6 text-sm font-semibold text-primary hover:underline"
          @click="goBack"
        >
          ← {{ t('branch.backToBranches') }}
        </button>

        <div
          v-if="isPending"
          class="rounded-xl border border-line p-8 text-center text-ink-muted"
          aria-live="polite"
        >
          {{ t('status.connecting') }}
        </div>
        <div
          v-else-if="isError || !branch"
          class="rounded-xl border border-line p-8 text-center"
        >
          <h1 id="branch-detail-title" class="text-xl font-bold text-ink">
            {{ t('branch.notFoundTitle') }}
          </h1>
          <p class="mt-2 text-sm text-ink-muted">
            {{ t('branch.notFoundDesc') }}
          </p>
          <button
            type="button"
            class="mt-5 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-fg"
            @click="goBack"
          >
            {{ t('branch.backToBranches') }}
          </button>
        </div>
        <article v-else class="rounded-xl border border-line p-6">
          <p class="text-xs font-semibold uppercase tracking-wide text-primary">
            {{ t('branch.list') }}
          </p>
          <h1 id="branch-detail-title" class="mt-1 text-2xl font-bold text-ink">
            {{ branch.name }}
          </h1>
          <dl class="mt-6 grid gap-4 sm:grid-cols-2">
            <div class="rounded-lg bg-surface-sunken p-4">
              <dt
                class="text-xs font-semibold uppercase tracking-wide text-ink-muted"
              >
                {{ t('branch.id') }}
              </dt>
              <dd class="mt-1 break-all font-mono text-sm text-ink">
                {{ branch.id }}
              </dd>
            </div>
            <div class="rounded-lg bg-surface-sunken p-4">
              <dt
                class="text-xs font-semibold uppercase tracking-wide text-ink-muted"
              >
                {{ t('branch.table.branch') }}
              </dt>
              <dd class="mt-1 text-sm text-ink">{{ branch.name }}</dd>
            </div>
            <div v-if="branch.address" class="rounded-lg bg-surface-sunken p-4">
              <dt
                class="text-xs font-semibold uppercase tracking-wide text-ink-muted"
              >
                {{ t('branch.address') }}
              </dt>
              <dd class="mt-1 text-sm text-ink">{{ branch.address }}</dd>
            </div>
            <div
              v-if="branch.opensAt && branch.closesAt"
              class="rounded-lg bg-surface-sunken p-4"
            >
              <dt
                class="text-xs font-semibold uppercase tracking-wide text-ink-muted"
              >
                {{ t('branch.openingHours') }}
              </dt>
              <dd class="mt-1 text-sm text-ink">
                {{ branch.opensAt }}–{{ branch.closesAt }}
              </dd>
            </div>
          </dl>
        </article>
      </div>
    </section>
  </component>
</template>

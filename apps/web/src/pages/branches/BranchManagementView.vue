<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query';
import { computed, reactive } from 'vue';
import { useI18n } from 'vue-i18n';
import OwnerPortalLayout from '../../layouts/OwnerPortalLayout.vue';
import UiButton from '../../components/ui/UiButton.vue';
import mapPin from '../../assets/icons/map-pin.svg';
import { createBranch } from '../../generated/api/sdk.gen';
import { branchesQueryOptions } from '../../queries/games';
import {
  currentUserQueryOptions,
  hasManagerAccess,
} from '../../queries/current-user';

const { t } = useI18n();
const queryClient = useQueryClient();
const branches = useQuery(branchesQueryOptions());
const currentUser = useQuery(currentUserQueryOptions());

const form = reactive({
  name: '',
  address: '',
  opensAt: '09:00',
  closesAt: '19:00',
});

const canCreate = computed(() =>
  hasManagerAccess(currentUser.data.value?.roles ?? []),
);

const create = useMutation({
  mutationFn: () =>
    createBranch({
      body: {
        name: form.name.trim(),
        address: form.address.trim() || null,
        opensAt: form.opensAt || null,
        closesAt: form.closesAt || null,
      },
      throwOnError: true,
    }),
  onSuccess: async () => {
    form.name = '';
    form.address = '';
    await queryClient.invalidateQueries({ queryKey: ['branches'] });
  },
});

function submit() {
  if (!form.name.trim() || create.isPending.value) return;
  create.mutate();
}
</script>

<template>
  <OwnerPortalLayout active="branches">
    <div
      class="staff-page-content flex w-full flex-col gap-7 px-8 pt-8 pb-10 sm:px-10"
    >
      <header class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p class="eyebrow mb-2">{{ t('staff.nav.branches') }}</p>
          <h1 class="staff-page-title">{{ t('branch.managementTitle') }}</h1>
          <p class="mt-3 max-w-2xl text-sm leading-6 text-ink-secondary">
            {{ t('branch.managementDescription') }}
          </p>
        </div>
        <span
          class="rounded-full border border-line bg-white/70 px-3 py-1.5 text-xs font-semibold text-ink-secondary"
        >
          {{ branches.data.value?.length ?? 0 }} {{ t('branch.list') }}
        </span>
      </header>

      <div
        class="grid w-full items-start gap-5 xl:grid-cols-[minmax(0,1fr)_22rem]"
      >
        <section class="staff-card-grid" aria-labelledby="branch-list-title">
          <div class="mb-4 flex items-center justify-between gap-3">
            <h2 id="branch-list-title" class="text-lg font-semibold text-ink">
              {{ t('branch.locationsTitle') }}
            </h2>
            <span
              v-if="branches.isFetching.value"
              class="text-xs text-ink-muted"
              aria-live="polite"
            >
              {{ t('status.connecting') }}
            </span>
          </div>

          <div
            v-if="branches.isError.value"
            class="staff-inline-state"
            role="alert"
          >
            <p>{{ t('branch.managementLoadFailed') }}</p>
            <UiButton
              class="mt-3"
              variant="outline"
              size="sm"
              @click="branches.refetch()"
            >
              {{ t('branch.retry') }}
            </UiButton>
          </div>
          <div
            v-else-if="branches.isPending.value"
            class="staff-inline-state"
            aria-live="polite"
          >
            {{ t('status.connecting') }}
          </div>
          <div
            v-else-if="branches.data.value?.length"
            class="grid gap-3 sm:grid-cols-2"
          >
            <article
              v-for="branch in branches.data.value"
              :key="branch.id"
              class="staff-branch-card"
            >
              <div class="staff-branch-card-header">
                <div class="min-w-0">
                  <p class="staff-branch-card-kicker">{{ t('branch.list') }}</p>
                  <h3 class="staff-branch-card-name">{{ branch.name }}</h3>
                </div>
                <span class="staff-branch-marker" aria-hidden="true">BG</span>
              </div>
              <p class="staff-branch-card-address">
                <img :src="mapPin" alt="" width="16" height="16" />
                <span>{{ branch.address || t('branch.noAddress') }}</span>
              </p>
              <div class="staff-branch-card-footer">
                <span class="staff-branch-card-hours-label">
                  {{ t('branch.openingHours') }}
                </span>
                <span class="staff-branch-card-hours">
                  {{
                    branch.opensAt && branch.closesAt
                      ? `${branch.opensAt}–${branch.closesAt}`
                      : t('branch.noHours')
                  }}
                </span>
              </div>
            </article>
          </div>
          <div v-else class="staff-inline-state">
            {{ t('branch.empty.description') }}
          </div>
        </section>

        <section
          v-if="canCreate"
          class="staff-card-grid staff-branch-create-panel"
          aria-labelledby="branch-create-title"
        >
          <h2 id="branch-create-title" class="text-lg font-semibold text-ink">
            {{ t('branch.createTitle') }}
          </h2>
          <p class="mt-2 text-sm leading-6 text-ink-secondary">
            {{ t('branch.createDescription') }}
          </p>
          <form class="mt-5 grid gap-4" @submit.prevent="submit">
            <label class="staff-form-field" for="branch-name">
              <span>{{ t('branch.name') }}</span>
              <input
                id="branch-name"
                v-model="form.name"
                required
                maxlength="120"
                autocomplete="organization"
              />
            </label>
            <label class="staff-form-field" for="branch-address">
              <span>{{ t('branch.address') }}</span>
              <textarea
                id="branch-address"
                v-model="form.address"
                maxlength="300"
                rows="3"
              />
            </label>
            <div class="grid grid-cols-2 gap-3">
              <label class="staff-form-field" for="branch-opens">
                <span>{{ t('branch.opensAt') }}</span>
                <input id="branch-opens" v-model="form.opensAt" type="time" />
              </label>
              <label class="staff-form-field" for="branch-closes">
                <span>{{ t('branch.closesAt') }}</span>
                <input id="branch-closes" v-model="form.closesAt" type="time" />
              </label>
            </div>
            <p
              v-if="create.isError.value"
              class="text-sm text-danger-fg"
              role="alert"
            >
              {{ t('branch.createFailed') }}
            </p>
            <p
              v-if="create.isSuccess.value"
              class="text-sm text-success-fg"
              role="status"
            >
              {{ t('branch.created') }}
            </p>
            <UiButton type="submit" :disabled="create.isPending.value">
              {{
                create.isPending.value
                  ? t('branch.creating')
                  : t('branch.createAction')
              }}
            </UiButton>
          </form>
        </section>
        <section
          v-else
          class="staff-card-grid staff-branch-create-panel"
          aria-labelledby="branch-permission-title"
        >
          <h2
            id="branch-permission-title"
            class="text-lg font-semibold text-ink"
          >
            {{ t('branch.managerOnlyTitle') }}
          </h2>
          <p class="mt-2 text-sm leading-6 text-ink-secondary">
            {{ t('branch.managerOnlyDescription') }}
          </p>
        </section>
      </div>
    </div>
  </OwnerPortalLayout>
</template>

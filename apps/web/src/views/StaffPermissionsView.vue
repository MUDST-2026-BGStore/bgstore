<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query';
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import OwnerLayout from '../layouts/OwnerLayout.vue';
import AccessDeniedView from './AccessDeniedView.vue';
import UiButton from '../components/ui/UiButton.vue';
import { branchesQueryOptions } from '../queries/games';
import {
  currentUserQueryOptions,
  hasManagerAccess,
  listStaffAssignments,
  replaceStaffAssignments,
} from '../queries/current-user';

const { t } = useI18n();
const queryClient = useQueryClient();
const user = useQuery(currentUserQueryOptions());
const canManage = computed(() =>
  hasManagerAccess(user.data.value?.roles ?? []),
);
const branches = useQuery({
  ...branchesQueryOptions(),
  enabled: canManage,
});
const assignments = useQuery({
  queryKey: ['staff-assignments'],
  queryFn: listStaffAssignments,
  enabled: canManage,
});
const selectedSubject = ref('');
const selectedBranchIds = ref<string[]>([]);
const staff = computed(() => assignments.data.value ?? []);
const selectedStaff = computed(() =>
  staff.value.find((entry) => entry.staffSubject === selectedSubject.value),
);

function select(subject: string) {
  selectedSubject.value = subject;
  selectedBranchIds.value = [
    ...(staff.value.find((entry) => entry.staffSubject === subject)
      ?.branchIds ?? []),
  ];
}

const save = useMutation({
  mutationFn: () =>
    replaceStaffAssignments(selectedSubject.value, {
      branchIds: selectedBranchIds.value,
    }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['staff-assignments'] });
  },
});
</script>

<template>
  <AccessDeniedView v-if="!canManage" />
  <OwnerLayout v-else active="profile">
    <main class="staff-page-content w-full px-8 pt-8 pb-10 sm:px-10">
      <p class="eyebrow mb-2">{{ t('staff.nav.profile') }}</p>
      <h1 class="staff-page-title">{{ t('staff.permissions.title') }}</h1>
      <p class="mt-3 max-w-2xl text-sm leading-6 text-ink-secondary">
        {{ t('staff.permissions.description') }}
      </p>
      <section class="mt-7 grid gap-5 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div class="staff-card-grid">
          <p v-if="assignments.isPending.value" role="status">
            {{ t('staff.permissions.loading') }}
          </p>
          <div
            v-else-if="assignments.isError.value"
            class="text-danger-fg"
            role="alert"
          >
            <p>{{ t('staff.permissions.loadError') }}</p>
            <UiButton
              class="mt-3"
              variant="outline"
              size="sm"
              @click="assignments.refetch()"
            >
              {{ t('staff.permissions.retry') }}
            </UiButton>
          </div>
          <button
            v-for="entry in staff"
            :key="entry.staffSubject"
            type="button"
            class="staff-branch-card text-left"
            @click="select(entry.staffSubject)"
          >
            <strong>{{ entry.displayName }}</strong>
            <span
              >{{ entry.email }} ·
              {{
                t('staff.permissions.branchCount', entry.branchIds.length)
              }}</span
            >
          </button>
          <p
            v-if="
              !assignments.isPending.value &&
              !assignments.isError.value &&
              !staff.length
            "
          >
            {{ t('staff.permissions.empty') }}
          </p>
        </div>
        <form
          v-if="selectedSubject"
          class="staff-card-grid"
          @submit.prevent="save.mutate()"
        >
          <h2 class="text-lg font-semibold">
            {{ selectedStaff?.displayName }}
          </h2>
          <p class="text-sm text-ink-secondary">{{ selectedStaff?.email }}</p>
          <div
            v-if="branches.isError.value"
            class="text-danger-fg"
            role="alert"
          >
            <p>{{ t('staff.permissions.branchesError') }}</p>
            <UiButton
              class="mt-3"
              variant="outline"
              size="sm"
              @click="branches.refetch()"
            >
              {{ t('staff.permissions.retry') }}
            </UiButton>
          </div>
          <label
            v-for="branch in branches.data.value ?? []"
            :key="branch.id"
            class="flex items-center gap-3 text-sm"
          >
            <input
              v-model="selectedBranchIds"
              type="checkbox"
              :value="branch.id"
            />
            {{ branch.name }}
          </label>
          <p v-if="save.isError.value" class="text-danger-fg" role="alert">
            {{ t('staff.permissions.saveError') }}
          </p>
          <p v-if="save.isSuccess.value" class="text-success-fg" role="status">
            {{ t('staff.permissions.saved') }}
          </p>
          <UiButton
            type="submit"
            :disabled="
              save.isPending.value ||
              branches.isPending.value ||
              branches.isError.value
            "
            >{{
              save.isPending.value
                ? t('staff.permissions.saving')
                : t('staff.permissions.save')
            }}</UiButton
          >
        </form>
      </section>
    </main>
  </OwnerLayout>
</template>

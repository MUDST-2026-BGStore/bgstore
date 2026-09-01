<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query';
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import GameFormView from './GameFormView.vue';
import OwnerLayout from '../../layouts/OwnerLayout.vue';
import UiButton from '../../components/ui/UiButton.vue';
import { branchesQueryOptions, createGameRequest } from '../../queries/games';
import {
  emptyForm,
  fieldErrorsOf,
  toGameRequest,
  type GameFormValues,
} from './form';

const { t } = useI18n();
const router = useRouter();
const queryClient = useQueryClient();

// The copies-per-branch table is one row per branch, so the form cannot be
// filled in before the directory has loaded.
const branches = useQuery(branchesQueryOptions());

const values = computed(() => emptyForm(branches.data.value ?? []));
const errors = ref<Record<string, string>>({});

const create = useMutation({
  mutationFn: createGameRequest,
  onSuccess: async () => {
    errors.value = {};
    await queryClient.invalidateQueries({ queryKey: ['games'] });
    // The flow ends back on the inventory, with the new row in it.
    await router.push('/games');
  },
  onError: (failure) => {
    errors.value = fieldErrorsOf(failure);
  },
});

function submit(form: GameFormValues) {
  create.mutate(toGameRequest(form));
}
</script>

<template>
  <OwnerLayout v-if="branches.isPending.value">
    <p data-testid="form-loading" class="px-8 py-10 text-[14px] text-ink-muted">
      {{ t('games.state.loading') }}
    </p>
  </OwnerLayout>

  <OwnerLayout v-else-if="branches.isError.value">
    <div class="flex flex-col items-start gap-3 px-8 py-10">
      <p
        data-testid="form-load-error"
        role="alert"
        class="text-[14px] text-danger-fg"
      >
        {{ t('games.state.loadFailed') }}
      </p>
      <UiButton variant="outline" size="sm" @click="branches.refetch()">
        {{ t('games.state.retry') }}
      </UiButton>
    </div>
  </OwnerLayout>

  <GameFormView
    v-else
    :breadcrumb="t('games.form.addBreadcrumb')"
    :page-title="t('games.form.addTitle')"
    :secondary-label="t('games.form.discard')"
    :primary-label="t('games.form.submitAdd')"
    :values="values"
    :errors="errors"
    :pending="create.isPending.value"
    :failed="create.isError.value"
    @submit="submit"
  />
</template>

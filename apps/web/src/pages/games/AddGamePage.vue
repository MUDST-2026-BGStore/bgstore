<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query';
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import GameFormView from './GameFormView.vue';
import ScreenStatus from '../../components/ScreenStatus.vue';
import { branchesQueryOptions, createGameRequest } from '../../queries/games';
import {
  emptyForm,
  fieldErrorsOf,
  numericErrorsOf,
  toGameRequest,
  type GameFormValues,
} from './form';
import { resolveLocalized } from './localized';

const { t, locale } = useI18n();
const router = useRouter();
const queryClient = useQueryClient();

// The copies-per-branch table is one row per branch, so the form cannot be
// filled in before the directory has loaded.
const branches = useQuery(branchesQueryOptions());

const values = computed(() => emptyForm(branches.data.value ?? []));
const errors = ref<Record<string, string>>({});

const create = useMutation({
  mutationFn: createGameRequest,
  onSuccess: async (game) => {
    errors.value = {};
    await queryClient.invalidateQueries({ queryKey: ['games'] });
    // Back to the inventory, which reports the saved title as the success
    // state — the row itself is the other half of the confirmation. The title
    // is resolved here so the notice reads in the same language as the row.
    await router.push({
      path: '/games',
      query: { saved: resolveLocalized(game.title, locale.value) },
    });
  },
  onError: (failure) => {
    errors.value = fieldErrorsOf(failure);
  },
});

function submit(form: GameFormValues) {
  // A number the browser cannot read is reported here rather than sent, so it
  // is marked as a wrong value and not as a missing one. Everything else is
  // still the API's to judge.
  const malformed = numericErrorsOf(form);
  errors.value = malformed;
  if (Object.keys(malformed).length > 0) {
    return;
  }

  create.mutate(toGameRequest(form));
}
</script>

<template>
  <ScreenStatus
    v-if="branches.isPending.value"
    state="loading"
    testid="form-loading"
  />

  <ScreenStatus
    v-else-if="branches.isError.value"
    state="failed"
    testid="form-load-error"
    @retry="branches.refetch()"
  />

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

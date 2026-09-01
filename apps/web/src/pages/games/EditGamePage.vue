<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query';
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import GameFormView from './GameFormView.vue';
import ScreenStatus from '../../components/ScreenStatus.vue';
import {
  branchesQueryOptions,
  gameQueryOptions,
  updateGameRequest,
} from '../../queries/games';
import {
  emptyForm,
  fieldErrorsOf,
  formValuesOf,
  hasStatus,
  toGameRequest,
  type GameFormValues,
} from './form';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const queryClient = useQueryClient();

const gameId = computed(() => String(route.params.gameId));

const branches = useQuery(branchesQueryOptions());
const game = useQuery(computed(() => gameQueryOptions(gameId.value)));

const loading = computed(
  () => branches.isPending.value || game.isPending.value,
);
const missing = computed(() => hasStatus(game.error.value, 404));
const failedToLoad = computed(
  () => (branches.isError.value || game.isError.value) && !missing.value,
);

const values = computed<GameFormValues>(() => {
  const loaded = game.data.value;
  const directory = branches.data.value ?? [];

  return loaded ? formValuesOf(loaded, directory) : emptyForm(directory);
});

const errors = ref<Record<string, string>>({});

const update = useMutation({
  mutationFn: (body: ReturnType<typeof toGameRequest>) =>
    updateGameRequest(gameId.value, body),
  onSuccess: async (game) => {
    errors.value = {};
    await queryClient.invalidateQueries({ queryKey: ['games'] });
    // The detail screen shows the saved record, which is the success state.
    await router.push({
      path: '/games/' + gameId.value,
      query: { saved: game.title },
    });
  },
  onError: (failure) => {
    errors.value = fieldErrorsOf(failure);
  },
});

function submit(form: GameFormValues) {
  update.mutate(toGameRequest(form));
}
</script>

<template>
  <ScreenStatus v-if="loading" state="loading" testid="form-loading" />

  <ScreenStatus v-else-if="missing" state="missing" testid="game-not-found" />

  <ScreenStatus
    v-else-if="failedToLoad"
    state="failed"
    testid="form-load-error"
    @retry="game.refetch()"
  />

  <GameFormView
    v-else
    :breadcrumb="t('games.form.editBreadcrumb')"
    :page-title="t('games.form.editTitle', { title: values.title })"
    :secondary-label="t('games.form.cancel')"
    :primary-label="t('games.form.submitEdit')"
    :values="values"
    :errors="errors"
    :pending="update.isPending.value"
    :failed="update.isError.value"
    show-thumbnails
    @submit="submit"
  />
</template>

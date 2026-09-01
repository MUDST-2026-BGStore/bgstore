<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query';
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';
import GameFormView from './GameFormView.vue';
import OwnerLayout from '../../layouts/OwnerLayout.vue';
import UiButton from '../../components/ui/UiButton.vue';
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
  onSuccess: async () => {
    errors.value = {};
    await queryClient.invalidateQueries({ queryKey: ['games'] });
    await router.push('/games/' + gameId.value);
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
  <OwnerLayout v-if="loading">
    <p data-testid="form-loading" class="px-8 py-10 text-[14px] text-ink-muted">
      {{ t('games.state.loading') }}
    </p>
  </OwnerLayout>

  <OwnerLayout v-else-if="missing">
    <div class="flex flex-col items-start gap-3 px-8 py-10">
      <p data-testid="game-not-found" role="alert" class="text-[14px] text-ink">
        {{ t('games.state.notFound') }}
      </p>
      <UiButton variant="outline" to="/games">
        {{ t('games.details.back') }}
      </UiButton>
    </div>
  </OwnerLayout>

  <OwnerLayout v-else-if="failedToLoad">
    <div class="flex flex-col items-start gap-3 px-8 py-10">
      <p
        data-testid="form-load-error"
        role="alert"
        class="text-[14px] text-danger-fg"
      >
        {{ t('games.state.loadFailed') }}
      </p>
      <UiButton variant="outline" size="sm" @click="game.refetch()">
        {{ t('games.state.retry') }}
      </UiButton>
    </div>
  </OwnerLayout>

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

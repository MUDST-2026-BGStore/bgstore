<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import OwnerLayout from '../layouts/OwnerLayout.vue';
import UiButton from './ui/UiButton.vue';

/**
 * The whole-screen stand-in a page shows while it has nothing to render yet:
 * loading, a load failure with a retry, or a record the API does not have.
 *
 * Every game screen needs the same three, so they live here rather than being
 * repeated per page.
 */
const props = defineProps<{
  state: 'loading' | 'failed' | 'missing';
  /** Test hook, so a page's own state can be addressed by name. */
  testid: string;
}>();

const emit = defineEmits<{ retry: [] }>();

const { t } = useI18n();

const message = {
  loading: 'games.state.loading',
  failed: 'games.state.loadFailed',
  missing: 'games.state.notFound',
}[props.state];
</script>

<template>
  <OwnerLayout>
    <div class="flex flex-col items-start gap-3 px-8 py-10">
      <p
        :data-testid="testid"
        :role="state === 'loading' ? undefined : 'alert'"
        class="text-[14px]"
        :class="state === 'failed' ? 'text-danger-fg' : 'text-ink-muted'"
      >
        {{ t(message) }}
      </p>

      <UiButton
        v-if="state === 'failed'"
        variant="outline"
        size="sm"
        @click="emit('retry')"
      >
        {{ t('games.state.retry') }}
      </UiButton>
      <UiButton v-else-if="state === 'missing'" variant="outline" to="/games">
        {{ t('games.details.back') }}
      </UiButton>
    </div>
  </OwnerLayout>
</template>

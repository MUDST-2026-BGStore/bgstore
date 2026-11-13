<script setup lang="ts">
import AppButton from './AppButton.vue';
defineProps<{ count: number }>();
const emit = defineEmits<{ clear: [] }>();
</script>
<template>
  <div
    class="app-selection-bar"
    :class="{ 'is-empty': !count }"
    :aria-hidden="!count"
    :inert="!count || undefined"
    role="region"
    aria-label="Selected row actions"
  >
    <span class="selection-count" role="status">{{ count }} selected</span>
    <div class="selection-actions"><slot /></div>
    <AppButton appearance="subtle" @click="emit('clear')"
      >Clear selection</AppButton
    >
  </div>
</template>
<style scoped>
.app-selection-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
  justify-content: flex-end;
  min-height: 32px;
}
/* Keep the same footprint, including wrapped actions, before selection. */
.app-selection-bar.is-empty {
  visibility: hidden;
}
.selection-count {
  min-inline-size: 12ch;
  font-size: 13px;
  font-weight: 600;
  color: var(--ui-text);
  white-space: nowrap;
}
.selection-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
</style>

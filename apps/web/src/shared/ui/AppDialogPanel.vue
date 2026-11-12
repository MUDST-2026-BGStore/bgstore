<script setup lang="ts">
import AppButton from './AppButton.vue';
import AppFormActions from './AppFormActions.vue';
withDefaults(
  defineProps<{
    titleId: string;
    title: string;
    description?: string;
    as?: 'div' | 'form';
  }>(),
  { as: 'div', description: undefined },
);
const emit = defineEmits<{ close: [] }>();
</script>
<template>
  <component :is="as" class="app-dialog-panel">
    <header class="dialog-panel-header">
      <div>
        <h2 :id="titleId">{{ title }}</h2>
        <p v-if="description">{{ description }}</p>
      </div>
      <AppButton appearance="subtle" aria-label="Close" @click="emit('close')"
        ><i class="fa-solid fa-xmark" aria-hidden="true"></i
      ></AppButton>
    </header>
    <div class="dialog-panel-body"><slot /></div>
    <AppFormActions v-if="$slots.footer"><slot name="footer" /></AppFormActions>
  </component>
</template>
<style scoped>
.app-dialog-panel {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 0;
  max-height: calc(100dvh - 32px);
  margin: 0;
  border: 1px solid var(--ui-stroke);
  border-radius: var(--ui-radius-lg);
  background: var(--ui-surface);
  color: var(--ui-text);
  overflow: hidden;
  box-shadow: 0 12px 36px rgb(0 0 0 / 18%);
}
.dialog-panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-shrink: 0;
  gap: 16px;
  padding: 20px 24px;
  border-bottom: 1px solid var(--ui-divider);
}
.dialog-panel-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 26px;
}
.dialog-panel-header p {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--ui-muted);
}
.dialog-panel-header > .app-button {
  min-width: 32px;
  padding-inline: 8px;
}
.dialog-panel-body {
  min-height: 0;
  padding: 24px;
  overflow-y: auto;
  overscroll-behavior: contain;
}
@media (max-width: 520px) {
  .dialog-panel-header {
    padding: 16px;
  }
  .dialog-panel-body {
    padding: 20px 16px;
  }
}
</style>

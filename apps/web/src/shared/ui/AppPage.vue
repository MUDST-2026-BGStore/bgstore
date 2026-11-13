<script setup lang="ts">
import { ref } from 'vue';
defineProps<{ title: string; subtitle?: string; titleId?: string }>();
const heading = ref<HTMLHeadingElement>();
defineExpose({ focusHeading: () => heading.value?.focus() });
</script>
<template>
  <div class="app-page">
    <header class="app-page-header">
      <div>
        <h1 :id="titleId" ref="heading" tabindex="-1">{{ title }}</h1>
        <p v-if="subtitle">{{ subtitle }}</p>
      </div>
      <slot name="actions" />
    </header>
    <slot />
  </div>
</template>
<style scoped>
.app-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 20px;
  min-width: 0;
  color: var(--ui-text);
}
.app-page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.app-page-header h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 600;
  line-height: 36px;
  letter-spacing: -0.5px;
}
.app-page-header h1:focus-visible {
  outline: 2px solid var(--ui-focus);
  outline-offset: 3px;
}
.app-page-header p {
  margin: 4px 0 0;
  color: var(--ui-muted);
  font-size: 14px;
  line-height: 20px;
}
@media (max-width: 600px) {
  .app-page-header {
    align-items: flex-start;
    gap: 12px;
  }
  .app-page-header h1 {
    font-size: 24px;
    line-height: 32px;
  }
}
</style>

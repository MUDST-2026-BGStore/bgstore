<script setup lang="ts">
defineProps<{ title?: string; titleId?: string }>();
</script>
<template>
  <section class="app-panel" :aria-labelledby="title ? titleId : undefined">
    <header v-if="title" class="panel-header">
      <div class="panel-label">
        <h2 :id="titleId">{{ title }}</h2>
        <slot name="meta" />
      </div>
      <slot name="command" />
    </header>
    <slot />
    <footer v-if="$slots.footer" class="panel-footer">
      <slot name="footer" />
    </footer>
  </section>
</template>
<style scoped>
.app-panel {
  min-width: 0;
  border: 1px solid var(--ui-stroke-subtle);
  border-radius: var(--ui-radius-lg);
  background: var(--ui-surface);
  color: var(--ui-text);
  box-shadow: 0 1px 2px rgb(0 0 0 / 3%);
  overflow: clip;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 52px;
  padding: 10px 20px;
}
.panel-label {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.panel-label h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 22px;
}
.panel-footer {
  padding: 8px 20px;
  border-top: 1px solid var(--ui-divider);
}
@media (max-width: 600px) {
  .panel-header {
    padding-inline: 16px;
  }
  .panel-label {
    gap: 8px;
  }
  .panel-label h2 {
    font-size: 14px;
    line-height: 20px;
  }
}
</style>

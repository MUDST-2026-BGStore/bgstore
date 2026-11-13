<script setup lang="ts">
withDefaults(defineProps<{ columns?: 1 | 2 | 3 }>(), { columns: 2 });
</script>
<template>
  <div class="app-form-grid" :style="{ '--form-columns': columns }">
    <slot />
  </div>
</template>
<style scoped>
.app-form-grid {
  display: grid;
  grid-template-columns: repeat(var(--form-columns), minmax(0, 1fr));
  gap: 20px 16px;
}
.app-form-grid :deep(.field-full-width) {
  grid-column: 1 / -1;
}
@media (max-width: 520px) {
  .app-form-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

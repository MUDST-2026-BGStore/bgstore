<script setup lang="ts">
defineProps<{ inspecting?: boolean }>();
</script>
<template>
  <div class="app-master-detail" :class="{ 'is-inspecting': inspecting }">
    <slot />
  </div>
</template>
<style scoped>
.app-master-detail {
  min-width: 0;
}
.is-inspecting {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  align-items: stretch;
}
.app-master-detail > :deep(.app-inspector) {
  border-left: 1px solid var(--ui-divider);
}
@media (max-width: 1100px) {
  .is-inspecting {
    grid-template-columns: minmax(0, 1fr);
  }
  .app-master-detail > :deep(.app-inspector) {
    grid-row: 1;
    border-left: 0;
    border-bottom: 1px solid var(--ui-divider);
  }
}
</style>

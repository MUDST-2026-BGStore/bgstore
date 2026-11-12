<script setup lang="ts">
defineProps<{ title: string; compact?: boolean }>();
</script>
<template>
  <div class="app-empty-state" :class="{ compact }" role="status">
    <component :is="compact ? 'p' : 'h2'">{{ title }}</component>
    <div v-if="$slots.default" class="empty-actions"><slot /></div>
  </div>
</template>
<style scoped>
.app-empty-state {
  padding: 40px 20px;
  color: var(--ui-muted);
  text-align: center;
  font-size: 14px;
  line-height: 20px;
}
h2,
p {
  margin: 0;
  font: inherit;
}
h2 {
  color: var(--ui-text);
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
}
.empty-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;
}
.compact {
  padding: 20px 16px;
}
</style>

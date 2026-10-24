<script setup lang="ts">
defineProps<{
  title?: string;
}>();
</script>

<template>
  <div class="b-card">
    <div v-if="title" class="b-card__header">
      <h3 class="b-card__title">
        {{ title }}
      </h3>
    </div>
    <div class="b-card__body">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.b-card {
  background: var(--color-surface, #1e293b);
  border: 1px solid var(--color-border, #334155);
  border-radius: 0.75rem;
  overflow: hidden;
}

.b-card__header {
  padding: 1rem 1.25rem;
  border-bottom: 1px solid var(--color-border, #334155);
}

.b-card__title {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--color-text, #e2e8f0);
}

.b-card__body {
  padding: 1.25rem;
}
</style>

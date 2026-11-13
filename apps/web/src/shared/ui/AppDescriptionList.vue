<script setup lang="ts">
defineProps<{ items: readonly { label: string; value: string | number }[] }>();
</script>
<template>
  <dl class="app-description-list">
    <div v-for="item in items" :key="item.label">
      <dt>{{ item.label }}</dt>
      <dd>{{ item.value }}</dd>
    </div>
  </dl>
</template>
<style scoped>
.app-description-list {
  display: grid;
  gap: 14px;
  margin: 0;
  font-size: 13px;
  line-height: 20px;
}
.app-description-list > div {
  display: grid;
  grid-template-columns: 95px minmax(0, 1fr);
  gap: 12px;
}
.app-description-list dt {
  color: var(--ui-muted);
}
.app-description-list dd {
  margin: 0;
  color: var(--ui-text);
  overflow-wrap: anywhere;
}
</style>

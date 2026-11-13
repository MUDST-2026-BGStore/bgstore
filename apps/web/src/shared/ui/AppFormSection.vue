<script setup lang="ts">
defineProps<{ title: string; titleId: string }>();
</script>
<template>
  <section class="app-form-section" :aria-labelledby="titleId">
    <h2 :id="titleId">{{ title }}</h2>
    <div class="form-section-body"><slot /></div>
  </section>
</template>
<style scoped>
.app-form-section {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) minmax(0, 2fr);
  gap: 24px;
  padding: 24px;
}
.app-form-section + .app-form-section {
  border-top: 1px solid var(--ui-divider);
}
h2 {
  margin: 0;
  color: var(--ui-text);
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
}
.form-section-body {
  min-width: 0;
  max-width: 600px;
}
@media (max-width: 700px) {
  .app-form-section {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
    padding: 16px;
  }
}
</style>

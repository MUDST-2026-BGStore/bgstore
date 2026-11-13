<script setup lang="ts">
import { onMounted, ref } from 'vue';
import AppButton from './AppButton.vue';
defineProps<{ title: string; titleId: string }>();
const emit = defineEmits<{ close: [] }>();
const panel = ref<HTMLElement>();
onMounted(() => {
  if (panel.value)
    panel.value.focus({
      preventScroll: getComputedStyle(panel.value).gridRowStart !== '1',
    });
});
</script>
<template>
  <aside
    ref="panel"
    class="app-inspector"
    tabindex="-1"
    :aria-labelledby="titleId"
    @keydown.esc.stop="emit('close')"
  >
    <header class="inspector-header">
      <h2 :id="titleId">{{ title }}</h2>
      <AppButton
        appearance="subtle"
        aria-label="Close details"
        @click="emit('close')"
        ><i class="fa-solid fa-xmark" aria-hidden="true"></i
      ></AppButton>
    </header>
    <div class="inspector-body"><slot /></div>
  </aside>
</template>
<style scoped>
.app-inspector {
  min-width: 0;
  background: var(--ui-surface);
}
.app-inspector:focus-visible {
  outline: 2px solid var(--ui-focus);
  outline-offset: -2px;
}
.inspector-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid var(--ui-divider);
}
.inspector-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
}
.inspector-header > .app-button {
  padding-inline: 8px;
}
.inspector-body {
  padding: 16px;
}
</style>

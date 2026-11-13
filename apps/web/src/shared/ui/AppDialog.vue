<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue';
defineProps<{ titleId: string; size?: 'compact' | 'wide' }>();
const emit = defineEmits<{ close: [] }>();
const dialog = ref<HTMLDialogElement>();
onMounted(() => dialog.value?.showModal());
onBeforeUnmount(() => dialog.value?.close());
function onBackdropClick(event: MouseEvent) {
  if (event.target === dialog.value) emit('close');
}
</script>
<template>
  <dialog
    ref="dialog"
    class="app-dialog"
    :class="size ? `app-dialog--${size}` : undefined"
    :aria-labelledby="titleId"
    @cancel.prevent="emit('close')"
    @click="onBackdropClick"
  >
    <slot />
  </dialog>
</template>
<style scoped>
.app-dialog {
  position: fixed;
  inset: 0;
  width: min(600px, calc(100vw - 32px));
  height: fit-content;
  max-width: none;
  max-height: calc(100dvh - 32px);
  margin: auto;
  padding: 0;
  border: 0;
  border-radius: var(--ui-radius-lg);
  color: inherit;
  font: inherit;
  background: transparent;
  overflow: visible;
}
.app-dialog--compact {
  width: min(460px, calc(100vw - 32px));
}
.app-dialog--wide {
  width: min(740px, calc(100vw - 32px));
}
.app-dialog::backdrop {
  background: rgb(16 26 20 / 42%);
}
</style>

<script setup lang="ts">
import AppButton from './AppButton.vue';
import AppDialog from './AppDialog.vue';
import AppDialogPanel from './AppDialogPanel.vue';
withDefaults(
  defineProps<{
    titleId: string;
    title: string;
    description?: string;
    confirmLabel: string;
    cancelLabel?: string;
    intent?: 'default' | 'danger';
  }>(),
  { cancelLabel: 'Cancel', intent: 'default', description: undefined },
);
const emit = defineEmits<{ close: []; confirm: [] }>();
</script>
<template>
  <AppDialog :title-id="titleId" size="compact" @close="emit('close')"
    ><AppDialogPanel
      :title-id="titleId"
      :title="title"
      :description="description"
      @close="emit('close')"
      ><slot /><template #footer
        ><AppButton @click="emit('close')">{{ cancelLabel }}</AppButton
        ><AppButton
          appearance="primary"
          :intent="intent"
          @click="emit('confirm')"
          >{{ confirmLabel }}</AppButton
        ></template
      ></AppDialogPanel
    ></AppDialog
  >
</template>

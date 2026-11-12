<script setup lang="ts">
import AppCheckbox from './AppCheckbox.vue';
withDefaults(
  defineProps<{
    modelValue: boolean;
    label: string;
    header?: boolean;
    indeterminate?: boolean;
    disabled?: boolean;
  }>(),
  { header: false, indeterminate: false, disabled: false },
);
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>();
</script>
<template>
  <component
    :is="header ? 'th' : 'td'"
    :scope="header ? 'col' : undefined"
    class="selection-cell"
    @click.stop
  >
    <AppCheckbox
      :model-value="modelValue"
      :label="label"
      :indeterminate="indeterminate"
      :disabled="disabled"
      @update:model-value="emit('update:modelValue', $event)"
    />
  </component>
</template>

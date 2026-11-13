<script setup lang="ts">
withDefaults(
  defineProps<{
    modelValue: boolean;
    label: string;
    indeterminate?: boolean;
    disabled?: boolean;
  }>(),
  { indeterminate: false, disabled: false },
);
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>();
function update(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).checked);
}
</script>
<template>
  <input
    type="checkbox"
    class="app-checkbox"
    :checked="modelValue"
    :indeterminate.prop="indeterminate"
    :aria-label="label"
    :disabled="disabled"
    @change="update"
  />
</template>
<style scoped>
.app-checkbox {
  width: 18px;
  height: 18px;
  margin: 0;
  vertical-align: middle;
  accent-color: var(--ui-brand);
  cursor: pointer;
}
.app-checkbox:focus-visible {
  outline: 2px solid var(--ui-focus);
  outline-offset: 3px;
}
.app-checkbox:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>

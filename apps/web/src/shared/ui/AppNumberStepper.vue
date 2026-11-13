<script setup lang="ts">
import AppButton from './AppButton.vue';
const props = withDefaults(
  defineProps<{
    modelValue: number;
    label: string;
    inputId?: string;
    min?: number;
    max?: number;
    step?: number;
    disabled?: boolean;
  }>(),
  {
    min: 0,
    max: Number.MAX_SAFE_INTEGER,
    step: 1,
    disabled: false,
    inputId: undefined,
  },
);
const emit = defineEmits<{ 'update:modelValue': [value: number] }>();
function setValue(value: number) {
  if (!Number.isFinite(value)) return;
  const next = Math.min(props.max, Math.max(props.min, Math.round(value)));
  emit('update:modelValue', next);
}
function onChange(event: Event) {
  const input = event.target as HTMLInputElement;
  if (input.value === '' || !Number.isFinite(input.valueAsNumber)) {
    input.value = String(props.modelValue);
    return;
  }
  setValue(input.valueAsNumber);
  input.value = String(
    Math.min(props.max, Math.max(props.min, Math.round(input.valueAsNumber))),
  );
}
</script>
<template>
  <div class="app-number-stepper" @click.stop>
    <AppButton
      :aria-label="`Decrease ${label}`"
      :disabled="disabled || modelValue <= min"
      @click="setValue(modelValue - step)"
      >−</AppButton
    >
    <input
      :id="inputId"
      type="number"
      :disabled="disabled"
      :value="modelValue"
      :min="min"
      :max="max"
      :step="step"
      :aria-label="label"
      @change="onChange"
    />
    <AppButton
      :aria-label="`Increase ${label}`"
      :disabled="disabled || modelValue >= max"
      @click="setValue(modelValue + step)"
      >+</AppButton
    >
  </div>
</template>
<style scoped>
.app-number-stepper {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.app-number-stepper > .app-button {
  min-width: max(30px, calc(var(--ui-control-height) - 4px));
  min-height: calc(var(--ui-control-height) - 2px);
  padding-inline: 6px;
}
.app-number-stepper input {
  width: 56px;
  min-width: 0;
  height: calc(var(--ui-control-height) - 2px);
  padding: 4px;
  border: 1px solid var(--ui-stroke);
  border-radius: var(--ui-radius);
  background: var(--ui-surface);
  color: var(--ui-text);
  font: inherit;
  font-size: var(--ui-control-font-size);
  text-align: center;
  font-variant-numeric: tabular-nums;
  appearance: textfield;
}
.app-number-stepper input::-webkit-inner-spin-button,
.app-number-stepper input::-webkit-outer-spin-button {
  appearance: none;
  margin: 0;
}
.app-number-stepper input:focus-visible {
  outline: 2px solid var(--ui-focus);
  outline-offset: 1px;
}
</style>

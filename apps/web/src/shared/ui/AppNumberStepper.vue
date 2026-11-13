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
/* One joined control: [−][value][+] inside a single border. */
.app-number-stepper {
  display: inline-flex;
  align-items: stretch;
  height: var(--ui-control-height);
  border: 1px solid var(--ui-stroke);
  border-radius: var(--ui-radius);
  background: var(--ui-surface);
  overflow: hidden;
}
.app-number-stepper:focus-within {
  border-color: var(--ui-stroke-strong);
}
.app-number-stepper > .app-button {
  min-width: var(--ui-control-height);
  min-height: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--ui-text);
  font-size: calc(var(--ui-control-font-size) + 4px);
  line-height: 1;
}
.app-number-stepper > .app-button:hover:enabled {
  background: var(--ui-surface-hover);
}
.app-number-stepper > .app-button:focus-visible {
  outline: 2px solid var(--ui-focus);
  outline-offset: -2px;
}
.app-number-stepper input {
  width: calc(var(--ui-control-height) + 12px);
  min-width: 0;
  height: 100%;
  padding: 0 4px;
  border: 0;
  border-inline: 1px solid var(--ui-stroke);
  border-radius: 0;
  background: transparent;
  color: var(--ui-text);
  font: inherit;
  font-size: var(--ui-control-font-size);
  font-weight: 600;
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
  outline-offset: -2px;
}
@media (forced-colors: active) {
  .app-number-stepper {
    border-color: ButtonText;
  }
}
</style>

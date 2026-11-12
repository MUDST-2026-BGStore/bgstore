<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string;
    modelValue: string;
    options: readonly {
      id: string;
      label: string;
      description?: string;
      disabled?: boolean;
    }[];
    layout?: 'grid' | 'list';
  }>(),
  { layout: 'grid' },
);
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
</script>
<template>
  <fieldset class="app-choice-group">
    <legend>{{ label }}</legend>
    <div
      class="choice-options"
      :class="{ 'choice-options--list': layout === 'list' }"
    >
      <button
        v-for="option in options"
        :key="option.id"
        type="button"
        class="choice-option"
        :aria-pressed="modelValue === option.id"
        :disabled="option.disabled"
        @click="emit('update:modelValue', option.id)"
      >
        <strong>{{ option.label }}</strong
        ><span v-if="option.description">{{ option.description }}</span>
      </button>
    </div>
  </fieldset>
</template>
<style scoped>
.app-choice-group {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}
.app-choice-group legend {
  margin-bottom: 8px;
  padding: 0;
  color: var(--ui-text);
  font-size: 13px;
  line-height: 20px;
  font-weight: 500;
}
.choice-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.choice-option {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 0;
  min-height: 64px;
  padding: 12px;
  border: 1px solid var(--ui-stroke);
  border-radius: var(--ui-radius);
  background: var(--ui-surface);
  color: var(--ui-text);
  text-align: left;
  font: inherit;
  cursor: pointer;
}
.choice-option strong {
  font-size: 14px;
  line-height: 20px;
  font-weight: 600;
}
.choice-option span {
  color: var(--ui-muted);
  font-size: 12px;
  line-height: 16px;
  white-space: pre-line;
}
.choice-option:hover:enabled {
  background: var(--ui-surface-hover);
  border-color: var(--ui-stroke-strong);
}
.choice-option[aria-pressed='true'] {
  border-color: var(--ui-brand);
  background: var(--ui-selected);
}
.choice-option:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.choice-option:focus-visible {
  outline: 2px solid var(--ui-focus);
  outline-offset: 2px;
}
.choice-options--list {
  grid-template-columns: minmax(0, 1fr);
  max-height: 280px;
  overflow-y: auto;
}
@media (max-width: 520px) {
  .choice-options {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .choice-options--list {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

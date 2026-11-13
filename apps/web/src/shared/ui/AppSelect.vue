<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import AppDropdown from './AppDropdown.vue';
import type { SelectOption } from './dropdown-types';

const props = withDefaults(
  defineProps<{
    modelValue: string;
    label: string;
    options: readonly SelectOption[];
    inputId?: string;
    name?: string;
    placeholder?: string;
    disabled?: boolean;
    /** Fill the container's width, like a form field. */
    block?: boolean;
  }>(),
  { placeholder: 'Select an option', disabled: false, block: false },
);
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
const dropdown = ref<InstanceType<typeof AppDropdown>>();
const optionElements: HTMLElement[] = [];
function setOptionRef(element: unknown, index: number) {
  if (element instanceof HTMLElement) optionElements[index] = element;
}
let searchText = '';
let lastTyped = 0;
function typeahead(event: KeyboardEvent) {
  if (
    event.key.length !== 1 ||
    event.key === ' ' ||
    event.ctrlKey ||
    event.metaKey ||
    event.altKey
  )
    return;
  event.preventDefault();
  searchText =
    Date.now() - lastTyped > 600 ? event.key : searchText + event.key;
  lastTyped = Date.now();
  const index = props.options.findIndex(
    (option) =>
      !option.disabled &&
      option.label.toLowerCase().startsWith(searchText.toLowerCase()),
  );
  if (index >= 0) void focusOption(index);
}
const active = ref(0);
const selectedLabel = computed(
  () =>
    props.options.find((option) => option.value === props.modelValue)?.label ||
    props.placeholder,
);
function select(option: SelectOption) {
  if (option.disabled) return;
  emit('update:modelValue', option.value);
  dropdown.value?.close();
}
async function focusOption(index: number) {
  active.value = index;
  await nextTick();
  optionElements[index]?.focus({ preventScroll: true });
  optionElements[index]?.scrollIntoView({ block: 'nearest' });
}
function onOpen() {
  const selected = props.options.findIndex(
    (option) => option.value === props.modelValue && !option.disabled,
  );
  void focusOption(
    selected >= 0
      ? selected
      : Math.max(
          0,
          props.options.findIndex((option) => !option.disabled),
        ),
  );
}
function move(direction: number) {
  const enabled = props.options
    .map((option, index) => (option.disabled ? -1 : index))
    .filter((index) => index >= 0);
  if (!enabled.length) return;
  const position = enabled.indexOf(active.value);
  void focusOption(
    enabled[(position + direction + enabled.length) % enabled.length],
  );
}
function onTriggerKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault();
    dropdown.value?.open();
  }
}
function onOptionKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault();
    move(event.key === 'ArrowDown' ? 1 : -1);
  } else if (event.key === 'Home' || event.key === 'End') {
    event.preventDefault();
    const enabled = props.options
      .map((option, index) => (option.disabled ? -1 : index))
      .filter((index) => index >= 0);
    if (enabled.length)
      void focusOption(
        event.key === 'Home' ? enabled[0] : enabled[enabled.length - 1],
      );
  } else if (event.key === 'Tab') dropdown.value?.close();
  else typeahead(event);
}
watch(
  () => props.options,
  () => {
    optionElements.length = 0;
  },
);
</script>

<template>
  <AppDropdown
    ref="dropdown"
    class="app-select"
    :trigger-id="inputId"
    :label="`${label}: ${selectedLabel}`"
    :panel-label="label"
    popup-role="listbox"
    :width="240"
    :disabled="disabled"
    :block="block"
    @opened="onOpen"
    @trigger-keydown="onTriggerKeydown"
  >
    <template #trigger
      ><span class="selected-label">{{ selectedLabel }}</span></template
    >
    <button
      v-for="(option, index) in options"
      :key="option.value"
      :ref="(element) => setOptionRef(element, index)"
      type="button"
      role="option"
      class="select-option"
      :aria-selected="option.value === modelValue"
      :disabled="option.disabled"
      :tabindex="active === index ? 0 : -1"
      @focus="active = index"
      @keydown="onOptionKeydown"
      @click="select(option)"
    >
      <span>{{ option.label }}</span
      ><span v-if="option.value === modelValue" aria-hidden="true">✓</span>
    </button>
  </AppDropdown>
  <input
    v-if="name"
    type="hidden"
    :name="name"
    :value="modelValue"
    :disabled="disabled"
  />
</template>

<style scoped>
.selected-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.select-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 36px;
  padding: 8px 12px;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: start;
  cursor: pointer;
}
.select-option:hover:enabled,
.select-option:focus-visible {
  background: var(--ui-selected);
  outline: 2px solid var(--ui-focus);
  outline-offset: -2px;
}
.select-option[aria-selected='true'] {
  color: var(--ui-brand);
  font-weight: 600;
}
.select-option:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>

<script setup lang="ts">
defineProps<{ modelValue: string; label: string; placeholder?: string }>();
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
function update(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).value);
}
</script>
<template>
  <label class="app-search-field"
    ><i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i
    ><input
      type="search"
      :value="modelValue"
      :aria-label="label"
      :placeholder="placeholder || label"
      @input="update"
  /></label>
</template>
<style scoped>
.app-search-field {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 240px;
  min-width: 0;
  max-width: 100%;
  min-height: var(--ui-control-height);
  padding-left: 10px;
  border: 1px solid var(--ui-stroke);
  border-radius: var(--ui-radius);
  background: var(--ui-surface);
  color: var(--ui-muted);
}
.app-search-field i {
  font-size: 13px;
}
.app-search-field input {
  width: 100%;
  min-width: 0;
  padding: 6px 10px 6px 0;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--ui-text);
  font: inherit;
  font-size: var(--ui-control-font-size);
  line-height: 20px;
}
.app-search-field:focus-within {
  outline: 2px solid var(--ui-focus);
  outline-offset: 1px;
}
</style>

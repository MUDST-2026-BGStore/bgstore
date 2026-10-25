<script setup lang="ts">
import { computed, ref } from 'vue';
import { useStoreText } from '../data/store-text';
const props = defineProps<{
  modelValue: string;
  options: { id: string; name: string }[];
  name: string;
  label: string;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
const { t } = useStoreText();
const query = ref('');
const matches = computed(() =>
  props.options.filter((option) =>
    t(option.name)
      .toLocaleLowerCase()
      .includes(query.value.trim().toLocaleLowerCase()),
  ),
);
// Keep the current option in the select while searching. Typing must never clear
// a saved branch or apply a different branch. TODO(branch-api): server-side branch
// search/pagination for large directories; include address/city in real results.
const visible = computed(() =>
  props.options.filter(
    (option) =>
      option.id === props.modelValue || matches.value.includes(option),
  ),
);
</script>
<template>
  <div class="branch-select">
    <label v-if="options.length > 8" class="branch-search">
      <span>{{ t('Find a branch') }}</span>
      <input
        v-model="query"
        type="search"
        :name="`${name}-search`"
        :placeholder="t('Search branches…')"
        autocomplete="off"
      />
    </label>
    <label>
      <span>{{ t(label) }}</span>
      <select
        :name="name"
        :value="modelValue"
        @change="
          emit('update:modelValue', ($event.target as HTMLSelectElement).value)
        "
      >
        <option value="">{{ t('Choose a branch') }}</option>
        <option v-for="option in visible" :key="option.id" :value="option.id">
          {{ t(option.name) }}
        </option>
      </select>
    </label>
    <p v-if="query.trim() && !matches.length" role="status">
      {{ t('No matching branches. Try another name.') }}
    </p>
  </div>
</template>
<style scoped>
.branch-select {
  min-width: 0;
  display: grid;
  gap: 10px;
}
label {
  display: grid;
  gap: 6px;
  min-width: 0;
  font-size: 13px;
  color: #202e27;
}
input,
select {
  width: 100%;
  min-width: 0;
  border: 1px solid #829087;
  border-radius: 3px;
  padding: 10px;
  background: white;
  color: #202e27;
  font-size: 14px;
}
select {
  cursor: pointer;
}
input:focus-visible,
select:focus-visible {
  outline: 2px solid #244c38;
  outline-offset: 3px;
}
p {
  font-size: 12px;
  line-height: 1.5;
  color: #4e5b53;
}
</style>

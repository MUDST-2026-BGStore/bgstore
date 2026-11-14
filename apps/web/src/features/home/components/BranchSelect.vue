<script setup lang="ts">
import { computed, ref } from 'vue';
import { AppSearchField, AppSelect } from '../../../shared/ui';
import { useStoreText } from '../data/store-text';
const props = defineProps<{
  modelValue: string;
  options: { id: string; name: string; address?: string }[];
  name: string;
  label: string;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
const { t } = useStoreText();
const query = ref('');
const matches = computed(() => {
  const needle = query.value.trim().toLocaleLowerCase();
  return props.options.filter(
    (option) =>
      t(option.name).toLocaleLowerCase().includes(needle) ||
      (option.address?.toLocaleLowerCase().includes(needle) ?? false),
  );
});
// Keep the current option in the list while searching. Typing must never clear
// a saved branch or apply a different branch.
// Search is client-side over the cached directory.
const visible = computed(() =>
  props.options.filter(
    (option) =>
      option.id === props.modelValue || matches.value.includes(option),
  ),
);
const selectOptions = computed(() =>
  visible.value.map((option) => ({ value: option.id, label: t(option.name) })),
);
</script>
<template>
  <div class="branch-select">
    <div v-if="options.length > 8" class="field">
      <span aria-hidden="true">{{ t('Find a branch') }}</span>
      <AppSearchField
        v-model="query"
        :label="t('Find a branch')"
        :placeholder="t('Search branches…')"
      />
    </div>
    <div class="field">
      <span aria-hidden="true">{{ t(label) }}</span>
      <AppSelect
        block
        :model-value="modelValue"
        :name="name"
        :label="t(label)"
        :placeholder="t('Choose a branch')"
        :options="selectOptions"
        @update:model-value="emit('update:modelValue', $event)"
      />
    </div>
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
.field {
  display: grid;
  gap: 6px;
  min-width: 0;
  font-size: 13px;
  color: #202e27;
}
.field .app-search-field {
  width: 100%;
}
p {
  font-size: 12px;
  line-height: 1.5;
  color: #4e5b53;
}
</style>

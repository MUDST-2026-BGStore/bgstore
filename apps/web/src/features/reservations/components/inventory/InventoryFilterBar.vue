<script setup lang="ts">
import {
  AppFilterMenu,
  AppSearchField,
  type FilterGroup,
  type FilterValues,
} from '../../../../shared/ui';

defineProps<{
  searchLabel: string;
  searchPlaceholder: string;
  groups: readonly FilterGroup[];
}>();
const search = defineModel<string>('search', { required: true });
const filters = defineModel<FilterValues>('filters', { required: true });
</script>

<template>
  <AppSearchField
    v-model="search"
    :label="searchLabel"
    :placeholder="searchPlaceholder"
  />
  <AppFilterMenu v-model="filters" :groups="groups" />
</template>

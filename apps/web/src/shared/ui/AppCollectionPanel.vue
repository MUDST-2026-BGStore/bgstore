<script setup lang="ts">
import AppPanel from './AppPanel.vue';
import AppMetricCard from './AppMetricCard.vue';
import AppTableToolbar from './AppTableToolbar.vue';
withDefaults(
  defineProps<{
    title: string;
    titleId: string;
    resultLabel: string;
    selectedCount?: number;
    metrics: readonly {
      label: string;
      value: string | number;
      caption?: string;
      tone?: 'neutral' | 'success' | 'warning' | 'danger';
    }[];
  }>(),
  { selectedCount: 0 },
);
const emit = defineEmits<{ clearSelection: [] }>();
</script>
<template>
  <AppPanel :title="title" :title-id="titleId">
    <div
      class="collection-metrics"
      role="group"
      :aria-label="`${title} summary`"
    >
      <AppMetricCard
        v-for="metric in metrics"
        :key="metric.label"
        v-bind="metric"
        appearance="plain"
      />
    </div>
    <div v-if="$slots.navigation" class="collection-navigation">
      <slot name="navigation" />
    </div>
    <AppTableToolbar
      :result-label="resultLabel"
      :selected-count="selectedCount"
      @clear-selection="emit('clearSelection')"
    >
      <slot name="filters" />
      <template v-if="$slots.actions" #actions
        ><slot name="actions"
      /></template>
      <template v-if="$slots.selection" #selection
        ><slot name="selection"
      /></template>
    </AppTableToolbar>
    <slot />
  </AppPanel>
</template>
<style scoped>
.collection-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-bottom: 1px solid var(--ui-divider);
}
.collection-navigation {
  min-width: 0;
  padding-inline: 16px;
  border-bottom: 1px solid var(--ui-divider);
}
@media (max-width: 1000px) {
  .collection-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

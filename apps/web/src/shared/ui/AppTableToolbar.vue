<script setup lang="ts">
import AppToolbar from './AppToolbar.vue';
import AppSelectionBar from './AppSelectionBar.vue';
withDefaults(defineProps<{ resultLabel: string; selectedCount?: number }>(), {
  selectedCount: 0,
});
const emit = defineEmits<{ clearSelection: [] }>();
</script>
<template>
  <AppToolbar
    class="app-table-toolbar"
    role="group"
    aria-label="Table controls"
  >
    <div class="table-filters"><slot /></div>
    <div class="table-commands">
      <div
        class="normal-commands"
        :class="{ concealed: selectedCount > 0 }"
        :aria-hidden="selectedCount > 0"
        :inert="selectedCount > 0 || undefined"
      >
        <span role="status" class="result-label">{{ resultLabel }}</span
        ><slot name="actions" />
      </div>
      <AppSelectionBar
        v-if="$slots.selection"
        :count="selectedCount"
        @clear="emit('clearSelection')"
        ><slot name="selection"
      /></AppSelectionBar>
    </div>
  </AppToolbar>
</template>
<style scoped>
.app-table-toolbar {
  flex-wrap: nowrap;
}
.table-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  flex: 1 1 420px;
  min-width: 0;
}
.table-commands {
  display: grid;
  margin-left: auto;
  max-width: 100%;
  min-width: 0;
}
.table-commands > * {
  grid-area: 1 / 1;
}
.normal-commands {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  min-height: 32px;
}
.result-label {
  color: var(--ui-muted);
  font-size: 12px;
  white-space: nowrap;
}
.concealed {
  visibility: hidden;
}
@media (max-width: 700px) {
  .app-table-toolbar {
    flex-wrap: wrap;
  }
  .table-filters {
    flex-basis: 100%;
  }
  .table-commands {
    width: 100%;
  }
}
</style>

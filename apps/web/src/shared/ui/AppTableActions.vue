<script setup lang="ts">
defineProps<{ wrap?: boolean }>();
</script>
<template>
  <td class="app-table-actions" :class="{ wrap }" @click.stop>
    <div class="app-row-actions"><slot /></div>
  </td>
</template>
<style scoped>
.app-table-actions {
  width: 1%;
  white-space: nowrap;
}
.app-row-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}
.wrap {
  white-space: normal;
}
.wrap > .app-row-actions {
  flex-wrap: wrap;
  min-width: 180px;
  max-width: 220px;
}
</style>

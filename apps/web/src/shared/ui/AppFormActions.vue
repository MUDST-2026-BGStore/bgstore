<template>
  <footer class="app-form-actions"><slot /></footer>
</template>
<style scoped>
.app-form-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  flex-shrink: 0;
  gap: 8px;
  padding: 16px 24px;
  border-top: 1px solid var(--ui-divider);
  background: var(--ui-surface-subtle);
}
@media (max-width: 520px) {
  .app-form-actions {
    padding: 12px 16px;
  }
}
</style>

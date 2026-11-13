<template>
  <div class="app-toolbar"><slot /></div>
</template>
<style scoped>
.app-toolbar {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px 16px;
  min-width: 0;
  padding: 12px 16px;
  border-bottom: 1px solid var(--ui-divider);
  background: var(--ui-surface);
}
@media (max-width: 700px) {
  .app-toolbar {
    padding: 10px 12px;
  }
}
</style>

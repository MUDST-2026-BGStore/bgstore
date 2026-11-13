<template>
  <button type="button" class="app-record-link"><slot /></button>
</template>
<style scoped>
.app-record-link {
  display: inline;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ui-brand);
  font: inherit;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}
.app-record-link:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}
.app-record-link:focus-visible {
  outline: 2px solid var(--ui-focus);
  outline-offset: 3px;
  text-decoration: underline;
}
</style>

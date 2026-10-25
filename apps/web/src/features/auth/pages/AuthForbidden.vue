<script setup lang="ts">
import { RouterLink } from 'vue-router';
</script>
<template>
  <main>
    <h1>Access denied</h1>
    <p>Your account does not have the role required for this page.</p>
    <RouterLink :to="{ name: 'WorkspaceIndex' }"
      >Open your workspace</RouterLink
    >
  </main>
</template>

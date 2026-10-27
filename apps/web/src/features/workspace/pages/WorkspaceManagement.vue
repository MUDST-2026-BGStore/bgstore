<script setup lang="ts">
// TODO(branch-management-api): Landing page only; implement branch-scoped server
// authorization and branch/table, stock, reservation and rental workflows before
// adding operational controls. Generated clients currently include backend stubs.
import { RouterLink } from 'vue-router';
</script>
<template>
  <main>
    <h1>Branch management</h1>
    <p>
      This is the manager landing page. Branch operations will be implemented
      through server-authorized modules.
    </p>
    <RouterLink :to="{ name: 'WorkspaceIndex' }">Open workspace</RouterLink>
  </main>
</template>

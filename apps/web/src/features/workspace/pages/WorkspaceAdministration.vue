<script setup lang="ts">
// TODO(administration-api): Only identity reads below are implemented here. Add
// branch and staff administration with server-enforced permissions; role display
// alone does not implement administration or authorize future mutations.
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import { useWhoami } from '@theparlor/api-client';

const { data: identity, error, isPending } = useWhoami();
const status = computed(() => {
  if (isPending.value) return 'Checking server profile…';
  if (error.value) return error.value.message;
  return identity.value?.roles.includes('admin')
    ? 'Administrator role confirmed by the server profile.'
    : 'Administrator role required.';
});
</script>
<template>
  <main>
    <h1>Administration</h1>
    <p data-testid="admin-status">{{ status }}</p>
    <p>Branch and staff administration features will be added here.</p>
    <RouterLink :to="{ name: 'WorkspaceIndex' }">Open workspace</RouterLink>
  </main>
</template>

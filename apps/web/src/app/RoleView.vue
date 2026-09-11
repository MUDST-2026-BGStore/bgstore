<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query';
import { computed, markRaw, type Component } from 'vue';
import {
  currentUserQueryOptions,
  hasStaffAccess,
} from '../queries/current-user';

/**
 * Renders the staff or the client screen for one URL, so `/games` is the
 * inventory for someone who runs the store and the catalogue for a guest.
 *
 * App.vue only renders routes once the current user has loaded, so this reads
 * the cached answer rather than waiting on a request of its own.
 */
const props = defineProps<{ staff: Component; client: Component }>();

const currentUser = useQuery(currentUserQueryOptions());

const screen = computed(() => {
  const user = currentUser.data.value;
  if (!user) {
    // Public role-aware routes still need a guest screen. Protected routes are
    // withheld by App.vue before this component is rendered.
    return markRaw(props.client);
  }

  // Components are definitions, not reactive state. Marking the selected
  // definition raw avoids Vue wrapping it when it crosses the prop boundary.
  return markRaw(hasStaffAccess(user.roles) ? props.staff : props.client);
});
</script>

<template>
  <component :is="screen" v-if="screen" />
</template>

<script setup lang="ts">
import { watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useQuery } from '@tanstack/vue-query';
import {
  authStartHref,
  currentUserQueryOptions,
} from '../queries/current-user';

const route = useRoute();
const router = useRouter();

function safeReturnTo(value: unknown): string {
  return typeof value === 'string' &&
    value.startsWith('/') &&
    !value.startsWith('//')
    ? value
    : '/';
}

const currentUser = useQuery(currentUserQueryOptions());

watch(
  () => [currentUser.data.value, currentUser.isError.value] as const,
  ([user, queryFailed]) => {
    // A signed-in visitor opening /login belongs back in the app; the router
    // guard's watcher cannot win against this view's hand-off, so decide here.
    if (user) {
      void router.replace('/');
      return;
    }
    if (queryFailed || user === null) {
      window.location.replace(
        authStartHref(safeReturnTo(route.query.redirect)),
      );
    }
  },
  { immediate: true },
);
</script>

<template>
  <span class="sr-only">Redirecting to sign in</span>
</template>

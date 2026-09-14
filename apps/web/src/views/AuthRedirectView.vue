<script setup lang="ts">
import { onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { authStartHref } from '../queries/current-user';

const route = useRoute();

function safeReturnTo(value: unknown): string {
  return typeof value === 'string' &&
    value.startsWith('/') &&
    !value.startsWith('//')
    ? value
    : '/';
}

onMounted(() => {
  window.location.replace(authStartHref(safeReturnTo(route.query.redirect)));
});
</script>

<template>
  <span class="sr-only">Redirecting to sign in</span>
</template>

<script setup lang="ts">
defineProps<{
  message: string;
  /** Test hook, so a page's loading state can be addressed by name. */
  testid?: string;
}>();
</script>

<template>
  <div
    class="rounded-md border border-line bg-surface p-8 text-ink-muted"
    :data-testid="testid"
    aria-live="polite"
  >
    {{ message }}
  </div>
</template>

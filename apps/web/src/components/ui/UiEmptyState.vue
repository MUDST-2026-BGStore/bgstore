<script setup lang="ts">
defineProps<{ message: string }>();
</script>

<template>
  <div class="rounded-md border border-line bg-surface p-8 text-ink-muted">
    <slot>{{ message }}</slot>
  </div>
</template>

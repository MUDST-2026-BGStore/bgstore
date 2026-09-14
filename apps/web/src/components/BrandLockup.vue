<script setup lang="ts">
withDefaults(defineProps<{ tone?: 'dark' | 'light' }>(), {
  tone: 'dark',
});
</script>

<template>
  <span class="brand-lockup" :class="`brand-lockup--${tone}`">
    BG<span aria-hidden="true">•</span>
  </span>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    /** The screens a client can navigate to so far. */
    active?: 'home' | 'reserve' | 'game' | 'branch' | 'history' | 'profile';
    guest?: boolean;
  }>(),
  { active: 'game', guest: false },
);
</script>

<template>
  <div class="flex min-h-screen w-full flex-col items-start bg-canvas">
    <div class="flex w-full flex-1 flex-col items-start">
      <slot />
    </div>
  </div>
</template>

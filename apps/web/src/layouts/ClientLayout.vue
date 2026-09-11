<script setup lang="ts">
import ClientHeader from '../components/ClientHeader.vue';

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
    <ClientHeader :active="active" :guest="guest" />
    <div class="flex w-full flex-1 flex-col items-start">
      <slot />
    </div>
  </div>
</template>

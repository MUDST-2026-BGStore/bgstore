<script setup lang="ts">
import ClientHeader from '../components/ClientHeader.vue';
</script>

<template>
  <div class="flex min-h-screen w-full flex-col items-start bg-canvas">
    <ClientHeader active="game" />
    <div class="flex w-full flex-1 flex-col items-start">
      <slot />
    </div>
  </div>
</template>

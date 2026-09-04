<script setup lang="ts">
import OwnerHeader from '../components/OwnerHeader.vue';
</script>

<template>
  <div class="flex min-h-screen w-full flex-col items-start bg-canvas">
    <OwnerHeader active="games" />
    <div class="flex w-full flex-1 flex-col items-start">
      <slot />
    </div>
  </div>
</template>

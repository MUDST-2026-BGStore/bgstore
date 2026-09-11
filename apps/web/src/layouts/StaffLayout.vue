<script setup lang="ts">
import StaffHeader from '../components/StaffHeader.vue';

defineProps<{ active: InstanceType<typeof StaffHeader>['$props']['active'] }>();
</script>

<template>
  <div class="flex min-h-screen w-full flex-col items-start bg-canvas">
    <StaffHeader :active="active" />
    <div class="flex w-full flex-1 flex-col items-start">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import StaffSidebar, {
  type StaffSection,
} from '../components/StaffSidebar.vue';

withDefaults(defineProps<{ active?: StaffSection }>(), { active: 'games' });
</script>

<template>
  <div class="staff-workspace refresh-owner-layout">
    <StaffSidebar :active="active" />
    <div class="owner-layout-content">
      <slot />
    </div>
  </div>
</template>

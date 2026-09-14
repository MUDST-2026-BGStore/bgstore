<script setup lang="ts">
import alarm from '../../assets/icons/alarm.svg';
import mapPin from '../../assets/icons/map-pin.svg';
import type { Branch } from '../../generated/api/types.gen';
import { RouterLink } from 'vue-router';

defineProps<{
  branch: Branch;
  index: number;
  hint: string;
  addressLabel: string;
  openingHoursLabel: string;
  bookLabel: string;
}>();
</script>

<template>
  <article data-testid="home-branch" class="client-branch-card">
    <div
      class="client-branch-art"
      :class="`client-branch-art--${(index % 3) + 1}`"
      aria-hidden="true"
    >
      <span class="client-branch-die">{{ index + 1 }}</span>
      <span class="client-branch-token">✦</span>
    </div>
    <div class="client-branch-details">
      <h3>{{ branch.name }}</h3>
      <address
        v-if="branch.address"
        class="client-branch-meta"
        :aria-label="addressLabel"
      >
        <img :src="mapPin" alt="" width="18" height="18" />
        <span>{{ branch.address }}</span>
      </address>
      <p v-if="branch.opensAt && branch.closesAt" class="client-branch-meta">
        <img :src="alarm" :alt="openingHoursLabel" width="18" height="18" />
        <span>{{ branch.opensAt }}–{{ branch.closesAt }}</span>
      </p>
      <p class="client-branch-hint">{{ hint }}</p>
      <RouterLink class="client-branch-action" to="/reservations/new">
        {{ bookLabel }}
      </RouterLink>
    </div>
  </article>
</template>

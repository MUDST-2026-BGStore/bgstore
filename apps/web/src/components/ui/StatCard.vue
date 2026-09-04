<script setup lang="ts">
import { computed } from 'vue';
import type { StatTone } from './types';

const props = defineProps<{
  tone: StatTone;
  icon: string;
  label: string;
  value: string;
}>();

const toneClass = computed(
  () =>
    ({
      neutral: 'bg-surface border-line',
      success: 'bg-success-bg border-success-border',
      warning: 'bg-warning-bg border-warning-border',
    })[props.tone],
);

const labelClass = computed(
  () =>
    ({
      neutral: 'text-ink-secondary',
      success: 'text-success-fg',
      warning: 'text-warning-fg',
    })[props.tone],
);
</script>

<template>
  <div
    class="flex min-w-0 flex-1 flex-col items-start gap-3 rounded-lg border px-6 py-5"
    :class="toneClass"
  >
    <div class="flex w-full items-center gap-2">
      <img
        :src="icon"
        alt=""
        class="block size-[18px] shrink-0"
        width="18"
        height="18"
      />
      <p
        class="min-w-0 flex-1 text-[14px] leading-[22px] font-medium"
        :class="labelClass"
      >
        {{ label }}
      </p>
    </div>
    <p
      class="w-full text-[32px] leading-[40px] font-semibold tracking-[-0.16px] text-ink"
    >
      {{ value }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { BadgeTone } from './types';

const props = withDefaults(defineProps<{ tone?: BadgeTone }>(), {
  tone: 'success',
});

const toneClass = computed(
  () =>
    ({
      success: 'bg-success-bg border-success-border text-success-fg',
      warning: 'bg-warning-bg border-warning-border text-warning-fg',
      neutral: 'bg-neutral-bg border-neutral-border text-neutral-fg',
    })[props.tone],
);
</script>

<template>
  <span
    class="inline-flex items-center justify-center gap-1 rounded-full border px-2 py-1 text-[12px] leading-[18px] whitespace-nowrap"
    :class="toneClass"
  >
    <slot />
  </span>
</template>

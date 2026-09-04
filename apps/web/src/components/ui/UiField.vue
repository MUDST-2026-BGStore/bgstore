<script setup lang="ts">
defineProps<{ label: string; inputId: string; error?: string }>();
</script>

<template>
  <div class="flex w-full min-w-0 flex-1 flex-col items-start gap-1.5">
    <label
      :for="inputId"
      class="text-[13px] leading-[20px] font-semibold text-ink"
    >
      {{ label }}
    </label>
    <slot />
    <p
      v-if="error"
      :data-testid="inputId + '-error'"
      class="text-[12px] leading-[18px] text-danger-fg"
    >
      {{ error }}
    </p>
  </div>
</template>

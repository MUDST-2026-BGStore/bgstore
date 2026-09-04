<script setup lang="ts">
defineProps<{ title: string }>();
</script>

<template>
  <section
    class="flex w-full flex-col items-start gap-4 rounded-card border border-line bg-surface p-5"
  >
    <header class="flex w-full flex-col items-start gap-0.5">
      <h2 class="text-[15px] leading-[23px] font-semibold text-ink">
        {{ title }}
      </h2>
      <p
        v-if="$slots.subtitle"
        class="w-full text-[12px] leading-[18px] text-ink-muted"
      >
        <slot name="subtitle" />
      </p>
    </header>
    <slot />
  </section>
</template>

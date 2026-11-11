<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';

withDefaults(
  defineProps<{
    /** Show the 3-card fan crest above the top border */
    showCards?: boolean;
    /** Max width class for the card container */
    maxWidthClass?: string;
    ariaLabel?: string;
  }>(),
  {
    showCards: true,
    maxWidthClass: 'max-w-[440px]',
    ariaLabel: 'Card content',
  },
);

const opened = ref(false);
let frame = 0;

function stopAnimation() {
  cancelAnimationFrame(frame);
}

function playEntrance() {
  stopAnimation();
  opened.value = false;
  frame = requestAnimationFrame(() => {
    frame = requestAnimationFrame(() => {
      opened.value = true;
    });
  });
}

onMounted(playEntrance);
onUnmounted(stopAnimation);
</script>

<template>
  <div
    class="parlor-card relative flex w-full flex-col items-center"
    :class="[maxWidthClass, { 'has-crest': showCards }]"
  >
    <!-- Paper Card Surface -->
    <section
      class="form-card relative z-20 w-full bg-[#f5ede0] p-2.5 text-[#1b221d] shadow-[0_28px_65px_rgba(0,0,0,0.7)]"
      :class="{ 'is-opened': opened }"
      :aria-label="ariaLabel"
    >
      <div class="border border-[#c9b99b] px-6 py-8 sm:px-9 sm:py-9">
        <slot />
      </div>
    </section>
  </div>
</template>

<style scoped>
.parlor-card.has-crest::before {
  position: absolute;
  top: -104px;
  left: 50%;
  width: 260px;
  height: 104px;
  transform: translateX(-50%);
  background: url('../../../../../deploy/keycloak/themes/theparlor/login/resources/img/card-fan.svg')
    center top / 260px 140px no-repeat;
  pointer-events: none;
  content: '';
}

.form-card {
  opacity: 0;
  transform: translateY(16px) scale(0.98);
  transition:
    transform 500ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 280ms ease;
}

.form-card.is-opened {
  opacity: 1;
  transform: translateY(0) scale(1);
}

@media (prefers-reduced-motion: reduce) {
  .form-card {
    transition-duration: 0.01ms;
  }
}
</style>

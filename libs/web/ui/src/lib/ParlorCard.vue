<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';

const props = withDefaults(
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
const dealt = ref(false);
let timer: ReturnType<typeof setTimeout> | undefined;
let frame = 0;

function stopAnimation() {
  if (timer) clearTimeout(timer);
  cancelAnimationFrame(frame);
}

function playEntrance() {
  stopAnimation();
  opened.value = false;
  dealt.value = false;
  frame = requestAnimationFrame(() => {
    frame = requestAnimationFrame(() => {
      opened.value = true;
      if (props.showCards) {
        timer = setTimeout(() => {
          dealt.value = true;
        }, 260);
      }
    });
  });
}

onMounted(playEntrance);
onUnmounted(stopAnimation);
</script>

<template>
  <div class="flex w-full flex-col items-center" :class="maxWidthClass">
    <!-- Optional 3-Card Fan Crest -->
    <div
      v-if="showCards"
      aria-hidden="true"
      class="relative mb-[-26px] flex h-[120px] w-[260px] items-center justify-center select-none"
    >
      <!-- Left Card: Ace of Spades -->
      <div
        class="mini-card absolute flex h-[118px] w-[84px] flex-col justify-between rounded-[5px] border border-[#d6c7ac] bg-[#f3ead8] p-2 text-[#18261f] shadow-[0_10px_25px_rgba(0,0,0,0.5)]"
        :class="{ 'is-dealt': dealt }"
        style="--tx: -54px; --ty: 8px; --rot: -14deg; --d: 0ms"
      >
        <div class="leading-none">
          <div class="font-serif text-sm font-bold">A</div>
          <div class="text-[10px]">♠</div>
        </div>
        <div class="self-center font-serif text-2xl">♠</div>
        <div class="rotate-180 leading-none">
          <div class="font-serif text-sm font-bold">A</div>
          <div class="text-[10px]">♠</div>
        </div>
      </div>

      <!-- Center Card: House Back -->
      <div
        class="mini-card z-10 flex h-[118px] w-[84px] flex-col items-center justify-between rounded-[5px] border border-[#d7bf8e] bg-[#183226] p-1.5 text-[#dfc48d] shadow-[0_14px_30px_rgba(0,0,0,0.65)]"
        :class="{ 'is-dealt': dealt }"
        style="--tx: 0px; --ty: -6px; --rot: 0deg; --d: 80ms"
      >
        <div
          class="flex h-full w-full items-center justify-center border border-[#c6ad7a]/45"
        >
          <span
            class="grid size-9 place-items-center rounded-full border border-[#c6ad7a]/50 font-editorial text-xl italic"
          >
            P
          </span>
        </div>
      </div>

      <!-- Right Card: King of Diamonds -->
      <div
        class="mini-card absolute flex h-[118px] w-[84px] flex-col justify-between rounded-[5px] border border-[#d6c7ac] bg-[#f3ead8] p-2 text-[#8c2d23] shadow-[0_10px_25px_rgba(0,0,0,0.5)]"
        :class="{ 'is-dealt': dealt }"
        style="--tx: 54px; --ty: 8px; --rot: 14deg; --d: 160ms"
      >
        <div class="leading-none">
          <div class="font-serif text-sm font-bold">K</div>
          <div class="text-[10px]">♦</div>
        </div>
        <div class="self-center font-serif text-2xl">♦</div>
        <div class="rotate-180 leading-none">
          <div class="font-serif text-sm font-bold">K</div>
          <div class="text-[10px]">♦</div>
        </div>
      </div>
    </div>

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
.font-editorial {
  font-family: 'Instrument Serif', Georgia, serif;
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

.mini-card {
  opacity: 0;
  transform: translateY(30px) scale(0.9);
  transition:
    transform 650ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 250ms ease;
  transition-delay: 0ms;
}

.mini-card.is-dealt {
  opacity: 1;
  transform: translate(var(--tx), var(--ty)) rotate(var(--rot));
  transition-delay: var(--d);
}

@media (prefers-reduced-motion: reduce) {
  .form-card,
  .mini-card {
    transition-duration: 0.01ms;
    transition-delay: 0ms;
  }
}
</style>

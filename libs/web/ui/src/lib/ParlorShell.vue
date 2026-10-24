<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue';

const props = withDefaults(
  defineProps<{
    /** Center content vertically (ideal for onboarding/auth screens) */
    centered?: boolean;
    /** Keep navigation visible while the document scrolls. */
    stickyHeader?: boolean;
    /** Optional storefront destination for the centered wordmark. */
    brandHref?: string;
  }>(),
  {
    centered: false,
    stickyHeader: false,
    brandHref: undefined,
  },
);
const root = ref<HTMLElement>();
const header = ref<HTMLElement>();
let observer: ResizeObserver | undefined;
onMounted(() => {
  if (!props.stickyHeader || !header.value) return;
  const updateHeight = () =>
    root.value?.style.setProperty(
      '--parlor-header-height',
      `${header.value?.getBoundingClientRect().height ?? 0}px`,
    );
  updateHeight();
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(updateHeight);
    observer.observe(header.value);
  }
});
onBeforeUnmount(() => observer?.disconnect());
</script>

<template>
  <main
    ref="root"
    :class="{ 'sticky-shell': stickyHeader }"
    class="parlor-root relative isolate flex min-h-svh flex-col overflow-hidden bg-[#0e1d16] text-[#efe5d0] selection:bg-[#c8aa6e] selection:text-[#0e1d16]"
  >
    <link rel="preconnect" href="https://fonts.googleapis.com" />
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin="" />
    <link
      href="https://fonts.googleapis.com/css2?family=Instrument+Serif:ital@0;1&family=Plus+Jakarta+Sans:wght@400;500;600&display=swap"
      rel="stylesheet"
    />

    <!-- Subtle felt texture -->
    <div
      aria-hidden="true"
      class="felt-noise pointer-events-none absolute inset-0 opacity-[0.04]"
    />

    <!-- Outer brass frame -->
    <div
      class="relative z-10 m-3 flex min-h-[calc(100svh-1.5rem)] flex-col border border-[#c6ad7a]/20 sm:m-5 sm:min-h-[calc(100svh-2.5rem)]"
    >
      <!-- 3-Column Header: Brand stays locked to dead-center -->
      <header
        ref="header"
        class="parlor-header grid grid-cols-3 items-center border-b border-[#c6ad7a]/20 px-5 py-4 sm:px-8"
      >
        <!-- Left Slot: Nav links on normal pages, or step title on onboarding -->
        <div
          class="flex items-center gap-6 text-[11px] uppercase tracking-[0.18em] text-[#a99978]"
        >
          <slot name="header-left" />
        </div>

        <!-- Center Brand Mark -->
        <component
          :is="brandHref ? 'a' : 'div'"
          :href="brandHref"
          :aria-label="brandHref ? 'The Parlor' : undefined"
          class="flex items-center justify-center gap-2 text-[#efe5d0] focus-visible:outline-2 focus-visible:outline-[#c6ad7a] focus-visible:outline-offset-4"
        >
          <span class="text-xs text-[#c6ad7a]" aria-hidden="true">♠</span>
          <span class="text-xs font-semibold uppercase tracking-[0.22em]"
            >The Parlor</span
          >
          <span class="text-xs text-[#c6ad7a]" aria-hidden="true">♦</span>
        </component>

        <!-- Right Slot: User menu on normal pages, or language/sign-out on onboarding -->
        <div class="flex items-center justify-end gap-4">
          <slot name="header-right" />
        </div>
      </header>

      <!-- Main Content Stage -->
      <div
        class="flex flex-1 flex-col px-4 py-10 sm:px-8"
        :class="{ 'my-auto items-center justify-center': centered }"
      >
        <slot />
      </div>
    </div>
  </main>
</template>

<style scoped>
.parlor-root {
  font-family:
    'Plus Jakarta Sans',
    -apple-system,
    BlinkMacSystemFont,
    sans-serif;
  background-image: radial-gradient(
    ellipse at 50% 35%,
    #193528 0%,
    #0e1d16 62%,
    #08120d 100%
  );
}

/* clip preserves the document as the scroll container; hidden would trap sticky. */
.sticky-shell {
  overflow: clip;
}
.sticky-shell .parlor-header {
  position: sticky;
  top: 0;
  z-index: 40;
  background: #0e1d16;
}

.felt-noise {
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
}
</style>

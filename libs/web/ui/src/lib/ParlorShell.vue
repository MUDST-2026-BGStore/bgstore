<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue';

const props = withDefaults(
  defineProps<{
    /** Center content vertically (ideal for onboarding/auth screens) */
    centered?: boolean;
    /** Use the full-width header and card spacing of the sign-in screens. */
    authLayout?: boolean;
    /** Keep navigation visible while the document scrolls. */
    stickyHeader?: boolean;
    /** Optional storefront destination for the centered wordmark. */
    brandHref?: string;
  }>(),
  {
    centered: false,
    authLayout: false,
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
    :class="{ 'sticky-shell': stickyHeader, 'auth-shell': authLayout }"
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
      class="parlor-frame relative z-10 m-3 flex min-h-[calc(100svh-1.5rem)] flex-col border border-[#c6ad7a]/20 sm:m-5 sm:min-h-[calc(100svh-2.5rem)]"
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
          lang="en"
          class="parlor-brand flex items-center justify-center gap-2 text-[#efe5d0] focus-visible:outline-2 focus-visible:outline-[#c6ad7a] focus-visible:outline-offset-4"
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
        class="parlor-stage flex flex-1 flex-col px-4 py-10 sm:px-8"
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

.auth-shell {
  overflow: clip;
  background-image: radial-gradient(
    ellipse at 50% 35%,
    #193528 0%,
    #0e1d16 62%,
    #08120d 100%
  );
}

.auth-shell .felt-noise {
  display: none;
}

.auth-shell .parlor-frame {
  isolation: isolate;
  min-height: 100svh;
  margin: 0;
  border: 0;
}

.auth-shell .parlor-frame::before {
  position: absolute;
  z-index: -1;
  inset: 86px 20px 20px;
  border: 1px solid #c6ad7a33;
  pointer-events: none;
  content: '';
}

.auth-shell .parlor-header {
  position: sticky;
  top: 0;
  z-index: 40;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  height: 66px;
  padding: 0 max(24px, calc((100vw - 1440px) / 2));
  border-bottom: 2px solid #c6ad7a;
  background: #203c30;
  color: #fff3dc;
}

.parlor-brand {
  gap: 10px;
  color: #fff3dc;
  font:
    600 13px/1.6 'Plus Jakarta Sans',
    Arial,
    sans-serif;
}

.parlor-brand span {
  font: inherit;
  letter-spacing: 0.18em;
}

.parlor-brand span[aria-hidden='true'] {
  color: #ddc18c;
  font-size: 15px;
  letter-spacing: normal;
}

.auth-shell .parlor-stage {
  padding: 128px 32px 64px;
}

@media (max-width: 600px) {
  .auth-shell .parlor-header {
    padding-inline: clamp(4px, calc(10vw - 24px), 8px);
  }

  .auth-shell .parlor-frame::before {
    inset: 78px 12px 12px;
  }

  .auth-shell .parlor-stage {
    padding-inline: 24px;
    padding-bottom: 40px;
  }
}

@media (max-width: 700px) {
  .parlor-root:not(.auth-shell) .parlor-brand {
    gap: 4px;
    font-size: 11px;
  }

  .parlor-root:not(.auth-shell) .parlor-brand span {
    letter-spacing: 0.08em;
  }

  .parlor-root:not(.auth-shell) .parlor-brand span[aria-hidden='true'] {
    font-size: 10px;
    letter-spacing: normal;
  }
}

@media (max-width: 300px) {
  .auth-shell .parlor-brand {
    gap: 4px;
    font-size: 11px;
  }

  .auth-shell .parlor-brand span {
    letter-spacing: 0.14em;
  }

  .auth-shell .parlor-brand span[aria-hidden='true'] {
    font-size: 12px;
  }
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

<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query';
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import ClientLayout from '../layouts/ClientLayout.vue';
import UiButton from '../components/ui/UiButton.vue';
import alarm from '../assets/icons/alarm.svg';
import chevronLeft from '../assets/icons/chevron-left.svg';
import chevronRight from '../assets/icons/chevron-right.svg';
import mapPin from '../assets/icons/map-pin.svg';
import { currentUserQueryOptions } from '../queries/current-user';
import { branchesQueryOptions } from '../queries/games';
import { wrapIndex } from '../pages/games/catalogue';
import type { Branch } from '../generated/api/types.gen';

/** The storefront a guest or a client lands on: what is on, and where to go. */
const { t } = useI18n();

// App.vue has already asked who this is, so this reads the cached answer.
const currentUser = useQuery(currentUserQueryOptions());
const guest = computed(() => currentUser.data.value === null);

// The design reserves the hero for three slides but gives them no content yet,
// so the controls only move between empty panels until there is a source.
const heroSlides = 3;
const slide = ref(0);

function showSlide(step: number) {
  slide.value = wrapIndex(slide.value + step, heroSlides);
}

const branches = useQuery(branchesQueryOptions());

// The design features three branches. "View all" has no branch screen to go
// to yet, so it reveals the rest of the directory in place.
const featuredCount = 3;
const showAll = ref(false);
const allBranches = computed(() => branches.data.value ?? []);
const visibleBranches = computed(() =>
  showAll.value ? allBranches.value : allBranches.value.slice(0, featuredCount),
);

function hoursOf(branch: Branch): string {
  return branch.opensAt && branch.closesAt
    ? t('home.branches.hours', {
        opens: branch.opensAt,
        closes: branch.closesAt,
      })
    : '';
}
</script>

<template>
  <ClientLayout active="home" :guest="guest">
    <div
      class="flex w-full flex-col items-start gap-10 px-8 pt-8 pb-14 lg:px-16"
    >
      <h1 class="sr-only">{{ t('app.title') }}</h1>

      <section
        :aria-label="t('home.hero.label')"
        aria-roledescription="carousel"
        class="relative h-[420px] w-full shrink-0 overflow-hidden rounded-[20px] border border-line bg-surface-sunken"
      >
        <button
          type="button"
          :aria-label="t('home.hero.previous')"
          class="absolute top-1/2 left-6 flex size-10 -translate-y-1/2 items-center justify-center rounded-full border border-line bg-surface drop-shadow-[0px_2px_3px_rgba(71,61,61,0.16)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          @click="showSlide(-1)"
        >
          <img
            :src="chevronLeft"
            alt=""
            class="block size-6"
            width="24"
            height="24"
          />
        </button>
        <button
          type="button"
          :aria-label="t('home.hero.next')"
          class="absolute top-1/2 right-6 flex size-10 -translate-y-1/2 items-center justify-center rounded-full border border-line bg-surface drop-shadow-[0px_2px_3px_rgba(71,61,61,0.16)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          @click="showSlide(1)"
        >
          <img
            :src="chevronRight"
            alt=""
            class="block size-6"
            width="24"
            height="24"
          />
        </button>
        <!-- Each dot is 8px; the padding widens the hit area without moving it. -->
        <div
          class="absolute bottom-8 left-1/2 flex -translate-x-1/2 items-center"
        >
          <button
            v-for="index in heroSlides"
            :key="index"
            type="button"
            data-testid="hero-dot"
            :aria-label="
              t('home.hero.slide', { index: index, total: heroSlides })
            "
            :aria-current="slide === index - 1 ? 'true' : undefined"
            class="flex p-1 focus-visible:outline-2 focus-visible:outline-primary"
            @click="slide = index - 1"
          >
            <span
              class="block size-2 rounded-full"
              :class="slide === index - 1 ? 'bg-primary' : 'bg-line-strong/50'"
            />
          </button>
        </div>
      </section>

      <section
        aria-labelledby="home-branches-title"
        class="flex w-full flex-col items-start gap-10"
      >
        <div class="flex w-full items-center gap-4">
          <h2
            id="home-branches-title"
            class="min-w-0 flex-1 text-[24px] leading-[1.35] font-bold text-ink"
          >
            {{ t('home.branches.title') }}
          </h2>
          <UiButton
            v-if="allBranches.length > featuredCount"
            variant="ghost"
            class="w-[120px]"
            :aria-expanded="showAll"
            @click="showAll = !showAll"
          >
            {{
              showAll
                ? t('home.branches.showFewer')
                : t('home.branches.viewAll')
            }}
          </UiButton>
        </div>

        <p
          v-if="branches.isPending.value"
          data-testid="home-branches-loading"
          class="text-[14px] text-ink-muted"
        >
          {{ t('home.branches.loading') }}
        </p>

        <div
          v-else-if="branches.isError.value"
          class="flex flex-col items-start gap-3"
        >
          <p
            data-testid="home-branches-error"
            role="alert"
            class="text-[14px] text-danger-fg"
          >
            {{ t('home.branches.loadFailed') }}
          </p>
          <UiButton variant="outline" size="sm" @click="branches.refetch()">
            {{ t('home.branches.retry') }}
          </UiButton>
        </div>

        <p
          v-else-if="allBranches.length === 0"
          data-testid="home-branches-empty"
          class="text-[14px] text-ink-muted"
        >
          {{ t('home.branches.empty') }}
        </p>

        <ul
          v-else
          class="grid w-full grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3"
        >
          <li v-for="branch in visibleBranches" :key="branch.id" class="flex">
            <article
              data-testid="home-branch"
              class="flex w-full flex-col overflow-hidden rounded-[16px] border border-line bg-surface shadow-[0px_2px_8px_0px_rgba(71,61,61,0.06)]"
            >
              <div class="h-[168px] w-full shrink-0 bg-surface-sunken" />
              <div
                class="flex w-full flex-1 flex-col gap-2.5 px-[18px] pt-4 pb-[18px]"
              >
                <h3 class="text-[18px] leading-[1.4] font-semibold text-ink">
                  {{ branch.name }}
                </h3>
                <address
                  v-if="branch.address"
                  class="flex items-start gap-2 text-[13px] leading-[1.55] text-ink-secondary not-italic"
                >
                  <img
                    :src="mapPin"
                    :alt="t('home.branches.address')"
                    class="block size-[18px] shrink-0"
                    width="18"
                    height="18"
                  />
                  <span class="min-w-0 flex-1">{{ branch.address }}</span>
                </address>
                <p
                  v-if="hoursOf(branch)"
                  class="flex items-start gap-2 text-[13px] leading-[1.55] text-ink-secondary"
                >
                  <img
                    :src="alarm"
                    :alt="t('home.branches.openingHours')"
                    class="block size-[18px] shrink-0"
                    width="18"
                    height="18"
                  />
                  <span class="min-w-0 flex-1">{{ hoursOf(branch) }}</span>
                </p>
                <!-- Reservations have no screen yet, so this is not a link. -->
                <span
                  aria-disabled="true"
                  class="mt-auto inline-flex h-10 w-full items-center justify-center rounded-md border border-secondary bg-secondary px-4 text-center text-[13px] leading-[20px] font-medium whitespace-nowrap text-secondary-fg"
                >
                  {{ t('home.branches.bookHere') }}
                </span>
              </div>
            </article>
          </li>
        </ul>
      </section>
    </div>
  </ClientLayout>
</template>

<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query';
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import BranchCard from '../components/home/BranchCard.vue';
import UiButton from '../components/ui/UiButton.vue';
import ClientLayout from '../layouts/ClientLayout.vue';
import { currentUserQueryOptions } from '../queries/current-user';
import { branchesQueryOptions } from '../queries/games';

const { t } = useI18n();
const currentUser = useQuery(currentUserQueryOptions());
const guest = computed(() => currentUser.data.value === null);
const branches = useQuery(branchesQueryOptions());

const heroSlides = 3;
const slide = ref(0);
const featuredCount = 3;
const showAll = ref(false);

const featureCards = computed(() => [
  {
    title: t('home.editorial.featureStrategy'),
    label: t('home.editorial.featureStrategyLabel'),
    tone: 'teal',
  },
  {
    title: t('home.editorial.featureParty'),
    label: t('home.editorial.featurePartyLabel'),
    tone: 'coral',
  },
  {
    title: t('home.editorial.featureFamily'),
    label: t('home.editorial.featureFamilyLabel'),
    tone: 'warm',
  },
]);

const allBranches = computed(() => branches.data.value ?? []);
const visibleBranches = computed(() =>
  showAll.value ? allBranches.value : allBranches.value.slice(0, featuredCount),
);

function showSlide(step: number) {
  slide.value = (slide.value + step + heroSlides) % heroSlides;
}
</script>

<template>
  <ClientLayout active="home" :guest="guest">
    <div class="client-home-shell">
      <h1 class="sr-only">{{ t('app.title') }}</h1>

      <section
        class="client-home-hero"
        :aria-label="t('home.hero.label')"
        aria-roledescription="carousel"
        aria-labelledby="client-home-title"
      >
        <button
          type="button"
          :aria-label="t('home.hero.previous')"
          class="client-home-arrow client-home-arrow--previous"
          @click="showSlide(-1)"
        >
          ‹
        </button>

        <div class="client-home-hero-copy">
          <p class="client-home-overline">{{ t('home.editorial.eyebrow') }}</p>
          <h2 id="client-home-title">{{ t('home.editorial.title') }}</h2>
          <p>{{ t('home.editorial.description') }}</p>
          <RouterLink class="client-home-search" to="/games">
            <span class="client-home-search-dot" aria-hidden="true"></span>
            <span>{{ t('home.editorial.searchGames') }}</span>
            <b aria-hidden="true">⌕</b>
          </RouterLink>
        </div>

        <div class="client-home-deck" aria-hidden="true">
          <article
            v-for="(card, index) in featureCards"
            :key="card.title"
            class="client-game-card"
            :class="[
              `client-game-card--${card.tone}`,
              { 'client-game-card--active': index === slide },
            ]"
          >
            <span class="client-game-card-label">{{ card.label }}</span>
            <strong>{{ card.title }}</strong>
            <span class="client-game-card-symbol">✦</span>
          </article>
        </div>

        <button
          type="button"
          :aria-label="t('home.hero.next')"
          class="client-home-arrow client-home-arrow--next"
          @click="showSlide(1)"
        >
          ›
        </button>

        <div class="client-home-dots">
          <button
            v-for="index in heroSlides"
            :key="index"
            type="button"
            data-testid="hero-dot"
            :aria-label="
              t('home.hero.slide', { index: index, total: heroSlides })
            "
            :aria-current="slide === index - 1 ? 'true' : undefined"
            @click="slide = index - 1"
          >
            <span :class="{ 'client-home-dot--active': slide === index - 1 }" />
          </button>
        </div>
      </section>

      <section
        class="client-home-branches"
        aria-labelledby="home-branches-title"
      >
        <div class="client-home-section-heading">
          <h2 id="home-branches-title">
            {{ t('home.editorial.findYourTable') }}
          </h2>
          <UiButton
            v-if="allBranches.length > featuredCount"
            variant="ghost"
            class="client-home-view-all"
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
          class="client-branch-empty"
          aria-busy="true"
        >
          {{ t('home.branches.loading') }}
        </p>
        <div v-else-if="branches.isError.value" class="client-branch-state">
          <p data-testid="home-branches-error" role="alert">
            {{ t('home.branches.loadFailed') }}
          </p>
          <UiButton variant="outline" size="sm" @click="branches.refetch()">
            {{ t('home.branches.retry') }}
          </UiButton>
        </div>
        <p
          v-else-if="allBranches.length === 0"
          data-testid="home-branches-empty"
          class="client-branch-empty"
        >
          {{ t('home.branches.empty') }}
        </p>
        <div v-else class="client-branch-grid">
          <BranchCard
            v-for="(branch, index) in visibleBranches"
            :key="branch.id"
            :branch="branch"
            :index="index"
            :hint="t('home.editorial.branchHint')"
            :address-label="t('home.branches.address')"
            :opening-hours-label="t('home.branches.openingHours')"
            :book-label="t('home.branches.bookHere')"
          />
        </div>
      </section>
    </div>
  </ClientLayout>
</template>

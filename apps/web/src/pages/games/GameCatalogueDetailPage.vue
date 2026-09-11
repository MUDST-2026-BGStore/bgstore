<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query';
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute } from 'vue-router';
import ClientLayout from '../../layouts/ClientLayout.vue';
import ScreenStatus from '../../components/ScreenStatus.vue';
import arrowForward from '../../assets/icons/arrow-forward.svg';
import bulletDot from '../../assets/icons/bullet-dot.svg';
import guideGamepad from '../../assets/icons/guide-gamepad.svg';
import guideStar from '../../assets/icons/guide-star.svg';
import { gameQueryOptions } from '../../queries/games';
import {
  freeCopies,
  playTime,
  playerCount,
  stockedBranches,
  wrapIndex,
} from './catalogue';
import { hasStatus } from './form';
import { resolveLocalized, secondaryTitle } from './localized';

/** The guest's view of one game: what it is and how to play it, before a visit. */
const { t, locale } = useI18n();
const route = useRoute();

const gameId = computed(() => String(route.params.gameId));
const query = useQuery(computed(() => gameQueryOptions(gameId.value)));

const game = computed(() => query.data.value);
const missing = computed(() => hasStatus(query.error.value, 404));

const title = computed(() => resolveLocalized(game.value?.title, locale.value));
const subtitle = computed(() =>
  secondaryTitle(game.value?.title, locale.value),
);

// App.vue keys the router view by path, so another game is a fresh instance
// and the gallery never carries an index over from the last one.
const photoIndex = ref(0);
const photos = computed(() => game.value?.imageUrls ?? []);
const photo = computed(() => photos.value[photoIndex.value]);

function showPhoto(step: number) {
  photoIndex.value = wrapIndex(photoIndex.value + step, photos.value.length);
}

const players = computed(() =>
  game.value
    ? playerCount(game.value.minPlayers, game.value.maxPlayers, t)
    : '',
);

const stats = computed(() => {
  const loaded = game.value;
  if (!loaded) {
    return [];
  }

  const unknown = t('games.state.unknown');
  return [
    { key: 'players', value: players.value },
    { key: 'playTime', value: playTime(loaded.playTimeMinutes, t) || unknown },
    { key: 'difficulty', value: loaded.difficulty || unknown },
  ].map((stat) => ({
    ...stat,
    label: t('catalogue.detail.stats.' + stat.key),
  }));
});

const availability = computed(() => {
  const loaded = game.value;
  if (!loaded) {
    return undefined;
  }

  const tone = {
    available: 'border-success-border bg-success-bg text-success-fg',
    allCopiesOut: 'border-warning-border bg-warning-bg text-warning-fg',
    notStocked: 'border-neutral-border bg-neutral-bg text-neutral-fg',
    retired: 'border-neutral-border bg-neutral-bg text-neutral-fg',
  }[loaded.status];

  return {
    tone,
    text: t('catalogue.detail.availability.' + loaded.status, {
      count: freeCopies(loaded.stock),
    }),
  };
});

// The design gives this action no destination yet (there is no branch screen
// for guests), so it reveals the branches holding the game in place.
const branchesOpen = ref(false);
const branches = computed(() => stockedBranches(game.value?.stock ?? []));

const goalBullets = computed(() => {
  const guide = game.value?.guide;
  const text = (value: Parameters<typeof resolveLocalized>[0]) =>
    resolveLocalized(value, locale.value);

  return [
    { key: 'goal', text: text(guide?.goal) },
    // Every game has a player range, so this bullet always has something to say.
    { key: 'players', text: text(guide?.players) || players.value },
    { key: 'equipment', text: text(guide?.equipment) },
  ]
    .filter((bullet) => bullet.text)
    .map((bullet) => ({
      ...bullet,
      label: t('catalogue.detail.' + bullet.key),
    }));
});

const steps = computed(() =>
  (game.value?.guide.steps ?? []).map((step) => ({
    title: resolveLocalized(step.title, locale.value),
    body: resolveLocalized(step.body, locale.value),
  })),
);
</script>

<template>
  <ScreenStatus
    v-if="query.isPending.value"
    audience="client"
    state="loading"
    testid="catalogue-detail-loading"
  />

  <ScreenStatus
    v-else-if="missing"
    audience="client"
    state="missing"
    testid="catalogue-game-not-found"
  />

  <ScreenStatus
    v-else-if="query.isError.value || !game"
    audience="client"
    state="failed"
    testid="catalogue-detail-error"
    @retry="query.refetch()"
  />

  <ClientLayout v-else>
    <div class="flex w-full flex-col items-start gap-8 px-8 pt-6 pb-12">
      <nav
        :aria-label="t('catalogue.detail.breadcrumb')"
        class="flex items-center gap-2 text-[13px] leading-[1.5]"
      >
        <router-link
          to="/games"
          class="font-medium text-ink-secondary hover:text-ink"
        >
          {{ t('catalogue.title') }}
        </router-link>
        <span aria-hidden="true" class="text-ink-muted">/</span>
        <span aria-current="page" class="font-semibold text-ink">
          {{ title }}
        </span>
      </nav>

      <section class="flex w-full flex-col items-start gap-10 lg:flex-row">
        <div
          class="relative h-[360px] w-full shrink-0 overflow-hidden rounded-[16px] border border-line bg-surface-sunken lg:w-[520px]"
        >
          <img
            v-if="photo"
            :src="photo"
            :alt="
              t('catalogue.detail.photo', {
                index: photoIndex + 1,
                total: photos.length,
                title,
              })
            "
            data-testid="catalogue-photo"
            class="block size-full object-cover"
          />
          <template v-if="photos.length > 1">
            <button
              type="button"
              :aria-label="t('catalogue.detail.previousPhoto')"
              class="absolute top-1/2 left-[15px] flex size-10 -translate-y-1/2 items-center justify-center rounded-full border border-line bg-surface drop-shadow-[0px_2px_3px_rgba(71,61,61,0.16)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              @click="showPhoto(-1)"
            >
              <img
                :src="arrowForward"
                alt=""
                class="block size-5 -scale-x-100"
                width="20"
                height="20"
              />
            </button>
            <button
              type="button"
              :aria-label="t('catalogue.detail.nextPhoto')"
              class="absolute top-1/2 right-[15px] flex size-10 -translate-y-1/2 items-center justify-center rounded-full border border-line bg-surface drop-shadow-[0px_2px_3px_rgba(71,61,61,0.16)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              @click="showPhoto(1)"
            >
              <img
                :src="arrowForward"
                alt=""
                class="block size-5"
                width="20"
                height="20"
              />
            </button>
          </template>
        </div>

        <div class="flex w-full min-w-0 flex-1 flex-col items-start gap-5">
          <ul
            :aria-label="t('catalogue.detail.badges')"
            class="flex flex-wrap items-start gap-2"
          >
            <li
              class="rounded-full border border-primary-subtle bg-primary-subtle px-3 py-[5px] text-[12px] leading-[1.3] font-semibold whitespace-nowrap text-primary-subtle-fg"
            >
              {{ t('catalogue.category.' + game.category) }}
            </li>
            <li
              v-for="tag in game.tags"
              :key="tag"
              class="rounded-full border border-neutral-border bg-neutral-bg px-3 py-[5px] text-[12px] leading-[1.3] font-semibold whitespace-nowrap text-neutral-fg"
            >
              {{ tag }}
            </li>
          </ul>

          <div class="flex w-full flex-col items-start gap-0.5">
            <h1
              class="text-[32px] leading-[1.2] font-bold text-ink lg:text-[40px]"
            >
              {{ title }}
            </h1>
            <p
              v-if="subtitle"
              data-testid="catalogue-subtitle"
              class="text-[16px] leading-[1.4] text-ink-secondary"
            >
              {{ subtitle }}
            </p>
          </div>

          <dl class="flex w-full flex-col gap-3 leading-[1.4] sm:flex-row">
            <div
              v-for="stat in stats"
              :key="stat.key"
              class="flex min-w-0 flex-1 flex-col items-start gap-0.5 rounded-lg border border-line bg-surface px-4 py-3"
            >
              <dt class="text-[12px] font-medium text-ink-muted">
                {{ stat.label }}
              </dt>
              <dd class="text-[17px] font-semibold text-ink">
                {{ stat.value }}
              </dd>
            </div>
          </dl>

          <p
            v-if="availability"
            data-testid="catalogue-availability"
            class="inline-flex items-center gap-2 rounded-full border px-[14px] py-2 text-[13px] leading-[1.4] font-medium"
            :class="availability.tone"
          >
            <span
              aria-hidden="true"
              class="block size-2 shrink-0 rounded-full bg-current"
            />
            {{ availability.text }}
          </p>

          <div class="flex w-full flex-col items-start gap-3 pt-1">
            <button
              type="button"
              aria-controls="catalogue-branches"
              :aria-expanded="branchesOpen"
              class="rounded-[10px] border border-line bg-secondary px-7 py-[14px] text-[15px] leading-[1.3] font-semibold whitespace-nowrap text-secondary-fg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              @click="branchesOpen = !branchesOpen"
            >
              {{ t('catalogue.detail.showBranches') }}
            </button>
            <div v-show="branchesOpen" id="catalogue-branches" class="w-full">
              <ul
                v-if="branches.length"
                class="flex w-full flex-col gap-2"
                data-testid="catalogue-branches"
              >
                <li
                  v-for="branch in branches"
                  :key="branch.branchId"
                  class="flex w-full items-center justify-between gap-3 rounded-lg border border-line bg-surface px-4 py-2.5 text-[13.5px] leading-[1.5]"
                >
                  <span class="font-semibold text-ink">
                    {{ branch.branchName }}
                  </span>
                  <span
                    :class="
                      branch.available > 0
                        ? 'text-success-fg'
                        : 'text-warning-fg'
                    "
                  >
                    {{
                      branch.available > 0
                        ? t('catalogue.detail.branchFree', {
                            count: branch.available,
                          })
                        : t('catalogue.detail.branchAllOut')
                    }}
                  </span>
                </li>
              </ul>
              <p v-else class="text-[13.5px] text-ink-muted">
                {{ t('catalogue.detail.noBranches') }}
              </p>
            </div>
          </div>
        </div>
      </section>

      <div class="flex w-full flex-col items-start gap-6 lg:flex-row">
        <section
          aria-labelledby="catalogue-goal-title"
          class="flex w-full flex-col items-start gap-4 rounded-[16px] border border-line bg-surface px-6 pt-[22px] pb-6 shadow-[0px_2px_8px_0px_rgba(71,61,61,0.08)] lg:w-[470px] lg:shrink-0"
        >
          <h2
            id="catalogue-goal-title"
            class="flex items-center gap-2.5 text-[18px] leading-[1.4] font-semibold text-ink"
          >
            <img
              :src="guideStar"
              alt=""
              class="block size-6 shrink-0"
              width="24"
              height="24"
            />
            {{ t('catalogue.detail.goalTitle') }}
          </h2>
          <ul class="flex w-full flex-col items-start gap-3">
            <li
              v-for="bullet in goalBullets"
              :key="bullet.key"
              data-testid="catalogue-goal"
              class="flex w-full items-start gap-2.5"
            >
              <img
                :src="bulletDot"
                alt=""
                class="block h-[15px] w-[6px] shrink-0"
                width="6"
                height="15"
              />
              <p
                class="min-w-0 flex-1 text-[14.5px] leading-[1.65] text-ink-secondary"
              >
                <span class="font-semibold text-ink">{{ bullet.label }}</span>
                {{ bullet.text }}
              </p>
            </li>
          </ul>
        </section>

        <section
          v-if="steps.length"
          aria-labelledby="catalogue-steps-title"
          class="flex w-full min-w-0 flex-1 flex-col items-start gap-4 rounded-[16px] border border-line bg-surface px-6 pt-[22px] pb-6 shadow-[0px_2px_8px_0px_rgba(71,61,61,0.08)]"
        >
          <h2
            id="catalogue-steps-title"
            class="flex items-center gap-2.5 text-[18px] leading-[1.4] font-semibold text-ink"
          >
            <img
              :src="guideGamepad"
              alt=""
              class="block size-6 shrink-0"
              width="24"
              height="24"
            />
            {{ t('catalogue.detail.howToPlay') }}
          </h2>
          <ol class="flex w-full flex-col items-start gap-[14px]">
            <li
              v-for="(step, index) in steps"
              :key="index"
              data-testid="catalogue-step"
              class="flex w-full items-start gap-3"
            >
              <span
                aria-hidden="true"
                class="flex size-[26px] shrink-0 items-center justify-center rounded-full bg-primary-subtle text-[13px] leading-none font-semibold text-primary-subtle-fg"
              >
                {{ index + 1 }}
              </span>
              <div
                class="flex min-w-0 flex-1 flex-col items-start gap-0.5 pt-0.5 text-[14.5px]"
              >
                <h3 class="w-full leading-[1.5] font-semibold text-ink">
                  {{ step.title }}
                </h3>
                <p
                  v-if="step.body"
                  class="w-full leading-[1.65] text-ink-secondary"
                >
                  {{ step.body }}
                </p>
              </div>
            </li>
          </ol>
        </section>
      </div>
    </div>
  </ClientLayout>
</template>

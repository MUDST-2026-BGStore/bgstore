<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute } from 'vue-router';
import UiButton from './ui/UiButton.vue';
import logo from '../assets/icons/logo.svg';
import navHome from '../assets/icons/nav-home.svg';
import navReserve from '../assets/icons/nav-reserve.svg';
import navGames from '../assets/icons/nav-games.svg';
import navGamesActive from '../assets/icons/nav-games-active.svg';
import navBranches from '../assets/icons/nav-branches.svg';
import navHistory from '../assets/icons/nav-history.svg';
import navProfile from '../assets/icons/nav-profile.svg';
import bookTable from '../assets/icons/book-table.svg';
import { signInHref } from '../queries/current-user';

const { t } = useI18n();
const route = useRoute();

/**
 * The guest-facing header from the Figma library. Only Home and Game have
 * screens so far, so the rest render as plain (non-navigating) items rather
 * than dead links, as OwnerHeader does.
 */
const items = [
  { key: 'home', icon: navHome, activeIcon: navHome, to: '/' },
  { key: 'reserve', icon: navReserve, activeIcon: navReserve, to: undefined },
  { key: 'game', icon: navGames, activeIcon: navGamesActive, to: '/games' },
  { key: 'branch', icon: navBranches, activeIcon: navBranches, to: undefined },
  { key: 'history', icon: navHistory, activeIcon: navHistory, to: '/history' },
  { key: 'profile', icon: navProfile, activeIcon: navProfile, to: undefined },
] as const;

/** A visitor without an account has nothing of their own to open yet. */
const guestItems: readonly (typeof items)[number]['key'][] = [
  'home',
  'game',
  'branch',
];

const props = defineProps<{
  active: (typeof items)[number]['key'];
  /** Signed out: the design's "Client Header — Guest" variant. */
  guest?: boolean;
}>();

const visibleItems = computed(() =>
  props.guest ? items.filter((item) => guestItems.includes(item.key)) : items,
);
</script>

<template>
  <header
    class="flex h-16 w-full shrink-0 items-center gap-4 border-b border-line bg-surface px-6"
  >
    <div class="flex shrink-0 items-center gap-2.5">
      <img
        :src="logo"
        :alt="t('app.title')"
        class="block size-8 shrink-0"
        width="32"
        height="32"
      />
    </div>
    <div class="h-px min-w-0 flex-1" />
    <nav class="flex min-w-0 shrink items-center gap-1 overflow-x-auto">
      <component
        :is="item.to ? 'router-link' : 'span'"
        v-for="item in visibleItems"
        :key="item.key"
        :to="item.to"
        :aria-current="item.key === active ? 'page' : undefined"
        class="flex shrink-0 items-center gap-2 rounded-md px-3 py-2 text-[14px] leading-[22px]"
        :class="
          item.key === active
            ? 'bg-primary-subtle font-medium text-primary-subtle-fg'
            : 'text-ink-secondary'
        "
      >
        <img
          :src="item.key === active ? item.activeIcon : item.icon"
          alt=""
          class="block size-[18px] shrink-0"
          width="18"
          height="18"
        />
        <span class="whitespace-nowrap">{{ t(`navigation.${item.key}`) }}</span>
      </component>
    </nav>
    <div v-if="guest" class="flex shrink-0 items-center gap-2">
      <UiButton
        variant="ghost"
        :href="signInHref(route.fullPath)"
        class="w-[120px]"
      >
        {{ t('navigation.login') }}
      </UiButton>
      <UiButton
        :href="signInHref(route.fullPath, { signUp: true })"
        class="w-[120px]"
      >
        {{ t('navigation.signUp') }}
      </UiButton>
    </div>
    <!-- Reservations have no screen yet, so the call to action is not a link. -->
    <span
      v-else
      aria-disabled="true"
      class="inline-flex h-10 w-[120px] shrink-0 items-center justify-center gap-2 rounded-md border border-primary bg-primary px-4 text-center text-[13px] leading-[20px] font-medium whitespace-nowrap text-primary-fg"
    >
      <img
        :src="bookTable"
        alt=""
        class="block size-4 shrink-0"
        width="16"
        height="16"
      />
      {{ t('navigation.bookTable') }}
    </span>
  </header>
</template>

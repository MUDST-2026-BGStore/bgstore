<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query';
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { RouterLink, useRoute } from 'vue-router';
import logo from '../assets/icons/logo.svg';
import navBranches from '../assets/icons/nav-branches.svg';
import navDashboard from '../assets/icons/nav-dashboard.svg';
import navGames from '../assets/icons/nav-games.svg';
import navGamesActive from '../assets/icons/nav-games-active.svg';
import navHistory from '../assets/icons/nav-history.svg';
import navHome from '../assets/icons/nav-home.svg';
import navProfile from '../assets/icons/nav-profile.svg';
import navTables from '../assets/icons/nav-tables.svg';
import UiButton from './ui/UiButton.vue';
import {
  currentUserQueryOptions,
  hasStaffAccess,
  signInHref,
} from '../queries/current-user';

type NavigationKey =
  | 'home'
  | 'branches'
  | 'games'
  | 'tables'
  | 'history'
  | 'profile';

type NavigationItem = {
  key: NavigationKey;
  label: string;
  to: string;
  icon: string;
  activeIcon: string;
};

const { t } = useI18n();
const route = useRoute();
const currentUser = useQuery(currentUserQueryOptions());

const guestItems: NavigationItem[] = [
  {
    key: 'home',
    label: 'navigation.home',
    to: '/',
    icon: navHome,
    activeIcon: navHome,
  },
  {
    key: 'games',
    label: 'navigation.game',
    to: '/games',
    icon: navGames,
    activeIcon: navGamesActive,
  },
  {
    key: 'branches',
    label: 'navigation.branch',
    to: '/branches',
    icon: navBranches,
    activeIcon: navBranches,
  },
];

const clientItems: NavigationItem[] = [
  ...guestItems,
  {
    key: 'history',
    label: 'navigation.history',
    to: '/history',
    icon: navHistory,
    activeIcon: navHistory,
  },
  {
    key: 'profile',
    label: 'navigation.profile',
    to: '/profile',
    icon: navProfile,
    activeIcon: navProfile,
  },
];

const staffItems: NavigationItem[] = [
  {
    key: 'home',
    label: 'games.nav.dashboard',
    to: '/',
    icon: navDashboard,
    activeIcon: navDashboard,
  },
  {
    key: 'branches',
    label: 'games.nav.branches',
    to: '/branches',
    icon: navBranches,
    activeIcon: navBranches,
  },
  {
    key: 'tables',
    label: 'games.nav.tables',
    to: '/tables',
    icon: navTables,
    activeIcon: navTables,
  },
  {
    key: 'games',
    label: 'games.nav.games',
    to: '/games',
    icon: navGames,
    activeIcon: navGamesActive,
  },
  {
    key: 'profile',
    label: 'navigation.profile',
    to: '/profile',
    icon: navProfile,
    activeIcon: navProfile,
  },
];

const isStaff = computed(() =>
  currentUser.data.value ? hasStaffAccess(currentUser.data.value.roles) : false,
);

const items = computed(() => {
  if (isStaff.value) return staffItems;
  return currentUser.data.value ? clientItems : guestItems;
});

const activeKey = computed<NavigationKey | null>(() => {
  const name = String(route.name ?? '');
  if (name === 'home') return 'home';
  if (name === 'branches' || name === 'branch-detail') return 'branches';
  if (name.startsWith('games')) return 'games';
  if (name.startsWith('history')) return 'history';
  if (name === 'tables') return 'tables';
  if (name === 'user-profile') return 'profile';
  return null;
});
</script>

<template>
  <header
    class="flex min-h-16 w-full shrink-0 items-center gap-4 border-b border-line bg-surface px-4 sm:px-6"
  >
    <RouterLink
      class="flex shrink-0 items-center gap-2.5"
      to="/"
      :aria-label="t('app.title')"
    >
      <img
        :src="logo"
        :alt="t('app.title')"
        class="block size-8 shrink-0"
        width="32"
        height="32"
      />
      <span class="hidden text-sm font-semibold text-ink sm:inline">{{
        t('app.title')
      }}</span>
    </RouterLink>

    <nav
      class="flex min-w-0 flex-1 items-center justify-end gap-1 overflow-x-auto"
      :aria-label="t(isStaff ? 'navigation.staff' : 'navigation.primary')"
    >
      <RouterLink
        v-for="item in items"
        :key="item.key"
        :to="item.to"
        :aria-current="item.key === activeKey ? 'page' : undefined"
        class="flex shrink-0 items-center gap-2 rounded-md px-3 py-2 text-[14px] leading-[22px]"
        :class="
          item.key === activeKey
            ? 'bg-primary-subtle font-medium text-primary-subtle-fg'
            : 'text-ink-secondary hover:bg-canvas'
        "
      >
        <img
          :src="item.key === activeKey ? item.activeIcon : item.icon"
          alt=""
          class="block size-[18px] shrink-0"
          width="18"
          height="18"
        />
        <span class="whitespace-nowrap">{{ t(item.label) }}</span>
      </RouterLink>
    </nav>

    <div
      v-if="!currentUser.data.value"
      class="flex shrink-0 items-center gap-2"
    >
      <UiButton
        variant="ghost"
        :href="signInHref(route.fullPath)"
        class="hidden sm:inline-flex"
      >
        {{ t('navigation.login') }}
      </UiButton>
      <UiButton :href="signInHref(route.fullPath, { signUp: true })">
        {{ t('navigation.signUp') }}
      </UiButton>
    </div>
  </header>
</template>

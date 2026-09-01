<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import logo from '../assets/icons/logo.svg';
import navDashboard from '../assets/icons/nav-dashboard.svg';
import navBranches from '../assets/icons/nav-branches.svg';
import navTables from '../assets/icons/nav-tables.svg';
import navGames from '../assets/icons/nav-games.svg';
import navGamesActive from '../assets/icons/nav-games-active.svg';

const { t } = useI18n();

/**
 * Only the Games section exists in the design today, so the other three
 * nav items render as plain (non-navigating) items rather than dead links.
 */
const items = [
  {
    key: 'dashboard',
    icon: navDashboard,
    activeIcon: navDashboard,
    to: undefined,
  },
  {
    key: 'branches',
    icon: navBranches,
    activeIcon: navBranches,
    to: undefined,
  },
  { key: 'tables', icon: navTables, activeIcon: navTables, to: undefined },
  { key: 'games', icon: navGames, activeIcon: navGamesActive, to: '/games' },
] as const;

defineProps<{ active: (typeof items)[number]['key'] }>();
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
    <nav class="flex shrink-0 items-center gap-1">
      <component
        :is="item.to ? 'router-link' : 'span'"
        v-for="item in items"
        :key="item.key"
        :to="item.to"
        :aria-current="item.key === active ? 'page' : undefined"
        class="flex h-full shrink-0 items-center gap-2 rounded-md px-3 py-2 text-[14px] leading-[22px]"
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
        <span class="whitespace-nowrap">{{ t(`games.nav.${item.key}`) }}</span>
      </component>
    </nav>
  </header>
</template>

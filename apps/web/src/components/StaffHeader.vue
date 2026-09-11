<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import logo from '../assets/icons/logo.svg';
import navHome from '../assets/icons/nav-home.svg';
import navHomeActive from '../assets/icons/nav-dashboard.svg';
import navReserve from '../assets/icons/nav-reserve.svg';
import navBranches from '../assets/icons/nav-branches.svg';
import navHistory from '../assets/icons/nav-history.svg';
import navCheckIn from '../assets/icons/nav-check-in.svg';
import navProfile from '../assets/icons/nav-profile.svg';

const { t } = useI18n();

/**
 * The staff header from the Figma library. Reserve, History and
 * Check-in / Check-out have no screens yet, so they render as plain
 * (non-navigating) items rather than dead links, as ClientHeader does.
 */
const items = [
  { key: 'home', icon: navHome, activeIcon: navHomeActive, to: '/' },
  { key: 'reserve', icon: navReserve, activeIcon: navReserve, to: undefined },
  {
    key: 'branch',
    icon: navBranches,
    activeIcon: navBranches,
    to: '/branches',
  },
  { key: 'history', icon: navHistory, activeIcon: navHistory, to: undefined },
  {
    key: 'checkInOut',
    icon: navCheckIn,
    activeIcon: navCheckIn,
    to: undefined,
  },
  { key: 'profile', icon: navProfile, activeIcon: navProfile, to: '/profile' },
] as const;

defineProps<{ active: (typeof items)[number]['key'] }>();
</script>

<template>
  <header
    class="flex h-16 w-full shrink-0 items-center gap-2 border-b border-line bg-surface px-6"
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
    <nav
      class="flex min-w-0 shrink items-center gap-1 overflow-x-auto"
      :aria-label="t('navigation.staff')"
    >
      <component
        :is="item.to ? 'router-link' : 'span'"
        v-for="item in items"
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
  </header>
</template>

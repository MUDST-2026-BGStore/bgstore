<script setup lang="ts">
import { ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { RouterLink } from 'vue-router';
import BrandLockup from './BrandLockup.vue';
import navBranches from '../assets/icons/nav-branches.svg';
import navCheckIn from '../assets/icons/nav-check-in.svg';
import navDashboard from '../assets/icons/nav-dashboard.svg';
import navGames from '../assets/icons/nav-games.svg';
import navGamesActive from '../assets/icons/nav-games-active.svg';
import navHistory from '../assets/icons/nav-history.svg';
import navProfile from '../assets/icons/nav-profile.svg';
import navTables from '../assets/icons/nav-tables.svg';
import { logout } from '../queries/current-user';

export type StaffSection =
  | 'dashboard'
  | 'reservations'
  | 'sessions'
  | 'tables'
  | 'games'
  | 'branches'
  | 'profile';

type StaffNavigationItem = {
  key: StaffSection;
  label: string;
  icon: string;
  activeIcon: string;
  to?: string;
};

const { t } = useI18n();
const isLoggingOut = ref(false);
const logoutError = ref(false);

const items: StaffNavigationItem[] = [
  {
    key: 'dashboard',
    label: 'staff.nav.dashboard',
    icon: navDashboard,
    activeIcon: navDashboard,
    to: '/',
  },
  {
    key: 'reservations',
    label: 'staff.nav.reservations',
    icon: navHistory,
    activeIcon: navHistory,
    to: '/staff/reservations',
  },
  {
    key: 'sessions',
    label: 'staff.nav.sessions',
    icon: navCheckIn,
    activeIcon: navCheckIn,
    to: '/staff/sessions',
  },
  {
    key: 'tables',
    label: 'staff.nav.tables',
    icon: navTables,
    activeIcon: navTables,
    to: '/tables',
  },
  {
    key: 'games',
    label: 'staff.nav.games',
    icon: navGames,
    activeIcon: navGamesActive,
    to: '/games',
  },
  {
    key: 'branches',
    label: 'staff.nav.branches',
    icon: navBranches,
    activeIcon: navBranches,
    to: '/branches',
  },
  {
    key: 'profile',
    label: 'staff.nav.profile',
    icon: navProfile,
    activeIcon: navProfile,
    to: '/profile',
  },
];

defineProps<{ active: StaffSection }>();

async function signOut() {
  if (isLoggingOut.value) return;

  isLoggingOut.value = true;
  logoutError.value = false;
  try {
    await logout();
  } catch {
    logoutError.value = true;
  } finally {
    isLoggingOut.value = false;
  }
}
</script>

<template>
  <aside class="owner-sidebar">
    <RouterLink class="owner-sidebar-brand" to="/" :aria-label="t('app.title')">
      <BrandLockup tone="light" />
    </RouterLink>

    <p class="owner-sidebar-label">{{ t('staff.workspace') }}</p>

    <nav class="owner-sidebar-nav" :aria-label="t('staff.navigation')">
      <component
        :is="item.to ? RouterLink : 'span'"
        v-for="item in items"
        :key="item.key"
        :to="item.to"
        :aria-current="item.key === active ? 'page' : undefined"
        :aria-disabled="!item.to ? 'true' : undefined"
        class="owner-sidebar-item"
        :class="[
          item.key === active ? 'owner-sidebar-item--active' : '',
          !item.to ? 'owner-sidebar-item--disabled' : '',
        ]"
      >
        <img
          :src="item.key === active ? item.activeIcon : item.icon"
          alt=""
          width="18"
          height="18"
        />
        <span>{{ t(item.label) }}</span>
      </component>
    </nav>

    <div class="owner-sidebar-footer">
      <span class="owner-sidebar-avatar">BG</span>
      <div class="owner-sidebar-account">
        <span>{{ t('staff.account') }}</span>
        <button
          type="button"
          class="owner-sidebar-logout"
          :disabled="isLoggingOut"
          @click="signOut"
        >
          {{ isLoggingOut ? t('staff.loggingOut') : t('navigation.logout') }}
        </button>
      </div>
    </div>
    <p v-if="logoutError" class="owner-sidebar-logout-error" role="alert">
      {{ t('staff.logoutFailed') }}
    </p>
  </aside>
</template>

<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { RouterLink, useRoute } from 'vue-router';
import navBranches from '../assets/icons/nav-branches.svg';
import navDashboard from '../assets/icons/nav-dashboard.svg';
import navGames from '../assets/icons/nav-games.svg';
import navGamesActive from '../assets/icons/nav-games-active.svg';
import navHistory from '../assets/icons/nav-history.svg';
import navHome from '../assets/icons/nav-home.svg';
import navProfile from '../assets/icons/nav-profile.svg';
import navTables from '../assets/icons/nav-tables.svg';
import UiButton from './ui/UiButton.vue';
import BrandLockup from './BrandLockup.vue';
import {
  currentUserQueryOptions,
  hasStaffAccess,
  logout,
  authStartHref,
} from '../queries/current-user';

type NavigationKey =
  | 'home'
  | 'reserve'
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
const profileMenuOpen = ref(false);
const logoutError = ref(false);
const hasScrolled = ref(false);

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
    key: 'reserve',
    label: 'navigation.reserve',
    to: '/reservations/new',
    icon: navTables,
    activeIcon: navTables,
  },
  {
    key: 'history',
    label: 'navigation.history',
    to: '/history',
    icon: navHistory,
    activeIcon: navHistory,
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
  if (name === 'login') {
    const redirect = route.query.redirect;
    if (typeof redirect === 'string' && redirect.startsWith('/games')) {
      return 'games';
    }
    if (
      typeof redirect === 'string' &&
      (redirect.startsWith('/branches') || redirect.startsWith('/branch'))
    ) {
      return 'branches';
    }
  }
  if (name === 'home') return 'home';
  if (name === 'staff-create-reservation') return 'reserve';
  if (name === 'branches' || name === 'branch-detail') return 'branches';
  if (name.startsWith('games')) return 'games';
  if (name.startsWith('history')) return 'history';
  if (name === 'tables') return 'tables';
  if (name === 'user-profile') return 'profile';
  return null;
});

function updateScrollState() {
  hasScrolled.value = window.scrollY > 12;
}

onMounted(() => {
  updateScrollState();
  window.addEventListener('scroll', updateScrollState, { passive: true });
});

onBeforeUnmount(() => {
  window.removeEventListener('scroll', updateScrollState);
});

const profileInitials = computed(() => {
  const user = currentUser.data.value;
  if (!user) return 'BG';

  const initials = [user.firstName, user.lastName]
    .filter(Boolean)
    .map((name) => name[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

  return initials || user.username[0]?.toUpperCase() || 'BG';
});

function toggleProfileMenu() {
  logoutError.value = false;
  profileMenuOpen.value = !profileMenuOpen.value;
}

async function signOut() {
  logoutError.value = false;
  try {
    await logout();
  } catch {
    logoutError.value = true;
  }
}
</script>

<template>
  <header
    class="app-navbar client-header"
    :class="{ 'client-header--scrolled': hasScrolled }"
  >
    <RouterLink class="client-header-brand" to="/" :aria-label="t('app.title')">
      <BrandLockup />
    </RouterLink>

    <nav
      class="client-nav-pill"
      :aria-label="t(isStaff ? 'navigation.staff' : 'navigation.primary')"
    >
      <RouterLink
        v-for="item in items"
        :key="item.key"
        :to="item.to"
        :aria-current="item.key === activeKey ? 'page' : undefined"
        class="client-nav-link"
        :class="
          item.key === activeKey
            ? 'client-nav-link--active'
            : 'client-nav-link--quiet'
        "
      >
        <img
          :src="item.key === activeKey ? item.activeIcon : item.icon"
          alt=""
          class="block size-[18px] shrink-0"
          width="18"
          height="18"
        />
        <span>{{ t(item.label) }}</span>
      </RouterLink>
    </nav>

    <div v-if="!currentUser.data.value" class="client-header-actions">
      <UiButton
        variant="ghost"
        :href="authStartHref(route.fullPath)"
        class="hidden sm:inline-flex"
      >
        {{ t('navigation.login') }}
      </UiButton>
      <UiButton :href="authStartHref(route.fullPath, { signUp: true })">
        {{ t('navigation.signUp') }}
      </UiButton>
    </div>

    <div v-else-if="!isStaff" class="client-header-actions">
      <div class="client-profile-menu">
        <button
          type="button"
          class="client-profile-trigger"
          :aria-expanded="profileMenuOpen"
          :aria-label="t('navigation.profile')"
          @click="toggleProfileMenu"
        >
          <span class="client-profile-avatar" aria-hidden="true">
            {{ profileInitials }}
          </span>
        </button>

        <div
          v-if="profileMenuOpen"
          class="client-profile-dropdown"
          role="menu"
          :aria-label="t('navigation.profile')"
        >
          <RouterLink
            to="/profile"
            role="menuitem"
            @click="profileMenuOpen = false"
          >
            {{ t('navigation.profile') }}
          </RouterLink>
          <button type="button" role="menuitem" @click="signOut">
            {{ t('navigation.logout') }}
          </button>
          <p v-if="logoutError" class="client-profile-error" role="alert">
            {{ t('navigation.logoutFailed') }}
          </p>
        </div>
      </div>
    </div>
  </header>
</template>

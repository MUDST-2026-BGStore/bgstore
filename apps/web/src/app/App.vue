<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query';
import { computed, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { RouterView, useRoute, useRouter } from 'vue-router';
import AppNavbar from '../components/AppNavbar.vue';
import {
  currentUserQueryOptions,
  hasStaffAccess,
} from '../queries/current-user';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const usesFocusedLayout = computed(() => route.meta.focused === true);
const currentUser = useQuery(currentUserQueryOptions());

const staffWorkspaceRoutes = new Set([
  'home',
  'tables',
  'games',
  'games-new',
  'games-detail',
  'games-edit',
  'branches',
  'user-profile',
]);

const usesStaffWorkspace = computed(
  () =>
    currentUser.data.value !== null &&
    currentUser.data.value !== undefined &&
    hasStaffAccess(currentUser.data.value.roles) &&
    staffWorkspaceRoutes.has(String(route.name)),
);

const signInRequired = computed(
  () =>
    route.meta.requiresAuth === true &&
    (currentUser.isError.value || currentUser.data.value === null),
);
const usesAuthSurface = computed(
  () =>
    route.name === 'login' ||
    route.name === 'onboarding' ||
    signInRequired.value,
);

watch(
  () =>
    [
      currentUser.data.value,
      currentUser.isError.value,
      route.meta.requiresAuth,
      route.fullPath,
    ] as const,
  ([user, queryFailed, requiresAuth]) => {
    if (!user) {
      if (
        (user === null || queryFailed) &&
        requiresAuth &&
        route.name !== 'login'
      ) {
        void router.replace({
          name: 'login',
          query: { redirect: route.fullPath },
        });
      }
      return;
    }
    if (route.name === 'login') {
      void router.replace('/');
      return;
    }
    if (user.onboardingRequired && route.name !== 'onboarding') {
      void router.replace({
        name: 'onboarding',
        query: { returnTo: route.fullPath },
      });
      return;
    }
    if (!user.onboardingRequired && route.name === 'onboarding') {
      const returnTo = route.query.returnTo;
      void router.replace(
        typeof returnTo === 'string' &&
          returnTo.startsWith('/') &&
          !returnTo.startsWith('//')
          ? returnTo
          : '/',
      );
    }
  },
  { immediate: true },
);
</script>

<template>
  <div
    class="app-shell min-h-screen w-full bg-canvas"
    :class="{ 'auth-surface': usesAuthSurface }"
  >
    <AppNavbar
      v-if="
        !currentUser.isPending.value &&
        !usesFocusedLayout &&
        !usesStaffWorkspace &&
        !usesAuthSurface
      "
    />

    <section
      v-if="currentUser.isPending.value"
      class="auth-state refresh-auth-state"
      aria-live="polite"
    >
      <div class="auth-state-card refresh-auth-card refresh-auth-card--loading">
        <span class="auth-flow-icon" aria-hidden="true">BG</span>
        <p class="auth-kicker">{{ t('home.editorial.eyebrow') }}</p>
        <h1>{{ t('auth.loadingTitle') }}</h1>
        <p>{{ t('auth.loading') }}</p>
        <div class="refresh-loading-lines" aria-hidden="true">
          <span></span>
          <span></span>
          <span></span>
        </div>
      </div>
    </section>

    <section
      v-else-if="signInRequired"
      class="auth-redirect-state"
      aria-live="polite"
    ></section>

    <main
      v-else
      :class="
        usesFocusedLayout
          ? 'min-h-screen w-full'
          : 'min-h-[calc(100vh-4rem)] w-full'
      "
    >
      <RouterView v-slot="{ Component, route: renderedRoute }">
        <component :is="Component" :key="renderedRoute.path" />
      </RouterView>
    </main>
  </div>
</template>

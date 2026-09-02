<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query';
import { computed, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router';
import { currentUserQueryOptions } from '../queries/current-user';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const currentUser = useQuery(currentUserQueryOptions());

const signInHref = computed(
  () =>
    `/oauth2/authorization/keycloak?returnTo=${encodeURIComponent(route.fullPath)}`,
);

watch(
  () => currentUser.data.value,
  (user) => {
    if (!user) {
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
  <main class="shell w-full min-h-screen bg-white">
    <!-- Top Navigation Bar (Figma Design) -->
    <header
      class="masthead w-full h-16 border-b border-gray-200 bg-white px-12 flex justify-between items-center"
    >
      <!-- Brand Logo -->
      <RouterLink
        class="brand flex items-center"
        to="/"
        aria-label="BGStore home"
      >
        <div
          class="w-10 h-10 rounded-full bg-[#386671] flex items-center justify-center text-white font-bold text-sm"
        >
          <span class="brand-mark" aria-hidden="true">BG</span>
        </div>
      </RouterLink>

      <!-- Navigation Links -->
      <nav
        class="site-nav flex items-center gap-1.5"
        aria-label="Primary navigation"
      >
        <RouterLink
          to="/"
          class="flex items-center gap-2 px-3.5 py-2 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-100 transition"
        >
          <svg
            class="w-4 h-4 text-gray-600"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"
            />
          </svg>
          <span>Dashboard</span>
        </RouterLink>

        <RouterLink
          to="/branches"
          class="flex items-center gap-2 px-3.5 py-2 rounded-lg text-sm font-semibold bg-[#e8eef0] text-gray-900 transition"
        >
          <svg
            class="w-4 h-4 text-gray-800"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"
            />
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M15 11a3 3 0 11-6 0 3 3 0 016 0z"
            />
          </svg>
          <span>Branches</span>
        </RouterLink>

        <RouterLink
          to="/tables"
          class="flex items-center gap-2 px-3.5 py-2 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-100 transition"
        >
          <svg
            class="w-4 h-4 text-gray-600"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M4 6h16M4 10h16M4 14h16M4 18h16"
            />
          </svg>
          <span>Tables</span>
        </RouterLink>

        <RouterLink
          to="/games"
          class="flex items-center gap-2 px-3.5 py-2 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-100 transition"
        >
          <svg
            class="w-4 h-4 text-gray-600"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M14.828 14.828a4 4 0 01-5.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
            />
          </svg>
          <span>Games</span>
        </RouterLink>
      </nav>
    </header>

    <!-- Auth Loading State -->
    <section
      v-if="currentUser.isPending.value"
      class="auth-state w-full min-h-[50vh] flex items-center justify-center text-gray-500 font-medium"
      aria-live="polite"
    >
      {{ t('auth.loading') }}
    </section>

    <!-- Auth Error / Sign In Prompt -->
    <section
      v-else-if="currentUser.isError.value"
      class="auth-state w-full min-h-[60vh] flex items-center justify-center p-6"
      aria-labelledby="sign-in-title"
    >
      <div
        class="auth-state-card max-w-sm w-full bg-white border border-gray-200 rounded-2xl p-8 text-center shadow-xs"
      >
        <span
          class="auth-state-icon inline-flex items-center justify-center w-12 h-12 rounded-full bg-[#386671] text-white font-bold text-lg mb-4"
          aria-hidden="true"
          >BG</span
        >
        <h1 id="sign-in-title" class="text-xl font-bold text-gray-900 mb-2">
          {{ t('auth.signInTitle') }}
        </h1>
        <p class="text-sm text-gray-600 mb-6">
          {{ t('status.authenticationHint') }}
        </p>
        <a
          class="button inline-flex items-center justify-center w-full bg-[#386671] hover:bg-[#2c535c] text-white font-medium px-5 py-2.5 rounded-lg transition"
          :href="signInHref"
        >
          {{ t('actions.signIn') }}
        </a>
      </div>
    </section>

    <!-- App Content View -->
    <RouterView v-else />
  </main>
</template>

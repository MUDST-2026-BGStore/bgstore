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
    <!-- Top Navigation Bar (Figma Design - คงเดิม 100%) -->
    <header
      class="masthead w-full h-12 border-b border-gray-200 bg-white px-12 flex justify-between items-center"
    >
      <!-- Brand Logo -->
      <RouterLink
        class="brand flex items-center"
        to="/"
        aria-label="BGStore home"
      >
        <div
          class="w-8 h-8 rounded-full bg-[#386671] flex items-center justify-center text-white font-bold text-xs"
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
          class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-gray-700 hover:bg-gray-100 transition"
        >
          <svg
            class="w-3.5 h-3.5 text-gray-600"
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
          class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-[#e8eef0] text-gray-900 transition"
        >
          <svg
            class="w-3.5 h-3.5 text-gray-800"
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
          class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-gray-700 hover:bg-gray-100 transition"
        >
          <svg
            class="w-3.5 h-3.5 text-gray-600"
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
          class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-gray-700 hover:bg-gray-100 transition"
        >
          <svg
            class="w-3.5 h-3.5 text-gray-600"
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

    <!-- Authentication State Views (สำหรับ Unit Test & Login Session) -->
    <section
      v-if="currentUser.isPending.value"
      class="auth-state"
      aria-live="polite"
    >
      {{ t('auth.loading') }}
    </section>

    <section
      v-else-if="currentUser.isError.value"
      class="auth-state"
      aria-labelledby="sign-in-title"
    >
      <div class="auth-state-card">
        <span class="auth-state-icon" aria-hidden="true">BG</span>
        <h1 id="sign-in-title">{{ t('auth.signInTitle') }}</h1>
        <p>{{ t('status.authenticationHint') }}</p>
        <a class="button" :href="signInHref">{{ t('actions.signIn') }}</a>
      </div>
    </section>

    <!-- Page Content ปกติ (แสดงผลเมื่อ Login เรียบร้อย) -->
    <RouterView v-else />
  </main>
</template>

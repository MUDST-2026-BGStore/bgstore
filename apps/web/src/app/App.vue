<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query';
import { computed, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { RouterView, useRoute, useRouter } from 'vue-router';
import AppNavbar from '../components/AppNavbar.vue';
import { currentUserQueryOptions, signInHref } from '../queries/current-user';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const usesFocusedLayout = computed(() => route.meta.focused === true);
const currentUser = useQuery(currentUserQueryOptions());

const signInRequired = computed(
  () =>
    !route.meta.public &&
    (currentUser.isError.value || currentUser.data.value === null),
);
const signIn = computed(() => signInHref(route.fullPath));

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
  <div class="min-h-screen w-full bg-canvas">
    <AppNavbar v-if="!usesFocusedLayout" />

    <section
      v-if="currentUser.isPending.value"
      class="auth-state"
      aria-live="polite"
    >
      {{ t('auth.loading') }}
    </section>

    <section
      v-else-if="signInRequired"
      class="auth-state"
      aria-labelledby="sign-in-title"
    >
      <div class="auth-state-card">
        <span class="auth-state-icon" aria-hidden="true">BG</span>
        <h1 id="sign-in-title">{{ t('auth.signInTitle') }}</h1>
        <p>{{ t('status.authenticationHint') }}</p>
        <a class="button" :href="signIn">{{ t('actions.signIn') }}</a>
      </div>
    </section>

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

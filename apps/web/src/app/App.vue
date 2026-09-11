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
  <main class="shell">
    <header class="masthead">
      <RouterLink class="brand" to="/" aria-label="BGStore home">
        <span class="brand-mark" aria-hidden="true">BG</span>
        <span>BGStore</span>
      </RouterLink>
      <nav class="site-nav" aria-label="Primary navigation">
        <RouterLink to="/">{{ t('navigation.home') }}</RouterLink>
        <span>{{ t('navigation.game') }}</span>
        <span>{{ t('navigation.branch') }}</span>
      </nav>
    </header>

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

    <RouterView v-else v-slot="{ Component, route: renderedRoute }">
      <component :is="Component" :key="renderedRoute.path" />
    </RouterView>
  </main>
</template>

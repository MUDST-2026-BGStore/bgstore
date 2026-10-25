import { createApp } from 'vue';
import { initAuth } from '@theparlor/auth';
import App from './App.vue';
import { pinia } from './plugins/pinia';
import { createAppRouter } from './router';
import { i18n } from './plugins/i18n';
import { VueQueryPlugin } from '@tanstack/vue-query';
import { configureApi, queryClient } from '../shared/lib/api';
import { initializeLocaleSync } from '../shared/lib/locale';

export async function bootstrap(): Promise<void> {
  const app = createApp(App);
  configureApi();
  initializeLocaleSync();
  app.use(pinia);
  app.use(VueQueryPlugin, { queryClient });

  // Restore the server identity before resolving public/private feature routes.
  try {
    await initAuth();
  } catch (error) {
    console.error('Session initialization failed:', error);
    const root = document.querySelector('#app');
    if (root)
      root.textContent =
        'The server is unavailable. Check the API and reload this page.';
    return;
  }

  const router = createAppRouter();
  app.use(router);
  app.use(i18n);
  await router.isReady();
  app.mount('#app');
}

import './styles.css';
import { VueQueryPlugin } from '@tanstack/vue-query';
import { createPinia } from 'pinia';
import { createApp, h } from 'vue';
import { RouterView } from 'vue-router';
import { client } from './generated/api/client.gen';
import { i18n } from './i18n';
import { router } from './router';

client.setConfig({
  baseUrl: '/api/v1',
  credentials: 'include',
});

const app = createApp({
  render: () => h(RouterView),
});

app.use(createPinia());
app.use(router);
app.use(i18n);
app.use(VueQueryPlugin, {
  queryClientConfig: {
    defaultOptions: {
      queries: {
        retry: 1,
        staleTime: 30_000,
      },
    },
  },
});
app.mount('#root');

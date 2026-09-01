import { createRouter, createWebHistory } from 'vue-router';
import App from './app/App.vue';
import TableManagementView from './app/tables/TableManagementView.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'App',
      component: App,
    },
    {
      path: '/tables',
      name: 'Tables',
      component: TableManagementView,
    },
  ],
});

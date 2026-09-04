import { createRouter, createWebHistory } from 'vue-router';
import TableManagementView from './app/tables/TableManagementView.vue';
import HomeView from './views/HomeView.vue';
import OnboardingView from './views/OnboardingView.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/tables', name: 'tables', component: TableManagementView },
    { path: '/onboarding', name: 'onboarding', component: OnboardingView },
  ],
});

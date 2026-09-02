import { createRouter, createWebHistory } from 'vue-router';
import HomeView from './views/HomeView.vue';
import OnboardingView from './views/OnboardingView.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/onboarding', name: 'onboarding', component: OnboardingView },
  ],
});

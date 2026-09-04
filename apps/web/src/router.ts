import { createRouter, createWebHistory } from 'vue-router';
import HomeView from './views/HomeView.vue';
import OnboardingView from './views/OnboardingView.vue';
import UserProfileView from './views/UserProfileView.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    {
      path: '/user-profile',
      alias: '/account/manage',
      name: 'user-profile',
      component: UserProfileView,
    },
    { path: '/onboarding', name: 'onboarding', component: OnboardingView },
  ],
});

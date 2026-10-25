import type { RouteRecordRaw } from 'vue-router';

export const accountRoutes: RouteRecordRaw[] = [
  {
    path: '/onboarding',
    name: 'AccountOnboarding',
    component: () => import('./pages/Onboarding.vue'),
    meta: {
      requiresAuth: true,
      layout: 'raw',
    },
  },
  /*
  {
    path: '/account',
    name: 'AccountIndex',
    component: () => import('./pages/AccountPage.vue'),
    meta: {
      requiresAuth: true,
      layout: 'customer',
    },
  },
   */
];

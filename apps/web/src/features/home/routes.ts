import type { RouteRecordRaw } from 'vue-router';

export const homeRoutes: RouteRecordRaw[] = [
  {
    path: '/games/:gameId',
    name: 'GameProduct',
    component: () => import('./pages/GameProduct.vue'),
  },
  {
    path: '/',
    name: 'HomeIndex',
    component: () => import('./pages/HomeIndex.vue'),
  },
];

import type { RouteRecordRaw } from 'vue-router';

export const homeRoutes: RouteRecordRaw[] = [
  {
    path: '/cart',
    name: 'Cart',
    component: () => import('./pages/CartPage.vue'),
  },
  {
    path: '/checkout',
    name: 'Checkout',
    component: () => import('./pages/CheckoutPage.vue'),
  },
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

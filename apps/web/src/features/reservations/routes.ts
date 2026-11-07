import type { RouteRecordRaw } from 'vue-router';

export const reservationRoutes: RouteRecordRaw[] = [
  {
    path: '/reservations/new',
    name: 'CustomerReservePage',
    meta: { requiresAuth: true },
    component: () => import('./pages/CustomerReservePage.vue'),
  },
  {
    path: '/reservations',
    name: 'CustomerReservationsList',
    meta: { requiresAuth: true },
    component: () => import('./pages/CustomerReservationsList.vue'),
  },
];

export const staffReservationRoutes: RouteRecordRaw[] = [
  {
    path: '/workspace/reservations',
    redirect: '/workspace/management',
  },
  {
    path: '/staff/reservations',
    redirect: '/workspace/management',
  },
];

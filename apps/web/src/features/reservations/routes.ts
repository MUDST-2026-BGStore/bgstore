import type { RouteRecordRaw } from 'vue-router';

export const reservationRoutes: RouteRecordRaw[] = [
  {
    path: '/reservations/new',
    name: 'CustomerReservePage',
    component: () => import('./pages/CustomerReservePage.vue'),
  },
  {
    path: '/reservations',
    name: 'CustomerReservationsList',
    component: () => import('./pages/CustomerReservationsList.vue'),
  },
];

export const staffReservationRoutes: RouteRecordRaw[] = [
  {
    path: '/workspace/reservations',
    name: 'StaffReservationsPage',
    meta: { requiresAuth: true },
    component: () => import('./pages/StaffReservationsPage.vue'),
  },
];


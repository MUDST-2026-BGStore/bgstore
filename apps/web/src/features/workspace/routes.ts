import type { RouteRecordRaw } from 'vue-router';

export const workspaceRoutes: RouteRecordRaw[] = [
  {
    path: '/workspace',
    name: 'WorkspaceIndex',
    meta: { requiresAuth: true },
    component: () => import('./pages/WorkspaceIndex.vue'),
  },
  {
    path: '/workspace/management',
    name: 'WorkspaceManagement',
    meta: { requiresAuth: true },
    component: () =>
      import('../reservations/pages/StaffReservationsPage.vue'),
  },
  {
    path: '/workspace/reservations',
    name: 'StaffReservationsPage',
    redirect: '/workspace/management',
  },
  {
    path: '/management',
    redirect: '/workspace/management',
  },
  {
    path: '/staff/reservations',
    redirect: '/workspace/management',
  },
  {
    path: '/management/reservations',
    redirect: '/workspace/management',
  },
  {
    path: '/admin',
    name: 'WorkspaceAdministration',
    meta: { roles: ['admin'] },
    component: () => import('./pages/WorkspaceAdministration.vue'),
  },
];

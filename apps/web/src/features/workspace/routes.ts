import type { RouteRecordRaw } from 'vue-router';

import { BRANCH_OPERATOR_ROLES } from '../../shared/lib/roles';

export const workspaceRoutes: RouteRecordRaw[] = [
  {
    path: '/workspace',
    name: 'WorkspaceIndex',
    meta: { requiresAuth: true, layout: 'customer' },
    component: () => import('./pages/WorkspaceIndex.vue'),
  },
  {
    path: '/workspace/management',
    name: 'WorkspaceManagement',
    // The redirects below land here, so they inherit this role check.
    meta: { requiresAuth: true, roles: BRANCH_OPERATOR_ROLES },
    component: () => import('../reservations/pages/StaffReservationsPage.vue'),
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

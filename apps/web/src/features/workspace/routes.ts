import type { RouteRecordRaw } from 'vue-router';

export const workspaceRoutes: RouteRecordRaw[] = [
  {
    path: '/workspace',
    name: 'WorkspaceIndex',
    meta: { requiresAuth: true },
    component: () => import('./pages/WorkspaceIndex.vue'),
  },
  {
    path: '/management',
    name: 'WorkspaceManagement',
    meta: { roles: ['admin', 'manager'] },
    component: () => import('./pages/WorkspaceManagement.vue'),
  },
  {
    path: '/admin',
    name: 'WorkspaceAdministration',
    meta: { roles: ['admin'] },
    component: () => import('./pages/WorkspaceAdministration.vue'),
  },
];

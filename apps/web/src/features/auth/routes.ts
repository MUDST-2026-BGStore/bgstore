import type { RouteRecordRaw } from 'vue-router';

export const authRoutes: RouteRecordRaw[] = [
  // Navigation-only route: the app guard starts OIDC or chooses the post-login destination.
  // Keycloak owns the login form; the local page is a navigation fallback, not a credentials form.
  {
    path: '/login',
    name: 'AuthLogin',
    component: () => import('./pages/AuthLogin.vue'),
  },
  {
    path: '/profile',
    name: 'AuthProfile',
    meta: { requiresAuth: true },
    component: () => import('./pages/AuthProfile.vue'),
  },
  {
    path: '/forbidden',
    name: 'AuthForbidden',
    component: () => import('./pages/AuthForbidden.vue'),
  },
];

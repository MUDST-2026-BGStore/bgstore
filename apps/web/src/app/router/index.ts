import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw,
} from 'vue-router';
import { homeRoutes } from '../../features/home/routes';
import { authRoutes } from '../../features/auth/routes';
import { workspaceRoutes } from '../../features/workspace/routes';
import { accountRoutes } from '../../features/account/routes';
import { reservationRoutes } from '../../features/reservations/routes';
import { installAuthGuards } from './guards';

import CustomerLayout from '../layouts/CustomerLayout.vue';

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean;
    roles?: readonly string[];
    layout?: 'customer' | 'raw';
  }
}

export function createAppRouter() {
  const customerPages: RouteRecordRaw[] = [
    ...homeRoutes,
    ...authRoutes,
    ...accountRoutes,
    ...reservationRoutes,
    ...workspaceRoutes.filter((route) => route.meta?.layout === 'customer'),
  ];

  const customerRoutes = customerPages.filter(
    (route) => route.meta?.layout !== 'raw',
  );

  const rawRoutes = customerPages.filter(
    (route) => route.meta?.layout === 'raw',
  );

  const router = createRouter({
    history: createWebHistory(),
    scrollBehavior: (_to, _from, savedPosition) => savedPosition || { top: 0 },
    routes: [
      {
        path: '/',
        component: CustomerLayout,
        children: customerRoutes,
      },

      // These pages render directly without ParlorShell.
      ...rawRoutes,

      // Staff pages remain separate.
      ...workspaceRoutes.filter((route) => route.meta?.layout !== 'customer'),

      {
        path: '/:pathMatch(.*)*',
        redirect: { name: 'HomeIndex' },
      },
    ],
  });

  installAuthGuards(router);

  return router;
}

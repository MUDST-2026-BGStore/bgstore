import { defineAsyncComponent } from 'vue';
import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw,
} from 'vue-router';
import RoleView from './app/RoleView.vue';
import HomeView from './views/HomeView.vue';
import OnboardingView from './views/OnboardingView.vue';
import BranchDetailView from './views/BranchDetailView.vue';
import BranchListView from './views/BranchListView.vue';
import UserProfileView from './views/UserProfileView.vue';
import AccessDeniedView from './views/AccessDeniedView.vue';

const fallbackRoute = {
  path: '/:pathMatch(.*)*',
  redirect: '/',
} as const;

declare module 'vue-router' {
  interface RouteMeta {
    /** A guest without a session may open the screen; the rest ask them to sign in. */
    public?: boolean;
    requiresAuth?: boolean;
    /** Full-page task flow that hides the shared application navigation. */
    focused?: boolean;
  }
}

export type AuthResolver = () => boolean;

let authResolver: AuthResolver = () => true;

export function setAuthResolver(resolver: AuthResolver) {
  authResolver = resolver;
}

export function resetAuthResolver() {
  authResolver = () => true;
}

export const routes: RouteRecordRaw[] = [
  // Home is the floor overview for staff and the store landing page for guests.
  {
    path: '/',
    name: 'home',
    component: RoleView,
    meta: { public: true },
    props: {
      staff: defineAsyncComponent(
        () => import('./pages/floor/FloorOverviewPage.vue'),
      ),
      client: HomeView,
    },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('./views/LoginView.vue'),
    meta: { public: true },
  },
  { path: '/onboarding', name: 'onboarding', component: OnboardingView },
  {
    path: '/branches',
    name: 'branches',
    component: BranchListView,
    meta: { public: true },
  },
  {
    path: '/branches/:id',
    name: 'branch-detail',
    component: BranchDetailView,
    meta: { public: true },
  },
  {
    path: '/tables',
    name: 'tables',
    component: RoleView,
    props: {
      staff: defineAsyncComponent(
        () => import('./app/tables/TableManagementView.vue'),
      ),
      client: AccessDeniedView,
    },
    meta: { requiresAuth: true },
  },
  {
    path: '/history',
    name: 'history',
    component: RoleView,
    props: {
      staff: AccessDeniedView,
      client: defineAsyncComponent(
        () => import('./pages/history/ClientHistoryListPage.vue'),
      ),
    },
    meta: { requiresAuth: true },
  },
  {
    path: '/history/:id',
    name: 'history-detail',
    component: RoleView,
    props: {
      staff: AccessDeniedView,
      client: defineAsyncComponent(
        () => import('./pages/history/ClientHistoryDetailPage.vue'),
      ),
    },
    meta: { requiresAuth: true },
  },
  // Staff manage the inventory at these two URLs; guests browse the catalogue
  // at the same ones, so a shared link to a game works for either.
  {
    path: '/games',
    name: 'games',
    component: RoleView,
    props: {
      staff: defineAsyncComponent(
        () => import('./pages/games/GamesInventoryPage.vue'),
      ),
      client: defineAsyncComponent(
        () => import('./pages/games/GameCataloguePage.vue'),
      ),
    },
  },
  {
    path: '/games/new',
    name: 'games-new',
    component: RoleView,
    props: {
      staff: defineAsyncComponent(
        () => import('./pages/games/AddGamePage.vue'),
      ),
      client: AccessDeniedView,
    },
    meta: { requiresAuth: true },
  },
  // An id with no record is answered by the API, and the screen renders its
  // own "not found" state, so there is no route guard to keep in step with it.
  {
    path: '/games/:gameId',
    name: 'games-detail',
    component: RoleView,
    props: {
      staff: defineAsyncComponent(
        () => import('./pages/games/GameDetailsPage.vue'),
      ),
      client: defineAsyncComponent(
        () => import('./pages/games/GameCatalogueDetailPage.vue'),
      ),
    },
  },
  {
    path: '/games/:gameId/edit',
    name: 'games-edit',
    component: RoleView,
    props: {
      staff: defineAsyncComponent(
        () => import('./pages/games/EditGamePage.vue'),
      ),
      client: AccessDeniedView,
    },
    meta: { requiresAuth: true },
  },
  // The SPA is served for every path (see apps/web/nginx.conf), so unmatched
  // URLs must resolve to a real screen instead of an empty router view.
  fallbackRoute,
];

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    ...routes.slice(0, -1),
    {
      path: '/sessions/active',
      alias: '/active-session',
      name: 'client-active-session',
      component: () => import('./views/ActiveSessionView.vue'),
      meta: { requiresAuth: true, focused: true },
    },
    {
      path: '/profile',
      alias: ['/user-profile', '/account/manage'],
      name: 'user-profile',
      component: RoleView,
      props: {
        staff: defineAsyncComponent(
          () => import('./views/StaffProfileView.vue'),
        ),
        client: UserProfileView,
      },
      meta: { requiresAuth: true },
    },
    fallbackRoute,
  ],
});

router.beforeEach((to, _from, next) => {
  if (to.matched.some((record) => record.meta?.requiresAuth)) {
    if (!authResolver()) {
      return next({ path: '/login', query: { redirect: to.fullPath } });
    }
  }
  next();
});

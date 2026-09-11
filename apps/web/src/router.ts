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

const fallbackRoute = {
  path: '/:pathMatch(.*)*',
  redirect: '/',
} as const;

declare module 'vue-router' {
  interface RouteMeta {
    /** A guest without a session may open the screen; the rest ask them to sign in. */
    public?: boolean;
    requiresAuth?: boolean;
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
  { path: '/', name: 'home', component: HomeView, meta: { public: true } },
  {
    path: '/login',
    name: 'login',
    component: () => import('./views/LoginView.vue'),
  },
  { path: '/onboarding', name: 'onboarding', component: OnboardingView },
  { path: '/branches', name: 'branches', component: BranchListView },
  {
    path: '/branches/:id',
    name: 'branch-detail',
    component: BranchDetailView,
  },
  {
    path: '/tables',
    name: 'tables',
    component: () => import('./app/tables/TableManagementView.vue'),
  },
  {
    path: '/history',
    name: 'history',
    component: () => import('./pages/history/ClientHistoryListPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/history/:id',
    name: 'history-detail',
    component: () => import('./pages/history/ClientHistoryDetailPage.vue'),
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
    component: () => import('./pages/games/AddGamePage.vue'),
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
    component: () => import('./pages/games/EditGamePage.vue'),
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
      path: '/profile',
      alias: ['/user-profile', '/account/manage'],
      name: 'user-profile',
      component: UserProfileView,
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

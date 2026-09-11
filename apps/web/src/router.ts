import { defineAsyncComponent } from 'vue';
import { createRouter, createWebHistory } from 'vue-router';
import RoleView from './app/RoleView.vue';
import HomeView from './views/HomeView.vue';
import OnboardingView from './views/OnboardingView.vue';

export const routes = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/onboarding', name: 'onboarding', component: OnboardingView },
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
  { path: '/:pathMatch(.*)*', redirect: '/' },
] as const;

export const router = createRouter({
  history: createWebHistory(),
  routes: [...routes],
});

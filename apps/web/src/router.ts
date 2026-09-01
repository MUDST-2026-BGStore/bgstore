import { createRouter, createWebHistory } from 'vue-router';
import HomeView from './views/HomeView.vue';
import OnboardingView from './views/OnboardingView.vue';

export const routes = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/onboarding', name: 'onboarding', component: OnboardingView },
  {
    path: '/games',
    name: 'games',
    component: () => import('./pages/games/GamesInventoryPage.vue'),
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
    component: () => import('./pages/games/GameDetailsPage.vue'),
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

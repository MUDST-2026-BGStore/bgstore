import { createRouter, createWebHistory } from 'vue-router';
import HomeView from './views/HomeView.vue';
import OnboardingView from './views/OnboardingView.vue';
import BranchListView from './views/BranchListView.vue';
import BranchDetailView from './views/BranchDetailView.vue';
import BranchFormView from './views/BranchFormView.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/onboarding', name: 'onboarding', component: OnboardingView },
    { path: '/branches', name: 'branches', component: BranchListView },
    { path: '/branches/new', name: 'branch-new', component: BranchFormView },
    {
      path: '/branches/:id',
      name: 'branch-detail',
      component: BranchDetailView,
    },
    {
      path: '/branches/:id/edit',
      name: 'branch-edit',
      component: BranchFormView,
    },
  ],
});

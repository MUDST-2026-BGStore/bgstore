import { defineStore } from 'pinia';
import { computed } from 'vue';
import { useAuth, login, logout } from '@theparlor/auth';

export const useAuthStore = defineStore('auth', () => {
  const state = useAuth();
  return {
    authenticated: computed(() => state.value.authenticated),
    username: computed(() => state.value.username),
    subject: computed(() => state.value.subject),
    displayName: computed(() => state.value.displayName),
    email: computed(() => state.value.email),
    emailVerified: computed(() => state.value.emailVerified),
    login,
    roles: computed(() => state.value.roles),
    logout,
  };
});

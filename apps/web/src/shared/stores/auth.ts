import { defineStore } from 'pinia';
import { computed } from 'vue';
import { useAuth, login, logout as endSession } from '@theparlor/auth';
import { clearProtectedState } from '../lib/protected-state';

export const useAuthStore = defineStore('auth', () => {
  const state = useAuth();
  /** Drops account data before the logout navigation, which may not return here. */
  async function logout(): Promise<void> {
    clearProtectedState();
    await endSession();
  }
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

import { session, csrf } from '@theparlor/api-client';
import { ref, readonly } from 'vue';
import type { AuthState } from './types';

const emptyState = (): AuthState => ({
  authenticated: false,
  username: null,
  subject: null,
  displayName: null,
  email: null,
  emailVerified: false,
  roles: [],
});
const state = ref<AuthState>(emptyState());

export function clearAuth(): void {
  state.value = emptyState();
}

/** Read server session state; no OAuth tokens enter the browser. */
export async function initAuth(): Promise<boolean> {
  const currentSession = await session();
  const profile = currentSession.profile;
  state.value =
    currentSession.authenticated && profile
      ? {
          authenticated: true,
          subject: profile.subject,
          username: profile.username,
          displayName: profile.displayName ?? null,
          email: profile.email ?? null,
          emailVerified: profile.emailVerified,
          roles: [...profile.roles],
        }
      : emptyState();
  return state.value.authenticated;
}

export function useAuth() {
  return readonly(state);
}

export async function login(): Promise<void> {
  window.location.assign('/api/auth/login/keycloak');
}

/** A native POST navigation lets Spring clear the session and redirect through Keycloak logout. */
export async function logout(): Promise<void> {
  const token = await csrf();
  const form = document.createElement('form');
  form.method = 'POST';
  form.action = '/api/auth/logout';
  const input = document.createElement('input');
  input.type = 'hidden';
  input.name = token.parameterName;
  input.value = token.token;
  form.append(input);
  document.body.append(form);
  form.submit();
  form.remove();
}

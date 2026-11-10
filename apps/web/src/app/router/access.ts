export interface IdentitySnapshot {
  authenticated: boolean;
  roles: readonly string[];
}
export interface PageAccess {
  requiresAuth?: boolean;
  roles?: readonly string[];
}

export function workspaceDestination(roles: readonly string[]): string {
  if (roles.includes('admin')) return 'WorkspaceAdministration';
  if (roles.includes('manager')) return 'WorkspaceManagement';
  return 'WorkspaceIndex';
}

const BASE_URL = 'https://theparlor.invalid';

// eslint-disable-next-line no-control-regex -- Security validation intentionally matches control characters.
const UNSAFE_PATH_CHARACTERS = /[\\\x00-\x1F\x7F]/;

/** Only accept a local route, never an arbitrary post-login URL. */
export function safeReturnPath(value: unknown): string | null {
  if (
    typeof value !== 'string' ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    UNSAFE_PATH_CHARACTERS.test(value)
  ) {
    return null;
  }

  const url = new URL(value, BASE_URL);

  if (url.origin !== BASE_URL || url.pathname === '/login') {
    return null;
  }

  return value;
}

/** A UI navigation decision; backend authorization is always authoritative. */
export function pageAccess(
  identity: IdentitySnapshot,
  page: PageAccess,
): 'allow' | 'login' | 'forbidden' {
  const requiresAuth = page.requiresAuth || !!page.roles?.length;
  if (requiresAuth && !identity.authenticated) return 'login';
  if (
    page.roles?.length &&
    !page.roles.some((role) => identity.roles.includes(role))
  )
    return 'forbidden';
  return 'allow';
}

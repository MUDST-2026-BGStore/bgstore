import { describe, expect, it } from 'vitest';
import { defaultDestination, pageAccess, safeReturnPath } from './access';

describe('public and private page policy', () => {
  it('allows public browsing without a session and requires login for protected pages', () => {
    const guest = { authenticated: false, roles: [] };
    expect(pageAccess(guest, {})).toBe('allow');
    expect(pageAccess(guest, { requiresAuth: true })).toBe('login');
    expect(pageAccess(guest, { roles: ['admin'] })).toBe('login');
  });
  it('separates being signed in from having the page role', () => {
    const staff = { authenticated: true, roles: ['staff'] };
    expect(pageAccess(staff, { requiresAuth: true })).toBe('allow');
    expect(pageAccess(staff, { roles: ['admin', 'manager'] })).toBe(
      'forbidden',
    );
    expect(
      pageAccess(
        { authenticated: true, roles: ['manager'] },
        { roles: ['admin', 'manager'] },
      ),
    ).toBe('allow');
  });
  it('uses a predictable landing page with administrator taking precedence', () => {
    expect(defaultDestination(['staff'])).toBe('WorkspaceIndex');
    expect(defaultDestination(['manager'])).toBe('WorkspaceManagement');
    expect(defaultDestination(['manager', 'admin'])).toBe(
      'WorkspaceAdministration',
    );
    expect(defaultDestination([])).toBe('WorkspaceIndex');
  });
  it.each([
    'https://evil.example',
    '//evil.example',
    '/\\evil.example',
    '/login?redirect=/login',
    '/\n',
  ])('rejects an unsafe or looping return path: %s', (path) => {
    expect(safeReturnPath(path)).toBeNull();
  });
  it('preserves an intended local path and its query', () => {
    expect(safeReturnPath('/profile?tab=details')).toBe('/profile?tab=details');
    expect(safeReturnPath(['/workspace'])).toBeNull();
  });
});

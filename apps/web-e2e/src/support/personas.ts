/**
 * The people `make seed-users` (tools/seed-dev-users.mjs, also run by `make seed-dev`) creates.
 * They share E2E_PASSWORD. Keep usernames and roles in step with that script.
 */
export const PERSONAS = {
  customer: { username: 'parlor-customer', roles: [] as string[] },
  staff: { username: 'parlor-staff', roles: ['staff'] },
  staffUnassigned: { username: 'parlor-staff-unassigned', roles: ['staff'] },
  manager: { username: 'parlor-manager', roles: ['manager'] },
  admin: { username: 'parlor-admin', roles: ['admin'] },
} as const;

export type PersonaKey = keyof typeof PERSONAS;

/** The branch `parlor-staff` is assigned to, and one it is not. */
export const ASSIGNED_BRANCH = 'Siam';
export const OTHER_BRANCH = 'Ari';

export function personaPassword(): string {
  const password = process.env['E2E_PASSWORD'];
  if (!password)
    throw new Error(
      'Set E2E_PASSWORD (run make configure, then make seed-users).',
    );
  return password;
}

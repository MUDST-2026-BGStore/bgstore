// Local-only seed command: one sign-in per kind of person who uses The Parlor, so every
// role can be tried by hand and exercised by the end-to-end tests. Idempotent; run after the
// reference data exists (`make seed-dev` runs it last, because reseeding deletes branches and
// their staff assignments). Credentials are never stored in the repository: every persona
// shares E2E_PASSWORD from the environment file.
//
//   customer          no realm role           the storefront, own orders and bookings
//   staff             staff                   operates the assigned branches only
//   staff-unassigned  staff, no assignments   proves staff access needs an explicit branch
//   manager           manager                 operates every branch
//   admin             admin                   everything, including staff assignments
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

process.loadEnvFile(process.env['THEPARLOR_ENV_FILE'] || '.env');
const env = process.env;
const root = fileURLToPath(new URL('../', import.meta.url));
const keycloak = `http://localhost:${env['KEYCLOAK_PORT'] || '8081'}`;
// Must match the issuer claim the API stores with each account identity.
const issuer = `${keycloak}/realms/theparlor`;
const password = env['E2E_PASSWORD'];
if (!password)
  throw new Error('Set E2E_PASSWORD in .env (run make configure).');

/** Keep in step with apps/web-e2e/src/support/personas.ts. */
const PERSONAS = [
  {
    username: 'parlor-customer',
    firstName: 'Dev',
    lastName: 'Customer',
    phone: '0810000001',
    roles: [],
    branches: [],
  },
  {
    username: 'parlor-staff',
    firstName: 'Dev',
    lastName: 'Staff',
    phone: '0810000002',
    roles: ['staff'],
    branches: ['Siam'],
  },
  {
    username: 'parlor-staff-unassigned',
    firstName: 'Dev',
    lastName: 'Unassigned',
    phone: '0810000003',
    roles: ['staff'],
    branches: [],
  },
  {
    username: 'parlor-manager',
    firstName: 'Dev',
    lastName: 'Manager',
    phone: '0810000004',
    roles: ['manager'],
    branches: [],
  },
  {
    username: 'parlor-admin',
    firstName: 'Dev',
    lastName: 'Admin',
    phone: '0810000005',
    roles: ['admin'],
    branches: [],
  },
];
// Roles this script owns on the seeded people; any other mapping is left alone.
const MANAGED_ROLES = ['admin', 'seller', 'manager', 'staff'];

const tokenResponse = await fetch(
  `${keycloak}/realms/master/protocol/openid-connect/token`,
  {
    method: 'POST',
    body: new URLSearchParams({
      grant_type: 'password',
      client_id: 'admin-cli',
      username: env['KC_BOOTSTRAP_ADMIN_USERNAME'] || 'admin',
      password: env['KC_BOOTSTRAP_ADMIN_PASSWORD'] || '',
    }),
  },
);
if (!tokenResponse.ok)
  throw new Error(`Keycloak admin login failed: ${tokenResponse.status}`);
const token = (await tokenResponse.json()).access_token;

async function admin(path, method = 'GET', body, allowed = []) {
  const response = await fetch(`${keycloak}/admin/realms/theparlor${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (allowed.includes(response.status)) return null;
  if (!response.ok)
    throw new Error(`Keycloak ${method} ${path}: ${response.status}`);
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

async function realmRole(name) {
  const existing = await admin(`/roles/${name}`, 'GET', undefined, [404]);
  if (existing) return existing;
  await admin('/roles', 'POST', { name });
  return admin(`/roles/${name}`);
}

async function findUser(username) {
  const users = await admin(
    `/users?username=${encodeURIComponent(username)}&exact=true`,
  );
  return users[0];
}

async function ensureUser(persona) {
  const profile = {
    username: persona.username,
    enabled: true,
    emailVerified: true,
    firstName: persona.firstName,
    lastName: persona.lastName,
    email: `${persona.username}@example.test`,
    requiredActions: [],
  };
  let user = await findUser(persona.username);
  if (user) await admin(`/users/${user.id}`, 'PUT', { ...user, ...profile });
  else {
    await admin('/users', 'POST', profile);
    user = await findUser(persona.username);
  }
  await admin(`/users/${user.id}/reset-password`, 'PUT', {
    type: 'password',
    value: password,
    temporary: false,
  });
  // Make the realm roles exactly the persona's, whatever an earlier run or a hand edit left.
  const current = await admin(`/users/${user.id}/role-mappings/realm`);
  const stale = current.filter(
    (role) =>
      MANAGED_ROLES.includes(role.name) && !persona.roles.includes(role.name),
  );
  if (stale.length)
    await admin(`/users/${user.id}/role-mappings/realm`, 'DELETE', stale);
  const missing = persona.roles.filter(
    (name) => !current.some((role) => role.name === name),
  );
  if (missing.length)
    await admin(
      `/users/${user.id}/role-mappings/realm`,
      'POST',
      await Promise.all(missing.map(realmRole)),
    );
  return user.id;
}

const subjects = new Map();
for (const persona of PERSONAS)
  subjects.set(persona.username, await ensureUser(persona));

// The application's own records: a provisioned account per person, so sign-in goes straight
// to the app instead of onboarding, and branch assignments for staff.
const literal = (value) => `'${String(value).replaceAll("'", "''")}'`;
const statements = [
  'BEGIN;',
  `DELETE FROM branch_staff_assignments WHERE staff_subject IN (${[...subjects.values()].map(literal).join(', ')});`,
];
for (const persona of PERSONAS) {
  const subject = subjects.get(persona.username);
  if (!/^[0-9a-f-]{36}$/.test(subject))
    throw new Error(`Unexpected Keycloak user id for ${persona.username}`);
  statements.push(`DO $seed$
DECLARE account uuid;
BEGIN
  SELECT account_id INTO account FROM account_identities
    WHERE issuer = ${literal(issuer)} AND subject = ${literal(subject)};
  IF account IS NULL THEN
    INSERT INTO accounts (first_name, last_name, phone_number, preferences)
      VALUES (${literal(persona.firstName)}, ${literal(persona.lastName)}, ${literal(persona.phone)},
              '{"preferredLocale":"en-US"}'::jsonb)
      RETURNING id INTO account;
    INSERT INTO account_identities (issuer, subject, account_id)
      VALUES (${literal(issuer)}, ${literal(subject)}, account);
  ELSE
    UPDATE accounts SET first_name = ${literal(persona.firstName)},
      last_name = ${literal(persona.lastName)}, phone_number = ${literal(persona.phone)},
      updated_at = now()
      WHERE id = account;
  END IF;
END
$seed$;`);
  if (persona.branches.length)
    statements.push(
      `INSERT INTO branch_staff_assignments (branch_id, staff_subject, granted_by)
  SELECT id, ${literal(subject)}, 'dev-seed' FROM branches
  WHERE name IN (${persona.branches.map(literal).join(', ')})
  ON CONFLICT DO NOTHING;`,
    );
}
statements.push('COMMIT;');
statements.push(
  `SELECT 'assignments: ' || count(*) FROM branch_staff_assignments
  WHERE granted_by = 'dev-seed';`,
);

const result = spawnSync(
  'docker',
  [
    'compose',
    '--env-file',
    env['THEPARLOR_ENV_FILE'] || '.env',
    '-f',
    'deploy/docker/compose/compose.yaml',
    'exec',
    '-T',
    'postgres',
    'sh',
    '-c',
    'psql -q -t -A -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"',
  ],
  { cwd: root, input: statements.join('\n'), encoding: 'utf8' },
);
if (result.error) throw result.error;
if (result.status !== 0)
  throw new Error(`Seeding application accounts failed:\n${result.stderr}`);

console.log(
  'Seeded sign-ins (password: E2E_PASSWORD in your environment file):',
);
for (const persona of PERSONAS)
  console.log(
    `  ${persona.username.padEnd(26)} roles: ${persona.roles.join(', ') || 'none'}` +
      `${persona.branches.length ? `; branches: ${persona.branches.join(', ')}` : ''}`,
  );
const assigned = result.stdout.trim();
if (assigned === 'assignments: 0')
  console.warn(
    'No branches matched, so no staff were assigned. Run make seed-dev to create the branches.',
  );

// Local-only seed command. No users or credentials are stored in the shared realm JSON.
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
process.loadEnvFile(process.env['THEPARLOR_ENV_FILE'] || '.env');
const env = process.env;
const base = `http://localhost:${env['KEYCLOAK_PORT'] || '8081'}`;
const username = env['E2E_USERNAME'];
const password = env['E2E_PASSWORD'];
if (!username || !password)
  throw new Error('Set E2E_USERNAME and E2E_PASSWORD in .env.');
const tokenResponse = await fetch(
  `${base}/realms/master/protocol/openid-connect/token`,
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
  throw new Error(`Admin login failed: ${tokenResponse.status}`);
const token = (await tokenResponse.json()).access_token;
async function admin(path, method = 'GET', body) {
  const response = await fetch(`${base}/admin/realms/theparlor${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok)
    throw new Error(`Keycloak ${method} ${path}: ${response.status}`);
  return response.status === 204 || response.status === 201
    ? null
    : response.json();
}
// Existing realms are not re-imported on restart. Add the checked-in local role definitions.
const realm = JSON.parse(
  readFileSync('deploy/keycloak/realm-theparlor.json', 'utf8'),
);
// Explicitly update the local confidential client; imports do not update existing realms.
if (!env['THEPARLOR_OIDC_CLIENT_SECRET'])
  throw new Error('Run make configure to create THEPARLOR_OIDC_CLIENT_SECRET.');
const definition = {
  ...realm.clients.find((client) => client.clientId === 'theparlor-bff'),
  secret: env['THEPARLOR_OIDC_CLIENT_SECRET'],
};
const origin =
  env['THEPARLOR_PUBLIC_ORIGIN'] ||
  `http://localhost:${env['WEB_PORT'] || '5173'}`;
if (!definition.redirectUris.includes(`${origin}/api/auth/callback/keycloak`))
  definition.redirectUris.push(`${origin}/api/auth/callback/keycloak`);
const logoutUris =
  definition.attributes['post.logout.redirect.uris'].split('##');
if (!logoutUris.includes(`${origin}/`)) logoutUris.push(`${origin}/`);
definition.attributes['post.logout.redirect.uris'] = logoutUris.join('##');
const clients = await admin('/clients');
const existingClient = clients.find(
  (client) => client.clientId === definition.clientId,
);
if (existingClient)
  await admin(`/clients/${existingClient.id}`, 'PUT', {
    ...definition,
    id: existingClient.id,
  });
else await admin('/clients', 'POST', definition);
const oldBrowserClient = clients.find(
  (client) => client.clientId === 'theparlor-web',
);
if (oldBrowserClient?.enabled)
  await admin(`/clients/${oldBrowserClient.id}`, 'PUT', {
    ...oldBrowserClient,
    enabled: false,
  });
const roles = await admin('/roles');
for (const role of realm.roles.realm) {
  if (!roles.some((existing) => existing.name === role.name))
    await admin('/roles', 'POST', role);
}
let users = await admin(
  `/users?username=${encodeURIComponent(username)}&exact=true`,
);
if (users.length === 0) {
  await admin('/users', 'POST', {
    id: randomUUID(),
    username,
    enabled: true,
    emailVerified: true,
    firstName: 'Local',
    lastName: 'Developer',
    email: `${username}@example.invalid`,
    requiredActions: [],
    credentials: [{ type: 'password', value: password, temporary: false }],
  });
  users = await admin(
    `/users?username=${encodeURIComponent(username)}&exact=true`,
  );
} else {
  await admin(`/users/${users[0].id}/reset-password`, 'PUT', {
    type: 'password',
    value: password,
    temporary: false,
  });
}
const staffRole = await admin('/roles/staff');
await admin(`/users/${users[0].id}/role-mappings/realm`, 'POST', [staffRole]);
console.log(
  `Local user ${username} is ready; credentials are in your environment file.`,
);

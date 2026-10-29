import { readFileSync } from 'node:fs';

// Realm imports skip existing realms. Sync the login theme and its entry points
// and local Mailpit delivery without replacing users, clients or credentials.
process.loadEnvFile(process.env['THEPARLOR_ENV_FILE'] || '.env');
const realm = JSON.parse(
  readFileSync('deploy/keycloak/realm-theparlor.json', 'utf8'),
);
const base = `http://localhost:${process.env['KEYCLOAK_PORT'] || '8081'}`;
const tokenResponse = await fetch(
  `${base}/realms/master/protocol/openid-connect/token`,
  {
    method: 'POST',
    body: new URLSearchParams({
      grant_type: 'password',
      client_id: 'admin-cli',
      username: process.env['KC_BOOTSTRAP_ADMIN_USERNAME'] || 'admin',
      password: process.env['KC_BOOTSTRAP_ADMIN_PASSWORD'] || '',
    }),
  },
);
if (!tokenResponse.ok)
  throw new Error(`Keycloak admin login failed: ${tokenResponse.status}`);

const { access_token: token } = await tokenResponse.json();
const response = await fetch(
  `${base}/admin/realms/${encodeURIComponent(realm.realm)}`,
  {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      loginTheme: realm.loginTheme,
      registrationAllowed: realm.registrationAllowed,
      resetPasswordAllowed: realm.resetPasswordAllowed,
      // This command targets the local Compose realm, never a deployed realm.
      smtpServer: {
        host: 'mailpit',
        port: '1025',
        from: 'no-reply@theparlor.test',
        fromDisplayName: 'The Parlor',
        auth: 'false',
        ssl: 'false',
        starttls: 'false',
      },
    }),
  },
);
if (!response.ok)
  throw new Error(`Keycloak theme update failed: ${response.status}`);
console.log(
  `Keycloak realm ${realm.realm} now uses ${realm.loginTheme} with sign-up and password reset.`,
);

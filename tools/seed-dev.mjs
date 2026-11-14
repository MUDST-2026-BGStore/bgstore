// Local reseed: sync the dev user/Keycloak client first, then reset and reseed
// the API's reference data (branches, catalog, editions, stock, tables, copies)
// and time-relative demo reservations, then the role users, whose staff assignments
// the reset deletes with the branches.
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
await import('./seed-dev-user.mjs');
process.loadEnvFile(process.env['THEPARLOR_ENV_FILE'] || '.env');
console.log('\nSeeding API reference data and live-demo reservations…');
const child = spawn('./gradlew', ['--console=plain', 'seedReferenceData'], {
  cwd: `${root}apps/api`,
  stdio: 'inherit',
  env: process.env,
});
for (const signal of ['SIGINT', 'SIGTERM'])
  process.on(signal, () => child.kill(signal));
const code = await new Promise((resolve) =>
  child.on('exit', (status) => resolve(status ?? 1)),
);
if (code !== 0) process.exit(code);
console.log('\nSeeding customer, staff, manager and admin sign-ins…');
await import('./seed-dev-users.mjs');

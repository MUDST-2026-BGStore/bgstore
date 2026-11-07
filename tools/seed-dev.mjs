// Local reseed: sync the dev user/Keycloak client first, then reset and reseed
// the API's reference data (branches, catalog, editions, stock, tables, copies).
// No business-specific demo data beyond what the storefront needs.
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
await import('./seed-dev-user.mjs');
process.loadEnvFile(process.env['THEPARLOR_ENV_FILE'] || '.env');
console.log('\nSeeding API reference data…');
const isWin = process.platform === 'win32';
const cmd = isWin ? 'cmd.exe' : './gradlew';
const args = isWin
  ? ['/c', 'gradlew.bat', '--console=plain', 'seedReferenceData']
  : ['--console=plain', 'seedReferenceData'];
const child = spawn(cmd, args, {
  cwd: `${root}apps/api`,
  stdio: 'inherit',
  env: process.env,
});
for (const signal of ['SIGINT', 'SIGTERM'])
  process.on(signal, () => child.kill(signal));
child.on('exit', (code) => process.exit(code ?? 1));

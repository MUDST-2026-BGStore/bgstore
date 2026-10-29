// Local maintenance only. Selectors are passed as environment values, never shell code.
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const mode = process.argv[2];
const env = process.env;
if (!['delete-user', 'wipe-users'].includes(mode))
  throw new Error('Expected delete-user or wipe-users.');
const selectors = [
  env['THEPARLOR_DELETE_USERNAME'],
  env['THEPARLOR_DELETE_USER_ID'],
].filter(Boolean).length;
if (mode === 'delete-user' ? selectors !== 1 : selectors !== 0)
  throw new Error(
    'delete-user requires exactly one of USERNAME or USER_ID; wipe-users accepts neither.',
  );
if (
  env['THEPARLOR_DELETE_DRY_RUN'] !== '1' &&
  env['THEPARLOR_DELETE_CONFIRM'] !== '1'
)
  throw new Error(
    'Preview with DRY_RUN=1, or explicitly delete with CONFIRM=1.',
  );
process.loadEnvFile(env['THEPARLOR_ENV_FILE'] || `${root}.env`);
const child = spawn('./gradlew', ['--console=plain', 'manageUsers'], {
  cwd: `${root}apps/api`,
  stdio: 'inherit',
  env: { ...env, THEPARLOR_DELETE_MODE: mode },
});
child.on('error', (error) => {
  console.error(error.message);
  process.exitCode = 1;
});
for (const signal of ['SIGINT', 'SIGTERM'])
  process.on(signal, () => child.kill(signal));
child.on('exit', (code) => process.exit(code ?? 1));

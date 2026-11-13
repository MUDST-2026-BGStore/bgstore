import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
const file = process.env['THEPARLOR_ENV_FILE'] || `${root}.env`;
if (existsSync(file)) process.loadEnvFile(file);
const child = spawn(
  './gradlew',
  process.argv.slice(2).length
    ? process.argv.slice(2)
    : ['test', 'maintenanceTest'],
  {
    cwd: `${root}apps/api`,
    stdio: 'inherit',
    env: process.env,
  },
);
child.on('exit', (code) => process.exit(code ?? 1));

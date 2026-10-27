import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
const file = process.env['THEPARLOR_ENV_FILE'] || '.env';
if (existsSync(file)) process.loadEnvFile(file);
const child = spawn('pnpm', ['nx', 'e2e', 'web-e2e'], {
  stdio: 'inherit',
  env: process.env,
});
child.on('exit', (code) => process.exit(code ?? 1));

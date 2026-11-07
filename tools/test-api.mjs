import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
const file = process.env['THEPARLOR_ENV_FILE'] || `${root}.env`;
if (existsSync(file)) process.loadEnvFile(file);
const isWin = process.platform === 'win32';
const cmd = isWin ? 'cmd.exe' : './gradlew';
const subArgs = process.argv.slice(2).length
  ? process.argv.slice(2)
  : ['test', 'maintenanceTest'];
const args = isWin ? ['/c', 'gradlew.bat', ...subArgs] : subArgs;
const child = spawn(
  cmd,
  args,
  {
    cwd: `${root}apps/api`,
    stdio: 'inherit',
    env: process.env,
  },
);
child.on('exit', (code) => process.exit(code ?? 1));

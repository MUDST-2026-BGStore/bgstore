import { spawn } from 'node:child_process';
process.loadEnvFile('.env');
const env = process.env;
const isWin = process.platform === 'win32';
const cmd = isWin ? 'cmd.exe' : './gradlew';
const args = isWin
  ? ['/c', 'gradlew.bat', '--console=plain', 'bootRun', '--args=--spring.profiles.active=dev']
  : ['--console=plain', 'bootRun', '--args=--spring.profiles.active=dev'];

const child = spawn(
  cmd,
  // Keep Gradle's progress redraws out of Nx's task output; Spring colors its logs.
  args,
  {
    cwd: 'apps/api',
    stdio: 'inherit',
    env: {
      ...env,
      SPRING_DATASOURCE_USERNAME: env['POSTGRES_USER'],
      SPRING_DATASOURCE_PASSWORD: env['POSTGRES_PASSWORD'],
      SPRING_DATASOURCE_URL: `jdbc:postgresql://127.0.0.1:${env['POSTGRES_PORT'] || '5432'}/${env['POSTGRES_DB'] || 'theparlor'}`,
    },
  },
);
// Nx sends termination to this launcher; forward it so Gradle cancels bootRun
// and shuts down the application JVM rather than leaving it on port 8080.
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => child.kill(signal));
}
child.on('exit', (code, signal) => process.exit(code ?? (signal ? 0 : 1)));

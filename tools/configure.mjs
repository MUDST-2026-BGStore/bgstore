import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { execFileSync } from 'node:child_process';

if (existsSync('.env')) {
  let source = readFileSync('.env', 'utf8');
  let changed = false;
  for (const name of ['THEPARLOR_OIDC_CLIENT_SECRET', 'VALKEY_PASSWORD']) {
    const pattern = new RegExp(`^${name}=.+$`, 'm');
    if (pattern.test(source)) continue;
    source = source.replace(new RegExp(`^${name}=.*\\r?\\n?`, 'm'), '');
    source = `${source.trimEnd()}\n${name}=${randomBytes(32).toString('base64url')}\n`;
    changed = true;
  }
  if (changed) writeFileSync('.env', source, { mode: 0o600 });
  console.log(
    changed
      ? 'Added missing BFF/Valkey secrets; existing configuration is preserved.'
      : '.env already exists; keeping your configuration.',
  );
} else {
  let source = readFileSync('.env.example', 'utf8');
  for (const name of [
    'POSTGRES_PASSWORD',
    'KC_BOOTSTRAP_ADMIN_PASSWORD',
    'GRAFANA_ADMIN_PASSWORD',
    'E2E_PASSWORD',
    'THEPARLOR_OIDC_CLIENT_SECRET',
    'VALKEY_PASSWORD',
  ]) {
    source = source.replace(
      `${name}=\n`,
      `${name}=${randomBytes(24).toString('base64url')}\n`,
    );
  }
  // Lima rootless Docker exposes a different socket inside its VM.
  try {
    const info = execFileSync(
      'docker',
      ['info', '--format', '{{json .SecurityOptions}}'],
      { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] },
    );
    const context = JSON.parse(
      execFileSync('docker', ['context', 'inspect'], { encoding: 'utf8' }),
    )[0];
    const instance = context.Endpoints.docker.Host.match(
      /\.lima\/([^/]+)\/sock\//,
    )?.[1];
    if (info.includes('rootless') && instance) {
      const uid = execFileSync('limactl', ['shell', instance, 'id', '-u'], {
        encoding: 'utf8',
      }).trim();
      if (/^\d+$/.test(uid))
        source = source.replaceAll(
          '=/var/run/docker.sock',
          `=/run/user/${uid}/docker.sock`,
        );
    }
  } catch {
    /* Keep the portable default when Docker is not available yet. */
  }
  writeFileSync('.env', source, { mode: 0o600 });
  console.log('Created .env with random local credentials.');
}

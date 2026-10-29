// Deliberately destructive, scoped to this workspace and its Compose project.
// No global Docker prune, package-store purge, or deletion of source/configuration.
import { existsSync, readFileSync, rmSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
const dryRun = process.argv.includes('--dry-run');
const compose = [
  'compose',
  '--env-file',
  '.env',
  '-f',
  'deploy/docker/compose/compose.yaml',
];

function run(command, args, { capture = false } = {}) {
  const result = spawnSync(command, args, {
    cwd: root,
    stdio: capture ? ['ignore', 'pipe', 'pipe'] : 'inherit',
    encoding: 'utf8',
  });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    // Captured Compose config contains credentials; never print its output.
    throw new Error(`${command} ${args.join(' ')} failed (${result.status}).`);
  }
  return result.stdout;
}

function action(command, args) {
  if (dryRun) console.log(`[dry run] ${command} ${args.join(' ')}`);
  else run(command, args);
}

if (!existsSync(join(root, '.env'))) {
  throw new Error(
    'Run make configure first; nuke preserves the existing .env.',
  );
}

// Read effective image names instead of guessing IMAGE_TAG or overriding .env.
const config = JSON.parse(
  run('docker', [...compose, 'config', '--format', 'json'], { capture: true }),
);
const images = [
  ...new Set(['api', 'web'].map((name) => config.services[name].image)),
];
if (images.some((image) => typeof image !== 'string' || !image)) {
  throw new Error(
    'Expected named api and web images in Compose configuration.',
  );
}

console.log(
  `${dryRun ? 'Previewing' : 'Resetting'} The Parlor: containers, volumes, app images, dependencies and workspace build/test caches.`,
);
console.log(
  'Preserving .env, source, lockfiles, shared package caches and other Docker projects.',
);

// Keep these steps sequential: deletion must finish before reinstall/build/start.
action('docker', [...compose, 'down', '--volumes', '--remove-orphans']);
for (const image of images) {
  if (
    dryRun ||
    run('docker', ['image', 'ls', '--quiet', image], { capture: true }).trim()
  ) {
    // No --force: another container using an image must not be disrupted.
    action('docker', ['image', 'rm', image]);
  }
}
const nxPackage = join(root, 'node_modules/nx/package.json');
if (existsSync(nxPackage)) {
  const { bin } = JSON.parse(readFileSync(nxPackage, 'utf8'));
  const nx = join(
    root,
    'node_modules/nx',
    typeof bin === 'string' ? bin : bin.nx,
  );
  if (existsSync(nx)) action(process.execPath, [nx, 'reset']);
}

const outputNames = [
  'dist',
  'out-tsc',
  'coverage',
  'test-output',
  'test-results',
  'playwright-report',
  'blob-report',
  'node_modules',
];
const projects = [
  'apps/web',
  'apps/web-e2e',
  'apps/api',
  'libs/web/ui',
  'libs/web/auth',
  'libs/web/api-client',
];
const paths = [
  '.nx/cache',
  '.nx/workspace-data',
  'apps/api/build',
  'apps/api/.gradle',
  ...outputNames,
  ...projects.flatMap((project) =>
    outputNames.map((name) => `${project}/${name}`),
  ),
];
for (const path of paths) {
  if (dryRun) console.log(`[dry run] remove workspace output ${path}`);
  else rmSync(join(root, path), { recursive: true, force: true });
}

action('pnpm', ['install', '--frozen-lockfile']);
// Bypass Docker build caches without pruning caches shared by other projects.
action('docker', [...compose, 'build', '--no-cache', 'api', 'web']);
action('docker', [...compose, 'up', '-d', '--wait', '--wait-timeout', '300']);
action(process.execPath, [join(root, 'tools/seed-dev.mjs')]);
console.log(
  dryRun
    ? 'Preview complete; nothing was deleted or restarted.'
    : 'Fresh The Parlor stack ready. Local login credentials are unchanged in .env.',
);

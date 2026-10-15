import { defineConfig, devices } from '@playwright/test';
import { nxE2EPreset } from '@nx/playwright/preset';
import { workspaceRoot } from '@nx/devkit';

const baseURL = process.env['BASE_URL'] || 'http://127.0.0.1:4300';
const externalServer = Boolean(process.env['BASE_URL']);

/**
 * See https://playwright.dev/docs/test-configuration.
 *
 * Generated as a .mts file so Node forces ESM regardless of workspace
 * `type`. Playwright routes `.mts` through its ESM loader (dynamic import,
 * bypassing the pirates CJS-compile path), and Nx's native TS strip loads
 * `.mts` directly. Playwright's configLoader auto-discovers
 * `playwright.config.mts` via its extension list
 * (.ts/.js/.mts/.mjs/.cts/.cjs).
 */
export default defineConfig({
  ...nxE2EPreset(import.meta.dirname, { testDir: './src' }),
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    baseURL,
    timezoneId: 'Asia/Bangkok',
    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: 'on-first-retry',
  },
  // Keep the stateful reservation flow on Chromium and serialize seeded-account
  // mutations, including independent smoke tests on the other engines.
  workers: process.env['BGSTORE_FULL_STACK'] ? 1 : undefined,
  /* Run your local dev server before starting the tests */
  webServer: externalServer
    ? undefined
    : {
        command: 'pnpm exec nx run @mudst-2026-bgstore/web:preview',
        url: 'http://127.0.0.1:4300',
        reuseExistingServer: !process.env['CI'],
        cwd: workspaceRoot,
      },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    ...[
      { name: 'firefox', device: 'Desktop Firefox' },
      { name: 'webkit', device: 'Desktop Safari' },
    ].map(({ name, device }) => ({
      name,
      testIgnore: '**/*full-stack.spec.ts',
      use: { ...devices[device] },
    })),
    {
      name: 'mobile-chromium',
      testMatch: '**/reservation-journey.spec.ts',
      use: { ...devices['Pixel 7'] },
    },
  ],
});

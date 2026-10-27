import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './src',
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  use: {
    baseURL: process.env['E2E_BASE_URL'] || 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});

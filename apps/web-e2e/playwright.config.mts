import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './src',
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  // CI keeps an HTML report and traces of failures as artifacts.
  reporter: process.env['CI']
    ? [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]]
    : 'list',
  use: {
    baseURL: process.env['E2E_BASE_URL'] || 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});

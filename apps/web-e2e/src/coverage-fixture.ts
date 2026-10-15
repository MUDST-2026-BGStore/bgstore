import { test as base, expect } from '@playwright/test';

const enabled = process.env['BGSTORE_E2E_COVERAGE'] === '1';

/**
 * Collect Chromium V8 script ranges from Playwright's default page fixture.
 * Coverage is retained through navigations and is attached to each test so
 * the reporter can merge parallel workers and retries at the end of the run.
 */
const test = base.extend<{ collectV8Coverage: void }>({
  collectV8Coverage: [
    async ({ page, browserName }, use, testInfo) => {
      if (!enabled || browserName !== 'chromium') {
        await use();
        return;
      }

      await page.coverage.startJSCoverage({ resetOnNavigation: false });

      try {
        await use();
      } finally {
        // Playwright includes each script's source, which the source-map
        // reporter needs; raw Profiler ranges alone cannot be mapped reliably.
        const result = await page.coverage.stopJSCoverage();

        await testInfo.attach('v8-coverage.json', {
          body: Buffer.from(JSON.stringify(result)),
          contentType: 'application/json',
        });
      }
    },
    { auto: true },
  ],
});

export { test, expect };
export type { Page } from '@playwright/test';

import { CoverageReport } from 'monocart-coverage-reports';
import type { Reporter, TestCase, TestResult } from '@playwright/test/reporter';
import { readFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

type V8CoverageEntry = {
  url: string;
  source: string;
  sourceMap?: object;
  functions: Array<{
    functionName: string;
    ranges: Array<{ startOffset: number; endOffset: number; count: number }>;
    isBlockCoverage: boolean;
  }>;
};

const outputDir = fileURLToPath(
  new URL('../test-output/coverage/', import.meta.url),
);
const isFirstPartySource = (sourcePath: string) => {
  const normalized = sourcePath.replaceAll('\\', '/');
  return (
    /^(?:apps\/web\/)?src\//.test(normalized) &&
    !/(?:^|\/)generated\//.test(normalized) &&
    !normalized.endsWith('.d.ts') &&
    !/(?:\.|\/)(?:spec|test)\.[cm]?[jt]sx?$/.test(normalized)
  );
};

/** Merge Chromium V8 data, including unvisited bundles, into mapped reports. */
export default class E2ECoverageReporter implements Reporter {
  private readonly coverageAttachments: TestResult['attachments'] = [];
  private readonly coverageReport =
    process.env['BGSTORE_E2E_COVERAGE'] === '1'
      ? new CoverageReport({
          name: 'BGStore Chromium E2E source coverage',
          outputDir,
          baseDir: fileURLToPath(new URL('../../../', import.meta.url)),
          reports: [
            [
              'v8',
              {
                outputFile: 'index.html',
                metrics: ['statements', 'branches', 'functions', 'lines'],
              },
            ],
            ['json', { file: 'coverage-final.json' }],
            ['json-summary', { file: 'coverage-summary.json' }],
            ['lcovonly', { file: 'lcov.info' }],
          ],
          sourceFilter: isFirstPartySource,
          sourcePath: (sourcePath) =>
            sourcePath.startsWith('src/')
              ? `apps/web/${sourcePath}`
              : sourcePath,
        })
      : undefined;

  onTestEnd(_test: TestCase, result: TestResult) {
    if (!this.coverageReport) return;
    this.coverageAttachments.push(
      ...result.attachments.filter(
        (attachment) => attachment.name === 'v8-coverage.json',
      ),
    );
  }

  private async addTestCoverage() {
    if (!this.coverageReport) return;

    for (const attachment of this.coverageAttachments) {
      const content = attachment.body
        ? attachment.body.toString('utf8')
        : attachment.path
          ? await readFile(attachment.path, 'utf8')
          : undefined;
      if (!content) continue;

      const coverage = JSON.parse(content) as V8CoverageEntry[];
      await this.coverageReport.add(coverage);
    }
  }

  async onEnd(): Promise<void | { status: 'failed' }> {
    try {
      // Playwright awaits onEnd, but not promises returned by onTestEnd.
      await this.addTestCoverage();
      await this.generateReport();
    } catch (error) {
      console.error('E2E source coverage failed:', error);
      return { status: 'failed' };
    }
  }

  private async generateReport() {
    if (!this.coverageReport) return;
    const dist = process.env['BGSTORE_E2E_WEB_DIST'];
    const baseURL = process.env['BASE_URL'];
    if (!dist || !baseURL) {
      throw new Error(
        'E2E coverage requires the matching web dist and BASE_URL',
      );
    }
    // Seed every emitted chunk with zero counts, including unopened lazy Vue
    // routes. Merging observed ranges then gives an honest bundled-source total.
    const entries: V8CoverageEntry[] = [];
    for (const file of await readdir(dist, { recursive: true })) {
      if (!file.endsWith('.js')) continue;
      const source = await readFile(path.join(dist, file), 'utf8');
      entries.push({
        url: new URL(file, `${baseURL}/`).href,
        source,
        sourceMap: JSON.parse(
          await readFile(path.join(dist, `${file}.map`), 'utf8'),
        ),
        functions: [
          {
            functionName: '',
            isBlockCoverage: true,
            ranges: [{ startOffset: 0, endOffset: source.length, count: 0 }],
          },
        ],
      });
    }
    await this.coverageReport.add(entries);
    await this.coverageReport.generate();
    const report = JSON.parse(
      await readFile(path.join(outputDir, 'coverage-final.json'), 'utf8'),
    );
    const files = Object.keys(report);
    if (
      !files.some((file) => file.endsWith('.vue')) ||
      !files.some((file) => file.endsWith('/router.ts')) ||
      !Object.values(report).some((file: unknown) =>
        Object.values((file as { s: Record<string, number> }).s).some(
          (count) => count > 0,
        ),
      )
    ) {
      throw new Error(
        'E2E report is missing exercised, source-mapped first-party Vue/TypeScript',
      );
    }
  }
}

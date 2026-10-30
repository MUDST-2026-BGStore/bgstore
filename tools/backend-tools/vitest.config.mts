import { defineConfig } from 'vitest/config';

export default defineConfig({
  root: import.meta.dirname,
  cacheDir: '../../node_modules/.vite/tools/backend-tools',
  test: {
    name: 'backend-tools',
    watch: false,
    environment: 'node',
    include: ['src/**/*.spec.ts'],
  },
});

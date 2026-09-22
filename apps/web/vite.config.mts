/// <reference types='vitest' />
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig(() => ({
  root: import.meta.dirname,
  cacheDir: '../../node_modules/.vite/apps/web',
  server: {
    port: 4200,
    host: 'localhost',
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
      '/auth': { target: 'http://localhost:8080', changeOrigin: false },
      '/oauth2': { target: 'http://localhost:8080', changeOrigin: false },
      '/logout': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
  preview: {
    port: 4300,
    // Keep the preview listener on IPv4. Some CI/container runtimes resolve
    // localhost to ::1 but do not permit IPv6 loopback listeners.
    host: '127.0.0.1',
  },
  plugins: [vue(), tailwindcss()],
  build: {
    outDir: './dist',
    emptyOutDir: true,
    reportCompressedSize: true,
    commonjsOptions: {
      transformMixedEsModules: true,
    },
  },
  test: {
    name: '@mudst-2026-bgstore/web',
    watch: false,
    globals: true,
    environment: 'jsdom',
    include: ['{src,tests}/**/*.{test,spec}.{js,mjs,cjs,ts,mts,cts,jsx,tsx}'],
    reporters: ['default'],
    coverage: {
      enabled: true,
      reportsDirectory: './test-output/vitest/coverage',
      provider: 'v8' as const,
      exclude: ['src/generated/**', 'src/main.ts', 'src/router.ts'],
      thresholds: {
        lines: 80,
        branches: 75,
      },
    },
  },
}));

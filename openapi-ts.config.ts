import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: 'packages/contracts/openapi.yaml',
  output: {
    path: 'apps/web/src/generated/api',
    clean: true,
    postProcess: ['prettier'],
  },
  plugins: ['@hey-api/client-fetch', '@hey-api/typescript', '@hey-api/sdk'],
});

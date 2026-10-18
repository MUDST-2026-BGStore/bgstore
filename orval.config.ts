import { defineConfig } from 'orval';

export default defineConfig({
  theparlor: {
    input:
      process.env['THEPARLOR_OPENAPI_SPEC'] ??
      './dist/openapi/theparlor.openapi.yaml',
    output: {
      target: './libs/web/api-client/src/generated/api.ts',
      schemas: './libs/web/api-client/src/generated/models',
      client: 'vue-query',
      httpClient: 'axios',
      mode: 'single',
      clean: ['./libs/web/api-client/src/generated'],
      override: {
        mutator: {
          path: './libs/web/api-client/src/lib/transport.ts',
          name: 'apiRequest',
        },
        query: { version: 5, signal: true },
      },
    },
  },
});

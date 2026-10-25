import { readdirSync } from 'node:fs';
import vue from 'eslint-plugin-vue';
import prettier from 'eslint-config-prettier';
import baseConfig from '../../eslint.config.mjs';

// Apply the same dependency rules automatically when another feature folder is added.
const features = readdirSync(new URL('./src/features/', import.meta.url), {
  withFileTypes: true,
})
  .filter((entry) => entry.isDirectory())
  .map((entry) => entry.name);
const restrictLayers = (folders) => [
  'error',
  {
    patterns: [
      {
        regex: `^(?:\\.\\.?/)+(?:${folders.map((folder) => RegExp.escape(folder)).join('|')})(?:/|$)`,
        message:
          'Use shared code or an app-level integration; do not import another feature or the app layer.',
      },
    ],
  },
];

export default [
  ...baseConfig,
  ...vue.configs['flat/recommended'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: await import('@typescript-eslint/parser'),
      },
    },
  },
  {
    files: ['**/*.ts', '**/*.tsx', '**/*.js', '**/*.jsx', '**/*.vue'],
    rules: {
      'vue/multi-word-component-names': 'off',
    },
  },
  {
    files: ['src/shared/**/*.{ts,tsx,js,jsx,vue}'],
    rules: { 'no-restricted-imports': restrictLayers(['app', 'features']) },
  },
  ...features.map((feature) => ({
    files: [`src/features/${feature}/**/*.{ts,tsx,js,jsx,vue}`],
    rules: {
      'no-restricted-imports': restrictLayers([
        'app',
        'features',
        ...features.filter((other) => other !== feature),
      ]),
    },
  })),
  prettier,
];
